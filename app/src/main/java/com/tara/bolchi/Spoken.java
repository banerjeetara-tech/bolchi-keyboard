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
            {"i", "mean"},
            // ("no no" is not in this list any more: "I am going, no no problem at all" lost its words; people say "no no" without correcting anything)
            {"না", "সরি"}, {"সরি", "না"},
            {"नहीं", "सॉरी"}, {"सॉरी", "नहीं"}};
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

    /** Words that are said twice on purpose. */
    private static final java.util.Set<String> TWICE_OK = new java.util.HashSet<>(Arrays.asList(
            "very", "so", "no", "bye", "ha", "had", "that", "really", "many", "long", "far", "too", "ok", "okay", "yes", "na", "hi", "bla", "blah", "knock", "more", "again"));

    /** Bengali / Hindi words that are doubled on purpose ("aste aste", "dhire dhire", "choto choto", "baar baar"): the second copy is a real word, not a stutter. */
    private static final java.util.Set<String> REDUP = new java.util.HashSet<>(Arrays.asList((
            "aste dhire choto boro jore mone hate bar roj onek thoda baar jaldi bahut").split(" ")));

    /** True when a single word said twice should stay: an intended double in English, or a Bengali / Hindi double. */
    private static boolean keepDouble(String w, boolean english) {
        if (TWICE_OK.contains(w) || w.matches("\\d+")) return true;
        if (english) return false;
        return REDUP.contains(w) || (w.length() >= 5 && w.endsWith("te"));      // "dekhte dekhte", "bolte bolte": two continuing actions
    }

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
                for (int i = 0; i + 2 * n <= tok.size(); i++) {
                    boolean same = true;
                    for (int k = 0; k < n && same; k++) same = norm(tok.get(i + k)).equals(norm(tok.get(i + n + k))) && !norm(tok.get(i + k)).isEmpty();
                    if (!same) continue;
                    if (n == 1 && keepDouble(norm(tok.get(i)), singleWords)) continue;
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
        for (int n = Math.min(8, Math.min(b.length, s.length - 1)); n >= 1; n--) {
            if (n == 1 && keepDouble(norm(s[0]), singleWords)) break;
            boolean same = true;
            for (int k = 0; k < n && same; k++) same = !norm(s[k]).isEmpty() && norm(s[k]).equals(norm(b[b.length - n + k]));
            if (!same) continue;
            StringBuilder o = new StringBuilder();
            for (int k = n; k < s.length; k++) { if (o.length() > 0) o.append(' '); o.append(s[k]); }
            return o.toString();
        }
        return said;
    }

    // ---------- punctuation by meaning (version 144) ----------
    // Tara: "punctuation will also work when speaking or writing in English; for Bengali English it will understand the tone and give only the appropriate punctuation".
    // A wrong mark annoys more than a missing one, so a question / cheer is only recognised when the words say so.

    private static java.util.Set<String> set(String s) { return new java.util.HashSet<>(Arrays.asList(s.split(" "))); }

    /** Words a sentence does not end on: a pause after one of these is a pause to think, not the end. */
    private static final java.util.Set<String> OPEN = set(
            "and but or so to the a an of in on at for with from by because that which who if when then than as is are was were am be will would can could should "
            + "my your his her our their not very also just into "
            + "ar ebong kintu je ki ke er te theke jonno karon tai jodi tokhon amar tomar tar amader tahole "
            + "aur lekin ya ka ko se me par liye kyunki agar toh to mera tumhara uska "
            + "ami tumi tui se amra tomra tara main hum tum tu aap woh wo ye yeh ek ekta kuch kichu kichhu sathe saath kache pe mein baad moddhe upor niche bhitor niye "
            + "tobe magar isliye jab jodio tarpor mane jeno");
    private static final java.util.Set<String> KI_AFTER = set("tumi tui apni tomra ora se eta ota ei oi tara aap tum tu woh wo");
    private static final java.util.Set<String> EMBED = set("jani janina janio janiye bujhte bujhlam bujhi bujhechi pata samajh samjha batao bolo bolun poochho puchho jaane jaanta jaanti \u099C\u09BE\u09A8\u09BF \u09AC\u09C1\u099D\u09BF");
    private static final java.util.Set<String> FIRST = set("ami amra main mai hum maine amake amar mera meri amader hamara mujhe humein");
    private static final java.util.Set<String> SECOND = set("tumi tui tum tu aap apni aapne tomra tomar tumhara tumhari tomake tumhe tera");

    /** A question after a filler or a greeting ("so what time is the meeting", "hi Tara how are you") is still a question. */
    private static final java.util.Set<String> LEAD = set("so ok okay and but also well hey then now oh um uh alright anyway like");
    private static final java.util.Set<String> GREETW = set("hi hello hey dear");
    /** Words a sentence cannot end on, not even a question: "can you send it and" is not finished. (No prepositions: "what is it for", "are you coming or not" are real questions.) */
    private static final java.util.Set<String> EN_DANGLE = set("and but or because if as the a an my your his her our their than very also just");
    private static final java.util.Set<String> EN_AUXEND = set("is are was were am be will would can could should");
    private static final java.util.Set<String> CLOSERS = set("ok_thanks okay_thanks thank_you see_you got_it will_do no_problem sounds_good me_too of_course not_really you_too take_care");
    private static final java.util.Set<String> EN_WH = set("what why how when where who whom whose which");
    private static final java.util.Set<String> EN_AUX = set("is are am was were can could will would should shall may might must have has had "
            + "isn't aren't wasn't weren't can't couldn't won't wouldn't shouldn't haven't hasn't hadn't");
    private static final java.util.Set<String> EN_DO = set("do does did don't doesn't didn't");
    private static final java.util.Set<String> EN_SUBJ = set("i you we he she they it there this that these those my your his her our their any anyone anybody everyone everybody "
            + "someone somebody nobody something anything everything both all each some people");
    private static final java.util.Set<String> EN_ART = set("the a an");
    private static final java.util.Set<String> EN_WH_PRON = set("i we you he she they it the a an my our their this that these those there his her");
    private static final java.util.Set<String> EN_ADJ = set("nice beautiful wonderful lovely sweet great cute awesome amazing good kind pretty lucky sad happy funny cool");
    private static final java.util.Set<String> NEG_AUX = set("isn't aren't wasn't weren't don't doesn't didn't won't wouldn't can't couldn't shouldn't haven't hasn't hadn't");
    private static final java.util.Set<String> TAG_PRON = set("it you he she they we i there that");
    private static final java.util.Set<String> CHEER1 = set("wow hurray hooray omg yay great awesome amazing wonderful fantastic superb excellent brilliant congratulations congrats cheers "
            + "darun bah shabash wah badhai mast zabardast chomotkar osadharon");
    private static final java.util.Set<String> WISH = set("mubarak subho shubho shubh badhai abhinandan shubhechha");
    private static final java.util.Set<String> KI = set("ki কি কী");
    private static final java.util.Set<String> KYA = set("kya क्या");
    private static final java.util.Set<String> KI_EXCL = set("sundor shundor sundar darun bhalo valo moja maja mishti chomotkar khub kando ador osadharon bhoyonkor ashchorjo accha achha mast "
            + "সুন্দর দারুণ ভালো মজা");
    private static final java.util.Set<String> KYA_EXCL = set("baat mast khoob kamal zabardast sundar accha achha badhiya gajab pyara \u092C\u093E\u0924 \u092E\u0938\u094D\u0924");
    private static final java.util.Set<String> EXCL_LEAD = set("koto kitna kitni kitne");
    /** Bangla / Hindi question words that ask whatever the place in the sentence ("tumi kothay acho", "tum kab aaoge"). */
    private static final java.util.Set<String> BN_Q_ANY = set("keno kothay kokhon kemon kobe kivabe kibhabe koto kota kon konta kake kader kyun kyu kyon kaise kaisa kaisi kab kahan kaun kitna kitne kitni "
            + "kidhar kiska kiski kisko naki "
            + "কেন কোথায় কোথায় কখন কেমন কত কবে কোন কোনটা "
            + "কিভাবে কীভাবে কাকে নাকি "
            + "क्यों कैसे कब कहाँ कहां कौन कितना");

    private static final java.util.Set<String> PRON = set("i you we he she they it there this that these those anyone anybody everyone everybody someone somebody nobody");
    private static final java.util.Set<String> DET = set("the my your his her our their");
    private static final java.util.Set<String> DO_SUBJ = set("i you we they anyone anybody everyone everybody these those people");
    private static final java.util.Set<String> HAVE_SUBJ = set("i you we they there anyone anybody everyone everybody people");
    private static final java.util.Set<String> HAS_SUBJ = set("he she it there this that the anyone anybody everyone everybody someone somebody");
    private static final java.util.Set<String> HAD_SUBJ = set("i you we he she they it");
    private static final java.util.Set<String> MAY_SUBJ = set("i we");
    private static final java.util.Set<String> IMPERATIVE = set("worry dare ever even forget touch");

    /** True when `aux` + `next` is a question word order: "can you", "is the file", "does it", "are they". Imperatives ("do it now", "have a nice day") and statements with the subject left out ("was a great evening") are not. */
    private static boolean auxSubject(String aux, String next, boolean nameNext) {
        String a = aux.endsWith("n't") ? aux.substring(0, aux.length() - 3) : aux;
        if (aux.equals("can't")) a = "can";
        if (aux.equals("won't")) a = "will";
        if (a.equals("do")) return DO_SUBJ.contains(next);
        if (a.equals("have")) return HAVE_SUBJ.contains(next);
        if (a.equals("has")) return HAS_SUBJ.contains(next) || nameNext;
        if (a.equals("had")) return HAD_SUBJ.contains(next);
        if (a.equals("may") || a.equals("might") || a.equals("must")) return MAY_SUBJ.contains(next);
        return PRON.contains(next) || DET.contains(next) || nameNext;                  // does, did, is, are, was, were, am, can, could, will, would, should, shall
    }

    private static String restFrom(String[] raw, int from) {
        StringBuilder b = new StringBuilder();
        for (int i = from; i < raw.length; i++) { if (b.length() > 0) b.append(' '); b.append(raw[i]); }
        return b.toString();
    }

    private static String joinUnderscore(String[] w) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w.length; i++) { if (i > 0) b.append('_'); b.append(w[i]); }
        return b.toString();
    }

    /** What a sentence asks for at its end: '?', '!', '.' (a danda in script) or 0 for nothing. `english`: the sentence is entirely English, so no Banglish rules are tried. */
    private static char kind(String t, boolean english) {
        String[] raw = t.trim().split("\\s+");
        int n = raw.length;
        String[] w = new String[n];
        for (int i = 0; i < n; i++) w[i] = norm(raw[i]);
        String w0 = w[0], w1 = n > 1 ? w[1] : "", last = w[n - 1];
        if (n >= 3 && LEAD.contains(w0) && kind(restFrom(raw, 1), english) == '?') return '?';
        if (n >= 4 && GREETW.contains(w0) && kind(restFrom(raw, 2), english) == '?') return '?';
        boolean name1 = n > 1 && !raw[1].isEmpty() && Character.isUpperCase(raw[1].charAt(0)) && !w1.equals("i");
        // an address, a link or a long number at the end: no mark after it (it would become part of it)
        String lr = raw[n - 1];
        if (lr.indexOf('@') >= 0 || lr.indexOf('/') >= 0 || lr.matches("[0-9][0-9-]{4,}") || lr.matches("(?i)[a-z0-9._-]+\\.(com|in|org|net|co|io)")) return 0;
        boolean bnQ = false, embed = false, first = false, second = false, bnWord = false;
        if (!english) {
            for (String x : w) if (EMBED.contains(x)) embed = true;
            for (int xi = 0; xi < n; xi++) { String x = w[xi]; if (BN_Q_ANY.contains(x) && !((x.equals("kyun") || x.equals("kyu") || x.equals("kyon")) && xi + 1 < n && KI.contains(w[xi + 1]))) { bnQ = true; bnWord = true; } if (EMBED.contains(x)) embed = true; if (FIRST.contains(x)) first = true; if (SECOND.contains(x)) second = true; }
            if (KI.contains(w0) || KYA.contains(w0) || (n >= 2 && (KI.contains(last) || KYA.contains(last)))) bnQ = true;
            if (bnQ && embed) return 0;                                           // "ami jani na kothay gelo": a statement that holds a question word
        }
        // 1. a cheer, a wish, a greeting, "what a ..." is an exclamation
        if (n <= 4 && CHEER1.contains(w0)) return '!';
        if ((w0.equals("congratulations") || w0.equals("congrats")) && n <= 8) return '!';
        if ((w0.equals("happy") || w0.equals("happiest")) && n <= 6) return '!';
        if (w0.equals("welcome") && n <= 5) return '!';
        if (w0.equals("good") && n <= 6 && (w1.equals("morning") || w1.equals("afternoon") || w1.equals("evening") || w1.equals("night"))) {
            return n >= 4 && kind(restFrom(raw, 2), english) == '?' ? '?' : '!';           // "good morning how are you" asks
        }
        if (n <= 4 && ((w0.equals("well") && w1.equals("done")) || (w0.equals("get") && w1.equals("well")) || (w0.equals("best") && w1.equals("wishes")))) return '!';
        if (n >= 3 && n <= 5 && w0.equals("all") && w1.equals("the") && w[2].equals("best")) return '!';
        if (w0.equals("what") && (w1.equals("a") || w1.equals("an")) && n <= 8) return '!';
        if (w0.equals("how") && n <= 7 && EN_ADJ.contains(w1) && (n == 2 || !(EN_AUX.contains(w[2]) || EN_DO.contains(w[2])))) return '!';
        if (!english) {
            if (n <= 6 && !bnQ) for (String x : w) if (WISH.contains(x)) return '!';
            if (n > 1 && n <= 8 && (KI.contains(w0) || KYA.contains(w0) || EXCL_LEAD.contains(w0)) && (KI_EXCL.contains(w1) || KYA_EXCL.contains(w1))) return '!';     // "ki sundor", "kya baat hai", "koto bhalo", "kitna accha laga"
        }
        if (n > 1 && (EN_DANGLE.contains(last) || (n <= 3 && EN_AUXEND.contains(last)))) return 0;      // unfinished: no mark, not even "?"
        if (english && n <= 3 && CLOSERS.contains(joinUnderscore(w))) return '.';                       // "thank you", "see you", "got it": short English closers end in a full stop
        // 2. a question
        if (EN_WH.contains(w0) && (w0.equals("when") || w0.equals("where")) && n > 1 && !(EN_AUX.contains(w1) || EN_DO.contains(w1))) {
            // a statement that begins with when / where
        } else if (EN_WH.contains(w0)) {
            if (!EN_WH_PRON.contains(w1)) return '?';                                     // "how are you", "what time is it", "who is coming"
            // "when you reach home call me" is a statement; "what you are doing" is a question
            if ((w0.equals("what") || w0.equals("why") || w0.equals("how") || w0.equals("where")) && n > 2 && (EN_AUX.contains(w[2]) || EN_DO.contains(w[2]))) return '?';
        } else if (n > 1 && (EN_AUX.contains(w0) || EN_DO.contains(w0))) {
            if (auxSubject(w0, w1, name1) && !(n > 2 && (w0.equals("don't") || w0.equals("do")) && IMPERATIVE.contains(w[2]))) return '?';                                    // "can you send", "is the file ready"; not "do it now", "have a nice day", "was a great evening", "will send tomorrow"
        }
        if (n >= 2 && NEG_AUX.contains(w[n - 2]) && TAG_PRON.contains(last)) return '?';   // "you will come, won't you"
        if (n >= 2 && raw[n - 2].endsWith(",") && (last.equals("right") || last.equals("ok") || last.equals("okay") || (!english && last.equals("na")))) return '?';
        if (!english && !embed) {
            if (bnWord) return '?';                                                       // a Bangla / Hindi question word anywhere ("kyun ki" = because does not count)
            if (KI.contains(w0) || KYA.contains(w0)) return '?';                          // "ki korcho", "kya kar rahe ho"
            if (n >= 2 && (KI.contains(last) || KYA.contains(last))) return '?';          // "tumi jabe ki"
            // "tumi ki khabe": ki right after a "you / he / she / it" word asks. (Hindi "ki" = that, and "X ki shaadi" = of X: not questions.)
            if (n >= 3 && n <= 6) for (int i = 1; i < n - 1; i++) if (KI.contains(w[i]) && KI_AFTER.contains(w[i - 1])) return '?';
            if (n >= 3 && n <= 6) for (int i = 1; i < n - 1; i++) if (KYA.contains(w[i]) && !EMBED.contains(w[i - 1])) return '?';
            if (w0.equals("ke") || w0.equals("kar") || w0.equals("কে") || w0.equals("কার")) return '?';
            // ("ami jabo na" is a plain statement: "na" at the end is NOT a question)
        }
        if (!english && second && !first) return 0;      // "tumi bari gechho": a statement or a question? only the voice knows: no mark
        // 3. a plain statement: a whole sentence of 3+ words that does not end on a joining word
        // (so / that / then end a finished English sentence: "I think so", "I did that", "see you then")
        if (n >= 3 && (!OPEN.contains(last) || (english && (last.equals("so") || last.equals("that") || last.equals("then"))))) {
            char lc = t.trim().charAt(t.trim().length() - 1);
            return lc >= 'ऀ' && lc <= '৿' ? '।' : '.';
        }
        return 0;
    }

    /**
     * The mark a finished sentence gets by its meaning: "?" for a question, "!" for a cheer / wish / greeting, a full stop (danda in Bangla / Hindi script) for a plain statement,
     * "" when it already has a mark, is too short or looks unfinished. `english`: the sentence is entirely English (see SentenceFix.isTotalEnglish); otherwise it is judged as
     * Banglish / Hinglish speech and only the question / cheer words of those languages count in addition.
     */
    public static String markFor(String sentence, boolean english) {
        if (sentence == null) return "";
        String t = sentence.trim();
        if (t.isEmpty()) return "";
        char lastCh = t.charAt(t.length() - 1);
        if (!Character.isLetterOrDigit(lastCh) && Character.getType(lastCh) != Character.NON_SPACING_MARK && Character.getType(lastCh) != Character.COMBINING_SPACING_MARK) return "";            // already ends in a mark, a comma, a bracket, an emoji ...
        char k = kind(t, english);
        return k == 0 ? "" : String.valueOf(k);
    }

    public static String markFor(String sentence) { return markFor(sentence, false); }
}
