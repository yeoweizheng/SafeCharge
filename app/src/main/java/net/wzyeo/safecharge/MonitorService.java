package net.wzyeo.safecharge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class MonitorService extends Service {
    public MonitorService() {
    }

    Thread thread;
    String ip;
    int threshold;
    byte[] onPayload;
    byte[] offPayload;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        super.onStartCommand(intent, flags, startId);
        ip = intent.getStringExtra("ip");
        threshold = intent.getIntExtra("threshold", -1);
        onPayload = Base64.decode("AAAAKtDygfiL/5r31e+UtsWg1Iv5nPCR6LfEsNGlwOLYo4HyhueT9tTu36Lfog==", Base64.DEFAULT);
        offPayload = Base64.decode("AAAAKtDygfiL/5r31e+UtsWg1Iv5nPCR6LfEsNGlwOLYo4HyhueT9tTu3qPeow==", Base64.DEFAULT);
        registerBatteryReceiver();
        NotificationChannel channel = new NotificationChannel("Foreground Service Channel", "Foreground Service Channel", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
        Notification notification = new NotificationCompat.Builder(this, "Foreground Service Channel")
                .setContentTitle("SafeCharge Service")
                .build();
        startForeground(1, notification);
        return START_REDELIVER_INTENT;
    }

    BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            boolean isCharging = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                    plugged == BatteryManager.BATTERY_PLUGGED_USB;
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            float batteryPct = level * 100 / (float) scale;
            if(isCharging && batteryPct >= threshold){
                turnOff();
            }
        }
    };
    void registerBatteryReceiver(){
        IntentFilter changedFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, changedFilter);
    }
    void turnOn(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket(ip, 9999);
                    OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(onPayload);
                } catch(IOException e){}
            }
        }).start();
    }
    void turnOff(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket(ip, 9999);
                    OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(offPayload);
                } catch(IOException e){}
            }
        }).start();
    }
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        unregisterReceiver(batteryReceiver);
    }
}
