package com.bantvegas.scancontroll.util;

import com.bantvegas.scancontroll.model.FakeOcrError;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class AcceptedErrorsUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ERRORS_FILENAME = "accepted_errors.json";

    // Načíta zoznam fake errorov z daného adresára
    public static List<FakeOcrError> loadFakeErrors(String dirPath) {
        File file = new File(dirPath, ERRORS_FILENAME);
        if (!file.exists()) return new ArrayList<>();
        try {
            return MAPPER.readValue(file, new TypeReference<List<FakeOcrError>>() {});
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // Pridá nový fake error do zoznamu (prípadne vytvorí nový súbor)
    public static void saveFakeError(String dirPath, FakeOcrError error) {
        List<FakeOcrError> all = loadFakeErrors(dirPath);
        all.add(error);
        File file = new File(dirPath, ERRORS_FILENAME);
        // Ak adresár neexistuje, vytvor ho
        File dir = new File(dirPath);
        if (!dir.exists()) dir.mkdirs();
        try (FileWriter fw = new FileWriter(file)) {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(fw, all);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
