package com.bantvegas.scancontroll.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Service
public class CompareReportService {

    // ZJEDNOTENÝ adresár s kontrolérom
    private static final String DIR = "C:/Users/lukac/Desktop/reporty";

    public List<Map<String, Object>> getAllCompareReports() {
        File dir = new File(DIR);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        List<Map<String, Object>> out = new ArrayList<>();
        if (files != null) {
            ObjectMapper mapper = new ObjectMapper();
            for (File f : files) {
                try (FileReader fr = new FileReader(f)) {
                    Map<String, Object> data = mapper.readValue(fr, Map.class);

                    // Ber iba COMPARE reporty!
                    String type = Objects.toString(data.get("reportType"), "").toUpperCase();
                    if (!"COMPARE".equals(type)) continue;

                    Map<String, Object> item = new LinkedHashMap<>();
                    // id pre dashboard (vezmi z field alebo použi hash/čas)
                    Object id = data.getOrDefault("id", f.getName().hashCode());
                    item.put("id", id);
                    item.put("reportType", "COMPARE");
                    item.put("operator", Objects.toString(data.get("operator"), ""));
                    item.put("productCode", Objects.toString(data.get("productCode"), ""));
                    item.put("jobNumber", Objects.toString(data.getOrDefault("jobNumber", ""), ""));
                    item.put("machine", Objects.toString(data.getOrDefault("machine", ""), ""));
                    item.put("datetime", Objects.toString(data.get("datetime"), ""));

                    // Posielaj detailsJson ako serializovaný string (stačí dať celý report ako detail)
                    item.put("detailsJson", mapper.writeValueAsString(data));

                    out.add(item);

                } catch (Exception ex) {
                    // Optional: loguj chyby načítania reportu
                    // ex.printStackTrace();
                }
            }
        }
        return out;
    }
}




