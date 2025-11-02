# main.py
import os
import uvicorn

if __name__ == "__main__":
    host = os.getenv("HOST", "127.0.0.1")
    port = int(os.getenv("PORT", "8011"))
    reload = os.getenv("RELOAD", "1") not in ("0","false","no")

    # len vypíšeme pár užitočných info
    try:
        import pytesseract
        tess = pytesseract.pytesseract.tesseract_cmd
    except Exception:
        tess = "unknown"

    try:
        from pyzbar import pyzbar  # noqa
        zbar_ok = True
    except Exception:
        zbar_ok = False

    print(f"[launcher] HOST={host} PORT={port} RELOAD={reload}")
    print(f"[launcher] Tesseract: {tess}")
    print(f"[launcher] pyzbar/zbar: {'OK' if zbar_ok else 'NOT AVAILABLE'}")

    # importujeme až po logoch (rýchlejšie zlyhania sa pekne ukážu)
    from compare import app
    uvicorn.run("compare:app", host=host, port=port, reload=reload)
