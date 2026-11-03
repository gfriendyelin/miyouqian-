package com.miyouqian.android;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 3001;
    private static final String CONSOLE_URL = "http://127.0.0.1:5890";
    private static final String PROBE_URL = "http://127.0.0.1:5890/api/auth/status";
    private static final long POLL_INTERVAL_MS = 400;
    private static final long POLL_TIMEOUT_MS = 45000;

    private LinearLayout batteryBanner;
    private TextView statusHint;
    private Button retryButton;
    private WebView web;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean webLoaded = false;
    private volatile boolean mainFrameError = false;
    private volatile boolean polling = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestNotificationPermission();
        startLocalService();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(12, 12, 12, 8);

        // 顶部提示和电池设置按钮作为一个整体：系统确认已豁免电池优化后隐藏。
        batteryBanner = new LinearLayout(this);
        batteryBanner.setOrientation(LinearLayout.VERTICAL);

        TextView batteryHint = new TextView(this);
        batteryHint.setText("为保证每日自动签到可靠执行，建议关闭电池优化。");
        batteryHint.setTextColor(Color.DKGRAY);
        batteryHint.setPadding(12, 4, 12, 4);
        batteryBanner.addView(batteryHint);

        Button battery = new Button(this);
        battery.setText("设置电池不受限制");
        battery.setOnClickListener(v -> requestIgnoreBatteryOptimization());
        batteryBanner.addView(battery);
        layout.addView(batteryBanner);

        // 启动状态提示：本地服务就绪前用它替代 WebView 白屏。
        statusHint = new TextView(this);
        statusHint.setText("米游签正在启动本地服务…\n首次启动需初始化运行环境，约 10~30 秒，请稍候。");
        statusHint.setTextColor(Color.rgb(60, 90, 130));
        statusHint.setGravity(Gravity.CENTER);
        statusHint.setPadding(12, 16, 12, 16);
        layout.addView(statusHint);

        // 超时后的重试入口。
        retryButton = new Button(this);
        retryButton.setText("重新加载");
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(v -> retryLoad());
        layout.addView(retryButton);

        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setBackgroundColor(Color.rgb(245, 248, 252));
        // 服务就绪前先占位但不显示，避免暴露纯白页面。
        web.setVisibility(View.INVISIBLE);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (mainFrameError) return;
                webLoaded = true;
                statusHint.setVisibility(View.GONE);
                retryButton.setVisibility(View.GONE);
                web.setVisibility(View.VISIBLE);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    mainFrameError = true;
                    webLoaded = false;
                    statusHint.setVisibility(View.VISIBLE);
                    statusHint.setText("页面加载失败，正在重试…");
                    scheduleReload();
                }
            }
        });
        layout.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);

        // 等窗口 DecorView 完成创建后再设置系统栏，避免启动阶段空指针。
        applySystemBarAppearance();
        startPolling();
    }

    /** 用户点击「重新加载」：清空状态并重新探测本地服务。 */
    private void retryLoad() {
        webLoaded = false;
        mainFrameError = false;
        retryButton.setVisibility(View.GONE);
        statusHint.setVisibility(View.VISIBLE);
        statusHint.setText("正在重新连接本地服务…");
        startPolling();
    }

    /** 后台线程轮询探测本地服务是否就绪，就绪后回到主线程加载页面。 */
    private void startPolling() {
        if (webLoaded || polling) return;
        polling = true;
        final long start = SystemClock.elapsedRealtime();
        final long[] lastShownSec = {-1};
        Thread t = new Thread(() -> {
            while (!webLoaded) {
                if (probeReady()) {
                    mainHandler.post(() -> {
                        if (webLoaded) return;
                        webLoaded = true;
                        mainFrameError = false;
                        statusHint.setText("本地服务已就绪，正在加载页面…");
                        web.setVisibility(View.VISIBLE);
                        web.loadUrl(CONSOLE_URL);
                    });
                    polling = false;
                    return;
                }
                long waitedSec = (SystemClock.elapsedRealtime() - start) / 1000;
                if (waitedSec >= POLL_TIMEOUT_MS / 1000) {
                    mainHandler.post(() -> {
                        polling = false;
                        if (!webLoaded) {
                            statusHint.setText("本地服务启动超时。\n请确认通知栏存在「米游签正在后台运行」，然后点击重试。");
                            retryButton.setVisibility(View.VISIBLE);
                        }
                    });
                    return;
                }
                if (waitedSec != lastShownSec[0]) {
                    lastShownSec[0] = waitedSec;
                    final long sec = waitedSec;
                    mainHandler.post(() -> {
                        if (!webLoaded) {
                            statusHint.setText("米游签正在启动本地服务…（已等待 " + sec + " 秒）");
                        }
                    });
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    polling = false;
                    return;
                }
            }
            polling = false;
        });
        t.setDaemon(true);
        t.start();
    }

    /** 页面加载失败后延迟重试（避免失败风暴）。 */
    private void scheduleReload() {
        mainHandler.postDelayed(() -> {
            if (!webLoaded) {
                mainFrameError = false;
                startPolling();
            }
        }, 1500);
    }

    /** 轻量探测本地服务：/api/auth/status 无需鉴权，返回 200 即视为就绪。 */
    private boolean probeReady() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(PROBE_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            return code == 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        // onResume 时也延后到 DecorView 就绪后执行，避免过早访问窗口控制器。
        View decor = getWindow() == null ? null : getWindow().getDecorView();
        if (decor != null) {
            decor.post(this::applySystemBarAppearance);
        }
        updateBatteryBannerVisibility();
        // 从电池优化设置页返回时，若还没加载成功则继续/恢复探测。
        if (!webLoaded && !polling) startPolling();
    }

    /** 浅色系统栏使用深色图标，使用兼容 API 避免启动阶段 WindowInsetsController 空指针。 */
    private void applySystemBarAppearance() {
        Window window = getWindow();
        if (window == null) return;

        View decor = window.getDecorView();
        if (decor == null) return;

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.rgb(245, 248, 252));
        window.setNavigationBarColor(Color.rgb(245, 248, 252));
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        int flags = decor.getSystemUiVisibility();
        flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    private void updateBatteryBannerVisibility() {
        if (batteryBanner == null) return;
        if (Build.VERSION.SDK_INT < 23) {
            batteryBanner.setVisibility(View.GONE);
            return;
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean exempt = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        batteryBanner.setVisibility(exempt ? View.GONE : View.VISIBLE);
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
