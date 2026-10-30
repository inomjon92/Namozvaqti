package uz.namozvaqti.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Barcha sozlamalar bitta joyda. */
public final class Prefs {

    public static final int MODE_OFF = 0, MODE_NOTIFY = 1, MODE_SOUND = 2;

    /** Ilova ichidagi ovozlar: resurs nomi va ko'rinadigan nomi. */
    public static final String[] RAW_SOUNDS = {"melody_hijoz", "melody_bell", "melody_tong", "melody_signal"};
    public static final String[] RAW_NAMES = {"Hijoz ohangi", "Qo'ng'iroq", "Tong ohangi", "Qisqa signal"};

    /** Bomdod, Quyosh, Peshin, Asr, Shom, Xufton, Saharlik, Iftor. */
    private static final int[] DEFAULT_MODES = {MODE_SOUND, MODE_NOTIFY, MODE_SOUND, MODE_SOUND, MODE_SOUND, MODE_SOUND,
            MODE_SOUND, MODE_SOUND};

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
    }

    public boolean onboarded() { return sp.getBoolean("onboarded", false); }
    public void setOnboarded() { sp.edit().putBoolean("onboarded", true).apply(); }

    public int region() { return Math.min(sp.getInt("region", 0), Cities.REGIONS.length - 1); }
    public int cityIndex() { return Math.min(sp.getInt("city", 0), Cities.REGIONS[region()].cities.length - 1); }
    public Cities.City city() { return Cities.REGIONS[region()].cities[cityIndex()]; }
    public void setCity(int region, int city) { sp.edit().putInt("region", region).putInt("city", city).apply(); }

    /** Shrift masshtabi foizda: 80..160. */
    public int fontScale() { return sp.getInt("fontScale", 100); }
    public void setFontScale(int v) { sp.edit().putInt("fontScale", Math.max(80, Math.min(160, v))).apply(); }

    public boolean dark() { return sp.getBoolean("dark", false); }
    public void setDark(boolean v) { sp.edit().putBoolean("dark", v).apply(); }

    public int mode(int prayer) { return sp.getInt("mode" + prayer, DEFAULT_MODES[prayer]); }
    public void setMode(int prayer, int v) { sp.edit().putInt("mode" + prayer, v).apply(); }

    /** "raw:<nom>" yoki "uri:<content uri>". */
    public String sound(int prayer) { return sp.getString("sound" + prayer, "raw:" + RAW_SOUNDS[0]); }
    public String soundName(int prayer) { return sp.getString("soundName" + prayer, RAW_NAMES[0]); }
    public void setSound(int prayer, String sound, String name) {
        sp.edit().putString("sound" + prayer, sound).putString("soundName" + prayer, name).apply();
    }

    /** Namozdan oldin eslatma, daqiqada (0 = o'chiq). */
    public int reminder() { return sp.getInt("reminder", 15); }
    public void setReminder(int v) { sp.edit().putInt("reminder", v).apply(); }

    public boolean hanafi() { return sp.getBoolean("hanafi", true); }
    public void setHanafi(boolean v) { sp.edit().putBoolean("hanafi", v).apply(); }

    public int offset(int prayer) { return sp.getInt("offset" + prayer, 0); }
    public void setOffset(int prayer, int v) { sp.edit().putInt("offset" + prayer, Math.max(-10, Math.min(10, v))).apply(); }
    public int[] offsets() {
        int[] o = new int[PrayTimes.COUNT];
        for (int i = 0; i < o.length; i++) o[i] = offset(i);
        return o;
    }

    /** Ramazon boshlanishini rasmiy e'longa moslash: -1, 0, +1 kun. */
    public int ramadanShift() { return sp.getInt("ramadanShift", 0); }
    public void setRamadanShift(int v) { sp.edit().putInt("ramadanShift", Math.max(-1, Math.min(1, v))).apply(); }
}
