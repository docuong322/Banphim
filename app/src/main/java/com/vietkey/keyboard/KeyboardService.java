package com.vietkey.keyboard;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Bàn phím tiếng Việt (Telex).
 *
 *  - "uow" / "uwow" luôn ra "ươ" (đủ cả hai dấu móc): xử lý trong TelexEngine.
 *  - Không tự chuyển sang EN: chế độ VI/EN chỉ đổi khi bấm phím VI/EN và được nhớ lại.
 *  - Từ tiếng Anh gõ giữa câu tự khôi phục khi nhấn cách (xem LangContext).
 *  - Gõ trực tiếp (xóa và chèn lại) để chạy được ở mọi ô nhập, kể cả ô tìm kiếm và trình duyệt.
 *  - Quay lại sửa từ cũ: dựng lại từ trước con trỏ rồi gõ tiếp vẫn ghép được dấu.
 *  - Hàng số, bong bóng phóng to phím, giữ phím để chọn chữ hoa hoặc số/ký tự, vuốt phím cách để di chuyển con trỏ.
 */
public class KeyboardService extends InputMethodService {

    private static final String[] NUM_ROW = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
    private static final String[][] LETTER_ROWS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"}
    };
    private static final String[] BOTTOM = {"SYM", ",", "LANG", "SPACE", ".", "ENTER"};
    private static final String[] SYM1 = {"@", "#", "$", "%", "&", "-", "+", "(", ")", "/"};
    private static final String[] SYM2_OLD = {"=", "~", "*", "\"", "'", ":", ";", "!", "?", "DEL"};
    private static final String[] SYM2_NEW = {"*", "\"", "'", ":", ";", "!", "?", "_", "=", "~"};
    private static final String[] SYM3_NEW = {"[", "]", "{", "}", "<", ">", "\\", "|", "^", "DEL"};

    /** Giữ phím chữ thì ngoài chữ hoa còn có số hoặc ký tự này (t: T, 5   a: A, @). */
    private static final HashMap<String, String> ALT = new HashMap<>();

    static {
        String top = "qwertyuiop", digits = "1234567890";
        for (int i = 0; i < top.length(); i++) ALT.put(String.valueOf(top.charAt(i)), String.valueOf(digits.charAt(i)));
        String r2 = "asdfghjkl", s2 = "@#$%&-+()";
        for (int i = 0; i < r2.length(); i++) ALT.put(String.valueOf(r2.charAt(i)), String.valueOf(s2.charAt(i)));
        String r3 = "zxcvbnm", s3 = "*\"':;!?";
        for (int i = 0; i < r3.length(); i++) ALT.put(String.valueOf(r3.charAt(i)), String.valueOf(s3.charAt(i)));
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TelexEngine engine = new TelexEngine();
    private final LangContext lang = new LangContext();
    private final ArrayList<TextView> letterViews = new ArrayList<>();

    private SharedPreferences prefs;
    private FrameLayout root;
    private LinearLayout keysBox;
    private ImageView shiftView;

    private PopupWindow preview;        // bong bóng phóng to phím đang nhấn
    private TextView previewTv;
    private View previewOwner;
    private PopupWindow altPopup;       // bảng chọn khi giữ phím
    private TextView[] altViews = new TextView[0];
    private int altSel = 0;
    private int altLeft = 0;
    private int altItemW = 0;
    private String[] altOpts = new String[0];

    private boolean viMode = true;
    private boolean directMode = false; // mật khẩu, email, số: gõ thẳng
    private boolean compat = true;      // gõ trực tiếp (xóa và chèn lại)
    private boolean symbols = false;
    private int shift = 0;              // 0 tắt, 1 một lần, 2 khóa
    private long lastShiftTap = 0;
    private long lastEdit = 0;
    private int compLen = 0;            // độ dài từ đang chèn (gõ trực tiếp)
    private boolean nullInput = false;  // ô nhập kiểu TYPE_NULL: chỉ nhận phím, không nhận văn bản
    private String builtSig = "";

    private int cBg, cKey, cSpecial, cPress, cText;
    private int keyAlpha;
    private int rowHeightDp;
    private boolean splitNow;
    private float gapWeight;

    // ------------------------------------------------------------------ vòng đời

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = Cfg.prefs(this);
        viMode = prefs.getBoolean(Cfg.K_VI, true);
    }

    @Override
    public void onDestroy() {
        hidePreview(null);
        hideAlt();
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        loadStyle();
        root = new FrameLayout(this);
        root.setBackgroundColor(cBg);

        if (prefs.getLong(Cfg.K_IMG, 0) > 0 && Cfg.bgFile(this).exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(Cfg.bgFile(this).getAbsolutePath());
            if (bmp != null) {
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(bmp);
                root.addView(iv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                int dim = prefs.getInt(Cfg.K_DIM, 25);
                View shade = new View(this);
                shade.setBackgroundColor((Math.min(80, dim) * 255 / 100) << 24);
                root.addView(shade, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        }

        keysBox = new LinearLayout(this);
        keysBox.setOrientation(LinearLayout.VERTICAL);
        keysBox.setPadding(dp(3), dp(4), dp(3), dp(8));
        root.addView(keysBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildKeys();
        builtSig = signature();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        engine.clear();
        lang.clear();
        compLen = 0;
        hidePreview(null);
        hideAlt();
        engine.setModernTone(prefs.getBoolean(Cfg.K_MODERN, false));
        viMode = prefs.getBoolean(Cfg.K_VI, true);
        compat = prefs.getBoolean(Cfg.K_COMPAT, true);
        nullInput = info.inputType == InputType.TYPE_NULL;

        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int var = info.inputType & InputType.TYPE_MASK_VARIATION;
        directMode = cls != InputType.TYPE_CLASS_TEXT
                || var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                // Thanh địa chỉ trình duyệt (Chrome, Cốc Cốc) là ô URL: mặc định vẫn gõ tiếng Việt
                || (var == InputType.TYPE_TEXT_VARIATION_URI && !prefs.getBoolean(Cfg.K_URI_VI, true));
        symbols = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
                || cls == InputType.TYPE_CLASS_DATETIME;

        shift = 0;
        if (!signature().equals(builtSig)) {
            setInputView(onCreateInputView());
        } else {
            buildKeys();
        }
        autoCaps();
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        engine.clear();
        compLen = 0;
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        hidePreview(null);
        hideAlt();
        finishComposing(); // đóng bàn phím giữa chừng: chốt từ đang gõ
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hidePreview(null);
        hideAlt();
        setInputView(onCreateInputView());
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false; // không dùng ô soạn thảo toàn màn hình khi xoay ngang
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candStart, int candEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candStart, candEnd);
        // Thông báo đến ngay sau khi tự gõ thì bỏ qua. Về sau, nếu từ đang soạn không còn nằm ngay
        // trước con trỏ (người dùng chạm sang chỗ khác) thì bắt đầu từ mới. Kiểm tra bằng nội dung thật,
        // không dựa vào vùng soạn thảo mà ô nhập báo về, vì nhiều ô (tìm kiếm, trình duyệt) báo sai.
        if (SystemClock.uptimeMillis() - lastEdit < 400 || engine.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && !compositionIntact(ic)) resetWord(ic);
    }

    /** Từ đang soạn còn nằm ngay trước con trỏ không? Trả về true nếu ô nhập không cho đọc để kiểm tra. */
    private boolean compositionIntact(InputConnection ic) {
        String shown = engine.text();
        if (shown.isEmpty()) return true;
        CharSequence before = ic.getTextBeforeCursor(shown.length(), 0);
        if (before == null || before.length() == 0) return true;
        return before.toString().equals(shown);
    }

    private void resetWord(InputConnection ic) {
        engine.clear();
        lang.clear();
        compLen = 0;
        if (!compat && ic != null) ic.finishComposingText();
    }

    // ------------------------------------------------------------------ dựng bàn phím

    private String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(getResources().getConfiguration().orientation);
        for (String k : new String[]{Cfg.K_ALPHA, Cfg.K_DIM, Cfg.K_HEIGHT, Cfg.K_SPLIT, Cfg.K_GAP, Cfg.K_IMG,
                Cfg.K_C_BG, Cfg.K_C_KEY, Cfg.K_C_SPECIAL, Cfg.K_C_TEXT}) {
            sb.append('|').append(String.valueOf(prefs.getAll().get(k)));
        }
        return sb.toString();
    }

    private void loadStyle() {
        cBg = Cfg.color(prefs, 0);
        cKey = Cfg.color(prefs, 1);
        cSpecial = Cfg.color(prefs, 2);
        cText = Cfg.color(prefs, 3);
        cPress = Cfg.pressColor(cKey);
        keyAlpha = Math.max(20, Math.min(100, prefs.getInt(Cfg.K_ALPHA, 90)));
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        float h = Math.max(36, Math.min(70, prefs.getInt(Cfg.K_HEIGHT, 50)));
        rowHeightDp = Math.round(landscape ? h * 0.8f : h);
        splitNow = landscape && prefs.getBoolean(Cfg.K_SPLIT, true);
        gapWeight = Math.max(10, Math.min(50, prefs.getInt(Cfg.K_GAP, 25))) / 10f;
    }

    private ArrayList<String[]> layoutRows() {
        boolean numRow = prefs.getBoolean(Cfg.K_NUMROW, true);
        ArrayList<String[]> rows = new ArrayList<>();
        if (!symbols) {
            if (numRow) rows.add(NUM_ROW);
            rows.add(LETTER_ROWS[0]);
            rows.add(LETTER_ROWS[1]);
            rows.add(LETTER_ROWS[2]);
        } else {
            rows.add(NUM_ROW);
            rows.add(SYM1);
            if (numRow) {
                rows.add(SYM2_NEW);
                rows.add(SYM3_NEW);
            } else {
                rows.add(SYM2_OLD);
            }
        }
        rows.add(BOTTOM);
        return rows;
    }

    private void buildKeys() {
        if (keysBox == null) return;
        keysBox.removeAllViews();
        letterViews.clear();
        shiftView = null;
        for (String[] ids : layoutRows()) keysBox.addView(buildRow(ids));
        refreshLetters();
    }

    private LinearLayout buildRow(String[] ids) {
        boolean bottom = false;
        for (String id : ids) if (id.equals("SPACE")) bottom = true;
        boolean numRow = ids == NUM_ROW;
        int heightDp = numRow ? Math.round(rowHeightDp * 0.78f) : rowHeightDp;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));

        int splitAt = (splitNow && !bottom) ? (ids.length + 1) / 2 : -1;
        boolean pad = !symbols && ids[0].equals("a");
        if (pad) row.addView(spacer(0.5f));

        for (int i = 0; i < ids.length; i++) {
            if (i == splitAt) row.addView(spacer(gapWeight));
            String id = ids[i];
            if (id.equals("SPACE")) {
                if (splitNow) {
                    row.addView(makeKey(id, "", 3f));
                    row.addView(spacer(gapWeight));
                    row.addView(makeKey(id, "", 3f));
                } else {
                    row.addView(makeKey(id, spaceLabel(), 6f));
                }
                continue;
            }
            if (id.equals("SHIFT") || id.equals("ENTER")) {
                row.addView(makeIconKey(id, weightFor(id)));
                continue;
            }
            row.addView(makeKey(id, labelFor(id), weightFor(id)));
        }
        if (pad) row.addView(spacer(0.5f));
        return row;
    }

    private String spaceLabel() {
        return splitNow ? "" : (viMode ? "Tiếng Việt" : "English");
    }

    private View spacer(float weight) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight));
        return v;
    }

    private String labelFor(String id) {
        switch (id) {
            case "DEL": return "⌫";
            case "SYM": return symbols ? "ABC" : "?123";
            case "LANG": return viMode ? "VI" : "EN";
            default: return id;
        }
    }

    private float weightFor(String id) {
        switch (id) {
            case "SHIFT":
            case "DEL":
            case "SYM":
            case "ENTER":
                return 1.5f;
            case "LANG":
                return 1.2f;
            default:
                return 1f;
        }
    }

    private boolean isSpecial(String id) {
        return id.length() > 1 && !id.equals("SPACE");
    }

    private TextView makeKey(String id, String label, float weight) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(cText);
        tv.setSingleLine(true);
        boolean special = isSpecial(id);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, special ? 15 : (id.equals("SPACE") ? 14 : 21));
        tv.setBackground(keyBackground(special));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(dp(2.5f), dp(3), dp(2.5f), dp(3));
        tv.setLayoutParams(lp);
        tv.setTag(id);
        tv.setOnTouchListener(new KeyTouch(id));
        if (id.length() == 1 && Character.isLetter(id.charAt(0))) letterViews.add(tv);
        return tv;
    }

    /** Phím Shift và Enter: vẽ mũi tên bằng hình vector, to và cân đối ở mọi cỡ phím. */
    private ImageView makeIconKey(String id, float weight) {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setPadding(dp(7), dp(7), dp(7), dp(7));
        iv.setColorFilter(cText, PorterDuff.Mode.SRC_IN);
        iv.setBackground(keyBackground(true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(dp(2.5f), dp(3), dp(2.5f), dp(3));
        iv.setLayoutParams(lp);
        iv.setTag(id);
        iv.setOnTouchListener(new KeyTouch(id));
        if (id.equals("ENTER")) {
            iv.setImageResource(R.drawable.ic_enter);
        } else {
            shiftView = iv;
            iv.setImageResource(shiftIcon());
        }
        return iv;
    }

    private int shiftIcon() {
        return shift == 2 ? R.drawable.ic_caps : shift == 1 ? R.drawable.ic_shift_on : R.drawable.ic_shift;
    }

    private StateListDrawable keyBackground(boolean special) {
        int base = special ? cSpecial : cKey;
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(withAlpha(cPress, Math.max(keyAlpha, 80))));
        s.addState(new int[]{}, rounded(withAlpha(base, keyAlpha)));
        return s;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(7));
        return g;
    }

    private static int withAlpha(int color, int percent) {
        return (color & 0x00FFFFFF) | ((percent * 255 / 100) << 24);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void refreshLetters() {
        boolean up = shift != 0;
        for (TextView tv : letterViews) {
            String id = (String) tv.getTag();
            tv.setText(up ? id.toUpperCase() : id);
        }
        if (shiftView != null) {
            shiftView.setImageResource(shiftIcon());
        }
    }

    private void haptic(View v) {
        if (prefs.getBoolean(Cfg.K_HAPTIC, true)) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    // ------------------------------------------------------------------ bong bóng phóng to phím

    private String previewText(String id) {
        return (Character.isLetter(id.charAt(0)) && shift != 0) ? id.toUpperCase() : id;
    }

    private void showPreview(View key, String text) {
        if (!prefs.getBoolean(Cfg.K_PREVIEW, true)) return;
        try {
            if (previewTv == null) {
                previewTv = new TextView(this);
                previewTv.setGravity(Gravity.CENTER);
                previewTv.setSingleLine(true);
                previewTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
                preview = new PopupWindow(previewTv, dp(60), dp(70), false);
                preview.setTouchable(false);
                preview.setClippingEnabled(false);
            }
            GradientDrawable g = new GradientDrawable();
            g.setColor(cKey | 0xFF000000);
            g.setCornerRadius(dp(10));
            g.setStroke(dp(1), cPress);
            previewTv.setBackground(g);
            previewTv.setTextColor(cText);
            previewTv.setText(text);

            int w = Math.max(key.getWidth() + dp(16), dp(52));
            int h = key.getHeight() + dp(24);
            int[] loc = new int[2];
            key.getLocationOnScreen(loc);
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int x = Math.max(dp(2), Math.min(screenW - w - dp(2), loc[0] + key.getWidth() / 2 - w / 2));
            int y = Math.max(0, loc[1] - h + dp(10));
            previewOwner = key;
            if (preview.isShowing()) {
                preview.update(x, y, w, h);
            } else {
                preview.setWidth(w);
                preview.setHeight(h);
                preview.showAtLocation(root, Gravity.NO_GRAVITY, x, y);
            }
        } catch (Exception ignored) {
            // Không hiện được bong bóng thì vẫn gõ bình thường
        }
    }

    /** owner == null: ẩn bất kể phím nào. */
    private void hidePreview(View owner) {
        try {
            if (preview != null && preview.isShowing() && (owner == null || owner == previewOwner)) {
                preview.dismiss();
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ bảng chọn khi giữ phím

    /** Các lựa chọn khi giữ phím chữ: chữ hoa (hoặc thường nếu đang bật Shift) và số/ký tự. */
    private String[] altOptions(String id) {
        if (symbols || id.length() != 1) return null;
        String alt = ALT.get(id);
        if (alt == null) return null;
        return new String[]{shift != 0 ? id : id.toUpperCase(), alt};
    }

    private void showAlt(View key, String[] opts) {
        try {
            hideAlt();
            altOpts = opts;
            altSel = 0;
            altItemW = dp(48);
            int itemH = dp(56);
            LinearLayout strip = new LinearLayout(this);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(cKey | 0xFF000000);
            bg.setCornerRadius(dp(10));
            bg.setStroke(dp(1), cPress);
            strip.setBackground(bg);
            altViews = new TextView[opts.length];
            for (int i = 0; i < opts.length; i++) {
                TextView t = new TextView(this);
                t.setText(opts[i]);
                t.setGravity(Gravity.CENTER);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                t.setTextColor(cText);
                strip.addView(t, new LinearLayout.LayoutParams(altItemW, itemH));
                altViews[i] = t;
            }
            paintAlt();

            int w = altItemW * opts.length;
            int[] loc = new int[2];
            key.getLocationOnScreen(loc);
            int screenW = getResources().getDisplayMetrics().widthPixels;
            altLeft = Math.max(dp(4), Math.min(screenW - w - dp(4), loc[0] + key.getWidth() / 2 - w / 2));
            int y = Math.max(0, loc[1] - itemH - dp(6));
            altPopup = new PopupWindow(strip, w, itemH, false);
            altPopup.setTouchable(false);
            altPopup.setClippingEnabled(false);
            altPopup.showAtLocation(root, Gravity.NO_GRAVITY, altLeft, y);
        } catch (Exception e) {
            altPopup = null;
        }
    }

    private void paintAlt() {
        for (int i = 0; i < altViews.length; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(8));
            g.setColor(i == altSel ? cPress | 0xFF000000 : 0x00000000);
            altViews[i].setBackground(g);
        }
    }

    private void highlightAlt(float rawX) {
        if (altPopup == null || altOpts.length == 0) return;
        int idx = (int) ((rawX - altLeft) / altItemW);
        idx = Math.max(0, Math.min(altOpts.length - 1, idx));
        if (idx != altSel) {
            altSel = idx;
            paintAlt();
        }
    }

    private String currentAlt() {
        return (altPopup != null && altSel < altOpts.length) ? altOpts[altSel] : null;
    }

    private void hideAlt() {
        try {
            if (altPopup != null && altPopup.isShowing()) altPopup.dismiss();
        } catch (Exception ignored) {
        }
        altPopup = null;
    }

    // ------------------------------------------------------------------ xử lý phím

    private final class KeyTouch implements View.OnTouchListener {
        private final String id;
        private View keyView;
        private boolean longFired = false;  // đã hiện bảng chọn hoặc đã vào chế độ di chuyển con trỏ
        private boolean cursorMode = false;
        private boolean altShown = false;
        private float downX, downY, lastX, lastY;

        private final Runnable longRun = new Runnable() {
            @Override public void run() {
                if (id.equals("SPACE")) {
                    cursorMode = true;
                    longFired = true;
                    finishComposing();
                    lang.clear();
                    setSpaceHint(keyView, true);
                    haptic(keyView);
                } else {
                    String[] opts = altOptions(id);
                    if (opts != null) {
                        longFired = true;
                        altShown = true;
                        hidePreview(null);
                        showAlt(keyView, opts);
                        haptic(keyView);
                    }
                }
            }
        };
        private final Runnable repeatRun = new Runnable() {
            @Override public void run() {
                handleDelete();
                handler.postDelayed(this, 55);
            }
        };

        KeyTouch(String id) { this.id = id; }

        private void cancelAll() {
            handler.removeCallbacks(longRun);
            handler.removeCallbacks(repeatRun);
        }

        /** Vuốt ngang để di chuyển trái phải, vuốt dọc để lên xuống dòng. */
        private void moveCursor(MotionEvent e) {
            float stepX = dp(11), stepY = dp(30);
            int nx = (int) ((e.getRawX() - lastX) / stepX);
            if (nx != 0) {
                nx = Math.max(-12, Math.min(12, nx));
                int code = nx > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
                for (int i = 0; i < Math.abs(nx); i++) sendDownUpKeyEvents(code);
                lastX += nx * stepX;
                haptic(keyView);
            }
            int ny = (int) ((e.getRawY() - lastY) / stepY);
            if (ny != 0) {
                ny = Math.max(-6, Math.min(6, ny));
                int code = ny > 0 ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP;
                for (int i = 0; i < Math.abs(ny); i++) sendDownUpKeyEvents(code);
                lastY += ny * stepY;
                haptic(keyView);
            }
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    keyView = v;
                    v.setPressed(true);
                    haptic(v);
                    longFired = false;
                    cursorMode = false;
                    altShown = false;
                    downX = lastX = e.getRawX();
                    downY = lastY = e.getRawY();
                    if (id.equals("DEL")) {
                        handleDelete();
                        handler.postDelayed(repeatRun, 400);
                    } else if (id.equals("SPACE")) {
                        handler.postDelayed(longRun, 320);
                    } else if (id.length() == 1) {
                        showPreview(v, previewText(id));
                        if (altOptions(id) != null) handler.postDelayed(longRun, 380);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (cursorMode) {
                        moveCursor(e);
                    } else if (altShown) {
                        highlightAlt(e.getRawX());
                    } else if (id.equals("SPACE") && !longFired
                            && Math.abs(e.getRawX() - downX) + Math.abs(e.getRawY() - downY) > dp(14)) {
                        handler.removeCallbacks(longRun); // vuốt khi chưa giữ đủ lâu: chỉ là gõ cách
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    v.setPressed(false);
                    cancelAll();
                    hidePreview(v);
                    if (altShown) {
                        String s = currentAlt();
                        hideAlt();
                        if (s != null) typeExact(s);
                        return true;
                    }
                    if (cursorMode) {
                        setSpaceHint(v, false);
                        return true;
                    }
                    if (!id.equals("DEL") && !longFired) onKey(id);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    cancelAll();
                    hidePreview(v);
                    hideAlt();
                    if (cursorMode) setSpaceHint(v, false);
                    return true;
                default:
                    return true;
            }
        }
    }

    private void setSpaceHint(View v, boolean active) {
        if (!(v instanceof TextView)) return;
        ((TextView) v).setText(active ? (splitNow ? "‹  ›" : "‹  kéo để di chuyển con trỏ  ›")
                : spaceLabel());
    }

    private void onKey(String id) {
        switch (id) {
            case "SHIFT": {
                long now = SystemClock.uptimeMillis();
                if (now - lastShiftTap < 350) shift = 2;
                else shift = (shift == 0) ? 1 : 0;
                lastShiftTap = now;
                refreshLetters();
                break;
            }
            case "SPACE":
                finishComposing();
                commit(" ");
                autoCaps();
                break;
            case "ENTER":
                doEnter();
                break;
            case "SYM":
                finishComposing();
                symbols = !symbols;
                buildKeys();
                break;
            case "LANG":
                finishComposing();
                lang.clear();
                viMode = !viMode;
                prefs.edit().putBoolean(Cfg.K_VI, viMode).apply();
                buildKeys();
                break;
            case "DEL":
                handleDelete();
                break;
            default:
                handleChar(id);
        }
    }

    /** Phím thường: áp dụng Shift rồi gõ. */
    private void handleChar(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        char c = s.charAt(0);
        boolean letter = c < 128 && Character.isLetter(c);
        if (!letter) {
            finishComposing();
            commit(s);
            return;
        }
        if (shift != 0) c = Character.toUpperCase(c);
        if (shift == 1) {
            shift = 0;
            refreshLetters();
        }
        typeLetter(ic, c);
    }

    /** Ký tự chọn từ bảng giữ phím: dùng đúng như đã chọn, không đổi theo Shift. */
    private void typeExact(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        char c = s.charAt(0);
        if (c < 128 && Character.isLetter(c)) {
            typeLetter(ic, c);
        } else {
            finishComposing();
            commit(s);
        }
    }

    private void typeLetter(InputConnection ic, char c) {
        if (viMode && !directMode) {
            // Chạm sang chỗ khác từ lần gõ trước: bỏ từ cũ
            if (!engine.isEmpty() && SystemClock.uptimeMillis() - lastEdit > 100 && !compositionIntact(ic)) {
                resetWord(ic);
            }
            // Quay lại sửa từ cũ: dựng lại từ đang nằm trước con trỏ để gõ tiếp vẫn ghép được dấu
            if (engine.isEmpty()) trySeed(ic);
            showComposition(ic, engine.type(c));
        } else {
            commit(String.valueOf(c));
        }
    }

    /** Nếu ngay trước con trỏ là một từ, nạp từ đó vào bộ gõ. */
    private void trySeed(InputConnection ic) {
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) return;         // đang bôi đen: gõ sẽ thay phần bôi đen
        CharSequence before = ic.getTextBeforeCursor(14, 0);
        if (before == null || before.length() == 0) return;
        String b = before.toString();
        int i = b.length();
        while (i > 0 && Character.isLetter(b.charAt(i - 1))) i--;
        String word = b.substring(i);
        if (word.isEmpty() || word.length() > 12) return;
        if (i == 0 && b.length() >= 14) return;              // chuỗi chữ quá dài: không phải một âm tiết
        if (!engine.seed(word)) return;
        if (compat) {
            compLen = word.length();                         // showComposition sẽ xóa rồi chèn lại
        } else {
            ic.deleteSurroundingText(word.length(), 0);
        }
    }

    /** Hiển thị từ đang soạn: xóa-và-chèn lại (gõ trực tiếp) hoặc gạch chân (composing). */
    private void showComposition(InputConnection ic, String text) {
        lastEdit = SystemClock.uptimeMillis();
        if (compat) {
            ic.beginBatchEdit();
            if (compLen > 0) ic.deleteSurroundingText(compLen, 0);
            ic.commitText(text, 1);
            ic.endBatchEdit();
            compLen = text.length();
        } else {
            ic.setComposingText(text, 1);
        }
    }

    private void handleDelete() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        lastEdit = SystemClock.uptimeMillis();
        if (!engine.isEmpty()) {
            String t = engine.backspace();
            if (t.isEmpty()) {
                if (compat) {
                    if (compLen > 0) ic.deleteSurroundingText(compLen, 0);
                    compLen = 0;
                } else {
                    ic.commitText("", 1);
                }
            } else {
                showComposition(ic, t);
            }
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        }
    }

    private void doEnter() {
        finishComposing();
        lang.clear();
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic == null) return;
        int action = ei != null ? (ei.imeOptions & EditorInfo.IME_MASK_ACTION) : 0;
        if (ei != null && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
                && action != EditorInfo.IME_ACTION_NONE
                && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action);
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
        autoCaps();
    }

    private void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        lastEdit = SystemClock.uptimeMillis();
        if (nullInput) {
            for (int i = 0; i < s.length(); i++) sendKeyChar(s.charAt(i));
        } else {
            ic.commitText(s, 1);
        }
    }

    /** Chốt từ đang soạn. LangContext quyết định giữ tiếng Việt hay trả về phím tiếng Anh. */
    private void finishComposing() {
        if (engine.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        lastEdit = SystemClock.uptimeMillis();
        String shown = engine.text();
        String out = lang.decide(engine, prefs.getBoolean(Cfg.K_RESTORE, true));
        if (ic != null) {
            if (compat) {
                if (!out.equals(shown)) {
                    ic.beginBatchEdit();
                    if (compLen > 0) ic.deleteSurroundingText(compLen, 0);
                    ic.commitText(out, 1);
                    ic.endBatchEdit();
                }
            } else {
                if (!out.equals(shown)) ic.setComposingText(out, 1);
                ic.finishComposingText();
            }
        }
        engine.clear();
        compLen = 0;
    }

    /** Tự viết hoa đầu câu nếu ô nhập yêu cầu. */
    private void autoCaps() {
        if (shift == 2) return;
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic == null || ei == null || ei.inputType == InputType.TYPE_NULL) return;
        int ns = ic.getCursorCapsMode(ei.inputType) != 0 ? 1 : 0;
        if (ns != shift) {
            shift = ns;
            refreshLetters();
        }
    }
}
