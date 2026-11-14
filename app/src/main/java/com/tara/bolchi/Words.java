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
    /** Name parts from the phone book (lower case -> as written). Kept in memory only. */
    private static final java.util.TreeMap<String, String> CONTACT = new java.util.TreeMap<>();

    // ---------- what she writes: how often each word, and which word follows which (kept on her phone only) ----------
    private static final HashMap<String, Integer> USE = new HashMap<>();
    private static final HashMap<String, HashMap<String, Integer>> NEXT = new HashMap<>();

    /** She typed (or picked) `word` after `prev`: count both. */
    public static void record(String prev, String word) {
        if (word == null) return;
        String w = word.toLowerCase(Locale.ROOT);
        if (w.length() < 2 || w.length() > 20) return;
        synchronized (USE) {
            Integer c = USE.get(w);
            USE.put(w, c == null ? 1 : c + 1);
            if (prev != null && prev.length() >= 2) {
                HashMap<String, Integer> m = NEXT.get(prev);
                if (m == null) { m = new HashMap<>(); NEXT.put(prev, m); }
                Integer n = m.get(w);
                m.put(w, n == null ? 1 : n + 1);
            }
        }
    }

    private static int used(String l) {
        synchronized (USE) { Integer c = USE.get(l); return c == null ? 0 : c; }
    }

    /** Words she uses often that start with these letters, most used first. */
    public static List<String> usedMatches(String prefix, int max) {
        List<String> out = new ArrayList<>();
        if (prefix == null || prefix.isEmpty()) return out;
        String p = prefix.toLowerCase(Locale.ROOT);
        final HashMap<String, Integer> snapshot = new HashMap<>();
        synchronized (USE) {
            for (java.util.Map.Entry<String, Integer> e : USE.entrySet()) {
                if (e.getValue() >= 2 && e.getKey().length() > p.length() && e.getKey().startsWith(p)) snapshot.put(e.getKey(), e.getValue());
            }
        }
        List<String> keys = new ArrayList<>(snapshot.keySet());
        java.util.Collections.sort(keys, new java.util.Comparator<String>() {
            @Override public int compare(String a, String b) { return snapshot.get(b) - snapshot.get(a); }
        });
        for (int i = 0; i < keys.size() && i < max; i++) out.add(keys.get(i));
        return out;
    }

    /** The words she most often writes right after `prev` (needs to have happened twice). */
    public static List<String> nextAfter(String prev, int max) {
        List<String> out = new ArrayList<>();
        if (prev == null || prev.length() < 2) return out;
        final HashMap<String, Integer> m;
        synchronized (USE) {
            HashMap<String, Integer> src = NEXT.get(prev);
            if (src == null) return out;
            m = new HashMap<>(src);
        }
        List<String> keys = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> e : m.entrySet()) if (e.getValue() >= 2) keys.add(e.getKey());
        java.util.Collections.sort(keys, new java.util.Comparator<String>() {
            @Override public int compare(String a, String b) { return m.get(b) - m.get(a); }
        });
        for (int i = 0; i < keys.size() && i < max; i++) out.add(keys.get(i));
        return out;
    }

    /** One text to keep in the phone's settings: the most used words and word pairs. */
    public static String exportUsage() {
        StringBuilder b = new StringBuilder();
        synchronized (USE) {
            List<java.util.Map.Entry<String, Integer>> all = new ArrayList<>(USE.entrySet());
            java.util.Collections.sort(all, new java.util.Comparator<java.util.Map.Entry<String, Integer>>() {
                @Override public int compare(java.util.Map.Entry<String, Integer> a, java.util.Map.Entry<String, Integer> c) { return c.getValue() - a.getValue(); }
            });
            for (int i = 0; i < all.size() && i < 3000; i++) b.append(all.get(i).getKey()).append(' ').append(all.get(i).getValue()).append('\n');
            b.append("#pairs\n");
            List<String[]> pairs = new ArrayList<>();
            for (java.util.Map.Entry<String, HashMap<String, Integer>> e : NEXT.entrySet())
                for (java.util.Map.Entry<String, Integer> f : e.getValue().entrySet())
                    if (f.getValue() >= 2) pairs.add(new String[]{e.getKey(), f.getKey(), String.valueOf(f.getValue())});
            java.util.Collections.sort(pairs, new java.util.Comparator<String[]>() {
                @Override public int compare(String[] a, String[] c) { return Integer.parseInt(c[2]) - Integer.parseInt(a[2]); }
            });
            for (int i = 0; i < pairs.size() && i < 3000; i++) b.append(pairs.get(i)[0]).append(' ').append(pairs.get(i)[1]).append(' ').append(pairs.get(i)[2]).append('\n');
        }
        return b.toString();
    }

    public static void importUsage(String text) {
        if (text == null || text.isEmpty()) return;
        synchronized (USE) {
            boolean pairs = false;
            for (String line : text.split("\n")) {
                if (line.equals("#pairs")) { pairs = true; continue; }
                String[] p = line.split(" ");
                try {
                    if (!pairs && p.length == 2) USE.put(p[0], Integer.parseInt(p[1]));
                    else if (pairs && p.length == 3) {
                        HashMap<String, Integer> m = NEXT.get(p[0]);
                        if (m == null) { m = new HashMap<>(); NEXT.put(p[0], m); }
                        m.put(p[1], Integer.parseInt(p[2]));
                    }
                } catch (NumberFormatException ignored) { /* skip a damaged line */ }
            }
        }
    }

    private static final class Person {
        final String name;
        final String[] words;
        Person(String n) { name = n; words = n.toLowerCase(Locale.ROOT).split("[^a-z]+"); }
    }
    private static volatile List<Person> PEOPLE = new ArrayList<>();

    /** Full phone book names with a word that starts with these letters; names starting with them come first. */
    public static List<String> contactNamesFor(String prefix, int max) {
        List<String> first = new ArrayList<>(), later = new ArrayList<>();
        if (prefix == null || prefix.length() < 2) return first;
        String p = prefix.toLowerCase(Locale.ROOT);
        for (Person x : PEOPLE) {
            int hit = -1;
            for (int i = 0; i < x.words.length; i++) if (x.words[i].startsWith(p)) { hit = i; break; }
            if (hit == 0) first.add(x.name); else if (hit > 0) later.add(x.name);
            if (first.size() >= max) break;
        }
        first.addAll(later);
        return first.size() > max ? new ArrayList<>(first.subList(0, max)) : first;
    }

    public static void setContacts(java.util.Collection<String> names) {
        java.util.LinkedHashSet<String> uniq = new java.util.LinkedHashSet<>();
        for (String n : names) if (n != null && n.length() <= 40 && n.matches(".*[A-Za-z]{2}.*")) uniq.add(n.trim());
        List<Person> people = new ArrayList<>();
        for (String n : uniq) people.add(new Person(n));
        PEOPLE = people;
        java.util.TreeMap<String, String> m = new java.util.TreeMap<>();
        for (String n : names) {
            if (n == null) continue;
            for (String part : n.split("[^A-Za-z]+")) {
                if (part.length() < 3 || part.length() > 20) continue;
                String k = part.toLowerCase(Locale.ROOT);
                String old = m.get(k);
                if (old == null || (Character.isUpperCase(part.charAt(0)) && !Character.isUpperCase(old.charAt(0)))) m.put(k, part);
            }
        }
        synchronized (CONTACT) { CONTACT.clear(); CONTACT.putAll(m); }
    }

    /** Phone book names that start with these letters, as written in the phone book. */
    public static List<String> contactMatches(String prefix, int max) {
        List<String> out = new ArrayList<>();
        if (prefix == null || prefix.length() < 2) return out;
        String p = prefix.toLowerCase(Locale.ROOT);
        synchronized (CONTACT) {
            for (java.util.Map.Entry<String, String> e : CONTACT.tailMap(p).entrySet()) {
                if (!e.getKey().startsWith(p)) break;
                if (e.getKey().length() > p.length()) out.add(e.getValue());
                if (out.size() >= max) break;
            }
        }
        return out;
    }

    private static boolean isContact(String l) {
        synchronized (CONTACT) { return CONTACT.containsKey(l); }
    }

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
        if (DESI.contains(l) || rank.containsKey(l) || isContact(l)) return true;
        synchronized (own) { return own.contains(l); }
    }

    private static boolean isOwnOrDesi(String l) {
        if (DESI.contains(l) || isContact(l) || used(l) >= 3) return true;   // words she keeps using are hers
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
        // words she uses a lot come first, then her own learned words
        for (String u : usedMatches(p, 2)) {
            if (out.size() >= max - 1) break;
            String shown = matchCase(typed, u);
            if (!out.contains(shown)) out.add(shown);
        }
        synchronized (own) {
            for (String s : own) {
                if (out.size() >= max - 1) break;
                if (s.length() > p.length() && s.startsWith(p)) out.add(matchCase(typed, s));
            }
        }
        // then names from her phone book (at most two), before the ordinary dictionary words
        for (String c : contactMatches(p, 2)) {
            if (out.size() >= max - 1) break;
            String shown = matchCase(typed, c);
            if (!out.contains(shown)) out.add(shown);
        }
        String[] l = list;
        for (int i = 0; i < l.length && out.size() < max; i++) {
            String s = l[i];
            if (s.length() > p.length() && s.startsWith(p) && !out.contains(matchCase(typed, s))) out.add(matchCase(typed, s));
        }
        return out;
    }
}
