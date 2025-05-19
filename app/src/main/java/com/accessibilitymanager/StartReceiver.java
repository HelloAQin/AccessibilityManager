package com.accessibilitymanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class StartReceiver extends BroadcastReceiver {
    private static final String TAG = "StartReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received broadcast: " + action);
        
        // 检查是否是开机相关的广播
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            
            SharedPreferences sharedPreferences = context.getSharedPreferences("data", 0);
            boolean shouldStart = sharedPreferences.getBoolean("boot", true);
            Log.d(TAG, "Should start service: " + shouldStart);
            
            if (shouldStart) {
                try {
                    Intent serviceIntent = new Intent(context, daemonService.class);
                    // 添加标志以支持直接启动
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        serviceIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Log.d(TAG, "Starting foreground service");
                        context.startForegroundService(serviceIntent);
                    } else {
                        Log.d(TAG, "Starting service");
                        context.startService(serviceIntent);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start service", e);
                }
            }
        }
    }
}
