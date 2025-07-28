package com.bantvegas.scancontroll.service;

import com.bantvegas.scancontroll.model.ScanReport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Service
public class ScanReportService {

    private static final String REPORTS_PATH = "reports/scanreports.json";
    private final ObjectMapper mapper = new ObjectMapper();

    private File getFile() {
        File file = new File(REPORTS_PATH);
        file.getParentFile().mkdirs();
        return file;
    }

    public synchronized List<ScanReport> getAllReports() {
        File file = getFile();
        if (!file.exists()) return new ArrayList<>();
        try {
            List<ScanReport> reports = mapper.readValue(file, new TypeReference<List<ScanReport>>() {});
            // Najnovšie prvé:
            reports.sort(Comparator.comparingLong(r -> -1L * Optional.ofNullable(r.getId()).orElse(0L)));
            return reports;
        } catch (IOException e) {
            throw new RuntimeException("Chyba pri čítaní reportov", e);
        }
    }

    public synchronized ScanReport saveReport(ScanReport report) {
        List<ScanReport> all = getAllReports();
        // Priraď nové ID:
        long nextId = all.stream().map(ScanReport::getId).filter(Objects::nonNull).max(Long::compare).orElse(0L) + 1;
        report.setId(nextId);
        all.add(report);
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(getFile(), all);
        } catch (IOException e) {
            throw new RuntimeException("Chyba pri ukladaní reportu", e);
        }
        return report;
    }

    public synchronized Optional<ScanReport> getReport(Long id) {
        return getAllReports().stream().filter(r -> Objects.equals(r.getId(), id)).findFirst();
    }

    public synchronized void deleteReport(Long id) {
        List<ScanReport> all = getAllReports();
        boolean removed = all.removeIf(r -> Objects.equals(r.getId(), id));
        if (removed) {
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(getFile(), all);
            } catch (IOException e) {
                throw new RuntimeException("Chyba pri ukladaní po mazaní", e);
            }
        }
    }
}

