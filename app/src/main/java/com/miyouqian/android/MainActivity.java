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
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 3001;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestNotificationPermission();
        startLocalService();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(12, 12, 12, 8);
        TextView hint = new TextView(this);
        hint.setText("米游签正在启动本地服务…\n首次启动可能需要一点时间。请保持后台通知开启。若页面未出现，请稍后重试。");
        hint.setTextColor(Color.DKGRAY);
        hint.setPadding(12, 12, 12, 12);
        layout.addView(hint);

        Button battery = new Button(this);
        battery.setText("设置电池不受限制（提高自动签到可靠性）");
        battery.setOnClickListener(v -> requestIgnoreBatteryOptimization());
        layout.addView(battery);

        WebView web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        layout.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        web.postDelayed(() -> web.loadUrl("http://127.0.0.1:5890"), 3500);
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
