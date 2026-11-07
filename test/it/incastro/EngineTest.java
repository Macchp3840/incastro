package it.incastro;

import java.util.Random;

/** Test del motore sulla JVM: java -cp build/test it.incastro.EngineTest */
public final class EngineTest {
    private static int failures;

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAIL " + what);
        }
    }

    private static int idx(String rows) {
        Engine.Shape want = new Engine.Shape(rows);
        for (int i = 0; i < Engine.SHAPES.length; i++) {
            Engine.Shape s = Engine.SHAPES[i];
            if (s.mask == want.mask && s.w == want.w && s.h == want.h) return i;
        }
        throw new IllegalArgumentException(rows);
    }

    private static void set(Engine e, int r, int c) {
        e.cells[r * 8 + c] = 1;
        e.board |= 1L << (r * 8 + c);
    }

    private static Engine empty(long seed) {
        Engine e = new Engine(new Random(seed));
        e.newGame();
        java.util.Arrays.fill(e.cells, 0);
        e.board = 0;
        e.trayShape[0] = idx("#");
        e.trayShape[1] = idx("##");
        e.trayShape[2] = idx("##");
        e.trayColor[0] = e.trayColor[1] = e.trayColor[2] = 3;
        e.score = 0;
        e.combo = 0;
        e.movesWithoutClear = 0;
        return e;
    }

    public static void main(String[] args) {
        // Catalogo: 51 forme (SHAPES[k] = pezzo numero k+1).
        check(Engine.SHAPES.length == 51, "51 forme");
        check(idx("##/##") == 8 && idx("###/###/###") == 12 && idx("#####") == 10 && idx(".##/##.") == 13, "id del catalogo");

        // Punteggio delle linee: 1 linea 10 x combo, n linee 10 x combo x n x (n-1).
        check(Engine.lineScore(1, 1) == 10 && Engine.lineScore(1, 3) == 30 && Engine.lineScore(2, 1) == 20
            && Engine.lineScore(3, 2) == 120 && Engine.lineScore(4, 1) == 120, "lineScore");

        // Riga 0 e colonna 0 complete tranne l'angolo: il pezzo singolo chiude 2 linee e svuota la griglia.
        Engine e = empty(1);
        for (int i = 1; i < 8; i++) {
            set(e, 0, i);
            set(e, i, 0);
        }
        e.round = 1;
        Engine.Move mv = e.place(0, 0, 0);
        check(mv != null && mv.lines == 2, "incrocio = 2 linee");
        check(mv.boardCleared && e.board == 0, "griglia svuotata");
        check(mv.points == 1 + 20 && mv.clearBonus == 0, "niente bonus nei primi 5 turni: " + mv.points);
        check(e.combo == 1, "combo dopo prima linea");

        e = empty(1);
        for (int i = 1; i < 8; i++) set(e, 0, i);
        e.round = 6;
        mv = e.place(0, 0, 0);
        check(mv.points == 1 + 10 + 300 && mv.clearBonus == 300, "bonus griglia vuota dal 6° turno: " + mv.points);

        // Combo: sale a ogni mossa con linee, si azzera dopo 3 mosse di fila senza linee.
        e = empty(2);
        e.combo = 4;
        e.trayShape[0] = e.trayShape[1] = e.trayShape[2] = idx("#");
        e.place(0, 7, 7);
        e.place(1, 7, 5);
        check(e.combo == 4 && e.movesWithoutClear == 2, "combo regge 2 mosse a vuoto");
        e.place(2, 7, 3);
        check(e.combo == 0, "combo azzerata alla 3a mossa a vuoto");

        // Combo: linea fatta col primo pezzo del vassoio -> restano 2 pezzi, soglia 3 + 2 = 5.
        e = empty(8);
        for (int c = 1; c < 8; c++) set(e, 0, c);
        e.trayShape[0] = e.trayShape[1] = e.trayShape[2] = idx("#");
        e.place(0, 0, 0);
        check(e.combo == 1 && e.comboGrace == 5, "soglia combo 3 + pezzi rimasti: " + e.comboGrace);
        e.place(1, 7, 7);
        e.place(2, 7, 5);
        e.trayShape[0] = e.trayShape[1] = e.trayShape[2] = idx("#");
        e.place(0, 7, 3);
        e.place(1, 7, 1);
        check(e.combo == 1, "combo regge il resto del vassoio + 2 pezzi del successivo");
        e.place(2, 5, 5);
        check(e.combo == 0, "combo azzerata dopo un vassoio intero senza linee");

        // Limiti della griglia.
        int i4 = idx("####");
        check(e.canPlace(i4, 0, 4) && !e.canPlace(i4, 0, 5) && !e.canPlace(i4, -1, 0), "limiti");

        // Combo: la seconda linea consecutiva vale il doppio.
        e = empty(2);
        for (int c = 0; c < 7; c++) set(e, 0, c);
        for (int c = 0; c < 7; c++) set(e, 2, c);
        e.combo = 1;
        mv = e.place(0, 0, 7);
        check(mv.lines == 1 && mv.combo == 2 && mv.points == 1 + 20, "combo x2: " + mv.points);

        // fullMask confrontato con un conteggio ingenuo su griglie casuali.
        Random r = new Random(3);
        for (int t = 0; t < 20000; t++) {
            long b = r.nextLong() | r.nextLong() | r.nextLong();
            long naive = 0;
            int lines = 0;
            for (int k = 0; k < 8; k++) {
                boolean row = true, col = true;
                for (int j = 0; j < 8; j++) {
                    row &= (b >>> (k * 8 + j) & 1) != 0;
                    col &= (b >>> (j * 8 + k) & 1) != 0;
                }
                if (row) { lines++; for (int j = 0; j < 8; j++) naive |= 1L << (k * 8 + j); }
                if (col) { lines++; for (int j = 0; j < 8; j++) naive |= 1L << (j * 8 + k); }
            }
            if (naive != Engine.fullMask(b) || lines != Engine.countLines(b)) {
                check(false, "fullMask/countLines");
                break;
            }
        }

        // Salvataggio e ripristino.
        e = new Engine(new Random(4));
        e.newGame();
        e.place(0, 0, 0);
        String s = e.save();
        Engine f = new Engine(new Random(5));
        check(f.load(s) && f.save().equals(s) && f.board == e.board, "save/load");
        check(!f.load("spazzatura") && !f.load("1;abc;;;;;;"), "load rifiuta input non validi");

        check(!f.load(s.replaceFirst("^2;", "1;")), "load rifiuta i salvataggi del vecchio formato");

        // Partite simulate con mosse casuali: terne sempre dal pool 2..30 e piazzabili in ordine.
        long refills = 0, inOrder = 0, moves = 0, scoreSum = 0, maxRefillNs = 0;
        for (int g = 0; g < 400; g++) {
            Engine p = new Engine(new Random(100 + g));
            p.newGame();
            Random pr = new Random(g);
            while (!p.over && moves < 10_000_000) {
                int slot, rr, cc;
                do {
                    slot = pr.nextInt(3);
                    rr = pr.nextInt(8);
                    cc = pr.nextInt(8);
                } while (p.trayShape[slot] < 0 || !p.canPlace(p.trayShape[slot], rr, cc));
                long t0 = System.nanoTime();
                Engine.Move m = p.place(slot, rr, cc);
                long dt = System.nanoTime() - t0;
                moves++;
                if (m.refilled) {
                    refills++;
                    maxRefillNs = Math.max(maxRefillNs, dt);
                    if (Engine.placeableInOrder(p.board, p.trayShape.clone())) {
                        inOrder++;
                        int[] k = p.trayShape;
                        boolean fallback = k[0] == 0 && k[2] == 0 && k[1] <= 29;
                        check(fallback || (k[0] >= 1 && k[0] <= 29 && k[1] >= 1 && k[1] <= 29 && k[2] >= 1 && k[2] <= 29),
                            "terna fuori dal pool: " + java.util.Arrays.toString(k));
                    }
                }
            }
            scoreSum += p.score;
        }
        System.out.printf("partite 400, mosse %d, punteggio medio %d, terne piazzabili in ordine %.2f%%, refill max %.1f ms%n",
            moves, scoreSum / 400, 100.0 * inOrder / refills, maxRefillNs / 1e6);

        System.out.println(failures == 0 ? "OK" : failures + " test falliti");
        if (failures > 0) System.exit(1);
    }
}
