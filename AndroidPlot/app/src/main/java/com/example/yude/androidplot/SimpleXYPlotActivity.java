package com.example.yude.androidplot;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

// jeremy added theses
import nus.context.fumapp.api.*;
import nus.dtn.middleware.api.DtnMiddlewareInterface;
import nus.dtn.middleware.api.DtnMiddlewareProxy;
import nus.dtn.middleware.api.MiddlewareEvent;
import nus.dtn.middleware.api.MiddlewareListener;
import nus.dtn.util.*;
import nus.dtn.api.fwdlayer.*;
import android.telephony.TelephonyManager;
import android.os.Handler;
import android.widget.Toast;

import javax.xml.transform.dom.DOMLocator;

/**
 * A straightforward example of using AndroidPlot to plot some data.
 */
public class SimpleXYPlotActivity extends Activity
{
    private final static String DIRECTORYNAME_DATACOLLECTOR= Environment
            .getExternalStorageDirectory().getPath()
            + "/CS4222DataCollector/";


    // threshold to be safe
    private final static double THRESHOLD_ACCEL = 1.1;
    private final static double THRESHOLD_BRAKE = -1.3;
    private final static double THRESHOLD_TURNLEFT = -1.1;
    private final static double THRESHOLD_TURNRIGHT = 1.1;
    private final static double THRESHOLD_TURNABOUT_LEFT = -1.5; // must be below 1.5
    private final static double THRESHOLD_TURNABOUT_RIGHT = 1.5; // must be below 1.5

    private final static double SECONDS_TURNLAROUND = 6.5; //

    private final static String FILENAME_ACCEL_DATA = "Accelerometer.csv";
    private final static String FILENAME_BARO_DATA = "Barometer.csv";
    private final static String FILENAME_GYRO_DATA = "Gyroscope.csv";

    // Indices of the values in the csv files
    private final static int INDEX_ACCEL_DATA_X = 3;
    private final static int INDEX_ACCEL_DATA_Y = 4;
    private final static int INDEX_ACCEL_DATA_Z = 5;
    private final static int INDEX_ACCEL_DATA_TIMEBEFOREPREV = 6;

    private final static int INDEX_BARO_DATA_MILLIBAR = 3;
    private final static int INDEX_BARO_DATA_HEIGHT = 4;
    private final static int INDEX_BARO_DATA_TIMEBEFOREPREV = 5;

    private final static int INDEX_GYRO_DATA_X = 3;
    private final static int INDEX_GYRO_DATA_Y = 4;
    private final static int INDEX_GYRO_DATA_Z = 5;
    private final static int INDEX_GYRO_DATA_TIMEBEFOREPREV = 6;

    // Used for moving average smoothening
    private final static int MAX_MOVINGAVG_POINTS = 50;
    private final static int DEFAULT_SMOOTHENING_NUMPOINTS = 10;


    private XYPlot plot;

    // Data to be initialized upon activity onCreate
    private ArrayList<Double> list_Accel_Time;
    private ArrayList<Double> list_Accel_X;
    private ArrayList<Double> list_Accel_Y;
    private ArrayList<Double> list_Accel_Z;

    private ArrayList<Double> list_Baro_Time;
    private ArrayList<Double> list_Baro_Millibar;
    private ArrayList<Double> list_Baro_Height;

    private ArrayList<Double> list_Gyro_Time;
    private ArrayList<Double> list_Gyro_X;
    private ArrayList<Double> list_Gyro_Y;
    private ArrayList<Double> list_Gyro_Z;

    // jeremy added theses
    private TextView testText;
    private ForwardingLayerInterface fwdLayer1;
    private DtnMiddlewareInterface middleware;
    private Handler handler;
    private Descriptor descriptor;
    private Spinner spinnerBarometer;
    private Spinner spinnerAccelerometer;
    private Spinner spinnerGyroscope;

    private FileObserver observer;
    private boolean accelerometerButtonPressed=false;
    private boolean barometerButtonPressed=false;
    private boolean gyroscopeButtonPressed=false;

    private Button accelerometerButton;
    private Button gyroscopeButton;
    private Button barometerButton;

    private Spinner spinnerForAllFiles;
    private TextView textViewForResult;

    List<String> listBarometer ;
    List<String> listAccelerometer;
    List<String> listGyroscope;
    List<String> listOfAllFiles ;

    ArrayAdapter<String> dataAdapterBarometer;
    ArrayAdapter<String> dataAdapterAccelerometer;
    ArrayAdapter<String> dataAdapterGyroscope;
    ArrayAdapter<String> dataAdapterForAllFiles;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        // Create buffered readers to read the data files
        BufferedReader bufReader_Accel;
        BufferedReader bufReader_Baro;
        BufferedReader bufReader_Gyro;

        // For tokenization later
        ArrayList<ArrayList<String>> tokens_Accel = new ArrayList<>();
        ArrayList<ArrayList<String>> tokens_Baro = new ArrayList<>();
        ArrayList<ArrayList<String>> tokens_Gyro = new ArrayList<>();
        try {
            bufReader_Accel = getBufReaderFromAssets(FILENAME_ACCEL_DATA);
            bufReader_Baro = getBufReaderFromAssets(FILENAME_BARO_DATA);
            bufReader_Gyro = getBufReaderFromAssets(FILENAME_GYRO_DATA);

            // Tokenize the data
            tokens_Accel = tokenizeCSVBufReader(bufReader_Accel);
            tokens_Baro = tokenizeCSVBufReader(bufReader_Baro);
            tokens_Gyro = tokenizeCSVBufReader(bufReader_Gyro);
        } catch (IOException ex) {
            Log.e("GETTING BUFFERED READER", "IOException encountered... finish() activity");
            finish(); // Don't continue execution if we could not read the files
        }

        // Make the ArrayLists representing the series
        ArrayList<Double> list_Accel_TimeBeforePrev = makeArrayListFromCSVTokens(tokens_Accel, INDEX_ACCEL_DATA_TIMEBEFOREPREV);
        list_Accel_X = makeArrayListFromCSVTokens(tokens_Accel, INDEX_ACCEL_DATA_X);
        list_Accel_Y = makeArrayListFromCSVTokens(tokens_Accel, INDEX_ACCEL_DATA_Y);
        list_Accel_Z = makeArrayListFromCSVTokens(tokens_Accel, INDEX_ACCEL_DATA_Z);

        ArrayList<Double> list_Baro_TimeBeforePrev = makeArrayListFromCSVTokens(tokens_Baro, INDEX_BARO_DATA_TIMEBEFOREPREV);
        list_Baro_Millibar = makeArrayListFromCSVTokens(tokens_Baro, INDEX_BARO_DATA_MILLIBAR);
        list_Baro_Height = makeArrayListFromCSVTokens(tokens_Baro, INDEX_BARO_DATA_HEIGHT);

        ArrayList<Double> list_Gyro_TimeBeforePrev = makeArrayListFromCSVTokens(tokens_Gyro, INDEX_GYRO_DATA_TIMEBEFOREPREV);
        list_Gyro_X = makeArrayListFromCSVTokens(tokens_Gyro, INDEX_GYRO_DATA_X);
        list_Gyro_Y = makeArrayListFromCSVTokens(tokens_Gyro, INDEX_GYRO_DATA_Y);
        list_Gyro_Z = makeArrayListFromCSVTokens(tokens_Gyro, INDEX_GYRO_DATA_Z);

        // Convert the TimeBeforePrev array lists to time so that they can be used as the domain of the series
        list_Accel_Time = getCumulativeSumList(list_Accel_TimeBeforePrev);
        list_Baro_Time = getCumulativeSumList(list_Baro_TimeBeforePrev);
        list_Gyro_Time = getCumulativeSumList(list_Gyro_TimeBeforePrev);

        // Smoothen data

        list_Accel_X = smoothenData(list_Accel_X, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Accel_Y = smoothenData(list_Accel_Y, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Accel_Z = smoothenData(list_Accel_Z, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Baro_Millibar = smoothenData(list_Baro_Millibar, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Baro_Height = smoothenData(list_Baro_Height, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Gyro_X = smoothenData(list_Gyro_X, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Gyro_Y = smoothenData(list_Gyro_Y, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Gyro_Z = smoothenData(list_Gyro_Z, DEFAULT_SMOOTHENING_NUMPOINTS);


        // fun little snippet that prevents users from taking screenshots
        // on ICS+ devices :-)
        // getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
        //         WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.simple_xy_plot_example);

        // initialize our XYPlot reference:
        plot = (XYPlot) findViewById(R.id.myDataPlot);

        // Create a couple arrays of y-values to plot:
        Number[] series1Numbers = {1, 8, 5, 2, 7, 4};
        Number[] series2Numbers = {4, 6, 3, 8, 2, 10};

        // Turn the above arrays into XYSeries':
        XYSeries series1 = new SimpleXYSeries(
                Arrays.asList(series1Numbers),          // SimpleXYSeries takes a List so turn our array into a List
                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, // Y_VALS_ONLY means use the element index as the x value
                "Series1");                             // Set the display title of the series

        // same as above
        XYSeries series2 = new SimpleXYSeries(Arrays.asList(series2Numbers), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Series2");

        // Create a formatter to use for drawing a series using LineAndPointRenderer
        // and configure it from xml:
        LineAndPointFormatter series1Format = new LineAndPointFormatter();
        series1Format.setPointLabelFormatter(new PointLabelFormatter());
        series1Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf1);

        // add a new series' to the xyplot:
        plot.addSeries(series1, series1Format);

        // same as above:
        LineAndPointFormatter series2Format = new LineAndPointFormatter();
        series2Format.setPointLabelFormatter(new PointLabelFormatter());
        series2Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf2);
        plot.addSeries(series2, series2Format);

        // reduce the number of range labels
        plot.setTicksPerRangeLabel(3);
        plot.getGraphWidget().setDomainLabelOrientation(-45);


        // jeremy added theses
        testText = (TextView) findViewById(R.id.TextView_Test);
        testText.setText("Testing 123");

        try {
            handler = new Handler();
            // Start the middleware
            middleware = new DtnMiddlewareProxy(getApplicationContext());
            middleware.start(new MiddlewareListener() {
                public void onMiddlewareEvent(MiddlewareEvent event) {
                    try {

                        // Check if the middleware failed to start
                        if (event.getEventType() != MiddlewareEvent.MIDDLEWARE_STARTED) {
                            throw new Exception("Middleware failed to start, is it installed?");
                        }

                        // Get the fwd layer API
                        fwdLayer1 = new ForwardingLayerProxy(middleware);
                        // Get a descriptor for this user
                        // Typically, the user enters the username, but here we simply use IMEI number
                        TelephonyManager telephonyManager =
                                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                        descriptor = fwdLayer1.getDescriptor("nus.cs4222.jeremy", telephonyManager.getDeviceId());

                        // Set the broadcast address
                        fwdLayer1.setBroadcastAddress ( "nus.cs4222.jeremy" , "graphapp" );

                        // Register a listener for received chat messages
                        ChatMessageListener messageListener = new ChatMessageListener();
                        fwdLayer1.addMessageListener(descriptor, messageListener);
                        createToast("Listening...");
                    } catch (Exception e) {
                        // Log the exception
                        Log.e("BroadcastApp", "Exception in middleware start listener", e);
                        // Inform the user
                       // createToast("Exception in middleware start listener, check log");
                    }
                }
            });
        } catch (Exception e) {
            createToast("error on create");
        }
        spinnerBarometer = (Spinner) findViewById(R.id.spinnerBarometer);
        spinnerAccelerometer = (Spinner) findViewById(R.id.spinnerAccelerometer);
        spinnerGyroscope = (Spinner) findViewById(R.id.spinnerGyroscope);

        spinnerForAllFiles = (Spinner) findViewById(R.id.spinnerForAllFiles);
        textViewForResult = (TextView) findViewById(R.id.TextView_EvaluationResult);

        addItemsOnSpinner();

        barometerButton = (Button) findViewById(R.id.barometerButton);
        accelerometerButton = (Button) findViewById(R.id.accelerometerButton);
        gyroscopeButton = (Button) findViewById(R.id.gyroscopeButton);
/*
        observer = new FileObserver(Environment
                .getExternalStorageDirectory().getPath()
                + "/CS4222DataCollector/") { // set up a file observer to watch this directory on sd card

            @Override
            public void onEvent(int event, String file) {
                //if(event == FileObserver.CREATE && !file.equals(".probe")){ // check if its a "create" and not equal to .probe because thats created every time camera is launched
            //    createToast("Event = " + event);
                    //}
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    createToast(e.toString());
                }
                if(barometerButtonPressed){
                    drawBarometer();
                }else if(gyroscopeButtonPressed) {
                   drawGyroscope();
                }else if(accelerometerButtonPressed) {
                    drawAccelerometer();
                }
            }
        };
        observer.startWatching(); //START OBSERVING
*/

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

                String selectedBarometerFile = message.readString();
                String selectedAccelerometerFile = message.readString();
                String selectedGyroscopeFile = message.readString();
/*
                listBarometer.add(selectedBarometerFile);
                listAccelerometer.add(selectedAccelerometerFile);
                listGyroscope.add(selectedGyroscopeFile);
                listOfAllFiles.add(selectedBarometerFile);
                listOfAllFiles.add(selectedAccelerometerFile);
                listOfAllFiles.add(selectedGyroscopeFile);

   */

                File selectedBarometerFileCSV = message.getFile(0);
                File selectedAccelerometerFileCSV = message.getFile(1);
                File selectedGyroscopeFileCSV = message.getFile(2);

                FileChannel inputChannel1 = null, inputChannel2 = null, inputChannel3 = null;
                FileChannel outputChannel1 = null, outputChannel2 = null, outputChannel3 = null;
                FileChannel fc1 = null, fc2 = null, fc3 = null, fc4 = null, fc5 = null, fc6 = null;

                try {
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


                    File logFileBarometer = new File(logDirectory, selectedBarometerFile);
                    File logFileAccelerometer = new File(logDirectory, selectedAccelerometerFile);
                    File logFileGyroscope = new File(logDirectory, selectedGyroscopeFile);


                    fc1 = new FileInputStream(selectedBarometerFileCSV).getChannel();
                    fc2 = new FileOutputStream(logFileBarometer).getChannel();
                    fc2.transferFrom(fc1, 0, fc1.size());

                    fc3 = new FileInputStream(selectedAccelerometerFileCSV).getChannel();
                    fc4 = new FileOutputStream(logFileAccelerometer).getChannel();
                    fc4.transferFrom(fc3, 0, fc3.size());

                    fc5 = new FileInputStream(selectedGyroscopeFileCSV).getChannel();
                    fc6 = new FileOutputStream(logFileGyroscope).getChannel();
                    fc6.transferFrom(fc5, 0, fc5.size());


                }finally{
                    fc1.close();
                    fc2.close();
                    fc3.close();
                    fc4.close();
                    fc5.close();
                    fc6.close();


                }
                // Append to the message list
                final String newText =
                        testText.getText() +
                                "\n" + source + " says: " + selectedBarometerFile + "\n " + selectedAccelerometerFile +"\n"+selectedGyroscopeFile+"\n";

                // Update the text view in Main UI thread
                createToast(newText);


                /*
                handler.post(new Runnable() {
                    public void run() {
                        testText.setText(newText);
                    }
                });
                */

             Thread.sleep(2000);
                handler.post(new Runnable() {

                    public void run() {

                        addItemsOnSpinner();
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

    private void addItemsOnSpinner() {
        File storageDir[] = new File(Environment
                .getExternalStorageDirectory().getPath()
                + "/CS4222DataCollector/").listFiles();

        String result="";
         listBarometer = new ArrayList<String>();
     listAccelerometer = new ArrayList<String>();
         listGyroscope = new ArrayList<String>();
        listOfAllFiles = new ArrayList<String>();

        if ( storageDir != null ) {
            for ( File file : storageDir ) {
                if ( file != null ) {
                    String tempDirectory = file.getAbsolutePath().substring(file.getAbsolutePath().indexOf("CS4222DataCollector")+20);
                    if(file.getAbsolutePath().contains("Barometer")){
                        listBarometer.add(tempDirectory);
                    }else if(file.getAbsolutePath().contains("Accelerometer")){
                        listAccelerometer.add(tempDirectory);
                        listOfAllFiles.add(tempDirectory);
                    }else if(file.getAbsolutePath().contains("Gyroscope")){
                        listGyroscope.add(tempDirectory);
                    }

                    result = result + file.getAbsolutePath() + "\n";
                }
            }
            //createToast("setting adapters");


             dataAdapterBarometer = new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_item, listBarometer);
            dataAdapterBarometer.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerBarometer.setAdapter(dataAdapterBarometer);

            dataAdapterAccelerometer = new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_item, listAccelerometer);
            dataAdapterAccelerometer.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerAccelerometer.setAdapter(dataAdapterAccelerometer);

            dataAdapterGyroscope = new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_item, listGyroscope);
            dataAdapterGyroscope.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerGyroscope.setAdapter(dataAdapterGyroscope);

            dataAdapterForAllFiles = new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_item, listOfAllFiles);
            dataAdapterForAllFiles.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerForAllFiles.setAdapter(dataAdapterForAllFiles);
           // createToast("setting adapters successsfully");
            //testText.setText(result);
        }
    }

    private void createToast(String toastMessage) {

        // Use a 'final' local variable, otherwise the compiler will complain
        final String toastMessageFinal = toastMessage;

        // Post a runnable in the Main UI thread
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),
                        toastMessageFinal,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
    public void pushData(View view) {

                Thread clickThread = new Thread() {
                    public void run() {

                        try {

                            String selectedBarometerFile = "dummy";
                            String selectedAccelerometerFile = "dummy";
                            String selectedGyroscopeFile = "dummy";
/*
                            File storageDir[] = new File(Environment
                                    .getExternalStorageDirectory().getPath()
                                    + "/CS4222DataCollector/").listFiles();
*/
                            File selectedBarometerFileCSV = null;
                            File selectedAccelerometerFileCSV = null;
                            File selectedGyroscopeFileCSV = null;
/*
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
                            */
                            //send message
                            DtnMessage message = new DtnMessage();

                            // Data part
                            message.addData().writeLong(1).writeString("hello");

                            // Broadcast the message using the fwd layer interface
                           fwdLayer1.sendMessage(descriptor, message, "everyone", null);

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



    public void plotAccelerometerData(View view) {

        accelerometerButtonPressed = true;
        barometerButtonPressed = false;
        gyroscopeButtonPressed = false;

        String fileName = spinnerAccelerometer.getSelectedItem().toString();
        File accelerometerFile = new File(DIRECTORYNAME_DATACOLLECTOR, fileName);

        prepareAccelerometerDataLists(accelerometerFile);
        preparePlot();

        Log.v("plotAccelerometerData", "Plotting accelerometer data...");
        plot.clear();
        plot.setTitle("Accelerometer Data");
        XYSeries accelXSeries = new SimpleXYSeries(
                list_Accel_Time,            // Time on x axis
                list_Accel_X,               // accelerometer x value on y axis
                "X");         // Set the display title of the series
        XYSeries accelYSeries = new SimpleXYSeries(
                list_Accel_Time,            // Time on x axis
                list_Accel_Y,               // accelerometer y value on y axis
                "Y");         // Set the display title of the series
        XYSeries accelZSeries = new SimpleXYSeries(
                list_Accel_Time,            // Time on x axis
                list_Accel_Z,               // accelerometer z value on y axis
                "Z");         // Set the display title of the series

        // Create a formatter to use for drawing a series using LineAndPointRenderer
        // and configure it from xml:
        LineAndPointFormatter series1Format = new LineAndPointFormatter();
        series1Format.setPointLabelFormatter(new PointLabelFormatter());
        series1Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf1);
        LineAndPointFormatter series2Format = new LineAndPointFormatter();
        series2Format.setPointLabelFormatter(new PointLabelFormatter());
        series2Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf2);
        LineAndPointFormatter series3Format = new LineAndPointFormatter();
        series3Format.setPointLabelFormatter(new PointLabelFormatter());
        series3Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf3);

        // Add series
        plot.addSeries(accelXSeries, series1Format);
        plot.addSeries(accelYSeries, series2Format);
        plot.addSeries(accelZSeries, series3Format);

        plot.redraw();
    }

    public void plotBarometerData(View view) {

        accelerometerButtonPressed = false;
        barometerButtonPressed = true;
        gyroscopeButtonPressed = false;

        String fileName = spinnerBarometer.getSelectedItem().toString();
        File barometerFile = new File(DIRECTORYNAME_DATACOLLECTOR, fileName);

        prepareBarometerDataLists(barometerFile);
        preparePlot();

        Log.v("plotBarometerData", "Plotting barometer data...");
        plot.clear();
        plot.setTitle("Barometer Data");
        XYSeries baroHeightSeries = new SimpleXYSeries(
                list_Baro_Time,
                list_Baro_Height,
                "Height");

        // Create a formatter to use for drawing a series using LineAndPointRenderer
        // and configure it from xml:
        LineAndPointFormatter series1Format = new LineAndPointFormatter();
        series1Format.setPointLabelFormatter(new PointLabelFormatter());
        series1Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf1);

        // Add series
        plot.addSeries(baroHeightSeries, series1Format);

        plot.redraw();
    }

    public void plotGyroscopeData(View view) {

        accelerometerButtonPressed = false;
        barometerButtonPressed = false;
        gyroscopeButtonPressed = true;

        String fileName = spinnerGyroscope.getSelectedItem().toString();
        File gyroscopeFile = new File(DIRECTORYNAME_DATACOLLECTOR, fileName);

        prepareGyroscopeDataLists(gyroscopeFile);
        preparePlot();

        Log.v("plotGyroscopeData", "Plotting gyroscope data...");
        plot.clear();
        plot.setTitle("Gyroscope Data");
        XYSeries gyroXSeries = new SimpleXYSeries(
                list_Gyro_Time,
                list_Gyro_X,
                "X");
        XYSeries gyroYSeries = new SimpleXYSeries(
                list_Gyro_Time,
                list_Gyro_Y,
                "Y");
        XYSeries gyroZSeries = new SimpleXYSeries(
                list_Gyro_Time,
                list_Gyro_Z,
                "Z");

        // Create a formatter to use for drawing a series using LineAndPointRenderer
        // and configure it from xml:
        LineAndPointFormatter series1Format = new LineAndPointFormatter();
        series1Format.setPointLabelFormatter(new PointLabelFormatter());
        series1Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf1);
        LineAndPointFormatter series2Format = new LineAndPointFormatter();
        series2Format.setPointLabelFormatter(new PointLabelFormatter());
        series2Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf2);
        LineAndPointFormatter series3Format = new LineAndPointFormatter();
        series3Format.setPointLabelFormatter(new PointLabelFormatter());
        series3Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf3);

        // Add series
        plot.addSeries(gyroXSeries, series1Format);
        plot.addSeries(gyroYSeries, series2Format);
        plot.addSeries(gyroZSeries, series3Format);

        plot.redraw();
    }

    private void prepareAccelerometerDataLists(File accelerometerFile) {
        BufferedReader bufReader_Accel;

        // For tokenization later
        ArrayList<ArrayList<String>> tokens_Accel = new ArrayList<>();
        try {
            bufReader_Accel =  new BufferedReader(
                    new FileReader(accelerometerFile)
            );

            // Tokenize the data
            tokens_Accel = tokenizeCSVBufReader(bufReader_Accel);
        } catch (IOException ex) {
            Log.e("GETTING BUFFERED READER", "IOException encountered... finish() activity");
            finish(); // Don't continue execution if we could not read the files
        }

        ArrayList<Double> list_Accel_TimeBeforePrev = makeArrayListFromCSVTokens(tokens_Accel, INDEX_ACCEL_DATA_TIMEBEFOREPREV);
        list_Accel_X = makeArrayListFromCSVTokens(tokens_Accel, INDEX_ACCEL_DATA_X);
        list_Accel_Y = makeArrayListFromCSVTokens(tokens_Accel, INDEX_ACCEL_DATA_Y);
        list_Accel_Z = makeArrayListFromCSVTokens(tokens_Accel, INDEX_ACCEL_DATA_Z);

        // Convert the TimeBeforePrev array lists to time so that they can be used as the domain of the series
        list_Accel_Time = getCumulativeSumList(list_Accel_TimeBeforePrev);
        list_Accel_Time = divideDoubles(list_Accel_Time, 1000); // convert to seconds

        // Smoothen data

        list_Accel_X = smoothenData(list_Accel_X, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Accel_Y = smoothenData(list_Accel_Y, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Accel_Z = smoothenData(list_Accel_Z, DEFAULT_SMOOTHENING_NUMPOINTS);
    }

    private void prepareBarometerDataLists(File barometerFile) {
        BufferedReader bufReader_Baro;

        // For tokenization later
        ArrayList<ArrayList<String>> tokens_Baro = new ArrayList<>();
        try {
            bufReader_Baro =  new BufferedReader(
                    new FileReader(barometerFile)
            );

            // Tokenize the data
            tokens_Baro = tokenizeCSVBufReader(bufReader_Baro);
        } catch (IOException ex) {
            Log.e("GETTING BUFFERED READER", "IOException encountered... finish() activity");
            finish(); // Don't continue execution if we could not read the files
        }

        ArrayList<Double> list_Baro_TimeBeforePrev = makeArrayListFromCSVTokens(tokens_Baro, INDEX_BARO_DATA_TIMEBEFOREPREV);
        list_Baro_Millibar = makeArrayListFromCSVTokens(tokens_Baro, INDEX_BARO_DATA_MILLIBAR);
        list_Baro_Height = makeArrayListFromCSVTokens(tokens_Baro, INDEX_BARO_DATA_HEIGHT);

        // Convert the TimeBeforePrev array lists to time so that they can be used as the domain of the series
        list_Baro_Time = getCumulativeSumList(list_Baro_TimeBeforePrev);
        list_Baro_Time = divideDoubles(list_Baro_Time, 1000); // convert to seconds

        // Smoothen data
        list_Baro_Millibar = smoothenData(list_Baro_Millibar, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Baro_Height = smoothenData(list_Baro_Height, DEFAULT_SMOOTHENING_NUMPOINTS);
    }

    private void prepareGyroscopeDataLists(File gyroscopeFile) {
        BufferedReader bufReader_Gyro;

        // For tokenization later
        ArrayList<ArrayList<String>> tokens_Gyro = new ArrayList<>();
        try {
            bufReader_Gyro =  new BufferedReader(
                    new FileReader(gyroscopeFile)
            );

            // Tokenize the data
            tokens_Gyro = tokenizeCSVBufReader(bufReader_Gyro);
        } catch (IOException ex) {
            Log.e("GETTING BUFFERED READER", "IOException encountered... finish() activity");
            finish(); // Don't continue execution if we could not read the files
        }

        ArrayList<Double> list_Gyro_TimeBeforePrev = makeArrayListFromCSVTokens(tokens_Gyro, INDEX_GYRO_DATA_TIMEBEFOREPREV);
        list_Gyro_X = makeArrayListFromCSVTokens(tokens_Gyro, INDEX_GYRO_DATA_X);
        list_Gyro_Y = makeArrayListFromCSVTokens(tokens_Gyro, INDEX_GYRO_DATA_Y);
        list_Gyro_Z = makeArrayListFromCSVTokens(tokens_Gyro, INDEX_GYRO_DATA_Z);

        // Convert the TimeBeforePrev array lists to time so that they can be used as the domain of the series
        list_Gyro_Time = getCumulativeSumList(list_Gyro_TimeBeforePrev);
        list_Gyro_Time = divideDoubles(list_Gyro_Time, 1000); // convert to seconds

        // Smoothen data

        list_Gyro_X = smoothenData(list_Gyro_X, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Gyro_Y = smoothenData(list_Gyro_Y, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Gyro_Z = smoothenData(list_Gyro_Z, DEFAULT_SMOOTHENING_NUMPOINTS);
    }

    private void preparePlot() {
        double startRange = getPlotStartRange();
        double endRange = getPlotEndRange();
        if (startRange < 0.0) {
            startRange = 0.0;
        }
        plot.setDomainLeftMin(startRange);
        if (endRange < 0.0) {
            plot.setDomainRightMax(999999999);
        } else {
            plot.setDomainRightMax(endRange);
        }
    }

    // negative denotes start of range (effectively, no input specified)
    private double getPlotStartRange() {
        String startRangeStr = ((EditText) findViewById(R.id.plotStartRange)).getText().toString();
        double startRange;
        if (startRangeStr == null || startRangeStr.isEmpty()) {
            startRange = -1.0; // not specified, start from the start
        } else {
            startRange = Double.parseDouble(startRangeStr);
        }
        Log.v("getPlotStartRange()", "startRange = " + startRange);
        return startRange;
    }

    // Negative end range denotes, effectively, no input
    private double getPlotEndRange() {
        String endRangeStr = ((EditText) findViewById(R.id.plotEndRange)).getText().toString();
        double endRange;
        if (endRangeStr == null || endRangeStr.isEmpty()) {
            endRange = -1.0; // negative to denote that we should plot to the end of the data
        } else {
            endRange = Double.parseDouble(endRangeStr);
        }
        Log.v("getPlotEndRange()", "endRange = " + endRange);
        return endRange;
    }

    private ArrayList<Double> divideDoubles(ArrayList<Double> doubleList, int divisor) {
        ArrayList<Double> retList = new ArrayList<>();
        for (int i = 0; i < doubleList.size(); i++) {
            retList.add(doubleList.get(i)/divisor);
        }
        return retList;
    }

    private BufferedReader getBufReaderFromAssets(String filename) throws IOException {
        return new BufferedReader(
                new InputStreamReader(getResources().getAssets().open(filename))
        );
    }

    /**
     * Tokenizes a CSV file into an array list of array list of strings
     * where each row is a line in the CSV file and each column is the comma-separated value.
     * @param br the buffered reader of the CSV file
     * @return an array list of array list of strings representation of the CSV file
     */
    private ArrayList<ArrayList<String> > tokenizeCSVBufReader(BufferedReader br) {
        ArrayList<ArrayList<String> > ret = new ArrayList<>();
        try {
            String line;
            while ((line = br.readLine()) != null) {
                ArrayList<String> row = new ArrayList<>();
                ret.add(row);
                StringTokenizer tokenizer = new StringTokenizer(line, ",");
                while (tokenizer.hasMoreTokens()) {
                    row.add(tokenizer.nextToken());
                }
            }
        } catch (IOException ex) {
            Log.e("tokenizeCSVBufReader()", "IOException encountered, finish() activity)");
            finish();
        }
        return ret;
    }

    /**
     * Extracts a (data-type Double) column out of the arraylist of arraylist of strings.
     * @param csvTokens the arraylist of arraylist of strings to perform extraction on
     * @param colIndex the column index required
     * @return an ArrayList<Double> representation of the column, assuming that the data can be converted to doubles
     */
    private ArrayList<Double> makeArrayListFromCSVTokens(ArrayList<ArrayList<String> > csvTokens, int colIndex) {
        ArrayList<Double> ret = new ArrayList<>();
        for (int i = 0; i < csvTokens.size(); i++) {
            if (colIndex >= csvTokens.get(i).size()) {
                // No such column for this row
                continue;
            }
            String strVal = csvTokens.get(i).get(colIndex);
            try {
                Double doubleVal = Double.valueOf(strVal);
                ret.add(doubleVal);
            } catch (NumberFormatException ex) {
                //Log.e("makeArrayListFromCSVTokens()", "Could not convert \"" + strVal + "\" to double, finish() activity");
                finish();
            }
        }
        return ret;
    }

    /**
     * Converts a list of doubles to a cumulative sum list.
     * @param doubleList the ArrayList of doubles
     * @return an ArrayList<Double> containing the cumulative sum of the list given
     */
    private ArrayList<Double> getCumulativeSumList(ArrayList<Double> doubleList) {
        ArrayList<Double> cumulativeSumList = new ArrayList<>();
        if (doubleList.isEmpty()) {
            return cumulativeSumList;
        }

        cumulativeSumList.add(doubleList.get(0));
        for (int i = 1; i < doubleList.size(); i++) {
            cumulativeSumList.add(doubleList.get(i) + cumulativeSumList.get(i-1));
        }
        return cumulativeSumList;
    }

    /**
     * Smoothens data in the form of an array list of doubles.
     * This is done by using a moving average over the specified number of data points.
     * @param doubleList the data to be smoothened
     * @param numPoints the number of points over which the moving average will be calculated
     * @return
     */
    private ArrayList<Double> smoothenData(ArrayList<Double> doubleList, int numPoints) {
        if (numPoints <= 1 || numPoints > MAX_MOVINGAVG_POINTS) {
            numPoints = MAX_MOVINGAVG_POINTS;
        }
        ArrayList<Double> rangeSum = getCumulativeSumList(doubleList);
        ArrayList<Double> ret = new ArrayList<Double>();
        double val = 0.0;
        for (int i = 0; i < doubleList.size(); i++) {
            if (i >= numPoints) {
                val = rangeSum.get(i) - rangeSum.get(i - numPoints);
                ret.add(val/numPoints);
            } else {
                val = rangeSum.get(i);
                ret.add(val/(i+1));
            }
        }
        return ret;
    }

    private void drawAccelerometer() {
        accelerometerButtonPressed = true;
        barometerButtonPressed = false;
        gyroscopeButtonPressed = false;

        String fileName = spinnerAccelerometer.getSelectedItem().toString();
        File accelerometerFile = new File(DIRECTORYNAME_DATACOLLECTOR, fileName);

        prepareAccelerometerDataLists(accelerometerFile);

        Log.v("plotAccelerometerData", "Plotting accelerometer data...");
        plot.clear();
        plot.setTitle("Accelerometer Data");
        XYSeries accelXSeries = new SimpleXYSeries(
                list_Accel_Time,            // Time on x axis
                list_Accel_X,               // accelerometer x value on y axis
                "X");         // Set the display title of the series
        XYSeries accelYSeries = new SimpleXYSeries(
                list_Accel_Time,            // Time on x axis
                list_Accel_Y,               // accelerometer y value on y axis
                "Y");         // Set the display title of the series
        XYSeries accelZSeries = new SimpleXYSeries(
                list_Accel_Time,            // Time on x axis
                list_Accel_Z,               // accelerometer z value on y axis
                "Z");         // Set the display title of the series

        // Create a formatter to use for drawing a series using LineAndPointRenderer
        // and configure it from xml:
        LineAndPointFormatter series1Format = new LineAndPointFormatter();
        series1Format.setPointLabelFormatter(new PointLabelFormatter());
        series1Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf1);
        LineAndPointFormatter series2Format = new LineAndPointFormatter();
        series2Format.setPointLabelFormatter(new PointLabelFormatter());
        series2Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf2);
        LineAndPointFormatter series3Format = new LineAndPointFormatter();
        series3Format.setPointLabelFormatter(new PointLabelFormatter());
        series3Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf3);

        // Add series
        plot.addSeries(accelXSeries, series1Format);
        plot.addSeries(accelYSeries, series2Format);
        plot.addSeries(accelZSeries, series3Format);

        plot.redraw();
    }
    private void drawBarometer(){
        accelerometerButtonPressed = false;
        barometerButtonPressed = true;
        gyroscopeButtonPressed = false;

        String fileName = spinnerBarometer.getSelectedItem().toString();
        File barometerFile = new File(DIRECTORYNAME_DATACOLLECTOR, fileName);

        prepareBarometerDataLists(barometerFile);

        Log.v("plotBarometerData", "Plotting barometer data...");
        plot.clear();
        plot.setTitle("Barometer Data");
        XYSeries baroHeightSeries = new SimpleXYSeries(
                list_Baro_Time,
                list_Baro_Height,
                "Height");

        // Create a formatter to use for drawing a series using LineAndPointRenderer
        // and configure it from xml:
        LineAndPointFormatter series1Format = new LineAndPointFormatter();
        series1Format.setPointLabelFormatter(new PointLabelFormatter());
        series1Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf1);

        // Add series
        plot.addSeries(baroHeightSeries, series1Format);

        plot.redraw();
    }
    public void evaluateFile(View view){


        String fileName = spinnerForAllFiles.getSelectedItem().toString();
        File selectedFileForEvaluation = new File(DIRECTORYNAME_DATACOLLECTOR, fileName);

        if(selectedFileForEvaluation.getAbsolutePath().contains("Barometer")){
            prepareBarometerDataLists(selectedFileForEvaluation);
        }else if(selectedFileForEvaluation.getAbsolutePath().contains("Accelerometer")){
            prepareAccelerometerDataLists(selectedFileForEvaluation);
        }else if(selectedFileForEvaluation.getAbsolutePath().contains("Gyroscope")){
            prepareGyroscopeDataLists(selectedFileForEvaluation);
        }



        preparePlot();

        Log.v("plotAccelerometerData", "Plotting accelerometer data...");
        plot.clear();
        plot.setTitle("Accelerometer Data");
        XYSeries accelXSeries = new SimpleXYSeries(
                list_Accel_Time,            // Time on x axis
                list_Accel_X,               // accelerometer x value on y axis
                "X");         // Set the display title of the series
        XYSeries accelYSeries = new SimpleXYSeries(
                list_Accel_Time,            // Time on x axis
                list_Accel_Y,               // accelerometer y value on y axis
                "Y");         // Set the display title of the series
        XYSeries accelZSeries = new SimpleXYSeries(
                list_Accel_Time,            // Time on x axis
                list_Accel_Z,               // accelerometer z value on y axis
                "Z");         // Set the display title of the series

        // Create a formatter to use for drawing a series using LineAndPointRenderer
        // and configure it from xml:
        LineAndPointFormatter series1Format = new LineAndPointFormatter();
        series1Format.setPointLabelFormatter(new PointLabelFormatter());
        series1Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf1);
        LineAndPointFormatter series2Format = new LineAndPointFormatter();
        series2Format.setPointLabelFormatter(new PointLabelFormatter());
        series2Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf2);
        LineAndPointFormatter series3Format = new LineAndPointFormatter();
        series3Format.setPointLabelFormatter(new PointLabelFormatter());
        series3Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_plf3);

        // Add series
        plot.addSeries(accelXSeries, series1Format);
        plot.addSeries(accelYSeries, series2Format);
        plot.addSeries(accelZSeries, series3Format);

        plot.redraw();

        // acceleration (positive y axis) 1.3
        // brake (negative y axis) -1.3
        // left turn (negative x) -1.1
        // right turn (positive x) 1.1
        // turnabout right 1.5
        // turnabout left -1.5
        // turnabout 7
        String result = "";


        double accelerationMax = 0;
        double brakeMax = 0;
        double turnAboutRightMax = 0;
        double turnAboutLeftMax = 0;
        double turnRightMax = 0;
        double turnLeftMax = 0;
        double startTime=0 ;
        double endTime=0 ;
        int totalOffenseCount = 0;
        for(int i=0;i<list_Accel_Time.size();i++) {
            // checking for acceleration and braking(y axis)
            if(list_Accel_Y.get(i)>0){
                if(list_Accel_Y.get(i)>accelerationMax) {
                    accelerationMax = list_Accel_Y.get(i);
                }else{
                    // to get start/end time
                    if(accelerationMax >THRESHOLD_ACCEL) {
                        for (int j = i; j >= 0; j--) {
                            if (list_Accel_Y.get(j) > -0.2 && list_Accel_Y.get(j) < 0.2) {
                                startTime = list_Accel_Time.get(j);
                                break;
                            }

                        }
                        for (int j = i; j < list_Accel_Time.size(); j++) {
                            if (list_Accel_Y.get(j) > -0.2 && list_Accel_Y.get(j) < 0.2) {
                                endTime = list_Accel_Time.get(j);
                                i=j;
                                break;
                            }
                        }

                        double p = Math.pow(10d, 4);
                        double accelerationMaxRounded = Math.round(accelerationMax * p)/p;
                        String current = startTime + "-" + endTime +", "+accelerationMaxRounded+ ", accelerates too fast\n";
                        result = result + current;
                        totalOffenseCount++;

                    }
                    accelerationMax = 0;
                    startTime = 0;
                    endTime = 0;
                }

            }else if(list_Accel_Y.get(i)<0){
                if(list_Accel_Y.get(i)<brakeMax) {
                    brakeMax = list_Accel_Y.get(i);
                }else{
                    // to get start/end time
                    if(brakeMax < THRESHOLD_BRAKE) {
                        for (int j = i; j >= 0; j--) {
                            if (list_Accel_Y.get(j) > -0.2 && list_Accel_Y.get(j) < 0.2) {
                                startTime = list_Accel_Time.get(j);
                                break;
                            }

                        }
                        for (int j = i; j < list_Accel_Time.size(); j++) {
                            if (list_Accel_Y.get(j) > -0.2 && list_Accel_Y.get(j) < 0.2) {
                                endTime = list_Accel_Time.get(j);
                                i=j;
                                break;
                            }
                        }

                        double p = Math.pow(10d, 4);
                        double brakeMaxRounded = Math.round(brakeMax * p)/p;
                        String current = startTime + "-" + endTime +", "+brakeMaxRounded+ ", breaks too fast\n";
                        result = result + current;
                        totalOffenseCount++;

                    }
                    brakeMax = 0;
                    startTime = 0;
                    endTime = 0;
                }
            }


            // checking for turns
            if(list_Accel_X.get(i)>0){
                if(list_Accel_X.get(i)>turnRightMax) {
                    turnRightMax = list_Accel_X.get(i);
                }else{
                    // to get start/end time
                    if(turnRightMax >THRESHOLD_TURNRIGHT) {
                        for (int j = i; j >= 0; j--) {
                            if (list_Accel_X.get(j) > -0.2 && list_Accel_X.get(j) < 0.2) {
                                startTime = list_Accel_Time.get(j);
                                break;
                            }

                        }
                        for (int j = i; j < list_Accel_Time.size(); j++) {
                            if (list_Accel_X.get(j) > -0.2 && list_Accel_X.get(j) < 0.2) {
                                endTime = list_Accel_Time.get(j);
                                i=j;
                                break;
                            }
                        }
                        if(endTime-startTime<SECONDS_TURNLAROUND) {
                            double p = Math.pow(10d, 4);
                            double turnRightMaxRounded = Math.round(turnRightMax * p) / p;
                            String current = startTime + "-" + endTime + ", " + turnRightMaxRounded + ", turn right too fast\n";
                            result = result + current;
                            totalOffenseCount++;
                        }else if(turnRightMax>THRESHOLD_TURNABOUT_RIGHT){
                            double p = Math.pow(10d, 4);
                            double turnRightMaxRounded = Math.round(turnRightMax * p) / p;
                            String current = startTime + "-" + endTime + ", " + turnRightMaxRounded + ", turnabout right too fast\n";
                            result = result + current;
                            totalOffenseCount++;
                        }

                    }
                    turnRightMax = 0;
                    startTime = 0;
                    endTime = 0;
                }

            }else if(list_Accel_X.get(i)<0){
                if(list_Accel_X.get(i)>turnLeftMax) {
                    turnLeftMax = list_Accel_X.get(i);
                }else{
                    // to get start/end time
                    if(turnLeftMax <THRESHOLD_TURNLEFT) {
                        for (int j = i; j >= 0; j--) {
                            if (list_Accel_X.get(j) > -0.2 && list_Accel_X.get(j) < 0.2) {
                                startTime = list_Accel_Time.get(j);
                                break;
                            }

                        }
                        for (int j = i; j < list_Accel_Time.size(); j++) {
                            if (list_Accel_X.get(j) > -0.2 && list_Accel_X.get(j) < 0.2) {
                                endTime = list_Accel_Time.get(j);
                                i=j;
                                break;
                            }
                        }
                        if(endTime-startTime<SECONDS_TURNLAROUND) {
                            double p = Math.pow(10d, 4);
                            double turnLeftMaxRounded = Math.round(turnLeftMax * p) / p;
                            String current = startTime + "-" + endTime + ", " + turnLeftMaxRounded + ", turn left too fast\n";
                            result = result + current;
                            totalOffenseCount++;
                        }else if(turnLeftMax<THRESHOLD_TURNABOUT_LEFT){
                            double p = Math.pow(10d, 4);
                            double turnLeftMaxRounded = Math.round(turnLeftMax * p) / p;
                            String current = startTime + "-" + endTime + ", " + turnLeftMaxRounded + ", turnabout left too fast\n";
                            result = result + current;
                            totalOffenseCount++;
                        }

                    }
                    turnLeftMax = 0;
                    startTime = 0;
                    endTime = 0;
                }

            }
        }
        result = result+"You have a total of "+totalOffenseCount+"\n";
        textViewForResult.setText(result);
    }
    public void dynamicPlot(View v) {
        Intent intent = new Intent(this, DynamicXYPlotActivity.class);
        startActivity(intent);
        finish();
    }
}