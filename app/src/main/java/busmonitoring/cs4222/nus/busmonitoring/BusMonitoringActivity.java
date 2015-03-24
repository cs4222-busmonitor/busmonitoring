package busmonitoring.cs4222.nus.busmonitoring;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class BusMonitoringActivity extends ActionBarActivity implements AdapterView.OnItemSelectedListener, View.OnClickListener {

    private String messageStarted = "Bus details recorded";

    private Button buttonLoginMessage;
    private Spinner dropdownRoutes;
    private static String dropdownRouteSelected;
    private EditText editNameMessage;
    private EditText editVehicleMessage;

    private String[] busRoutes = {"A1","A1E","A2","A2E","B","BTC1","BTC2","C","D1","D2"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bus_monitoring);

        editNameMessage = (EditText) findViewById(R.id.editNameId);
        editVehicleMessage = (EditText) findViewById(R.id.editVehicleId);
        buttonLoginMessage = (Button) findViewById(R.id.btnStartId);
        buttonLoginMessage.setOnClickListener(this);

        dropdownRouteSelected = "";
        dropdownRoutes = (Spinner) findViewById(R.id.dropdownRoutes);

        // dynamically load bus route options to the dropdown list
        List<String> list = new ArrayList<String>();
        list.addAll(Arrays.asList(busRoutes));
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, list);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dropdownRoutes.setAdapter(dataAdapter);

        // listen to item selection event
        dropdownRoutes.setOnItemSelectedListener(this);
    }

    public void startMonitor(View view){
        Toast.makeText(getApplicationContext(), messageStarted, Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, TabActivity.class);
        intent.putExtra("busDetails", new BusDetails(editNameMessage.getText().toString(),
                                                    editVehicleMessage.getText().toString(),
                                                    dropdownRouteSelected));
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_bus_monitoring, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
        switch(v.getId()){
            case R.id.dropdownRoutes:
                dropdownRouteSelected = parent.getItemAtPosition(position).toString();
                break;
            default:
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.btnStartId:
                startMonitor(v);
                break;
            default:
                break;
        }
    }
}