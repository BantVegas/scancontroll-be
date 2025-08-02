package com.bantvegas.scancontroll.controller;

import com.bantvegas.scancontroll.model.FakeOcrError;
import com.bantvegas.scancontroll.util.AcceptedErrorsUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/fake-ocr-error")
public class FakeOcrErrorController {

    // Nastav základnú cestu na master etikety (ako v CompareController)
    private static final String MASTER_BASE_PATH = "C:/Users/lukac/Desktop/Master/";

    @PostMapping
    public ResponseEntity<?> saveFakeOcrError(@RequestBody Map<String, Object> req) {
        String masterText = (String) req.get("masterText");
        String scanText = (String) req.get("scanText");
        int similarity = req.get("similarity") != null ? ((Number) req.get("similarity")).intValue() : 0;
        String productNumber = req.get("productNumber") != null ? (String) req.get("productNumber") : null;
        String hash = DigestUtils.sha256Hex(masterText + "||" + scanText);

        if (productNumber == null || productNumber.isBlank()) {
            return ResponseEntity.badRequest().body("Missing productNumber");
        }

        String dirPath = MASTER_BASE_PATH + productNumber;

        List<FakeOcrError> exist = AcceptedErrorsUtil.loadFakeErrors(dirPath);
        boolean found = exist.stream().anyMatch(e -> hash.equals(e.getHash()));
        if (!found) {
            FakeOcrError error = new FakeOcrError();
            error.setProductNumber(productNumber);
            error.setMasterText(masterText);
            error.setScanText(scanText);
            error.setSimilarity(similarity);
            error.setHash(hash);
            error.setCreatedAt(LocalDateTime.now().toString());
            AcceptedErrorsUtil.saveFakeError(dirPath, error);
        }
        return ResponseEntity.ok().build();
    }
}



