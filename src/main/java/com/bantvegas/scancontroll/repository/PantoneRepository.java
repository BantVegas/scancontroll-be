package com.bantvegas.scancontroll.repository;

import com.bantvegas.scancontroll.model.PantoneColor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

@Repository
public class PantoneRepository {

    private final ObjectMapper mapper = new ObjectMapper();
    private Map<String, PantoneColor> pantoneMap = Collections.emptyMap();

    @PostConstruct
    public void load() throws Exception {
        String path = "/pantone/pantone-colors.json";
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Pantone JSON nenájdený: " + path);
            }
            pantoneMap = mapper.readValue(is, new TypeReference<>() {});
            System.out.println("✅ Načítaných Pantone farieb: " + pantoneMap.size());
        }
    }

    public PantoneColor get(String code) {
        if (code == null) return null;
        // normalizácia (bez medzier / case-insensitive)
        String cleaned = code.trim().toLowerCase().replaceAll("\\s+", " ");
        return pantoneMap.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().equals(cleaned))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    public Map<String, PantoneColor> getAll() {
        return pantoneMap;
    }
}

