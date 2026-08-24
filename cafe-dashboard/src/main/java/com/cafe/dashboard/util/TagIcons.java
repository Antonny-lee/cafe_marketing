package com.cafe.dashboard.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps a review-keyword tag's text to a representative emoji for card UI. */
public final class TagIcons {

    private static final Map<String, String> ICON_BY_KEYWORD = new LinkedHashMap<>();

    static {
        ICON_BY_KEYWORD.put("디저트", "🍰");
        ICON_BY_KEYWORD.put("커피", "☕");
        ICON_BY_KEYWORD.put("음료", "🥤");
        ICON_BY_KEYWORD.put("인테리어", "🛋️");
        ICON_BY_KEYWORD.put("분위기", "🌿");
        ICON_BY_KEYWORD.put("친절", "💛");
        ICON_BY_KEYWORD.put("청결", "🧼");
        ICON_BY_KEYWORD.put("사진", "📸");
        ICON_BY_KEYWORD.put("전망", "🌇");
        ICON_BY_KEYWORD.put("주차", "🅿️");
        ICON_BY_KEYWORD.put("반려동물", "🐾");
        ICON_BY_KEYWORD.put("가격", "💰");
        ICON_BY_KEYWORD.put("양", "🍽️");
        ICON_BY_KEYWORD.put("특별한 메뉴", "✨");
        ICON_BY_KEYWORD.put("공간", "🪑");
        ICON_BY_KEYWORD.put("혼자", "🧑");
        ICON_BY_KEYWORD.put("데이트", "💑");
    }

    private TagIcons() {}

    public static String iconFor(String tagText) {
        if (tagText == null) return "🏷️";
        for (Map.Entry<String, String> e : ICON_BY_KEYWORD.entrySet()) {
            if (tagText.contains(e.getKey())) return e.getValue();
        }
        return "🏷️";
    }
}
