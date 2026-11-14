package com.tara.bolchi;

/** A sum typed without spaces ("250*12", "1200+350-50=", "45x3", "200*15%") and its answer. Pure Java. */
public final class Calc {
    private Calc() { }

    private static final java.util.regex.Pattern TAIL = java.util.regex.Pattern.compile(
            "(?<![A-Za-z0-9.,])\\d[\\d.,]*%?(?:\\s*[-+*/xX\u00D7\u00F7]\\s*\\d[\\d.,]*%?)+\\s*=?\\s*$");

    /** The sum that ends this text ("total 20+30" gives "20+30", "20 + 30 =" too), or null. For text that was pasted, not typed. */
    public static String tail(CharSequence before) {
        if (before == null || before.length() < 3) return null;
        java.util.regex.Matcher m = TAIL.matcher(before);
        return m.find() ? m.group().trim() : null;
    }

    /** The answer as text ("3000", "12.5"), or null when this is not a sum. */
    public static String eval(String t) {
        if (t == null) return null;
        String s = t.replace(" ", "").trim();
        if (s.endsWith("=")) s = s.substring(0, s.length() - 1);
        // a date (14/11/2026, 14-11-26) or a phone number with dashes is not a sum
        if (s.matches("\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}")) return null;
        if (s.matches("\\d+(-\\d+)+") && (s.matches(".*\\d{5,}.*") || s.indexOf('-') != s.lastIndexOf('-'))) return null;
        if (s.matches("0\\d+[-/].*")) return null;
        if (s.length() < 3 || s.length() > 40) return null;
        double[] num = new double[20];
        char[] op = new char[20];
        int n = 0, i = 0, len = s.length();
        while (i < len) {
            int st = i;
            while (i < len && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.' || s.charAt(i) == ',')) i++;
            if (i == st || n >= 19) return null;
            double v;
            try { v = Double.parseDouble(s.substring(st, i).replace(",", "")); } catch (Exception e) { return null; }
            if (i < len && s.charAt(i) == '%') { v = v / 100.0; i++; }
            num[n] = v;
            if (i == len) { n++; break; }
            char c = s.charAt(i++);
            if (c == 'x' || c == 'X' || c == '×') c = '*';
            if (c == '÷') c = '/';
            if (c == '−') c = '-';
            if (c != '+' && c != '-' && c != '*' && c != '/') return null;
            if (i == len) return null;                      // ends on a sign: not finished yet
            op[n++] = c;
        }
        if (n < 2) return null;
        // times and divide first
        double[] a = new double[n];
        char[] o = new char[n];
        int m = 0;
        a[0] = num[0];
        for (int k = 1; k < n; k++) {
            char c = op[k - 1];
            if (c == '*') a[m] *= num[k];
            else if (c == '/') { if (num[k] == 0) return null; a[m] /= num[k]; }
            else { o[m] = c; a[++m] = num[k]; }
        }
        double r = a[0];
        for (int k = 1; k <= m; k++) r = o[k - 1] == '+' ? r + a[k] : r - a[k];
        if (Double.isNaN(r) || Double.isInfinite(r) || Math.abs(r) > 1e15) return null;
        double rd = Math.round(r * 100.0) / 100.0;
        if (rd == Math.rint(rd)) return String.valueOf((long) rd);
        String out = String.format(java.util.Locale.ENGLISH, "%.2f", rd);
        return out.endsWith("0") ? out.substring(0, out.length() - 1) : out;
    }
}
