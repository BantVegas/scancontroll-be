// src/main/java/com/bantvegas/sctext/controller/CompareOneController.java
package com.bantvegas.sctext.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.cloud.firestore.FirestoreOptions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class CompareOneController {

    // ===== RestTemplate (s timeoutmi) =====
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5_000);
        f.setReadTimeout(15_000);
        return new RestTemplate(f);
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper om = new ObjectMapper();

    // Nechytáme sa na localhost default – nútime to nastaviť cez env / application.properties
    @Value("${python.compare.one.url:}")
    private String pyOneUrl;

    @Value("${python.compare.legacy.url:}")
    private String pyLegacyUrl;

    @Value("${firestore.collection.ignore_rules:compare_ignore_rules}")
    private String ignoreRulesCollection;

    private Firestore firestore;

    @PostConstruct
    public void init() {
        try {
            this.firestore = FirestoreOptions.getDefaultInstance().getService();
            log.info("Firestore OK. ignoreRulesCollection='{}'", ignoreRulesCollection);
        } catch (Exception e) {
            log.warn("Firestore nebude dostupné: {}", e.getMessage());
        }
        log.info("PY endpoints: PY_COMPARE_ONE_URL='{}', PY_COMPARE_URL='{}'", pyOneUrl, pyLegacyUrl);
    }

    // ---- MULTIPART helper
    private HttpEntity<org.springframework.core.io.Resource> filePart(String fieldName, MultipartFile mf) throws Exception {
        HttpHeaders h = new HttpHeaders();
        String ct = Optional.ofNullable(mf.getContentType()).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        h.setContentType(MediaType.parseMediaType(ct));
        ContentDisposition cd = ContentDisposition.builder("form-data")
                .name(fieldName)
                .filename(Objects.requireNonNullElse(mf.getOriginalFilename(), "upload.bin"))
                .build();
        h.setContentDisposition(cd);
        return new HttpEntity<>(
                new MultipartInputStreamFileResource(mf.getInputStream(), mf.getOriginalFilename()),
                h
        );
    }

    // ---- dátové typy na výstup
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeLegacy(Map<String, Object> legacy, int labelW, int labelH, String etiketaDataUrl) {
        // očakávame perLabels[0] -> spravíme unifikovaný tvar
        List<Map<String, Object>> perLabels;
        Object plObj = legacy.get("perLabels");
        if (plObj instanceof List) {
            perLabels = (List<Map<String, Object>>) plObj;
        } else {
            plObj = legacy.get("perLabel");
            perLabels = (plObj instanceof List) ? (List<Map<String, Object>>) plObj : Collections.emptyList();
        }
        if (perLabels.isEmpty()) return Map.of("status", "NOK", "reason", "Legacy formát bez perLabels");

        Map<String, Object> pl = perLabels.get(0);
        Map<String, Object> local = (Map<String, Object>) pl.getOrDefault("local", Map.of());

        List<Map<String, Object>> boxes = new ArrayList<>();
        // diff
        List<Map<String, Object>> errs = (List<Map<String, Object>>) local.getOrDefault("errors", List.of());
        for (Map<String, Object> e : errs) {
            List<Number> bb = (List<Number>) e.getOrDefault("bbox", List.of(0, 0, 1, 1));
            boxes.add(Map.of(
                    "x", toN(bb, 0), "y", toN(bb, 1), "w", toN(bb, 2), "h", toN(bb, 3),
                    "type", "diff", "subType", e.getOrDefault("type", ""), "desc", e.getOrDefault("desc", "Rozdiel")
            ));
        }
        // ocr
        List<Map<String, Object>> ocrData = (List<Map<String, Object>>) local.getOrDefault("ocrData", List.of());
        for (Map<String, Object> o : ocrData) {
            List<Number> bb = (List<Number>) o.getOrDefault("bbox", List.of(0, 0, 1, 1));
            boxes.add(Map.of(
                    "x", toN(bb, 0), "y", toN(bb, 1), "w", toN(bb, 2), "h", toN(bb, 3),
                    "type", "ocr", "subType", "Text", "desc", o.getOrDefault("desc", "Rozdiel v texte")
            ));
        }
        // barcode fails
        List<Map<String, Object>> bar = (List<Map<String, Object>>) local.getOrDefault("barcodeData", List.of());
        List<Map<String, Object>> barcodeItems = new ArrayList<>();
        for (Map<String, Object> b : bar) {
            boolean valid = !(Boolean.FALSE.equals(b.get("valid")));
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("symbology", b.getOrDefault("symbology", b.getOrDefault("type", "BARCODE")));
            one.put("value", b.getOrDefault("value", b.getOrDefault("text", "-")));
            one.put("valid", valid);
            one.put("reason", b.getOrDefault("reason", b.getOrDefault("desc", null)));
            barcodeItems.add(one);

            if (!valid) {
                List<Number> bb = (List<Number>) b.getOrDefault("bbox", List.of(0, 0, 1, 1));
                boxes.add(Map.of(
                        "x", toN(bb, 0), "y", toN(bb, 1), "w", toN(bb, 2), "h", toN(bb, 3),
                        "type", "barcode", "subType", b.getOrDefault("symbology", "BARCODE"),
                        "desc", b.getOrDefault("reason", "Chyba čiarového kódu")
                ));
            }
        }

        String imgB64 = String.valueOf(pl.getOrDefault("image", ""));
        String image = imgB64.length() > 64 ? toImgUrl(imgB64) : etiketaDataUrl;

        // OCR texty, ak existujú (voliteľné)
        String ocrMaster = String.valueOf(pl.getOrDefault("ocrTextMaster", ""));
        String ocrScan = String.valueOf(pl.getOrDefault("ocrTextScan", ""));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "OK");
        resp.put("image", image);
        resp.put("w", labelW);
        resp.put("h", labelH);
        resp.put("graphics", Map.of("boxes", boxes));
        resp.put("ocr", Map.of("masterText", ocrMaster, "scanText", ocrScan, "items", ocrData));
        resp.put("barcode", Map.of("items", barcodeItems));
        return resp;
    }

    private static int toN(List<Number> arr, int i) {
        if (arr == null || arr.size() <= i || arr.get(i) == null) return 0;
        return ((Number) arr.get(i)).intValue();
    }

    private static String toImgUrl(String maybeB64) {
        if (maybeB64 == null) return "";
        if (maybeB64.startsWith("data:")) return maybeB64;
        return "data:image/jpeg;base64," + maybeB64;
    }

    // ====== HLAVNÉ POROVNANIE 1:1 ======
    @PostMapping(path = "/compare-one", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> compareOne(
            @RequestParam("master") MultipartFile master,
            @RequestParam("etiketa") MultipartFile etiketa,
            @RequestParam(value = "productNumber", required = false) String productNumber
    ) {
        try {
            // -- rýchla kontrola konfigurácie (Fail-Fast)
            if (pyOneUrl == null || pyOneUrl.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("status", "NOK", "reason", "PY_COMPARE_ONE_URL nie je nastavené"));
            }
            if (pyLegacyUrl == null || pyLegacyUrl.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("status", "NOK", "reason", "PY_COMPARE_URL nie je nastavené"));
            }

            // rozmer masteru (pre legacy fallback)
            int mw = 0, mh = 0;
            try (InputStream is = master.getInputStream()) {
                BufferedImage im = ImageIO.read(is);
                if (im != null) { mw = im.getWidth(); mh = im.getHeight(); }
            }

            // multipart pre nové PY /api/compare-one
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("master", filePart("master", master));
            body.add("etiketa", filePart("etiketa", etiketa));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, headers);

            Map<String, Object> pyResp = null;

            try {
                log.info("Calling PY one: {}", pyOneUrl);
                ResponseEntity<Map> py = restTemplate.postForEntity(pyOneUrl, req, Map.class);
                if (py.getStatusCode().is2xxSuccessful()) {
                    //noinspection unchecked
                    pyResp = (Map<String, Object>) py.getBody();
                } else {
                    log.warn("PY one returned {}", py.getStatusCode());
                }
            } catch (Exception ex) {
                log.warn("PY one call failed: {}", ex.toString());
            }

            // fallback na legacy /api/compare (rows=1, cols=1)
            if (pyResp == null) {
                try {
                    MultiValueMap<String, Object> body2 = new LinkedMultiValueMap<>();
                    body2.add("master", filePart("master", master));
                    body2.add("scan", filePart("scan", etiketa));
                    body2.add("rows", "1"); body2.add("cols", "1");
                    body2.add("label_w", String.valueOf(Math.max(4, mw)));
                    body2.add("label_h", String.valueOf(Math.max(4, mh)));
                    body2.add("gap_x", "0"); body2.add("gap_y", "0");
                    body2.add("dpi", "800");
                    body2.add("wind", "A1");

                    HttpEntity<MultiValueMap<String, Object>> req2 = new HttpEntity<>(body2, headers);
                    log.info("Calling PY legacy: {}", pyLegacyUrl);
                    ResponseEntity<Map> pyOld = restTemplate.postForEntity(pyLegacyUrl, req2, Map.class);
                    if (!pyOld.getStatusCode().is2xxSuccessful() || pyOld.getBody() == null) {
                        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                                .body(Map.of("status", "NOK", "reason", "Python API zlyhalo (legacy)"));
                    }
                    //noinspection unchecked
                    Map<String, Object> legacy = (Map<String, Object>) pyOld.getBody();
                    pyResp = normalizeLegacy(legacy, mw, mh, "");
                } catch (Exception ex) {
                    log.warn("PY legacy call failed: {}", ex.toString());
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(Map.of("status", "NOK", "reason", "Python API nedostupné"));
                }
            }

            // ignore-rules (ak je Firestore k dispozícii)
            if (firestore != null && productNumber != null && !productNumber.isBlank()) {
                pyResp = applyIgnoreRules(pyResp, productNumber);
            }

            return ResponseEntity.ok(pyResp);

        } catch (Exception e) {
            log.error("compare-one error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "NOK", "reason", "Chyba backendu: " + e.getMessage()));
        }
    }

    // ===== Feedback: uloženie fake chýb (acknowledged false positives) =====
    @PostMapping(path = "/compare-one/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> feedback(@RequestBody Map<String, Object> body) {
        try {
            if (firestore == null) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "NOK", "reason", "Firestore nie je dostupný"));

            String product = String.valueOf(body.getOrDefault("productNumber", "-")).trim();
            if (product.isEmpty() || product.equals("-")) {
                return ResponseEntity.badRequest().body(Map.of("status", "NOK", "reason", "productNumber je povinné"));
            }
            // očakávame: { productNumber, imageW, imageH, ackBoxes: [{x,y,w,h,type}] }
            Number iw = (Number) body.getOrDefault("imageW", 0);
            Number ih = (Number) body.getOrDefault("imageH", 0);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ack = (List<Map<String, Object>>) body.getOrDefault("ackBoxes", List.of());

            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("productNumber", product);
            doc.put("imageW", iw.intValue());
            doc.put("imageH", ih.intValue());
            doc.put("ackBoxes", ack);
            doc.put("createdAt", Instant.now().toString());

            String docId = product + "_" + System.currentTimeMillis();
            ApiFuture<WriteResult> fut = firestore.collection(ignoreRulesCollection).document(docId).set(doc, SetOptions.merge());
            WriteResult wr = fut.get(10, TimeUnit.SECONDS);
            return ResponseEntity.ok(Map.of("status", "OK", "storedAt", String.valueOf(wr.getUpdateTime())));
        } catch (Exception e) {
            log.error("feedback error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "NOK", "reason", "Chyba feedbacku: " + e.getMessage()));
        }
    }

    // ===== načítanie a aplikácia ignore rules na výstupné boxy =====
    @SuppressWarnings("unchecked")
    private Map<String, Object> applyIgnoreRules(Map<String, Object> out, String productNumber) throws Exception {
        if (out == null) return out;
        if (firestore == null) return out;

        Query q = firestore.collection(ignoreRulesCollection).whereEqualTo("productNumber", productNumber);
        List<QueryDocumentSnapshot> docs = q.get().get().getDocuments();
        if (docs.isEmpty()) return out;

        Map<String, Object> graphics = (Map<String, Object>) out.getOrDefault("graphics", Map.of());
        List<Map<String, Object>> boxes = (List<Map<String, Object>>) graphics.getOrDefault("boxes", List.of());
        if (boxes.isEmpty()) return out;

        // Normalizované pravidlá
        List<Map<String, Object>> rules = new ArrayList<>();
        for (QueryDocumentSnapshot d : docs) {
            Map<String, Object> data = d.getData();
            List<Map<String, Object>> ack = (List<Map<String, Object>>) data.getOrDefault("ackBoxes", List.of());
            rules.addAll(ack);
        }

        // jednoduché filtrovanie: vyhoď box, ktorý má IOU >= 0.7 s nejakým pravidlom rovnakej type
        List<Map<String, Object>> kept = new ArrayList<>();
        for (Map<String, Object> b : boxes) {
            double bx = asD(b.get("x")), by = asD(b.get("y")), bw = asD(b.get("w")), bh = asD(b.get("h"));
            String bt = String.valueOf(b.getOrDefault("type", ""));
            boolean drop = false;
            for (Map<String, Object> r : rules) {
                String rt = String.valueOf(r.getOrDefault("type", ""));
                if (!Objects.equals(bt, rt)) continue;
                double rx = asD(r.get("x")), ry = asD(r.get("y")), rw = asD(r.get("w")), rh = asD(r.get("h"));
                if (iou(bx, by, bw, bh, rx, ry, rw, rh) >= 0.7) { drop = true; break; }
            }
            if (!drop) kept.add(b);
        }
        Map<String, Object> newGraphics = new LinkedHashMap<>(graphics);
        newGraphics.put("boxes", kept);
        Map<String, Object> result = new LinkedHashMap<>(out);
        result.put("graphics", newGraphics);
        return result;
    }

    private static double asD(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o == null) return 0;
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    private static double iou(double ax, double ay, double aw, double ah, double bx, double by, double bw, double bh) {
        double x1 = Math.max(ax, bx), y1 = Math.max(ay, by);
        double x2 = Math.min(ax + aw, bx + bw), y2 = Math.min(ay + ah, by + bh);
        double inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        double ua = aw * ah + bw * bh - inter;
        return ua > 0 ? inter / ua : 0.0;
    }

    // ---- Multipart resource
    static class MultipartInputStreamFileResource extends org.springframework.core.io.InputStreamResource {
        private final String filename;
        MultipartInputStreamFileResource(InputStream inputStream, String filename) {
            super(inputStream);
            this.filename = filename;
        }
        @Override public String getFilename() { return this.filename; }
        @Override public long contentLength() { return -1; }
    }
}


