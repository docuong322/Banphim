package com.vietkey.keyboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.InputStream;

/** Màn hình cài đặt: bật bàn phím, chọn nền, màu, tách đôi... */
public class MainActivity extends Activity {

    private static final int REQ_IMAGE = 101;
    private SharedPreferences prefs;
    private LinearLayout box;

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

        section("3. Giao diện");
        label("Bộ màu");
        final Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Cfg.THEME_NAMES));
        sp.setSelection(Math.max(0, Math.min(Cfg.THEME_NAMES.length - 1, prefs.getInt(Cfg.K_THEME, 0))));
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                prefs.edit().putInt(Cfg.K_THEME, pos).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        box.addView(sp);

        button("Chọn ảnh nền từ thư viện", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                startActivityForResult(i, REQ_IMAGE);
            }
        });
        button("Xóa ảnh nền (dùng màu)", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Cfg.bgFile(MainActivity.this).delete();
                prefs.edit().putLong(Cfg.K_IMG, 0).apply();
                toast("Đã xóa ảnh nền");
            }
        });

        seek("Độ đậm của phím", Cfg.K_ALPHA, 20, 100, 90, "%");
        seek("Độ tối của ảnh nền (giúp chữ dễ đọc)", Cfg.K_DIM, 0, 80, 25, "%");
        seek("Chiều cao phím", Cfg.K_HEIGHT, 36, 70, 50, " dp");

        section("4. Xoay ngang");
        sw("Tách đôi bàn phím khi xoay ngang", Cfg.K_SPLIT, true);
        seek("Độ rộng khoảng trống ở giữa", Cfg.K_GAP, 10, 50, 25, "%");

        section("5. Gõ tiếng Việt");
        sw("Đặt dấu kiểu mới (hoà, thuý thay vì hòa, thúy)", Cfg.K_MODERN, false);
        sw("Rung khi gõ phím", Cfg.K_HAPTIC, true);

        text("Mẹo: giữ phím hàng trên cùng (q w e r t y u i o p) để gõ số 1 2 3 ... 0.");
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

    private void label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setPadding(0, dp(8), 0, 0);
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

    private void seek(String s, final String key, final int min, int max, int def, final String unit) {
        final TextView t = new TextView(this);
        int cur = prefs.getInt(key, def);
        t.setText(s + ": " + cur + unit);
        t.setPadding(0, dp(10), 0, 0);
        box.addView(t);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(cur - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int p, boolean user) {
                t.setText(s_label(t) + ": " + (p + min) + unit);
                if (user) prefs.edit().putInt(key, p + min).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        box.addView(sb);
    }

    /** Lấy phần nhãn (trước dấu ':' cuối) của TextView đang hiển thị giá trị. */
    private static String s_label(TextView t) {
        String s = t.getText().toString();
        int i = s.lastIndexOf(": ");
        return i >= 0 ? s.substring(0, i) : s;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
