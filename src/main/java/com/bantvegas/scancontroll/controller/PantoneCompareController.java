package com.bantvegas.scancontroll.controller;

import com.bantvegas.scancontroll.dto.PantoneCompareRequest;
import com.bantvegas.scancontroll.dto.PantoneCompareResponse;
import com.bantvegas.scancontroll.model.PantoneReport;
import com.bantvegas.scancontroll.service.PantoneService;
import com.bantvegas.scancontroll.service.PantoneReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/pantone")
public class PantoneCompareController {

    private final PantoneService pantoneService;
    private final PantoneReportService pantoneReportService;

    public PantoneCompareController(
            PantoneService pantoneService,
            PantoneReportService pantoneReportService
    ) {
        this.pantoneService = pantoneService;
        this.pantoneReportService = pantoneReportService;
    }

    @PostMapping("/compare")
    public PantoneCompareResponse compare(@RequestBody PantoneCompareRequest req) {
        // 1. Porovnanie Pantone (výpočet rozdielov)
        PantoneCompareResponse resp = pantoneService.compare(req);

        // 2. Uloženie do reportu na disk
        PantoneReport report = new PantoneReport();
        report.setReportType("PANTONE");
        report.setOperator(req.getOperator());
        report.setProductCode(req.getProductCode());
        report.setDatetime(req.getDatetime() != null && !req.getDatetime().isEmpty()
                ? req.getDatetime()
                : LocalDateTime.now().toString());
        report.setPantoneCode(req.getPantoneCode());
        report.setPantoneHex(resp.getPantoneHex());
        report.setRefR(resp.getRefR());
        report.setRefG(resp.getRefG());
        report.setRefB(resp.getRefB());
        report.setSampleR(resp.getSampleR());
        report.setSampleG(resp.getSampleG());
        report.setSampleB(resp.getSampleB());
        report.setDeltaE2000(resp.getDeltaE2000());
        report.setDeltaE76(resp.getDeltaE76());
        report.setRgbDistance(resp.getRgbDistance());
        report.setMatchPercent(resp.getMatchPercent());
        report.setRating(resp.getRating());

        // --- SERIALIZUJ NA STRING ---
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> details = new HashMap<>();
            details.put("pantoneCode", report.getPantoneCode());
            details.put("pantoneHex", report.getPantoneHex());
            details.put("refR", report.getRefR());
            details.put("refG", report.getRefG());
            details.put("refB", report.getRefB());
            details.put("sampleR", report.getSampleR());
            details.put("sampleG", report.getSampleG());
            details.put("sampleB", report.getSampleB());
            details.put("deltaE2000", report.getDeltaE2000());
            details.put("deltaE76", report.getDeltaE76());
            details.put("rgbDistance", report.getRgbDistance());
            details.put("matchPercent", report.getMatchPercent());
            details.put("rating", report.getRating());

            String detailsJson = objectMapper.writeValueAsString(details);
            report.setDetailsJson(detailsJson);
        } catch (Exception e) {
            // Fallback na prázdny objekt ak niečo zlyhá
            report.setDetailsJson("{}");
        }

        pantoneReportService.savePantoneReport(report); // Uloží report aj TXT

        // 3. Vráť odpoveď pre FE
        return resp;
    }
}


