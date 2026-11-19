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
    // Two layers: the ENGLISH rules (english == true: the sentence is entirely English) and the BANGLISH / HINGLISH / script rules (english == false), which are only tried when english is false.

    private static java.util.Set<String> set(String s) { return new java.util.HashSet<>(Arrays.asList(s.split(" "))); }

    /** Words a sentence does not end on: a pause after one of these is a pause to think, not the end. */
    private static final java.util.Set<String> OPEN = set(
            "and but or so to the a an of in on at for with from by because that which who if when then than as is are was were am be will would can could should "
            + "my your his her our their not very also just into "
            + "ar ebong kintu je ki ke er te theke jonno karon tai jodi tokhon amar tomar tar amader tahole "
            + "aur lekin ya ka ko se me par liye kyunki agar toh to mera tumhara uska "
            // pronouns and little words a Banglish / Hinglish sentence is not finished on ("ei jonno ami", "main ghar pe", "ami tai bhabchi")
            + "ami tumi tui se amra tomra tara main hum tum tu aap woh wo ye yeh ek ekta kuch kichu kichhu sathe saath kache pe mein baad moddhe upor niche bhitor niye "
            + "tobe magar isliye jab jodio tarpor mane jeno");
    /** Banglish / Hinglish / script words a sentence cannot end on. Used ONLY when the sentence is not English (us, tab, take, jo are English words). */
    private static final java.util.Set<String> OPEN_BN = set(
            "tarpor tarpore tobe othoba noyto noile kenona jehetu jokhon jodio tomake amake oke tader sathe niye moddhe bole kache ekta ei oi shei ek kono "
            + "mein main mere meri tumhari tumhare uski uske apna apne phir jis magar warna "
            + "কারণ এবং কিন্তু অথবা তাহলে তারপর কারণে আর যদি তখন কি। और लेकिन क्योंकि अगर मगर तब जब फिर कि जो में मैं मेरे");

    // ----- English layer -----
    /** A question after a filler or a greeting ("so what time is the meeting", "hi Tara how are you") is still a question. */
    private static final java.util.Set<String> LEAD = set("so ok okay and but also well hey then now oh um uh alright anyway like btw sir madam bro sorry thanks thank please");
    private static final java.util.Set<String> GREETW = set("hi hello hey dear");
    /** Words a sentence cannot end on, not even a question: "can you send it and" is not finished. (No prepositions: "what is it for", "are you coming or not" are real questions.) */
    private static final java.util.Set<String> EN_DANGLE = set("and but or because if as the a an my your his her our their than very also just");
    private static final java.util.Set<String> EN_AUXEND = set("is are was were am be will would can could should");
    private static final java.util.Set<String> CLOSERS = set("ok_thanks okay_thanks thank_you see_you got_it will_do no_problem sounds_good me_too of_course not_really you_too take_care");
    private static final java.util.Set<String> WH_CONTR = set("what's whats how's hows where's wheres who's whos when's why's");
    private static final java.util.Set<String> EN_WH = set("what why how when where who whom whose which");
    private static final java.util.Set<String> EN_AUX = set("is are am was were can could will would should shall may might must have has had "
            + "isn't aren't wasn't weren't can't couldn't won't wouldn't shouldn't haven't hasn't hadn't");
    private static final java.util.Set<String> EN_DO = set("do does did don't doesn't didn't");
    private static final java.util.Set<String> EN_WH_PRON = set("i we you he she they it the a an my our their this that these those there his her");
    private static final java.util.Set<String> EN_ADJ = set("nice beautiful wonderful lovely sweet great cute awesome amazing good kind pretty lucky sad happy funny cool");
    private static final java.util.Set<String> NEG_AUX = set("isn't aren't wasn't weren't don't doesn't didn't won't wouldn't can't couldn't shouldn't haven't hasn't hadn't");
    /** "when exactly will it be ready", "where else did you go": the question word, one more word, then the auxiliary. */
    private static final java.util.Set<String> WH_ADV = set("exactly else precisely actually specifically ever");
    private static final java.util.Set<String> TAG_PRON = set("it you he she they we i there that");
    private static final java.util.Set<String> PRON = set("i you we he she they it there this that these those anyone anybody everyone everybody someone somebody nobody");
    private static final java.util.Set<String> DET = set("the my your his her our their");
    private static final java.util.Set<String> DO_SUBJ = set("i you we they anyone anybody everyone everybody these those people");
    private static final java.util.Set<String> HAVE_SUBJ = set("i you we they there anyone anybody everyone everybody people");
    private static final java.util.Set<String> HAS_SUBJ = set("he she it there this that the anyone anybody everyone everybody someone somebody");
    private static final java.util.Set<String> HAD_SUBJ = set("i you we he she they it");
    private static final java.util.Set<String> MAY_SUBJ = set("i we");
    private static final java.util.Set<String> NAME_AUX = set("did does can could will would should shall");
    private static final java.util.Set<String> IMPERATIVE = set("worry dare ever even forget touch");

    // ----- Banglish / Hinglish / script layer -----
    // CHEER1 without the first names (kamal, subho ...): those are in NAMEY below
    private static final java.util.Set<String> CHEER1 = set("wow hurray hooray omg yay great awesome amazing wonderful fantastic superb excellent brilliant congratulations congrats cheers "
            + "darun bah shabash wah badhai zabardast chomotkar osadharon "
            + "বাহ ওয়াও শাবাশ দারুণ চমৎকার অসাধারণ বাহ্ ওয়াহ वाह शाबाश बधाई ज़बरदस्त");
    /** Cheer / wish words that are also first names: a cheer only when alone (or 2 words), or when a verb of praise follows. */
    private static final java.util.Set<String> NAMEY = set("kamal mast subho shubho shubh abhinandan");
    private static final java.util.Set<String> PRAISE_NEXT = set("hai hoyeche hoise ho laglo lagche kiya kar diya dikhaya");
    private static final java.util.Set<String> WISH = set("mubarak mubarakbad badhai shubhechha shuvechha subhechha shubhokamona shuvokamona "
            + "শুভেচ্ছা শুভকামনা মোবারক মুবারক अभिनंदन मुबारक बधाई शुभकामनाएं शुभकामनाएँ");
    private static final java.util.Set<String> WISH_NOUN = set("nabobarsho noboborsho nababarsha bijoya dipaboli jonmodin janmodin jonmodiner janmodiner ratri sakal shokal sandhya "
            + "janamdin jannat diwali dussehra holi eid birthday "
            + "নববর্ষ বিজয়া দীপাবলি জন্মদিন জন্মদিনের জন্মদিনে রাত্রি সকাল শুভ जन्मदिन दिवाली होली ईद नववर्ष");
    private static final java.util.Set<String> WISH_SHUBH = set("shubho shubh shuvo subho শুভ শুভো शुभ");
    private static final java.util.Set<String> KI = set("ki kii কি কী");
    private static final java.util.Set<String> KYA = set("kya kyaa क्या");
    private static final java.util.Set<String> KI_EXCL = set("sundor shundor sundar darun bhalo valo moja maja mishti chomotkar khub kando ador osadharon "
            + "bhoyonkor bhishon ashchorjo oddhut mojar nishthur lojja "
            + "সুন্দর দারুণ ভালো মজা ভয়ঙ্কর ভীষণ অদ্ভুত আশ্চর্য চমৎকার অসাধারণ");
    /** Appreciative words that make "ki / koto / kya / kitna + word" an exclamation (not bhalo / bada: "tumi ki bhalo acho", "kitna bada hai" are questions). */
    private static final java.util.Set<String> ADJ_POS = set("sundor shundor sundar darun bhoyonkor bhishon ashchorjo oddhut mojar mishti chomotkar osadharon kando ador "
            + "pyara pyari achha accha acchi achhe mast gazab gajab kamaal zabardast shandar badhiya khubsurat mazedaar "
            + "সুন্দর দারুণ ভয়ঙ্কর ভীষণ অদ্ভুত আশ্চর্য চমৎকার অসাধারণ সুন্দরী मजेदार सुंदर प्यारा प्यारी अच्छा अच्छी शानदार बढ़िया ख़ूबसूरत खूबसूरत ग़ज़ब गजब कमाल");
    private static final java.util.Set<String> EXCL_Q = set("ki কি কী koto কত kya क्या kitna kitne kitni कितना कितने कितनी");
    private static final java.util.Set<String> KYA_EXCL = set("mast khoob kamal zabardast मस्त खूब कमाल");
    /** Question words that ask wherever they stand ("tumi kothay acho", "tum kab aaoge"). */
    private static final java.util.Set<String> BN_Q_ANY = set("keno kothay kokhon kemon kobe kivabe kibhabe koto kota kon konta kake kader kyun kyu kyon kaise kaisa kaisi kab kahan kaun kitna kitne kitni "
            + "kidhar kiska kiski kisko naki "
            + "কেন কোথায় কখন কেমন কত কবে কোন কোনটা "
            + "কিভাবে কীভাবে কাকে নাকি "
            + "क्यों कैसे कब कहाँ कहां कौन कितना "
            + "kina koyta kotota kotokkhon kotokhon kotodur kotodin kotogulo kisher kiser kemne kemone kisne kisse kiske kis kaunsa kaunsi konsa konsi "
            + "কে কার কিসের কীসের কয়টা কতক্ষণ কতদূর কতদিন কিনা "
            + "कितने कितनी किसने किसे किसके किसका किसकी किसको किधर कौनसा कौनसी कहाँ");
    /** "I know / I say / I see" words: a question word after one of them is a clause inside a statement ("ami jani ki hoyeche"). */
    private static final java.util.Set<String> EMB = set("jani janina jantam jante bujhi bujhte bujhlam bujhini bujhechi dekhchi dekhte dekhlam dekhechi shunechi shunlam shunchi "
            + "dekho bolchi bolechi bolechilam bolbo bolte bhebe bhabchi bhule bhulechi jaanta jaanti maaloom malum samajh yaad pata पता जानता जानती জানি জানতাম বুঝি বুঝতে বলছি বলব ভাবছি");
    /** Words a "tell me where / when ..." sentence ends on (Bangla / Hindi verb-final order: the question word stands BEFORE them). */
    private static final java.util.Set<String> EMB_END = set("batao janiye janio poochho puchho jano janbe");
    /** ... and, after "ta", also "bolo / bolun" ("kab aaoge bolo" alone is a request that asks). */
    private static final java.util.Set<String> EMB_ASK = set("bolo bolun jano janbe batao janio");
    private static final java.util.Set<String> FP = set("mujhe main maine hum humne ami amar amake मुझे मैं मैंने हम");
    private static final java.util.Set<String> HI_CUE = set("hai hain hoon ho tha thi hoga hogi honge main mera meri mere tum tumhara tumhari tumhare aap aapka aapki mujhe tumhe usne maine hum humne "
            + "nahi nahin yeh woh iska uska bahut kuch aur bhi lekin magar kyunki mein liye raha rahe rahi karna karo karenge jaunga aaunga jao aao");
    private static final java.util.Set<String> BN_CUE = set("ami tumi tui apni amar tomar amader tomader ache achi acho achen hobe korbo korchi korcho kore ta jabo jabe khabo khabe nei na");
    private static final java.util.Set<String> HI_TAG_PREV = set("hai hain ho tha thi the hoga hogi honge hai। है हैं हो था थी होगा होगी");

    /** A vowel sign of a Bangla / Hindi / other Indic word (these are not "letters" in Java, but a word can end on one). */
    private static boolean indicMark(char c) {
        int ty = Character.getType(c);
        return (ty == Character.NON_SPACING_MARK || ty == Character.COMBINING_SPACING_MARK) && c >= 0x0900 && c <= 0x0DFF;
    }

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

    /** True when a Bangla / Hindi question word stands after word i ("mubarak ho kab aaoge"). */
    private static boolean askedAfter(String[] w, int i) {
        for (int k = i + 1; k < w.length; k++) if (BN_Q_ANY.contains(w[k])) return true;
        return false;
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
        boolean agree = n == 3 && w0.equals("so") && (EN_AUX.contains(w1) || EN_DO.contains(w1)) && (w[2].equals("i") || w[2].equals("we"));     // "so do I", "so am I" agree: they do not ask
        if (n >= 3 && !agree && LEAD.contains(w0) && kind(restFrom(raw, 1), english) == '?') return '?';
        if (n >= 3 && GREETW.contains(w0) && kind(restFrom(raw, 1), english) == '?') return '?';          // "hello how are you"
        if (n >= 4 && GREETW.contains(w0) && kind(restFrom(raw, 2), english) == '?') return '?';          // "hi Tara how are you"
        boolean name1 = n > 1 && !raw[1].isEmpty() && Character.isUpperCase(raw[1].charAt(0)) && !w1.equals("i");
        // an address, a link or a long number at the end: no mark after it (it would become part of it)
        String lr = raw[n - 1];
        if (lr.indexOf('@') >= 0 || lr.indexOf('/') >= 0 || lr.matches("[0-9][0-9-]{4,}") || lr.matches("(?i)[a-z0-9_-]+(\\.[a-z0-9_-]{2,6})+")) return 0;
        // Banglish / Hinglish helpers
        boolean hindi = false;
        int emb = n;                                   // first "I know / say / see" word: question words after it belong to a statement
        if (!english) {
            boolean hi = false, bn = false, fp = false;
            for (int i = 0; i < n; i++) {
                if (HI_CUE.contains(w[i])) hi = true;
                if (BN_CUE.contains(w[i])) bn = true;
                if (emb == n && EMB.contains(w[i])) {
                    boolean needsI = w[i].equals("pata") || w[i].equals("पता") || w[i].equals("जानता") || w[i].equals("जानती") || w[i].equals("jaanta") || w[i].equals("jaanti") || w[i].equals("samajh") || w[i].equals("yaad") || w[i].equals("maaloom") || w[i].equals("malum");
                    boolean ok = !needsI || fp || (i > 0 && w[i - 1].equals("nahi")) || (i + 1 < n && w[i + 1].equals("nahi")) || w[i].equals("pata") && i == 0;
                    if (ok) emb = i;
                }
                if (emb == n && i > 0 && w[i].equals("jaane") && w[i - 1].equals("kaun")) emb = i - 1;   // "kaun jaane kya hoga" = who knows
                if (FP.contains(w[i])) fp = true;
            }
            hindi = hi && !bn;
        }
        // 1. a cheer, a wish, a greeting, "what a ..." is an exclamation
        if (n <= 4 && CHEER1.contains(w0)) return '!';
        if (n <= 4 && NAMEY.contains(w0) && PRAISE_NEXT.contains(w1)) return '!';                // a name: only "kamal hoyeche", never "Kamal kothay"
        if ((w0.equals("congratulations") || w0.equals("congrats")) && n <= 8) return '!';
        if ((w0.equals("happy") || w0.equals("happiest")) && n <= 6) return '!';
        if (w0.equals("welcome") && n <= 5) return '!';
        if (w0.equals("good") && n <= 6 && (w1.equals("morning") || w1.equals("afternoon") || w1.equals("evening") || w1.equals("night"))) {
            return n >= 4 && kind(restFrom(raw, 2), english) == '?' ? '?' : '!';           // "good morning how are you" asks
        }
        if (n <= 4 && ((w0.equals("well") && w1.equals("done")) || (w0.equals("get") && w1.equals("well")) || (w0.equals("best") && w1.equals("wishes")))) return '!';
        if (n >= 3 && n <= 5 && w0.equals("all") && w1.equals("the") && w[2].equals("best")) return '!';
        if (n >= 3 && n <= 8 && w0.equals("best") && w1.equals("of") && w[2].equals("luck")) return '!';   // "best of luck for tomorrow"
        if (n <= 6 && w0.equals("merry") && w1.equals("christmas")) return '!';
        if (w0.equals("what") && (w1.equals("a") || w1.equals("an")) && n <= 8) return '!';
        if (w0.equals("how") && n <= 7 && EN_ADJ.contains(w1) && (n == 2 || !(EN_AUX.contains(w[2]) || EN_DO.contains(w[2])))) return '!';   // "how nice of you"; not "how good is it"
        // WISH words work in every sentence (rare in plain English: mubarak, shubhechha)
        if (n <= 6) for (int i = 0; i < n; i++) {
            if (WISH.contains(w[i]) && !(!english && askedAfter(w, i))) return '!';      // not "mubarak ho kab aaoge" (a question word after the wish)
            if (!english && w[i].equals("abhinandan") && i > 0) return '!';
            if (!english && WISH_SHUBH.contains(w[i]) && ((n == 1) || (i + 1 < n && WISH_NOUN.contains(w[i + 1])))) return '!';   // names ("Subho kal ashche") no longer match
        }
        if (!english) {
            // "ki / koto / kya / kitna" + an appreciative word anywhere: "tomar bari ta ki sundor", "koto sundor dekhte", "kya sundar hai"
            for (int i = 0; i + 1 < n; i++) if (EXCL_Q.contains(w[i]) && ADJ_POS.contains(w[i + 1]) && n <= 8) return '!';
            for (int i = 0; i + 2 < n; i++) if (w[i].equals("koto") && (w[i + 1].equals("din") || w[i + 1].equals("bochhor")) && w[i + 2].equals("por")) return '!';
            for (int i = 0; i + 1 < n; i++) if (w[i].equals("koto") && (w[i + 1].equals("bhalobashi") || w[i + 1].equals("bhalobasi"))) return '!';
            if (n > 1 && n <= 5 && KI.contains(w0) && KI_EXCL.contains(w1)) return '!';          // "ki sundor"
            if (n > 1 && KYA.contains(w0) && (w1.equals("baat") || w1.equals("बात"))) {                                // "kya baat hai" yes; "kya baat kar rahe ho" no
                if (n == 2 || (n >= 3 && (w[2].equals("hai") || w[2].equals("है")))) return '!';
            } else if (n > 1 && n <= 5 && KYA.contains(w0) && KYA_EXCL.contains(w1)) return '!';
        }
        if (n > 1 && ((EN_DANGLE.contains(last) && !(!english && last.equals("the"))) || (n <= 3 && EN_AUXEND.contains(last)))) return 0;      // unfinished: no mark, not even "?" (Hindi "the" = were: "tum kahan the" is finished)
        if (english && n <= 3 && CLOSERS.contains(joinUnderscore(w))) return '.';                       // "thank you", "see you", "got it": short English closers end in a full stop
        // 2. a question
        if (WH_CONTR.contains(w0)) return '?';                                                                // "what's the plan", "how's it going", "where's the file"
        if (n >= 3 && (w0.equals("what") || w0.equals("where") || w0.equals("how") || w0.equals("when")) && w1.equals("you") && w[2].endsWith("ing")) return '?';     // "what you doing", "where you going"
        if (EN_WH.contains(w0) && (w0.equals("when") || w0.equals("where")) && n > 1 && !(EN_AUX.contains(w1) || EN_DO.contains(w1) || (!english && w1.equals("r")) || (n > 2 && WH_ADV.contains(w1) && (EN_AUX.contains(w[2]) || EN_DO.contains(w[2]))))) {
            // a statement that begins with when / where
        } else if (EN_WH.contains(w0)) {
            if (!EN_WH_PRON.contains(w1)) return '?';                                     // "how are you", "what time is it", "who is coming"
            // "when you reach home call me" is a statement; "what you are doing" is a question
            if ((w0.equals("what") || w0.equals("why") || w0.equals("how") || w0.equals("where")) && n > 2 && (EN_AUX.contains(w[2]) || EN_DO.contains(w[2]))) return '?';
        } else if (n > 1 && (EN_AUX.contains(w0) || EN_DO.contains(w0))) {
            // "can you send", "is the file ready"; not "do it now", "have a nice day", "was a great evening", "will send tomorrow". A capital name counts as a subject in English, and in mixed speech after did / can / will ...; not after is / do / are ("is Sunday ko ..." is Hinglish).
            if (auxSubject(w0, w1, name1 && (english || NAME_AUX.contains(w0))) && !(n > 2 && (w0.equals("don't") || w0.equals("do")) && IMPERATIVE.contains(w[2]))) return '?';
        }
        if (n >= 2 && NEG_AUX.contains(w[n - 2]) && TAG_PRON.contains(last)) return '?';   // "you will come, won't you"
        if (n >= 2 && raw[n - 2].endsWith(",") && (last.equals("right") || last.equals("ok") || last.equals("okay") || (!english && (last.equals("na") || last.equals("না") || last.equals("ना"))))) return '?';
        if (!english) {
            // Hindi tag: "theek hai na", "tum aaoge na"
            if (n >= 2 && (last.equals("na") || last.equals("ना")) && ((n >= 3 && HI_TAG_PREV.contains(w[n - 2])) || w[n - 2].endsWith("oge") || w[n - 2].endsWith("ogi") || w[n - 2].endsWith("enge") || w[n - 2].endsWith("ega") || w[n - 2].endsWith("ेगा") || w[n - 2].endsWith("ोगे"))) return '?';
            // statement ending in "... parchi na" / "... jani na": an embedded question before it belongs to the statement
            boolean taEnd = EMB_END.contains(last) || (n >= 3 && EMB.contains(last) && (w[n - 2].equals("na") || w[n - 2].equals("nai"))) || (n >= 3 && w[n - 2].equals("janiye") && (last.equals("dio") || last.equals("do") || last.equals("den") || last.equals("dena")));   // "kab aaoge ye batao", "tumi kokhon asbe janiye dio": tell-me clauses end on the verb
            for (int i = 1; i < n - 1; i++) if (w[i].equals("ta") && (EMB.contains(last) || EMB_ASK.contains(last))) taEnd = true;   // "kothay jabo ta bolo"
            boolean stmtEnd = taEnd || n >= 3 && last.equals("na") && (w[n - 2].equals("parchi") || w[n - 2].equals("pari") || w[n - 2].equals("parbo") || w[n - 2].equals("jani") || w[n - 2].equals("bujhi") || w[n - 2].equals("bujhlam"))
                    || n >= 3 && last.equals("না") && (w[n - 2].equals("জানি") || w[n - 2].equals("বুঝি") || w[n - 2].equals("পারি"));
            // 2nd particle at the very end always asks
            if (n >= 2 && (KI.contains(last) && !hindi || KYA.contains(last))) return '?';        // "tumi jabe ki" (NOT Hindi "main chahta hoon ki")
            if (n >= 2 && (last.equals("kina") || last.equals("naki") || last.equals("কিনা") || last.equals("নাকি") || last.equals("ke") || last.equals("কে"))) return '?';
            if (!stmtEnd) {
                for (int i = 0; i < n && i < emb; i++) {
                    if (BN_Q_ANY.contains(w[i])) {
                        if ((w[i].equals("kyun") || w[i].equals("kyu") || w[i].equals("kyon")) && i + 1 < n && w[i + 1].equals("ki")) continue;          // Hindi "kyun ki" = because
                        return '?';
                    }
                }
                if (!hindi) {
                    if (KI.contains(w0) || KYA.contains(w0)) return '?';
                    for (int i = 1; i <= 3 && i < n - 1 && i < emb; i++) if (KI.contains(w[i]) || KYA.contains(w[i])) return '?';   // "tumi ki khabe"; also long ones
                } else {
                    if (KYA.contains(w0) && emb == n) return '?';
                    for (int i = 1; i <= 3 && i < n - 1 && i < emb; i++) if (KYA.contains(w[i])) return '?';
                }
                if (!hindi && (w0.equals("ke") || w0.equals("kar") || w0.equals("কে") || w0.equals("কার"))) return '?';
            }
        }
        // 3. a plain statement: a whole sentence of 3+ words that does not end on a joining word
        // (so / that / then end a finished English sentence: "I think so", "I did that", "see you then")
        boolean open = OPEN.contains(last) && !(english && (last.equals("so") || last.equals("that") || last.equals("then")));
        if (!english && OPEN_BN.contains(last)) open = true;
        if (n >= 3 && !open) {
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
        if (!Character.isLetterOrDigit(lastCh) && !indicMark(lastCh)) return "";      // already ends in a mark, a comma, a bracket, an emoji ... (Bangla / Hindi words end in vowel signs, which are not "letters" in Java)
        char k = kind(t, english);
        return k == 0 ? "" : String.valueOf(k);
    }

    public static String markFor(String sentence) { return markFor(sentence, false); }
}
