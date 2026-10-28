package uz.namozvaqti.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Telefon qayta yoqilganda, vaqt yoki mintaqa o'zgarganda budilnikni qayta qo'yadi. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Scheduler.schedule(ctx);
    }
}
