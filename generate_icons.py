#!/usr/bin/env python3
"""
Generates all Android app icon sizes from a source PNG.
Usage: python3 generate_icons.py [source_image.png]
Default source: icon_source.png in the same directory.
"""
import sys
import os
from pathlib import Path
from PIL import Image

BASE = Path(__file__).parent / "android/app/src/main/res"

# Standard launcher icon sizes per density bucket
SIZES = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}

# Play Store / Google requires a 512x512 for the listing
STORE_SIZE = 512

# Adaptive icon foreground canvas is 108dp with a 72dp safe zone in center.
# We render the foreground at 432x432 (108dp @ 4x) so it fits xxxhdpi.
ADAPTIVE_FG_SIZE = 432

def make_round(img: Image.Image) -> Image.Image:
    """Crop image to a circle with transparent background."""
    size = img.size
    mask = Image.new("L", size, 0)
    from PIL import ImageDraw
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size[0], size[1]), fill=255)
    result = img.copy().convert("RGBA")
    result.putalpha(mask)
    return result

def resize_high_quality(img: Image.Image, target: int) -> Image.Image:
    return img.resize((target, target), Image.LANCZOS)

def main():
    source_path = sys.argv[1] if len(sys.argv) > 1 else "icon_source.png"
    source = Path(source_path)
    if not source.exists():
        print(f"ERROR: Source image not found: {source}")
        print("Please save the icon image and pass its path as argument.")
        print("  python3 generate_icons.py /path/to/icon.png")
        sys.exit(1)

    print(f"Source: {source} ({source.stat().st_size // 1024} KB)")
    img = Image.open(source).convert("RGBA")
    print(f"Size: {img.size[0]}x{img.size[1]}")

    # ── Standard icons (square) ──────────────────────────────────────────────
    for folder, px in SIZES.items():
        dest_dir = BASE / folder
        dest_dir.mkdir(parents=True, exist_ok=True)

        resized = resize_high_quality(img, px)
        resized.save(dest_dir / "ic_launcher.png", optimize=True)

        round_img = make_round(resized)
        round_img.save(dest_dir / "ic_launcher_round.png", optimize=True)

        print(f"  {folder}: ic_launcher.png + ic_launcher_round.png ({px}px)")

    # ── Adaptive icon foreground (xxxhdpi, transparent background) ──────────
    fg_dir = BASE / "mipmap-xxxhdpi"
    fg_dir.mkdir(parents=True, exist_ok=True)

    # Adaptive foreground: 108dp canvas = 432px @ 4x density.
    # The safe zone is the inner 72dp = 288px. We scale the icon to fit 288px
    # and center it on a 432px transparent canvas so it doesn't get clipped.
    canvas_px = 432
    safe_px = 288
    fg_img = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    content = resize_high_quality(img, safe_px)
    offset = (canvas_px - safe_px) // 2
    fg_img.paste(content, (offset, offset), content)
    fg_img.save(fg_dir / "ic_launcher_foreground.png", optimize=True)
    print(f"  mipmap-xxxhdpi: ic_launcher_foreground.png ({canvas_px}px adaptive foreground)")

    # ── Play Store icon ──────────────────────────────────────────────────────
    store_dir = Path(__file__).parent / "android"
    store_img = resize_high_quality(img, STORE_SIZE)
    store_img.save(store_dir / "ic_launcher_playstore.png", optimize=True)
    print(f"  ic_launcher_playstore.png ({STORE_SIZE}px for Play Store listing)")

    # ── Notification small icon (white monochrome for status bar) ────────────
    # Android requires notification icons to be white-on-transparent.
    notif_dir = BASE / "drawable"
    notif_dir.mkdir(parents=True, exist_ok=True)
    notif_size = 96
    notif = resize_high_quality(img, notif_size).convert("RGBA")
    # Convert to white silhouette: make every non-transparent pixel white
    r, g, b, a = notif.split()
    white = Image.new("RGBA", notif.size, (255, 255, 255, 0))
    white.putalpha(a)
    white.save(notif_dir / "ic_notification.png", optimize=True)
    print(f"  drawable/ic_notification.png ({notif_size}px white monochrome for status bar)")

    print("\nDone! All icons generated.")
    print("Next: update AndroidManifest + MonitoringWorker to use @drawable/ic_notification")

if __name__ == "__main__":
    main()
