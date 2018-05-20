package ps.uiet.chd.sensortasks;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.IOException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, View.OnLongClickListener
{
    static String kernel = "Ham Polynomial Kernel Exponent 3 C 100.0 94.0.model";
    boolean permissionCheckPassed = false;
    boolean ServiceStarted = false;
    RelativeLayout MainActivityLayout;
    Button Accelerometer,AccelerometerService,wekaButton;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Accelerometer = findViewById(R.id.AccelerometerButton);
        Accelerometer.setOnClickListener(this);
        AccelerometerService = findViewById(R.id.AccelerometerServiceButton);
        AccelerometerService.setOnClickListener(this);
        AccelerometerService.setOnLongClickListener(this);
        wekaButton = findViewById(R.id.dataCollectionButton);
        wekaButton.setOnClickListener(this);
        MainActivityLayout = findViewById(R.id.MainActivityLayout);
        if(isMyServiceRunning(AccelerometerBackgroundService.class))
        {
            Intent intent = new Intent(this, AccelerometerBackgroundService.class);
            intent.setAction("Stop");
            stopService(intent);
        }
        checkPermissions();
    }


    @Override
    public void onClick(View v)
    {
        switch (v.getId())
        {
            case R.id.AccelerometerButton:
                Intent intent2 = new Intent(getApplicationContext(),Accelerometer.class);
                startActivity(intent2);
                break;
            case R.id.AccelerometerServiceButton:
                if(!ServiceStarted && permissionCheckPassed)
                {
                    Intent intent = new Intent(this, AccelerometerBackgroundService.class);
                    intent.setAction("Start");
                    intent.putExtra("Kernel",kernel);
                    startService(intent);
                    AccelerometerService.setText("Stop accelerometer\nService");
                    ServiceStarted = true;
                    finish();
                }
                else
                {
                    Intent intent = new Intent(this, AccelerometerBackgroundService.class);
                    intent.setAction("Stop");
                    stopService(intent);
                    AccelerometerService.setText("Start accelerometer\nService");
                    ServiceStarted = false;
                }
                break;
            case R.id.dataCollectionButton:
                if(permissionCheckPassed)
                {
                    Intent intent3 = new Intent(getApplicationContext(),dataCollection.class);
                    startActivity(intent3);
                    finish();
                }
                break;
            default:
                break;
        }
    }

    private boolean isMyServiceRunning(Class<?> serviceClass)
    {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        assert manager != null;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onLongClick(View v)
    {
        switch (v.getId())
        {
            case R.id.AccelerometerServiceButton:
                if(permissionCheckPassed)createKernelChooser();
                break;
            default:
                break;
        }
        return false;
    }

    public void createKernelChooser()
    {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        @SuppressLint("InflateParams") View dialogView = inflater.inflate(R.layout.kernel_chooser_alertdialog, null);
        dialogBuilder.setView(dialogView);
        dialogBuilder.setTitle("Pick a kernel");
        dialogBuilder.setCancelable(false);
        final RadioGroup radioGroup = dialogView.findViewById(R.id.kernelRG);
        int count = 0;
        try
        {
            String[] kernelNames = getAssets().list("");
            for(String kernel : kernelNames)
            {
                RadioButton radioButton = new RadioButton(this);
                radioButton.setText(kernel);
                radioButton.setId(count);
                radioGroup.addView(radioButton);
                if(count==3)radioButton.setChecked(true);
                count++;
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        dialogBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int id)
            {
                int radioButtonID = radioGroup.getCheckedRadioButtonId();
                RadioButton radioButton = radioGroup.findViewById(radioButtonID);
                kernel = radioButton.getText().toString();
                Toast.makeText(getApplicationContext(),kernel,Toast.LENGTH_SHORT).show();
            }
        });
        AlertDialog alertDialog = dialogBuilder.create();
        alertDialog.show();
    }

    private void checkPermissions()
    {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) + ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) + ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
        {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO))
            {
                Snackbar.make(MainActivityLayout, "Please grant the required permissions", Snackbar.LENGTH_INDEFINITE).setAction("ENABLE",
                        new View.OnClickListener()
                        {
                            @Override
                            public void onClick(View v)
                            {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                {
                                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO}, 1);
                                }
                            }
                        }).show();
            }
            else
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO}, 1);
                }
            }
        }
        else permissionCheckPassed = true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode)
        {
            case 1:
                if (grantResults.length > 0)
                {
                    boolean writeExternalStorage = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean accessFineLocation = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                    boolean recordAudio = grantResults[2] == PackageManager.PERMISSION_GRANTED;

                    if(writeExternalStorage && accessFineLocation && recordAudio) permissionCheckPassed = true;
                    else
                    {
                        Snackbar.make(MainActivityLayout, "Please grant the required permissions", Snackbar.LENGTH_INDEFINITE).setAction("ENABLE",
                                new View.OnClickListener()
                                {
                                    @Override
                                    public void onClick(View v)
                                    {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                        {
                                            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO}, 1);
                                        }
                                    }
                                }).show();
                    }
                }
                break;
        }
    }
}
