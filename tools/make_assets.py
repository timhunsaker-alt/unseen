"""Generates the footprint block models, textures and the mod icon.

Run from the project root:  python3 tools/make_assets.py
Edit BOOT below to change the shape of the print (# = pressed down).
"""
import json, os, random
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "unseen")

# 16x16, north (the walking direction) is the top row. '#' = pressed into the snow.
# This is the LEFT boot, sitting left of center. The right boot is the mirror image.
BOOT = [
    "................",
    "................",
    "....###.........",
    "...#####........",
    "...#####........",
    "...######.......",
    "...######.......",
    "....#####.......",
    "....####........",
    "....####........",
    "................",
    "....####........",
    "....####........",
    "....####........",
    "................",
    "................",
]

def mask(left):
    rows = [list(r) for r in BOOT]
    if not left:
        rows = [r[::-1] for r in rows]
    return [[c == "#" for c in r] for r in rows]

def rectangles(cells):
    """Greedy merge of True cells into rectangles: (x0, z0, x1, z1), exclusive ends."""
    used = [[False] * 16 for _ in range(16)]
    out = []
    for z in range(16):
        for x in range(16):
            if not cells[z][x] or used[z][x]:
                continue
            x1 = x
            while x1 < 16 and cells[z][x1] and not used[z][x1]:
                x1 += 1
            z1 = z + 1
            while z1 < 16 and all(cells[z1][i] and not used[z1][i] for i in range(x, x1)):
                z1 += 1
            for zz in range(z, z1):
                for xx in range(x, x1):
                    used[zz][xx] = True
            out.append((x, z, x1, z1))
    return out

def face(tex, uv, cull=None):
    f = {"uv": uv, "texture": tex}
    if cull:
        f["cullface"] = cull
    return f

def model(left):
    cells = mask(left)
    raised = [[not c for c in row] for row in cells]
    elements = [{
        # The packed-down floor of the print, 1px thick, covering the whole block.
        "from": [0, 0, 0], "to": [16, 1, 16],
        "faces": {
            "down": face("#snow", [0, 0, 16, 16], "down"),
            "up": face("#floor", [0, 0, 16, 16]),
            "north": face("#snow", [0, 15, 16, 16], "north"),
            "south": face("#snow", [0, 15, 16, 16], "south"),
            "west": face("#snow", [0, 15, 16, 16], "west"),
            "east": face("#snow", [0, 15, 16, 16], "east"),
        },
    }]
    for (x0, z0, x1, z1) in rectangles(raised):
        faces = {"up": face("#snow", [x0, z0, x1, z1])}
        # Side faces: walls of the print where they border it, block edges otherwise.
        faces["north"] = face("#snow", [16 - x1, 14, 16 - x0, 15], "north" if z0 == 0 else None)
        faces["south"] = face("#snow", [x0, 14, x1, 15], "south" if z1 == 16 else None)
        faces["west"] = face("#snow", [z0, 14, z1, 15], "west" if x0 == 0 else None)
        faces["east"] = face("#snow", [16 - z1, 14, 16 - z0, 15], "east" if x1 == 16 else None)
        elements.append({"from": [x0, 1, z0], "to": [x1, 2, z1], "faces": faces})
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": True,
        "textures": {
            "particle": "minecraft:block/snow",
            "snow": "minecraft:block/snow",
            "floor": "unseen:block/footprint_floor",
        },
        "elements": elements,
    }

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")

def floor_texture():
    # Compacted snow at the bottom of a print: a touch bluer and darker, with soft noise.
    rnd = random.Random(7)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            n = rnd.randint(-10, 10)
            img.putpixel((x, y), (178 + n, 192 + n, 214 + n, 255))
    path = os.path.join(ROOT, "textures", "block", "footprint_floor.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)

def icon():
    size = 128
    img = Image.new("RGB", (size, size), (14, 16, 24))
    d = ImageDraw.Draw(img)
    # Snowy ground in the dark.
    d.rectangle([0, 76, size, size], fill=(150, 160, 182))
    # A trail of prints walking toward the viewer, then stopping.
    for i, (cx, cy, s) in enumerate([(70, 84, 5), (54, 94, 6), (74, 104, 7), (50, 116, 8)]):
        d.ellipse([cx - s, cy - s * 1.6, cx + s, cy + s * 0.4], fill=(70, 78, 100))
        d.ellipse([cx - s * 0.8, cy + s * 0.8, cx + s * 0.8, cy + s * 1.9], fill=(70, 78, 100))
    img = img.filter(ImageFilter.GaussianBlur(0.6))
    img.save(os.path.join(ROOT, "icon.png"))

if __name__ == "__main__":
    write_json(os.path.join(ROOT, "models", "block", "footprint_left.json"), model(True))
    write_json(os.path.join(ROOT, "models", "block", "footprint_right.json"), model(False))
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for left in (True, False):
            v = {"model": "unseen:block/footprint_" + ("left" if left else "right")}
            if y:
                v["y"] = y
            variants[f"facing={facing},left={'true' if left else 'false'}"] = v
    write_json(os.path.join(ROOT, "blockstates", "footprint.json"), {"variants": variants})
    write_json(os.path.join(ROOT, "lang", "en_us.json"), {"block.unseen.footprint": "Footprint"})
    floor_texture()
    icon()
    print("assets written")
