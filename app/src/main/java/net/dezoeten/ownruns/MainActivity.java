package net.dezoeten.ownruns;

import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProviders;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.annotation.Nullable;
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
import java.util.concurrent.TimeUnit;

class CurrentSensorData extends ViewModel {

    public MutableLiveData<Boolean> getRunning() {
        if (mRunning == null) {
            mRunning = new MutableLiveData<Boolean>();
            mRunning.setValue(false);
        }

        return mRunning;
    }

    public MutableLiveData<Long> getElapsedTime() {
        if (mElapsedTime == null) {
            mElapsedTime = new MutableLiveData<Long>();
            mElapsedTime.setValue(Long.valueOf(0));
        }
        return mElapsedTime;
    }

    public MutableLiveData<Float> getDistance() {
        if (mDistance == null) {
            mDistance = new MutableLiveData<Float>();
            mDistance.setValue(Float.valueOf(0) );
        }
        return mDistance;
    }

    public MutableLiveData<Float> getSpeed() {
        if (mSpeed == null) {
            mSpeed = new MutableLiveData<Float>();
            mSpeed.setValue(Float.valueOf(0));
        }
        return mSpeed;
    }

    public MutableLiveData<Float> getStride() {
        if (mStride == null) {
            mStride = new MutableLiveData<Float>();
            mStride.setValue(Float.valueOf(0));
        }
        return mStride;
    }

    public MutableLiveData<Float> getIncline() {
        if (mIncline == null){
            mIncline = new MutableLiveData<Float>();
            mIncline.setValue(Float.valueOf(0));
        }
        return mIncline;
    }

    public MutableLiveData<String> getIntervalType() {
        if (mIntervalType == null) {
            mIntervalType = new MutableLiveData<String>();
            mIntervalType.setValue("Distance");
        }
        return mIntervalType;
    }

    public MutableLiveData<Float> getAvgSpeed() {
        if (mAvgSpeed == null) {
            mAvgSpeed = new MutableLiveData<Float>();
            mAvgSpeed.setValue(Float.valueOf(0));
        }
        return mAvgSpeed;
    }

    public MutableLiveData<Long> getHeartRate() {
        if (null == mHeartRate) {
            mHeartRate = new MutableLiveData<Long>();
            mHeartRate.setValue(Long.valueOf(0));
        }
        return mHeartRate;
    }

    public MutableLiveData<Long> getCadence() {
        if (mCadence == null) {
            mCadence = new MutableLiveData<Long>();
            mCadence.setValue(Long.valueOf(0));
        }
        return mCadence;
    }

    public MutableLiveData<Long> getPower() {
        if (mPower == null) {
            mPower = new MutableLiveData<Long>();
            mPower.setValue(Long.valueOf(0));
        }
        return mPower;
    }

    public MutableLiveData<Long> getInterval() {
        if (mInterval == null) {
            mInterval = new MutableLiveData<Long>();
            mInterval.setValue(Long.valueOf(0));
        }
        return mInterval;
    }

    public MutableLiveData<Long> getIntervalCount() {
        if (mIntervalCount == null) {
            mIntervalCount = new MutableLiveData<Long>();
            mIntervalCount.setValue(Long.valueOf(0) );
        }
        return mIntervalCount;
    }

    public MutableLiveData<Long> getIntervalRemaining() {
        if (mIntervalRemaining == null) {
            mIntervalRemaining = new MutableLiveData<Long>();
            mIntervalRemaining.setValue(Long.valueOf(0) );
        }
        return mIntervalRemaining;
    }

    private MutableLiveData<Boolean>    mRunning;
    private MutableLiveData<Long>       mElapsedTime;
    private MutableLiveData<Float>      mDistance;
    private MutableLiveData<Float>      mStride;
    private MutableLiveData<Float>      mSpeed;
    private MutableLiveData<Long>       mHeartRate;
    private MutableLiveData<Long>       mCadence;
    private MutableLiveData<Float>      mIncline;
    private MutableLiveData<Long>       mPower;
    private MutableLiveData<Long>       mInterval;
    private MutableLiveData<Long>       mIntervalCount;
    private MutableLiveData<String>     mIntervalType;
    private MutableLiveData<Long>       mIntervalRemaining;
    private MutableLiveData<Float>      mAvgSpeed;

}

public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();

    public final static String ACTION_RUN_STARTED =
            "com.example.bluetooth.le.ACTION_RUN_STARTED";

    public final static String ACTION_RUN_STOPPED =
            "com.example.bluetooth.le.ACTION_RUN_STOPPED";

    private List<FrameLayout>       mFrameLayoutList;
    private List<IndicatorBase>     mIndicatorList;
    private CurrentSensorData       mCurrentSensorData;
    private int                     mBackButtonCount;

    // ACTION_SENSOR_DATA: new data from sensor.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_SENSOR_DATA.equals(action)) {
                Bundle bundle = intent.getExtras();
                for (String key : bundle.keySet()) {
                    if (key.equals(BluetoothLeService.HEART_RATE) ) mCurrentSensorData.getHeartRate().setValue(Long.valueOf(bundle.getInt(key) ) );
                    else if (key.equals(BluetoothLeService.SPEED) ) mCurrentSensorData.getSpeed().setValue(bundle.getFloat(key) );
                    else if (key.equals(BluetoothLeService.DISTANCE) ) mCurrentSensorData.getDistance().setValue(bundle.getFloat(key) );
                    else if (key.equals(BluetoothLeService.CADENCE) ) mCurrentSensorData.getCadence().setValue(Long.valueOf(bundle.getInt(key)) );
                    else if (key.equals(BluetoothLeService.STRIDE) ) mCurrentSensorData.getStride().setValue(bundle.getFloat(key) );
                    else if (key.equals(BluetoothLeService.INCLINE) ) mCurrentSensorData.getIncline().setValue(bundle.getFloat(key) );
                    else if (key.equals(BluetoothLeService.POWER) ) mCurrentSensorData.getPower().setValue(Long.valueOf(bundle.getInt(key)) );
                }
            }
            else if (ForegroundService.ACTION_WORKOUT_DATA.equals(action)) {
                Bundle bundle = intent.getExtras();
                for (String key : bundle.keySet()) {
                    if (key.equals(ForegroundService.WORKOUT_ELAPSED_TIME) ) mCurrentSensorData.getElapsedTime().setValue(bundle.getLong(key) );
                    else if(key.equals(ForegroundService.WORKOUT_INTERVAL) ) mCurrentSensorData.getInterval().setValue(Long.valueOf(bundle.getInt(key)) );
                    else if(key.equals(ForegroundService.WORKOUT_INTERVAL_COUNT) ) mCurrentSensorData.getIntervalCount().setValue(Long.valueOf(bundle.getInt(key)) );
                    else if(key.equals(ForegroundService.WORKOUT_INTERVAL_TRIGGER) ) mCurrentSensorData.getIntervalType().setValue(bundle.getString(key) );
                    else if(key.equals(ForegroundService.WORKOUT_VALUE_TO_TRIGGER) ) mCurrentSensorData.getIntervalRemaining().setValue(bundle.getLong(key) );
                }
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

        // Get the ViewModel.
        mCurrentSensorData = ViewModelProviders.of(this).get(CurrentSensorData.class);


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
            if (mCurrentSensorData.getRunning().getValue() ) {
                mCurrentSensorData.getRunning().setValue(false);
                stopRunning();
            }

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
        if (!mCurrentSensorData.getRunning().getValue() ) {
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
                mCurrentSensorData.getRunning().setValue(true);
                break;
            case R.id.menu_stop:
                stopRunning();
                mCurrentSensorData.getRunning().setValue(false);
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

    class IndicatorBase {
        void setValue(String value){
        }
    };

    class IndicatorNameValue extends IndicatorBase {
        protected final ImageView       mImage;
        protected View                  mView;
        protected TextView              mName;
        protected TextView              mValue;

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

    class LongIndicator extends IndicatorNameValue {

        LongIndicator(MainActivity activity, FrameLayout frame, String name, String initialValue, MutableLiveData<Long> value) {
            super(activity, frame, name, initialValue);

            // Create the observer which updates the UI.
            final Observer<Long> valueObserver = new Observer<Long>() {
                @Override
                public void onChanged(@Nullable final Long newValue) {
                    // Update the UI, in this case, a TextView.
                    setValue(String.format("%d", newValue) );
                }
            };

            value.observe(activity, valueObserver);
        }
    }

    class FloatIndicator extends IndicatorNameValue {

        FloatIndicator(MainActivity activity, FrameLayout frame, String name, String initialValue, MutableLiveData<Float> value) {
            super(activity, frame, name, initialValue);

            // Create the observer which updates the UI.
            final Observer<Float> valueObserver = new Observer<Float>() {
                @Override
                public void onChanged(@Nullable final Float newValue) {
                    // Update the UI, in this case, a TextView.
                   setValue(String.format("%.02f", newValue) );
                }
            };

            value.observe(activity, valueObserver);
        }
    }

    class TimeIndicator extends IndicatorNameValue {

        TimeIndicator(MainActivity activity, FrameLayout frame, String name, String initialValue, MutableLiveData<Long> time) {
            super(activity, frame, name, initialValue);

            // Create the observer which updates the UI.
            final Observer<Long> valueObserver = new Observer<Long>() {
                @Override
                public void onChanged(@Nullable final Long newValue) {
                    // Update the UI, in this case, a TextView.
                    setValue(formatInterval(newValue) );
                }
            };

            time.observe(activity, valueObserver);
        }
    }

    class IntervalRemainingIndicator extends IndicatorNameValue {

        MutableLiveData<String> mIntervalType;
        MutableLiveData<Long> mRemaining;

        IntervalRemainingIndicator(MainActivity activity, FrameLayout frame, String name, String initialValue, MutableLiveData<String> intervalType, MutableLiveData<Long> remaining) {
            super(activity, frame, name, initialValue);

            mIntervalType = intervalType;
            mRemaining = remaining;

            final Observer<Long> remainingObserver = new Observer<Long>() {
                @Override
                public void onChanged(@Nullable final Long newValue) {
                    // Update the UI, in this case, a TextView.
                    setValue();
                }
            };

            final Observer<String> typeObserver = new Observer<String>() {
                @Override
                public void onChanged(@Nullable final String newValue) {
                    // Update the UI, in this case, a TextView.
                    setValue();
                }
            };

            mRemaining.observe(activity, remainingObserver);
            mIntervalType.observe(activity, typeObserver);
        }

        void setValue() {
            if (mIntervalType.getValue().equals("Distance")) {
                setValue(String.format("%dm", mRemaining.getValue() ));

            } else if (mIntervalType.getValue().equals("Time")) {
                setValue(formatInterval(mRemaining.getValue()));
            }
        }
    }

    class IntervalCountIndicator extends IndicatorNameValue {

        MutableLiveData<Long> mCurrent;
        MutableLiveData<Long> mTotal;

        IntervalCountIndicator(MainActivity activity, FrameLayout frame, String name, String initialValue, MutableLiveData<Long> current, MutableLiveData<Long> total) {
            super(activity, frame, name, initialValue);

            mCurrent = current;
            mTotal = total;

            final Observer<Long> currentObserver = new Observer<Long>() {
                @Override
                public void onChanged(@Nullable final Long newValue) {
                    // Update the UI, in this case, a TextView.
                    setValue();
                }
            };

            final Observer<Long> totalObserver = new Observer<Long>() {
                @Override
                public void onChanged(@Nullable final Long newValue) {
                    // Update the UI, in this case, a TextView.
                    setValue();
                }
            };

            mCurrent.observe(activity, currentObserver);
            mTotal.observe(activity, totalObserver);
        }

        void setValue() {
            setValue( String.format("%d/%d", mCurrent.getValue().longValue()+1, mTotal.getValue()) );
        }
    }


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
        mIndicatorList.add(new LongIndicator(this, mFrameLayoutList.get(0), "Heart Rate", "0", mCurrentSensorData.getHeartRate()));
        mIndicatorList.add(new LongIndicator(this, mFrameLayoutList.get(1), "Cadence", "0", mCurrentSensorData.getCadence()));
        mIndicatorList.add(new FloatIndicator(this, mFrameLayoutList.get(2), "Speed", "0", mCurrentSensorData.getSpeed() ));
        mIndicatorList.add(new LongIndicator(this, mFrameLayoutList.get(3), "Power", "0", mCurrentSensorData.getPower()));
        mIndicatorList.add(new FloatIndicator(this, mFrameLayoutList.get(4), "Avg Speed", "0", mCurrentSensorData.getAvgSpeed()));
        mIndicatorList.add(new FloatIndicator(this, mFrameLayoutList.get(5), "Distance", "0", mCurrentSensorData.getDistance()));
        mIndicatorList.add(new FloatIndicator(this, mFrameLayoutList.get(6), "Stride", "0", mCurrentSensorData.getStride()));
        mIndicatorList.add(new FloatIndicator(this, mFrameLayoutList.get(7), "Incline", "0%", mCurrentSensorData.getIncline()));
        mIndicatorList.add(new TimeIndicator(this, mFrameLayoutList.get(8), "Time", "0:00", mCurrentSensorData.getElapsedTime()));
        mIndicatorList.add(new IntervalRemainingIndicator(this, mFrameLayoutList.get(9), "Remaining", "0", mCurrentSensorData.getIntervalType(), mCurrentSensorData.getIntervalRemaining()));
        mIndicatorList.add(new IntervalCountIndicator(this, mFrameLayoutList.get(10), "Interval", "0", mCurrentSensorData.getInterval(), mCurrentSensorData.getIntervalCount()));
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



