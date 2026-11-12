package com.tara.bolchi;

/** Bangla and Hindi letter-key layouts: 4 rows, normal and shifted. Row 4 sits between shift and delete. */
final class ScriptKeys {
    private ScriptKeys() {}

    static final String[] BN = {
            "া ি ী ু ূ ৃ ে ৈ ো ৌ",
            "ক খ গ ঘ ঙ চ ছ জ ঝ ঞ",
            "ট ঠ ড ঢ ণ ত থ দ ধ ন",
            "প ফ ব ভ ম য র ল হ"};
    static final String[] BN_SHIFT = {
            "অ আ ই ঈ উ ঊ ঋ এ ঐ ও ঔ",
            "শ ষ স ড় ঢ় য় ৎ ং ঃ ঁ",
            "১ ২ ৩ ৪ ৫ ৬ ৭ ৮ ৯ ০",
            "্ । , ? ! ্য ্র ক্ষ জ্ঞ"};
    static final String[] HI = {
            "ा ि ी ु ू ृ े ै ो ौ",
            "क ख ग घ ङ च छ ज झ ञ",
            "ट ठ ड ढ ण त थ द ध न",
            "प फ ब भ म य र ल व"};
    static final String[] HI_SHIFT = {
            "अ आ इ ई उ ऊ ऋ ए ऐ ओ औ",
            "श ष स ह ड़ ढ़ क़ ज़ फ़ ं",
            "१ २ ३ ४ ५ ६ ७ ८ ९ ०",
            "् । ँ ः ॉ ऑ ज्ञ त्र क्ष"};
}
