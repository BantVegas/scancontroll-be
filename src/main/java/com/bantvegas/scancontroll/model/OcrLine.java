package com.bantvegas.scancontroll.model;

/**
 * Model pre jeden riadok extrahovaný z OCR (text + bounding box).
 */
public class OcrLine {
    public int index;     // index v zozname (poradie riadku v etikete)
    public String text;   // rozpoznaný, už normalizovaný text
    public int x, y, w, h; // bounding box (ľavý horný roh + rozmery)

    public OcrLine() {
    }

    public OcrLine(int index, String text, int x, int y, int w, int h) {
        this.index = index;
        this.text = text;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    // Gettre/settre ak potrebuješ
    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getW() { return w; }
    public void setW(int w) { this.w = w; }

    public int getH() { return h; }
    public void setH(int h) { this.h = h; }

    @Override
    public String toString() {
        return "OcrLine{" +
                "index=" + index +
                ", text='" + text + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", w=" + w +
                ", h=" + h +
                '}';
    }
}

