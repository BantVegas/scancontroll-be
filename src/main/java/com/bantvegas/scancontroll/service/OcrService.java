package com.bantvegas.scancontroll.service;

import com.bantvegas.scancontroll.model.OcrLineResult;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.*;
import net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.text.Normalizer;
import java.util.*;

@Service
@Slf4j
public class OcrService {
    private final ITesseract tesseract = new Tesseract();

    public OcrService() {
        String tessData = new File("tessdata").getAbsolutePath();
        log.info("📁 Tessdata path: {}", tessData);
        tesseract.setDatapath(tessData);
        tesseract.setLanguage("deu+eng+ces+slk+hun");
    }

    /**
     * Extrahuje textové riadky s bounding boxmi aj uhlom textu (na etikete).
     * Každý výsledok obsahuje aj angle (uhol otočenia etikety).
     */
    public List<OcrLineResult> getTextLinesWithAngle(File imageFile) {
        List<OcrLineResult> results = new ArrayList<>();
        try {
            int angle = detectImageOrientation(imageFile);
            tesseract.setLanguage("deu+eng+ces+slk+hun");
            BufferedImage img = javax.imageio.ImageIO.read(imageFile);

            List<Word> lines = tesseract.getWords(img, TessPageIteratorLevel.RIL_TEXTLINE);
            int idx = 0;
            for (Word w : lines) {
                String raw = w.getText();
                if (raw == null) continue;

                // Filtrovanie: len dostatočne dlhé alfanumerické riadky
                String cleaned = raw.replaceAll("[^\\p{L}\\p{Nd}\\s]", "");
                if (cleaned.trim().isEmpty() || cleaned.length() < 3) continue;

                double nonAlpha = (raw.length() - cleaned.length()) / (double)Math.max(1, raw.length());
                if (nonAlpha > 0.5) continue;

                String normalized = normalizeOcrText(raw);

                int x = Math.max(0, w.getBoundingBox().x);
                int y = Math.max(0, w.getBoundingBox().y);
                int wBox = Math.max(1, w.getBoundingBox().width);
                int hBox = Math.max(1, w.getBoundingBox().height);

                // angle sa pridáva do každého riadku (celý obrázok má rovnaký angle)
                OcrLineResult line = new OcrLineResult(
                        idx++,      // lineIdx
                        normalized, // masterText (použiješ ak treba)
                        normalized, // scanText (použiješ ak treba)
                        x, y, wBox, hBox,
                        100,        // similarity default
                        false,      // error default
                        angle       // angle (detekovaný)
                );
                results.add(line);
            }
        } catch (Exception ex) {
            log.error("OCR extraction with angle failed:", ex);
        }
        return results;
    }

    /**
     * Ak potrebuješ pôvodné "extrahovanie bez angle" pre iné služby:
     */
    public List<OcrLineResult> extractLinesWithBoxes(File imageFile) {
        List<OcrLineResult> results = new ArrayList<>();
        try {
            tesseract.setLanguage("deu+eng+ces+slk+hun");
            BufferedImage img = javax.imageio.ImageIO.read(imageFile);

            List<Word> lines = tesseract.getWords(img, TessPageIteratorLevel.RIL_TEXTLINE);
            int idx = 0;
            for (Word w : lines) {
                String raw = w.getText();
                if (raw == null) continue;

                String cleaned = raw.replaceAll("[^\\p{L}\\p{Nd}\\s]", "");
                if (cleaned.trim().isEmpty() || cleaned.length() < 3) continue;
                double nonAlpha = (raw.length() - cleaned.length()) / (double)Math.max(1, raw.length());
                if (nonAlpha > 0.5) continue;

                String normalized = normalizeOcrText(raw);

                int x = Math.max(0, w.getBoundingBox().x);
                int y = Math.max(0, w.getBoundingBox().y);
                int wBox = Math.max(1, w.getBoundingBox().width);
                int hBox = Math.max(1, w.getBoundingBox().height);

                OcrLineResult line = new OcrLineResult(
                        idx++,
                        normalized, normalized, x, y, wBox, hBox,
                        100, false, 0 // angle = 0
                );
                results.add(line);
            }
        } catch (Exception ex) {
            log.error("OCR extraction failed:", ex);
        }
        return results;
    }

    /**
     * Detekuje orientáciu obrázka (etikety) v stupňoch pomocou Tesseract OSD CLI.
     * Výsledok: 0 (A1), 90 (A2), 180 (A3), 270/-90 (A4)
     */
    public int detectImageOrientation(File imageFile) {
        try {
            String tesseractCmd = "tesseract";
            ProcessBuilder pb = new ProcessBuilder(
                    tesseractCmd,
                    imageFile.getAbsolutePath(),
                    "stdout",
                    "--psm", "0"
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            int angle = 0;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("Orientation in degrees:")) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        angle = Integer.parseInt(parts[1].trim());
                        log.info("Tesseract OSD (CLI) detected orientation angle: {}", angle);
                    }
                }
            }
            proc.waitFor();
            return normalizeAngle(angle);
        } catch (Exception ex) {
            log.error("Tesseract OSD CLI failed:", ex);
            return 0;
        }
    }

    /**
     * Pre mapovanie neštandardných hodnôt (napr. -90 na 270).
     */
    private int normalizeAngle(int angle) {
        if (angle < 0) angle += 360;
        if (angle == 360) angle = 0;
        return angle;
    }

    /**
     * Normalizuje text: odstráni diakritiku, prevedie na uppercase, opraví OCR omyly.
     */
    public static String normalizeOcrText(String s) {
        if (s == null) return "";
        String noDiacritics = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        String upper = noDiacritics.toUpperCase();
        upper = upper
                .replace('0', 'O')
                .replace('1', 'I')
                .replace('5', 'S')
                .replace('8', 'B')
                .replace('|', 'I')
                .replace('!', 'I');
        upper = upper.replaceAll("\\s+", " ").trim();
        return upper;
    }
}


