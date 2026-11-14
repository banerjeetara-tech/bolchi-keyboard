package com.tara.bolchi;

/**
 * "Fonts" for messages: the letters are swapped for the look-alike styled letters that exist in Unicode
 * (bold, italic, script, circled...). They work in any app that can show those characters; Bangla and Hindi stay as they are.
 */
public final class FontStyles {
    private FontStyles() { }

    public static final int BOLD = 0, ITALIC = 1, BOLD_ITALIC = 2;

    public static final String[] NAMES = {
            "Bold", "Italic", "Bold italic", "Script", "Bold script", "Fraktur", "Bold fraktur", "Double", "Sans",
            "Sans bold", "Sans italic", "Sans bold italic", "Mono", "Circled", "Black circles", "Squares", "Black squares",
            "Wide", "Small caps", "Tiny", "Upside down", "Strikethrough", "Underline"};

    /** First code point of A, of a, and of 0 in the Unicode block of each style (0 = none). */
    private static final int[][] MATH = {
            {0x1D400, 0x1D41A, 0x1D7CE},   // bold
            {0x1D434, 0x1D44E, 0},         // italic
            {0x1D468, 0x1D482, 0},         // bold italic
            {0x1D49C, 0x1D4B6, 0},         // script
            {0x1D4D0, 0x1D4EA, 0},         // bold script
            {0x1D504, 0x1D51E, 0},         // fraktur
            {0x1D56C, 0x1D586, 0},         // bold fraktur
            {0x1D538, 0x1D552, 0x1D7D8},   // double-struck
            {0x1D5A0, 0x1D5BA, 0x1D7E2},   // sans
            {0x1D5D4, 0x1D5EE, 0x1D7EC},   // sans bold
            {0x1D608, 0x1D622, 0},         // sans italic
            {0x1D63C, 0x1D656, 0},         // sans bold italic
            {0x1D670, 0x1D68A, 0x1D7F6}};  // monospace

    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
    private static final String UPSIDE_DOWN = "ɐqɔpǝɟƃɥᴉɾʞlɯuodbɹsʇnʌʍxʎz";
    private static final String TINY_LOWER = "ᵃᵇᶜᵈᵉᶠᵍʰⁱʲᵏˡᵐⁿᵒᵖᵠʳˢᵗᵘᵛʷˣʸᶻ";
    private static final String TINY_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹";

    // letters that have their own place in Unicode instead of the regular block
    private static int hole(int style, int c) {
        switch (style) {
            case 1: if (c == 'h') return 0x210E; break;                                  // italic h
            case 3: switch (c) { case 'B': return 0x212C; case 'E': return 0x2130; case 'F': return 0x2131; case 'H': return 0x210B;
                case 'I': return 0x2110; case 'L': return 0x2112; case 'M': return 0x2133; case 'R': return 0x211B;
                case 'e': return 0x212F; case 'g': return 0x210A; case 'o': return 0x2134; } break;
            case 5: switch (c) { case 'C': return 0x212D; case 'H': return 0x210C; case 'I': return 0x2111; case 'R': return 0x211C; case 'Z': return 0x2128; } break;
            case 7: switch (c) { case 'C': return 0x2102; case 'H': return 0x210D; case 'N': return 0x2115; case 'P': return 0x2119;
                case 'Q': return 0x211A; case 'R': return 0x211D; case 'Z': return 0x2124; } break;
            default: break;
        }
        return 0;
    }

    private static boolean letter(int c) { return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'); }

    private static java.util.HashMap<Integer, Character> rev;

    /** Turns text that is already in a font back into plain letters, so a new font can be chosen. */
    public static String plain(String text) {
        if (text == null) return "";
        if (rev == null) {
            java.util.HashMap<Integer, Character> m = new java.util.HashMap<>();
            String all = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            for (int st = 0; st < NAMES.length; st++) {
                if (st == 20 || st == 21 || st == 22) continue;
                for (int i = 0; i < all.length(); i++) {
                    String o = applyRaw(st, String.valueOf(all.charAt(i)));
                    if (o.codePointCount(0, o.length()) == 1 && o.codePointAt(0) != all.charAt(i)) m.put(o.codePointAt(0), all.charAt(i));
                }
            }
            rev = m;
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Character c = rev.get(cp);
            if (c != null) b.append(c.charValue());
            else if (cp != 0x0336 && cp != 0x0332) b.appendCodePoint(cp);
        }
        return b.toString();
    }

    /** The font this text is written in, or -1 when it is plain (or only partly styled). */
    public static int detect(String text) {
        if (text == null || text.isEmpty()) return -1;
        String pl = plain(text);
        if (pl.equals(text)) return -1;
        for (int st = 0; st < NAMES.length; st++) if (st != 20 && applyRaw(st, pl).equals(text)) return st;
        return -1;
    }

    public static String apply(int style, String text) {
        return applyRaw(style, plain(text));
    }

    private static String applyRaw(int style, String text) {
        if (text == null || text.isEmpty() || style < 0 || style >= NAMES.length) return text == null ? "" : text;
        StringBuilder b = new StringBuilder();
        if (style == 20) {                                       // upside down: flip every letter and read backwards
            for (int i = text.length() - 1; i >= 0; i--) {
                char c = text.charAt(i);
                b.append(c >= 'a' && c <= 'z' ? UPSIDE_DOWN.charAt(c - 'a') : c >= 'A' && c <= 'Z' ? UPSIDE_DOWN.charAt(c - 'A') : c);
            }
            return b.toString();
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (style <= 12) {
                int[] m = MATH[style];
                int h = hole(style, c);
                if (h != 0) b.appendCodePoint(h);
                else if (c >= 'A' && c <= 'Z') b.appendCodePoint(m[0] + (c - 'A'));
                else if (c >= 'a' && c <= 'z') b.appendCodePoint(m[1] + (c - 'a'));
                else if (c >= '0' && c <= '9' && m[2] != 0) b.appendCodePoint(m[2] + (c - '0'));
                else b.append(c);
            } else if (style == 13) {                            // circled
                if (c >= 'A' && c <= 'Z') b.appendCodePoint(0x24B6 + (c - 'A'));
                else if (c >= 'a' && c <= 'z') b.appendCodePoint(0x24D0 + (c - 'a'));
                else if (c == '0') b.appendCodePoint(0x24EA);
                else if (c >= '1' && c <= '9') b.appendCodePoint(0x2460 + (c - '1'));
                else b.append(c);
            } else if (style == 14) {                            // black circles
                if (letter(c)) b.appendCodePoint(0x1F150 + (Character.toUpperCase(c) - 'A')); else b.append(c);
            } else if (style == 15) {                            // squares
                if (letter(c)) b.appendCodePoint(0x1F130 + (Character.toUpperCase(c) - 'A')); else b.append(c);
            } else if (style == 16) {                            // black squares
                if (letter(c)) b.appendCodePoint(0x1F170 + (Character.toUpperCase(c) - 'A')); else b.append(c);
            } else if (style == 17) {                            // wide (fullwidth)
                if (c > ' ' && c <= '~') b.append((char) (c + 0xFEE0)); else b.append(c);
            } else if (style == 18) {                            // small caps
                b.append(c >= 'a' && c <= 'z' ? SMALL_CAPS.charAt(c - 'a') : c);
            } else if (style == 19) {                            // tiny letters
                if (c >= 'a' && c <= 'z') b.append(TINY_LOWER.charAt(c - 'a'));
                else if (c >= 'A' && c <= 'Z') b.append(TINY_LOWER.charAt(c - 'A'));
                else if (c >= '0' && c <= '9') b.append(TINY_DIGITS.charAt(c - '0'));
                else b.append(c);
            } else {                                             // 21 strikethrough, 22 underline
                b.append(c);
                if (c != '\n') b.append(style == 21 ? '̶' : '̲');
            }
        }
        return b.toString();
    }
}
