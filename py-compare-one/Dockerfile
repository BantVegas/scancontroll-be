FROM python:3.11-slim

# Systémové knižnice: zbar pre pyzbar + tesseract (SK/EN)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libzbar0 tesseract-ocr tesseract-ocr-slk tesseract-ocr-eng \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Rýchlejší/predvídateľný pip
ENV PIP_NO_CACHE_DIR=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# requirements.txt musí sedieť na:
# fastapi, uvicorn[standard], numpy, pillow, opencv-python-headless, scikit-image, pytesseract, pyzbar
COPY requirements.txt .
RUN python -m pip install --upgrade pip && \
    pip install -r requirements.txt

# App kód
COPY . .

# Konfigurácia služby
ENV HOST=0.0.0.0 \
    PORT=8011 \
    TESSERACT_CMD=/usr/bin/tesseract \
    ENABLE_OCR=1 \
    ENABLE_BARCODE=1 \
    CORS_ALLOW_ORIGINS="https://scancontroll.eu,https://*.vercel.app"

EXPOSE 8011

# (voliteľné) healthcheck na /api/health
HEALTHCHECK --interval=30s --timeout=5s --retries=5 CMD \
  wget -qO- http://127.0.0.1:${PORT}/api/health || exit 1

# Spustenie: priamo uvicorn v exec forme
CMD ["uvicorn","compare:app","--host","0.0.0.0","--port","8011"]
