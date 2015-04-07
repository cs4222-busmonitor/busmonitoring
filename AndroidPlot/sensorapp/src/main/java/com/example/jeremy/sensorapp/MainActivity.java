package com.example.jeremy.sensorapp;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;


import nus.dtn.middleware.api.DtnMiddlewareInterface;
import nus.dtn.middleware.api.DtnMiddlewareProxy;
import nus.dtn.middleware.api.MiddlewareEvent;
import nus.dtn.middleware.api.MiddlewareListener;
import nus.dtn.util.*;
import nus.dtn.api.fwdlayer.*;
import android.telephony.TelephonyManager;

public class MainActivity extends Activity
        implements SensorEventListener {

    /**
     * Called when the activity is created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create a handler to the main thread


        try {
            handler = new Handler();
            // Set up the GUI
            setUpGUI();

            // Open the log files
            openLogFiles();

            // Start the middleware
            middleware = new DtnMiddlewareProxy ( getApplicationContext() );
            middleware.start ( new MiddlewareListener() {
                public void onMiddlewareEvent ( MiddlewareEvent event ) {
                    try {

                        // Check if the middleware failed to start
                        if ( event.getEventType() != MiddlewareEvent.MIDDLEWARE_STARTED ) {
                            throw new Exception( "Middleware failed to start, is it installed?" );
                        }

                        // Get the fwd layer API
                        fwdLayer1 = new ForwardingLayerProxy ( middleware );
                        // Get a descriptor for this user
                        // Typically, the user enters the username, but here we simply use IMEI number

                        TelephonyManager telephonyManager =
                                (TelephonyManager) getSystemService ( Context.TELEPHONY_SERVICE );

                        descriptor = fwdLayer1.getDescriptor ( "nus.cs4222.jeremy" , telephonyManager.getDeviceId() );

                        // Set the broadcast address
                        //fwdLayer1.setBroadcastAddress ( "nus.cs4222.jeremy" , "everyone" );

                        // Register a listener for received chat messages
                        ChatMessageListener messageListener = new ChatMessageListener();
                        fwdLayer1.addMessageListener ( descriptor , messageListener );
                        createToast ( "Listening..." );

                    }
                    catch ( Exception e ) {
                        // Log the exception
                        Log.e ( "BroadcastApp" , "Exception in middleware start listener" , e );
                        // Inform the user
                        createToast ( "Exception in middleware start listener, check log" );
                    }
                }
            } );


        } catch (Exception e) {
            // Log the exception
            Log.e(TAG, "Unable to create activity", e);
            // Tell the user
            createToast("Unable to create CS4222 Activity: " + e.toString());
        }
    }

    private class ChatMessageListener
            implements MessageListener {

        /**
         * {@inheritDoc}
         */
        public void onMessageReceived(String source,
                                      String destination,
                                      DtnMessage message) {

            try {

                // Read the DTN message
                // Data part
                message.switchToData();
                long timeStamp = message.readLong();
                String quoteMessage = message.readString();

                // Append to the message list

                final String newText =
                        testText.getText() +
                                "\n" + source + " says: " + quoteMessage;

                // Update the text view in Main UI thread
                createToast(newText);
                handler.post(new Runnable() {
                    public void run() {
                        testText.setText(newText);
                    }
                });
            } catch (Exception e) {
                // Log the exception
                Log.e("BroadcastApp", "Exception on message event", e);
                // Tell the user
                createToast("Exception on message event, check log");
            }
        }
    }

    /**
     * Called when the activity is destroyed.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        try {

            // Close the log files
            closeLogFiles();

            // Stop sensor sampling (if user didn't stop)
            stopBarometerSampling();
            stopGyroscopeSampling();
            stopAccelerometerSampling();
            //stopLocationSampling();
        } catch (Exception e) {
            // Log the exception
            Log.e(TAG, "Unable to destroy activity", e);
            // Tell the user
            createToast("Unable to destroy CS4222 Activity: " + e.toString());
        }
    }

    /**
     * Helper method that starts Barometer sampling.
     */
    private void startBarometerSampling() {

        try {

            // Check the flag
            if (isBarometerOn)
                return;

            // Get the sensor manager
            sensorManager =
                    (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            // Get the barometer sensor
            barometerSensor =
                    (Sensor) sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
            if (barometerSensor == null) {
                throw new Exception("Sensor not available");
            }

            // Initialise timestamps and count
            prevBarometerTime = System.currentTimeMillis();
            barometerDelayTime = 0;
            numBarometerReadings = 0;

            // Add a sensor change listener
            sensorManager.registerListener(this,
                    barometerSensor,
                    BAROMETER_SAMPLING_RATE * 1000);

            // Set the flag
            isBarometerOn = true;
        } catch (Exception e) {
            // Log the exception
            Log.e(TAG, "Unable to start barometer", e);
            // Tell the user
            createToast("Unable to start barometer: " + e.toString());
        }
    }

    /**
     * Helper method that stops Barometer sampling.
     */
    private void stopBarometerSampling() {

        try {

            // Check the flag
            if (!isBarometerOn)
                return;

            // Set the flag
            isBarometerOn = false;

            // Remove the sensor change listener
            sensorManager.unregisterListener(this,
                    barometerSensor);
        } catch (Exception e) {
            // Log the exception
            Log.e(TAG, "Unable to stop barometer", e);
            // Tell the user
            createToast("Unable to stop barometer: " + e.toString());
        } finally {
            //sensorManager = null;
            barometerSensor = null;
        }
    }


    private void startGyroscopeSampling() {
        try {
            if (isGyroscopeOn)
                return;
            sensorManager =
                    (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            gyroscopeSensor =
                    (Sensor) sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if (gyroscopeSensor == null) {
                throw new Exception("Sensor not available");
            }
            prevGyroscopeTime = System.currentTimeMillis();
            gyroscopeDelayTime = 0;
            numGyroscopeReadings = 0;

            sensorManager.registerListener(this,
                    gyroscopeSensor,
                    GYROSCOPE_SAMPLING_RATE * 1000);

            isGyroscopeOn = true;
        } catch (Exception e) {
            Log.e(TAG, "Unable to start gyroscope", e);
            createToast("Unable to start gyroscope: " + e.toString());
        }
    }

    private void stopGyroscopeSampling() {
        try {
            if (!isGyroscopeOn)
                return;
            isGyroscopeOn = false;
            sensorManager.unregisterListener(this,
                    gyroscopeSensor);
        } catch (Exception e) {
            Log.e(TAG, "Unable to stop gyroscope", e);
            createToast("Unable to stop gyroscope: " + e.toString());
        } finally {
            // sensorManager = null;
            gyroscopeSensor = null;
        }
    }

    private void startAccelerometerSampling() {
        try {
            if (isAccelerometerOn)
                return;
            sensorManager =
                    (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            accelerometerSensor =
                    (Sensor) sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            if (accelerometerSensor == null) {
                throw new Exception("Sensor not available");
            }
            prevAccelerometerTime = System.currentTimeMillis();
            accelerometerDelayTime = 0;
            numAccelerometerReadings = 0;

            sensorManager.registerListener(this,
                    accelerometerSensor,
                    ACCELEROMETER_SAMPLING_RATE * 1000);

            isAccelerometerOn = true;
        } catch (Exception e) {
            Log.e(TAG, "Unable to start accelerometer", e);
            createToast("Unable to start accelerometer: " + e.toString());
        }
    }

    private void stopAccelerometerSampling() {
        try {
            if (!isAccelerometerOn)
                return;
            isAccelerometerOn = false;
            sensorManager.unregisterListener(this,
                    accelerometerSensor);
        } catch (Exception e) {
            Log.e(TAG, "Unable to stop accelerometer", e);
            createToast("Unable to stop accelerometer: " + e.toString());
        } finally {
            //  sensorManager = null;
            accelerometerSensor = null;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        // SensorEvent's timestamp is the device uptime,
        //  but for logging we use UTC time
        long universalTime = System.currentTimeMillis();
        //createToast("there is event " + event.sensor.getName() + event.sensor.getType() + "p"+Sensor.TYPE_PRESSURE+"a"+Sensor.TYPE_ACCELEROMETER+"g"+Sensor.TYPE_GYROSCOPE);
        // Validity check: This must be the barometer sensor
        if (event.sensor.getType() != Sensor.TYPE_PRESSURE || event.sensor.getType() != Sensor.TYPE_LINEAR_ACCELERATION || event.sensor.getType() != Sensor.TYPE_GYROSCOPE) {

            if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
                // Calculate the delay for first reading
                if (barometerDelayTime == 0) {
                    barometerDelayTime = universalTime - prevBarometerTime;
                }

                // Although we specified 1 Hz, events usually arrive at
                //  a faster rate. Cap frequency to 1 Hz.
                if (universalTime - prevBarometerTime < (long) BAROMETER_SAMPLING_RATE)
                    return;
                // Update the count
                ++numBarometerReadings;

                // Convert reported millibar value to meters above sea level
                float height = convertMillibarToMetres(event.values[0]);

                // Log the reading
                logBarometerReading(universalTime,
                        event.values[0],
                        height);

                // Update the GUI
                updateBarometerTextView(universalTime,
                        event.values[0],
                        height);

                // Update the last timestamp
                prevBarometerTime = universalTime;

            } else if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                if (accelerometerDelayTime == 0) {
                    accelerometerDelayTime = universalTime - prevAccelerometerTime;
                }

                // Although we specified 1 Hz, events usually arrive at
                //  a faster rate. Cap frequency to 1 Hz.
                if (universalTime - prevAccelerometerTime < (long) ACCELEROMETER_SAMPLING_RATE)
                    return;
                // Update the count
                ++numAccelerometerReadings;


                float AccelerationX = event.values[0];
                float AccelerationY = event.values[1];
                float AccelerationZ = event.values[2];

                // Log the reading
                logAccelerometerReading(universalTime,
                        event.values[0], event.values[1], event.values[2]);

                // Update the GUI
                updateAccelerometerTextView(universalTime,
                        event.values[0], event.values[1], event.values[2]);

                // Update the last timestamp
                prevAccelerometerTime = universalTime;


            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                if (gyroscopeDelayTime == 0) {
                    gyroscopeDelayTime = universalTime - prevGyroscopeTime;
                }

                // Although we specified 1 Hz, events usually arrive at
                //  a faster rate. Cap frequency to 1 Hz.
                if (universalTime - prevGyroscopeTime < (long) GYROSCOPE_SAMPLING_RATE)
                    return;
                // Update the count
                ++numGyroscopeReadings;


                float OrientationX = event.values[0];
                float OrientationY = event.values[1];
                float OrientationZ = event.values[2];

                // Log the reading
                logGyroscopeReading(universalTime,
                        event.values[0], event.values[1], event.values[2]);

                // Update the GUI
                updateGyroscopeTextView(universalTime,
                        event.values[0], event.values[1], event.values[2]);

                // Update the last timestamp
                prevGyroscopeTime = universalTime;
            }
        }
    }

    /**
     * Helper method to convert millibar to metres.
     */
    private static float convertMillibarToMetres(float millibar) {
        // Calculate the altitude in metres above sea level
        float height =
                SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE,  // Sea level
                        millibar);                                   // Pressure
        return height;
    }

    /**
     * Called when the barometer accuracy changes.
     */
    @Override
    public void onAccuracyChanged(Sensor sensor,
                                  int accuracy) {
        // Nothing to do here
    }

    /**
     * Helper method that sets up the GUI.
     */
    private void setUpGUI() {

        // Set the GUI content to the XML layout specified
        setContentView(R.layout.activity_main);

        // Get references to GUI widgets
        startBarometerButton =
                (Button) findViewById(R.id.startBarometer);
        stopBarometerButton =
                (Button) findViewById(R.id.stopBarometer);
        startGyroscopeButton =
                (Button) findViewById(R.id.startGyroscope);
        stopGyroscopeButton =
                (Button) findViewById(R.id.stopGyroscope);
        startAccelerometerButton =
                (Button) findViewById(R.id.startAccelerometer);
        stopAccelerometerButton =
                (Button) findViewById(R.id.stopAccelerometer);
        flushLocationButton =
                (Button) findViewById(R.id.PA1Activity_Button_FlushLocation);
        barometerTextView =
                (TextView) findViewById(R.id.TextView_Barometer);
        gyroscopeTextView =
                (TextView) findViewById(R.id.TextView_Gyroscope);
        accelerometerTextView =
                (TextView) findViewById(R.id.TextView_Accelerometer);
        startAll = (Button) findViewById(R.id.startAll);
        stopAll = (Button) findViewById(R.id.stopAll);

        // Disable the stop buttons
        stopBarometerButton.setEnabled(false);
        stopAccelerometerButton.setEnabled(false);
        stopGyroscopeButton.setEnabled(false);
        stopAll.setEnabled(false);

        spinnerBarometer = (Spinner) findViewById(R.id.spinnerBarometer);
        spinnerAccelerometer = (Spinner) findViewById(R.id.spinnerAccelerometer);
        spinnerGyroscope = (Spinner) findViewById(R.id.spinnerGyroscope);

        testText = (TextView) findViewById(R.id.TextView_Test);

        sendCSVFiles = (Button) findViewById(R.id.sendCSVFiles);

        addItemsOnSpinner();
        // Set up button listeners
        setUpButtonListeners();
    }
    /**
     * Helper method that sets up button listeners.
     */
    private void addItemsOnSpinner() {
        File storageDir[] = new File(Environment
                .getExternalStorageDirectory().getPath()
                + "/CS4222DataCollector/").listFiles();

        String result="";
        List<String> listBarometer = new ArrayList<String>();
        List<String> listAccelerometer = new ArrayList<String>();
        List<String> listGyroscope = new ArrayList<String>();

        if ( storageDir != null ) {
            for ( File file : storageDir ) {
                if ( file != null ) {
                    String tempDirectory = file.getAbsolutePath().substring(file.getAbsolutePath().indexOf("CS4222DataCollector")+20);
                    if(file.getAbsolutePath().contains("Barometer")){
                        listBarometer.add(tempDirectory);
                    }else if(file.getAbsolutePath().contains("Accelerometer")){
                        listAccelerometer.add(tempDirectory);
                    }else if(file.getAbsolutePath().contains("Gyroscope")){
                        listGyroscope.add(tempDirectory);
                    }
                    result = result + file.getAbsolutePath() + "\n";
                }
            }
            ArrayAdapter<String> dataAdapterBarometer = new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_item, listBarometer);
            dataAdapterBarometer.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerBarometer.setAdapter(dataAdapterBarometer);

            ArrayAdapter<String> dataAdapterAccelerometer = new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_item, listAccelerometer);
            dataAdapterAccelerometer.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerAccelerometer.setAdapter(dataAdapterAccelerometer);

            ArrayAdapter<String> dataAdapterGyroscope = new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_item, listGyroscope);
            dataAdapterGyroscope.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerGyroscope.setAdapter(dataAdapterGyroscope);


            //testText.setText(result);
        }
    }

    private void setUpButtonListeners() {

        sendCSVFiles.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Thread clickThread = new Thread() {
                    public void run() {

                        try {

                            String selectedBarometerFile = String.valueOf(spinnerBarometer.getSelectedItem());
                            String selectedAccelerometerFile = String.valueOf(spinnerAccelerometer.getSelectedItem());
                            String selectedGyroscopeFile = String.valueOf(spinnerGyroscope.getSelectedItem());

                            File storageDir[] = new File(Environment
                                    .getExternalStorageDirectory().getPath()
                                    + "/CS4222DataCollector/").listFiles();

                            File selectedBarometerFileCSV = null;
                            File selectedAccelerometerFileCSV = null;
                            File selectedGyroscopeFileCSV = null;

                            if ( storageDir != null ) {
                                for (File file : storageDir) {
                                    if (file != null) {
                                        if (file.getAbsolutePath().contains(selectedBarometerFile)) {
                                            selectedBarometerFileCSV = file;
                                        } else if (file.getAbsolutePath().contains(selectedAccelerometerFile)) {
                                            selectedAccelerometerFileCSV = file;
                                        } else if (file.getAbsolutePath().contains(selectedGyroscopeFile)) {
                                            selectedGyroscopeFileCSV = file;
                                        }
                                    }
                                }
                            }
                            //send message
                            DtnMessage message = new DtnMessage();

                            // Data part
                            message.addData().writeString(selectedBarometerFile).writeString(selectedAccelerometerFile).writeString(selectedGyroscopeFile).addFile(selectedBarometerFileCSV).addFile(selectedAccelerometerFileCSV).addFile(selectedGyroscopeFileCSV);
                          //  message.addData().writeLong(1).writeInt(1).writeInt(1);  // Chat message


                            // Broadcast the message using the fwd layer interface
                            fwdLayer1.sendMessage(descriptor, message, "graphapp", null);

                            // Tell the user that the message has been sent
                            createToast("Chat message broadcast! "+selectedBarometerFile + ", " + selectedAccelerometerFile + ", " + selectedGyroscopeFile);
                        } catch (Exception e) {
                            // Log the exception
                            //Log.e("BroadcastApp", "Exception while sending message", e);
                            // Inform the user
                            createToast("Exception while sending message, check log");
                        }
                    }
                };
                clickThread.start();
            }
        });

        startAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Start barometer sampling
                addItemsOnSpinner();
                startBarometerSampling();
                startAccelerometerSampling();
                startGyroscopeSampling();
                // Disable the start and enable stop button
                startBarometerButton.setEnabled(false);
                stopBarometerButton.setEnabled(true);
                startAccelerometerButton.setEnabled(false);
                stopAccelerometerButton.setEnabled(true);
                startGyroscopeButton.setEnabled(false);
                stopGyroscopeButton.setEnabled(true);
                startAll.setEnabled(false);
                stopAll.setEnabled(true);
                // Inform the user
                createToast("All started sampling");
            }
        });

        stopAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Stop barometer sampling
                stopBarometerSampling();
                stopAccelerometerSampling();
                stopGyroscopeSampling();
                // Disable the stop and enable start button
                startBarometerButton.setEnabled(true);
                stopBarometerButton.setEnabled(false);
                startAccelerometerButton.setEnabled(true);
                stopAccelerometerButton.setEnabled(false);
                startGyroscopeButton.setEnabled(true);
                stopGyroscopeButton.setEnabled(false);
                startAll.setEnabled(true);
                stopAll.setEnabled(false);
                // Inform the user
                createToast("All sampling stopped");
            }
        });

        // Start barometer
        startBarometerButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Start barometer sampling
                addItemsOnSpinner();
                startBarometerSampling();
                // Disable the start and enable stop button
                startBarometerButton.setEnabled(false);
                stopBarometerButton.setEnabled(true);
                // Inform the user
                barometerTextView.setText("\nAwaiting Barometer readings...\n");
                createToast("Barometer sampling started");
            }
        });

        // Stop barometer
        stopBarometerButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Stop barometer sampling
                stopBarometerSampling();
                // Disable the stop and enable start button
                startBarometerButton.setEnabled(true);
                stopBarometerButton.setEnabled(false);
                // Inform the user
                createToast("Barometer sampling stopped");
            }
        });

        startAccelerometerButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Start barometer sampling
                addItemsOnSpinner();
                startAccelerometerSampling();
                // Disable the start and enable stop button
                startAccelerometerButton.setEnabled(false);
                stopAccelerometerButton.setEnabled(true);
                // Inform the user
                accelerometerTextView.setText("\nAwaiting Accelerometer readings...\n");
                createToast("Accelerometer sampling started");
            }
        });

        stopAccelerometerButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Stop barometer sampling
                stopAccelerometerSampling();
                // Disable the stop and enable start button
                startAccelerometerButton.setEnabled(true);
                stopAccelerometerButton.setEnabled(false);
                // Inform the user
                createToast("Accelerometer sampling stopped");
            }
        });


        startGyroscopeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Start barometer sampling
                addItemsOnSpinner();
                startGyroscopeSampling();
                // Disable the start and enable stop button
                startGyroscopeButton.setEnabled(false);
                stopGyroscopeButton.setEnabled(true);
                // Inform the user
                gyroscopeTextView.setText("\nAwaiting Gyroscope readings...\n");
                createToast("Gyroscope sampling started");
            }
        });

        stopGyroscopeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Stop barometer sampling
                stopGyroscopeSampling();
                // Disable the stop and enable start button
                startGyroscopeButton.setEnabled(true);
                stopGyroscopeButton.setEnabled(false);
                // Inform the user
                createToast("Gyroscope sampling stopped");
            }
        });
/*
        // Start Location
        startLocationButton.setOnClickListener ( new View.OnClickListener() {
            public void onClick ( View v ) {
                // Start Location sampling
                startLocationSampling();
                // Disable the start and enable stop button
                startLocationButton.setEnabled( false );
                stopLocationButton.setEnabled( true );
                // Inform the user
                locationTextView.setText( "\nAwaiting location readings...\n" );
                createToast( "Location sampling started" );
            }
        } );

        // Stop Location
        stopLocationButton.setOnClickListener ( new View.OnClickListener() {
            public void onClick ( View v ) {
                // Stop Location sampling
                stopLocationSampling();
                // Disable the stop and enable start button
                startLocationButton.setEnabled( true );
                stopLocationButton.setEnabled( false );
                // Inform the user
                createToast( "Location sampling stopped" );
            }
        } );

        // Flush Location
        flushLocationButton.setOnClickListener ( new View.OnClickListener() {
            public void onClick ( View v ) {
                // Ask the location manager to flush cached GPS data and
                //  start from scratch
                // Note: This is only for PA1, this is not done in real
                //       application code
                try {
                    if ( ! isLocationOn ) {
                        locationManager = (LocationManager)
                                getSystemService( Context.LOCATION_SERVICE );
                    }
                    if ( locationManager.sendExtraCommand( LocationManager.GPS_PROVIDER ,
                            "delete_aiding_data" ,
                            null ) ) {
                        createToast( "Cached GPS data will be flushed" );
                    }
                    else {
                        createToast( "Warning: Unable to flush old GPS data" );
                    }
                }
                catch ( Exception e ) {
                    // Log the exception
                    Log.e( TAG , "Exception while flushing GPS data" , e );
                    // Inform the user
                    createToast( "Exception while flushing GPS data: " + e.toString() );
                }
                finally {
                    if ( ! isLocationOn ) {
                        locationManager = null;
                    }
                }
            }
        } );
        */
    }

    /**
     * Helper method that updates the barometer text view.
     */
    private void updateBarometerTextView(long barometerTime,
                                         float millibar,
                                         float height) {

        // Barometer details
        final StringBuilder sb = new StringBuilder();
        sb.append("\nBarometer--");
        sb.append("\nNumber of readings: " + numBarometerReadings);
        sb.append("\nMillibar: " + millibar);
        sb.append("\nHeight (m): " + height);
        sb.append("\nTime to get first sensor reading (msec): " + barometerDelayTime);
        sb.append("\nTime from previous reading (msec): " +
                (barometerTime - prevBarometerTime));

        // Update the text view in the main UI thread
        handler.post(new Runnable() {
            @Override
            public void run() {
                barometerTextView.setText(sb.toString());
            }
        });
    }
    private void logBarometerReading(long barometerTime,
                                     float millibar,
                                     float height) {

        // Barometer details
        final StringBuilder sb = new StringBuilder();
        sb.append(numBarometerReadings + ",");
        sb.append(barometerTime + ",");
        sb.append(getHumanReadableTime(barometerTime) + ",");
        sb.append(millibar + ",");
        sb.append(height + ",");
        sb.append((barometerTime - prevBarometerTime) + ",");
        sb.append(barometerDelayTime);

        // Log to the file (and flush)
        barometerLogFileOut.println(sb.toString());
        barometerLogFileOut.flush();
    }

    private void updateAccelerometerTextView(long accelerometerTime,
                                             float AccelerationX,
                                             float AccelerationY,
                                             float AccelerationZ) {

        // Barometer details
        final StringBuilder sb = new StringBuilder();
        sb.append("\nAccelerometer--");
        sb.append("\nNumber of readings: " + numAccelerometerReadings);
        sb.append("\nAccelerationX: " + AccelerationX);
        sb.append("\nAccelerationY: " + AccelerationY);
        sb.append("\nAccelerationZ: " + AccelerationZ);
        sb.append("\nTime to get first sensor reading (msec): " + accelerometerDelayTime);
        sb.append("\nTime from previous reading (msec): " +
                (accelerometerTime - prevAccelerometerTime));

        // Update the text view in the main UI thread
        handler.post(new Runnable() {
            @Override
            public void run() {
                accelerometerTextView.setText(sb.toString());
            }
        });
    }
    private void logAccelerometerReading(long accelerometerTime,
                                         float AccelerationX,
                                         float AccelerationY,
                                         float AccelerationZ) {

        // Barometer details
        final StringBuilder sb = new StringBuilder();
        sb.append(numAccelerometerReadings + ",");
        sb.append(accelerometerTime + ",");
        sb.append(getHumanReadableTime(accelerometerTime) + ",");
        sb.append(AccelerationX + ",");
        sb.append(AccelerationY + ",");
        sb.append(AccelerationZ + ",");
        sb.append((accelerometerTime - prevAccelerometerTime) + ",");
        sb.append(accelerometerDelayTime);

        // Log to the file (and flush)
        accelerometerLogFileOut.println(sb.toString());
        accelerometerLogFileOut.flush();
    }

    private void updateGyroscopeTextView(long gyroscopeTime,
                                         float OrientationX,
                                         float OrientationY,
                                         float OrientationZ) {

        // Barometer details
        final StringBuilder sb = new StringBuilder();
        sb.append("\nGyroscope--");
        sb.append("\nNumber of readings: " + numGyroscopeReadings);
        sb.append("\nOrientationX: " + OrientationX);
        sb.append("\nOrientationY: " + OrientationY);
        sb.append("\nOrientationZ: " + OrientationZ);
        sb.append("\nTime to get first sensor reading (msec): " + gyroscopeDelayTime);
        sb.append("\nTime from previous reading (msec): " +
                (gyroscopeTime - prevGyroscopeTime));

        // Update the text view in the main UI thread
        handler.post(new Runnable() {
            @Override
            public void run() {
                gyroscopeTextView.setText(sb.toString());
            }
        });
    }
    private void logGyroscopeReading(long gyroscopeTime,
                                     float OrientationX,
                                     float OrientationY,
                                     float OrientationZ) {

        // Barometer details
        final StringBuilder sb = new StringBuilder();
        sb.append(numGyroscopeReadings + ",");
        sb.append(gyroscopeTime + ",");
        sb.append(getHumanReadableTime(gyroscopeTime) + ",");
        sb.append(OrientationX + ",");
        sb.append(OrientationY + ",");
        sb.append(OrientationZ + ",");
        sb.append((gyroscopeTime - prevGyroscopeTime) + ",");
        sb.append(gyroscopeDelayTime);

        // Log to the file (and flush)
        gyroscopeLogFileOut.println(sb.toString());
        gyroscopeLogFileOut.flush();
    }


    /**
     * Helper method to create toasts for the user.
     */
    private void createToast(final String toastMessage) {

        // Post a runnable in the Main UI thread
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),
                        toastMessage,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }


    public void openLogFiles()
            throws IOException {

        // First, check if the sdcard is available for writing
        String externalStorageState = Environment.getExternalStorageState();
        if (!externalStorageState.equals(Environment.MEDIA_MOUNTED) &&
                !externalStorageState.equals(Environment.MEDIA_SHARED))
            throw new IOException("sdcard is not mounted on the filesystem");

        // Second, create the log directory
        File logDirectory = new File(Environment.getExternalStorageDirectory(),
                "CS4222DataCollector");
        logDirectory.mkdirs();
        if (!logDirectory.isDirectory())
            throw new IOException("Unable to create log directory");

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
        //System.out.println(timeStamp);

        // Third, create output streams for the log files (APPEND MODE)
        // Barometer log
        File logFile = new File(logDirectory, timeStamp+"Barometer.csv");
        FileOutputStream fout = new FileOutputStream(logFile, true);
        barometerLogFileOut = new PrintWriter(fout);

        logFile = new File(logDirectory, timeStamp+"Accelerometer.csv");
        fout = new FileOutputStream(logFile, true);
        accelerometerLogFileOut = new PrintWriter(fout);

        logFile = new File(logDirectory, timeStamp+"Gyroscope.csv");
        fout = new FileOutputStream(logFile, true);
        gyroscopeLogFileOut = new PrintWriter(fout);
        // Location log
        /*
        logFile = new File( logDirectory , "GPS.csv" );
        fout = new FileOutputStream( logFile , true );
        locationLogFileOut = new PrintWriter( fout );
        */
    }

    /**
     * Helper method that closes the log files.
     */
    public void closeLogFiles() {

        // Close the barometer log file
        try {
            barometerLogFileOut.close();
        } catch (Exception e) {
            Log.e(TAG, "Unable to close barometer log file", e);
        } finally {
            barometerLogFileOut = null;
        }
        try {
            accelerometerLogFileOut.close();
        } catch (Exception e) {
            Log.e(TAG, "Unable to close accelerometer log file", e);
        } finally {
            accelerometerLogFileOut = null;
        }
        try {
            gyroscopeLogFileOut.close();
        } catch (Exception e) {
            Log.e(TAG, "Unable to close gyroscope log file", e);
        } finally {
            gyroscopeLogFileOut = null;
        }

    }



    /**
     * Helper method to get the human readable time from unix time.
     */
    private static String getHumanReadableTime(long unixTime) {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-h-mm-ssa");
        return sdf.format(new Date(unixTime));
    }


    private Button startBarometerButton;
    private Button stopBarometerButton;
    private Button startGyroscopeButton;
    private Button stopGyroscopeButton;
    private Button startAccelerometerButton;
    private Button stopAccelerometerButton;
    private Button startAll;
    private Button stopAll;

    private Spinner spinnerBarometer;
    private Spinner spinnerAccelerometer;
    private Spinner spinnerGyroscope;

    private TextView testText;

    private Button sendCSVFiles;

    private Button flushLocationButton;
    private TextView barometerTextView;
    private TextView gyroscopeTextView;
    private TextView accelerometerTextView;

    /**
     * Sensor Manager.
     */
    private SensorManager sensorManager;
    /** Location Manager. */
    // private LocationManager locationManager;

    /**
     * Barometer sensor.
     */
    private Sensor barometerSensor;
    private Sensor accelerometerSensor;
    private Sensor gyroscopeSensor;
    /**
     * Barometer sampling rate (millisec).
     */
    private static final int BAROMETER_SAMPLING_RATE = 200;
    private static final int GYROSCOPE_SAMPLING_RATE = 200;
    private static final int ACCELEROMETER_SAMPLING_RATE = 200;

    private long barometerDelayTime;
    private long prevBarometerTime;
    private long accelerometerDelayTime;
    private long prevAccelerometerTime;
    private long gyroscopeDelayTime;
    private long prevGyroscopeTime;
    private long locationDelayTime;
    private long prevLocationTime;

    private int numBarometerReadings;
    private int numGyroscopeReadings;
    private int numAccelerometerReadings;

    private boolean isBarometerOn;
    private boolean isGyroscopeOn;
    private boolean isAccelerometerOn;

    private Handler handler;

    private ForwardingLayerInterface fwdLayer1;
    private DtnMiddlewareInterface middleware;

    private Descriptor descriptor;

    public PrintWriter barometerLogFileOut;
    public PrintWriter gyroscopeLogFileOut;
    public PrintWriter accelerometerLogFileOut;
    /**
     * DDMS Log Tag.
     */
    private static final String TAG = "CS4222DataCollector";
}

