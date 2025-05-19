package com.accessibilitymanager;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class daemonService extends Service {

    private SettingsValueChangeContentObserver mContentOb;
    SharedPreferences sp;
    Notification.Builder notification;
    NotificationManager systemService;
    String tmpSettingValue;
    List<String> l;
    PackageManager packageManager;

    final private BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String set = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (set == null) set = "";
            if (tmpSettingValue.equals(set)) return;
            doDaemon(set);
        }
    };

    //自定义一个内容监视器
    class SettingsValueChangeContentObserver extends ContentObserver {
        public SettingsValueChangeContentObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (s == null) s = "";
            //如果这俩相等，说明本次变动是APP自己改的。于是就不需要做处理。
            if (tmpSettingValue.equals(s)) return;
            doDaemon(s);
        }
    }


    private void doDaemon(String s) {
        String list = sp.getString("daemon", "");
        String[] serviceNames = Pattern.compile(":").split(list);
        StringBuilder add = new StringBuilder();
        StringBuilder add1 = new StringBuilder();
        for (String serviceName : serviceNames) {
            if (serviceName == null || serviceName.equals("null") || serviceName.isEmpty() || s.contains(serviceName) || !l.contains(serviceName))
                continue;

            ApplicationInfo applicationInfo = new ApplicationInfo();
            try {
                applicationInfo = packageManager.getApplicationInfo(serviceName.substring(0, serviceName.indexOf("/")), PackageManager.GET_META_DATA);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            String packageLabel = applicationInfo.loadLabel(packageManager).toString();
            add.append(serviceName).append(":");
            add1.append(packageLabel).append("\n");
            if (sp.getBoolean("toast", true))
                Toast.makeText(daemonService.this, "保活" + packageLabel, Toast.LENGTH_SHORT).show();
        }
        if (add.length() > 0) {
            // 添加1秒延时后再保活
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
            tmpSettingValue = add + s;
            Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, tmpSettingValue);
            systemService.notify(1, notification.build());
            }, 1000);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sp = getSharedPreferences("data", 0);
        if (sp.getString("daemon", "").isEmpty()) {
            stopSelf();
            return;
        }
        
        // 检查是否在直接启动模式下
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (getSystemService(Context.USER_SERVICE) == null) {
                Log.d("daemonService", "Running in direct boot mode");
                // 在直接启动模式下，延迟一些操作直到用户解锁
                return;
            }
        }
        
        packageManager = getPackageManager();
        Toast.makeText(daemonService.this, "启动保活", Toast.LENGTH_SHORT).show();
        List<AccessibilityServiceInfo> list = ((AccessibilityManager) getApplicationContext().getSystemService(Context.ACCESSIBILITY_SERVICE)).getInstalledAccessibilityServiceList();
        l = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            l.add(list.get(i).getId());
        }
        //注册监视器，读取当前设置项并存到tmpsettingValue
        mContentOb = new SettingsValueChangeContentObserver();
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), true, mContentOb);
        tmpSettingValue = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (tmpSettingValue == null) tmpSettingValue = "";

        registerReceiver(myReceiver, new IntentFilter("android.intent.action.SCREEN_ON"));
        
        // 创建通知渠道
        systemService = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(
                "daemon",
                "保活无障碍",
                NotificationManager.IMPORTANCE_HIGH  // 提高重要性
            );
            notificationChannel.enableLights(false);
            notificationChannel.setShowBadge(false);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);  // 在锁屏界面显示
            systemService.createNotificationChannel(notificationChannel);
        }

        // 创建通知
        notification = new Notification.Builder(this)
                .setAutoCancel(false)  // 设置为不可取消
                .setOngoing(true)      // 设置为持续通知
                .setContentTitle("海绵宝宝，猜猜我有几颗糖~")
                .setContentText("猜对了两颗都给你！")
                .setSmallIcon(R.drawable.tile)
                .setPriority(Notification.PRIORITY_HIGH);  // 提高优先级

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notification
                    .setColor(getColor(R.color.bg))
                    .setSmallIcon(Icon.createWithResource(this, R.drawable.tile))
                    .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification.setChannelId("daemon");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            notification.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        startForeground(1, notification.build());

        //先做一次保活
        doDaemon(tmpSettingValue);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(myReceiver);
        getContentResolver().unregisterContentObserver(mContentOb);
        Toast.makeText(daemonService.this, "停止保活", Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

}
