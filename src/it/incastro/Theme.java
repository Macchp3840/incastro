package it.incastro;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

/** Temi grafici: colori, blocchi, caselle vuote e fondo della griglia (vedi temi-anteprima.html). */
abstract class Theme {
    private final String it, en;
    final int bgTop, bgBottom, boardBg;
    /** Indici 1..7 come Ui.PALETTE; 0 non usato. */
    final int[] pal;

    Theme(String it, String en, int bgTop, int bgBottom, int boardBg, int[] pal) {
        this.it = it;
        this.en = en;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.boardBg = boardBg;
        this.pal = pal;
    }

    String name() {
        return L.t(it, en);
    }

    abstract void block(Canvas c, int s, int color);

    abstract void empty(Canvas c, float x, float y, float cell, Paint p);

    void boardDeco(Canvas c, float size, Paint p) {}

    Bitmap blockBitmap(int s, int color) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        block(new Canvas(b), s, color);
        return b;
    }

    static final Theme[] ALL = {new Classic(), new Neon(), new Wood()};

    static int mix(int a, int b, float t) {
        return Ui.mix(a, b, t);
    }

    /** Blocchi vivi con bisello chiaro in alto a sinistra, scuro in basso a destra, e riflesso. */
    static final class Classic extends Theme {
        Classic() {
            super("Classico", "Classic", 0xFF24307A, 0xFF0E1440, 0xFF0C1236,
                new int[]{0, 0xFFFF4D5E, 0xFFFF9F2E, 0xFFFFD93B, 0xFF4FD16A, 0xFF2FC6E0, 0xFF4B7CF0, 0xFFA25CF0});
        }

        @Override
        void block(Canvas c, int s, int col) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Path path = new Path();
            float o = s * 0.04f, w = s - 2 * o, b = s * 0.14f;
            p.setColor(mix(col, 0xFF000000, 0.38f));
            c.drawRoundRect(o, o, o + w, o + w, s * 0.1f, s * 0.1f, p);
            p.setColor(mix(col, 0xFFFFFFFF, 0.45f));
            path.moveTo(o, o); path.lineTo(o + w, o); path.lineTo(o + w - b, o + b); path.lineTo(o + b, o + b);
            path.lineTo(o + b, o + w - b); path.lineTo(o, o + w); path.close();
            c.drawPath(path, p);
            path.reset();
            p.setColor(mix(col, 0xFF000000, 0.18f));
            path.moveTo(o + w, o); path.lineTo(o + w, o + w); path.lineTo(o, o + w); path.lineTo(o + b, o + w - b);
            path.lineTo(o + w - b, o + w - b); path.lineTo(o + w - b, o + b); path.close();
            c.drawPath(path, p);
            p.setShader(new LinearGradient(0, o + b, 0, o + w - b, mix(col, 0xFFFFFFFF, 0.12f), col, Shader.TileMode.CLAMP));
            c.drawRect(o + b, o + b, o + w - b, o + w - b, p);
            p.setShader(null);
            p.setColor(0x59FFFFFF);
            c.drawRoundRect(o + b * 1.3f, o + b * 1.25f, o + b * 1.3f + (w - 2 * b) * 0.55f, o + b * 1.25f + s * 0.07f,
                s * 0.035f, s * 0.035f, p);
        }

        @Override
        void empty(Canvas c, float x, float y, float cell, Paint p) {
            p.setColor(0xFF18225A);
            c.drawRoundRect(x + cell * 0.05f, y + cell * 0.05f, x + cell * 0.95f, y + cell * 0.95f, cell * 0.12f, cell * 0.12f, p);
        }
    }

    /** Fondo nero, blocchi scuri con bordo luminoso e bagliore. */
    static final class Neon extends Theme {
        Neon() {
            super("Neon", "Neon", 0xFF0B0B18, 0xFF040409, 0xFF07070F,
                new int[]{0, 0xFFFF3366, 0xFFFF8A00, 0xFFFFE600, 0xFF7DFF3A, 0xFF00F0FF, 0xFF3D7BFF, 0xFFC04DFF});
        }

        @Override
        void block(Canvas c, int s, int col) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            float o = s * 0.1f, w = s - 2 * o, r = s * 0.16f;
            Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
            glow.setColor(col);
            glow.setStyle(Paint.Style.STROKE);
            glow.setStrokeWidth(s * 0.14f);
            glow.setMaskFilter(new BlurMaskFilter(s * 0.07f, BlurMaskFilter.Blur.NORMAL));
            c.drawRoundRect(o, o, o + w, o + w, r, r, glow);
            p.setColor(mix(col, 0xFF000000, 0.78f));
            c.drawRoundRect(o, o, o + w, o + w, r, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.09f);
            p.setColor(col);
            c.drawRoundRect(o, o, o + w, o + w, r, r, p);
            p.setStyle(Paint.Style.FILL);
            float i = s * 0.14f;
            p.setColor(mix(col, 0xFF000000, 0.55f));
            c.drawRoundRect(o + i, o + i, o + w - i, o + w - i, s * 0.08f, s * 0.08f, p);
            p.setColor(0x8CFFFFFF);
            c.drawRect(o + s * 0.12f, o + s * 0.07f, o + s * 0.12f + w * 0.35f, o + s * 0.105f, p);
        }

        @Override
        void empty(Canvas c, float x, float y, float cell, Paint p) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, cell * 0.025f));
            p.setColor(0xFF1D1D33);
            c.drawRoundRect(x + cell * 0.08f, y + cell * 0.08f, x + cell * 0.92f, y + cell * 0.92f, cell * 0.14f, cell * 0.14f, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    /** Gemme sfaccettate su legno scuro venato. */
    static final class Wood extends Theme {
        Wood() {
            super("Legno e pietra", "Wood & stone", 0xFF4A2E1B, 0xFF1E120A, 0xFF2B1A0F,
                new int[]{0, 0xFFD7263D, 0xFFF08A24, 0xFFF4C430, 0xFF2AA16A, 0xFF2BB3C0, 0xFF2F5FD0, 0xFF8E44C9});
        }

        @Override
        void block(Canvas c, int s, int col) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            float o = s * 0.05f, w = s - 2 * o, f = s * 0.2f, X = o, Y = o;
            quad(c, p, mix(col, 0xFFFFFFFF, 0.42f), X, Y, X + w, Y, X + w - f, Y + f, X + f, Y + f);
            quad(c, p, mix(col, 0xFF000000, 0.12f), X + w, Y, X + w, Y + w, X + w - f, Y + w - f, X + w - f, Y + f);
            quad(c, p, mix(col, 0xFF000000, 0.42f), X, Y + w, X + w, Y + w, X + w - f, Y + w - f, X + f, Y + w - f);
            quad(c, p, mix(col, 0xFFFFFFFF, 0.2f), X, Y, X + f, Y + f, X + f, Y + w - f, X, Y + w);
            p.setShader(new LinearGradient(X + f, Y + f, X + w - f, Y + w - f,
                mix(col, 0xFFFFFFFF, 0.3f), mix(col, 0xFF000000, 0.1f), Shader.TileMode.CLAMP));
            c.drawRect(X + f, Y + f, X + w - f, Y + w - f, p);
            p.setShader(null);
            Path t = new Path();
            t.moveTo(X + f * 1.2f, Y + f * 1.2f);
            t.lineTo(X + f * 2.3f, Y + f * 1.2f);
            t.lineTo(X + f * 1.2f, Y + f * 2.3f);
            t.close();
            p.setColor(0xB3FFFFFF);
            c.drawPath(t, p);
        }

        private static void quad(Canvas c, Paint p, int color, float... v) {
            Path path = new Path();
            path.moveTo(v[0], v[1]);
            for (int i = 2; i < v.length; i += 2) path.lineTo(v[i], v[i + 1]);
            path.close();
            p.setColor(color);
            c.drawPath(path, p);
        }

        @Override
        void empty(Canvas c, float x, float y, float cell, Paint p) {
            p.setColor(0xFF3A2516);
            c.drawRoundRect(x + cell * 0.06f, y + cell * 0.06f, x + cell * 0.94f, y + cell * 0.94f, cell * 0.1f, cell * 0.1f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, cell * 0.02f));
            p.setColor(0x59000000);
            c.drawRoundRect(x + cell * 0.06f, y + cell * 0.06f, x + cell * 0.94f, y + cell * 0.94f, cell * 0.1f, cell * 0.1f, p);
            p.setStyle(Paint.Style.FILL);
        }

        @Override
        void boardDeco(Canvas c, float size, Paint p) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, size / 400f));
            p.setColor(0x2E8A5A35);
            Path path = new Path();
            for (int i = 0; i < 26; i++) {
                float y = i * size / 26f;
                path.reset();
                path.moveTo(0, y);
                path.cubicTo(size * 0.3f, y + size * 0.012f, size * 0.6f, y - size * 0.012f, size, y + size * 0.006f);
                c.drawPath(path, p);
            }
            p.setStyle(Paint.Style.FILL);
        }
    }
}
