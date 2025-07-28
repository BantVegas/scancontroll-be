package com.bantvegas.scancontroll.service;

import com.bantvegas.scancontroll.model.BarcodeResult;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

@Service
@Slf4j
public class BarcodeService {

    /**
     * Dekóduje všetky barcody na obrázku (celý pás alebo jedna etiketa) - vstup File.
     */
    public List<BarcodeResult> decodeAllBarcodes(File imageFile) {
        try {
            BufferedImage img = ImageIO.read(imageFile);
            return decodeAllBarcodes(img);
        } catch (Exception ex) {
            log.warn("Chyba pri načítaní obrázka: {}", ex.getMessage());
            List<BarcodeResult> results = new ArrayList<>();
            results.add(new BarcodeResult(1, null, null, 0,0,0,0, false, "Chyba: " + ex.getMessage()));
            return results;
        }
    }

    /**
     * Dekóduje všetky barcody na obrázku (celý pás alebo jedna etiketa) - vstup BufferedImage.
     */
    public List<BarcodeResult> decodeAllBarcodes(BufferedImage img) {
        List<BarcodeResult> results = new ArrayList<>();
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(img);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            MultiFormatReader multiFormatReader = new MultiFormatReader();
            com.google.zxing.multi.GenericMultipleBarcodeReader multiReader = new com.google.zxing.multi.GenericMultipleBarcodeReader(multiFormatReader);
            Map<DecodeHintType, Object> hints = new HashMap<>();
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

            Result[] detected = multiReader.decodeMultiple(bitmap, hints);

            int idx = 1;
            for (Result r : detected) {
                ResultPoint[] points = r.getResultPoints();
                int x = 0, y = 0, w = 0, h = 0;
                if (points != null && points.length >= 2) {
                    x = (int) Math.min(points[0].getX(), points[1].getX());
                    y = (int) Math.min(points[0].getY(), points[1].getY());
                    w = (int) Math.abs(points[0].getX() - points[1].getX());
                    h = img.getHeight();
                }
                results.add(new BarcodeResult(
                        idx,
                        r.getText(),
                        r.getBarcodeFormat().toString(),
                        x, y, w, h,
                        true,
                        null
                ));
                idx++;
            }
            if (results.isEmpty()) {
                results.add(new BarcodeResult(1, null, null, 0,0,0,0, false, "Barcode nenájdený"));
            }
        } catch (Exception ex) {
            log.warn("Chyba pri dekódovaní barcode: {}", ex.getMessage());
            results.add(new BarcodeResult(1, null, null, 0,0,0,0, false, "Chyba: " + ex.getMessage()));
        }
        return results;
    }

    /**
     * Dekóduje barcody pre každú etiketu zvlášť a vracia zoznam BarcodeResult.
     * (Použi toto ak máš etikety už rozseknuté na jednotlivé obrázky)
     */
    public List<BarcodeResult> decodeAllLabels(List<File> labelFiles, List<String> expectedCodes) {
        List<BarcodeResult> results = new ArrayList<>();
        int idx = 1;
        for (File labelFile : labelFiles) {
            BarcodeResult res = decodeSingleLabel(labelFile, idx, expectedCodes != null && expectedCodes.size() >= idx ? expectedCodes.get(idx - 1) : null);
            results.add(res);
            idx++;
        }
        return results;
    }

    /**
     * Dekóduje jeden obrázok etikety.
     */
    public BarcodeResult decodeSingleLabel(File imageFile, int labelIndex, String expectedCode) {
        try {
            BufferedImage img = ImageIO.read(imageFile);
            LuminanceSource source = new BufferedImageLuminanceSource(img);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Map<DecodeHintType, Object> hints = new HashMap<>();
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

            Result result = new MultiFormatReader().decode(bitmap, hints);

            boolean valid = expectedCode == null || expectedCode.equals(result.getText());
            String error = valid ? null : "Neočakávaný kód (očakávaný: " + expectedCode + ", získaný: " + result.getText() + ")";
            // Bounding box
            ResultPoint[] points = result.getResultPoints();
            int x = 0, y = 0, w = 0, h = 0;
            if (points != null && points.length >= 2) {
                x = (int) Math.min(points[0].getX(), points[1].getX());
                y = (int) Math.min(points[0].getY(), points[1].getY());
                w = (int) Math.abs(points[0].getX() - points[1].getX());
                h = img.getHeight();
            }

            return new BarcodeResult(labelIndex, result.getText(), result.getBarcodeFormat().toString(), x, y, w, h, valid, error);

        } catch (NotFoundException e) {
            return new BarcodeResult(labelIndex, null, null, 0,0,0,0, false, "Barcode nenájdený");
        } catch (Exception ex) {
            log.warn("Chyba pri dekódovaní barcode: {}", ex.getMessage());
            return new BarcodeResult(labelIndex, null, null, 0,0,0,0, false, "Chyba: " + ex.getMessage());
        }
    }
}

