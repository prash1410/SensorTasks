package ps.uiet.chd.sensortasks;

import android.annotation.SuppressLint;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

public class LocationActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,GoogleApiClient.OnConnectionFailedListener,LocationListener
{
    static float PreviousLatitude = (float) 0.0;
    static long startTime = 0;
    static long stopTime = 0;
    static float distance = 0;
    static float PreviousLongitude = (float) 0.0;
    static int LocationPoints = 0;
    final static int PLAY_SERVICES_RESOLUTION_REQUEST = 1000;
    boolean mRequestingLocationUpdates = false;
    TextView lblLocation;
    Button btnStartLocationUpdates;
    RelativeLayout LocationLayout;
    Location mLastLocation;
    GoogleApiClient mGoogleApiClient;
    LocationRequest mLocationRequest;
    static int UPDATE_INTERVAL = 0; // 5 sec
    static int FATEST_INTERVAL = 0; // 5 sec
    static int DISPLACEMENT = 0; // 5 meters
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);
        getSupportActionBar().setTitle("Geo Location");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        LocationLayout = findViewById(R.id.LocationLayout);
        lblLocation = findViewById(R.id.lblLocation);
        btnStartLocationUpdates = findViewById(R.id.btnLocationUpdates);

        if (checkPlayServices())
        {
            buildGoogleApiClient();
            createLocationRequest();
        }

        btnStartLocationUpdates.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                togglePeriodicLocationUpdates();
            }
        });
    }

    public boolean checkPlayServices()
    {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS)
        {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode))
            {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this, PLAY_SERVICES_RESOLUTION_REQUEST).show();
            }
            else
            {
                Toast.makeText(getApplicationContext(),"This device is not supported.", Toast.LENGTH_LONG).show();
                finish();
            }
            return false;
        }
        return true;
    }

    protected synchronized void buildGoogleApiClient()
    {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API).build();
    }

    protected void createLocationRequest()
    {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FATEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setSmallestDisplacement(DISPLACEMENT);
    }

    public void togglePeriodicLocationUpdates()
    {
        if (!mRequestingLocationUpdates)
        {
            btnStartLocationUpdates.setText("Stop location updates");
            mRequestingLocationUpdates = true;
            startLocationUpdates();
        }
        else
        {
            btnStartLocationUpdates.setText("Start location updates");
            mRequestingLocationUpdates = false;
            stopLocationUpdates();
        }
    }

    @SuppressLint("MissingPermission")
    private void displayLocation()
    {
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null)
        {
            if(LocationPoints==0)startTime = System.currentTimeMillis();
            LocationPoints++;
            float latitude = (float) mLastLocation.getLatitude();
            float longitude = (float) mLastLocation.getLongitude();
            lblLocation.setText(""+latitude+"\n"+longitude);
            if(LocationPoints>1)
            {
                stopTime = System.currentTimeMillis();
                long elapsedTime = (stopTime-startTime)/1000;
                distance = distance + calculateDistance(PreviousLatitude,PreviousLongitude,latitude,longitude);
                float speed = distance/elapsedTime;
                Toast.makeText(getApplicationContext(),""+speed,Toast.LENGTH_LONG).show();
            }
            PreviousLatitude = latitude;
            PreviousLongitude = longitude;
        } else
        {
            lblLocation.setText("(Couldn't get the location. Make sure GPS is enabled)");
        }
    }

    @SuppressLint("MissingPermission")
    public void startLocationUpdates()
    {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    public void stopLocationUpdates()
    {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        if (mGoogleApiClient != null)
        {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        checkPlayServices();
        // Resuming the periodic location updates
        if (mGoogleApiClient.isConnected() && mRequestingLocationUpdates)
        {
            startLocationUpdates();
        }
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        if (mGoogleApiClient.isConnected())
        {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        stopLocationUpdates();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle)
    {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location)
    {
        mLastLocation = location;
        displayLocation();
    }

    public float calculateDistance (float lat_a, float lng_a, float lat_b, float lng_b )
    {
        double earthRadius = 3958.75;
        double latDiff = Math.toRadians(lat_b-lat_a);
        double lngDiff = Math.toRadians(lng_b-lng_a);
        double a = Math.sin(latDiff /2) * Math.sin(latDiff /2) +
                Math.cos(Math.toRadians(lat_a)) * Math.cos(Math.toRadians(lat_b)) *
                        Math.sin(lngDiff /2) * Math.sin(lngDiff /2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double distance = earthRadius * c;

        int meterConversion = 1609;

        return (float) (distance * meterConversion);
    }
}
