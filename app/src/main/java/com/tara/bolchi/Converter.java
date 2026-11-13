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
        String q = java.net.URLEncoder.encode(text, "UTF-8");
        String[] hosts = {"https://translate.google.com", "https://translate.googleapis.com"};
        Exception last = null;
        for (String h : hosts) {
            try {
                stage = "asking Google Translate";
                HttpURLConnection c = (HttpURLConnection) new URL(h + "/translate_a/single?client=gtx&dt=t&tl=en&sl="
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

    // ---------- AI: translate / Bangla script ----------
    static String translate(String text, String key) throws Exception {
        return gemini(key,
                "Translate the user's message into natural, simple English. The message may be Bengali or Hindi "
                        + "written in English letters (Banglish or Hinglish), Bengali or Hindi script, or mixed with English. "
                        + "Keep names, numbers and emojis as they are. Reply with only the translation, nothing else.",
                text);
    }

    /** Tidies dictated text: keeps the speaker's corrected version, drops fillers and repeats. */
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
                        + "version. Remove accidental repeats. Add basic punctuation and capital letters. Do not add or remove "
                        + "meaning and do not answer the text. Reply with only the cleaned text.",
                text);
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
            "(?i)(₹|rs\\.?|\\$|€|£)?\\s*(\\d+(?:[.,]\\d+)?)\\s*(kgs?|kilos?|kilograms?|lbs?|pounds?|grams?|gms?|g|oz|ounces?|"
                    + "km|kilometers?|kilometres?|miles?|mi|meters?|metres?|m|ft|feet|foot|cm|inch(?:es)?|in|"
                    + "litres?|liters?|l|ml|gallons?|gal|°?c|°?f|celsius|fahrenheit|"
                    + "inr|rupees?|usd|dollars?|eur|euros?|gbp|aed|dirhams?)?\\b");

    /** Returns one line per amount found, e.g. "5 kg = 11.02 lb". needsRates is filled if currency was found. */
    static String units(String text, JSONObject usdRates) {
        Matcher m = AMOUNT.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String sym = m.group(1) == null ? "" : m.group(1).toLowerCase(Locale.ROOT);
            String unit = m.group(3) == null ? "" : m.group(3).toLowerCase(Locale.ROOT).replace("°", "");
            if (sym.isEmpty() && unit.isEmpty()) continue;
            double v;
            try { v = Double.parseDouble(m.group(2).replace(",", ".")); } catch (Exception e) { continue; }
            String line = convertOne(v, sym, unit, usdRates);
            if (line != null) {
                if (out.length() > 0) out.append(" · ");
                out.append(line);
            }
        }
        return out.toString();
    }

    static boolean hasCurrency(String text) {
        Matcher m = AMOUNT.matcher(text);
        while (m.find()) {
            if (currencyOf(m.group(1) == null ? "" : m.group(1).toLowerCase(Locale.ROOT),
                    m.group(3) == null ? "" : m.group(3).toLowerCase(Locale.ROOT)) != null) return true;
        }
        return false;
    }

    private static String currencyOf(String sym, String unit) {
        if (sym.equals("₹") || sym.startsWith("rs") || unit.equals("inr") || unit.startsWith("rupee")) return "INR";
        if (sym.equals("$") || unit.equals("usd") || unit.startsWith("dollar")) return "USD";
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
        if (unit.matches("km|kilometers?|kilometres?")) return n(v) + " km = " + n(v / 1.60934) + " miles";
        if (unit.matches("miles?|mi")) return n(v) + " miles = " + n(v * 1.60934) + " km";
        if (unit.matches("meters?|metres?|m")) return n(v) + " m = " + n(v * 3.28084) + " ft";
        if (unit.matches("ft|feet|foot")) return n(v) + " ft = " + n(v / 3.28084) + " m";
        if (unit.equals("cm")) return n(v) + " cm = " + n(v / 2.54) + " in";
        if (unit.matches("inch(?:es)?|in")) return n(v) + " in = " + n(v * 2.54) + " cm";
        if (unit.matches("litres?|liters?|l")) return n(v) + " L = " + n(v / 3.78541) + " gal";
        if (unit.equals("ml")) return n(v) + " ml = " + n(v / 29.5735) + " fl oz";
        if (unit.matches("gallons?|gal")) return n(v) + " gal = " + n(v * 3.78541) + " L";
        if (unit.matches("c|celsius")) return n(v) + " °C = " + n(v * 9 / 5 + 32) + " °F";
        if (unit.matches("f|fahrenheit")) return n(v) + " °F = " + n((v - 32) * 5 / 9) + " °C";
        return null;
    }

    /** Today's rates against USD from a free service (no key needed). */
    static JSONObject usdRates() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://open.er-api.com/v6/latest/USD").openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        int code = c.getResponseCode();
        if (code != 200) throw new UserMessage("Couldn't get today's currency rates");
        JSONObject j = new JSONObject(read(c.getInputStream()));
        JSONObject r = j.optJSONObject("rates");
        if (r == null) throw new UserMessage("Couldn't get today's currency rates");
        return r;
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
