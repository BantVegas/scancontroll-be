package com.bantvegas.scancontroll.controller;

import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.file.*;

@RestController
@RequestMapping("/api/master")
public class MasterImageController {

    @GetMapping("/image")
    public ResponseEntity<Resource> getMasterImage(@RequestParam String productNumber) {
        try {
            // TU UPRAV CESTU NA DISK (Windows, žiadne data/master)
            Path path = Paths.get("C:/Users/lukac/Desktop/Master/" + productNumber + "/master.png");
            if (!Files.exists(path)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Resource file = new UrlResource(path.toUri());
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(file);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
