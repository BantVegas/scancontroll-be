package com.bantvegas.scancontroll.dto;

public class PantoneCompareResponse {
    private String pantoneCode;
    private String pantoneHex;
    private int refR, refG, refB;
    private int sampleR, sampleG, sampleB;
    private double deltaE2000, deltaE76, rgbDistance;
    private String rating;
    private double matchPercent;

    public PantoneCompareResponse(
            String pantoneCode,
            String pantoneHex,
            int refR, int refG, int refB,
            int sampleR, int sampleG, int sampleB,
            double deltaE2000, double deltaE76, double rgbDistance,
            String rating, double matchPercent
    ) {
        this.pantoneCode = pantoneCode;
        this.pantoneHex = pantoneHex;
        this.refR = refR;
        this.refG = refG;
        this.refB = refB;
        this.sampleR = sampleR;
        this.sampleG = sampleG;
        this.sampleB = sampleB;
        this.deltaE2000 = deltaE2000;
        this.deltaE76 = deltaE76;
        this.rgbDistance = rgbDistance;
        this.rating = rating;
        this.matchPercent = matchPercent;
    }

    // GETTERY
    public String getPantoneCode() { return pantoneCode; }
    public String getPantoneHex() { return pantoneHex; }
    public int getRefR() { return refR; }
    public int getRefG() { return refG; }
    public int getRefB() { return refB; }
    public int getSampleR() { return sampleR; }
    public int getSampleG() { return sampleG; }
    public int getSampleB() { return sampleB; }
    public double getDeltaE2000() { return deltaE2000; }
    public double getDeltaE76() { return deltaE76; }
    public double getRgbDistance() { return rgbDistance; }
    public String getRating() { return rating; }
    public double getMatchPercent() { return matchPercent; }
}


