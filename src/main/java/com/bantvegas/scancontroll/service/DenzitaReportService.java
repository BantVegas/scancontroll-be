package com.bantvegas.scancontroll.service;

import com.bantvegas.scancontroll.model.DenzitaReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Service
public class DenzitaReportService {

    private static final String REPORTS_DIR = "C:/Users/lukac/Desktop/reporty/";

    public synchronized DenzitaReport saveDenzitaReport(DenzitaReport report) {
        long nextId = getNextId();
        report.setId(nextId);
        saveReportToTxt(report);
        return report;
    }

    private long getNextId() {
        File dir = new File(REPORTS_DIR);
        dir.mkdirs();
        String[] files = dir.list((d, name) -> name.startsWith("denzita_") && name.endsWith(".txt"));
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

    private void saveReportToTxt(DenzitaReport report) {
        try {
            File dir = new File(REPORTS_DIR);
            dir.mkdirs();
            String filename = String.format("denzita_%d_%s.txt",
                    report.getId(),
                    sanitizeFileName(report.getProductCode() != null ? report.getProductCode() : "unknown")
            );
            File file = new File(dir, filename);

            try (FileWriter fw = new FileWriter(file, false)) {
                fw.write("--- Denzita Report ---\n");
                fw.write("ID: " + report.getId() + "\n");
                fw.write("Operátor: " + safe(report.getOperator()) + "\n");
                fw.write("Produkt: " + safe(report.getProductCode()) + "\n");
                fw.write("Čas: " + safe(report.getDatetime()) + "\n");
                fw.write("C: " + safe(report.getCyan()) + "\n");
                fw.write("M: " + safe(report.getMagenta()) + "\n");
                fw.write("Y: " + safe(report.getYellow()) + "\n");
                fw.write("K: " + safe(report.getBlack()) + "\n");
                fw.write("Vyhodnotenie: " + safe(report.getSummary()) + "\n");
                fw.write("--------------------------\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("Chyba pri ukladaní Denzita TXT reportu", e);
        }
    }

    // Nová metóda: vráti List<Map<String, Object>> pre agregátor/dashboard
    public List<Map<String, Object>> getAllDenzitaReports() {
        List<Map<String, Object>> out = new ArrayList<>();
        File dir = new File(REPORTS_DIR);
        File[] files = dir.listFiles((d, name) -> name.startsWith("denzita_") && name.endsWith(".txt"));
        if (files == null) return out;

        ObjectMapper mapper = new ObjectMapper();

        for (File file : files) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                Map<String, Object> item = new HashMap<>();
                item.put("reportType", "DENZITA");

                double cyan = 0, magenta = 0, yellow = 0, black = 0;
                String vyhodnotenie = "";
                String id = "";
                String operator = "";
                String productCode = "";
                String datetime = "";

                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("ID:")) id = line.replace("ID:", "").trim();
                    if (line.startsWith("Operátor:")) operator = line.replace("Operátor:", "").trim();
                    if (line.startsWith("Produkt:")) productCode = line.replace("Produkt:", "").trim();
                    if (line.startsWith("Čas:")) datetime = line.replace("Čas:", "").trim();
                    if (line.startsWith("C:")) cyan = parse(line.replace("C:", "").trim());
                    if (line.startsWith("M:")) magenta = parse(line.replace("M:", "").trim());
                    if (line.startsWith("Y:")) yellow = parse(line.replace("Y:", "").trim());
                    if (line.startsWith("K:")) black = parse(line.replace("K:", "").trim());
                    if (line.startsWith("Vyhodnotenie:")) vyhodnotenie = line.replace("Vyhodnotenie:", "").trim();
                }

                item.put("id", id.isEmpty() ? null : Long.parseLong(id));
                item.put("operator", operator);
                item.put("productCode", productCode);
                item.put("datetime", datetime);

                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("cyan", cyan);
                detail.put("magenta", magenta);
                detail.put("yellow", yellow);
                detail.put("black", black);
                detail.put("summary", vyhodnotenie);

                item.put("detailsJson", mapper.writeValueAsString(detail));
                out.add(item);

            } catch (IOException ex) {
                // prípadne logovať error file.getName()
            }
        }
        return out;
    }

    private double parse(String s) {
        try {
            return Double.parseDouble(s.replace(",", "."));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
    private String safe(Object s) {
        return s == null ? "-" : s.toString();
    }
}

