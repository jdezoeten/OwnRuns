/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dezoeten.ownruns;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import static android.os.SystemClock.elapsedRealtime;
import static net.dezoeten.ownruns.BluetoothLeService.FitShowState.FIT_STATE_GET_SYS_INFO_MODEL;
import static net.dezoeten.ownruns.BluetoothLeService.FitShowState.FIT_STATE_CONTROL;
import static net.dezoeten.ownruns.BluetoothLeService.FitShowState.FIT_STATE_GET_SYS_STATUS;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();
    private Timer mFitShowTimer;
    private TimerTask mFitShowTimerTask;
    private float mTargetSpeed = 8f;
    private int mTargetIncline = 1;
    private float mPrevDistance = -1f;

    public enum FitShowState {
        NO_FIT_SHOW_DEVICE,
        FIT_STATE_GET_SYS_INFO_MODEL,
        FIT_STATE_GET_MODEL,
        FIT_STATE_GET_SYS_STATUS,
        FIT_STATE_CONTROL
    }

    private class BluetoothLeConnection
    {
        private String mBluetoothDeviceAddress;
        private BluetoothGatt mBluetoothGatt;
        private int mConnectionState = STATE_DISCONNECTED;
        private List<BluetoothGattService> mServiceList;
        private List<BluetoothGattCharacteristic> mToSubscribeList;

        FitShowState    mFitShowState;
    };

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private List<BluetoothLeConnection> mBluetoothLeConnectionList;

    private boolean mTreadmillConnected = false;
    private boolean mFootpodConnected = false;


    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_SENSOR_DATA =
            "com.example.bluetooth.le.ACTION_SENSOR_DATA";

    public final static String ACTION_ACTUATOR_CONTROL =
            "com.example.bluetooth.le.ACTION_ACTUATOR_CONTROL";


    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";


    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static String TIMESTAMP =
            "com.example.bluetooth.le.TIMESTAMP";
    public final static String SENSOR_NAME =
            "com.example.bluetooth.le.SENSOR_NAME";
    public final static String SENSOR_ID =
            "com.example.bluetooth.le.SENSOR_ID";
    public final static String HEART_RATE =
            "com.example.bluetooth.le.HEART_RATE";
    public final static String SPEED =
            "com.example.bluetooth.le.SPEED";
    public final static String POWER =
            "com.example.bluetooth.le.POWER";
    public final static String DISTANCE =
            "com.example.bluetooth.le.DISTANCE";
    public final static String CADENCE =
            "com.example.bluetooth.le.CADENCE";
    public final static String STRIDE =
            "com.example.bluetooth.le.STRIDE";
    public final static String INCLINE =
            "com.example.bluetooth.le.INCLINE";

    public final static UUID UUID_GAP_DEVICE_NAME =
            UUID.fromString(BluetoothLeGattAttributes.GAP_DEVICE_NAME);
    public final static UUID UUID_GAP_APPEARANCE =
            UUID.fromString(BluetoothLeGattAttributes.GAP_APPEARANCE);

    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(BluetoothLeGattAttributes.HEART_RATE_MEASUREMENT);

    public final static UUID UUID_RSC_FEATURE =
            UUID.fromString(BluetoothLeGattAttributes.RSC_FEATURE);
    public final static UUID UUID_RSC_MEASUREMENT =
            UUID.fromString(BluetoothLeGattAttributes.RSC_MEASUREMENT);

    public final static UUID UUID_CYCLING_POWER_FEATURE =
            UUID.fromString(BluetoothLeGattAttributes.CYCLING_POWER_FEATURE);
    public final static UUID UUID_CYCLING_POWER_MEASUREMENT =
            UUID.fromString(BluetoothLeGattAttributes.CYCLING_POWER_MEASUREMENT);

    public final static UUID UUID_FITSHOW_CUSTOM_SERVICE =
            UUID.fromString(BluetoothLeGattAttributes.FITSHOW_CUSTOM_SERVICE);
    public final static UUID UUID_FITSHOW_COMMAND =
            UUID.fromString(BluetoothLeGattAttributes.FITSHOW_COMMAND);
    public final static UUID UUID_FITSHOW_RESPONSE =
            UUID.fromString(BluetoothLeGattAttributes.FITSHOW_RESPONSE);


    private Handler mHandler;

    // ACTION_SENSOR_DATA: new data from sensor.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_ACTUATOR_CONTROL.equals(action)) {
                Bundle bundle = intent.getExtras();
                if (bundle.containsKey(SENSOR_ID) ) {
                    String deviceId = bundle.getString(SENSOR_ID);

                    BluetoothLeConnection lC = null;
                    for (BluetoothLeConnection lTry: mBluetoothLeConnectionList) {
                        if (lTry.mBluetoothDeviceAddress.equals(deviceId) ) {
                            lC = lTry;
                        }
                    }
                    if (null == lC) return;

                    if (bundle.containsKey(SPEED) ) {
                        mTargetSpeed = bundle.getFloat(SPEED);
                        if (bundle.containsKey(INCLINE)) {

                            mTargetIncline = bundle.getInt(INCLINE);
                            /* Schedule the update */
                            lC.mFitShowState = FIT_STATE_CONTROL;
                        }
                    }
                }
            }
        }
    };

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            BluetoothLeConnection lC = null;
            for (BluetoothLeConnection lTry: mBluetoothLeConnectionList) {
                if (lTry.mBluetoothGatt == gatt) {
                    lC = lTry;
                }
            }
            if (null == lC) return;

            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                lC.mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                lC.mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (gatt.getDevice().getAddress().equals("08:7C:BE:8E:BF:80") ) {
                    if (null != mFitShowTimer) {
                        mFitShowTimer.cancel();
                        mFitShowTimer = null;
                        mTreadmillConnected = false;
                    }
                }
                else if (gatt.getDevice().getAddress().equals("50:65:83:A1:2E:4D")
                        || gatt.getDevice().getAddress().equals("C9:F3:94:15:7A:EA")
                    )
                {
                    mFootpodConnected = false;
                }

                intentAction = ACTION_GATT_DISCONNECTED;
                lC.mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothLeConnection lC = null;
            for (BluetoothLeConnection lTry: mBluetoothLeConnectionList) {
                if (lTry.mBluetoothGatt == gatt) {
                    lC = lTry;
                }
            }
            if (null == lC) return;

            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                //Log.w(TAG, "Services Discovered" );

                lC.mToSubscribeList = new ArrayList<>();
                lC.mServiceList = lC.mBluetoothGatt.getServices();
                for (BluetoothGattService service: lC.mServiceList) {

                    //Log.w(TAG, "Service: " + service.getUuid().toString());

                    List<BluetoothGattCharacteristic> characteristicList = service.getCharacteristics();
                    for (BluetoothGattCharacteristic c: characteristicList) {

                        //Log.w(TAG, gatt.getDevice().getAddress() + " Characteristic: " + c.getUuid().toString() );

                        if (UUID_HEART_RATE_MEASUREMENT.equals(c.getUuid()) ) {
                            Log.w(TAG, "onServicesDiscovered received: HEART_RATE_MEASUREMENT");
                            lC.mToSubscribeList.add(c);
                            //setCharacteristicNotification(lC, c, true);
                        }
                        else if (UUID_RSC_MEASUREMENT.equals(c.getUuid())  ) {
                            Log.w(TAG, "onServicesDiscovered received: RSC_MEASUREMENT");
                            lC.mToSubscribeList.add(c);
                            //setCharacteristicNotification(lC, c, true);
                        }

                        else if (UUID_CYCLING_POWER_MEASUREMENT.equals(c.getUuid() ) ) {
                            Log.w(TAG, "onServicesDiscovered received: CYCLING_POWER_MEASUREMENT");
                            lC.mToSubscribeList.add(c);
                            //setCharacteristicNotification(lC, c, true);
                        }

                        if (gatt.getDevice().getAddress().equals("08:7C:BE:8E:BF:80") ) {
                            if (UUID_FITSHOW_RESPONSE.equals(c.getUuid()) ) {
                                Log.w(TAG, "onServicesDiscovered received: FITSHOW_RESPONSE");
                                lC.mToSubscribeList.add(c);
                                //setCharacteristicNotification(lC, c, true);

                                fitShowInit(gatt);

                            }
                        }
                    }
                }
                if (lC.mToSubscribeList.size() > 0 ) {
                    BluetoothGattCharacteristic c = lC.mToSubscribeList.get(0);
                    lC.mToSubscribeList.remove(0);
                    setCharacteristicNotification(lC, c, true);
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onCharacteristicData(gatt, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            onCharacteristicData(gatt, characteristic);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                            BluetoothGattDescriptor descriptor, int status)
        {
            BluetoothLeConnection lC = null;
            for (BluetoothLeConnection lTry: mBluetoothLeConnectionList) {
                if (lTry.mBluetoothGatt == gatt) {
                    lC = lTry;
                }
            }
            if (null == lC) return;

            Log.d(TAG, String.format("onDescriptorWrite"));

            if (lC.mToSubscribeList.size() > 0 ) {
                BluetoothGattCharacteristic c = lC.mToSubscribeList.get(0);
                lC.mToSubscribeList.remove(0);
                setCharacteristicNotification(lC, c, true);
            }

        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void onCharacteristicData(final BluetoothGatt gatt,
                                 final BluetoothGattCharacteristic characteristic) {

//        Log.d(TAG, String.format("Received DATA"));

        final Intent intent = new Intent(ACTION_DATA_AVAILABLE);

        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            broadcastHeartRateMeasurement(gatt, characteristic);

        } else if (UUID_FITSHOW_RESPONSE.equals(characteristic.getUuid() ) ) {
            processFitShowResponse(gatt, characteristic);

        } else if (UUID_GAP_DEVICE_NAME.equals(characteristic.getUuid())) {
            String s = characteristic.getStringValue(0);
            Log.d(TAG, String.format("Received device name: %s", s));
            intent.putExtra(EXTRA_DATA, "Device name:\n" + s);

        } else if(UUID_GAP_APPEARANCE.equals(characteristic.getUuid())) {
            int appearance = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            Log.d(TAG, String.format("Appearance: %d", appearance));
            intent.putExtra(EXTRA_DATA, String.valueOf(appearance));

        } else if (UUID_RSC_MEASUREMENT.equals(characteristic.getUuid()) ) {
            broadcastRSCMeasurement(gatt, characteristic);

        } else if (UUID_RSC_FEATURE.equals(characteristic.getUuid())) {
            int features = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            String s = "Features supported:\n";
            if (0 != (1 & features)) s += "Stride Length\n";
            if (0 != (2 & features)) s += "Distance\n";
            if (0 != (4 & features)) s += "WalkRun\n";
            if (0 != (8 & features)) s += "Calibration\n";
            if (0 != (16 & features)) s += "Multiple";
            Log.d(TAG, String.format("Received RSC_FEATURE: %s", s));

        } else if (UUID_CYCLING_POWER_MEASUREMENT.equals(characteristic.getUuid()) ) {
            broadcastCyclingPowerMeasurement(gatt, characteristic);

        } else if (UUID_CYCLING_POWER_FEATURE.equals(characteristic.getUuid() )) {
            int features = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
            String s = "Features supported:\n";
            if (0 != ((1) & features)) s += "Pedal Power Balance Supported\n";
            if (0 != ((1<<1) & features)) s += "Accumulated Torque Supported\n";
            if (0 != ((1<<2) & features)) s += "Wheel Revolution Data Supported\n";
            if (0 != ((1<<3) & features)) s += "Crank Revolution Data Supported\n";
            if (0 != ((1<<4) & features)) s += "Extreme Magnitudes Supported\n";
            if (0 != ((1<<5) & features)) s += "Extreme Angles Supported\n";
            if (0 != ((1<<6) & features)) s += "Top and Bottom Dead Spot Angles Supported\n";
            if (0 != ((1<<7) & features)) s += "Accumulated Energy Supported\n";
            if (0 != ((1<<8) & features)) s += "Offset Compensation Indicator Supported\n";
            if (0 != ((1<<9) & features)) s += "Offset Compensation Supported\n";
            if (0 != ((1<<10) & features)) s += "Cycling Power Measurement Characteristic Content Masking Supported\n";
            if (0 != ((1<<11) & features)) s += "Multiple Sensor Locations Supported\n";
            if (0 != ((1<<12) & features)) s += "Crank Length Adjustment Supported\n";
            if (0 != ((1<<13) & features)) s += "Chain Length Adjustment Supported\n";
            if (0 != ((1<<14) & features)) s += "Chain Weight Adjustment Supported\n";
            if (0 != ((1<<15) & features)) s += "Span Length Adjustment Supported\n";
            if (0 != ((1<<16) & features)) s += "Sensor Measurement Context\n";
            if (0 != ((1<<17) & features)) s += "Instantaneous Measurement Direction Supported\n";
            if (0 != ((1<<18) & features)) s += "Factory Calibration Date Supported\n";
            if (0 != ((1<<19) & features)) s += "Enhanced Offset Compensation Supported\n";

            if (0 == ((features>>20)&0x3)) s += "Unspecified (legacy sensor)\n";
            else if (1 == ((features>>20)&0x3)) s += "Not for use in a distributed system\n";
            else if (2 == ((features>>20)&0x3)) s += "Can be used in a distributed system\n";
            else if (3 == ((features>>20)&0x3)) s += "RFU\n";

            Log.d(TAG, String.format("Received CYCLING_POWER_FEATURE: %s", s));

        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }

    private void fitShowInit(final BluetoothGatt gatt) {
        BluetoothLeConnection lMatch = null;
        for (BluetoothLeConnection lTry: mBluetoothLeConnectionList) {
            if (lTry.mBluetoothGatt == gatt) {
                lMatch = lTry;
            }
        }
        if (null == lMatch) return;

        final BluetoothLeConnection lC = lMatch;

        lC.mFitShowState = FIT_STATE_GET_SYS_INFO_MODEL;

        /* We are connected, start update timer */
        mFitShowTimer = new Timer();
        mFitShowTimerTask = new TimerTask() {
            public void run() {
                switch (lC.mFitShowState) {
                    case FIT_STATE_GET_SYS_INFO_MODEL:  fitShowGetSysInfoModel(gatt); break;
                    case FIT_STATE_GET_SYS_STATUS:      fitShowGetSysStatus(gatt); break;
                    case FIT_STATE_CONTROL:             fitShowSysControlRun(gatt, mTargetSpeed, mTargetIncline);
                }
            }
        };

        mFitShowTimer.schedule(mFitShowTimerTask, 200, 200);
    }

    private void fitShowGetSysInfoModel(final BluetoothGatt gatt) {
        byte[] command = {0x50, 0x00};
        fitShowSendCommand(gatt, command);
    }

    private void processFitShowSysInfoModel(final BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] data) {
        BluetoothLeConnection lC = null;
        for (BluetoothLeConnection lTry: mBluetoothLeConnectionList) {
            if (lTry.mBluetoothGatt == gatt) {
                lC = lTry;
            }
        }
        if (null == lC) return;

        lC.mFitShowState = FIT_STATE_GET_SYS_STATUS;
        mTreadmillConnected = true;
    }

    private void fitShowGetSysStatus(BluetoothGatt gatt) {
        byte[] command = {0x51};
        fitShowSendCommand(gatt, command);
    }

    int unsignedByteToInt(byte value) {
        return (int)value & 0xFF;
    }

    float unsignedByteToFloat(byte value) {
        return (float)unsignedByteToInt(value);
    }

    private void processFitShowSysStatus(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] data) {
        if (data.length > 3) {
            switch (data[2] ) {
                case 0x3: { /* STATUS_RUNNING */

                    if (data.length > 8) {
                        float distance = (float)((unsignedByteToInt(data[8])<<8) + (unsignedByteToInt(data[7])))/1000f;
                        if (mPrevDistance != distance) {
                            final Intent intent = newSensorDataIntent(gatt);

                            if (!mFootpodConnected) {
                                intent.putExtra(DISTANCE, distance);

                                float speed = unsignedByteToFloat(data[3]) / 10f;
                                intent.putExtra(SPEED, speed);
                            }

                            float incline = unsignedByteToFloat(data[4]);
                            intent.putExtra(INCLINE, incline);

                            long time = (unsignedByteToInt(data[6]) << 8) + unsignedByteToInt(data[5]);
                            //intent.putExtra(TIME, incline);


                            if (data.length > 10) {
                                long calories = (unsignedByteToInt(data[10]) << 8) + unsignedByteToInt(data[9]);
                                //intent.putExtra(DISTANCE, distance);
                            }

                            if (data.length > 12) {
                                long stepCount = (unsignedByteToInt(data[12]) << 8) + unsignedByteToInt(data[11]);
                                //intent.putExtra(DISTANCE, distance);
                            }

                            if (data.length > 13) {
                                int heartRate = unsignedByteToInt(data[13]);
                                //intent.putExtra(HEART_RATE, heartRate);
                            }

                            sendBroadcast(intent);
                        }
                    }

                    break;
                }
            }
        }
    }

    private void fitShowSysControlRun(BluetoothGatt gatt, float speed, int incline) {
        BluetoothLeConnection lC = null;
        for (BluetoothLeConnection lTry: mBluetoothLeConnectionList) {
            if (lTry.mBluetoothGatt == gatt) {
                lC = lTry;
            }
        }
        if (null == lC) return;

        if (speed < 20 && speed >= 0 && incline >= 0 && incline < 15) {
            byte bSpeed = (byte)(((int)(speed*10)) & 0xFF);
            byte bIncline = (byte)(incline & 0xFF);

            byte[] command = {0x53, 0x02, bSpeed, bIncline};

            fitShowSendCommand(gatt, command);
        }

        lC.mFitShowState = FIT_STATE_GET_SYS_STATUS;
    }


    private void fitShowSendCommand(BluetoothGatt gatt, byte[] command) {
        BluetoothGattCharacteristic characteristic = gatt.getService(UUID_FITSHOW_CUSTOM_SERVICE).getCharacteristic(UUID_FITSHOW_COMMAND);


        byte[] data = new byte[command.length + 3];
        data[0] = 0x02;
        data[data.length-1] = 0x03;
        byte fcs = 0;
        for (int i=0; i<command.length; i++) {
            data[i+1] = command[i];
            fcs = (byte)(0xFF & (fcs ^ command[i]));
        }
        data[data.length-2] = fcs;

//        String s = "FitShow Send Command: ";
//        for (int i=0; i<data.length; i++) {
//            s += String.format(" 0x%02X", data[i] );
//        }
//       Log.w(TAG, s);

        characteristic.setValue(data);
        gatt.writeCharacteristic(characteristic);
    }

    private void processFitShowResponse(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
    {
//        Log.i(TAG, "FitShow Response Received");

        byte[] data = characteristic.getValue();
        if (null != data && data.length > 3) {

//            String s = "FitShow Response Received: ";
//            for (int i=0; i<data.length; i++) {
//                s += String.format(" 0x%02X", data[i] );
//            }
//            Log.w(TAG, s);

            switch (data[1] ) {
                case 0x50: { /* SYS_INFO */
                    processFitShowSysInfoModel(gatt, characteristic, data);
                    break;
                }

                case 0x51: { /* SYS_STATUS */
                    processFitShowSysStatus(gatt, characteristic, data);
                    break;
                }
            }

        }
    }

    private void broadcastCyclingPowerMeasurement(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        final Intent intent = newSensorDataIntent(gatt);

        int flag = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        int power = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 2);

        intent.putExtra(POWER, power);
        sendBroadcast(intent);
    }

    private void broadcastRSCMeasurement(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        final Intent intent = newSensorDataIntent(gatt);

        int flag = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

        int speedMS = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1);
        float speed = (float)speedMS / 256f * 3.6f;
        intent.putExtra(SPEED, speed);

        int cadence = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 3);
        intent.putExtra(CADENCE, cadence);

        int p = 4;
        if (0 != (1 & flag)) {
            int strideI = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, p);
            float stride = (float)strideI/100;
            intent.putExtra(STRIDE, stride);
            p+=2;
        }

        if (0 != (2 & flag)) {
            int distanceI = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, p);
            float distance = (float)distanceI/10f/1000f;
            intent.putExtra(DISTANCE, distance);
        }

        mFootpodConnected = true;
        sendBroadcast(intent);
    }

    private void broadcastHeartRateMeasurement(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        final Intent intent = newSensorDataIntent(gatt);

        int flag = characteristic.getProperties();
        int format = -1;
        if ((flag & 0x01) != 0) {
            format = BluetoothGattCharacteristic.FORMAT_UINT16;
        } else {
            format = BluetoothGattCharacteristic.FORMAT_UINT8;
        }
        final int heartRate = characteristic.getIntValue(format, 1);
        intent.putExtra(HEART_RATE, heartRate);

        sendBroadcast(intent);
    }

    private Intent newSensorDataIntent(BluetoothGatt gatt) {
        final Intent intent = new Intent(ACTION_SENSOR_DATA);

        intent.putExtra(TIMESTAMP, elapsedRealtime() );
        intent.putExtra(SENSOR_NAME, gatt.getDevice().getName() );
        intent.putExtra(SENSOR_ID, gatt.getDevice().getAddress() );

        return intent;
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_ACTUATOR_CONTROL);
//        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
//        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
//        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param addressList The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final List<String> addressList) {
        if (mBluetoothAdapter == null || addressList.size() == 0) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        if (null == mBluetoothLeConnectionList) mBluetoothLeConnectionList = new ArrayList<BluetoothLeConnection>();

        for (String address: addressList) {
            // Previously connected device.  Try to reconnect.
            if (mBluetoothLeConnectionList.contains(address) ) {
                BluetoothLeConnection lC = mBluetoothLeConnectionList.get(mBluetoothLeConnectionList.indexOf(address));

                if (lC.mBluetoothGatt != null) {
                    Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
                    if (lC.mBluetoothGatt.connect()) {
                        lC.mConnectionState = STATE_CONNECTING;
                        lC.mFitShowState = FitShowState.NO_FIT_SHOW_DEVICE;
                        return true;
                    } else {
                        return false;
                    }
                }
            }

            final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
            if (device == null) {
                Log.w(TAG, "Device not found.  Unable to connect.");
                return false;
            }
            // We want to directly connect to the device, so we are setting the autoConnect
            // parameter to false.
            BluetoothLeConnection lC = new BluetoothLeConnection();
            mBluetoothLeConnectionList.add(lC);
            lC.mBluetoothDeviceAddress = address;
            lC.mConnectionState = STATE_CONNECTING;
            lC.mFitShowState = FitShowState.NO_FIT_SHOW_DEVICE;
            lC.mBluetoothGatt = device.connectGatt(this, true, mGattCallback);
            Log.d(TAG, "Trying to create a new connection.");
        }
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothLeConnectionList.size() == 0) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        for (BluetoothLeConnection lC: mBluetoothLeConnectionList) {
            lC.mBluetoothGatt.disconnect();
        }
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        unregisterReceiver(mGattUpdateReceiver );

        if (null != mFitShowTimer) {
            mFitShowTimer.cancel();
            mFitShowTimer = null;
        }

        if (mBluetoothLeConnectionList.size() == 0) {
            return;
        }
        for (BluetoothLeConnection lC: mBluetoothLeConnectionList) {
            lC.mBluetoothGatt.close();
            lC.mBluetoothGatt = null;
        }
        mBluetoothLeConnectionList.clear();
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothLeConnection lC, BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || lC.mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        lC.mBluetoothGatt.readCharacteristic(characteristic);
    }

    public void writeCharacteristic(BluetoothLeConnection lC, BluetoothGattCharacteristic characteristic, byte[] value) {
        if (mBluetoothAdapter == null || lC.mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        characteristic.setValue(value);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothLeConnection lC, BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || lC.mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        lC.mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        //if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
        //if (!UUID_RSC_MEASUREMENT.equals(characteristic.getUuid())  ) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(BluetoothLeGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            lC.mBluetoothGatt.writeDescriptor(descriptor);
        //}
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices(BluetoothLeConnection lC) {
        if (lC.mBluetoothGatt == null) return null;

        return lC.mBluetoothGatt.getServices();
    }
}
