package net.wzyeo.safecharge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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

    String ip;
    int threshold;
    byte[] onPayload;
    byte[] offPayload;
    boolean firstRun;
    static final String CHANNEL_ID = "Foreground Service Channel";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        super.onStartCommand(intent, flags, startId);
        ip = intent.getStringExtra("ip");
        threshold = intent.getIntExtra("threshold", -1);
        onPayload = Base64.decode("AAAAKtDygfiL/5r31e+UtsWg1Iv5nPCR6LfEsNGlwOLYo4HyhueT9tTu36Lfog==", Base64.DEFAULT);
        offPayload = Base64.decode("AAAAKtDygfiL/5r31e+UtsWg1Iv5nPCR6LfEsNGlwOLYo4HyhueT9tTu3qPeow==", Base64.DEFAULT);
        firstRun = true;
        registerBatteryReceiver();
        Intent activityIntent = new Intent(this, MainActivity.class);
        activityIntent.putExtra("ip", ip);
        activityIntent.putExtra("threshold", threshold);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Foreground Service Channel", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("SafeCharge Service")
                .setContentText("Plug IP: " + ip + " Threshold: " + threshold)
                .setContentIntent(pendingIntent)
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
                sendPayload(offPayload);
            }
            if(!isCharging && batteryPct < threshold && firstRun){
                sendPayload(onPayload);
                firstRun = false;
            }
        }
    };
    void registerBatteryReceiver(){
        IntentFilter changedFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, changedFilter);
    }
    void sendPayload(final byte[] payload){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket(ip, 9999);
                    OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(payload);
                    socket.close();
                } catch(IOException e){
                    e.printStackTrace();
                }
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
