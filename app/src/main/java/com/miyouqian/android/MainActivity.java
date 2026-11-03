package com.miyouqian.android;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 3001;
    private LinearLayout batteryBanner;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        applySystemBarAppearance();
        requestNotificationPermission();
        startLocalService();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(12, 12, 12, 8);
        // 顶部提示和电池设置按钮作为一个整体：系统确认已豁免电池优化后隐藏。
        batteryBanner = new LinearLayout(this);
        batteryBanner.setOrientation(LinearLayout.VERTICAL);

        TextView hint = new TextView(this);
        hint.setText("米游签正在启动本地服务…\n首次启动可能需要一点时间。请保持后台通知开启。若页面未出现，请稍后重试。");
        hint.setTextColor(Color.DKGRAY);
        hint.setPadding(12, 12, 12, 12);
        batteryBanner.addView(hint);

        Button battery = new Button(this);
        battery.setText("设置电池不受限制（提高自动签到可靠性）");
        battery.setOnClickListener(v -> requestIgnoreBatteryOptimization());
        batteryBanner.addView(battery);
        layout.addView(batteryBanner);

        WebView web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        layout.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        web.postDelayed(() -> web.loadUrl("http://127.0.0.1:5890"), 3500);
    }

    @Override protected void onResume() {
        super.onResume();
        applySystemBarAppearance();
        updateBatteryBannerVisibility();
    }

    /** 浅色系统栏使用深色图标，适配浅色界面。 */
    private void applySystemBarAppearance() {
        Window window = getWindow();
        if (window == null) return;

        window.setStatusBarColor(Color.rgb(245, 248, 252));
        window.setNavigationBarColor(Color.rgb(245, 248, 252));
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightBars, lightBars);
            }
        } else {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(flags);
        }
    }

    private void updateBatteryBannerVisibility() {
        if (batteryBanner == null) return;
        if (Build.VERSION.SDK_INT < 23) {
            batteryBanner.setVisibility(android.view.View.GONE);
            return;
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean exempt = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        batteryBanner.setVisibility(exempt ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    private void startLocalService() {
        Intent service = new Intent(this, MiyoService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private void requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT < 23) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "已不受电池优化限制", Toast.LENGTH_SHORT).show();
            updateBatteryBannerVisibility();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }
}
