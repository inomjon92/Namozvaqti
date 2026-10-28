package uz.namozvaqti.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Bildirishnoma kanallari va oddiy eslatmalar. */
public final class Notifier {

    public static final String CH_REMINDER = "reminders";
    public static final String CH_AZAN = "azan";

    private Notifier() {}

    public static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        NotificationChannel r = new NotificationChannel(CH_REMINDER, "Eslatmalar", NotificationManager.IMPORTANCE_HIGH);
        r.setDescription("Namozdan oldingi eslatmalar va saharlik/iftor");
        r.enableVibration(true);
        nm.createNotificationChannel(r);
        NotificationChannel a = new NotificationChannel(CH_AZAN, "Azon", NotificationManager.IMPORTANCE_HIGH);
        a.setDescription("Namoz vaqti kirganda azon yoki melodiya");
        a.setSound(null, null);
        a.enableVibration(true);
        nm.createNotificationChannel(a);
    }

    public static void show(Context ctx, int id, String title, String text) {
        createChannels(ctx);
        PendingIntent open = PendingIntent.getActivity(ctx, 3, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(ctx, CH_REMINDER)
                : new Notification.Builder(ctx);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER);
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        }
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            nm.notify(id, b.build());
        } catch (SecurityException ignored) {
            // Bildirishnoma ruxsati berilmagan.
        }
    }
}
