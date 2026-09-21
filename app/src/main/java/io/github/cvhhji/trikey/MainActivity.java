package io.github.cvhhji.trikey;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MainActivity extends Activity {
    private final Map<String, String> actions = new LinkedHashMap<>();
    private final Map<String, Spinner> typeViews = new LinkedHashMap<>();
    private final Map<String, EditText> valueViews = new LinkedHashMap<>();
    private CheckBox enabled;
    private EditText keyCode;
    private EditText doubleMs;
    private EditText longMs;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE);
        actions.put("关闭", "none");
        actions.put("启动应用", "app");
        actions.put("Intent URI", "intent");
        actions.put("微信付款码", "wechat_pay");
        actions.put("微信扫一扫", "wechat_scan");
        actions.put("全屏识屏", "ocr");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(32));
        root.setBackgroundColor(Color.rgb(247, 248, 250));

        TextView title = text("TriKey", 28, true);
        root.addView(title);
        TextView intro = text("将一个硬件按键扩展为单击、双击和长按。修改后无需重启；首次启用模块或更改作用域后需要重启系统。", 14, false);
        intro.setTextColor(Color.DKGRAY);
        root.addView(intro, margins(0, 6, 0, 18));

        enabled = new CheckBox(this);
        enabled.setText("启用按键映射");
        enabled.setChecked(prefs.getBoolean("enabled", true));
        root.addView(enabled);

        keyCode = numberField(root, "按键码", prefs.getInt("keyCode", Config.DEFAULT_KEY_CODE));
        doubleMs = numberField(root, "双击间隔（毫秒）", prefs.getInt("doubleMs", Config.DEFAULT_DOUBLE_MS));
        longMs = numberField(root, "长按阈值（毫秒）", prefs.getInt("longMs", Config.DEFAULT_LONG_MS));

        addGesture(root, "single", "单击");
        addGesture(root, "double", "双击");
        addGesture(root, "long", "长按");

        Button save = new Button(this);
        save.setText("保存设置");
        save.setOnClickListener(v -> save());
        root.addView(save, margins(0, 22, 0, 8));

        TextView hint = text("启动应用时填写包名；Intent URI 可填写 intent:#Intent;action=...;end。默认按键码 219 是 KEYCODE_ASSIST。若快捷键无反应，可用 adb shell getevent -l 确认按键，再填写 Android keyCode。", 13, false);
        hint.setTextColor(Color.GRAY);
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void addGesture(LinearLayout root, String key, String label) {
        root.addView(text(label, 18, true), margins(0, 18, 0, 5));
        Spinner spinner = new Spinner(this);
        String[] labels = actions.keySet().toArray(new String[0]);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        String selected = prefs.getString(key + "Type", defaultType(key));
        int index = 0;
        for (String value : actions.values()) {
            if (value.equals(selected)) break;
            index++;
        }
        spinner.setSelection(Math.min(index, labels.length - 1));
        root.addView(spinner);
        EditText value = new EditText(this);
        value.setHint("包名或 Intent URI（预设动作可留空）");
        value.setSingleLine(false);
        value.setText(prefs.getString(key + "Value", ""));
        root.addView(value);
        typeViews.put(key, spinner);
        valueViews.put(key, value);
    }

    private EditText numberField(LinearLayout root, String label, int value) {
        root.addView(text(label, 15, true), margins(0, 12, 0, 0));
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(value));
        root.addView(input);
        return input;
    }

    private void save() {
        try {
            int key = Integer.parseInt(keyCode.getText().toString().trim());
            int dbl = Integer.parseInt(doubleMs.getText().toString().trim());
            int lng = Integer.parseInt(longMs.getText().toString().trim());
            if (key < 1 || dbl < 100 || dbl > 1000 || lng < 250 || lng > 3000) {
                throw new IllegalArgumentException();
            }
            SharedPreferences.Editor e = prefs.edit()
                    .putBoolean("enabled", enabled.isChecked())
                    .putInt("keyCode", key)
                    .putInt("doubleMs", dbl)
                    .putInt("longMs", lng);
            for (String gesture : typeViews.keySet()) {
                String label = String.valueOf(typeViews.get(gesture).getSelectedItem());
                e.putString(gesture + "Type", actions.get(label));
                e.putString(gesture + "Value", valueViews.get(gesture).getText().toString().trim());
            }
            e.apply();
            Toast.makeText(this, "已保存，下一次按键立即生效", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
            Toast.makeText(this, "请检查按键码和时间参数", Toast.LENGTH_SHORT).show();
        }
    }

    private String defaultType(String key) {
        if ("single".equals(key)) return "wechat_pay";
        if ("double".equals(key)) return "wechat_scan";
        return "ocr";
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.BLACK);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

