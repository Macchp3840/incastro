package it.incastro;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerArray;

/** Effetti sonori sintetizzati al primo avvio: nel pacchetto non c'è nessun file audio. */
final class Sfx {
    static final int TAP = 0, PICK = 1, PLACE = 2, CLEAR = 3, PERFECT = 4, BAD = 5, OVER = 6, REFILL = 7;
    private static final String[] NAMES = {"tap", "pick", "place", "clear", "perfect", "bad", "over", "refill"};
    private static final int RATE = 44100;

    private final SoundPool pool;
    private final AtomicIntegerArray ids = new AtomicIntegerArray(NAMES.length);
    private final Set<Integer> loaded = ConcurrentHashMap.newKeySet();
    volatile boolean enabled = true;

    Sfx(Context ctx) {
        pool = new SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .build();
        pool.setOnLoadCompleteListener((p, sampleId, status) -> {
            if (status == 0) loaded.add(sampleId);
        });
        File dir = new File(ctx.getCacheDir(), "sfx1");
        new Thread(() -> {
            dir.mkdirs();
            for (int i = 0; i < NAMES.length; i++) {
                File f = new File(dir, NAMES[i] + ".wav");
                try {
                    if (f.length() == 0) writeWav(f, synth(i));
                    ids.set(i, pool.load(f.getPath(), 1));
                } catch (IOException ignored) {
                    // Senza file il suono resta muto: il gioco funziona lo stesso.
                }
            }
        }, "sfx").start();
    }

    void play(int sound) {
        play(sound, 1f, 0.8f);
    }

    void play(int sound, float rate, float volume) {
        if (!enabled) return;
        int id = ids.get(sound);
        if (id != 0 && loaded.contains(id)) pool.play(id, volume, volume, 1, 0, Math.max(0.5f, Math.min(2f, rate)));
    }

    void release() {
        pool.release();
    }

    private static float[] synth(int sound) {
        float[] o;
        switch (sound) {
            case TAP:
                o = new float[(int) (RATE * 0.06)];
                note(o, 0, 1250, 0.06f, 70, 0.25f, 0);
                break;
            case PICK:
                o = new float[(int) (RATE * 0.07)];
                sweep(o, 0, 600, 900, 0.07f, 45, 0.22f);
                break;
            case PLACE:
                o = new float[(int) (RATE * 0.14)];
                sweep(o, 0, 260, 140, 0.12f, 30, 0.55f);
                noise(o, 0, 0.03f, 160, 0.18f);
                break;
            case CLEAR:
                o = new float[(int) (RATE * 0.55)];
                note(o, 0.00f, 1046.5f, 0.40f, 9, 0.28f, 0.25f);
                note(o, 0.05f, 1318.5f, 0.40f, 9, 0.28f, 0.25f);
                note(o, 0.10f, 1568.0f, 0.40f, 9, 0.28f, 0.25f);
                note(o, 0.12f, 3136.0f, 0.30f, 14, 0.08f, 0);
                break;
            case PERFECT:
                o = new float[(int) (RATE * 1.1)];
                float[] arp = {523.25f, 659.25f, 783.99f, 1046.5f, 1318.5f, 1568.0f};
                for (int i = 0; i < arp.length; i++) note(o, i * 0.06f, arp[i], 0.7f, 5, 0.22f, 0.3f);
                note(o, 0.36f, 2093.0f, 0.7f, 6, 0.07f, 0);
                note(o, 0.40f, 2637.0f, 0.65f, 6, 0.06f, 0);
                break;
            case BAD:
                o = new float[(int) (RATE * 0.12)];
                sweep(o, 0, 180, 120, 0.12f, 35, 0.4f);
                break;
            case OVER:
                o = new float[(int) (RATE * 0.9)];
                note(o, 0.0f, 392.0f, 0.45f, 6, 0.3f, 0.2f);
                note(o, 0.2f, 329.63f, 0.45f, 6, 0.3f, 0.2f);
                note(o, 0.4f, 261.63f, 0.5f, 5, 0.3f, 0.2f);
                break;
            default:
                o = new float[(int) (RATE * 0.16)];
                sweep(o, 0, 500, 1000, 0.08f, 40, 0.15f);
                sweep(o, 0.07f, 700, 1400, 0.08f, 40, 0.12f);
                break;
        }
        return o;
    }

    /** Inviluppo: attacco di 4 ms, decadimento esponenziale, chiusura di 10 ms contro i clic. */
    private static float env(float t, float dur, float decay) {
        return Math.min(1f, t / 0.004f) * (float) Math.exp(-decay * t) * Math.min(1f, (dur - t) / 0.01f);
    }

    private static void note(float[] o, float start, float freq, float dur, float decay, float amp, float harmonic) {
        int s0 = (int) (start * RATE), n = (int) (dur * RATE);
        for (int i = 0; i < n && s0 + i < o.length; i++) {
            float t = i / (float) RATE;
            double ph = 2 * Math.PI * freq * t;
            o[s0 + i] += amp * env(t, dur, decay) * (float) (Math.sin(ph) + harmonic * Math.sin(2 * ph));
        }
    }

    private static void sweep(float[] o, float start, float f0, float f1, float dur, float decay, float amp) {
        int s0 = (int) (start * RATE), n = (int) (dur * RATE);
        double ph = 0;
        for (int i = 0; i < n && s0 + i < o.length; i++) {
            float t = i / (float) RATE;
            ph += 2 * Math.PI * (f0 + (f1 - f0) * (t / dur)) / RATE;
            o[s0 + i] += amp * env(t, dur, decay) * (float) Math.sin(ph);
        }
    }

    private static void noise(float[] o, float start, float dur, float decay, float amp) {
        int s0 = (int) (start * RATE), n = (int) (dur * RATE);
        long seed = 0x9E3779B97F4A7C15L;
        float lp = 0;
        for (int i = 0; i < n && s0 + i < o.length; i++) {
            seed ^= seed << 13;
            seed ^= seed >>> 7;
            seed ^= seed << 17;
            lp += 0.35f * (((seed >>> 40) / (float) (1 << 23)) - 1f - lp);
            o[s0 + i] += amp * env(i / (float) RATE, dur, decay) * lp;
        }
    }

    private static void writeWav(File f, float[] samples) throws IOException {
        float peak = 0;
        for (float v : samples) peak = Math.max(peak, Math.abs(v));
        float k = peak > 0.95f ? 0.95f / peak : 1f;
        int dataLen = samples.length * 2;
        byte[] out = new byte[44 + dataLen];
        put(out, 0, "RIFF");
        le32(out, 4, 36 + dataLen);
        put(out, 8, "WAVEfmt ");
        le32(out, 16, 16);
        le16(out, 20, 1);
        le16(out, 22, 1);
        le32(out, 24, RATE);
        le32(out, 28, RATE * 2);
        le16(out, 32, 2);
        le16(out, 34, 16);
        put(out, 36, "data");
        le32(out, 40, dataLen);
        for (int i = 0; i < samples.length; i++) le16(out, 44 + i * 2, (int) (samples[i] * k * 32767));
        File tmp = new File(f.getPath() + ".tmp");
        try (OutputStream os = new FileOutputStream(tmp)) {
            os.write(out);
        }
        if (!tmp.renameTo(f)) throw new IOException("rename " + f);
    }

    private static void put(byte[] b, int at, String s) {
        for (int i = 0; i < s.length(); i++) b[at + i] = (byte) s.charAt(i);
    }

    private static void le16(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >> 8);
    }

    private static void le32(byte[] b, int at, int v) {
        le16(b, at, v);
        le16(b, at + 2, v >> 16);
    }
}
