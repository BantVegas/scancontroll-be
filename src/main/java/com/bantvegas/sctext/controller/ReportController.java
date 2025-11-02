package com.bantvegas.sctext.controller;


import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.cloud.firestore.FirestoreOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/report")
@Slf4j
public class ReportController {

    @Value("${firestore.collection.reports:compare_reports}")
    private String reportsCollection;

    private Firestore firestore;

    @PostConstruct
    public void init() {
        try {
            this.firestore = FirestoreOptions.getDefaultInstance().getService();
            log.info("Firestore OK (collection='{}')", reportsCollection);
        } catch (Exception e) {
            log.warn("Firestore nie je k dispozícii: {}", e.getMessage());
        }
    }

    @PostMapping(path="/save", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> save(@RequestBody Map<String,Object> report) {
        try {
            if (firestore == null) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error","Firestore nie je dostupný"));

            String docId = "rep_" + Instant.now().toEpochMilli();
            report.putIfAbsent("createdAt", Instant.now().toString());

            ApiFuture<WriteResult> fut = firestore.collection(reportsCollection).document(docId).set(report, SetOptions.merge());
            WriteResult wr = fut.get(10, TimeUnit.SECONDS);

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "id", docId,
                    "txt", "Report uložený @ " + wr.getUpdateTime()
            ));
        } catch (Exception e) {
            log.error("save report error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error","Chyba uloženia: " + e.getMessage()));
        }
    }
}
