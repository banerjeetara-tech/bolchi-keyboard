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
    private static final java.util.Set<String> LOAN = new java.util.HashSet<>();
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
        // English words as the Bengali speech engine writes them. With these the word comes out in English spelling, also with a Bengali ending
        // on it ("অফিসের" -> "office er", "মিটিংটা" -> "meeting ta").
        String[] e = {
            "ইমেল","email","মেল","mail","প্রেজেন্টেশন","presentation","প্রজেক্ট","project","ম্যানেজার","manager","টিম","team","ক্লায়েন্ট","client","কাস্টমার","customer","ডেডলাইন","deadline","টার্গেট","target","বাজেট","budget","স্যালারি","salary","বোনাস","bonus","ইনভয়েস","invoice",
            "বিল","bill","অ্যাকাউন্ট","account","অ্যাকাউন্টস","accounts","ফিনান্স","finance","অডিট","audit","ট্যাক্স","tax","এক্সেল","Excel","শিট","sheet","ডেটা","data","ডাটা","data","সিস্টেম","system","সফটওয়্যার","software","কম্পিউটার","computer","ল্যাপটপ","laptop",
            "প্রিন্ট","print","প্রিন্টার","printer","স্ক্যান","scan","কপি","copy","পেস্ট","paste","ডাউনলোড","download","আপলোড","upload","ইনস্টল","install","লগইন","login","পাসওয়ার্ড","password","ইন্টারনেট","internet","নেটওয়ার্ক","network","ওয়াইফাই","WiFi","সার্ভার","server",
            "ওয়েবসাইট","website","পেজ","page","স্ক্রিন","screen","স্ক্রিনশট","screenshot","কিবোর্ড","keyboard","বাটন","button","অপশন","option","সেটিং","setting","সেটিংস","settings","ভার্সন","version","ফিচার","feature","এরর","error","ইস্যু","issue","সাপোর্ট","support",
            "সার্ভিস","service","রিকোয়েস্ট","request","অ্যাপ্রুভ","approve","অ্যাপ্রুভাল","approval","রিজেক্ট","reject","সাবমিট","submit","পেন্ডিং","pending","কমপ্লিট","complete","স্ট্যাটাস","status","রিমাইন্ডার","reminder","শিডিউল","schedule","ক্যালেন্ডার","calendar","ডেট","date","টুডে","today",
            "টুমরো","tomorrow","উইক","week","মান্থ","month","ইয়ার","year","মিনিট","minute","আওয়ার","hour","সেকেন্ড","second","ব্রেক","break","লিভ","leave","হলিডে","holiday","জুম","Zoom","টিমস","Teams","গুগল","Google","ইউটিউব","YouTube",
            "ফেসবুক","Facebook","ইনস্টাগ্রাম","Instagram","মেসেঞ্জার","Messenger","চ্যাট","chat","রিপ্লাই","reply","ফরওয়ার্ড","forward","ডিলিট","delete","সেভ","save","এডিট","edit","ওপেন","open","ক্লোজ","close","স্টার্ট","start","স্টপ","stop","ট্রাই","try",
            "টেস্ট","test","রেজাল্ট","result","নোট","note","নোটস","notes","লিস্ট","list","ফর্ম","form","ফরম্যাট","format","কোড","code","ওটিপি","OTP","কার্ড","card","ক্যাশ","cash","লোন","loan","রিচার্জ","recharge","ব্যালেন্স","balance",
            "ট্রান্সফার","transfer","রিফান্ড","refund","ডিসকাউন্ট","discount","অফার","offer","প্রাইস","price","রেট","rate","কস্ট","cost","টোটাল","total","পার্সেন্ট","percent","কোম্পানি","company","কলিগ","colleague","স্টাফ","staff","এইচআর","HR",
            "ইন্টারভিউ","interview","জব","job","ডিউটি","duty","শিফট","shift","ট্রেনিং","training","ডিপার্টমেন্ট","department","ব্রাঞ্চ","branch","ডিরেক্টর","director","ট্যাক্সি","taxi","ক্যাব","cab","বাস","bus","মেট্রো","metro","ফ্লাইট","flight","এয়ারপোর্ট","airport",
            "স্টেশন","station","হোটেল","hotel","রুম","room","ফ্ল্যাট","flat","গেট","gate","লিফট","lift","পার্কিং","parking","ট্রাফিক","traffic","জ্যাম","jam","মার্কেট","market","শপিং","shopping","রেস্টুরেন্ট","restaurant","কফি","coffee","ব্রেকফাস্ট","breakfast",
            "কেক","cake","পিৎজা","pizza","বার্গার","burger","চকলেট","chocolate","মেডিসিন","medicine","ডক্টর","doctor","নার্স","nurse","ফিভার","fever","জিম","gym","ড্রেস","dress","শার্ট","shirt","কালার","colour","ডিজাইন","design","সাইজ","size",
            "স্টাইল","style","গিফট","gift","বার্থডে","birthday","হ্যাপি","happy","ওয়েলকাম","welcome","নাইস","nice","বিউটিফুল","beautiful","গ্রেট","great","সুপার","super","পারফেক্ট","perfect","ফাইন","fine","ইয়েস","yes","অ্যাকচুয়ালি","actually","বেসিক্যালি","basically",
            "ডেফিনিটলি","definitely","এক্স্যাক্টলি","exactly","অবভিয়াসলি","obviously","সিরিয়াসলি","seriously","মেবি","maybe","অলরেডি","already","অলওয়েজ","always","এগেইন","again","জাস্ট","just","অনলি","only","ভেরি","very","বাট","but","অ্যান্ড","and","বিকজ","because",
            "লাইক","like","থিংক","think","ফিল","feel","ওয়েট","wait","হেল্প","help","ইউজ","use","নিড","need","ডিসকাস","discuss","ডিসিশন","decision","ডিসাইড","decide","চেঞ্জ","change","অ্যাড","add","রিমুভ","remove","ফিক্স","fix",
            "ওয়ার্ক","work","ওয়ার্কিং","working","ইম্পর্ট্যান্ট","important","আর্জেন্ট","urgent","সিরিয়াস","serious","ইজি","easy","ডিফিকাল্ট","difficult","সিম্পল","simple","পসিবল","possible","ইম্পসিবল","impossible","ডিফারেন্ট","different","সেম","same","নিউ","new","ওল্ড","old",
            "নেক্সট","next","লাস্ট","last","ফার্স্ট","first","ফাইনাল","final","এক্সট্রা","extra","হাফ","half","পার্ট","part","পয়েন্ট","point","টপিক","topic","আইডিয়া","idea","ইনফরমেশন","information","ডিটেলস","details","ডিটেইলস","details",
            "এক্সাম্পল","example","কোয়েশ্চেন","question","আনসার","answer","রিজন","reason","মিসটেক","mistake","চান্স","chance","রিস্ক","risk","সেফ","safe","সিকিউরিটি","security","কোয়ালিটি","quality","লেভেল","level","টাইপ","type","টাইপিং","typing","স্পিকিং","speaking",
            "ইংলিশ","English","ইংরেজি","English","বেঙ্গলি","Bengali","হিন্দি","Hindi","ল্যাঙ্গুয়েজ","language","ওয়ার্ড","word","সেন্টেন্স","sentence","কারেকশন","correction","অটো","auto","কারেক্ট","correct","রাইট","right","লেফট","left","ডাউন","down","ব্যাক","back",
            "ফ্রন্ট","front","সাইড","side","সেন্টার","center","ফ্যামিলি","family","ফ্রেন্ড","friend","ফ্রেন্ডস","friends","আঙ্কেল","uncle","আন্টি","aunty","হাজব্যান্ড","husband","ওয়াইফ","wife","বেবি","baby","লাইফ","life","মিস","miss",
            "ফান","fun","মুভি","movie","গেম","game","মিউজিক","music","নিউজ","news","চ্যানেল","channel","টিভি","TV","এসি","AC","লাইট","light","সুইচ","switch","চার্জ","charge","চার্জার","charger","ব্যাটারি","battery","ক্যামেরা","camera",
            "পিকচার","picture","ইমেজ","image","পজ","pause","পস","pause","স্পেস","space","ফন্ট","font","থিম","theme","ইমোজি","emoji","স্টিকার","sticker","কনভার্ট","convert","কনভার্টার","converter","কারেন্সি","currency","ডলার","dollar","সাজেশন","suggestion",
            "প্রেডিকশন","prediction","ট্রান্সপারেন্ট","transparent","কম্বিনেশন","combination","ভয়েস","voice","টেক্সট","text","মেনশন","mention","সাবসিডিয়ারি","subsidiary","কনট্যাক্ট","contact","কন্টাক্ট","contact","অ্যাড্রেস","address","অ্যাটাচ","attach","অ্যাটাচমেন্ট","attachment","ড্রাফট","draft","সাবজেক্ট","subject",
            "সিগনেচার","signature","ডায়মন্ড","diamond","উইথড্র","withdraw","ভাউচার","voucher","রিভিউ","review","পেরোল","payroll","টেবিল","table","অপরচুনিটি","opportunity","ইনক্রিমেন্ট","increment","ফরওয়ার্ডিং","forwarding","অফিস","office","মিটিং","meeting","ফোন","phone","ক্লাস","class","স্কুল","school","কলেজ","college","রিপোর্ট","report","মেসেজ","message","কল","call","টাইম","time","মেইল","mail","ইমেইল","email","ফাইল","file",
            "লিংক","link","গ্রুপ","group","ভিডিও","video","ফটো","photo","ট্রেন","train","টিকিট","ticket","লাঞ্চ","lunch","ডিনার","dinner","পার্টি","party","প্ল্যান","plan","প্রবলেম","problem","টেনশন","tension","ব্যাংক","bank","ব্যাঙ্ক","bank",
            "মোবাইল","mobile","নাম্বার","number","নম্বর","number","পেমেন্ট","payment","অ্যাপ","app","ডকুমেন্ট","document","অর্ডার","order","ডেলিভারি","delivery","হসপিটাল","hospital","সিনেমা","cinema","বুকিং","booking","লোকেশন","location","আপডেট","update"
        };
        for (int i = 0; i + 1 < e.length; i += 2) { String k = norm(e[i]); if (!DICT.containsKey(k)) DICT.put(k, e[i + 1]); LOAN.add(k); }
    }

    /** Bengali endings that are put on English words too, longest first, with how they are written in Banglish. */
    private static final String[][] ENDS = {{"গুলোকে", "guloke"}, {"গুলোর", "gulor"}, {"গুলো", "gulo"}, {"গুলি", "guli"}, {"টাকে", "take"}, {"টার", "tar"}, {"টায়", "tay"},
            {"টা", "ta"}, {"টি", "ti"}, {"ের", "er"}, {"কে", "ke"}, {"তে", "te"}, {"ে", "e"}, {"র", "r"}};

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
        for (String[] en : ENDS) {
            if (w.length() > en[0].length() + 1 && w.endsWith(en[0])) {
                String stem = w.substring(0, w.length() - en[0].length());
                if (LOAN.contains(stem)) return DICT.get(stem) + " " + en[1];
            }
        }
        // an English word the speech engine wrote in Bengali letters ("ম্যানেজমেন্ট"): give its real English spelling when the sound matches one clearly
        if (looksLoan(w)) {
            String en = Words.soundAlike(plain(w));
            if (en != null) return en;
            for (String[] end : ENDS) {
                if (w.length() > end[0].length() + 3 && w.endsWith(end[0])) {
                    String stem = w.substring(0, w.length() - end[0].length());
                    if (!looksLoan(stem)) continue;               // "দরজাটা": the ট is only the ending, the word before it is Bengali
                    en = Words.soundAlike(plain(stem));
                    if (en != null) return en + " " + end[1];
                }
            }
        }
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

    /** Letters and signs that English words written in Bengali never use: a word with one of them is a Bengali word. */
    private static final String NATIVE_ONLY = "খঘঝঠঢথধছণষৎঋঃঁঞঈঊঐঔৃ";

    /**
     * True when a Bengali-script word looks like an English word written by sound: only the letters such words use, and at least one
     * tell-tale of them (ট, ড, ফ, the "্যা" vowel, a "শন" ending, "ক্স").
     */
    private static boolean looksLoan(String w) {
        if (w.length() < 4) return false;
        // Bengali verb forms ("কাটলেন", "ডাকবেন", "টানলাম") are never English words
        for (String v : new String[]{"লেন", "বেন", "তেন", "লাম", "ছেন", "ছিল"}) if (w.endsWith(v)) return false;
        if (w.indexOf('\u09DC') >= 0 || w.indexOf('\u09DD') >= 0 || w.indexOf('\u09BC') >= 0) return false;      // ড় ঢ় and the dot below: Bengali words
        for (int i = 0; i < w.length(); i++) if (NATIVE_ONLY.indexOf(w.charAt(i)) >= 0) return false;
        return w.indexOf('ট') >= 0 || w.indexOf('ড') >= 0 || w.indexOf('ফ') >= 0 || w.contains("্যা") || w.endsWith("শন") || w.contains("ক্স") || w.contains("ন্স");
    }

    /** The word by the ordinary letter rules (no word list). */
    private static String plain(String w) {
        w = w.replace("অ্যা", "আ").replace("এ্যা", "আ");
        List<U> u = parse(w);
        if (u.isEmpty()) return "";
        resolve(u);
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
