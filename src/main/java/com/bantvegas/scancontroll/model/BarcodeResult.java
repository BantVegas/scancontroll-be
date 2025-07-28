package com.bantvegas.scancontroll.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BarcodeResult {
    private int labelIndex;        // index etikety (od 1)
    private String value;          // dekódovaný text
    private String format;         // typ barcode (napr. CODE_128)
    private int x, y, w, h;        // bounding box
    private boolean valid;         // je očakávaný kód?
    private String error;          // prípadná chybová hláška
}
