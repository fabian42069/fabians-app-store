// Builds the Fabians App Store repository in site/repo/ from catalog.json:
// copies each APK and icon, and writes an F-Droid compatible index
// (index-v1.json, plus index-v1.jar signed with the store key), so the same
// repository works for the Fabians App Store app and for F-Droid clients.
//
//   node tools/build-repo.mjs
//
// Needs the Android SDK build-tools (aapt, apksigner) and a JDK (keytool, jar,
// jarsigner). The store key is the Android signing key of Fabian's PC
// (~/.android/debug.keystore); every APK in the store must be signed with it,
// otherwise installed apps can't be updated.
import { readFileSync, writeFileSync, mkdirSync, copyFileSync, rmSync, existsSync, statSync, readdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { dirname, join, resolve, isAbsolute } from 'node:path';
import { fileURLToPath } from 'node:url';
import { homedir } from 'node:os';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const home = homedir();
const sdk = process.env.ANDROID_HOME || join(home, 'AppData', 'Local', 'Android', 'Sdk');
const buildTools = join(sdk, 'build-tools', readdirSync(join(sdk, 'build-tools')).sort().pop());
const javaHome = process.env.JAVA_HOME || 'C:/Program Files/Android/Android Studio/jbr';
const exe = process.platform === 'win32' ? '.exe' : '';
const tool = {
  aapt: join(buildTools, 'aapt' + exe),
  apksigner: join(buildTools, process.platform === 'win32' ? 'apksigner.bat' : 'apksigner'),
  keytool: join(javaHome, 'bin', 'keytool' + exe),
  jar: join(javaHome, 'bin', 'jar' + exe),
  jarsigner: join(javaHome, 'bin', 'jarsigner' + exe),
};
const keystore = process.env.STORE_KEYSTORE || join(home, '.android', 'debug.keystore');
const storepass = process.env.STORE_KEYSTORE_PASS || 'android';
const alias = process.env.STORE_KEY_ALIAS || 'androiddebugkey';

const run = (cmd, args, opts = {}) => execFileSync(cmd, args, { encoding: 'utf8', shell: cmd.endsWith('.bat'), ...opts });
const sha256 = (buf) => createHash('sha256').update(buf).digest('hex');

// The store key's certificate: its SHA-256 is the repository fingerprint, and
// F-Droid's "sig" is the MD5 of its hex form.
const certDer = execFileSync(tool.keytool, ['-exportcert', '-keystore', keystore, '-storepass', storepass, '-alias', alias]);
const signer = sha256(certDer);
const sig = createHash('md5').update(certDer.toString('hex')).digest('hex');

const catalog = JSON.parse(readFileSync(join(root, 'catalog.json'), 'utf8'));
const site = join(root, 'site');
const repoDir = join(site, 'repo');
rmSync(repoDir, { recursive: true, force: true });
const ICON_DIRS = ['icons', 'icons-120', 'icons-160', 'icons-240', 'icons-320', 'icons-480', 'icons-640'];
for (const d of ICON_DIRS) mkdirSync(join(repoDir, d), { recursive: true });

const now = Date.now();
const index = {
  repo: {
    timestamp: now,
    version: 20002,
    maxage: 14,
    name: catalog.repo.name,
    icon: 'icon.png',
    address: catalog.repo.address,
    description: catalog.repo.description,
  },
  requests: { install: [], uninstall: [] },
  apps: [],
  packages: {},
};
for (const d of ICON_DIRS) copyFileSync(join(root, catalog.repo.icon), join(repoDir, d, 'icon.png'));

for (const app of catalog.apps) {
  const apkPath = isAbsolute(app.apk) ? app.apk : resolve(root, app.apk);
  if (!existsSync(apkPath)) throw new Error(`APK not found for ${app.packageName}: ${apkPath}`);
  const badging = run(tool.aapt, ['dump', 'badging', apkPath]);
  const field = (re) => (badging.match(re) || [])[1];
  const packageName = field(/package: name='([^']+)'/);
  const versionCode = Number(field(/versionCode='(\d+)'/));
  const versionName = field(/versionName='([^']*)'/);
  const minSdk = Number(field(/sdkVersion:'(\d+)'/));
  const targetSdk = Number(field(/targetSdkVersion:'(\d+)'/));
  if (packageName !== app.packageName) throw new Error(`${apkPath} is ${packageName}, not ${app.packageName}`);

  // Every APK must be signed with the store key, or updates would fail.
  const certs = run(tool.apksigner, ['verify', '--print-certs', apkPath]);
  const apkSigner = (certs.match(/certificate SHA-256 digest: ([0-9a-f]+)/) || [])[1];
  if (apkSigner !== signer) throw new Error(`${packageName} is not signed with the store key`);

  const permissions = [...badging.matchAll(/uses-permission: name='([^']+)'(?: maxSdkVersion='(\d+)')?/g)]
    .map((m) => [m[1], m[2] ? Number(m[2]) : null]);
  const nativecode = (field(/native-code: (.+)/) || '').split(/\s+/).map((s) => s.replace(/'/g, '')).filter(Boolean);

  const apkName = `${packageName}_${versionCode}.apk`;
  copyFileSync(apkPath, join(repoDir, apkName));
  const apkBuf = readFileSync(apkPath);
  const iconName = `${packageName}.${versionCode}.png`;
  for (const d of ICON_DIRS) copyFileSync(resolve(root, app.icon), join(repoDir, d, iconName));

  index.apps.push({
    packageName,
    name: app.name,
    summary: app.summary,
    description: app.description,
    icon: iconName,
    license: app.license,
    categories: app.categories,
    sourceCode: app.sourceCode,
    webSite: app.webSite || app.sourceCode,
    suggestedVersionCode: String(versionCode),
    added: now,
    lastUpdated: now,
    localized: { 'en-US': { name: app.name, summary: app.summary, description: app.description } },
  });
  const pkg = {
    added: now,
    apkName,
    hash: sha256(apkBuf),
    hashType: 'sha256',
    minSdkVersion: minSdk,
    targetSdkVersion: targetSdk,
    packageName,
    sig,
    signer,
    size: statSync(apkPath).size,
    'uses-permission': permissions,
    versionCode,
    versionName,
  };
  if (nativecode.length) pkg.nativecode = nativecode;
  index.packages[packageName] = [pkg];
  console.log(`${app.name}: ${versionName} (${versionCode}), ${(pkg.size / 1048576).toFixed(1)} MB`);
}

// index-v1.json (read by the Fabians App Store app) and the signed index-v1.jar (read by F-Droid).
const json = JSON.stringify(index, null, 1);
writeFileSync(join(repoDir, 'index-v1.json'), json);
run(tool.jar, ['cf', 'index-v1.jar', 'index-v1.json'], { cwd: repoDir });
run(tool.jarsigner, ['-keystore', keystore, '-storepass', storepass, '-digestalg', 'SHA-256', '-sigalg', 'SHA256withRSA', 'index-v1.jar', alias], { cwd: repoDir });

// A stable download link for the store app itself.
const self = index.packages['nl.fabian42069.appstore']?.[0];
if (self) copyFileSync(join(repoDir, self.apkName), join(site, 'FabiansAppStore.apk'));

// The web page that explains how to get the store (and add it to F-Droid).
for (const f of readdirSync(join(root, 'web'))) copyFileSync(join(root, 'web', f), join(site, f));

const fingerprint = signer.toUpperCase();
writeFileSync(join(site, 'fingerprint.txt'), fingerprint + '\n');
console.log(`Repository fingerprint: ${fingerprint}`);
