package com.bantvegas.scancontroll.util;

import com.bantvegas.scancontroll.model.OcrLineResult;
import com.bantvegas.scancontroll.service.OcrService;

import java.io.File;
import java.util.List;

/**
 * Pomocná trieda na detekciu orientácie etikety (navin) podľa OCR textu.
 */
public class OcrWindDetector {

    /**
     * Deteguje smer etikety na základe najväčšieho textu a jeho uhlu.
     *
     * @param etiketaFile PNG obrázok etikety
     * @param ocrService  OCR služba tvojho systému
     * @return String (A1, A2, A3, A4), alebo null ak nevieme zistiť
     */
    public static String detectWind(File etiketaFile, OcrService ocrService) {
        List<OcrLineResult> lines = ocrService.getTextLinesWithAngle(etiketaFile);
        if (lines == null || lines.isEmpty()) return null;

        // Nájdeme najväčší textový box (alebo najsilnejší riadok)
        OcrLineResult biggest = lines.get(0);
        for (OcrLineResult l : lines) {
            if (l.getWidth() * l.getHeight() > biggest.getWidth() * biggest.getHeight()) {
                biggest = l;
            }
        }

        int angle = biggest.getAngle(); // musíš vedieť extrahovať angle z OCR výstupu
        // 0 = normálne, 180/-180 = hore nohami, 90 = na výšku vpravo, -90 = na výšku vľavo
        if (angle == 0) return "A1";
        if (angle == 180 || angle == -180) return "A3";
        if (angle == 90) return "A2";
        if (angle == -90) return "A4";
        return null;
    }
}
