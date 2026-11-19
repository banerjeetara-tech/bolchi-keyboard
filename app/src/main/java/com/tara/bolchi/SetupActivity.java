package com.tara.bolchi;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** One-time setup screen: allow the mic, turn the keyboard on, switch to it, try it. */
public class SetupActivity extends Activity {

    private static final int BG = Color.parseColor("#F6ECE0");
    private static final int INK = Color.parseColor("#2B211A");
    private static final int MUTED = Color.parseColor("#7A6A5C");
    private static final int ACCENT = Color.parseColor("#B9502B");
    private static final int OK = Color.parseColor("#2F6B4F");
    private static final int CARD = Color.parseColor("#FFFAF2");
    private static final int LINE = Color.parseColor("#E6D7C3");

    private TextView micBtn;
    private TextView smsBtn;
    private TextView smsInfo;
    private TextView contactsBtn;
    private TextView enableBtn;
    private LinearLayout problemBox;
    private TextView problemText;

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable box(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private TextView button(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(17);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackground(box(ACCENT, 0, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(4), 0, dp(14));
        t.setLayoutParams(lp);
        return t;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(28), dp(20), dp(28));
        scroll.addView(col);

        col.addView(text("Bolchi Keyboard", 28, INK));
        col.addView(text("One keyboard for everything: type English on the keys, or tap Speak and talk in Bangla (types Banglish), Hindi (types Hinglish) or English.", 16, MUTED));

        col.addView(text("1. Allow the microphone", 17, INK));
        micBtn = button("Allow microphone");
        micBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            }
        });
        col.addView(micBtn);

        col.addView(text("Codes from SMS (OTP)", 17, INK));
        col.addView(text("Allow this and Bolchi shows the one-time code above the keys, so you can tap it. Bolchi keeps only the code, not your messages.", 14, MUTED));
        smsBtn = button("Allow reading codes from SMS");
        smsBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, 2);
            }
        });
        col.addView(smsBtn);
        smsInfo = text("", 13, MUTED);
        col.addView(smsInfo);

        col.addView(text("Names from your phone book", 17, INK));
        col.addView(text("Allow this and the keyboard offers the names in your contacts as you type their first letters. The names stay on your phone and are not sent anywhere.", 14, MUTED));
        contactsBtn = button("Allow phone book names");
        contactsBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 3);
            }
        });
        col.addView(contactsBtn);

        col.addView(text("2. Turn on Bolchi in your keyboard list", 17, INK));
        enableBtn = button("Open keyboard settings");
        enableBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)); }
                catch (Exception e) { /* settings screen missing on this phone */ }
            }
        });
        col.addView(enableBtn);

        col.addView(text("3. Switch to Bolchi", 17, INK));
        TextView pick = button("Choose keyboard");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }
        });
        col.addView(pick);

        col.addView(text("4. Try it here", 17, INK));
        EditText try1 = new EditText(this);
        try1.setHint("Tap here, then tap the mic and speak");
        try1.setTextColor(INK);
        try1.setTextSize(18);
        try1.setMinLines(3);
        try1.setGravity(Gravity.TOP);
        try1.setBackground(box(CARD, LINE, 12));
        try1.setPadding(dp(12), dp(12), dp(12), dp(12));
        col.addView(try1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        col.addView(text("Pick Bangla, Hindi or English on the bottom bar, then tap Speak. It keeps listening until you tap Stop. "
                + "The palette key changes colours, the \u21C4 key opens the converter, the smiley key opens emojis, and holding the space bar switches keyboards.", 14, MUTED));
        col.addView(text("If it says voice is not set up: open the Google app, go to Settings, Voice, "
                + "Languages, and add Bengali (India) and Hindi (India). Voice typing needs internet.", 14, MUTED));

        // AI key for the converter
        col.addView(text("5. Converter key (for Translate and Bangla script)", 17, INK));
        col.addView(text("Get a free key at aistudio.google.com/apikey, copy it, paste it here and tap Save.", 14, MUTED));
        final EditText keyBox = new EditText(this);
        keyBox.setHint("Paste your Google AI Studio key");
        keyBox.setSingleLine(true);
        keyBox.setTextColor(INK);
        keyBox.setTextSize(15);
        keyBox.setBackground(box(CARD, LINE, 12));
        keyBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        keyBox.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        final SharedPreferences prefs = getSharedPreferences("bolchi", Context.MODE_PRIVATE);
        keyBox.setText(prefs.getString("aikey", ""));
        col.addView(keyBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView saveKey = button("Save key");
        saveKey.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.edit().putString("aikey", keyBox.getText().toString().trim()).apply();
                Toast.makeText(SetupActivity.this, "Key saved", Toast.LENGTH_SHORT).show();
            }
        });
        col.addView(saveKey);

        // free backup AI keys: used automatically when Google's AI is busy or cannot be reached
        col.addView(text("Backup AI keys (free, optional)", 17, INK));
        col.addView(text("If Google's AI is busy, Bolchi tries these, fastest first. Groq: console.groq.com \u2192 API Keys. Cerebras: cloud.cerebras.ai \u2192 API Keys. "
                + "Mistral: console.mistral.ai \u2192 API Keys. OpenRouter: openrouter.ai \u2192 Keys. Any one is enough; paste, then tap Save. "
                + "Without any key Bolchi still tries free services that need none.", 14, MUTED));
        final String[][] links = {{"Open the Groq key page", "https://console.groq.com/keys"}, {"Open the Cerebras key page", "https://cloud.cerebras.ai/platform"},
                {"Open the Mistral key page", "https://console.mistral.ai/api-keys"}, {"Open the OpenRouter key page", "https://openrouter.ai/keys"},};
        for (final String[] lk : links) {
            TextView lb = button(lk[0]);
            lb.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(lk[1]))); }
                    catch (Exception e) { Toast.makeText(SetupActivity.this, lk[1], Toast.LENGTH_LONG).show(); }
                }
            });
            col.addView(lb);
        }
        final String[] hints = {"Groq key (starts with gsk_)", "Cerebras key (starts with csk-)", "Mistral key", "OpenRouter key (starts with sk-or-)",};
        final String[] prefNames = {"groqKey", "cerebrasKey", "mistralKey", "openrouterKey"};
        final EditText[] boxes = new EditText[prefNames.length];
        for (int bi = 0; bi < prefNames.length; bi++) {
            EditText b = new EditText(this);
            boxes[bi] = b;
            b.setHint(hints[bi]);
            b.setSingleLine(true);
            b.setTextColor(INK);
            b.setTextSize(15);
            b.setBackground(box(CARD, LINE, 12));
            b.setPadding(dp(12), dp(12), dp(12), dp(12));
            b.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            b.setText(prefs.getString(prefNames[bi], ""));
            col.addView(b, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        final Runnable saveBackups = new Runnable() { @Override public void run() {
            SharedPreferences.Editor ed = prefs.edit();
            for (int i = 0; i < prefNames.length; i++) ed.putString(prefNames[i], boxes[i].getText().toString().trim());
            ed.apply();
            Converter.useExtraKeys(prefs);
        } };
        TextView saveExtra = button("Save backup keys");
        saveExtra.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                saveBackups.run();
                Toast.makeText(SetupActivity.this, "Backup keys saved", Toast.LENGTH_SHORT).show();
            }
        });
        col.addView(saveExtra);

        // the translator that lives on the phone: free, no limit, works with no internet once downloaded
        col.addView(text("Translator on your phone (free, never runs out)", 17, INK));
        col.addView(text("Download once (about 90 MB, needs internet this one time). After that Translate to English / Hindi / Bengali also works with no internet at all, "
                + "and it is the last helper if every online service fails.", 14, MUTED));
        offView = text("Checking\u2026", 13, MUTED);
        final TextView offOut = offView;
        TextView offBtn = button("Download offline translator");
        offBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (downloadingOffline) return;
                downloadingOffline = true;
                offOut.setText("Starting\u2026 keep this screen open and stay online.");
                new Thread(new Runnable() { @Override public void run() {
                    final String r = Offline.downloadAll(new Offline.Progress() {
                        @Override public void say(final String text) { runOnUiThread(new Runnable() { @Override public void run() { offOut.setText(text); } }); }
                    });
                    downloadingOffline = false;
                    final String st = Offline.status();
                    runOnUiThread(new Runnable() { @Override public void run() { offOut.setText(r + "\n" + st); } });
                } }).start();
            }
        });
        col.addView(offBtn);
        col.addView(offOut);

        final TextView allOut = text("", 13, MUTED);
        TextView allBtn = button("Test all connections");
        allBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.edit().putString("aikey", keyBox.getText().toString().trim()).apply();
                saveBackups.run();
                allOut.setText("Testing, wait up to 40 seconds\u2026");
                final String k = keyBox.getText().toString().trim();
                new Thread(new Runnable() { @Override public void run() {
                    final String r = Converter.testAll(k);
                    runOnUiThread(new Runnable() { @Override public void run() { allOut.setText(r); } });
                } }).start();
            }
        });
        col.addView(allBtn);
        col.addView(allOut);

        final TextView cmpOut = text("", 13, MUTED);
        TextView cmpBtn = button("Compare translators (quality and speed)");
        cmpBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                saveBackups.run();
                prefs.edit().putString("aikey", keyBox.getText().toString().trim()).apply();
                cmpOut.setText("Comparing, wait up to a minute\u2026");
                final String k = keyBox.getText().toString().trim();
                new Thread(new Runnable() { @Override public void run() {
                    final String r = Converter.compareTranslators(k);
                    runOnUiThread(new Runnable() { @Override public void run() { cmpOut.setText(r); } });
                } }).start();
            }
        });
        col.addView(cmpBtn);
        col.addView(cmpOut);

        // test the AI key, and show which version is installed
        final TextView testOut = text(diagText(), 14, MUTED);
        diagView = testOut;
        TextView testBtn = button("Test AI key");
        testBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String k = keyBox.getText().toString().trim();
                prefs.edit().putString("aikey", k).apply();
                Converter.useExtraKeys(prefs);
                testOut.setText("Testing, wait up to 30 seconds\u2026");
                new Thread(new Runnable() { @Override public void run() {
                    final String r = Converter.test(k);
                    runOnUiThread(new Runnable() { @Override public void run() {
                        testOut.setText("Bolchi " + BuildConfigInfo.version(SetupActivity.this) + "\n" + r);
                    } });
                } }).start();
            }
        });
        col.addView(testBtn);

        // trial: let the phone's speech engine switch between Bengali / Hindi and English inside one sentence (Android 14+, not every engine can)
        final TextView mixBtn = button(prefs.getBoolean("langSwitch", false) ? "Mixed Bengali + English listening: ON (tap to turn off)"
                : "Mixed Bengali + English listening: OFF (tap to try)");
        mixBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean on = !prefs.getBoolean("langSwitch", false);
                prefs.edit().putBoolean("langSwitch", on).apply();
                mixBtn.setText(on ? "Mixed Bengali + English listening: ON (tap to turn off)" : "Mixed Bengali + English listening: OFF (tap to try)");
                Toast.makeText(SetupActivity.this, on ? "On. If Bengali gets worse, turn it off here" : "Off", Toast.LENGTH_LONG).show();
            }
        });
        col.addView(mixBtn);

        // what speaking writes on the Bangla / Hindi tabs: Banglish / Hinglish (English letters) or Bengali / Hindi letters
        final TextView bnOut = button("");
        final TextView hiOut = button("");
        final Runnable paintOut = new Runnable() { @Override public void run() {
            bnOut.setText("Speak on Bangla writes: " + (prefs.getBoolean("bnScript", false) ? "\u09ac\u09be\u0982\u09b2\u09be letters" : "Banglish (English letters)") + "  (tap to change)");
            hiOut.setText("Speak on Hindi writes: " + (prefs.getBoolean("hiScript", false) ? "\u0939\u093f\u0902\u0926\u0940 letters" : "Hinglish (English letters)") + "  (tap to change)");
        } };
        paintOut.run();
        bnOut.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { prefs.edit().putBoolean("bnScript", !prefs.getBoolean("bnScript", false)).apply(); paintOut.run(); }
        });
        hiOut.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { prefs.edit().putBoolean("hiScript", !prefs.getBoolean("hiScript", false)).apply(); paintOut.run(); }
        });
        col.addView(bnOut);
        col.addView(hiOut);

        // where a tap lands: the thumb presses lower than it aims, so taps are moved up a little (the keys keep their size). Auto = measured from her own taps.
        final String[] touchNames = {"Auto (learns from your taps)", "Normal", "A little higher", "Higher", "Much higher"};
        final int[] touchDp = {-1, 0, 5, 8, 11};
        int touchCur = 0;
        for (int ti = 0; ti < touchDp.length; ti++) if (prefs.getInt("touchUp", -1) == touchDp[ti]) touchCur = ti;
        final int[] touchPick = {touchCur};
        final TextView touchBtn = button("Key touch point: " + touchNames[touchCur] + " (tap to change)");
        float tb = prefs.getFloat("touchBias", 0f);
        int tn = prefs.getInt("touchN", 0);
        col.addView(text(tn < 10 ? "Taps measured so far: " + tn + ". Type a few messages, then look here."
                : "Measured on " + tn + " taps: they land on average " + String.format(java.util.Locale.US, "%.1f", Math.abs(tb)) + " dp "
                + (tb > 0.5f ? "LOWER" : tb < -0.5f ? "HIGHER" : "right on") + (Math.abs(tb) > 0.5f ? " than the middle of the key." : " the middle of the key."), 14, MUTED));
        touchBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                touchPick[0] = (touchPick[0] + 1) % touchDp.length;
                prefs.edit().putInt("touchUp", touchDp[touchPick[0]]).apply();
                touchBtn.setText("Key touch point: " + touchNames[touchPick[0]] + " (tap to change)");
                Toast.makeText(SetupActivity.this, "Open the keyboard and type a few words. If the row BELOW still comes, choose Higher", Toast.LENGTH_LONG).show();
            }
        });
        col.addView(touchBtn);
        col.addView(testOut);

        // GIF suggestions (Giphy free key)
        col.addView(text("GIFs (Giphy key)", 17, INK));
        col.addView(text("Get a free key at developers.giphy.com (Create an App, choose API), copy it and paste it here. Then GIFs appear above the keys next to the emoji when you type a word like love or happy.", 14, MUTED));
        final EditText gifKeyBox = new EditText(this);
        gifKeyBox.setHint("Paste your Giphy key");
        gifKeyBox.setSingleLine(true);
        gifKeyBox.setTextColor(INK);
        gifKeyBox.setTextSize(15);
        gifKeyBox.setBackground(box(CARD, LINE, 12));
        gifKeyBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        gifKeyBox.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        gifKeyBox.setText(prefs.getString("giphyKey", ""));
        col.addView(gifKeyBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        final TextView gifOut = text("", 14, MUTED);
        TextView gifBtn = button("Save and test GIF key");
        gifBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String k = gifKeyBox.getText().toString().trim();
                prefs.edit().putString("giphyKey", k).apply();
                gifOut.setText("Testing\u2026");
                new Thread(new Runnable() { @Override public void run() {
                    final String r = Gifs.test(k);
                    runOnUiThread(new Runnable() { @Override public void run() { gifOut.setText(r); } });
                } }).start();
            }
        });
        col.addView(gifBtn);
        col.addView(gifOut);

        // her own phone numbers, offered when she types phone / mobile / number
        col.addView(text("My phone numbers", 17, INK));
        col.addView(text("One on each line. When you type the word phone, mobile or number, the keyboard offers these numbers to tap. (If your own number is in the Me card of your phone book it is found by itself.)", 14, MUTED));
        final EditText numBox = new EditText(this);
        numBox.setHint("+91 9XXXXXXXXX");
        numBox.setMinLines(2);
        numBox.setGravity(android.view.Gravity.TOP);
        numBox.setTextColor(INK);
        numBox.setTextSize(15);
        numBox.setBackground(box(CARD, LINE, 12));
        numBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        numBox.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        numBox.setText(prefs.getString("myNumbers", ""));
        col.addView(numBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView saveNum = button("Save my numbers");
        saveNum.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.edit().putString("myNumbers", numBox.getText().toString().trim()).apply();
                Toast.makeText(SetupActivity.this, "Saved", Toast.LENGTH_SHORT).show();
            }
        });
        col.addView(saveNum);

        // personal words: the keyboard offers them when their first letters are typed
        col.addView(text("6. My words (English and Bengali, names, email, address)", 17, INK));
        col.addView(text("One on each line, as many as you like. English words, Bengali or Hindi words (in English letters or in Bengali script, for example \u0986\u09AE\u09BF) are all fine. Type the first letters and the keyboard offers it; when you speak, it writes the word your way. You can also write letters = word, for example: ami = \u0986\u09AE\u09BF", 14, MUTED));
        final EditText myBox = new EditText(this);
        myBox.setHint("yourname@gmail.com\nbeauty\n\u0986\u09AE\u09BF");
        myBox.setMinLines(3);
        myBox.setGravity(android.view.Gravity.TOP);
        myBox.setTextColor(INK);
        myBox.setTextSize(15);
        myBox.setBackground(box(CARD, LINE, 12));
        myBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        myBox.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        myBox.setText(prefs.getString("myWords", ""));
        col.addView(myBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView saveMy = button("Save my words");
        saveMy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.edit().putString("myWords", myBox.getText().toString().trim()).apply();
                Toast.makeText(SetupActivity.this, "Saved", Toast.LENGTH_SHORT).show();
            }
        });
        col.addView(saveMy);

        // problem report, shown only when the keyboard closed unexpectedly
        problemBox = new LinearLayout(this);
        problemBox.setOrientation(LinearLayout.VERTICAL);
        problemBox.addView(text("Problem report", 17, INK));
        problemBox.addView(text("The keyboard closed unexpectedly. Tap Copy and paste this to Claude so it can be fixed.", 14, MUTED));
        problemText = text("", 12, MUTED);
        problemText.setMaxLines(8);
        problemBox.addView(problemText);
        TextView copy = button("Copy problem report");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Bolchi problem", prefs.getString("problem", "")));
                Toast.makeText(SetupActivity.this, "Copied", Toast.LENGTH_SHORT).show();
            }
        });
        problemBox.addView(copy);
        TextView clear = button("Clear report");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.edit().remove("problem").apply();
                refresh();
            }
        });
        problemBox.addView(clear);
        col.addView(problemBox);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refresh();
    }

    private TextView offView;
    private volatile boolean downloadingOffline;
    private TextView diagView;

    /** The "Installed version" block: version, Auto-correct, why the last word was not corrected, and what the next-word guesses did. Always shows the next-word line. */
    private String diagText() {
        SharedPreferences p = getSharedPreferences("bolchi", Context.MODE_PRIVATE);
        String next = p.getString("nextWhy", "");
        return "Installed version: Bolchi " + BuildConfigInfo.version(this)
                + "\nAuto-correct: " + (p.getBoolean("autoFix", true) ? "ON" : "OFF (hold the Auto key on the keyboard to turn it on)")
                + (p.getString("fixWhy", "").isEmpty() ? "" : "\nLast word not corrected: " + p.getString("fixWhy", ""))
                + (p.getString("learnLog", "").isEmpty() ? "" : "\nLearned lately (words before > word):\n" + p.getString("learnLog", ""))
                + (p.getString("voiceWhy", "").isEmpty() ? "" : "\nLast voice error: " + p.getString("voiceWhy", ""))
                + "\nNext-word guesses: " + (next.isEmpty()
                    ? "nothing noted yet. Type a few words with spaces in any app (English keys), then open this app again. If this stays empty, the keyboard installed is older than version 120."
                    : next);
    }

    private void refresh() {
        if (diagView != null && diagView.getText().toString().startsWith("Installed version")) diagView.setText(diagText());
        if (offView != null && !downloadingOffline) {
            new Thread(new Runnable() { @Override public void run() {
                final String st = Offline.status();
                runOnUiThread(new Runnable() { @Override public void run() { if (offView != null && !downloadingOffline) offView.setText(st); } });
            } }).start();
        }
        boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        micBtn.setText(mic ? "✓ Microphone allowed" : "Allow microphone");
        micBtn.setBackground(box(mic ? OK : ACCENT, 0, 12));
        boolean sms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        smsBtn.setText(sms ? "\u2713 SMS codes allowed" : "Allow reading codes from SMS");
        smsBtn.setBackground(box(sms ? OK : ACCENT, 0, 12));
        boolean cts = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        contactsBtn.setText(cts ? "\u2713 Phone book names allowed" : "Allow phone book names");
        contactsBtn.setBackground(box(cts ? OK : ACCENT, 0, 12));
        SharedPreferences sp = getSharedPreferences("bolchi", Context.MODE_PRIVATE);
        long seen = sp.getLong("smsAnyAt", 0);
        smsInfo.setText(!sms ? "" : seen == 0 ? "No text message seen by Bolchi yet."
                : "Last text seen by Bolchi: " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date(seen))
                + (sp.getBoolean("smsAnyHadCode", false) ? " (a code was found)" : " (no code found in it)"));

        boolean on = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            List<InputMethodInfo> list = imm.getEnabledInputMethodList();
            if (list != null) {
                for (InputMethodInfo info : list) {
                    if (getPackageName().equals(info.getPackageName())) { on = true; break; }
                }
            }
        }
        enableBtn.setText(on ? "✓ Bolchi is turned on" : "Open keyboard settings");
        enableBtn.setBackground(box(on ? OK : ACCENT, 0, 12));

        String problem = getSharedPreferences("bolchi", Context.MODE_PRIVATE).getString("problem", "");
        problemBox.setVisibility(problem.isEmpty() ? View.GONE : View.VISIBLE);
        problemText.setText(problem);
    }
}
