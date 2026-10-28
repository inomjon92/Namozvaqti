package uz.namozvaqti.app;

import android.content.Context;
import android.icu.util.IslamicCalendar;

import java.util.Calendar;
import java.util.TimeZone;

/** Kunlik vaqtlar, hijriy sana va Ramazon taqvimi. */
public final class Calc {

    public static final TimeZone TZ = TimeZone.getTimeZone("Asia/Tashkent");
    /** O'zbekiston uchun standart burchaklar (rasmiy taqvim bilan solishtirib tekshiriladi). */
    public static final double FAJR_ANGLE = 15.0, ISHA_ANGLE = 15.0;

    public static final String[] NAMES = {"Bomdod", "Quyosh", "Peshin", "Asr", "Shom", "Xufton"};
    public static final String[] MONTHS = {"yanvar", "fevral", "mart", "aprel", "may", "iyun", "iyul",
            "avgust", "sentabr", "oktabr", "noyabr", "dekabr"};
    public static final String[] WEEKDAYS = {"", "Yakshanba", "Dushanba", "Seshanba", "Chorshanba",
            "Payshanba", "Juma", "Shanba"};
    public static final String[] HIJRI_MONTHS = {"Muharram", "Safar", "Rabiul avval", "Rabiul oxir",
            "Jumodul avval", "Jumodul oxir", "Rajab", "Sha'bon", "Ramazon", "Shavvol", "Zulqa'da", "Zulhijja"};
    public static final int RAMADAN = 8;

    private Calc() {}

    public static Calendar now() {
        return Calendar.getInstance(TZ);
    }

    public static Calendar dayStart(Calendar c) {
        Calendar d = (Calendar) c.clone();
        d.set(Calendar.HOUR_OF_DAY, 0);
        d.set(Calendar.MINUTE, 0);
        d.set(Calendar.SECOND, 0);
        d.set(Calendar.MILLISECOND, 0);
        return d;
    }

    public static int[] times(Context ctx, Calendar day) {
        Prefs p = new Prefs(ctx);
        Cities.City city = p.city();
        PrayTimes pt = new PrayTimes(city.lat, city.lng, 5.0, FAJR_ANGLE, ISHA_ANGLE, p.hanafi());
        return pt.compute(day, p.offsets());
    }

    /** Kun boshidan daqiqalarni millisekundga aylantiradi. */
    public static long at(Calendar day, int minutes) {
        Calendar d = dayStart(day);
        d.add(Calendar.MINUTE, minutes);
        return d.getTimeInMillis();
    }

    public static String hhmm(int minutes) {
        return String.format(java.util.Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }

    public static boolean isFriday(Calendar c) {
        return c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
    }

    /** Juma kuni Peshin o'rniga "Juma". */
    public static String name(int prayer, Calendar day) {
        if (prayer == PrayTimes.DHUHR && isFriday(day)) return "Juma";
        return NAMES[prayer];
    }

    public static String gregorian(Calendar c) {
        return c.get(Calendar.DAY_OF_MONTH) + "-" + MONTHS[c.get(Calendar.MONTH)] + ", " + WEEKDAYS[c.get(Calendar.DAY_OF_WEEK)];
    }

    public static String shortDate(Calendar c) {
        return c.get(Calendar.DAY_OF_MONTH) + "-" + MONTHS[c.get(Calendar.MONTH)];
    }

    private static IslamicCalendar islamic() {
        IslamicCalendar ic = new IslamicCalendar(android.icu.util.TimeZone.getTimeZone("Asia/Tashkent"));
        ic.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        return ic;
    }

    /** {yil, oy (0..11), kun} hijriy, Ramazon tuzatishi hisobga olingan. */
    public static int[] hijri(Context ctx, Calendar day) {
        Calendar d = dayStart(day);
        d.set(Calendar.HOUR_OF_DAY, 12);
        d.add(Calendar.DAY_OF_MONTH, -new Prefs(ctx).ramadanShift());
        IslamicCalendar ic = islamic();
        ic.setTimeInMillis(d.getTimeInMillis());
        return new int[]{ic.get(IslamicCalendar.YEAR), ic.get(IslamicCalendar.MONTH), ic.get(IslamicCalendar.DAY_OF_MONTH)};
    }

    public static String hijriText(Context ctx, Calendar day) {
        int[] h = hijri(ctx, day);
        return h[2] + " " + HIJRI_MONTHS[h[1]] + " " + h[0] + " h.";
    }

    /** Ramazon davri: boshlanish kuni, kunlar soni va hijriy yil. */
    public static final class Ramadan {
        public final Calendar start;
        public final int days;
        public final int hijriYear;

        Ramadan(Calendar start, int days, int hijriYear) {
            this.start = start;
            this.days = days;
            this.hijriYear = hijriYear;
        }

        public Calendar day(int i) {
            Calendar c = (Calendar) start.clone();
            c.add(Calendar.DAY_OF_MONTH, i);
            return c;
        }

        public Calendar end() { return day(days - 1); }

        /** Bugun Ramazonning nechanchi kuni (0 dan), Ramazon bo'lmasa -1. */
        public int indexOf(Calendar today) {
            long diff = dayStart(today).getTimeInMillis() - start.getTimeInMillis();
            int i = (int) Math.round(diff / 86400000.0);
            return (i >= 0 && i < days) ? i : -1;
        }
    }

    public static Ramadan ramadan(Context ctx, int hijriYear) {
        IslamicCalendar ic = islamic();
        ic.clear();
        ic.set(hijriYear, RAMADAN, 1, 12, 0, 0);
        int days = ic.getActualMaximum(IslamicCalendar.DAY_OF_MONTH);
        Calendar start = now();
        start.setTimeInMillis(ic.getTimeInMillis());
        start = dayStart(start);
        start.add(Calendar.DAY_OF_MONTH, new Prefs(ctx).ramadanShift());
        return new Ramadan(start, days, hijriYear);
    }

    /** Joriy yoki keyingi Ramazon: har yili o'zi hisoblanadi, yangilanish kerak emas. */
    public static Ramadan currentOrNextRamadan(Context ctx) {
        Calendar today = dayStart(now());
        int y = hijri(ctx, today)[0];
        Ramadan r = ramadan(ctx, y);
        if (today.after(r.end())) r = ramadan(ctx, y + 1);
        return r;
    }
}
