package com.miyouqian.android;

import android.app.*;
import android.content.Intent;
import android.os.*;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import java.io.*;
import java.util.concurrent.Executors;

public class MiyoService extends Service {
    private static final String CHANNEL = "miyouqian_service";
    @Override public void onCreate() {
        super.onCreate(); createChannel();
        Notification n = new Notification.Builder(this, CHANNEL)
            .setContentTitle("米游签正在后台运行")
            .setContentText("每日自动任务与本地管理服务运行中")
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setOngoing(true).build();
        startForeground(1001, n);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                copyAssetIfMissing("config.example.yaml", "config.yaml");
                if (!Python.isStarted()) Python.start(new AndroidPlatform(this));
                Python.getInstance().getModule("android_entry").callAttr("start", getFilesDir().getAbsolutePath());
            } catch (Throwable e) { android.util.Log.e("MiyoQian", "Python service failed", e); }
        });
    }
    private void copyAssetIfMissing(String asset, String name) throws IOException {
        File out = new File(getFilesDir(), name); if (out.exists()) return;
        try (InputStream in = getAssets().open(asset); OutputStream os = new FileOutputStream(out)) {
            byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0) os.write(b, 0, n);
        }
    }
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "米游签后台服务", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }
    @Override public int onStartCommand(Intent i, int flags, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }
}
