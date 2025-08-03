package com.bantvegas.scancontroll.service;

import com.bantvegas.scancontroll.dto.PantoneCompareRequest;
import com.bantvegas.scancontroll.dto.PantoneCompareResponse;
import com.bantvegas.scancontroll.model.PantoneColor;
import com.bantvegas.scancontroll.repository.PantoneRepository;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.util.Base64;

@Service
public class PantoneService {

    private final PantoneRepository repo;

    public PantoneService(PantoneRepository repo) {
        this.repo = repo;
    }

    public PantoneCompareResponse compare(PantoneCompareRequest req) {
        // Prijíma presne tie isté názvy premenných ako FE posiela
        PantoneColor pc = repo.get(req.getPantoneCode());
        if (pc == null) {
            throw new IllegalArgumentException("Pantone kód neznámy: " + req.getPantoneCode());
        }

        int[] rgb = extractAverageRGB(req.getImageBase64());
        int sr = safe(rgb[0]);
        int sg = safe(rgb[1]);
        int sb = safe(rgb[2]);

        double deltaE76 = deltaE76(pc.getR(), pc.getG(), pc.getB(), sr, sg, sb);

        double deltaE2000 = deltaE2000(
                rgbToLab(pc.getR(), pc.getG(), pc.getB()),
                rgbToLab(sr, sg, sb)
        );

        double rgbDist = Math.sqrt(
                Math.pow(pc.getR() - sr, 2) +
                        Math.pow(pc.getG() - sg, 2) +
                        Math.pow(pc.getB() - sb, 2)
        );

        String rating = rating(deltaE2000);
        double matchPercent = matchPercent(deltaE2000);

        // Tu presne FE očakáva polia (pozri FE renderovanie v DashboardReport!)
        return new PantoneCompareResponse(
                req.getPantoneCode(),           // pantoneCode
                pc.getHex(),                    // pantoneHex
                pc.getR(), pc.getG(), pc.getB(),// refR refG refB
                sr, sg, sb,                     // sampleR sampleG sampleB
                round(deltaE2000),              // deltaE2000
                round(deltaE76),                // deltaE76
                round(rgbDist),                 // rgbDistance
                rating,                         // rating
                round(matchPercent)             // matchPercent
        );
    }

    private int safe(Integer v) {
        if (v == null || v < 0 || v > 255) throw new IllegalArgumentException("Neplatné RGB");
        return v;
    }

    // SPRACUJ BASE64 OBRÁZOK a VYPOČÍTAJ PRIEMERNÉ RGB
    private int[] extractAverageRGB(String base64) {
        try {
            String data = base64.contains(",") ? base64.substring(base64.indexOf(",") + 1) : base64;
            byte[] bytes = Base64.getDecoder().decode(data);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) throw new IllegalArgumentException("Nepodarilo sa načítať obrázok z base64");

            long r = 0, g = 0, b = 0, count = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    Color c = new Color(img.getRGB(x, y));
                    r += c.getRed();
                    g += c.getGreen();
                    b += c.getBlue();
                    count++;
                }
            }
            if (count == 0) throw new IllegalArgumentException("Prázdny obrázok!");
            return new int[] { (int)(r/count), (int)(g/count), (int)(b/count) };
        } catch (Exception e) {
            throw new IllegalArgumentException("Chyba pri spracovaní base64 obrázka: " + e.getMessage(), e);
        }
    }

    // ——— Color math ———

    private record Lab(double L, double a, double b) {}

    private Lab rgbToLab(int r, int g, int b) {
        double rn = pivotRgb(r / 255.0);
        double gn = pivotRgb(g / 255.0);
        double bn = pivotRgb(b / 255.0);

        double x = rn * 0.4124 + gn * 0.3576 + bn * 0.1805;
        double y = rn * 0.2126 + gn * 0.7152 + bn * 0.0722;
        double z = rn * 0.0193 + gn * 0.1192 + bn * 0.9505;

        double Xn = 0.95047, Yn = 1.0, Zn = 1.08883;

        double fx = fxyz(x / Xn);
        double fy = fxyz(y / Yn);
        double fz = fxyz(z / Zn);

        double L = 116 * fy - 16;
        double a = 500 * (fx - fy);
        double b2 = 200 * (fy - fz);
        return new Lab(L, a, b2);
    }

    private double pivotRgb(double c) {
        return c > 0.04045 ? Math.pow((c + 0.055) / 1.055, 2.4) : c / 12.92;
    }

    private double fxyz(double t) {
        return t > 0.008856 ? Math.cbrt(t) : (7.787 * t) + 16.0 / 116.0;
    }

    private double deltaE76(int r1, int g1, int b1, int r2, int g2, int b2) {
        return Math.sqrt(Math.pow(r1 - r2, 2) + Math.pow(g1 - g2, 2) + Math.pow(b1 - b2, 2));
    }

    private double deltaE2000(Lab lab1, Lab lab2) {
        double L1 = lab1.L(), a1 = lab1.a(), b1 = lab1.b();
        double L2 = lab2.L(), a2 = lab2.a(), b2 = lab2.b();

        double avgLp = (L1 + L2) / 2.0;
        double C1 = Math.hypot(a1, b1);
        double C2 = Math.hypot(a2, b2);
        double avgC = (C1 + C2) / 2.0;
        double G = 0.5 * (1 - Math.sqrt(Math.pow(avgC, 7) / (Math.pow(avgC, 7) + Math.pow(25, 7))));
        double a1p = (1 + G) * a1;
        double a2p = (1 + G) * a2;
        double C1p = Math.hypot(a1p, b1);
        double C2p = Math.hypot(a2p, b2);
        double avgCp = (C1p + C2p) / 2.0;

        double h1p = Math.atan2(b1, a1p); if (h1p < 0) h1p += 2 * Math.PI;
        double h2p = Math.atan2(b2, a2p); if (h2p < 0) h2p += 2 * Math.PI;

        double dLp = L2 - L1;
        double dCp = C2p - C1p;

        double dhp;
        double diff = h2p - h1p;
        if (Math.abs(diff) <= Math.PI) {
            dhp = diff;
        } else {
            dhp = diff > 0 ? diff - 2 * Math.PI : diff + 2 * Math.PI;
        }

        double dHp = 2 * Math.sqrt(C1p * C2p) * Math.sin(dhp / 2);

        double avgHp;
        if (Math.abs(h1p - h2p) > Math.PI) {
            avgHp = (h1p + h2p + 2 * Math.PI) / 2.0;
        } else {
            avgHp = (h1p + h2p) / 2.0;
        }

        double T = 1
                - 0.17 * Math.cos(avgHp - Math.PI / 6)
                + 0.24 * Math.cos(2 * avgHp)
                + 0.32 * Math.cos(3 * avgHp + Math.PI / 30)
                - 0.20 * Math.cos(4 * avgHp - 63 * Math.PI / 180);

        double deltaRo = 30 * Math.PI / 180 * Math.exp(-Math.pow(((avgHp * 180 / Math.PI) - 275) / 25, 2));
        double Rc = 2 * Math.sqrt(Math.pow(avgCp, 7) / (Math.pow(avgCp, 7) + Math.pow(25, 7)));
        double Sl = 1 + (0.015 * Math.pow(avgLp - 50, 2)) / Math.sqrt(20 + Math.pow(avgLp - 50, 2));
        double Sc = 1 + 0.045 * avgCp;
        double Sh = 1 + 0.015 * avgCp * T;
        double Rt = -Math.sin(2 * deltaRo) * Rc;

        return Math.sqrt(
                Math.pow(dLp / Sl, 2) +
                        Math.pow(dCp / Sc, 2) +
                        Math.pow(dHp / Sh, 2) +
                        Rt * (dCp / Sc) * (dHp / Sh)
        );
    }

    // Presne podľa frontend mapy!
    private String rating(double dE) {
        if (dE < 3) return "Výborné";
        if (dE < 5)   return "Dobre";
        if (dE < 8)   return "Priemerné";
        if (dE < 15)  return "Slabé";
        return "Neznáme";
    }

    private double matchPercent(double dE) {
        double max = 10.0; // po 10 už nezmyselné
        double val = Math.max(0, Math.min(1, 1 - dE / max));
        return val * 100;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}


