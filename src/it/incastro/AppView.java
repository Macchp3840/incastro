package it.incastro;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

/**
 * Unica vista dell'app: tiene le schermate (home, impostazioni, gioco), le dissolvenze tra una
 * e l'altra, le preferenze e il salvataggio. Si ridisegna solo mentre qualcosa si muove.
 */
final class AppView extends View {
    private static final long TRANS_MS = 240;

    final Ui ui = new Ui();
    final Engine eng;
    final Sfx sfx;
    private final SharedPreferences prefs;
    boolean hapticsOn;

    final HomeScreen home;
    final SettingsScreen settings;
    final GameScreen game;
    private Screen current, leaving;
    private long transStart;
    private int insetTop, insetBottom;

    AppView(Context ctx, Engine eng, SharedPreferences prefs, Sfx sfx) {
        super(ctx);
        this.eng = eng;
        this.prefs = prefs;
        this.sfx = sfx;
        hapticsOn = prefs.getBoolean("haptics", true);
        sfx.enabled = prefs.getBoolean("sound", true);
        String lang = prefs.getString("lang", null);
        L.en = lang != null ? lang.equals("en") : !java.util.Locale.getDefault().getLanguage().equals("it");
        ui.theme = Theme.ALL[Math.floorMod(prefs.getInt("theme", 0), Theme.ALL.length)];
        home = new HomeScreen(this);
        settings = new SettingsScreen(this);
        game = new GameScreen(this);
        current = home;
        home.onShow(SystemClock.uptimeMillis());
        setHapticFeedbackEnabled(true);
    }

    void show(Screen s) {
        if (s == current) return;
        long now = SystemClock.uptimeMillis();
        leaving = current;
        current = s;
        transStart = now;
        s.onShow(now);
        setKeepScreenOn(s == game);
        invalidate();
    }

    /** Indietro dal sistema; false quando si è già nella home e si può uscire. */
    boolean back() {
        if (leaving != null) return true;
        return current.back();
    }

    void haptic(int feedback) {
        if (hapticsOn) performHapticFeedback(feedback);
    }

    void save() {
        prefs.edit().putLong("best", eng.best).putString("state", eng.save()).apply();
    }

    /** Passa a un tema a caso diverso da quello attuale. */
    Theme nextTheme() {
        int cur = java.util.Arrays.asList(Theme.ALL).indexOf(ui.theme);
        int next = (cur + 1 + (int) (Math.random() * (Theme.ALL.length - 1))) % Theme.ALL.length;
        ui.setTheme(Theme.ALL[next]);
        prefs.edit().putInt("theme", next).apply();
        return ui.theme;
    }

    void setEnglish(boolean on) {
        L.en = on;
        prefs.edit().putString("lang", on ? "en" : "it").apply();
    }

    void setSound(boolean on) {
        sfx.enabled = on;
        prefs.edit().putBoolean("sound", on).apply();
    }

    void setHaptics(boolean on) {
        hapticsOn = on;
        prefs.edit().putBoolean("haptics", on).apply();
    }

    void resetRecord() {
        eng.best = eng.score;
        eng.bestBefore = 0;
        save();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets in) {
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets s = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            insetTop = s.top;
            insetBottom = s.bottom;
        } else {
            insetTop = in.getSystemWindowInsetTop();
            insetBottom = in.getSystemWindowInsetBottom();
        }
        relayout(getWidth(), getHeight());
        invalidate();
        return in;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        relayout(w, h);
    }

    private void relayout(int w, int h) {
        if (w == 0 || h == 0) return;
        ui.layout(w, h, insetTop, insetBottom);
        home.layout();
        settings.layout();
        game.layout();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (ui.w == 0) return;
        long now = SystemClock.uptimeMillis();
        boolean anim;
        if (leaving != null) {
            float t = (now - transStart) / (float) TRANS_MS;
            if (t >= 1) {
                leaving = null;
                anim = current.draw(c, now);
            } else {
                // Dissolvenza attraverso il blu scuro: prima esce la vecchia schermata, poi entra la nuova.
                float shade;
                if (t < 0.5f) {
                    leaving.draw(c, now);
                    shade = t / 0.5f;
                } else {
                    current.draw(c, now);
                    shade = (1 - t) / 0.5f;
                }
                ui.fill.setColor(Ui.SHADE);
                ui.fill.setAlpha((int) (255 * shade));
                c.drawRect(0, 0, ui.w, ui.h, ui.fill);
                anim = true;
            }
        } else {
            anim = current.draw(c, now);
        }
        if (anim) postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (leaving != null) return true;
        // Gli eventi arrivano appena il dito si muove, senza attendere il fotogramma successivo.
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && Build.VERSION.SDK_INT >= 30) requestUnbufferedDispatch(e);
        current.touch(e, SystemClock.uptimeMillis());
        invalidate();
        return true;
    }
}
