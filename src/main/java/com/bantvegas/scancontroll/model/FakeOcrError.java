package com.bantvegas.scancontroll.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FakeOcrError {
    private String productNumber;
    private String masterText;
    private String scanText;
    private int similarity;
    private String hash;
    private String createdAt;  // ISO8601 string ("2025-07-20T18:30:00")
}

