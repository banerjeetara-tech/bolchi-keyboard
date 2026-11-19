package com.tara.bolchi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The four converter options. Network ones must be called off the main thread. */
final class Converter {
    private Converter() {}

    static final String MODEL = "gemini-flash-lite-latest";
    static volatile String stage = "";
    static volatile HttpURLConnection current;
    static void cancel() { try { HttpURLConnection c = current; if (c != null) c.disconnect(); } catch (Throwable t) { } }
    static final String MODEL_BACKUP = "gemini-flash-latest";

    // ---------- offline: script to English letters ----------
    static String toEnglishLetters(String s) {
        return HindiTranslit.toHinglish(Translit.toBanglish(s));
    }

    // ---------- Google typing service + Google Translate (no key) ----------
    private static final Pattern WORD = Pattern.compile("[A-Za-z]+");

    /** Banglish/Hinglish words to script, word by word, using the same service as the suggestions. */
    static String romanToScript(String text, boolean hindi) throws Exception {
        Matcher m = WORD.matcher(text);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            stage = "converting \"" + m.group() + "\"";
            java.util.List<String> c = Phonetic.suggest(m.group(), hindi);
            m.appendReplacement(out, Matcher.quoteReplacement(c.isEmpty() ? m.group() : c.get(0)));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static boolean hasScript(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u0900' && c <= '\u09FF') return true;
        }
        return false;
    }

    /** Free Google Translate. lang = "bn", "hi" or "auto". */
    static String googleTranslate(String text, String lang) throws Exception {
        return googleTranslateTo(text, lang, "en");
    }

    private static volatile long googleTranslateBlockedUntil;

    /** Google Translate from `lang` (or "auto") into the language `to` ("en", "hi", "bn"). */
    static String googleTranslateTo(String text, String lang, String to) throws Exception {
        if (System.currentTimeMillis() < googleTranslateBlockedUntil) throw new UserMessage("Google Translate refused (429), not asked again for a while");
        String q = java.net.URLEncoder.encode(text, "UTF-8");
        String[] hosts = {"https://translate.google.com", "https://translate.googleapis.com"};
        Exception last = null;
        for (String h : hosts) {
            try {
                stage = "asking Google Translate";
                HttpURLConnection c = (HttpURLConnection) new URL(h + "/translate_a/single?client=gtx&dt=t&tl=" + to + "&sl="
                        + lang + "&q=" + q).openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(8000);
                int rc = c.getResponseCode();
                if (rc == 429) googleTranslateBlockedUntil = System.currentTimeMillis() + 15 * 60 * 1000L;       // its free door is shut for her phone: go straight to the next service
                if (rc != 200) { last = new UserMessage("Google Translate didn't answer (" + rc + " from " + h.replace("https://", "") + ")"); continue; }
                JSONArray j = new JSONArray(read(c.getInputStream()));
                JSONArray parts = j.getJSONArray(0);
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < parts.length(); i++) {
                    JSONArray p = parts.optJSONArray(i);
                    if (p != null && !p.isNull(0)) b.append(p.getString(0));
                }
                if (b.length() > 0) return b.toString().trim();
            } catch (Exception e) { last = new UserMessage("Google Translate: " + e.getClass().getSimpleName() + " from " + h.replace("https://", "")); }
        }
        throw last != null ? last : new UserMessage("Google Translate didn't answer");
    }

    private static final String EN_PROMPT = "Translate the user's message into natural, simple English. The message may be Bengali or Hindi "
            + "written in English letters (Banglish or Hinglish), Bengali or Hindi script, or mixed with English. "
            + "Keep names, numbers and emojis as they are. Reply with only the translation, nothing else.";
    private static final String BN_PROMPT = "Translate the user's message into natural, simple Bengali written in Bengali script. The message may be English, Hindi "
            + "(Devanagari), or Bengali or Hindi in English letters. Keep names, numbers, emojis and links as they are. Reply with only the translation, nothing else.";
    private static final String HI_PROMPT = "Translate the user's message into natural, simple Hindi written in Devanagari script. The message may be English, "
            + "Bengali, or Bengali or Hindi in English letters. Keep names, numbers, emojis and links as they are. Reply with only the translation, nothing else.";

    /**
     * Translate `text` into `to` ("en", "hi", "bn"). In this order: the free translators that need no key (several, Google's among them), then the AI services,
     * then the translator that lives ON the phone (works without internet once its languages are downloaded), then MyMemory.
     */
    private static String translateChain(String text, String from, String to, String prompt, String key, boolean useDoors) throws Exception {
        StringBuilder why = new StringBuilder();
        // the translator ON the phone comes first when its languages are downloaded: instant, free, no limit, no internet. She prefers it to the AI services.
        if (useDoors || hasScript(text)) {
            try {
                String r = offlineTranslate(text, to);
                if (r != null && !r.trim().isEmpty() && !r.trim().equals(text.trim())) return r;
            } catch (Throwable ignored) { /* not downloaded yet, or not possible: the online services follow */ }
        }
        if (useDoors) {
            try { return translateDoors(text, from, to); } catch (Exception e) { why.append(shortAi(e)).append(" "); }
        }
        try { return gemini(key, prompt, text); } catch (Exception e) { why.append(shortAi(e)).append(" "); }
        try { return offlineTranslate(text, to); } catch (Throwable e) { why.append("On the phone: ").append(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()).append(". "); }
        try { return myMemoryAuto(text, to); } catch (Exception e) { why.append("MyMemory: ").append(shortWhy(e)); }
        throw new UserMessage(why.toString().trim());
    }

    /** Translate to English. Banglish / Hinglish typed in English letters is first written in its own script, then translated. */
    static String translateFree(String text, int voiceMode, String key) throws Exception {
        if (hasScript(text) || voiceMode == 2) return translateChain(text, "auto", "en", EN_PROMPT, key, true);
        boolean hindi = voiceMode == 1;
        String script = null;
        try { script = romanToScript(text, hindi); } catch (Exception ignored) { /* the AI can read Banglish itself */ }
        if (script != null) return translateChain(script, hindi ? "hi" : "bn", "en", EN_PROMPT, key, true);
        return translateChain(text, "auto", "en", EN_PROMPT, key, false);
    }

    static boolean hasDevanagari(String s) {
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c >= '\u0900' && c <= '\u097F') return true; }
        return false;
    }

    private static boolean hasBengali(String s) {
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c >= '\u0980' && c <= '\u09FF') return true; }
        return false;
    }

    /**
     * Translate into Bengali (Bengali script). Banglish typed in English letters is ALREADY Bengali: when a translator leaves it unchanged it is only
     * written in Bengali letters (`romanToScript`).
     */
    static String translateToBengali(String text, String key) throws Exception {
        if (!hasScript(text) && looksBanglishText(text)) return romanToScript(text, false);       // already Bengali, only in English letters: no translating
        String out = translateChain(text, "auto", "bn", BN_PROMPT, key, true);
        if (hasBengali(out)) return out;
        if (!hasScript(text)) return romanToScript(text, false);       // Banglish: only the letters change
        return out;
    }

    /** True when a third or more of the words are Banglish / Hinglish (the built-in list of everyday Bengali / Hindi words in English letters). */
    private static boolean looksBanglishText(String text) {
        try {
            if (!Words.ready) return false;
            int n = 0, d = 0;
            for (String t : text.toLowerCase(Locale.ROOT).split("[^a-z']+")) {
                if (t.length() < 2) continue;
                n++;
                if (Words.isDesi(t)) d++;
            }
            return d >= 1 && d * 3 >= n;
        } catch (Throwable ignored) { return false; }
    }

    /** Translate into Hindi (Devanagari). */
    static String translateToHindi(String text, String key) throws Exception {
        if (!hasScript(text) && looksBanglishText(text)) {                // Banglish in English letters: first write it in Bengali letters, then translate that
            String bn = romanToScript(text, false);
            return translateChain(bn, "bn", "hi", HI_PROMPT, key, true);
        }
        return translateChain(text, "auto", "hi", HI_PROMPT, key, true);
    }

    // ---------- the free translators that need no key: each is tried in turn, and one that failed is left alone for a few minutes ----------
    static String translateDoors(String text, String from, String to) throws Exception {
        StringBuilder why = new StringBuilder();
        String[] names = {"Google", "Edge", "GoogleAlt", "Lingva"};
        for (int pass = 0; pass < 2; pass++) {
            boolean tried = false;
            for (String n : names) {
                if (pass == 0 && down(n)) { why.append(n).append(": skipped. "); continue; }
                tried = true;
                try {
                    String r = n.equals("Google") ? googleTranslateTo(text, from, to) : n.equals("Edge") ? edgeTranslate(text, from, to)
                            : n.equals("GoogleAlt") ? googleAltTranslate(text, from, to) : lingvaTranslate(text, from, to);
                    if (r != null && !r.trim().isEmpty()) return r;
                } catch (Exception e) {
                    // Google's doors answered 429 (too many requests), Edge's sign-in is gone (404) and Lingva's volunteers were slow: do not ask again soon
                    mark(n, n.equals("Edge") ? 6 * 3600 * 1000L : n.equals("Lingva") ? 30 * 60 * 1000L : 15 * 60 * 1000L);
                    why.append(n).append(": ").append(shortWhy(e)).append(". ");
                }
            }
            if (tried) break;
        }
        throw new UserMessage(why.toString().trim());
    }

    private static volatile String edgeToken;
    private static volatile long edgeTokenAt;

    /** Microsoft Edge's built-in translator (no key; the same door the browser uses). */
    private static String edgeTranslate(String text, String from, String to) throws Exception {
        long now = System.currentTimeMillis();
        if (edgeToken == null || now - edgeTokenAt > 8 * 60 * 1000L) {
            HttpURLConnection a = (HttpURLConnection) new URL("https://edge.microsoft.com/translate/auth").openConnection();
            a.setConnectTimeout(4000);
            a.setReadTimeout(5000);
            if (a.getResponseCode() != 200) throw new UserMessage("no token (" + a.getResponseCode() + ")");
            edgeToken = read(a.getInputStream()).trim();
            edgeTokenAt = now;
        }
        String url = "https://api-edge.cognitive.microsofttranslator.com/translate?api-version=3.0&to=" + to + (from.equals("auto") ? "" : "&from=" + from);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(4000);
        c.setReadTimeout(8000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + edgeToken);
        OutputStream os = c.getOutputStream();
        os.write(new JSONArray().put(new JSONObject().put("Text", text)).toString().getBytes("UTF-8"));
        os.close();
        int code = c.getResponseCode();
        if (code != 200) { edgeToken = null; throw new UserMessage("answered " + code); }
        JSONArray r = new JSONArray(read(c.getInputStream()));
        return r.getJSONObject(0).getJSONArray("translations").getJSONObject(0).getString("text");
    }

    /** Google's other translate door (the one its Chrome extension uses). */
    private static String googleAltTranslate(String text, String from, String to) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=" + from + "&tl=" + to
                + "&q=" + java.net.URLEncoder.encode(text, "UTF-8")).openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(7000);
        int code = c.getResponseCode();
        if (code != 200) throw new UserMessage("answered " + code);
        Object o = new org.json.JSONTokener(read(c.getInputStream())).nextValue();
        if (o instanceof JSONArray && ((JSONArray) o).length() > 0) {
            Object f = ((JSONArray) o).get(0);
            if (f instanceof String) return (String) f;
            if (f instanceof JSONArray) return ((JSONArray) f).getString(0);
        }
        throw new UserMessage("unknown answer");
    }

    private static final String[] LINGVA = {"https://lingva.ml", "https://lingva.garudalinux.org", "https://translate.plausibility.cloud", "https://lingva.lunar.icu"};

    /** Lingva: open-source front doors to Google Translate run by volunteers (no key). Short texts only; the first two that answer are enough. */
    private static String lingvaTranslate(String text, String from, String to) throws Exception {
        if (text.length() > 1200) throw new UserMessage("text too long for Lingva");
        String q = java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20");
        String last = "no instance answered";
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < 2 && i < LINGVA.length; i++) {            // two instances at most, 7 s at most: the volunteers' servers can hang for a long time
            String h = LINGVA[i];
            if (System.currentTimeMillis() - t0 > 7000) break;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(h + "/api/v1/" + from + "/" + to + "/" + q).openConnection();
                c.setConnectTimeout(2500);
                c.setReadTimeout(3500);
                int code = c.getResponseCode();
                if (code != 200) { last = "answered " + code; continue; }
                String t = new JSONObject(read(c.getInputStream())).optString("translation", "");
                if (!t.isEmpty()) return t;
            } catch (Exception e) { last = e.getClass().getSimpleName().replace("Exception", ""); }
        }
        throw new UserMessage(last);
    }

    /** The translator ON the phone (Google's on-device model, free, no limit, works without internet once downloaded from the Bolchi app). */
    private static String offlineTranslate(String text, String to) throws Exception {
        String from = hasDevanagari(text) ? "hi" : hasBengali(text) ? "bn" : "en";
        if (from.equals(to)) throw new UserMessage("nothing to translate");
        return Offline.translate(text, from, to);
    }

    // ---------- AI: translate / Bangla script ----------
    static String translate(String text, String key) throws Exception {
        return gemini(key,
                "Translate the user's message into natural, simple English. The message may be Bengali or Hindi "
                        + "written in English letters (Banglish or Hinglish), Bengali or Hindi script, or mixed with English. "
                        + "Keep names, numbers and emojis as they are. Reply with only the translation, nothing else.",
                text);
    }

    /** Tidies dictated text: keeps the speaker's corrected version, drops fillers and repeats. */
    /** What Google said the last time a request was refused (for the Test button). */
    static volatile String lastDetail = "";

    /** One real AI request, with a plain answer about what happened. */
    static String test(String key) {
        lastDetail = "";
        try {
            return "AI works. It wrote: " + tidy("aai em apdeting phrom the link, ami bhat khabo", key);
        } catch (Throwable t) {
            String why = lastDetail.isEmpty() ? t.getClass().getSimpleName() + ": " + t.getMessage() : lastDetail;
            return "AI did NOT work.\n" + why;
        }
    }

    private interface Probe { String run() throws Exception; }

    private static String probe(String name, Probe p) {
        long t0 = System.currentTimeMillis();
        try {
            String r = p.run();
            if (r != null && r.length() > 40) r = r.substring(0, 40);
            return "\u2713 " + name + " (" + (System.currentTimeMillis() - t0) + " ms): " + (r == null ? "" : r.replace('\n', ' ')) + "\n";
        } catch (Throwable t) {
            String m = t.getMessage() == null ? "" : t.getMessage();
            if (m.length() > 120) m = m.substring(0, 120);
            return "\u2717 " + name + " (" + (System.currentTimeMillis() - t0) + " ms): " + t.getClass().getSimpleName() + (m.isEmpty() ? "" : " - " + m) + "\n";
        }
    }

    /** One report for the Bolchi app: which of the services the keyboard uses can be reached from this phone right now, how long each took, and why not. */
    static String testAll(final String key) {
        clearDown();
        StringBuilder b = new StringBuilder("Connection test\n");
        b.append(probe("Google Translate", new Probe() { public String run() throws Exception { return googleTranslateTo("hello", "en", "bn"); } }));
        b.append(probe("Edge translator", new Probe() { public String run() throws Exception { return edgeTranslate("hello", "en", "bn"); } }));
        b.append(probe("Google (other door)", new Probe() { public String run() throws Exception { return googleAltTranslate("hello", "en", "bn"); } }));
        b.append(probe("Lingva", new Probe() { public String run() throws Exception { return lingvaTranslate("hello", "en", "bn"); } }));
        b.append(probe("MyMemory", new Probe() { public String run() throws Exception { return myMemory("good morning", "en", "bn"); } }));
        b.append(probe("On the phone (en->bn)", new Probe() { public String run() throws Exception { return Offline.translate("good morning", "en", "bn"); } }));
        b.append(probe("Bangla letters (inputtools)", new Probe() { public String run() throws Exception { return Phonetic.suggest("ami", false).get(0); } }));
        if (key == null || key.trim().isEmpty()) b.append("- Gemini: no key saved\n");
        else b.append(probe("Gemini", new Probe() { public String run() throws Exception {
            try { return gemini(MODEL, key, "Reply with the single word ok", "hello"); }
            catch (Busy bz) { throw new UserMessage("busy, Google answered " + bz.code + " " + (lastDetail == null ? "" : lastDetail)); }
            catch (ModelMissing mm) { throw new UserMessage("model name not found (404)"); }
        } }));
        for (final Prov p : new Prov[]{GROQ, CEREBRAS, MISTRAL, OPENROUTER, POLLINATIONS}) {
            if (p.key.isEmpty()) { b.append("- ").append(p.name).append(": no key saved\n"); continue; }
            b.append(probe(p.name + (p.key.equals("unused") || p.key.equals("none") ? " (no key)" : ""), new Probe() { public String run() throws Exception {
                String[] ms = p.listUrl == null ? p.fallback : discover(p.listUrl, p.key, p.prefer, p.freeOnly);
                return ms[0] + " -> " + chatCompat(p.chatUrl, p.key, ms[0], "Reply with the single word ok", "hello");
            } }));
        }
        return b.toString();
    }

    static String tidy(String text, String key) throws Exception {
        return gemini(key,
                "The user dictated this text by voice on a Bengali/Hindi speech engine. The speaker mixes Bengali (or Hindi) "
                        + "with English. Bengali and Hindi words are written in English letters (Banglish, Hinglish): keep those "
                        + "words exactly as they are, do not translate them and do not change their spelling. But English words "
                        + "and English sentences were written down by sound, for example 'aai em apdeting phrom the link yor "
                        + "provaid' or 'miting', 'ophis', 'sekshan': rewrite every such English word in its correct English "
                        + "spelling ('I am updating from the link you provided', 'meeting', 'office', 'section'). If the text is "
                        + "in Bengali or Hindi script, keep that script and write the English words in English letters. "
                        + "When the speaker corrects themselves (for example 'at five, no sorry, six'), keep only the corrected "
                        + "version. Remove stammering and false starts: a broken piece of a word followed by the full word "
                        + "('wh what's' becomes 'What's', 'I I think' becomes 'I think', 'ami ami jabo' becomes 'ami jabo'), and "
                        + "filler sounds like 'um', 'uh', 'aa'. Remove accidental repeats. Add punctuation and capital letters, choosing each mark from the meaning and tone: a question ends with a question mark, "
                        + "excitement or a greeting with an exclamation mark, a list gets commas, a new thought starts a new sentence. Do not add or remove "
                        + "meaning and do not answer the text. Reply with only the cleaned text.",
                text);
    }

    /** Fixes the grammar of what she wrote, or rewrites it so it reads well. Same language and script, nothing translated. */
    static String polish(String text, String key, boolean rewrite) throws Exception {
        String keep = "Keep the meaning, the tone and the language exactly: English stays English, Banglish or Hinglish stays in English letters, "
                + "Bengali or Hindi script stays in that script. Keep names, numbers, emojis and links as they are. Do not translate and do not add new information. ";
        String task = rewrite
                ? "Rewrite the user's message so it is clear, natural and well written, correcting any wrong sentence. "
                : "Fix the grammar, spelling and punctuation of the user's message, changing as little else as possible. ";
        return gemini(key, task + keep + "Reply with only the resulting message, nothing else.", text);
    }

    /** Three ready answers to a message she received (one per line). */
    static String replies(String received, String key) throws Exception {
        return gemini(key, "The user received the message below and wants to answer it. Write three different short replies the user could send: "
                + "one that agrees or says yes, one neutral that asks for a little time or more detail, and one that politely declines. "
                + "Write in the same language and style as the message (English stays English, Banglish or Hinglish stays in English letters, "
                + "Bengali or Hindi script stays in that script). Each reply on its own line, one or two sentences, no numbering, no labels, nothing else.",
                received);
    }

    static final String[] TONES = {"Write it for me", "Formal", "Friendly", "Short", "Polite", "Email", "Funny"};

    /** Says the same thing in another tone, or turns a few rough words into a complete message. */
    static String tone(String text, String key, int tone) throws Exception {
        String keep = "Keep the language exactly: English stays English, Banglish or Hinglish stays in English letters, Bengali or Hindi script "
                + "stays in that script. Keep names, numbers and links as they are. Do not invent facts. Reply with only the message, nothing else.";
        String task;
        switch (tone) {
            case 1: task = "Rewrite the user's message in a formal, professional tone suitable for office communication. "; break;
            case 2: task = "Rewrite the user's message in a warm, friendly, casual tone. "; break;
            case 3: task = "Rewrite the user's message as short as possible while keeping every important point. "; break;
            case 4: task = "Rewrite the user's message so it is polite and respectful, softening anything that sounds rude or abrupt. "; break;
            case 5: task = "Turn the user's message into a short, well-structured email body with a greeting and a closing line (no subject line, no signature name). "; break;
            case 6: task = "Rewrite the user's message in a light, funny tone, with one or two fitting emojis. "; break;
            default: task = "The user wrote a few rough words about what they want to say. Write the complete, natural, polite message they would send "
                    + "(one to three sentences), as if written by them. "; break;
        }
        return gemini(key, task + keep, text);
    }

    static String toBanglaScript(String text, String key) throws Exception {
        return gemini(key,
                "Rewrite the user's text in Bengali script. The text is Bengali written in English letters (Banglish), "
                        + "possibly mixed with English words. Do not translate or change the words, only the script; write "
                        + "English words the way Bengalis normally write them in Bengali script. Keep numbers, punctuation "
                        + "and emojis. Reply with only the converted text, nothing else.",
                text);
    }

    // ---------- the AI services: every one that can answer is tried, the fast ones first; a service that failed is left alone for a few minutes ----------
    private static final java.util.HashMap<String, Long> DOWN = new java.util.HashMap<>();
    private static synchronized boolean down(String name) { Long t = DOWN.get(name); return t != null && System.currentTimeMillis() < t; }
    private static synchronized void mark(String name, long ms) { DOWN.put(name, System.currentTimeMillis() + ms); }
    static synchronized void clearDown() { DOWN.clear(); googleTranslateBlockedUntil = 0; }

    /** A service that speaks the common "chat completions" format. Keyless ones have a fixed key. `listUrl` = where it names its models (null: fixed names). */
    private static final class Prov {
        final String name, chatUrl, listUrl; final String[] prefer, fallback; final boolean freeOnly;
        volatile String key = ""; volatile String[] picked; volatile long pickedAt;
        Prov(String n, String base, String list, String[] prefer, String[] fallback, boolean freeOnly) {
            name = n; chatUrl = base; listUrl = list; this.prefer = prefer; this.fallback = fallback; this.freeOnly = freeOnly;
        }
    }
    private static final String[] NOT_CHAT = {"whisper", "guard", "tts", "orpheus", "embed", "moderation", "image", "audio", "safeguard", "playai", "allam", "-ocr"};
    static final Prov GROQ = new Prov("Groq", "https://api.groq.com/openai/v1/chat/completions", "https://api.groq.com/openai/v1/models",
            new String[]{"llama-3.3-70b", "gpt-oss-120b", "llama-4-maverick", "llama-4-scout", "qwen", "llama-3.1-8b", "gpt-oss-20b", "gemma"}, new String[]{"llama-3.3-70b-versatile", "llama-3.1-8b-instant"}, false);
    static final Prov CEREBRAS = new Prov("Cerebras", "https://api.cerebras.ai/v1/chat/completions", "https://api.cerebras.ai/v1/models",
            new String[]{"gpt-oss-120b", "llama-3.3-70b", "qwen", "llama"}, new String[]{"llama3.1-8b"}, false);
    static final Prov MISTRAL = new Prov("Mistral", "https://api.mistral.ai/v1/chat/completions", "https://api.mistral.ai/v1/models",
            new String[]{"mistral-small", "mistral-medium", "open-mistral", "ministral"}, new String[]{"mistral-small-latest"}, false);
    static final Prov OPENROUTER = new Prov("OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "https://openrouter.ai/api/v1/models",
            new String[]{"llama-3.3-70b", "gpt-oss-120b", "qwen", "gemma", "mistral", "deepseek", "llama"}, new String[]{"meta-llama/llama-3.3-70b-instruct:free", "google/gemma-3-27b-it:free"}, true);
    // no key at all: open projects that answer anyone (they can be slow or busy, so they come last)
    static final Prov LLM7 = new Prov("LLM7", "https://api.llm7.io/v1/chat/completions", "https://api.llm7.io/v1/models",
            new String[]{"gpt-4.1", "deepseek", "llama", "qwen", "mistral"}, new String[]{"gpt-4.1-nano-2025-04-14"}, false);
    static final Prov POLLINATIONS = new Prov("Pollinations", "https://text.pollinations.ai/openai", null, new String[0], new String[]{"openai"}, false);
    static { LLM7.key = "unused"; POLLINATIONS.key = "none"; }
    private static final Prov[] FAST = {GROQ, CEREBRAS, MISTRAL};
    private static final Prov[] LAST = {OPENROUTER, POLLINATIONS};       // LLM7 now wants a key (401 on her test): left out

    static volatile String groqKey = "", openrouterKey = "";
    /** Reads the keys she saved in the Bolchi app (all optional). */
    static void useExtraKeys(android.content.SharedPreferences p) {
        try {
            GROQ.key = p.getString("groqKey", "").trim(); groqKey = GROQ.key;
            CEREBRAS.key = p.getString("cerebrasKey", "").trim();
            MISTRAL.key = p.getString("mistralKey", "").trim();
            OPENROUTER.key = p.getString("openrouterKey", "").trim(); openrouterKey = OPENROUTER.key;
        } catch (Throwable ignored) { /* keep what we had */ }
    }

    /** The models tried one after the other when Google's AI is busy (503 / 500 / 502 / 504), over its free limit (429: each model has its own limit) or a name is not known (404). */
    private static final String[] MODELS = {MODEL, MODEL_BACKUP, "gemini-2.5-flash-lite", "gemini-2.5-flash"};

    private static String gemini(String key, String instruction, String text) throws Exception {
        long t0 = System.currentTimeMillis();
        StringBuilder why = new StringBuilder();
        for (Prov p : FAST) {
            if (System.currentTimeMillis() - t0 > 22000) break;
            String r = viaProv(p, why, instruction, text);
            if (r != null) return r;
        }
        int busyCode = 0;
        java.io.IOException net = null;
        UserMessage keyMsg = null;
        boolean haveGoogle = key != null && !key.trim().isEmpty();
        if (haveGoogle && !down("Gemini")) {
            for (String m : MODELS) {
                if (System.currentTimeMillis() - t0 > 22000) break;
                try {
                    return gemini(m, key, instruction, text);
                } catch (ModelMissing mm) {
                    continue;
                } catch (Busy b) {
                    busyCode = b.code;
                    try { Thread.sleep(400); } catch (InterruptedException ie) { break; }
                } catch (java.io.IOException io) {
                    net = io;                                              // Google's AI cannot be reached at all: do not try every model
                    break;
                } catch (UserMessage um) {
                    keyMsg = um;                                           // key refused: the others may still answer
                    break;
                }
            }
            mark("Gemini", 5 * 60 * 1000L);
            if (keyMsg != null) why.append("Gemini: ").append(keyMsg.getMessage()).append(". ");
            else if (net != null) why.append("Gemini: no connection (").append(net.getClass().getSimpleName().replace("Exception", "")).append("). ");
            else if (busyCode == 429) why.append("Gemini: free limit (429). ");
            else if (busyCode != 0) why.append("Gemini: busy (").append(busyCode).append("). ");
            else why.append("Gemini: model not available. ");
        } else if (haveGoogle) why.append("Gemini: skipped (failed lately). ");
        for (Prov p : LAST) {
            if (System.currentTimeMillis() - t0 > 26000) break;
            String r = viaProv(p, why, instruction, text);
            if (r != null) return r;
        }
        throw new UserMessage(why.toString().trim());
    }

    /** One service: its models best first. Null when it has no key, was left alone, or none answered (`why` gets one short phrase). */
    private static String viaProv(Prov p, StringBuilder why, String instruction, String text) {
        if (p.key.isEmpty()) return null;
        if (down(p.name)) { why.append(p.name).append(": skipped (failed lately). "); return null; }
        String last = "";
        for (String m : modelsFor(p)) {
            try { stage = "asking " + p.name; return chatCompat(p.chatUrl, p.key, m, instruction, text); }
            catch (Exception e) {
                last = shortWhy(e);
                if (last.startsWith("key refused")) { mark(p.name, 30 * 60 * 1000L); break; }
                if (last.startsWith("limit reached")) { mark(p.name, 10 * 60 * 1000L); break; }
            }
        }
        if (!down(p.name)) mark(p.name, 3 * 60 * 1000L);
        why.append(p.name).append(": ").append(last).append(". ");
        return null;
    }

    // model names change over time: ask the service which models exist (kept for 6 hours), the fixed lists are only the fallback
    /** The ids from a "list models" answer, best first: preferred names in order, no speech / guard models, and for OpenRouter only free ones. */
    private static String[] discover(String url, String key, String[] prefer, boolean freeOnly) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        current = c;
        c.setConnectTimeout(5000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Authorization", "Bearer " + key);
        int code = c.getResponseCode();
        String resp = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code == 401 || code == 403) throw new UserMessage("key refused (" + code + "): copy the whole key again");
        if (code >= 400) throw new UserMessage("model list answered " + code);
        JSONArray data = new JSONObject(resp).getJSONArray("data");
        java.util.List<String> ok = new java.util.ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject m = data.getJSONObject(i);
            String id = m.optString("id", "");
            String low = id.toLowerCase(Locale.ROOT);
            boolean bad = id.isEmpty();
            for (String n : NOT_CHAT) if (low.contains(n)) bad = true;
            if (freeOnly) {
                JSONObject pr = m.optJSONObject("pricing");
                boolean free = low.endsWith(":free") || (pr != null && "0".equals(pr.optString("prompt")) && "0".equals(pr.optString("completion")));
                if (!free) bad = true;
            } else if (m.has("active") && !m.optBoolean("active", true)) bad = true;
            if (!bad) ok.add(id);
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String p : prefer) for (String id : ok) if (id.toLowerCase(Locale.ROOT).contains(p) && !out.contains(id) && out.size() < 4) out.add(id);
        if (out.isEmpty()) for (String id : ok) { if (out.size() < 3) out.add(id); }
        if (out.isEmpty()) throw new UserMessage("no usable model in the list");
        return out.toArray(new String[0]);
    }

    private static String[] modelsFor(Prov p) {
        if (p.listUrl == null) return p.fallback;
        long now = System.currentTimeMillis();
        if (p.picked != null && now - p.pickedAt < 6 * 3600 * 1000L) return p.picked;
        try { p.picked = discover(p.listUrl, p.key, p.prefer, p.freeOnly); p.pickedAt = now; return p.picked; }
        catch (Exception e) { return p.fallback; }
    }

    private static String shortAi(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return m.length() > 150 ? m.substring(0, 150) : m;
    }

    private static String shortWhy(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName().replace("Exception", "") : e.getMessage();
        return m.length() > 70 ? m.substring(0, 70) : m;
    }

    /** One request to a service that speaks the common "chat completions" format (Groq, OpenRouter). */
    private static String chatCompat(String url, String key, String model, String instruction, String text) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("temperature", 0.3);
        body.put("messages", new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", instruction))
                .put(new JSONObject().put("role", "user").put("content", text)));
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        current = c;
        c.setRequestMethod("POST");
        c.setConnectTimeout(5000);
        c.setReadTimeout(14000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("X-Title", "Bolchi Keyboard");
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();
        int code = c.getResponseCode();
        String resp = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) {
            String detail = "";
            try { detail = new JSONObject(resp).getJSONObject("error").optString("message", ""); } catch (Throwable ignored) { detail = resp == null ? "" : resp.replace('\n', ' '); }
            if (detail.length() > 60) detail = detail.substring(0, 60);
            if (code == 401 || code == 403) throw new UserMessage("key refused (" + code + ")");
            if (code == 429) throw new UserMessage("limit reached (429)");
            throw new UserMessage(code + " " + detail);
        }
        JSONArray ch = new JSONObject(resp).optJSONArray("choices");
        if (ch == null || ch.length() == 0) throw new UserMessage("no answer");
        String out = ch.getJSONObject(0).getJSONObject("message").optString("content", "").trim();
        if (out.isEmpty()) throw new UserMessage("no answer");
        return out;
    }

    // ---------- translating without any key: MyMemory (a free translation memory, limited per day; 500 letters per request) ----------
    private static String myMemory(String text, String from, String to) throws Exception {
        StringBuilder out = new StringBuilder();
        String rest = text.trim();
        while (!rest.isEmpty()) {
            int cut = Math.min(rest.length(), 420);
            if (cut < rest.length()) {
                int p = Math.max(rest.lastIndexOf('.', cut - 1), Math.max(rest.lastIndexOf('?', cut - 1), Math.max(rest.lastIndexOf('!', cut - 1), rest.lastIndexOf(' ', cut - 1))));
                if (p > 100) cut = p + 1;
            }
            String piece = rest.substring(0, cut).trim();
            rest = rest.substring(cut).trim();
            if (piece.isEmpty()) continue;
            stage = "asking MyMemory";
            HttpURLConnection c = (HttpURLConnection) new URL("https://api.mymemory.translated.net/get?langpair=" + from + "%7C" + to + "&q="
                    + java.net.URLEncoder.encode(piece, "UTF-8")).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) throw new UserMessage("MyMemory did not answer (" + c.getResponseCode() + ")");
            JSONObject r = new JSONObject(read(c.getInputStream()));
            String t = r.getJSONObject("responseData").optString("translatedText", "");
            if (t.isEmpty() || t.toUpperCase(Locale.ROOT).contains("MYMEMORY WARNING")) throw new UserMessage("MyMemory limit reached for today");
            if (out.length() > 0) out.append(' ');
            out.append(t);
        }
        return out.toString();
    }

    /** Last fallback of every translation: MyMemory, with the source language taken from the letters (Hindi / Bengali script, else English). */
    private static String myMemoryAuto(String text, String to) throws Exception {
        String from = hasDevanagari(text) ? "hi" : hasBengali(text) ? "bn" : "en";
        if (from.equals(to)) throw new UserMessage("nothing for MyMemory to translate");
        return myMemory(text, from, to);
    }

    private static String gemini(String model, String key, String instruction, String text) throws Exception {
        if (key == null || key.trim().isEmpty()) throw new UserMessage("Add your Google AI Studio key in the Bolchi app first");
        JSONObject body = new JSONObject();
        body.put("systemInstruction", new JSONObject().put("parts",
                new JSONArray().put(new JSONObject().put("text", instruction))));
        body.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts",
                new JSONArray().put(new JSONObject().put("text", text)))));
        HttpURLConnection c = (HttpURLConnection) new URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent").openConnection();
        current = c;
        stage = "connecting to Google";
        c.setRequestMethod("POST");
        c.setConnectTimeout(4000);
        c.setReadTimeout(20000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("x-goog-api-key", key.trim());
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();
        stage = "waiting for the AI answer";
        int code = c.getResponseCode();
        stage = "reading the answer";
        String resp = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) lastDetail = "Google answered " + code + ": " + (resp.length() > 300 ? resp.substring(0, 300) : resp);
        if (code == 404) throw new ModelMissing();
        if (code == 400 || code == 401 || code == 403) {
            throw new UserMessage(resp.contains("API_KEY") || resp.contains("API key")
                    ? "Your AI key isn't working. Check it in the Bolchi app" : "The AI service refused this request");
        }
        if (code == 429 || code == 500 || code == 502 || code == 503 || code == 504) throw new Busy(code);        // the next model may answer
        if (code >= 400) throw new UserMessage("AI service problem (" + code + "). Try again");
        JSONObject j = new JSONObject(resp);
        JSONArray cands = j.optJSONArray("candidates");
        if (cands == null || cands.length() == 0) throw new UserMessage("No answer came back. Try again");
        JSONArray parts = cands.getJSONObject(0).getJSONObject("content").getJSONArray("parts");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject p = parts.getJSONObject(i);
            if (p.optBoolean("thought", false)) continue;
            b.append(p.optString("text", ""));
        }
        String out = b.toString().trim();
        if (out.isEmpty()) throw new UserMessage("No answer came back. Try again");
        return out;
    }

    // ---------- units and currency ----------
    private static final Pattern AMOUNT = Pattern.compile(
            "(?i)(₹|rs\\.?|inr|usd|\\$|€|£|¥|৳|₩|฿)?\\s*(\\d+(?:,\\d+)*(?:\\.\\d+)?)\\s*(k(?![a-z])|lakhs?|lacs?|crores?|cr(?![a-z]))?\\s*"
                    + "(kgs?|kilos?|kilograms?|lbs?|pounds?|grams?|gms?|g|oz|ounces?|"
                    + "kms?|kilometers?|kilometres?|miles?|mi|meters?|metres?|m|ft|feet|foot|cms?|inch(?:es)?|"
                    + "litres?|liters?|l|ml|gallons?|gal|°?c|°?f|celsius|fahrenheit|degrees?|"
                    + "inr|rupees?|rs\\.?|/-|usd|dollars?|eur|euros?|gbp|aed|dirhams?|"
                    + "sgd|aud|cad|jpy|yen|cny|yuan|chf|francs?|sar|riyals?|qar|kwd|dinars?|omr|bhd|bdt|taka|npr|lkr|pkr|thb|baht|myr|ringgit|hkd|nzd|zar|krw|idr|rub|rubles?)?(?![A-Za-z])");

    /** The number as written: 1,500 and 1,00,000 are thousands; 5,5 is five and a half; k / lakh / crore multiply. */
    private static double amount(String num, String mult) {
        String t = num.matches("\\d+,\\d{1,2}") ? num.replace(',', '.') : num.replace(",", "");
        double v = Double.parseDouble(t);
        String x = mult == null ? "" : mult.toLowerCase(Locale.ROOT);
        if (x.equals("k")) v *= 1000;
        else if (x.startsWith("la")) v *= 100000;
        else if (x.startsWith("cr")) v *= 10000000;
        return v;
    }

    private static String g(Matcher m, int i) { return m.group(i) == null ? "" : m.group(i).toLowerCase(Locale.ROOT); }

    /** Returns one line per amount found, e.g. "5 kg = 11.02 lb". Currency lines need usdRates. */
    static String units(String text, JSONObject usdRates) {
        Matcher m = AMOUNT.matcher(FontStyles.plain(text));       // digits typed in a font are still digits
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String sym = g(m, 1), mult = g(m, 3);
            String unit = g(m, 4).replace("°", "");
            if (sym.isEmpty() && unit.isEmpty() && !(mult.startsWith("la") || mult.startsWith("cr"))) continue;
            double v;
            try { v = amount(m.group(2), mult); } catch (Exception e) { continue; }
            String line = convertOne(v, sym, unit.isEmpty() && sym.isEmpty() ? "inr" : unit, usdRates);     // "5 lakh" means rupees
            if (line != null) {
                if (out.length() > 0) out.append(" · ");
                out.append(line);
            }
        }
        return out.toString();
    }

    static boolean hasCurrency(String text) {
        Matcher m = AMOUNT.matcher(FontStyles.plain(text));
        while (m.find()) {
            String sym = g(m, 1), mult = g(m, 3), unit = g(m, 4);
            if (currencyOf(sym, unit) != null) return true;
            if (sym.isEmpty() && unit.isEmpty() && (mult.startsWith("la") || mult.startsWith("cr"))) return true;
        }
        return false;
    }

    /** True when the piece just typed is one amount of money: "10rs", "₹500", "$20", "50usd", "5lakh". */
    static boolean isMoney(String token) {
        Matcher m = AMOUNT.matcher(FontStyles.plain(token));
        if (!m.matches()) return false;
        String mult = g(m, 3);
        return currencyOf(g(m, 1), g(m, 4)) != null || (g(m, 1).isEmpty() && g(m, 4).isEmpty() && (mult.startsWith("la") || mult.startsWith("cr")));
    }

    /** The typed amount in every other currency the keyboard knows, with signs (rupees first when she typed foreign money). Null when it is not money or a rate is missing. */
    static String[] money(String token, JSONObject rates) {
        Matcher m = AMOUNT.matcher(FontStyles.plain(token));
        if (rates == null || !m.matches()) return null;
        String cur = currencyOf(g(m, 1), g(m, 4));
        if (cur == null) cur = "INR";
        double v;
        try { v = amount(m.group(2), g(m, 3)); } catch (Exception e) { return null; }
        double from = cur.equals("USD") ? 1 : rates.optDouble(cur, 0);
        if (from <= 0) return null;
        java.util.List<String> l = new java.util.ArrayList<>();
        for (String c : CURRENCIES) {
            if (c.equals(cur)) continue;
            double t = c.equals("USD") ? 1 : rates.optDouble(c, 0);
            if (t > 0) l.add(sign(c) + n(v / from * t));
        }
        return l.isEmpty() ? null : l.toArray(new String[0]);
    }

    /** Country code (for the flag), currency name and country of every currency in CURRENCIES, same order. */
    private static final String[][] LANDS = {{"IN", "Indian Rupee", "India"}, {"US", "US Dollar", "United States"}, {"EU", "Euro", "Europe"},
            {"GB", "Pound", "United Kingdom"}, {"AE", "Dirham", "UAE (Dubai)"}, {"SG", "Singapore Dollar", "Singapore"}, {"AU", "Australian Dollar", "Australia"},
            {"CA", "Canadian Dollar", "Canada"}, {"JP", "Yen", "Japan"}, {"CN", "Yuan", "China"}, {"CH", "Swiss Franc", "Switzerland"}, {"SA", "Riyal", "Saudi Arabia"},
            {"QA", "Riyal", "Qatar"}, {"KW", "Dinar", "Kuwait"}, {"OM", "Rial", "Oman"}, {"BH", "Dinar", "Bahrain"}, {"BD", "Taka", "Bangladesh"},
            {"NP", "Rupee", "Nepal"}, {"LK", "Rupee", "Sri Lanka"}, {"PK", "Rupee", "Pakistan"}, {"TH", "Baht", "Thailand"}, {"MY", "Ringgit", "Malaysia"},
            {"HK", "Dollar", "Hong Kong"}, {"NZ", "Dollar", "New Zealand"}, {"ZA", "Rand", "South Africa"}, {"KR", "Won", "South Korea"},
            {"ID", "Rupiah", "Indonesia"}, {"RU", "Ruble", "Russia"}};

    private static String flag(String cc) {
        return new String(Character.toChars(0x1F1E6 + cc.charAt(0) - 'A')) + new String(Character.toChars(0x1F1E6 + cc.charAt(1) - 'A'));
    }

    /**
     * For the converter list: the last amount of money in the text in every other currency. Each row is {flag + currency + country, value with sign};
     * row 0 is {what she typed (e.g. "₹10"), its currency and country}. Null when there is no money in the text or the rates are not here yet.
     */
    static java.util.List<String[]> moneyList(String text) {
        JSONObject rates = ratesNow();
        if (rates == null) return null;
        Matcher m = AMOUNT.matcher(FontStyles.plain(text));
        String cur = null;
        double v = 0;
        while (m.find()) {
            String sym = g(m, 1), mult = g(m, 3), unit = g(m, 4);
            String c = currencyOf(sym, unit);
            if (c == null && sym.isEmpty() && unit.isEmpty() && (mult.startsWith("la") || mult.startsWith("cr"))) c = "INR";
            if (c == null) continue;
            try { v = amount(m.group(2), mult); cur = c; } catch (Exception e) { /* not a number */ }
        }
        if (cur == null) return null;
        double from = cur.equals("USD") ? 1 : rates.optDouble(cur, 0);
        if (from <= 0) return null;
        java.util.List<String[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < CURRENCIES.length; i++) {
            String c = CURRENCIES[i];
            String label = flag(LANDS[i][0]) + "  " + LANDS[i][1] + " \u00B7 " + LANDS[i][2];
            if (c.equals(cur)) { out.add(0, new String[]{sign(c) + n(v), label}); continue; }
            double t = c.equals("USD") ? 1 : rates.optDouble(c, 0);
            if (t > 0) out.add(new String[]{label, sign(c) + n(v / from * t)});
        }
        return out.size() < 2 ? null : out;
    }

    /** The rates if they are already here (no waiting); null when they still have to be fetched. */
    static JSONObject ratesNow() {
        return ratesKept != null && System.currentTimeMillis() - ratesAt < 3600 * 1000L ? ratesKept : null;
    }

    /** Every currency the keyboard knows, in the order they are offered. */
    static final String[] CURRENCIES = {"INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD", "JPY", "CNY", "CHF", "SAR", "QAR", "KWD", "OMR", "BHD",
            "BDT", "NPR", "LKR", "PKR", "THB", "MYR", "HKD", "NZD", "ZAR", "KRW", "IDR", "RUB"};

    private static String currencyOf(String sym, String unit) {
        if (unit.startsWith("dollar")) return "USD";
        if (unit.startsWith("euro")) return "EUR";
        if (unit.startsWith("dirham")) return "AED";
        if (unit.equals("yen")) return "JPY";
        if (unit.equals("yuan")) return "CNY";
        if (unit.startsWith("franc")) return "CHF";
        if (unit.startsWith("riyal")) return "SAR";
        if (unit.startsWith("dinar")) return "KWD";
        if (unit.equals("taka")) return "BDT";
        if (unit.equals("baht")) return "THB";
        if (unit.equals("ringgit")) return "MYR";
        if (unit.startsWith("ruble")) return "RUB";
        if (unit.startsWith("rupee") || unit.startsWith("rs") || unit.equals("/-")) return "INR";
        if (unit.length() == 3) for (String c : CURRENCIES) if (c.equalsIgnoreCase(unit)) return c;
        if (sym.equals("₹") || sym.startsWith("rs") || sym.equals("inr")) return "INR";
        if (sym.equals("$") || sym.equals("usd")) return "USD";
        if (sym.equals("€")) return "EUR";
        if (sym.equals("£")) return "GBP";
        if (sym.equals("¥")) return "JPY";
        if (sym.equals("৳")) return "BDT";
        if (sym.equals("₩")) return "KRW";
        if (sym.equals("฿")) return "THB";
        return null;
    }

    private static String n(double d) {
        if (Math.abs(d - Math.rint(d)) < 1e-9) return String.format(Locale.ROOT, "%,.0f", d);
        return String.format(Locale.ROOT, "%,.2f", d);
    }

    private static String sign(String cur) {
        switch (cur) {
            case "INR": return "₹"; case "USD": return "$"; case "EUR": return "€"; case "GBP": return "£";
            case "JPY": return "¥"; case "CNY": return "CN¥"; case "BDT": return "৳"; case "KRW": return "₩"; case "THB": return "฿"; case "RUB": return "₽";
            case "AUD": return "A$"; case "CAD": return "C$"; case "SGD": return "S$"; case "HKD": return "HK$"; case "NZD": return "NZ$";
            default: return cur + " ";
        }
    }

    private static String convertOne(double v, String sym, String unit, JSONObject rates) {
        String cur = currencyOf(sym, unit);
        if (cur != null) {
            if (rates == null) return null;
            double from = cur.equals("USD") ? 1 : rates.optDouble(cur, 0);
            if (from <= 0) return null;
            // rupees are shown in the four she is most likely to need; any other money is shown in rupees
            StringBuilder b = new StringBuilder(sign(cur) + n(v) + " = about ");
            int k = 0;
            for (String to : cur.equals("INR") ? new String[]{"USD", "EUR", "GBP", "AED"} : new String[]{"INR"}) {
                double target = to.equals("USD") ? 1 : rates.optDouble(to, 0);
                if (target <= 0) continue;
                if (k++ > 0) b.append(" / ");
                b.append(sign(to)).append(n(v / from * target));
            }
            return k == 0 ? null : b.toString();
        }
        if (unit.matches("kgs?|kilos?|kilograms?")) return n(v) + " kg = " + n(v * 2.20462) + " lb";
        if (unit.matches("lbs?|pounds?")) return n(v) + " lb = " + n(v / 2.20462) + " kg";
        if (unit.matches("grams?|gms?|g")) return n(v) + " g = " + n(v / 28.3495) + " oz";
        if (unit.matches("oz|ounces?")) return n(v) + " oz = " + n(v * 28.3495) + " g";
        if (unit.matches("kms?|kilometers?|kilometres?")) return n(v) + " km = " + n(v / 1.60934) + " miles";
        if (unit.matches("miles?|mi")) return n(v) + " miles = " + n(v * 1.60934) + " km";
        if (unit.matches("meters?|metres?|m")) return n(v) + " m = " + n(v * 3.28084) + " ft";
        if (unit.matches("ft|feet|foot")) return n(v) + " ft = " + n(v / 3.28084) + " m";
        if (unit.matches("cms?")) return n(v) + " cm = " + n(v / 2.54) + " in";
        if (unit.matches("inch(?:es)?|in")) return n(v) + " in = " + n(v * 2.54) + " cm";
        if (unit.matches("litres?|liters?|l")) return n(v) + " L = " + n(v / 3.78541) + " gal";
        if (unit.equals("ml")) return n(v) + " ml = " + n(v / 29.5735) + " fl oz";
        if (unit.matches("gallons?|gal")) return n(v) + " gal = " + n(v * 3.78541) + " L";
        if (unit.matches("c|celsius|degrees?")) return n(v) + " °C = " + n(v * 9 / 5 + 32) + " °F";
        if (unit.matches("f|fahrenheit")) return n(v) + " °F = " + n((v - 32) * 5 / 9) + " °C";
        return null;
    }

    private static volatile JSONObject ratesKept;
    private static volatile long ratesAt;

    /** Today's rates against USD from a free service (no key needed). Three services are tried; the answer is kept for 1 hour. */
    static synchronized JSONObject usdRates() throws Exception {
        if (ratesKept != null && System.currentTimeMillis() - ratesAt < 3600 * 1000L) return ratesKept;
        String[] urls = {"https://open.er-api.com/v6/latest/USD", "https://api.frankfurter.app/latest?from=USD",
                "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.min.json"};
        for (String u : urls) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(10000);
                if (c.getResponseCode() != 200) continue;
                JSONObject j = new JSONObject(read(c.getInputStream()));
                JSONObject r = j.optJSONObject("rates");
                if (r == null) {
                    JSONObject low = j.optJSONObject("usd");        // third service: {"usd": {"inr": 88.1, ...}}
                    if (low == null) continue;
                    r = new JSONObject();
                    for (String k : CURRENCIES) r.put(k, low.optDouble(k.toLowerCase(Locale.ROOT), 0));
                }
                if (r.optDouble("INR", 0) <= 0) continue;
                ratesKept = r;
                ratesAt = System.currentTimeMillis();
                return r;
            } catch (UserMessage um) { throw um;
            } catch (Exception next) { /* try the next service */ }
        }
        if (ratesKept != null) return ratesKept;             // older rates are better than nothing
        throw new UserMessage("Couldn't get today's currency rates. Check the internet and try again");
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int k;
        while ((k = in.read(buf)) > 0) b.write(buf, 0, k);
        in.close();
        return b.toString("UTF-8");
    }

    static final class ModelMissing extends Exception {}
    static final class Busy extends Exception { final int code; Busy(int c) { code = c; } }

    /** An error with a message meant for the person, not a crash. */
    static final class UserMessage extends Exception {
        UserMessage(String m) { super(m); }
    }
}
