package com.tara.bolchi;

import android.graphics.Color;

/** Keyboard colour themes: background, key, function key, text, muted text, accent. */
final class Themes {
    private Themes() {}

    static final String[] NAMES = {
            "Black & Gold", "Black & Red", "Black & Mint", "Black & Pink", "Black & Sky",
            "Black, White & Red", "Navy & Coral", "Plum & Peach", "Forest, Cream & Gold", "Teal, White & Yellow",
            "Cream", "Mint", "Lavender", "Sky", "Rose", "Peach", "Lemon", "Ocean",
            "Sage", "Coral", "Sand", "Lilac", "Aqua", "Blush", "Olive", "Silver", "Black & White", "Plum Lavender",
            "Midnight & Gold", "Charcoal & Orange", "Wine & Rose Gold", "Emerald & Gold", "Purple Night", "Ocean Deep", "Slate & Teal", "Neon Night", "Coffee & Cream", "Rose & White", "Butter Yellow", "Fresh Green", "Sunset", "Baby Blue & Navy", "Mocha"};

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
            {"#F0F0F0", "#FFFFFF", "#D6D6D6", "#000000", "#555555", "#000000"},
            {"#EBDFF0", "#FFFFFF", "#DCC8E6", "#3A1F45", "#7A6A80", "#944FAA"},
            {"#0B1530", "#16264A", "#22386A", "#F2F5FF", "#8FA3CC", "#F5C542"},
            {"#161616", "#262626", "#363636", "#FFFFFF", "#A8A8A8", "#FF7A1A"},
            {"#3B0F1E", "#50182C", "#66233A", "#FFF1F3", "#D4A5AE", "#E8B4A0"},
            {"#0B3B2E", "#135240", "#1B6A53", "#F3FBF6", "#8FC4AE", "#E6B84A"},
            {"#1B1030", "#2A1A4A", "#3B2763", "#F6F0FF", "#B7A4DB", "#B36BFF"},
            {"#062B3A", "#0B3F54", "#12546F", "#EAF8FF", "#8CC0D4", "#2EC4F2"},
            {"#26323A", "#33434D", "#45586A", "#F1F6F8", "#9DB1BC", "#2EC4B6"},
            {"#0A0A12", "#181826", "#262638", "#F2F2FF", "#9A9AB8", "#39FF9C"},
            {"#3A2A20", "#F3E7D8", "#D8C3A8", "#3A2A20", "#7A6451", "#C8793A"},
            {"#FFF0F3", "#FFFFFF", "#FAD4DD", "#5A1A2C", "#A06A78", "#E5486E"},
            {"#FFF6C9", "#FFFFFF", "#F6E58E", "#3F3600", "#867A2C", "#E0A800"},
            {"#E7F6DC", "#FFFFFF", "#CFEBB8", "#1B3A12", "#5A7A4A", "#3FA32B"},
            {"#FFE3D0", "#FFFFFF", "#FFC9A8", "#5A1F10", "#A06A58", "#F0552F"},
            {"#D6E8FF", "#FFFFFF", "#B3D2FA", "#0C2A55", "#4F6F9C", "#0C4FB8"},
            {"#E9DDD0", "#FBF6F0", "#D5C3B0", "#3A2C22", "#78675A", "#8B5A3C"}};

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
