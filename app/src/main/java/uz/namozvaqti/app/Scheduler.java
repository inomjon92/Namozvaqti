package uz.namozvaqti.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Keyingi hodisani (namoz vaqti, eslatma, Ramazonda saharlik va iftor) topib, bitta aniq budilnik qo'yadi.
 * Bir daqiqaga to'g'ri kelgan hodisalar birga ishlanadi.
 */
public final class Scheduler {

    public static final int TYPE_ALERT = 0, TYPE_REMINDER = 1;
    public static final String EXTRA_TIME = "time";

    public static final class Event {
        public final long time;
        public final int type, alert;

        Event(long time, int type, int alert) {
            this.time = time;
            this.type = type;
            this.alert = alert;
        }
    }

    private Scheduler() {}

    /** Bugundan boshlab 3 kunlik barcha hodisalar. */
    static List<Event> events(Context ctx) {
        Prefs p = new Prefs(ctx);
        Calc.Ramadan ram = Calc.currentOrNextRamadan(ctx);
        List<Event> out = new ArrayList<>();
        Calendar day = Calc.dayStart(Calc.now());
        day.add(Calendar.DAY_OF_MONTH, -1);
        for (int d = 0; d < 4; d++) {
            int[] t = Calc.times(ctx, day);
            boolean inRamadan = ram.indexOf(day) >= 0;
            for (int i = 0; i < Calc.ALERTS; i++) {
                if (i >= PrayTimes.COUNT && !inRamadan) continue;
                if (p.mode(i) == Prefs.MODE_OFF) continue;
                out.add(new Event(Calc.at(day, Calc.alertMinutes(i, t)), TYPE_ALERT, i));
                if (i < PrayTimes.COUNT && p.reminder() > 0) {
                    out.add(new Event(Calc.at(day, t[i] - p.reminder()), TYPE_REMINDER, i));
                }
            }
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        return out;
    }

    public static long nextTime(Context ctx, long now) {
        long best = Long.MAX_VALUE;
        for (Event e : events(ctx)) {
            if (e.time > now && e.time < best) best = e.time;
        }
        return best == Long.MAX_VALUE ? -1 : best;
    }

    /** Berilgan daqiqadagi hodisalar: Ramazon ogohlantirishlari birinchi (iftor Shomdan ustun). */
    public static List<Event> eventsAt(Context ctx, long time) {
        List<Event> res = new ArrayList<>();
        for (Event e : events(ctx)) {
            if (Math.abs(e.time - time) < 30000) {
                if (e.alert >= PrayTimes.COUNT) res.add(0, e);
                else res.add(e);
            }
        }
        return res;
    }

    public static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctx, AlarmReceiver.class);
        long t = nextTime(ctx, System.currentTimeMillis() + 1000);
        if (t < 0) {
            am.cancel(PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            return;
        }
        i.putExtra(EXTRA_TIME, t);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (canExact(ctx)) {
            PendingIntent show = PendingIntent.getActivity(ctx, 2, new Intent(ctx, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, show), pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        }
    }

    public static boolean canExact(Context ctx) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return am.canScheduleExactAlarms();
    }
}
