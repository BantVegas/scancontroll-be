package com.bantvegas.scancontroll.model;

import java.util.Objects;

public class OcrLineResult {
    public int lineIdx;         // index riadku v etikete
    public String masterText;   // text v master etikete
    public String scanText;     // text v sken etikete
    public int x, y, w, h;      // bounding box pre chybný riadok
    public int similarity;      // podobnosť v %
    public boolean error;       // je chyba?
    public int angle;           // uhol textu v etikete (A1=0, A2=90, A3=180, A4=-90)

    public OcrLineResult(int lineIdx, String masterText, String scanText,
                         int x, int y, int w, int h,
                         int similarity, boolean error, int angle) {
        this.lineIdx = lineIdx;
        this.masterText = masterText;
        this.scanText = scanText;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.similarity = similarity;
        this.error = error;
        this.angle = angle;
    }

    // Konštruktor s default angle=0
    public OcrLineResult(int lineIdx, String masterText, String scanText,
                         int x, int y, int w, int h,
                         int similarity, boolean error) {
        this(lineIdx, masterText, scanText, x, y, w, h, similarity, error, 0);
    }

    // --- GETTERY/SETTERY pre kompatibilitu ---
    public int getLineIdx() { return lineIdx; }
    public String getMasterText() { return masterText; }
    public String getScanText() { return scanText; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return w; }
    public int getHeight() { return h; }
    public int getSimilarity() { return similarity; }
    public boolean isError() { return error; }
    public int getAngle() { return angle; }

    public void setLineIdx(int lineIdx) { this.lineIdx = lineIdx; }
    public void setMasterText(String masterText) { this.masterText = masterText; }
    public void setScanText(String scanText) { this.scanText = scanText; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setWidth(int w) { this.w = w; }
    public void setHeight(int h) { this.h = h; }
    public void setSimilarity(int similarity) { this.similarity = similarity; }
    public void setError(boolean error) { this.error = error; }
    public void setAngle(int angle) { this.angle = angle; }

    @Override
    public String toString() {
        return "OcrLineResult{" +
                "lineIdx=" + lineIdx +
                ", masterText='" + masterText + '\'' +
                ", scanText='" + scanText + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", w=" + w +
                ", h=" + h +
                ", similarity=" + similarity +
                ", error=" + error +
                ", angle=" + angle +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OcrLineResult)) return false;
        OcrLineResult that = (OcrLineResult) o;
        return lineIdx == that.lineIdx &&
                x == that.x &&
                y == that.y &&
                w == that.w &&
                h == that.h &&
                similarity == that.similarity &&
                error == that.error &&
                angle == that.angle &&
                Objects.equals(masterText, that.masterText) &&
                Objects.equals(scanText, that.scanText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineIdx, masterText, scanText, x, y, w, h, similarity, error, angle);
    }
}

