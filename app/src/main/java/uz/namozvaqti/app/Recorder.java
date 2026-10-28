package uz.namozvaqti.app;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;

import java.io.File;

/** Har bir namoz uchun o'z ovozingizda e'lon yozib olish ("Peshin namozi vaqti kirdi"). */
public final class Recorder {

    private static Context appCtx;
    private MediaRecorder rec;
    private int recordingPrayer = -1;

    public static File file(Prefs p, int prayer) {
        if (appCtx == null) return null;
        return new File(appCtx.getFilesDir(), "say_" + prayer + ".m4a");
    }

    public static void init(Context ctx) {
        appCtx = ctx.getApplicationContext();
    }

    public boolean isRecording() { return rec != null; }
    public int recordingPrayer() { return recordingPrayer; }

    public boolean start(Context ctx, int prayer) {
        stop();
        init(ctx);
        File f = new File(ctx.getFilesDir(), "say_" + prayer + ".m4a");
        try {
            MediaRecorder r = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(ctx) : new MediaRecorder();
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioSamplingRate(44100);
            r.setAudioEncodingBitRate(96000);
            r.setMaxDuration(10000);
            r.setOutputFile(f.getAbsolutePath());
            r.prepare();
            r.start();
            rec = r;
            recordingPrayer = prayer;
            return true;
        } catch (Exception e) {
            rec = null;
            recordingPrayer = -1;
            return false;
        }
    }

    public void stop() {
        if (rec != null) {
            try {
                rec.stop();
            } catch (Exception ignored) {
                // Juda qisqa yozuv: fayl yaroqsiz bo'lishi mumkin.
            }
            rec.release();
            rec = null;
        }
        recordingPrayer = -1;
    }
}
