from fastapi import FastAPI, UploadFile, File, Form
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from typing import Optional, List, Dict, Tuple
import io, base64, os, json

import numpy as np
from PIL import Image
import cv2
from skimage.metrics import structural_similarity as ssim
import pytesseract

# --- ZBAR/PYZBAR ---
from pyzbar.pyzbar import decode as zbar_decode

# --- Tesseract cesta (Windows) ---
TESSERACT_DEFAULT = r"C:\Program Files\Tesseract-OCR\tesseract.exe"
pytesseract.pytesseract.tesseract_cmd = os.environ.get("TESSERACT_CMD", TESSERACT_DEFAULT)

app = FastAPI(title="compare-one")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Jednoduchá "pamäť" na učenie z fake chýb
MEM_FILE = "memory.json"
def load_mem():
    if not os.path.exists(MEM_FILE):
        return {"fake_errors":[]}
    with open(MEM_FILE, "r", encoding="utf-8") as f:
        return json.load(f)
def save_mem(d):
    with open(MEM_FILE, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=2)

@app.get("/api/health")
def health():
    return {"ok": True, "service": "compare-one"}

@app.get("/api/debug/state")
def debug_state():
    return {
        "ok": True,
        "tesseract": pytesseract.pytesseract.tesseract_cmd,
        "zbar": True,      # ak by pyzbar nevedel importnúť zbar, endpoint by spadol už vyššie
        "pyzbar": True
    }

# ------------- helpers -------------
def img_from_upload(f: UploadFile) -> Image.Image:
    # čítame RAW bajty (bez seekovania do začiatku) – UploadFile drží súbor v pamäti
    return Image.open(io.BytesIO(f.file.read())).convert("RGB")

def to_b64_png(arr: np.ndarray) -> str:
    im = Image.fromarray(arr)
    buf = io.BytesIO()
    im.save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode("utf-8")

def ocr_text(img: Image.Image, lang: str) -> str:
    # OCR NECHÁVAM TAK, ako si písal – netuním nič
    try:
        return pytesseract.image_to_string(img, lang=lang or "eng+slk")
    except Exception:
        return ""

# ---- BARCODE ----
def _sym_norm(sym: str) -> str:
    s = (sym or "").upper().replace("-", "_")
    mapping = {
        "EAN13": "EAN_13", "EAN8": "EAN_8",
        "UPCA": "UPC_A", "UPCE": "UPC_E",
        "QRCODE": "QR_CODE", "CODE128": "CODE_128",
        "CODE39": "CODE_39", "DATAMATRIX": "DATA_MATRIX",
        "PDF417": "PDF_417", "I25": "ITF"
    }
    return mapping.get(s, s)

def _ean13_ok(code: str) -> Optional[bool]:
    digits = "".join(ch for ch in str(code) if ch.isdigit())
    if len(digits) != 13:
        return None
    nums = [int(c) for c in digits]
    s = sum(nums[i] * (3 if i % 2 else 1) for i in range(12))
    chk = (10 - (s % 10)) % 10
    return chk == nums[12]

def decode_barcodes(img: Image.Image) -> List[Dict]:
    """
    Vráti zoznam detekcií zo zadaného obrázka.
    Každá položka: {symbology, value, x,y,w,h, checksumOk}
    """
    bgr = cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)
    H, W = bgr.shape[:2]

    out: List[Dict] = []
    # viacero pre-processing variantov pre robustnosť
    variants = [bgr]
    g = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    variants.append(cv2.cvtColor(clahe.apply(g), cv2.COLOR_GRAY2BGR))
    thr = cv2.adaptiveThreshold(g, 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY, 31, 5)
    variants.append(cv2.cvtColor(thr, cv2.COLOR_GRAY2BGR))
    variants.append(cv2.resize(bgr, (min(2*W, 2500), min(2*H, 2500)), interpolation=cv2.INTER_CUBIC))
    variants.append(cv2.bitwise_not(bgr))

    seen = set()
    for base in variants:
        for rot in (0, 1, 2, 3):
            if rot == 1:
                test = cv2.rotate(base, cv2.ROTATE_90_CLOCKWISE)
            elif rot == 2:
                test = cv2.rotate(base, cv2.ROTATE_180)
            elif rot == 3:
                test = cv2.rotate(base, cv2.ROTATE_90_COUNTERCLOCKWISE)
            else:
                test = base
            arr = cv2.cvtColor(test, cv2.COLOR_BGR2RGB)
            for r in zbar_decode(arr):
                sym = _sym_norm(getattr(r, "type", "") or "BARCODE")
                val = r.data.decode("utf-8", errors="ignore") if getattr(r, "data", None) else ""
                rect = getattr(r, "rect", None)
                if rect:
                    x, y, w, h = rect.left, rect.top, rect.width, rect.height
                else:
                    x, y, w, h = 0, 0, arr.shape[1], arr.shape[0]

                key = (sym, val, x, y, w, h)
                if key in seen:
                    continue
                seen.add(key)

                checksum_ok = True
                if sym == "EAN_13":
                    v = _ean13_ok(val)
                    checksum_ok = True if v is None else bool(v)

                out.append({
                    "symbology": sym,
                    "value": val,
                    "x": int(x), "y": int(y), "w": int(w), "h": int(h),
                    "checksumOk": checksum_ok
                })
        if out:
            break
    return out

# ---- GRAFICKÉ ROZDIELY (SSIM + boxy) ----
def _merge_boxes(boxes: List[Tuple[int,int,int,int]], max_gap: int = 6) -> List[Tuple[int,int,int,int]]:
    if not boxes:
        return []
    boxes = [list(map(int, b)) for b in boxes]
    changed = True
    while changed:
        changed = False
        used = [False]*len(boxes)
        out = []
        for i in range(len(boxes)):
            if used[i]: continue
            x,y,w,h = boxes[i]
            x2, y2 = x+w, y+h
            for j in range(i+1, len(boxes)):
                if used[j]: continue
                xx,yy,ww,hh = boxes[j]
                xx2, yy2 = xx+ww, yy+hh
                # prekryv s malou medzerou
                if not (x2 + max_gap < xx or xx2 + max_gap < x or y2 + max_gap < yy or yy2 + max_gap < y):
                    x, y = min(x, xx), min(y, yy)
                    x2, y2 = max(x2, xx2), max(y2, yy2)
                    used[j] = True
                    changed = True
            used[i] = True
            out.append((x, y, x2-x, y2-y))
        boxes = out
    return [tuple(map(int, b)) for b in boxes]

def compare_graphics(master: Image.Image, etiketa: Image.Image):
    # SSIM 0..1 (1 = identické)
    # pracujeme v sivej a rovnakom rozmere
    m = cv2.cvtColor(np.array(master), cv2.COLOR_RGB2GRAY)
    e = cv2.cvtColor(np.array(etiketa), cv2.COLOR_RGB2GRAY)
    H = min(m.shape[0], e.shape[0]); W = min(m.shape[1], e.shape[1])
    if (m.shape[0], m.shape[1]) != (H, W):
        m = cv2.resize(m, (W, H), interpolation=cv2.INTER_AREA)
    if (e.shape[0], e.shape[1]) != (H, W):
        e = cv2.resize(e, (W, H), interpolation=cv2.INTER_AREA)

    m_b = cv2.GaussianBlur(m, (5,5), 0)
    e_b = cv2.GaussianBlur(e, (5,5), 0)

    val, diff = ssim(m_b, e_b, full=True)
    diff_img = ((1.0 - diff) * 255).astype(np.uint8)

    # prahovanie + morfológia → boxy rozdielov
    _, bw = cv2.threshold(diff_img, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    k = cv2.getStructuringElement(cv2.MORPH_RECT, (3,3))
    bw = cv2.morphologyEx(bw, cv2.MORPH_OPEN, k, iterations=1)
    bw = cv2.morphologyEx(bw, cv2.MORPH_CLOSE, k, iterations=2)

    cnts, _ = cv2.findContours(bw, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    min_area = max(120, int(0.003 * H * W))
    boxes = []
    for c in cnts:
        x,y,w,h = cv2.boundingRect(c)
        if w*h >= min_area:
            boxes.append((x,y,w,h))
    boxes = _merge_boxes(boxes, max_gap=max(4, int(min(H,W)*0.015)))

    # farebná náhľadová heatmapa
    heat = np.zeros((H,W,3), dtype=np.uint8)
    heat[:,:,2] = diff_img  # červený kanál
    preview_b64 = to_b64_png(heat)
    return float(val), boxes, preview_b64

# ------------- API -------------
@app.post("/api/compare-one")
async def compare_one(
        master: UploadFile = File(...),
        etiketa: UploadFile = File(...),
        operator: Optional[str] = Form(None),
        productNumber: Optional[str] = Form(None),
        spoolNumber: Optional[str] = Form(None),
        OCR_LANG: Optional[str] = Form("eng+slk"),
):
    mem = load_mem()

    m_img = img_from_upload(master)
    e_img = img_from_upload(etiketa)

    # OCR (nechávam bez zásahu)
    ocr_master = ocr_text(m_img, OCR_LANG)
    ocr_scan   = ocr_text(e_img, OCR_LANG)

    # ---- BARCODE porovnanie (SET equality) ----
    det_m = decode_barcodes(m_img)
    det_s = decode_barcodes(e_img)

    master_set = {(d["symbology"], d["value"]) for d in det_m if d.get("checksumOk", True)}
    scan_set   = {(d["symbology"], d["value"]) for d in det_s if d.get("checksumOk", True)}

    # „match“ iba ak sú PRESNE rovnaké množiny (žiadny chýbajúci ani navyše)
    barcode_match = bool(master_set) and (scan_set == master_set)

    # položky pre FE – vyrábame hlavne zo SCAN-u; ak master niečo má a scan nie, pridáme „missing“
    items = []
    for d in det_s:
        sym = d["symbology"]; val = d["value"]
        checksum_ok = d.get("checksumOk", True)
        match_master = (sym, val) in master_set
        valid = bool(checksum_ok and match_master)
        reason = None
        if not checksum_ok:
            reason = "bad checksum"
        elif not match_master:
            reason = "mismatch vs master"
        items.append({
            "symbology": sym, "value": val,
            "x": d["x"], "y": d["y"], "w": d["w"], "h": d["h"],
            "valid": valid, "reason": reason
        })

    # kódy, ktoré má master a SCAN ich vôbec nenašiel → tiež „invalid“
    missing = []
    for sym, val in master_set:
        if (sym, val) not in scan_set:
            missing.append({
                "symbology": sym, "value": val,
                "x": 0, "y": 0, "w": 0, "h": 0,
                "valid": False, "reason": "missing on scan"
            })
    items.extend(missing)

    # ---- GRAFIKA ----
    ssim_val, diff_boxes, diff_preview_b64 = compare_graphics(m_img, e_img)

    # ukladanie štatistiky
    sample = {
        "operator": operator or "-",
        "productNumber": productNumber or "-",
        "spoolNumber": spoolNumber or "-",
        "ocrDiffChars": abs(len((ocr_master or "").strip()) - len((ocr_scan or "").strip())),
        "barcodeMatch": barcode_match,
        "ssim": ssim_val
    }
    mem["fake_errors"].append(sample)
    save_mem(mem)

    return JSONResponse({
        "status": "OK",
        "operator": operator or "-",
        "productNumber": productNumber or "-",
        "spoolNumber": spoolNumber or "-",

        "ocr": {
            "masterText": ocr_master,
            "scanText": ocr_scan
        },

        # >>> FE očakáva 'barcode.items' (valid=false => CHYBA)
        "barcode": {
            "items": items,
            "match": barcode_match
        },

        "graphics": {
            "ssim": ssim_val,                 # 0..1 (bližšie k 1 = lepšie)
            "boxes": [{"x":x,"y":y,"w":w,"h":h} for (x,y,w,h) in diff_boxes],
            "preview": diff_preview_b64       # PNG base64 (bez data: prefixu)
        },

        # možno sa ti hodí späť do FE
        "image": None,
        "width": int(e_img.width),
        "height": int(e_img.height)
    })

