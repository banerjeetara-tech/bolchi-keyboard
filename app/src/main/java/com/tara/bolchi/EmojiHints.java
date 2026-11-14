package com.tara.bolchi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;

/**
 * Emoji suggestions for a typed word ("family" gives a family emoji).
 * assets/emoji.txt: one line per word, the word then its emojis, best first.
 * Built from the Emojibase data (MIT licence).
 */
public final class EmojiHints {
    private EmojiHints() { }

    private static final HashMap<String, String[]> MAP = new HashMap<>();
    public static volatile boolean ready;

    public static void load(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        HashMap<String, String[]> m = new HashMap<>(8192);
        String line;
        while ((line = r.readLine()) != null) {
            String[] p = line.trim().split(" ");
            if (p.length < 2) continue;
            String[] em = new String[p.length - 1];
            System.arraycopy(p, 1, em, 0, em.length);
            m.put(p[0], em);
        }
        r.close();
        String[][] extra = {{"ok", "\uD83D\uDC4D", "\uD83D\uDC4C"}, {"no", "\u274C", "\uD83D\uDE45"}, {"yes", "\u2705", "\uD83D\uDC4D"},
                {"brother", "\uD83D\uDC66", "\uD83D\uDC68"}, {"sister", "\uD83D\uDC67", "\uD83D\uDC69"},
                {"urgent", "⚠️", "🚨"}, {"important", "❗", "⚠️"}, {"careful", "⚠️", "👀"},
                {"deadline", "⏰", "⌛"}, {"problem", "⚠️", "😟"}, {"issue", "⚠️", "😟"},
                {"road", "🛣️", "🚗"}, {"india", "🇮🇳", "🙏"}, {"key", "🔑", "🗝️"},
                {"new", "🆕", "✨"}, {"top", "🔝", "👍"}, {"up", "⬆️", "👆"}, {"back", "🔙", "⬅️"}};
        for (String[] e : extra) if (!m.containsKey(e[0])) m.put(e[0], new String[]{e[1], e[2]});
        synchronized (MAP) { MAP.clear(); MAP.putAll(m); }
        ready = true;
    }

    /** Short forms, feelings and Banglish / Hinglish words, each pointing at the English word whose emojis fit. */
    private static final HashMap<String, String> ALIAS = new HashMap<>();
    static {
        String t = "lol:laugh haha:laugh hahaha:laugh hehe:laugh lmao:laugh rofl:laugh hihi:laugh thx:thanks thanku:thanks ty:thanks thnx:thanks "
            + "congrats:congratulations congratulation:congratulations bday:birthday gm:morning gn:night okay:ok okk:ok okie:ok kk:ok yep:yes yeah:yes "
            + "yup:yes nope:no pls:please plz:please luv:love lovely:love omg:surprised wow:surprised oops:sorry yay:celebrate hurray:celebrate "
            + "yummy:delicious tasty:delicious awesome:great superb:great amazing:great nice:good cool:good bye:wave hello:wave hi:wave hey:wave "
            + "tired:sleepy angry:angry mad:angry scared:fear worried:worried bored:bored sick:sick fever:sick ill:sick office:office "
            + "bhalobashi:love bhalobasha:love bhalobasi:love pyaar:love pyar:love prem:love mohabbat:love ishq:love dil:heart mon:heart "
            + "dhonnobad:thanks dhanyavad:thanks dhanyabad:thanks shukriya:thanks namaskar:pray nomoshkar:pray pronam:pray namaste:pray "
            + "khushi:happy khusi:happy anondo:happy anando:happy moja:happy maza:happy dukkho:sad dukho:sad kosto:sad dukh:sad udaas:sad "
            + "kanna:cry kandchi:cry rona:cry hashi:laugh hasi:laugh hasna:laugh hashchi:laugh rag:angry gussa:angry raag:angry "
            + "bhoy:fear darr:fear dar:fear ghum:sleep ghumacchi:sleep neend:sleep sona:sleep khabar:food khana:food khabo:food khacchi:food "
            + "bhat:rice bhaat:rice chawal:rice chai:tea cha:tea coffee:coffee jol:water pani:water mishti:sweet misti:sweet mithai:sweet "
            + "mach:fish maach:fish machh:fish machli:fish murgi:chicken dim:egg anda:egg doodh:milk dudh:milk aam:mango kola:banana kela:banana "
            + "phul:flower phool:flower gach:tree ped:tree brishti:rain bristi:rain barish:rain baarish:rain rod:sun dhoop:sun surjo:sun suraj:sun "
            + "chand:moon chaand:moon tara:star sitara:star akash:sky megh:cloud badal:cloud thanda:cold gorom:hot garam:hot "
            + "bari:house ghar:house basha:house gari:car gaadi:car tren:train bus:bus biman:airplane plane:airplane "
            + "maa:mother mummy:mother mom:mother baba:father papa:father dad:father bhai:brother dada:brother didi:sister bon:sister behen:sister "
            + "bondhu:friend dost:friend yaar:friend baccha:baby bacha:baby chele:boy meye:girl ladka:boy ladki:girl "
            + "biye:wedding shaadi:wedding shadi:wedding jonmodin:birthday janamdin:birthday puja:pray pujo:pray diwali:fireworks holi:rainbow eid:moon "
            + "taka:money paisa:money rupee:money rupees:money kaj:work kaaj:work kaam:work chhuti:holiday chuti:holiday chutti:holiday "
            + "gaan:music gana:music naach:dance nach:dance khela:game khel:game cinema:movie boi:book kitab:book phone:phone "
            + "kukur:dog kutta:dog biral:cat billi:cat pakhi:bird chidiya:bird goru:cow gai:cow bagh:tiger hati:elephant haathi:elephant "
            + "shubho:celebrate badhai:congratulations obhinondon:congratulations shabash:clap sabash:clap darun:great khub:good bhalo:good accha:ok "
            + "achha:ok thik:ok theek:ok haan:yes hyan:yes na:no nahi:no sorry:sorry maaf:sorry khoma:sorry chumu:kiss kiss:kiss jhappi:hug"
            + " meet:handshake msg:envelope reply:envelope report:chart doc:page salary:money payment:money price:money budget:money profit:chart sale:chart sales:chart"
            + " finished:check approved:check approve:check confirm:check confirmed:check right:check wrong:cross rejected:cross today:calendar tomorrow:calendar schedule:calendar week:calendar"
            + " month:calendar team:handshake welcome:handshake pc:computer online:globe website:globe find:search say:speech chat:speech discuss:speech tour:airplane vacation:beach"
            + " leave:beach hill:mountain rest:sleep wake:alarm meal:food biryani:rice roti:bread icecream:ice enjoy:party fun:party tablet:pill cough:sneeze"
            + " match:trophy lose:sad college:school read:book exam:pencil write:pencil learn:book homework:book result:chart shop:shopping buy:shopping bought:shopping"
            + " saree:sari jewellery:gem blessings:pray excellent:thumbs beautiful:sparkles gorgeous:sparkles sweetheart:heart dear:heart darling:heart hate:angry rainy:rain storm:lightning"
            + " noon:sun afternoon:sun midnight:moon husband:man wife:woman uncle:man aunt:woman marriage:wedding pic:camera picture:camera song:music songs:music"
            + " tv:television green:plant kolkata:city village:house safe:lock start:rocket begin:rocket number:numbers";
        for (String e : t.split(" ")) {
            int k = e.indexOf(':');
            if (k > 0) ALIAS.put(e.substring(0, k), e.substring(k + 1));
        }
    }

    /**
     * The emoji word this typed word stands for, or null: the word itself, a short form or Banglish/Hinglish word
     * ("lol", "bhalobashi"), or its plain form ("cakes", "laughing", "loved", "parties").
     */
    public static String key(String word) {
        if (!ready || word == null || word.length() < 2) return null;
        String w = word.toLowerCase(Locale.ROOT);
        String a = ALIAS.get(w);
        if (a != null && MAP.containsKey(a)) return a;
        if (w.length() < 3) return null;
        if (MAP.containsKey(w)) return w;
        int n = w.length();
        String[] tries = {
                n > 3 && w.endsWith("s") ? w.substring(0, n - 1) : null,
                n > 4 && w.endsWith("es") ? w.substring(0, n - 2) : null,
                n > 4 && w.endsWith("ies") ? w.substring(0, n - 3) + "y" : null,
                n > 5 && w.endsWith("ing") ? w.substring(0, n - 3) : null,
                n > 5 && w.endsWith("ing") ? w.substring(0, n - 3) + "e" : null,
                n > 6 && w.endsWith("ing") && w.charAt(n - 4) == w.charAt(n - 5) ? w.substring(0, n - 4) : null,
                n > 4 && w.endsWith("ed") ? w.substring(0, n - 2) : null,
                n > 4 && w.endsWith("ed") ? w.substring(0, n - 1) : null,
                n > 5 && w.endsWith("ed") && w.charAt(n - 3) == w.charAt(n - 4) ? w.substring(0, n - 3) : null,
                n > 4 && w.endsWith("ly") ? w.substring(0, n - 2) : null,
                n > 4 && w.endsWith("'s") ? w.substring(0, n - 2) : null};
        for (String x : tries) if (x != null && x.length() >= 3 && MAP.containsKey(x)) return x;
        return null;
    }

    /** Emoji search: every emoji whose word (or short form / Banglish word) starts with these letters, the closest words first. */
    public static java.util.List<String> search(String query, int max) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (!ready || query == null || query.trim().length() < 2) return out;
        String q = query.trim().toLowerCase(Locale.ROOT);
        java.util.List<String> keys = new java.util.ArrayList<>();
        synchronized (MAP) { for (String k : MAP.keySet()) if (k.startsWith(q)) keys.add(k); }
        java.util.Collections.sort(keys, new java.util.Comparator<String>() {
            @Override public int compare(String a, String b) { return a.length() != b.length() ? a.length() - b.length() : a.compareTo(b); }
        });
        String exact = key(q);
        if (exact != null) { keys.remove(exact); keys.add(0, exact); }
        for (java.util.Map.Entry<String, String> e : ALIAS.entrySet()) if (e.getKey().startsWith(q) && !keys.contains(e.getValue())) keys.add(e.getValue());
        for (String k : keys) {
            String[] em = MAP.get(k);
            if (em == null) continue;
            for (String x : em) { if (!out.contains(x)) out.add(x); if (out.size() >= max) return out; }
        }
        return out;
    }

    /** Emojis for this word (see key), or null. */
    public static String[] get(String word) {
        String k = key(word);
        return k == null ? null : MAP.get(k);
    }
}
