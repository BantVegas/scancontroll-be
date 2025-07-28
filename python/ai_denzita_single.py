#!/usr/bin/env python3
import sys
import json
from PIL import Image

LIMITS = ["cyan", "magenta", "yellow", "black"]

WHITE_THRESHOLD = 230
COLOR_MIN_FRAC = 0.10
CROP_PX = 10

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

def analyze_density(img):
    w, h = img.size
    img = img.crop((CROP_PX, CROP_PX, w - CROP_PX, h - CROP_PX))
    pixels = list(img.getdata())
    n = 0
    sums = {chan: 0.0 for chan in LIMITS}
    for (r, g, b) in pixels:
        if r > WHITE_THRESHOLD and g > WHITE_THRESHOLD and b > WHITE_THRESHOLD:
            continue
        c, m, y, k = rgb_to_cmyk_frac(r, g, b)
        if max(c, m, y, k) < COLOR_MIN_FRAC:
            continue
        sums["cyan"]    += c
        sums["magenta"] += m
        sums["yellow"]  += y
        sums["black"]   += k
        n += 1
    if n == 0:
        raise Exception("Nenašiel som žiadne relevantné pixely na analýzu.")
    values = {}
    for idx, chan in enumerate(LIMITS):
        frac = sums[chan] / n
        values[chan] = frac * 100
    return values

def main():
    if len(sys.argv) != 3:
        sys.stderr.write("Použitie: ai_denzita_single.py <master.png> <etiketa.png>\n")
        sys.exit(1)
    master_path = sys.argv[1]
    etiketa_path = sys.argv[2]
    try:
        master_img = Image.open(master_path).convert("RGB")
        master_density = analyze_density(master_img)
        etiketa_img = Image.open(etiketa_path).convert("RGB")
        etiketa_density = analyze_density(etiketa_img)
        result = {}
        for chan in LIMITS:
            master_val = master_density[chan]
            etiketa_val = etiketa_density[chan]
            abs_rozdiel = etiketa_val - master_val
            rel_rozdiel = 0.0
            if abs(master_val) > 1e-4:
                rel_rozdiel = (master_val - etiketa_val) / master_val * 100
            if abs(rel_rozdiel) <= 10:
                hodnotenie = "OK"
            elif rel_rozdiel > 0:
                hodnotenie = f"Pridať {abs(round(rel_rozdiel, 1))}%"
            else:
                hodnotenie = f"Ubrať {abs(round(rel_rozdiel, 1))}%"
            result[chan] = {
                "master": round(master_val, 1),
                "etiketa": round(etiketa_val, 1),
                "rozdiel": round(abs_rozdiel, 1),
                "rel_rozdiel": round(rel_rozdiel, 1),
                "vyhodnotenie": hodnotenie
            }
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except Exception as e:
        sys.stderr.write(f"Chyba pri analýze: {e}\n")
        sys.exit(2)

if __name__ == "__main__":
    main()
