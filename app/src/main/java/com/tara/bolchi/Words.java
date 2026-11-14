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
        synchronized (own) { if (own.contains(l)) return false; }
        return used(l) < 2;
    }
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
                "teh", "the", "adn", "and", "taht", "that", "thier", "their", "recieve", "receive",
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
        for (String x : new String[]{"hes", "fro", "si", "ti", "fo", "ot", "wat", "hav", "coz", "form", "meeting", "alright", "defiantly"}) FIXED.remove(x);
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
        if (r != null && r < 3000) return false;                      // "to", "main", "are" are English first
        return DESI.contains(l) || (r == null && looksBanglish(l));
    }

    /** True when the keyboard may swap the word in by itself: a known slip, or a word that is in no dictionary. A rare real word is only offered. */
    public static boolean sureFix(String typed) {
        String l = typed.toLowerCase(Locale.ROOT);
        return FIXED.containsKey(l) || !rank.containsKey(l);
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
                float miss = (j > 1 && b == c.charAt(j - 2)) ? 0.45f : b == '\'' ? 0.3f : 0.8f;
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
        String out = fix0(typed, autoCap);
        synchronized (FIXED) { cacheKey = key; cacheVal = out; }
        return out;
    }

    private static void forget() { synchronized (FIXED) { cacheKey = null; } }

    private static String fix0(String typed, boolean autoCap) {
        String w = typed.toLowerCase(Locale.ROOT);
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if (!(c >= 'a' && c <= 'z') && c != '\'') return null;
        }
        if (DESI.contains(w) || isContact(w) || isKept(w)) return null;   // only words she explicitly kept are left alone
        if (looksBanglish(w) && !rank.containsKey(w) && !FIXED.containsKey(w)) return null;   // Bangla/Hindi in English letters is not an English typo
        String f = FIXED.get(w);
        if (f != null) return f.equals(typed) ? null : (f.startsWith("I") ? f : matchCase(typed, f));
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
        return best == null ? null : matchCase(typed, best);
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
