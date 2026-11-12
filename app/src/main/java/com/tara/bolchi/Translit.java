package com.tara.bolchi;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bengali script to everyday "Banglish" (Bengali written in English letters). */
public final class Translit {
    private Translit() {}

    private static final Map<Character, String> CONS = new HashMap<>();
    private static final Map<Character, String> VOW = new HashMap<>();
    private static final Map<Character, String> SIGN = new HashMap<>();
    private static final Map<String, String> DICT = new HashMap<>();
    private static final char HAS = '্';
    private static final char YY = 'য়';
    private static final Pattern BN = Pattern.compile("[ঀ-৿]+");

    private static void put(Map<Character, String> m, String... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i].charAt(0), kv[i + 1]);
    }

    static {
        put(CONS, "ক","k","খ","kh","গ","g","ঘ","gh","ঙ","ng","চ","ch","ছ","ch","জ","j","ঝ","jh","ঞ","n",
                "ট","t","ঠ","th","ড","d","ঢ","dh","ণ","n","ত","t","থ","th","দ","d","ধ","dh","ন","n",
                "প","p","ফ","f","ব","b","ভ","bh","ম","m","য","j","র","r","ল","l","শ","sh","ষ","sh","স","s","হ","h",
                "ড়","r","ঢ়","rh","য়","y","ৎ","t");
        put(VOW, "অ","o","আ","a","ই","i","ঈ","i","উ","u","ঊ","u","ঋ","ri","এ","e","ঐ","oi","ও","o","ঔ","ou");
        put(SIGN, "া","a","ি","i","ী","i","ু","u","ূ","u","ৃ","ri","ে","e","ৈ","oi","ো","o","ৌ","ou");
        String[] d = {
            "সব","sob","খুব","khub","ভাব","bhab","অভাব","obhab","জবাব","jobab","হিসাব","hisab","সাহেব","saheb","গরিব","gorib","স্বভাব","sobhab","প্রভাব","probhab","কিতাব","kitab","ডাব","dab",
            "গাছ","gach","মাছ","mach","কাছ","kach",
            "কেন","keno","যেন","jeno","ভাল","bhalo","বড়","boro","ছোট","choto","শুভ","shubho","গত","goto","মত","moto","কত","koto","এত","eto","তত","toto","যত","joto","শত","shoto",
            "করল","korlo","বলল","bollo","গেল","gelo","এল","elo","হল","holo","দিল","dilo","নিল","nilo","ছিল","chilo","খেল","khelo","পেল","pelo","চলল","chollo","থাকল","thaklo","দেখল","dekhlo","জানল","janlo","পারল","parlo","লাগল","laglo","বসল","boslo","উঠল","uthlo","পড়ল","porlo","রইল","roilo",
            "করত","korto","হত","hoto","যেত","jeto","বলত","bolto","থাকত","thakto","পারত","parto","আসত","asto","দিত","dito","নিত","nito","খেত","kheto","চাইত","chaito",
            "হ্যাঁ","hyan","দুঃখ","dukkho","দুঃখিত","dukkhito","গতকাল","gotokal","ভালবাসা","bhalobasa","ভালবাসি","bhalobasi","সরাসরি","sorasori","কতটা","kotota","এতটা","etota","জনগণ","jonogon","সহ্য","sojjho",
            "অফিস","office","অফিসে","office e","মিটিং","meeting","ফোন","phone","ক্লাস","class","স্কুল","school","কলেজ","college","রিপোর্ট","report","মেসেজ","message","ম্যাসেজ","message","কল","call","টাইম","time","প্লিজ","please","সরি","sorry","ওকে","ok","হ্যালো","hello","মেইল","mail","ইমেইল","email","ফাইল","file","লিংক","link","গ্রুপ","group","ভিডিও","video","ফটো","photo","ট্রেন","train","টিকিট","ticket","হোয়াটসঅ্যাপ","WhatsApp","গুড","good","মর্নিং","morning","নাইট","night","বাই","bye","লাঞ্চ","lunch","ডিনার","dinner","পার্টি","party","প্ল্যান","plan","প্রবলেম","problem","টেনশন","tension","স্যার","sir","ম্যাডাম","madam","ম্যাম","maam","ব্যাংক","bank","ব্যাঙ্ক","bank","থ্যাংক","thank","ইউ","you","স্মার্ট","smart","মোবাইল","mobile","নাম্বার","number","নম্বর","number","পেমেন্ট","payment","অনলাইন","online","অ্যাপ","app","ডকুমেন্ট","document","লেট","late","ফ্রি","free","বিজি","busy","শিওর","sure","থ্যাংকস","thanks","উইকেন্ড","weekend","অর্ডার","order","ডেলিভারি","delivery","ড্রাইভার","driver","ডাক্তার","daktar","হসপিটাল","hospital","সিনেমা","cinema","রেডি","ready","চেক","check","সেন্ড","send","শেয়ার","share","আপডেট","update","কনফার্ম","confirm","ক্যান্সেল","cancel","বুকিং","booking","লোকেশন","location","পিকআপ","pickup","ড্রপ","drop"
        };
        for (int i = 0; i + 1 < d.length; i += 2) DICT.put(norm(d[i]), d[i + 1]);
    }

    private static final class U {
        boolean isC;
        String cl = "";
        String sign = null;
        boolean ex;
        String mods = "";
        String v = "";
        String r = "";
        char raw;
        boolean conj;
    }

    private static final class Cl {
        String r;
        boolean ya, dbl;
        int real;
    }

    private static String norm(String s) {
        return s.replace("ড়", "ড়").replace("ঢ়", "ঢ়").replace("য়", "য়")
                .replaceAll("[​-‍﻿]", "");
    }

    private static List<U> parse(String w) {
        List<U> u = new ArrayList<>();
        int i = 0, n = w.length();
        while (i < n) {
            char c = w.charAt(i);
            if (VOW.containsKey(c)) {
                U x = new U(); x.v = VOW.get(c); x.raw = c; u.add(x); i++;
            } else if (CONS.containsKey(c)) {
                U x = new U(); x.isC = true;
                StringBuilder cl = new StringBuilder().append(c);
                boolean ex = false;
                i++;
                while (i < n && w.charAt(i) == HAS) {
                    if (i + 1 < n && CONS.containsKey(w.charAt(i + 1))) { cl.append(w.charAt(i + 1)); i += 2; }
                    else { ex = true; i++; break; }
                }
                if (i < n && SIGN.containsKey(w.charAt(i))) { x.sign = SIGN.get(w.charAt(i)); i++; }
                x.cl = cl.toString();
                x.ex = ex || c == 'ৎ';
                u.add(x);
            } else if (c == 'ং' || c == 'ঃ' || c == 'ঁ') {
                if (!u.isEmpty()) u.get(u.size() - 1).mods += c;
                i++;
            } else if (SIGN.containsKey(c)) {
                U x = new U(); x.v = SIGN.get(c); x.raw = c; u.add(x); i++;
            } else {
                i++;
            }
        }
        return u;
    }

    private static Cl cluster(String cl, boolean initial, boolean noDouble) {
        List<String> parts = new ArrayList<>();
        Cl o = new Cl();
        int len = cl.length();
        for (int k = 0; k < len; k++) {
            char c = cl.charAt(k);
            char p = k > 0 ? cl.charAt(k - 1) : 0;
            char nx = k + 1 < len ? cl.charAt(k + 1) : 0;
            if (c == 'ক' && nx == 'ষ') { parts.add(initial && k == 0 ? "kh" : "kkh"); k++; o.real += 2; continue; }
            if (c == 'জ' && nx == 'ঞ') { parts.add(initial && k == 0 ? "gy" : "gg"); k++; o.real += 2; continue; }
            if (c == 'চ' && (nx == 'ছ' || nx == 'চ')) { parts.add("cch"); k++; o.real += 2; continue; }
            if (c == 'ঙ' && nx == 'গ') { parts.add("ng"); k++; o.real += 2; continue; }
            if (c == 'ঙ' && nx == 'ক') { parts.add("nk"); k++; o.real += 2; continue; }
            if (k > 0 && c == 'য' && k == len - 1 && p != 'র') { o.ya = true; continue; }
            if (k > 0 && c == 'ব' && "দসশতধজহষথগটক".indexOf(p) >= 0) { o.dbl = true; continue; }
            if (k > 0 && c == 'ম' && "তদষ".indexOf(p) >= 0) { o.dbl = true; continue; }
            parts.add(CONS.get(c));
            o.real++;
        }
        if ((o.ya || o.dbl) && !initial && !noDouble && o.real == 1 && parts.size() == 1) {
            String s = parts.get(0);
            parts.set(0, s.charAt(0) + s);
        }
        StringBuilder sb = new StringBuilder();
        for (String s : parts) sb.append(s);
        o.r = sb.toString();
        return o;
    }

    private static boolean loanEnd(String cl) {
        int len = cl.length();
        if (len < 2) return false;
        char a = cl.charAt(len - 2), b = cl.charAt(len - 1);
        if ((b == 'ট' || b == 'ড') && a != 'ষ' && a != 'ণ') return true;
        String ab = "" + a + b;
        String[] loans = {"রক","কস","মপ","লফ","লম","সক","নস","রস","রজ","ঞজ","রচ","ঞচ","রন","রল","রভ"};
        for (String l : loans) if (l.equals(ab)) return true;
        return false;
    }

    private static void resolve(List<U> u) {
        int n = u.size();
        for (int i = n - 1; i >= 0; i--) {
            U x = u.get(i);
            if (!x.isC) continue;
            boolean init = i == 0;
            U prev = i > 0 ? u.get(i - 1) : null;
            U next = i + 1 < n ? u.get(i + 1) : null;
            Cl cr = cluster(x.cl, init, prev != null && prev.mods.indexOf('ং') >= 0);
            x.r = cr.r;
            x.conj = cr.real >= 2 || cr.ya || cr.dbl;
            char last = x.cl.charAt(x.cl.length() - 1);
            if (cr.ya) {
                if ("a".equals(x.sign)) x.v = init ? "ya" : "a";
                else if (x.sign != null) x.v = x.sign;
                else x.v = init ? "e" : "o";
                continue;
            }
            if (x.sign != null) { x.v = x.sign; continue; }
            if (x.ex) { x.v = ""; continue; }
            if (!x.mods.isEmpty()) { x.v = "o"; continue; }
            if (next == null) {
                if (n == 1) x.v = "o";
                else if (cr.real >= 2 || cr.dbl) x.v = loanEnd(x.cl) ? "" : "o";
                else if (last == 'হ' || last == 'ছ' || last == 'ব') x.v = "o";
                else if (last == YY) x.v = (prev != null && prev.isC && "i".equals(prev.sign)) ? "o" : "";
                else x.v = "";
                continue;
            }
            if (!next.isC) { x.v = "o"; continue; }
            if (init || x.conj || next.conj) { x.v = "o"; continue; }
            if (i == 1 && !prev.isC && prev.raw == 'অ') { x.v = "o"; continue; }
            if (prev.isC && prev.ex) { x.v = "o"; continue; }
            x.v = next.v.isEmpty() ? "o" : "";
        }
        for (int i = 0; i < n - 1; i++) {
            U x = u.get(i), next = u.get(i + 1);
            if (!x.isC && x.raw == 'ও' && next.isC && next.cl.length() == 1 && next.cl.charAt(0) == YY && next.sign != null) {
                x.v = "";
                next.r = "w";
            }
        }
    }

    private static String word(String w) {
        String hit = DICT.get(w);
        if (hit != null) return hit;
        w = w.replace("অ্যা", "আ").replace("এ্যা", "আ");
        List<U> u = parse(w);
        if (u.isEmpty()) return "";
        resolve(u);
        if (w.endsWith("ছিল")) u.get(u.size() - 1).v = "o";
        StringBuilder out = new StringBuilder();
        for (U x : u) {
            if (x.isC) out.append(x.r);
            out.append(x.v);
            if (x.mods.indexOf('ং') >= 0) out.append("ng");
        }
        return out.toString();
    }

    public static String toBanglish(String text) {
        if (text == null) return "";
        String s = norm(text);
        StringBuilder t = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '০' && c <= '৯') t.append((char) ('0' + (c - '০')));
            else if (c == '।') t.append('.');
            else if (c == '৳') t.append("Rs ");
            else t.append(c);
        }
        Matcher m = BN.matcher(t);
        StringBuffer out = new StringBuffer();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(word(m.group())));
        m.appendTail(out);
        return out.toString();
    }
}
