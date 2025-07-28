import sys
import json
import cv2
import os

# args: scan_path, errors_json, output_dir
scan_path, json_path, out_dir = sys.argv[1], sys.argv[2], sys.argv[3]

img = cv2.imread(scan_path)
if img is None:
    print(f"Chyba načítania obrázka: {scan_path}")
    sys.exit(1)

with open(json_path, 'r', encoding='utf-8') as f:
    errors = json.load(f)

for e in errors:
    # Zvýraznenie TEXT chýb červeným rámom
    if e.get("type") == "TEXT":
        x, y, w, h = e["x"], e["y"], e["width"], e["height"]
        cv2.rectangle(img, (x, y), (x + w, y + h), (0, 0, 255), 2)

filename = "viz_" + os.path.basename(scan_path)
out_path = os.path.join(out_dir, filename)

cv2.imwrite(out_path, img)
print(f"[viz] Uloženo: {out_path}")
