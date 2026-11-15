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
            "Honey", "Coral Reef", "Jade", "Powder Pink", "Sand Dune", "Periwinkle",
            "Peach Sorbet", "Lavender Honey", "Rose Gold", "Sky & Sunshine", "Matcha Latte", "Strawberry Milk",
            "Blueberry Cream", "Coral & Teal", "Lilac & Lemon", "Sea Glass", "Pearl & Gold", "Mango Lassi",
            "Watermelon", "Terracotta Cream", "Plum Blossom", "Morning Mist",
            // see-through ("glass") themes: the colours carry an alpha value, so the app shows through the keyboard
            "Glass Clear", "Glass Rose", "Glass Peach", "Glass Lemon", "Glass Mint", "Glass Aqua", "Glass Sky", "Glass Lavender", "Glass Lilac", "Glass Blush",
            "Glass Sand", "Glass Coral", "Glass Sage", "Glass Pearl", "Glass Sunrise", "Glass Ocean", "Glass Smoke", "Glass Navy", "Glass Plum", "Glass Forest",
            // colourful combinations: Trio = background + letter keys + side keys in three colours; Quad = the number row in a fourth colour
            "Trio Candy", "Trio Citrus", "Trio Ocean", "Trio Sunset", "Trio Garden", "Trio Grape", "Trio Tropical", "Trio Berry", "Trio Sky", "Trio Peacock", "Trio Flamingo", "Trio Lemon Berry",
            "Quad Rainbow", "Quad Fiesta", "Quad Festival", "Quad Carnival", "Quad Holi", "Quad Peacock", "Quad Sherbet", "Quad Bouquet", "Quad Beach", "Quad Mela", "Quad Orchid", "Quad Mango"};

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
            {"#E6E6FA", "#FFFFFF", "#CFCFF3", "#25255C", "#6767A5", "#5252D9"},
            {"#FFE9DC", "#FFFBF7", "#FFD3BC", "#5A2A14", "#A0705A", "#FF7F50"},
            {"#EFE6FA", "#FFFDF5", "#F6DFA0", "#33235A", "#7B6C9C", "#8A5BD6"},
            {"#F9E4E0", "#FFFAF8", "#EFC7BD", "#4E2A26", "#96706A", "#C98469"},
            {"#DDEEFC", "#FFFDF0", "#FFE38A", "#12325A", "#56739A", "#F2A900"},
            {"#E4EDD6", "#FBF8EF", "#CFDDB8", "#2D3A1C", "#6C7A56", "#6E9A2E"},
            {"#FFE3EA", "#FFFFFF", "#FFC4D2", "#5A1A2E", "#A0607A", "#F0527F"},
            {"#E1E6FB", "#FFFFFF", "#C5CEF5", "#1C2660", "#5E69A8", "#4A5BD8"},
            {"#FFE7E0", "#FFFFFF", "#BFE8E2", "#0F4A46", "#5E8A86", "#FF6B5A"},
            {"#F0E4FA", "#FFFEF2", "#F5EC9A", "#3C2260", "#806AA0", "#9B5DE0"},
            {"#DCF3EC", "#FAFFFD", "#BCE5DA", "#103F38", "#4F8078", "#1FA890"},
            {"#F4F1EA", "#FFFFFF", "#E8DFC8", "#3A3220", "#857A60", "#C9A227"},
            {"#FFF1CC", "#FFFDF5", "#FFD98A", "#5A3400", "#A07A3A", "#FF9A1F"},
            {"#FFE1E4", "#F6FFF4", "#BFE8B8", "#4A1420", "#8A5A62", "#F0445E"},
            {"#F6E5D8", "#FFFAF4", "#E9C7AE", "#4E2A18", "#93705C", "#C8623A"},
            {"#F5E1EE", "#FFFFFF", "#E8C3DA", "#4A1A3C", "#8E6382", "#B8438F"},
            {"#E8EEF2", "#FFFFFF", "#D2DDE5", "#1F2E3A", "#62727F", "#4E8FB5"},
            // glass themes (#AARRGGBB)
            {"#2EFFFFFF", "#B3FFFFFF", "#A6E6E6E6", "#1C1C1C", "#5A5A5A", "#4E8FB5"},
            {"#2EFFD6E0", "#B3FFFFFF", "#A6F7B6C8", "#4A1426", "#8A5A68", "#E0527A"},
            {"#2EFFE0C8", "#B3FFFFFF", "#A6F9C4A0", "#4E2A12", "#93705A", "#F08A3C"},
            {"#2EFFF4B8", "#B3FFFFFF", "#A6F6E38A", "#4A3C00", "#8A7A3A", "#E0B400"},
            {"#2ECFF5E3", "#B3FFFFFF", "#A6A6E6C8", "#0E3F2C", "#4F8070", "#1FA870"},
            {"#2EC8F2F4", "#B3FFFFFF", "#A69ADFE4", "#0C3C42", "#4C7C82", "#14A0B0"},
            {"#2ED2E8FF", "#B3FFFFFF", "#A6A8CFF8", "#10305A", "#52708F", "#2F7FE0"},
            {"#2EE4DCFF", "#B3FFFFFF", "#A6C8B8F6", "#2C1C5A", "#6A5C92", "#7A5CE0"},
            {"#2EF1D8F6", "#B3FFFFFF", "#A6DDB4E8", "#44184E", "#84608C", "#B04CC8"},
            {"#2EFBDCD8", "#B3FFFFFF", "#A6F2B8B0", "#4E1C16", "#8E625C", "#E0604C"},
            {"#2EF2E6D0", "#B3FFFFFF", "#A6E2CDA6", "#44341A", "#84745A", "#C89A3C"},
            {"#2EFFD4CC", "#B3FFFFFF", "#A6FAAE9E", "#521C10", "#925E54", "#F0583C"},
            {"#2EDCE8D4", "#B3FFFFFF", "#A6BCD0AE", "#24361A", "#647658", "#6A9A4C"},
            {"#2EF4F0EA", "#B3FFFFFF", "#A6E0D8CC", "#2E2A24", "#6E6A62", "#B89A5C"},
            {"#2EFFE2D6", "#B3FFFFFF", "#A6FFD08A", "#4A2410", "#8A6450", "#F07A4C"},
            {"#2ECDEBF2", "#B3FFFFFF", "#A69FD3E6", "#0E3448", "#4E7488", "#1C8CC0"},
            {"#38000000", "#6B000000", "#941A1A1A", "#FFFFFF", "#D0D0D0", "#FFC83C"},
            {"#380A1E46", "#6B0A1E46", "#9410285A", "#FFFFFF", "#C8D4EC", "#FF8A6C"},
            {"#383A1040", "#6B3A1040", "#944A1852", "#FFFFFF", "#E4CCE8", "#FFB48A"},
            {"#380C3220", "#6B0C3220", "#94124028", "#FFFFFF", "#CCE4D6", "#F0D060"},
            // Trio (3 colours) and Quad (4 colours: the 7th value is the number row)
            {"#FFF0F5", "#FFB3D1", "#8FE3F0", "#3A1030", "#8A5A78", "#E0408A"},
            {"#FFF9E0", "#FFD54F", "#A5E88A", "#3A3000", "#8A7A3A", "#F08A00"},
            {"#E8F7FF", "#8FD3FF", "#7FE8D0", "#06304A", "#4A7088", "#0A8AD0"},
            {"#FFF1E6", "#FFB37A", "#FF8FB0", "#4A1E0C", "#8A5E4C", "#F0503C"},
            {"#F2FBE8", "#B6E87A", "#FFC0D8", "#20380C", "#60784C", "#5AA81C"},
            {"#F6EEFF", "#C9A8FF", "#FFD27A", "#2A1050", "#6A5890", "#7A3CE0"},
            {"#E6FFF8", "#7FE8C8", "#FFB86A", "#08382C", "#48786C", "#0AA880"},
            {"#FFEFF4", "#FF9FBA", "#B8A8FF", "#4A0C22", "#8A4C62", "#E02C64"},
            {"#EEF6FF", "#A8CCFF", "#FFE27A", "#0C2850", "#4C6890", "#2C6CE0"},
            {"#E8FBFB", "#7FDDE0", "#C8F07A", "#06383A", "#46787A", "#0A9AA0"},
            {"#FFF0EC", "#FFA898", "#8FE0D0", "#4A140C", "#8A544C", "#F04C3C"},
            {"#FFFBE0", "#FFE66A", "#FFA8C8", "#3A3200", "#7A7240", "#E04C8A"},
            {"#FFF8EC", "#8FD8FF", "#B8F08A", "#2A2A2A", "#6A6A6A", "#F0503C", "#FFB3C8"},
            {"#FFF4E0", "#FFD05A", "#FF9FB8", "#3A2000", "#7A6040", "#E0407A", "#8FE0E8"},
            {"#FFEFF6", "#FFB0D0", "#FFD86A", "#3A1030", "#7A5070", "#E0408A", "#A8E8A0"},
            {"#EEF4FF", "#A8C8FF", "#FFB88A", "#0C2450", "#4C6490", "#2C6CE0", "#F0A8FF"},
            {"#F4FFE8", "#C0F08A", "#FFA8D0", "#20380C", "#60784C", "#E0408A", "#8FD0FF"},
            {"#E6FBF8", "#7FE0D0", "#B8A8FF", "#06382C", "#46786C", "#0AA890", "#FFE06A"},
            {"#FFF2EA", "#FFC0A0", "#B0E8C0", "#4A200C", "#8A604C", "#F06A3C", "#FFE88A"},
            {"#FBEFFF", "#E0B0FF", "#A8E8B0", "#341050", "#745890", "#A040E0", "#FFB8B8"},
            {"#EAFBFF", "#8FE0F0", "#FFE08A", "#06343A", "#46747A", "#0A9AB0", "#FFB0A0"},
            {"#FFFAE0", "#FFE06A", "#8FE0C8", "#3A3000", "#7A7040", "#F08A00", "#FFA8C0"},
            {"#F8EEFF", "#D0B0FF", "#FFB0D8", "#2C1050", "#6C5890", "#8A3CE0", "#A8E0FF"},
            {"#FFF6DC", "#FFCF5A", "#A8E08A", "#3A2800", "#7A6840", "#F09000", "#FFA890"}};

    static final int BG = 0, KEY = 1, FN = 2, INK = 3, MUTED = 4, ACCENT = 5;

    /** Readable text colour on top of the accent (dark on gold/yellow, white on the rest). */
    static int onAccent(int theme) {
        int c = color(theme, ACCENT);
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (0.299 * r + 0.587 * g + 0.114 * b) > 160 ? Color.parseColor("#111111") : Color.WHITE;
    }

    /** True for a see-through theme (its background colour is not solid). */
    static boolean glass(int theme) { return theme >= 0 && theme < HEX.length && HEX[theme][BG].length() == 9; }

    /** The colour of the number row: its own colour in a four-colour theme, else the ordinary key colour. */
    static int numberRow(int theme) {
        if (theme < 0 || theme >= HEX.length) theme = 0;
        return Color.parseColor(HEX[theme].length > 6 ? HEX[theme][6] : HEX[theme][KEY]);
    }

    /** True for the colourful Trio / Quad themes (the palette shows all their colours on the square). */
    static boolean combo(int theme) { return theme >= 0 && theme < NAMES.length && (NAMES[theme].startsWith("Trio ") || NAMES[theme].startsWith("Quad ")); }

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

