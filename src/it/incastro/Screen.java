package it.incastro;

import android.graphics.Canvas;
import android.view.MotionEvent;

/** Una schermata disegnata dentro {@link AppView}. */
abstract class Screen {
    final AppView app;
    final Ui ui;

    Screen(AppView app) {
        this.app = app;
        this.ui = app.ui;
    }

    /** Ricalcola le misure da ui.w, ui.h e dagli inset di sistema. */
    abstract void layout();

    /** Disegna un fotogramma; restituisce true se serve un altro fotogramma (animazione in corso). */
    abstract boolean draw(Canvas c, long now);

    abstract void touch(MotionEvent e, long now);

    void onShow(long now) {
    }

    /** Gesto Indietro: true se gestito, false per lasciare uscire dall'app. */
    boolean back() {
        return false;
    }
}
