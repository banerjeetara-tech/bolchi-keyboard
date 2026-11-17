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
    /** Words she explicitly kept (tapped her own spelling, or saved a name): never flagged. Words she merely typed a lot are NOT in here. */
    private static final Set<String> KEPT = new HashSet<>();

    public static void addKept(String w) {
        if (w == null || w.isEmpty()) return;
        synchronized (KEPT) { KEPT.add(w.toLowerCase(Locale.ROOT)); }
        forget();
    }

    public static String keptAsText() {
        StringBuilder b = new StringBuilder();
        synchronized (KEPT) { for (String w : KEPT) b.append(w).append(' '); }
        return b.toString().trim();
    }

    private static boolean isKept(String l) {
        synchronized (KEPT) { return KEPT.contains(l); }
    }

    /** True when the keyboard may change this word by itself; false for a word she has typed before (then it only prompts). */
    public static boolean autoReplaceOk(String typed) {
        String l = typed.toLowerCase(Locale.ROOT);
        // A known slip is always put right. Any other wrong word is put right too, unless she has written it so often (5 times or more)
        // that it is probably a word of her own; then the right word is only offered. Words she KEPT by hand never get here at all.
        if (PROPER.containsKey(l) || FIXED.containsKey(l)) return true;
        return used(l) < 5;
    }
    public static volatile boolean ready;
    /** Name parts from the phone book (lower case -> as written). Kept in memory only. */
    private static final java.util.TreeMap<String, String> CONTACT = new java.util.TreeMap<>();

    // ---------- what she writes: how often each word, and which word follows which (kept on her phone only) ----------
    private static final HashMap<String, Integer> USE = new HashMap<>();
    private static final HashMap<String, HashMap<String, Integer>> NEXT = new HashMap<>();

    /** Phrases: two words in a row -> the word that followed (key "first second"). Also kept on her phone only. */
    private static final HashMap<String, HashMap<String, Integer>> PHRASE = new HashMap<>();
    private static final int MAXCTX = 5;                  // a phrase is followed up to 5 words back
    private static final ArrayList<String> CHAIN = new ArrayList<>();     // the last words recorded in a row (a new sentence or a cursor jump starts it again)

    private static String join(List<String> w) {
        StringBuilder b = new StringBuilder();
        for (String x : w) { if (b.length() > 0) b.append(' '); b.append(x); }
        return b.toString();
    }

    /** She typed (or picked) `word` after `prev`: count the word, the pair, and the three-word phrase. */
    public static void record(String prev, String word) {
        if (word == null) return;
        String w = word.toLowerCase(Locale.ROOT);
        boolean tiny = w.equals("i") || w.equals("a");          // "I am going", "a lot": one-letter words belong to phrases
        if ((w.length() < 2 && !tiny) || w.length() > 20) return;
        synchronized (USE) {
            if (!tiny) { Integer c = USE.get(w); USE.put(w, c == null ? 1 : c + 1); }
        }
        link(prev, w);
    }

    /** A word written in Bengali / Hindi letters (typed on the ab->Bangla / Hindi keys or spoken as script): only the pair and phrase memory, no word count. */
    public static void recordSeq(String prev, String word) {
        if (word == null) return;
        String w = word.toLowerCase(Locale.ROOT);
        if (w.isEmpty() || w.length() > 24) return;
        link(prev, w);
    }

    private static boolean isScript(String w) { return !w.isEmpty() && w.charAt(0) >= '\u0900'; }

    private static void link(String prev, String w) {
        synchronized (USE) {
            String p = prev == null ? "" : prev.toLowerCase(Locale.ROOT);
            if (p.length() >= 2 || p.equals("i") || p.equals("a") || isScript(p)) {
                HashMap<String, Integer> m = NEXT.get(p);
                if (m == null) { m = new HashMap<>(); NEXT.put(p, m); }
                Integer n = m.get(w);
                m.put(w, n == null ? 1 : n + 1);
                if (CHAIN.isEmpty() || !CHAIN.get(CHAIN.size() - 1).equals(p)) { CHAIN.clear(); CHAIN.add(p); }
                // every length of phrase that ends with `p` (2 .. MAXCTX words) remembers which word came next
                for (int k = 2; k <= CHAIN.size(); k++) {
                    String key = join(CHAIN.subList(CHAIN.size() - k, CHAIN.size()));
                    HashMap<String, Integer> pm = PHRASE.get(key);
                    if (pm == null) { pm = new HashMap<>(); PHRASE.put(key, pm); }
                    Integer pn = pm.get(w);
                    pm.put(w, pn == null ? 1 : pn + 1);
                }
            } else CHAIN.clear();
            CHAIN.add(w);
            while (CHAIN.size() > MAXCTX) CHAIN.remove(0);
        }
    }

    /** What the keyboard does to a word when Auto-correct is OFF: only a capital letter (I, I'm, days, months, cities, apps), never another letter. Null when nothing to change. */
    public static String capitalOnly(String typed) {
        if (typed == null || typed.isEmpty()) return null;
        String l = typed.toLowerCase(Locale.ROOT);
        if (!typed.equals(l)) return null;                      // she typed a capital herself: leave it
        if (l.equals("i")) return "I";
        if (l.matches("i'(m|ll|ve|d)")) return "I" + l.substring(1);
        String p = PROPER.get(l);
        return p != null && !p.equals(typed) ? p : null;
    }

    private static int used(String l) {
        synchronized (USE) { Integer c = USE.get(l); return c == null ? 0 : c; }
    }

    /** Words she uses often that start with these letters, most used first. */
    private static int lev(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) cur[j] = Math.min(Math.min(cur[j - 1], prev[j]) + 1, prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    /**
     * Her own spelling of a Bengali / Hindi word, from memory: a word that sounds the same as one she uses a lot (same consonant sounds, at most 2 letters different, she used that one 3+ times and
     * has not used this spelling twice) is written the way SHE writes it ("korchhi" -> "korchi"). Null when nothing fits. English words are never touched.
     */
    public static String rememberedSpelling(String w) {
        if (!ready || w == null) return null;
        String l = w.toLowerCase(Locale.ROOT);
        if (l.length() < 4 || rank.containsKey(l) || DESI.contains(l) || isContact(l) || used(l) >= 2) return null;
        String sk = null, best = null;
        int bestUse = 0;
        synchronized (USE) {
            for (java.util.Map.Entry<String, Integer> e : USE.entrySet()) {
                String c = e.getKey();
                int u = e.getValue();
                if (u < 3 || u <= bestUse || c.length() < 4 || Math.abs(c.length() - l.length()) > 2 || c.charAt(0) != l.charAt(0) || rank.containsKey(c)) continue;
                if (sk == null) sk = shape(l, false)[0];
                if (!shape(c, false)[0].equals(sk) || lev(c, l) > 2) continue;
                best = c;
                bestUse = u;
            }
        }
        return best;
    }

    /**
     * The English word SHE uses (typed, spoken or saved, 2+ times) that a spoken Bengali spelling stands for: "biuti" / "beauti" -> "beauty". Only her own words are looked at (a short list), so two
     * consonant sounds are enough; the same consonant sounds, a vowel in the same places that fits, at most one letter longer or shorter, and at most 3 letters different.
     */
    public static String personalEnglish(String w) {
        if (!ready || w == null) return null;
        String l = w.toLowerCase(Locale.ROOT);
        if (l.length() < 4 || rank.containsKey(l) || DESI.contains(l) || isContact(l) || used(l) >= 2) return null;
        String[] r = shape(l, false);
        if (r[0].length() < 2) return null;
        String best = null;
        int bestUse = 0;
        synchronized (USE) {
            for (java.util.Map.Entry<String, Integer> e : USE.entrySet()) {
                String c = e.getKey();
                int u = e.getValue();
                if (u < 2 || u <= bestUse || c.length() < 4 || Math.abs(c.length() - l.length()) > 1 || c.charAt(0) != l.charAt(0) || !rank.containsKey(c)) continue;
                String[] en = shape(c, true);
                if (!en[0].equals(r[0]) || en[1].length() != r[1].length()) continue;
                boolean ok = true;
                for (int i = 0; ok && i < en[1].length(); i++) {
                    boolean lead = i == 0 && r[0].charAt(0) == 'a';
                    boolean bv = r[1].charAt(i) != '-' || lead, ev = en[1].charAt(i) != '-' || (i == 0 && en[0].charAt(0) == 'a');
                    if (bv != ev) ok = false;
                    else if (bv && !lead && !vowelFits(r[2].charAt(i), en[2].charAt(i))) ok = false;
                }
                if (!ok || lev(c, l) > 3) continue;
                best = c;
                bestUse = u;
            }
        }
        return best;
    }

    /** Makes sure the memory counts this word as used at least n times (idempotent: asking again does not add more). Used for the words she saves by hand. */
    public static void ensureUsed(String w, int n) {
        if (w == null) return;
        String l = w.toLowerCase(Locale.ROOT);
        if (l.length() < 2 || l.length() > 20) return;
        synchronized (USE) {
            Integer c = USE.get(l);
            if (c == null || c < n) USE.put(l, n);
        }
    }

    /** True when she has already saved this word (kept it by hand) or used it 3+ times. */
    public static boolean isSaved(String w) {
        if (w == null) return false;
        String l = w.toLowerCase(Locale.ROOT);
        return isKept(l) || used(l) >= 3;
    }

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

    /** Everyday English: the words that usually come next. Her own habits (NEXT) always come first. */
    private static final HashMap<String, String[]> USUAL = new HashMap<>();
    static {
        String t = "i:am will have|i'm:not going sorry|i'll:be call send|i've:been sent got|you:are can have|you're:welcome right not|we:are will have|"
            + "they:are will have|he:is was will|she:is was will|it:is was will|it's:a not ok|this:is week one|that:is was the|that's:great fine why|"
            + "there:is are was|here:is are you|what:is are do|what's:the up your|when:will are is|where:are is do|who:is are will|why:are is do|"
            + "how:are is much|which:is one are|am:i not going|is:it the not|are:you we they|was:a not the|were:you not the|be:there a able|"
            + "been:a to waiting|have:a to you|has:been to a|had:a to been|will:be call send|would:be you like|can:you i we|could:you be not|"
            + "should:i be we|shall:i we be|may:i be not|might:be not have|must:be have not|do:you not it|does:not it this|did:you not it|"
            + "don't:know worry have|can't:wait be come|won't:be come take|didn't:know get see|doesn't:matter work have|"
            + "thank:you god|thanks:for a and|please:send let check|pls:send call check|sorry:for i to|hello:sir dear how|hi:sir dear how|"
            + "dear:sir all team|good:morning night evening|happy:birthday to anniversary|many:thanks happy more|best:regards wishes of|"
            + "kind:regards attention of|warm:regards wishes welcome|with:regards the you|"
            + "let:me us you|let's:go meet do|see:you the if|talk:to later soon|call:me you back|send:me the it|give:me you a|tell:me him her|"
            + "ask:him her them|show:me the you|help:me you with|wait:for a till|come:to back here|go:to back there|get:the a back|"
            + "take:care a the|look:at forward into|looking:forward for at|make:sure it a|need:to a the|want:to a the|have:a to you|"
            + "going:to on out|coming:to back home|trying:to|able:to|used:to|supposed:to|according:to|due:to|thanks:for a and|"
            + "to:the be you|of:the this my|in:the a this|on:the my this|at:the home least|for:the you your|from:the my home|by:the tomorrow today|"
            + "the:same meeting file|a:lot few good|an:hour email update|my:phone name mother|your:name email reply|our:team office meeting|"
            + "and:the i then|but:i the not|or:not the else|so:i that we|if:you i possible|because:i of the|as:per soon well|then:i we you|"
            + "not:able sure yet|no:problem need issue|yes:please i sure|ok:thanks i sure|okay:thanks i sure|sure:i will thanks|"
            + "very:good much nice|too:much many late|so:much i that|much:better more appreciated|more:than details time|"
            + "last:week night month|next:week month time|this:week is month|every:day month time|per:month day our|"
            + "today:i is evening|tomorrow:morning i we|yesterday:i we evening|tonight:i we|now:i we|just:now a checked|"
            + "will:be call send|shall:i we be|kindly:check share confirm|find:attached the below|attached:herewith file is|"
            + "as:per soon well|per:our the your|soon:as|well:as|such:as|each:other|at:the home least|least:one|of:course the this|"
            + "right:now|after:that the lunch|before:that the lunch|during:the|about:the it this|into:the|over:the|under:the|between:the|"
            + "one:of more day|two:days more|few:days minutes more|some:time more of|all:the of is|any:update other time|"
            + "meeting:is with at|office:today tomorrow|home:now today|phone:number call|mobile:number|email:id address|"
            + "happy:birthday to anniversary|birthday:to wishes|new:year one|get:well the a|well:soon done|take:care a the|care:of|"
            + "love:you it|miss:you|how:are is much|are:you we they|fine:thanks and thank|nice:to day|great:day job work|"
            + "on:the my this|way:to|reach:home by|reached:home office safely|leaving:now office in|running:late|late:today|"
            + "on:the my this|waiting:for|check:and the with|share:the with|confirm:the|update:me the|reply:soon|forward:to the";
        for (String e : t.split("\\|")) {
            int k = e.indexOf(':');
            if (k > 0 && !USUAL.containsKey(e.substring(0, k))) USUAL.put(e.substring(0, k), e.substring(k + 1).split(" "));
        }
    }

    /** Names that are always written with a capital letter (days, months, places, languages). */
    private static final HashMap<String, String> PROPER = new HashMap<>();
    static {
        for (String n : ("Monday Tuesday Wednesday Thursday Friday Saturday Sunday January February April June July August September "
                + "October November December India Indian Kolkata Calcutta Delhi Mumbai Bangalore Bengaluru Chennai Hyderabad Pune Bengal "
                + "Bengali Bangla Hindi English British American America Europe Asia Africa Australia Dubai London Singapore "
                + "Google WhatsApp Facebook Instagram YouTube Gmail Samsung Android Zoom Outlook Excel Microsoft Amazon Flipkart "
                + "Diwali Durga Puja Holi Eid Christmas").split(" ")) PROPER.put(n.toLowerCase(Locale.ROOT), n);
    }

    private static void addCounted(List<String> out, HashMap<String, Integer> src, int need, int max) {
        if (src == null) return;
        final HashMap<String, Integer> m = new HashMap<>(src);
        List<String> keys = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> e : m.entrySet()) if (e.getValue() >= need) keys.add(e.getKey());
        java.util.Collections.sort(keys, new java.util.Comparator<String>() {
            @Override public int compare(String a, String b) { return m.get(b) - m.get(a); }
        });
        for (String k : keys) { if (out.size() >= max) break; if (!out.contains(k)) out.add(k); }
    }

    /**
     * The words she most often writes next: first what followed her last words in the LONGEST phrase she used twice or more (up to 5 words), then in shorter ones,
     * then what followed the last word alone (a pair seen twice), then everyday English.
     */
    public static List<String> nextAfter(String prev, int max) {
        List<String> out = new ArrayList<>();
        if (prev == null || prev.isEmpty()) return out;
        prev = prev.toLowerCase(Locale.ROOT);
        synchronized (USE) {
            // the longest phrase she used twice or more that ends with her last words comes first (up to 5 words back), then shorter ones, then the last word alone
            if (!CHAIN.isEmpty() && CHAIN.get(CHAIN.size() - 1).equals(prev)) {
                List<String> ctx = new ArrayList<>(CHAIN);
                for (int k = ctx.size(); k >= 2 && out.size() < max; k--) addCounted(out, PHRASE.get(join(ctx.subList(ctx.size() - k, ctx.size()))), 2, max);
            }
            addCounted(out, NEXT.get(prev), 2, max);
        }
        String[] u = USUAL.get(prev);
        if (u != null) for (String x : u) {
            if (out.size() >= max) break;
            if (!out.contains(x)) out.add(x);
        }
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
                    pairs.add(new String[]{e.getKey(), f.getKey(), String.valueOf(f.getValue())});      // also pairs seen once: the second time may come in another session
            java.util.Collections.sort(pairs, new java.util.Comparator<String[]>() {
                @Override public int compare(String[] a, String[] c) { return Integer.parseInt(c[2]) - Integer.parseInt(a[2]); }
            });
            for (int i = 0; i < pairs.size() && i < 5000; i++) b.append(pairs.get(i)[0]).append(' ').append(pairs.get(i)[1]).append(' ').append(pairs.get(i)[2]).append('\n');
            b.append("#phrases\n");
            List<String[]> ph = new ArrayList<>();
            for (java.util.Map.Entry<String, HashMap<String, Integer>> e : PHRASE.entrySet())
                for (java.util.Map.Entry<String, Integer> f : e.getValue().entrySet())
                    ph.add(new String[]{e.getKey(), f.getKey(), String.valueOf(f.getValue())});
            java.util.Collections.sort(ph, new java.util.Comparator<String[]>() {
                @Override public int compare(String[] a, String[] c) { return Integer.parseInt(c[2]) - Integer.parseInt(a[2]); }
            });
            for (int i = 0; i < ph.size() && i < 6000; i++) b.append(ph.get(i)[0]).append('\t').append(ph.get(i)[1]).append('\t').append(ph.get(i)[2]).append('\n');
        }
        return b.toString();
    }

    public static void importUsage(String text) {
        if (text == null || text.isEmpty()) return;
        synchronized (USE) {
            boolean pairs = false, phrases = false;
            for (String line : text.split("\n")) {
                if (line.equals("#pairs")) { pairs = true; phrases = false; continue; }
                if (line.equals("#phrases")) { pairs = false; phrases = true; continue; }
                String[] p = line.split(" ");
                try {
                    if (phrases) {
                        String[] t = line.indexOf('\t') >= 0 ? line.split("\t") : null;      // new format: "words<TAB>next<TAB>count"; the old one had exactly two words in front
                        String key, nx, cnt;
                        if (t != null && t.length == 3) { key = t[0]; nx = t[1]; cnt = t[2]; }
                        else if (p.length == 4) { key = p[0] + " " + p[1]; nx = p[2]; cnt = p[3]; }
                        else continue;
                        HashMap<String, Integer> pm = PHRASE.get(key);
                        if (pm == null) { pm = new HashMap<>(); PHRASE.put(key, pm); }
                        pm.put(nx, Integer.parseInt(cnt));
                    } else if (!pairs && !phrases && p.length == 2) USE.put(p[0], Integer.parseInt(p[1]));
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
        forget();
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
            + "bhaiya didi mummy papa chacha mausi dost ladka ladki yaar arre arey ji "
            + "bhat bhaat roti ruti dal mach maach jol pani chai tomar amake tomake oke take bolo dekhi dekha shuno aschi aschhi acchi "
            + "bhabchi bhabo bhabe bujhte bujhi bujhchi pari hoyechhe hocche gelo aashi ashbo thakchi diye niye theke kore kora kono konodin "
            + "shob sobai ekdom ekebare mato jonyo beshi kom aro aaro ebar eibar kivabe kibhabe era ora ekta duto tin char paanch pach choy "
            + "saat aat noy dosh taka kaj kaaj ghar bhalobasha valo bhaloi thikache thikai han naa nai khai khaichi khelchi dekhchi bolchhi "
            + "korchhi jacchhi aschhe jachhe dilo nilo elo gechhe hoyechhe korechhe bolechhe dekhechhe khechhe aar tahole tahle noile nahole "
            + "kalke ajke porshu shorir sharir sobar shobar gulo guli desh deshe jete jeo jao jai jay jaben jabona sokale bikele rate dupure "
            + "abar abaro mone monehoy theko thako thaki thaken thakis thake thakte korte bolte dekhte khete shunte ashte aste jante parte "
            + "nite dite hote uthte boshte likhte porte kinte bujhte ghumate cholo chole cholte cholche cholbe chol chal chalo chalte chale chalega "
            + "milte milke milkar milna mila mile mili kaisa kaisi kaise jaldi liya liye diya diye kiye gaye hue hua hui huye raho rehna "
            + "ratri shubho dhonnobad dhanyavad namaskar nomoshkar pronam asche ashche eseche esechi giyechi korechi porechi aneche anbo anbe "
            + "ke je se shey ora tara ei sei oi ota eta egulo ogulo ekhane okhane sekhane kothao kothay kotha kothai keu kauke kono konta "
            + "lok manush chele meye bor bou shami stri baccha bacha chhele dadu thakur didima boudi jamai mesho pishe kaku kakima jethu "
            + "office school college e te ta ti tar der ke re ra o to ba naki bujhi mane mone ichha ichhe iccha dorkar lagbe labh khoti "
            + "sundor shundor sundar bhari halka boro choto chhoto notun purono gorom thanda misti jhal tok nonta sada kalo lal nil holud sobuj "
            + "khabar ranna bazar dokan rasta gari bus tren pothe pore pora lekha likha bola kotha gaan naach khela chobi boi khata kolom "
            + "ektu ekta ekti duto dujon tinte onekta kotota etota otota jotota beshi kom thik bhul shotti mittha sotti mithya "
            + "achha acha arre are oma baba maago uff ish dhur dhat besh bah darun fatafati jompesh khasa mojar moja anondo dukkho kosto rag "
            + "mujhe mujhko tujhe tumhe tumko aapko humko unko usko isko inko kisko mujhse tumse aapse usse isse unse hamse "
            + "yeh ye woh wo voh vo yahan wahan yaha waha idhar udhar kidhar jab tab kab ab abhi kabhi sabhi tabhi phir fir firse "
            + "hoga hogi honge hota hoti hote hona hui hue raha rahi rahe rahta rehta karta karti karte karke karega karegi karenge "
            + "aata aati aate aaya aaye aayega aayegi jata jati jate jaata jaati jaye jaaye jayenge chalo chale chalte chalenge "
            + "milte milenge milega milegi milo dena lena dedo ledo dijiye lijiye kijiye boliye bataiye batao bata batana samjha samjhe samjho "
            + "pata nahi nai mat bas sirf bilkul zaroor zarur shayad lagta lagti laga lagi pasand pyaar pyar dil mann zindagi "
            + "accha achha bura sahi galat naya purana bada chota chhota lamba mota patla garam thanda meetha teekha "
            + "khana pani chai doodh roti sabzi chawal daal namak cheeni ghar kamra darwaza khidki gaadi sadak dukan paisa paise rupaye "
            + "aaj kal parso subah dopahar shaam raat din hafta mahina saal samay waqt der jaldi turant dheere "
            + "haan han ji nahin na arey oye yaar bhai behen beta beti bhabhi chachi nani dadi dada nana mama mami tau "
            + "kyunki isliye lekin magar parantu ya athva aur tatha bhi hi toh to ki ke ka ko se mein me pe par tak liye wala wali wale"
    ).split(" ")));

    /** Short fixes that are always right. */
    private static final HashMap<String, String> FIXED = new HashMap<>();
    static {
        String[] f = {"i", "I", "im", "I'm", "ive", "I've", "ill", "I'll", "id", "I'd",
                "dont", "don't", "doesnt", "doesn't", "didnt", "didn't", "cant", "can't", "wont", "won't",
                "isnt", "isn't", "arent", "aren't", "wasnt", "wasn't", "werent", "weren't", "havent", "haven't",
                "hasnt", "hasn't", "couldnt", "couldn't", "wouldnt", "wouldn't", "shouldnt", "shouldn't",
                "thats", "that's", "whats", "what's", "youre", "you're", "theyre", "they're", "lets", "let's",
                "teh", "the", "wil", "will",
                // chat short forms (see SHORT below)
                "wat", "what", "gud", "good", "gd", "good", "tonite", "tonight", "nite", "night", "mrng", "morning", "evng", "evening", "cuz", "because", "dnt", "don't", "cnt", "can't", "thn", "then", "whr", "where", "hw", "how", "nw", "now", "hv", "have", "hd", "had", "bt", "but", "nd", "and", "luv", "love", "sry", "sorry",
                "wlcm", "welcome", "bday", "birthday", "hpy", "happy", "offc", "office", "mtg", "meeting", "mtng", "meeting", "dcmnt", "document", "rprt", "report", "amt", "amount", "acnt", "account", "nmbr", "number", "adrs", "address", "tym", "time", "yr", "year", "wk", "week", "hrs", "hours", "gng", "going", "cmng", "coming", "dng", "doing", "gt", "get",
                "gv", "give", "u", "you", "ur", "your", "r", "are", "fr", "for", "ws", "was", "hs", "has", "bn", "been", "cm", "come", "okk", "ok", "fourty", "forty", "harrass", "harass", "supercede", "supersede", "tounge", "tongue", "sended", "sent", "posb", "possible", "psbl", "possible", "pbm", "problem", "prblm", "problem",
                "thanku", "thank you", "adn", "and", "taht", "that", "thier", "their", "recieve", "receive",
                "youll", "you'll", "youve", "you've", "youd", "you'd", "theyll", "they'll", "theyve", "they've",
                "weve", "we've", "hes", "he's", "shes", "she's", "heres", "here's", "theres", "there's",
                "wheres", "where's", "whos", "who's", "hows", "how's", "itll", "it'll", "thatll", "that'll",
                "mustnt", "mustn't", "neednt", "needn't", "aint", "ain't", "yall", "y'all", "oclock", "o'clock",
                // spellings people get wrong that are too far from the right word to be found by key slips
                "thr", "the", "ther", "there", "bac", "back", "sme", "some", "iam", "I am", "alot", "a lot", "aswell", "as well", "infact", "in fact", "atleast", "at least", "incase", "in case",
                "eachother", "each other", "thankyou", "thank you", "ofcourse", "of course", "noone", "no one",
                "alright", "alright", "allright", "all right", "tommorow", "tomorrow", "tomorow", "tomorrow", "tommorrow", "tomorrow",
                "tmrw", "tomorrow", "tmr", "tomorrow", "pls", "please", "plz", "please", "thx", "thanks", "thnx", "thanks",
                "thnks", "thanks", "tnx", "thanks", "msg", "message", "ppl", "people", "bcoz", "because", "bcz", "because",
                "becoz", "because", "coz", "because", "wat", "what", "wht", "what", "hav", "have", "abt", "about",
                "definately", "definitely", "definatly", "definitely", "defiantly", "definitely", "probly", "probably",
                "probaly", "probably", "realy", "really", "writting", "writing", "comming", "coming", "begining", "beginning",
                "beleive", "believe", "freind", "friend", "wierd", "weird", "peice", "piece", "acheive", "achieve",
                "neccessary", "necessary", "necesary", "necessary", "occured", "occurred", "occuring", "occurring",
                "seperate", "separate", "accomodate", "accommodate", "adress", "address", "bussiness", "business",
                "buisness", "business", "calender", "calendar", "collegue", "colleague", "goverment", "government",
                "immediatly", "immediately", "independant", "independent", "knowlege", "knowledge", "liason", "liaison",
                "maintainance", "maintenance", "maintenence", "maintenance", "recomend", "recommend", "reccomend", "recommend",
                "refered", "referred", "sucess", "success", "succesful", "successful", "successfull", "successful",
                "suprise", "surprise", "tommorows", "tomorrow's", "truely", "truly", "untill", "until", "wich", "which",
                "whith", "with", "wiht", "with", "becuase", "because", "beacuse", "because", "becasue", "because",
                "enviroment", "environment", "existance", "existence", "experiance", "experience", "embarass", "embarrass",
                "foward", "forward", "grammer", "grammar", "happend", "happened", "recieved", "received", "reciept", "receipt",
                "sincerly", "sincerely", "rember", "remember", "remeber", "remember", "frind", "friend", "regrads", "regards", "reagrds", "regards", "kindy", "kindly", "attatched", "attached",
                "attachement", "attachment", "aproval", "approval", "aprove", "approve", "confrim", "confirm",
                "meeting", "meeting", "meting", "meeting", "shedule", "schedule", "schedual", "schedule", "salery", "salary",
                "payement", "payment", "ammount", "amount", "recieving", "receiving", "plese", "please", "pleae", "please",
                "yuo", "you", "yuor", "your", "hte", "the", "nad", "and", "waht", "what", "whta", "what", "jsut", "just",
                "konw", "know", "knwo", "know", "liek", "like", "tiem", "time", "wrok", "work", "owrk", "work",
                "mroe", "more", "soem", "some", "tehm", "them", "tehy", "they", "thsi", "this", "tihs", "this",
                "form", "form", "fro", "for", "ot", "to", "si", "is", "ti", "it", "fo", "of", "nto", "not", "cna", "can",
                "ahve", "have", "hvae", "have", "wsa", "was", "aer", "are", "aslo", "also", "alos", "also"};
        for (int i = 0; i + 1 < f.length; i += 2) FIXED.put(f[i], f[i + 1]);
        // "ill", "id", "lets" are real words too: leave them alone
        FIXED.remove("ill"); FIXED.remove("id"); FIXED.remove("lets");
        // real words or Hindi words: leave them alone
        for (String x : new String[]{"hes", "fro", "si", "ti", "fo", "ot", "form", "meeting", "alright", "defiantly"}) FIXED.remove(x);
    }

    private static volatile int[] masks = new int[0];

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
            String[] arr = l.toArray(new String[0]);
            int[] mk = new int[arr.length];
            for (int i = 0; i < arr.length; i++) mk[i] = mask(arr[i]);
            masks = mk;
            list = arr;
        }
        forget();
        ready = true;
    }

    /** True when some ordinary English word starts with these letters (so predictions have something to offer). */
    public static boolean hasDictionaryStart(String prefix) {
        if (prefix == null || prefix.isEmpty()) return false;
        String p = prefix.toLowerCase(Locale.ROOT);
        String[] l = list;
        for (int i = 0; i < l.length; i++) if (l[i].startsWith(p)) return true;
        return false;
    }

    // ---------- English words written down by sound (the Bengali speech engine writes "myanejment" for "management") ----------
    private static volatile HashMap<String, List<String>> SOUND;

    /**
     * Sound shape of a word: [0] its consonant sounds in order (a leading vowel is "a"), [1] for each of them what follows:
     * 'v' a vowel, 'o' only the letter o (in Banglish that is often just the breath between two consonants), '-' nothing.
     * english = true reads English spelling by rough rules; false reads Banglish letters as the Bengali converter writes them.
     */
    static String[] shape(String word, boolean english) {
        String w = word.toLowerCase(Locale.ROOT);
        if (english) {
            // a silent last e: it still makes the c or g before it soft ("conference", "manage"), then it goes
            if (w.length() > 3 && w.endsWith("e") && "aeiouy".indexOf(w.charAt(w.length() - 2)) < 0) {
                char p2 = w.charAt(w.length() - 2);
                w = w.substring(0, w.length() - 2) + (p2 == 'c' ? "S" : p2 == 'g' ? "S" : String.valueOf(p2));
            }
            w = w.replace("ssion", "Xon").replace("gge", "je").replace("tion", "Xon").replace("sion", "Xon").replace("cian", "Xon").replace("ture", "Cer").replace("sch", "SK").replace("chn", "KN")
                    .replace("chr", "KR").replace("ph", "F").replace("gh", "").replace("ck", "K").replace("ch", "C").replace("sh", "X").replace("th", "T")
                    .replace("dg", "S").replace("qu", "K").replace("x", "KS");
            if (w.startsWith("kn") || w.startsWith("wr")) w = w.substring(1);
        } else {
            w = w.replace("bh", "V").replace("ph", "F").replace("sh", "X").replace("ch", "C");
        }
        StringBuilder k = new StringBuilder(), f = new StringBuilder(), q = new StringBuilder();     // q: the first vowel letter after each sound
        int n = w.length();
        for (int i = 0; i < n; i++) {
            char c = w.charAt(i);
            char next = i + 1 < n ? w.charAt(i + 1) : ' ';
            char o = 0;
            boolean vowel = false;
            if (c >= 'A' && c <= 'Z') o = c;
            else switch (c) {
                case 'a': case 'e': case 'i': case 'o': case 'u': case 'y': vowel = true; break;
                case 'c': o = !english ? 'K' : next == 'e' || next == 'i' || next == 'y' ? 'S' : 'K'; break;
                case 'g': o = english && (next == 'e' || next == 'i' || next == 'y') ? 'S' : 'G'; break;
                case 's': case 'z': case 'j': o = 'S'; break;
                case 'k': case 'q': o = 'K'; break;
                case 'f': o = 'F'; break;
                case 'v': o = 'V'; break;
                case 'b': case 'd': case 'l': case 'm': case 'n': case 'p': case 'r': case 't': o = Character.toUpperCase(c); break;
                default: break;                                   // w, h and anything else carry no sound here
            }
            if (vowel) {
                // in Banglish "ai" is the English long i ("fail" = file), "iu" is u ("iunit" = unit), "ou" is o
                char vq = english ? c : c == 'a' && next == 'i' ? 'i' : c == 'i' && next == 'u' ? 'u' : c;
                if (k.length() == 0) { k.append('a'); f.append('-'); q.append(vq); }
                else {
                    int at = f.length() - 1;
                    if (q.charAt(at) == '-') q.setCharAt(at, vq);
                    char was = f.charAt(at);
                    char now = c == 'o' && !english ? (was == 'v' ? 'v' : 'o') : 'v';
                    if (k.length() == 1 && k.charAt(0) == 'a') now = '-';       // still the leading vowel
                    f.setCharAt(at, now);
                }
            } else if (o != 0) {
                if (k.length() > 0 && k.charAt(k.length() - 1) == o && f.charAt(f.length() - 1) == '-') continue;     // a doubled letter
                k.append(o); f.append('-'); q.append('-');
            }
        }
        // a last o in Banglish is a spoken vowel, not a breath
        if (!english && f.length() > 0 && f.charAt(f.length() - 1) == 'o') f.setCharAt(f.length() - 1, 'v');
        return new String[]{k.toString(), f.toString(), q.toString()};
    }

    /**
     * The usual English word that sounds like these Banglish letters, or null. Only long words with a clear shape are matched
     * (6+ letters, 4+ consonant sounds, the vowels sitting in the same places), because short ones collide with real Bengali words.
     */
    private static volatile HashMap<String, List<String>> SOUND_SHORT;

    /** Which English vowel letters a Banglish vowel can stand for ("a" is never an English i, "i" never an a ...). */
    private static boolean vowelFits(char bn, char en) {
        switch (bn) {
            case 'a': return en == 'a' || en == 'u' || en == 'o' || en == 'e';
            case 'e': return en == 'e' || en == 'a';
            case 'i': return en == 'i' || en == 'e' || en == 'y';
            case 'o': return en == 'o' || en == 'a';
            case 'u': return en == 'u' || en == 'o' || en == 'e';
            default: return true;
        }
    }

    /**
     * Short words (4 or 5 Banglish letters: "topik", "print", "lebel"): only very usual English words, the same sounds, a vowel in exactly
     * the same places and each vowel a fitting one. Stricter than the long-word match, because short Bengali words look like English ones.
     */
    private static HashMap<String, List<String>> shortMap() {
        HashMap<String, List<String>> m = SOUND_SHORT;
        if (m == null) {
            m = new HashMap<>();
            String[] l = list;
            for (int i = 0; i < l.length && i < 8000; i++) {
                String w = l[i];
                if (w.length() < 4 || w.length() > 7 || w.indexOf('\'') >= 0 || DESI.contains(w)) continue;
                String k = shape(w, true)[0];
                if (k.length() < 3) continue;
                List<String> at = m.get(k);
                if (at == null) { at = new ArrayList<>(2); m.put(k, at); }
                if (at.size() < 6) at.add(w);
            }
            SOUND_SHORT = m;
        }
        return m;
    }

    private static HashMap<String, List<String>> longMap() {
        HashMap<String, List<String>> m = SOUND;
        if (m == null) {
            m = new HashMap<>();
            String[] l = list;
            for (int i = 0; i < l.length && i < 20000; i++) {
                String w = l[i];
                if (w.length() < 5 || w.indexOf('\'') >= 0 || DESI.contains(w)) continue;
                String k = shape(w, true)[0];
                if (k.length() < 4) continue;
                List<String> at = m.get(k);
                if (at == null) { at = new ArrayList<>(2); m.put(k, at); }
                if (at.size() < 4) at.add(w);                     // most usual first
            }
            SOUND = m;
        }
        return m;
    }

    /**
     * For a word she can tap and repair: English words that sound like these Banglish letters, nearest first (a typo fix, then every word with the
     * same consonant sounds; the vowels are not compared, because she chose to repair this word herself). Empty when nothing fits.
     */
    public static List<String> englishFor(String roman, int max) {
        List<String> out = new ArrayList<>();
        if (!ready || roman == null || roman.length() < 3) return out;
        String l = roman.toLowerCase(Locale.ROOT);
        String mine = personalEnglish(roman);
        if (mine != null) out.add(matchCase(roman, mine));       // an English word she uses herself comes first
        String fx = fix(roman);
        if (fx != null && !out.contains(fx)) out.add(fx);
        String k = shape(l, false)[0];
        List<String> a = k.length() >= 4 ? longMap().get(k) : null, b = k.length() >= 3 ? shortMap().get(k) : null;
        for (List<String> src : new List[]{a, b}) {
            if (src == null) continue;
            for (String w : src) {
                if (Math.abs(w.length() - l.length()) > 4) continue;
                String m = matchCase(roman, w);
                if (!out.contains(m)) out.add(m);
            }
        }
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    private static String soundAlikeShort(String roman) {
        HashMap<String, List<String>> m = shortMap();
        String[] r = shape(roman, false);
        if (r[0].length() < 3) return null;
        List<String> hits = m.get(r[0]);
        if (hits == null) return null;
        for (String hit : hits) {
            int d = hit.length() - roman.length();
            if (d > 2 || d < -1) continue;
            String[] e = shape(hit, true);
            boolean ok = e[1].length() == r[1].length();
            for (int i = 0; ok && i < e[1].length(); i++) {
                boolean bv = r[1].charAt(i) != '-' || (i == 0 && r[0].charAt(0) == 'a'), ev = e[1].charAt(i) != '-' || (i == 0 && e[0].charAt(0) == 'a');
                if (bv != ev) ok = false;
                else if (bv && !(i == 0 && r[0].charAt(0) == 'a') && !vowelFits(r[2].charAt(i), e[2].charAt(i))) ok = false;      // (a leading vowel is too loosely written to compare)
            }
            if (ok) return hit;
        }
        return null;
    }

    public static String soundAlike(String roman) {
        if (!ready || roman == null || roman.length() < 4) return null;
        if (roman.length() < 6) return soundAlikeShort(roman);
        String longHit = soundAlikeLong(roman);
        return longHit != null || roman.length() > 7 ? longHit : soundAlikeShort(roman);      // "tichar", "bhijit": six letters but only three sounds
    }

    private static String soundAlikeLong(String roman) {
        HashMap<String, List<String>> m = longMap();
        int half = roman.length() / 2;
        if (roman.length() % 2 == 0 && roman.substring(0, half).equals(roman.substring(half))) return null;      // "tuktuk": Bengali doubles words
        String[] r = shape(roman, false);
        if (r[0].length() < 4) return null;
        List<String> hits = m.get(r[0]);
        if (hits == null) return null;
        String second = null;
        for (String hit : hits) {
            int d = hit.length() - roman.length();
            if (d > 4 || d < -4) continue;
            String[] es = shape(hit, true);
            String ef = es[1];
            if (ef.length() != r[1].length()) continue;
            int extra = 0, odd = 0;
            boolean ok = true;
            for (int i = 0; i < ef.length() && ok; i++) {
                char b = r[1].charAt(i), e = ef.charAt(i);
                if (b == 'v' && e == '-') ok = false;             // a clear vowel in the spoken word where English has none: another word
                else if (b == '-' && e == 'v') extra++;           // English writes a vowel that is not spoken (management)
                else if (b == 'v' && e == 'v' && !(i == 0 && r[0].charAt(0) == 'a') && !vowelFits(r[2].charAt(i), es[2].charAt(i))) odd++;   // "fanding" is funding, not finding
            }
            if (odd > (roman.length() >= 9 ? 1 : 0)) ok = false;      // a long word may have one vowel written differently ("rikoyarment")
            if (!ok) continue;
            if (extra == 0) return hit;                           // the vowels sit in exactly the same places ("promotion", not "permission")
            if (extra == 1 && second == null) second = hit;
        }
        if (second != null) return second;
        return null;
    }

    /** True for a usual English word of 5+ letters that is not also Bangla / Hindi: on the Bangla / Hindi tab such a word is meant as English. */
    public static boolean clearlyEnglish(String w) {
        if (w == null || w.length() < 5) return false;
        String l = w.toLowerCase(Locale.ROOT);
        Integer r = rank.get(l);
        // "office", "phone" sit in the protected list too, but they are English first (a usual dictionary word)
        return r != null && r < (DESI.contains(l) ? 4000 : 10000) && !looksBanglish(l);
    }

    /** True for an ordinary English dictionary word (names and her own words are not). */
    public static boolean inDictionary(String w) {
        return w != null && rank.containsKey(w.toLowerCase(Locale.ROOT));
    }

    /** All words, most used first. */
    public static String[] all() { return list; }

    /** Ordinary English dictionary words that start with these letters (the keyboard's own predictions). */
    public static List<String> dictCompletions(String typed, int max) {
        List<String> out = new ArrayList<>();
        if (typed == null || typed.isEmpty()) return out;
        String p = typed.toLowerCase(Locale.ROOT);
        String[] l = list;
        for (int i = 0; i < l.length && out.size() < max; i++) {
            String w = l[i];
            if (w.length() > p.length() && w.startsWith(p)) out.add(matchCase(typed, w));
        }
        return out;
    }

    /** Words from HER memory that start with these letters: the ones she uses most, her learned words and her contacts. */
    public static List<String> memoryMatches(String typed, int max) {
        List<String> out = new ArrayList<>();
        if (typed == null || typed.isEmpty()) return out;
        String p = typed.toLowerCase(Locale.ROOT);
        for (String u : usedMatches(p, max)) { String m = matchCase(typed, u); if (!out.contains(m)) out.add(m); }
        synchronized (own) {
            for (String o : own) {
                if (out.size() >= max) break;
                if (o.length() > p.length() && o.startsWith(p)) { String m = matchCase(typed, o); if (!out.contains(m)) out.add(m); }
            }
        }
        for (String c : contactMatches(p, 1)) { if (out.size() >= max) break; String m = matchCase(typed, c); if (!out.contains(m)) out.add(m); }
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

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
        if (DESI.contains(l) || rank.containsKey(l) || isContact(l) || isKept(l)) return true;
        synchronized (own) { return own.contains(l); }
    }

    private static boolean isOwnOrDesi(String l) {
        if (DESI.contains(l) || isContact(l) || isKept(l)) return true;   // only words she explicitly kept are left alone
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

    /** Spellings that are typical of Bangla/Hindi in English letters (bhat, khabo, korchi, bhaat). */
    private static boolean looksBanglish(String w) {
        if (w.length() < 3) return false;
        if (w.matches("^(bh|kh|gh|jh|dh|chh|th[aeiou]h).*")) return true;                 // bhat, khabo, ghar, jhol, dhaka
        if (w.contains("aa")) return true;                                                // bhaat, khaabo
        return w.length() >= 5 && w.matches(".*(bo|bi|chi|cho|che|lam|ben|chhi|chho|chhe)$"); // khabo, korchi, bolcho, gelam
    }

    /** True when the word is Bangla/Hindi written in English letters (so she is not writing English just now). */
    public static boolean isDesi(String w) {
        if (w == null || w.isEmpty()) return false;
        String l = w.toLowerCase(Locale.ROOT);
        Integer r = rank.get(l);
        if (r != null && r < 15000) return false;                     // "to", "main", "are", "mat", "din" are English first
        return DESI.contains(l) || (r == null && looksBanglish(l));
    }

    /** Chat short forms ("u", "hv", "bt"). They are written out in English text, but right after Banglish / Hinglish they are only offered: "r", "k", "bt" are words there too. */
    private static final Set<String> SHORT = new HashSet<>(Arrays.asList(("hav coz wht abt becoz wat gud gd tonite nite mrng evng cuz dnt cnt thn whr hw nw hv hd bt nd luv sry wlcm bday hpy offc mtg mtng dcmnt rprt amt acnt nmbr adrs tym yr wk hrs gng cmng dng gt gv u ur r fr ws hs bn cm okk posb psbl pbm prblm").split(" ")));

    static {
        // Banglish words that sit one letter away from a usual English word: never "corrected", also between English words
        DESI.addAll(Arrays.asList(("giye chup kon besi yar adda mukh buk jabi jabo dilo nilo elo gelo holo kheye pore kore bole hoye boro choto sobar sobai kaaj kaj "
                + "tomar tomake amake tokhon ekhon bhai bhaiya didi dada boudi mashi pishi kaku jethu mama mami dosh noy").split(" ")));
    }

    /** True when two words differ by one inserted, missing or wrong letter, or two neighbouring letters swapped. */
    private static boolean oneEdit(String a, String b) {
        int n = a.length(), m = b.length();
        if (Math.abs(n - m) > 1) return false;
        int i = 0;
        while (i < n && i < m && a.charAt(i) == b.charAt(i)) i++;
        if (i == n && i == m) return true;
        if (n == m) {
            if (a.substring(i + 1).equals(b.substring(i + 1))) return true;                                            // one wrong letter
            return i + 1 < n && a.charAt(i) == b.charAt(i + 1) && a.charAt(i + 1) == b.charAt(i) && a.substring(i + 2).equals(b.substring(i + 2));   // swapped
        }
        return n < m ? a.substring(i).equals(b.substring(i + 1)) : a.substring(i + 1).equals(b.substring(i));        // one letter missing / extra
    }

    /**
     * True when a correction is so plain that it may be made even right after Banglish words: the typed word is one slip away from a VERY usual English word
     * (the first 2000 of the list: too, the, with, because) and does not look like Banglish. Everything less sure is still only offered.
     */
    public static boolean confidentFix(String typed, String fixed) {
        if (typed == null || fixed == null || typed.length() < 3 || fixed.indexOf(' ') >= 0) return false;
        String t = typed.toLowerCase(Locale.ROOT), f = fixed.toLowerCase(Locale.ROOT);
        Integer r = rank.get(f);
        if (r == null || r >= 2000) return false;
        if (looksBanglish(t) || DESI.contains(t) || isContact(t) || isKept(t)) return false;
        return oneEdit(t, f);
    }

    /** True for a spelling from the table of known English slips ("recieve", "teh", "dont"): always safe to put right. */
    public static boolean knownSlip(String typed) {
        if (typed == null) return false;
        String l = typed.toLowerCase(Locale.ROOT);
        return FIXED.containsKey(l) && !SHORT.contains(l);
    }

    /** True when the keyboard may swap the word in by itself: a known slip, or a word that is in no dictionary. A rare real word is only offered. */
    public static boolean sureFix(String typed) {
        String l = typed.toLowerCase(Locale.ROOT);
        return FIXED.containsKey(l) || PROPER.containsKey(l) || !rank.containsKey(l);
    }

    private static volatile String[] desiList;
    private static String[] desiSorted() {
        String[] d = desiList;
        if (d == null) {
            List<String> l = new ArrayList<>();
            for (String x : DESI) if (x.length() >= 3) l.add(x);
            java.util.Collections.sort(l);
            desiList = d = l.toArray(new String[0]);
        }
        return d;
    }

    /** Banglish / Hinglish words that start with these letters: her own often-used ones first, then the built-in list (shortest first). */
    public static List<String> desiCompletions(String typed, int max) {
        List<String> out = new ArrayList<>();
        if (typed == null || typed.length() < 2) return out;
        String p = typed.toLowerCase(Locale.ROOT);
        for (String u : usedMatches(p, 4)) {
            if (out.size() >= max) break;
            if (!rank.containsKey(u) && !out.contains(u)) out.add(u);
        }
        List<String> more = new ArrayList<>();
        for (String x : desiSorted()) if (x.length() > p.length() && x.startsWith(p) && !rank.containsKey(x)) more.add(x);
        java.util.Collections.sort(more, new java.util.Comparator<String>() {
            @Override public int compare(String a, String b) { return a.length() != b.length() ? a.length() - b.length() : a.compareTo(b); }
        });
        for (String x : more) { if (out.size() >= max) break; if (!out.contains(x)) out.add(x); }
        return out;
    }

    /** A Banglish / Hinglish word one finger slip away from what she typed ("korhci" gives "korchi"), or null. Only ever offered, never swapped in. */
    public static String desiFix(String typed) {
        if (typed == null || typed.length() < 4 || typed.length() > 20) return null;
        String w = typed.toLowerCase(Locale.ROOT);
        if (DESI.contains(w) || isKept(w) || isContact(w) || rank.containsKey(w) || used(w) >= 3) return null;
        int n = w.length();
        float max = n == 4 ? 0.85f : 1.0f, bestCost = 99f;
        float[][] d = new float[n + 1][n + 4];
        String best = null;
        List<String> pool = new ArrayList<>(Arrays.asList(desiSorted()));
        synchronized (USE) { for (java.util.Map.Entry<String, Integer> e : USE.entrySet()) if (e.getValue() >= 3 && !rank.containsKey(e.getKey())) pool.add(e.getKey()); }
        synchronized (KEPT) { pool.addAll(KEPT); }
        for (String c : pool) {
            int m = c.length();
            if (m < 3 || m - n > 2 || n - m > 2 || c.charAt(0) != w.charAt(0) || c.equals(w)) continue;
            float cost = slip(w, c, max, d);
            if (cost <= max && cost < bestCost) { bestCost = cost; best = c; }
        }
        return best;
    }

    private static final String[] ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
    private static final boolean[][] NEAR = new boolean[26][26];
    static {
        float[] off = {0f, 0.5f, 1.5f};
        for (int r = 0; r < 3; r++) for (int i = 0; i < ROWS[r].length(); i++)
            for (int q = 0; q < 3; q++) for (int j = 0; j < ROWS[q].length(); j++) {
                float dx = (i + off[r]) - (j + off[q]);
                if (Math.abs(r - q) <= 1 && Math.abs(dx) <= 1.1f && !(r == q && i == j))
                    NEAR[ROWS[r].charAt(i) - 'a'][ROWS[q].charAt(j) - 'a'] = true;
            }
    }
    private static boolean near(char x, char y) {
        return x >= 'a' && x <= 'z' && y >= 'a' && y <= 'z' && NEAR[x - 'a'][y - 'a'];
    }
    private static boolean vowel(char c) { return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y'; }
    private static int mask(String w) {
        int m = 0;
        for (int i = 0; i < w.length(); i++) { char c = w.charAt(i); if (c >= 'a' && c <= 'z') m |= 1 << (c - 'a'); }
        return m;
    }

    /**
     * How far the typed word is from a dictionary word, counted the way fingers slip: a neighbouring key, a letter doubled or
     * not doubled, two letters swapped and a wrong vowel cost less than an unrelated letter. Stops early above max.
     */
    private static float slip(String t, String c, float max, float[][] d) {
        int n = t.length(), m = c.length();
        for (int j = 0; j <= m; j++) d[0][j] = j * 0.9f;
        for (int i = 1; i <= n; i++) {
            char a = t.charAt(i - 1);
            d[i][0] = i * 0.9f;
            float low = d[i][0];
            for (int j = 1; j <= m; j++) {
                char b = c.charAt(j - 1);
                float sub = a == b ? 0f : near(a, b) ? 0.7f : (vowel(a) && vowel(b)) ? 0.8f : 1f;
                if (sub > 0 && i == 1 && j == 1) sub += 0.3f;                       // the first letter is usually right
                float v = d[i - 1][j - 1] + sub;
                // a letter typed too many: cheap when it repeats the one before, or is a neighbour key brushed on the way
                float extra = (i > 1 && a == t.charAt(i - 2)) ? 0.45f
                        : ((i > 1 && near(a, t.charAt(i - 2))) || (i < n && near(a, t.charAt(i)))) ? 0.8f : 0.9f;
                if (i == 1) extra += 0.3f;
                v = Math.min(v, d[i - 1][j] + extra);
                // a letter left out: cheap when it is the second of a double letter
                float miss = (j > 1 && b == c.charAt(j - 2)) ? 0.45f : b == '\'' ? 0.3f : 0.62f;     // a dropped letter is the commonest slip: cheaper than a neighbour key
                if (j == 1) miss += 0.3f;
                v = Math.min(v, d[i][j - 1] + miss);
                if (i > 1 && j > 1 && a == c.charAt(j - 2) && t.charAt(i - 2) == b && a != b)
                    v = Math.min(v, d[i - 2][j - 2] + 0.7f);                        // two letters swapped
                d[i][j] = v;
                if (v < low) low = v;
            }
            if (low > max) return 99f;
        }
        return d[n][m];
    }

    private static String cacheKey, cacheVal;

    /** The corrected word, or null when the typed word should stay. */
    public static String fix(String typed) { return fix(typed, false); }

    /**
     * The corrected word, or null when the typed word should stay.
     * autoCap: the capital first letter was put there by the keyboard (start of a sentence), so the word is not a name.
     */
    public static String fix(String typed, boolean autoCap) {
        if (typed == null || typed.isEmpty() || typed.length() > 20) return null;
        String key = autoCap ? "^" + typed : typed;
        synchronized (FIXED) { if (key.equals(cacheKey)) return cacheVal; }
        String out = fix0(typed, autoCap, false);
        synchronized (FIXED) { cacheKey = key; cacheVal = out; }
        return out;
    }

    /**
     * One-time clean-up. Older versions saved every unknown word she typed as "her word", typos included, and those were then never corrected.
     * Every kept word and every counted word that is really a slip of a dictionary word is forgotten (her saved names are put back by the keyboard).
     * Returns how many were dropped.
     */
    public static int cleanKept() {
        int n = 0;
        List<String> all;
        synchronized (KEPT) { all = new ArrayList<>(KEPT); }
        for (String k : all) {
            if (fix0(k, false, true) != null) { synchronized (KEPT) { KEPT.remove(k); } synchronized (own) { own.remove(k); } n++; }
        }
        List<String> seen;
        synchronized (USE) { seen = new ArrayList<>(USE.keySet()); }
        for (String k : seen) {
            if (rank.containsKey(k)) continue;
            if (fix0(k, false, true) != null) { synchronized (USE) { USE.remove(k); NEXT.remove(k); } n++; }
        }
        List<String> mine;
        synchronized (own) { mine = new ArrayList<>(own); }
        for (String k : mine) if (fix0(k, false, true) != null) { synchronized (own) { own.remove(k); } n++; }
        forget();
        return n;
    }

    private static void forget() { synchronized (FIXED) { cacheKey = null; } }

    private static String fix0(String typed, boolean autoCap, boolean ignoreKept) {
        String w = typed.toLowerCase(Locale.ROOT);
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if (!(c >= 'a' && c <= 'z') && c != '\'') return null;
        }
        if (DESI.contains(w) || isContact(w) || (!ignoreKept && isKept(w))) return null;   // only words she explicitly kept are left alone
        if (looksBanglish(w) && !rank.containsKey(w) && !FIXED.containsKey(w)) return null;   // Bangla/Hindi in English letters is not an English typo
        String f = FIXED.get(w);
        if (f != null) return f.equals(typed) ? null : (f.startsWith("I") ? f : matchCase(typed, f));
        String pn = PROPER.get(w);
        if (pn != null) return typed.equals(w) ? pn : null;                // monday -> Monday (only when typed all small)
        if (w.length() < 3) return null;
        // the tail of the word list holds misspellings too ("thr", "untill"): such a word only counts as right
        // when no far more usual word is one slip away
        Integer own0 = rank.get(w);
        if (own0 != null && own0 < 24000) return null;
        // a word she typed with a capital letter herself is most likely a name: never "correct" it
        boolean cap = Character.isUpperCase(typed.charAt(0));
        if (cap && !autoCap) return null;
        if (typed.length() > 1 && typed.equals(typed.toUpperCase(Locale.ROOT))) return null;
        if (autoCap && !typed.substring(1).equals(w.substring(1))) return null;      // McDonald, iPhone
        int n = w.length();
        // short words: one slip, and only into a common word. Longer words can hold two slips.
        float max = own0 != null ? 0.85f : n <= 4 ? 1.0f : n <= 6 ? 1.3f : n <= 8 ? 2.0f : 2.5f;
        int limit = own0 != null ? 150 : n <= 4 ? 3000 : n <= 7 ? 15000 : Integer.MAX_VALUE;
        String[] l = list;
        int[] mk = masks;
        int wm = mask(w);
        char w0 = w.charAt(0), w1 = w.charAt(1);
        float[][] d = new float[n + 1][n + 4];
        String best = null;
        float bestScore = 99f;
        for (int i = 0; i < l.length && i < limit; i++) {
            String c = l[i];
            int m = c.length();
            if (m < 3 || m - n > 2 || n - m > 2) continue;
            if (Integer.bitCount(wm ^ mk[i]) > 4) continue;
            char c0 = c.charAt(0);
            if (c0 != w0 && c0 != w1 && !near(c0, w0) && c.charAt(1) != w0) continue;
            float cost = slip(w, c, max, d);
            if (cost > max) continue;
            if (cost > 1.0f && c0 != w0) continue;                                   // two slips: the first letter must be right
            if (cost > 1.0f && i > 20000) continue;                                  // ...and the word not a rare one
            float score = cost + 0.16f * (float) Math.log10(i + 10);                 // among equals the more usual word wins
            if (score < bestScore) { bestScore = score; best = c; }
        }
        if (best == null && own0 == null) best = split(w);
        if (best == null) return null;
        String proper = typed.equals(w) ? PROPER.get(best) : null;          // "wensday" -> Wednesday, with its capital
        return proper != null ? proper : matchCase(typed, best);
    }

    /** Two everyday words typed without the space between them ("thankyou", "alot", "iam"). */
    private static String split(String w) {
        int n = w.length();
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        for (int i = 1; i <= n - 2; i++) {
            String a = w.substring(0, i), b = w.substring(i);
            if (i == 1 && !(a.equals("i") || a.equals("a"))) continue;
            if (i == 2 || (i > 1 && b.length() < 3)) continue;
            if (DESI.contains(a) || DESI.contains(b)) continue;
            Integer ra = rank.get(a), rb = rank.get(b);
            int cap = i == 1 ? 1000 : 3000;
            if (ra == null || rb == null || ra >= 3000 || rb >= cap) continue;
            int r = Math.max(ra, rb);
            if (r < bestRank) { bestRank = r; best = (a.equals("i") ? "I" : a) + " " + b; }
        }
        return best;
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
