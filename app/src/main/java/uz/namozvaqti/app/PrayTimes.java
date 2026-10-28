package uz.namozvaqti.app;

import java.util.Calendar;

/**
 * Namoz vaqtlarini astronomik formula bilan hisoblaydi (praytimes.org algoritmi asosida).
 * Natija: kun boshidan daqiqalar (0..1439), tartib: Bomdod, Quyosh, Peshin, Asr, Shom, Xufton.
 */
public final class PrayTimes {

    public static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;
    public static final int COUNT = 6;

    private final double lat, lng, timeZone;
    private final double fajrAngle, ishaAngle;
    private final int asrFactor;
    private double jDate;

    public PrayTimes(double lat, double lng, double timeZone, double fajrAngle, double ishaAngle, boolean hanafi) {
        this.lat = lat;
        this.lng = lng;
        this.timeZone = timeZone;
        this.fajrAngle = fajrAngle;
        this.ishaAngle = ishaAngle;
        this.asrFactor = hanafi ? 2 : 1;
    }

    /** Berilgan kun uchun vaqtlar (daqiqalarda), offsets[i] daqiqa tuzatish qo'shiladi. */
    public int[] compute(int year, int month, int day, int[] offsets) {
        jDate = julian(year, month, day) - lng / (15.0 * 24.0);
        double[] t = {5, 6, 12, 13, 18, 18};
        for (int iter = 0; iter < 2; iter++) {
            t = computeOnce(t);
        }
        int[] res = new int[COUNT];
        for (int i = 0; i < COUNT; i++) {
            double h = t[i] + timeZone - lng / 15.0;
            int min = (int) Math.floor(h * 60.0 + 0.5);
            if (offsets != null) min += offsets[i];
            res[i] = ((min % 1440) + 1440) % 1440;
        }
        return res;
    }

    public int[] compute(Calendar c, int[] offsets) {
        return compute(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), offsets);
    }

    private double[] computeOnce(double[] t) {
        double[] p = new double[COUNT];
        for (int i = 0; i < COUNT; i++) p[i] = t[i] / 24.0;
        double fajr = sunAngleTime(fajrAngle, p[0], true);
        double sunrise = sunAngleTime(0.833, p[1], true);
        double dhuhr = midDay(p[2]);
        double asr = asrTime(asrFactor, p[3]);
        double sunset = sunAngleTime(0.833, p[4], false);
        double isha = sunAngleTime(ishaAngle, p[5], false);
        return new double[]{fajr, sunrise, dhuhr, asr, sunset, isha};
    }

    private double midDay(double time) {
        double eqt = sunPosition(jDate + time)[1];
        return fixHour(12 - eqt);
    }

    private double sunAngleTime(double angle, double time, boolean ccw) {
        double decl = sunPosition(jDate + time)[0];
        double noon = midDay(time);
        double cosT = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat));
        cosT = Math.max(-1, Math.min(1, cosT));
        double t = darccos(cosT) / 15.0;
        return noon + (ccw ? -t : t);
    }

    private double asrTime(int factor, double time) {
        double decl = sunPosition(jDate + time)[0];
        double angle = -darccot(factor + dtan(Math.abs(lat - decl)));
        return sunAngleTime(angle, time, false);
    }

    /** [deklinatsiya, vaqt tenglamasi] */
    private static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0;
        double eqt = q / 15.0 - fixHour(ra);
        double decl = darcsin(dsin(e) * dsin(l));
        return new double[]{decl, eqt};
    }

    private static double julian(int year, int month, int day) {
        if (month <= 2) {
            year -= 1;
            month += 12;
        }
        double a = Math.floor(year / 100.0);
        double b = 2 - a + Math.floor(a / 4.0);
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
    }

    /** Ka'ba tomon yo'nalish, shimoldan soat yo'nalishida graduslarda. */
    public static double qiblaBearing(double lat, double lng) {
        double kLat = 21.422487, kLng = 39.826206;
        double dl = Math.toRadians(kLng - lng);
        double p1 = Math.toRadians(lat), p2 = Math.toRadians(kLat);
        double y = Math.sin(dl);
        double x = Math.cos(p1) * Math.tan(p2) - Math.sin(p1) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    private static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double dtan(double d) { return Math.tan(Math.toRadians(d)); }
    private static double darcsin(double x) { return Math.toDegrees(Math.asin(x)); }
    private static double darccos(double x) { return Math.toDegrees(Math.acos(x)); }
    private static double darctan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
    private static double darccot(double x) { return Math.toDegrees(Math.atan(1 / x)); }
    private static double fixAngle(double a) { return fix(a, 360); }
    private static double fixHour(double a) { return fix(a, 24); }

    private static double fix(double a, double b) {
        a = a - b * Math.floor(a / b);
        return a < 0 ? a + b : a;
    }
}
