package uz.namozvaqti.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {

    private static final int TAB_HOME = 0, TAB_MONTH = 1, TAB_RAMADAN = 2, TAB_QIBLA = 3, TAB_SETTINGS = 4;
    private static final String[] TAB_NAMES = {"Bosh", "Jadval", "Ro'za", "Qibla", "Sozlama"};
    private static final int REQ_NOTIF = 1, REQ_LOCATION = 2, REQ_RECORD = 3;
    private static final int REQ_RINGTONE = 100, REQ_FILE = 200;

    private Prefs prefs;
    private Ui ui;
    private int tab = TAB_HOME;
    private int monthOffset;
    private boolean advancedOpen;
    private int pendingRecordPrayer = -1;
    private int pendingRecordDialog = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView countdown;
    private int renderedNext = -1;
    private TextToSpeech tts;
    private final Recorder recorder = new Recorder();

    private SensorManager sensors;
    private CompassView compass;
    private TextView compassText;
    private float declination;
    private float smoothHeading = Float.NaN;
    private boolean wasAligned;
    private boolean usingRotationVector;
    private boolean needsCalibration;
    private float[] gravity, geomagnetic;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        Recorder.init(this);
        Notifier.createChannels(this);
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        render();
        Scheduler.schedule(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(ticker);
        if (tab == TAB_QIBLA) startCompass();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        stopCompass();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        recorder.stop();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- Umumiy tuzilma

    private void render() {
        ui = new Ui(this);
        stopCompass();
        countdown = null;
        getWindow().setStatusBarColor(ui.bg);
        getWindow().setNavigationBarColor(ui.bg);
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(ui.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        if (!prefs.onboarded()) {
            setContentView(scroll(onboarding()));
            return;
        }

        LinearLayout root = ui.vbox();
        root.setBackgroundColor(ui.bg);
        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        View page;
        switch (tab) {
            case TAB_MONTH: page = monthPage(); break;
            case TAB_RAMADAN: page = ramadanPage(); break;
            case TAB_QIBLA: page = qiblaPage(); break;
            case TAB_SETTINGS: page = settingsPage(); break;
            default: page = homePage(); break;
        }
        content.addView(page);
        root.addView(bottomBar());
        setContentView(root);
        if (tab == TAB_QIBLA) startCompass();
    }

    private ScrollView scroll(LinearLayout inner) {
        ScrollView s = new ScrollView(this);
        s.setBackgroundColor(ui.bg);
        s.setFillViewport(true);
        int pad = ui.dp(16);
        inner.setPadding(pad, pad, pad, pad * 2);
        s.addView(inner);
        return s;
    }

    private View bottomBar() {
        LinearLayout bar = ui.hbox();
        bar.setBackgroundColor(ui.surface);
        bar.setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(6));
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int idx = i;
            TextView t = ui.text(TAB_NAMES[i], 0, i == tab ? ui.btnText : ui.text, true, Gravity.CENTER);
            // Pastki menyu yozuvi: 18sp dan, masshtab bilan o'sadi, lekin sig'ishi uchun 24sp bilan cheklangan.
            int size = (int) Math.min(24, Math.max(18, ui.sp(18)));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
            t.setSingleLine(true);
            t.setMaxLines(1);
            // Uzun yozuv (masalan, "Sozlama") ikki qatorga tushmasin: sig'maguncha kichrayadi.
            t.setAutoSizeTextTypeUniformWithConfiguration(12, size, 1, TypedValue.COMPLEX_UNIT_SP);
            t.setMinHeight(ui.dp(64));
            t.setGravity(Gravity.CENTER);
            t.setBackground(i == tab ? ui.rounded(ui.btnBg, 0, 0, 14) : null);
            t.setContentDescription(TAB_NAMES[i] + " bo'limi");
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tab = idx;
                    render();
                }
            });
            bar.addView(t, Ui.weight(1, ui.dp(2)));
        }
        return bar;
    }

    private TextView heading(String s) {
        TextView t = ui.text(s, 28, ui.text, true);
        t.setLayoutParams(Ui.match(ui.dp(8)));
        return t;
    }

    private void add(LinearLayout parent, View v, int topDp) {
        parent.addView(v, Ui.match(ui.dp(topDp)));
    }

    // ---------------------------------------------------------------- Bosh sahifa

    /** {joriy namoz indeksi yoki -1, keyingi namoz indeksi, keyingi namoz ertaga bo'lsa 1} */
    private int[] currentAndNext(int[] t, Calendar now) {
        int mins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int next = -1;
        for (int i = 0; i < PrayTimes.COUNT; i++) {
            if (t[i] > mins) {
                next = i;
                break;
            }
        }
        int current = next <= 0 ? PrayTimes.ISHA : next - 1;
        if (current == PrayTimes.SUNRISE) current = -1; // Quyosh chiqqandan Peshingacha namoz vaqti yo'q.
        return new int[]{current, next == -1 ? 0 : next, next == -1 ? 1 : 0};
    }

    private View homePage() {
        LinearLayout v = ui.vbox();
        Calendar now = Calc.now();
        int[] t = Calc.times(this, now);
        int[] cn = currentAndNext(t, now);
        renderedNext = cn[1] * 10 + cn[2];

        Cities.City city = prefs.city();
        v.addView(ui.text(city.name, 30, ui.text, true));
        add(v, ui.text(Calc.gregorian(now), 22, ui.sub, false), 2);
        add(v, ui.text(Calc.hijriText(this, now), 22, ui.sub, false), 0);

        Calc.Ramadan ram = Calc.currentOrNextRamadan(this);
        int rIdx = ram.indexOf(now);
        if (rIdx >= 0) {
            LinearLayout rc = ui.card(ui.surface, ui.border, 2);
            rc.addView(ui.text("Ramazonning " + (rIdx + 1) + "-kuni", 24, ui.accentText, true));
            rc.addView(ui.text("Saharlik: " + Calc.hhmm(t[PrayTimes.FAJR]) + "   Iftor: " + Calc.hhmm(t[PrayTimes.MAGHRIB]), 26, ui.text, true));
            add(rc, ui.text("Saharlik eslatmasi", 20, ui.sub, false), 8);
            add(rc, alertRow(Calc.SAHAR), 4);
            add(rc, ui.text("Iftor eslatmasi", 20, ui.sub, false), 8);
            add(rc, alertRow(Calc.IFTOR), 4);
            add(v, rc, 12);
        }

        // Keyingi namoz kartasi.
        Calendar nextDay = (Calendar) now.clone();
        if (cn[2] == 1) nextDay.add(Calendar.DAY_OF_MONTH, 1);
        int[] tn = cn[2] == 1 ? Calc.times(this, nextDay) : t;
        LinearLayout card = ui.card(ui.cardBg, 0, 0);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(ui.text("Keyingi:", 22, ui.cardText, false, Gravity.CENTER));
        card.addView(ui.text(Calc.name(cn[1], nextDay), 36, ui.cardText, true, Gravity.CENTER));
        card.addView(ui.text(Calc.hhmm(tn[cn[1]]), 64, ui.cardText, true, Gravity.CENTER));
        countdown = ui.text("", 24, ui.cardText, false, Gravity.CENTER);
        card.addView(countdown);
        card.setContentDescription("Keyingi namoz " + Calc.name(cn[1], nextDay) + ", soat " + Calc.hhmm(tn[cn[1]]));
        add(v, card, 14);

        // 6 ta vaqt ro'yxati.
        int mins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        for (int i = 0; i < PrayTimes.COUNT; i++) {
            boolean isCurrent = i == cn[0];
            boolean past = t[i] <= mins && !isCurrent;
            LinearLayout row = ui.hbox();
            row.setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10));
            row.setBackground(isCurrent ? ui.rounded(ui.surface, ui.border, 4, 16) : ui.rounded(ui.bg, ui.surface, 2, 16));
            LinearLayout left = ui.vbox();
            left.addView(ui.text(Calc.name(i, now), 28, past ? ui.sub : ui.text, isCurrent));
            if (isCurrent) left.addView(ui.text("HOZIR", 20, ui.accentText, true));
            row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(ui.text(Calc.hhmm(t[i]), 36, past ? ui.sub : ui.text, true, Gravity.END));
            row.setContentDescription(Calc.name(i, now) + ", soat " + Calc.hhmm(t[i]) + (isCurrent ? ", hozirgi vaqt" : ""));
            LinearLayout box = ui.vbox();
            box.setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(12));
            box.setBackground(row.getBackground());
            row.setBackground(null);
            row.setPadding(0, 0, 0, 0);
            box.addView(row);
            add(box, alertRow(i), 8);
            add(v, box, 8);
        }

        add(v, ui.button("Vaqtlarni ovoz bilan o'qish", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                speakToday();
            }
        }), 14);
        tick();
        return scroll(v);
    }

    /** Vaqt ostidagi qator: qo'ng'iroqcha (ovozni yoqish/o'chirish) va ovoz tanlash tugmasi. */
    private View alertRow(final int prayer) {
        LinearLayout r = ui.hbox();
        final boolean on = prefs.mode(prayer) != Prefs.MODE_OFF;
        TextView bell = ui.text(on ? "\uD83D\uDD14" : "\uD83D\uDD15", 30, ui.text, false, Gravity.CENTER);
        bell.setMinHeight(ui.dp(64));
        bell.setBackground(on ? ui.rounded(ui.surface, ui.border, 2, 14) : ui.rounded(ui.surface, 0xFFC62828, 2, 14));
        bell.setContentDescription(Calc.alertName(prayer) + (on ? ": ovoz yoqilgan, o'chirish uchun bosing" : ": ovoz o'chiq, yoqish uchun bosing"));
        bell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.setMode(prayer, on ? Prefs.MODE_OFF : Prefs.MODE_SOUND);
                Scheduler.schedule(MainActivity.this);
                toast(Calc.alertName(prayer) + (on ? ": ovoz o'chirildi" : ": ovoz yoqildi"));
                render();
            }
        });
        r.addView(bell, new LinearLayout.LayoutParams(ui.dp(76), ViewGroup.LayoutParams.WRAP_CONTENT));
        String label;
        int mode = prefs.mode(prayer);
        if (mode == Prefs.MODE_OFF) label = "Ovozsiz";
        else if (mode == Prefs.MODE_NOTIFY) label = "Faqat bildirishnoma";
        else label = "\u266A " + prefs.soundName(prayer);
        Button sound = ui.button(label, false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickSound(prayer);
            }
        });
        sound.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.sp(19));
        sound.setSingleLine(true);
        sound.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sound.setContentDescription(Calc.alertName(prayer) + " uchun ovozni tanlash. Hozir: " + label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = ui.dp(8);
        r.addView(sound, lp);
        return r;
    }

    private void tick() {
        if (countdown == null || tab != TAB_HOME || !prefs.onboarded()) return;
        Calendar now = Calc.now();
        int[] t = Calc.times(this, now);
        int[] cn = currentAndNext(t, now);
        if (cn[1] * 10 + cn[2] != renderedNext) {
            render();
            return;
        }
        Calendar day = (Calendar) now.clone();
        if (cn[2] == 1) day.add(Calendar.DAY_OF_MONTH, 1);
        int[] tn = cn[2] == 1 ? Calc.times(this, day) : t;
        long diff = Calc.at(day, tn[cn[1]]) - now.getTimeInMillis();
        long totalMin = (diff + 59999) / 60000;
        String s;
        if (totalMin <= 1) s = "1 daqiqadan kam qoldi";
        else if (totalMin < 60) s = totalMin + " daqiqadan keyin";
        else if (totalMin % 60 == 0) s = (totalMin / 60) + " soatdan keyin";
        else s = (totalMin / 60) + " soat " + (totalMin % 60) + " daqiqadan keyin";
        countdown.setText(s);
    }

    private void speakToday() {
        final Calendar now = Calc.now();
        final int[] t = Calc.times(this, now);
        final StringBuilder sb = new StringBuilder("Bugungi namoz vaqtlari. ");
        for (int i = 0; i < PrayTimes.COUNT; i++) {
            sb.append(Calc.name(i, now)).append(", soat ").append(t[i] / 60).append(" dan ")
                    .append(t[i] % 60).append(" daqiqa o'tganda. ");
        }
        if (tts != null) {
            tts.speak(sb.toString(), TextToSpeech.QUEUE_FLUSH, null, "today");
            return;
        }
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS) {
                    toast("Telefonda ovozli o'qish dasturi topilmadi");
                    tts = null;
                    return;
                }
                int r = tts.setLanguage(new Locale("uz", "UZ"));
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    toast("Telefoningizda o'zbekcha ovoz o'rnatilmagan. Sozlamalar > Til > Matnni nutqqa aylantirish");
                }
                tts.setSpeechRate(0.85f);
                tts.speak(sb.toString(), TextToSpeech.QUEUE_FLUSH, null, "today");
            }
        });
    }

    // ---------------------------------------------------------------- Oylik jadval

    private View monthPage() {
        LinearLayout v = ui.vbox();
        Calendar m = Calc.dayStart(Calc.now());
        m.set(Calendar.DAY_OF_MONTH, 1);
        m.add(Calendar.MONTH, monthOffset);
        String title = Calc.MONTHS[m.get(Calendar.MONTH)];
        title = Character.toUpperCase(title.charAt(0)) + title.substring(1) + " " + m.get(Calendar.YEAR);
        v.addView(ui.text(title, 32, ui.text, true, Gravity.CENTER));
        v.addView(ui.text(prefs.city().name, 22, ui.sub, false, Gravity.CENTER));

        LinearLayout nav = ui.hbox();
        nav.addView(ui.button("Oldingi oy", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                monthOffset--;
                render();
            }
        }), Ui.weight(1, ui.dp(4)));
        nav.addView(ui.button("Keyingi oy", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                monthOffset++;
                render();
            }
        }), Ui.weight(1, ui.dp(4)));
        add(v, nav, 10);

        Calendar today = Calc.dayStart(Calc.now());
        int days = m.getActualMaximum(Calendar.DAY_OF_MONTH);
        View todayCard = null;
        for (int d = 1; d <= days; d++) {
            Calendar day = (Calendar) m.clone();
            day.set(Calendar.DAY_OF_MONTH, d);
            boolean isToday = day.getTimeInMillis() == today.getTimeInMillis();
            int[] t = Calc.times(this, day);
            LinearLayout card = ui.card(isToday ? ui.surface : ui.bg, isToday ? ui.border : ui.surface, isToday ? 4 : 2);
            card.addView(ui.text(Calc.gregorian(day) + (isToday ? " (bugun)" : ""), 24, isToday ? ui.accentText : ui.text, true));
            for (int r = 0; r < 2; r++) {
                LinearLayout row = ui.hbox();
                for (int c = 0; c < 3; c++) {
                    int i = r * 3 + c;
                    LinearLayout cell = ui.vbox();
                    cell.addView(ui.text(Calc.name(i, day), 20, ui.sub, false));
                    cell.addView(ui.text(Calc.hhmm(t[i]), 30, ui.text, true));
                    row.addView(cell, Ui.weight(1, 0));
                }
                add(card, row, 6);
            }
            add(v, card, 10);
            if (isToday) todayCard = card;
        }
        final ScrollView s = scroll(v);
        final View target = todayCard;
        if (target != null) {
            s.post(new Runnable() {
                @Override
                public void run() {
                    s.scrollTo(0, Math.max(0, target.getTop() - ui.dp(16)));
                }
            });
        }
        return s;
    }

    // ---------------------------------------------------------------- Ro'za taqvimi

    private View ramadanPage() {
        LinearLayout v = ui.vbox();
        Calc.Ramadan ram = Calc.currentOrNextRamadan(this);
        Calendar today = Calc.dayStart(Calc.now());
        int idx = ram.indexOf(today);

        v.addView(ui.text("Ro'za taqvimi", 32, ui.text, true, Gravity.CENTER));
        v.addView(ui.text("Ramazon " + ram.hijriYear + " h. · " + prefs.city().name, 22, ui.sub, false, Gravity.CENTER));
        v.addView(ui.text(Calc.shortDate(ram.start) + " " + ram.start.get(Calendar.YEAR) + " — "
                + Calc.shortDate(ram.end()) + " " + ram.end().get(Calendar.YEAR), 22, ui.sub, false, Gravity.CENTER));

        LinearLayout status = ui.card(ui.cardBg, 0, 0);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        if (idx >= 0) {
            int[] t = Calc.times(this, today);
            status.addView(ui.text("Bugun Ramazonning " + (idx + 1) + "-kuni", 24, ui.cardText, true, Gravity.CENTER));
            status.addView(ui.text("Saharlik (og'iz yopish)", 22, ui.cardText, false, Gravity.CENTER));
            status.addView(ui.text(Calc.hhmm(t[PrayTimes.FAJR]), 52, ui.cardText, true, Gravity.CENTER));
            status.addView(ui.text("Iftor (og'iz ochish)", 22, ui.cardText, false, Gravity.CENTER));
            status.addView(ui.text(Calc.hhmm(t[PrayTimes.MAGHRIB]), 52, ui.cardText, true, Gravity.CENTER));
        } else {
            long days = Math.round((ram.start.getTimeInMillis() - today.getTimeInMillis()) / 86400000.0);
            status.addView(ui.text("Ramazon boshlanishiga", 24, ui.cardText, false, Gravity.CENTER));
            status.addView(ui.text(days + " kun qoldi", 44, ui.cardText, true, Gravity.CENTER));
            status.addView(ui.text("Taqvim har yili o'zi yangilanadi", 20, ui.cardText, false, Gravity.CENTER));
        }
        add(v, status, 12);

        add(v, heading("Eslatmalar"), 14);
        add(v, ui.text("Namoz vaqtlari kabi: qo'ng'iroqcha bilan yoqing yoki o'chiring, ovozini tanlang.", 20, ui.sub, false), 4);
        LinearLayout sc = ui.card(ui.surface, 0, 0);
        sc.addView(ui.text("Saharlik", 26, ui.text, true));
        sc.addView(ui.text("Og'iz yopishdan " + Calc.SAHAR_BEFORE + " daqiqa oldin", 20, ui.sub, false));
        add(sc, alertRow(Calc.SAHAR), 8);
        add(v, sc, 8);
        LinearLayout ic = ui.card(ui.surface, 0, 0);
        ic.addView(ui.text("Iftor", 26, ui.text, true));
        ic.addView(ui.text("Og'iz ochish vaqtida (Shom)", 20, ui.sub, false));
        add(ic, alertRow(Calc.IFTOR), 8);
        add(v, ic, 8);

        LinearLayout duo = ui.card(ui.surface, 0, 0);
        duo.addView(ui.text("Saharlik duosi (niyat)", 22, ui.accentText, true));
        duo.addView(ui.text("Navaytu an asuma sovma shahri ramazona minal fajri ilal mag'ribi, xolisan lillahi ta'ala. Allohu akbar.", 22, ui.text, false));
        add(duo, ui.text("Iftorlik duosi", 22, ui.accentText, true), 10);
        duo.addView(ui.text("Allohumma laka sumtu va bika amantu va 'alayka tavakkaltu va 'ala rizqika aftartu, fag'firli ya G'offaru ma qoddamtu va ma axxortu.", 22, ui.text, false));
        add(v, duo, 12);

        add(v, heading("Barcha kunlar"), 12);
        View todayCard = null;
        for (int i = 0; i < ram.days; i++) {
            Calendar day = ram.day(i);
            int[] t = Calc.times(this, day);
            boolean isToday = i == idx;
            LinearLayout card = ui.card(isToday ? ui.surface : ui.bg, isToday ? ui.border : ui.surface, isToday ? 4 : 2);
            card.addView(ui.text((i + 1) + "-kun · " + Calc.gregorian(day), 22, isToday ? ui.accentText : ui.text, true));
            LinearLayout row = ui.hbox();
            LinearLayout a = ui.vbox();
            a.addView(ui.text("Saharlik", 20, ui.sub, false));
            a.addView(ui.text(Calc.hhmm(t[PrayTimes.FAJR]), 32, ui.text, true));
            LinearLayout b = ui.vbox();
            b.addView(ui.text("Iftor", 20, ui.sub, false));
            b.addView(ui.text(Calc.hhmm(t[PrayTimes.MAGHRIB]), 32, ui.text, true));
            row.addView(a, Ui.weight(1, 0));
            row.addView(b, Ui.weight(1, 0));
            card.addView(row);
            card.setContentDescription((i + 1) + "-kun, saharlik " + Calc.hhmm(t[PrayTimes.FAJR]) + ", iftor " + Calc.hhmm(t[PrayTimes.MAGHRIB]));
            add(v, card, 8);
            if (isToday) todayCard = card;
        }

        add(v, heading("Rasmiy e'longa moslash"), 16);
        add(v, ui.text("Ramazon rasmiy e'londa bir kun oldin yoki keyin boshlansa, shu yerda to'g'rilang.", 20, ui.sub, false), 4);
        LinearLayout shift = ui.hbox();
        String[] labels = {"1 kun oldin", "Hisob bo'yicha", "1 kun keyin"};
        for (int s = -1; s <= 1; s++) {
            final int val = s;
            Button btn = ui.button(labels[s + 1], prefs.ramadanShift() == s, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    prefs.setRamadanShift(val);
                    Scheduler.schedule(MainActivity.this);
                    render();
                }
            });
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.sp(18));
            shift.addView(btn, Ui.weight(1, ui.dp(3)));
        }
        add(v, shift, 8);

        final ScrollView sv = scroll(v);
        final View target = todayCard;
        if (target != null) {
            sv.post(new Runnable() {
                @Override
                public void run() {
                    sv.scrollTo(0, Math.max(0, target.getTop() - ui.dp(16)));
                }
            });
        }
        return sv;
    }

    private Button toggleButton(String label, boolean on, View.OnClickListener l) {
        return ui.button((on ? "Yoqilgan: " : "O'chiq: ") + label, on, l);
    }

    // ---------------------------------------------------------------- Qibla

    private View qiblaPage() {
        LinearLayout v = ui.vbox();
        Cities.City c = prefs.city();
        float q = (float) PrayTimes.qiblaBearing(c.lat, c.lng);
        v.addView(ui.text("Qibla", 32, ui.text, true, Gravity.CENTER));
        v.addView(ui.text(c.name + " · shimoldan " + Math.round(q) + "°", 24, ui.sub, false, Gravity.CENTER));
        compass = new CompassView(this, ui, q);
        compass.setContentDescription("Qibla kompasi");
        add(v, compass, 10);
        compassText = ui.text("Telefonni tekis ushlang va strelka tepaga qaraguncha aylaning", 24, ui.text, true, Gravity.CENTER);
        add(v, compassText, 8);
        add(v, ui.text("Kompas noto'g'ri ko'rsatsa, telefonni havoda 8 raqami shaklida bir necha marta aylantiring.", 20, ui.sub, false, Gravity.CENTER), 12);
        GeomagneticField g = new GeomagneticField((float) c.lat, (float) c.lng, 0, System.currentTimeMillis());
        declination = g.getDeclination();
        if (!hasCompass()) {
            compassText.setText("Telefoningizda kompas (magnit datchigi) yo'q. Qibla shimoldan soat yo'nalishi bo'yicha "
                    + Math.round(q) + "° da.");
        }
        return scroll(v);
    }

    private boolean hasCompass() {
        return sensors != null && (sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
                || sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null);
    }

    /**
     * Arzon telefonlarda giroskop bo'lmaydi va TYPE_ROTATION_VECTOR ishlamaydi,
     * shuning uchun akselerometr + magnit datchigi ham ishlatiladi.
     */
    private void startCompass() {
        if (sensors == null || compass == null) return;
        gravity = null;
        geomagnetic = null;
        Sensor rv = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        Sensor acc = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor mag = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (rv != null) {
            usingRotationVector = true;
            sensors.registerListener(this, rv, SensorManager.SENSOR_DELAY_UI);
        } else if (acc != null && mag != null) {
            usingRotationVector = false;
            sensors.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI);
            sensors.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI);
        }
        // Magnit datchigi aniqligini kuzatish (kalibrovka kerakmi).
        if (rv != null && mag != null) sensors.registerListener(this, mag, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void stopCompass() {
        if (sensors != null) sensors.unregisterListener(this);
        smoothHeading = Float.NaN;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (compass == null || compassText == null) return;
        int type = e.sensor.getType();
        float[] rot = new float[9];
        if (type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rot, e.values);
        } else if (usingRotationVector) {
            return; // Faqat aniqlik uchun ro'yxatdan o'tgan magnit datchigi.
        } else {
            if (type == Sensor.TYPE_ACCELEROMETER) gravity = lowPass(e.values, gravity);
            else if (type == Sensor.TYPE_MAGNETIC_FIELD) geomagnetic = lowPass(e.values, geomagnetic);
            if (gravity == null || geomagnetic == null) return;
            if (!SensorManager.getRotationMatrix(rot, null, gravity, geomagnetic)) return;
        }
        // Telefon tik ushlansa (ekran yuzga qaragan), o'qlarni almashtiramiz.
        float[] o = new float[3];
        SensorManager.getOrientation(rot, o);
        if (Math.abs(Math.toDegrees(o[1])) > 50) {
            float[] remapped = new float[9];
            SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped);
            SensorManager.getOrientation(remapped, o);
        }
        float h = (float) ((Math.toDegrees(o[0]) + declination + 720) % 360);
        if (Float.isNaN(smoothHeading)) smoothHeading = h;
        float diff = ((h - smoothHeading + 540) % 360) - 180;
        smoothHeading = (smoothHeading + diff * 0.2f + 360) % 360;
        compass.setHeading(smoothHeading);
        Cities.City c = prefs.city();
        float q = (float) PrayTimes.qiblaBearing(c.lat, c.lng);
        float off = ((q - smoothHeading + 540) % 360) - 180;
        boolean aligned = Math.abs(off) < 5;
        compass.setAligned(aligned);
        if (needsCalibration) compassText.setText("Telefonni havoda 8 raqami shaklida aylantiring");
        else if (aligned) compassText.setText("Qibla to'g'ri!");
        else compassText.setText((off > 0 ? "O'ngga buriling: " : "Chapga buriling: ") + Math.round(Math.abs(off)) + "°");
        if (aligned && !wasAligned) vibrate(150);
        wasAligned = aligned;
    }

    private static float[] lowPass(float[] in, float[] out) {
        if (out == null) return in.clone();
        for (int i = 0; i < in.length && i < out.length; i++) out[i] = out[i] + 0.25f * (in[i] - out[i]);
        return out;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            needsCalibration = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW;
        }
    }

    private void vibrate(long ms) {
        Vibrator vb = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vb == null) return;
        if (Build.VERSION.SDK_INT >= 26) vb.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        else vb.vibrate(ms);
    }

    // ---------------------------------------------------------------- Sozlamalar

    private View settingsPage() {
        LinearLayout v = ui.vbox();
        v.addView(ui.text("Sozlamalar", 32, ui.text, true, Gravity.CENTER));

        fontSection(v);

        add(v, heading("Ko'rinish"), 18);
        LinearLayout theme = ui.hbox();
        theme.addView(ui.button("Yorug'", !prefs.dark(), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.setDark(false);
                render();
            }
        }), Ui.weight(1, ui.dp(4)));
        theme.addView(ui.button("Qorong'i", prefs.dark(), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.setDark(true);
                render();
            }
        }), Ui.weight(1, ui.dp(4)));
        add(v, theme, 8);

        add(v, heading("Shahar"), 18);
        citySection(v);

        add(v, heading("Namoz ovozlari"), 18);
        add(v, ui.text("Ovoz chalinayotganda telefonning ovoz tugmasini bossangiz, ovoz o'chadi.", 20, ui.sub, false), 4);
        for (int i = 0; i < Calc.ALERTS; i++) prayerSoundCard(v, i);


        add(v, heading("Oldindan eslatma"), 18);
        final int[] opts = {0, 5, 10, 15, 30};
        add(v, ui.button("Namozdan oldin: " + reminderLabel(prefs.reminder()), false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String[] items = new String[opts.length];
                for (int i = 0; i < opts.length; i++) items[i] = reminderLabel(opts[i]);
                choose("Oldindan eslatma", items, new Pick() {
                    @Override
                    public void picked(int i) {
                        prefs.setReminder(opts[i]);
                        Scheduler.schedule(MainActivity.this);
                        render();
                    }
                });
            }
        }), 8);

        add(v, heading("Ruxsatlar"), 18);
        add(v, ui.button("Bildirishnomaga ruxsat berish", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                askNotifications();
            }
        }), 8);
        add(v, ui.button("Batareya tejash cheklovini o'chirish", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                askBattery();
            }
        }), 8);
        if (!Scheduler.canExact(this)) {
            add(v, ui.button("Aniq vaqtda chalish ruxsati", true, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (Build.VERSION.SDK_INT >= 31) {
                        startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
                    }
                }
            }), 8);
        }

        advancedSection(v);

        add(v, heading("Ilova haqida"), 18);
        add(v, ui.text("Namoz vaqtlari — Katta shrift. Bepul ilova, reklamasiz. Internetsiz ishlaydi.\n"
                + "Hisoblash: Bomdod va Xufton " + (int) Calc.FAJR_ANGLE + "°, Asr — " + (prefs.hanafi() ? "Hanafiy" : "Shofiiy")
                + " usulida. Vaqtlarni masjid taqvimi bilan solishtirib, kerak bo'lsa \"Kengaytirilgan\" bo'limida daqiqalarni to'g'rilang.", 20, ui.sub, false), 4);
        return scroll(v);
    }

    private void fontSection(LinearLayout v) {
        add(v, heading("Shrift o'lchami"), 14);
        LinearLayout row = ui.hbox();
        Button minus = ui.button("A −", true, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.setFontScale(prefs.fontScale() - 10);
                render();
            }
        });
        minus.setContentDescription("Shriftni kichraytirish");
        Button plus = ui.button("A +", true, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.setFontScale(prefs.fontScale() + 10);
                render();
            }
        });
        plus.setContentDescription("Shriftni kattalashtirish");
        row.addView(minus, Ui.weight(1, ui.dp(4)));
        row.addView(ui.text(prefs.fontScale() + "%", 30, ui.text, true, Gravity.CENTER), Ui.weight(1, 0));
        row.addView(plus, Ui.weight(1, ui.dp(4)));
        add(v, row, 8);
        LinearLayout sample = ui.card(ui.surface, 0, 0);
        sample.addView(ui.text("Namuna:", 20, ui.sub, false));
        LinearLayout sr = ui.hbox();
        sr.addView(ui.text("Peshin", 28, ui.text, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sr.addView(ui.text("12:30", 36, ui.text, true));
        sample.addView(sr);
        add(v, sample, 8);
    }

    private void citySection(LinearLayout v) {
        add(v, ui.button(prefs.city().name + " (o'zgartirish)", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickCity();
            }
        }), 8);
        add(v, ui.button("Joylashuvni avtomatik aniqlash", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                detectLocation();
            }
        }), 8);
    }

    private static final String[] MODE_LABELS = {"O'chiq", "Faqat bildirishnoma", "Ovoz bilan"};

    private void prayerSoundCard(LinearLayout v, final int prayer) {
        LinearLayout card = ui.card(ui.surface, 0, 0);
        String title = Calc.alertName(prayer);
        if (prayer == PrayTimes.DHUHR) title += " / Juma";
        if (prayer == Calc.SAHAR) title += " (Ramazonda, Bomdoddan " + Calc.SAHAR_BEFORE + " daqiqa oldin)";
        if (prayer == Calc.IFTOR) title += " (Ramazonda, Shom vaqtida)";
        card.addView(ui.text(title, 26, ui.text, true));
        int mode = prefs.mode(prayer);
        add(card, ui.button("Holati: " + MODE_LABELS[mode], mode == Prefs.MODE_SOUND, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                choose(Calc.alertName(prayer) + " uchun", MODE_LABELS, new Pick() {
                    @Override
                    public void picked(int i) {
                        prefs.setMode(prayer, i);
                        Scheduler.schedule(MainActivity.this);
                        render();
                    }
                });
            }
        }), 8);
        if (mode == Prefs.MODE_SOUND) {
            add(card, ui.button("Ovoz: " + prefs.soundName(prayer), false, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    pickSound(prayer);
                }
            }), 8);
            add(card, ui.button(AzanService.running ? "To'xtatish" : "Sinab eshitish", false, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (AzanService.running) {
                        AzanService.stop(MainActivity.this);
                    } else {
                        startService(new Intent(MainActivity.this, AzanService.class)
                                .putExtra(AzanService.EXTRA_PRAYER, prayer)
                                .putExtra(AzanService.EXTRA_PREVIEW_SOUND, prefs.sound(prayer)));
                    }
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            render();
                        }
                    }, 300);
                }
            }), 8);
        }
        add(v, card, 10);
    }

    private void pickSound(final int prayer) {
        final int n = Prefs.RAW_SOUNDS.length;
        String[] items = new String[n + 3];
        items[0] = "O'z ovozim (yozib olish)";
        for (int i = 0; i < n; i++) items[i + 1] = Prefs.RAW_NAMES[i];
        items[n + 1] = "Telefon ovozlari (ohanglar)";
        items[n + 2] = "Telefondagi fayl (azon, qo'shiq mp3)";
        choose(Calc.alertName(prayer) + " uchun ovoz", items, new Pick() {
            @Override
            public void picked(int i) {
                if (prefs.mode(prayer) != Prefs.MODE_SOUND) {
                    prefs.setMode(prayer, Prefs.MODE_SOUND);
                    Scheduler.schedule(MainActivity.this);
                }
                if (i == 0) {
                    recordDialog(prayer);
                    return;
                }
                i--;
                if (i < n) {
                    prefs.setSound(prayer, "raw:" + Prefs.RAW_SOUNDS[i], Prefs.RAW_NAMES[i]);
                    render();
                } else if (i == n) {
                    Intent it = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                    it.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL);
                    it.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Ovozni tanlang");
                    it.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
                    startActivityForResult(it, REQ_RINGTONE + prayer);
                } else {
                    Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    it.addCategory(Intent.CATEGORY_OPENABLE);
                    it.setType("audio/*");
                    startActivityForResult(it, REQ_FILE + prayer);
                }
            }
        });
    }

    /** O'z ovozingizni yozib olish oynasi: yozilgan ovoz namoz vaqti kirganda chalinadi. */
    private void recordDialog(final int prayer) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingRecordDialog = prayer;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD);
            return;
        }
        recorder.stop();
        LinearLayout box = ui.vbox();
        box.setBackgroundColor(ui.bg);
        int pad = ui.dp(18);
        box.setPadding(pad, pad, pad, pad);
        box.addView(ui.text(Calc.alertName(prayer) + " uchun o'z ovozingiz", 28, ui.text, true));
        add(box, ui.text("\"Yozish\"ni bosing va ayting, masalan: \"" + Calc.alertName(prayer)
                + " namozi vaqti kirdi\". Keyin \"To'xtatish\"ni bosing.", 21, ui.sub, false), 8);
        final TextView status = ui.text("", 24, ui.accentText, true, Gravity.CENTER);
        add(box, status, 12);
        final Button rec = ui.button("Yozish", true, null);
        final Button listen = ui.button("Eshitib ko'rish", false, null);
        final Button save = ui.button("Saqlash", true, null);
        Button cancel = ui.button("Bekor qilish", false, null);
        add(box, rec, 10);
        add(box, listen, 10);
        add(box, save, 10);
        add(box, cancel, 10);
        int theme = ui.dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        final AlertDialog d = new AlertDialog.Builder(this, theme).setView(sv).create();
        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                File f = Recorder.file(prefs, prayer);
                boolean has = f != null && f.exists() && f.length() > 0;
                if (recorder.isRecording()) {
                    status.setText("Yozilmoqda... gapiring");
                    status.setTextColor(0xFFC62828);
                    rec.setText("To'xtatish");
                } else {
                    status.setText(has ? "Ovoz yozilgan" : "Hali yozilmagan");
                    status.setTextColor(ui.accentText);
                    rec.setText(has ? "Qaytadan yozish" : "Yozish");
                }
                listen.setEnabled(has && !recorder.isRecording());
                listen.setAlpha(listen.isEnabled() ? 1f : 0.4f);
                save.setEnabled(has && !recorder.isRecording());
                save.setAlpha(save.isEnabled() ? 1f : 0.4f);
            }
        };
        rec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (recorder.isRecording()) {
                    recorder.stop();
                } else {
                    AzanService.stop(MainActivity.this);
                    if (!recorder.start(MainActivity.this, prayer)) toast("Yozib bo'lmadi");
                }
                refresh.run();
            }
        });
        listen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startService(new Intent(MainActivity.this, AzanService.class)
                        .putExtra(AzanService.EXTRA_PRAYER, prayer)
                        .putExtra(AzanService.EXTRA_PREVIEW_SOUND, "rec:" + prayer));
            }
        });
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.setSound(prayer, "rec:" + prayer, "O'z ovozim");
                prefs.setMode(prayer, Prefs.MODE_SOUND);
                Scheduler.schedule(MainActivity.this);
                d.dismiss();
                toast("Saqlandi. " + Calc.alertName(prayer) + " vaqtida shu ovoz chalinadi.");
                render();
            }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                d.dismiss();
            }
        });
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface di) {
                recorder.stop();
            }
        });
        refresh.run();
        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void toggleRecord(int prayer) {
        if (recorder.isRecording()) {
            boolean same = recorder.recordingPrayer() == prayer;
            recorder.stop();
            render();
            if (same) return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingRecordPrayer = prayer;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD);
            return;
        }
        if (!recorder.start(this, prayer)) toast("Yozib bo'lmadi");
        render();
    }

    private void advancedSection(LinearLayout v) {
        add(v, ui.button(advancedOpen ? "Kengaytirilgan sozlamalarni yopish" : "Kengaytirilgan sozlamalar", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                advancedOpen = !advancedOpen;
                render();
            }
        }), 18);
        if (!advancedOpen) return;
        add(v, heading("Asr usuli"), 12);
        LinearLayout asr = ui.hbox();
        asr.addView(ui.button("Hanafiy", prefs.hanafi(), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.setHanafi(true);
                Scheduler.schedule(MainActivity.this);
                render();
            }
        }), Ui.weight(1, ui.dp(4)));
        asr.addView(ui.button("Shofiiy", !prefs.hanafi(), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.setHanafi(false);
                Scheduler.schedule(MainActivity.this);
                render();
            }
        }), Ui.weight(1, ui.dp(4)));
        add(v, asr, 8);

        add(v, heading("Vaqtni to'g'rilash (daqiqa)"), 16);
        add(v, ui.text("Masjid taqvimi bilan farq bo'lsa, daqiqani qo'shing yoki ayiring.", 20, ui.sub, false), 4);
        for (int i = 0; i < PrayTimes.COUNT; i++) {
            final int prayer = i;
            LinearLayout row = ui.hbox();
            row.addView(ui.text(Calc.NAMES[i], 24, ui.text, true), Ui.weight(1.4f, 0));
            row.addView(ui.button("−", false, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    prefs.setOffset(prayer, prefs.offset(prayer) - 1);
                    Scheduler.schedule(MainActivity.this);
                    render();
                }
            }), Ui.weight(1, ui.dp(3)));
            int o = prefs.offset(i);
            row.addView(ui.text((o > 0 ? "+" : "") + o, 26, ui.text, true, Gravity.CENTER), Ui.weight(0.8f, 0));
            row.addView(ui.button("+", false, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    prefs.setOffset(prayer, prefs.offset(prayer) + 1);
                    Scheduler.schedule(MainActivity.this);
                    render();
                }
            }), Ui.weight(1, ui.dp(3)));
            add(v, row, 8);
        }
    }

    private static String reminderLabel(int m) {
        return m == 0 ? "O'chiq" : m + " daqiqa";
    }

    // ---------------------------------------------------------------- Birinchi ishga tushirish

    private LinearLayout onboarding() {
        LinearLayout v = ui.vbox();
        v.setBackgroundColor(ui.bg);
        v.addView(ui.text("Assalomu alaykum!", 34, ui.text, true, Gravity.CENTER));
        v.addView(ui.text("Ilovani 3 qadamda sozlaymiz", 22, ui.sub, false, Gravity.CENTER));

        add(v, ui.text("1. Shrift o'lchami", 28, ui.accentText, true), 20);
        add(v, ui.text("O'qishga qulay bo'lguncha kattalashtiring.", 20, ui.sub, false), 2);
        fontSection(v);

        add(v, ui.text("2. Shahringiz", 28, ui.accentText, true), 22);
        citySection(v);

        add(v, ui.text("3. Azon uchun ruxsat", 28, ui.accentText, true), 22);
        add(v, ui.text("Namoz vaqtida azon chalinishi uchun bildirishnomaga ruxsat bering.", 20, ui.sub, false), 2);
        add(v, ui.button("Ruxsat berish", false, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                askNotifications();
            }
        }), 8);

        add(v, ui.button("Boshlash", true, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.setOnboarded();
                Scheduler.schedule(MainActivity.this);
                tab = TAB_HOME;
                render();
            }
        }), 28);
        return v;
    }

    // ---------------------------------------------------------------- Yordamchilar

    interface Pick {
        void picked(int index);
    }

    /** Katta yozuvli tanlov oynasi. */
    private void choose(String title, String[] items, final Pick pick) {
        LinearLayout box = ui.vbox();
        box.setBackgroundColor(ui.bg);
        int pad = ui.dp(16);
        box.setPadding(pad, pad, pad, pad);
        box.addView(ui.text(title, 28, ui.text, true));
        ScrollView sv = new ScrollView(this);
        LinearLayout list = ui.vbox();
        sv.addView(list);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        int theme = ui.dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
        final AlertDialog d = new AlertDialog.Builder(this, theme).setView(box).create();
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            Button b = ui.button(items[i], false, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    d.dismiss();
                    pick.picked(idx);
                }
            });
            b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            list.addView(b, Ui.match(ui.dp(8)));
        }
        Button cancel = ui.button("Bekor qilish", true, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                d.dismiss();
            }
        });
        box.addView(cancel, Ui.match(ui.dp(12)));
        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void pickCity() {
        String[] regions = new String[Cities.REGIONS.length];
        for (int i = 0; i < regions.length; i++) regions[i] = Cities.REGIONS[i].name;
        choose("Viloyatni tanlang", regions, new Pick() {
            @Override
            public void picked(final int r) {
                Cities.City[] cs = Cities.REGIONS[r].cities;
                if (cs.length == 1) {
                    setCity(r, 0);
                    return;
                }
                String[] names = new String[cs.length];
                for (int i = 0; i < cs.length; i++) names[i] = cs[i].name;
                choose("Shaharni tanlang", names, new Pick() {
                    @Override
                    public void picked(int c) {
                        setCity(r, c);
                    }
                });
            }
        });
    }

    private void setCity(int r, int c) {
        prefs.setCity(r, c);
        Scheduler.schedule(this);
        render();
    }

    private void detectLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        final LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return;
        Location best = null;
        for (String p : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (SecurityException ignored) {
            }
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < 6 * 3600 * 1000L) {
            applyLocation(best);
            return;
        }
        String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER
                : lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : null;
        if (provider == null) {
            if (best != null) applyLocation(best);
            else toast("Joylashuv o'chiq. Shaharni ro'yxatdan tanlang.");
            return;
        }
        toast("Joylashuv aniqlanmoqda...");
        try {
            lm.requestSingleUpdate(provider, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    applyLocation(location);
                }

                @Override
                public void onStatusChanged(String p, int s, Bundle e) {}

                @Override
                public void onProviderEnabled(String p) {}

                @Override
                public void onProviderDisabled(String p) {}
            }, Looper.getMainLooper());
        } catch (SecurityException ignored) {
        }
    }

    private void applyLocation(Location l) {
        int[] n = Cities.nearest(l.getLatitude(), l.getLongitude());
        toast("Topildi: " + Cities.REGIONS[n[0]].cities[n[1]].name);
        setCity(n[0], n[1]);
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            try {
                startActivity(i);
            } catch (Exception e) {
                toast("Bildirishnomalar yoqilgan");
            }
        }
    }

    private void askBattery() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            toast("Cheklov allaqachon o'chirilgan");
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (code == REQ_LOCATION && ok) detectLocation();
        else if (code == REQ_LOCATION) toast("Ruxsat berilmadi. Shaharni ro'yxatdan tanlang.");
        else if (code == REQ_RECORD && ok && pendingRecordDialog >= 0) {
            int p = pendingRecordDialog;
            pendingRecordDialog = -1;
            recordDialog(p);
        } else if (code == REQ_RECORD && ok && pendingRecordPrayer >= 0) toggleRecord(pendingRecordPrayer);
        else if (code == REQ_NOTIF && ok) toast("Rahmat! Azon yoqildi.");
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (result != RESULT_OK || data == null) return;
        if (code >= REQ_FILE && code < REQ_FILE + Calc.ALERTS) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            prefs.setSound(code - REQ_FILE, "uri:" + uri, displayName(uri));
            render();
        } else if (code >= REQ_RINGTONE && code < REQ_RINGTONE + Calc.ALERTS) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri == null) return;
            String name = "Telefon ovozi";
            Ringtone r = RingtoneManager.getRingtone(this, uri);
            if (r != null) name = r.getTitle(this);
            prefs.setSound(code - REQ_RINGTONE, "uri:" + uri, name);
            render();
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        return "Tanlangan fayl";
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) && AzanService.running) {
            AzanService.stop(this);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (tab == TAB_SETTINGS) render();
                }
            }, 300);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (prefs.onboarded() && tab != TAB_HOME) {
            tab = TAB_HOME;
            render();
            return;
        }
        super.onBackPressed();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
