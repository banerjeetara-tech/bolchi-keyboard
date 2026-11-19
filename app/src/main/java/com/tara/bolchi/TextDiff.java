package com.tara.bolchi;

import java.util.ArrayList;
import java.util.List;

/**
 * Word-by-word comparison of her text and the AI's answer (grammar fix, rewrite, tone), so the keyboard can show WHAT changed:
 * new / changed words in the answer, removed / replaced words in her text. Pure Java.
 */
final class TextDiff {
    final int[] aStart, aEnd, bStart, bEnd;      // character range of every word of the old text (a) and the new text (b)
    final boolean[] aChanged, bChanged;          // true = removed from a / added or changed in b
    final int removed, added;

    private TextDiff(int[] as, int[] ae, int[] bs, int[] be, boolean[] ac, boolean[] bc) {
        aStart = as; aEnd = ae; bStart = bs; bEnd = be; aChanged = ac; bChanged = bc;
        int r = 0, a = 0;
        for (boolean x : ac) if (x) r++;
        for (boolean x : bc) if (x) a++;
        removed = r; added = a;
    }

    private static void words(String s, List<int[]> out) {
        int i = 0, n = s.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(s.charAt(i))) i++;
            int st = i;
            while (i < n && !Character.isWhitespace(s.charAt(i))) i++;
            if (i > st) out.add(new int[]{st, i});
        }
    }

    /** Null when the texts are too long to compare quickly. */
    static TextDiff compare(String a, String b) {
        List<int[]> wa = new ArrayList<>(), wb = new ArrayList<>();
        words(a, wa); words(b, wb);
        int n = wa.size(), m = wb.size();
        if (n == 0 || m == 0 || (long) n * m > 360000L) return null;
        String[] ta = new String[n], tb = new String[m];
        for (int i = 0; i < n; i++) ta[i] = a.substring(wa.get(i)[0], wa.get(i)[1]);
        for (int j = 0; j < m; j++) tb[j] = b.substring(wb.get(j)[0], wb.get(j)[1]);
        int[][] L = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--)
            for (int j = m - 1; j >= 0; j--)
                L[i][j] = ta[i].equals(tb[j]) ? L[i + 1][j + 1] + 1 : Math.max(L[i + 1][j], L[i][j + 1]);
        boolean[] ac = new boolean[n], bc = new boolean[m];
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (ta[i].equals(tb[j])) { i++; j++; }
            else if (L[i + 1][j] >= L[i][j + 1]) ac[i++] = true;
            else bc[j++] = true;
        }
        while (i < n) ac[i++] = true;
        while (j < m) bc[j++] = true;
        int[] as = new int[n], ae = new int[n], bs = new int[m], be = new int[m];
        for (int k = 0; k < n; k++) { as[k] = wa.get(k)[0]; ae[k] = wa.get(k)[1]; }
        for (int k = 0; k < m; k++) { bs[k] = wb.get(k)[0]; be[k] = wb.get(k)[1]; }
        return new TextDiff(as, ae, bs, be, ac, bc);
    }
}
