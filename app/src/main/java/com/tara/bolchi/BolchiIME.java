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
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * One keyboard for everything: type English on the letter keys, or tap the mic and speak
 * Bangla (types Banglish), Hindi (types Hinglish) or English.
 */
public class BolchiIME extends InputMethodService implements RecognitionListener {

    private static final int MODE_BN = 0, MODE_HI = 1, MODE_EN = 2;
    private static final String[] MODE_LABEL = {"Bangla", "Hindi", "English"};
    private static final String[] MODE_LANG = {"bn-IN", "hi-IN", "en-IN"};
    private static final String[] MODE_HINT = {
            "Speak Bengali \u2192 types Banglish",
            "Speak Hindi \u2192 types Hinglish",
            "Speak English \u2192 types English"};

    private static final int BG = Color.parseColor("#EFE3D3");
    private static final int INK = Color.parseColor("#2B211A");
    private static final int MUTED = Color.parseColor("#7A6A5C");
    private static final int ACCENT = Color.parseColor("#B9502B");
    private static final int ACCENT_ON = Color.parseColor("#8F3417");
    private static final int KEY = Color.parseColor("#FFFAF2");
    private static final int KEY_FN = Color.parseColor("#E2D2BE");
    private static final int CHIP_ON = Color.parseColor("#2B211A");

    private static final String[][] LETTERS = {
            {"q","w","e","r","t","y","u","i","o","p"},
            {"a","s","d","f","g","h","j","k","l"},
            {"z","x","c","v","b","n","m"}};
    private static final String[][] SYMBOLS = {
            {"1","2","3","4","5","6","7","8","9","0"},
            {"@","#","\u20B9","_","&","-","+","(",")"},
            {"*","\"","'",":",";","!","?"}};
    private static final String[][] SYMBOLS2 = {
            {"~","`","|","\u2022","\u221A","\u03C0","\u00F7","\u00D7","{","}"},
            {"$","\u20AC","\u00A3","%","^","=","[","]","/"},
            {"<",">","\\","\u00A9","\u00B0","\u2026","\u2013"}};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private boolean listening;
    private int quietRounds;
    private int mode = MODE_BN;
    private int page = 0;            // 0 letters, 1 symbols, 2 more symbols
    private boolean shift, capsLock;
    private long lastShiftTap;

    private LinearLayout root;
    private LinearLayout keysBox;
    private TextView status;
    private TextView mic;
    private final List<TextView> chips = new ArrayList<>();
    private final List<TextView> letterKeys = new ArrayList<>();
    private TextView shiftKey;

    private final Runnable nextRound = new Runnable() {
        @Override public void run() { if (listening) beginRound(); }
    };
    private final Runnable repeatDelete = new Runnable() {
        @Override public void run() {
            deleteOne();
            handler.postDelayed(this, 55);
        }
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

    private TextView makeKey(String label, float weight, int fill) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(INK);
        t.setTextSize(20);
        t.setGravity(Gravity.CENTER);
        t.setBackground(box(fill, 7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), weight);
        lp.setMargins(dp(2.5f), dp(3), dp(2.5f), dp(3));
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
        mode = prefs().getInt("mode", MODE_BN);
        if (mode < 0 || mode > 2) mode = MODE_BN;

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(4), dp(6), dp(4), dp(8));

        // voice bar: language chips + mic
        LinearLayout bar = row();
        chips.clear();
        for (int i = 0; i < 3; i++) {
            final int m = i;
            TextView c = new TextView(this);
            c.setText(MODE_LABEL[i]);
            c.setTextSize(14);
            c.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            lp.setMargins(dp(2.5f), 0, dp(2.5f), 0);
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
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, dp(40), 1.4f);
        mlp.setMargins(dp(2.5f), 0, dp(2.5f), 0);
        mic.setLayoutParams(mlp);
        mic.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                tick(v);
                if (listening) stopListening(null); else startListening();
            }
        });
        bar.addView(mic);
        root.addView(bar);

        status = new TextView(this);
        status.setTextColor(MUTED);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.START);
        status.setPadding(dp(6), dp(4), dp(6), dp(2));
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        keysBox = new LinearLayout(this);
        keysBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(keysBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        paintChips();
        paintMic();
        say(null);
        buildKeys();
        return root;
    }

    private void buildKeys() {
        keysBox.removeAllViews();
        letterKeys.clear();
        shiftKey = null;
        String[][] rows = page == 0 ? LETTERS : page == 1 ? SYMBOLS : SYMBOLS2;

        // row 1
        LinearLayout r1 = row();
        for (String k : rows[0]) r1.addView(charKey(k));
        keysBox.addView(r1);

        // row 2
        LinearLayout r2 = row();
        r2.addView(spacer(0.5f));
        for (String k : rows[1]) r2.addView(charKey(k));
        r2.addView(spacer(0.5f));
        keysBox.addView(r2);

        // row 3
        LinearLayout r3 = row();
        if (page == 0) {
            shiftKey = makeKey("\u21E7", 1.5f, KEY_FN);
            shiftKey.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { tick(v); onShift(); }
            });
            r3.addView(shiftKey);
        } else {
            TextView more = makeKey(page == 1 ? "=\\<" : "?123", 1.5f, KEY_FN);
            more.setTextSize(14);
            more.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { tick(v); page = page == 1 ? 2 : 1; buildKeys(); }
            });
            r3.addView(more);
        }
        for (String k : rows[2]) r3.addView(charKey(k));
        TextView del = makeKey("\u232B", 1.5f, KEY_FN);
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
        r3.addView(del);
        keysBox.addView(r3);

        // row 4
        LinearLayout r4 = row();
        TextView pageKey = makeKey(page == 0 ? "?123" : "ABC", 1.5f, KEY_FN);
        pageKey.setTextSize(14);
        pageKey.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tick(v); page = page == 0 ? 1 : 0; buildKeys(); }
        });
        r4.addView(pageKey);
        r4.addView(charKey(","));
        TextView globe = makeKey("\uD83C\uDF10", 1f, KEY_FN);
        globe.setTextSize(16);
        globe.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tick(v); showPicker(); }
        });
        r4.addView(globe);
        TextView space = makeKey("Bolchi", 4f, KEY);
        space.setTextSize(13);
        space.setTextColor(MUTED);
        space.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tick(v); typeSpace(); }
        });
        space.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showPicker(); return true; }
        });
        r4.addView(space);
        r4.addView(charKey("."));
        TextView enter = makeKey("\u23CE", 1.5f, ACCENT);
        enter.setTextColor(Color.WHITE);
        enter.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tick(v); onEnter(); }
        });
        r4.addView(enter);
        keysBox.addView(r4);

        updateShift();
    }

    private TextView charKey(final String ch) {
        final TextView t = makeKey(ch, 1f, KEY);
        if (ch.length() == 1 && Character.isLetter(ch.charAt(0)) && page == 0) letterKeys.add(t);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                tick(v);
                String out = t.getText().toString();
                if (".".equals(ch) || ",".equals(ch) || "?".equals(ch) || "!".equals(ch)) {
                    typeMark(out);
                } else {
                    commit(out);
                }
                if (shift && !capsLock && page == 0 && letterKeys.contains(t)) {
                    shift = false;
                    updateShift();
                }
                if ("'".equals(ch) && page != 0) { page = 0; buildKeys(); }
            }
        });
        return t;
    }

    // ---------- typing ----------
    private void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.finishComposingText();
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
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        if (before != null && before.length() == 1 && before.charAt(0) == ' ') ic.deleteSurroundingText(1, 0);
        ic.commitText(m + " ", 1);
        if (page != 0) { page = 0; buildKeys(); }
        autoShift();
    }

    private void deleteOne() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.finishComposingText();
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) ic.commitText("", 1);
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
    }

    private void onEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.finishComposingText();
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
        if (page != 0 || capsLock) return;
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
            shiftKey.setText(capsLock ? "\u21EA" : "\u21E7");
            shiftKey.setBackground(box(up ? Color.parseColor("#F3DCCB") : KEY_FN, 7));
            shiftKey.setTextColor(up ? ACCENT : INK);
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
        int want = (cls == EditorInfo.TYPE_CLASS_NUMBER || cls == EditorInfo.TYPE_CLASS_PHONE) ? 1 : 0;
        if (page != want) { page = want; buildKeys(); }
        autoShift();
        say(null);
    }

    // ---------- voice ----------
    private void setMode(int m) {
        if (m == mode) return;
        boolean was = listening;
        if (was) stopListening("");
        mode = m;
        prefs().edit().putInt("mode", m).apply();
        paintChips();
        paintMic();
        say(null);
        if (was) startListening();
    }

    private void paintChips() {
        for (int i = 0; i < chips.size(); i++) {
            TextView c = chips.get(i);
            boolean on = i == mode;
            c.setBackground(box(on ? CHIP_ON : KEY, 20));
            c.setTextColor(on ? Color.WHITE : INK);
            c.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private void paintMic() {
        if (mic == null) return;
        mic.setText(listening ? "\u25CF Stop" : "\uD83C\uDFA4 Speak");
        mic.setBackground(box(listening ? ACCENT_ON : ACCENT, 20));
    }

    private void say(String s) {
        if (status == null) return;
        if (s == null) s = listening ? "Listening\u2026" : MODE_HINT[mode];
        status.setText(s);
    }

    private String convert(String heard) {
        if (mode == MODE_BN) return Translit.toBanglish(heard);
        if (mode == MODE_HI) return HindiTranslit.toHinglish(heard);
        return heard;
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            say("Allow the microphone in the Bolchi app first");
            try {
                Intent i = new Intent(this, SetupActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) { /* setup screen unavailable */ }
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("No voice typing service. Enable the Google app.");
            return;
        }
        listening = true;
        quietRounds = 0;
        paintMic();
        say(null);
        beginRound();
    }

    private void beginRound() {
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                recognizer.setRecognitionListener(this);
            }
            String lang = MODE_LANG[mode];
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            recognizer.startListening(i);
        } catch (Exception e) {
            stopListening("Could not start the mic. Tap Speak again.");
        }
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
        paintMic();
        say(message);
    }

    private static String first(Bundle b) {
        if (b == null) return "";
        ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (l == null || l.isEmpty() || l.get(0) == null) return "";
        return l.get(0).trim();
    }

    @Override public void onReadyForSpeech(Bundle params) { if (listening) say(null); }
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
        String t = convert(heard);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.setComposingText(t, 1);
        say(t);
    }

    @Override
    public void onResults(Bundle results) {
        if (!listening) return;
        String heard = first(results);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            if (!heard.isEmpty()) {
                ic.setComposingText("", 1);
                ic.finishComposingText();
                ic.commitText(lead() + convert(heard), 1);
                quietRounds = 0;
            } else {
                ic.finishComposingText();
            }
        }
        say(null);
        handler.postDelayed(nextRound, 150);
    }

    @Override
    public void onError(int error) {
        if (!listening) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                quietRounds++;
                if (quietRounds >= 2) stopListening("Paused. Tap Speak to talk again.");
                else handler.postDelayed(nextRound, 200);
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                quietRounds++;
                if (quietRounds >= 3) {
                    stopListening("Voice typing is busy. Tap Speak again.");
                } else {
                    try { recognizer.cancel(); } catch (Exception e) { /* ignore */ }
                    handler.postDelayed(nextRound, 500);
                }
                break;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
            case SpeechRecognizer.ERROR_SERVER:
                stopListening("Voice needs internet. Check your connection.");
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                stopListening("Open the Bolchi app and allow the microphone.");
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                stopListening(null);
                break;
            default:
                if (error == 12 || error == 13) {
                    stopListening(MODE_LABEL[mode] + " voice not set up. See the Bolchi app.");
                } else {
                    stopListening("Voice stopped (code " + error + "). Tap Speak again.");
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
