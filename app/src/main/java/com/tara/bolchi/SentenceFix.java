package com.tara.bolchi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Version 144 (Tara: "correction should only work in total English, while writing or speaking"; "punctuation will also work in English; for Bengali English it will understand the
 * tone and give only the appropriate punctuation").
 *
 * Looks at a whole message (typed or spoken) and does for it what auto-correct does for one word:
 *  - a sentence that is ENTIRELY English gets its spelling mistakes, short forms ("u", "plz"), "i" and sentence-start capitals put right;
 *  - a sentence with ANY Bangla / Hindi script word, any Banglish / Hinglish word (or too many words the dictionary does not know) is left exactly as it is (no letter changes);
 *  - the LAST sentence of the message, when it has no mark yet, gets the mark its meaning asks for ({@link Spoken#markFor}): in English "." "?" "!", in Banglish / Hinglish only
 *    what the tone calls for (a question word gives "?", a cheer gives "!").
 * Pure Java (needs Words to be loaded), so it can be tested on a PC.
 */
public final class SentenceFix {
    private SentenceFix() { }

    /** What a pass did. `text` is the whole new text, `cursor` where the cursor belongs in it; [from, to) of the OLD text became `insert`. */
    public static final class Result {
        public String text = "";
        public int cursor;
        public int fixed;                 // words changed (spelling, short forms, capitals)
        public boolean marked;            // an end mark was added
        public String mark = "";          // ... and which one
        public int english;               // sentences that were entirely English (and so looked at)
        public int left;                  // sentences left alone because they hold Bangla / Hindi / Banglish words
        public int from, to;              // the part of the old text that changed
        public String insert = "";        // what stands there now
        public final List<String> pairs = new ArrayList<>();      // "teh>the", for the Bolchi app screen
        public boolean changed() { return fixed > 0 || marked; }
    }

    private static final class Tok {
        int s, e;
        String core;          // letters (and inner apostrophes), as written, with a curly apostrophe turned into '
        boolean plain;        // an ordinary word: counts for "is this English?" and may be changed
        boolean edit;         // may be changed (plain, and no apostrophe / curly quote)
    }

    private static final class Edit {
        final int s, e;
        final String txt;
        Edit(int s, int e, String txt) { this.s = s; this.e = e; this.txt = txt; }
    }

    private static final Set<String> ABBR = new HashSet<>(Arrays.asList(("mr mrs ms dr prof vs st approx eg ie ref fig sr jr rs pvt ltd inc co corp dept govt asst mgr etc est tel ph "
            + "mon tue wed thu fri sat sun jan feb mar apr jun jul aug sep sept oct nov dec min max hon rev col capt cf viz nos").split(" ")));
    private static final java.util.regex.Pattern INITIALISM = java.util.regex.Pattern.compile("([a-z]\\.)+[a-z]");

    private static boolean isTerm(char c) { return c == '.' || c == '!' || c == '?' || c == '।' || c == '॥' || c == '…'; }

    private static boolean isCloser(char c) { return c == '"' || c == '\'' || c == ')' || c == ']' || c == '}' || c == '”' || c == '’'; }

    private static boolean isAsciiLetter(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'); }

    /** The word (letters, digits, dots, inner apostrophes) that ends right before index i, as written. */
    private static String rawWordBefore(String t, int i) {
        int j = i;
        while (j > 0) {
            char c = t.charAt(j - 1);
            if (Character.isLetterOrDigit(c) || c == '.') j--;
            else if ((c == '\'' || c == '\u2019') && j >= 2 && j < i && Character.isLetter(t.charAt(j - 2)) && Character.isLetter(t.charAt(j))) j--;      // can't, don't
            else break;
        }
        return t.substring(j, i);
    }

    /** True when the mark at index i really ends a sentence (and is not a decimal point, an address, "e.g." or "Mr."). */
    private static boolean boundaryAt(String t, int i) {
        char c = t.charAt(i);
        int n = t.length();
        int j = i + 1;
        while (j < n && (isTerm(t.charAt(j)) || isCloser(t.charAt(j)))) j++;
        if (j < n && !Character.isWhitespace(t.charAt(j))) return false;               // "3.5", "gmail.com", "word.Next"
        if (c == '.') {
            String praw = rawWordBefore(t, i);
            String p = praw.toLowerCase(Locale.ROOT);
            if (p.isEmpty()) return true;
            if (INITIALISM.matcher(p).matches()) return false;                         // "e.g.", "a.m."
            if (ABBR.contains(p)) return false;                                        // "Mr.", "Dr.", "Pvt.", "Rs.", "etc."
            if (praw.length() == 1 && Character.isUpperCase(praw.charAt(0)) && !p.equals("i") && !p.equals("a")) return false;     // an initial: "A. Kumar" (not "u." or "k.")
        }
        return true;
    }

    /** The sentences of a text as [start, end) pairs: end is before the mark that closes it; blank stretches are skipped. A line break always ends one. */
    private static List<int[]> sentences(String t) {
        List<int[]> out = new ArrayList<>();
        int n = t.length(), start = -1, i = 0;
        while (i < n) {
            char c = t.charAt(i);
            if (c == '\n') { close(t, out, start, i); start = -1; i++; continue; }
            if (isTerm(c) && boundaryAt(t, i)) {
                int j = i;
                while (j < n && (isTerm(t.charAt(j)) || isCloser(t.charAt(j)))) j++;
                close(t, out, start, i);
                start = -1;
                i = j;
                continue;
            }
            if (start < 0 && !Character.isWhitespace(c)) start = i;
            i++;
        }
        close(t, out, start, n);
        return out;
    }

    private static void close(String t, List<int[]> out, int s, int e) {
        if (s < 0) return;
        while (e > s && Character.isWhitespace(t.charAt(e - 1))) e--;
        if (e > s) out.add(new int[]{s, e});
    }

    /** True when the character next to a word makes it part of something else (an address, a number, a file name, a hyphenated word). */
    private static boolean joined(String t, int idx, boolean before) {
        if (idx < 0 || idx >= t.length()) return false;
        char c = t.charAt(idx);
        if (Character.isDigit(c) || "@_/\\#%&=+*|<>~$^`".indexOf(c) >= 0) return true;
        if (c > 127 && Character.isLetter(c)) return true;
        if (c == '.' || c == ':' || c == '-') {
            int far = before ? idx - 1 : idx + 1;
            if (!before && c == ':' && far < t.length() && (t.charAt(far) == '/' || t.charAt(far) == '+')) return true;      // https://..., tel:+91...
            return far >= 0 && far < t.length() && Character.isLetterOrDigit(t.charAt(far));
        }
        return false;
    }

    /** Cuts [s, e) of the text into words. Returns true when the stretch holds a letter that is not plain English (Bengali, Devanagari, a styled font, accents). */
    private static boolean tokenize(String t, int s, int e, List<Tok> out) {
        boolean foreign = false;
        int i = s;
        int n = t.length();
        while (i < e) {
            char c = t.charAt(i);
            if (isAsciiLetter(c)) {
                int j = i;
                boolean curly = false;
                while (j < e) {
                    char d = t.charAt(j);
                    if (isAsciiLetter(d)) j++;
                    else if ((d == '\'' || d == '’') && j + 1 < e && isAsciiLetter(t.charAt(j + 1)) && j > i) { if (d == '’') curly = true; j++; }
                    else break;
                }
                Tok k = new Tok();
                k.s = i;
                k.e = j;
                k.core = t.substring(i, j).replace('’', '\'');
                k.plain = !joined(t, i - 1, true) && !joined(t, j, false);
                k.edit = k.plain && !curly && k.core.indexOf('\'') < 0;
                out.add(k);
                i = j;
            } else {
                int cp = t.codePointAt(i);
                if (cp > 127 && Character.isLetter(cp)) foreign = true;
                i += Character.charCount(cp);
            }
        }
        return foreign;
    }

    private static boolean allCaps(String w) { return w.length() > 1 && w.equals(w.toUpperCase(Locale.ROOT)); }

    /**
     * Hindi / Bangla words that are not English although the word list holds them (or a word one slip away from them): a sentence with one of these is NOT English.
     * (The tests turned "kitne baje meeting hai" into "Kite base meeting hai" because none of these was known as Hinglish.)
     */
    private static final Set<String> DESI_EXTRA = new HashSet<>(Arrays.asList(("ta ti hai hain hoon hu uska uski unka unki kitne kitna kitni baje pathao pathiye asbe asbo dekhbe dekhunga dekhna bolna chalna "
            + "koto kader tader moddhe shudhu sudhu bondho bondhura kisi kuchh dibe dure gussa hasi kanna mausam naukri garmi sardi zyada jyada kahin kaha humein hamari tumhari "
            + "subho mishti maja pori upor ese gele mubarak nahi nahin nai kya kyun kyu kab kahan kaise kaisa kaun bhi toh tumhara aapka aapki apka apki unhe unko isko usko na ka ke ki ko pe "
            + "bohot matlab samajh shukriya namaste pranam tabiyat sehat dosti mohabbat pagal diwana byah papa nana mama masi bua phupha jiju chutti tyohar mela mandir masjid pujo pujor payesh rosogolla "
            + "sandesh alur dom begun ilish chingri mangsho panch shat ekush bish tirish challish pochash ashi nobboi shotto hajar lokkho shoshur shashuri thakurda pishima mashima jethima boliyo "
            + "onekkhon ektuku onnorokom ajkal agamikal hamesha bohut iska mere tere apna apne upar andar karni karne lena jana peena sona uthna baithna ye le ja mat").split(" ")));
    /** Small Hindi / Bangla words that are also English words: one is not enough to decide, two (or one beside a doubtful word) mean the sentence is not English. */
    private static final Set<String> DESI_WEAK = new HashSet<>(Arrays.asList(("ho ye yeh wo woh hum tum tu mere mera meri tha thi tai kobe koi").split(" ")));
    /** An out-of-dictionary word with a Bangla / Hindi verb ending ("dekhbe", "korchi", "gelam", "pathabo"). */
    private static final java.util.regex.Pattern DESI_END = java.util.regex.Pattern.compile(".*(be|bo|bi|chi|cho|che|chhi|chho|chhe|lam|lem|ben|ibe|abo|ebo|iye|echi|echhi|ilam)$");

    private static boolean desiLike(String core) {
        if (core.indexOf('\'') >= 0) return false;
        String l = core.toLowerCase(Locale.ROOT);
        if (DESI_EXTRA.contains(l)) return true;
        if (Words.isFixedEntry(l) && !DESI_WEAK.contains(l)) return false;                        // "nite", "gud", "u": chat short forms of English words
        if (Words.isDesi(core)) return true;
        return l.length() >= 4 && !Words.known(l) && !Words.knownSlip(l) && DESI_END.matcher(l).matches();
    }

    private static boolean totalEnglish(List<Tok> toks, boolean foreign) {
        if (!Words.ready || foreign) return false;
        int words = 0, desi = 0, unk = 0, weak = 0, guess = 0;
        for (Tok k : toks) {
            if (!k.plain) continue;
            words++;
            String w = k.core;
            if (desiLike(w)) { desi++; continue; }
            String l = w.toLowerCase(Locale.ROOT);
            if (DESI_WEAK.contains(l)) weak++;
            if (w.indexOf('\'') >= 0 || allCaps(w) || Character.isUpperCase(w.charAt(0)) || Words.known(w)) continue;
            String fx = Words.fix(w, false);
            if (fx == null) unk++;                                                    // no dictionary, no name list, no correction knows it: Banglish, a name or slang
            else if (!Words.knownSlip(w) && !Words.isFixedEntry(w) && !Words.typoShape(w, fx)) guess++;         // only a distance guess ("kitne" -> "kite"): as likely Banglish as a typo
        }
        if (words == 0 || desi > 0) return false;
        if (weak >= 2 || (weak >= 1 && unk + guess >= 1)) return false;
        if (unk >= 2 || (unk == 1 && words <= 3)) return false;
        return unk + guess < (words <= 4 ? 2 : 3);
    }

    /** True when the sentence is entirely English: no Bangla / Hindi letters, no Banglish / Hinglish word, hardly a word the dictionary does not know. */
    public static boolean isTotalEnglish(String sentence) {
        if (sentence == null || sentence.trim().isEmpty()) return false;
        List<Tok> toks = new ArrayList<>();
        boolean foreign = tokenize(sentence, 0, sentence.length(), toks);
        return totalEnglish(toks, foreign);
    }

    /** True when the sentence beginning at s is the first one, or follows a mark (. ! ? danda); a plain line break does not count. */
    private static boolean startsSentence(String t, int s) {
        int i = s - 1;
        while (i >= 0 && Character.isWhitespace(t.charAt(i))) i--;
        if (i < 0) return true;
        while (i >= 0 && isCloser(t.charAt(i))) i--;
        return i < 0 || isTerm(t.charAt(i));
    }

    private static boolean shapeOk(String core, String fx) {
        if (Words.typoShape(core, fx)) return true;
        // a wrong vowel in a long word ("documants"): not a finger slip, but names are rarely one vowel away from a long ordinary word
        if (core.length() >= 7 && fx.length() == core.length()) {
            int diff = 0, at = -1;
            for (int i = 0; i < core.length(); i++) if (Character.toLowerCase(core.charAt(i)) != Character.toLowerCase(fx.charAt(i))) { diff++; at = i; }
            return diff == 1 && "aeiou".indexOf(Character.toLowerCase(core.charAt(at))) >= 0 && "aeiou".indexOf(Character.toLowerCase(fx.charAt(at))) >= 0;
        }
        return false;
    }

    private static boolean adjacent(String t, int from, int to) {
        if (to < from || to - from > 3) return false;
        for (int i = from; i < to; i++) if (!Character.isWhitespace(t.charAt(i))) return false;
        return to > from;
    }

    private static boolean commaBetween(String t, int from, int to) {
        for (int i = from; i < to && i < t.length(); i++) { char c = t.charAt(i); if (c == ',' || c == ';' || c == ':') return true; }
        return false;
    }

    private static Set<String> set(String s) { return new HashSet<>(Arrays.asList(s.split(" "))); }

    private static final Set<String> GREET = set("hi hello hey dear ask tell call ping cc bcc thanks thank regards");
    private static final Set<String> HONOR = set("ji bhai da di babu sir madam maam");
    private static final Set<String> BASE_VERBS = set("meet go start discuss continue catch do see talk check plan finish wait try have make take get move begin call leave stop celebrate review order pay eat sit watch decide");
    private static final Set<String> ITS_NEXT = set("not a an been very so too just really ok okay fine done late true my your gone here there still always already never raining");
    private static final Set<String> CONTEXT = set("its lets ill ur hes");
    private static final Set<String> ILL_NEXT = set("be do send call check let come try see get take talk share reply revert update go have make meet");
    private static final Set<String> UR_NEXT = set("going coming welcome free right late not doing being here there so very too");
    private static final Set<String> BEFORE_LETS = set("so and but ok okay then yes no hey sure");
    private static final Set<String> NOT_BEFORE_ILL = set("is was feel feels felt very so too been get got fell fall become became");

    /** Real words that are often meant as a short form of something else, decided by the word next to them ("its not" is "it's not", "its name" stays). */
    private static String contextWord(String w, String prev, String next, boolean first) {
        if (w.equals("its") && ITS_NEXT.contains(next)) return "it's";
        if (w.equals("hes") && ITS_NEXT.contains(next)) return "he's";
        if (w.equals("lets") && BASE_VERBS.contains(next) && (first || BEFORE_LETS.contains(prev))) return "let's";
        if (w.equals("ill") && ILL_NEXT.contains(next) && !NOT_BEFORE_ILL.contains(prev) && (first || BEFORE_LETS.contains(prev))) return "I'll";
        if (w.equals("ur") && UR_NEXT.contains(next)) return "you're";
        return null;
    }

    private static String capital(String w) { return w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1); }

    /** `punctuate`: also give the last sentence its mark. `cursor`: where the cursor stands (a word it stands inside is never touched). */
    public static Result fix(String text, int cursor, boolean punctuate) { return fix(text, cursor, punctuate, false); }

    /** keepCursorWord: the word that ends exactly at the cursor is left alone too (automatic use: she may still be typing it). */
    public static Result fix(String text, int cursor, boolean punctuate, boolean keepCursorWord) {
        Result r = new Result();
        if (text == null) text = "";
        int n = text.length();
        cursor = Math.max(0, Math.min(cursor, n));
        r.text = text;
        r.cursor = cursor;
        r.to = r.from = cursor;
        if (n == 0 || !Words.ready) return r;
        List<Edit> edits = new ArrayList<>();
        List<int[]> sents = sentences(text);
        boolean lastEnglish = false;
        for (int si = 0; si < sents.size(); si++) {
            int s = sents.get(si)[0], e = sents.get(si)[1];
            List<Tok> toks = new ArrayList<>();
            boolean foreign = tokenize(text, s, e, toks);
            boolean eng = totalEnglish(toks, foreign);
            if (si == sents.size() - 1) lastEnglish = eng;
            if (toks.isEmpty()) continue;
            if (!eng) { r.left++; continue; }
            r.english++;
            for (int k = 0; k < toks.size(); k++) {
                Tok tk = toks.get(k);
                if (!tk.plain) continue;
                String core = tk.core;
                boolean first = k == 0 && noWordBefore(text, s, tk.s) && startsSentence(text, s);       // (a line break without a mark does not start a sentence: lists keep their lines as typed)
                boolean inside = tk.s < cursor && (cursor < tk.e || (keepCursorWord && cursor == tk.e));    // she is editing this word right now: nothing about it changes
                boolean cap = Character.isUpperCase(core.charAt(0));
                String result = null;
                String prevW = k > 0 && adjacent(text, toks.get(k - 1).e, tk.s) ? toks.get(k - 1).core.toLowerCase(Locale.ROOT) : "";
                String nextW = k + 1 < toks.size() && adjacent(text, tk.e, toks.get(k + 1).s) ? toks.get(k + 1).core.toLowerCase(Locale.ROOT) : "";
                if (tk.edit) {
                    boolean autoCap = first && cap && (core.length() == 1 || core.substring(1).equals(core.substring(1).toLowerCase(Locale.ROOT)));
                    if (!inside) {
                        String lw = core.toLowerCase(Locale.ROOT);
                        boolean clauseStart = first || (k > 0 && commaBetween(text, toks.get(k - 1).e, tk.s));
                        String cx = CONTEXT.contains(lw) ? contextWord(lw, prevW, nextW, clauseStart) : null;          // "its not" -> "it's not", "ur doing" -> "you're doing"
                        if (cx != null) result = Words.matchCase(core, cx);
                        else {
                            String fx = Words.fix(core, autoCap);
                            if (fx != null && !fx.equals(core)) {
                                boolean ok;
                                if (first && cap) {
                                    ok = Words.knownSlip(core) || Words.isFixedEntry(core) || (core.length() >= 4 && Words.confidentFix(core, fx) && shapeOk(core, fx));       // a capital first word may be a name
                                } else if (Words.knownSlip(core) || Words.isFixedEntry(core)) {
                                    ok = true;                                            // "teh", "recieve", "u", "plz": always right
                                } else {
                                    ok = Words.sureFix(core) && Words.autoReplaceOk(core);
                                    // a word is changed only when the slip is a finger slip (neighbour key, swap, dropped or doubled letter; a wrong vowel in a long word): "sandeep" is not a typo of "sanders"
                                    if (ok && !shapeOk(core, fx)) ok = false;
                                    if (ok && fx.indexOf(' ') >= 0) ok = false;                        // an unknown word is never cut in two ("onedrive", "ranjan")
                                    // a lower-case name after "hi", "dear", "call" ... or before "ji", "bhai" ... is a name
                                    if (ok && !cap && core.length() <= 6 && (GREET.contains(prevW) || HONOR.contains(nextW))) ok = false;
                                }
                                if (ok) result = fx;
                            }
                        }
                    }
                } else if (!inside && core.indexOf('\'') >= 0 && !allCaps(core)) {
                    String cp = Words.capitalOnly(core);                              // i'm -> I'm
                    if (cp != null && !cp.equals(core)) result = cp;
                }
                if (result != null && text.substring(tk.s, tk.e).indexOf('\u2019') >= 0) result = result.replace('\'', '\u2019');       // keep her curly apostrophe
                String shownWord = result != null ? result : core;
                // the first word of a sentence starts with a capital letter (like the keyboard's own auto-capital does while typing)
                if (first && !inside && !cap && Character.isLowerCase(shownWord.charAt(0)) && inDict(shownWord) && !Words.isProtected(shownWord)) shownWord = capital(shownWord);
                if (!shownWord.equals(core)) {
                    edits.add(new Edit(tk.s, tk.e, shownWord));
                    r.fixed++;
                    r.pairs.add(core + ">" + shownWord);
                }
            }
        }
        // the mark at the end of the message
        if (punctuate && !sents.isEmpty()) {
            int[] ls = sents.get(sents.size() - 1);
            if (restIsBlank(text, ls[1])) {
                String sent = applyRange(text, edits, ls[0], ls[1]);
                String mark = sent.isEmpty() ? "" : Spoken.markFor(sent, lastEnglish);
                // Banglish / Hinglish / script text gets only what the tone asks for (? or !): a typed message without a full stop stays that way
                if (!lastEnglish && (mark.equals(".") || mark.equals("\u0964"))) mark = "";
                if (!mark.isEmpty()) {
                    edits.add(new Edit(ls[1], ls[1], mark));
                    r.marked = true;
                    r.mark = mark;
                }
            }
        }
        if (edits.isEmpty()) return r;
        Collections.sort(edits, new Comparator<Edit>() {
            @Override public int compare(Edit a, Edit b) { return a.s != b.s ? a.s - b.s : a.e - b.e; }
        });
        StringBuilder out = new StringBuilder();
        int pos = 0, cur = cursor, from = Integer.MAX_VALUE, to = -1;
        for (Edit ed : edits) {
            out.append(text, pos, ed.s).append(ed.txt);
            pos = ed.e;
            int delta = ed.txt.length() - (ed.e - ed.s);
            if (ed.e <= cursor) cur += delta;
            from = Math.min(from, ed.s);
            to = Math.max(to, ed.e);
        }
        out.append(text, pos, n);
        r.text = out.toString();
        r.cursor = Math.max(0, Math.min(cur, r.text.length()));
        r.from = from;
        r.to = to;
        r.insert = r.text.substring(from, r.text.length() - (n - to));
        return r;
    }

    private static boolean inDict(String w) {
        int sp = w.indexOf(' ');
        return Words.inDictionary(sp > 0 ? w.substring(0, sp) : w);
    }

    private static boolean noWordBefore(String t, int from, int to) {
        for (int i = from; i < to; i++) if (Character.isLetterOrDigit(t.charAt(i))) return false;
        return true;
    }

    private static boolean restIsBlank(String t, int from) {
        for (int i = from; i < t.length(); i++) if (!Character.isWhitespace(t.charAt(i))) return false;
        return true;
    }

    /** The text of [s, e) with the edits that lie inside it applied. */
    private static String applyRange(String text, List<Edit> edits, int s, int e) {
        List<Edit> in = new ArrayList<>();
        for (Edit ed : edits) if (ed.s >= s && ed.e <= e) in.add(ed);
        Collections.sort(in, new Comparator<Edit>() {
            @Override public int compare(Edit a, Edit b) { return a.s != b.s ? a.s - b.s : a.e - b.e; }
        });
        StringBuilder b = new StringBuilder();
        int pos = s;
        for (Edit ed : in) { b.append(text, pos, ed.s).append(ed.txt); pos = ed.e; }
        b.append(text, pos, e);
        return b.toString();
    }
}
