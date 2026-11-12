package com.tara.bolchi;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
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
    private TextView enableBtn;

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
                + "The palette key changes colours, the smiley key opens emojis, and holding the space bar switches keyboards.", 14, MUTED));
        col.addView(text("If it says voice is not set up: open the Google app, go to Settings, Voice, "
                + "Languages, and add Bengali (India) and Hindi (India). Voice typing needs internet.", 14, MUTED));

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

    private void refresh() {
        boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        micBtn.setText(mic ? "✓ Microphone allowed" : "Allow microphone");
        micBtn.setBackground(box(mic ? OK : ACCENT, 0, 12));

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
    }
}
