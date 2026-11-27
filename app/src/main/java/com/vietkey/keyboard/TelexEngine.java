package com.vietkey.keyboard;

import java.text.Normalizer;
import java.util.ArrayList;

/**
 * Bộ gõ tiếng Việt kiểu Telex.
 *
 * Cách làm: giữ lại chuỗi phím thô của TỪ đang gõ, và mỗi lần có phím mới thì tính lại
 * toàn bộ từ từ đầu. Nhờ vậy:
 *  - gõ lặp phím để hủy dấu (aaa -> aa, ass -> as, uww -> uw) luôn đúng,
 *  - xóa lùi (backspace) chỉ cần bỏ phím thô cuối rồi tính lại,
 *  - dấu thanh luôn được đặt lại đúng chỗ khi gõ thêm nguyên âm/phụ âm.
 *
 * Quy tắc chính (đã sửa các lỗi hay gặp):
 *  - "uow" / "uwow" / "uo"+"w" luôn ra "ươ" (đủ CẢ HAI dấu móc).
 *  - "ưo" + phụ âm cuối hoặc i/u tự đổi thành "ươ" (dduwocj -> được).
 *  - Bộ gõ KHÔNG BAO GIỜ tự chuyển sang tiếng Anh. Việc đổi ngôn ngữ chỉ do người dùng bấm phím VI/EN.
 */
public final class TelexEngine {

    private final StringBuilder raw = new StringBuilder();
    private boolean modernTone = false; // false: hòa, thúy  |  true: hoà, thuý
    private String text = "";

    public void setModernTone(boolean modern) {
        modernTone = modern;
        text = compose();
    }

    public boolean isEmpty() { return raw.length() == 0; }

    public void clear() {
        raw.setLength(0);
        text = "";
    }

    /** Văn bản đang soạn (đã chuyển thành tiếng Việt). */
    public String text() { return text; }

    /** Thêm một phím chữ cái. Trả về văn bản đang soạn mới. */
    public String type(char c) {
        raw.append(c);
        text = compose();
        return text;
    }

    /** Bỏ phím thô cuối cùng. Trả về văn bản đang soạn mới (có thể rỗng). */
    public String backspace() {
        if (raw.length() > 0) raw.setLength(raw.length() - 1);
        text = compose();
        return text;
    }

    private Word build() {
        Word w = new Word(modernTone);
        for (int i = 0; i < raw.length(); i++) w.feed(raw.charAt(i));
        return w;
    }

    private String compose() {
        return build().render();
    }

    /** Từ đang soạn có dạng một âm tiết tiếng Việt hợp lệ không (kể cả khi chưa biến đổi gì). */
    public boolean looksVietnamese() {
        return build().valid();
    }

    /** Các phím thô đã bấm cho từ đang soạn (dùng để khôi phục từ tiếng Anh). */
    public String raw() { return raw.toString(); }

    /**
     * Từ đang soạn có phải âm tiết tiếng Việt hợp lệ không?
     * Từ không bị biến đổi gì (text == raw) luôn coi là hợp lệ vì không cần khôi phục.
     */
    public boolean isValidWord() {
        if (text.equals(raw.toString())) return true;
        return build().valid();
    }

    /** Dành cho kiểm thử: gõ rồi "nhấn cách", có tự khôi phục từ tiếng Anh. */
    public static String convertAndCommit(String keys, boolean modernTone) {
        TelexEngine e = new TelexEngine();
        e.modernTone = modernTone;
        for (int i = 0; i < keys.length(); i++) e.type(keys.charAt(i));
        return e.isValidWord() ? e.text() : e.raw();
    }

    /** Tiện ích cho kiểm thử: gõ cả chuỗi phím thô và lấy kết quả. */
    public static String convert(String keys, boolean modernTone) {
        TelexEngine e = new TelexEngine();
        e.modernTone = modernTone;
        for (int i = 0; i < keys.length(); i++) e.type(keys.charAt(i));
        return e.text();
    }

    // ------------------------------------------------------------------------------------

    private static final java.util.Set<String> ONSETS = new java.util.HashSet<>(java.util.Arrays.asList(
            "", "b", "c", "ch", "d", "đ", "g", "gh", "gi", "h", "k", "kh", "l", "m", "n", "ng", "ngh",
            "nh", "ph", "qu", "r", "s", "t", "th", "tr", "v", "x"));
    private static final java.util.Set<String> CODAS = new java.util.HashSet<>(java.util.Arrays.asList(
            "", "c", "ch", "m", "n", "ng", "nh", "p", "t"));
    private static final java.util.Set<String> NUCLEI = new java.util.HashSet<>(java.util.Arrays.asList(
            "a", "ă", "â", "e", "ê", "i", "o", "ô", "ơ", "u", "ư", "y",
            "ai", "ao", "au", "ay", "âu", "ây", "eo", "êu", "ia", "iê", "iu",
            "oa", "oă", "oe", "oi", "ôi", "ơi", "ua", "uâ", "uê", "ui",
            "uô", "uơ", "uy", "ưa", "ưi", "ươ", "ưu", "yê", "oo",
            "iêu", "oai", "oay", "oeo", "uây", "uôi", "ươi", "ươu",
            "uyê", "uyu", "uya", "yêu"));

    private static final int NONE = 0, CIRC = 1, BREVE = 2, HORN = 3, STROKE = 4;
    private static final String[] TONE_MARKS = {"", "́", "̀", "̉", "̃", "̣"};

    private static final class VChar {
        char base;          // chữ thường không dấu
        boolean upper;
        int mod = NONE;     // mũ, trăng, móc, gạch đ
        boolean locked;     // ký tự "thật" sau khi hủy dấu, không biến đổi nữa
        boolean fromW;      // ư sinh ra từ phím w đứng sau phụ âm (nhw -> như)

        VChar(char base, boolean upper) {
            this.base = base;
            this.upper = upper;
        }
    }

    private static final class Word {
        final ArrayList<VChar> ch = new ArrayList<>();
        final boolean modern;
        int tone = 0;               // 0 không dấu, 1 sắc, 2 huyền, 3 hỏi, 4 ngã, 5 nặng
        boolean toneBlocked = false; // đã hủy dấu thanh: các phím dấu sau đó là chữ thường

        Word(boolean modern) { this.modern = modern; }

        void feed(char c) {
            char lc = Character.toLowerCase(c);

            // 1) dấu thanh: s f r x j (z = xóa dấu)
            int toneCode = toneOf(lc);
            if (toneCode > 0 && !toneBlocked && cluster() != null) {
                if (tone == toneCode) {          // gõ lại cùng dấu: hủy dấu
                    tone = 0;
                    toneBlocked = true;
                    appendLocked(c);
                } else {
                    tone = toneCode;
                }
                return;
            }
            if (lc == 'z' && tone != 0) {
                tone = 0;
                return;
            }

            // 2) dấu mũ, gạch đ, móc/trăng
            if (lc == 'a' || lc == 'e' || lc == 'o') {
                if (tryCircumflex(lc, c)) return;
            } else if (lc == 'd') {
                if (tryStroke(c)) return;
            } else if (lc == 'w') {
                if (tryW(c)) return;
            } else {
                maybeCompleteHorns(lc);
            }
            append(c);
        }

        // ---------------- biến đổi ----------------

        private boolean tryCircumflex(char lc, char c) {
            int n = ch.size();
            if (n == 0) return false;
            VChar last = ch.get(n - 1);
            if (last.base != lc || last.locked || !isVowelAt(n - 1)) return false;
            if (last.mod == CIRC) {              // aaa -> aa
                last.mod = NONE;
                last.locked = true;
                appendLocked(c);
            } else {
                last.mod = CIRC;
            }
            return true;
        }

        private boolean tryStroke(char c) {
            int n = ch.size();
            if (n == 0) return false;
            VChar last = ch.get(n - 1);
            if (last.base != 'd' || last.locked) return false;
            if (last.mod == STROKE) {            // ddd -> dd
                last.mod = NONE;
                last.locked = true;
                appendLocked(c);
                return true;
            }
            if (last.mod == NONE) {
                last.mod = STROKE;
                return true;
            }
            return false;
        }

        private boolean tryW(char c) {
            int n = ch.size();

            // "nhw" rồi gõ w nữa -> hủy ư, trả về chữ w
            if (n > 0) {
                VChar last = ch.get(n - 1);
                if (last.fromW && last.mod == HORN && !last.locked) {
                    last.base = 'w';
                    last.mod = NONE;
                    last.fromW = false;
                    last.locked = true;
                    return true;
                }
            }

            int[] cl = cluster();
            if (cl == null) {
                // w đứng một mình: sau phụ âm thì thành ư (nhw -> như); đầu từ thì giữ chữ w (www.)
                if (n == 0 || ch.get(n - 1).locked || ch.get(n - 1).base == 'w') return false;
                VChar v = new VChar('u', Character.isUpperCase(c));
                v.mod = HORN;
                v.fromW = true;
                ch.add(v);
                return true;
            }
            int s = cl[0], e = cl[1];

            // a) "uo" -> "ươ": luôn gắn móc cho CẢ HAI nguyên âm
            for (int i = s; i < e; i++) {
                VChar a = ch.get(i), b = ch.get(i + 1);
                if (a.base == 'u' && b.base == 'o' && !a.locked && !b.locked) {
                    if (a.mod == HORN && b.mod == HORN) {   // ươ + w -> uow
                        a.mod = NONE;
                        b.mod = NONE;
                        a.locked = true;
                        b.locked = true;
                        appendLocked(c);
                    } else {
                        a.mod = HORN;
                        b.mod = HORN;
                    }
                    return true;
                }
            }
            // b) có chữ a: "ua" -> "ưa", còn lại -> "ă"
            for (int i = e; i >= s; i--) {
                VChar v = ch.get(i);
                if (v.base == 'a' && !v.locked) {
                    if (i > s && ch.get(i - 1).base == 'u' && !ch.get(i - 1).locked) {
                        toggleHorn(ch.get(i - 1), c);
                    } else if (v.mod == BREVE) {
                        v.mod = NONE;
                        v.locked = true;
                        appendLocked(c);
                    } else {
                        v.mod = BREVE;
                    }
                    return true;
                }
            }
            // c) chữ o -> ơ, d) chữ u -> ư
            for (int i = e; i >= s; i--) {
                VChar v = ch.get(i);
                if (v.base == 'o' && !v.locked) {
                    toggleHorn(v, c);
                    return true;
                }
            }
            for (int i = e; i >= s; i--) {
                VChar v = ch.get(i);
                if (v.base == 'u' && !v.locked) {
                    toggleHorn(v, c);
                    return true;
                }
            }
            return false; // chỉ có e, i, y: w là chữ thường
        }

        private void toggleHorn(VChar v, char c) {
            if (v.mod == HORN) {
                v.mod = NONE;
                v.locked = true;
                appendLocked(c);
            } else {
                v.mod = HORN;
            }
        }

        /** "ưo" + c/m/n/p/t/i/u  ->  "ươ..." (dduwocj -> được, ruwowu -> rượu). */
        private void maybeCompleteHorns(char lc) {
            if ("cimnptu".indexOf(lc) < 0) return;
            int n = ch.size();
            if (n < 2) return;
            VChar u = ch.get(n - 2), o = ch.get(n - 1);
            if (u.base != 'u' || o.base != 'o') return;
            if (!isVowelAt(n - 2) || !isVowelAt(n - 1)) return;
            if (u.locked || o.locked) return;
            if (u.mod == HORN && o.mod == NONE) o.mod = HORN;
        }

        private void append(char c) {
            ch.add(new VChar(Character.toLowerCase(c), Character.isUpperCase(c)));
        }

        private void appendLocked(char c) {
            VChar v = new VChar(Character.toLowerCase(c), Character.isUpperCase(c));
            v.locked = true;
            ch.add(v);
        }

        private static int toneOf(char lc) {
            switch (lc) {
                case 's': return 1;
                case 'f': return 2;
                case 'r': return 3;
                case 'x': return 4;
                case 'j': return 5;
                default: return 0;
            }
        }

        // ---------------- nguyên âm / đặt dấu thanh ----------------

        private boolean isVowelAt(int i) {
            VChar v = ch.get(i);
            if ("aeiouy".indexOf(v.base) < 0) return false;
            if (v.base == 'u' && i > 0 && ch.get(i - 1).base == 'q') return false;           // qu
            if (v.base == 'i' && i == 1 && ch.get(0).base == 'g' && i + 1 < ch.size()
                    && "aeiouy".indexOf(ch.get(i + 1).base) >= 0) return false;                // gi + nguyên âm
            return true;
        }

        /** Cụm nguyên âm cuối cùng của từ: {đầu, cuối} hoặc null nếu không có. */
        private int[] cluster() {
            int e = -1;
            for (int i = ch.size() - 1; i >= 0; i--) {
                if (isVowelAt(i)) { e = i; break; }
            }
            if (e < 0) return null;
            int s = e;
            while (s > 0 && isVowelAt(s - 1)) s--;
            return new int[]{s, e};
        }

        private int tonePosition() {
            int[] cl = cluster();
            if (cl == null) return -1;
            int s = cl[0], e = cl[1], len = e - s + 1;

            // nguyên âm có dấu mũ/trăng/móc được ưu tiên (lấy cái cuối: ươ -> ơ, iê -> ê)
            int pick = -1;
            for (int i = s; i <= e; i++) {
                int m = ch.get(i).mod;
                if (m == CIRC || m == BREVE || m == HORN) pick = i;
            }
            if (pick >= 0) return pick;

            if (len == 1) return s;
            if (len == 2) {
                boolean hasFinal = e < ch.size() - 1;
                if (hasFinal) return e;                                   // toán, tuyết...
                if (modern) {
                    char a = ch.get(s).base, b = ch.get(e).base;
                    boolean special = (a == 'o' && b == 'a') || (a == 'o' && b == 'e') || (a == 'u' && b == 'y');
                    if (special) return e;                                // hoà, khoẻ, thuý
                }
                return s;                                                 // hòa, khỏe, thúy, mái, múa
            }
            return s + 1;                                                 // ngoài, tuổi...
        }

        // ---------------- kiểm tra âm tiết tiếng Việt ----------------

        private String piece(VChar v) {
            char b = v.base;
            if (b == 'd') return v.mod == STROKE ? "đ" : "d";
            if (b == 'a') return v.mod == CIRC ? "â" : v.mod == BREVE ? "ă" : "a";
            if (b == 'e') return v.mod == CIRC ? "ê" : "e";
            if (b == 'o') return v.mod == CIRC ? "ô" : v.mod == HORN ? "ơ" : "o";
            if (b == 'u') return v.mod == HORN ? "ư" : "u";
            return String.valueOf(b);
        }

        boolean valid() {
            int n = ch.size();
            int i = 0;
            StringBuilder onset = new StringBuilder();
            while (i < n && !isVowelAt(i)) onset.append(piece(ch.get(i++)));
            StringBuilder nuc = new StringBuilder();
            while (i < n && isVowelAt(i)) nuc.append(piece(ch.get(i++)));
            if (nuc.length() == 0) return true;                // chưa có nguyên âm: không đụng vào
            StringBuilder coda = new StringBuilder();
            while (i < n) {
                if (isVowelAt(i)) return false;                // nguyên âm sau phụ âm cuối (ana, farther...)
                coda.append(piece(ch.get(i++)));
            }
            if (!ONSETS.contains(onset.toString())) return false;
            if (!NUCLEI.contains(nuc.toString())) return false;
            String c = coda.toString();
            if (!CODAS.contains(c)) return false;
            // vần kết thúc bằng c, ch, p, t chỉ có dấu sắc hoặc nặng
            if ((c.equals("c") || c.equals("ch") || c.equals("p") || c.equals("t"))
                    && (tone == 2 || tone == 3 || tone == 4)) return false;
            return true;
        }

        String render() {
            int tonePos = tone != 0 ? tonePosition() : -1;
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < ch.size(); i++) {
                VChar v = ch.get(i);
                String piece;
                if (v.base == 'd' && v.mod == STROKE) {
                    piece = "đ";
                } else {
                    StringBuilder t = new StringBuilder().append(v.base);
                    if (v.mod == CIRC) t.append('̂');
                    else if (v.mod == BREVE) t.append('̆');
                    else if (v.mod == HORN) t.append('̛');
                    if (i == tonePos) t.append(TONE_MARKS[tone]);
                    piece = Normalizer.normalize(t, Normalizer.Form.NFC);
                }
                out.append(v.upper ? piece.toUpperCase() : piece);
            }
            return out.toString();
        }
    }
}
