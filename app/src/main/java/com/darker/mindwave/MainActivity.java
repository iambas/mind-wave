package com.darker.mindwave;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.neurosky.connection.ConnectionStates;
import com.neurosky.connection.DataType.MindDataType;
import com.neurosky.connection.EEGPower;
import com.neurosky.connection.TgStreamHandler;
import com.neurosky.connection.TgStreamReader;

import java.text.DecimalFormat;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private TgStreamReader tgStreamReader;

    // TODO connection sdk
    private BluetoothAdapter mBluetoothAdapter;
    private BTDeviceListAdapter deviceListApapter = null;
    private Dialog selectDialog;
    private int badPacketCount = 0;

    private TextView hbeta, lbeta;
    private ImageView imgStatus;
    public static final int value = 8388608;
    GradientDrawable backgroundGradient;
    private String address = null;
    public int att = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        hbeta = (TextView) findViewById(R.id.hbeta);
        lbeta = (TextView) findViewById(R.id.lbeta);
        imgStatus = (ImageView) findViewById(R.id.img_status);
        backgroundGradient = (GradientDrawable) imgStatus.getBackground();

        try {
            // TODO
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                mBluetoothAdapter.enable();
            }
            scanDevice();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Log.i(TAG, "error:" + e.getMessage());
        }
    }

    private void start() {
        if (address != null) {
            BluetoothDevice bd = mBluetoothAdapter.getRemoteDevice(address);
            createStreamReader(bd);
            tgStreamReader.connectAndStart();
        } else {
            showToast("Please select device first!", Toast.LENGTH_SHORT);
        }
    }

    public void stop() {
        if (tgStreamReader != null) {
            tgStreamReader.stop();
            tgStreamReader.close();//if there is not stop cmd, please call close() or the data will accumulate
            tgStreamReader = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBluetoothAdapter.enable();
        scanDevice();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothAdapter.disable();
        if (tgStreamReader != null) {
            tgStreamReader.close();
            tgStreamReader = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
//        mBluetoothAdapter.disable();
    }

    // (3) Demo of getting Bluetooth device dynamically
    public void scanDevice() {
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        setUpDeviceListView();
        //register the receiver for scanning
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);
        mBluetoothAdapter.startDiscovery();
    }

    private void setUpDeviceListView() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.dialog_select_device, null);
        ListView list_select = (ListView) view.findViewById(R.id.list_select);
        selectDialog = new Dialog(this, R.style.dialog1);
        selectDialog.setContentView(view);
        //List device dialog

        deviceListApapter = new BTDeviceListAdapter(this);
        list_select.setAdapter(deviceListApapter);
        list_select.setOnItemClickListener(selectDeviceItemClickListener);

        selectDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {

            @Override
            public void onCancel(DialogInterface arg0) {
                // TODO Auto-generated method stub
                Log.e(TAG, "onCancel called!");
//                MainActivity.this.unregisterReceiver(mReceiver);
            }

        });

        selectDialog.show();

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            deviceListApapter.addDevice(device);
        }
        deviceListApapter.notifyDataSetChanged();
    }

    private TgStreamHandler callback = new TgStreamHandler() {

        @Override
        public void onStatesChanged(int connectionStates) {
            // TODO Auto-generated method stub
            Log.d(TAG, "connectionStates change to: " + connectionStates);
            switch (connectionStates) {
                case ConnectionStates.STATE_CONNECTED:
                    showToast("Connected", Toast.LENGTH_SHORT);
                    break;
                case ConnectionStates.STATE_WORKING:
                    LinkDetectedHandler.sendEmptyMessageDelayed(1234, 5000);
                    break;
                case ConnectionStates.STATE_GET_DATA_TIME_OUT:
                    //get data time out
                    break;
                case ConnectionStates.STATE_COMPLETE:
                    //read file complete
                    break;
                case ConnectionStates.STATE_STOPPED:
                    break;
                case ConnectionStates.STATE_DISCONNECTED:
                    break;
                case ConnectionStates.STATE_ERROR:
                    Log.d(TAG, "Connect error, Please try again!");
                    break;
                case ConnectionStates.STATE_FAILED:
                    Log.d(TAG, "Connect failed, Please try again!");
                    break;
            }
            Message msg = LinkDetectedHandler.obtainMessage();
            msg.what = MSG_UPDATE_STATE;
            msg.arg1 = connectionStates;
            LinkDetectedHandler.sendMessage(msg);
        }

        @Override
        public void onRecordFail(int a) {
            // TODO Auto-generated method stub
            Log.e(TAG, "onRecordFail: " + a);
        }

        @Override
        public void onChecksumFail(byte[] payload, int length, int checksum) {
            // TODO Auto-generated method stub
            badPacketCount++;
            Message msg = LinkDetectedHandler.obtainMessage();
            msg.what = MSG_UPDATE_BAD_PACKET;
            msg.arg1 = badPacketCount;
            LinkDetectedHandler.sendMessage(msg);
        }

        @Override
        public void onDataReceived(int datatype, int data, Object obj) {
            // TODO Auto-generated method stub
            Message msg = LinkDetectedHandler.obtainMessage();
            msg.what = datatype;
            msg.arg1 = data;
            msg.obj = obj;
            LinkDetectedHandler.sendMessage(msg);
        }
    };

    private static final int MSG_UPDATE_BAD_PACKET = 1001;
    private static final int MSG_UPDATE_STATE = 1002;
    private boolean isReadFilter = false;

    private Handler LinkDetectedHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1234:
                    tgStreamReader.MWM15_getFilterType();
                    isReadFilter = true;
                    Log.d(TAG, "MWM15_getFilterType ");
                    break;
                case 1235:
                    tgStreamReader.MWM15_setFilterType(MindDataType.FilterType.FILTER_60HZ);
                    Log.d(TAG, "MWM15_setFilter  60HZ");
                    LinkDetectedHandler.sendEmptyMessageDelayed(1237, 1000);
                    break;
                case 1236:
                    tgStreamReader.MWM15_setFilterType(MindDataType.FilterType.FILTER_50HZ);
                    Log.d(TAG, "MWM15_SetFilter 50HZ ");
                    LinkDetectedHandler.sendEmptyMessageDelayed(1237, 1000);
                    break;
                case 1237:
                    tgStreamReader.MWM15_getFilterType();
                    Log.d(TAG, "MWM15_getFilterType ");
                    break;
                case MindDataType.CODE_FILTER_TYPE:
                    Log.d(TAG, "CODE_FILTER_TYPE: " + msg.arg1 + "  isReadFilter: " + isReadFilter);
                    if (isReadFilter) {
                        isReadFilter = false;
                        if (msg.arg1 == MindDataType.FilterType.FILTER_50HZ.getValue()) {
                            LinkDetectedHandler.sendEmptyMessageDelayed(1235, 1000);
                        } else if (msg.arg1 == MindDataType.FilterType.FILTER_60HZ.getValue()) {
                            LinkDetectedHandler.sendEmptyMessageDelayed(1236, 1000);
                        } else {
                            Log.e(TAG, "Error filter type");
                        }
                    }
                    break;
                case MindDataType.CODE_ATTENTION:
                    att = msg.arg1;
                    Log.d("Att : ", "" + att);
                    break;
                case MindDataType.CODE_EEGPOWER:
                    DecimalFormat numFormat = new DecimalFormat("#,###,###,###");
                    EEGPower power = (EEGPower) msg.obj;
                    Log.d("TEST LBeta", "" + numFormat.format(power.lowBeta));
                    Log.d("TEST HBeta", "" + numFormat.format(power.highBeta));
                    Log.d("TEST Delta", "" + numFormat.format(power.delta));
                    Log.d("TEST LAlpha", "" + numFormat.format(power.lowAlpha));
                    Log.d("TEST HAlpha", "" + numFormat.format(power.highAlpha));
                    Log.d("TEST LGama", "" + numFormat.format(power.lowGamma));
                    Log.d("TEST MGama", "" + numFormat.format(power.middleGamma));
                    Log.d("TEST Theta", "" + numFormat.format(power.theta));
                    Log.d("Att2 : ", "" + att);
                    if (att > 0) {
//                        EEGPower power = (EEGPower) msg.obj;
                        if (power.isValidate()) {
//                            DecimalFormat numFormat = new DecimalFormat("#,###,###,###");
                            String highBeta = numFormat.format(power.highBeta);
                            String lowBeta = numFormat.format(power.lowBeta);
                            hbeta.setText("HBeta : " + highBeta);
                            lbeta.setText("LBeta : " + lowBeta);

                            if (power.highBeta > value) {
                                imgStatus.setColorFilter(Color.RED);
                            } else if (power.lowBeta > value) {
                                imgStatus.setColorFilter(Color.YELLOW);
                            } else {
                                imgStatus.setColorFilter(Color.GREEN);
                            }
                        }
                    }else{
                        imgStatus.setColorFilter(Color.BLACK);
                    }
                    break;
                case MindDataType.CODE_RAW:
                    Log.d("RAW", "" + msg.arg1);
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };


    public void showToast(final String msg, final int timeStyle) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, timeStyle).show();
            }
        });
    }

    //Select device operation
    private AdapterView.OnItemClickListener selectDeviceItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
            // TODO Auto-generated method stub
            Log.d(TAG, "Rico ####  list_select onItemClick     ");
            if (mBluetoothAdapter.isDiscovering()) {
                mBluetoothAdapter.cancelDiscovery();
            }
            //unregister receiver
//            MainActivity.this.unregisterReceiver(mReceiver);

            BluetoothDevice mBluetoothDevice = deviceListApapter.getDevice(arg2);
            try {
                selectDialog.dismiss();
                selectDialog = null;
            }catch (Exception e){
                Log.e(TAG, e.getMessage());
            }

            Log.d(TAG, "onItemClick name: " + mBluetoothDevice.getName() + " , address: " + mBluetoothDevice.getAddress());
            address = mBluetoothDevice.getAddress().toString();

            //ger remote device
            BluetoothDevice remoteDevice = mBluetoothAdapter.getRemoteDevice(mBluetoothDevice.getAddress().toString());

            //bind and connect
            //bindToDevice(remoteDevice); // create bond works unstable on Samsung S5
            //showToast("pairing ...",Toast.LENGTH_SHORT);

            tgStreamReader = createStreamReader(remoteDevice);
            tgStreamReader.connectAndStart();
        }
    };

    /**
     * If the TgStreamReader is created, just change the bluetooth
     * else create TgStreamReader, set data receiver, TgStreamHandler and parser
     *
     * @param bd
     * @return TgStreamReader
     */
    public TgStreamReader createStreamReader(BluetoothDevice bd) {

        if (tgStreamReader == null) {
            // Example of constructor public TgStreamReader(BluetoothDevice mBluetoothDevice,TgStreamHandler tgStreamHandler)
            tgStreamReader = new TgStreamReader(bd, callback);
            tgStreamReader.startLog();
        } else {
            // (1) Demo of changeBluetoothDevice
            tgStreamReader.changeBluetoothDevice(bd);

            // (4) Demo of setTgStreamHandler, you can change the data handler by this function
            tgStreamReader.setTgStreamHandler(callback);
        }
        return tgStreamReader;
    }

    //The BroadcastReceiver that listens for discovered devices
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "mReceiver()");
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d(TAG, "mReceiver found device: " + device.getName());

                // update to UI
                deviceListApapter.addDevice(device);
                deviceListApapter.notifyDataSetChanged();
            }
        }
    };
}
