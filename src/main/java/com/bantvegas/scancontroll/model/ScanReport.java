package com.bantvegas.scancontroll.model;

import lombok.Data;
import java.util.Map;

/**
 * Univerzálny model pre všetky typy reportov v Scancontroll.
 * Nepoužívame žiadne JPA, slúži len na ukladanie do JSON (disk).
 */
@Data
public class ScanReport {
    private Long id;               // Pridáva sa automaticky na BE
    private String reportType;     // COMPARE, DENZITA, PANTONE

    // Spoločné polia
    private String operator;
    private String productCode;
    private String jobNumber;
    private String machine;
    private String datetime;

    // COMPARE špecifické
    private String summary;                // Textové zhrnutie
    private Integer textErrors;            // počet textových chýb
    private Integer barcodeErrors;         // počet barcode chýb
    private Map<String, Object> compareDetail; // podrobné chyby, napr. zoznam, ak potrebuješ

    // DENZITA špecifické
    private Integer c;
    private Integer m;
    private Integer y;
    private Integer k;

    // PANTONE špecifické
    private String pantone;                // napr. "485C"
    private Integer matchPercent;          // percentuálna zhoda
    private String rating;                 // napr. "Výborné", "Slabé"
    private String pantoneHex;             // #hex kód
    private Double deltaE76;
    private Double deltaE2000;
    private Double rgbDistance;
    private Integer sampleR;
    private Integer sampleG;
    private Integer sampleB;
    private Integer refR;
    private Integer refG;
    private Integer refB;

    // ... ďalšie polia podľa potreby pre ďalšie reporty
}
