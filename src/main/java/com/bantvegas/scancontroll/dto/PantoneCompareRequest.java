package com.bantvegas.scancontroll.dto;

public class PantoneCompareRequest {
    public String pantoneCode;
    public String imageBase64;

    public String operator;
    public String productCode;
    public String datetime;

    public String getPantoneCode() { return pantoneCode; }
    public void setPantoneCode(String pantoneCode) { this.pantoneCode = pantoneCode; }

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getDatetime() { return datetime; }
    public void setDatetime(String datetime) { this.datetime = datetime; }
}
