package it.incastro;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.window.OnBackInvokedDispatcher;

import java.util.Random;

public final class MainActivity extends Activity {
    private AppView view;
    private Sfx sfx;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        SharedPreferences prefs = getSharedPreferences("incastro", MODE_PRIVATE);
        Engine eng = new Engine(new Random());
        eng.best = prefs.getLong("best", 0);
        String state = prefs.getString("state", null);
        if (state == null || !eng.load(state)) eng.newGame();

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            preferHighestRefreshRate();
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        sfx = new Sfx(this);
        view = new AppView(this, eng, prefs, sfx);
        setContentView(view);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                if (!view.back()) finish();
            });
        }
    }

    /** Chiede la frequenza più alta del display (es. 120 Hz) alla risoluzione attuale. */
    private void preferHighestRefreshRate() {
        Display d = getDisplay();
        if (d == null) return;
        Display.Mode best = d.getMode();
        for (Display.Mode m : d.getSupportedModes()) {
            if (m.getPhysicalWidth() == best.getPhysicalWidth() && m.getPhysicalHeight() == best.getPhysicalHeight()
                && m.getRefreshRate() > best.getRefreshRate()) {
                best = m;
            }
        }
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.preferredDisplayModeId = best.getModeId();
        getWindow().setAttributes(lp);
    }

    @Override
    public void onBackPressed() {
        if (!view.back()) super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.save();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sfx.release();
    }
}
