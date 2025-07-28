package com.bantvegas.scancontroll.service;

import com.bantvegas.scancontroll.model.LabelResult;
import com.bantvegas.scancontroll.model.OcrLineResult;
import com.bantvegas.scancontroll.model.BarcodeResult;
import com.bantvegas.scancontroll.model.FakeOcrError;
import com.bantvegas.scancontroll.util.AcceptedErrorsUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrComparisonService {

    private final OcrService ocrService;
    private final BarcodeService barcodeService;

    /**
     * Normalizuje text pre fuzzy porovnávanie (odstráni špeciálne znaky, upper case).
     */
    private String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("[^\\p{L}\\p{Nd}]", "").toUpperCase().trim();
    }

    /**
     * Percentuálna zhoda dvoch textov (0-100).
     */
    private int similarityPercent(String a, String b) {
        a = normalize(a);
        b = normalize(b);
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 100;
        int dist = LevenshteinDistance.getDefaultInstance().apply(a, b);
        return 100 - (dist * 100 / maxLen);
    }

    /**
     * Vráti true, ak je daná kombinácia masterText/scanText označená operátorom ako fake chyba.
     */
    private boolean isFakeOcrError(String masterText, String scanText, String productNumber) {
        String hash = DigestUtils.sha256Hex(masterText + "||" + scanText);
        String dirPath = "data/master/" + productNumber;
        List<FakeOcrError> exist = AcceptedErrorsUtil.loadFakeErrors(dirPath);
        return exist.stream().anyMatch(e -> hash.equals(e.getHash()));
    }

    /**
     * Porovná master etiketu s každou etiketou z pásu RIADOK PO RIADKU.
     * Vracia bounding box pre každý riadok kde similarity < threshold a NIE JE fake error.
     */
    public List<LabelResult> compareAllLabels(File masterFile, List<File> labelFiles, String productNumber) {
        List<LabelResult> results = new ArrayList<>();
        final int SIM_THRESHOLD = 65; // uprav podľa potreby

        // OCR riadky z master etikety
        List<OcrLineResult> masterLines = ocrService.extractLinesWithBoxes(masterFile);

        for (int idx = 0; idx < labelFiles.size(); idx++) {
            File label = labelFiles.get(idx);
            List<OcrLineResult> scanLines = ocrService.extractLinesWithBoxes(label);
            List<OcrLineResult> ocrErrors = new ArrayList<>();

            // Spáruj vždy podľa indexu (jednoduchá stratégia, môžeš vylepšiť podľa potreby)
            int n = Math.min(masterLines.size(), scanLines.size());
            for (int i = 0; i < n; i++) {
                String masterText = masterLines.get(i).scanText;
                String scanText = scanLines.get(i).scanText;
                int sim = similarityPercent(masterText, scanText);

                // Chyba ak similarity pod threshold a nie je fake error
                if (sim < SIM_THRESHOLD && !isFakeOcrError(masterText, scanText, productNumber)) {
                    OcrLineResult err = new OcrLineResult(
                            i,
                            masterText,
                            scanText,
                            scanLines.get(i).x,
                            scanLines.get(i).y,
                            scanLines.get(i).w,
                            scanLines.get(i).h,
                            sim,
                            true,
                            scanLines.get(i).angle
                    );
                    ocrErrors.add(err);
                }
            }

            // Ak etiketa extrémne krátka/nečitateľná – globálna chyba
            if (scanLines.size() < 3) {
                ocrErrors.add(new OcrLineResult(
                        0, "", "❌ Etiketa je poškodená alebo nečitateľná (málo textu/riadkov)!",
                        0, 0, 0, 0, 0, true
                ));
            }

            // Čiarové kódy
            List<BarcodeResult> barcodes = barcodeService.decodeAllBarcodes(label);
            results.add(new LabelResult(idx + 1, ocrErrors, barcodes));
        }

        return results;
    }
}








