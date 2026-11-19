package com.tara.bolchi;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.concurrent.TimeUnit;

/**
 * Translation done ON the phone with Google's on-device models: free, no key, no daily limit, works without internet.
 * Each language (English, Hindi, Bengali) is downloaded once (about 30 MB each) from the Bolchi app. Never call from the main thread.
 */
final class Offline {
    private Offline() {}

    static final String[] LANGS = {"en", "hi", "bn"};

    interface Progress { void say(String text); }

    static String name(String l) { return l.equals("en") ? "English" : l.equals("hi") ? "Hindi" : "Bengali"; }

    private static boolean has(String lang) throws Exception {
        TranslateRemoteModel m = new TranslateRemoteModel.Builder(lang).build();
        return Boolean.TRUE.equals(Tasks.await(RemoteModelManager.getInstance().isModelDownloaded(m), 5, TimeUnit.SECONDS));
    }

    /** "English \u2713   Hindi \u2717   Bengali \u2713": which languages are on the phone now. */
    static String status() {
        StringBuilder b = new StringBuilder("On your phone now:  ");
        for (String l : LANGS) {
            boolean ok;
            try { ok = has(l); } catch (Throwable t) { ok = false; }
            b.append(name(l)).append(ok ? " \u2713   " : " \u2717   ");
        }
        return b.toString().trim();
    }

    /** from / to: "en", "hi" or "bn". Fails fast (no waiting) when a language has not been downloaded yet. */
    static String translate(String text, String from, String to) throws Exception {
        if (!has(from) || !has(to) || (!from.equals("en") && !to.equals("en") && !has("en")))
            throw new Converter.UserMessage("not downloaded yet: Bolchi app, Download offline translator");
        Translator t = Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build());
        try {
            return Tasks.await(t.translate(text), 25, TimeUnit.SECONDS);
        } finally {
            t.close();
        }
    }

    /** Downloads the languages that are missing, telling `p` what is happening every 2 seconds. Needs internet this once. Returns the final report. */
    static String downloadAll(Progress p) {
        StringBuilder b = new StringBuilder();
        for (String l : LANGS) {
            try {
                if (has(l)) { b.append(name(l)).append(" is already on the phone \u2713\n"); p.say(b.toString()); continue; }
                Task<Void> t = RemoteModelManager.getInstance().download(new TranslateRemoteModel.Builder(l).build(), new DownloadConditions.Builder().build());
                long t0 = System.currentTimeMillis();
                Throwable err = null;
                while (System.currentTimeMillis() - t0 < 15 * 60 * 1000L) {
                    if (t.isComplete()) { if (!t.isSuccessful()) err = t.getException(); break; }
                    p.say(b + "Downloading " + name(l) + "\u2026 " + (System.currentTimeMillis() - t0) / 1000 + " s (about 30 MB)");
                    Thread.sleep(2000);
                }
                if (has(l)) b.append(name(l)).append(" downloaded \u2713\n");
                else b.append(name(l)).append(" NOT downloaded: ").append(err == null ? "it took too long, tap the button again" : err.getClass().getSimpleName() + (err.getMessage() == null ? "" : " - " + err.getMessage())).append('\n');
            } catch (Throwable t) {
                b.append(name(l)).append(" failed: ").append(t.getClass().getSimpleName()).append(t.getMessage() == null ? "" : " - " + t.getMessage()).append('\n');
            }
            p.say(b.toString());
        }
        return b.toString();
    }
}
