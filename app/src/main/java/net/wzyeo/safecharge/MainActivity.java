package net.wzyeo.safecharge;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {

    String wlanIP;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getDeviceWlanIp();
    }
    void getDeviceWlanIp(){
        String ip;
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
                    ip = address.getHostAddress();
                }
            }
        } catch(SocketException e){
            e.printStackTrace();
        }
    }
}
