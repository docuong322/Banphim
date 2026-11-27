package com.vietkey.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/** Hằng số và cài đặt dùng chung giữa màn hình cài đặt và bàn phím. */
final class Cfg {
    private Cfg() {}

    static final String FILE = "vk";
    static final String K_ALPHA = "alpha";     // int 20..100: độ đậm phím (%)
    static final String K_DIM = "dim";         // int 0..80: độ tối ảnh nền (%)
    static final String K_HEIGHT = "height";   // int 36..70: chiều cao mỗi hàng phím (dp)
    static final String K_SPLIT = "split";     // boolean: tách đôi khi xoay ngang
    static final String K_GAP = "gap";         // int 10..50: độ rộng khoảng trống ở giữa (%)
    static final String K_MODERN = "modern";   // boolean: hoà/thuý (kiểu mới)
    static final String K_HAPTIC = "haptic";   // boolean: rung khi gõ
    static final String K_URI_VI = "uri_vi";   // boolean: gõ tiếng Việt trong ô URL / thanh địa chỉ
    static final String K_RESTORE = "restore"; // boolean: tự nhận diện và khôi phục từ tiếng Anh
    static final String K_COMPAT = "compat";   // boolean: chế độ tương thích (không dùng gạch chân soạn thảo)
    static final String K_IMG = "img";         // long: dấu thời gian ảnh nền (0 = không có)
    static final String K_VI = "vi";           // boolean: đang ở chế độ tiếng Việt

    static final String K_C_BG = "c_bg";
    static final String K_C_KEY = "c_key";
    static final String K_C_SPECIAL = "c_special";
    static final String K_C_TEXT = "c_text";

    /** Thứ tự: 0 nền, 1 phím thường, 2 phím chức năng, 3 chữ. */
    static final String[] COLOR_KEYS = {K_C_BG, K_C_KEY, K_C_SPECIAL, K_C_TEXT};
    static final String[] COLOR_LABELS = {"Màu nền", "Màu phím", "Màu phím chức năng", "Màu chữ"};
    /** Vị trí tương ứng trong mảng THEMES. */
    private static final int[] COLOR_THEME_IDX = {0, 1, 2, 4};

    static final String[] THEME_NAMES = {
            "Tối", "Sáng", "Xanh dương", "Hồng", "Xanh lá", "Tím", "Cam", "Đen tuyền (AMOLED)", "Đỏ rượu"};

    /** nền, phím thường, phím chức năng, (không dùng), màu chữ */
    static final int[][] THEMES = {
            {0xFF1B1B1F, 0xFF3A3A42, 0xFF2A2A30, 0, 0xFFFFFFFF},
            {0xFFE8EAED, 0xFFFFFFFF, 0xFFC9CDD3, 0, 0xFF202124},
            {0xFF0B3D5C, 0xFF1F6F9F, 0xFF155578, 0, 0xFFFFFFFF},
            {0xFFFCE4EC, 0xFFFFFFFF, 0xFFF8BBD0, 0, 0xFF4A1A2C},
            {0xFF143D2B, 0xFF2E7D5B, 0xFF1F5C42, 0, 0xFFFFFFFF},
            {0xFF2A1B3D, 0xFF5B3F8C, 0xFF43306A, 0, 0xFFFFFFFF},
            {0xFFFFF3E0, 0xFFFFFFFF, 0xFFFFCC80, 0, 0xFF3E2723},
            {0xFF000000, 0xFF202020, 0xFF101010, 0, 0xFFFFFFFF},
            {0xFF3B0A14, 0xFF7A1F33, 0xFF5A1526, 0, 0xFFFFE9ED},
    };

    static int themeColor(int theme, int colorIdx) {
        return THEMES[theme][COLOR_THEME_IDX[colorIdx]];
    }

    static int color(SharedPreferences p, int colorIdx) {
        return p.getInt(COLOR_KEYS[colorIdx], themeColor(0, colorIdx));
    }

    /** Màu phím khi đang nhấn: sáng hơn nếu phím tối, tối hơn nếu phím sáng. */
    static int pressColor(int key) {
        int r = (key >> 16) & 0xFF, g = (key >> 8) & 0xFF, b = key & 0xFF;
        double lum = 0.299 * r + 0.587 * g + 0.114 * b;
        int target = lum < 128 ? 255 : 0;
        float t = lum < 128 ? 0.30f : 0.22f;
        r += (int) ((target - r) * t);
        g += (int) ((target - g) * t);
        b += (int) ((target - b) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static File bgFile(Context c) {
        return new File(c.getFilesDir(), "bg.jpg");
    }
}
