package net.dezoeten.ownruns;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.icu.text.SimpleDateFormat;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import static android.os.SystemClock.elapsedRealtime;
import static android.view.SoundEffectConstants.CLICK;

public class ForegroundService extends Service {
    private final static String TAG = ForegroundService.class.getSimpleName();

    //we are going to use a handler to be able to run in our TimerTask
    final Handler mHandler = new Handler();

    public static String STARTFOREGROUND_ACTION = "com.truiton.foregroundservice.action.startforeground";
    public static String STOPFOREGROUND_ACTION = "com.truiton.foregroundservice.action.stopforeground";

    public static String PLAY_ACTION = "com.truiton.foregroundservice.action.play";
    public static String MAIN_ACTION = "com.truiton.foregroundservice.action.main";

    public final static String ACTION_WORKOUT_DATA =
            "com.example.bluetooth.le.ACTION_WORKOUT_DATA";
    public final static String WORKOUT_ELAPSED_TIME =
            "com.example.bluetooth.le.WORKOUT_ELAPSED_TIME";
    public final static String WORKOUT_INTERVAL =
            "com.example.bluetooth.le.WORKOUT_INTERVAL";
    public final static String WORKOUT_INTERVAL_COUNT =
            "com.example.bluetooth.le.WORKOUT_INTERVAL_COUNT";
    public final static String WORKOUT_INTERVAL_TRIGGER =
            "com.example.bluetooth.le.WORKOUT_INTERVAL_TRIGGER";
    public final static String WORKOUT_VALUE_TO_TRIGGER =
            "com.example.bluetooth.le.WORKOUT_VALUE_TO_TRIGGER";




    public static int FOREGROUND_SERVICE = 101;

    private static final String LOG_TAG = "ForegroundService";

    private BluetoothLeService      mBluetoothLeService;
    private List<String>            mBluetoothLeDeviceAddressList;

    class Record {
        Record() {
            mTimeStamp  = 0;
            mHeartRate   = 0;
            mSpeed       = 0;
            mDistance    = 0;
            mCadence     = 0;
            mStride      = 0;
            mIncline     = 0;
            mPower       = 0;
        }

        Record(Record other) {
            mTimeStamp   = other.mTimeStamp;
            mHeartRate   = other.mHeartRate;
            mSpeed       = other.mSpeed;
            mDistance    = other.mDistance;
            mCadence     = other.mCadence;
            mStride      = other.mStride;
            mIncline     = other.mIncline;
            mPower       = other.mPower;
        }

        long mTimeStamp;
        int mHeartRate;
        float mSpeed;
        float mDistance;
        int mCadence;
        float mStride;
        float mIncline;
        int mPower;
    };

    private enum IntervalTrigger {
        TIME,
        DISTANCE
    };

    private class Interval
    {
        Interval(IntervalTrigger trigger, long triggerValue, float targetSpeed, int targetIncline) {
            mTrigger = trigger;
            mTriggerValue = triggerValue;
            mTargetSpeed = targetSpeed;
            mTargetIncline = targetIncline;
        }

        IntervalTrigger     mTrigger;
        long                mTriggerValue;
        float               mTargetSpeed;
        int                 mTargetIncline;
    };

    List<Interval>  mWorkoutPlan;

    boolean         mRunning;
    Record          mCurrentRecord = new Record();
    Record          mLapStartRecord;
    int             mCurrentLap;

    List<Record>    mRunLog;


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
            }
            else {
                // Automatically connects to the device upon successful start-up initialization.
                mBluetoothLeService.connect(mBluetoothLeDeviceAddressList);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // ACTION_SENSOR_DATA: new data from sensor.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_SENSOR_DATA.equals(action)) {
                Bundle bundle = intent.getExtras();
                for (String key : bundle.keySet()) {
                    if (key.equals(BluetoothLeService.HEART_RATE) ) mCurrentRecord.mHeartRate = bundle.getInt(key);
                    if (key.equals(BluetoothLeService.SPEED) ) mCurrentRecord.mSpeed = bundle.getFloat(key);
                    if (key.equals(BluetoothLeService.DISTANCE) ) {
                        mCurrentRecord.mDistance = bundle.getFloat(key);
                        mCurrentRecord.mTimeStamp = bundle.getLong(BluetoothLeService.TIMESTAMP);
                    }
                    if (key.equals(BluetoothLeService.CADENCE) ) mCurrentRecord.mCadence = bundle.getInt(key);
                    if (key.equals(BluetoothLeService.STRIDE) ) mCurrentRecord.mStride = bundle.getFloat(key);
                    if (key.equals(BluetoothLeService.INCLINE) ) mCurrentRecord.mIncline = bundle.getFloat(key);
                    if (key.equals(BluetoothLeService.POWER) ) mCurrentRecord.mPower = bundle.getInt(key);
                }

//                Log.d(TAG, String.format("Sensor data from %s (%s)"
//                        , bundle.getString(BluetoothLeService.SENSOR_NAME)
//                        , bundle.getString(BluetoothLeService.SENSOR_ID)
//                        )
//                );
            }
            else if (MainActivity.ACTION_RUN_STARTED.equals(action) ) {
                if (!mRunning) {
                    startRun();
                    mRunning = true;
                }
            }
            else if (MainActivity.ACTION_RUN_STOPPED.equals(action) ) {
                if (mRunning) {
                    stopRun();
                    mRunning = false;
                }
            }
        }
    };
    private long mStartTime;
    private Date mStartDate;
    private Timer mTimer;
    private TimerTask mTimerTask;
    private long mTotalTime;
    private File mFile;
    private FileOutputStream mOutputStream;
    private float mAvgSpeed;

    private void startRun() {

        mWorkoutPlan = new ArrayList<Interval>();



        float slow = 10.7f;
        float fast = 12.0f;

        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 30*60*1000, slow, 0));
 //       mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 1*60*1000, fast, 0));
 //       mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 10*60*1000, slow, 0));


/*
        float slow = 10f;
        float fast = 14.17f;
        float slow = 5.5f;
        float fast = 7.8f;


        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 3*60*1000, fast, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 3*60*1000, slow, 0));

        for(int i=0; i<13; i++) {
            mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 1*60*1000, fast, 0));
            mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 30*1000, slow, 0));
        }
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 1*60*1000, fast, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 4*60*1000, slow, 0));


        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 10*60*1000, slow, 0));
        for(int i=0; i<5; i++) {
            mWorkoutPlan.add(new Interval(IntervalTrigger.DISTANCE, 400, fast, 0));
            mWorkoutPlan.add(new Interval(IntervalTrigger.DISTANCE, 400, slow, 0));
        }
        mWorkoutPlan.add(new Interval(IntervalTrigger.DISTANCE, 400, fast, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 10*60*1000, slow, 0));
*/

/*
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 1*60*1000, fast, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 30*1000, slow, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 1*60*1000, fast, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 30*1000, slow, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 1*60*1000, fast, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 30*1000, slow, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 1*60*1000, fast, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 30*1000, slow, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 1*60*1000, fast, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 30*1000, slow, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 1*60*1000, fast, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 30*1000, slow, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 1*60*1000, fast, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 30*1000, slow, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 1*60*1000, fast, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 30*1000, slow, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 1*60*1000, fast, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 30*1000, slow, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 1*60*1000, fast, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 30*1000, slow, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 1*60*1000, fast, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 30*1000, slow, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 1*60*1000, fast, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 30*1000, slow, 0));
        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 1*60*1000, fast, 0));

        mWorkoutPlan.add(new Interval(IntervalTrigger.TIME, 4*60*1000, slow, 0));
*/


        mStartTime = elapsedRealtime();
        mStartDate = new Date();
        mCurrentRecord = new Record();
        mCurrentRecord.mTimeStamp = mStartTime;
        mLapStartRecord = new Record(mCurrentRecord);
        mCurrentLap = 0;

        mRunLog = new ArrayList<Record>();
        logRun();
        setIntervalTargets();

        mTimer = new Timer();
        mTimerTask = new TimerTask() {
            public void run() {
                /* use a handler to run a toast that shows the current timestamp */
                boolean post;
                if (mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        //get the current timeStamp
                        long t = elapsedRealtime();
                        mTotalTime = t - mStartTime;

                        logRun();
                    }
                })) post = true;
                else post = false;
            }
        };

        mTimer.schedule(mTimerTask, 1000, 1000);
    }

    private void logRun() {
        Record r = new Record(mCurrentRecord);

        if (mRunLog.size() > 0) {
            Record l = mRunLog.get(mRunLog.size() - 1);
            if (r.mTimeStamp > l.mTimeStamp) {
                mRunLog.add(new Record(mCurrentRecord));
            }
        } else {
            mRunLog.add(new Record(mCurrentRecord));
        }

        checkNextInterval();
    }

    private void checkNextInterval() {
        if (0 == mWorkoutPlan.size() ) return;
        Interval i = mWorkoutPlan.get( mCurrentLap % mWorkoutPlan.size()  );
        long currentTime = elapsedRealtime();
        long elapsedTime = currentTime - mStartTime;

        if (elapsedTime > 0) {
            mAvgSpeed =  (1000f * 60f * 60f) * (mCurrentRecord.mDistance - mRunLog.get(0).mDistance)/(float)elapsedTime;
        }
        else {
            mAvgSpeed = 0f;
        }

        Intent intent = new Intent(ACTION_WORKOUT_DATA);
        intent.putExtra(WORKOUT_ELAPSED_TIME, elapsedTime);
        intent.putExtra(WORKOUT_INTERVAL, mCurrentLap % mWorkoutPlan.size());
        intent.putExtra(WORKOUT_INTERVAL_COUNT, mWorkoutPlan.size() );

        long currentValue = 0;
        switch (i.mTrigger) {
            case TIME:
                currentValue = currentTime - mLapStartRecord.mTimeStamp;
                intent.putExtra(WORKOUT_INTERVAL_TRIGGER, "Time");
                break;

            case DISTANCE:
                currentValue = (long)(1000f*mCurrentRecord.mDistance - 1000f*mLapStartRecord.mDistance);
                intent.putExtra(WORKOUT_INTERVAL_TRIGGER, "Distance");
                break;

            default: return;
        }
        intent.putExtra(WORKOUT_VALUE_TO_TRIGGER, i.mTriggerValue-currentValue);
        sendBroadcast(intent);

        if ( currentValue >= i.mTriggerValue) {
            mLapStartRecord = new Record(mCurrentRecord);
            mLapStartRecord.mTimeStamp = elapsedRealtime();
            mCurrentLap++;
            setIntervalTargets();
        }
    }

    private void setIntervalTargets() {
        Interval i = mWorkoutPlan.get( mCurrentLap % mWorkoutPlan.size()  );
        Intent intent = new Intent(BluetoothLeService.ACTION_ACTUATOR_CONTROL);
        intent.putExtra(BluetoothLeService.SENSOR_ID, "08:7C:BE:8E:BF:80");
        intent.putExtra(BluetoothLeService.SPEED, i.mTargetSpeed);
        intent.putExtra(BluetoothLeService.INCLINE, i.mTargetIncline);
        sendBroadcast(intent);
    }

    private void stopRun() {
        mTimer.cancel();
        long endTime = elapsedRealtime();


        Log.d(TAG, getExternalFilesDir(null)+"/"+String.valueOf(mStartTime)+".tcx");

        try {
            SimpleDateFormat sdffile = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss");
            String name = sdffile.format(mStartDate);


            File f = new File(getExternalFilesDir(null), name+".tcx");
            FileOutputStream fou = new FileOutputStream(f, true);
            Log.d(TAG, getExternalFilesDir(null)+"/"+name+".tcx");
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fou);

            outputStreamWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                    "<TrainingCenterDatabase \n" +
                    "    xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\" \n" +
                    "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2 http://www.garmin.com/xmlschemas/TrainingCenterDatabasev2.xsd\">\n" +
                    "    <Activities>\n" +
                    "        <Activity Sport=\"Running\">\n"
            );

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

            outputStreamWriter.write(String.format("\t\t<Id>%s</Id>\n", sdf.format(mStartDate) ) );
            outputStreamWriter.write(String.format("\t\t\t<Lap StartTime=\"%s\">\n", sdf.format(mStartDate) ) );

            Record last;
            Record first;
            if (mRunLog.size() > 0) {
                first = mRunLog.get(0);
                last = mRunLog.get(mRunLog.size() - 1);
            }
            else {
                first = mCurrentRecord;
                last = mCurrentRecord;
            }

            outputStreamWriter.write(String.format("\t\t\t\t<TotalTimeSeconds>%s</TotalTimeSeconds>\n", (last.mTimeStamp-mStartTime)/1000 ) );
            outputStreamWriter.write(String.format("\t\t\t\t<DistanceMeters>%s</DistanceMeters>\n", String.valueOf((int)(1000*(last.mDistance-first.mDistance)) ) ) );
            outputStreamWriter.write(String.format("\t\t\t\t<Intensity>Active</Intensity>\n") );
            outputStreamWriter.write(String.format("\t\t\t\t<TriggerMethod>Manual</TriggerMethod>\n") );
            outputStreamWriter.write(String.format("\t\t\t\t<Track>\n") );

            float prevDistance = -1f;
            double altitude = 0.0;
            for (Record r: mRunLog) {

                Date d = new Date(mStartDate.getTime() + (r.mTimeStamp - mStartTime));

                if (-1f != prevDistance) {
                    altitude += ((double)r.mIncline / 100d) * 1000d * (r.mDistance-prevDistance);
                }

                if (r.mDistance != prevDistance && r.mTimeStamp != mStartTime ) {
                    prevDistance = r.mDistance;
                    outputStreamWriter.write(String.format("\t\t\t\t<Trackpoint>\n"));
                    outputStreamWriter.write(String.format("\t\t\t\t\t<Time>%s</Time>\n", sdf.format(d)));
                    outputStreamWriter.write(String.format("\t\t\t\t\t<LatitudeDegrees>51.925788</LatitudeDegrees>\n") );
                    outputStreamWriter.write(String.format("\t\t\t\t\t<LongitudeDegrees>5.857312</LongitudeDegrees>\n") );
                    outputStreamWriter.write(String.format(Locale.US, "\t\t\t\t\t<AltitudeMeters>%.02f</AltitudeMeters>\n", altitude) );
                    outputStreamWriter.write(String.format("\t\t\t\t\t<DistanceMeters>%s</DistanceMeters>\n", String.valueOf((int) (1000 * (r.mDistance - first.mDistance)))));
                    outputStreamWriter.write(String.format("\t\t\t\t\t<HeartRateBpm>\n"));
                    outputStreamWriter.write(String.format("\t\t\t\t\t\t<Value>%d</Value>\n", r.mHeartRate));
                    outputStreamWriter.write(String.format("\t\t\t\t\t</HeartRateBpm>\n"));
                    outputStreamWriter.write(String.format("\t\t\t\t\t<Cadence>%s</Cadence>\n", String.valueOf(r.mCadence)));
                    outputStreamWriter.write(String.format("\t\t\t\t\t<SensorState>Present</SensorState>\n"));
                    outputStreamWriter.write(String.format("\t\t\t\t\t<Extensions>\n"));
                    outputStreamWriter.write(String.format("\t\t\t\t\t\t<TPX xmlns=\"http://www.garmin.com/xmlschemas/ActivityExtension/v2\" CadenceSensor=\"FootPod\">\n"));
                    outputStreamWriter.write(String.format(Locale.US, "\t\t\t\t\t\t\t<Speed>%.02f</Speed>\n", r.mSpeed*1000f/3600f));
                    outputStreamWriter.write(String.format("\t\t\t\t\t\t<Watts>%s</Watts>\n", String.valueOf(r.mPower)));
                    outputStreamWriter.write(String.format("\t\t\t\t\t\t</TPX>\n"));
                    outputStreamWriter.write(String.format("\t\t\t\t\t</Extensions>\n"));
                    outputStreamWriter.write(String.format("\t\t\t\t</Trackpoint>\n"));
                }
            }

            outputStreamWriter.write(
                    "                </Track>\n" +
                            "            </Lap>\n" +
                            "        </Activity>\n" +
                            "    </Activities>\n" +
                            "</TrainingCenterDatabase>"
            );
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }

        File f = new File(getExternalFilesDir(null), String.valueOf(mStartTime)+".tcx");
        Uri uri = Uri.fromFile(f);
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
        sendBroadcast(intent);

        mRunLog.clear();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mRunLog = new ArrayList<Record>();

        mBluetoothLeDeviceAddressList = new ArrayList<String>();
        mBluetoothLeDeviceAddressList.add("00:22:D0:C3:04:BD");     // Polar H7
        //mBluetoothLeDeviceAddressList.add("50:65:83:A1:2E:4D");     // MilestonePOD
        mBluetoothLeDeviceAddressList.add("08:7C:BE:8E:BF:80");     // FitShow
        mBluetoothLeDeviceAddressList.add("C9:F3:94:15:7A:EA");    // Stryd

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        registerReceiver(mGattUpdateReceiver, makeIntentFilter());

    }

    private IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_SENSOR_DATA);
        intentFilter.addAction(MainActivity.ACTION_RUN_STARTED);
        intentFilter.addAction(MainActivity.ACTION_RUN_STOPPED);
        //        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        //        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        //        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(STARTFOREGROUND_ACTION)) {
            Log.i(LOG_TAG, "Received Start Foreground Intent ");
            Intent notificationIntent = new Intent(this, MainActivity.class);
            notificationIntent.setAction(MAIN_ACTION);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    notificationIntent, 0);

            Intent playIntent = new Intent(this, ForegroundService.class);
            playIntent.setAction(PLAY_ACTION);
            PendingIntent pplayIntent = PendingIntent.getService(this, 0,
                    playIntent, 0);

            //Bitmap icon = BitmapFactory.decodeResource(getResources(),
            //        R.drawable.ic_launcher_foreground);

            Notification notification = new NotificationCompat.Builder(this)
                    .setContentTitle("OwnRuns")
                    .setTicker("OwnRuns")
                    .setContentText("Running")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    //.setLargeIcon(
                    //        Bitmap.createScaledBitmap(icon, 128, 128, false))
                    .setContentIntent(pendingIntent)
                    .addAction(android.R.drawable.ic_media_play, "Run", pplayIntent)
                    .setOngoing(true).build();
            startForeground(FOREGROUND_SERVICE,
                    notification);
        } else if (intent.getAction().equals(
                STOPFOREGROUND_ACTION)) {
            Log.i(LOG_TAG, "Received Stop Foreground Intent");
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(LOG_TAG, "In onDestroy");

        unregisterReceiver(mGattUpdateReceiver);

        if (mRunning) {
            stopRun();
        }

        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Used only in case of bound services.
        return null;
    }
};
