#!/usr/bin/env python
"""Generate the app's icons from one geometry, so they never drift apart.

    python tools/gen_icons.py              (writes android/app/src/main/res/drawable/)
    python tools/gen_icons.py --svg DIR    (also writes preview SVGs to DIR)

Writes ic_launcher_foreground / _background / _monochrome, ic_notification and the
shortcut icons (mipmap ic_shortcut_lines / _place). The canvas
is the 108x108 adaptive-icon grid; everything stays inside the middle 66 (the safe zone),
so no launcher mask (circle, squircle, teardrop) clips the bus.
"""
import pathlib
import sys

def rrect(x0, y0, x1, y1, r, ccw=False):
    # Rounded rectangle as a closed path (clockwise, or counter-clockwise for a hole).
    if not ccw:
        return (f"M{x0+r},{y0}H{x1-r}A{r},{r} 0 0 1 {x1},{y0+r}V{y1-r}A{r},{r} 0 0 1 {x1-r},{y1}"
                f"H{x0+r}A{r},{r} 0 0 1 {x0},{y1-r}V{y0+r}A{r},{r} 0 0 1 {x0+r},{y0}Z")
    return (f"M{x0+r},{y0}A{r},{r} 0 0 0 {x0},{y0+r}V{y1-r}A{r},{r} 0 0 0 {x0+r},{y1}"
            f"H{x1-r}A{r},{r} 0 0 0 {x1},{y1-r}V{y0+r}A{r},{r} 0 0 0 {x1-r},{y0}Z")

def circle(cx, cy, r, ccw=False):
    sweep = 0 if ccw else 1
    return f"M{cx-r},{cy}A{r},{r} 0 1 {sweep} {cx+r},{cy}A{r},{r} 0 1 {sweep} {cx-r},{cy}Z"

BODY = rrect(33, 24, 75, 70, 9)
MIRRORS = rrect(28.5, 37, 31.5, 47, 1.5) + rrect(76.5, 37, 79.5, 47, 1.5)
WHEELS = rrect(37, 66, 46, 76, 2.5) + rrect(62, 66, 71, 76, 2.5)
SIGN = rrect(42, 28.5, 66, 33.5, 2)
SCREEN = rrect(37.5, 37, 70.5, 55, 4)
LIGHTS = [(43, 62.5, 3.4), (65, 62.5, 3.4)]
LINE = rrect(28, 80, 80, 85, 2.5)  # the route line under the bus, round caps

WHITE, AMBER, DEEP = "#FFFFFF", "#FFB300", "#0D47A1"

def colour_paths():
    return [
        (WHEELS, WHITE), (MIRRORS, WHITE), (BODY, WHITE),
        (SIGN, AMBER), (SCREEN, DEEP),
        ("".join(circle(*l) for l in LIGHTS), AMBER),
        (LINE, WHITE),
    ]

def mono_path():
    # One silhouette; the sign, windscreen and headlights are holes (evenOdd), so a
    # themed icon keeps the bus's face instead of becoming a white block.
    holes = rrect(42, 28.5, 66, 33.5, 2, ccw=True) + rrect(37.5, 37, 70.5, 55, 4, ccw=True) + \
        "".join(circle(*l, ccw=True) for l in LIGHTS)
    return BODY + holes, WHEELS + MIRRORS + LINE

HEADER = '<?xml version="1.0" encoding="utf-8"?>\n'

def vector(body, comment, w=108, h=108, vw=108, vh=108, group=None, tint=None):
    attrs = f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{w}dp"
    android:height="{h}dp"
    android:viewportWidth="{vw}"
    android:viewportHeight="{vh}"''' + (f'\n    android:tint="{tint}"' if tint else '') + '>\n'
    inner = body if not group else f'    <group android:translateX="{group[0]}" android:translateY="{group[1]}">\n' + \
        "".join("    " + l + "\n" for l in body.rstrip("\n").split("\n")) + '    </group>\n'
    return HEADER + f"<!-- {comment} -->\n" + attrs + inner + "</vector>\n"

def path(d, color, evenodd=False):
    fill = ' android:fillType="evenOdd"' if evenodd else ''
    return f'    <path android:fillColor="{color}"{fill} android:pathData="{d}" />\n'

ROOT = pathlib.Path(__file__).resolve().parent.parent
out = ROOT / "android/app/src/main/res/drawable"
svg_dir = pathlib.Path(sys.argv[sys.argv.index("--svg") + 1]) if "--svg" in sys.argv else None
fg = "".join(path(d, c) for d, c in colour_paths())
open(f"{out}/ic_launcher_foreground.xml", "w").write(vector(fg,
    "A bold front view of a bus over a route line, inside the 66dp safe zone of the 108dp\n"
    "     adaptive-icon canvas, so every launcher mask (circle, squircle, teardrop) keeps it whole.\n"
    "     Generated with the monochrome and notification icons from one geometry: keep them in step."))

face, rest = mono_path()
mono = path(face, "#FFFFFFFF", evenodd=True) + path(rest, "#FFFFFFFF")
open(f"{out}/ic_launcher_monochrome.xml", "w").write(vector(mono,
    "Themed-icon layer (Android 13+): the same bus as one silhouette with the windscreen,\n"
    "     sign and headlights cut out, so the system's single tint still shows a face."))

# Notification: the silhouette cropped to its bounds (x 27-81, y 23-86) on a 24dp icon.
open(f"{out}/ic_notification.xml", "w").write(vector(mono,
    "Status-bar icon: the launcher bus as a white silhouette (Android tints it).",
    w=24, h=24, vw=63, vh=63, group=(-22.5, -23.5)))

bg = '''    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108">
                <item android:color="#FF2196F3" android:offset="0" />
                <item android:color="#FF1565C0" android:offset="1" />
            </gradient>
        </aapt:attr>
    </path>
'''
bgx = vector(bg, "Transit blue, lit from the top corner.").replace(
    'xmlns:android="http://schemas.android.com/apk/res/android"',
    'xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:aapt="http://schemas.android.com/aapt"')
open(f"{out}/ic_launcher_background.xml", "w").write(bgx)

# App-icon shortcuts (long-press the launcher icon): the app's own symbols, so each shortcut
# is told apart at a glance: ♥ My lines (as in the ⋮ menu), ★ a saved place (as on its chip).
# Material glyphs (Apache 2.0), 24-unit paths scaled to 46 on the 108 canvas.
HEART = ("M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09"
         "C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z")
STAR = "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z"
SHORTCUT_BG = "#FFD1E4FF"  # LightColors.primaryContainer (ui/Theme.kt)

def glyph(d, color, comment):
    k = 46 / 24
    body = (f'    <group android:translateX="{(108 - 46) / 2}" android:translateY="{(108 - 46) / 2}"\n'
            f'        android:scaleX="{k:.4f}" android:scaleY="{k:.4f}">\n'
            f'        <path android:fillColor="{color}" android:pathData="{d}" />\n'
            f'    </group>\n')
    return vector(body, comment)

open(f"{out}/ic_shortcut_lines_fg.xml", "w").write(glyph(HEART, "#FF0061A4", "Shortcut glyph: My lines (♥, primary blue)."))
open(f"{out}/ic_shortcut_place_fg.xml", "w").write(glyph(STAR, "#FF7C5800", "Shortcut glyph: a saved place (★, tertiary amber)."))
open(f"{out}/ic_shortcut_bg.xml", "w").write(vector(
    f'    <path android:fillColor="{SHORTCUT_BG}" android:pathData="M0,0h108v108h-108z" />\n',
    "Shortcut background: the light primary container; the launcher masks it to its shape."))
mipmap = out.parent / "mipmap-anydpi-v26"
for name in ("lines", "place"):
    (mipmap / f"ic_shortcut_{name}.xml").write_text(
        HEADER + '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@drawable/ic_shortcut_bg" />\n'
        f'    <foreground android:drawable="@drawable/ic_shortcut_{name}_fg" />\n'
        '</adaptive-icon>\n')

if svg_dir:
    svg_dir.mkdir(parents=True, exist_ok=True)
    head = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">'
    grad = ('<defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">'
            '<stop offset="0" stop-color="#2196F3"/><stop offset="1" stop-color="#1565C0"/></linearGradient></defs>'
            '<rect width="108" height="108" fill="url(#bg)"/>')
    fg_svg = "".join(f'<path fill="{c}" d="{d}"/>' for d, c in colour_paths())
    mono_svg = f'<path fill="currentColor" fill-rule="evenodd" d="{face}"/><path fill="currentColor" d="{rest}"/>'
    (svg_dir / "icon.svg").write_text(head + grad + fg_svg + "</svg>")
    (svg_dir / "icon_mono.svg").write_text(head + mono_svg + "</svg>")
print("icons written to", out)
