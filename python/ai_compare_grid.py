#!/usr/bin/env python3
import sys
import os
import json
import cv2
import numpy as np

def split_labels_grid_with_gaps(scan_img, rows, cols, label_w, label_h, gap_x, gap_y):
    labels = []
    for r in range(rows):
        for c in range(cols):
            x1 = int(c * (label_w + gap_x))
            y1 = int(r * (label_h + gap_y))
            x2 = x1 + label_w
            y2 = y1 + label_h
            if y2 > scan_img.shape[0] or x2 > scan_img.shape[1]:
                continue
            crop = scan_img[y1:y2, x1:x2]
            # Optional: resize (ak crop nie je presne label_w x label_h)
            if crop.shape[0] != label_h or crop.shape[1] != label_w:
                crop = cv2.resize(crop, (label_w, label_h))
            labels.append({
                "row": r + 1,
                "col": c + 1,
                "x": x1,
                "y": y1,
                "img": crop
            })
    return labels

def compare_label(master, label_img, diff_thresh=30, min_area=100):
    master_gray = cv2.cvtColor(master, cv2.COLOR_BGR2GRAY)
    label_gray  = cv2.cvtColor(label_img, cv2.COLOR_BGR2GRAY)
    diff = cv2.absdiff(master_gray, label_gray)
    _, bw = cv2.threshold(diff, diff_thresh, 255, cv2.THRESH_BINARY)
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (5,5))
    bw = cv2.morphologyEx(bw, cv2.MORPH_CLOSE, kernel, iterations=2)
    contours, _ = cv2.findContours(bw, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    bboxes = []
    for cnt in contours:
        x,y,w,h = cv2.boundingRect(cnt)
        if w*h >= min_area:
            bboxes.append((x,y,w,h))
    return bboxes

def main():
    if len(sys.argv) != 9:
        print("Použitie: ai_compare_grid.py <master_path> <scan_path> <output_dir> <rows> <cols> <label_w_px> <label_h_px> <gap_x_px> <gap_y_px>")
        print("Príklad: ai_compare_grid.py master.png scan.png out_dir 3 5 320 220 10 15")
        sys.exit(1)

    master_path, scan_path, output_dir, rows_s, cols_s, label_w_s, label_h_s, gap_x_s, gap_y_s = sys.argv[1:]
    rows, cols = int(rows_s), int(cols_s)
    label_w, label_h = int(label_w_s), int(label_h_s)
    gap_x, gap_y = int(gap_x_s), int(gap_y_s)

    os.makedirs(output_dir, exist_ok=True)

    master = cv2.imread(master_path)
    scan   = cv2.imread(scan_path)
    if master is None or scan is None:
        print("Chyba: Nepodarilo sa načítať obrázky.")
        sys.exit(1)

    labels = split_labels_grid_with_gaps(scan, rows, cols, label_w, label_h, gap_x, gap_y)
    errors = []
    annotated = scan.copy()

    for label in labels:
        r, c, x_off, y_off, lbl = label["row"], label["col"], label["x"], label["y"], label["img"]
        bboxes = compare_label(master, lbl)
        for (x, y, w, h) in bboxes:
            # Prekresli bbox v globálnych súradniciach
            cv2.rectangle(
                annotated,
                (x_off + x, y_off + y),
                (x_off + x + w, y_off + y + h),
                (0,0,255), 3
            )
            errors.append({
                "row": r,
                "col": c,
                "bbox": [int(x_off + x), int(y_off + y), int(w), int(h)],
                "desc": f"Rozdiel v etikete r{r}c{c}"
            })

    out_img_path = os.path.join(output_dir, "output.png")
    cv2.imwrite(out_img_path, annotated)
    out_json = os.path.join(output_dir, "errors.json")
    with open(out_json, "w", encoding="utf-8") as f:
        json.dump(errors, f, ensure_ascii=False, indent=2)

    print(f"Hotovo. Výsledok: {out_img_path}, {out_json}")

if __name__ == "__main__":
    main()
