package com.tara.bolchi;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.PrintWriter;
import java.io.StringWriter;
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

    private static final int PAGE_ABC = 0, PAGE_SYM1 = 1, PAGE_SYM2 = 2, PAGE_EMOJI = 3, PAGE_CONVERT = 4;
    private static final String[] CONV_NAMES = {"Translate to English", "To Bangla script", "To English letters", "Units and currency"};

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
    private final StringBuilder spokenAll = new StringBuilder();   // everything written in this Speak session
    private String lastPartial = "";
    private int tidyJob;
    private boolean tidyBusy, tidyFailed;
    private String tidyPendSaid, tidyPendOut, tidyDone = "";

    private int mode = MODE_BN;
    private int theme = 0;
    private int page = PAGE_ABC;
    private int emojiTab = 1;
    private static final String[] LAYOUT_KEY = {"EN", "ab\u2192\u09ac\u09be\u0982", "ab\u2192\u0939\u093f\u0902", "\u09ac\u09be\u0982", "\u0939\u093f\u0902"};
    private static final String[] LAYOUT_NAME = {"English", "Bangla: type English, get \u0986\u09ae\u09bf",
            "Hindi: type English, get \u092e\u0948\u0902", "Bangla letter keys", "Hindi letter keys"};
    private int layout = 0;
    private boolean scriptShift;
    private final StringBuilder phon = new StringBuilder();
    private List<String> cands = new ArrayList<>();
    private String candFor = "";
    private int candJob = 0;
    private LinearLayout candRow;
    private final boolean[] voiceScript = new boolean[2];
    private final List<TextView> vowelKeys = new ArrayList<>();
    private boolean vowelRowFull;
    private int convOption = 0;
    private int convJob = 0;
    private String convResult = null;
    private TextView convBtn;
    private TextView convResBox;
    private long convStarted;
    private boolean shift, capsLock;
    private long lastShiftTap;

    // word being typed on the English keys: suggestions and auto-correct
    private final StringBuilder word = new StringBuilder();
    private LinearLayout sugRow;
    private final TextView[] sug = new TextView[3];
    private final String[] sugVal = {"", "", ""};
    private boolean sugKeepsTyped;
    private boolean autoFix = true;
    private boolean noFix;
    private String fixTyped, fixDone;
    private float letterDp = 32;

    // all key touches are handled in one place, so two thumbs and near-misses work
    private final List<View> keyViews = new ArrayList<>();
    private final SparseArray<View> heldKeys = new SparseArray<>();
    private boolean directTouch;

    /**
     * The key area hands every finger to the nearest key itself. Android's normal delivery drops the
     * second thumb when the first finger rests on an empty spot, and ignores taps that land between keys.
     */
    private class KeyArea extends LinearLayout {
        KeyArea(Context c) { super(c); setWillNotDraw(false); }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (!swiping || swN < 2) return;
            swPaint.setColor(cAcc);
            swPaint.setAlpha(170);
            swPaint.setStyle(Paint.Style.STROKE);
            swPaint.setStrokeWidth(dp(7));
            swPaint.setStrokeCap(Paint.Cap.ROUND);
            swPaint.setStrokeJoin(Paint.Join.ROUND);
            swPaint.setAntiAlias(true);
            swPath.rewind();
            int from = Math.max(0, swN - 60);
            swPath.moveTo(swX[from], swY[from]);
            for (int i = from + 1; i < swN; i++) swPath.lineTo(swX[i], swY[i]);
            canvas.drawPath(swPath, swPaint);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (!directTouch) return super.dispatchTouchEvent(ev);
            try {
                int a = ev.getActionMasked();
                int idx = ev.getActionIndex();
                int id = ev.getPointerId(idx);
                if (a == MotionEvent.ACTION_DOWN) heldKeys.clear();
                if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
                    View k = keyAt(ev.getX(idx), ev.getY(idx));
                    if (swId != -1) { swId = -1; swiping = false; invalidate(); }   // a second finger: plain typing
                    else if (a == MotionEvent.ACTION_DOWN && k != null && wordsOn() && letterKeys.contains(k)) {
                        swId = id; swN = 0; swiping = false; swKeyW = k.getWidth();
                        swFirst = ((TextView) k).getText().toString();
                        swAdd(ev.getX(idx), ev.getY(idx));
                    }
                    if (k != null) { heldKeys.put(id, k); sendTo(k, MotionEvent.ACTION_DOWN, ev); }
                } else if (a == MotionEvent.ACTION_MOVE) {
                    int pi = swId == -1 ? -1 : ev.findPointerIndex(swId);
                    if (pi >= 0) {
                        for (int h = 0; h < ev.getHistorySize(); h++) swAdd(ev.getHistoricalX(pi, h), ev.getHistoricalY(pi, h));
                        swAdd(ev.getX(pi), ev.getY(pi));
                        if (!swiping && Math.hypot(ev.getX(pi) - swX[0], ev.getY(pi) - swY[0]) > swKeyW * 0.75f) swiping = true;
                        if (swiping) invalidate();
                    }
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP) {
                    View k = heldKeys.get(id);
                    heldKeys.remove(id);
                    if (k != null) sendTo(k, MotionEvent.ACTION_UP, ev);
                    if (id == swId) {
                        boolean was = swiping;
                        swId = -1; swiping = false; invalidate();
                        if (was) finishSwipe();
                    }
                } else if (a == MotionEvent.ACTION_CANCEL) {
                    swId = -1; swiping = false; invalidate();
                    for (int i = 0; i < heldKeys.size(); i++) sendTo(heldKeys.valueAt(i), MotionEvent.ACTION_CANCEL, ev);
                    heldKeys.clear();
                }
            } catch (Exception ex) { saveProblem(ex); }
            return true;
        }
    }

    // ---------- gesture typing: slide over the letters of a word ----------
    private final float[] swX = new float[600], swY = new float[600];
    private int swN, swId = -1;
    private boolean swiping;
    private float swKeyW;
    private String swFirst = "";
    private String swipeDone;          // the word (with its space) the last slide wrote
    private boolean sugSwipe;
    private final Paint swPaint = new Paint();
    private final Path swPath = new Path();

    private void swAdd(float x, float y) {
        if (swN > 0 && Math.hypot(x - swX[swN - 1], y - swY[swN - 1]) < dp(3)) return;
        if (swN < swX.length) { swX[swN] = x; swY[swN] = y; swN++; }
    }

    private void finishSwipe() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || !Words.ready || letterKeys.size() < 26) return;
        float[] cx = new float[26], cy = new float[26];
        for (TextView t : letterKeys) {
            String s = t.getText().toString().toLowerCase();
            if (s.length() != 1 || s.charAt(0) < 'a' || s.charAt(0) > 'z' || !(t.getParent() instanceof View)) continue;
            View row = (View) t.getParent();
            cx[s.charAt(0) - 'a'] = row.getLeft() + t.getLeft() + t.getWidth() / 2f;
            cy[s.charAt(0) - 'a'] = row.getTop() + t.getTop() + t.getHeight() / 2f;
        }
        List<String> got = Swipe.decode(swX, swY, swN, cx, cy, swKeyW, 3);
        if (got.isEmpty()) return;                       // nothing fits: the first letter stays
        String w = Words.matchCase(swFirst, got.get(0));
        ic.beginBatchEdit();
        ic.deleteSurroundingText(1, 0);                  // the letter typed when the finger touched down
        CharSequence b = ic.getTextBeforeCursor(1, 0);
        boolean glue = b == null || b.length() == 0 || Character.isWhitespace(b.charAt(0)) || "([{\"'".indexOf(b.charAt(0)) >= 0;
        ic.commitText((glue ? "" : " ") + w + " ", 1);
        ic.endBatchEdit();
        word.setLength(0);
        fixTyped = null;
        swipeDone = w + " ";
        if (sugRow != null) {
            sugKeepsTyped = false;
            sugSwipe = true;
            String[] v = {got.size() > 2 ? Words.matchCase(swFirst, got.get(2)) : "", "", got.size() > 1 ? Words.matchCase(swFirst, got.get(1)) : ""};
            v[1] = v[2]; v[2] = v[0]; v[0] = "";
            for (int i = 0; i < 3; i++) { sugVal[i] = v[i]; sug[i].setText(v[i]); sug[i].setTypeface(Typeface.DEFAULT); }
        }
        autoShift();
    }

    /** Backspace or another choice right after a slide works on the whole word. */
    private boolean swipeWordIsThere(InputConnection ic) {
        if (swipeDone == null) return false;
        CharSequence b = ic.getTextBeforeCursor(swipeDone.length(), 0);
        return b != null && b.toString().equals(swipeDone);
    }

    private void sendTo(View k, int action, MotionEvent from) {
        MotionEvent e = MotionEvent.obtain(from.getDownTime(), from.getEventTime(), action, 1f, 1f, 0);
        try { k.dispatchTouchEvent(e); } finally { e.recycle(); }
    }

    /** The key under the finger, or the closest one when the finger lands just beside a key. */
    private View keyAt(float x, float y) {
        View best = null;
        float bestD = dp(34) * (float) dp(34);
        for (int i = keyViews.size() - 1; i >= 0; i--) {
            View v = keyViews.get(i);
            if (!(v.getParent() instanceof View)) { keyViews.remove(i); continue; }
            View row = (View) v.getParent();
            if (row.getParent() != keysBox) continue;
            float l = row.getLeft() + v.getLeft(), t = row.getTop() + v.getTop();
            float dx = Math.max(0, Math.max(l - x, x - (l + v.getWidth())));
            float dy = Math.max(0, Math.max(t - y, y - (t + v.getHeight())));
            float d = dx * dx + dy * dy;
            if (d < bestD) { bestD = d; best = v; if (d == 0) break; }
        }
        return best;
    }


    private int cBg, cKey, cFn, cInk, cMuted, cAcc, cOnAcc;

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
        cOnAcc = Themes.onAccent(theme);
    }

    /** Key face drawn inset, so the touch area covers the gaps between keys too. */
    private Drawable keyFace(int fill) {
        return new InsetDrawable(box(fill, 7), dp(GAP), dp(2), dp(GAP), dp(2));
    }

    /** Changes a key's colour without rebuilding its background. */
    private void face(View v, int fill) {
        Object g = v.getTag();
        if (g instanceof GradientDrawable) ((GradientDrawable) g).setColor(fill);
        else v.setBackground(keyFace(fill));
    }

    private TextView makeKey(String label, float weight, int fill, int textColor, float textSp) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(textColor);
        t.setTextSize(textSp);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        GradientDrawable g = box(fill, 7);
        t.setTag(g);
        keyViews.add(t);
        t.setBackground(new InsetDrawable(g, dp(GAP), dp(2), dp(GAP), dp(2)));
        t.setLayoutParams(new LinearLayout.LayoutParams(0, dp(KEY_H + 4), weight));
        return t;
    }

    /** Pressed colour: a little darker (or lighter on dark themes) than the key. */
    private int pressedOf(int fill) {
        int r = (fill >> 16) & 0xFF, g = (fill >> 8) & 0xFF, b = fill & 0xFF;
        boolean dark = (0.299 * r + 0.587 * g + 0.114 * b) < 100;
        float f = dark ? 1.6f : 0.85f;
        int add = dark ? 30 : 0;
        return Color.rgb(Math.min(255, (int) (r * f) + add), Math.min(255, (int) (g * f) + add), Math.min(255, (int) (b * f) + add));
    }

    /** Runs the action the instant the finger touches the key, with a press highlight. */
    private void onPress(final TextView t, final int fill, final Runnable action) {
        t.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    try { action.run(); } catch (Exception ex) { saveProblem(ex); }
                    face(v, pressedOf(fill));
                    tick(v);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    face(v, fill);
                    if (v == shiftKey) updateShift();
                }
                return true;
            }
        });
    }

    private View spacer(float weight) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, dp(1), weight));
        return v;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setBaselineAligned(false);   // keep every key level, whatever its text size
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
        layout = p.getInt("layout", 0);
        if (layout < 0 || layout > 4) layout = 0;
        voiceScript[0] = p.getBoolean("bnScript", false);
        voiceScript[1] = p.getBoolean("hiScript", false);
        autoFix = p.getBoolean("autoFix", true);
        theme = p.getInt("theme", 0);
        if (theme < 0 || theme >= Themes.count()) theme = 0;
        loadColors();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        root.setPadding(0, 0, 0, dp(6));

        keysBox = new KeyArea(this);
        keysBox.setOrientation(LinearLayout.VERTICAL);
        keysBox.setPadding(dp(3), dp(4), dp(3), 0);
        root.addView(keysBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // bottom bar: palette, language chips, speak
        LinearLayout bar = row();
        ((LinearLayout.LayoutParams) bar.getLayoutParams()).setMargins(dp(3), dp(4), dp(3), 0);
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

        convBtn = new TextView(this);
        convBtn.setText("\u21C4");
        convBtn.setTextSize(19);
        convBtn.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(46), dp(40));
        clp.setMargins(dp(GAP), 0, dp(GAP), 0);
        convBtn.setLayoutParams(clp);
        convBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                tick(v);
                if (listening) stopListening(null);
                page = page == PAGE_CONVERT ? PAGE_ABC : PAGE_CONVERT;
                convResult = null;
                buildKeysSoon();
            }
        });
        bar.addView(convBtn);

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
                @Override public void onClick(View v) {
                    tick(v);
                    if (m == mode && m != MODE_EN) toggleVoiceScript(); else setMode(m);
                }
            });
            chips.add(c);
            bar.addView(c);
        }
        mic = new TextView(this);
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

    /** Rebuild after the current touch finishes (never remove a view while it is being touched). */
    private void buildKeysSoon() {
        handler.post(new Runnable() { @Override public void run() {
            try { buildKeys(); } catch (Exception ex) { saveProblem(ex); }
        } });
    }

    /** Saves what went wrong so the Bolchi app can show it. */
    void saveProblem(Throwable t) {
        try {
            StringWriter w = new StringWriter();
            t.printStackTrace(new PrintWriter(w));
            String txt = "Bolchi " + BuildConfigInfo.version(this) + "\n" + w.toString();
            if (txt.length() > 6000) txt = txt.substring(0, 6000);
            prefs().edit().putString("problem", txt).putLong("problemAt", System.currentTimeMillis()).commit();
        } catch (Throwable ignored) { /* nothing more we can do */ }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread th, Throwable ex) {
                saveProblem(ex);
                if (prev != null) prev.uncaughtException(th, ex);
            }
        });
        for (String w : prefs().getString("ownWords", "").split(" ")) Words.addOwn(w);
        if (!Words.ready) new Thread(new Runnable() { @Override public void run() {
            try { Words.load(getAssets().open("words.txt")); } catch (Throwable t) { saveProblem(t); }
        } }).start();
    }

    private void buildKeys() {
        keysBox.removeAllViews();
        keyViews.clear();
        directTouch = page != PAGE_EMOJI && page != PAGE_CONVERT;
        letterKeys.clear();
        shiftKey = null;
        spaceKey = null;
        sugRow = null;
        float keyW = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density / 10f;
        letterDp = Math.max(22f, Math.min(26f, keyW * 0.6f));
        if (convBtn != null) {
            boolean on = page == PAGE_CONVERT;
            convBtn.setBackground(box(on ? cInk : cFn, 20));
            convBtn.setTextColor(on ? cKey : cInk);
        }
        if (page == PAGE_EMOJI) { buildEmoji(); return; }
        if (page == PAGE_CONVERT) { buildConverter(); return; }

        candRow = null;
        if (page == PAGE_ABC && layout >= 3) { buildScriptKeys(); return; }
        String[] rows = page == PAGE_ABC ? LETTERS : page == PAGE_SYM1 ? SYM1 : SYM2;

        if (page == PAGE_ABC && (layout == 1 || layout == 2)) {
            candRow = row();
            candRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(KEY_H + 4)));
            keysBox.addView(candRow);
            paintCands();
        } else {
            if (wordsOn()) buildSugRow();
            LinearLayout nums = row();
            for (String k : NUMBERS.split(" ")) nums.addView(charKey(k));
            keysBox.addView(nums);
        }

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
            onPress(shiftKey, cFn, new Runnable() { @Override public void run() { onShift(); } });
            r3.addView(shiftKey);
        } else {
            TextView pg = makeKey(page == PAGE_SYM1 ? "1/2" : "2/2", 1.5f, cFn, cInk, 15);
            onPress(pg, cFn, new Runnable() { @Override public void run() {
                page = page == PAGE_SYM1 ? PAGE_SYM2 : PAGE_SYM1; buildKeysSoon(); } });
            r3.addView(pg);
        }
        for (String k : rows[2].split(" ")) r3.addView(charKey(k));
        r3.addView(deleteKey(1.5f));
        keysBox.addView(r3);

        LinearLayout r4 = row();
        TextView pageKey = makeKey(page == PAGE_ABC ? "!#1" : "ABC", 1.5f, cFn, cInk, 15);
        onPress(pageKey, cFn, new Runnable() { @Override public void run() {
            page = page == PAGE_ABC ? PAGE_SYM1 : PAGE_ABC; buildKeysSoon(); } });
        r4.addView(pageKey);
        bottomRowRest(r4, page == PAGE_ABC);
        keysBox.addView(r4);
        paintSpace();
        updateShift();
    }

    // ---------- suggestions and auto-correct (English keys) ----------
    private boolean wordsOn() { return layout == 0 && page == PAGE_ABC && !noFix; }

    private void buildSugRow() {
        sugRow = row();
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = makeKey("", 1f, cBg, cInk, 18);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1f));
            t.setEllipsize(TextUtils.TruncateAt.END);
            onPress(t, cBg, new Runnable() { @Override public void run() { pickSug(idx); } });
            sug[i] = t;
            sugRow.addView(t);
        }
        final TextView auto = makeKey(autoFix ? "Auto \u2713" : "Auto \u2715", 0.7f, cFn, autoFix ? cInk : cMuted, 13);
        auto.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 0.7f));
        onPress(auto, cFn, new Runnable() { @Override public void run() {
            autoFix = !autoFix;
            prefs().edit().putBoolean("autoFix", autoFix).apply();
            auto.setText(autoFix ? "Auto \u2713" : "Auto \u2715");
            auto.setTextColor(autoFix ? cInk : cMuted);
            flashSpace(autoFix ? "Auto-correct on" : "Auto-correct off");
            paintSugs();
        } });
        sugRow.addView(auto);
        keysBox.addView(sugRow);
        paintSugs();
    }

    private void paintSugs() {
        if (sugRow == null) return;
        String w = word.toString();
        String[] v = {"", "", ""};
        boolean bold = false;
        sugKeepsTyped = false;
        sugSwipe = false;
        if (w.length() > 0 && Words.ready) {
            String fx = Words.fix(w);
            List<String> c = Words.complete(w, 3);
            if (fx != null) {
                v[0] = w; v[1] = fx; sugKeepsTyped = true; bold = autoFix;
                for (String x : c) if (!x.equals(fx)) { v[2] = x; break; }
            } else {
                if (c.size() > 0) v[1] = c.get(0);
                if (c.size() > 1) v[0] = c.get(1);
                if (c.size() > 2) v[2] = c.get(2);
            }
        }
        for (int i = 0; i < 3; i++) {
            sugVal[i] = v[i];
            String show = i == 0 && sugKeepsTyped ? "\u201C" + v[i] + "\u201D" : v[i];
            sug[i].setText(show);
            sug[i].setTypeface(i == 1 && bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private void resetWord() {
        fixTyped = null;
        if (word.length() == 0) return;
        word.setLength(0);
        paintSugs();
    }

    private void learn(String w) {
        Words.addOwn(w);
        prefs().edit().putString("ownWords", Words.ownAsText()).apply();
    }

    /** True when the text just before the cursor is exactly the word we tracked. */
    private boolean wordIsThere(InputConnection ic, String w) {
        CharSequence b = ic.getTextBeforeCursor(w.length() + 1, 0);
        if (b == null) return false;
        String bs = b.toString();
        return bs.endsWith(w) && (bs.length() == w.length() || !Character.isLetter(bs.charAt(0)));
    }

    private void pickSug(int i) {
        String val = sugVal[i];
        String w = word.toString();
        InputConnection ic = getCurrentInputConnection();
        if (sugSwipe) {
            // another guess for the word that was just slid
            if (val.isEmpty() || ic == null || !swipeWordIsThere(ic)) return;
            ic.beginBatchEdit();
            ic.deleteSurroundingText(swipeDone.length(), 0);
            ic.commitText(val + " ", 1);
            ic.endBatchEdit();
            swipeDone = val + " ";
            sugVal[i] = "";
            sug[i].setText("");
            return;
        }
        if (val.isEmpty() || w.isEmpty() || ic == null) return;
        boolean keep = i == 0 && sugKeepsTyped;
        if (!wordIsThere(ic, w)) { resetWord(); return; }
        ic.beginBatchEdit();
        ic.deleteSurroundingText(w.length(), 0);
        ic.commitText(val + " ", 1);
        ic.endBatchEdit();
        if (keep) learn(w);
        resetWord();
        autoShift();
    }

    /** A word just ended (space or punctuation): fix it if it is a typing mistake. */
    private void endWord(InputConnection ic) {
        String w = word.toString();
        word.setLength(0);
        fixTyped = null;
        if (w.isEmpty()) return;
        if (autoFix && Words.ready && layout == 0 && !noFix) {
            String fx = Words.fix(w);
            if (fx != null && wordIsThere(ic, w)) {
                ic.beginBatchEdit();
                ic.deleteSurroundingText(w.length(), 0);
                ic.commitText(fx, 1);
                ic.endBatchEdit();
                fixTyped = w;
                fixDone = fx;
            }
        }
        paintSugs();
    }

    /** Backspace right after an auto-correct puts the typed word back and remembers it. */
    private boolean undoFix(InputConnection ic) {
        if (fixTyped == null) return false;
        String typed = fixTyped, done = fixDone;
        fixTyped = null;
        CharSequence b = ic.getTextBeforeCursor(done.length() + 2, 0);
        if (b == null) return false;
        String bs = b.toString();
        int at = bs.lastIndexOf(done);
        if (at < 0) return false;
        String tail = bs.substring(at + done.length());
        if (tail.length() > 2) return false;
        ic.beginBatchEdit();
        ic.deleteSurroundingText(bs.length() - at, 0);
        ic.commitText(typed + tail, 1);
        ic.endBatchEdit();
        learn(typed);
        flashSpace("Kept \u201C" + typed + "\u201D");
        return true;
    }

    private void paste() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        flushPhon(false);
        resetWord();
        CharSequence txt = null;
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData d = cm == null ? null : cm.getPrimaryClip();
            if (d != null && d.getItemCount() > 0) txt = d.getItemAt(0).coerceToText(this);
        } catch (Exception ignored) { /* fall back to the app's own paste */ }
        ic.finishComposingText();
        composing = false;
        if (txt != null && txt.length() > 0) ic.commitText(txt.toString(), 1);
        else if (!ic.performContextMenuAction(android.R.id.paste)) flashSpace("Nothing copied yet");
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        // the cursor was moved by hand: forget the word we were tracking
        if (word.length() > 0 && (newSelStart != newSelEnd || Math.abs(newSelStart - oldSelStart) > 1)) {
            word.setLength(0);
            paintSugs();
        }
    }

    private TextView layoutKey() {
        TextView k = makeKey(LAYOUT_KEY[layout], 1.4f, cFn, cInk, layout == 0 ? 14 : 15);
        onPress(k, cFn, new Runnable() { @Override public void run() {
            flushPhon(false);
            layout = (layout + 1) % LAYOUT_KEY.length;
            scriptShift = false;
            prefs().edit().putInt("layout", layout).apply();
            buildKeysSoon();
            flashSpace(LAYOUT_NAME[layout]);
        } });
        return k;
    }

    /** Emoji, language, comma, space, full stop, enter. */
    private void bottomRowRest(LinearLayout r4, boolean letters) {
        r4.addView(emojiKey());
        r4.addView(layoutKey());
        if (letters) r4.addView(charKey(","));
        spaceKey = makeKey("", letters ? 5f : 6f, cKey, cMuted, 13);
        spaceKey.setEllipsize(TextUtils.TruncateAt.START);
        spaceKey.setOnTouchListener(new View.OnTouchListener() {
            private boolean held;
            private final Runnable longHold = new Runnable() {
                @Override public void run() { held = true; showPicker(); }
            };
            @Override public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    held = false;
                    face(v, pressedOf(cKey));
                    tick(v);
                    handler.postDelayed(longHold, 600);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    handler.removeCallbacks(longHold);
                    face(v, cKey);
                    if (a == MotionEvent.ACTION_UP && !held) {
                        try { typeSpace(); refreshVowelRow(); } catch (Exception ex) { saveProblem(ex); }
                    }
                }
                return true;
            }
        });
        r4.addView(spaceKey);
        r4.addView(charKey("."));
        TextView enter = makeKey("⏎", 1.5f, cAcc, cOnAcc, 20);
        onPress(enter, cAcc, new Runnable() { @Override public void run() { onEnter(); } });
        r4.addView(enter);
    }

    private void buildScriptKeys() {
        boolean hi = layout == 4;
        String[] rows = scriptShift ? (hi ? ScriptKeys.HI_SHIFT : ScriptKeys.BN_SHIFT) : (hi ? ScriptKeys.HI : ScriptKeys.BN);
        vowelKeys.clear();
        InputConnection ic0 = getCurrentInputConnection();
        vowelRowFull = !scriptShift && ScriptKeys.wantsFullVowel(ic0 == null ? null : ic0.getTextBeforeCursor(1, 0));
        for (int i = 0; i < 3; i++) {
            LinearLayout r = row();
            String keys = rows[i];
            if (i == 0 && vowelRowFull) keys = hi ? ScriptKeys.HI_VOWELS : ScriptKeys.BN_VOWELS;
            for (String k : keys.split(" ")) {
                TextView t = scriptKey(k);
                if (i == 0 && !scriptShift) vowelKeys.add(t);
                r.addView(t);
            }
            keysBox.addView(r);
        }
        LinearLayout r3 = row();
        TextView sh = makeKey(scriptShift ? "\u21E7" : "\u21E7", 1.4f, scriptShift ? cKey : cFn, scriptShift ? cAcc : cInk, 20);
        onPress(sh, scriptShift ? cKey : cFn, new Runnable() { @Override public void run() {
            scriptShift = !scriptShift; buildKeysSoon(); } });
        r3.addView(sh);
        for (String k : rows[3].split(" ")) r3.addView(scriptKey(k));
        r3.addView(deleteKey(1.4f));
        keysBox.addView(r3);

        LinearLayout r4 = row();
        TextView pageKey = makeKey("!#1", 1.5f, cFn, cInk, 15);
        onPress(pageKey, cFn, new Runnable() { @Override public void run() { page = PAGE_SYM1; buildKeysSoon(); } });
        r4.addView(pageKey);
        bottomRowRest(r4, false);
        keysBox.addView(r4);
        paintSpace();
    }

    private TextView scriptKey(final String ch0) {
        final TextView t = makeKey(ch0, 1f, cKey, cInk, 28);
        onPress(t, cKey, new Runnable() { @Override public void run() {
            String ch = t.getText().toString();
            if ("।".equals(ch) || ",".equals(ch) || "?".equals(ch) || "!".equals(ch)) typeMark(ch);
            else commit(ch);
            refreshVowelRow();
        } });
        return t;
    }

    /** Full vowels at the start of a syllable, vowel signs after a consonant. */
    private void refreshVowelRow() {
        if (layout < 3 || page != PAGE_ABC || scriptShift || vowelKeys.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        boolean full = ScriptKeys.wantsFullVowel(ic == null ? null : ic.getTextBeforeCursor(1, 0));
        if (full == vowelRowFull) return;
        vowelRowFull = full;
        boolean hi = layout == 4;
        String[] keys = (full ? (hi ? ScriptKeys.HI_VOWELS : ScriptKeys.BN_VOWELS)
                : (hi ? ScriptKeys.HI[0] : ScriptKeys.BN[0])).split(" ");
        for (int i = 0; i < vowelKeys.size() && i < keys.length; i++) vowelKeys.get(i).setText(keys[i]);
    }

    // ---------- type English, get script ----------
    private boolean phonetic() { return page == PAGE_ABC && (layout == 1 || layout == 2); }

    /** Put the word being typed into the text box, using the top suggestion if we have one. */
    private void flushPhon(boolean useSuggestion) {
        if (phon.length() == 0) return;
        InputConnection ic = getCurrentInputConnection();
        String word = phon.toString();
        String out = useSuggestion && word.equals(candFor) && !cands.isEmpty() ? cands.get(0) : word;
        phon.setLength(0);
        cands = new ArrayList<>();
        candFor = "";
        candJob++;
        if (ic != null) ic.commitText(out, 1);
        paintCands();
    }

    private void pickCand(String c) {
        InputConnection ic = getCurrentInputConnection();
        phon.setLength(0);
        cands = new ArrayList<>();
        candFor = "";
        candJob++;
        if (ic != null) ic.commitText(c + " ", 1);
        paintCands();
    }

    private void typePhon(String letter) {
        phon.append(letter);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.setComposingText(phon.toString(), 1);
        askSuggestions();
    }

    private void askSuggestions() {
        final int job = ++candJob;
        final String word = phon.toString();
        final boolean hi = layout == 2;
        if (word.isEmpty()) { paintCands(); return; }
        handler.removeCallbacks(candAsk);
        candAsk = new Runnable() { @Override public void run() { fetchSuggestions(job, word, hi); } };
        handler.postDelayed(candAsk, 140);
    }

    private Runnable candAsk;

    private void fetchSuggestions(final int job, final String word, final boolean hi) {
        if (job != candJob) return;
        new Thread(new Runnable() { @Override public void run() {
            List<String> got;
            try { got = Phonetic.suggest(word, hi); } catch (Throwable t) { got = null; }
            final List<String> fin = got;
            handler.post(new Runnable() { @Override public void run() {
                if (job != candJob) return;
                if (fin == null) { flashSpace("No internet: English letters kept"); return; }
                cands = fin;
                candFor = word;
                paintCands();
            } });
        } }).start();
    }

    private void paintCands() {
        if (candRow == null) return;
        candRow.removeAllViews();
        if (phon.length() == 0) {
            TextView hint = new TextView(this);
            hint.setText(layout == 2 ? "Type in English letters: main \u2192 \u092e\u0948\u0902" : "Type in English letters: ami \u2192 \u0986\u09ae\u09bf");
            hint.setTextColor(cMuted);
            hint.setTextSize(14);
            hint.setGravity(Gravity.CENTER);
            candRow.addView(hint, new LinearLayout.LayoutParams(0, dp(KEY_H + 4), 1f));
            return;
        }
        List<String> show = new ArrayList<>(cands);
        show.add(phon.toString());
        for (int i = 0; i < show.size(); i++) {
            final String c = show.get(i);
            boolean top = i == 0;
            TextView t = makeKey(c, 1f, top ? cFn : cKey, cInk, 21);
            if (top) t.setTypeface(Typeface.DEFAULT_BOLD);
            onPress(t, top ? cFn : cKey, new Runnable() { @Override public void run() { pickCand(c); } });
            candRow.addView(t);
        }
    }

    private TextView emojiKey() {
        TextView e = makeKey("🙂", 1f, cFn, cInk, 19);
        onPress(e, cFn, new Runnable() { @Override public void run() { page = PAGE_EMOJI; buildKeysSoon(); } });
        return e;
    }

    private TextView deleteKey(float weight) {
        TextView del = makeKey("⌫", weight, cFn, cInk, 20);
        del.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    face(v, pressedOf(cFn));
                    tick(v);
                    try { deleteOne(); refreshVowelRow(); } catch (Exception ex) { saveProblem(ex); }
                    handler.postDelayed(repeatDelete, 400);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    face(v, cFn);
                    handler.removeCallbacks(repeatDelete);
                }
                return true;
            }
        });
        return del;
    }

    private TextView charKey(final String ch) {
        final TextView t = makeKey(ch, 1f, cKey, cInk, 26);
        final boolean letter = page == PAGE_ABC && ch.length() == 1 && Character.isLetter(ch.charAt(0));
        if (letter) letterKeys.add(t);
        onPress(t, cKey, new Runnable() { @Override public void run() {
            String out = t.getText().toString();
            if (".".equals(ch) || ",".equals(ch) || "?".equals(ch) || "!".equals(ch)) typeMark(out);
            else commit(out);
            if (letter && shift && !capsLock) { shift = false; updateShift(); }
        } });
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
                @Override public void onClick(View v) {
                    tick(v);
                    try { commit(em); addRecent(em); } catch (Exception ex) { saveProblem(ex); }
                }
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
        onPress(abc, cFn, new Runnable() { @Override public void run() { page = PAGE_ABC; buildKeysSoon(); } });
        tabs.addView(abc);
        for (int i = 0; i < Emojis.TAB_ICONS.length; i++) {
            final int tab = i;
            TextView t = makeKey(Emojis.TAB_ICONS[i], 1f, i == emojiTab ? cFn : cKey, cInk, 19);
            final int f = i == emojiTab ? cFn : cKey;
            onPress(t, f, new Runnable() { @Override public void run() { emojiTab = tab; buildKeysSoon(); } });
            tabs.addView(t);
        }
        tabs.addView(deleteKey(1.5f));
        keysBox.addView(tabs);
    }

    // ---------- converter panel ----------
    /** The text to convert: the selection if there is one, otherwise the whole message. */
    private String currentText(boolean[] isSelection) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) { isSelection[0] = true; return sel.toString(); }
        isSelection[0] = false;
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        if (et != null && et.text != null) return et.text.toString();
        CharSequence b = ic.getTextBeforeCursor(4000, 0);
        CharSequence a = ic.getTextAfterCursor(4000, 0);
        return (b == null ? "" : b.toString()) + (a == null ? "" : a.toString());
    }

    private void replaceText(String result) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        boolean[] isSel = new boolean[1];
        String cur = currentText(isSel);
        ic.beginBatchEdit();
        if (!isSel[0]) {
            ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
            int len = et != null && et.text != null ? et.text.length() : cur.length();
            ic.setSelection(0, len);
        }
        ic.commitText(result, 1);
        ic.endBatchEdit();
    }

    private TextView panelBox(String label, String body, boolean strong) {
        TextView t = new TextView(this);
        t.setText(label + "\n" + body);
        t.setTextColor(cInk);
        t.setTextSize(15);
        if (strong) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setMaxLines(4);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(10), dp(6), dp(10), dp(6));
        t.setBackground(keyFace(cKey));
        return t;
    }

    private void buildConverter() {
        boolean[] isSel = new boolean[1];
        final String src = currentText(isSel).trim();

        for (int rIdx = 0; rIdx < 2; rIdx++) {
            LinearLayout r = row();
            for (int c = 0; c < 2; c++) {
                final int idx = rIdx * 2 + c;
                boolean on = idx == convOption;
                final int fill = on ? cInk : cKey;
                TextView o = makeKey(CONV_NAMES[idx], 1f, fill, on ? cKey : cInk, 14);
                if (on) o.setTypeface(Typeface.DEFAULT_BOLD);
                onPress(o, fill, new Runnable() { @Override public void run() {
                    convOption = idx; convResult = null; buildKeysSoon(); } });
                r.addView(o);
            }
            keysBox.addView(r);
        }

        String label = isSel[0] ? "Selected text" : "Your text";
        TextView srcBox = panelBox(label, src.isEmpty() ? "Type or speak a message first" : src, false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(KEY_H + 20));
        keysBox.addView(srcBox, slp);

        final TextView resBox = panelBox("Result", convResult != null ? convResult
                : src.isEmpty() ? "" : "Working\u2026", true);
        convResBox = resBox;
        keysBox.addView(resBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(KEY_H + 20)));

        LinearLayout act = row();
        TextView back = makeKey("Back to keys", 1f, cFn, cInk, 14);
        onPress(back, cFn, new Runnable() { @Override public void run() { page = PAGE_ABC; buildKeysSoon(); } });
        act.addView(back);
        final TextView use = makeKey("Replace my text", 1f, cAcc, cOnAcc, 14);
        onPress(use, cAcc, new Runnable() { @Override public void run() {
            if (convResult == null || convResult.isEmpty()) return;
            if (convResult.startsWith("\u26A0")) { convResult = null; buildKeysSoon(); return; }
            String out = convResult;
            if (convOption == 3) {
                // units: add the answer after the message instead of replacing it
                commit((src.endsWith(" ") ? "" : " ") + "(" + out + ")");
            } else {
                replaceText(out);
            }
            page = PAGE_ABC; buildKeysSoon();
        } });
        if (convOption == 3) use.setText("Add result");
        if (convResult != null && convResult.startsWith("\u26A0")) use.setText("Try again");
        act.addView(use);
        keysBox.addView(act);

        if (src.isEmpty() || convResult != null) return;
        runConversion(src, resBox);
    }

    private void showResult(final int job, final String text) {
        handler.post(new Runnable() { @Override public void run() {
            if (job != convJob || page != PAGE_CONVERT) return;
            convResult = text;
            buildKeys();
        } });
    }

    private void runConversion(final String src, TextView resBox) {
        final int job = ++convJob;
        final int opt = convOption;
        final String key = prefs().getString("aikey", "");
        if (opt == 2) { showResult(job, Converter.toEnglishLetters(src)); return; }
        if (opt == 3 && !Converter.hasCurrency(src)) {
            String r = Converter.units(src, null);
            showResult(job, r.isEmpty() ? "\u26A0 No amounts found. Try \"5 kg\" or \"\u20B9500\"" : r);
            return;
        }
        convStarted = System.currentTimeMillis();
        Converter.stage = "starting";
        handler.postDelayed(new Runnable() { @Override public void run() {
            if (job != convJob || convResult != null || page != PAGE_CONVERT || convResBox == null) return;
            long secs = (System.currentTimeMillis() - convStarted) / 1000;
            if (secs >= 30) {
                convJob++;
                Converter.cancel();
                convResult = "\u26A0 Took too long while " + Converter.stage + ". Tap Try again";
                buildKeys();
                return;
            }
            convResBox.setText("Result\nWorking\u2026 " + secs + "s (" + Converter.stage + ")");
            handler.postDelayed(this, 1000);
        } }, 1000);
        new Thread(new Runnable() { @Override public void run() {
            String out;
            try {
                if (opt == 0) out = Converter.translateFree(src, mode, key);
                else if (opt == 1) out = Converter.romanToScript(src, false);
                else {
                    String r = Converter.units(src, Converter.usdRates());
                    out = r.isEmpty() ? "\u26A0 No amounts found. Try \"5 kg\" or \"\u20B9500\"" : r;
                }
            } catch (Converter.UserMessage um) {
                out = "\u26A0 " + um.getMessage();
            } catch (java.net.SocketTimeoutException to) {
                out = "\u26A0 The AI took too long. Tap Try again";
            } catch (java.net.UnknownHostException uh) {
                out = "\u26A0 No internet. Check your connection";
            } catch (java.io.IOException io) {
                saveProblem(io);
                out = "\u26A0 Connection problem: " + io.getClass().getSimpleName();
            } catch (Throwable e) {
                saveProblem(e);
                out = "\u26A0 Something went wrong (" + e.getClass().getSimpleName() + "). Tap Try again";
            }
            showResult(job, out);
        } }).start();
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
        if (phonetic() && s.length() == 1 && Character.isLetter(s.charAt(0)) && s.charAt(0) < 128) {
            typePhon(s); return;
        }
        if (composing) { ic.finishComposingText(); composing = false; }
        swipeDone = null;
        if (wordsOn() && s.length() == 1 && (Character.isLetter(s.charAt(0)) || (s.charAt(0) == '\'' && word.length() > 0))) {
            ic.commitText(s, 1);
            word.append(s);
            fixTyped = null;
            paintSugs();
            return;
        }
        flushPhon(true);
        if (" ".equals(s)) endWord(ic); else resetWord();
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
        flushPhon(true);
        ic.finishComposingText();
        composing = false;
        endWord(ic);
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        if (before != null && before.length() == 1 && before.charAt(0) == ' ') ic.deleteSurroundingText(1, 0);
        ic.commitText(m + " ", 1);
        if (page != PAGE_ABC) { page = PAGE_ABC; buildKeysSoon(); }
        autoShift();
    }

    private void deleteOne() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (phon.length() > 0) {
            phon.setLength(phon.length() - 1);
            ic.setComposingText(phon.toString(), 1);
            if (phon.length() == 0) { ic.finishComposingText(); candJob++; cands = new ArrayList<>(); paintCands(); }
            else askSuggestions();
            return;
        }
        if (composing) { ic.finishComposingText(); composing = false; }
        if (undoFix(ic)) return;
        if (swipeDone != null) {
            boolean there = swipeWordIsThere(ic);
            int len = swipeDone.length();
            swipeDone = null;
            if (there) { ic.deleteSurroundingText(len, 0); word.setLength(0); paintSugs(); autoShift(); return; }
        }
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) { ic.commitText("", 1); resetWord(); return; }
        CharSequence b = ic.getTextBeforeCursor(26, 0);
        int n = b == null ? 0 : b.length();
        boolean pair = n >= 2 && Character.isSurrogatePair(b.charAt(n - 2), b.charAt(n - 1));
        if (pair) ic.deleteSurroundingText(2, 0);
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        if (wordsOn()) {
            // keep following the word that is left before the cursor
            int end = Math.max(0, n - (pair ? 2 : 1)), st = end;
            while (st > 0 && (Character.isLetter(b.charAt(st - 1)) || b.charAt(st - 1) == '\'')) st--;
            word.setLength(0);
            if (end - st < 25 && st < end) word.append(b, st, end);
            paintSugs();
        }
    }

    private void onEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        flushPhon(true);
        ic.finishComposingText();
        composing = false;
        endWord(ic);
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
            t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, up ? letterDp * 0.9f : letterDp);
        }
        if (shiftKey != null) {
            shiftKey.setText(capsLock ? "⇪" : "⇧");
            face(shiftKey, up ? cKey : cFn);
            shiftKey.setTextColor(up ? cAcc : cInk);
        }
    }

    private void showPicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        phon.setLength(0);
        cands = new ArrayList<>();
        word.setLength(0);
        fixTyped = null;
        int cls = info.inputType & EditorInfo.TYPE_MASK_CLASS;
        int var = info.inputType & EditorInfo.TYPE_MASK_VARIATION;
        boolean wasNoFix = noFix;
        noFix = cls != EditorInfo.TYPE_CLASS_TEXT || var == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                || var == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD || var == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || var == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || var == EditorInfo.TYPE_TEXT_VARIATION_URI;
        if (keysBox == null) return;
        capsLock = false;
        int want = (cls == EditorInfo.TYPE_CLASS_NUMBER || cls == EditorInfo.TYPE_CLASS_PHONE) ? PAGE_SYM1 : PAGE_ABC;
        if (page != want || wasNoFix != noFix) { page = want; buildKeys(); }
        paintSugs();
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
        String s = spaceText != null ? spaceText : listening ? "Listening…" : "Bolchi";
        spaceKey.setText(s);
    }

    private void toggleVoiceScript() {
        int i = mode == MODE_BN ? 0 : 1;
        voiceScript[i] = !voiceScript[i];
        prefs().edit().putBoolean(i == 0 ? "bnScript" : "hiScript", voiceScript[i]).apply();
        paintChips();
        flashSpace(i == 0 ? (voiceScript[0] ? "Speak \u2192 \u09ac\u09be\u0982\u09b2\u09be script" : "Speak \u2192 Banglish")
                : (voiceScript[1] ? "Speak \u2192 \u0939\u093f\u0902\u0926\u0940 script" : "Speak \u2192 Hinglish"));
    }

    private void paintChips() {
        if (chips.size() == 3) {
            chips.get(0).setText(voiceScript[0] ? "\u09ac\u09be\u0982\u09b2\u09be" : "Bangla");
            chips.get(1).setText(voiceScript[1] ? "\u0939\u093f\u0902\u0926\u0940" : "Hindi");
        }
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
        mic.setTextColor(listening ? cKey : cOnAcc);
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
        if (mode == MODE_BN) return voiceScript[0] ? heard : Translit.toBanglish(heard);
        if (mode == MODE_HI) return voiceScript[1] ? heard : HindiTranslit.toHinglish(heard);
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
        flushPhon(true);
        listening = true;
        spokenAll.setLength(0);
        lastPartial = "";
        tidyPendOut = null;
        tidyDone = "";
        tidyFailed = false;
        tidyJob++;
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
            if (composing) spokenAll.append(lastPartial);
        }
        composing = false;
        lastPartial = "";
        paintMic();
        if (message != null) flashSpace(message); else paintSpace();
        if (was) { tidyApply(); tidyAsk(); }
    }

    /**
     * While speaking, the AI (if a key is saved) keeps tidying what was said so far: it keeps the
     * corrected version when she changes her mind, drops fumbles and adds punctuation. No need to tap Stop.
     */
    private void tidyAsk() {
        if (tidyBusy || tidyFailed) return;
        final String said = spokenAll.toString();
        final String key = prefs().getString("aikey", "");
        if (key.trim().isEmpty() || said.equals(tidyDone) || said.trim().split("\\s+").length < 3) return;
        tidyBusy = true;
        final int job = tidyJob;
        new Thread(new Runnable() { @Override public void run() {
            String got;
            try { got = Converter.tidy(said.trim(), key); } catch (Throwable t) { got = null; }
            final String out = got;
            handler.post(new Runnable() { @Override public void run() {
                tidyBusy = false;
                if (job != tidyJob) return;
                if (out == null || out.isEmpty()) {
                    tidyFailed = true;
                    flashSpace("AI did not connect: kept as spoken");
                    return;
                }
                tidyPendSaid = said;
                tidyPendOut = out;
                if (!composing) tidyApply();
            } });
        } }).start();
    }

    /** Puts the AI's tidy text in place of what was spoken, keeping anything said after it. */
    private void tidyApply() {
        if (tidyPendOut == null) return;
        String said = tidyPendSaid, out = tidyPendOut;
        tidyPendOut = null;
        String all = spokenAll.toString();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || !all.startsWith(said)) return;
        CharSequence b = ic.getTextBeforeCursor(all.length(), 0);
        if (b == null || !b.toString().equals(all)) { spokenAll.setLength(0); return; }   // she edited by hand: leave it
        String clean = (said.startsWith(" ") ? " " : "") + out;
        String neu = clean + all.substring(said.length());
        if (!neu.equals(all)) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(all.length(), 0);
            ic.commitText(neu, 1);
            ic.endBatchEdit();
        }
        spokenAll.setLength(0);
        if (neu.length() < 1200) spokenAll.append(neu);   // long talk: start a fresh stretch
        tidyDone = clean;
        if (!all.equals(said)) tidyAsk();
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
        lastPartial = roundLead + convert(heard);
        ic.setComposingText(lastPartial, 1);
    }

    @Override
    public void onResults(Bundle results) {
        if (!listening) return;
        String heard = Spoken.fix(first(results));
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            if (!heard.isEmpty()) {
                if (!composing) roundLead = lead();
                ic.setComposingText(roundLead + convert(heard), 1);
                spokenAll.append(roundLead).append(convert(heard));
            }
            ic.finishComposingText();
        }
        composing = false;
        lastPartial = "";
        tidyApply();
        tidyAsk();
        failRounds = 0;
        handler.postDelayed(nextRound, 60);
    }

    @Override
    public void onError(int error) {
        if (!listening) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
        if (composing) spokenAll.append(lastPartial);
        composing = false;
        lastPartial = "";
        tidyApply();
        tidyAsk();
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
