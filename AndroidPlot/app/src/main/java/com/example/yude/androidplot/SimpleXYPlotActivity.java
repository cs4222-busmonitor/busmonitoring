package com.example.yude.androidplot;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

/**
 * A straightforward example of using AndroidPlot to plot some data.
 */
public class SimpleXYPlotActivity extends Activity
{
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
        plot = (XYPlot) findViewById(R.id.mySimpleXYPlot);

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
                        createToast("Exception in middleware start listener, check log");
                    }
                }
            });
        } catch (Exception e) {
            createToast("error on create");
        }
        spinnerBarometer = (Spinner) findViewById(R.id.spinnerBarometer);
        spinnerAccelerometer = (Spinner) findViewById(R.id.spinnerAccelerometer);
        spinnerGyroscope = (Spinner) findViewById(R.id.spinnerGyroscope);
        addItemsOnSpinner();
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
}