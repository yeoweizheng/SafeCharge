package net.wzyeo.safecharge;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import org.apache.commons.net.util.SubnetUtils;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {

    String wlanIP;
    ArrayList<String> ipFound;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getDeviceWlanIp();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    getBatteryStatus();
                    try{
                        Thread.sleep(1000);
                    } catch(InterruptedException e){
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }
    void getDeviceWlanIp(){
        try{
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while(interfaces.hasMoreElements()){
                NetworkInterface iface = interfaces.nextElement();
                if(iface.isLoopback() || !iface.isUp()){
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                if(addresses == null) continue;
                while(addresses.hasMoreElements()){
                    InetAddress address = addresses.nextElement();
                    if(!(address instanceof Inet4Address)) continue;
                    if(!iface.getDisplayName().contains("wlan")) continue;
                    wlanIP = address.getHostAddress();
                    TextView ipView = findViewById(R.id.textview_device_ip);
                    ipView.setText(wlanIP);
                }
            }
        } catch(SocketException e){
            e.printStackTrace();
        }
        if(!wlanIP.isEmpty()) {
            scanNetwork();
        }
    }
    void scanNetwork(){
        ipFound = new ArrayList<>();
        SubnetUtils utils = new SubnetUtils(wlanIP + "/24");
        String[] allIps = utils.getInfo().getAllAddresses();
        ArrayList<String> openIps = new ArrayList<String>();
        for(int i = 0; i < allIps.length; i++){
            final String ip = allIps[i];
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try{
                        Socket socket = new Socket(ip, 9999);
                        Log.d("weizheng", ip);
                        ipFound.add(ip);
                    } catch(UnknownHostException e){
                    } catch(IOException e){
                    }
                }
            }).start();
        }
    }
    void getBatteryStatus(){
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = getApplicationContext().registerReceiver(null, intentFilter);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
        TextView statusView = findViewById(R.id.textview_battery_status);
        if(isCharging){
            statusView.setText("Charging");
        } else {
            statusView.setText("Not charging");
        }
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level * 100 / (float) scale;
        TextView levelView = findViewById(R.id.textview_battery_level);
        levelView.setText(batteryPct + "");
    }
}
