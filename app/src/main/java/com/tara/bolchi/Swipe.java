package com.tara.bolchi;

import java.util.ArrayList;
import java.util.List;

/**
 * Gesture typing: slide a finger over the letters of a word and lift.
 * Compares the finger's path with the ideal path of every likely word (pure Java).
 */
public final class Swipe {
    private Swipe() { }

    private static final int N = 28;

    /** Spreads a path into N evenly spaced points. */
    private static void resample(float[] x, float[] y, int n, float[] ox, float[] oy) {
        float total = 0;
        for (int i = 1; i < n; i++) total += (float) Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]);
        if (total <= 0) { for (int k = 0; k < N; k++) { ox[k] = x[0]; oy[k] = y[0]; } return; }
        float step = total / (N - 1), walked = 0;
        int seg = 1, k = 1;
        ox[0] = x[0]; oy[0] = y[0];
        float px = x[0], py = y[0];
        while (k < N - 1 && seg < n) {
            float d = (float) Math.hypot(x[seg] - px, y[seg] - py);
            if (walked + d >= step && d > 0) {
                float f = (step - walked) / d;
                px += (x[seg] - px) * f; py += (y[seg] - py) * f;
                ox[k] = px; oy[k] = py; k++;
                walked = 0;
            } else { walked += d; px = x[seg]; py = y[seg]; seg++; }
        }
        for (; k < N; k++) { ox[k] = x[n - 1]; oy[k] = y[n - 1]; }
    }

    /**
     * @param x,y  finger path (n points)
     * @param cx,cy centres of the keys a..z
     * @param keyW width of one key
     * @return best words first (lower case), at most max
     */
    public static List<String> decode(float[] x, float[] y, int n, float[] cx, float[] cy, float keyW, int max) {
        List<String> out = new ArrayList<>();
        if (n < 2) return out;
        float[] ux = new float[N], uy = new float[N], ix = new float[N], iy = new float[N];
        resample(x, y, n, ux, uy);
        float userLen = 0;
        for (int i = 1; i < n; i++) userLen += (float) Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]);
        float reach = keyW * 1.15f;
        float sx = x[0], sy = y[0], ex = x[n - 1], ey = y[n - 1];
        boolean[] startOk = new boolean[26], endOk = new boolean[26];
        for (int c = 0; c < 26; c++) {
            startOk[c] = Math.hypot(cx[c] - sx, cy[c] - sy) < reach;
            endOk[c] = Math.hypot(cx[c] - ex, cy[c] - ey) < reach;
        }
        String[] all = Words.all();
        float[] px = new float[24], py = new float[24];
        String[] best = new String[max];
        double[] bestS = new double[max];
        for (int i = 0; i < max; i++) bestS[i] = Double.MAX_VALUE;
        for (int r = 0; r < all.length; r++) {
            String w = all[r];
            int len = w.length();
            if (len < 2 || len > 20) continue;
            int f = w.charAt(0) - 'a', l = w.charAt(len - 1) - 'a';
            if (f < 0 || f > 25 || l < 0 || l > 25 || !startOk[f] || !endOk[l]) continue;
            int m = 0, prev = -1;
            boolean bad = false;
            for (int k = 0; k < len; k++) {
                int c = w.charAt(k) - 'a';
                if (c < 0 || c > 25) { if (w.charAt(k) == '\'') continue; bad = true; break; }
                if (c == prev) continue;
                px[m] = cx[c]; py[m] = cy[c]; m++; prev = c;
            }
            if (bad || m < 2) continue;
            float ideal = 0;
            for (int k = 1; k < m; k++) ideal += (float) Math.hypot(px[k] - px[k - 1], py[k] - py[k - 1]);
            if (ideal > userLen * 1.5f + keyW || ideal < userLen * 0.55f - keyW) continue;
            resample(px, py, m, ix, iy);
            double sum = 0;
            for (int k = 0; k < N; k++) sum += Math.hypot(ix[k] - ux[k], iy[k] - uy[k]);
            double score = sum / N / keyW + 0.035 * Math.log(r + 2);
            if (score >= bestS[max - 1]) continue;
            int at = max - 1;
            while (at > 0 && bestS[at - 1] > score) { bestS[at] = bestS[at - 1]; best[at] = best[at - 1]; at--; }
            bestS[at] = score; best[at] = w;
        }
        for (int i = 0; i < max; i++) if (best[i] != null && bestS[i] < 1.2) out.add(best[i]);
        return out;
    }
}
