package com.tara.bolchi;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

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

    static volatile String lastShape = "";

    private static JSONObject firstOf(JSONObject im, String... names) {
        for (String n : names) {
            JSONObject o = im.optJSONObject(n);
            if (o != null && !o.optString("url").isEmpty()) return o;
        }
        return null;
    }

    private static String urlOf(JSONObject im, String... names) {
        JSONObject o = firstOf(im, names);
        return o == null ? "" : o.optString("url");
    }

    private static final LruCache<String, Bitmap> PICS = new LruCache<>(60);
    private static final LruCache<String, List<Item>> FOUND = new LruCache<>(40);

    static List<Item> search(String key, String word) throws Exception {
        List<Item> cached = FOUND.get(word);
        if (cached != null) return cached;
        URL u = new URL("https://api.giphy.com/v1/gifs/search?api_key=" + URLEncoder.encode(key.trim(), "UTF-8")
                + "&q=" + URLEncoder.encode(word, "UTF-8") + "&limit=6&rating=g&lang=en");
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
        lastShape = "";
        for (int i = 0; data != null && i < data.length(); i++) {
            JSONObject im = data.getJSONObject(i).optJSONObject("images");
            if (im == null) continue;
            if (lastShape.isEmpty()) lastShape = String.valueOf(im.names());
            // the small still picture for the row, and the GIF to send: take the first size that exists
            String thumb = urlOf(im, "fixed_height_small_still", "fixed_height_still", "fixed_width_small_still", "fixed_width_still", "downsized_still", "preview_gif");
            JSONObject gf = firstOf(im, "fixed_height", "fixed_width", "downsized", "downsized_medium", "original");
            if (thumb.isEmpty() || gf == null) continue;
            Item it = new Item();
            it.thumb = thumb;
            it.gif = gf.optString("url");
            // a very big GIF is slow to send: use the smaller one
            String small = urlOf(im, "fixed_height_small", "fixed_width_small");
            if (!small.isEmpty() && gf.optLong("size", 0) > 1500000L) it.gif = small;
            if (!it.gif.isEmpty()) out.add(it);
        }
        if (!out.isEmpty()) FOUND.put(word, out);
        return out;
    }

    static Bitmap bitmap(String url) {
        Bitmap hit = PICS.get(url);
        if (hit != null) return hit;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(10000);
            InputStream in = c.getInputStream();
            try {
                Bitmap b = BitmapFactory.decodeStream(in);
                if (b != null) PICS.put(url, b);
                return b;
            } finally { in.close(); }
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

    /** One real request, timed, with a plain answer for the Bolchi app. */
    static String test(String key) {
        if (key == null || key.trim().isEmpty()) return "Paste your Giphy key first.";
        try {
            long t0 = System.currentTimeMillis();
            List<Item> r = search(key, "love");
            long t1 = System.currentTimeMillis();
            if (r.isEmpty()) return "The key works but no usable GIFs came back (" + (t1 - t0) + " ms). Giphy sizes: " + lastShape;
            Bitmap b = bitmap(r.get(0).thumb);
            long t2 = System.currentTimeMillis();
            return "GIFs work: " + r.size() + " found.\nSearch took " + (t1 - t0) + " ms, first picture " + (t2 - t1)
                    + " ms" + (b == null ? " (picture failed)" : "") + ".\nUnder about 800 ms each feels quick.";
        } catch (Throwable t) {
            return "GIFs did NOT work: " + t.getMessage();
        }
    }

    /** Opens the connection to Giphy ahead of time so the first real search is not slow. */
    static void warmUp(String key) {
        try { search(key, "hi"); } catch (Throwable ignored) { }
    }
}
