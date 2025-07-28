package com.bantvegas.scancontroll.service;

import com.bantvegas.scancontroll.model.OcrLine;
import com.bantvegas.scancontroll.util.TextUtils;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OcrDiffService {

    public List<OcrDiffResult> compareEtiketaWithMaster(
            List<OcrLine> masterLines,
            List<OcrLine> etiketaLines,
            double similarityThreshold
    ) {
        List<OcrDiffResult> diffs = new ArrayList<>();

        // Index podľa poradia, ale vieš použiť aj lepšie párovanie (podľa similarity)
        int n = Math.max(masterLines.size(), etiketaLines.size());
        for (int i = 0; i < n; i++) {
            OcrLine master = i < masterLines.size() ? masterLines.get(i) : null;
            OcrLine etiketa = i < etiketaLines.size() ? etiketaLines.get(i) : null;

            if (master != null && etiketa != null) {
                if (!TextUtils.isSimilar(master.text, etiketa.text, similarityThreshold)) {
                    OcrDiffResult diff = new OcrDiffResult();
                    diff.lineIndex = etiketa.index;
                    diff.masterText = master.text;
                    diff.etiketaText = etiketa.text;
                    diff.x = etiketa.x;
                    diff.y = etiketa.y;
                    diff.w = etiketa.w;
                    diff.h = etiketa.h;
                    diff.type = "DIFFERENT";
                    diffs.add(diff);
                }
            } else if (master != null) {
                // Chýba v etikete
                OcrDiffResult diff = new OcrDiffResult();
                diff.lineIndex = master.index;
                diff.masterText = master.text;
                diff.etiketaText = "";
                diff.x = master.x;
                diff.y = master.y;
                diff.w = master.w;
                diff.h = master.h;
                diff.type = "MISSING";
                diffs.add(diff);
            } else if (etiketa != null) {
                // Pridané v etikete navyše
                OcrDiffResult diff = new OcrDiffResult();
                diff.lineIndex = etiketa.index;
                diff.masterText = "";
                diff.etiketaText = etiketa.text;
                diff.x = etiketa.x;
                diff.y = etiketa.y;
                diff.w = etiketa.w;
                diff.h = etiketa.h;
                diff.type = "EXTRA";
                diffs.add(diff);
            }
        }
        return diffs;
    }

    @Data
    public static class OcrDiffResult {
        private int lineIndex;
        private String masterText;
        private String etiketaText;
        private int x, y, w, h;
        private String type; // DIFFERENT / MISSING / EXTRA
    }
}

