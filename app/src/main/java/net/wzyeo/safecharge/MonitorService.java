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
import android.content.ServiceConnection;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
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
    boolean triggeredOff;
    static final String CHANNEL_ID = "Foreground Service Channel";
    BatteryStatusRunnable runnable;
    IMonitorService callback;
    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        super.onStartCommand(intent, flags, startId);
        ip = intent.getStringExtra("ip");
        threshold = intent.getIntExtra("threshold", -1);
        onPayload = Base64.decode("AAAAKtDygfiL/5r31e+UtsWg1Iv5nPCR6LfEsNGlwOLYo4HyhueT9tTu36Lfog==", Base64.DEFAULT);
        offPayload = Base64.decode("AAAAKtDygfiL/5r31e+UtsWg1Iv5nPCR6LfEsNGlwOLYo4HyhueT9tTu3qPeow==", Base64.DEFAULT);
        firstRun = true;
        triggeredOff = false;
        getBatteryStatus();
        Intent activityIntent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        } else {
            Notification notification = new NotificationCompat.Builder(this)
                    .setContentTitle("SafeCharge Service")
                    .setContentText("Plug IP: " + ip + " Threshold: " + threshold)
                    .build();
            startForeground(1, notification);
        }
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        startWakeLock();
        return START_REDELIVER_INTENT;
    }
    void updateBatteryStatus(int level){
        if(level >= threshold){
            sendPayload(offPayload);
            stopSelf();
            if(callback != null){
                callback.unbindService();
            }
        }
        if(level < threshold && firstRun){
            sendPayload(onPayload);
            firstRun = false;
        }
    }
    class BatteryStatusRunnable implements Runnable {
        BatteryManager batteryManager;
        boolean stopped = false;
        public BatteryStatusRunnable(BatteryManager batteryManager) {
            this.batteryManager = batteryManager;
        }
        public void stop(){
            sendPayload(offPayload);
            stopped = true;
        }
        @Override
        public void run() {
            int level;
            while (true && !stopped) {
                level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                updateBatteryStatus(level);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            }
        }
    }
    void getBatteryStatus(){
        final BatteryManager batteryManager = (BatteryManager)getSystemService(BATTERY_SERVICE);
        runnable = new BatteryStatusRunnable(batteryManager);
        new Thread(runnable).start();
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
    public String getIP(){
        return this.ip;
    }
    public int getThreshold(){
        return this.threshold;
    }
    IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder {
        MonitorService getService(){
            return MonitorService.this;
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    @Override
    public boolean onUnbind(Intent intent){
        this.callback = null;
        return super.onUnbind(intent);
    }
    public interface IMonitorService{
        void unbindService();
    }
    public void setCallback(IMonitorService callback){
        this.callback = callback;
    }
    public void startWakeLock(){
        if(wakeLock != null){
            if(!wakeLock.isHeld()) wakeLock.acquire();
        } else {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MonitorService::WakeLockTag");
            wakeLock.acquire();
        }
    }
    public void stopWakeLock(){
        if(wakeLock != null) wakeLock.release();
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        runnable.stop();
        stopWakeLock();
    }
}
