package com.bantvegas.scancontroll.util;

public final class ColorUtils {

    private ColorUtils() {}

    public record Lab(double L, double a, double b) {}

    public static Lab rgbToLab(int r, int g, int b) {
        double R=r/255.0, G=g/255.0, B=b/255.0;
        R = (R>0.04045)?Math.pow((R+0.055)/1.055,2.4):R/12.92;
        G = (G>0.04045)?Math.pow((G+0.055)/1.055,2.4):G/12.92;
        B = (B>0.04045)?Math.pow((B+0.055)/1.055,2.4):B/12.92;

        double X=(R*0.4124+G*0.3576+B*0.1805)/0.95047;
        double Y=(R*0.2126+G*0.7152+B*0.0722);
        double Z=(R*0.0193+G*0.1192+B*0.9505)/1.08883;

        X=f(X); Y=f(Y); Z=f(Z);

        double L=116*Y-16;
        double a=500*(X-Y);
        double b_=200*(Y-Z);
        return new Lab(L,a,b_);
    }

    private static double f(double t){
        return t>0.008856 ? Math.cbrt(t) : (7.787*t + 16.0/116.0);
    }

    public static double deltaE76(Lab a, Lab b){
        double dL=a.L()-b.L();
        double da=a.a()-b.a();
        double db=a.b()-b.b();
        return Math.sqrt(dL*dL + da*da + db*db);
    }

    public static double deltaE2000(Lab lab1, Lab lab2){
        double L1=lab1.L(), a1=lab1.a(), b1=lab1.b();
        double L2=lab2.L(), a2=lab2.a(), b2=lab2.b();
        double avgLp=(L1+L2)/2.0;
        double C1=Math.sqrt(a1*a1 + b1*b1);
        double C2=Math.sqrt(a2*a2 + b2*b2);
        double avgC=(C1+C2)/2.0;
        double G=0.5*(1 - Math.sqrt(Math.pow(avgC,7)/(Math.pow(avgC,7)+Math.pow(25,7))));
        double a1p=a1*(1+G), a2p=a2*(1+G);
        double C1p=Math.sqrt(a1p*a1p + b1*b1);
        double C2p=Math.sqrt(a2p*a2p + b2*b2);
        double avgCp=(C1p+C2p)/2.0;
        double h1p=Math.atan2(b1,a1p); if(h1p<0) h1p+=2*Math.PI;
        double h2p=Math.atan2(b2,a2p); if(h2p<0) h2p+=2*Math.PI;

        double dLp=L2-L1;
        double dCp=C2p-C1p;
        double dhp=h2p-h1p;
        if(dhp>Math.PI) dhp-=2*Math.PI;
        else if(dhp<-Math.PI) dhp+=2*Math.PI;
        else if(C1p*C2p==0) dhp=0;

        double dHp=2*Math.sqrt(C1p*C2p)*Math.sin(dhp/2.0);

        double avgHp;
        if(C1p*C2p==0) avgHp=h1p+h2p;
        else if(Math.abs(h1p-h2p)<=Math.PI) avgHp=(h1p+h2p)/2.0;
        else avgHp=(h1p+h2p+2*Math.PI)/2.0;

        double T=1
                -0.17*Math.cos(avgHp - Math.toRadians(30))
                +0.24*Math.cos(2*avgHp)
                +0.32*Math.cos(3*avgHp + Math.toRadians(6))
                -0.20*Math.cos(4*avgHp - Math.toRadians(63));

        double SL=1 + (0.015*Math.pow(avgLp-50,2))/Math.sqrt(20+Math.pow(avgLp-50,2));
        double SC=1 + 0.045*avgCp;
        double SH=1 + 0.015*avgCp*T;

        double deltaTheta = Math.toRadians(30 * Math.exp(-Math.pow(((avgHp*180/Math.PI) - 275)/25,2)));
        double RC = 2*Math.sqrt(Math.pow(avgCp,7)/(Math.pow(avgCp,7)+Math.pow(25,7)));
        double RT = -RC * Math.sin(2*deltaTheta);

        return Math.sqrt(
                Math.pow(dLp/SL,2) +
                        Math.pow(dCp/SC,2) +
                        Math.pow(dHp/SH,2) +
                        RT*(dCp/SC)*(dHp/SH)
        );
    }

    public static String verdict(double d){
        if(d < 0.8) return "Bez rozdielu / nepostrehnuteľné";
        if(d < 1.5) return "Mierny rozdiel (akceptovateľné)";
        if(d < 3) return "Malý rozdiel";
        if(d < 5) return "Viditeľný rozdiel";
        if(d < 8) return "Výrazný rozdiel";
        return "Veľký rozdiel";
    }

    public static String toHex(int r,int g,int b){
        return "#"+hex(r)+hex(g)+hex(b);
    }
    private static String hex(int v){
        String s=Integer.toHexString(v).toUpperCase();
        return s.length()==1?"0"+s:s;
    }
}
