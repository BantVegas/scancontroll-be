package com.bantvegas.scancontroll.util;

import org.apache.commons.text.similarity.LevenshteinDistance;

public class TextUtils {

    /**
     * Porovná dva texty s toleranciou.
     * @param a Prvý text (napr. OCR z master etikety)
     * @param b Druhý text (napr. OCR z etikety na páse)
     * @param threshold Percentuálna zhoda, napr. 0.95 (95%) = považuj za rovnaké
     * @return true ak je zhoda vyššia alebo rovná threshold
     */
    public static boolean isSimilar(String a, String b, double threshold) {
        a = (a == null ? "" : a).replaceAll("[^\\p{L}\\p{Nd}]", "").toUpperCase();
        b = (b == null ? "" : b).replaceAll("[^\\p{L}\\p{Nd}]", "").toUpperCase();

        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return true; // oboje prázdne = rovnaké

        int distance = LevenshteinDistance.getDefaultInstance().apply(a, b);
        double similarity = 1.0 - (double) distance / maxLen;
        return similarity >= threshold; // napr. threshold = 0.95 (95%)
    }
}
