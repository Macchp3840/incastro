package it.incastro;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.HashMap;

/** Colori, pennelli, bitmap dei blocchi e piccole curve di animazione condivisi dalle schermate. */
final class Ui {
    static final int[] PALETTE = {0, 0xFFE84855, 0xFFF9A03F, 0xFFF6D743, 0xFF5BC85B, 0xFF3FC1D9, 0xFF4A7BE8, 0xFF9B5DE5};
    static final int RED = 1, ORANGE = 2, YELLOW = 3, GREEN = 4, TEAL = 5, BLUE = 6, PURPLE = 7;
    static final int CARD = 0xFF232B5C, SHADE = 0xFF0B0E22, GOLD = 0xFFF6D743, CYAN = 0xFF7FE3FF, DIM = 0xFFB8C0E8,
        WHITE = 0xFFFFFFFF;

    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Typeface heavy = Typeface.create("sans-serif-black", Typeface.NORMAL);
    final Typeface bold = Typeface.DEFAULT_BOLD;
    final RectF tmp = new RectF();
    final Path path = new Path();

    int w, h, top, bottom;
    Theme theme = Theme.ALL[0];
    private Shader bg;
    private final HashMap<Integer, Bitmap[]> blocks = new HashMap<>();

    void layout(int w, int h, int top, int bottom) {
        this.w = w;
        this.h = h;
        this.top = top;
        this.bottom = bottom;
        setTheme(theme);
    }

    void setTheme(Theme t) {
        theme = t;
        bg = new LinearGradient(0, 0, 0, Math.max(1, h), t.bgTop, t.bgBottom, Shader.TileMode.CLAMP);
        blocks.clear();
    }

    float availH() {
        return h - top - bottom;
    }

    void background(Canvas c) {
        fill.setShader(bg);
        c.drawRect(0, 0, w, h, fill);
        fill.setShader(null);
    }

    /** Bitmap dei blocchi del tema per ogni colore a una data dimensione, create una volta sola. */
    Bitmap[] blocks(float size) {
        int s = Math.max(2, (int) Math.ceil(size));
        Bitmap[] b = blocks.get(s);
        if (b == null) {
            b = new Bitmap[theme.pal.length];
            for (int i = 1; i < theme.pal.length; i++) b[i] = theme.blockBitmap(s, theme.pal[i]);
            blocks.put(s, b);
        }
        return b;
    }

    void label(Canvas c, String s, float x, float y, float size, int color, Typeface tf, Paint.Align align) {
        text.setTypeface(tf);
        text.setTextSize(size);
        text.setColor(color);
        text.setTextAlign(align);
        c.drawText(s, x, y, text);
    }

    /** Pannello scuro semitrasparente più scheda centrale, con comparsa animata (t da 0 a 1). */
    void dialog(Canvas c, float t, RectF card) {
        float e = ease(t);
        fill.setColor(0xC0080A1A);
        fill.setAlpha((int) (0xC0 * e));
        c.drawRect(0, 0, w, h, fill);
        float s = 0.85f + 0.15f * back(t);
        c.save();
        c.scale(s, s, card.centerX(), card.centerY());
        fill.setColor(CARD);
        c.drawRoundRect(card, card.width() * 0.06f, card.width() * 0.06f, fill);
        c.restore();
    }

    /** Corona stilizzata, usata accanto al record. */
    void crown(Canvas c, float cx, float cy, float size, int color) {
        float hw = size / 2f, hh = size * 0.38f;
        path.reset();
        path.moveTo(cx - hw, cy + hh);
        path.lineTo(cx - hw, cy - hh * 0.6f);
        path.lineTo(cx - hw * 0.45f, cy);
        path.lineTo(cx, cy - hh);
        path.lineTo(cx + hw * 0.45f, cy);
        path.lineTo(cx + hw, cy - hh * 0.6f);
        path.lineTo(cx + hw, cy + hh);
        path.close();
        fill.setColor(color);
        c.drawPath(path, fill);
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    static float clamp01(float t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    /** Uscita morbida (ease-out cubica). */
    static float ease(float t) {
        t = 1 - clamp01(t);
        return 1 - t * t * t;
    }

    /** Uscita con leggero rimbalzo oltre il valore finale. */
    static float back(float t) {
        t = clamp01(t) - 1;
        return 1 + 2.70158f * t * t * t + 1.70158f * t * t;
    }
}
