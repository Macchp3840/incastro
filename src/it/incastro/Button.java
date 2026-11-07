package it.incastro;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Pulsante a pillola con effetto pressione; il clic scatta al rilascio dentro il pulsante. */
final class Button {
    final RectF r = new RectF();
    String label;
    int color;
    private boolean pressed;

    Button(String label, int color) {
        this.label = label;
        this.color = color;
    }

    private boolean hit(float x, float y) {
        float m = r.height() * 0.15f;
        return !r.isEmpty() && x >= r.left - m && x <= r.right + m && y >= r.top - m && y <= r.bottom + m;
    }

    boolean down(float x, float y) {
        pressed = hit(x, y);
        return pressed;
    }

    boolean up(float x, float y) {
        boolean clicked = pressed && hit(x, y);
        pressed = false;
        return clicked;
    }

    void cancel() {
        pressed = false;
    }

    void draw(Canvas c, Ui ui) {
        if (r.isEmpty()) return;
        float rad = r.height() / 2f, depth = r.height() * 0.09f, dy = pressed ? depth * 0.7f : 0;
        ui.fill.setColor(Ui.mix(color, 0xFF000000, 0.32f));
        c.drawRoundRect(r.left, r.top + depth, r.right, r.bottom + depth, rad, rad, ui.fill);
        ui.fill.setColor(pressed ? Ui.mix(color, 0xFF000000, 0.08f) : color);
        c.drawRoundRect(r.left, r.top + dy, r.right, r.bottom + dy, rad, rad, ui.fill);
        ui.fill.setColor(0x24FFFFFF);
        c.drawRoundRect(r.left + rad * 0.6f, r.top + dy + r.height() * 0.08f, r.right - rad * 0.6f,
            r.top + dy + r.height() * 0.2f, rad * 0.12f, rad * 0.12f, ui.fill);
        ui.label(c, label, r.centerX(), r.centerY() + dy + r.height() * 0.15f, r.height() * 0.42f,
            Ui.WHITE, ui.heavy, Paint.Align.CENTER);
    }
}
