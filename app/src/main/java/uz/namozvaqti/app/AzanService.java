package uz.namozvaqti.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Namoz yoki ro'za vaqtida tanlangan ovozni (azon, melodiya, o'z ovozi) chaladi.
 * Ovoz tugmasi (pasaytirish yoki oshirish) bosilsa, darhol to'xtaydi.
 */
public class AzanService extends Service {

    public static final String ACTION_STOP = "uz.namozvaqti.app.STOP";
    public static final String ACTION_FINISHED = "uz.namozvaqti.app.FINISHED";
    public static final String EXTRA_PRAYER = "prayer";
    /** Sozlamalardan sinab eshitish. */
    public static final String EXTRA_PREVIEW_SOUND = "previewSound";

    private static final int NOTIF_ID = 7;

    public static volatile boolean running;
    public static volatile int currentPrayer = -1;

    private final List<String> queue = new ArrayList<>();
    private MediaPlayer player;
    private MediaSession session;
    private PowerManager.WakeLock wakeLock;
    private boolean receiverRegistered;

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            // Ovoz darajasi o'zgardi (foydalanuvchi ovoz tugmasini bosdi).
            stopAll();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            if (!running) startAsForeground(-1, false);
            stopAll();
            return START_NOT_STICKY;
        }
        Recorder.init(this);
        releasePlayer();
        queue.clear();
        int prayer = intent.getIntExtra(EXTRA_PRAYER, PrayTimes.DHUHR);
        String preview = intent.getStringExtra(EXTRA_PREVIEW_SOUND);
        Prefs p = new Prefs(this);
        queue.add(preview != null ? preview : p.sound(prayer));

        currentPrayer = preview != null ? -1 : prayer;
        startAsForeground(prayer, preview == null);
        running = true;
        acquireWakeLock();
        startVolumeWatch();
        playNext();
        return START_NOT_STICKY;
    }

    private void startAsForeground(int prayer, boolean fullScreen) {
        Notifier.createChannels(this);
        PendingIntent stop = PendingIntent.getService(this, 4,
                new Intent(this, AzanService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent ui = new Intent(this, AzanActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(this, 5, ui,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title;
        if (prayer < 0) title = "Namoz vaqtlari";
        else if (!fullScreen) title = "Sinab eshitish";
        else title = Calc.alertTitle(prayer, Calc.now());
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, Notifier.CH_AZAN)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText("To'xtatish uchun ovoz tugmasini bosing")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(open)
                .setDeleteIntent(stop)
                .addAction(new Notification.Action.Builder(null, "TO'XTATISH", stop).build());
        if (fullScreen) b.setFullScreenIntent(open, true);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_MAX);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void playNext() {
        releasePlayer();
        while (!queue.isEmpty()) {
            String src = queue.remove(0);
            MediaPlayer mp = create(src);
            if (mp == null) mp = create("raw:" + Prefs.RAW_SOUNDS[0]);
            if (mp == null) continue;
            player = mp;
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) {
                    playNext();
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer m, int what, int extra) {
                    playNext();
                    return true;
                }
            });
            mp.start();
            return;
        }
        stopAll();
    }

    private MediaPlayer create(String src) {
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            if (src.startsWith("raw:")) {
                int id = getResources().getIdentifier(src.substring(4), "raw", getPackageName());
                if (id == 0) throw new IllegalArgumentException(src);
                AssetFileDescriptor afd = getResources().openRawResourceFd(id);
                mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            } else if (src.startsWith("rec:")) {
                File f = Recorder.file(null, Integer.parseInt(src.substring(4)));
                if (f == null || !f.exists() || f.length() == 0) throw new IllegalArgumentException(src);
                mp.setDataSource(f.getAbsolutePath());
            } else if (src.startsWith("file:")) {
                mp.setDataSource(src.substring(5));
            } else if (src.startsWith("uri:")) {
                mp.setDataSource(this, Uri.parse(src.substring(4)));
            } else {
                throw new IllegalArgumentException(src);
            }
            mp.prepare();
            return mp;
        } catch (Exception e) {
            mp.release();
            return null;
        }
    }

    /** Ovoz tugmalarini ushlash: masofaviy ovoz boshqaruvi + ovoz darajasi o'zgarishi. */
    private void startVolumeWatch() {
        if (session == null) {
            session = new MediaSession(this, "azan");
            session.setCallback(new MediaSession.Callback() {
                @Override
                public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                    stopAll();
                    return true;
                }

                @Override
                public void onPause() { stopAll(); }

                @Override
                public void onStop() { stopAll(); }
            });
            session.setPlaybackToRemote(new VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
                @Override
                public void onAdjustVolume(int direction) {
                    stopAll();
                }
            });
        }
        session.setPlaybackState(new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                .setActions(PlaybackState.ACTION_STOP | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE)
                .build());
        session.setActive(true);
        if (!receiverRegistered) {
            IntentFilter f = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(volumeReceiver, f, Context.RECEIVER_EXPORTED);
            else registerReceiver(volumeReceiver, f);
            receiverRegistered = true;
        }
    }

    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "namozvaqti:azan");
            wakeLock.setReferenceCounted(false);
        }
        wakeLock.acquire(10 * 60 * 1000L);
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
    }

    void stopAll() {
        queue.clear();
        releasePlayer();
        if (session != null) {
            session.setActive(false);
            session.release();
            session = null;
        }
        if (receiverRegistered) {
            unregisterReceiver(volumeReceiver);
            receiverRegistered = false;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        running = false;
        currentPrayer = -1;
        sendBroadcast(new Intent(ACTION_FINISHED).setPackage(getPackageName()));
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (running) stopAll();
        super.onDestroy();
    }

    /** Istalgan joydan to'xtatish. */
    public static void stop(Context ctx) {
        if (!running) return;
        ctx.startService(new Intent(ctx, AzanService.class).setAction(ACTION_STOP));
    }
}
