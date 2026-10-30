package uz.namozvaqti.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Budilnik ishlaganda: ovoz yoki bildirishnoma, keyin keyingi hodisani rejalashtirish. */
public class AlarmReceiver extends BroadcastReceiver {

    /** Kechikib kelgan budilnik (masalan, telefon o'chiq bo'lgan) 10 daqiqadan eski bo'lsa, ovoz chalinmaydi. */
    private static final long MAX_LATE = 10 * 60 * 1000L;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        long time = intent.getLongExtra(Scheduler.EXTRA_TIME, 0);
        if (time > 0 && System.currentTimeMillis() - time <= MAX_LATE) {
            boolean soundStarted = false;
            for (Scheduler.Event e : Scheduler.eventsAt(ctx, time)) {
                soundStarted |= handle(ctx, e, soundStarted);
            }
        }
        Scheduler.schedule(ctx);
    }

    /** @return ovoz boshlangan bo'lsa true. Bir vaqtda faqat bitta ovoz chalinadi, qolganlari bildirishnoma. */
    static boolean handle(Context ctx, Scheduler.Event e, boolean soundAlreadyStarted) {
        Prefs p = new Prefs(ctx);
        Calendar today = Calc.now();
        int[] t = Calc.times(ctx, today);
        int i = e.alert;
        if (e.type == Scheduler.TYPE_REMINDER) {
            int min = p.reminder();
            String title = i == PrayTimes.SUNRISE
                    ? "Quyosh chiqishiga " + min + " daqiqa qoldi"
                    : Calc.name(i, today) + " namoziga " + min + " daqiqa qoldi";
            String text = i == PrayTimes.SUNRISE
                    ? "Bomdod vaqti " + Calc.hhmm(t[i]) + " da tugaydi"
                    : Calc.name(i, today) + " vaqti: " + Calc.hhmm(t[i]);
            Notifier.show(ctx, 30 + i, title, text);
            return false;
        }
        if (p.mode(i) == Prefs.MODE_SOUND && !soundAlreadyStarted) {
            Intent s = new Intent(ctx, AzanService.class).putExtra(AzanService.EXTRA_PRAYER, i);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(s);
            else ctx.startService(s);
            return true;
        }
        String text;
        if (i == Calc.SAHAR) text = "Og'iz yopish: " + Calc.hhmm(t[PrayTimes.FAJR]);
        else if (i == Calc.IFTOR) text = "Og'iz ochish: " + Calc.hhmm(t[PrayTimes.MAGHRIB]);
        else text = "Soat " + Calc.hhmm(t[i]);
        Notifier.show(ctx, 20 + i, Calc.alertTitle(i, today), text);
        return false;
    }
}
