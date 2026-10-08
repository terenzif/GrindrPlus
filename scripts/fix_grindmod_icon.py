"""Build GrindMod launcher assets: punched mask, yellow plate, thick-border gear."""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "brand"
PLAYSTORE = ROOT / "app" / "src" / "main" / "ic_launcher-playstore.png"
YELLOW = (253, 194, 1, 255)  # #FDC201
BLACK = (17, 17, 17, 255)

DENSITY_PX = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
BRAND_SIZES = {
    "ic_launcher_48.png": 48,
    "ic_launcher_72.png": 72,
    "ic_launcher_96.png": 96,
    "ic_launcher_144.png": 144,
    "ic_launcher_192.png": 192,
}


def is_bg(c: tuple[int, int, int, int]) -> bool:
    r, g, b, a = c
    if a < 28:
        return True
    if r > 185 and g > 175 and b > 155 and abs(r - g) < 55 and abs(g - b) < 60:
        return True
    if min(r, g, b) > 220 and max(r, g, b) - min(r, g, b) < 25:
        return True
    return False


def flood_punch(im: Image.Image) -> Image.Image:
    im = im.convert("RGBA")
    w, h = im.size
    px = im.load()
    assert px is not None
    seen: set[tuple[int, int]] = set()
    stack = [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]
    step = max(1, w // 12)
    for x in range(0, w, step):
        stack.extend([(x, 0), (x, h - 1)])
    for y in range(0, h, step):
        stack.extend([(0, y), (w - 1, y)])
    while stack:
        x, y = stack.pop()
        if (x, y) in seen or x < 0 or y < 0 or x >= w or y >= h:
            continue
        seen.add((x, y))
        if not is_bg(px[x, y]):
            continue
        px[x, y] = (0, 0, 0, 0)
        stack.extend([(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)])
    for y in range(h):
        for x in range(w):
            if is_bg(px[x, y]):
                px[x, y] = (0, 0, 0, 0)
    return im


def punch_mask(src: Path) -> Image.Image:
    """Punch cream; keep natural art scale (no extra inset here)."""
    im = flood_punch(Image.open(src))
    bbox = im.getbbox()
    if not bbox:
        print(f"skip empty {src}")
        return im
    # tight crop then pad to original square centered
    cropped = im.crop(bbox)
    w, h = im.size
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    # Fit content to ~72% of canvas (adaptive safe zone leaves margin)
    scale = 0.72
    ratio = min((w * scale) / cropped.size[0], (h * scale) / cropped.size[1])
    tw = max(1, int(cropped.size[0] * ratio))
    th = max(1, int(cropped.size[1] * ratio))
    resized = cropped.resize((tw, th), Image.Resampling.LANCZOS)
    canvas.paste(resized, ((w - tw) // 2, (h - th) // 2), resized)
    canvas.save(src)
    print(f"updated {src.name}@{src.parent.name} content {tw}x{th}")
    return canvas


def draw_gear(size: int) -> Image.Image:
    """Yellow gear with thick black outline (stroke-based)."""
    # draw at 4x then downscale for clean AA
    s = size * 4
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    cx = cy = s / 2
    teeth = 8
    outer_r = s * 0.42
    inner_r = s * 0.26
    hub_r = s * 0.12
    hole_r = s * 0.055
    stroke = max(8, int(s * 0.08))

    # Build gear polygon (outer teeth)
    pts_outer: list[tuple[float, float]] = []
    for i in range(teeth * 2):
        ang = (i / (teeth * 2)) * math.tau - math.pi / (teeth * 2)
        r = outer_r if i % 2 == 0 else inner_r
        # widen teeth: for even indices use a small arc span via two points
        pts_outer.append((cx + math.cos(ang) * r, cy + math.sin(ang) * r))

    # Black outline (larger), then yellow fill
    def gear_path(scale: float) -> list[tuple[float, float]]:
        out = []
        for i in range(teeth):
            a0 = (i / teeth) * math.tau
            a1 = ((i + 0.45) / teeth) * math.tau
            a2 = ((i + 0.55) / teeth) * math.tau
            a3 = ((i + 1.0) / teeth) * math.tau
            for a, r in (
                (a0, inner_r * scale),
                (a1, outer_r * scale),
                (a2, outer_r * scale),
                (a3, inner_r * scale),
            ):
                out.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
        return out

    draw.polygon(gear_path(1.0), fill=BLACK)
    # yellow inset via shrink
    shrink = 1.0 - (stroke / outer_r)
    draw.polygon(gear_path(shrink), fill=YELLOW)
    # hub
    draw.ellipse([cx - hub_r, cy - hub_r, cx + hub_r, cy + hub_r], fill=BLACK)
    draw.ellipse(
        [cx - hub_r + stroke * 0.55, cy - hub_r + stroke * 0.55, cx + hub_r - stroke * 0.55, cy + hub_r - stroke * 0.55],
        fill=YELLOW,
    )
    draw.ellipse([cx - hole_r, cy - hole_r, cx + hole_r, cy + hole_r], fill=BLACK)
    out = img.resize((size, size), Image.Resampling.LANCZOS)
    return out


def compose_launcher(size: int, mask: Image.Image, gear: Image.Image) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), YELLOW)
    # mask occupies ~55% (safe inside round adaptive clip)
    msize = int(size * 0.55)
    m = mask.resize((msize, msize), Image.Resampling.LANCZOS)
    # slight left bias so gear has room
    mx = int(size * 0.18)
    my = (size - msize) // 2
    canvas.alpha_composite(m, (mx, my))
    gsize = max(14, int(size * 0.34))
    g = gear.resize((gsize, gsize), Image.Resampling.LANCZOS)
    margin = max(3, int(size * 0.12))
    canvas.alpha_composite(g, (size - gsize - margin, size - gsize - margin))
    return canvas


def write_mipmaps(mask: Image.Image) -> None:
    gear = draw_gear(256)
    for dens, px in DENSITY_PX.items():
        folder = RES / f"mipmap-{dens}"
        folder.mkdir(parents=True, exist_ok=True)
        icon = compose_launcher(px, mask, gear)
        for name in ("ic_launcher", "ic_launcher_round"):
            for ext in (".webp", ".png"):
                p = folder / f"{name}{ext}"
                if p.exists():
                    p.unlink()
            icon.save(folder / f"{name}.png")
            print(f"wrote {folder.name}/{name}.png")


def write_gear_drawable(gear: Image.Image) -> None:
    """Raster gear badge so launcher doesn't depend on vector fill quirks."""
    for dens, px in DENSITY_PX.items():
        folder = RES / f"drawable-{dens}"
        folder.mkdir(parents=True, exist_ok=True)
        g = gear.resize((max(24, px // 3), max(24, px // 3)), Image.Resampling.LANCZOS)
        g.save(folder / "ic_gear_badge.png")
    # keep xml as fallback name conflict: remove xml if png densities exist
    xml = RES / "drawable" / "ic_gear_badge.xml"
    if xml.exists():
        xml.unlink()
        print("removed drawable/ic_gear_badge.xml (using density PNGs)")


def export_brand(mask: Image.Image, gear: Image.Image) -> None:
    ASSETS.mkdir(parents=True, exist_ok=True)
    for name, size in BRAND_SIZES.items():
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        msize = int(size * 0.62)
        m = mask.resize((msize, msize), Image.Resampling.LANCZOS)
        canvas.alpha_composite(m, (int(size * 0.12), (size - msize) // 2))
        gsize = max(12, int(size * 0.36))
        g = gear.resize((gsize, gsize), Image.Resampling.LANCZOS)
        margin = max(2, size // 14)
        canvas.alpha_composite(g, (size - gsize - margin, size - gsize - margin))
        canvas.save(ASSETS / name)
        print(f"exported assets/brand/{name}")
    compose_launcher(512, mask, gear).save(PLAYSTORE)
    print(f"exported {PLAYSTORE.relative_to(ROOT)}")


def main() -> None:
    # Prefer original cream playstore if present in git; else current
    cream_path = ROOT / "tmp-icon-apk" / "assets_brand_ic_launcher_144.png"
    if not cream_path.exists():
        # extract from git
        import subprocess

        cream_path.parent.mkdir(parents=True, exist_ok=True)
        with open(cream_path, "wb") as f:
            raw = subprocess.check_output(
                ["git", "show", "HEAD:app/src/main/assets/brand/ic_launcher_144.png"],
                cwd=ROOT,
            )
            f.write(raw)
    cream = Image.open(cream_path).convert("RGBA")
    # if already punched (little cream), fall back to playstore git
    creamish = sum(1 for r, g, b, a in cream.getdata() if a > 20 and r > 185 and g > 175 and b > 155)
    if creamish < 1000:
        with open(cream_path, "wb") as f:
            import subprocess

            f.write(
                subprocess.check_output(
                    ["git", "show", "HEAD:app/src/main/ic_launcher-playstore.png"],
                    cwd=ROOT,
                )
            )
        cream = Image.open(cream_path).convert("RGBA")

    for dens, px in DENSITY_PX.items():
        out = RES / f"drawable-{dens}" / "ic_brand_mask.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        cream.resize((px, px), Image.Resampling.LANCZOS).save(out)

    master: Image.Image | None = None
    for dens in DENSITY_PX:
        p = RES / f"drawable-{dens}" / "ic_brand_mask.png"
        im = punch_mask(p)
        if dens == "xxhdpi":
            master = im
    assert master is not None
    gear = draw_gear(256)
    write_gear_drawable(gear)
    write_mipmaps(master)
    export_brand(master, gear)


if __name__ == "__main__":
    main()
