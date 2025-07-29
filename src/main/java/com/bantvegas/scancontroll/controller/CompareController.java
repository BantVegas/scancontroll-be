package com.bantvegas.scancontroll.controller;

import com.bantvegas.scancontroll.model.BarcodeResult;
import com.bantvegas.scancontroll.model.LabelResult;
import com.bantvegas.scancontroll.model.OcrLineResult;
import com.bantvegas.scancontroll.service.BarcodeService;
import com.bantvegas.scancontroll.service.OcrComparisonService;
import com.bantvegas.scancontroll.service.OcrService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.Base64;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class CompareController {

    private final BarcodeService barcodeService;
    private final OcrService ocrService;
    private final OcrComparisonService ocrComparisonService;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DENZITA_SCRIPT_PATH =
            System.getProperty("user.dir") + File.separator +
                    "python" + File.separator +
                    "ai_denzita_single.py";

    // ======== MASTER LOAD ENDPOINT ========
    @GetMapping("/master/load")
    public ResponseEntity<?> loadMaster(@RequestParam("productNumber") String productNumber) {
        try {
            File masterDir = new File("data/master/" + productNumber);
            if (!masterDir.exists() || !masterDir.isDirectory())
                return ResponseEntity.status(404).body(Map.of("error", "Master adresár neexistuje: " + masterDir.getAbsolutePath()));

            File img = new File(masterDir, "master.png");
            File meta = new File(masterDir, "params.json");
            if (!img.exists() || !meta.exists())
                return ResponseEntity.status(404).body(Map.of("error", "master.png alebo params.json neexistuje"));

            Map<String, Object> metaObj = MAPPER.readValue(meta, Map.class);

            byte[] imgBytes = Files.readAllBytes(img.toPath());
            String b64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(imgBytes);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("pngBase64", b64);
            resp.put("meta", metaObj);

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ======== MASTER SAVE ENDPOINT ========
    @PostMapping(value = "/master/save", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> saveMaster(
            @RequestParam("productNumber") String productNumber,
            @RequestParam("master") MultipartFile masterFile,
            @RequestParam("params") String paramsJson
    ) {
        try {
            File masterDir = new File("data/master/" + productNumber);
            if (!masterDir.exists()) {
                boolean ok = masterDir.mkdirs();
                if (!ok) {
                    return ResponseEntity.status(500)
                            .body(Map.of("error", "Nepodarilo sa vytvoriť adresár: " + masterDir.getAbsolutePath()));
                }
            }

            File img = new File(masterDir, "master.png");
            masterFile.transferTo(img);

            File meta = new File(masterDir, "params.json");
            Files.writeString(meta.toPath(), paramsJson);

            log.info("ULOŽENÉ: " + img.getAbsolutePath());
            log.info("ULOŽENÉ: " + meta.getAbsolutePath());

            return ResponseEntity.ok(Map.of("message", "Master etiketa uložená"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ======== POROVNANIE (SCAN VS MASTER) ========
    @PostMapping(
            path = "/compare",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> compare(
            @RequestParam("master") MultipartFile masterFile,
            @RequestParam("scan") MultipartFile scanFile,
            @RequestParam("wind") String wind,
            @RequestParam("rows") Integer rows,
            @RequestParam("cols") Integer cols,
            @RequestParam("labelWidthPx") Integer labelWidthPx,
            @RequestParam("labelHeightPx") Integer labelHeightPx,
            @RequestParam("horizontalGapMm") String horizontalGapMm,
            @RequestParam("verticalGapMm") String verticalGapMm,
            @RequestParam("dpi") Integer dpi,
            @RequestParam(value = "operatorName", required = false) String operatorName,
            @RequestParam(value = "orderNumber", required = false) String orderNumber,
            @RequestParam(value = "productNumber", required = false) String productNumberRaw,
            @RequestParam(value = "spoolNumber", required = false) String spoolNumber,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "ackBoxes", required = false) String ackBoxesJson
    ) {
        String productNumber = safeProductNumber(productNumberRaw);

        File stripTmp = null;
        File masterTmp = null;
        List<File> labelFiles = new ArrayList<>();

        try {
            int totalLabels = rows * cols;
            stripTmp = File.createTempFile("strip-", ".png");
            scanFile.transferTo(stripTmp);
            BufferedImage stripImg = ImageIO.read(stripTmp);

            masterTmp = File.createTempFile("master-", ".png");
            masterFile.transferTo(masterTmp);
            BufferedImage masterImg = ImageIO.read(masterTmp);

            BufferedImage masterCrop = masterImg;
            if (masterImg.getWidth() > 8 && masterImg.getHeight() > 8) {
                masterCrop = masterImg.getSubimage(
                        4, 4,
                        masterImg.getWidth() - 8,
                        masterImg.getHeight() - 8
                );
            }

            // ---- BARCODE MASTER ----
            List<BarcodeResult> masterCodes = barcodeService.decodeAllBarcodes(masterCrop);
            String expectedBarcode = (masterCodes != null && !masterCodes.isEmpty() && masterCodes.get(0).getValue() != null)
                    ? masterCodes.get(0).getValue()
                    : null;

            int gapXPx = (int) Math.round(Double.parseDouble(horizontalGapMm) / 25.4 * dpi);
            int gapYPx = (int) Math.round(Double.parseDouble(verticalGapMm) / 25.4 * dpi);

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int x0 = c * (labelWidthPx + gapXPx);
                    int y0 = r * (labelHeightPx + gapYPx);
                    BufferedImage cell = safeSubimage(stripImg, x0, y0, labelWidthPx, labelHeightPx);
                    File tmp = File.createTempFile("label-", ".png");
                    ImageIO.write(cell, "png", tmp);
                    labelFiles.add(tmp);
                }
            }

            // Accepted errors
            Set<String> acceptedErrors = new HashSet<>();
            if (productNumber != null && !productNumber.isBlank()) {
                File ackFile = new File("data/master/" + productNumber + "/accepted_errors.json");
                if (ackFile.exists()) {
                    try {
                        String ackJson = Files.readString(ackFile.toPath()).trim();
                        if (!ackJson.isEmpty()) {
                            List<String> ackList = MAPPER.readValue(ackJson, List.class);
                            acceptedErrors.addAll(ackList);
                        }
                    } catch (Exception ex) {
                        log.warn("Chyba pri načítaní accepted_errors.json: {}", ex.getMessage());
                    }
                }
            }

            // Detekcia navinu
            String detectedWind = detectWindByOcr(labelFiles.get(0));
            String expectedWind = wind;
            String windResult;
            String windDetail;
            if (detectedWind == null) {
                windResult = "CHYBA";
                windDetail = "Nepodarilo sa zistiť orientáciu etikiet (navin) cez OCR!";
            } else if (!detectedWind.equalsIgnoreCase(expectedWind)) {
                windResult = "CHYBA";
                windDetail = "Etikety na páse sú otočené nesprávne (detekované: " +
                        detectedWind + ", očakávané: " + expectedWind + ")";
            } else {
                windResult = "OK";
                windDetail = "Navin etikiet je správny (" + expectedWind + ")";
            }

            // OCR porovnanie
            List<LabelResult> labelResults = ocrComparisonService.compareAllLabels(masterTmp, labelFiles, expectedBarcode);

            // ---- BARCODE DATA ----
            List<Map<String, Object>> barcodeData = new ArrayList<>();
            int missing = 0;
            boolean allValid = true;
            String barcodeMsg;

            if (expectedBarcode == null) {
                for (int i = 0; i < labelFiles.size(); i++) {
                    Map<String, Object> br = new LinkedHashMap<>();
                    br.put("index", i + 1);
                    br.put("text", null);
                    br.put("format", null);
                    br.put("valid", false);
                    br.put("error", "Barcode neočakáva sa");
                    br.put("points", List.of());
                    barcodeData.add(br);
                }
                barcodeMsg = "Bez čiarového kódu (neočakáva sa)";
            } else {
                for (LabelResult lr : labelResults) {
                    Map<String, Object> br = new LinkedHashMap<>();
                    BarcodeResult bc = (lr.barcodes != null && !lr.barcodes.isEmpty()) ? lr.barcodes.get(0) : null;
                    if (bc == null || bc.getValue() == null) {
                        missing++;
                        allValid = false;
                        br.put("index", lr.index);
                        br.put("text", null);
                        br.put("format", null);
                        br.put("valid", false);
                        br.put("error", "Bez čiarového kódu");
                        br.put("points", List.of());
                    } else {
                        boolean valid = expectedBarcode.equals(bc.getValue());
                        if (!valid) allValid = false;
                        List<Map<String, Integer>> pts = List.of(
                                Map.of("x", bc.getX(), "y", bc.getY()),
                                Map.of("x", bc.getX() + bc.getW(), "y", bc.getY()),
                                Map.of("x", bc.getX() + bc.getW(), "y", bc.getY() + bc.getH()),
                                Map.of("x", bc.getX(), "y", bc.getY() + bc.getH())
                        );
                        br.put("index", lr.index);
                        br.put("text", bc.getValue());
                        br.put("format", bc.getFormat());
                        br.put("valid", valid);
                        br.put("points", pts);
                        br.put("error", valid ? null : "Nesprávny kód");
                    }
                    barcodeData.add(br);
                }

                if (missing == labelFiles.size()) {
                    barcodeMsg = "Bez čiarového kódu";
                } else if (missing > 0) {
                    barcodeMsg = "❌ Nájdených kódov: " + (labelFiles.size() - missing) + " z " + labelFiles.size();
                } else if (allValid) {
                    barcodeMsg = "✅ Všetky kódy správne (" + labelFiles.size() + "/" + labelFiles.size() + ")";
                } else {
                    barcodeMsg = "❌ Nie všetky kódy sú platné";
                }
            }

            // OCR data
            List<Map<String, Object>> ocrData = new ArrayList<>();
            boolean hasOcrError = false;
            for (LabelResult lr : labelResults) {
                for (OcrLineResult o : lr.ocrErrors) {
                    String hash = getOcrErrorHash(lr.index, o);
                    boolean isAccepted = acceptedErrors.contains(hash);
                    boolean isError = o.error && !isAccepted;
                    if (isError) hasOcrError = true;

                    Map<String, Object> ocrMap = new HashMap<>();
                    ocrMap.put("labelIndex", lr.index);
                    ocrMap.put("lineIdx", o.lineIdx);
                    ocrMap.put("masterText", o.masterText);
                    ocrMap.put("scanText", o.scanText);
                    ocrMap.put("x", o.x);
                    ocrMap.put("y", o.y);
                    ocrMap.put("w", o.w);
                    ocrMap.put("h", o.h);
                    ocrMap.put("similarity", o.similarity);
                    ocrMap.put("error", isError ? ("Text rozdiel: " + o.masterText + " vs. " + o.scanText)
                            : isAccepted ? "ACCEPTED" : null);
                    ocrMap.put("hash", hash);
                    ocrData.add(ocrMap);
                }
            }

            String ocrSummary = hasOcrError ? "Chyba" : "OK";
            if (ocrData.isEmpty()) {
                Map<String, Object> ocrOK = new HashMap<>();
                ocrOK.put("labelIndex", 0);
                ocrOK.put("lineIdx", 0);
                ocrOK.put("masterText", "");
                ocrOK.put("scanText", "OK");
                ocrOK.put("x", 0);
                ocrOK.put("y", 0);
                ocrOK.put("w", 0);
                ocrOK.put("h", 0);
                ocrOK.put("similarity", 100);
                ocrOK.put("error", null);
                ocrOK.put("hash", "");
                ocrData.add(ocrOK);
            }

            // Farebnosť
            List<Map<String, Object>> colorData = new ArrayList<>();
            List<String> colorSummaries = new ArrayList<>();
            for (int i = 0; i < labelFiles.size(); i++) {
                File lf = labelFiles.get(i);
                Map<String, Object> colorDiffRaw = callDenzitaComparePython(masterTmp, lf);
                Map<String, Object> colorDiff = new LinkedHashMap<>(colorDiffRaw);
                double diff = 0;
                if (colorDiff.get("rozdiel") instanceof Number) {
                    diff = ((Number) colorDiff.get("rozdiel")).doubleValue();
                }
                String summary = diff > 20 ? "Príliš veľký rozdiel" : "OK";
                colorDiff.put("summary", summary);
                colorDiff.put("labelIndex", i + 1);
                colorData.add(colorDiff);
                colorSummaries.add(summary);
            }
            String colorSummary = colorSummaries.stream().anyMatch(s -> s.startsWith("Príliš")) ? "Chyba" : "OK";

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("operatorName", operatorName != null ? operatorName : "-");
            resp.put("orderNumber", orderNumber != null ? orderNumber : "-");
            resp.put("productNumber", productNumber != null ? productNumber : "-");
            resp.put("spoolNumber", spoolNumber != null ? spoolNumber : "-");
            resp.put("date", date != null ? date : "-");
            resp.put("wind", wind != null ? wind : "-");

            resp.put("detectedWind", detectedWind);
            resp.put("expectedWind", expectedWind);
            resp.put("windResult", windResult);
            resp.put("windDetail", windDetail);

            resp.put("barcodeResult", barcodeMsg);
            resp.put("ocrResult", ocrSummary);
            resp.put("colorResult", colorSummary);

            resp.put("barcodeData", barcodeData);
            resp.put("ocrData", ocrData);
            resp.put("colorData", colorData);

            resp.put("stripPreviewUrl", "/api/files/" + stripTmp.getName());

            if (ackBoxesJson != null && !ackBoxesJson.isBlank()) {
                try {
                    List<String> ackBoxList = MAPPER.readValue(ackBoxesJson, List.class);
                    resp.put("ackBoxes", ackBoxList);
                } catch (Exception ignored) {}
            }

            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.error("❌ Chyba pri porovnaní etikiet", e);
            Map<String, Object> err = new HashMap<>();
            err.put("message", "Chyba backendu: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        } finally {
            for (File lf : labelFiles) {
                if (lf != null && lf.exists()) lf.delete();
            }
            if (stripTmp != null && stripTmp.exists()) stripTmp.delete();
            if (masterTmp != null && masterTmp.exists()) masterTmp.delete();
        }
    }

    // ======== REPORT SAVE ENDPOINT (TXT + DASHBOARD JSON na DESKTOP) ========
    @PostMapping("/report/save")
    public ResponseEntity<?> saveReport(@RequestBody Map<String, Object> report) {
        try {
            // Ukladanie na Desktop používateľa lukac
            String reportDir = "C:/Users/lukac/Desktop/reporty";
            File dir = new File(reportDir);
            if (!dir.exists()) dir.mkdirs();

            String jobNumber = (String) (report.get("jobNumber") != null ? report.get("jobNumber") : report.getOrDefault("zakazka", null));
            String productNumber = (String) (report.get("productNumber") != null ? report.get("productNumber") : report.getOrDefault("produkt", null));

            String datum = (String) (report.get("datetime") != null ? report.get("datetime")
                    : report.get("datum") != null ? report.get("datum")
                    : report.getOrDefault("controlDate", null));
            if (datum == null || datum.trim().isEmpty() || "-".equals(datum.trim())) {
                datum = ZonedDateTime.now(ZoneId.of("Europe/Bratislava"))
                        .format(DateTimeFormatter.ofPattern("d. M. yyyy HH:mm:ss"));
            }

            if (jobNumber == null || jobNumber.trim().isEmpty() ||
                    productNumber == null || productNumber.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Chýba číslo zákazky alebo produktu. Report nebude uložený."
                ));
            }

            String fileName = "report_" + jobNumber + "_" + productNumber + "_" + System.currentTimeMillis() + ".txt";
            File out = new File(dir, fileName);

            try (PrintWriter pw = new PrintWriter(out, "UTF-8")) {
                pw.println("Scancontroll - Výsledok kontroly etikiet");
                pw.println("-----------------------------------------");
                pw.println("Meno operátora: " + report.getOrDefault("operator", "-"));
                pw.println("Číslo zákazky: " + jobNumber);
                pw.println("Číslo produktu: " + productNumber);
                pw.println("Stroj: " + report.getOrDefault("machine", report.getOrDefault("stroj", "-")));
                pw.println("Číslo kotúča: " + report.getOrDefault("spoolNumber", "-"));
                pw.println("Dátum a čas kontroly: " + datum);
                pw.println();
                pw.println("NAVIN: " + report.getOrDefault("windResult", "-") + " (" + report.getOrDefault("detectedWind", "-") + ")");
                pw.println("Čiarový kód: " + report.getOrDefault("barcodeResult", "-"));
                pw.println("Text: " + report.getOrDefault("ocrResult", "-"));
                pw.println("Farebnosť: " + report.getOrDefault("colorResult", "-"));
                pw.println("Poznámka: " + report.getOrDefault("note", ""));
                pw.println();
                pw.println("--- Výsledky jednotlivých etikiet ---");

                List<Map<String, Object>> colorData = (List<Map<String, Object>>) report.get("colorData");
                List<Map<String, Object>> barcodeData = (List<Map<String, Object>>) report.get("barcodeData");
                List<Map<String, Object>> ocrData = (List<Map<String, Object>>) report.get("ocrData");

                int etikiet = (colorData != null) ? colorData.size() : 0;
                for (int i = 0; i < etikiet; i++) {
                    int etiketaIdx = i + 1;
                    pw.println("Etiketa č." + etiketaIdx);

                    // Barcode
                    Map<String, Object> bc = barcodeData != null && barcodeData.size() > i ? barcodeData.get(i) : null;
                    if (bc != null && Boolean.FALSE.equals(bc.get("valid"))) {
                        pw.println("  Barcode: ❌ " + bc.getOrDefault("error", "-"));
                    } else {
                        pw.println("  Barcode: OK");
                    }

                    // OCR
                    boolean ocrChyba = false;
                    if (ocrData != null) {
                        for (Map<String, Object> o : ocrData) {
                            if (etiketaIdx == ((Number)o.getOrDefault("labelIndex", -1)).intValue()) {
                                if (o.get("error") != null && !"ACCEPTED".equals(o.get("error")) && o.get("error").toString().toLowerCase().contains("rozdiel")) {
                                    pw.println("  Text: ❌ " + o.get("error"));
                                    ocrChyba = true;
                                }
                            }
                        }
                    }
                    if (!ocrChyba) {
                        pw.println("  Text: OK");
                    }

                    // Farebnosť
                    Map<String, Object> c = colorData != null && colorData.size() > i ? colorData.get(i) : null;
                    if (c != null) {
                        pw.println("  Farebnosť: " + c.getOrDefault("summary", "-"));
                        pw.println("    C: " + c.getOrDefault("cyan", "-"));
                        pw.println("    M: " + c.getOrDefault("magenta", "-"));
                        pw.println("    Y: " + c.getOrDefault("yellow", "-"));
                        pw.println("    K: " + c.getOrDefault("black", "-"));
                    }
                    pw.println();
                }
            }

            // 2. Ulož JSON pre dashboard - tiež na Desktop do rovnakého priečinka
            String dashboardDir = "C:/Users/lukac/Desktop/reporty";
            File dashDir = new File(dashboardDir);
            if (!dashDir.exists()) dashDir.mkdirs();

            long id = System.currentTimeMillis();
            String dashFile = "report_" + jobNumber + "_" + productNumber + "_" + id + ".json";
            File dashOut = new File(dashDir, dashFile);

            Map<String, Object> dashJson = new LinkedHashMap<>();
            dashJson.put("id", id);
            dashJson.put("reportType", "COMPARE");
            dashJson.put("operator", report.getOrDefault("operator", "-"));
            dashJson.put("productCode", productNumber);
            dashJson.put("jobNumber", jobNumber);
            dashJson.put("machine", report.getOrDefault("machine", report.getOrDefault("stroj", "-")));
            dashJson.put("datetime", datum);

            dashJson.put("windResult", report.getOrDefault("windResult", "-"));
            dashJson.put("detectedWind", report.getOrDefault("detectedWind", "-"));
            dashJson.put("barcodeResult", report.getOrDefault("barcodeResult", "-"));
            dashJson.put("ocrResult", report.getOrDefault("ocrResult", "-"));
            dashJson.put("colorResult", report.getOrDefault("colorResult", "-"));
            dashJson.put("note", report.getOrDefault("note", ""));

            List<Map<String, Object>> colorData = (List<Map<String, Object>>) report.get("colorData");
            List<Map<String, Object>> barcodeData = (List<Map<String, Object>>) report.get("barcodeData");
            List<Map<String, Object>> ocrData = (List<Map<String, Object>>) report.get("ocrData");

            List<Map<String, Object>> etikety = new ArrayList<>();
            int etikiet = (colorData != null) ? colorData.size() : 0;
            for (int i = 0; i < etikiet; i++) {
                Map<String, Object> etiketa = new LinkedHashMap<>();
                etiketa.put("index", i + 1);

                Map<String, Object> bc = barcodeData != null && barcodeData.size() > i ? barcodeData.get(i) : null;
                if (bc != null && Boolean.FALSE.equals(bc.get("valid"))) {
                    etiketa.put("barcodeError", bc.getOrDefault("error", "-"));
                }
                String ocrErr = null;
                if (ocrData != null) {
                    for (Map<String, Object> o : ocrData) {
                        if ((i + 1) == ((Number)o.getOrDefault("labelIndex", -1)).intValue()) {
                            if (o.get("error") != null && !"ACCEPTED".equals(o.get("error")) && o.get("error").toString().toLowerCase().contains("rozdiel")) {
                                ocrErr = o.get("error").toString();
                                break;
                            }
                        }
                    }
                }
                if (ocrErr != null) {
                    etiketa.put("ocrError", ocrErr);
                }
                Map<String, Object> c = colorData != null && colorData.size() > i ? colorData.get(i) : null;
                if (c != null && !"OK".equals(c.getOrDefault("summary", "OK"))) {
                    etiketa.put("colorError", c.getOrDefault("summary", "-"));
                }
                etikety.add(etiketa);
            }
            dashJson.put("etikety", etikety);

            MAPPER.writerWithDefaultPrettyPrinter().writeValue(dashOut, dashJson);

            return ResponseEntity.ok(Map.of(
                    "message", "Report uložený.",
                    "txt", out.getAbsolutePath(),
                    "json", dashOut.getAbsolutePath()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ====== UTIL METÓDY ======
    private String safeProductNumber(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private String getOcrErrorHash(int labelIndex, OcrLineResult o) {
        String base = labelIndex + ":" + o.lineIdx + ":" +
                (o.masterText == null ? "" : o.masterText) + ":" +
                (o.scanText == null ? "" : o.scanText) + ":" + o.x + ":" + o.y;
        return Integer.toHexString(base.hashCode());
    }

    private String detectWindByOcr(File etiketaFile) {
        try {
            List<OcrLineResult> lines = ocrService.getTextLinesWithAngle(etiketaFile);
            if (lines == null || lines.isEmpty()) return null;
            OcrLineResult biggest = lines.get(0);
            for (OcrLineResult l : lines) {
                if (l.getWidth() * l.getHeight() > biggest.getWidth() * biggest.getHeight()) {
                    biggest = l;
                }
            }
            int angle = biggest.getAngle();
            if (angle == 0) return "A2";
            if (angle == 90 || angle == -270) return "A1";
            if (angle == 180 || angle == -180) return "A4";
            if (angle == -90 || angle == 270) return "A3";
            return null;
        } catch (Exception e) {
            log.warn("Nepodarilo sa detegovať navin podľa OCR", e);
            return null;
        }
    }

    private Map<String, Object> callDenzitaComparePython(File master, File etiketa) throws IOException, InterruptedException {
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
                Map<String, Object> err = new HashMap<>();
                err.put("error", "AI denzita compare error");
                err.put("detail", out.toString());
                return err;
            }
            log.info("AI Denzita Compare OUTPUT: {}", out);
            return MAPPER.readValue(out.toString(), Map.class);
        }
    }

    private BufferedImage safeSubimage(BufferedImage img, int x, int y, int w, int h) {
        int xx = Math.max(0, Math.min(x, img.getWidth() - 1));
        int yy = Math.max(0, Math.min(y, img.getHeight() - 1));
        int ww = Math.max(1, Math.min(w, img.getWidth() - xx));
        int hh = Math.max(1, Math.min(h, img.getHeight() - yy));
        return img.getSubimage(xx, yy, ww, hh);
    }
}





















