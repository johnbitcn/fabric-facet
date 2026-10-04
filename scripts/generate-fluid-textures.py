#!/usr/bin/env python3
"""Generate static, antialiased UK road-sign-style straight-ahead fluid arrows."""
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src/shared/resources/assets/facet/textures/block"
FLOW_SIZE = 256
SAMPLES = 4


def inside_arrow(x, y, center):
    """Filled straight shaft and broad triangular head, pointing toward increasing V."""
    dx, dy = abs(x - center), y - center
    return (-7 <= dy <= 0 and dx <= 2) or (0 <= dy <= 7 and dx <= 6 * (1 - dy / 7))


def write_png(name, size, background, arrow=None, center=16):
    rows = bytearray()
    scale = size / 32
    for y in range(size):
        rows.append(0)  # PNG filter: none
        for x in range(size):
            if arrow is None:
                rows.extend(background)
                continue
            # Keep normalized UVs unchanged: top center=0.5, side center=0.25.
            # Supersample coverage, blending RGB only so fluid alpha is unchanged.
            coverage = sum(
                inside_arrow((x + (sx + 0.5) / SAMPLES) / scale,
                             (y + (sy + 0.5) / SAMPLES) / scale, center)
                for sy in range(SAMPLES) for sx in range(SAMPLES)
            ) / (SAMPLES * SAMPLES)
            rows.extend(round(a + (b - a) * coverage) for a, b in zip(background[:3], arrow[:3]))
            rows.append(background[3])

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b"")
    (TEXTURES / f"{name}.png").write_bytes(png)


if __name__ == "__main__":
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for name, background, arrow in (
        ("lava_gel", (240, 85, 15, 110), (255, 125, 55, 110)),
        # Neutral pixels retain biome-dependent water tint and vanilla alpha.
        ("water_direction", (150, 150, 150, 180), (190, 190, 190, 180)),
    ):
        write_png(name + "_still", 16, background)
        write_png(name + "_flow", FLOW_SIZE, background, arrow)
        write_png(name + "_side", FLOW_SIZE, background, arrow, center=8)
