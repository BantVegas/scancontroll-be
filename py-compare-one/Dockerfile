FROM python:3.11-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr libzbar0 build-essential \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .
ENV TESSERACT_CMD=/usr/bin/tesseract
ENV HOST=0.0.0.0
ENV PORT=8011
EXPOSE 8011

CMD ["bash","-lc","python -m uvicorn compare:app --host 0.0.0.0 --port ${PORT}"]
