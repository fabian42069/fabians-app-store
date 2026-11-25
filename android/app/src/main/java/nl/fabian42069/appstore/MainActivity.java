package nl.fabian42069.appstore;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Fabians App Store: shows the apps from the store's repository (an F-Droid
 * compatible index) and installs or updates them with Android's own installer.
 * The screen itself is a small web page in assets/; this class gives it the
 * few things a web page can't do: download, install, check versions, open apps.
 */
public class MainActivity extends Activity {
    private static MainActivity current;
    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = this;
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        web.setBackgroundColor(0xFF24273A);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                // Links (e.g. source code) open in the normal browser.
                startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Store");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    @Override
    protected void onResume() {
        super.onResume();
        js("window.onResume && window.onResume()");
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // The page closes its pop-up first; otherwise leave the app.
        web.evaluateJavascript("window.onBack ? window.onBack() : false", (handled) -> {
            if (!"true".equals(handled)) MainActivity.super.onBackPressed();
        });
    }

    private void js(String script) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(script, null); });
    }

    private static String q(String s) { return JSONObject.quote(s == null ? "" : s); }

    /** Called by InstallResultReceiver when Android reports how an install went. */
    static void installFinished(String pkg, boolean ok, String message) {
        MainActivity a = current;
        if (a != null) a.js("window.onInstallDone(" + q(pkg) + "," + ok + "," + q(message) + ")");
    }

    private long installedVersion(String pkg) {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(pkg, 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    private class Bridge {
        @JavascriptInterface
        public String selfPackage() { return getPackageName(); }

        /** The store's own version name, shown on the About page. */
        @JavascriptInterface
        public String selfVersion() {
            try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
            catch (PackageManager.NameNotFoundException e) { return ""; }
        }

        /** Downloads a text file (the store index) and hands it to the page. */
        @JavascriptInterface
        public void fetch(String url, String callbackId) {
            new Thread(() -> {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    c.setUseCaches(false);
                    if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    try (InputStream in = c.getInputStream()) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    js("window.onFetched(" + q(callbackId) + ",true," + q(out.toString("UTF-8")) + ")");
                } catch (Exception e) {
                    js("window.onFetched(" + q(callbackId) + ",false," + q(String.valueOf(e.getMessage())) + ")");
                }
            }).start();
        }

        /** {"package.name": installedVersionCode or -1, ...} for the given list of packages. */
        @JavascriptInterface
        public String installedVersions(String packagesJson) {
            JSONObject result = new JSONObject();
            try {
                JSONArray list = new JSONArray(packagesJson);
                for (int i = 0; i < list.length(); i++) result.put(list.getString(i), installedVersion(list.getString(i)));
            } catch (Exception ignored) { }
            return result.toString();
        }

        @JavascriptInterface
        public boolean canInstall() {
            return getPackageManager().canRequestPackageInstalls();
        }

        /** Opens the Android setting that lets this store install apps. */
        @JavascriptInterface
        public void openInstallSettings() {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
        }

        @JavascriptInterface
        public boolean open(String pkg) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) return false;
            startActivity(launch);
            return true;
        }

        /** Downloads the APK straight into an Android install session, checks it, and installs it. */
        @JavascriptInterface
        public void install(String url, String sha256, String pkg) {
            new Thread(() -> {
                PackageInstaller installer = getPackageManager().getPackageInstaller();
                PackageInstaller.Session session = null;
                try {
                    PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    params.setAppPackageName(pkg);
                    if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                    int id = installer.createSession(params);
                    session = installer.openSession(id);

                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(60000);
                    if (c.getResponseCode() != 200) throw new Exception("Download failed (HTTP " + c.getResponseCode() + ")");
                    long total = c.getContentLengthLong();
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    long done = 0;
                    int lastPct = -1;
                    try (InputStream in = c.getInputStream(); OutputStream out = session.openWrite("base.apk", 0, total > 0 ? total : -1)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            digest.update(buf, 0, n);
                            done += n;
                            int pct = total > 0 ? (int) (done * 100 / total) : -1;
                            if (pct != lastPct) { lastPct = pct; js("window.onInstallProgress(" + q(pkg) + "," + pct + ")"); }
                        }
                        session.fsync(out);
                    }
                    StringBuilder hex = new StringBuilder();
                    for (byte b : digest.digest()) hex.append(String.format("%02x", b));
                    if (sha256 != null && !sha256.isEmpty() && !hex.toString().equalsIgnoreCase(sha256)) {
                        throw new Exception("The download was damaged. Please try again.");
                    }

                    js("window.onInstallProgress(" + q(pkg) + ",101)"); // installing
                    Intent result = new Intent(MainActivity.this, InstallResultReceiver.class).putExtra("pkg", pkg);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    PendingIntent pi = PendingIntent.getBroadcast(MainActivity.this, id, result, flags);
                    session.commit(pi.getIntentSender());
                    session.close();
                } catch (Exception e) {
                    if (session != null) { try { session.abandon(); } catch (Exception ignored) { } }
                    installFinished(pkg, false, e.getMessage());
                }
            }).start();
        }
    }
}
