package com.tara.bolchi;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/** A voice keyboard: speak Bengali, it types Banglish into whatever app is open. */
public class BolchiIME extends InputMethodService implements RecognitionListener {

    private static final String LANG = "bn-IN";
    private static final int BG = Color.parseColor("#F6ECE0");
    private static final int INK = Color.parseColor("#2B211A");
    private static final int MUTED = Color.parseColor("#7A6A5C");
    private static final int ACCENT = Color.parseColor("#B9502B");
    private static final int ACCENT_ON = Color.parseColor("#8F3417");
    private static final int KEY = Color.parseColor("#FFFAF2");
    private static final int LINE = Color.parseColor("#E6D7C3");
    private static final String IDLE = "Tap the mic and speak in Bengali";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private boolean listening;
    private int quietRounds;
    private TextView status;
    private TextView mic;

    private final Runnable nextRound = new Runnable() {
        @Override public void run() { if (listening) beginRound(); }
    };
    private final Runnable repeatDelete = new Runnable() {
        @Override public void run() {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            handler.postDelayed(this, 60);
        }
    };

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

    private TextView key(String label, float weight) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(INK);
        t.setTextSize(18);
        t.setGravity(Gravity.CENTER);
        t.setBackground(box(KEY, LINE, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), weight);
        lp.setMargins(dp(3), 0, dp(3), 0);
        t.setLayoutParams(lp);
        return t;
    }

    @Override
    public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(8), dp(8), dp(8), dp(10));

        status = new TextView(this);
        status.setText(IDLE);
        status.setTextColor(MUTED);
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        status.setMaxLines(2);
        status.setPadding(dp(6), dp(2), dp(6), dp(8));
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mic = new TextView(this);
        mic.setTextColor(Color.WHITE);
        mic.setTextSize(18);
        mic.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60));
        mlp.setMargins(dp(3), 0, dp(3), dp(8));
        root.addView(mic, mlp);
        mic.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (listening) stopListening(IDLE); else startListening();
            }
        });
        paintMic();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView abc = key("ABC", 1.3f);
        abc.setTextSize(14);
        abc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchKeyboard(); }
        });
        abc.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showPicker(); return true; }
        });
        row.addView(abc);

        final String[] marks = {",", ".", "?"};
        for (int i = 0; i < marks.length; i++) {
            final String m = marks[i];
            TextView k = key(m, 1f);
            k.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { typeMark(m); }
            });
            row.addView(k);
        }

        TextView space = key("space", 2.4f);
        space.setTextSize(14);
        space.setTextColor(MUTED);
        space.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { commit(" "); }
        });
        row.addView(space);

        TextView del = key("⌫", 1.3f);
        del.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                int a = e.getAction();
                if (a == MotionEvent.ACTION_DOWN) {
                    finishComposing();
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                    handler.postDelayed(repeatDelete, 400);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    handler.removeCallbacks(repeatDelete);
                }
                return true;
            }
        });
        row.addView(del);

        TextView enter = key("⏎", 1.3f);
        enter.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finishComposing(); sendKeyChar('\n'); }
        });
        row.addView(enter);

        return root;
    }

    private void paintMic() {
        if (mic == null) return;
        mic.setText(listening ? "●  Listening… tap to stop" : "🎤  Tap to speak");
        mic.setBackground(box(listening ? ACCENT_ON : ACCENT, 0, 14));
    }

    private void say(String s) {
        if (status != null) status.setText(s);
    }

    private void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(s, 1);
    }

    private void finishComposing() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
    }

    /** Punctuation sits right after the last word, then a space. */
    private void typeMark(String m) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.finishComposingText();
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        if (before != null && before.length() == 1 && before.charAt(0) == ' ') ic.deleteSurroundingText(1, 0);
        ic.commitText(m + " ", 1);
    }

    private void showPicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    private void switchKeyboard() {
        stopListening(IDLE);
        boolean ok = false;
        if (Build.VERSION.SDK_INT >= 28) {
            try { ok = switchToPreviousInputMethod(); } catch (Exception e) { ok = false; }
        }
        if (!ok) showPicker();
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            say("Microphone not allowed yet. Opening Bolchi to allow it…");
            try {
                Intent i = new Intent(this, SetupActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) {
                say("Open the Bolchi Keyboard app and allow the microphone.");
            }
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("No voice typing service found. Install or enable the Google app.");
            return;
        }
        listening = true;
        quietRounds = 0;
        paintMic();
        say("Listening…");
        beginRound();
    }

    private void beginRound() {
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                recognizer.setRecognitionListener(this);
            }
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANG);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANG);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            recognizer.startListening(i);
        } catch (Exception e) {
            stopListening("Could not start the mic. Tap to try again.");
        }
    }

    private void stopListening(String message) {
        boolean was = listening;
        listening = false;
        handler.removeCallbacks(nextRound);
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception e) { /* nothing to cancel */ }
        }
        if (was) finishComposing();
        paintMic();
        say(message);
    }

    private static String first(Bundle b) {
        if (b == null) return "";
        ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (l == null || l.isEmpty() || l.get(0) == null) return "";
        return l.get(0).trim();
    }

    // ---- RecognitionListener ----
    @Override public void onReadyForSpeech(Bundle params) { if (listening) say("Listening…"); }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { }
    @Override public void onEvent(int eventType, Bundle params) { }

    @Override
    public void onPartialResults(Bundle partialResults) {
        if (!listening) return;
        String bn = first(partialResults);
        if (bn.isEmpty()) return;
        String t = Translit.toBanglish(bn);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.setComposingText(t, 1);
        say(t);
    }

    @Override
    public void onResults(Bundle results) {
        if (!listening) return;
        String bn = first(results);
        if (!bn.isEmpty()) {
            commit(Translit.toBanglish(bn) + " ");
            quietRounds = 0;
        } else {
            finishComposing();
        }
        say("Listening…");
        handler.postDelayed(nextRound, 150);
    }

    @Override
    public void onError(int error) {
        if (!listening) return;
        finishComposing();
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                quietRounds++;
                if (quietRounds >= 2) stopListening("Paused. Tap the mic to speak again.");
                else handler.postDelayed(nextRound, 200);
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                quietRounds++;
                if (quietRounds >= 3) {
                    stopListening("Voice typing is busy. Tap to try again.");
                } else {
                    try { recognizer.cancel(); } catch (Exception e) { /* ignore */ }
                    handler.postDelayed(nextRound, 500);
                }
                break;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
            case SpeechRecognizer.ERROR_SERVER:
                stopListening("Voice typing needs internet. Check your connection.");
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                stopListening("Open the Bolchi Keyboard app and allow the microphone.");
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                stopListening(IDLE);
                break;
            default:
                if (error == 12 || error == 13) {
                    stopListening("Bengali voice is not set up on this phone. Open the Bolchi app for the fix.");
                } else {
                    stopListening("Voice typing stopped (code " + error + "). Tap to try again.");
                }
        }
    }

    // ---- lifecycle ----
    @Override
    public void onFinishInputView(boolean finishingInput) {
        if (listening) stopListening(IDLE);
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
