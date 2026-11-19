package com.tara.bolchi;

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

    private static boolean has(String lang) throws Exception {
        TranslateRemoteModel m = new TranslateRemoteModel.Builder(lang).build();
        return Boolean.TRUE.equals(Tasks.await(RemoteModelManager.getInstance().isModelDownloaded(m), 5, TimeUnit.SECONDS));
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

    /** Downloads the three languages. Returns a short report; needs internet this once. */
    static String downloadAll() {
        StringBuilder b = new StringBuilder();
        for (String l : LANGS) {
            try {
                if (has(l)) { b.append(l).append(" already on the phone\n"); continue; }
                Tasks.await(RemoteModelManager.getInstance().download(new TranslateRemoteModel.Builder(l).build(), new DownloadConditions.Builder().build()), 6, TimeUnit.MINUTES);
                b.append(l).append(" downloaded ✓\n");
            } catch (Throwable t) {
                b.append(l).append(" failed: ").append(t.getClass().getSimpleName()).append(t.getMessage() == null ? "" : " - " + t.getMessage()).append('\n');
            }
        }
        return b.toString();
    }
}
