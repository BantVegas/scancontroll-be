package com.bantvegas.scancontroll.controller;

import com.bantvegas.scancontroll.service.BarcodeService;
import com.bantvegas.scancontroll.model.BarcodeResult; // SPRÁVNY import!

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api/labels")
@RequiredArgsConstructor
@Slf4j
public class LabelController {
    // Uprav na správnu Windows cestu:
    private static final String SCAN_DIR = "C:/Users/lukac/Desktop/Scan/";
    private final BarcodeService barcodeService;

    @GetMapping("/barcode/{filename}")
    public ResponseEntity<List<BarcodeResult>> readBarcode(@PathVariable String filename) {
        File f = Paths.get(SCAN_DIR, filename).toFile();
        if (!f.exists()) return ResponseEntity.notFound().build();

        List<BarcodeResult> codes = barcodeService.decodeAllBarcodes(f);
        if (codes == null || codes.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(codes);
    }
}


