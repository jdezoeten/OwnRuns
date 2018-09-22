package net.dezoeten.ownruns;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static android.os.SystemClock.elapsedRealtime;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();

    public final static String ACTION_RUN_STARTED =
            "com.example.bluetooth.le.ACTION_RUN_STARTED";

    public final static String ACTION_RUN_STOPPED =
            "com.example.bluetooth.le.ACTION_RUN_STOPPED";

    //we are going to use a handler to be able to run in our TimerTask
    final Handler mHandler = new Handler();

    private int mBackButtonCount;

    private List<FrameLayout>       mFrameLayoutList;
    private List<IndicatorBase>     mIndicatorList;

    private boolean    mRunning;
    private long       mElapsedTime = 0;
    private float      mDistance = 0;
    private float      mStride = 0;
    private float      mSpeed = 0;
    private int        mHeartRate = 0;
    private int        mCadence = 0;
    private float      mIncline = 0;
    private int        mPower = 0;

    private int        mInterval = 0;
    private int        mIntervalCount = 0;

    private String     mIntervalType = "Time";
    private long       mIntervalRemaining = 0;

    // Calculated
    private float       mAvgSpeed = 0;


    // ACTION_SENSOR_DATA: new data from sensor.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_SENSOR_DATA.equals(action)) {
                Bundle bundle = intent.getExtras();
                for (String key : bundle.keySet()) {
                    if (key.equals(BluetoothLeService.HEART_RATE) ) mHeartRate = bundle.getInt(key);
                    else if (key.equals(BluetoothLeService.SPEED) ) mSpeed = bundle.getFloat(key);
                    else if (key.equals(BluetoothLeService.DISTANCE) ) mDistance = bundle.getFloat(key);
                    else if (key.equals(BluetoothLeService.CADENCE) ) mCadence = bundle.getInt(key);
                    else if (key.equals(BluetoothLeService.STRIDE) ) mStride = bundle.getFloat(key);
                    else if (key.equals(BluetoothLeService.INCLINE) ) mIncline = bundle.getFloat(key);
                    else if (key.equals(BluetoothLeService.POWER) ) mPower = bundle.getInt(key);
                }

                valuesChanged();
            }
            else if (ForegroundService.ACTION_WORKOUT_DATA.equals(action)) {
                Bundle bundle = intent.getExtras();
                for (String key : bundle.keySet()) {
                    if (key.equals(ForegroundService.WORKOUT_ELAPSED_TIME) ) mElapsedTime = bundle.getLong(key);
                    else if(key.equals(ForegroundService.WORKOUT_INTERVAL) ) mInterval = bundle.getInt(key);
                    else if(key.equals(ForegroundService.WORKOUT_INTERVAL_COUNT) ) mIntervalCount = bundle.getInt(key);
                    else if(key.equals(ForegroundService.WORKOUT_INTERVAL_TRIGGER) ) mIntervalType = bundle.getString(key);
                    else if(key.equals(ForegroundService.WORKOUT_VALUE_TO_TRIGGER) ) mIntervalRemaining = bundle.getLong(key);

                }

                valuesChanged();
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "OnCreate()");

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        Intent startIntent = new Intent(MainActivity.this, ForegroundService.class);
        startIntent.setAction(ForegroundService.STARTFOREGROUND_ACTION);
        startService(startIntent);

        mBackButtonCount = 0;
        mFrameLayoutList = new ArrayList<FrameLayout>();
        mIndicatorList = new ArrayList<IndicatorBase>();
        setLayout2x4();
        setIndicators();

    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "OnResume()");

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();

        Log.d(TAG, "onPause()");

        unregisterReceiver(mGattUpdateReceiver );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy()");

        if (mRunning) {
            stopRunning();
        }

    }

    /**
     * Back button listener.
     * Will close the application if the back button pressed twice.
     */
    @Override
    public void onBackPressed()
    {
        Log.d(TAG, "onBackPressed()");

        if(mBackButtonCount >= 1)
        {
            Intent stopIntent = new Intent(MainActivity.this, ForegroundService.class);
            stopIntent.setAction(ForegroundService.STOPFOREGROUND_ACTION);
            startService(stopIntent);

            finish();
            super.onBackPressed();
        }
        else
        {
            mBackButtonCount++;
            Toast.makeText(this, "Press the back button once again to close the application.", Toast.LENGTH_SHORT).show();
            new Handler().postDelayed(new Runnable() {

                @Override
                public void run() {
                    mBackButtonCount = 0;
                }
            }, 2000);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mRunning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_start).setVisible(true);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_start).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_start:
                startRunning();
                mRunning = true;
                break;
            case R.id.menu_stop:
                stopRunning();
                mRunning = false;
                break;
        }
        invalidateOptionsMenu();
        return true;
    }

    private void startRunning() {
        final Intent intent = new Intent(ACTION_RUN_STARTED);
        sendBroadcast(intent);

    }

    private void stopRunning() {
        final Intent intent = new Intent(ACTION_RUN_STOPPED);
        sendBroadcast(intent);
    }

    private static String formatInterval(final long millis) {
        final long hr = TimeUnit.MILLISECONDS.toHours(millis);
        final long min = TimeUnit.MILLISECONDS.toMinutes(millis - TimeUnit.HOURS.toMillis(hr));
        final long sec = TimeUnit.MILLISECONDS.toSeconds(millis - TimeUnit.HOURS.toMillis(hr) - TimeUnit.MINUTES.toMillis(min));
//        final long ms = TimeUnit.MILLISECONDS.toMillis(millis - TimeUnit.HOURS.toMillis(hr) - TimeUnit.MINUTES.toMillis(min) - TimeUnit.SECONDS.toMillis(sec));
//        return String.format("%02d:%02d:%02d.%03d", hr, min, sec, ms);
        return String.format("%02d:%02d:%02d", hr, min, sec);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
    }

    class IndicatorBase {
        void setValue(String value){
        }
    };

    class IndicatorNameValue extends IndicatorBase {
        private final ImageView mImage;
        private View    mView;
        private TextView mName;
        private TextView mValue;

        IndicatorNameValue(MainActivity activity, FrameLayout frame, String name, String initialValue) {
            LayoutInflater l = activity.getLayoutInflater();
            mView = l.inflate(R.layout.indicator_name_value, null);
            mName = (TextView)mView.findViewById(R.id.indicator_name);
            mValue = (TextView)mView.findViewById(R.id.indicator_value);
            mImage = (ImageView)mView.findViewById(R.id.indicator_image);
            mView.setBackgroundColor(0xffffffff);
            //mView.setBackgroundColor(0xffccecff);
            mView.setTag(this);

            if (name.equals("Heart Rate") ) mImage.setImageResource(R.mipmap.ic_elise_heart);
            else if (name.equals("Cadence") ) mImage.setImageResource(R.mipmap.ic_elise_feet);
            else if (name.equals("Speed") ) mImage.setImageResource(R.mipmap.ic_elise_runner);
            else if (name.equals("Avg Speed") ) mImage.setImageResource(R.mipmap.ic_elise_runner);
            else if (name.equals("Distance") ) mImage.setImageResource(R.mipmap.ic_elise_road);
            else if (name.equals("Stride") ) mImage.setImageResource(R.mipmap.ic_elise_feet);
            else if (name.equals("Power") ) mImage.setImageResource(R.mipmap.ic_elise_feet);


            mName.setText(name);
            mValue.setText(initialValue);

            frame.addView(mView);
        }

        @Override
        void setValue(String value){
            mValue.setText(value);
        }

    };

    private void setLayout2x4() {
        setContentView(R.layout.activity_main_2x5);

        mFrameLayoutList.add( (FrameLayout)findViewById(R.id.frame_1) );
        mFrameLayoutList.add( (FrameLayout)findViewById(R.id.frame_2) );
        mFrameLayoutList.add( (FrameLayout)findViewById(R.id.frame_3) );
        mFrameLayoutList.add( (FrameLayout)findViewById(R.id.frame_4) );
        mFrameLayoutList.add( (FrameLayout)findViewById(R.id.frame_5) );
        mFrameLayoutList.add( (FrameLayout)findViewById(R.id.frame_6) );
        mFrameLayoutList.add( (FrameLayout)findViewById(R.id.frame_7) );
        mFrameLayoutList.add( (FrameLayout)findViewById(R.id.frame_8) );
        mFrameLayoutList.add( (FrameLayout)findViewById(R.id.frame_9) );
        mFrameLayoutList.add( (FrameLayout)findViewById(R.id.frame_10) );
        mFrameLayoutList.add( (FrameLayout)findViewById(R.id.frame_11) );
        mFrameLayoutList.add( (FrameLayout)findViewById(R.id.frame_12) );
    }

    private void setIndicators() {
        mIndicatorList.add(new IndicatorNameValue(this, mFrameLayoutList.get(0), "Heart Rate", "0"));
        mIndicatorList.add(new IndicatorNameValue(this, mFrameLayoutList.get(1), "Cadence", "0"));
        mIndicatorList.add(new IndicatorNameValue(this, mFrameLayoutList.get(2), "Speed", "0"));
        mIndicatorList.add(new IndicatorNameValue(this, mFrameLayoutList.get(3), "Power", "0"));
        mIndicatorList.add(new IndicatorNameValue(this, mFrameLayoutList.get(4), "Avg Speed", "0"));
        mIndicatorList.add(new IndicatorNameValue(this, mFrameLayoutList.get(5), "Distance", "0"));
        mIndicatorList.add(new IndicatorNameValue(this, mFrameLayoutList.get(6), "Stride", "0"));
        mIndicatorList.add(new IndicatorNameValue(this, mFrameLayoutList.get(7), "Incline", "0%"));
        mIndicatorList.add(new IndicatorNameValue(this, mFrameLayoutList.get(8), "Time", "0:00"));
        mIndicatorList.add(new IndicatorNameValue(this, mFrameLayoutList.get(9), "Remaining", "0"));
        mIndicatorList.add(new IndicatorNameValue(this, mFrameLayoutList.get(10), "Interval", "0"));
    }

    private void valuesChanged() {
        mIndicatorList.get(0).setValue( String.format("%d", mHeartRate) );
        mIndicatorList.get(1).setValue( String.format("%d", mCadence) );
        mIndicatorList.get(2).setValue( String.format("%.06f", mSpeed) );
        mIndicatorList.get(3).setValue( String.format("%d", mPower) );
        mIndicatorList.get(4).setValue( String.format("%.01f", mAvgSpeed) );
        mIndicatorList.get(5).setValue( String.format("%.01f", mDistance) );
        mIndicatorList.get(6).setValue( String.format("%.1f", mStride) );
        mIndicatorList.get(7).setValue( String.format("%.1f", mIncline) );
        mIndicatorList.get(8).setValue( formatInterval(mElapsedTime) );
        if (mIntervalType.equals("Distance") ) {
            mIndicatorList.get(9).setValue( String.format("%dm", mIntervalRemaining) );

        }
        else if (mIntervalType.equals("Time") ) {
            mIndicatorList.get(9).setValue( formatInterval(mIntervalRemaining) );
        }
        mIndicatorList.get(10).setValue( String.format("%d/%d", mInterval+1, mIntervalCount) );
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_SENSOR_DATA);
        intentFilter.addAction(ForegroundService.ACTION_WORKOUT_DATA);
//        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
//        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
//        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

}



