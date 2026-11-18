package com.tawkit.model.common;

/**
 * Single source of truth for transliterated Hijri month names (English
 * spellings used by tawkit.net, e.g. "Dhul'Qidah"). Shared by the calendar
 * service and the dashboard date header.
 */
public final class HijriMonthNames {

    /** English (transliterated) month names, index 0 = Muharram. */
    public static final String[] ENGLISH = {
            "Muharram", "Safar", "Rabi' Al-Awwal", "Rabi' Al-Thani",
            "Jumada Al-Awwal", "Jumada Al-Thani", "Rajab", "Sha'ban",
            "Ramadan", "Shawwal", "Dhul'Qidah", "Dhul'Hijjah"
    };
    /** French transliterated month names, index 0 = Muharram. */
    public static final String[] FRENCH = {
            "Mouharram", "Safar", "Rabi' al-awwal", "Rabi' ath-thani",
            "Joumada al-oula", "Joumada ath-thania", "Rajab", "Chaabane",
            "Ramadan", "Chawwal", "Dhou al-qi'da", "Dhou al-hijja"
    };

    /** Arabic month names, index 0 = Muharram. */
    public static final String[] ARABIC = {
            "محرم", "صفر", "ربيع الأول", "ربيع الثاني",
            "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
            "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    };

    /** Month name 1..12 in the given language code (en, fr, ar), or "" if out of range. */
    public static String forLanguage(String lang, int month) {
        if (month < 1 || month > 12) return "";
        return switch (lang == null ? "en" : lang) {
            case "fr" -> FRENCH[month - 1];
            case "ar" -> ARABIC[month - 1];
            default -> ENGLISH[month - 1];
        };
    }
    private HijriMonthNames() {
    }

    /** English name of month 1..12, or "" if out of range. */
    public static String english(int month) {
        return (month >= 1 && month <= 12) ? ENGLISH[month - 1] : "";
    }
}
