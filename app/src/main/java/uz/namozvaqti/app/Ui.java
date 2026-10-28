package uz.namozvaqti.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Katta shrift va yuqori kontrastli UI elementlari. Ranglar WCAG AAA (7:1) ga mos. */
public final class Ui {

    public final int bg, surface, text, sub, accentText, cardBg, cardText, btnBg, btnText, border;
    public final boolean dark;
    private final Context ctx;
    private final float scale;

    public Ui(Context ctx) {
        this.ctx = ctx;
        Prefs p = new Prefs(ctx);
        dark = p.dark();
        scale = p.fontScale() / 100f;
        if (dark) {
            bg = Color.BLACK;
            surface = 0xFF1C1C1C;
            text = Color.WHITE;
            sub = 0xFFDDDDDD;
            accentText = 0xFF3DDC97;
            cardBg = 0xFF0B5C40;
            cardText = Color.WHITE;
            btnBg = 0xFF3DDC97;
            btnText = Color.BLACK;
            border = 0xFF3DDC97;
        } else {
            bg = Color.WHITE;
            surface = 0xFFEFF5F2;
            text = 0xFF111111;
            sub = 0xFF333333;
            accentText = 0xFF075E45;
            cardBg = 0xFF075E45;
            cardText = Color.WHITE;
            btnBg = 0xFF075E45;
            btnText = Color.WHITE;
            border = 0xFF075E45;
        }
    }

    public int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.getResources().getDisplayMetrics());
    }

    /** Asosiy o'lcham ("Katta" daraja) * foydalanuvchi masshtabi. */
    public float sp(float base) {
        return base * scale;
    }

    public TextView text(String s, float baseSp, int color, boolean bold) {
        return text(s, baseSp, color, bold, Gravity.START);
    }

    public TextView text(String s, float baseSp, int color, boolean bold, int gravity) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(baseSp));
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setGravity(gravity);
        t.setFontFeatureSettings("tnum");
        t.setLineSpacing(0, 1.1f);
        return t;
    }

    public GradientDrawable rounded(int fill, int strokeColor, int strokeDp, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    /** Katta tugma: kamida 64dp balandlik, yozuv 22sp dan. */
    public Button button(String label, boolean primary, View.OnClickListener l) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(22));
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(64));
        b.setMinimumHeight(dp(64));
        b.setPadding(dp(16), dp(10), dp(16), dp(10));
        b.setStateListAnimator(null);
        if (primary) {
            b.setTextColor(btnText);
            b.setBackground(rounded(btnBg, 0, 0, 16));
        } else {
            b.setTextColor(accentText);
            b.setBackground(rounded(surface, border, 2, 16));
        }
        b.setOnClickListener(l);
        return b;
    }

    public LinearLayout vbox() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public LinearLayout hbox() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public LinearLayout card(int fill, int stroke, int strokeDp) {
        LinearLayout l = vbox();
        l.setBackground(rounded(fill, stroke, strokeDp, 20));
        l.setPadding(dp(18), dp(14), dp(18), dp(14));
        return l;
    }

    public static LinearLayout.LayoutParams match(int topMarginPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMarginPx;
        return lp;
    }

    public static LinearLayout.LayoutParams weight(float w, int sideMarginPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
        lp.leftMargin = sideMarginPx;
        lp.rightMargin = sideMarginPx;
        return lp;
    }
}
