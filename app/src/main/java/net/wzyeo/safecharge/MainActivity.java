package net.wzyeo.safecharge;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.net.util.SubnetUtils;

import java.io.IOException;
import java.io.OutputStream;
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
    Button scanButton;
    Button startServiceButton;
    Button stopServiceButton;
    byte[] onPayload;
    byte[] offPayload;
    ArrayList<RowItem> rowItemList;
    RowAdapter rowAdapter;
    TextView ipView;
    TextView statusView;
    TextView levelView;
    TextView serviceStatusView;
    TextView selectedPlugView;
    EditText chargingThresholdView;
    ListView listView;
    Intent serviceIntent;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusView = findViewById(R.id.textview_battery_status);
        levelView = findViewById(R.id.textview_battery_level);
        serviceStatusView = findViewById(R.id.textview_service_status);
        ipView = findViewById(R.id.textview_device_ip);
        selectedPlugView = findViewById(R.id.textview_selected_plug);
        chargingThresholdView = findViewById(R.id.textview_charging_threshold);
        onButton = findViewById(R.id.button_on);
        offButton = findViewById(R.id.button_off);
        startServiceButton = findViewById(R.id.button_start_service);
        stopServiceButton = findViewById(R.id.button_stop_service);
        scanButton = findViewById(R.id.button_scan);
        onButton.setOnClickListener(this);
        offButton.setOnClickListener(this);
        startServiceButton.setOnClickListener(this);
        stopServiceButton.setOnClickListener(this);
        scanButton.setOnClickListener(this);
        onPayload = Base64.decode("AAAAKtDygfiL/5r31e+UtsWg1Iv5nPCR6LfEsNGlwOLYo4HyhueT9tTu36Lfog==", Base64.DEFAULT);
        offPayload = Base64.decode("AAAAKtDygfiL/5r31e+UtsWg1Iv5nPCR6LfEsNGlwOLYo4HyhueT9tTu3qPeow==", Base64.DEFAULT);
        rowItemList = new ArrayList<>();
        listView = findViewById(R.id.listview_ip_list);
        rowAdapter = new RowAdapter(this, R.layout.activity_main, rowItemList);
        listView.setAdapter(rowAdapter);
        listView.setOnItemClickListener(this);
        serviceIntent = new Intent(this, MonitorService.class);
        getBatteryStatus();
    }
    @Override
    public void onResume(){
        super.onResume();
        if(getIntent().getStringExtra("ip") != null){
            selectedPlugIP = getIntent().getStringExtra("ip");
            selectedPlugView.setText(selectedPlugIP);
        } else{
            resetIPInfo();
        }
        chargingThresholdView.setText(getIntent().getIntExtra("threshold", 80) + "");
        getDeviceWlanIp();
        setPlugControls();
        setServiceStatus();
    }
    @Override
    protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
    }
    void resetIPInfo(){
        selectedPlugIP = null;
        selectedPlugView.setText("(Not selected)");
    }
    void getDeviceWlanIp(){
        wlanIP = null;
        ipView.setText("(Not available)");
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
                    ipView.setText(wlanIP);
                }
            }
        } catch(SocketException e){
            e.printStackTrace();
        }
        scanNetwork();
    }
    void scanNetwork(){
        ipFound = new ArrayList<>();
        rowItemList.clear();
        rowAdapter.notifyDataSetChanged();
        if(wlanIP == null) return;
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
    void updateBatteryStatus(String status, int level){
        statusView.setText(status);
        levelView.setText(level + "");
    }
    void getBatteryStatus(){
        final BatteryManager batteryManager = (BatteryManager)getSystemService(BATTERY_SERVICE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                int statusCode;
                int level;
                String status;
                while(true) {
                    statusCode = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
                    if(statusCode == BatteryManager.BATTERY_STATUS_CHARGING || statusCode == BatteryManager.BATTERY_STATUS_FULL){
                        status = "Charging";
                    } else {
                        status = "Not charging";
                    }
                    level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    final String status2 = status;
                    final int level2 = level;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateBatteryStatus(status2, level2);
                        }
                    });
                    try{
                        Thread.sleep(1000);
                    } catch(InterruptedException e){}
                }

            }
        }).start();
    }
    void setPlugControls(){
        if(selectedPlugIP != null){
            onButton.setEnabled(true);
            offButton.setEnabled(true);
            onButton.setAlpha(1f);
            offButton.setAlpha(1f);
        } else {
            onButton.setEnabled(false);
            offButton.setEnabled(false);
            onButton.setAlpha(0.4f);
            offButton.setAlpha(0.4f);
        }
    }
    void sendPayload(final byte[] payload){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket(selectedPlugIP, 9999);
                    OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(payload);
                    socket.close();
                } catch(IOException e){
                    e.printStackTrace();
                }
            }
        }).start();
    }
    boolean isServiceRunning(){
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for(ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)){
            if(service.service.getClassName().equals(MonitorService.class.getName())) return true;
        }
        return false;
    }
    void setServiceStatus(){
        if(isServiceRunning()){
            serviceStatusView.setText("Started");
            startServiceButton.setEnabled(false);
            startServiceButton.setAlpha(0.4f);
            stopServiceButton.setEnabled(true);
            stopServiceButton.setAlpha(1f);
        } else {
            serviceStatusView.setText("Not started");
            if(selectedPlugIP != null) {
                startServiceButton.setEnabled(true);
                startServiceButton.setAlpha(1f);
            } else {
                startServiceButton.setEnabled(false);
                startServiceButton.setAlpha(0.4f);
            }
            stopServiceButton.setEnabled(false);
            stopServiceButton.setAlpha(0.4f);
        }
    }
    @Override
    public void onItemClick(AdapterView<?> adapter, View view, int pos, long id){
        RowItem item = (RowItem) adapter.getItemAtPosition(pos);
        selectedPlugIP = item.ipAddress;
        selectedPlugView.setText(selectedPlugIP);
        setPlugControls();
        setServiceStatus();
    }
    @Override
    public void onClick(View view){
        switch (view.getId()) {
            case R.id.button_start_service:
                int threshold = Integer.parseInt(chargingThresholdView.getText().toString());
                serviceIntent.putExtra("ip", selectedPlugIP);
                serviceIntent.putExtra("threshold", threshold);
                stopService(serviceIntent);
                startService(serviceIntent);
                setServiceStatus();
                Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show();
                break;
            case R.id.button_stop_service:
                stopService(serviceIntent);
                setServiceStatus();
                Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
                break;
            case R.id.button_scan:
                resetIPInfo();
                getDeviceWlanIp();
                setPlugControls();
                setServiceStatus();
                break;
            case R.id.button_on:
                sendPayload(onPayload);
                break;
            case R.id.button_off:
                sendPayload(offPayload);
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
    @Override
    public void onBackPressed(){
        finish();
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
    }

}
