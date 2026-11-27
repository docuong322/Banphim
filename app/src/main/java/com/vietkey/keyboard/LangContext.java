package com.vietkey.keyboard;

import java.util.ArrayDeque;

/**
 * Quyết định một từ vừa gõ xong là tiếng Việt hay tiếng Anh, dựa vào chính từ đó và các từ gõ ngay trước.
 *
 *  - Từ không thể là âm tiết tiếng Việt (farther, text, windows...)  -> trả về đúng phím đã gõ (tiếng Anh).
 *  - Từ vừa là tiếng Việt hợp lệ vừa có thể là tiếng Anh (six -> sĩ, too -> tô, see -> sê...):
 *      + 2 trong 3 từ gần nhất là tiếng Anh  -> giữ tiếng Anh   ("for five six" -> six)
 *      + còn lại                              -> giữ tiếng Việt  ("ca sĩ" -> sĩ)
 *  - Từ có dấu đặc trưng tiếng Việt (đ, ươ, uô, iê, yê) luôn giữ tiếng Việt, kể cả khi đang gõ tiếng Anh.
 */
final class LangContext {

    private final ArrayDeque<Boolean> history = new ArrayDeque<>(); // true = Việt, false = Anh

    void clear() {
        history.clear();
    }

    private void push(boolean vietnamese) {
        history.addLast(vietnamese);
        while (history.size() > 3) history.removeFirst();
    }

    private boolean englishContext() {
        int en = 0, vi = 0;
        for (Boolean b : history) {
            if (b) vi++;
            else en++;
        }
        return en > vi;
    }

    private static boolean strongVietnamese(String s) {
        String t = s.toLowerCase();
        return t.contains("đ") || t.contains("ươ") || t.contains("uô")
                || t.contains("iê") || t.contains("yê");
    }

    /** Trả về văn bản cần để lại trong ô nhập cho từ đang soạn của bộ gõ. */
    String decide(TelexEngine e, boolean restoreEnabled) {
        String shown = e.text();
        if (!restoreEnabled) return shown;
        // Gõ đôi để hủy dấu (iss -> is, ass -> as): người dùng chủ động muốn tiếng Anh, giữ nguyên kết quả.
        if (e.userUndid()) {
            push(false);
            return shown;
        }
        String raw = e.raw();
        boolean modified = !shown.equals(raw);
        boolean valid = e.looksVietnamese();

        if (modified) {
            if (!valid) {                                  // farther, text, off...
                push(false);
                return raw;
            }
            // Chỉ từ thật sự là tiếng Anh (six, too, see, box...) mới bị trả về giữa câu tiếng Anh.
            // Từ không có trong từ điển (lại, nhé, được...) luôn giữ tiếng Việt.
            if (!strongVietnamese(shown) && englishContext()
                    && EnglishCollisions.contains(raw.toLowerCase())) {
                push(false);
                return raw;
            }
            push(true);
            return shown;
        }
        if (!valid) push(false);                           // five, love, iPhone...: chắc chắn là tiếng Anh
        return shown;                                      // ca, an, to...: dùng được cho cả hai, không tính
    }
}
