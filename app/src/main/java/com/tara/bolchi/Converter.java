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

    /** Google Translate from `lang` (or "auto") into the language `to` ("en", "hi", "bn"). */
    static String googleTranslateTo(String text, String lang, String to) throws Exception {
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
                if (c.getResponseCode() != 200) { last = new UserMessage("Google Translate didn't answer"); continue; }
                JSONArray j = new JSONArray(read(c.getInputStream()));
                JSONArray parts = j.getJSONArray(0);
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < parts.length(); i++) {
                    JSONArray p = parts.optJSONArray(i);
                    if (p != null && !p.isNull(0)) b.append(p.getString(0));
                }
                if (b.length() > 0) return b.toString().trim();
            } catch (Exception e) { last = e; }
        }
        throw last != null ? last : new UserMessage("Google Translate didn't answer");
    }

    /** Translate without a key; falls back to the AI only if a key is saved and Google Translate fails. */
    static String translateFree(String text, int voiceMode, String key) throws Exception {
        try {
            if (hasScript(text)) return googleTranslate(text, "auto");
            if (voiceMode == 2) return googleTranslate(text, "auto");
            boolean hindi = voiceMode == 1;
            String script = romanToScript(text, hindi);
            return googleTranslate(script, hindi ? "hi" : "bn");
        } catch (Exception e) {
            if (key != null && !key.trim().isEmpty()) return translate(text, key);
            throw e;
        }
    }

    /** Translate into Hindi (Devanagari). Google Translate first, the AI only if that fails and a key is saved. */
    static String translateToHindi(String text, String key) throws Exception {
        try {
            return googleTranslateTo(text, "auto", "hi");
        } catch (Exception e) {
            if (key == null || key.trim().isEmpty()) throw e;
            return gemini(key,
                    "Translate the user's message into natural, simple Hindi written in Devanagari script. The message may be English, "
                            + "Bengali, or Bengali or Hindi in English letters. Keep names, numbers, emojis and links as they are. "
                            + "Reply with only the translation, nothing else.", text);
        }
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

    private static String gemini(String key, String instruction, String text) throws Exception {
        try {
            return gemini(MODEL, key, instruction, text);
        } catch (ModelMissing m) {
            return gemini(MODEL_BACKUP, key, instruction, text);
        }
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
        if (code == 429) throw new UserMessage("Free AI limit reached for now. Try again in a minute");
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
            "(?i)(₹|rs\\.?|inr|usd|\\$|€|£)?\\s*(\\d+(?:,\\d+)*(?:\\.\\d+)?)\\s*(k(?![a-z])|lakhs?|lacs?|crores?|cr(?![a-z]))?\\s*"
                    + "(kgs?|kilos?|kilograms?|lbs?|pounds?|grams?|gms?|g|oz|ounces?|"
                    + "kms?|kilometers?|kilometres?|miles?|mi|meters?|metres?|m|ft|feet|foot|cms?|inch(?:es)?|"
                    + "litres?|liters?|l|ml|gallons?|gal|°?c|°?f|celsius|fahrenheit|degrees?|"
                    + "inr|rupees?|rs\\.?|/-|usd|dollars?|eur|euros?|gbp|aed|dirhams?)?(?![A-Za-z])");

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

    /** The typed amount in other money, with signs: rupees give $ € £; anything else gives ₹ first. Null when it is not money or a rate is missing. */
    static String[] money(String token, JSONObject rates) {
        Matcher m = AMOUNT.matcher(FontStyles.plain(token));
        if (rates == null || !m.matches()) return null;
        String cur = currencyOf(g(m, 1), g(m, 4));
        if (cur == null) cur = "INR";
        double v;
        try { v = amount(m.group(2), g(m, 3)); } catch (Exception e) { return null; }
        double from = cur.equals("USD") ? 1 : rates.optDouble(cur, 0);
        if (from <= 0) return null;
        String[] to = cur.equals("INR") ? new String[]{"USD", "EUR", "GBP"} : cur.equals("USD") ? new String[]{"INR", "EUR", "GBP"}
                : new String[]{"INR", "USD", cur.equals("EUR") ? "GBP" : "EUR"};
        String[] out = new String[3];
        for (int i = 0; i < 3; i++) {
            double t = to[i].equals("USD") ? 1 : rates.optDouble(to[i], 0);
            out[i] = t <= 0 ? "" : sign(to[i]) + n(v / from * t);
        }
        return out[0].isEmpty() ? null : out;
    }

    /** The rates if they are already here (no waiting); null when they still have to be fetched. */
    static JSONObject ratesNow() {
        return ratesKept != null && System.currentTimeMillis() - ratesAt < 6 * 3600 * 1000L ? ratesKept : null;
    }

    private static String currencyOf(String sym, String unit) {
        if (unit.equals("usd") || unit.startsWith("dollar")) return "USD";
        if (unit.equals("eur") || unit.startsWith("euro")) return "EUR";
        if (unit.equals("gbp")) return "GBP";
        if (unit.equals("aed") || unit.startsWith("dirham")) return "AED";
        if (sym.equals("₹") || sym.startsWith("rs") || sym.equals("inr") || unit.equals("inr") || unit.startsWith("rupee") || unit.startsWith("rs") || unit.equals("/-")) return "INR";
        if (sym.equals("$") || sym.equals("usd")) return "USD";
        if (sym.equals("€") || unit.equals("eur") || unit.startsWith("euro")) return "EUR";
        if (sym.equals("£") || unit.equals("gbp")) return "GBP";
        if (unit.equals("aed") || unit.startsWith("dirham")) return "AED";
        return null;
    }

    private static String n(double d) {
        if (Math.abs(d - Math.rint(d)) < 1e-9) return String.format(Locale.ROOT, "%,.0f", d);
        return String.format(Locale.ROOT, "%,.2f", d);
    }

    private static String sign(String cur) {
        switch (cur) {
            case "INR": return "₹"; case "USD": return "$"; case "EUR": return "€"; case "GBP": return "£";
            default: return cur + " ";
        }
    }

    private static String convertOne(double v, String sym, String unit, JSONObject rates) {
        String cur = currencyOf(sym, unit);
        if (cur != null) {
            if (rates == null) return null;
            String to = cur.equals("INR") ? "USD" : "INR";
            double from = cur.equals("USD") ? 1 : rates.optDouble(cur, 0);
            double target = to.equals("USD") ? 1 : rates.optDouble(to, 0);
            if (from <= 0 || target <= 0) return null;
            double r = v / from * target;
            return sign(cur) + n(v) + " = about " + sign(to) + n(r);
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

    /** Today's rates against USD from a free service (no key needed). Three services are tried; the answer is kept for 6 hours. */
    static synchronized JSONObject usdRates() throws Exception {
        if (ratesKept != null && System.currentTimeMillis() - ratesAt < 6 * 3600 * 1000L) return ratesKept;
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
                    for (String k : new String[]{"inr", "eur", "gbp", "aed"}) r.put(k.toUpperCase(Locale.ROOT), low.optDouble(k, 0));
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

    /** An error with a message meant for the person, not a crash. */
    static final class UserMessage extends Exception {
        UserMessage(String m) { super(m); }
    }
}
