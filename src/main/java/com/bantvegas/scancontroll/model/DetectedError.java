package com.bantvegas.scancontroll.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetectedError {
    private int id;
    private String type;    // TEXT, FAREBNOST, BARCODE, INFO
    private String message; // Operator-friendly message
    private int x,y,width,height;  // For drawing red boxes
}

