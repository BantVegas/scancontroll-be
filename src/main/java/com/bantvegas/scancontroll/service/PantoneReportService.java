package com.bantvegas.scancontroll.service;

import com.bantvegas.scancontroll.model.PantoneReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Service
public class PantoneReportService {

    private static final String REPORTS_DIR = "C:/Users/lukac/Desktop/reporty/";

    public synchronized PantoneReport savePantoneReport(PantoneReport report) {
        long nextId = getNextId();
        report.setId(nextId);
        saveReportToTxt(report);
        return report;
    }

    // Vygeneruj ďalšie ID podľa počtu súborov
    private long getNextId() {
        File dir = new File(REPORTS_DIR);
        dir.mkdirs();
        String[] files = dir.list((d, name) -> name.startsWith("pantone_") && name.endsWith(".txt"));
        if (files == null || files.length == 0) return 1;
        return Arrays.stream(files)
                .map(f -> {
                    try {
                        return Long.parseLong(f.split("_")[1]);
                    } catch (Exception e) {
                        return 0L;
                    }
                })
                .max(Long::compare).orElse(0L) + 1;
    }

    // Ulož report ako TXT súbor na disk
    private void saveReportToTxt(PantoneReport report) {
        try {
            File dir = new File(REPORTS_DIR);
            dir.mkdirs();
            String filename = String.format("pantone_%d_%s.txt",
                    report.getId(),
                    sanitizeFileName(report.getPantoneCode() != null ? report.getPantoneCode() : "unknown")
            );
            File file = new File(dir, filename);

            try (FileWriter fw = new FileWriter(file, false)) {
                fw.write("--- Pantone Report ---\n");
                fw.write("ID: " + report.getId() + "\n");
                fw.write("Operátor: " + safe(report.getOperator()) + "\n");
                fw.write("Produkt: " + safe(report.getProductCode()) + "\n");
                fw.write("Čas: " + safe(report.getDatetime()) + "\n");
                fw.write("Pantone: " + safe(report.getPantoneCode()) + "\n");
                fw.write("PantoneHex: " + safe(report.getPantoneHex()) + "\n");
                fw.write("Výsledok: " + safe(report.getRating()) + "\n");
                fw.write("% Zhody: " + (report.getMatchPercent() != null ? report.getMatchPercent() : "-") + "\n");
                fw.write("RefR: " + safe(report.getRefR()) + "\n");
                fw.write("RefG: " + safe(report.getRefG()) + "\n");
                fw.write("RefB: " + safe(report.getRefB()) + "\n");
                fw.write("SampleR: " + safe(report.getSampleR()) + "\n");
                fw.write("SampleG: " + safe(report.getSampleG()) + "\n");
                fw.write("SampleB: " + safe(report.getSampleB()) + "\n");
                fw.write("DeltaE2000: " + (report.getDeltaE2000() != null ? report.getDeltaE2000() : "-") + "\n");
                fw.write("DeltaE76: " + (report.getDeltaE76() != null ? report.getDeltaE76() : "-") + "\n");
                fw.write("--------------------------\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("Chyba pri ukladaní Pantone TXT reportu", e);
        }
    }

    // Agregátor pre dashboard – vracia JSON pole map podľa dashboard štruktúry
    public List<Map<String, Object>> getAllPantoneReports() {
        List<Map<String, Object>> out = new ArrayList<>();
        File dir = new File(REPORTS_DIR);
        File[] files = dir.listFiles((d, name) -> name.startsWith("pantone_") && name.endsWith(".txt"));
        if (files == null) return out;

        ObjectMapper mapper = new ObjectMapper();

        for (File file : files) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                Map<String, Object> item = new HashMap<>();
                item.put("reportType", "PANTONE");

                String id = "", operator = "", productCode = "", datetime = "";
                String pantoneCode = "", pantoneHex = "", rating = "";
                Double matchPercent = null, deltaE2000 = null, deltaE76 = null;
                Integer refR = null, refG = null, refB = null, sampleR = null, sampleG = null, sampleB = null;

                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("ID:")) id = line.replace("ID:", "").trim();
                    if (line.startsWith("Operátor:")) operator = line.replace("Operátor:", "").trim();
                    if (line.startsWith("Produkt:")) productCode = line.replace("Produkt:", "").trim();
                    if (line.startsWith("Čas:")) datetime = line.replace("Čas:", "").trim();
                    if (line.startsWith("Pantone:")) pantoneCode = line.replace("Pantone:", "").trim();
                    if (line.startsWith("PantoneHex:")) pantoneHex = line.replace("PantoneHex:", "").trim();
                    if (line.startsWith("Výsledok:")) rating = line.replace("Výsledok:", "").trim();
                    if (line.startsWith("% Zhody:")) matchPercent = parseDoubleOrNull(line.replace("% Zhody:", "").trim());
                    if (line.startsWith("RefR:")) refR = parseIntOrNull(line.replace("RefR:", "").trim());
                    if (line.startsWith("RefG:")) refG = parseIntOrNull(line.replace("RefG:", "").trim());
                    if (line.startsWith("RefB:")) refB = parseIntOrNull(line.replace("RefB:", "").trim());
                    if (line.startsWith("SampleR:")) sampleR = parseIntOrNull(line.replace("SampleR:", "").trim());
                    if (line.startsWith("SampleG:")) sampleG = parseIntOrNull(line.replace("SampleG:", "").trim());
                    if (line.startsWith("SampleB:")) sampleB = parseIntOrNull(line.replace("SampleB:", "").trim());
                    if (line.startsWith("DeltaE2000:")) deltaE2000 = parseDoubleOrNull(line.replace("DeltaE2000:", "").trim());
                    if (line.startsWith("DeltaE76:")) deltaE76 = parseDoubleOrNull(line.replace("DeltaE76:", "").trim());
                }

                item.put("id", id.isEmpty() ? null : Long.parseLong(id));
                item.put("operator", operator);
                item.put("productCode", productCode);
                item.put("datetime", datetime);

                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("pantoneCode", pantoneCode);
                detail.put("pantoneHex", pantoneHex);
                detail.put("rating", rating);
                detail.put("matchPercent", matchPercent);
                detail.put("refR", refR);
                detail.put("refG", refG);
                detail.put("refB", refB);
                detail.put("sampleR", sampleR);
                detail.put("sampleG", sampleG);
                detail.put("sampleB", sampleB);
                detail.put("deltaE2000", deltaE2000);
                detail.put("deltaE76", deltaE76);

                item.put("detailsJson", mapper.writeValueAsString(detail));
                out.add(item);

            } catch (Exception e) {
                // log.error("Chyba čítania pantone reportu: " + file.getName());
            }
        }
        return out;
    }

    // Helpery
    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String safe(Object s) {
        return s == null ? "-" : s.toString();
    }
    private Integer parseIntOrNull(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }
    private Double parseDoubleOrNull(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
    }
}
