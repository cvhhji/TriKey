package io.github.cvhhji.trikey;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.ComponentName;
import android.content.Intent;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class MainActivity extends Activity {
    private final ExecutorService statusWorker = Executors.newSingleThreadExecutor();
    private final Map<String, String> actions = new LinkedHashMap<>();
    private final Map<String, Spinner> typeViews = new LinkedHashMap<>();
    private final Map<String, EditText> valueViews = new LinkedHashMap<>();
    private CheckBox enabled;
    private CheckBox consumeOriginal;
    private Switch launcherVisible;
    private LinearLayout activationCard;
    private FrameLayout activationIconHolder;
    private ImageView activationIcon;
    private ProgressBar activationProgress;
    private TextView activationStatus;
    private EditText keyCode;
    private EditText doubleMs;
    private EditText longMs;
    private Button save;
    private SharedPreferences prefs;
    private volatile XposedService xposedService;
    private volatile boolean destroyed;
    private int statusCheckGeneration;

    private enum ActivationState {
        CHECKING,
        ACTIVE,
        RESTART_REQUIRED,
        INACTIVE
    }

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
        root.setPadding(dp(20), dp(24), dp(20), dp(36));
        root.setBackgroundColor(color(R.color.page_background));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("T", 22, true, R.color.primary);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(R.color.primary_container, 16));
        header.addView(mark, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.addView(text("TriKey", 28, true, R.color.text_primary));
        heading.addView(text("硬件快捷键设置", 14, false, R.color.text_secondary));
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, -2, 1f);
        headingParams.setMargins(dp(14), 0, 0, 0);
        header.addView(heading, headingParams);
        root.addView(header);

        activationCard = new LinearLayout(this);
        activationCard.setOrientation(LinearLayout.HORIZONTAL);
        activationCard.setGravity(Gravity.CENTER_VERTICAL);
        activationCard.setPadding(dp(20), dp(18), dp(20), dp(18));
        activationCard.setMinimumHeight(dp(96));
        activationCard.setFocusable(false);
        activationCard.setClickable(false);
        activationIconHolder = new FrameLayout(this);
        activationIconHolder.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        activationIcon = new ImageView(this);
        activationIcon.setVisibility(View.GONE);
        activationIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER);
        activationIconHolder.addView(activationIcon, iconParams);
        activationProgress = new ProgressBar(this);
        activationProgress.setIndeterminateTintList(ColorStateList.valueOf(color(R.color.primary)));
        activationProgress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER);
        activationIconHolder.addView(activationProgress, progressParams);
        activationCard.addView(activationIconHolder, new LinearLayout.LayoutParams(dp(52), dp(52)));
        activationStatus = text("正在检查…", 22, true, R.color.text_secondary);
        activationStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams activationStatusParams = new LinearLayout.LayoutParams(0, -2, 1f);
        activationStatusParams.setMargins(dp(16), 0, 0, 0);
        activationCard.addView(activationStatus, activationStatusParams);
        setActivation(ActivationState.CHECKING);
        root.addView(activationCard, margins(0, 20, 0, 0));

        LinearLayout general = card("常规");
        enabled = new CheckBox(this);
        enabled.setText("启用按键映射");
        enabled.setTextColor(color(R.color.text_primary));
        enabled.setTextSize(16);
        enabled.setTypeface(uiTypeface(false));
        enabled.setMinHeight(dp(48));
        enabled.setChecked(true);
        general.addView(enabled);

        consumeOriginal = new CheckBox(this);
        consumeOriginal.setText("替代系统原动作");
        consumeOriginal.setTextColor(color(R.color.text_primary));
        consumeOriginal.setTextSize(16);
        consumeOriginal.setTypeface(uiTypeface(false));
        consumeOriginal.setMinHeight(dp(48));
        consumeOriginal.setChecked(false);
        general.addView(consumeOriginal, margins(0, 4, 0, 0));
        general.addView(text("开启后目标按键只执行 TriKey 动作；遇到兼容问题时请关闭。", 13, false,
                R.color.text_tertiary), margins(4, 0, 0, 0));

        launcherVisible = new Switch(this);
        launcherVisible.setText("在桌面显示图标");
        launcherVisible.setTextColor(color(R.color.text_primary));
        launcherVisible.setTextSize(16);
        launcherVisible.setTypeface(uiTypeface(false));
        launcherVisible.setChecked(isLauncherVisible());
        launcherVisible.setOnCheckedChangeListener((button, visible) -> setLauncherVisible(visible));
        launcherVisible.setMinHeight(dp(48));
        general.addView(launcherVisible, margins(0, 8, 0, 0));
        general.addView(text("隐藏后可从 LSPosed 的模块列表重新打开。", 13, false, R.color.text_tertiary), margins(4, 2, 0, 0));
        root.addView(general, margins(0, 14, 0, 0));

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
        save.setTypeface(uiTypeface(true));
        save.setTextColor(color(R.color.on_primary));
        save.setAllCaps(false);
        save.setEnabled(false);
        save.setAlpha(0.48f);
        save.setBackground(round(R.color.primary, 16));
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(56));
        saveParams.setMargins(0, dp(20), 0, dp(14));
        root.addView(save, saveParams);

        TextView hint = text("ColorOS 实体快捷键默认按键码为 780，不同系统版本可能不同。", 13, false, R.color.text_tertiary);
        hint.setLineSpacing(0, 1.2f);
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
        bindXposedService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshInjectionStatus();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        statusWorker.shutdownNow();
        super.onDestroy();
    }

    private void addGesture(LinearLayout root, String key, String label) {
        LinearLayout section = card(label);
        Spinner spinner = new Spinner(this);
        String[] labels = actions.keySet().toArray(new String[0]);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, labels) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                return styleSpinnerText(super.getView(position, convertView, parent), false);
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                return styleSpinnerText(super.getDropDownView(position, convertView, parent), true);
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setContentDescription(label + "时执行的动作");
        String selected = defaultType(key);
        int index = 0;
        for (String value : actions.values()) {
            if (value.equals(selected)) break;
            index++;
        }
        spinner.setSelection(Math.min(index, labels.length - 1));
        spinner.setBackground(roundWithStroke(R.color.field_background, R.color.outline, 14));
        spinner.setPadding(dp(12), 0, dp(8), 0);
        section.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView valueLabel = text("参数", 13, false, R.color.text_secondary);
        valueLabel.setVisibility(View.GONE);
        section.addView(valueLabel, margins(0, 10, 0, 5));
        EditText value = new EditText(this);
        value.setId(View.generateViewId());
        valueLabel.setLabelFor(value.getId());
        value.setContentDescription(label + "动作参数");
        value.setHint("填写自定义动作参数");
        value.setHintTextColor(color(R.color.text_tertiary));
        value.setTextColor(color(R.color.text_primary));
        value.setTextSize(14);
        value.setTypeface(uiTypeface(false));
        value.setSingleLine(false);
        value.setMinLines(1);
        value.setMaxLines(3);
        value.setPadding(dp(12), dp(10), dp(12), dp(10));
        value.setBackground(roundWithStroke(R.color.field_background, R.color.outline, 14));
        value.setText("");
        value.setVisibility(View.GONE);
        section.addView(value, margins(0, 0, 0, 0));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String type = actions.get(String.valueOf(parent.getItemAtPosition(position)));
                boolean custom = "app".equals(type) || "intent".equals(type);
                valueLabel.setText("app".equals(type) ? "应用包名" : "Intent URI");
                value.setHint("app".equals(type) ? "例如 com.example.app" : "粘贴 Intent URI");
                valueLabel.setVisibility(custom ? View.VISIBLE : View.GONE);
                value.setVisibility(custom ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                valueLabel.setVisibility(View.GONE);
                value.setVisibility(View.GONE);
            }
        });
        root.addView(section, margins(0, 14, 0, 0));
        typeViews.put(key, spinner);
        valueViews.put(key, value);
    }

    private LinearLayout card(String title) {
        LinearLayout layout = card();
        layout.addView(text(title, 18, true, R.color.text_primary), margins(0, 0, 0, 12));
        return layout;
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(18), dp(20), dp(20));
        layout.setBackground(round(R.color.card_background, 20));
        return layout;
    }

    private View styleSpinnerText(View view, boolean dropdown) {
        TextView label = (TextView) view;
        label.setTypeface(uiTypeface(false));
        label.setTextColor(color(R.color.text_primary));
        label.setTextSize(15);
        if (dropdown) label.setPadding(dp(16), dp(14), dp(16), dp(14));
        return label;
    }

    private EditText numberField(LinearLayout root, String label, int value) {
        TextView fieldLabel = text(label, 14, false, R.color.text_secondary);
        root.addView(fieldLabel, margins(0, 8, 0, 6));
        EditText input = new EditText(this);
        input.setId(View.generateViewId());
        fieldLabel.setLabelFor(input.getId());
        input.setContentDescription(label);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(value));
        input.setTextColor(color(R.color.text_primary));
        input.setTextSize(16);
        input.setTypeface(uiTypeface(false));
        input.setSingleLine(true);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(roundWithStroke(R.color.field_background, R.color.outline, 14));
        root.addView(input, new LinearLayout.LayoutParams(-1, dp(50)));
        return input;
    }

    private void save() {
        try {
            if (prefs == null) {
                Toast.makeText(this, "请先在模块管理器中启用模块", Toast.LENGTH_LONG).show();
                return;
            }
            int key = parseNumber(keyCode, "按键码");
            int dbl = parseNumber(doubleMs, "双击间隔");
            int lng = parseNumber(longMs, "长按阈值");
            if (key < 1) throw new IllegalArgumentException("按键码必须大于 0");
            if (dbl < 100 || dbl > 1000) {
                throw new IllegalArgumentException("双击间隔必须在 100–1000 毫秒之间");
            }
            if (lng < 250 || lng > 3000) {
                throw new IllegalArgumentException("长按阈值必须在 250–3000 毫秒之间");
            }
            validateCustomActions();
            SharedPreferences.Editor e = prefs.edit()
                    .putBoolean("enabled", enabled.isChecked())
                    .putBoolean("consumeOriginal", consumeOriginal.isChecked())
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
        } catch (IllegalArgumentException error) {
            String message = error.getMessage();
            Toast.makeText(this, message == null ? "请检查按键码和时间参数" : message,
                    Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {
            Toast.makeText(this, "设置校验失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    private static int parseNumber(EditText input, String label) {
        try {
            return Integer.parseInt(input.getText().toString().trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + "必须是整数");
        }
    }

    private void validateCustomActions() {
        for (String gesture : typeViews.keySet()) {
            String label = String.valueOf(typeViews.get(gesture).getSelectedItem());
            String type = actions.get(label);
            String value = valueViews.get(gesture).getText().toString().trim();
            if ("app".equals(type)) {
                if (value.isEmpty() || getPackageManager().getLaunchIntentForPackage(value) == null) {
                    throw new IllegalArgumentException(labelForGesture(gesture) + "：找不到可启动的应用包名");
                }
            } else if ("intent".equals(type)) {
                try {
                    if (value.isEmpty()) throw new IllegalArgumentException();
                    Intent.parseUri(value, Intent.URI_INTENT_SCHEME);
                } catch (Exception error) {
                    throw new IllegalArgumentException(labelForGesture(gesture) + "：Intent URI 格式无效");
                }
            }
        }
    }

    private static String labelForGesture(String gesture) {
        if ("single".equals(gesture)) return "单击";
        if ("double".equals(gesture)) return "双击";
        return "长按";
    }

    private void bindXposedService() {
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService service) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    xposedService = service;
                    prefs = service.getRemotePreferences(Config.PREFS);
                    loadPreferences();
                    save.setEnabled(true);
                    save.setAlpha(1f);
                    refreshInjectionStatus();
                });
            }

            @Override
            public void onServiceDied(XposedService service) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    statusCheckGeneration++;
                    xposedService = null;
                    prefs = null;
                    setActivation(ActivationState.INACTIVE);
                    save.setEnabled(false);
                    save.setAlpha(0.48f);
                });
            }
        });
    }

    private void refreshInjectionStatus() {
        if (destroyed) return;
        XposedService service = xposedService;
        if (service == null) {
            statusCheckGeneration++;
            setActivation(ActivationState.INACTIVE);
            return;
        }
        int generation = ++statusCheckGeneration;
        setActivation(ActivationState.CHECKING);
        statusWorker.execute(() -> {
            ActivationState result = ActivationState.INACTIVE;
            try {
                HookedTarget system = null;
                for (HookedTarget target : service.getRunningTargets()) {
                    String process = target.getProcessName();
                    if ("system_server".equals(process) || "system".equals(process)) {
                        system = target;
                        break;
                    }
                }
                if (system != null) {
                    android.content.pm.PackageInfo appInfo = getPackageManager()
                            .getPackageInfo(getPackageName(), 0);
                    long currentVersion = appInfo.versionCode;
                    long loadedVersion = system.getLoadedVersionCode();
                    HookedTarget.State targetState = system.getState();
                    boolean current = loadedVersion == currentVersion;
                    boolean upToDate = targetState == HookedTarget.State.UP_TO_DATE;
                    if (current && upToDate) {
                        result = ActivationState.ACTIVE;
                    } else if (targetState == HookedTarget.State.STALE
                            || (loadedVersion > 0 && loadedVersion != currentVersion)) {
                        result = ActivationState.RESTART_REQUIRED;
                    }
                }
            } catch (Throwable ignored) {
                result = ActivationState.INACTIVE;
            }
            ActivationState verified = result;
            runOnUiThread(() -> {
                if (!destroyed && !isFinishing() && generation == statusCheckGeneration) {
                    setActivation(verified);
                }
            });
        });
    }

    private void setActivation(ActivationState state) {
        int cardColor;
        int iconBackground;
        int iconForeground;
        int labelColor;
        int iconResource;
        String label;
        boolean checking = state == ActivationState.CHECKING;
        if (state == ActivationState.ACTIVE) {
            cardColor = R.color.primary_container;
            iconBackground = R.color.primary;
            iconForeground = R.color.on_primary;
            labelColor = R.color.on_primary_container;
            iconResource = R.drawable.ic_status_check;
            label = "已激活";
        } else if (state == ActivationState.RESTART_REQUIRED) {
            cardColor = R.color.warning_container;
            iconBackground = R.color.warning;
            iconForeground = R.color.on_warning;
            labelColor = R.color.on_warning_container;
            iconResource = R.drawable.ic_status_attention;
            label = "需要重启";
        } else if (state == ActivationState.INACTIVE) {
            cardColor = R.color.error_container;
            iconBackground = R.color.error;
            iconForeground = R.color.on_error;
            labelColor = R.color.on_error_container;
            iconResource = R.drawable.ic_status_attention;
            label = "未激活";
        } else {
            cardColor = R.color.card_background;
            iconBackground = R.color.primary_container;
            iconForeground = R.color.primary;
            labelColor = R.color.text_secondary;
            iconResource = 0;
            label = "正在检查…";
        }
        activationCard.setBackground(round(cardColor, 24));
        activationIconHolder.setBackground(round(iconBackground, 26));
        activationStatus.setText(label);
        activationStatus.setTextColor(color(labelColor));
        activationProgress.setIndeterminateTintList(ColorStateList.valueOf(color(iconForeground)));
        activationProgress.setVisibility(checking ? View.VISIBLE : View.GONE);
        activationIcon.setVisibility(checking ? View.GONE : View.VISIBLE);
        if (!checking) {
            activationIcon.setImageResource(iconResource);
            activationIcon.setImageTintList(ColorStateList.valueOf(color(iconForeground)));
        }
    }

    private void loadPreferences() {
        enabled.setChecked(prefs.getBoolean("enabled", true));
        consumeOriginal.setChecked(prefs.getBoolean("consumeOriginal", false));
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
        view.setTypeface(uiTypeface(bold));
        return view;
    }

    private Typeface uiTypeface(boolean bold) {
        return Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
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
