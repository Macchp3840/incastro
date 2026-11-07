package it.incastro;

import java.util.Arrays;
import java.util.Random;

/**
 * Logica pura del gioco, senza dipendenze Android (si testa sulla JVM).
 * La griglia 8x8 è una bitboard: il bit i corrisponde alla cella (i / 8, i % 8).
 */
public final class Engine {
    public static final int N = 8;
    public static final int COLORS = 7;
    /** Bonus quando una mossa svuota la griglia, dato solo dopo i primi 5 turni (sotto il 6° turno niente bonus). */
    public static final int BOARD_CLEAR_BONUS = 300;
    /**
     * Piazzamenti senza linee concessi prima che la combo si azzeri: 3 più i pezzi rimasti
     * nel vassoio dopo l'ultima mossa con linee (cioè il resto del vassoio più un vassoio intero).
     */
    public static final int COMBO_GRACE = 3;
    /** Il generatore pesca uniformemente tra i pezzi 2..30 del catalogo. */
    static final int POOL_FIRST = 2, POOL_LAST = 30;
    /** Tempo massimo per cercare una terna piazzabile (100 ms). */
    private static final long SEARCH_NS = 100_000_000L;

    static final long[] ROW = new long[N];
    static final long[] COL = new long[N];

    static {
        for (int i = 0; i < N; i++) {
            ROW[i] = 0xFFL << (i * N);
            long m = 0;
            for (int r = 0; r < N; r++) m |= 1L << (r * N + i);
            COL[i] = m;
        }
    }

    public static final class Shape {
        public final int w, h, size;
        /** Maschera con l'angolo in alto a sinistra in (0,0). */
        public final long mask;
        /** Maschere di tutte le posizioni che stanno dentro la griglia. */
        final long[] spots;
        private final boolean[] filled;

        Shape(String rows) {
            String[] lines = rows.split("/");
            h = lines.length;
            w = lines[0].length();
            filled = new boolean[w * h];
            long m = 0;
            int n = 0;
            for (int r = 0; r < h; r++) {
                for (int c = 0; c < w; c++) {
                    if (lines[r].charAt(c) == '#') {
                        filled[r * w + c] = true;
                        m |= 1L << (r * N + c);
                        n++;
                    }
                }
            }
            mask = m;
            size = n;
            spots = new long[(N - h + 1) * (N - w + 1)];
            int k = 0;
            for (int r = 0; r + h <= N; r++) {
                for (int c = 0; c + w <= N; c++) spots[k++] = m << (r * N + c);
            }
        }

        public boolean at(int r, int c) {
            return filled[r * w + c];
        }
    }

    /**
     * Catalogo dei pezzi: SHAPES[k] è il pezzo numero k+1. Righe separate da '/'.
     */
    public static final Shape[] SHAPES = {
        new Shape("#"), new Shape("#/#"), new Shape("##"), new Shape("#/#/#"),
        new Shape("###"), new Shape("##/#."), new Shape("#/#/#/#"), new Shape("#../###"),
        new Shape("##/##"), new Shape(".#./###"), new Shape("#####"), new Shape("###/..#/..#"),
        new Shape("###/###/###"), new Shape(".##/##."), new Shape("##/.#"), new Shape("#./##/.#"),
        new Shape("####"), new Shape("##./.##"), new Shape(".#/##/#."), new Shape("#./##/#."),
        new Shape("###/#../#.."), new Shape("#/#/#/#/#"), new Shape("#../#../###"), new Shape("..#/..#/###"),
        new Shape(".#/##/.#"), new Shape("###/.#."), new Shape("#./##"), new Shape(".#/##"),
        new Shape(".#/.#/##"), new Shape("###/..#"), new Shape("##/#./#."), new Shape("##/.#/.#"),
        new Shape("..#/###"), new Shape("###/#.."), new Shape("###/###"), new Shape("##/##/##"),
        new Shape("#./.#"), new Shape(".#/#."), new Shape("#../.#./..#"), new Shape("..#/.#./#.."),
        new Shape("..#/.#./#.."), new Shape("#./#./##"), new Shape("#../###/#.."), new Shape("#.#/###"),
        new Shape("##/.#/##"), new Shape("###/#.#"), new Shape("##/#./##"), new Shape(".#./.#./###"),
        new Shape("..#/###/..#"), new Shape("###/.#./.#."), new Shape(".#./###/.#."),
    };

    /** Esito di un piazzamento, usato dalla vista per animazioni e testi. */
    public static final class Move {
        public int shape, color, r, c, lines, points, combo;
        public long placedMask, clearedMask;
        public final int[] clearedColors = new int[N * N];
        public int clearBonus;
        public boolean boardCleared, refilled, gameOver;
    }

    /** 0 = vuota, 1..COLORS = colore del blocco. */
    public final int[] cells = new int[N * N];
    public long board;
    /** Indice in SHAPES, -1 se lo slot è già stato usato. */
    public final int[] trayShape = {-1, -1, -1};
    public final int[] trayColor = new int[3];
    public long score, best, bestBefore;
    /** combo = continuousEliminateTimes, movesWithoutClear = unEliminateTimes, round = terne date finora. */
    public int combo, movesWithoutClear, round, comboGrace = COMBO_GRACE;
    public boolean over;

    private final Random rnd;

    public Engine(Random rnd) {
        this.rnd = rnd;
    }

    public void newGame() {
        Arrays.fill(cells, 0);
        board = 0;
        score = 0;
        combo = 0;
        movesWithoutClear = 0;
        comboGrace = COMBO_GRACE;
        round = 0;
        bestBefore = best;
        Arrays.fill(trayShape, -1);
        refill();
        over = !anyMoveLeft();
    }

    public static long fullMask(long b) {
        long m = 0;
        for (int i = 0; i < N; i++) {
            if ((b & ROW[i]) == ROW[i]) m |= ROW[i];
            if ((b & COL[i]) == COL[i]) m |= COL[i];
        }
        return m;
    }

    public static int countLines(long b) {
        int n = 0;
        for (int i = 0; i < N; i++) {
            if ((b & ROW[i]) == ROW[i]) n++;
            if ((b & COL[i]) == COL[i]) n++;
        }
        return n;
    }

    public long maskAt(int shape, int r, int c) {
        return SHAPES[shape].mask << (r * N + c);
    }

    public boolean canPlace(int shape, int r, int c) {
        Shape sh = SHAPES[shape];
        if (r < 0 || c < 0 || r + sh.h > N || c + sh.w > N) return false;
        return (board & maskAt(shape, r, c)) == 0;
    }

    public static boolean fits(long b, int shape) {
        for (long m : SHAPES[shape].spots) if ((b & m) == 0) return true;
        return false;
    }

    public boolean anyMoveLeft() {
        for (int s : trayShape) if (s >= 0 && fits(board, s)) return true;
        return false;
    }

    /**
     * Piazza il pezzo dello slot con l'angolo in alto a sinistra in (r, c).
     * Righe e colonne piene si svuotano insieme: un incrocio riga+colonna vale 2 linee.
     * Punteggio: celle piazzate + combo (1 linea: 10 x combo;
     * n linee: 10 x combo x n x (n-1)) + 300 se la griglia si svuota dopo il 5° turno.
     */
    public Move place(int slot, int r, int c) {
        if (over || slot < 0 || slot > 2) return null;
        int s = trayShape[slot];
        if (s < 0 || !canPlace(s, r, c)) return null;
        Shape sh = SHAPES[s];
        Move mv = new Move();
        mv.shape = s;
        mv.color = trayColor[slot];
        mv.r = r;
        mv.c = c;
        long m = maskAt(s, r, c);
        mv.placedMask = m;
        board |= m;
        for (int i = 0; i < N * N; i++) if ((m >>> i & 1) != 0) cells[i] = mv.color;

        long full = fullMask(board);
        int lines = countLines(board);
        int points = sh.size;
        if (lines > 0) {
            movesWithoutClear = 0;
            combo++;
            comboGrace = COMBO_GRACE + restAfter(slot);
            points += lineScore(lines, combo);
            for (int i = 0; i < N * N; i++) {
                if ((full >>> i & 1) != 0) {
                    mv.clearedColors[i] = cells[i];
                    cells[i] = 0;
                }
            }
            board &= ~full;
            if (board == 0 && round > 5) {
                mv.clearBonus = BOARD_CLEAR_BONUS;
                points += BOARD_CLEAR_BONUS;
            }
            mv.boardCleared = board == 0;
        } else if (++movesWithoutClear >= comboGrace) {
            combo = 0;
        }
        mv.lines = lines;
        mv.clearedMask = full;
        mv.points = points;
        mv.combo = combo;
        score += points;
        if (score > best) best = score;

        trayShape[slot] = -1;
        if (trayShape[0] < 0 && trayShape[1] < 0 && trayShape[2] < 0) {
            refill();
            mv.refilled = true;
        }
        over = !anyMoveLeft();
        mv.gameOver = over;
        return mv;
    }

    private int restAfter(int slot) {
        int n = 0;
        for (int j = 0; j < 3; j++) if (j != slot && trayShape[j] >= 0) n++;
        return n;
    }

    static int lineScore(int lines, int combo) {
        return lines == 1 ? 10 * combo : 10 * combo * lines * (lines - 1);
    }

    /**
     * Nuova terna, sempre onesta: 3 pezzi uniformi tra il 2 e il 30 (con ripetizioni), accettati solo se si possono piazzare
     * in quell'ordine, svuotando le linee tra un pezzo e l'altro. Se in 100 ms non si trova,
     * dà [1, un pezzo che entra, 1] o [1, 1, 1].
     */
    void refill() {
        round++;
        int[] chosen = null;
        long t0 = System.nanoTime();
        int span = POOL_LAST - POOL_FIRST + 1;
        while (chosen == null && System.nanoTime() - t0 <= SEARCH_NS) {
            int[] tri = new int[3];
            for (int i = 0; i < 3; i++) tri[i] = POOL_FIRST - 1 + rnd.nextInt(span);
            if (placeableInOrder(board, tri)) chosen = tri;
        }
        if (chosen == null) {
            int[] fit = new int[span];
            int n = 0;
            for (int s = POOL_FIRST - 1; s <= POOL_LAST - 1; s++) if (fits(board, s)) fit[n++] = s;
            chosen = new int[]{0, n > 0 ? fit[rnd.nextInt(n)] : 0, 0};
        }
        int[] colors = {1, 2, 3, 4, 5, 6, 7};
        for (int i = 0; i < 3; i++) {
            int j = i + rnd.nextInt(COLORS - i);
            int tmp = colors[i];
            colors[i] = colors[j];
            colors[j] = tmp;
            trayShape[i] = chosen[i];
            trayColor[i] = colors[i];
        }
    }

    /** Il primo pezzo in qualche posizione, poi il secondo, poi il terzo deve entrare (linee svuotate ogni volta). */
    static boolean placeableInOrder(long b, int[] tri) {
        for (long m1 : SHAPES[tri[0]].spots) {
            if ((b & m1) != 0) continue;
            long b1 = b | m1;
            b1 &= ~fullMask(b1);
            for (long m2 : SHAPES[tri[1]].spots) {
                if ((b1 & m2) != 0) continue;
                long b2 = b1 | m2;
                b2 &= ~fullMask(b2);
                if (fits(b2, tri[2])) return true;
            }
        }
        return false;
    }

    public String save() {
        StringBuilder sb = new StringBuilder("2;");
        for (int v : cells) sb.append((char) ('0' + v));
        sb.append(';').append(trayShape[0]).append(',').append(trayShape[1]).append(',').append(trayShape[2]);
        sb.append(';').append(trayColor[0]).append(',').append(trayColor[1]).append(',').append(trayColor[2]);
        sb.append(';').append(score).append(';').append(combo).append(';').append(movesWithoutClear);
        sb.append(';').append(bestBefore).append(';').append(round).append(';').append(comboGrace);
        return sb.toString();
    }

    public boolean load(String s) {
        try {
            String[] p = s.split(";");
            if ((p.length != 9 && p.length != 10) || !p[0].equals("2") || p[1].length() != N * N) return false;
            int[] nc = new int[N * N];
            long nb = 0;
            for (int i = 0; i < N * N; i++) {
                int v = p[1].charAt(i) - '0';
                if (v < 0 || v > COLORS) return false;
                nc[i] = v;
                if (v > 0) nb |= 1L << i;
            }
            String[] ts = p[2].split(","), tc = p[3].split(",");
            int[] nts = new int[3], ntc = new int[3];
            for (int i = 0; i < 3; i++) {
                nts[i] = Integer.parseInt(ts[i]);
                ntc[i] = Integer.parseInt(tc[i]);
                if (nts[i] < -1 || nts[i] >= SHAPES.length || ntc[i] < 1 || ntc[i] > COLORS) return false;
            }
            System.arraycopy(nc, 0, cells, 0, N * N);
            board = nb;
            System.arraycopy(nts, 0, trayShape, 0, 3);
            System.arraycopy(ntc, 0, trayColor, 0, 3);
            score = Long.parseLong(p[4]);
            combo = Integer.parseInt(p[5]);
            movesWithoutClear = Integer.parseInt(p[6]);
            bestBefore = Long.parseLong(p[7]);
            round = Integer.parseInt(p[8]);
            comboGrace = p.length == 10 ? Integer.parseInt(p[9]) : COMBO_GRACE;
            if (score > best) best = score;
            if (trayShape[0] < 0 && trayShape[1] < 0 && trayShape[2] < 0) refill();
            over = !anyMoveLeft();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
