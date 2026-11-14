package com.tara.bolchi;

import android.graphics.Color;

/** Keyboard colour themes: background, key, function key, text, muted text, accent. */
final class Themes {
    private Themes() {}

    static final String[] NAMES = {
            "Black & Gold", "Black & Red", "Black & Mint", "Black & Pink", "Black & Sky", "Black, White & Red",
            "Navy & Coral", "Plum & Peach", "Forest, Cream & Gold", "Teal, White & Yellow", "Cream", "Mint",
            "Lavender", "Sky", "Rose", "Peach", "Lemon", "Ocean",
            "Sage", "Coral", "Sand", "Lilac", "Aqua", "Blush",
            "Olive", "Silver", "Black & White", "Plum Lavender", "Rose & White", "Butter Yellow",
            "Fresh Green", "Sunset", "Baby Blue & Navy", "Mocha", "Pistachio", "Lilac Mist",
            "Apricot", "Cotton Candy", "Ice Blue", "Mint Cream", "Lemonade", "Dusty Rose",
            "Warm Grey", "Sky Lavender", "Seafoam", "Tangerine Cream", "Bubblegum", "Meadow",
            "Peacock Light", "Vanilla", "Orchid", "Cherry Blossom", "Fresh Lime", "Denim Light",
            "Honey", "Coral Reef", "Jade", "Powder Pink", "Sand Dune", "Periwinkle"};

    /** The list of the earlier build (43 themes), only to keep the theme she already picked when the list changes. */
    static final String[] OLD = {
            "Black & Gold", "Black & Red", "Black & Mint", "Black & Pink", "Black & Sky", "Black, White & Red",
            "Navy & Coral", "Plum & Peach", "Forest, Cream & Gold", "Teal, White & Yellow", "Cream", "Mint",
            "Lavender", "Sky", "Rose", "Peach", "Lemon", "Ocean",
            "Sage", "Coral", "Sand", "Lilac", "Aqua", "Blush",
            "Olive", "Silver", "Black & White", "Plum Lavender", "Midnight & Gold", "Charcoal & Orange",
            "Wine & Rose Gold", "Emerald & Gold", "Purple Night", "Ocean Deep", "Slate & Teal", "Neon Night",
            "Coffee & Cream", "Rose & White", "Butter Yellow", "Fresh Green", "Sunset", "Baby Blue & Navy",
            "Mocha"};

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
            {"#FFF0F3", "#FFFFFF", "#FAD4DD", "#5A1A2C", "#A06A78", "#E5486E"},
            {"#FFF6C9", "#FFFFFF", "#F6E58E", "#3F3600", "#867A2C", "#E0A800"},
            {"#E7F6DC", "#FFFFFF", "#CFEBB8", "#1B3A12", "#5A7A4A", "#3FA32B"},
            {"#FFE3D0", "#FFFFFF", "#FFC9A8", "#5A1F10", "#A06A58", "#F0552F"},
            {"#D6E8FF", "#FFFFFF", "#B3D2FA", "#0C2A55", "#4F6F9C", "#0C4FB8"},
            {"#E9DDD0", "#FBF6F0", "#D5C3B0", "#3A2C22", "#78675A", "#8B5A3C"},
            {"#E6F2D5", "#FFFFFF", "#CFE5B0", "#233A12", "#5F7A45", "#6BA33A"},
            {"#EEE8FB", "#FFFFFF", "#DCD0F5", "#2D2158", "#6E62A0", "#7A5CE0"},
            {"#FFE8CF", "#FFFBF5", "#FFD3A6", "#4F2A0B", "#9A6B44", "#F08A24"},
            {"#FDE4F2", "#FFFFFF", "#F9C9E3", "#561A3F", "#A06285", "#E0489A"},
            {"#E1F1FB", "#FFFFFF", "#C4E2F5", "#0B3350", "#4E7391", "#2796D9"},
            {"#E9F8F0", "#FFFFFF", "#CBEFDC", "#0E3A28", "#4F7D68", "#1FB27A"},
            {"#FFF9D6", "#FFFFFF", "#FBEFA0", "#3F3A06", "#857E2E", "#D9B400"},
            {"#F4E1E1", "#FFFAFA", "#E8C6C8", "#4A2326", "#8F6165", "#C4575F"},
            {"#ECEAE6", "#FFFFFF", "#D9D5CE", "#2A2723", "#6E6A63", "#B5623A"},
            {"#E3E9FB", "#FFFFFF", "#C9D4F6", "#1E2A5C", "#5C6AA0", "#4062E0"},
            {"#DDF3EF", "#FFFFFF", "#BDE7DF", "#0C3B35", "#477A72", "#17A592"},
            {"#FFEFD9", "#FFFFFF", "#FFD9A3", "#5A2E00", "#A06F2E", "#FF8A00"},
            {"#FFE1EC", "#FFFFFF", "#FFC2D8", "#5A1230", "#A05A78", "#FF3F86"},
            {"#EAF5D8", "#FCFFF7", "#D5EBB2", "#24400F", "#668047", "#4CAF1E"},
            {"#D9F0F5", "#FFFFFF", "#B2E0EA", "#08384A", "#3F7487", "#0A8FB8"},
            {"#FBF3E3", "#FFFFFF", "#F0E2C6", "#3D2E12", "#8A7650", "#C98A1B"},
            {"#F3E0F7", "#FFFFFF", "#E5C3EE", "#41154F", "#8A5A99", "#B83FD4"},
            {"#FFE8EE", "#FFFFFF", "#FBCBD8", "#5C1830", "#A86079", "#E8517B"},
            {"#F0F9D3", "#FFFFFF", "#DEF09A", "#2F3F08", "#74872E", "#8DC400"},
            {"#DCE7F4", "#FFFFFF", "#BDD1EA", "#14294A", "#566F93", "#2B5DA8"},
            {"#FFF0C2", "#FFFDF3", "#FFDE85", "#4A3300", "#98793A", "#E0961A"},
            {"#FFE5E0", "#FFFFFF", "#FFC8BE", "#5A1C14", "#A5655C", "#F2634C"},
            {"#D8F2E2", "#FCFFFD", "#B4E4C9", "#0B3A22", "#4A7F63", "#12A15A"},
            {"#F9E6EC", "#FFFFFF", "#F1CBD8", "#4F2030", "#9A6578", "#D86A8E"},
            {"#F3E9D2", "#FFFDF6", "#E6D4AA", "#40310F", "#8A7640", "#C08A1E"},
            {"#E6E6FA", "#FFFFFF", "#CFCFF3", "#25255C", "#6767A5", "#5252D9"}};

    static final int BG = 0, KEY = 1, FN = 2, INK = 3, MUTED = 4, ACCENT = 5;

    /** Readable text colour on top of the accent (dark on gold/yellow, white on the rest). */
    static int onAccent(int theme) {
        int c = color(theme, ACCENT);
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (0.299 * r + 0.587 * g + 0.114 * b) > 160 ? Color.parseColor("#111111") : Color.WHITE;
    }

    static int count() { return NAMES.length; }

    static int indexOf(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return i;
        return -1;
    }


    static int color(int theme, int part) {
        if (theme < 0 || theme >= HEX.length) theme = 0;
        return Color.parseColor(HEX[theme][part]);
    }
}
