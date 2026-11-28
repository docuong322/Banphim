package com.vietkey.keyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.InputStream;

/** Màn hình cài đặt: bật bàn phím, ảnh nền, bảng màu, tách đôi, bộ gõ... */
public class MainActivity extends Activity {

    private static final int REQ_IMAGE = 101;

    private SharedPreferences prefs;
    private LinearLayout box;
    private final View[] swatches = new View[4];
    private LinearLayout preview;
    private final TextView[] pvKeys = new TextView[4];

    private interface ColorCallback {
        void onColor(int color);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Cfg.prefs(this);

        ScrollView sv = new ScrollView(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(32));
        sv.addView(box);
        setContentView(sv);

        title("VietKey - Bàn phím tiếng Việt");
        text("Gõ Telex. Gõ uow hoặc uwow ra \"ươ\" đủ cả hai dấu móc. Bàn phím không bao giờ tự "
                + "chuyển sang tiếng Anh: chỉ đổi khi bạn bấm phím VI/EN.");

        section("1. Cài đặt bàn phím");
        button("Bật VietKey trong danh sách bàn phím", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        });
        button("Chọn VietKey làm bàn phím đang dùng", new View.OnClickListener() {
            @Override public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }
        });

        section("2. Thử gõ");
        EditText test = new EditText(this);
        test.setHint("Chạm vào đây để thử gõ");
        test.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(test);

        section("3. Màu sắc");
        buildPreview();
        button("Chọn bộ màu có sẵn", new View.OnClickListener() {
            @Override public void onClick(View v) { showPresets(); }
        });
        text("Hoặc chạm vào từng ô màu bên dưới để chọn từ bảng màu:");
        for (int i = 0; i < 4; i++) colorRow(i);
        button("Đặt lại màu về mặc định", new View.OnClickListener() {
            @Override public void onClick(View v) { applyPreset(0); }
        });

        section("4. Ảnh nền và kích thước");
        button("Chọn ảnh nền từ thư viện", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                startActivityForResult(i, REQ_IMAGE);
            }
        });
        button("Xóa ảnh nền (dùng màu nền)", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Cfg.bgFile(MainActivity.this).delete();
                prefs.edit().putLong(Cfg.K_IMG, 0).apply();
                toast("Đã xóa ảnh nền");
            }
        });
        seek("Độ đậm của phím", Cfg.K_ALPHA, 20, 100, 90, "%");
        seek("Độ tối của ảnh nền (giúp chữ dễ đọc)", Cfg.K_DIM, 0, 80, 25, "%");
        seek("Chiều cao phím", Cfg.K_HEIGHT, 36, 70, 50, " dp");

        section("5. Bố cục và xoay ngang");
        sw("Hiện hàng số phía trên bàn phím", Cfg.K_NUMROW, true);
        sw("Hiện bong bóng phóng to phím khi gõ", Cfg.K_PREVIEW, true);
        sw("Tách đôi bàn phím khi xoay ngang", Cfg.K_SPLIT, true);
        seek("Độ rộng khoảng trống ở giữa", Cfg.K_GAP, 10, 50, 25, "%");

        section("6. Gõ tiếng Việt");
        sw("Đặt dấu kiểu mới (hoà, thuý thay vì hòa, thúy)", Cfg.K_MODERN, false);
        sw("Tự nhận diện từ tiếng Anh và khôi phục khi nhấn cách (farther, text, for five six...)",
                Cfg.K_RESTORE, true);
        sw("Gõ tiếng Việt trong thanh địa chỉ trình duyệt (tắt nếu hay gõ link)", Cfg.K_URI_VI, true);
        sw("Rung khi gõ phím", Cfg.K_HAPTIC, true);
        sw("Gõ trực tiếp, ổn định ở mọi ô nhập (khuyên dùng). Tắt để hiện gạch chân khi soạn, nhưng có thể lỗi ở ô tìm kiếm, trình duyệt",
                Cfg.K_COMPAT, true);

        text("Mẹo: giữ một phím chữ để chọn chữ hoa hoặc số/ký tự (t: T, 5   a: A, @), trượt ngón tay rồi nhả để chọn. "
                + "Giữ phím cách rồi vuốt ngang hoặc dọc để di chuyển con trỏ. "
                + "Quay lại sửa một từ cũ rồi gõ tiếp, bộ gõ vẫn ghép được dấu tiếng Việt.");
        refreshColors();
    }

    // ------------------------------------------------------------------ màu sắc

    private void buildPreview() {
        preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.HORIZONTAL);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(10), dp(12), dp(10), dp(12));
        String[] labels = {"Việt", "Nam", "⇧", "⌫"};
        for (int i = 0; i < 4; i++) {
            TextView k = new TextView(this);
            k.setText(labels[i]);
            k.setGravity(Gravity.CENTER);
            k.setTextSize(18);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), i < 2 ? 1.4f : 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            preview.addView(k, lp);
            pvKeys[i] = k;
        }
        box.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void refreshColors() {
        int bg = Cfg.color(prefs, 0), key = Cfg.color(prefs, 1);
        int special = Cfg.color(prefs, 2), txt = Cfg.color(prefs, 3);
        preview.setBackgroundColor(bg);
        for (int i = 0; i < 4; i++) {
            pvKeys[i].setTextColor(txt);
            GradientDrawable g = new GradientDrawable();
            g.setColor(i < 2 ? key : special);
            g.setCornerRadius(dp(7));
            pvKeys[i].setBackground(g);
        }
        for (int i = 0; i < 4; i++) {
            if (swatches[i] == null) continue;
            GradientDrawable g = new GradientDrawable();
            g.setColor(Cfg.color(prefs, i));
            g.setCornerRadius(dp(6));
            g.setStroke(dp(1), 0xFF888888);
            swatches[i].setBackground(g);
        }
    }

    private void colorRow(final int idx) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView label = new TextView(this);
        label.setText(Cfg.COLOR_LABELS[idx]);
        label.setTextSize(16);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View sw = new View(this);
        swatches[idx] = sw;
        row.addView(sw, new LinearLayout.LayoutParams(dp(56), dp(34)));
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                pickColor(Cfg.COLOR_LABELS[idx], Cfg.color(prefs, idx), new ColorCallback() {
                    @Override public void onColor(int color) {
                        prefs.edit().putInt(Cfg.COLOR_KEYS[idx], color).apply();
                        refreshColors();
                    }
                });
            }
        });
        box.addView(row);
    }

    private void showPresets() {
        new AlertDialog.Builder(this)
                .setTitle("Bộ màu có sẵn")
                .setItems(Cfg.THEME_NAMES, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { applyPreset(which); }
                })
                .show();
    }

    private void applyPreset(int theme) {
        SharedPreferences.Editor ed = prefs.edit();
        for (int i = 0; i < 4; i++) ed.putInt(Cfg.COLOR_KEYS[i], Cfg.themeColor(theme, i));
        ed.apply();
        refreshColors();
    }

    /** Bảng màu: lưới màu có sẵn kèm 3 thanh trượt Đỏ / Xanh lá / Xanh dương để tinh chỉnh. */
    private void pickColor(String title, int initial, final ColorCallback cb) {
        final int[] cur = {initial | 0xFF000000};

        LinearLayout lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        lay.setPadding(dp(16), dp(8), dp(16), dp(8));

        final View pv = new View(this);
        pv.setBackgroundColor(cur[0]);
        lay.addView(pv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        final SeekBar[] bars = new SeekBar[3];
        final SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                cur[0] = Color.rgb(bars[0].getProgress(), bars[1].getProgress(), bars[2].getProgress());
                pv.setBackgroundColor(cur[0]);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };

        // Lưới màu: hàng đầu là các mức xám, các hàng sau là 12 sắc độ ở các mức đậm nhạt khác nhau.
        final int cols = 12;
        float[][] sv = {{1f, 1f}, {0.55f, 1f}, {1f, 0.7f}, {0.7f, 0.45f}, {0.4f, 0.25f}};
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(10), 0, dp(6));
        for (int r = 0; r < sv.length + 1; r++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < cols; c++) {
                final int color;
                if (r == 0) {
                    int v = c * 255 / (cols - 1);
                    color = Color.rgb(v, v, v);
                } else {
                    color = Color.HSVToColor(new float[]{c * 30f, sv[r - 1][0], sv[r - 1][1]});
                }
                View cell = new View(this);
                cell.setBackgroundColor(color);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(28), 1f);
                lp.setMargins(dp(1), dp(1), dp(1), dp(1));
                line.addView(cell, lp);
                cell.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        bars[0].setProgress(Color.red(color));
                        bars[1].setProgress(Color.green(color));
                        bars[2].setProgress(Color.blue(color));
                    }
                });
            }
            grid.addView(line);
        }
        lay.addView(grid);

        String[] names = {"Đỏ", "Xanh lá", "Xanh dương"};
        int[] init = {Color.red(cur[0]), Color.green(cur[0]), Color.blue(cur[0])};
        for (int i = 0; i < 3; i++) {
            TextView t = new TextView(this);
            t.setText(names[i]);
            lay.addView(t);
            SeekBar sb = new SeekBar(this);
            sb.setMax(255);
            sb.setProgress(init[i]);
            bars[i] = sb;
            lay.addView(sb);
        }
        for (SeekBar sb : bars) sb.setOnSeekBarChangeListener(listener);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(lay);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("Chọn", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { cb.onColor(cur[0]); }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ------------------------------------------------------------------ chọn ảnh

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMAGE || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Uri u = data.getData();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(u);
            BitmapFactory.decodeStream(in, null, o);
            if (in != null) in.close();

            int sample = 1;
            while (o.outWidth / sample > 1800 || o.outHeight / sample > 1800) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            in = getContentResolver().openInputStream(u);
            Bitmap bmp = BitmapFactory.decodeStream(in, null, o);
            if (in != null) in.close();
            if (bmp == null) throw new IllegalStateException("decode");

            FileOutputStream fo = new FileOutputStream(Cfg.bgFile(this));
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, fo);
            fo.close();
            prefs.edit().putLong(Cfg.K_IMG, System.currentTimeMillis()).apply();
            toast("Đã đặt ảnh nền. Mở lại bàn phím để xem.");
        } catch (Exception e) {
            toast("Không đọc được ảnh này");
        }
    }

    // ------------------------------------------------------------------ dựng giao diện

    private void title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(22);
        t.setPadding(0, 0, 0, dp(8));
        box.addView(t);
    }

    private void section(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(17);
        t.setPadding(0, dp(22), 0, dp(6));
        box.addView(t);
    }

    private void text(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setPadding(0, dp(6), 0, dp(6));
        box.addView(t);
    }

    private void button(String s, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(s);
        bt.setAllCaps(false);
        bt.setOnClickListener(l);
        box.addView(bt, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void sw(String s, final String key, boolean def) {
        Switch w = new Switch(this);
        w.setText(s);
        w.setChecked(prefs.getBoolean(key, def));
        w.setPadding(0, dp(8), 0, dp(8));
        w.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton c, boolean on) {
                prefs.edit().putBoolean(key, on).apply();
            }
        });
        box.addView(w);
    }

    private void seek(final String name, final String key, final int min, int max, int def, final String unit) {
        final TextView t = new TextView(this);
        int cur = prefs.getInt(key, def);
        t.setText(name + ": " + cur + unit);
        t.setPadding(0, dp(10), 0, 0);
        box.addView(t);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(cur - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int p, boolean user) {
                t.setText(name + ": " + (p + min) + unit);
                if (user) prefs.edit().putInt(key, p + min).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        box.addView(sb);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
