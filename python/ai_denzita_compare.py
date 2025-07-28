#!/usr/bin/env python3
import sys
import json
from PIL import Image

LIMITS = {
    "cyan":    (1.3, 1.5),
    "magenta": (1.3, 1.5),
    "yellow":  (1.1, 1.4),
    "black":   (1.5, 1.8),
}

def rgb_to_cmyk_frac(r, g, b):
    if (r, g, b) == (0, 0, 0):
        return 0.0, 0.0, 0.0, 1.0
    c = 1 - r / 255.0
    m = 1 - g / 255.0
    y = 1 - b / 255.0
    k = min(c, m, y)
    if k >= 1.0:
        return 0.0, 0.0, 0.0, 1.0
    denom = 1 - k
    return ((c - k) / denom, (m - k) / denom, (y - k) / denom, k)

def analyze_density(image_path):
    img = Image.open(image_path).convert("RGB")
    pixels = list(img.getdata())
    n = len(pixels)
    sums = {chan: 0.0 for chan in LIMITS}
    for (r, g, b) in pixels:
        c, m, y, k = rgb_to_cmyk_frac(r, g, b)
        sums["cyan"]    += c
        sums["magenta"] += m
        sums["yellow"]  += y
        sums["black"]   += k
    values = {}
    for chan in LIMITS:
        frac = sums[chan] / n
        values[chan] = frac * 100  # percentá pokrytia
    return values

def main():
    if len(sys.argv) != 3:
        sys.stderr.write("Použitie: ai_denzita_compare.py <master.png> <etiketa.png>\n")
        sys.exit(1)
    master_path = sys.argv[1]
    etiketa_path = sys.argv[2]
    try:
        master = analyze_density(master_path)
        etiketa = analyze_density(etiketa_path)
        result = {}
        for chan in LIMITS:
            master_val = master[chan]
            etiketa_val = etiketa[chan]
            abs_rozdiel = etiketa_val - master_val
            rel_rozdiel = 0.0
            if abs(master_val) > 1e-4:
                rel_rozdiel = (etiketa_val - master_val) / master_val * 100
            result[chan] = {
                "master": round(master_val, 1),
                "etiketa": round(etiketa_val, 1),
                "rozdiel": round(abs_rozdiel, 1),
                "rel_rozdiel": round(rel_rozdiel, 1)
            }
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except Exception as e:
        sys.stderr.write(f"Chyba pri analýze: {e}\n")
        sys.exit(2)

if __name__ == "__main__":
    main()

