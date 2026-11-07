package it.incastro;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/** La partita: griglia, vassoio, trascinamento, animazioni e dialoghi. */
final class GameScreen extends Screen {
    private static final long BURST_MS = 380, WAVE_MS = 22, RETURN_MS = 85, POP_MS = 170,
        REFILL_MS = 280, REFILL_STAGGER_MS = 70, OVER_DELAY_MS = 700, DIALOG_MS = 280;
    private static final int MAX_PARTICLES = 320;
    /** Il pezzo si sposta 1,5 volte il dito. */
    private static final float DRAG_RATIO = 1.5f;
    private static final float SLOT_STEP = 0.287f;
    /** Tolleranza in celle per restare agganciati all'ultima posizione valida: BLOCK_SIZE + 10 su 106. */
    private static final float SNAP_KEEP = 116f / 106f;

    private final Engine eng;
    private final Random fx = new Random();

    private float cell, boardX, boardY, boardSize, boardPad, trayTop, trayCy, slotW, trayCell, headerTop, headerH;
    private Bitmap[] big, small;
    private Bitmap boardLayer;
    private final RectF homeBtn = new RectF(), restartBtn = new RectF(), overCard = new RectF(), askCard = new RectF();
    private final Button again = new Button("Gioca ancora", Ui.PALETTE[Ui.GREEN]);
    private final Button toHome = new Button("Home", Ui.PALETTE[Ui.BLUE]);
    private final Button yes = new Button("Sì", Ui.PALETTE[Ui.RED]);
    private final Button no = new Button("No", Ui.PALETTE[Ui.BLUE]);

    // Trascinamento: al tocco il pezzo salta a grandezza piena con il centro
    // sul bordo basso della griglia, poi si muove 1,5 volte il dito. dragX/dragY = angolo del pezzo.
    private int dragSlot = -1;
    private float dragX, dragY, startX, startY, touchX0, touchY0;
    private int snapR, snapC;
    private boolean snapOk, hadValid;
    private long previewFull;
    // Pezzo che torna al vassoio dopo un rilascio non valido.
    private int returnSlot = -1;
    private float retX, retY, retSize;
    private long returnAt;

    private long placedMask, placedAt, refillAt = -1, overAt = -1, dialogAt;
    private boolean overSoundDone, confirmRestart, pressHome, pressRestart;
    private final boolean[] slotFits = new boolean[3];
    private double shownScore;
    private long lastFrame;

    // Testi dell'intestazione ricreati solo quando cambia il valore, non a ogni fotogramma.
    private String scoreStr = "0", bestStr = "0", comboStr = "";
    private long scoreVal = -1, bestVal = -1;
    private int comboVal = -1;

    private static final class Burst {
        int idx, color;
        long start;
    }

    private static final class Floater {
        String s;
        float x, y, size;
        int color;
        long start, dur;
    }

    private final ArrayList<Burst> bursts = new ArrayList<>();
    private final ArrayList<Floater> floaters = new ArrayList<>();

    // Particelle in array preallocati: nessuna allocazione durante le esplosioni.
    private final float[] px = new float[MAX_PARTICLES], py = new float[MAX_PARTICLES];
    private final float[] pvx = new float[MAX_PARTICLES], pvy = new float[MAX_PARTICLES];
    private final float[] plife = new float[MAX_PARTICLES], pttl = new float[MAX_PARTICLES], psize = new float[MAX_PARTICLES];
    private final int[] pcol = new int[MAX_PARTICLES];
    private int pCount;

    GameScreen(AppView app) {
        super(app);
        eng = app.eng;
    }

    @Override
    void layout() {
        float top = ui.top, avail = ui.availH();
        int w = ui.w;
        headerTop = top;
        headerH = avail * 0.16f;
        // Griglia all'88,5% della larghezza, vassoio 2,37 celle sotto il bordo.
        boardSize = Math.min(w * 0.885f, avail * 0.56f);
        cell = boardSize / Engine.N;
        boardX = (w - boardSize) / 2f;
        boardY = top + headerH;
        boardPad = cell * 0.12f;
        trayTop = boardY + boardSize + avail * 0.035f;
        float trayH = ui.h - ui.bottom - trayTop;
        slotW = w * SLOT_STEP;
        trayCell = Math.min(cell * 0.45f, Math.min(slotW * 0.88f / 5f, trayH * 0.8f / 5f));
        trayCy = Math.min(boardY + boardSize + 2.37f * cell, ui.h - ui.bottom - trayCell * 2.6f);
        big = ui.blocks(cell);
        small = ui.blocks(trayCell);
        float b = headerH * 0.34f, m = w * 0.045f, by = top + headerH * 0.1f;
        homeBtn.set(m, by, m + b, by + b);
        restartBtn.set(w - m - b, by, w - m, by + b);
        buildBoardLayer();

        float cw = w * 0.82f, bh = avail * 0.065f, bw = cw * 0.72f, cy = top + avail * 0.45f;
        float ch = avail * 0.42f;
        overCard.set((w - cw) / 2f, cy - ch / 2f, (w + cw) / 2f, cy + ch / 2f);
        float gy = overCard.top + ch * 0.56f;
        again.r.set((w - bw) / 2f, gy, (w + bw) / 2f, gy + bh);
        toHome.r.set((w - bw) / 2f, gy + bh * 1.3f, (w + bw) / 2f, gy + bh * 2.3f);
        float ah = avail * 0.25f;
        askCard.set((w - cw) / 2f, cy - ah / 2f, (w + cw) / 2f, cy + ah / 2f);
        float half = cw * 0.34f, ay = askCard.bottom - bh - ah * 0.12f;
        yes.r.set(w / 2f - half - cw * 0.03f, ay, w / 2f - cw * 0.03f, ay + bh);
        no.r.set(w / 2f + cw * 0.03f, ay, w / 2f + half + cw * 0.03f, ay + bh);
    }

    /** Sfondo della griglia e caselle vuote disegnati una volta sola in una bitmap. */
    private void buildBoardLayer() {
        int size = (int) Math.ceil(boardSize + 2 * boardPad);
        boardLayer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(boardLayer);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Theme th = ui.theme;
        p.setColor(th.boardBg);
        c.drawRoundRect(0, 0, size, size, cell * 0.25f, cell * 0.25f, p);
        th.boardDeco(c, size, p);
        for (int i = 0; i < Engine.N * Engine.N; i++) {
            th.empty(c, boardPad + (i % Engine.N) * cell, boardPad + (i / Engine.N) * cell, cell, p);
        }
    }

    @Override
    void onShow(long now) {
        shownScore = eng.score;
        lastFrame = 0;
        refillAt = now;
        dragSlot = -1;
        returnSlot = -1;
        confirmRestart = false;
        updateFits();
        overAt = eng.over ? now : -1;
        overSoundDone = eng.over;
    }

    private void updateFits() {
        for (int s = 0; s < 3; s++) slotFits[s] = eng.trayShape[s] >= 0 && Engine.fits(eng.board, eng.trayShape[s]);
    }

    /** Centri dei 3 pezzi nel vassoio: 21%, 50% e 79% della larghezza. */
    private float slotCx(int s) {
        return ui.w * (0.5f + (s - 1) * SLOT_STEP);
    }

    private float slotX0(int s, Engine.Shape sh) {
        return slotCx(s) - sh.w * trayCell / 2f;
    }

    private float slotY0(Engine.Shape sh) {
        return trayCy - sh.h * trayCell / 2f;
    }


    @Override
    boolean draw(Canvas c, long now) {
        float dt = lastFrame == 0 ? 0 : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (returnSlot >= 0 && now - returnAt >= RETURN_MS) returnSlot = -1;
        boolean anim = false;
        ui.background(c);
        anim |= drawHeader(c);
        c.drawBitmap(boardLayer, boardX - boardPad, boardY - boardPad, null);
        anim |= drawCells(c, now);
        anim |= drawBursts(c, now);
        anim |= drawParticles(c, dt);
        anim |= drawTray(c, now);
        anim |= drawHand(c, now);
        anim |= drawFloaters(c, now);
        anim |= drawDialogs(c, now);
        if (!anim) lastFrame = 0;
        return anim;
    }

    private boolean drawHeader(Canvas c) {
        boolean anim = false;
        double diff = eng.score - shownScore;
        if (Math.abs(diff) > 0.5) {
            shownScore += diff * 0.18 + Math.signum(diff) * 0.5;
            if (Math.signum(eng.score - shownScore) != Math.signum(diff)) shownScore = eng.score;
            anim = true;
        } else {
            shownScore = eng.score;
        }
        long sv = Math.round(shownScore);
        if (sv != scoreVal) {
            scoreVal = sv;
            scoreStr = Long.toString(sv);
        }
        if (eng.best != bestVal) {
            bestVal = eng.best;
            bestStr = Long.toString(bestVal);
        }
        if (eng.combo != comboVal) {
            comboVal = eng.combo;
            comboStr = "COMBO ×" + comboVal; // uguale nelle due lingue
        }
        drawIcon(c, homeBtn, pressHome, true);
        drawIcon(c, restartBtn, pressRestart, false);
        ui.label(c, scoreStr, ui.w / 2f, headerTop + headerH * 0.62f, headerH * 0.42f, Ui.WHITE, ui.heavy, Paint.Align.CENTER);
        float size = headerH * 0.15f, ly = headerTop + headerH * 0.9f;
        ui.crown(c, homeBtn.left + size * 0.55f, ly - size * 0.36f, size * 1.1f, Ui.GOLD);
        ui.label(c, bestStr, homeBtn.left + size * 1.35f, ly, size, Ui.GOLD, ui.bold, Paint.Align.LEFT);
        if (eng.combo >= 2) ui.label(c, comboStr, restartBtn.right, ly, size, Ui.CYAN, ui.bold, Paint.Align.RIGHT);
        return anim;
    }

    private void drawIcon(Canvas c, RectF r, boolean pressed, boolean home) {
        ui.fill.setColor(pressed ? 0x55FFFFFF : 0x2EFFFFFF);
        c.drawOval(r, ui.fill);
        float cx = r.centerX(), cy = r.centerY(), s = r.width();
        ui.fill.setColor(Ui.WHITE);
        if (home) {
            ui.path.reset();
            ui.path.moveTo(cx, cy - s * 0.25f);
            ui.path.lineTo(cx + s * 0.26f, cy - s * 0.02f);
            ui.path.lineTo(cx - s * 0.26f, cy - s * 0.02f);
            ui.path.close();
            c.drawPath(ui.path, ui.fill);
            c.drawRect(cx - s * 0.17f, cy - s * 0.05f, cx + s * 0.17f, cy + s * 0.21f, ui.fill);
            ui.fill.setColor(0xFF39427A);
            c.drawRect(cx - s * 0.05f, cy + s * 0.06f, cx + s * 0.05f, cy + s * 0.21f, ui.fill);
            return;
        }
        float rr = s * 0.25f;
        ui.fill.setStyle(Paint.Style.STROKE);
        ui.fill.setStrokeWidth(rr * 0.32f);
        ui.fill.setStrokeCap(Paint.Cap.ROUND);
        ui.tmp.set(cx - rr, cy - rr, cx + rr, cy + rr);
        c.drawArc(ui.tmp, -60, 285, false, ui.fill);
        ui.fill.setStyle(Paint.Style.FILL);
        // Punta della freccia alla fine dell'arco, nel verso di rotazione.
        double a = Math.toRadians(225);
        float ax = cx + rr * (float) Math.cos(a), ay = cy + rr * (float) Math.sin(a);
        float tx = (float) -Math.sin(a), ty = (float) Math.cos(a);
        float nx = (float) Math.cos(a), ny = (float) Math.sin(a), k = rr * 0.55f;
        ui.path.reset();
        ui.path.moveTo(ax + tx * k, ay + ty * k);
        ui.path.lineTo(ax + nx * k * 0.8f, ay + ny * k * 0.8f);
        ui.path.lineTo(ax - nx * k * 0.8f, ay - ny * k * 0.8f);
        ui.path.close();
        c.drawPath(ui.path, ui.fill);
    }

    /** Blocchi in griglia, anteprima del pezzo e linee che si chiuderebbero, "pop" dell'ultimo pezzo. */
    private boolean drawCells(Canvas c, long now) {
        long ghost = 0;
        int dragColor = 0;
        if (dragSlot >= 0 && snapOk) {
            ghost = eng.maskAt(eng.trayShape[dragSlot], snapR, snapC);
            dragColor = eng.trayColor[dragSlot];
        }
        float popT = (now - placedAt) / (float) POP_MS;
        boolean pop = popT < 1 && placedMask != 0;
        for (int i = 0; i < Engine.N * Engine.N; i++) {
            long bit = 1L << i;
            float x = boardX + (i % Engine.N) * cell, y = boardY + (i / Engine.N) * cell;
            if ((previewFull & bit) != 0) {
                c.drawBitmap(big[dragColor], x, y, null);
            } else if (eng.cells[i] > 0) {
                if (pop && (placedMask & bit) != 0) {
                    float half = cell * (1f + 0.12f * (float) Math.sin(Math.PI * popT)) / 2f;
                    ui.tmp.set(x + cell / 2f - half, y + cell / 2f - half, x + cell / 2f + half, y + cell / 2f + half);
                    c.drawBitmap(big[eng.cells[i]], null, ui.tmp, ui.bmp);
                } else {
                    c.drawBitmap(big[eng.cells[i]], x, y, null);
                }
            } else if ((ghost & bit) != 0) {
                ui.bmp.setAlpha(110);
                c.drawBitmap(big[dragColor], x, y, ui.bmp);
                ui.bmp.setAlpha(255);
            }
        }
        return pop;
    }

    /** Celle eliminate: si gonfiano e svaniscono, a onda dal punto del piazzamento. */
    private boolean drawBursts(Canvas c, long now) {
        for (Iterator<Burst> it = bursts.iterator(); it.hasNext(); ) {
            Burst b = it.next();
            float t = (now - b.start) / (float) BURST_MS;
            if (t >= 1) {
                it.remove();
                continue;
            }
            float x = boardX + (b.idx % Engine.N) * cell, y = boardY + (b.idx / Engine.N) * cell;
            if (t < 0) {
                c.drawBitmap(big[b.color], x, y, null);
                continue;
            }
            float half = cell * (1f + 0.35f * t) / 2f, cx = x + cell / 2f, cy = y + cell / 2f;
            ui.tmp.set(cx - half, cy - half, cx + half, cy + half);
            ui.bmp.setAlpha((int) (255 * (1 - t)));
            c.drawBitmap(big[b.color], null, ui.tmp, ui.bmp);
        }
        ui.bmp.setAlpha(255);
        return !bursts.isEmpty();
    }

    private void spawn(float x, float y, int color, int n, float speed, float size) {
        for (int k = 0; k < n && pCount < MAX_PARTICLES; k++) {
            double a = fx.nextDouble() * Math.PI * 2;
            float v = speed * (0.35f + 0.65f * fx.nextFloat());
            int i = pCount++;
            px[i] = x;
            py[i] = y;
            pvx[i] = (float) Math.cos(a) * v;
            pvy[i] = (float) Math.sin(a) * v - speed * 0.35f;
            plife[i] = 0;
            pttl[i] = 0.45f + 0.4f * fx.nextFloat();
            psize[i] = size * (0.5f + 0.5f * fx.nextFloat());
            pcol[i] = color;
        }
    }

    private boolean drawParticles(Canvas c, float dt) {
        float gravity = cell * 9f;
        for (int i = 0; i < pCount; ) {
            plife[i] += dt;
            if (plife[i] >= pttl[i]) {
                int last = --pCount;
                px[i] = px[last];
                py[i] = py[last];
                pvx[i] = pvx[last];
                pvy[i] = pvy[last];
                plife[i] = plife[last];
                pttl[i] = pttl[last];
                psize[i] = psize[last];
                pcol[i] = pcol[last];
                continue;
            }
            pvx[i] *= 0.985f;
            pvy[i] += gravity * dt;
            px[i] += pvx[i] * dt;
            py[i] += pvy[i] * dt;
            float k = 1 - plife[i] / pttl[i], s = psize[i] * (0.4f + 0.6f * k) / 2f;
            ui.fill.setColor(pcol[i]);
            ui.fill.setAlpha((int) (255 * k));
            c.drawRect(px[i] - s, py[i] - s, px[i] + s, py[i] + s, ui.fill);
            i++;
        }
        return pCount > 0;
    }

    /** I 3 pezzi: attenuati quelli che non entrano, con comparsa a molla e bagliore dopo un incastro perfetto. */
    private boolean drawTray(Canvas c, long now) {
        boolean anim = false;
        for (int s = 0; s < 3; s++) {
            int sh = eng.trayShape[s];
            if (sh < 0 || s == dragSlot || s == returnSlot) continue;
            Engine.Shape shape = Engine.SHAPES[sh];
            float cx = slotCx(s), cy = trayCy, scale = 1;
            if (refillAt >= 0) {
                float t = (now - refillAt - s * REFILL_STAGGER_MS) / (float) REFILL_MS;
                if (t < 1) {
                    anim = true;
                    if (t <= 0) continue;
                    scale = Ui.back(t);
                }
            }
            ui.bmp.setAlpha(slotFits[s] ? 255 : 80);
            boolean scaled = scale != 1;
            if (scaled) {
                c.save();
                c.scale(scale, scale, cx, cy);
            }
            float x0 = slotX0(s, shape), y0 = slotY0(shape);
            for (int r = 0; r < shape.h; r++) {
                for (int col = 0; col < shape.w; col++) {
                    if (shape.at(r, col)) c.drawBitmap(small[eng.trayColor[s]], x0 + col * trayCell, y0 + r * trayCell, ui.bmp);
                }
            }
            if (scaled) c.restore();
        }
        ui.bmp.setAlpha(255);
        return anim;
    }

    private void drawShapeSized(Canvas c, Engine.Shape shape, Bitmap block, float x0, float y0, float size) {
        for (int r = 0; r < shape.h; r++) {
            for (int col = 0; col < shape.w; col++) {
                if (!shape.at(r, col)) continue;
                float x = x0 + col * size, y = y0 + r * size;
                ui.tmp.set(x, y, x + size, y + size);
                c.drawBitmap(block, null, ui.tmp, ui.bmp);
            }
        }
    }

    /** Pezzo in mano (cresce da misura vassoio a misura griglia) e pezzo che torna al vassoio. */
    private boolean drawHand(Canvas c, long now) {
        boolean anim = false;
        if (returnSlot >= 0) {
            anim = true;
            Engine.Shape shape = Engine.SHAPES[eng.trayShape[returnSlot]];
            float e = Ui.ease((now - returnAt) / (float) RETURN_MS);
            float size = retSize + (trayCell - retSize) * e;
            float x = retX + (slotX0(returnSlot, shape) - retX) * e, y = retY + (slotY0(shape) - retY) * e;
            drawShapeSized(c, shape, big[eng.trayColor[returnSlot]], x, y, size);
        }
        if (dragSlot >= 0) {
            Engine.Shape shape = Engine.SHAPES[eng.trayShape[dragSlot]];
            drawShapeSized(c, shape, big[eng.trayColor[dragSlot]], dragX, dragY, cell);
        }
        return anim;
    }

    private boolean drawFloaters(Canvas c, long now) {
        ui.text.setTypeface(ui.heavy);
        ui.text.setTextAlign(Paint.Align.CENTER);
        for (Iterator<Floater> it = floaters.iterator(); it.hasNext(); ) {
            Floater f = it.next();
            float t = (now - f.start) / (float) f.dur;
            if (t >= 1) {
                it.remove();
                continue;
            }
            if (t < 0) continue;
            float size = f.size * (t < 0.15f ? 0.6f + t / 0.15f * 0.4f : 1f);
            ui.text.setTextSize(size);
            ui.text.setColor(f.color);
            ui.text.setAlpha((int) (255 * (t < 0.7f ? 1f : (1f - t) / 0.3f)));
            ui.text.setShadowLayer(size * 0.12f, 0, size * 0.06f, 0xAA000000);
            c.drawText(f.s, f.x, f.y - t * cell * 1.2f, ui.text);
        }
        ui.text.setShadowLayer(0, 0, 0, 0);
        ui.text.setAlpha(255);
        return !floaters.isEmpty();
    }

    private boolean drawDialogs(Canvas c, long now) {
        if (eng.over) {
            if (overAt < 0 || now < overAt || !bursts.isEmpty()) return true;
            if (!overSoundDone) {
                overSoundDone = true;
                dialogAt = now;
                app.sfx.play(Sfx.OVER);
                app.haptic(HapticFeedbackConstants.LONG_PRESS);
            }
            float t = (now - dialogAt) / (float) DIALOG_MS;
            ui.dialog(c, t, overCard);
            if (t < 1) return true;
            float cx = ui.w / 2f, top = overCard.top, ch = overCard.height();
            ui.label(c, L.t("Nessuno spazio!", "No space left!"), cx, top + ch * 0.15f, ui.w * 0.075f, Ui.WHITE, ui.heavy, Paint.Align.CENTER);
            ui.label(c, L.t("PUNTEGGIO", "SCORE"), cx, top + ch * 0.26f, ui.w * 0.04f, Ui.DIM, ui.bold, Paint.Align.CENTER);
            ui.label(c, scoreStr, cx, top + ch * 0.41f, ui.w * 0.12f, Ui.WHITE, ui.heavy, Paint.Align.CENTER);
            boolean record = eng.score > eng.bestBefore && eng.score > 0;
            ui.label(c, record ? L.t("Nuovo record!", "New best!") : L.t("Record ", "Best ") + bestStr, cx, top + ch * 0.5f, ui.w * 0.045f,
                Ui.GOLD, ui.bold, Paint.Align.CENTER);
            again.label = L.t("Gioca ancora", "Play again");
            again.draw(c, ui);
            toHome.draw(c, ui);
            return false;
        }
        if (confirmRestart) {
            float t = (now - dialogAt) / (float) DIALOG_MS;
            ui.dialog(c, t, askCard);
            if (t < 1) return true;
            ui.label(c, L.t("Ricominciare?", "Start over?"), ui.w / 2f, askCard.top + askCard.height() * 0.32f, ui.w * 0.075f,
                Ui.WHITE, ui.heavy, Paint.Align.CENTER);
            yes.label = L.t("Sì", "Yes");
            yes.draw(c, ui);
            no.draw(c, ui);
        }
        return false;
    }

    private boolean hitIcon(RectF r, float x, float y) {
        float m = r.width() * 0.3f;
        return x >= r.left - m && x <= r.right + m && y >= r.top - m && y <= r.bottom + m;
    }

    @Override
    void touch(MotionEvent e, long now) {
        float x = e.getX(), y = e.getY();
        int action = e.getActionMasked();
        if (eng.over) {
            if (overAt < 0 || now < overAt || !bursts.isEmpty()) return;
            if (action == MotionEvent.ACTION_DOWN) {
                again.down(x, y);
                toHome.down(x, y);
            } else if (action == MotionEvent.ACTION_UP) {
                if (again.up(x, y)) {
                    app.sfx.play(Sfx.TAP);
                    newGame(now);
                } else if (toHome.up(x, y)) {
                    app.sfx.play(Sfx.TAP);
                    app.show(app.home);
                }
            } else if (action == MotionEvent.ACTION_CANCEL) {
                again.cancel();
                toHome.cancel();
            }
            return;
        }
        if (confirmRestart) {
            if (action == MotionEvent.ACTION_DOWN) {
                yes.down(x, y);
                no.down(x, y);
            } else if (action == MotionEvent.ACTION_UP) {
                if (yes.up(x, y)) {
                    app.sfx.play(Sfx.TAP);
                    newGame(now);
                } else if (no.up(x, y) || !askCard.contains(x, y)) {
                    app.sfx.play(Sfx.TAP);
                    confirmRestart = false;
                }
            } else if (action == MotionEvent.ACTION_CANCEL) {
                yes.cancel();
                no.cancel();
            }
            return;
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (hitIcon(homeBtn, x, y)) {
                    pressHome = true;
                } else if (hitIcon(restartBtn, x, y)) {
                    pressRestart = true;
                } else if (y > boardY + boardSize + boardPad) {
                    startDrag(x, y, now);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragSlot >= 0) {
                    moveTo(x, y);
                    updateSnap();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (pressHome) {
                    pressHome = false;
                    if (hitIcon(homeBtn, x, y)) {
                        app.sfx.play(Sfx.TAP);
                        app.show(app.home);
                    }
                } else if (pressRestart) {
                    pressRestart = false;
                    if (hitIcon(restartBtn, x, y)) {
                        app.sfx.play(Sfx.TAP);
                        confirmRestart = true;
                        dialogAt = now;
                    }
                } else if (dragSlot >= 0) {
                    moveTo(x, y);
                    updateSnap();
                    drop(now);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                pressHome = pressRestart = false;
                if (dragSlot >= 0) startReturn(now);
                break;
            default:
                break;
        }
    }

    private void startDrag(float x, float y, long now) {
        int s = x < ui.w * (0.5f - SLOT_STEP / 2) ? 0 : x < ui.w * (0.5f + SLOT_STEP / 2) ? 1 : 2;
        if (eng.trayShape[s] < 0) return;
        Engine.Shape shape = Engine.SHAPES[eng.trayShape[s]];
        if (returnSlot == s) returnSlot = -1;
        startX = slotCx(s) - shape.w * cell / 2f;
        startY = boardY + boardSize - shape.h * cell / 2f;
        touchX0 = x;
        touchY0 = y;
        dragSlot = s;
        dragX = startX;
        dragY = startY;
        snapOk = hadValid = false;
        updateSnap();
        app.sfx.play(Sfx.PICK, 1f, 0.6f);
        app.haptic(HapticFeedbackConstants.CLOCK_TICK);
    }

    private void moveTo(float x, float y) {
        dragX = startX + (x - touchX0) * DRAG_RATIO;
        dragY = startY + (y - touchY0) * DRAG_RATIO;
    }

    /**
     * Ogni blocco va nella cella che contiene il suo centro. Se lì non entra, resta valida
     * l'ultima posizione buona finché il pezzo le sta entro SNAP_KEEP celle in orizzontale e in verticale.
     */
    private void updateSnap() {
        int sh = eng.trayShape[dragSlot];
        float fc = (dragX - boardX) / cell, fr = (dragY - boardY) / cell;
        int r = Math.round(fr), c = Math.round(fc);
        boolean ok;
        int nr = snapR, nc = snapC;
        if (eng.canPlace(sh, r, c)) {
            ok = true;
            nr = r;
            nc = c;
            hadValid = true;
        } else {
            ok = hadValid && Math.abs(fc - snapC) <= SNAP_KEEP && Math.abs(fr - snapR) <= SNAP_KEEP;
        }
        if (ok == snapOk && (!ok || (nr == snapR && nc == snapC))) return;
        snapOk = ok;
        snapR = nr;
        snapC = nc;
        previewFull = ok ? Engine.fullMask(eng.board | eng.maskAt(sh, nr, nc)) : 0;
    }

    private void drop(long now) {
        int s = dragSlot;
        if (snapOk) {
            long t0 = System.nanoTime();
            Engine.Move mv = eng.place(s, snapR, snapC);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            if (ms >= 8) Log.w("Incastro", "place lento: " + ms + " ms");
            if (mv != null) {
                dragSlot = -1;
                snapOk = false;
                previewFull = 0;
                onMove(mv, now);
                return;
            }
        }
        if (dragY < boardY + boardSize + boardPad) app.sfx.play(Sfx.BAD, 1f, 0.5f);
        startReturn(now);
    }

    private void startReturn(long now) {
        retX = dragX;
        retY = dragY;
        retSize = cell;
        returnSlot = dragSlot;
        returnAt = now;
        dragSlot = -1;
        snapOk = false;
        previewFull = 0;
    }

    private void onMove(Engine.Move mv, long now) {
        Engine.Shape shape = Engine.SHAPES[mv.shape];
        placedMask = mv.placedMask;
        placedAt = now;
        float midR = mv.r + shape.h / 2f - 0.5f, midC = mv.c + shape.w / 2f - 0.5f;
        for (int i = 0; i < Engine.N * Engine.N; i++) {
            if ((mv.clearedMask >>> i & 1) == 0) continue;
            Burst b = new Burst();
            b.idx = i;
            b.color = mv.clearedColors[i];
            b.start = now + (long) (Math.hypot(i / Engine.N - midR, i % Engine.N - midC) * WAVE_MS);
            bursts.add(b);
            spawn(boardX + (i % Engine.N + 0.5f) * cell, boardY + (i / Engine.N + 0.5f) * cell,
                ui.theme.pal[b.color], 3, cell * 4f, cell * 0.16f);
        }
        float cx = ui.w / 2f, bx = boardX + (midC + 0.5f) * cell, by = boardY + (midR + 0.5f) * cell;
        addFloater("+" + mv.points, bx, by, cell * 0.55f, Ui.WHITE, now, 950);
        if (mv.lines >= 2) {
            String praise = mv.lines == 2 ? L.t("Ottimo!", "Great!") : mv.lines == 3 ? L.t("Fantastico!", "Fantastic!") : L.t("Incredibile!", "Incredible!");
            addFloater(praise, cx, boardY + boardSize * 0.45f, cell * 0.8f, Ui.GOLD, now + 60, 950);
        }
        if (mv.combo >= 2) addFloater("Combo ×" + mv.combo, cx, boardY + boardSize * 0.64f, cell * 0.6f, Ui.CYAN, now + 120, 950);
        if (mv.boardCleared) {
            addFloater((mv.clearBonus > 0 ? L.t("Griglia pulita! +", "Board clear! +") + mv.clearBonus : L.t("Griglia pulita!", "Board clear!")), cx, boardY + boardSize * 0.7f, cell * 0.55f,
                0xFF9BF59B, now + 180, 1100);
        }

        app.sfx.play(Sfx.PLACE, 0.95f + 0.1f * fx.nextFloat(), 0.8f);
        if (mv.lines > 0) app.sfx.play(Sfx.CLEAR, 1f + 0.1f * (mv.lines - 1) + 0.05f * (mv.combo - 1), 0.8f);
        if (mv.lines > 0 && Build.VERSION.SDK_INT >= 30) {
            app.haptic(HapticFeedbackConstants.CONFIRM);
        } else {
            app.haptic(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        // Tema nuovo a caso dopo la griglia pulita (dal 6° turno) o a ogni combo 8, 16, ...
        if (mv.clearBonus > 0 || (mv.lines > 0 && mv.combo >= 8 && mv.combo % 8 == 0)) {
            Theme th = app.nextTheme();
            big = ui.blocks(cell);
            small = ui.blocks(trayCell);
            buildBoardLayer();
            addFloater(L.t("Tema: ", "Theme: ") + th.name(), cx, boardY + boardSize * 0.2f, cell * 0.6f, Ui.WHITE, now + 250, 1400);
        }
        if (mv.refilled) {
            refillAt = now + 120;
            if (mv.lines == 0) app.sfx.play(Sfx.REFILL, 1f, 0.5f);
        }
        updateFits();
        if (mv.gameOver) {
            overAt = now + OVER_DELAY_MS;
            overSoundDone = false;
        }
        app.save();
    }

    private void addFloater(String s, float x, float y, float size, int color, long start, long dur) {
        ui.text.setTypeface(ui.heavy);
        ui.text.setTextSize(size);
        float tw = ui.text.measureText(s), max = ui.w * 0.9f;
        if (tw > max) {
            size *= max / tw;
            tw = max;
        }
        Floater f = new Floater();
        f.s = s;
        f.x = Math.max(ui.w / 2f - (max - tw) / 2f, Math.min(ui.w / 2f + (max - tw) / 2f, x));
        f.y = y;
        f.size = size;
        f.color = color;
        f.start = start;
        f.dur = dur;
        floaters.add(f);
    }

    private void newGame(long now) {
        eng.newGame();
        shownScore = 0;
        bursts.clear();
        floaters.clear();
        pCount = 0;
        confirmRestart = false;
        overAt = -1;
        overSoundDone = false;
        refillAt = now;
        placedMask = 0;
        dragSlot = -1;
        returnSlot = -1;
        updateFits();
        app.save();
        app.sfx.play(Sfx.REFILL, 1f, 0.6f);
    }

    @Override
    boolean back() {
        if (confirmRestart) {
            confirmRestart = false;
            return true;
        }
        if (dragSlot >= 0) startReturn(android.os.SystemClock.uptimeMillis());
        app.sfx.play(Sfx.TAP);
        app.show(app.home);
        return true;
    }
}
