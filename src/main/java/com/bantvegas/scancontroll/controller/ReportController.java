package com.bantvegas.scancontroll.controller;

import com.bantvegas.scancontroll.service.DenzitaReportService;
import com.bantvegas.scancontroll.service.PantoneReportService;
import com.bantvegas.scancontroll.service.CompareReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final DenzitaReportService denzitaService;
    private final PantoneReportService pantoneService;
    private final CompareReportService compareService;

    @GetMapping
    public List<Map<String, Object>> getAllReports() {
        List<Map<String, Object>> all = new ArrayList<>();

        // Každý servis MUSÍ vrátiť List<Map<String, Object>>
        try {
            List<Map<String, Object>> denzita = denzitaService.getAllDenzitaReports();
            if (denzita != null) all.addAll(denzita);
        } catch (Exception ex) {
            System.err.println("Chyba v DenzitaReportService: " + ex.getMessage());
        }

        try {
            List<Map<String, Object>> pantone = pantoneService.getAllPantoneReports();
            if (pantone != null) all.addAll(pantone);
        } catch (Exception ex) {
            System.err.println("Chyba v PantoneReportService: " + ex.getMessage());
        }

        try {
            List<Map<String, Object>> compare = compareService.getAllCompareReports();
            if (compare != null) all.addAll(compare);
        } catch (Exception ex) {
            System.err.println("Chyba v CompareReportService: " + ex.getMessage());
        }

        // Zoradenie podľa datetime (od najnovšieho)
        all.sort((a, b) -> {
            String dtA = Objects.toString(a.get("datetime"), "");
            String dtB = Objects.toString(b.get("datetime"), "");
            return dtB.compareTo(dtA);
        });

        return all;
    }
}


