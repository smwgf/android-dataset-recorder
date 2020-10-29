package io.rpng.recorder.managers;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import io.rpng.recorder.activities.MainActivity;

/**
 * @author chadrockey@gmail.com (Chad Rockey)
 * @author axelfurlan@gmail.com (Axel Furlan)
 */
public class IMUManagerMultiThread
{
    private class ImuThread extends Thread
    {
        private final SensorManager sensorManager;
        private SensorListener sensorListener;
        private Looper threadLooper;

        private final Sensor accelSensor;
        private final Sensor gyroSensor;
//        private final Sensor quatSensor;

        private ImuThread(SensorManager sensorManager, SensorListener sensorListener)
        {
            this.sensorManager = sensorManager;
            this.sensorListener = sensorListener;
            this.accelSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            this.gyroSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
//            this.quatSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }


        public void run()
        {
            Looper.prepare();
            this.threadLooper = Looper.myLooper();
            register();
//            this.sensorManager.registerListener(this.sensorListener, this.quatSensor, SensorManager.SENSOR_DELAY_FASTEST);
            Looper.loop();
        }
        /**
         * This will register all IMU listeners
         */
        public void register() {
            // Get the freq we should get messages at (default is SensorManager.SENSOR_DELAY_GAME)
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
            String imuFreq = sharedPreferences.getString("perfImuFreq", "1");
            // Register the IMUs
            this.sensorManager.registerListener(this.sensorListener, accelSensor, Integer.parseInt(imuFreq));
            this.sensorManager.registerListener(this.sensorListener, gyroSensor, Integer.parseInt(imuFreq));
        }

        /**
         * This will unregister all IMU listeners
         */
        public void unregister() {
            this.sensorManager.unregisterListener(this.sensorListener, accelSensor);
            this.sensorManager.unregisterListener(this.sensorListener, gyroSensor);
            this.sensorManager.unregisterListener(this.sensorListener);
        }

        public void shutdown()
        {
            unregister();
            if(this.threadLooper != null)
            {
                this.threadLooper.quit();
            }
        }
    }

    private class SensorListener implements SensorEventListener
    {
        // Data storage (linear)
        long linear_time;
        int linear_acc;
        float[] linear_data;

        // Data storage (angular)
        long angular_time;
        int angular_acc;
        float[] angular_data;
        File destToWrite;

        private SensorListener()
        {
            this.linear_time = 0;
            this.angular_time = 0;
        }

        public void SetIMURecordPath(String pathToSet){
            String filename = "data_imu.txt";
            String path = Environment.getExternalStorageDirectory().getAbsolutePath()
                    + "/dataset_recorder/" + pathToSet + "/";

            // Create export file
            new File(path).mkdirs();
            destToWrite = new File(path + filename);
            try {
                // If the file does not exist yet, create it
                if (!destToWrite.exists())
                    destToWrite.createNewFile();
            }
            // Ran into a problem writing to file
            catch (IOException ioe) {
                System.err.println("IOException: " + ioe.getMessage());
            }
        }

        //	@Override
        public void onAccuracyChanged(Sensor sensor, int accuracy)
        {
        }

        //	@Override
        public void onSensorChanged(SensorEvent event)
        {
            event.timestamp = MainActivity.InitMillis+System.nanoTime() - MainActivity.InitNanos;

            // Handle accelerometer reading
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                linear_time = event.timestamp;
                linear_data = event.values;
            }
            // Handle a gyro reading
            else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                angular_time = event.timestamp;
                angular_data = event.values;
            }

            // If the timestamps are not zeros, then we know we have two measurements
            if(linear_time != 0 && angular_time != 0) {

                // Write the data to file if we are recording
                if(MainActivity.is_recording) {
                    try{
                        BufferedWriter writer = new BufferedWriter(new FileWriter(destToWrite, true));

                        // Master string of information
                        String data = event.timestamp
                                + "," + angular_data[0] + "," + angular_data[1] + "," + angular_data[2]
                                + "," + linear_data[0] + "," + linear_data[1] + "," + linear_data[2];

                        // Appends the string to the file and closes
                        writer.write(data + "\n");
                        writer.flush();
                        writer.close();
                    }
                    catch (Exception e){
                        System.err.println("Exception: " + e.getMessage());
                    }

                }
                // Reset timestamps
                linear_time = 0;
                angular_time = 0;
            }
        }
    }
    Activity activity;
    private ImuThread imuThread;
    private SensorListener sensorListener;
    private SensorManager sensorManager;

    public IMUManagerMultiThread(Activity activity)
    {
        this.activity = activity;
        this.sensorManager = (SensorManager)activity.getSystemService(Context.SENSOR_SERVICE);
        // Create the sensor objects

    }

    public void StartRecording(String path)
    {
        this.sensorListener = new SensorListener();
        this.sensorListener.SetIMURecordPath(path);
        this.imuThread = new ImuThread(this.sensorManager, sensorListener);
        this.imuThread.start();
    }
    public void StopRecording(){
        this.imuThread.shutdown();
        this.imuThread.interrupt();;
        this.imuThread = null;
        this.sensorListener=null;
    }
}
