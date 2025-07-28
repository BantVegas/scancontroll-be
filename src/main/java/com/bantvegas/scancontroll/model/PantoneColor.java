package com.bantvegas.scancontroll.model;

public class PantoneColor {
    private String hex;
    private int r;
    private int g;
    private int b;

    public PantoneColor() {}
    public PantoneColor(String hex, int r, int g, int b) {
        this.hex = hex;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public String getHex() { return hex; }
    public int getR() { return r; }
    public int getG() { return g; }
    public int getB() { return b; }

    public void setHex(String hex) { this.hex = hex; }
    public void setR(int r) { this.r = r; }
    public void setG(int g) { this.g = g; }
    public void setB(int b) { this.b = b; }
}
