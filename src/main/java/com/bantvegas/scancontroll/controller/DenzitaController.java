package com.bantvegas.scancontroll.controller;

import com.bantvegas.scancontroll.model.DenzitaReport;
import com.bantvegas.scancontroll.service.DenzitaReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class DenzitaController {

    // Cesta k master etiketam na disku
    private static final String MASTER_BASE_PATH = "C:/Users/lukac/Desktop/Master/";

    private static final String DENZITA_SCRIPT_PATH =
            System.getProperty("user.dir") + File.separator +
                    "python" + File.separator +
                    "ai_denzita_single.py";

    private final DenzitaReportService denzitaReportService;

    // ===== 1) POROVNANIE (upload + python) =====
    @PostMapping(
            path = "/denzita-compare",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> compareDenzita(
            @RequestParam("etiketa") MultipartFile etiketaFile,
            @RequestParam(value = "productNumber", required = false) String productNumber,
            @RequestParam(value = "master", required = false) MultipartFile masterUpload,
            @RequestParam(value = "operator", required = false) String operator
    ) {
        File etiketaTmp = null;
        File masterTmp = null;
        try {
            if (etiketaFile == null || etiketaFile.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Etiketa (etiketaFile) musí byť uploadnutá"));
            }

            // --- master: buď uploadnutý, alebo podľa productNumber ---
            File masterFileToUse = null;

            // 1. Priorita: uploadnutý master (cez FE)
            if (masterUpload != null && !masterUpload.isEmpty()) {
                masterTmp = File.createTempFile("denz-master-", ".png");
                masterUpload.transferTo(masterTmp);
                masterFileToUse = masterTmp;
            }
            // 2. Priorita: master z disku podľa productNumber
            else if (productNumber != null && !productNumber.isBlank()) {
                File diskMaster = new File(MASTER_BASE_PATH + productNumber + File.separator + "master.png");
                if (!diskMaster.exists()) {
                    log.error("❌ Master etiketa pre produkt {} neexistuje: {}", productNumber, diskMaster.getAbsolutePath());
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Master etiketa pre tento produkt neexistuje"));
                }
                masterFileToUse = diskMaster;
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Chýba master etiketa: musíš nahrať alebo zadať číslo produktu"));
            }

            etiketaTmp = File.createTempFile("denz-etiketa-", ".png");
            etiketaFile.transferTo(etiketaTmp);

            Map<String, Object> result = callDenzitaPython(masterFileToUse, etiketaTmp);

            // uloženie iba ak máme všetky kanály
            if (hasAllChannels(result)) {
                DenzitaReport report = new DenzitaReport();
                report.setOperator(operator);
                report.setProductCode(productNumber != null ? productNumber : "UPLOAD");
                report.setDatetime(nowString());
                report.setCyan(extractRel(result.get("cyan")));
                report.setMagenta(extractRel(result.get("magenta")));
                report.setYellow(extractRel(result.get("yellow")));
                report.setBlack(extractRel(result.get("black")));
                report.setSummary("OK");
                denzitaReportService.saveDenzitaReport(report);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ Chyba v denzita-compare", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Chyba backendu: " + e.getMessage()));
        } finally {
            if (etiketaTmp != null && etiketaTmp.exists()) etiketaTmp.delete();
            if (masterTmp != null && masterTmp.exists()) masterTmp.delete();
        }
    }

    // ===== 2) PRIAME ULOŽENIE REPORTU Z FE (JSON) =====
    @PostMapping(
            path = "/denzita-report",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> saveDenzitaReport(@RequestBody DenzitaReportDto dto) {
        if (dto == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Chýbajúce dáta reportu"));
        }

        DenzitaReport r = new DenzitaReport();
        r.setOperator(dto.getOperator());
        r.setProductCode(dto.getProductCode());
        r.setDatetime(dto.getDatetime() != null ? dto.getDatetime() : nowString());
        r.setCyan(dto.getCyan());
        r.setMagenta(dto.getMagenta());
        r.setYellow(dto.getYellow());
        r.setBlack(dto.getBlack());
        r.setSummary(dto.getSummary() != null ? dto.getSummary() : "OK");

        denzitaReportService.saveDenzitaReport(r);
        return ResponseEntity.ok(Map.of("status", "saved", "id", r.getId()));
    }

    // ===== 3) DASHBOARD GET - ČÍTANIE Denzita reportov z TXT na disku =====
    @GetMapping("/report/denzita")
    public List<Map<String, Object>> getAllDenzitaReports() throws IOException {
        File dir = new File("data/reports");
        if (!dir.exists()) dir.mkdirs();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".txt"));
        List<Map<String, Object>> out = new ArrayList<>();
        int id = 1;
        if (files != null) {
            for (File f : files) {
                List<String> lines = Files.readAllLines(f.toPath());
                Map<String, Object> item = new HashMap<>();
                item.put("id", id++);
                item.put("reportType", "DENZITA");
                item.put("operator", "");
                item.put("productCode", "");
                item.put("datetime", "");
                item.put("detailsJson", "");

                // ---- Parsovanie riadkov ----
                double cyan = 0, magenta = 0, yellow = 0, black = 0;
                String vyhodnotenie = "";
                for (String line : lines) {
                    if (line.startsWith("Operátor:")) {
                        item.put("operator", line.replace("Operátor:", "").trim());
                    }
                    if (line.startsWith("Produkt:")) {
                        item.put("productCode", line.replace("Produkt:", "").trim());
                    }
                    if (line.startsWith("Čas:")) {
                        item.put("datetime", line.replace("Čas:", "").trim());
                    }
                    if (line.startsWith("C:")) {
                        cyan = Double.parseDouble(line.replace("C:", "").trim().replace(",", "."));
                    }
                    if (line.startsWith("M:")) {
                        magenta = Double.parseDouble(line.replace("M:", "").trim().replace(",", "."));
                    }
                    if (line.startsWith("Y:")) {
                        yellow = Double.parseDouble(line.replace("Y:", "").trim().replace(",", "."));
                    }
                    if (line.startsWith("K:")) {
                        black = Double.parseDouble(line.replace("K:", "").trim().replace(",", "."));
                    }
                    if (line.startsWith("Vyhodnotenie:")) {
                        vyhodnotenie = line.replace("Vyhodnotenie:", "").trim();
                    }
                }
                // Vytvor JSON string s detailmi pre FE
                Map<String, Object> detail = new HashMap<>();
                detail.put("cyan", cyan);
                detail.put("magenta", magenta);
                detail.put("yellow", yellow);
                detail.put("black", black);
                detail.put("summary", vyhodnotenie);

                item.put("detailsJson", new ObjectMapper().writeValueAsString(detail));

                out.add(item);
            }
        }
        return out;
    }

    // ========= HELPERS =========

    private Map<String, Object> callDenzitaPython(File master, File etiketa) throws IOException, InterruptedException {
        String os = System.getProperty("os.name").toLowerCase();
        String pythonExec = os.contains("win") ? "python" : "python3";

        ProcessBuilder pb = new ProcessBuilder(
                pythonExec,
                DENZITA_SCRIPT_PATH,
                master.getAbsolutePath(),
                etiketa.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (BufferedReader rd = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) out.append(line);
            int code = p.waitFor();

            if (code != 0) {
                log.warn("AI denzita skript skončil s kódom {} výstup: {}", code, out);
                return Map.of("error", "AI denzita error", "detail", out.toString());
            }
            ObjectMapper m = new ObjectMapper();
            String s = out.toString().trim();
            if (s.startsWith("[")) {
                List<?> lst = m.readValue(s, List.class);
                return Map.of("data", lst);
            }
            return m.readValue(s, Map.class);
        }
    }

    private boolean hasAllChannels(Map<String, Object> res) {
        return res != null &&
                res.containsKey("cyan") &&
                res.containsKey("magenta") &&
                res.containsKey("yellow") &&
                res.containsKey("black");
    }

    /** Vytiahne číslo z mapy kanála (očakáva kľúč "rel_rozdiel"). */
    private Double extractRel(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Map<?, ?> map) {
            Object v = map.get("rel_rozdiel");
            return toDouble(v);
        }
        // fallback – ak by to nebola mapa
        return toDouble(obj);
    }

    private static Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String nowString() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    // DTO pre JSON ukladanie – UPRAVENÉ!
    @Data
    public static class DenzitaReportDto {
        private String operator;
        private String productCode;
        private String datetime;
        private Double cyan;
        private Double magenta;
        private Double yellow;
        private Double black;
        private String summary;
        // reportType nie je povinný
    }
}






