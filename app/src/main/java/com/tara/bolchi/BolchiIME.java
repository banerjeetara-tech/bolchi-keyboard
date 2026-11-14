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
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.Size;
import android.view.inputmethod.InlineSuggestion;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.view.inputmethod.InlineSuggestionsResponse;
import android.widget.inline.InlinePresentationSpec;
import android.widget.inline.InlineContentView;
import android.os.Build;
import android.content.ClipDescription;
import android.graphics.Bitmap;
import android.view.inputmethod.InputContentInfo;
import android.widget.ImageView;
import android.net.Uri;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.HorizontalScrollView;
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

    private static final int PAGE_ABC = 0, PAGE_SYM1 = 1, PAGE_SYM2 = 2, PAGE_EMOJI = 3, PAGE_CONVERT = 4, PAGE_THEMES = 5;
    private static final String[] CONV_NAMES = {"Translate to English", "To Bangla script", "To English letters", "Units and currency", "Fix my grammar", "Rewrite it better", "To Hindi script", "Translate to Hindi", "Bold", "Italic", "Bold italic", "Font style"};

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
    private boolean googleFailed;
    private boolean composing;
    private String roundLead = "";
    private final StringBuilder spokenAll = new StringBuilder();   // everything written in this Speak session
    private String lastPartial = "";
    private int tidyJob;
    private boolean tidyBusy, tidyFailed;
    private boolean aiTidy;   // off unless she turns it on by holding Speak
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
    private int typeStyle = -1;   // a font chosen in the converter: new English typing comes out in it (-1 = normal)
    private int fontStyle = 3;   // the style chosen under "Font style" (starts on Script)
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
    private final boolean[] slotEmoji = new boolean[3];   // this suggestion slot holds an emoji for the typed word
    private boolean sugKeepsTyped;
    private String[] myWords = new String[0];   // her own entries (email, name) from the Bolchi app
    private boolean autoFix = true;
    private boolean noFix;        // password box: no suggestions at all
    private boolean plainField;   // email / web address / number box: only her own saved words are offered
    private final StringBuilder token = new StringBuilder();   // everything typed since the last space (to spot an email)
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
        ic.deleteSurroundingText(typeStyle >= 0 ? FontStyles.apply(typeStyle, swFirst).length() : 1, 0);   // the letter typed when the finger touched down
        CharSequence b = ic.getTextBeforeCursor(1, 0);
        boolean glue = b == null || b.length() == 0 || Character.isWhitespace(b.charAt(0)) || "([{\"'".indexOf(b.charAt(0)) >= 0;
        ic.commitText((glue ? "" : " ") + (typeStyle >= 0 ? FontStyles.apply(typeStyle, w) : w) + " ", 1);
        ic.endBatchEdit();
        word.setLength(0);
        fixTyped = null;
        swipeDone = w + " ";
        if (sugRow != null) {
            sugKeepsTyped = false;
            sugSwipe = true;
            slotEmoji[0] = slotEmoji[1] = slotEmoji[2] = false;
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
        aiTidy = p.getBoolean("aiFix", true);
        theme = p.getInt("theme", 0);
        // find the theme she picked by its name, so removing the dark themes does not change it
        String tn = p.getString("themeName", null);
        if (tn == null && theme >= 0 && theme < Themes.OLD.length) tn = Themes.OLD[theme];
        int at = tn == null ? -1 : Themes.indexOf(tn);
        theme = at >= 0 ? at : 0;
        loadColors();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        root.setPadding(0, 0, 0, dp(6));

        smsRow = new LinearLayout(this);
        smsRow.setOrientation(LinearLayout.HORIZONTAL);
        smsRow.setPadding(dp(3), dp(4), dp(3), 0);
        smsRow.setVisibility(View.GONE);
        root.addView(smsRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        gifScroll = new HorizontalScrollView(this);
        gifScroll.setHorizontalScrollBarEnabled(false);
        gifScroll.setPadding(dp(3), 0, dp(3), 0);
        gifScroll.setVisibility(View.GONE);
        gifBox = new LinearLayout(this);
        gifBox.setOrientation(LinearLayout.HORIZONTAL);
        gifScroll.addView(gifBox, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(gifScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        contactScroll = new HorizontalScrollView(this);
        contactScroll.setHorizontalScrollBarEnabled(false);
        contactScroll.setPadding(dp(3), 0, dp(3), 0);
        contactScroll.setVisibility(View.GONE);
        contactBox = new LinearLayout(this);
        contactBox.setOrientation(LinearLayout.HORIZONTAL);
        contactScroll.addView(contactBox, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(contactScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        otpRow = new LinearLayout(this);
        otpRow.setOrientation(LinearLayout.HORIZONTAL);
        otpRow.setGravity(Gravity.CENTER_VERTICAL);
        otpRow.setPadding(dp(3), dp(4), dp(3), 0);
        otpRow.setVisibility(View.GONE);
        root.addView(otpRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

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
            @Override public void onClick(View v) {
                tick(v);
                if (listening) stopListening(null);
                page = page == PAGE_THEMES ? PAGE_ABC : PAGE_THEMES;     // tap: all colours as squares
                buildKeysSoon();
            }
        });
        pal.setOnLongClickListener(new View.OnLongClickListener() {      // hold: the next colour
            @Override public boolean onLongClick(View v) { tick(v); nextTheme(); return true; }
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
        // hold the converter button: straight to "Fix my grammar"
        convBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                tick(v);
                if (listening) stopListening(null);
                convOption = 4;
                page = PAGE_CONVERT;
                convResult = null;
                buildKeysSoon();
                return true;
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
        mic.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                tick(v);
                aiTidy = !aiTidy;
                prefs().edit().putBoolean("aiFix", aiTidy).apply();
                flashSpace(aiTidy ? "AI fix ON (hold Speak to turn off)" : "AI fix OFF");
                return true;
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
        Words.importUsage(prefs().getString("usage", ""));
        if (!prefs().contains("keptWords")) {                      // first run of this version: the words she already kept stay kept
            for (String w : prefs().getString("ownWords", "").split(" ")) Words.addKept(w);
            prefs().edit().putString("keptWords", Words.keptAsText()).apply();
        } else {
            for (String w : prefs().getString("keptWords", "").split(" ")) Words.addKept(w);
        }
        smsWatch = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                if ("smsCode".equals(key)) handler.post(new Runnable() { @Override public void run() {
                    showSmsCode(isInputViewShown());
                } });
            }
        };
        prefs().registerOnSharedPreferenceChangeListener(smsWatch);
        if (!Words.ready) new Thread(new Runnable() { @Override public void run() {
            try { Words.load(getAssets().open("words.txt")); } catch (Throwable t) { saveProblem(t); }
            try { EmojiHints.load(getAssets().open("emoji.txt")); } catch (Throwable t) { saveProblem(t); }
        } }).start();
    }

    private void buildKeys() {
        hideGifs();
        keysBox.removeAllViews();
        keyViews.clear();
        directTouch = page != PAGE_EMOJI && page != PAGE_CONVERT && page != PAGE_THEMES;
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
        if (page == PAGE_THEMES) { buildThemes(); return; }

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
            if (wordsOn() || numberStrip()) buildSugRow();
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

    /** Shows the full phone book names that match the word being typed, as a row she can scroll. */
    private void paintContacts(String w) {
        if (contactScroll == null) return;
        contactBox.removeAllViews();
        List<String> names = (layout == 0 && wordsOn() && !plainField && nameLike(w))
                ? Words.contactNamesFor(w, 10) : new ArrayList<String>();
        if (names.isEmpty()) {
            contactScroll.setVisibility(View.GONE);
            if (sugRow != null && (gifScroll == null || gifScroll.getVisibility() != View.VISIBLE)) sugRow.setVisibility(View.VISIBLE);
            return;
        }
        for (final String n : names) {
            TextView t = new TextView(this);
            t.setText(n);
            t.setTextColor(cInk);
            t.setTextSize(15);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setMaxWidth(dp(250));
            t.setPadding(dp(14), 0, dp(14), 0);
            t.setBackground(box(cKey, 12));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { tick(v); pickContact(n); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
            lp.setMargins(0, 0, dp(4), 0);
            contactBox.addView(t, lp);
        }
        contactScroll.scrollTo(0, 0);
        contactScroll.setVisibility(View.VISIBLE);
        if (sugRow != null) sugRow.setVisibility(View.GONE);   // the names take the place of the word row: the keyboard keeps its height
    }

    /**
     * The phone book names take over the word row only when English predictions have nothing to offer:
     * no dictionary word starts with the letters, or the word has a capital letter in the middle of a sentence (a name).
     */
    private boolean nameLike(String w) {
        if (w.length() < 3) return false;
        if (!Words.hasDictionaryStart(w)) return true;
        if (!Character.isUpperCase(w.charAt(0))) return false;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        CharSequence b = ic.getTextBeforeCursor(w.length() + 3, 0);
        if (b == null || b.length() <= w.length()) return false;
        String before = b.subSequence(0, b.length() - w.length()).toString().trim();
        return !before.isEmpty() && ".!?\n".indexOf(before.charAt(before.length() - 1)) < 0;
    }

    /** Puts only the name she was typing (e.g. "Rituparna" from "Emami Rituparna") in place of the typed letters. */
    private void pickContact(String name) {
        InputConnection ic = getCurrentInputConnection();
        String w = word.toString();
        if (ic != null && !w.isEmpty() && wordIsThere(ic, w)) {
            String pick = name;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[A-Za-z]+").matcher(name);
            while (m.find()) { if (m.group().toLowerCase().startsWith(w.toLowerCase())) { pick = m.group(); break; } }
            ic.beginBatchEdit();
            ic.deleteSurroundingText(shown(w).length(), 0);
            ic.commitText(shown(pick + " "), 1);
            ic.endBatchEdit();
        }
        fixTyped = null;
        word.setLength(0);
        paintSugs();
        autoShift();
    }

    private void paintSugs() {
        if (sugRow == null) { if (contactScroll != null) contactScroll.setVisibility(View.GONE); return; }
        String w = word.toString();
        paintContacts(w);
        String[] v = {"", "", ""};
        boolean bold = false;
        sugKeepsTyped = false;
        sugSwipe = false;
        slotEmoji[0] = slotEmoji[1] = slotEmoji[2] = false;
        slotNext[0] = slotNext[1] = slotNext[2] = false;
        slotFix[0] = slotFix[1] = slotFix[2] = false;
        slotNum[0] = slotNum[1] = slotNum[2] = false;
        slotNumFill[0] = slotNumFill[1] = slotNumFill[2] = false;
        boolean[] ownSlot = new boolean[3];
        if (w.isEmpty() && fixShownTyped != null && !plainField && layout == 0) {
            // a word was just corrected: show what she typed and what it became (in bold), so she can see which is which
            v[0] = fixShownTyped; v[1] = fixShownDone;
            slotFix[0] = true; slotFix[1] = true;
            bold = true;
        } else if (w.isEmpty() && !plainField && layout == 0 && Words.ready) {
            // after a space: the words she usually writes next
            List<String> nx = Words.nextAfter(lastWord, 3);
            int[] order = {1, 0, 2};
            for (int k = 0; k < nx.size() && k < 3; k++) { v[order[k]] = nx.get(k); slotNext[order[k]] = true; }
        }
        if (w.length() > 0 && Words.ready && !plainField) {
            // BOLD in the middle = the keyboard's own correction or prediction.
            // Normal on the left / right = HER word: what she typed, or a word from her own memory.
            String fx = Words.fix(w);
            List<String> dict = Words.dictCompletions(w, 3);
            List<String> mem = Words.memoryMatches(w, 2);
            if (fx != null) {
                v[0] = w; sugKeepsTyped = true;                     // her word, exactly as typed
                v[1] = fx;                                          // the corrected word
                for (String x : mem) if (!x.equals(fx) && !x.equals(w)) { v[2] = x; break; }
                if (v[2].isEmpty()) for (String x : dict) if (!x.equals(fx)) { v[2] = x; break; }
            } else {
                if (!dict.isEmpty()) v[1] = dict.get(0);            // the predicted word
                if (!mem.isEmpty()) v[0] = mem.get(0);              // her word from memory
                else if (dict.size() > 1) v[0] = dict.get(1);
                if (mem.size() > 1) v[2] = mem.get(1);
                else if (dict.size() > 2) v[2] = dict.get(2);
            }
            bold = !v[1].isEmpty();
        }
        if (w.length() >= 2) {
            String lw = w.toLowerCase();
            int[] slot = plainField || !sugKeepsTyped ? new int[]{2, 0, 1} : new int[]{2};
            if (plainField) slot = new int[]{1, 0, 2};
            int used = 0;
            for (String m : myWords) {
                if (used >= slot.length) break;
                if (m.length() > w.length() && m.toLowerCase().startsWith(lw)) { ownSlot[slot[used]] = true; v[slot[used++]] = m; }
            }
        }
        // she is typing digits and they begin one of her numbers: offer the whole number
        if (numTyped.length() >= 3) {
            List<String> nums = numbersStartingWith(numTyped.toString());
            int[] order = {1, 0, 2};
            for (int k = 0; k < nums.size() && k < 3; k++) { v[order[k]] = nums.get(k); slotNumFill[order[k]] = true; ownSlot[order[k]] = true; }
        }
        // she typed "phone", "mobile", "number"...: offer her own phone numbers
        if (!plainField && !sugKeepsTyped && NUM_WORDS.contains(w.toLowerCase())) {
            List<String> nums = allMyNumbers();
            int[] order = {2, 0, 1};
            for (int k = 0; k < nums.size() && k < 3; k++) { v[order[k]] = nums.get(k); slotNum[order[k]] = true; ownSlot[order[k]] = true; }
        }
        // a typed word that has emojis (not a typo being fixed): offer them, never over her saved words
        if (!plainField && !sugKeepsTyped && w.length() >= 3) {
            String[] em = EmojiHints.get(w);
            if (em != null) {
                int[] order = {2, 0};
                for (int k = 0; k < order.length && k < em.length; k++) {
                    if (ownSlot[order[k]]) continue;
                    v[order[k]] = em[k];
                    slotEmoji[order[k]] = true;
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            sugVal[i] = v[i];
            String show = i == 0 && (sugKeepsTyped || slotFix[0]) ? "\u201C" + v[i] + "\u201D" : v[i];
            sug[i].setText(show);
            sug[i].setTypeface(i == 1 && bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        maybeGifs(w);
    }

    private void resetWord() {
        fixTyped = null;
        if (word.length() == 0) return;
        word.setLength(0);
        paintSugs();
    }

    /** She kept her own spelling on purpose: never flag this word again. */
    private void keep(String w) {
        Words.addKept(w);
        Words.addOwn(w);
        prefs().edit().putString("keptWords", Words.keptAsText()).putString("ownWords", Words.ownAsText()).apply();
    }

    private void learn(String w) {
        Words.addOwn(w);
        prefs().edit().putString("ownWords", Words.ownAsText()).apply();
    }

    /** How a word is written in the text: in the chosen font when font mode is on (letters are tracked plain, the text is styled). */
    private String shown(String raw) {
        return typeStyle >= 0 && typeStyle != 20 ? FontStyles.apply(typeStyle, raw) : raw;
    }

    /** True when the text just before the cursor is exactly the word we tracked. */
    private boolean wordIsThere(InputConnection ic, String w) {
        String sw = shown(w);
        CharSequence b = ic.getTextBeforeCursor(sw.length() + 1, 0);
        if (b == null) return false;
        String bs = b.toString();
        return bs.endsWith(sw) && (bs.length() == sw.length() || !Character.isLetter(bs.charAt(0)));
    }

    private void pickSug(int i) {
        String val = sugVal[i];
        String w = word.toString();
        InputConnection ic = getCurrentInputConnection();
        if (slotFix[i] && ic != null && fixShownDone != null) {
            String done = fixShownDone, typed = fixShownTyped;
            if (fixPrompt) {
                if (i == 0) keep(typed);     // she means her spelling: never flag it again
                else {                       // she wants the right word: swap it in
                    String st = shown(typed), sd = shown(done), sp = shown(" ");
                    CharSequence b = ic.getTextBeforeCursor(st.length() + sp.length(), 0);
                    String bs = b == null ? "" : b.toString();
                    if (bs.endsWith(st + sp)) { ic.deleteSurroundingText(st.length() + sp.length(), 0); ic.commitText(sd + sp, 1); }
                    else if (bs.endsWith(st)) { ic.deleteSurroundingText(st.length(), 0); ic.commitText(sd, 1); }
                }
            } else if (i == 0) {             // tap her original word: put it back and remember it
                String st = shown(typed), sd = shown(done), sp = shown(" ");
                CharSequence b = ic.getTextBeforeCursor(sd.length() + sp.length(), 0);
                String bs = b == null ? "" : b.toString();
                if (bs.endsWith(sd + sp)) { ic.deleteSurroundingText(sd.length() + sp.length(), 0); ic.commitText(st + sp, 1); keep(typed); }
                else if (bs.endsWith(sd)) { ic.deleteSurroundingText(sd.length(), 0); ic.commitText(st, 1); keep(typed); }
            }
            fixShownTyped = fixShownDone = null;
            paintSugs();
            return;
        }
        if (slotNumFill[i] && !val.isEmpty() && ic != null) {
            String typedDigits = shown(numTyped.toString());              // the digits as they sit in the text (styled in font mode)
            int n = typedDigits.length();
            CharSequence b = ic.getTextBeforeCursor(n, 0);
            boolean numberBox = fieldClass == EditorInfo.TYPE_CLASS_NUMBER || fieldClass == EditorInfo.TYPE_CLASS_PHONE;
            String out = numberBox ? val.replaceAll("[^0-9+]", "") : shown(val);
            if (b != null && b.toString().equals(typedDigits)) {
                ic.beginBatchEdit();
                ic.deleteSurroundingText(n, 0);
                ic.commitText(out, 1);
                ic.endBatchEdit();
            }
            numTyped.setLength(0);
            paintSugs();
            return;
        }
        if (slotNum[i] && !val.isEmpty() && ic != null) {
            if (wordIsThere(ic, w)) ic.commitText(" " + val + " ", 1);
            resetWord();
            return;
        }
        if (slotNext[i] && !val.isEmpty() && ic != null) {
            String out = (shift || capsLock) ? Character.toUpperCase(val.charAt(0)) + val.substring(1) : val;
            if (val.equals("i")) out = "I";
            ic.commitText(shown(out + " "), 1);
            Words.record(lastWord, val);
            lastWord = val;
            if (++usageEdits >= 25) saveUsage();
            if (shift && !capsLock) { shift = false; updateShift(); }
            paintSugs();
            autoShift();
            return;
        }
        if (slotEmoji[i] && !val.isEmpty() && ic != null) {
            // the emoji goes after the word she typed
            if (wordIsThere(ic, w)) { ic.commitText(" " + val + " ", 1); addRecent(val); }
            resetWord();
            return;
        }
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
        ic.deleteSurroundingText(shown(w).length(), 0);
        ic.commitText(val.indexOf('@') >= 0 ? val + " " : shown(val + " "), 1);   // an email must stay exactly as saved
        ic.endBatchEdit();
        if (!keep) { Words.record(lastWord, val); lastWord = val.toLowerCase(); if (++usageEdits >= 25) saveUsage(); }
        if (keep) keep(w);
        resetWord();
        autoShift();
    }

    /** A word just ended (space or punctuation): fix it if it is a typing mistake. */
    private void endWord(InputConnection ic) {
        String w = word.toString();
        word.setLength(0);
        fixTyped = null;
        if (w.isEmpty()) return;
        String finalWord = w;
        if (Words.ready && layout == 0 && !noFix && !plainField) {
            String fx = Words.fix(w);
            if (fx == null) {
                // a new word of hers: remember it so it is offered next time
                if (w.length() >= 3 && !Words.known(w)) learn(w);
            } else if (autoFix && wordIsThere(ic, w) && Words.autoReplaceOk(w)) {
                ic.beginBatchEdit();
                ic.deleteSurroundingText(shown(w).length(), 0);
                ic.commitText(shown(fx), 1);
                ic.endBatchEdit();
                fixTyped = w;
                fixDone = fx;
                fixShownTyped = w;
                fixShownDone = fx;
                fixPrompt = false;
                finalWord = fx;
            } else {
                // a wrong word she has used before is left as typed, but the right one is offered in bold
                fixShownTyped = w;
                fixShownDone = fx;
                fixPrompt = true;
            }
            // count the word she wrote (the corrected one if it was fixed) and which word came before it
            Words.record(lastWord, finalWord);
            lastWord = finalWord.toLowerCase();
            if (++usageEdits >= 25) saveUsage();
        } else {
            lastWord = "";
        }
        paintSugs();
    }

    /** A space or Enter ended a stretch of typing: if it was an email address, remember it. */
    private void tokenEnd() {
        String t = token.toString().replaceAll("[.,;:!?)]+$", "");
        token.setLength(0);
        if (noFix || t.length() > 80 || !t.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")) return;
        for (String m : myWords) if (m.equalsIgnoreCase(t)) return;
        String all = prefs().getString("myWords", "").trim();
        prefs().edit().putString("myWords", all.isEmpty() ? t : all + "\n" + t).apply();
        String[] more = new String[myWords.length + 1];
        System.arraycopy(myWords, 0, more, 0, myWords.length);
        more[myWords.length] = t;
        myWords = more;
        flashSpace("Saved " + t);
    }

    /** Backspace right after an auto-correct puts the typed word back and remembers it. */
    private boolean undoFix(InputConnection ic) {
        if (fixTyped == null) return false;
        String typed = fixTyped, done = fixDone;
        fixTyped = null;
        String sd = shown(done), st = shown(typed);
        CharSequence b = ic.getTextBeforeCursor(sd.length() + 4, 0);
        if (b == null) return false;
        String bs = b.toString();
        int at = bs.lastIndexOf(sd);
        if (at < 0) return false;
        String tail = bs.substring(at + sd.length());
        if (tail.length() > 4) return false;
        ic.beginBatchEdit();
        ic.deleteSurroundingText(bs.length() - at, 0);
        ic.commitText(st + tail, 1);
        ic.endBatchEdit();
        keep(typed);
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
        // with a font on, one typed letter is two characters long (a styled letter, or a letter plus its line), so the cursor moves by 2 per letter
        int step = typeStyle >= 0 ? 2 : 1;
        boolean jumped = newSelStart != newSelEnd || Math.abs(newSelStart - oldSelStart) > step;
        if (jumped) { token.setLength(0); lastWord = ""; if (numTyped.length() > 0) { numTyped.setLength(0); paintSugs(); } }
        if (word.length() > 0 && jumped) {
            word.setLength(0);
            paintSugs();
        }
    }

    /**
     * Switches the typing layout. A word being typed in English letters follows the switch, like Gboard:
     * type "ami", tap Bangla and it becomes the Bangla word, tap Hindi for the Hindi word, tap English to keep "ami".
     */
    private void setLayoutTo(int nl) {
        if (nl == layout) return;
        InputConnection ic = getCurrentInputConnection();
        boolean toPhonetic = nl == 1 || nl == 2;
        if (ic != null) {
            if (layout == 0 && toPhonetic) {
                String raw = word.toString();                 // letters typed on the English keys
                if (!raw.isEmpty() && wordIsThere(ic, raw)) {
                    ic.deleteSurroundingText(raw.length(), 0);
                    word.setLength(0);
                    fixTyped = null;
                    phon.append(raw);
                    ic.setComposingText(phon.toString(), 1);
                }
            } else if ((layout == 1 || layout == 2) && nl == 0) {
                String raw = phon.toString();                 // letters waiting to become Bangla/Hindi: keep them as English
                if (!raw.isEmpty()) {
                    phon.setLength(0);
                    candJob++;
                    cands = new ArrayList<>();
                    candFor = "";
                    ic.commitText(raw, 1);
                    word.append(raw);
                }
            } else if (!toPhonetic && phon.length() > 0) {
                flushPhon(false);
            }
        }
        layout = nl;
        scriptShift = false;
        if (nl >= 3) word.setLength(0);
        prefs().edit().putInt("layout", layout).apply();
        if (phon.length() > 0) { candJob++; askSuggestions(); }   // new language: fetch its words for the same letters
        buildKeysSoon();
        flashSpace(LAYOUT_NAME[layout]);
    }

    private TextView layoutKey() {
        TextView k = makeKey(LAYOUT_KEY[layout], 1.4f, cFn, cInk, layout == 0 ? 14 : 15);
        onPress(k, cFn, new Runnable() { @Override public void run() {
            setLayoutTo((layout + 1) % LAYOUT_KEY.length);
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
        enterKey = enter;
        paintEnter();
    }

    private TextView enterKey;

    // ---------- one-time codes (OTP) offered by Android above the keys ----------
    private LinearLayout otpRow;   // Android's own suggestions; hidden when there are none
    private LinearLayout smsRow;   // the code read from SMS; separate so Android's updates never wipe it
    private SharedPreferences.OnSharedPreferenceChangeListener smsWatch;
    private String curApp = "";   // the app whose box is open now
    private HorizontalScrollView contactScroll;   // full phone book names for the word being typed
    private LinearLayout contactBox;
    private boolean fixPrompt;                                  // the corrected word is only offered, the text was not changed
    private String fixShownTyped, fixShownDone;                 // the last auto-correct, kept in the top row
    private final boolean[] slotFix = new boolean[3];            // slot 0 = her original word, slot 1 = the corrected word
    private String lastWord = "";          // the word before the cursor position (for next-word suggestions)
    private int usageEdits;
    private final boolean[] slotNext = new boolean[3];   // this slot holds a predicted next word

    private void saveUsage() {
        usageEdits = 0;
        prefs().edit().putString("usage", Words.exportUsage()).apply();
    }

    // her own phone numbers: typed in the Bolchi app, plus the "Me" card of the phone book
    private String[] myNumbers = new String[0];
    private volatile String[] meNumbers = new String[0];
    private final boolean[] slotNum = new boolean[3];
    private final boolean[] slotNumFill = new boolean[3];     // this slot holds one of her numbers that completes the digits just typed
    private final StringBuilder numTyped = new StringBuilder();   // the digits typed in a row
    private int fieldClass;

    private static String digitsOf(String s) { return s.replaceAll("[^0-9]", ""); }

    /** Her numbers that begin with the digits typed (with or without +91), as saved. */
    private List<String> numbersStartingWith(String t) {
        List<String> out = new ArrayList<>();
        if (t.length() < 3) return out;
        for (String n : allMyNumbers()) {
            String d = digitsOf(n);
            String national = d.length() > 10 && d.startsWith("91") ? d.substring(d.length() - 10) : d;
            if ((d.startsWith(t) && d.length() > t.length()) || (national.startsWith(t) && national.length() > t.length())) out.add(n);
        }
        return out;
    }

    /** A strip is shown in number and phone boxes too, when she has saved numbers to offer. */
    private boolean numberStrip() {
        return page == PAGE_SYM1 && !noFix && (fieldClass == EditorInfo.TYPE_CLASS_NUMBER || fieldClass == EditorInfo.TYPE_CLASS_PHONE)
                && !allMyNumbers().isEmpty();
    }
    private static final java.util.Set<String> NUM_WORDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "phone", "mobile", "number", "whatsapp", "contact", "mob", "cell", "tel", "num"));

    private List<String> allMyNumbers() {
        List<String> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String n : myNumbers) { String d = n.replaceAll("[^0-9]", ""); if (!d.isEmpty() && seen.add(d)) out.add(n); }
        for (String n : meNumbers) { String d = n.replaceAll("[^0-9]", ""); if (!d.isEmpty() && seen.add(d)) out.add(n); }
        return out;
    }

    // ---------- GIFs beside the emoji suggestions (Giphy) ----------
    private HorizontalScrollView gifScroll;
    private LinearLayout gifBox;
    private String gifFor = "";
    private int gifJob;
    private Runnable gifAsk;

    private void hideGifs() {
        gifJob++;
        gifFor = "";
        if (gifAsk != null) handler.removeCallbacks(gifAsk);
        if (gifScroll != null) {
            gifBox.removeAllViews();
            gifScroll.setVisibility(View.GONE);
            // the word row comes back (unless the phone book names are using its place)
            if (sugRow != null && (contactScroll == null || contactScroll.getVisibility() != View.VISIBLE)) sugRow.setVisibility(View.VISIBLE);
        }
    }

    /** An emoji chip in the GIF row: the emoji goes after the word she typed. */
    private void pickEmoji(String em) {
        InputConnection ic = getCurrentInputConnection();
        String w = word.toString();
        if (ic != null && !w.isEmpty() && wordIsThere(ic, w)) { ic.commitText(" " + em + " ", 1); addRecent(em); }
        resetWord();
    }

    /** A word that has emoji also gets GIFs (after a short pause, so typing is never held up). */
    private void maybeGifs(String w) {
        if (gifScroll == null) return;
        final String key = prefs().getString("giphyKey", "").trim();
        String[] em = (!plainField && layout == 0 && wordsOn() && !key.isEmpty() && w.length() >= 3) ? EmojiHints.get(w) : null;
        if (em == null) { if (!gifFor.isEmpty()) hideGifs(); return; }
        final String q = w.toLowerCase();
        if (q.equals(gifFor)) return;
        hideGifs();
        gifFor = q;
        final int job = gifJob;
        gifAsk = new Runnable() { @Override public void run() {
            new Thread(new Runnable() { @Override public void run() {
                List<Gifs.Item> got;
                try { got = Gifs.search(key, q); } catch (Throwable t) { got = null; }
                final List<Gifs.Item> items = got;
                if (items == null || items.isEmpty()) return;
                // show the row at once with empty frames, then every picture fills in the moment it arrives
                handler.post(new Runnable() { @Override public void run() {
                    if (job != gifJob) return;
                    gifBox.removeAllViews();
                    // emojis first, then the GIFs: one row of the same height as the word row it replaces
                    String[] ems = EmojiHints.get(q);
                    for (int k = 0; ems != null && k < ems.length && k < 3; k++) {
                        final String em = ems[k];
                        TextView et = new TextView(BolchiIME.this);
                        et.setText(em);
                        et.setTextSize(22);
                        et.setGravity(Gravity.CENTER);
                        et.setPadding(dp(12), 0, dp(12), 0);
                        et.setBackground(box(cKey, 8));
                        et.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) { tick(v); pickEmoji(em); }
                        });
                        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
                        elp.setMargins(0, 0, dp(4), 0);
                        gifBox.addView(et, elp);
                    }
                    for (int i = 0; i < items.size(); i++) {
                        final Gifs.Item it = items.get(i);
                        final ImageView iv = new ImageView(BolchiIME.this);
                        iv.setAdjustViewBounds(true);
                        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        iv.setBackground(box(cKey, 8));
                        iv.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) { tick(v); sendGif(it); }
                        });
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(56), dp(40));
                        lp.setMargins(0, 0, dp(4), 0);
                        gifBox.addView(iv, lp);
                        new Thread(new Runnable() { @Override public void run() {
                            final Bitmap bm = Gifs.bitmap(it.thumb);
                            if (bm == null) return;
                            handler.post(new Runnable() { @Override public void run() {
                                if (job != gifJob) return;
                                iv.setImageBitmap(bm);
                                iv.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                                iv.requestLayout();
                            } });
                        } }).start();
                    }
                    gifScroll.scrollTo(0, 0);
                    gifScroll.setVisibility(View.VISIBLE);
                    if (sugRow != null) sugRow.setVisibility(View.GONE);   // same height as the word row: the keys do not move
                } });
            } }).start();
        } };
        handler.postDelayed(gifAsk, 150);
    }

    private boolean acceptsGif(EditorInfo ei) {
        try {
            String[] t = (String[]) EditorInfo.class.getField("contentMimeTypes").get(ei);
            if (t == null && ei.extras != null) t = ei.extras.getStringArray("androidx.core.view.inputmethod.EditorInfoCompat.CONTENT_MIME_TYPES");
            if (t == null) return false;
            for (String m : t) if ("image/gif".equals(m) || "image/*".equals(m) || "*/*".equals(m)) return true;
            return false;
        } catch (Throwable ignored) {
            return true;   // cannot tell: try, and fall back to the link
        }
    }

    private void sendGif(final Gifs.Item it) {
        flashSpace("Getting the GIF\u2026");
        new Thread(new Runnable() { @Override public void run() {
            java.io.File f = null;
            try { f = Gifs.download(BolchiIME.this, it.gif); } catch (Throwable ignored) { }
            final java.io.File file = f;
            handler.post(new Runnable() { @Override public void run() {
                InputConnection ic = getCurrentInputConnection();
                if (ic == null) return;
                boolean ok = false;
                EditorInfo ei = getCurrentInputEditorInfo();
                if (file != null && Build.VERSION.SDK_INT >= 25 && ei != null && acceptsGif(ei)) {
                    try {
                        Uri uri = Uri.parse("content://com.tara.bolchi.gifs/" + file.getName());
                        InputContentInfo info = new InputContentInfo(uri, new ClipDescription("GIF", new String[]{"image/gif"}), Uri.parse(it.gif));
                        ok = ic.commitContent(info, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null);
                    } catch (Throwable ignored) { ok = false; }
                }
                if (ok) { flashSpace("GIF sent"); }
                else {
                    // this app does not take pictures from the keyboard: the link still shows the GIF in chats
                    ic.commitText(it.gif + " ", 1);
                    flashSpace("This app takes links: GIF link sent");
                }
                word.setLength(0);
                paintSugs();
            } });
        } }).start();
    }

    private long contactsLoadedAt;
    private long gifWarmAt;
    private boolean contactsLoading;

    /** Reads the names from the phone book (if she allowed it) into memory, at most every 10 minutes. */
    private void loadContactsSoon() {
        if (contactsLoading || System.currentTimeMillis() - contactsLoadedAt < 10 * 60 * 1000L) return;
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return;
        contactsLoading = true;
        new Thread(new Runnable() { @Override public void run() {
            Cursor c = null;
            try {
                List<String> names = new ArrayList<>();
                c = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                        new String[]{ContactsContract.Contacts.DISPLAY_NAME_PRIMARY}, null, null, null);
                while (c != null && c.moveToNext()) { String n = c.getString(0); if (n != null) names.add(n); }
                Words.setContacts(names);
                contactsLoadedAt = System.currentTimeMillis();
                Cursor pc = null;
                try {
                    List<String> mine = new ArrayList<>();
                    pc = getContentResolver().query(
                            Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI, ContactsContract.Contacts.Data.CONTENT_DIRECTORY),
                            new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                            ContactsContract.Data.MIMETYPE + "=?",
                            new String[]{ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE}, null);
                    while (pc != null && pc.moveToNext()) { String n = pc.getString(0); if (n != null && !n.trim().isEmpty()) mine.add(n.trim()); }
                    meNumbers = mine.toArray(new String[0]);
                } catch (Throwable ignored) {
                    // no "Me" card or no access: the numbers typed in the Bolchi app still work
                } finally {
                    if (pc != null) try { pc.close(); } catch (Throwable ignored) { }
                }
            } catch (Throwable ignored) {
                // permission taken away or no phone book: carry on without it
            } finally {
                if (c != null) try { c.close(); } catch (Throwable ignored) { }
                contactsLoading = false;
            }
        } }).start();
    }

    /** Shows the newest SMS code (from SmsCodeReceiver) above the keys, for 3 minutes, once. */
    private void showSmsCode(boolean boxIsOpen) {
        if (smsRow == null) return;
        SharedPreferences p = prefs();
        final String code = p.getString("smsCode", "");
        long at = p.getLong("smsCodeAt", 0);
        if (code.isEmpty() || System.currentTimeMillis() - at > 2 * 60 * 1000L) return;
        // the first box opened after the code arrives claims it; other apps never get it
        String target = p.getString("smsCodeApp", "");
        if (target.isEmpty()) {
            if (!boxIsOpen) return;
            target = curApp;
            p.edit().putString("smsCodeApp", curApp).apply();
        }
        if (!target.equals(curApp)) return;
        clearSms();
        TextView t = makeKey("Code " + code + "  (tap to fill)", 1f, cAcc, cOnAcc, 16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        onPress(t, cAcc, new Runnable() { @Override public void run() {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText(code, 1);
            prefs().edit().remove("smsCode").apply();   // used: do not offer it again
            clearSms();
        } });
        smsRow.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(KEY_H + 4)));
        smsRow.setVisibility(View.VISIBLE);
        handler.removeCallbacks(expireSms);
        handler.postDelayed(expireSms, 2 * 60 * 1000L);
    }

    /** Asks Android for code suggestions (Android 11+). Android fills the box when one is tapped. */
    @Override
    public InlineSuggestionsRequest onCreateInlineSuggestionsRequest(Bundle uiExtras) {
        if (Build.VERSION.SDK_INT < 30) return null;
        List<InlinePresentationSpec> specs = new ArrayList<>();
        specs.add(new InlinePresentationSpec.Builder(new Size(dp(90), dp(44)), new Size(dp(260), dp(44))).build());
        return new InlineSuggestionsRequest.Builder(specs).setMaxSuggestionCount(3).build();
    }

    /** Android sent code suggestions: show them above the keys. */
    @Override
    public boolean onInlineSuggestionsResponse(InlineSuggestionsResponse response) {
        if (Build.VERSION.SDK_INT < 30 || response == null || otpRow == null) return false;
        List<InlineSuggestion> items = response.getInlineSuggestions();
        clearOtp();
        if (items == null || items.isEmpty()) return true;
        for (InlineSuggestion item : items) {
            item.inflate(this, new Size(dp(260), dp(44)), getMainExecutor(), new java.util.function.Consumer<InlineContentView>() {
                @Override public void accept(InlineContentView view) {
                    if (view == null || otpRow == null) return;
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(260), dp(44));
                    lp.setMargins(dp(GAP), 0, dp(GAP), 0);
                    otpRow.addView(view, lp);
                    otpRow.setVisibility(View.VISIBLE);
                }
            });
        }
        return true;
    }

    private final Runnable expireSms = new Runnable() {
        @Override public void run() { clearSms(); }
    };

    private void clearSms() {
        handler.removeCallbacks(expireSms);
        if (smsRow != null) { smsRow.removeAllViews(); smsRow.setVisibility(View.GONE); }
    }

    private void clearOtp() {
        // (code shown by showSmsCode is cleared here too)
        if (otpRow != null) { otpRow.removeAllViews(); otpRow.setVisibility(View.GONE); }
    }

    /** The enter key says what it will do in this box: Go, Search, Send, Next or Done. */
    private void paintEnter() {
        if (enterKey == null) return;
        String label = "⏎";
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei != null) {
            int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            boolean noEnterAction = (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
            boolean multiLine = (ei.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
            if (!noEnterAction && !multiLine) {
                if (action == EditorInfo.IME_ACTION_GO) label = "Go";
                else if (action == EditorInfo.IME_ACTION_SEARCH) label = "Search";
                else if (action == EditorInfo.IME_ACTION_SEND) label = "Send";
                else if (action == EditorInfo.IME_ACTION_NEXT) label = "Next";
                else if (action == EditorInfo.IME_ACTION_DONE) label = "Done";
            }
        }
        enterKey.setText(label);
        enterKey.setTextSize(label.length() > 1 ? 14 : 20);
        enterKey.setTypeface(label.length() > 1 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
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
        if (ic != null) ic.commitText(shown(out), 1);      // English letters kept as typed follow the font too
        paintCands();
    }

    private void pickCand(String c) {
        InputConnection ic = getCurrentInputConnection();
        // she kept the English letters (a name): remember it so it is offered next time
        if (phon.length() >= 3 && c.equals(phon.toString()) && c.charAt(0) < 128) learn(c);
        phon.setLength(0);
        cands = new ArrayList<>();
        candFor = "";
        candJob++;
        if (ic != null) ic.commitText(shown(c + " "), 1);
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
        // her own saved names and words that start with what she typed (English letters)
        String lp = phon.toString().toLowerCase();
        int added = 0;
        for (String m : myWords) {
            if (added >= 2) break;
            if (m.length() > lp.length() && m.toLowerCase().startsWith(lp) && !show.contains(m)) { show.add(m); added++; }
        }
        for (String o : Words.contactMatches(lp, 2)) {
            if (added >= 3) break;
            if (!show.contains(o)) { show.add(o); added++; }
        }
        for (String o : Words.ownMatches(lp, 2)) {
            if (added >= 3) break;
            String shown = Words.matchCase(phon.toString(), o);
            if (!show.contains(shown)) { show.add(shown); added++; }
        }
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

        // small tabs, four to a row, in a sensible order (two rows, shorter than before)
        final int[] ORDER = {0, 7, 1, 6, 2, 3, 4, 5, 8, 9, 10, 11};
        for (int rIdx = 0; rIdx < 3; rIdx++) {
            LinearLayout r = row();
            for (int c = 0; c < 4; c++) {
                final int idx = ORDER[rIdx * 4 + c];
                boolean on = idx == convOption;
                final int fill = on ? cInk : cKey;
                TextView o = makeKey(CONV_NAMES[idx], 1f, fill, on ? cKey : cInk, 12);
                o.setSingleLine(false);
                o.setMaxLines(2);
                o.setPadding(dp(2), 0, dp(2), 0);
                o.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1f));
                if (on) o.setTypeface(Typeface.DEFAULT_BOLD);
                onPress(o, fill, new Runnable() { @Override public void run() {
                    convOption = idx; convResult = null; buildKeysSoon(); } });
                r.addView(o);
            }
            keysBox.addView(r);
        }

        if (convOption == 11) {
            // every font as a chip in its own look: tap one to change the text into it
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout fb = new LinearLayout(this);
            fb.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = -1; i < FontStyles.NAMES.length; i++) {
                final int st = i;
                TextView chip = new TextView(this);
                chip.setText(i < 0 ? "Normal" : FontStyles.apply(i, "Abc 12"));
                chip.setTextSize(17);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(dp(14), 0, dp(14), 0);
                boolean sel = i == (fontStyle);
                chip.setTextColor(sel ? cKey : cInk);
                chip.setBackground(box(sel ? cInk : cKey, 10));
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { tick(v); fontStyle = st; convResult = null; buildKeysSoon(); }
                });
                LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
                clp2.setMargins(dp(2), dp(2), dp(2), dp(2));
                fb.addView(chip, clp2);
            }
            hs.addView(fb, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            keysBox.addView(hs, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
        }

        String label = isSel[0] ? "Selected text" : "Your text";
        TextView srcBox = panelBox(label, src.isEmpty() ? "Type or speak a message first" : src, false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(KEY_H + 8));
        keysBox.addView(srcBox, slp);

        final TextView resBox = panelBox("Result", convResult != null ? convResult
                : src.isEmpty() ? "" : "Working\u2026", true);
        convResBox = resBox;
        keysBox.addView(resBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(KEY_H + 8)));

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
        else if (convOption >= 8) use.setText("Done");
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
        if (opt >= 8) {   // fonts work on the phone at once, no internet
            int st = opt == 8 ? FontStyles.BOLD : opt == 9 ? FontStyles.ITALIC : opt == 10 ? FontStyles.BOLD_ITALIC : fontStyle;   // -1 = Normal
            String styled = st < 0 ? FontStyles.plain(src) : FontStyles.apply(st, src);
            typeStyle = st >= 0 && st != 20 ? st : -1;     // new typing follows the chosen font (not upside down: it would have to be written backwards)
            replaceText(styled);            // applied right away: it stays when she leaves the converter
            paintSpace();
            showResult(job, styled);
            return;
        }
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
                else if (opt == 4 || opt == 5) out = Converter.polish(src, key, opt == 5);
                else if (opt == 6) out = Converter.romanToScript(src, true);
                else if (opt == 7) out = Converter.translateToHindi(src, key);
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
        if (typeStyle >= 0 && layout == 0 && s.length() == 1 && s.charAt(0) < 128
                && (Character.isDigit(s.charAt(0)) || (Character.isLetter(s.charAt(0)) && !wordsOn())
                    || (typeStyle >= 21 && s.charAt(0) == ' '))) {
            // font mode, digits and boxes without suggestions: the character goes in as its styled look-alike
            boolean dig = Character.isDigit(s.charAt(0));
            if (dig) numTyped.append(s); else numTyped.setLength(0);
            if (s.charAt(0) == ' ') { endWord(ic); tokenEnd(); }          // the word just ended: correct it first
            else { resetWord(); lastWord = ""; token.setLength(0); }
            ic.commitText(FontStyles.apply(typeStyle, s), 1);
            if (dig) paintSugs();
            return;
        }
        if (wordsOn() && s.length() == 1 && (Character.isLetter(s.charAt(0)) || (s.charAt(0) == '\'' && word.length() > 0))) {
            ic.commitText(shown(s), 1);        // in font mode the letter is written styled but tracked plain, so suggestions and auto-correct keep working
            word.append(s);
            token.append(s);
            fixTyped = null;
            fixShownTyped = fixShownDone = null;
            paintSugs();
            return;
        }
        flushPhon(true);
        boolean digit = s.length() == 1 && Character.isDigit(s.charAt(0));
        if (digit) numTyped.append(s); else numTyped.setLength(0);
        if (" ".equals(s)) { endWord(ic); tokenEnd(); }
        else { resetWord(); lastWord = ""; if (s.length() == 1) token.append(s); else token.setLength(0); }
        ic.commitText(s, 1);
        if (digit) paintSugs();
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
        lastWord = "";
        if (".".equals(m) && (plainField || token.indexOf("@") >= 0)) {
            // inside an email or web address: a plain dot, no space after it
            ic.commitText(".", 1);
            token.append('.');
            return;
        }
        tokenEnd();
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
        if (token.length() > 0) token.setLength(token.length() - 1);
        if (numTyped.length() > 0) numTyped.setLength(numTyped.length() - 1);
        fixShownTyped = fixShownDone = null;
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
        boolean marked = n >= 2 && (b.charAt(n - 1) == 0x0336 || b.charAt(n - 1) == 0x0332);   // letter + strikethrough/underline mark
        if (pair || marked) ic.deleteSurroundingText(2, 0);
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        if (wordsOn()) {
            // keep following the word that is left before the cursor
            int end = Math.max(0, n - (pair || marked ? 2 : 1)), st = end;
            while (st > 0 && (Character.isLetter(b.charAt(st - 1)) || b.charAt(st - 1) == '\'')) st--;
            if (typeStyle >= 0) { if (word.length() > 0) word.setLength(word.length() - 1); }   // text is styled: keep the plain letters in step
            else {
                word.setLength(0);
                if (end - st < 25 && st < end) word.append(b, st, end);
            }
            paintSugs();
        } else if (sugRow != null) {
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
        tokenEnd();
        lastWord = "";
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
        curApp = info == null || info.packageName == null ? "" : info.packageName;
        lastWord = "";
        fixShownTyped = fixShownDone = null;
        clearOtp();
        clearSms();
        loadContactsSoon();
        final String gk = prefs().getString("giphyKey", "").trim();
        if (!gk.isEmpty() && System.currentTimeMillis() - gifWarmAt > 4 * 60 * 1000L) {
            gifWarmAt = System.currentTimeMillis();
            new Thread(new Runnable() { @Override public void run() { Gifs.warmUp(gk); } }).start();
        }
        handler.post(new Runnable() { @Override public void run() { showSmsCode(true); } });
        List<String> mine = new ArrayList<>();
        for (String m : prefs().getString("myWords", "").split("\n")) {
            m = m.trim();
            if (m.isEmpty()) continue;
            mine.add(m);
            for (String part : m.split("[^A-Za-z]+")) { Words.addOwn(part); Words.addKept(part); }   // her saved names are never flagged
        }
        myWords = mine.toArray(new String[0]);
        List<String> nums = new ArrayList<>();
        for (String n : prefs().getString("myNumbers", "").split("\n")) { n = n.trim(); if (!n.isEmpty()) nums.add(n); }
        myNumbers = nums.toArray(new String[0]);
        int cls = info.inputType & EditorInfo.TYPE_MASK_CLASS;
        fieldClass = cls;
        numTyped.setLength(0);
        int var = info.inputType & EditorInfo.TYPE_MASK_VARIATION;
        boolean wasNoFix = noFix;
        boolean text = cls == EditorInfo.TYPE_CLASS_TEXT;
        noFix = text && (var == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                || var == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD || var == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        plainField = !text || var == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || var == EditorInfo.TYPE_TEXT_VARIATION_URI;
        token.setLength(0);
        if (keysBox == null) return;
        capsLock = false;
        int want = (cls == EditorInfo.TYPE_CLASS_NUMBER || cls == EditorInfo.TYPE_CLASS_PHONE) ? PAGE_SYM1 : PAGE_ABC;
        if (page != want || wasNoFix != noFix) { page = want; buildKeys(); }
        paintEnter();
        paintSugs();
        autoShift();
        spaceText = null;
        paintSpace();
    }

    // ---------- look ----------
    private void nextTheme() { applyTheme((theme + 1) % Themes.count()); }

    private void applyTheme(int idx) {
        theme = idx;
        prefs().edit().putInt("theme", theme).putString("themeName", Themes.NAMES[theme]).apply();
        if (listening) stopListening(null);
        page = PAGE_ABC;
        setInputView(onCreateInputView());
        flashSpace(Themes.NAMES[theme]);
    }

    /** Every theme as a small square in a scrolling grid: tap one to use it. */
    private void buildThemes() {
        final int perRow = 6;
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        for (int i0 = 0; i0 < Themes.count(); i0 += perRow) {
            LinearLayout r = row();
            for (int c = 0; c < perRow; c++) {
                final int idx = i0 + c;
                if (idx >= Themes.count()) { r.addView(spacer(1f)); continue; }
                TextView sw = new TextView(this);
                boolean cur = idx == theme;
                sw.setText(cur ? "\u2713" : "Aa");
                sw.setTextSize(cur ? 20 : 15);
                sw.setTypeface(Typeface.DEFAULT_BOLD);
                sw.setGravity(Gravity.CENTER);
                sw.setTextColor(Themes.color(idx, Themes.INK));
                GradientDrawable g = new GradientDrawable();
                g.setColor(Themes.color(idx, Themes.BG));
                g.setCornerRadius(dp(10));
                g.setStroke(dp(cur ? 4 : 3), cur ? Themes.color(idx, Themes.INK) : Themes.color(idx, Themes.ACCENT));
                sw.setBackground(g);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                sw.setLayoutParams(lp);
                sw.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { tick(v); applyTheme(idx); }
                });
                r.addView(sw);
            }
            col.addView(r);
        }
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // no extra button: tap a colour, or tap the palette button again, to go back to the keys
        keysBox.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(KEY_H * 5 + 14)));
    }

    private void flashSpace(String s) {
        handler.removeCallbacks(clearSpace);
        spaceText = s;
        paintSpace();
        handler.postDelayed(clearSpace, 1500);
    }

    private void paintSpace() {
        if (spaceKey == null) return;
        String s = spaceText != null ? spaceText : listening ? "Listening…"
                : typeStyle >= 0 ? FontStyles.apply(typeStyle, FontStyles.NAMES[typeStyle]) + "  (font on)" : "Bolchi";
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
        // the tab also sets the typing language (the letter-key layouts she picked stay as they are)
        setLayoutTo(m == MODE_EN ? 0 : m == MODE_BN ? (layout == 3 ? 3 : 1) : (layout == 4 ? 4 : 2));
        paintChips();
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
        firstRound = true;
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
        // Phone's own voice engine, as in build-13 (forcing Google's engine made Bangla worse for Tara).
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
    }

    // The phone's voice engine beeps every time it starts and stops. We restart it after every pause,
    // so it would beep again and again: silence those beeps (only the first start is heard).
    private static final int[] BEEP_STREAMS = {AudioManager.STREAM_MUSIC, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM};
    private final boolean[] hushed = new boolean[BEEP_STREAMS.length];
    private boolean firstRound;
    private final Runnable unhush = new Runnable() {
        @Override public void run() {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            for (int k = 0; k < BEEP_STREAMS.length; k++) {
                if (!hushed[k]) continue;
                hushed[k] = false;
                try { if (am != null) am.adjustStreamVolume(BEEP_STREAMS[k], AudioManager.ADJUST_UNMUTE, 0); } catch (Exception ignored) { }
            }
        }
    };

    private void hushBeep() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        for (int k = 0; k < BEEP_STREAMS.length; k++) {
            try {
                if (hushed[k] || am.isStreamMute(BEEP_STREAMS[k])) continue;   // already silent: leave it alone
                am.adjustStreamVolume(BEEP_STREAMS[k], AudioManager.ADJUST_MUTE, 0);
                hushed[k] = true;
            } catch (Exception ignored) { /* not allowed for this stream */ }
        }
        handler.removeCallbacks(unhush);
        handler.postDelayed(unhush, 900);
    }

    private void beginRound() {
        try {
            if (firstRound) firstRound = false; else hushBeep();
            if (recognizer == null) freshRecognizer();
            String lang = MODE_LANG[mode];
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            // a pause of about 2.5 s ends a stretch, so the AI fix arrives soon; listening restarts by itself
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L);
            recognizer.startListening(i);
        } catch (Exception e) {
            retryLater();
        }
    }

    /** Something went wrong mid-way: start a fresh recogniser and carry on. */
    private void retryLater() {
        failRounds++;
        if (failRounds == 3) googleFailed = true;
        if (failRounds > 6) { stopListening("Voice stopped. Tap Speak again."); return; }
        try { freshRecognizer(); } catch (Exception e) { /* try again below */ }
        handler.postDelayed(nextRound, 400);
    }

    private void stopListening(String message) {
        boolean was = listening;
        if (was) hushBeep();
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
        if (!aiTidy || tidyBusy || tidyFailed) return;
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
                    flashSpace("AI did not work: open Bolchi app, tap Test AI key");
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
    @Override public void onEndOfSpeech() { if (listening) hushBeep(); }
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
        hushBeep();
        String heard = first(results);
        if (mode == MODE_EN) heard = Spoken.fix(heard);   // Bangla/Hindi speech is written exactly as heard
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
        hushBeep();
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
        if (usageEdits > 0) saveUsage();
        clearOtp();                                   // leaving the box: the code strips go
        clearSms();
        if (contactScroll != null) contactScroll.setVisibility(View.GONE);
        hideGifs();
        prefs().edit().putString("lastApp", curApp).apply();
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
