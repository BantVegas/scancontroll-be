package com.bantvegas.scancontroll.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

public class PythonRunner {

    /**
     * Spustí Python skript na AI porovnanie etikiet.
     * @param master Cesta k master obrázku
     * @param strip Cesta k páse etikiet
     * @param outDir Výstupný adresár
     * @return exit code z Python procesu
     */
    public int runPython(String master, String strip, String outDir) {
        String pythonExe = "C:\\Users\\lukac\\AppData\\Local\\Programs\\Python\\Python310\\python.exe";
        String script = "C:\\Users\\lukac\\Desktop\\Scancontroll\\Scancontroll\\python\\ai_compare.py";
        List<String> command = Arrays.asList(pythonExe, script, master, strip, outDir);
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[PYTHON]: " + line);
                }
            }
            return proc.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    // Príklad použitia pre testovanie
    public static void main(String[] args) {
        PythonRunner runner = new PythonRunner();
        String master = "C:\\Users\\lukac\\Desktop\\Scancontroll\\Scancontroll\\data\\master.png";
        String strip = "C:\\Users\\lukac\\Desktop\\Scancontroll\\Scancontroll\\data\\strip.jpg";
        String outDir = "C:\\Users\\lukac\\Desktop\\Scancontroll\\Scancontroll\\data\\out";
        int exitCode = runner.runPython(master, strip, outDir);
        System.out.println("Python skript skončil s kódom: " + exitCode);
    }
}
