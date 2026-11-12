package com.tara.bolchi;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Type English letters, get Bangla/Hindi script suggestions (Google Input Tools, no key needed). */
final class Phonetic {
    private Phonetic() {}

    static List<String> suggest(String word, boolean hindi) throws Exception {
        String itc = hindi ? "hi-t-i0-und" : "bn-t-i0-und";
        URL u = new URL("https://inputtools.google.com/request?ie=utf-8&oe=utf-8&num=5&cp=0&cs=1&itc=" + itc
                + "&text=" + URLEncoder.encode(word, "UTF-8"));
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(5000);
        List<String> out = new ArrayList<>();
        if (c.getResponseCode() != 200) return out;
        InputStream in = c.getInputStream();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int k;
        while ((k = in.read(buf)) > 0) b.write(buf, 0, k);
        in.close();
        JSONArray j = new JSONArray(b.toString("UTF-8"));
        if (!"SUCCESS".equals(j.optString(0))) return out;
        JSONArray list = j.getJSONArray(1).getJSONArray(0).getJSONArray(1);
        for (int i = 0; i < list.length() && out.size() < 4; i++) out.add(list.getString(i));
        return out;
    }
}
