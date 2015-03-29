package com.example.yude.androidplot;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;

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

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Create buffered readers to read the data files
        BufferedReader bufReader_Accel;
        BufferedReader bufReader_Baro;
        BufferedReader bufReader_Gyro;

        // For tokenization later
        ArrayList<ArrayList<String> > tokens_Accel = new ArrayList<>();
        ArrayList<ArrayList<String> > tokens_Baro = new ArrayList<>();
        ArrayList<ArrayList<String> > tokens_Gyro = new ArrayList<>();
        try {
            bufReader_Accel = getBufReaderFromAssets(FILENAME_ACCEL_DATA);
            bufReader_Baro = getBufReaderFromAssets(FILENAME_BARO_DATA);
            bufReader_Gyro = getBufReaderFromAssets(FILENAME_GYRO_DATA);

            // Tokenize the data
            tokens_Accel =   tokenizeCSVBufReader(bufReader_Accel);
            tokens_Baro =    tokenizeCSVBufReader(bufReader_Baro);
            tokens_Gyro =    tokenizeCSVBufReader(bufReader_Gyro);
        } catch (IOException ex) {
            Log.e("GETTING BUFFERED READER", "IOException encountered... finish() activity");
            finish(); // Don't continue execution if we could not read the files
        }

        // Make the ArrayLists representing the series
        ArrayList<Double> list_Accel_TimeBeforePrev =   makeArrayListFromCSVTokens(tokens_Accel, INDEX_ACCEL_DATA_TIMEBEFOREPREV);
        list_Accel_X =                                  makeArrayListFromCSVTokens(tokens_Accel, INDEX_ACCEL_DATA_X);
        list_Accel_Y =                                  makeArrayListFromCSVTokens(tokens_Accel, INDEX_ACCEL_DATA_Y);
        list_Accel_Z =                                  makeArrayListFromCSVTokens(tokens_Accel, INDEX_ACCEL_DATA_Z);

        ArrayList<Double> list_Baro_TimeBeforePrev =    makeArrayListFromCSVTokens(tokens_Baro, INDEX_BARO_DATA_TIMEBEFOREPREV);
        list_Baro_Millibar =                            makeArrayListFromCSVTokens(tokens_Baro, INDEX_BARO_DATA_MILLIBAR);
        list_Baro_Height =                              makeArrayListFromCSVTokens(tokens_Baro, INDEX_BARO_DATA_HEIGHT);

        ArrayList<Double> list_Gyro_TimeBeforePrev =    makeArrayListFromCSVTokens(tokens_Gyro, INDEX_GYRO_DATA_TIMEBEFOREPREV);
        list_Gyro_X =                                   makeArrayListFromCSVTokens(tokens_Gyro, INDEX_GYRO_DATA_X);
        list_Gyro_Y =                                   makeArrayListFromCSVTokens(tokens_Gyro, INDEX_GYRO_DATA_Y);
        list_Gyro_Z =                                   makeArrayListFromCSVTokens(tokens_Gyro, INDEX_GYRO_DATA_Z);

        // Convert the TimeBeforePrev array lists to time so that they can be used as the domain of the series
        list_Accel_Time = getCumulativeSumList(list_Accel_TimeBeforePrev);
        list_Baro_Time =  getCumulativeSumList(list_Baro_TimeBeforePrev);
        list_Gyro_Time =  getCumulativeSumList(list_Gyro_TimeBeforePrev);

        // Smoothen data

        list_Accel_X =          smoothenData(list_Accel_X, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Accel_Y =          smoothenData(list_Accel_Y, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Accel_Z =          smoothenData(list_Accel_Z, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Baro_Millibar =    smoothenData(list_Baro_Millibar, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Baro_Height =      smoothenData(list_Baro_Height, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Gyro_X =           smoothenData(list_Gyro_X, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Gyro_Y =           smoothenData(list_Gyro_Y, DEFAULT_SMOOTHENING_NUMPOINTS);
        list_Gyro_Z =           smoothenData(list_Gyro_Z, DEFAULT_SMOOTHENING_NUMPOINTS);


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
                Log.e("makeArrayListFromCSVTokens()", "Could not convert \"" + strVal + "\" to double, finish() activity");
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