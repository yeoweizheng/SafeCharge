package net.wzyeo.safecharge;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.icu.util.Output;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.apache.commons.net.util.SubnetUtils;
import org.w3c.dom.Text;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        AdapterView.OnItemClickListener, View.OnClickListener {

    String wlanIP;
    ArrayList<String> ipFound;
    String selectedPlugIP;
    Button onButton;
    Button offButton;
    byte[] onPayload;
    byte[] offPayload;
    ArrayList<RowItem> rowItemList;
    RowAdapter rowAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        onButton = findViewById(R.id.button_on);
        offButton = findViewById(R.id.button_off);
        onButton.setOnClickListener(this);
        offButton.setOnClickListener(this);
        onPayload = Base64.decode("AAAAKtDygfiL/5r31e+UtsWg1Iv5nPCR6LfEsNGlwOLYo4HyhueT9tTu36Lfog==", Base64.DEFAULT);
        offPayload = Base64.decode("AAAAKtDygfiL/5r31e+UtsWg1Iv5nPCR6LfEsNGlwOLYo4HyhueT9tTu3qPeow==", Base64.DEFAULT);
        rowItemList = new ArrayList<>();
        ListView listView = findViewById(R.id.listview_ip_list);
        rowAdapter = new RowAdapter(this, R.layout.activity_main, rowItemList);
        listView.setAdapter(rowAdapter);
        listView.setOnItemClickListener(this);
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
                    boolean validIP = false;
                    try{
                        Socket socket = new Socket(ip, 9999);
                        ipFound.add(ip);
                        validIP = true;
                        socket.close();
                    } catch(UnknownHostException e){
                    } catch(IOException e){
                    } finally {
                        final boolean validIP2 = validIP;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                scanNetworkCallback(ip, validIP2);
                            }
                        });
                    }
                }
            }).start();
        }
    }
    void scanNetworkCallback(String ip, boolean validIP){
        if(validIP){
            rowItemList.add(new RowItem(ip));
            rowAdapter.notifyDataSetChanged();
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
    @Override
    public void onItemClick(AdapterView<?> adapter, View view, int pos, long id){
        RowItem item = (RowItem) adapter.getItemAtPosition(pos);
        selectedPlugIP = item.ipAddress;
        TextView selectedPlugView = findViewById(R.id.textview_selected_plug);
        selectedPlugView.setText(selectedPlugIP);
        onButton.setVisibility(View.VISIBLE);
        offButton.setVisibility(View.VISIBLE);
    }
    @Override
    public void onClick(View view){
        switch (view.getId()) {
            case R.id.button_on:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Socket socket = new Socket(selectedPlugIP, 9999);
                            OutputStream outputStream = socket.getOutputStream();
                            outputStream.write(onPayload);
                        } catch(IOException e){}
                    }
                }).start();
                break;
            case R.id.button_off:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Socket socket = new Socket(selectedPlugIP, 9999);
                            OutputStream outputStream = socket.getOutputStream();
                            outputStream.write(offPayload);
                        } catch(IOException e){}
                    }
                }).start();
                break;
        }
    }
    public class RowItem{
        String ipAddress;
        public RowItem(String ipAddress){
            this.ipAddress = ipAddress;
        }
    }
    public class RowAdapter extends ArrayAdapter{
        Context context;
        public RowAdapter(Context context, int resourceId, List<RowItem> items){
            super(context, resourceId, items);
            this.context = context;
        }
        @Override
        public View getView(int position, View view, ViewGroup parent){
            RowItemView row = null;
            RowItem item = (RowItem) getItem(position);
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
            if(view == null){
                view = inflater.inflate(R.layout.rowitem_ip_list, null);
                row = new RowItemView();
                row.ipAddressView = view.findViewById(R.id.textview_ip_address);
                view.setTag(row);
            } else {
                row = (RowItemView) view.getTag();
            }
            row.ipAddressView.setText(item.ipAddress);
            return view;
        }
    }
    public class RowItemView{
        TextView ipAddressView;
    }

}
