package com.tara.bolchi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks at incoming SMS for a one-time code (OTP) and remembers only the code and the time.
 * The message text itself is not stored.
 */
public class SmsCodeReceiver extends BroadcastReceiver {
    private static final Pattern CODE = Pattern.compile("(?<![0-9])([0-9]{4,8})(?![0-9])");
    private static final Pattern HINT = Pattern.compile(
            "(?i)\\b(otp|one[- ]time|verification|verify|code|password|pin)\\b");

    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        try {
            SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (msgs == null) return;
            StringBuilder body = new StringBuilder();
            for (SmsMessage m : msgs) if (m != null && m.getMessageBody() != null) body.append(m.getMessageBody()).append(' ');
            String text = body.toString();
            if (!HINT.matcher(text).find()) return;          // not a code message
            Matcher m = CODE.matcher(text);
            if (!m.find()) return;
            SharedPreferences p = c.getSharedPreferences("bolchi", Context.MODE_PRIVATE);
            p.edit().putString("smsCode", m.group(1)).putLong("smsCodeAt", System.currentTimeMillis()).putString("smsCodeApp", "").apply();
        } catch (Exception ignored) {
            // a bad message must never break the phone's messages
        }
    }
}
