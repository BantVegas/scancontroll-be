package com.bantvegas.scancontroll.model;

import java.util.List;
import java.util.ArrayList;

public class LabelResult {
    public int index; // index etikety v páse
    public List<OcrLineResult> ocrErrors; // chyby OCR pre túto etiketu
    public List<BarcodeResult> barcodes; // všetky barkódy detegované na tejto etikete
    public List<String> errors; // všeobecné chyby etikety (barcode, OCR)

    public LabelResult(int index, List<OcrLineResult> ocrErrors, List<BarcodeResult> barcodes) {
        this.index = index;
        this.ocrErrors = ocrErrors;
        this.barcodes = barcodes;
        this.errors = new ArrayList<>();
    }
}
