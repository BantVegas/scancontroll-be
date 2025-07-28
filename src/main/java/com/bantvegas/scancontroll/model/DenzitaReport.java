package com.bantvegas.scancontroll.model;

import lombok.Data;

@Data
public class DenzitaReport {
    private Long id;
    private String operator;
    private String productCode;
    private String datetime;
    private Double cyan;
    private Double magenta;
    private Double yellow;
    private Double black;
    private String summary; // napr. OK / CHYBA / Pridať, Ubrať
}

