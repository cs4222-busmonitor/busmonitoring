package busmonitoring.cs4222.nus.busmonitoring;

import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;

import nus.context.fumapp.api.FumAppContextEvent;
import nus.context.fumapp.api.FumAppContextListener;
import nus.context.fumapp.api.FumAppInterface;
import nus.context.fumapp.api.FumAppProxy;
import nus.context.fumapp.api.FumAppServiceListener;


public class InfoActivity extends ActionBarActivity {

    /*
     * Private variables
     */
    /** Text View (displays real-time context). */
    private static TextView textView_Context;
    /** Button to get current Google context. */
    private static Button button_Google;
    /** Button to get current Baro context. */
    private static Button button_Baro;

    /** Context detection API. */
    private FumAppInterface contextApi;

    /** Current Google context. */
    private int currentGoogleContext;
    /** Current Baro context. */
    private int currentBaroContext;
    /** Current Fused (Baro + Google) context. */
    private int currentFusedContext;

    /** Timestamp of latest Google context event. */
    private long googleTimestamp;
    /** Timestamp of latest Baro context event. */
    private long baroTimestamp;
    /** Timestamp of latest 'fused' context event. */
    private long fusedTimestamp;

    /** Handler to the main thread to do UI stuff. */
    private Handler handler;

    /** Name of barometer context provider. */
    private static final String BARO_PROVIDER = "baro";
    /** Name of Google context provider. */
    private static final String GOOGLE_PROVIDER = "google";

    /** DDMS Log Tag. */
    private static final String TAG = "ContextApp";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {

            // Specify what GUI to use
            setContentView(R.layout.activity_info);
            // Set a handler to the current UI thread
            handler = new Handler();

            // Start the context service
            contextApi = new FumAppProxy( getApplicationContext() );
            contextApi.start( new MyStartListener() );

            // Initialise GUI
            initialiseGUI();
        }
        catch ( Exception e ) {
            // Log the exception
            Log.e(TAG, "Exception in onCreate() while starting context service", e);
            // Inform the user
            createToast ( "Exception in onCreate() while starting context service, check log" );

        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_info, menu);
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


    /** Helper method to initialise GUI. */
    private void initialiseGUI() throws Exception {

        // Get references to the GUI widgets
        textView_Context = (TextView) findViewById ( R.id.textRouteInfoId );
        button_Baro = (Button) findViewById ( R.id.btnBaroInfoId );
        button_Google = (Button) findViewById ( R.id.btnGoogleContextInfoId );

        // Set the button's click listener
        button_Baro.setOnClickListener( new MyButtonListener( BARO_PROVIDER ) );
        button_Google.setOnClickListener( new MyButtonListener( GOOGLE_PROVIDER ) );

        // Initialise the text view
        currentGoogleContext = FumAppContextEvent.UNKNOWN;
        currentBaroContext = FumAppContextEvent.IDLE;
        currentFusedContext = FumAppContextEvent.IDLE;
        googleTimestamp =
                baroTimestamp =
                        fusedTimestamp = System.currentTimeMillis();
        updateContextTextView();
    }

    /** Listener called when Context service is started. */
    private class MyStartListener
            implements FumAppServiceListener {

        /** Called when the context service is started. */
        public void onFumAppStarted( boolean success ) {
            try {
                // Check if the context service failed to start
                if( ! success ) {
                    Log.e ( TAG , "Context service failed to start" );
                    createToast ( "Context service failed to start" );
                    return;
                }

                // Register a listener for Context events from
                //  both "baro" and "google" context providers
                MyContextListener listener = new MyContextListener();
                contextApi.addContextListener( listener , BARO_PROVIDER );
                contextApi.addContextListener( listener , GOOGLE_PROVIDER );
            }
            catch ( Exception e ) {
                // Log the exception
                Log.e ( TAG , "Exception in Context service start listener" , e );
                // Inform the user
                createToast ( "Exception in start listener, check log" );
            }
        }
    }

    /** Listener for Button clicks. */
    private class MyButtonListener
            implements View.OnClickListener {

        /** Constructor specifying the context provider to use. */
        public MyButtonListener( String provider ) {
            this.provider = provider;
        }

        /** Called when the button is clicked. */
        public void onClick ( View v ) {

            // Do processing in a different thread to avoid blocking
            //  the main thread
            Thread clickThread = new Thread() {
                public void run() {

                    try {

                        // Get the current activity of the user
                        //  from the specified provider
                        int context = contextApi.getCurrentContext( provider );

                        // Tell the user the current context
                        createToast ( "User's current activity: " +
                                convertContextToString( context ) );
                    }
                    catch ( Exception e ) {
                        // Log the exception
                        Log.e ( TAG , "Exception while retrieving context" , e );
                        // Inform the user
                        createToast ( "Exception while retrieving context, check log" );
                    }
                }
            };
            clickThread.start();

            // Inform the user
            createToast ( "Getting context from " + provider + " provider..." );
        }

        /** Context Provider to use. */
        private String provider;
    }

    /** Listener for received context events. */
    private class MyContextListener
            implements FumAppContextListener {

        /** {@inheritDoc} */
        public void onContextEvent( String provider ,
                                    FumAppContextEvent event ) {

            try {

                // Update the current context
                if( BARO_PROVIDER.equals( provider ) ) {
                    baroTimestamp = event.getTimestamp();
                    currentBaroContext = event.getContext();
                }
                else if( GOOGLE_PROVIDER.equals( provider ) ) {
                    googleTimestamp = event.getTimestamp();
                    currentGoogleContext = event.getContext();
                }

                // Fuse the google and baro context to get a better result
                fuseGoogleAndBaro( event.getTimestamp() );

                // Update the GUI
                updateContextTextView();
            }
            catch ( Exception e ) {
                // Log the exception
                Log.e ( TAG , "Exception on context event" , e );
                // Tell the user
                createToast ( "Exception on context event, check log" );
            }
        }
    }

    /** Helper method to update the context in the text view. */
    private void updateContextTextView() {

        // Build a string to display to the user
        final StringBuilder sb = new StringBuilder();
        sb.append( "Google (" + convertTimeToString( googleTimestamp ) + ") :\n" );
        sb.append( convertContextToString( currentGoogleContext ) + "\n" );
        sb.append( "Baro (" + convertTimeToString( baroTimestamp ) + ") :\n" );
        sb.append( convertContextToString( currentBaroContext ) + "\n" );
        sb.append( "Fusion (" + convertTimeToString( fusedTimestamp ) + ") :\n" );
        sb.append( convertContextToString( currentFusedContext ) + "\n" );

        // Update the text view in Main UI thread
        /*
        handler.post ( new Runnable() {
            public void run() {
                textView_Context.setText( sb.toString() );
            }
        } );
        */
    }

    /** Helper method to convert a UNIX timestamp to readable string. */
    private String convertTimeToString( long timestamp ) {
        SimpleDateFormat sdf = new SimpleDateFormat( "h-mm-ssa" );
        return sdf.format( new Date( timestamp ) );
    }

    /** Helper method to convert context values to readable strings. */
    private static String convertContextToString( int context ) {
        switch( context ) {
            case FumAppContextEvent.IDLE:
                return "IDLE";
            case FumAppContextEvent.WALKING:
                return "WALKING";
            case FumAppContextEvent.VEHICLE:
                return "VEHICLE";
            default:
                return "UNKNOWN";
        }
    }
    /** Helper method to create toasts. */
    private void createToast ( String toastMessage ) {

        // Use a 'final' local variable, otherwise the compiler will complain
        final String toastMessageFinal = toastMessage;

        // Post a runnable in the Main UI thread
        handler.post ( new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),
                        toastMessageFinal,
                        Toast.LENGTH_SHORT).show();
            }
        } );
    }

    private void fuseGoogleAndBaro( long timestamp ) {

        // Give priority to Google's WALKING
        if( currentGoogleContext == FumAppContextEvent.WALKING ) {
            currentFusedContext = FumAppContextEvent.WALKING;
        }
        // Otherwise, give priority to Barometer's context
        else {
            currentFusedContext = currentBaroContext;
        }

        // Set the fused timestamp
        fusedTimestamp = timestamp;
    }
}
