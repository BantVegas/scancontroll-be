package com.bantvegas.scancontroll.model;

public class PantoneReport {
    private Long id;
    private String operator;
    private String productCode;
    private String datetime;
    private String pantoneCode;
    private String pantoneHex;
    private Integer refR, refG, refB;
    private Integer sampleR, sampleG, sampleB;
    private Double deltaE2000, deltaE76, rgbDistance, matchPercent;
    private String rating;

    // --- Pre dashboard/FE ---
    private String reportType;   // "PANTONE"
    private String detailsJson;  // JSON string pre frontend

    // -- GETTER/SETTER --

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getDatetime() { return datetime; }
    public void setDatetime(String datetime) { this.datetime = datetime; }

    public String getPantoneCode() { return pantoneCode; }
    public void setPantoneCode(String pantoneCode) { this.pantoneCode = pantoneCode; }

    public String getPantoneHex() { return pantoneHex; }
    public void setPantoneHex(String pantoneHex) { this.pantoneHex = pantoneHex; }

    public Integer getRefR() { return refR; }
    public void setRefR(Integer refR) { this.refR = refR; }

    public Integer getRefG() { return refG; }
    public void setRefG(Integer refG) { this.refG = refG; }

    public Integer getRefB() { return refB; }
    public void setRefB(Integer refB) { this.refB = refB; }

    public Integer getSampleR() { return sampleR; }
    public void setSampleR(Integer sampleR) { this.sampleR = sampleR; }

    public Integer getSampleG() { return sampleG; }
    public void setSampleG(Integer sampleG) { this.sampleG = sampleG; }

    public Integer getSampleB() { return sampleB; }
    public void setSampleB(Integer sampleB) { this.sampleB = sampleB; }

    public Double getDeltaE2000() { return deltaE2000; }
    public void setDeltaE2000(Double deltaE2000) { this.deltaE2000 = deltaE2000; }

    public Double getDeltaE76() { return deltaE76; }
    public void setDeltaE76(Double deltaE76) { this.deltaE76 = deltaE76; }

    public Double getRgbDistance() { return rgbDistance; }
    public void setRgbDistance(Double rgbDistance) { this.rgbDistance = rgbDistance; }

    public Double getMatchPercent() { return matchPercent; }
    public void setMatchPercent(Double matchPercent) { this.matchPercent = matchPercent; }

    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }

    // --- NOVÉ: Pre dashboard/FE (musíš mať tieto!) ---
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
}
