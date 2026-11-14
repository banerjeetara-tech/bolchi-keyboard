package com.tara.bolchi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Voice self-correction: "meet at five no sorry six" becomes "meet at six".
 * Simple rules, works inside one spoken stretch (until a pause).
 */
public final class Spoken {
    private Spoken() { }

    /** Phrases that mean "I said it wrong, here is the right one". Longer ones first. */
    private static final String[][] MARKS = {
            {"sorry", "i", "mean"}, {"no", "i", "mean"}, {"no", "sorry"}, {"oh", "sorry"}, {"no", "wait"},
            {"i", "mean"}, {"no", "no"},
            {"না", "সরি"}, {"সরি", "না"}, {"না", "না"},
            {"नहीं", "सॉरी"}, {"सॉरी", "नहीं"}, {"नहीं", "नहीं"}};
    /** Phrases that throw away everything said before them. */
    private static final String[][] WIPES = {{"scratch", "that"}, {"delete", "that"}, {"cancel", "that"}};

    private static String norm(String t) {
        return t.toLowerCase(Locale.ROOT).replaceAll("[,.!?;:।]", "");
    }

    private static boolean at(List<String> n, int i, String[] m) {
        if (i + m.length > n.size()) return false;
        for (int k = 0; k < m.length; k++) if (!n.get(i + k).equals(m[k])) return false;
        return true;
    }

    public static String fix(String heard) {
        if (heard == null) return "";
        List<String> tok = new ArrayList<>(Arrays.asList(heard.trim().split("\\s+")));
        // stammer: a broken start of the next word ("wh what's") is dropped
        for (int i = tok.size() - 2; i >= 0; i--) {
            String a = norm(tok.get(i)), b = norm(tok.get(i + 1));
            if (a.length() >= 1 && a.length() <= 3 && b.length() > a.length() && b.startsWith(a) && !Words.common(a)) tok.remove(i);
        }
        for (int round = 0; round < 6; round++) {
            List<String> n = new ArrayList<>();
            for (String t : tok) n.add(norm(t));
            boolean changed = false;
            search:
            for (int i = 0; i < tok.size(); i++) {
                for (String[] m : WIPES) {
                    if (at(n, i, m)) {
                        tok = new ArrayList<>(tok.subList(i + m.length, tok.size()));
                        changed = true;
                        break search;
                    }
                }
                if (i == 0) continue;
                for (String[] m : MARKS) {
                    if (!at(n, i, m) || i + m.length >= tok.size()) continue;
                    String first = n.get(i + m.length);
                    int cut = i - 1;                       // default: swap the last word
                    for (int j = i - 1; j >= Math.max(0, i - 8); j--) {
                        if (n.get(j).equals(first)) { cut = j; break; }   // she restarted the phrase
                    }
                    List<String> out = new ArrayList<>(tok.subList(0, cut));
                    out.addAll(tok.subList(i + m.length, tok.size()));
                    tok = out;
                    changed = true;
                    break search;
                }
            }
            if (!changed) break;
        }
        StringBuilder b = new StringBuilder();
        for (String t : tok) { if (b.length() > 0) b.append(' '); b.append(t); }
        return b.toString();
    }

    private static final java.util.Set<String> ASKS = new java.util.HashSet<>(Arrays.asList((
            "what why how when where who whom whose which is are am was were do does did can could will would should shall "
            + "keno kothay kokhon kemon kobe kivabe kibhabe ki ke kar koto kon konta "
            + "kya kyun kyu kyon kaise kaisa kaisi kab kahan kaha kaun kitna kitne kitni "
            + "কেন কোথায় কোথায় কখন কেমন কি কী কে কত "
            + "क्या क्यों कैसे कब कहाँ कहां कौन कितना").split(" ")));
    private static final java.util.Set<String> ASK_ENDS = new java.util.HashSet<>(Arrays.asList(
            "ki", "naki", "kya", "na", "কি", "কী", "নাকি", "क्या", "ना"));
    private static final java.util.Set<String> CHEERS = new java.util.HashSet<>(Arrays.asList(
            "wow", "congratulations", "congrats", "hurray", "omg", "great", "awesome", "darun", "bah", "shabash", "wah", "badhai"));

    /**
     * Ends a spoken stretch the way it sounds: a question gets "?", a cheer gets "!", a full sentence gets a full stop
     * (the Bengali / Hindi danda in those scripts). Short bits of one or two words are left open.
     */
    public static String punctuate(String text) {
        if (text == null) return "";
        String t = text.trim();
        if (t.isEmpty()) return text;
        char last = t.charAt(t.length() - 1);
        if (".?!,;:।".indexOf(last) >= 0) return text;
        String[] w = norm(t).split("\\s+");
        if (w.length == 0) return text;
        boolean ask = ASKS.contains(w[0]) || (w.length >= 2 && ASK_ENDS.contains(w[w.length - 1]));
        // Bangla and Hindi put the question word in the middle ("tumi kemon acho"); English helpers like "is" only count at the start
        for (int i = 1; i < w.length && !ask; i++) ask = w[i].length() > 2 && w[i].charAt(0) != 'w' && w[i].charAt(0) != 'd' && w[i].charAt(0) != 's'
                && w[i].charAt(0) != 'c' && w[i].charAt(0) != 'a' && w[i].charAt(0) != 'i' && ASKS.contains(w[i]) && !w[i].equals("kar") && !w[i].equals("kon");
        if (ask) return t + "?";
        if (CHEERS.contains(w[0]) || (w.length >= 2 && w[0].equals("happy"))) return t + "!";
        if (w.length < 3) return text;
        boolean script = last >= 'ऀ' && last <= '৿';
        return t + (script ? "।" : ".");
    }
}
