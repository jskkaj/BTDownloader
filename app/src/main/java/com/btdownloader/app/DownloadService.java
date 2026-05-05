package com.btdownloader.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

/**
 * 后台下载服务 - 保持下载在后台运行
 */
public class DownloadService extends Service {
    
    private static final String CHANNEL_ID = "bt_download_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    private BTDownloadManager downloadManager;
    private boolean isRunning = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        downloadManager = new BTDownloadManager(this);
        downloadManager.initialize();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            startForeground(NOTIFICATION_ID, createNotification("BT下载服务运行中"));
            isRunning = true;
        }
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (downloadManager != null) {
            downloadManager.destroy();
        }
        isRunning = false;
    }
    
    public BTDownloadManager getDownloadManager() {
        return downloadManager;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "BT下载服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持BT下载在后台运行");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification(String content) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BT下载器")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
    
    public void updateNotification(String content) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(content));
        }
    }
}
