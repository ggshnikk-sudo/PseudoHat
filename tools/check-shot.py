#!/usr/bin/env python3
"""Checks that a head cosmetic actually drew.

The sample textures are flat, known colours, so instead of eyeballing a screenshot we count pixels
matching them. Vanilla has no such colours, so any hit came from the cosmetic rendering.

Entity shading multiplies a face colour by the light it receives, so a flat texture comes out as
some fraction of itself (the sample brim renders at roughly 0.4x under normal light). Comparing raw
RGB would reject a face that drew correctly, so each pixel is allowed to be any scalar multiple of a
target: only the direction in colour space has to match.
"""
import sys
from collections import Counter
from pathlib import Path

TARGETS = {
    "cube (red)": (196, 64, 64),
    "accent (blue)": (64, 128, 220),
}

TOLERANCE = 12
MIN_SCALE = 0.35
MAX_SCALE = 1.2


def close(a, b, tol=TOLERANCE):
    return all(abs(int(x) - int(y)) <= tol for x, y in zip(a[:3], b[:3]))


def shade_match(colour, target):
    """True when colour is target scaled by a plausible shading factor."""
    tt = sum(t * t for t in target)
    scale = sum(c * t for c, t in zip(colour, target)) / tt

    if not MIN_SCALE <= scale <= MAX_SCALE:
        return False

    return close(colour, [scale * t for t in target])


def main(paths):
    failures = 0

    for arg in paths:
        path = Path(arg)
        if not path.exists():
            print(f"MISSING {path}")
            failures += 1
            continue

        try:
            from PIL import Image
        except ImportError:
            print("PIL is required: pip install pillow")
            return 2

        image = Image.open(path).convert("RGB")
        counts = Counter(image.getdata())

        print(f"{path.name}: {image.size[0]}x{image.size[1]}, {len(counts)} distinct colours")

        for label, target in TARGETS.items():
            hits = 0
            sampled = None
            for colour, count in counts.items():
                if close(colour, target) or shade_match(colour, target):
                    hits += count
                    sampled = sampled or colour
            pct = 100.0 * hits / (image.size[0] * image.size[1])
            flag = "OK  " if hits > 0 else "FAIL"
            print(f"  {flag} {label}: {hits} px ({pct:.3f}%) nearest={sampled}")
            if hits == 0:
                failures += 1

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))