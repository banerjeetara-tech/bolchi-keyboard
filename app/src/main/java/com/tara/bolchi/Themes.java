package com.tara.bolchi;

import android.graphics.Color;

/** Keyboard colour themes: background, key, function key, text, muted text, accent. */
final class Themes {
    private Themes() {}

    static final String[] NAMES = {
            "Black & Gold", "Black & Red", "Black & Mint", "Black & Pink", "Black & Sky",
            "Black, White & Red", "Navy & Coral", "Plum & Peach", "Forest, Cream & Gold", "Teal, White & Yellow",
            "Cream", "Mint", "Lavender", "Sky", "Rose", "Peach", "Lemon", "Ocean",
            "Sage", "Coral", "Sand", "Lilac", "Aqua", "Blush", "Olive", "Silver", "Black & White"};

    private static final String[][] HEX = {
            {"#0E0E0E", "#1E1E1E", "#2C2C2C", "#F5F5F5", "#A0A0A0", "#D4A72C"},
            {"#0E0E0E", "#1E1E1E", "#2C2C2C", "#F5F5F5", "#A0A0A0", "#E23B3B"},
            {"#0E0E0E", "#1E1E1E", "#2C2C2C", "#F5F5F5", "#A0A0A0", "#2EC4A0"},
            {"#0E0E0E", "#1E1E1E", "#2C2C2C", "#F5F5F5", "#A0A0A0", "#FF5C93"},
            {"#0E0E0E", "#1E1E1E", "#2C2C2C", "#F5F5F5", "#A0A0A0", "#3BA3FF"},
            {"#000000", "#FFFFFF", "#D6D6D6", "#000000", "#555555", "#E23B3B"},
            {"#13233F", "#1E3357", "#2A4470", "#F4F6FA", "#9FB0CC", "#FF7A59"},
            {"#2E1A3A", "#3F2750", "#523465", "#FBF3FF", "#BFA6CC", "#FFB38A"},
            {"#1F3A2C", "#F6EFE2", "#C9B99A", "#1F3A2C", "#6B6B5A", "#E0A84B"},
            {"#0E5A5F", "#FFFFFF", "#BFE3E4", "#0E3B3E", "#4E7A7D", "#F2C230"},
            {"#EFE3D3", "#FFFAF2", "#E2D2BE", "#2B211A", "#7A6A5C", "#B9502B"},
            {"#DDEFE6", "#FFFFFF", "#C3E2D3", "#16352A", "#4E7363", "#0F7A5A"},
            {"#E8E4F6", "#FFFFFF", "#D3CCEE", "#2A2350", "#6A6290", "#5B4BC4"},
            {"#DCEBFA", "#FFFFFF", "#C2DAF3", "#0F2D4D", "#4D6B8A", "#1F6FC5"},
            {"#F8E1E8", "#FFFFFF", "#F0C9D6", "#4A1A2B", "#8A5466", "#C2416B"},
            {"#FCE6D8", "#FFFAF6", "#F6CFB6", "#4A2512", "#8A5E45", "#E06A2C"},
            {"#F7F0C9", "#FFFDF2", "#EDE1A3", "#3D3405", "#7A6E33", "#A88A00"},
            {"#D5EEF0", "#FFFFFF", "#B5DFE3", "#0B3A40", "#3F6D73", "#0E8A96"},
            {"#E3EBDD", "#FBFDF9", "#CCDABF", "#26331C", "#5E6E52", "#5A7D3A"},
            {"#FBE0DA", "#FFFAF8", "#F4C3B8", "#4D1C12", "#8C5448", "#E0533C"},
            {"#EEE6D8", "#FFFDF8", "#DED1BA", "#3A3020", "#76694F", "#9C7A3C"},
            {"#F1E3F4", "#FFFFFF", "#E3C9E9", "#3E1D46", "#7A5582", "#A04BB5"},
            {"#D8F3EE", "#FFFFFF", "#B6E8DE", "#0D3B34", "#41716A", "#14A08A"},
            {"#FBE8E4", "#FFFCFB", "#F2D0C8", "#4A2620", "#8A5F57", "#D9776A"},
            {"#ECEBD5", "#FDFDF6", "#DAD8B3", "#33331A", "#6E6C48", "#7A7A1F"},
            {"#E9EBEE", "#FFFFFF", "#D3D7DD", "#22262C", "#5E646D", "#3D5A80"},
            {"#F0F0F0", "#FFFFFF", "#D6D6D6", "#000000", "#555555", "#000000"}};

    static final int BG = 0, KEY = 1, FN = 2, INK = 3, MUTED = 4, ACCENT = 5;

    /** Readable text colour on top of the accent (dark on gold/yellow, white on the rest). */
    static int onAccent(int theme) {
        int c = color(theme, ACCENT);
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (0.299 * r + 0.587 * g + 0.114 * b) > 160 ? Color.parseColor("#111111") : Color.WHITE;
    }

    static int count() { return NAMES.length; }

    static int color(int theme, int part) {
        if (theme < 0 || theme >= HEX.length) theme = 0;
        return Color.parseColor(HEX[theme][part]);
    }
}
