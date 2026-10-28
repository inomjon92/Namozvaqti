package uz.namozvaqti.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Keyingi hodisani (azon, eslatma, saharlik) topib, bitta aniq budilnik qo'yadi. */
public final class Scheduler {

    public static final int TYPE_PRAYER = 0, TYPE_REMINDER = 1, TYPE_SAHAR = 2, TYPE_IFTOR = 3;
    public static final String EXTRA_TYPE = "type", EXTRA_PRAYER = "prayer", EXTRA_TIME = "time";
    /** Saharlik eslatmasi Bomdoddan necha daqiqa oldin. */
    public static final int SAHAR_BEFORE = 30;

    public static final class Event {
        public final long time;
        public final int type, prayer;

        Event(long time, int type, int prayer) {
            this.time = time;
            this.type = type;
            this.prayer = prayer;
        }
    }

    private Scheduler() {}

    public static Event next(Context ctx, long nowMillis) {
        Prefs p = new Prefs(ctx);
        Calc.Ramadan ram = Calc.currentOrNextRamadan(ctx);
        Event best = null;
        Calendar day = Calc.dayStart(Calc.now());
        for (int d = 0; d < 3; d++) {
            int[] t = Calc.times(ctx, day);
            boolean inRamadan = ram.indexOf(day) >= 0;
            for (int i = 0; i < PrayTimes.COUNT; i++) {
                int mode = p.mode(i);
                if (mode != Prefs.MODE_OFF) {
                    best = earlier(best, new Event(Calc.at(day, t[i]), TYPE_PRAYER, i), nowMillis);
                    if (p.reminder() > 0) {
                        best = earlier(best, new Event(Calc.at(day, t[i] - p.reminder()), TYPE_REMINDER, i), nowMillis);
                    }
                }
            }
            if (inRamadan && p.saharOn()) {
                best = earlier(best, new Event(Calc.at(day, t[PrayTimes.FAJR] - SAHAR_BEFORE), TYPE_SAHAR, PrayTimes.FAJR), nowMillis);
            }
            if (inRamadan && p.iftorOn() && p.mode(PrayTimes.MAGHRIB) == Prefs.MODE_OFF) {
                best = earlier(best, new Event(Calc.at(day, t[PrayTimes.MAGHRIB]), TYPE_IFTOR, PrayTimes.MAGHRIB), nowMillis);
            }
            if (best != null) break;
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        return best;
    }

    private static Event earlier(Event a, Event b, long now) {
        if (b.time <= now) return a;
        if (a == null || b.time < a.time) return b;
        return a;
    }

    public static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctx, AlarmReceiver.class);
        Event e = next(ctx, System.currentTimeMillis() + 1000);
        if (e == null) {
            am.cancel(PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            return;
        }
        i.putExtra(EXTRA_TYPE, e.type).putExtra(EXTRA_PRAYER, e.prayer).putExtra(EXTRA_TIME, e.time);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (canExact(ctx)) {
            PendingIntent show = PendingIntent.getActivity(ctx, 2, new Intent(ctx, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(e.time, show), pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.time, pi);
        }
    }

    public static boolean canExact(Context ctx) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return am.canScheduleExactAlarms();
    }
}
