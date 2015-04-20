package com.example.yude.androidplot;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.XYSeries;
import com.androidplot.xy.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.StringTokenizer;

import nus.dtn.api.fwdlayer.ForwardingLayerInterface;
import nus.dtn.middleware.api.DtnMiddlewareInterface;
import nus.dtn.util.Descriptor;


public class DynamicXYPlotActivity extends Activity implements View.OnClickListener {

	private final static String DIRECTORYNAME_DATACOLLECTOR= Environment
					.getExternalStorageDirectory().getPath()
					+ "/CS4222DataCollector/";

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
	public static final int SERIES_INDEX_X = 1;
	public static final int SERIES_INDEX_Y = 2;
	public static final int SERIES_INDEX_Z = 3;
	public static final int SERIES_INDEX_HEIGHT = 4;

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
	private ForwardingLayerInterface fwdLayer1;
	private DtnMiddlewareInterface middleware;
	private Handler handler;
	private Descriptor descriptor;
	private Spinner spinnerBarometer;
	private Spinner spinnerAccelerometer;
	private Spinner spinnerGyroscope;

	private Button accelerometerButton;
	private Button accelerometerStartButton;
	private Button accelerometerStopButton;

	private Button gyroscopeButton;
	private Button gyroscopeStartButton;
	private Button gyroscopeStopButton;

	private Button barometerButton;
	private Button barometerStartButton;
	private Button barometerStopButton;

	private EditText maxElements;

	// redraws a plot whenever an update is received:
	private class MyPlotUpdater implements Observer {
		XYPlot plot;

		public MyPlotUpdater(XYPlot plot) {
			this.plot = plot;
		}

		@Override
		public void update(Observable o, Object arg) {
			plot.redraw();
		}
	}

	private XYPlot dynamicPlot;
	private MyPlotUpdater plotUpdater;
	private Thread myThread;
	private MonitorData data;


	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.dynamicxyplot_example);

		// get handles to our View defined in layout.xml:
		dynamicPlot = (XYPlot) findViewById(R.id.myDataPlot);

		plotUpdater = new MyPlotUpdater(dynamicPlot);

		data = new MonitorData();
		data.addObserver(plotUpdater);

		list_Accel_Time = new ArrayList<Double>();
		list_Accel_X = new ArrayList<Double>();
		list_Accel_Y = new ArrayList<Double>();
		list_Accel_Z = new ArrayList<Double>();

		list_Baro_Time = new ArrayList<Double>();
		list_Baro_Millibar = new ArrayList<Double>();
		list_Baro_Height = new ArrayList<Double>();

		list_Gyro_Time = new ArrayList<Double>();
		list_Gyro_X = new ArrayList<Double>();
		list_Gyro_Y = new ArrayList<Double>();
		list_Gyro_Z = new ArrayList<Double>();

		// UI setup
		maxElements = (EditText) findViewById(R.id.plotMaxElements);
		spinnerBarometer = (Spinner) findViewById(R.id.spinnerBarometer);
		spinnerAccelerometer = (Spinner) findViewById(R.id.spinnerAccelerometer);
		spinnerGyroscope = (Spinner) findViewById(R.id.spinnerGyroscope);

		accelerometerButton = (Button) findViewById(R.id.accelerometerButton);
		accelerometerStartButton = (Button) findViewById(R.id.accelerometerStartButton);
		accelerometerStopButton = (Button) findViewById(R.id.accelerometerStopButton);

		gyroscopeButton = (Button) findViewById(R.id.gyroscopeButton);
		gyroscopeStartButton = (Button) findViewById(R.id.gyroscopeStartButton);
		gyroscopeStopButton = (Button) findViewById(R.id.gyroscopeStopButton);

		barometerButton = (Button) findViewById(R.id.barometerButton);
		barometerStartButton = (Button) findViewById(R.id.barometerStartButton);
		barometerStopButton = (Button) findViewById(R.id.barometerStopButton);

		addItemsOnSpinner();

		accelerometerButton.setOnClickListener(this);
		accelerometerStartButton.setOnClickListener(this);
		accelerometerStopButton.setOnClickListener(this);
		gyroscopeButton.setOnClickListener(this);
		gyroscopeStartButton.setOnClickListener(this);
		gyroscopeStopButton.setOnClickListener(this);
		barometerButton.setOnClickListener(this);
		barometerStartButton.setOnClickListener(this);
		barometerStopButton.setOnClickListener(this);
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

	public void plotAccelerometerData(View view, boolean isSimulation) {

		String fileName = spinnerAccelerometer.getSelectedItem().toString();
		File accelerometerFile = new File(DIRECTORYNAME_DATACOLLECTOR, fileName);

		prepareAccelerometerDataLists(accelerometerFile);

		if (isSimulation) {
			preparePlot(-1.0, -1.0);
		} else {
			preparePlot(null, null);
		}

		Log.v("plotAccelerometerData", "Plotting accelerometer data...");
		dynamicPlot.clear();
		dynamicPlot.setTitle("Accelerometer Data");

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


		XYSeries accelXSeries;
		XYSeries accelYSeries;
		XYSeries accelZSeries;

		if(isSimulation) {
			accelXSeries = new DynamicSeries(
							data,            // Time on x axis
							SERIES_INDEX_X,               // accelerometer x value on y axis
							"X");         // Set the display title of the series
			accelYSeries = new DynamicSeries(
							data,            // Time on x axis
							SERIES_INDEX_Y,               // accelerometer y value on y axis
							"Y");         // Set the display title of the series
			accelZSeries = new DynamicSeries(
							data,            // Time on x axis
							SERIES_INDEX_Z,               // accelerometer z value on y axis
							"Z");         // Set the display title of the series
		}else{
			accelXSeries = new SimpleXYSeries(
							list_Accel_Time,            // Time on x axis
							list_Accel_X,               // accelerometer x value on y axis
							"X");         // Set the display title of the series
			accelYSeries = new SimpleXYSeries(
							list_Accel_Time,            // Time on x axis
							list_Accel_Y,               // accelerometer y value on y axis
							"Y");         // Set the display title of the series
			accelZSeries = new SimpleXYSeries(
							list_Accel_Time,            // Time on x axis
							list_Accel_Z,               // accelerometer z value on y axis
							"Z");         // Set the display title of the series
		}
		// Add series
		dynamicPlot.addSeries(accelXSeries, series1Format);
		dynamicPlot.addSeries(accelYSeries, series2Format);
		dynamicPlot.addSeries(accelZSeries, series3Format);

		if(isSimulation){
			data.startThread();
		}else{
			dynamicPlot.redraw();
		}
	}

	public void plotBarometerData(View view, boolean isSimulation) {

		String fileName = spinnerBarometer.getSelectedItem().toString();
		File barometerFile = new File(DIRECTORYNAME_DATACOLLECTOR, fileName);

		prepareBarometerDataLists(barometerFile);
		if (isSimulation) {
			preparePlot(-1.0, -1.0);
		} else {
			preparePlot(null, null);
		}

		Log.v("plotBarometerData", "Plotting barometer data...");
		dynamicPlot.clear();
		dynamicPlot.setTitle("Barometer Data");

		// Create a formatter to use for drawing a series using LineAndPointRenderer
		// and configure it from xml:
		LineAndPointFormatter series1Format = new LineAndPointFormatter();
		series1Format.setPointLabelFormatter(new PointLabelFormatter());
		series1Format.configure(getApplicationContext(),
						R.xml.line_point_formatter_with_plf1);


		XYSeries baroHeightSeries;
		if (isSimulation){
			baroHeightSeries = new DynamicSeries(
							data,
							SERIES_INDEX_HEIGHT,
							"Height");
		}else{
			baroHeightSeries = new SimpleXYSeries(
							list_Baro_Time,
							list_Baro_Height,
							"Height");
		}

		// Add series
		dynamicPlot.addSeries(baroHeightSeries, series1Format);

		if(isSimulation){
			data.startThread();
		}else{
			dynamicPlot.redraw();
		}
	}

	public void plotGyroscopeData(View view, boolean isSimulation) {

		String fileName = spinnerGyroscope.getSelectedItem().toString();
		File gyroscopeFile = new File(DIRECTORYNAME_DATACOLLECTOR, fileName);

		prepareGyroscopeDataLists(gyroscopeFile);

		if (isSimulation) {
			preparePlot(-1.0, -1.0);
		} else {
			preparePlot(null, null);
		}

		Log.v("plotGyroscopeData", "Plotting gyroscope data...");
		dynamicPlot.clear();
		dynamicPlot.setTitle("Gyroscope Data");

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

		XYSeries gyroXSeries;
		XYSeries gyroYSeries;
		XYSeries gyroZSeries;

		if(isSimulation) {
			gyroXSeries = new DynamicSeries(data,
							SERIES_INDEX_X,
							"X");
			gyroYSeries = new DynamicSeries(data,
							SERIES_INDEX_Y,
							"Y");
			gyroZSeries = new DynamicSeries(data,
							SERIES_INDEX_Z,
							"Z");
		}else{
			gyroXSeries = new SimpleXYSeries(list_Gyro_Time,
							list_Gyro_X,
							"X");
			gyroYSeries = new SimpleXYSeries(list_Gyro_Time,
							list_Gyro_Y,
							"Y");
			gyroZSeries = new SimpleXYSeries(list_Gyro_Time,
							list_Gyro_Z,
							"Z");
		}

		// Add series
		dynamicPlot.addSeries(gyroXSeries, series1Format);
		dynamicPlot.addSeries(gyroYSeries, series2Format);
		dynamicPlot.addSeries(gyroZSeries, series3Format);

		if(isSimulation){
			data.startThread();
		}else{
			dynamicPlot.redraw();
		}
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

	// negative denotes start of range (effectively, no input specified)
	private double getPlotStartDomain() {
		String startRangeStr = ((EditText) findViewById(R.id.plotStartRange)).getText().toString();
		double startRange;
		if (startRangeStr == null || startRangeStr.isEmpty()) {
			startRange = -1.0; // not specified, start from the start
		} else {
			startRange = Double.parseDouble(startRangeStr);
		}
		Log.v("getPlotStartDomain()", "startRange = " + startRange);
		return startRange;
	}

	// Negative end range denotes, effectively, no input
	private double getPlotEndDomain() {
		String endRangeStr = ((EditText) findViewById(R.id.plotEndRange)).getText().toString();
		double endRange;
		if (endRangeStr == null || endRangeStr.isEmpty()) {
			endRange = -1.0; // negative to denote that we should plot to the end of the data
		} else {
			endRange = Double.parseDouble(endRangeStr);
		}
		Log.v("getPlotEndDomain()", "endRange = " + endRange);
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

	private void preparePlot(Double start, Double end) {
		double startRange;
		double endRange;

		if(start != null){
			startRange = start;
		}else {
			startRange = getPlotStartDomain();
		}

		if(end != null){
			endRange = end;
		}else {
			endRange = getPlotEndDomain();
		}

		if (startRange < 0.0) {
			startRange = 0.0;
		}
		dynamicPlot.setDomainLeftMin(startRange);
		if (endRange < 0.0) {
			dynamicPlot.setDomainRightMax(999999999);
		} else {
			dynamicPlot.setDomainRightMax(endRange);
		}
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

	@Override
	public void onClick(View v) {
		data.pauseThread();

		switch (v.getId()) {
			case R.id.accelerometerStopButton:
			case R.id.barometerStopButton:
			case R.id.gyroscopeStopButton: {
				break;
			}
			default: {
				list_Accel_Time.clear();
				list_Accel_X.clear();
				list_Accel_Y.clear();
				list_Accel_Z.clear();

				list_Baro_Time.clear();
				list_Baro_Millibar.clear();
				list_Baro_Height.clear();

				list_Gyro_Time.clear();
				list_Gyro_X.clear();
				list_Gyro_Y.clear();
				list_Gyro_Z.clear();
				break;
			}
		}
		switch (v.getId()) {
			case R.id.barometerStartButton:{
				plotBarometerData(v ,true);
				break;
			}
			case R.id.gyroscopeStartButton:{
				plotGyroscopeData(v, true);
				break;
			}
			case R.id.accelerometerStartButton:{
				plotAccelerometerData(v, true);
				break;
			}
			case R.id.accelerometerStopButton:
			case R.id.barometerStopButton:
			case R.id.gyroscopeStopButton: {
				data.pauseThread();
				break;
			}
			case R.id.accelerometerButton: {
				plotAccelerometerData(v, false);
				break;
			}
			case R.id.gyroscopeButton: {
				plotGyroscopeData(v, false);
				break;
			}
			case R.id.barometerButton: {
				plotBarometerData(v ,false);
				break;
			}
			default:
				break;
		}
	}

	@Override
	public void onResume() {
		// kick off the data generating thread:
		myThread = new Thread(data);
		myThread.start();
		super.onResume();
	}

	@Override
	public void onPause() {
		data.stopThread();
		super.onPause();
	}

	class MonitorData implements Runnable {

		// encapsulates management of the observers watching this datasource for update events:
		class MyObservable extends Observable {
			@Override
			public void notifyObservers() {
				setChanged();
				super.notifyObservers();
			}
		}

		private int readingCount = 0;
		private MyObservable notifier;
		private boolean keepRunning = false;
		private boolean isPause = false;
		private long interval = 0;

		public MonitorData(){
			notifier = new MyObservable();
			keepRunning = false;
			isPause = false;
			readingCount = 0;
		}

		public void stopThread() {
			isPause = true;
			keepRunning = false;
			Log.v("Thread","stop");
		}

		public void pauseThread() {
			android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
			isPause = true;
			Log.v("Thread","pause");
		}

		public void startThread(){
			readingCount = 0;
			keepRunning = true;
			isPause = false;
			android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
			Log.v("Thread","start");
		}

		// @Override
		public void run() {
			try {
				keepRunning = true;
				while (keepRunning) {
					Thread.sleep(interval); // decrease or remove to speed up the refresh rate.
					if (!isPause) {
						readingCount++;
						Double previousSec = 0.0;
						Double currentSec = 0.2;
						if(list_Accel_Time.size()>0 && list_Accel_Time.size() > readingCount){

							previousSec = list_Accel_Time.get(readingCount-1);
							currentSec = list_Accel_Time.get(readingCount);

						}else if(list_Baro_Time.size()>0 && list_Baro_Time.size() > readingCount){

							previousSec = list_Baro_Time.get(readingCount-1);
							currentSec = list_Baro_Time.get(readingCount);

						}else if(list_Gyro_Time.size()>0 && list_Gyro_Time.size() > readingCount){

							previousSec = list_Gyro_Time.get(readingCount-1);
							currentSec = list_Gyro_Time.get(readingCount);

						}

						interval = (int) ((currentSec - previousSec) * 1000.0);
						Log.v("Count", "Count " + readingCount+" interval "+interval);
					}
					notifier.notifyObservers();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			Log.v("Count", "Count end");
		}

		public int getItemCount(int series) {
			int max;
			try{
				max = Integer.parseInt(maxElements.getText().toString());
			}catch (NumberFormatException nfe){
				max = 100;
			}

			return Math.min(readingCount, Math.min(150,max));
		}

		private int getListSize(int series){
			switch(series){
				case SERIES_INDEX_X:
					return Math.max(list_Accel_X.size(), list_Gyro_X.size());
				case SERIES_INDEX_Y:
					return Math.max(list_Accel_Y.size(), list_Gyro_Y.size());
				case SERIES_INDEX_Z:
					return Math.max(list_Accel_Z.size(), list_Gyro_Z.size());
				case SERIES_INDEX_HEIGHT:
					return list_Baro_Height.size();
				default:
					return 0;
			}
		}

		public Number getX(int series, int index) {

			if(readingCount >= getListSize(series)) {
				Log.v("getX","readingCount "+readingCount+" exceed "+getListSize(series));
				data.pauseThread();
			}

			int plotableIndex = readingCount - getItemCount(series) + index;

			if(list_Accel_Time.size() > 0) {
				return list_Accel_Time.get(plotableIndex);
			}else if(list_Gyro_Time.size() > 0){
				return list_Gyro_Time.get(plotableIndex);
			}else{
				return list_Baro_Time.get(plotableIndex);
			}
		}

		public Number getY(int series, int index) {

			if(readingCount >= getListSize(series)) {
				Log.v("getY","readingCount "+readingCount+" exceed "+getListSize(series));
				data.pauseThread();
			}

			int plotableIndex = readingCount - getItemCount(series) + index;
			switch(series){
				case SERIES_INDEX_X:
					if(list_Accel_X.size() > 0){
						return list_Accel_X.get(plotableIndex);
					}else{
						return list_Gyro_X.get(plotableIndex);
					}
				case SERIES_INDEX_Y:
					if(list_Accel_Y.size() > 0){
						return list_Accel_Y.get(plotableIndex);
					}else{
						return list_Gyro_Y.get(plotableIndex);
					}
				case SERIES_INDEX_Z:
					if(list_Accel_Z.size() > 0){
						return list_Accel_Z.get(plotableIndex);
					}else{
						return list_Gyro_Z.get(plotableIndex);
					}
				case SERIES_INDEX_HEIGHT:
					if(list_Baro_Height.size() > 0){
						return list_Baro_Height.get(plotableIndex);
					}else{
						return 0;
					}
				default:
					throw new IllegalArgumentException();
			}
		}

		public void addObserver(Observer observer) {
			notifier.addObserver(observer);
		}

		public void removeObserver(Observer observer) {
			notifier.deleteObserver(observer);
		}

	}

	class DynamicSeries implements XYSeries {
		private MonitorData datasource;
		private int seriesIndex;
		private String title;

		public DynamicSeries(MonitorData datasource, int seriesIndex, String title) {
			this.datasource = datasource;
			this.seriesIndex = seriesIndex;
			this.title = title;
		}

		@Override
		public String getTitle() {
			return title;
		}

		@Override
		public int size() {
			return datasource.getItemCount(seriesIndex);
		}

		@Override
		public Number getX(int index) {
//			Log.v("getX ", "getX "+datasource.getX(seriesIndex, index));
			return datasource.getX(seriesIndex, index);
		}

		@Override
		public Number getY(int index) {
//			Log.v("getY ", "getY "+datasource.getY(seriesIndex, index));
			return datasource.getY(seriesIndex, index);
		}
	}
	public void simplePlot(View v) {
		Intent intent = new Intent(this, SimpleXYPlotActivity.class);
		startActivity(intent);
		finish();
	}
}