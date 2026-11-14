package com.tara.bolchi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * English word list for suggestions and auto-correct (pure Java).
 * assets/words.txt holds common English words, most used first (from the wordfreq project, CC BY-SA 4.0).
 */
public final class Words {
    private Words() { }

    private static String[] list = new String[0];
    private static final HashMap<String, Integer> rank = new HashMap<>();
    private static final Set<String> own = new HashSet<>();
    public static volatile boolean ready;

    /** Common Banglish / Hinglish words that must never be "corrected" into English. */
    private static final Set<String> DESI = new HashSet<>(Arrays.asList((
            "ami tumi tui apni amra tomra tara amar tomar tor apnar amader tomader oder eta ota ei oi ki keno kothay kokhon "
            + "kemon kobe ke kar kake ache achi acho achen nei na haan hyan thik accha acha bhalo bhalobashi kharap khub ektu "
            + "onek sob kichu kichhu ekhon tokhon aaj aj kal porshu din raat rat sokal bikel dupur sondhe bari ghor baire "
            + "jabo jabe jabi jacchi jacche gelo gechi gelam elam eso esho asho ashbo ashbe ashchi korbo korbe korchi korcho "
            + "korche korlam korlo koro kori kore khabo khabe khacchi kheyechi khelam bolo bolbo bolbe bolchi bollo bollam "
            + "dekho dekhbo dekhchi dekhlam shono shunchi jani jano jane janina hobe hoyeche hoye hocche holo hoy chilo "
            + "chilam thako thakbo thakbe dao debo dibo dilam nao nebo nibo nilam parbo parbe pari paro parina lagbe lagche "
            + "lage chai chao chay kintu ar aar ba tai tobe jodi karon jonno sathe shathe kache theke diye niye moto "
            + "dada didi bhai bon maa baba kaka mama mashi pishi dida thakuma bondhu chele meye bou "
            + "main mai tum aap hum mera meri mere tera teri tumhara aapka hamara kya kyun kyu kaise kab kahan kaun kiska "
            + "hai hain ho hoon hu tha thi the nahi nahin haa accha theek thik bahut thoda sab kuch abhi phir fir "
            + "kal parso raat subah shaam ghar bahar jaunga jaungi jayega jayegi gaya gayi aaya aayi aao jao karo karna "
            + "karunga karungi kiya kar raha rahi rahe khana khaya bolo bola dekho dekha suno suna pata chahiye sakta "
            + "sakti lekin aur ya toh to agar kyunki liye saath paas se ko ka ki ke mein par bhi hi wala wali "
            + "bhaiya didi mummy papa chacha mausi dost ladka ladki yaar arre arey ji"
    ).split(" ")));

    /** Short fixes that are always right. */
    private static final HashMap<String, String> FIXED = new HashMap<>();
    static {
        String[] f = {"i", "I", "im", "I'm", "ive", "I've", "ill", "I'll", "id", "I'd",
                "dont", "don't", "doesnt", "doesn't", "didnt", "didn't", "cant", "can't", "wont", "won't",
                "isnt", "isn't", "arent", "aren't", "wasnt", "wasn't", "werent", "weren't", "havent", "haven't",
                "hasnt", "hasn't", "couldnt", "couldn't", "wouldnt", "wouldn't", "shouldnt", "shouldn't",
                "thats", "that's", "whats", "what's", "youre", "you're", "theyre", "they're", "lets", "let's",
                "teh", "the", "adn", "and", "taht", "that", "thier", "their", "recieve", "receive"};
        for (int i = 0; i + 1 < f.length; i += 2) FIXED.put(f[i], f[i + 1]);
        // "ill", "id", "lets" are real words too: leave them alone
        FIXED.remove("ill"); FIXED.remove("id"); FIXED.remove("lets");
    }

    public static void load(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        List<String> l = new ArrayList<>(32000);
        HashMap<String, Integer> m = new HashMap<>(64000);
        String s;
        while ((s = r.readLine()) != null) {
            s = s.trim();
            if (s.isEmpty() || m.containsKey(s)) continue;
            m.put(s, l.size());
            l.add(s);
        }
        r.close();
        synchronized (rank) {
            rank.clear();
            rank.putAll(m);
            list = l.toArray(new String[0]);
        }
        ready = true;
    }

    /** All words, most used first. */
    public static String[] all() { return list; }

    /** Her own learned words that start with these letters (for the Bangla/Hindi typing rows). */
    public static List<String> ownMatches(String prefix, int max) {
        List<String> out = new ArrayList<>();
        if (prefix == null || prefix.isEmpty()) return out;
        String p = prefix.toLowerCase(Locale.ROOT);
        synchronized (own) {
            for (String s : own) {
                if (out.size() >= max) break;
                if (s.length() > p.length() && s.startsWith(p)) out.add(s);
            }
        }
        return out;
    }

    /** Words the user wants kept as typed. */
    public static void addOwn(String w) {
        if (w == null || w.isEmpty()) return;
        synchronized (own) { own.add(w.toLowerCase(Locale.ROOT)); }
    }

    public static String ownAsText() {
        StringBuilder b = new StringBuilder();
        synchronized (own) { for (String w : own) b.append(w).append(' '); }
        return b.toString().trim();
    }

    public static boolean known(String w) {
        String l = w.toLowerCase(Locale.ROOT);
        if (DESI.contains(l) || rank.containsKey(l)) return true;
        synchronized (own) { return own.contains(l); }
    }

    private static boolean isOwnOrDesi(String l) {
        if (DESI.contains(l)) return true;
        synchronized (own) { return own.contains(l); }
    }

    /** True for very common real words (so "to tomorrow" is not taken for a stammer). */
    public static boolean common(String w) {
        String l = w.toLowerCase(Locale.ROOT);
        if (isOwnOrDesi(l)) return true;
        Integer r = rank.get(l);
        return r != null && r < 1500;
    }

    /** Gives the word the same capital letters as the typed one. */
    public static String matchCase(String typed, String cand) {
        if (cand.equals("i") || cand.startsWith("i'")) return "I" + cand.substring(1);
        if (typed.length() > 0 && Character.isUpperCase(typed.charAt(0))) {
            if (typed.length() > 1 && typed.equals(typed.toUpperCase(Locale.ROOT))) return cand.toUpperCase(Locale.ROOT);
            return Character.toUpperCase(cand.charAt(0)) + cand.substring(1);
        }
        return cand;
    }

    /** The corrected word, or null when the typed word should stay. */
    public static String fix(String typed) {
        if (typed == null || typed.isEmpty() || typed.length() > 20) return null;
        String w = typed.toLowerCase(Locale.ROOT);
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if (!(c >= 'a' && c <= 'z') && c != '\'') return null;
        }
        if (isOwnOrDesi(w)) return null;
        String f = FIXED.get(w);
        if (f != null) return f.equals(typed) ? null : (f.startsWith("I") ? f : matchCase(typed, f));
        if (w.length() < 3 || rank.containsKey(w)) return null;
        // a word typed with a capital letter is most likely a name: never "correct" it (common typos like Teh are in FIXED above)
        if (typed.length() >= 3 && Character.isUpperCase(typed.charAt(0))) return null;
        if (typed.length() > 1 && typed.equals(typed.toUpperCase(Locale.ROOT))) return null;
        int limit = w.length() <= 4 ? 5000 : Integer.MAX_VALUE;
        String best = null;
        int bestRank = limit;
        char[] a = w.toCharArray();
        int n = a.length;
        StringBuilder sb = new StringBuilder();
        // swapped neighbours
        for (int i = 0; i + 1 < n; i++) {
            char t = a[i]; a[i] = a[i + 1]; a[i + 1] = t;
            Integer r = rank.get(new String(a));
            if (r != null && r < bestRank) { bestRank = r; best = new String(a); }
            t = a[i]; a[i] = a[i + 1]; a[i + 1] = t;
        }
        // one letter too many
        for (int i = 0; i < n; i++) {
            sb.setLength(0);
            sb.append(w, 0, i).append(w, i + 1, n);
            String c = sb.toString();
            Integer r = rank.get(c);
            if (r != null && c.length() >= 3 && r < bestRank) { bestRank = r; best = c; }
        }
        // one wrong letter (the first letter is trusted)
        for (int i = 1; i < n; i++) {
            char keep = a[i];
            for (char ch = 'a'; ch <= 'z'; ch++) {
                if (ch == keep) continue;
                a[i] = ch;
                Integer r = rank.get(new String(a));
                if (r != null && r < bestRank) { bestRank = r; best = new String(a); }
            }
            a[i] = keep;
        }
        // one letter missing
        for (int i = 1; i <= n; i++) {
            for (char ch = 'a'; ch <= 'z' + 1; ch++) {
                char put = ch > 'z' ? '\'' : ch;
                sb.setLength(0);
                sb.append(w, 0, i).append(put).append(w, i, n);
                Integer r = rank.get(sb.toString());
                if (r != null && r < bestRank) { bestRank = r; best = sb.toString(); }
            }
        }
        return best == null ? null : matchCase(typed, best);
    }

    /** Most used words that start with what has been typed. */
    public static List<String> complete(String typed, int max) {
        List<String> out = new ArrayList<>();
        if (typed == null || typed.isEmpty()) return out;
        String p = typed.toLowerCase(Locale.ROOT);
        // her own words first, then the common English ones
        synchronized (own) {
            for (String s : own) {
                if (out.size() >= max - 1) break;
                if (s.length() > p.length() && s.startsWith(p)) out.add(matchCase(typed, s));
            }
        }
        String[] l = list;
        for (int i = 0; i < l.length && out.size() < max; i++) {
            String s = l[i];
            if (s.length() > p.length() && s.startsWith(p) && !out.contains(matchCase(typed, s))) out.add(matchCase(typed, s));
        }
        return out;
    }
}
