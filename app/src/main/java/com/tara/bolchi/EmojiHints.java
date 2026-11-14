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
        synchronized (MAP) { MAP.clear(); MAP.putAll(m); }
        ready = true;
    }

    /** Emojis for this exact word, or null. */
    public static String[] get(String word) {
        if (!ready || word == null || word.length() < 3) return null;
        return MAP.get(word.toLowerCase(Locale.ROOT));
    }
}
