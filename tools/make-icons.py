#!/usr/bin/env python3
"""The logo recipe for all of Fabian's apps: one drawing per app, every logo file made from it.

    python3 tools/make-icons.py          (needs rsvg-convert and ImageMagick's `magick`)

Recipe (keep it the same for every app):
  * Arcticons-style line drawing, colour mauve #c6a0f6, line 1.5dp, round ends, no fill,
    transparent background. Drawn on a 48x48 grid (like Arcticons).
  * Android launcher: ONLY the drawing, no frame, no <monochrome> layer (the launcher would
    make it white). Its longest side is LAUNCHER_DP of the 108dp canvas, so it is as big as
    the Arcticons icons but stays inside the circle a launcher may cut icons to.
  * Inside apps (store tiles, the store's own logo, web favicon, app header): the drawing
    inside a rounded mauve frame (the same frame for every app).

Writes into the sibling repos ~/claude/fabians-app-store, ~/claude/Weegdagboek and
~/claude/fairtaler-translate, at the paths listed in OUTPUTS below.
"""
import os, re, subprocess, tempfile, urllib.parse

HOME = os.path.expanduser('~/claude')
MAUVE = '#c6a0f6'
LINE_DP = 1.5          # line width in dp on the launcher icon
LAUNCHER_DP = 49.0     # longest side of the drawing on the 108dp launcher canvas
FRAME = 'M14.5,7.5H33.5A7,7 0 0,1 40.5,14.5V33.5A7,7 0 0,1 33.5,40.5H14.5A7,7 0 0,1 7.5,33.5V14.5A7,7 0 0,1 14.5,7.5Z'
FRAME_DP = 62.0        # the frame's size on a 108dp canvas (framed versions only)

# A drawing is a list of items: a path string, or ('g', tx, ty, scale, [items]) for a moved/scaled group.
# 'box' is the drawing's bounding box on the 48 grid (x0, y0, x1, y1), used to centre and size it.
# 'inframe' places the drawing inside the frame: (tx, ty, scale) applied to the drawing.
# 'launcher_dp' (optional) overrides LAUNCHER_DP for drawings that reach into the corners.
APPS = {
    'store': dict(
        drawing=['M14.5 13H33.5L36 18.5H12Z',                                                  # awning
                 'M12 18.5A2 2 0 0 0 16 18.5A2 2 0 0 0 20 18.5A2 2 0 0 0 24 18.5A2 2 0 0 0 28 18.5A2 2 0 0 0 32 18.5A2 2 0 0 0 36 18.5',
                 'M13.5 21V35.5H34.5V21',                                                     # walls
                 ('g', 16.9, 24.1, 1, ['M0 0H8Q7.4 5.8 1.4 8.6',                               # フ
                                       'M9.6 3.6H14.2Q13.9 5 12.5 5.8', 'M12 4.7Q12.1 7.2 10 8.6'])],  # small ァ
        box=(12, 13, 36, 35.5), inframe=(0, 0, 1)),
    'fairtaler': dict(
        drawing=['M9 9H29Q27.4 13.6 22.4 17.2', 'M17.6 12.6Q18.2 23.2 10 28.4',               # ア
                 'M20.5 41L31 18.5L41.5 41', 'M24.5 32.5H37.5'],                               # A
        box=(9, 9, 41.5, 41), inframe=(24 - 25.25 * 0.68, 24 - 25 * 0.68, 0.68),
        launcher_dp=45.0),   # smaller: ア and A reach into the corners, which a round launcher cuts off
    'weegdagboek': dict(
        drawing=['M15.5,23.5A8.5,8.5 0 0,1 32.5,23.5M15.5,23.5H32.5',                         # dial
                 'M24,23.5L27.2,17.9',                                                        # needle
                 'M19.8,28.5V34.5M22.8,30.6L19.8,33.2M21.2,32L23,34.5',                        # k
                 'M25.3,32.1A1.6,1.6 0 1,0 28.5,32.1A1.6,1.6 0 1,0 25.3,32.1ZM28.5,30.5V34.9A1.7,1.7 0 0,1 26.8,36.6H25.5'],  # g
        box=(15.5, 15, 32.5, 36.6), inframe=(0, 0, 1)),
}

def placed(app, framed):
    """(items, tx, ty, scale) on the 108 canvas."""
    a = APPS[app]
    if framed:
        s = FRAME_DP / 33.0                      # the frame is 33 units wide
        tx, ty, k = a['inframe']
        return [FRAME, ('g', tx, ty, k, a['drawing'])], 54 - 24 * s, 54 - 24 * s, s
    x0, y0, x1, y1 = a['box']
    s = a.get('launcher_dp', LAUNCHER_DP) / max(x1 - x0, y1 - y0)
    return a['drawing'], 54 - (x0 + x1) / 2 * s, 54 - (y0 + y1) / 2 * s, s

def to_svg_items(items, scale):
    out = ''
    for it in items:
        if isinstance(it, str):
            out += f'<path d="{it}" fill="none" stroke="{MAUVE}" stroke-width="{LINE_DP / scale:.3f}" stroke-linecap="round" stroke-linejoin="round"/>'
        else:
            _, tx, ty, k, kids = it
            out += f'<g transform="translate({tx:.3f} {ty:.3f}) scale({k})">{to_svg_items(kids, scale * k)}</g>'
    return out

def svg(app, framed, viewbox):
    items, tx, ty, s = placed(app, framed)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{viewbox}">'
            f'<g transform="translate({tx:.3f} {ty:.3f}) scale({s:.4f})">{to_svg_items(items, s)}</g></svg>')

def to_vd_items(items, scale, ind):
    out = ''
    for it in items:
        if isinstance(it, str):
            out += (f'\n{ind}<path android:pathData="{it}" android:strokeColor="#FF{MAUVE[1:].upper()}" '
                    f'android:strokeWidth="{LINE_DP / scale:.3f}" android:strokeLineCap="round" '
                    f'android:strokeLineJoin="round" android:fillColor="#00000000"/>')
        else:
            _, tx, ty, k, kids = it
            out += (f'\n{ind}<group android:translateX="{tx:.3f}" android:translateY="{ty:.3f}" '
                    f'android:scaleX="{k}" android:scaleY="{k}">{to_vd_items(kids, scale * k, ind + "    ")}\n{ind}</group>')
    return out

def vector(app):
    items, tx, ty, s = placed(app, framed=False)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Made by fabians-app-store/tools/make-icons.py: edit the drawing there, not here. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <group android:translateX="{tx:.3f}" android:translateY="{ty:.3f}" android:scaleX="{s:.4f}" android:scaleY="{s:.4f}">{to_vd_items(items, s, "        ")}
    </group>
</vector>
'''

VISIBLE = '18 18 72 72'      # the part of a launcher icon that is shown
TIGHT = '21 21 66 66'        # framed logo with only a small margin around the frame

def png(app, framed, viewbox, path, size):
    with tempfile.NamedTemporaryFile('w', suffix='.svg', delete=False) as f:
        f.write(svg(app, framed, viewbox))
    subprocess.run(['rsvg-convert', '-w', str(size), '-h', str(size), '-o', path, f.name], check=True)
    os.unlink(f.name)

def write(path, text):
    open(path, 'w').write(text)

DENSITIES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}

def main():
    st, ft, wg = f'{HOME}/fabians-app-store', f'{HOME}/fairtaler-translate', f'{HOME}/Weegdagboek'
    # Android launcher icons (no frame)
    write(f'{st}/android/app/src/main/res/drawable/ic_launcher_fg.xml', vector('store'))
    write(f'{wg}/android/app/src/main/res/drawable/ic_launcher_fg.xml', vector('weegdagboek'))
    for d in ('drawable', 'drawable-v24'):
        write(f'{ft}/mobile/android/app/src/main/res/{d}/ic_launcher_foreground.xml', vector('fairtaler'))
    for dens, px in DENSITIES.items():   # old Android versions (before 8.0) use these PNGs
        png('store', False, VISIBLE, f'{st}/android/app/src/main/res/mipmap-{dens}/ic_launcher.png', px)
        for name in ('ic_launcher', 'ic_launcher_round'):
            png('fairtaler', False, VISIBLE, f'{ft}/mobile/android/app/src/main/res/mipmap-{dens}/{name}.png', px)
    # Inside the store (with frame)
    for app in APPS:
        png(app, True, VISIBLE, f'{st}/icons/{app}.png', 512)
    png('store', True, TIGHT, f'{st}/android/app/src/main/assets/store-icon.png', 512)
    # Fairtaler: desktop app icon, web favicon and the logo in the app's header (with frame)
    png('fairtaler', True, TIGHT, f'{ft}/desktop/icon.png', 512)
    page = open(f'{ft}/index.html').read()
    fav = 'data:image/svg+xml,' + urllib.parse.quote(svg('fairtaler', True, TIGHT), safe=" /=:'")
    page = re.sub(r'<link rel="icon" href="[^"]*" />', f'<link rel="icon" href="{fav}" />', page, count=1)
    page = re.sub(r'(<span class="brand-mark" aria-hidden="true">).*?(</span>)',
                  lambda m: m.group(1) + svg('fairtaler', True, TIGHT).replace('<svg ', '<svg width="32" height="32" ', 1) + m.group(2),
                  page, count=1, flags=re.S)
    write(f'{ft}/index.html', page)
    print('Icons written.')

if __name__ == '__main__':
    main()
