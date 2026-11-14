package com.tara.bolchi;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** GIF search on Giphy (free key from developers.giphy.com) and the cache the keyboard sends GIFs from. */
final class Gifs {
    private Gifs() { }

    static final class Item {
        String thumb;   // small still picture shown above the keys
        String gif;     // the animated GIF that is sent
    }

    static List<Item> search(String key, String word) throws Exception {
        URL u = new URL("https://api.giphy.com/v1/gifs/search?api_key=" + URLEncoder.encode(key.trim(), "UTF-8")
                + "&q=" + URLEncoder.encode(word, "UTF-8") + "&limit=8&rating=g&lang=en");
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(10000);
        int code = c.getResponseCode();
        String body = new String(readAll(code >= 400 ? c.getErrorStream() : c.getInputStream(), 400000), "UTF-8");
        if (code == 401 || code == 403) throw new Exception("Giphy did not accept the key");
        if (code == 429) throw new Exception("Giphy limit reached for now");
        if (code >= 400) throw new Exception("Giphy problem (" + code + ")");
        JSONArray data = new JSONObject(body).optJSONArray("data");
        List<Item> out = new ArrayList<>();
        for (int i = 0; data != null && i < data.length(); i++) {
            JSONObject im = data.getJSONObject(i).optJSONObject("images");
            if (im == null) continue;
            JSONObject th = im.optJSONObject("fixed_height_small_still");
            JSONObject gf = im.optJSONObject("fixed_height");
            if (th == null || gf == null) continue;
            Item it = new Item();
            it.thumb = th.optString("url");
            it.gif = gf.optString("url");
            if (!it.thumb.isEmpty() && !it.gif.isEmpty()) out.add(it);
        }
        return out;
    }

    static Bitmap bitmap(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(10000);
            InputStream in = c.getInputStream();
            try { return BitmapFactory.decodeStream(in); } finally { in.close(); }
        } catch (Throwable t) {
            return null;
        }
    }

    /** Downloads the GIF into the keyboard's cache (kept small) and returns the file. */
    static File download(Context ctx, String url) throws Exception {
        File dir = new File(ctx.getCacheDir(), "gifs");
        if (!dir.exists()) dir.mkdirs();
        File[] old = dir.listFiles();
        if (old != null && old.length > 30) for (File f : old) f.delete();
        File f = new File(dir, "g" + Integer.toHexString(url.hashCode()) + ".gif");
        if (f.exists() && f.length() > 0) return f;
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(15000);
        byte[] bytes = readAll(c.getInputStream(), 6 * 1024 * 1024);
        FileOutputStream o = new FileOutputStream(f);
        try { o.write(bytes); } finally { o.close(); }
        return f;
    }

    private static byte[] readAll(InputStream in, int max) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            b.write(buf, 0, n);
            if (b.size() > max) break;
        }
        in.close();
        return b.toByteArray();
    }

    /** One real request, with a plain answer for the Bolchi app. */
    static String test(String key) {
        if (key == null || key.trim().isEmpty()) return "Paste your Giphy key first.";
        try {
            List<Item> r = search(key, "love");
            return r.isEmpty() ? "The key works but no GIFs came back." : "GIFs work: " + r.size() + " found for \"love\".";
        } catch (Throwable t) {
            return "GIFs did NOT work: " + t.getMessage();
        }
    }
}
