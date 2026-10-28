package uz.namozvaqti.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Qibla kompasi: aylana, "Sh" (shimol) belgisi va Ka'ba tomon katta strelka. */
public class CompassView extends View {

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Ui ui;
    /** Telefonning yo'nalishi (shimoldan, graduslarda). */
    private float heading;
    private final float qibla;
    private boolean aligned;

    public CompassView(Context c, Ui ui, float qibla) {
        super(c);
        this.ui = ui;
        this.qibla = qibla;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(ui.dp(6));
        label.setTextAlign(Paint.Align.CENTER);
        label.setFakeBoldText(true);
        label.setTextSize(ui.dp(28));
    }

    public void setHeading(float h) {
        heading = h;
        invalidate();
    }

    public void setAligned(boolean a) {
        aligned = a;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int size = MeasureSpec.getSize(w);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - ui.dp(16);
        int green = 0xFF1B9E4B;
        ring.setColor(aligned ? green : ui.text);
        c.drawCircle(cx, cy, r, ring);

        // Shimol belgisi telefon burilishiga qarab aylanadi.
        c.save();
        c.rotate(-heading, cx, cy);
        label.setColor(0xFFC62828);
        c.drawText("Sh", cx, cy - r + ui.dp(40), label);
        c.restore();

        // Qibla strelkasi.
        c.save();
        c.rotate(qibla - heading, cx, cy);
        arrow.setColor(aligned ? green : ui.accentText);
        Path p = new Path();
        p.moveTo(cx, cy - r + ui.dp(20));
        p.lineTo(cx + ui.dp(34), cy - r + ui.dp(90));
        p.lineTo(cx + ui.dp(12), cy - r + ui.dp(90));
        p.lineTo(cx + ui.dp(12), cy + r * 0.45f);
        p.lineTo(cx - ui.dp(12), cy + r * 0.45f);
        p.lineTo(cx - ui.dp(12), cy - r + ui.dp(90));
        p.lineTo(cx - ui.dp(34), cy - r + ui.dp(90));
        p.close();
        c.drawPath(p, arrow);
        c.restore();
    }
}
