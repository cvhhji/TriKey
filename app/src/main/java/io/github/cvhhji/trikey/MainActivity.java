package io.github.cvhhji.trikey;

import android.app.Activity;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class MainActivity extends Activity {
    private final Map<String, String> actions = new LinkedHashMap<>();
    private final Map<String, Spinner> typeViews = new LinkedHashMap<>();
    private final Map<String, EditText> valueViews = new LinkedHashMap<>();
    private CheckBox enabled;
    private Switch launcherVisible;
    private EditText keyCode;
    private EditText doubleMs;
    private EditText longMs;
    private Button save;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        actions.put("关闭", "none");
        actions.put("打开微信", "wechat");
        actions.put("全局搜索", "global_search");
        actions.put("系统设置", "settings");
        actions.put("应用搜索", "app_search");
        actions.put("翻译", "translate");
        actions.put("游戏中心", "game_center");
        actions.put("全屏识屏", "ocr");
        actions.put("相机", "camera");
        actions.put("录像", "video_capture");
        actions.put("微信付款码", "wechat_pay");
        actions.put("微信扫一扫", "wechat_scan");
        actions.put("支付宝付款码", "alipay_pay");
        actions.put("支付宝扫一扫", "alipay_scan");
        actions.put("录音", "recorder");
        actions.put("展开状态栏", "statusbar_expand");
        actions.put("收起状态栏", "statusbar_collapse");
        actions.put("快速截图", "screenshot");
        actions.put("返回", "back");
        actions.put("锁屏", "lock_screen");
        actions.put("自定义应用", "app");
        actions.put("自定义 Intent", "intent");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(36));
        root.setBackgroundColor(color(R.color.page_background));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("T", 22, true, R.color.on_primary);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(R.color.primary, 14));
        header.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.addView(text("TriKey", 27, true, R.color.text_primary));
        heading.addView(text("一个按键，三种动作", 14, false, R.color.text_secondary));
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, -2, 1f);
        headingParams.setMargins(dp(14), 0, 0, 0);
        header.addView(heading, headingParams);
        root.addView(header);

        TextView intro = text("将硬件按键扩展为单击、双击和长按。设置保存后立即生效。", 14, false, R.color.text_secondary);
        intro.setLineSpacing(0, 1.15f);
        root.addView(intro, margins(0, 16, 0, 20));

        LinearLayout general = card("常规");
        enabled = new CheckBox(this);
        enabled.setText("启用按键映射");
        enabled.setTextColor(color(R.color.text_primary));
        enabled.setTextSize(16);
        enabled.setChecked(true);
        general.addView(enabled);

        launcherVisible = new Switch(this);
        launcherVisible.setText("在桌面显示图标");
        launcherVisible.setTextColor(color(R.color.text_primary));
        launcherVisible.setTextSize(16);
        launcherVisible.setChecked(isLauncherVisible());
        launcherVisible.setOnCheckedChangeListener((button, visible) -> setLauncherVisible(visible));
        general.addView(launcherVisible, margins(0, 8, 0, 0));
        general.addView(text("隐藏后可从 LSPosed 的模块列表重新打开。", 12, false, R.color.text_tertiary), margins(4, 2, 0, 0));
        root.addView(general);

        LinearLayout timing = card("按键与时序");
        keyCode = numberField(timing, "按键码", Config.DEFAULT_KEY_CODE);
        doubleMs = numberField(timing, "双击间隔（毫秒）", Config.DEFAULT_DOUBLE_MS);
        longMs = numberField(timing, "长按阈值（毫秒）", Config.DEFAULT_LONG_MS);
        root.addView(timing, margins(0, 14, 0, 0));

        addGesture(root, "single", "单击");
        addGesture(root, "double", "双击");
        addGesture(root, "long", "长按");

        save = new Button(this);
        save.setText("保存设置");
        save.setTextSize(16);
        save.setTextColor(color(R.color.on_primary));
        save.setAllCaps(false);
        save.setEnabled(false);
        save.setBackground(round(R.color.primary, 14));
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(52));
        saveParams.setMargins(0, dp(20), 0, dp(14));
        root.addView(save, saveParams);

        TextView hint = text("ColorOS 实体快捷键默认按键码为 780，不同系统版本可能不同。", 12, false, R.color.text_tertiary);
        hint.setLineSpacing(0, 1.2f);
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
        bindXposedService();
    }

    private void addGesture(LinearLayout root, String key, String label) {
        LinearLayout section = card(label);
        Spinner spinner = new Spinner(this);
        String[] labels = actions.keySet().toArray(new String[0]);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        String selected = defaultType(key);
        int index = 0;
        for (String value : actions.values()) {
            if (value.equals(selected)) break;
            index++;
        }
        spinner.setSelection(Math.min(index, labels.length - 1));
        spinner.setBackground(roundWithStroke(R.color.field_background, R.color.outline, 10));
        spinner.setPadding(dp(12), 0, dp(8), 0);
        section.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48)));

        EditText value = new EditText(this);
        value.setHint("包名或 Intent URI（预设动作可留空）");
        value.setHintTextColor(color(R.color.text_tertiary));
        value.setTextColor(color(R.color.text_primary));
        value.setTextSize(14);
        value.setSingleLine(false);
        value.setMinLines(1);
        value.setMaxLines(3);
        value.setPadding(dp(12), dp(10), dp(12), dp(10));
        value.setBackground(roundWithStroke(R.color.field_background, R.color.outline, 10));
        value.setText("");
        section.addView(value, margins(0, 10, 0, 0));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String type = actions.get(String.valueOf(parent.getItemAtPosition(position)));
                value.setVisibility("app".equals(type) || "intent".equals(type) ? View.VISIBLE : View.GONE);
                value.setHint("app".equals(type) ? "应用包名" : "Intent URI");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                value.setVisibility(View.GONE);
            }
        });
        root.addView(section, margins(0, 14, 0, 0));
        typeViews.put(key, spinner);
        valueViews.put(key, value);
    }

    private LinearLayout card(String title) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(14), dp(16), dp(16));
        layout.setBackground(roundWithStroke(R.color.card_background, R.color.outline, 16));
        layout.addView(text(title, 17, true, R.color.text_primary), margins(0, 0, 0, 10));
        return layout;
    }

    private EditText numberField(LinearLayout root, String label, int value) {
        root.addView(text(label, 13, false, R.color.text_secondary), margins(0, 8, 0, 5));
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(value));
        input.setTextColor(color(R.color.text_primary));
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(roundWithStroke(R.color.field_background, R.color.outline, 10));
        root.addView(input, new LinearLayout.LayoutParams(-1, dp(46)));
        return input;
    }

    private void save() {
        try {
            if (prefs == null) {
                Toast.makeText(this, "请先在模块管理器中启用模块", Toast.LENGTH_LONG).show();
                return;
            }
            int key = Integer.parseInt(keyCode.getText().toString().trim());
            int dbl = Integer.parseInt(doubleMs.getText().toString().trim());
            int lng = Integer.parseInt(longMs.getText().toString().trim());
            if (key < 1 || dbl < 100 || dbl > 1000 || lng < 250 || lng > 3000) throw new IllegalArgumentException();
            SharedPreferences.Editor e = prefs.edit().putBoolean("enabled", enabled.isChecked()).putInt("keyCode", key).putInt("doubleMs", dbl).putInt("longMs", lng);
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

    private void bindXposedService() {
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService service) {
                runOnUiThread(() -> {
                    prefs = service.getRemotePreferences(Config.PREFS);
                    loadPreferences();
                    save.setEnabled(true);
                });
            }

            @Override
            public void onServiceDied(XposedService service) {
                runOnUiThread(() -> {
                    prefs = null;
                    save.setEnabled(false);
                });
            }
        });
    }

    private void loadPreferences() {
        enabled.setChecked(prefs.getBoolean("enabled", true));
        int savedKeyCode = Config.normalizeKeyCode(prefs.getInt("keyCode", Config.DEFAULT_KEY_CODE));
        keyCode.setText(String.valueOf(savedKeyCode));
        if (prefs.getInt("keyCode", Config.DEFAULT_KEY_CODE) == Config.LEGACY_DEFAULT_KEY_CODE) {
            prefs.edit().putInt("keyCode", Config.DEFAULT_KEY_CODE).apply();
        }
        doubleMs.setText(String.valueOf(prefs.getInt("doubleMs", Config.DEFAULT_DOUBLE_MS)));
        longMs.setText(String.valueOf(prefs.getInt("longMs", Config.DEFAULT_LONG_MS)));
        for (String gesture : typeViews.keySet()) {
            String selected = prefs.getString(gesture + "Type", defaultType(gesture));
            int index = 0;
            for (String type : actions.values()) {
                if (type.equals(selected)) break;
                index++;
            }
            typeViews.get(gesture).setSelection(Math.min(index, actions.size() - 1));
            valueViews.get(gesture).setText(prefs.getString(gesture + "Value", ""));
        }
    }

    private boolean isLauncherVisible() {
        int state = getPackageManager().getComponentEnabledSetting(launcherComponent());
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    private void setLauncherVisible(boolean visible) {
        getPackageManager().setComponentEnabledSetting(launcherComponent(), visible ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }

    private ComponentName launcherComponent() {
        return new ComponentName(this, getPackageName() + ".Launcher");
    }

    private String defaultType(String key) {
        if ("single".equals(key)) return "wechat_pay";
        if ("double".equals(key)) return "wechat_scan";
        return "ocr";
    }

    private TextView text(String value, int sp, boolean bold, int colorId) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color(colorId));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int colorId, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(colorId));
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable roundWithStroke(int fillId, int strokeId, int radius) {
        GradientDrawable drawable = round(fillId, radius);
        drawable.setStroke(dp(1), color(strokeId));
        return drawable;
    }

    private int color(int id) {
        return getColor(id);
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
