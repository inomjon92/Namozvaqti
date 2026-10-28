package uz.namozvaqti.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Budilnik ishlaganda: azon yoki eslatma, keyin keyingi hodisani rejalashtirish. */
public class AlarmReceiver extends BroadcastReceiver {

    /** Kechikib kelgan budilnik (masalan, telefon o'chiq bo'lgan) 10 daqiqadan eski bo'lsa, ovoz chalinmaydi. */
    private static final long MAX_LATE = 10 * 60 * 1000L;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        int type = intent.getIntExtra(Scheduler.EXTRA_TYPE, -1);
        int prayer = intent.getIntExtra(Scheduler.EXTRA_PRAYER, 0);
        long time = intent.getLongExtra(Scheduler.EXTRA_TIME, 0);
        boolean late = System.currentTimeMillis() - time > MAX_LATE;
        if (type >= 0 && !late) handle(ctx, type, prayer);
        Scheduler.schedule(ctx);
    }

    static void handle(Context ctx, int type, int prayer) {
        Prefs p = new Prefs(ctx);
        Calendar today = Calc.now();
        int[] t = Calc.times(ctx, today);
        boolean ramadan = Calc.currentOrNextRamadan(ctx).indexOf(today) >= 0;
        String name = Calc.name(prayer, today);
        switch (type) {
            case Scheduler.TYPE_PRAYER: {
                boolean iftor = ramadan && prayer == PrayTimes.MAGHRIB && p.iftorOn();
                if (p.mode(prayer) == Prefs.MODE_SOUND) {
                    Intent s = new Intent(ctx, AzanService.class)
                            .putExtra(AzanService.EXTRA_PRAYER, prayer)
                            .putExtra(AzanService.EXTRA_IFTOR, iftor);
                    if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(s);
                    else ctx.startService(s);
                } else {
                    String title = prayer == PrayTimes.SUNRISE ? "Quyosh chiqdi" : name + " namozi vaqti kirdi";
                    if (iftor) title = "Iftor vaqti. " + title;
                    Notifier.show(ctx, 20 + prayer, title, "Soat " + Calc.hhmm(t[prayer]));
                }
                break;
            }
            case Scheduler.TYPE_REMINDER: {
                int min = p.reminder();
                String title = prayer == PrayTimes.SUNRISE
                        ? "Quyosh chiqishiga " + min + " daqiqa qoldi"
                        : name + " namoziga " + min + " daqiqa qoldi";
                String text = prayer == PrayTimes.SUNRISE
                        ? "Bomdod vaqti " + Calc.hhmm(t[prayer]) + " da tugaydi"
                        : name + " vaqti: " + Calc.hhmm(t[prayer]);
                Notifier.show(ctx, 30 + prayer, title, text);
                break;
            }
            case Scheduler.TYPE_SAHAR:
                Notifier.show(ctx, 40, "Saharlik tugashiga " + Scheduler.SAHAR_BEFORE + " daqiqa qoldi",
                        "Og'iz yopish: " + Calc.hhmm(t[PrayTimes.FAJR]));
                break;
            case Scheduler.TYPE_IFTOR:
                Notifier.show(ctx, 41, "Iftor vaqti bo'ldi", "Og'iz ochish: " + Calc.hhmm(t[PrayTimes.MAGHRIB]));
                break;
            default:
                break;
        }
    }
}
