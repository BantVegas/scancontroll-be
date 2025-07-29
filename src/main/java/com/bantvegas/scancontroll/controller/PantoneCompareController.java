package com.bantvegas.scancontroll.controller;

import com.bantvegas.scancontroll.dto.PantoneCompareRequest;
import com.bantvegas.scancontroll.dto.PantoneCompareResponse;
import com.bantvegas.scancontroll.model.PantoneReport;
import com.bantvegas.scancontroll.service.PantoneService;
import com.bantvegas.scancontroll.service.PantoneReportService;
import org.springframework.web.bind.annotation.*;

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
        report.setOperator(req.getOperator());
        report.setProductCode(req.getProductCode());
        report.setDatetime(req.getDatetime());
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

        pantoneReportService.savePantoneReport(report); // ← tu sa uloží aj TXT

        // 3. Vráť odpoveď pre FE
        return resp;
    }
}


