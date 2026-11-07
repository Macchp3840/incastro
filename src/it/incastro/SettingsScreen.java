package it.incastro;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

/** Impostazioni: suoni, vibrazione, lingua, azzeramento del record. */
final class SettingsScreen extends Screen {
    private static final long DIALOG_MS = 260, TOAST_MS = 1600;
    static final String VERSION = "1.2";

    private final Button reset = new Button("Azzera record", Ui.PALETTE[Ui.RED]);
    private final Button back = new Button("Indietro", Ui.PALETTE[Ui.BLUE]);
    private final Button yes = new Button("Sì", Ui.PALETTE[Ui.RED]);
    private final Button no = new Button("No", Ui.PALETTE[Ui.BLUE]);
    private final RectF soundRow = new RectF(), hapticRow = new RectF(), langRow = new RectF(), card = new RectF();
    private float soundKnob, hapticKnob, titleY, footerY;
    private boolean confirm, pressedSound, pressedHaptic, pressedLang;
    private long confirmAt, toastAt = -1;

    SettingsScreen(AppView app) {
        super(app);
    }

    @Override
    void layout() {
        float avail = ui.availH(), top = ui.top, m = ui.w * 0.07f, rh = avail * 0.085f;
        titleY = top + avail * 0.11f;
        soundRow.set(m, top + avail * 0.19f, ui.w - m, top + avail * 0.19f + rh);
        hapticRow.set(m, soundRow.bottom + rh * 0.3f, ui.w - m, soundRow.bottom + rh * 1.3f);
        langRow.set(m, hapticRow.bottom + rh * 0.3f, ui.w - m, hapticRow.bottom + rh * 1.3f);
        float bw = ui.w * 0.64f, bh = avail * 0.07f, bx = (ui.w - bw) / 2f;
        reset.r.set(bx, top + avail * 0.58f, bx + bw, top + avail * 0.58f + bh);
        back.r.set(bx, top + avail * 0.8f, bx + bw, top + avail * 0.8f + bh);
        footerY = top + avail * 0.97f;
        float cw = ui.w * 0.82f, ch = avail * 0.25f, cy = top + avail * 0.45f;
        card.set((ui.w - cw) / 2f, cy - ch / 2f, (ui.w + cw) / 2f, cy + ch / 2f);
        float half = cw * 0.34f, gy = card.bottom - bh - ch * 0.12f;
        yes.r.set(ui.w / 2f - half - cw * 0.03f, gy, ui.w / 2f - cw * 0.03f, gy + bh);
        no.r.set(ui.w / 2f + cw * 0.03f, gy, ui.w / 2f + half + cw * 0.03f, gy + bh);
    }

    @Override
    void onShow(long now) {
        soundKnob = app.sfx.enabled ? 1 : 0;
        hapticKnob = app.hapticsOn ? 1 : 0;
        confirm = false;
        toastAt = -1;
    }

    @Override
    boolean draw(Canvas c, long now) {
        ui.background(c);
        boolean anim = false;
        ui.label(c, L.t("Impostazioni", "Settings"), ui.w / 2f, titleY, ui.w * 0.085f, Ui.WHITE, ui.heavy, Paint.Align.CENTER);

        float ts = app.sfx.enabled ? 1 : 0, th = app.hapticsOn ? 1 : 0;
        soundKnob += (ts - soundKnob) * 0.3f;
        hapticKnob += (th - hapticKnob) * 0.3f;
        if (Math.abs(ts - soundKnob) > 0.01f || Math.abs(th - hapticKnob) > 0.01f) anim = true;
        drawRow(c, soundRow, L.t("Suoni", "Sound"), soundKnob, pressedSound);
        drawRow(c, hapticRow, L.t("Vibrazione", "Vibration"), hapticKnob, pressedHaptic);
        drawLangRow(c);

        reset.label = L.t("Azzera record", "Reset best");
        back.label = L.t("Indietro", "Back");
        yes.label = L.t("Sì", "Yes");

        reset.draw(c, ui);
        back.draw(c, ui);
        ui.label(c, "Incastro " + VERSION, ui.w / 2f, footerY, ui.w * 0.035f, Ui.DIM, ui.bold, Paint.Align.CENTER);

        if (toastAt >= 0) {
            float t = (now - toastAt) / (float) TOAST_MS;
            if (t >= 1) {
                toastAt = -1;
            } else {
                anim = true;
                ui.text.setTypeface(ui.bold);
                ui.text.setTextSize(ui.w * 0.045f);
                ui.text.setTextAlign(Paint.Align.CENTER);
                ui.text.setColor(Ui.GOLD);
                ui.text.setAlpha((int) (255 * Math.min(1f, (1 - t) * 4)));
                c.drawText(L.t("Record azzerato", "Best score reset"), ui.w / 2f, reset.r.bottom + reset.r.height() * 1.1f, ui.text);
            }
        }

        if (confirm) {
            float t = (now - confirmAt) / (float) DIALOG_MS;
            if (t < 1) anim = true;
            ui.dialog(c, t, card);
            if (t >= 1) {
                ui.label(c, L.t("Azzerare il record?", "Reset best score?"), ui.w / 2f, card.top + card.height() * 0.32f,
                    ui.w * 0.06f, Ui.WHITE, ui.heavy, Paint.Align.CENTER);
                yes.draw(c, ui);
                no.draw(c, ui);
            }
        }
        return anim;
    }

    /** Riga "Lingua": a destra la lingua attuale, un tocco passa all'altra. */
    private void drawLangRow(Canvas c) {
        RectF row = langRow;
        ui.fill.setColor(pressedLang ? Ui.mix(Ui.CARD, Ui.WHITE, 0.06f) : Ui.CARD);
        c.drawRoundRect(row, row.height() * 0.3f, row.height() * 0.3f, ui.fill);
        ui.label(c, L.t("Lingua", "Language"), row.left + row.height() * 0.4f, row.centerY() + row.height() * 0.13f,
            row.height() * 0.36f, Ui.WHITE, ui.bold, Paint.Align.LEFT);
        ui.label(c, L.en ? "English" : "Italiano", row.right - row.height() * 0.4f, row.centerY() + row.height() * 0.13f,
            row.height() * 0.34f, Ui.CYAN, ui.bold, Paint.Align.RIGHT);
    }

    private void drawRow(Canvas c, RectF row, String label, float knob, boolean pressed) {
        ui.fill.setColor(pressed ? Ui.mix(Ui.CARD, Ui.WHITE, 0.06f) : Ui.CARD);
        c.drawRoundRect(row, row.height() * 0.3f, row.height() * 0.3f, ui.fill);
        ui.label(c, label, row.left + row.height() * 0.4f, row.centerY() + row.height() * 0.13f,
            row.height() * 0.36f, Ui.WHITE, ui.bold, Paint.Align.LEFT);
        float th = row.height() * 0.42f, tw = th * 1.8f, tr = row.right - row.height() * 0.35f;
        ui.tmp.set(tr - tw, row.centerY() - th / 2f, tr, row.centerY() + th / 2f);
        ui.fill.setColor(Ui.mix(0xFF3A4275, Ui.PALETTE[Ui.GREEN], knob));
        c.drawRoundRect(ui.tmp, th / 2f, th / 2f, ui.fill);
        float r = th * 0.4f, kx = ui.tmp.left + th / 2f + (tw - th) * knob;
        ui.fill.setColor(Ui.WHITE);
        c.drawCircle(kx, row.centerY(), r, ui.fill);
    }

    @Override
    void touch(MotionEvent e, long now) {
        float x = e.getX(), y = e.getY();
        int action = e.getActionMasked();
        if (confirm) {
            if (action == MotionEvent.ACTION_DOWN) {
                yes.down(x, y);
                no.down(x, y);
            } else if (action == MotionEvent.ACTION_UP) {
                if (yes.up(x, y)) {
                    app.resetRecord();
                    app.sfx.play(Sfx.TAP);
                    confirm = false;
                    toastAt = now;
                } else if (no.up(x, y) || !card.contains(x, y)) {
                    app.sfx.play(Sfx.TAP);
                    confirm = false;
                }
            } else if (action == MotionEvent.ACTION_CANCEL) {
                yes.cancel();
                no.cancel();
            }
            return;
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                pressedSound = soundRow.contains(x, y);
                pressedHaptic = hapticRow.contains(x, y);
                pressedLang = langRow.contains(x, y);
                reset.down(x, y);
                back.down(x, y);
                break;
            case MotionEvent.ACTION_UP:
                if (pressedSound && soundRow.contains(x, y)) {
                    app.setSound(!app.sfx.enabled);
                    app.sfx.play(Sfx.TAP);
                }
                if (pressedHaptic && hapticRow.contains(x, y)) {
                    app.setHaptics(!app.hapticsOn);
                    app.haptic(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    app.sfx.play(Sfx.TAP);
                }
                if (pressedLang && langRow.contains(x, y)) {
                    app.setEnglish(!L.en);
                    app.sfx.play(Sfx.TAP);
                }
                if (reset.up(x, y)) {
                    app.sfx.play(Sfx.TAP);
                    confirm = true;
                    confirmAt = now;
                }
                if (back.up(x, y)) back();
                pressedSound = pressedHaptic = pressedLang = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                pressedSound = pressedHaptic = pressedLang = false;
                reset.cancel();
                back.cancel();
                break;
            default:
                break;
        }
    }

    @Override
    boolean back() {
        if (confirm) {
            confirm = false;
            return true;
        }
        app.sfx.play(Sfx.TAP);
        app.show(app.home);
        return true;
    }
}
