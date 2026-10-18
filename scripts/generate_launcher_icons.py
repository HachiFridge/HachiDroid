#!/usr/bin/env python3
"""Regenerate Android launcher icon resources from the HachiDroid asset PNGs.

Usage:
    python3 scripts/generate_launcher_icons.py [assets_dir]

Default assets_dir: ~/Desktop/HachiFridge/Assets/HachiDroid

Source assets and what they become:
    AppIcon.png        -> mipmap-*/ic_launcher.webp          (legacy square icon, API < 26)
    CircledAppIcon.png -> mipmap-*/ic_launcher_round.webp    (legacy round icon, API < 26)
    ResizedIcon.png    -> drawable-nodpi/ic_launcher_foreground.webp (glyph, also used in-app)
                       -> drawable-nodpi/ic_launcher_monochrome.webp (Android 13 themed icon)
    Background.png     -> drawable-nodpi/ic_launcher_background.webp (adaptive icon background)

Adaptive icon geometry (see drawable/ic_launcher_foreground_layer.xml):
    The adaptive-icon layer is 108dp; the launcher mask reveals at most a 72dp
    circle/squircle and the safe zone is the centred 66dp circle. The foreground
    and monochrome art is therefore scaled by (108 - 2 * INSET_DP) / 108 so the
    glyph's max radius lands at ~32dp, matching the legacy circled icon.
    Keep INSET_DP in sync with drawable/ic_launcher_foreground_layer.xml.
"""

import sys
from pathlib import Path

from PIL import Image

REPO_ROOT = Path(__file__).resolve().parent.parent
RES = REPO_ROOT / "app" / "src" / "main" / "res"

# Icon canvas in dp; the launcher mask reveals a 72dp region, so the adaptive
# foreground/monochrome art is inset by this much on each side.
LAYER_DP = 108
INSET_DP = 18
FG_SCALE = (LAYER_DP - 2 * INSET_DP) / LAYER_DP  # 0.6667

DENSITIES = {  # launcher icon size in px per density bucket
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
LAYER_PX = 512  # canvas for full-bleed layer bitmaps (background / monochrome)

WEBP_OPTS = dict(method=6, quality=95, alpha_quality=100)


def save_webp(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "WEBP", **WEBP_OPTS)
    print(f"  {path.relative_to(REPO_ROOT)}  {img.size[0]}x{img.size[1]}")


def scale_centered(img: Image.Image, scale: float) -> Image.Image:
    """Scale RGBA image about its centre onto the same-size transparent canvas."""
    w, h = img.size
    nw, nh = max(1, round(w * scale)), max(1, round(h * scale))
    scaled = img.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    canvas.paste(scaled, ((w - nw) // 2, (h - nh) // 2), scaled)
    return canvas


def main() -> int:
    assets = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.home() / "Desktop/HachiFridge/Assets/HachiDroid"
    required = ["AppIcon.png", "CircledAppIcon.png", "ResizedIcon.png", "Background.png"]
    missing = [f for f in required if not (assets / f).is_file()]
    if missing:
        print(f"error: missing assets in {assets}: {', '.join(missing)}", file=sys.stderr)
        return 1

    app_icon = Image.open(assets / "AppIcon.png").convert("RGBA")
    circled = Image.open(assets / "CircledAppIcon.png").convert("RGBA")
    glyph = Image.open(assets / "ResizedIcon.png").convert("RGBA")
    background = Image.open(assets / "Background.png").convert("RGBA")

    print("legacy mipmaps (API < 26):")
    for bucket, size in DENSITIES.items():
        save_webp(app_icon.resize((size, size), Image.LANCZOS), RES / f"mipmap-{bucket}/ic_launcher.webp")
        save_webp(circled.resize((size, size), Image.LANCZOS), RES / f"mipmap-{bucket}/ic_launcher_round.webp")

    print("adaptive icon layers (API >= 26):")
    save_webp(glyph.resize((LAYER_PX, LAYER_PX), Image.LANCZOS), RES / "drawable-nodpi/ic_launcher_foreground.webp")
    save_webp(background.resize((LAYER_PX, LAYER_PX), Image.LANCZOS), RES / "drawable-nodpi/ic_launcher_background.webp")

    # Themed-icon silhouette: solid white, glyph alpha, scaled into the safe zone.
    silhouette = Image.new("RGBA", glyph.size, (255, 255, 255, 255))
    silhouette.putalpha(glyph.getchannel("A"))
    save_webp(scale_centered(silhouette, FG_SCALE), RES / "drawable-nodpi/ic_launcher_monochrome.webp")

    print(f"done (foreground/monochrome safe-zone scale = {FG_SCALE:.4f}, inset = {INSET_DP}dp)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
