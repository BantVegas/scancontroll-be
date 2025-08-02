package com.bantvegas.scancontroll.util;

import com.bantvegas.scancontroll.model.FakeOcrError;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AcceptedErrorsUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ERRORS_FILENAME = "accepted_errors.json";

    // Načíta zoznam fake errorov z daného adresára
    public static List<FakeOcrError> loadFakeErrors(String dirPath) {
        File file = new File(dirPath, ERRORS_FILENAME);
        if (!file.exists()) return new ArrayList<>();
        try {
            List<FakeOcrError> errors = MAPPER.readValue(file, new TypeReference<List<FakeOcrError>>() {});
            System.out.println("Načítaných fake errorov z " + file.getAbsolutePath() + ": " + errors.size());
            return errors;
        } catch (Exception e) {
            System.err.println("Chyba pri načítaní fake errorov zo " + file.getAbsolutePath());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // Pridá nový fake error do zoznamu (prípadne vytvorí nový súbor)
    public static void saveFakeError(String dirPath, FakeOcrError error) {
        // Vytvor adresár ak neexistuje
        File dir = new File(dirPath);
        if (!dir.exists()) dir.mkdirs();

        List<FakeOcrError> all = loadFakeErrors(dirPath);
        all.add(error);

        File file = new File(dirPath, ERRORS_FILENAME);

        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, all);
            System.out.println("Zapísaný error do: " + file.getAbsolutePath() + ", počet errorov: " + all.size());
        } catch (IOException e) {
            System.err.println("Chyba pri zápise fake erroru do " + file.getAbsolutePath());
            e.printStackTrace();
        }
    }
}
