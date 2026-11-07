package it.incastro;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;

/** Schermata iniziale: logo animato, record, Gioca e Impostazioni. */
final class HomeScreen extends Screen {
    /** Logo: un quadrato 3x3 riempito da quattro pezzi che si incastrano. {riga, colonna, colore, pezzo} */
    private static final int[][] LOGO = {
        {0, 0, Ui.RED, 0}, {0, 1, Ui.RED, 0}, {1, 0, Ui.RED, 0},
        {0, 2, Ui.YELLOW, 1}, {1, 2, Ui.YELLOW, 1}, {2, 2, Ui.YELLOW, 1},
        {2, 0, Ui.GREEN, 2}, {2, 1, Ui.GREEN, 2},
        {1, 1, Ui.BLUE, 3},
    };
    /** Da dove arriva ogni pezzo del logo, in celle. */
    private static final float[][] FROM = {{-1.5f, -1.5f}, {1.8f, 0}, {0, 1.8f}, {0, -2.5f}};
    private static final long PIECE_MS = 420, STAGGER_MS = 130;

    private final Button play = new Button("Gioca", Ui.PALETTE[Ui.GREEN]);
    private final Button options = new Button("Impostazioni", Ui.PALETTE[Ui.BLUE]);
    private long shownAt;
    private Bitmap[] logoBlocks;
    private float logoCell, logoX, logoY, titleY, subtitleY, recordY;
    private String recordStr = "";
    private long recordVal = -1;

    HomeScreen(AppView app) {
        super(app);
    }

    @Override
    void layout() {
        float avail = ui.availH(), top = ui.top;
        logoCell = Math.min(ui.w * 0.16f, avail * 0.085f);
        logoBlocks = ui.blocks(logoCell);
        logoX = (ui.w - 3 * logoCell) / 2f;
        logoY = top + avail * 0.13f;
        titleY = logoY + 3 * logoCell + avail * 0.1f;
        subtitleY = titleY + avail * 0.045f;
        recordY = subtitleY + avail * 0.07f;
        float bw = ui.w * 0.64f, bh = avail * 0.075f, bx = (ui.w - bw) / 2f, by = top + avail * 0.62f;
        play.r.set(bx, by, bx + bw, by + bh);
        options.r.set(bx, by + bh * 1.45f, bx + bw, by + bh * 2.45f);
    }

    @Override
    void onShow(long now) {
        shownAt = now;
        logoBlocks = ui.blocks(logoCell); // il tema può essere cambiato durante la partita
        recordVal = -1; // e la lingua nelle impostazioni
    }

    @Override
    boolean draw(Canvas c, long now) {
        ui.background(c);
        boolean anim = false;
        for (int[] b : LOGO) {
            float t = (now - shownAt - b[3] * STAGGER_MS) / (float) PIECE_MS;
            if (t < 1) anim = true;
            if (t <= 0) continue;
            float e = Ui.back(t), fade = Ui.clamp01(t * 2.5f);
            float x = logoX + (b[1] + FROM[b[3]][0] * (1 - e)) * logoCell;
            float y = logoY + (b[0] + FROM[b[3]][1] * (1 - e)) * logoCell;
            ui.bmp.setAlpha((int) (255 * fade));
            c.drawBitmap(logoBlocks[b[2]], x, y, ui.bmp);
        }
        ui.bmp.setAlpha(255);

        float tt = Ui.clamp01((now - shownAt - 350) / 400f);
        if (tt < 1) anim = true;
        ui.text.setLetterSpacing(0.06f);
        ui.label(c, "INCASTRO", ui.w / 2f, titleY, ui.w * 0.13f, Ui.WHITE, ui.heavy, Paint.Align.CENTER);
        ui.text.setLetterSpacing(0);
        ui.label(c, L.t("puzzle a blocchi", "block puzzle"), ui.w / 2f, subtitleY, ui.w * 0.045f, Ui.DIM, ui.bold, Paint.Align.CENTER);
        if (app.eng.best > 0) {
            if (recordVal != app.eng.best) {
                recordVal = app.eng.best;
                recordStr = L.t("Record ", "Best ") + recordVal;
            }
            float size = ui.w * 0.05f;
            ui.text.setTypeface(ui.bold);
            ui.text.setTextSize(size);
            float tw = ui.text.measureText(recordStr), cx = ui.w / 2f + size * 0.7f;
            ui.crown(c, cx - tw / 2f - size * 0.9f, recordY - size * 0.35f, size * 1.05f, Ui.GOLD);
            ui.label(c, recordStr, cx, recordY, size, Ui.GOLD, ui.bold, Paint.Align.CENTER);
        }
        // I pulsanti entrano con un piccolo ritardo, dopo il logo.
        float s = 0.8f + 0.2f * Ui.back(tt);
        c.save();
        c.scale(s, s, ui.w / 2f, play.r.bottom);
        play.label = L.t("Gioca", "Play");
        options.label = L.t("Impostazioni", "Settings");
        play.draw(c, ui);
        options.draw(c, ui);
        c.restore();
        return anim;
    }

    @Override
    void touch(MotionEvent e, long now) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                play.down(x, y);
                options.down(x, y);
                break;
            case MotionEvent.ACTION_UP:
                if (play.up(x, y)) {
                    app.sfx.play(Sfx.TAP);
                    if (app.eng.over) {
                        app.eng.newGame();
                        app.save();
                    }
                    app.show(app.game);
                }
                if (options.up(x, y)) {
                    app.sfx.play(Sfx.TAP);
                    app.show(app.settings);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                play.cancel();
                options.cancel();
                break;
            default:
                break;
        }
    }
}
