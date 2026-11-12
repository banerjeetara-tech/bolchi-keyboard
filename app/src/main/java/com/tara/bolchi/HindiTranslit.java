package com.tara.bolchi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hindi (Devanagari) to everyday Hinglish, e.g. "मैं घर जा रहा हूँ" -> "main ghar ja raha hoon". */
public final class HindiTranslit {
    private HindiTranslit() {}

    private static final Map<Character, String> CONS = new HashMap<>();
    private static final Map<Character, String> VOW = new HashMap<>();
    private static final Map<Character, String> SIGN = new HashMap<>();
    private static final Map<String, String> DICT = new HashMap<>();
    private static final char HAL = '्';
    private static final char NUKTA = '़';
    private static final Pattern HI = Pattern.compile("[ऀ-ॿ]+");

    private static void put(Map<Character, String> m, String... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i].charAt(0), kv[i + 1]);
    }

    static {
        put(CONS, "क","k","ख","kh","ग","g","घ","gh","ङ","n","च","ch","छ","chh","ज","j","झ","jh","ञ","n",
                "ट","t","ठ","th","ड","d","ढ","dh","ण","n","त","t","थ","th","द","d","ध","dh","न","n",
                "प","p","फ","ph","ब","b","भ","bh","म","m","य","y","र","r","ल","l","व","v",
                "श","sh","ष","sh","स","s","ह","h",
                "क़","q","ख़","kh","ग़","g","ज़","z","ड़","d","ढ़","dh","फ़","f","य़","y");
        put(VOW, "अ","a","आ","a","इ","i","ई","i","उ","u","ऊ","u","ऋ","ri","ए","e","ऐ","ai","ओ","o","औ","au","ऑ","o","ऍ","e");
        put(SIGN, "ा","a","ि","i","ी","I","ु","u","ू","U","ृ","ri","े","e","ै","ai","ो","o","ौ","au","ॉ","o","ॅ","e");
        String[] d = {
            "में","mein","मैं","main","हैं","hain","नहीं","nahi","नही","nahi","क्यों","kyun","क्यूँ","kyun","यह","yeh","वह","woh","वो","woh","ये","ye",
            "माँ","maa","हूँ","hoon","हूं","hoon","हाँ","haan","जी","ji","ठीक","theek","कुछ","kuch","पूछ","pooch","सच","sach",
            "ऑफिस","office","ऑफ़िस","office","मीटिंग","meeting","फोन","phone","फ़ोन","phone","प्लीज","please","प्लीज़","please","सॉरी","sorry",
            "ओके","ok","कॉल","call","मैसेज","message","मेसेज","message","टाइम","time","थैंक्स","thanks","थैंक","thank","यू","you",
            "प्लान","plan","प्रॉब्लम","problem","टेंशन","tension","रिपोर्ट","report","फाइल","file","लिंक","link","ग्रुप","group",
            "वीडियो","video","फोटो","photo","सर","sir","मैडम","madam","मैम","maam","बैंक","bank","मोबाइल","mobile","नंबर","number",
            "पेमेंट","payment","ऑनलाइन","online","ऐप","app","लेट","late","फ्री","free","बिजी","busy","श्योर","sure","रेडी","ready",
            "चेक","check","शेयर","share","अपडेट","update","कन्फर्म","confirm","कैंसल","cancel","बुकिंग","booking","लोकेशन","location",
            "गुड","good","मॉर्निंग","morning","नाइट","night","बाय","bye","लंच","lunch","डिनर","dinner","पार्टी","party","हेलो","hello","हैलो","hello"
        };
        for (int i = 0; i + 1 < d.length; i += 2) DICT.put(norm(d[i]), d[i + 1]);
    }

    private static String norm(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i + 1 < s.length() && s.charAt(i + 1) == NUKTA) {
                char m = 0;
                switch (c) {
                    case 'क': m = 'क़'; break; case 'ख': m = 'ख़'; break; case 'ग': m = 'ग़'; break;
                    case 'ज': m = 'ज़'; break; case 'ड': m = 'ड़'; break; case 'ढ': m = 'ढ़'; break;
                    case 'फ': m = 'फ़'; break; case 'य': m = 'य़'; break; default: break;
                }
                if (m != 0) { b.append(m); i++; continue; }
            }
            if (c == NUKTA || (c >= '​' && c <= '‍')) continue;
            b.append(c);
        }
        return b.toString();
    }

    private static final class U {
        boolean isC;
        String cl = "";
        String sign;      // matra (I/U = long i/u)
        boolean hal;      // ends in a visible halant
        String mods = ""; // ं ँ ः
        String v = "";    // vowel as written
        boolean first;    // independent vowel at word start
    }

    private static List<U> parse(String w) {
        List<U> u = new ArrayList<>();
        int i = 0, n = w.length();
        while (i < n) {
            char c = w.charAt(i);
            if (VOW.containsKey(c)) {
                U x = new U(); x.v = c == 'ई' ? "I" : c == 'ऊ' ? "U" : VOW.get(c);
                if (c == 'आ') x.v = "A";
                x.first = u.isEmpty(); x.sign = String.valueOf(c); u.add(x); i++;
            } else if (CONS.containsKey(c)) {
                U x = new U(); x.isC = true;
                StringBuilder cl = new StringBuilder().append(c);
                i++;
                while (i < n && w.charAt(i) == HAL) {
                    if (i + 1 < n && CONS.containsKey(w.charAt(i + 1))) { cl.append(w.charAt(i + 1)); i += 2; }
                    else { x.hal = true; i++; break; }
                }
                if (i < n && SIGN.containsKey(w.charAt(i))) {
                    x.sign = SIGN.get(w.charAt(i));
                    if (w.charAt(i) == 'ा') x.sign = "A";
                    i++;
                }
                x.cl = cl.toString();
                u.add(x);
            } else if (c == 'ं' || c == 'ँ' || c == 'ः') {
                if (!u.isEmpty()) u.get(u.size() - 1).mods += c;
                i++;
            } else {
                i++;
            }
        }
        return u;
    }

    private static String cluster(String cl, boolean initial) {
        StringBuilder b = new StringBuilder();
        int len = cl.length();
        for (int k = 0; k < len; k++) {
            char c = cl.charAt(k);
            char nx = k + 1 < len ? cl.charAt(k + 1) : 0;
            if (c == 'च' && (nx == 'छ' || nx == 'च')) { b.append("cch"); k++; continue; }
            if (c == 'ज' && nx == 'ञ') { b.append("gy"); k++; continue; }
            if (c == 'क' && nx == 'ष') { b.append("ksh"); k++; continue; }
            if (c == 'व' && k > 0) { b.append("w"); continue; }
            if (c == 'छ' && k > 0) { b.append("chh"); continue; }
            b.append(CONS.get(c));
        }
        String r = b.toString();
        if (initial && r.startsWith("chh")) r = "ch" + r.substring(3);
        return r;
    }

    private static boolean hasVowel(U x) { return !x.isC || !x.v.isEmpty(); }

    private static String word(String w) {
        String hit = DICT.get(w);
        if (hit != null) return hit;
        List<U> u = parse(w);
        int n = u.size();
        if (n == 0) return "";
        // written vowels
        for (U x : u) {
            if (!x.isC) continue;
            if (x.sign != null) x.v = x.sign;
            else if (x.hal) x.v = "";
            else x.v = "a";
        }
        // word-final inherent a goes silent (kept on one-letter words and on endings like -tra, -dra)
        U last = u.get(n - 1);
        if (n > 1 && last.isC && last.sign == null && !last.hal && last.mods.isEmpty()) {
            boolean keep = last.cl.length() > 1 && last.cl.charAt(last.cl.length() - 1) == 'र';
            if (!keep) last.v = "";
        }
        // medial inherent a goes silent between two pronounced vowels: kar-na, sam-jha, lad-ki
        for (int i = n - 2; i >= 1; i--) {
            U x = u.get(i), prev = u.get(i - 1), next = u.get(i + 1);
            if (!x.isC || x.sign != null || x.hal || !x.mods.isEmpty() || !"a".equals(x.v)) continue;
            if (x.cl.length() > 1) continue;
            if (!hasVowel(prev)) continue;
            if (!next.isC || next.cl.length() > 1 || next.v.isEmpty()) continue;
            x.v = "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n; i++) {
            U x = u.get(i);
            U next = i + 1 < n ? u.get(i + 1) : null;
            boolean closed = (!x.mods.isEmpty() && next != null) || (next != null && next.isC && next.v.isEmpty());
            String v = x.v;
            if ("A".equals(v)) v = (closed || (!x.isC && x.first)) ? "aa" : "a";
            else if ("I".equals(v)) v = closed ? "ee" : "i";
            else if ("U".equals(v)) v = closed ? "oo" : "u";
            if (x.isC) out.append(cluster(x.cl, i == 0));
            else if (i > 0 && hasVowel(u.get(i - 1)) && "e".equals(v)) v = "ye";
            out.append(v);
            for (int k = 0; k < x.mods.length(); k++) out.append(x.mods.charAt(k) == 'ः' ? "h" : "n");
        }
        return out.toString();
    }

    public static String toHinglish(String text) {
        if (text == null) return "";
        String s = norm(text);
        StringBuilder t = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '०' && c <= '९') t.append((char) ('0' + (c - '०')));
            else if (c == '।' || c == '॥') t.append('.');
            else t.append(c);
        }
        Matcher m = HI.matcher(t);
        StringBuffer out = new StringBuffer();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(word(m.group())));
        m.appendTail(out);
        return out.toString();
    }
}
