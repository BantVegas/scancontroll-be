package com.bantvegas.scancontroll.dto;

public record NearestResponse(
        String code,
        String hex,
        int r,
        int g,
        int b,
        double deltaE2000
) { }

