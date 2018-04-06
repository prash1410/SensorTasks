package ps.uiet.chd.sensortasks;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity implements View.OnClickListener
{
    boolean ServiceStarted = false;
    RelativeLayout MainActivityLayout;
    Button LocationButton,Accelerometer,AccelerometerService,wekaButton;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Accelerometer = findViewById(R.id.AccelerometerButton);
        Accelerometer.setOnClickListener(this);
        LocationButton = findViewById(R.id.LocationButton);
        LocationButton.setOnClickListener(this);
        AccelerometerService = findViewById(R.id.AccelerometerServiceButton);
        AccelerometerService.setOnClickListener(this);
        wekaButton = findViewById(R.id.dataCollectionButton);
        wekaButton.setOnClickListener(this);
        MainActivityLayout = findViewById(R.id.MainActivityLayout);
        ServiceStarted = isMyServiceRunning(AccelerometerBackgroundService.class);
        if(ServiceStarted)AccelerometerService.setText("Stop accelerometer\nservice");
        else AccelerometerService.setText("Start accelerometer\nservice");

    }

    @TargetApi(23)
    private boolean checkRequestLocationPermission(Context context)
    {
        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        else
        {
            return true;
        }
        return false;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults)
    {
        switch (requestCode)
        {
            case 1:
            {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                    {
                        LocationButton.callOnClick();
                    }
                }
                else
                {
                    Snackbar snackbar = Snackbar.make(MainActivityLayout, "Location permission is required for this feature", Snackbar.LENGTH_LONG)
                            .setAction("OK", new View.OnClickListener()
                            {
                                @Override
                                public void onClick(View view)
                                {
                                    checkRequestLocationPermission(getApplicationContext());
                                }
                            });

                    snackbar.show();
                }
            }
        }
    }

    @Override
    public void onClick(View v)
    {
        switch (v.getId())
        {
            case R.id.LocationButton:
                if(checkRequestLocationPermission(getApplicationContext()))
                {
                    if(isGPSEnabled())locationFunction();
                    else showSettingsAlert();
                }
                break;
            case R.id.AccelerometerButton:
                Intent intent2 = new Intent(getApplicationContext(),Accelerometer.class);
                startActivity(intent2);
                break;
            case R.id.AccelerometerServiceButton:
                if(!ServiceStarted)
                {
                    startService(new Intent(this, AccelerometerBackgroundService.class));
                    AccelerometerService.setText("Stop accelerometer\nService");
                    ServiceStarted = true;
                }
                else
                {
                    stopService(new Intent(this, AccelerometerBackgroundService.class));
                    AccelerometerService.setText("Start accelerometer\nService");
                    ServiceStarted = false;
                }
                break;
            case R.id.dataCollectionButton:
                Intent intent3 = new Intent(getApplicationContext(),dataCollection.class);
                startActivity(intent3);
                break;
            default:
                break;
        }
    }

    @SuppressLint("MissingPermission")
    public void locationFunction()
    {
        //gpsTracker = new GPSTracker(MainActivity.this);
        Intent intent = new Intent(getApplicationContext(),LocationActivity.class);
        startActivity(intent);
    }

    public void showSettingsAlert()
    {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle("GPS disabled");
        alertDialog.setMessage("GPS is not enabled. Do you want to go to settings menu?");
        alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog,int which)
            {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                getApplicationContext().startActivity(intent);
            }
        });
        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.cancel();
            }
        });
        alertDialog.show();
    }

    public boolean isGPSEnabled()
    {
        LocationManager manager = (LocationManager) getSystemService( Context.LOCATION_SERVICE );
        assert manager != null;
        return manager.isProviderEnabled( LocationManager.GPS_PROVIDER );
    }


    private boolean isMyServiceRunning(Class<?> serviceClass)
    {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
