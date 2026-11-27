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
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Bàn phím tiếng Việt (Telex).
 *
 *  - "uow" / "uwow" luôn ra "ươ" (đủ cả hai dấu móc): xử lý trong TelexEngine.
 *  - Không tự chuyển sang EN: chế độ VI/EN chỉ đổi khi bấm phím VI/EN và được nhớ lại.
 *  - Từ tiếng Anh gõ giữa câu tự khôi phục khi nhấn cách (xem LangContext).
 *  - Chế độ tương thích: không dùng gạch chân soạn thảo, dành cho app/trình duyệt xử lý composing kém.
 */
public class KeyboardService extends InputMethodService {

    private static final String[][] LETTERS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"},
            {"SYM", ",", "LANG", "SPACE", ".", "ENTER"}
    };
    private static final String[][] SYMBOLS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"@", "#", "$", "%", "&", "-", "+", "(", ")", "/"},
            {"=", "~", "*", "\"", "'", ":", ";", "!", "?", "DEL"},
            {"SYM", ",", "LANG", "SPACE", ".", "ENTER"}
    };
    private static final String DIGITS = "1234567890";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TelexEngine engine = new TelexEngine();
    private final LangContext lang = new LangContext();
    private final ArrayList<TextView> letterViews = new ArrayList<>();

    private SharedPreferences prefs;
    private FrameLayout root;
    private LinearLayout keysBox;
    private ImageView shiftView;

    private boolean viMode = true;
    private boolean directMode = false; // mật khẩu, email, số: gõ thẳng
    private boolean compat = false;     // chế độ tương thích
    private boolean symbols = false;
    private int shift = 0;              // 0 tắt, 1 một lần, 2 khóa
    private long lastShiftTap = 0;
    private long lastEdit = 0;
    private int compLen = 0;            // chế độ tương thích: độ dài từ đang chèn
    private int cursorAfterEdit = -1;   // chế độ tương thích: vị trí con trỏ sau lần gõ cuối
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
        cursorAfterEdit = -1;
        engine.setModernTone(prefs.getBoolean(Cfg.K_MODERN, false));
        viMode = prefs.getBoolean(Cfg.K_VI, true);
        compat = prefs.getBoolean(Cfg.K_COMPAT, false);

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
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
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
        // Thông báo đến ngay sau khi tự gõ: chỉ ghi nhận vị trí con trỏ, không coi là người dùng chạm sang chỗ khác.
        if (SystemClock.uptimeMillis() - lastEdit < 400) {
            cursorAfterEdit = newSelStart;
            return;
        }
        if (engine.isEmpty()) return;
        // Chỉ so điểm đầu vùng chọn: trình duyệt có thể bôi đen phần tự hoàn thành phía sau con trỏ.
        boolean moved = compat
                ? newSelStart != cursorAfterEdit
                : (candEnd == -1 || newSelStart != candEnd);
        if (moved) {
            engine.clear();
            lang.clear();
            compLen = 0;
            InputConnection ic = getCurrentInputConnection();
            if (!compat && ic != null) ic.finishComposingText();
        }
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

    private void buildKeys() {
        if (keysBox == null) return;
        keysBox.removeAllViews();
        letterViews.clear();
        shiftView = null;
        String[][] layout = symbols ? SYMBOLS : LETTERS;
        for (int r = 0; r < layout.length; r++) {
            keysBox.addView(buildRow(layout[r], r));
        }
        refreshLetters();
    }

    private LinearLayout buildRow(String[] ids, int rowIdx) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(rowHeightDp)));

        int splitAt = (splitNow && rowIdx < 3) ? (ids.length + 1) / 2 : -1;
        boolean pad = !symbols && rowIdx == 1;
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
                    row.addView(makeKey(id, viMode ? "Tiếng Việt" : "English", 6f));
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

    private View spacer(float weight) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight));
        return v;
    }

    private String labelFor(String id) {
        switch (id) {
            case "SHIFT": return shift == 2 ? "⇪" : "⇧";
            case "DEL": return "⌫";
            case "ENTER": return "↵";
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

    // ------------------------------------------------------------------ xử lý phím

    private final class KeyTouch implements View.OnTouchListener {
        private final String id;
        private boolean longFired = false;
        private final Runnable longRun = new Runnable() {
            @Override public void run() {
                int idx = "qwertyuiop".indexOf(id);
                if (idx >= 0) {
                    longFired = true;
                    handleChar(String.valueOf(DIGITS.charAt(idx)));
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

        private boolean hasLong() {
            return !symbols && id.length() == 1 && "qwertyuiop".indexOf(id) >= 0;
        }

        private void cancelAll() {
            handler.removeCallbacks(longRun);
            handler.removeCallbacks(repeatRun);
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    if (prefs.getBoolean(Cfg.K_HAPTIC, true)) {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }
                    longFired = false;
                    if (id.equals("DEL")) {
                        handleDelete();
                        handler.postDelayed(repeatRun, 400);
                    } else if (hasLong()) {
                        handler.postDelayed(longRun, 350);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    v.setPressed(false);
                    cancelAll();
                    if (!id.equals("DEL") && !longFired) onKey(id);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    cancelAll();
                    return true;
                default:
                    return true;
            }
        }
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
        if (viMode && !directMode) {
            showComposition(ic, engine.type(c));
        } else {
            commit(String.valueOf(c));
        }
    }

    /** Hiển thị từ đang soạn: gạch chân (composing) hoặc xóa-và-chèn lại ở chế độ tương thích. */
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
        ic.commitText(s, 1);
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
