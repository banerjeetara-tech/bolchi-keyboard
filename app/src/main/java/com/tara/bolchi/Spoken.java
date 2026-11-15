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
    private static final java.util.Set<String> ASKS_ANY = new java.util.HashSet<>(Arrays.asList((
            "keno kothay kokhon kemon kobe kivabe kibhabe koto kyun kyu kyon kaise kaisa kaisi kab kahan kaun kitna kitne kitni "
            + "\u0995\u09C7\u09A8 \u0995\u09CB\u09A5\u09BE\u09DF \u0995\u09CB\u09A5\u09BE\u09AF\u09BC \u0995\u0996\u09A8 \u0995\u09C7\u09AE\u09A8 \u0995\u09A4 "
            + "\u0915\u094D\u092F\u093E \u0915\u094D\u092F\u094B\u0902 \u0915\u0948\u0938\u0947 \u0915\u092C \u0915\u0939\u093E\u0901 \u0915\u0939\u093E\u0902 \u0915\u094C\u0928 \u0915\u093F\u0924\u0928\u093E").split(" ")));
    private static final java.util.Set<String> ASK_ENDS = new java.util.HashSet<>(Arrays.asList(
            "ki", "naki", "kya", "na", "কি", "কী", "নাকি", "क्या", "ना"));
    private static final java.util.Set<String> CHEERS = new java.util.HashSet<>(Arrays.asList(
            "wow", "congratulations", "congrats", "hurray", "omg", "great", "awesome", "darun", "bah", "shabash", "wah", "badhai"));

    /**
     * Ends a spoken stretch the way it sounds: a question gets "?", a cheer gets "!", a full sentence gets a full stop
     * (the Bengali / Hindi danda in those scripts). Short bits of one or two words are left open.
     */
    /** Words that are said twice on purpose. */
    private static final java.util.Set<String> TWICE_OK = new java.util.HashSet<>(Arrays.asList(
            "very", "so", "no", "bye", "ha", "had", "that", "really", "many", "long", "far", "too", "ok", "okay", "yes", "na", "hi", "bla", "blah", "knock", "more", "again"));

    /**
     * Takes out what was said twice in a row: a repeated group of two to five words ("I have I have stopped" becomes "I have stopped"),
     * and, when singleWords is true (English), a repeated single word ("the the"). Bengali and Hindi double words on purpose
     * ("aste aste", "choto choto"), so single words are left alone there.
     */
    public static String dedupe(String text, boolean singleWords) {
        if (text == null) return "";
        List<String> tok = new ArrayList<>(Arrays.asList(text.trim().split("\\s+")));
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int n = 5; n >= 1 && !changed; n--) {
                if (n == 1 && !singleWords) break;
                for (int i = 0; i + 2 * n <= tok.size(); i++) {
                    boolean same = true;
                    for (int k = 0; k < n && same; k++) same = norm(tok.get(i + k)).equals(norm(tok.get(i + n + k))) && !norm(tok.get(i + k)).isEmpty();
                    if (!same) continue;
                    if (n == 1 && (TWICE_OK.contains(norm(tok.get(i))) || norm(tok.get(i)).matches("\\d+"))) continue;
                    for (int k = 0; k < n; k++) tok.remove(i);          // the first copy goes, the second (with its punctuation) stays
                    changed = true;
                    break;
                }
            }
        }
        StringBuilder b = new StringBuilder();
        for (String t : tok) { if (b.length() > 0) b.append(' '); b.append(t); }
        return b.toString();
    }

    /**
     * She paused and then said the last words again: the new stretch starts with the words the text already ends with.
     * Returns the new stretch without that repeat (two or more words always, one word only when singleWords).
     */
    public static String dropRepeat(String before, String said, boolean singleWords) {
        if (before == null || said == null) return said;
        String[] b = before.trim().split("\\s+"), s = said.trim().split("\\s+");
        for (int n = Math.min(5, Math.min(b.length, s.length - 1)); n >= 1; n--) {
            if (n == 1 && (!singleWords || TWICE_OK.contains(norm(s[0])))) break;
            boolean same = true;
            for (int k = 0; k < n && same; k++) same = !norm(s[k]).isEmpty() && norm(s[k]).equals(norm(b[b.length - n + k]));
            if (!same) continue;
            StringBuilder o = new StringBuilder();
            for (int k = n; k < s.length; k++) { if (o.length() > 0) o.append(' '); o.append(s[k]); }
            return o.toString();
        }
        return said;
    }

    public static String punctuate(String text) { return punctuate(text, true); }

    /** Words a sentence does not end on: a pause after one of these is a pause to think, not the end. */
    private static final java.util.Set<String> OPEN = new java.util.HashSet<>(java.util.Arrays.asList((
            "and but or so to the a an of in on at for with from by because that which who if when then than as is are was were am be will would can could should "
            + "my your his her our their this these those i we you he she they it not very also just about into "
            + "ar ebong kintu je ki ke er te theke jonno karon tai jodi tokhon amar tomar tar amader "
            + "aur lekin ya ki ka ke ko se me par liye kyunki agar toh to mera tumhara uska").split(" ")));

    /** True when a stretch of speech looks like a finished sentence that should get a full stop (3+ words, no mark yet, not ending on a joining word). */
    public static boolean wantsStop(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty() || ".?!,;:।".indexOf(t.charAt(t.length() - 1)) >= 0) return false;
        String[] w = norm(t).split("\\s+");
        return w.length >= 3 && !OPEN.contains(w[w.length - 1]);
    }

    /** stop = false: only ? and ! are added; the full stop is left to the caller, who knows how long the pause really was. */
    public static String punctuate(String text, boolean stop) {
        if (text == null) return "";
        String t = text.trim();
        if (t.isEmpty()) return text;
        char last = t.charAt(t.length() - 1);
        if (".?!,;:।".indexOf(last) >= 0) return text;
        String[] w = norm(t).split("\\s+");
        if (w.length == 0) return text;
        boolean ask = ASKS.contains(w[0]) || (w.length >= 2 && ASK_ENDS.contains(w[w.length - 1]));
        // Bangla and Hindi put the question word in the middle ("tumi kemon acho"); English helpers like "is" only count at the start
        for (int i = 1; i < w.length && !ask; i++) ask = ASKS_ANY.contains(w[i]);
        if (ask) return t + "?";
        if (CHEERS.contains(w[0]) || (w.length >= 2 && w[0].equals("happy"))) return t + "!";
        if (w.length < 3 || !stop) return text;
        boolean script = last >= 'ऀ' && last <= '৿';
        return t + (script ? "।" : ".");
    }
}
