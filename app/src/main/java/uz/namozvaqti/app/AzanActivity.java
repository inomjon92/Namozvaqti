package uz.namozvaqti.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/** Qulflangan ekranda ham chiqadigan to'liq ekranli oyna: katta "TO'XTATISH" tugmasi. */
public class AzanActivity extends Activity {

    private boolean registered;
    private final BroadcastReceiver finished = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Ui ui = new Ui(this);
        int prayer = AzanService.currentPrayer;
        Calendar today = Calc.now();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(ui.cardBg);
        int pad = ui.dp(24);
        root.setPadding(pad, pad, pad, pad);

        String name = prayer < 0 ? "Namoz" : Calc.name(prayer, today);
        String sub = prayer == PrayTimes.SUNRISE ? "Quyosh chiqdi" : "namozi vaqti kirdi";
        root.addView(ui.text(prayer == PrayTimes.SUNRISE ? "Quyosh" : name, 52, Color.WHITE, true, Gravity.CENTER));
        root.addView(ui.text(sub, 30, Color.WHITE, false, Gravity.CENTER));
        if (prayer >= 0) {
            int[] t = Calc.times(this, today);
            root.addView(ui.text(Calc.hhmm(t[prayer]), 80, Color.WHITE, true, Gravity.CENTER));
        }

        Button stop = new Button(this);
        stop.setText("TO'XTATISH");
        stop.setTextSize(ui.sp(34));
        stop.setTextColor(Color.BLACK);
        stop.setAllCaps(false);
        stop.setBackground(ui.rounded(Color.WHITE, 0, 0, 28));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(180));
        lp.topMargin = ui.dp(40);
        stop.setLayoutParams(lp);
        stop.setContentDescription("Azonni to'xtatish");
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AzanService.stop(AzanActivity.this);
                finish();
            }
        });
        root.addView(stop);
        root.addView(ui.text("Ovoz tugmasini bossangiz ham to'xtaydi", 22, Color.WHITE, false, Gravity.CENTER));
        setContentView(root);

        IntentFilter f = new IntentFilter(AzanService.ACTION_FINISHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(finished, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(finished, f);
        registered = true;
        if (!AzanService.running) finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            AzanService.stop(this);
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (registered) unregisterReceiver(finished);
        super.onDestroy();
    }
}
