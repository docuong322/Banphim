package com.vietkey.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/** Hằng số và cài đặt dùng chung giữa màn hình cài đặt và bàn phím. */
final class Cfg {
    private Cfg() {}

    static final String FILE = "vk";
    static final String K_THEME = "theme";    // int: chỉ số bộ màu
    static final String K_ALPHA = "alpha";    // int 20..100: độ đậm phím (%)
    static final String K_DIM = "dim";        // int 0..80: độ tối ảnh nền (%)
    static final String K_HEIGHT = "height";  // int 36..70: chiều cao mỗi hàng phím (dp)
    static final String K_SPLIT = "split";    // boolean: tách đôi khi xoay ngang
    static final String K_GAP = "gap";        // int 10..50: độ rộng khoảng trống ở giữa (%)
    static final String K_MODERN = "modern";  // boolean: hoà/thuý (kiểu mới)
    static final String K_HAPTIC = "haptic";  // boolean: rung khi gõ
    static final String K_IMG = "img";        // long: dấu thời gian ảnh nền (0 = không có)
    static final String K_VI = "vi";          // boolean: đang ở chế độ tiếng Việt

    static final String[] THEME_NAMES = {"Tối", "Sáng", "Xanh dương", "Hồng", "Xanh lá"};

    /** nền, phím thường, phím chức năng, màu khi nhấn, màu chữ */
    static final int[][] THEMES = {
            {0xFF1B1B1F, 0xFF3A3A42, 0xFF2A2A30, 0xFF5A5A66, 0xFFFFFFFF},
            {0xFFE8EAED, 0xFFFFFFFF, 0xFFC9CDD3, 0xFFB0B5BD, 0xFF202124},
            {0xFF0B3D5C, 0xFF1F6F9F, 0xFF155578, 0xFF3B8FBF, 0xFFFFFFFF},
            {0xFFFCE4EC, 0xFFFFFFFF, 0xFFF8BBD0, 0xFFF48FB1, 0xFF4A1A2C},
            {0xFF143D2B, 0xFF2E7D5B, 0xFF1F5C42, 0xFF4FA07C, 0xFFFFFFFF},
    };

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static File bgFile(Context c) {
        return new File(c.getFilesDir(), "bg.jpg");
    }
}
