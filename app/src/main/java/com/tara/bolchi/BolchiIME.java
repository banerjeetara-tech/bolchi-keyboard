package com.tara.bolchi;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * One keyboard for everything: type on the keys, or tap Speak and talk in
 * Bangla (types Banglish), Hindi (types Hinglish) or English.
 */
public class BolchiIME extends InputMethodService implements RecognitionListener {

    private static final int MODE_BN = 0, MODE_HI = 1, MODE_EN = 2;
    private static final String[] MODE_LABEL = {"Bangla", "Hindi", "English"};
    private static final String[] MODE_LANG = {"bn-IN", "hi-IN", "en-IN"};

    private static final int PAGE_ABC = 0, PAGE_SYM1 = 1, PAGE_SYM2 = 2, PAGE_EMOJI = 3;

    private static final String NUMBERS = "1 2 3 4 5 6 7 8 9 0";
    private static final String[] LETTERS = {"q w e r t y u i o p", "a s d f g h j k l", "z x c v b n m"};
    private static final String[] SYM1 = {"+ × ÷ = / _ < > [ ]", "! @ # ₹ % ^ & * ( )", "- ' \" : ; , ?"};
    private static final String[] SYM2 = {"` ~ \\ | { } € £ ¥ $",
            "° • ○ ● □ ■ ♤ ♡ ◇ ♧",
            "☆ ▪ ¤ 《 》 ¡ ¿"};

    private static final int KEY_H = 46;     // dp
    private static final float GAP = 1.5f;   // dp between keys

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private boolean listening;
    private int failRounds;
    private boolean composing;
    private String roundLead = "";

    private int mode = MODE_BN;
    private int theme = 0;
    private int page = PAGE_ABC;
    private int emojiTab = 1;
    private boolean shift, capsLock;
    private long lastShiftTap;

    private int cBg, cKey, cFn, cInk, cMuted, cAcc;

    private LinearLayout keysBox;
    private TextView mic;
    private TextView spaceKey;
    private TextView shiftKey;
    private final List<TextView> chips = new ArrayList<>();
    private final List<TextView> letterKeys = new ArrayList<>();
    private String spaceText = null;

    private final Runnable nextRound = new Runnable() {
        @Override public void run() { if (listening) beginRound(); }
    };
    private final Runnable repeatDelete = new Runnable() {
        @Override public void run() { deleteOne(); handler.postDelayed(this, 55); }
    };
    private final Runnable clearSpace = new Runnable() {
        @Override public void run() { spaceText = null; paintSpace(); }
    };

    // ---------- helpers ----------
    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable box(int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("bolchi", Context.MODE_PRIVATE);
    }

    private void tick(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void loadColors() {
        cBg = Themes.color(theme, Themes.BG);
        cKey = Themes.color(theme, Themes.KEY);
        cFn = Themes.color(theme, Themes.FN);
        cInk = Themes.color(theme, Themes.INK);
        cMuted = Themes.color(theme, Themes.MUTED);
        cAcc = Themes.color(theme, Themes.ACCENT);
    }

    private TextView makeKey(String label, float weight, int fill, int textColor, float textSp) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(textColor);
        t.setTextSize(textSp);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setBackground(box(fill, 7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(KEY_H), weight);
        lp.setMargins(dp(GAP), dp(2), dp(GAP), dp(2));
        t.setLayoutParams(lp);
        return t;
    }

    private View spacer(float weight) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, dp(1), weight));
        return v;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return r;
    }

    // ---------- layout ----------
    @Override
    public View onCreateInputView() {
        SharedPreferences p = prefs();
        mode = p.getInt("mode", MODE_BN);
        if (mode < 0 || mode > 2) mode = MODE_BN;
        theme = p.getInt("theme", 0);
        if (theme < 0 || theme >= Themes.count()) theme = 0;
        loadColors();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        root.setPadding(dp(3), dp(4), dp(3), dp(6));

        keysBox = new LinearLayout(this);
        keysBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(keysBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // bottom bar: palette, language chips, speak
        LinearLayout bar = row();
        ((LinearLayout.LayoutParams) bar.getLayoutParams()).topMargin = dp(4);
        TextView pal = new TextView(this);
        pal.setText("🎨");
        pal.setTextSize(17);
        pal.setGravity(Gravity.CENTER);
        pal.setBackground(box(cFn, 20));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(46), dp(40));
        plp.setMargins(dp(GAP), 0, dp(GAP), 0);
        pal.setLayoutParams(plp);
        pal.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tick(v); nextTheme(); }
        });
        bar.addView(pal);

        chips.clear();
        for (int i = 0; i < 3; i++) {
            final int m = i;
            TextView c = new TextView(this);
            c.setText(MODE_LABEL[i]);
            c.setTextSize(14);
            c.setGravity(Gravity.CENTER);
            c.setSingleLine(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            lp.setMargins(dp(GAP), 0, dp(GAP), 0);
            c.setLayoutParams(lp);
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { tick(v); setMode(m); }
            });
            chips.add(c);
            bar.addView(c);
        }
        mic = new TextView(this);
        mic.setTextColor(Color.WHITE);
        mic.setTextSize(15);
        mic.setTypeface(Typeface.DEFAULT_BOLD);
        mic.setGravity(Gravity.CENTER);
        mic.setSingleLine(true);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, dp(40), 1.4f);
        mlp.setMargins(dp(GAP), 0, dp(GAP), 0);
        mic.setLayoutParams(mlp);
        mic.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                tick(v);
                if (listening) stopListening(null); else startListening();
            }
        });
        bar.addView(mic);
        root.addView(bar);

        paintChips();
        paintMic();
        buildKeys();
        return root;
    }

    private void buildKeys() {
        keysBox.removeAllViews();
        letterKeys.clear();
        shiftKey = null;
        spaceKey = null;
        if (page == PAGE_EMOJI) { buildEmoji(); return; }

        String[] rows = page == PAGE_ABC ? LETTERS : page == PAGE_SYM1 ? SYM1 : SYM2;

        LinearLayout nums = row();
        for (String k : NUMBERS.split(" ")) nums.addView(charKey(k));
        keysBox.addView(nums);

        LinearLayout r1 = row();
        for (String k : rows[0].split(" ")) r1.addView(charKey(k));
        keysBox.addView(r1);

        LinearLayout r2 = row();
        if (page == PAGE_ABC) r2.addView(spacer(0.5f));
        for (String k : rows[1].split(" ")) r2.addView(charKey(k));
        if (page == PAGE_ABC) r2.addView(spacer(0.5f));
        keysBox.addView(r2);

        LinearLayout r3 = row();
        if (page == PAGE_ABC) {
            shiftKey = makeKey("⇧", 1.5f, cFn, cInk, 20);
            shiftKey.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { tick(v); onShift(); }
            });
            r3.addView(shiftKey);
        } else {
            TextView pg = makeKey(page == PAGE_SYM1 ? "1/2" : "2/2", 1.5f, cFn, cInk, 15);
            pg.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    tick(v); page = page == PAGE_SYM1 ? PAGE_SYM2 : PAGE_SYM1; buildKeys();
                }
            });
            r3.addView(pg);
        }
        for (String k : rows[2].split(" ")) r3.addView(charKey(k));
        r3.addView(deleteKey(1.5f));
        keysBox.addView(r3);

        LinearLayout r4 = row();
        TextView pageKey = makeKey(page == PAGE_ABC ? "!#1" : "ABC", 1.5f, cFn, cInk, 15);
        pageKey.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                tick(v); page = page == PAGE_ABC ? PAGE_SYM1 : PAGE_ABC; buildKeys();
            }
        });
        r4.addView(pageKey);
        r4.addView(emojiKey());
        if (page == PAGE_ABC) r4.addView(charKey(","));
        spaceKey = makeKey("", page == PAGE_ABC ? 4f : 5f, cKey, cMuted, 13);
        spaceKey.setEllipsize(TextUtils.TruncateAt.START);
        spaceKey.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tick(v); typeSpace(); }
        });
        spaceKey.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showPicker(); return true; }
        });
        r4.addView(spaceKey);
        r4.addView(charKey("."));
        TextView enter = makeKey("⏎", 1.5f, cAcc, Color.WHITE, 20);
        enter.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tick(v); onEnter(); }
        });
        r4.addView(enter);
        keysBox.addView(r4);

        paintSpace();
        updateShift();
    }

    private TextView emojiKey() {
        TextView e = makeKey("🙂", 1f, cFn, cInk, 19);
        e.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tick(v); page = PAGE_EMOJI; buildKeys(); }
        });
        return e;
    }

    private TextView deleteKey(float weight) {
        TextView del = makeKey("⌫", weight, cFn, cInk, 20);
        del.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                int a = e.getAction();
                if (a == MotionEvent.ACTION_DOWN) {
                    tick(v);
                    deleteOne();
                    handler.postDelayed(repeatDelete, 400);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    handler.removeCallbacks(repeatDelete);
                }
                return true;
            }
        });
        return del;
    }

    private TextView charKey(final String ch) {
        final TextView t = makeKey(ch, 1f, cKey, cInk, 23);
        final boolean letter = page == PAGE_ABC && ch.length() == 1 && Character.isLetter(ch.charAt(0));
        if (letter) letterKeys.add(t);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                tick(v);
                String out = t.getText().toString();
                if (".".equals(ch) || ",".equals(ch) || "?".equals(ch) || "!".equals(ch)) typeMark(out);
                else commit(out);
                if (letter && shift && !capsLock) { shift = false; updateShift(); }
            }
        });
        return t;
    }

    // ---------- emoji panel ----------
    private void buildEmoji() {
        int widthDp = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        int cols = Math.max(8, Math.min(14, widthDp / 46));

        String list = emojiTab == 0 ? prefs().getString("recent", "") : Emojis.SETS[emojiTab];
        String[] items = list.trim().isEmpty() ? new String[0] : list.trim().split(" ");

        ScrollView scroll = new ScrollView(this);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(grid);
        if (items.length == 0) {
            TextView none = new TextView(this);
            none.setText("Emojis you use will show here");
            none.setTextColor(cMuted);
            none.setTextSize(14);
            none.setGravity(Gravity.CENTER);
            none.setPadding(0, dp(40), 0, 0);
            grid.addView(none);
        }
        LinearLayout r = null;
        for (int i = 0; i < items.length; i++) {
            if (i % cols == 0) { r = row(); grid.addView(r); }
            final String em = items[i];
            TextView t = new TextView(this);
            t.setText(em);
            t.setTextSize(26);
            t.setGravity(Gravity.CENTER);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, dp(KEY_H + 2), 1f));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { tick(v); commit(em); addRecent(em); }
            });
            r.addView(t);
        }
        if (r != null) {
            int fill = cols - (items.length % cols);
            if (fill < cols) for (int i = 0; i < fill; i++) r.addView(spacer(1f));
        }
        keysBox.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4 * (KEY_H + 4))));

        LinearLayout tabs = row();
        TextView abc = makeKey("ABC", 1.5f, cFn, cInk, 15);
        abc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tick(v); page = PAGE_ABC; buildKeys(); }
        });
        tabs.addView(abc);
        for (int i = 0; i < Emojis.TAB_ICONS.length; i++) {
            final int tab = i;
            TextView t = makeKey(Emojis.TAB_ICONS[i], 1f, i == emojiTab ? cFn : cKey, cInk, 19);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { tick(v); emojiTab = tab; buildKeys(); }
            });
            tabs.addView(t);
        }
        tabs.addView(deleteKey(1.5f));
        keysBox.addView(tabs);
    }

    private void addRecent(String em) {
        String cur = prefs().getString("recent", "");
        StringBuilder b = new StringBuilder(em);
        int n = 1;
        for (String s : cur.trim().split(" ")) {
            if (s.isEmpty() || s.equals(em)) continue;
            if (n >= 32) break;
            b.append(' ').append(s);
            n++;
        }
        prefs().edit().putString("recent", b.toString()).apply();
    }

    // ---------- typing ----------
    private void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.finishComposingText();
        composing = false;
        ic.commitText(s, 1);
    }

    private void typeSpace() {
        commit(" ");
        autoShift();
    }

    /** Punctuation sits right after the last word, then a space. */
    private void typeMark(String m) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.finishComposingText();
        composing = false;
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        if (before != null && before.length() == 1 && before.charAt(0) == ' ') ic.deleteSurroundingText(1, 0);
        ic.commitText(m + " ", 1);
        if (page != PAGE_ABC) { page = PAGE_ABC; buildKeys(); }
        autoShift();
    }

    private void deleteOne() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.finishComposingText();
        composing = false;
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) { ic.commitText("", 1); return; }
        CharSequence b = ic.getTextBeforeCursor(2, 0);
        if (b != null && b.length() == 2 && Character.isSurrogatePair(b.charAt(0), b.charAt(1))) {
            ic.deleteSurroundingText(2, 0);
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        }
    }

    private void onEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.finishComposingText();
        composing = false;
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei != null) {
            int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            boolean noEnterAction = (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
            boolean multiLine = (ei.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
            if (!noEnterAction && !multiLine && action != EditorInfo.IME_ACTION_NONE
                    && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action);
                return;
            }
        }
        sendKeyChar('\n');
        autoShift();
    }

    private void onShift() {
        long now = System.currentTimeMillis();
        if (capsLock) { capsLock = false; shift = false; }
        else if (shift && now - lastShiftTap < 400) { capsLock = true; }
        else { shift = !shift; }
        lastShiftTap = now;
        updateShift();
    }

    /** Capital letter at the start of a message or a new sentence. */
    private void autoShift() {
        if (page != PAGE_ABC || capsLock) return;
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null) return;
        int cls = ei.inputType & EditorInfo.TYPE_MASK_CLASS;
        int var = ei.inputType & EditorInfo.TYPE_MASK_VARIATION;
        if (cls != EditorInfo.TYPE_CLASS_TEXT || var == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || var == EditorInfo.TYPE_TEXT_VARIATION_URI || var == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                || var == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD) {
            if (shift) { shift = false; updateShift(); }
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        boolean start = false;
        if (ic != null) {
            CharSequence b = ic.getTextBeforeCursor(3, 0);
            if (b == null || b.length() == 0) start = true;
            else {
                String s = b.toString();
                start = s.endsWith("\n") || s.endsWith(". ") || s.endsWith("? ") || s.endsWith("! ");
            }
        }
        if (shift != start) { shift = start; updateShift(); }
    }

    private void updateShift() {
        boolean up = shift || capsLock;
        for (TextView t : letterKeys) {
            String s = t.getText().toString();
            t.setText(up ? s.toUpperCase() : s.toLowerCase());
        }
        if (shiftKey != null) {
            shiftKey.setText(capsLock ? "⇪" : "⇧");
            shiftKey.setBackground(box(up ? cKey : cFn, 7));
            shiftKey.setTextColor(up ? cAcc : cInk);
        }
    }

    private void showPicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (keysBox == null) return;
        capsLock = false;
        int cls = info.inputType & EditorInfo.TYPE_MASK_CLASS;
        int want = (cls == EditorInfo.TYPE_CLASS_NUMBER || cls == EditorInfo.TYPE_CLASS_PHONE) ? PAGE_SYM1 : PAGE_ABC;
        if (page != want) { page = want; buildKeys(); }
        autoShift();
        spaceText = null;
        paintSpace();
    }

    // ---------- look ----------
    private void nextTheme() {
        theme = (theme + 1) % Themes.count();
        prefs().edit().putInt("theme", theme).apply();
        boolean was = listening;
        if (was) stopListening(null);
        setInputView(onCreateInputView());
        flashSpace(Themes.NAMES[theme]);
    }

    private void flashSpace(String s) {
        handler.removeCallbacks(clearSpace);
        spaceText = s;
        paintSpace();
        handler.postDelayed(clearSpace, 1500);
    }

    private void paintSpace() {
        if (spaceKey == null) return;
        String s = spaceText != null ? spaceText : listening ? "Listening… tap Stop when done" : "Bolchi";
        spaceKey.setText(s);
    }

    private void paintChips() {
        for (int i = 0; i < chips.size(); i++) {
            TextView c = chips.get(i);
            boolean on = i == mode;
            c.setBackground(box(on ? cInk : cKey, 20));
            c.setTextColor(on ? cKey : cInk);
            c.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private void paintMic() {
        if (mic == null) return;
        mic.setText(listening ? "● Stop" : "🎤 Speak");
        mic.setBackground(box(listening ? cInk : cAcc, 20));
        mic.setTextColor(listening ? cKey : Color.WHITE);
    }

    // ---------- voice ----------
    private void setMode(int m) {
        if (m == mode) return;
        boolean was = listening;
        if (was) stopListening(null);
        mode = m;
        prefs().edit().putInt("mode", m).apply();
        paintChips();
        flashSpace(MODE_LABEL[m] + (m == MODE_BN ? " → Banglish" : m == MODE_HI ? " → Hinglish" : ""));
        if (was) startListening();
    }

    private String convert(String heard) {
        if (mode == MODE_BN) return Translit.toBanglish(heard);
        if (mode == MODE_HI) return HindiTranslit.toHinglish(heard);
        return heard;
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            flashSpace("Allow the mic in the Bolchi app");
            try {
                Intent i = new Intent(this, SetupActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) { /* setup screen unavailable */ }
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            flashSpace("Turn on the Google app for voice");
            return;
        }
        listening = true;
        failRounds = 0;
        composing = false;
        paintMic();
        paintSpace();
        beginRound();
    }

    private void freshRecognizer() {
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception e) { /* already gone */ }
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
    }

    private void beginRound() {
        try {
            if (recognizer == null) freshRecognizer();
            String lang = MODE_LANG[mode];
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L);
            recognizer.startListening(i);
        } catch (Exception e) {
            retryLater();
        }
    }

    /** Something went wrong mid-way: start a fresh recogniser and carry on. */
    private void retryLater() {
        failRounds++;
        if (failRounds > 6) { stopListening("Voice stopped. Tap Speak again."); return; }
        try { freshRecognizer(); } catch (Exception e) { /* try again below */ }
        handler.postDelayed(nextRound, 400);
    }

    private void stopListening(String message) {
        boolean was = listening;
        listening = false;
        handler.removeCallbacks(nextRound);
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception e) { /* nothing to cancel */ }
        }
        if (was) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
        }
        composing = false;
        paintMic();
        if (message != null) flashSpace(message); else paintSpace();
    }

    private static String first(Bundle b) {
        if (b == null) return "";
        ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (l == null || l.isEmpty() || l.get(0) == null) return "";
        return l.get(0).trim();
    }

    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { }
    @Override public void onEvent(int eventType, Bundle params) { }

    /** A space before new speech unless we're at the start or already after a space. */
    private String lead() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence b = ic.getTextBeforeCursor(1, 0);
        if (b == null || b.length() == 0) return "";
        char c = b.charAt(0);
        return (c == ' ' || c == '\n') ? "" : " ";
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        if (!listening) return;
        String heard = first(partialResults);
        if (heard.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (!composing) { roundLead = lead(); composing = true; }
        ic.setComposingText(roundLead + convert(heard), 1);
    }

    @Override
    public void onResults(Bundle results) {
        if (!listening) return;
        String heard = first(results);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            if (!heard.isEmpty()) {
                if (!composing) roundLead = lead();
                ic.setComposingText(roundLead + convert(heard), 1);
            }
            ic.finishComposingText();
        }
        composing = false;
        failRounds = 0;
        handler.postDelayed(nextRound, 60);
    }

    @Override
    public void onError(int error) {
        if (!listening) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
        composing = false;
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                // just a pause in speech: keep listening
                handler.postDelayed(nextRound, 100);
                break;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
            case SpeechRecognizer.ERROR_SERVER:
                stopListening("Voice needs internet");
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                stopListening("Allow the mic in the Bolchi app");
                break;
            default:
                if (error == 12 || error == 13) {
                    stopListening(MODE_LABEL[mode] + " voice not set up, see Bolchi app");
                } else {
                    retryLater();
                }
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        if (listening) stopListening(null);
        handler.removeCallbacks(repeatDelete);
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception e) { /* already gone */ }
            recognizer = null;
        }
        super.onDestroy();
    }
}
