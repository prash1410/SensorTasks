package ps.uiet.chd.sensortasks;

import android.content.DialogInterface;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.StrictMode;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Accelerometer extends AppCompatActivity
{
    ArrayList <Double> xMagList = new ArrayList<>();
    ArrayList <Double> yMagList = new ArrayList<>();
    ArrayList <Double> zMagList = new ArrayList<>();
    double xAngleGravity,yAngleGravity,zAngleGravity;
    double initAngleY,termAngleY,initAngleX,termAngleX,initAngleZ,termAngleZ;
    double gravity[] = new double[3];
    static double initX,initY,initZ,termX,termY,termZ;
    static String yLinearAcceleration = "";
    static String xLinearAcceleration = "";
    static String zLinearAcceleration = "";
    static double linearAccelerationSum = 0,xlinearAccelerationSum = 0,zlinearAccelerationSum = 0;
    static int count = 0;
    static boolean sendResults = false;
    static String PHP_URL = "http://172.16.176.217/sensor_data.php";
    static int samplingRate = 50000;
    static int samplingTime = 100;
    Boolean Activate = true;
    static double noise = 0;
    String output = "",xMeasure = "",yMeasure = "",zMeasure = "";
    Button AccelerometerToggleButton,CalibrateAccelerometer;
    TextView AccelerometerValue;
    SensorManager AccelerometerManager;
    Sensor Accelerometer;
    SensorEventListener AccelerometerListener;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setTitle("Accelerometer");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.activity_accelerometer);
        StrictMode.ThreadPolicy policy=new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        AccelerometerToggleButton = findViewById(R.id.ToggleAccelerometer);
        AccelerometerValue = findViewById(R.id.AccelerometerValue);
        CalibrateAccelerometer = findViewById(R.id.CalibrateAccelerometer);
        AccelerometerManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        assert AccelerometerManager != null;
        Accelerometer = AccelerometerManager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
        AccelerometerListener = new SensorEventListener()
        {
            @Override
            public void onSensorChanged(SensorEvent event)
            {
                // alpha is calculated as t / (t + dT)
                // with t, the low-pass filter's time-constant
                // and dT, the event delivery rate

                final float alpha = 0.8f;
                double x = event.values[0];
                double y = event.values[1];
                double z = event.values[2];
                double total = (Math.sqrt(x * x + y * y + z * z))-noise;

                gravity[0] = alpha * gravity[0] + (1 - alpha) * x;
                gravity[1] = alpha * gravity[1] + (1 - alpha) * y;
                gravity[2] = alpha * gravity[2] + (1 - alpha) * z;

                if(count==0)
                {
                    initX = Math.round(x*100d)/100d;
                    initY = Math.round(y*100d)/100d;
                    initZ = Math.round(z*100d)/100d;
                    getAngle(initX,initY,initZ,true);
                    findMotionDirection();
                }

                if(count%5==0)
                {
                    x = Math.round((event.values[0]-gravity[0])*100d)/100d;
                    y = Math.round((event.values[1]-gravity[1])*100d)/100d;
                    z = Math.round((event.values[2]-gravity[2])*100d)/100d;
                    if(count==0||count==5||count==10)
                    {
                        x=0.0;
                        z=0.0;
                        y=0.0;
                    }
                    xMagList.add(x);
                    yMagList.add(y);
                    zMagList.add(z);
                    yLinearAcceleration += ""+y+",";
                    xLinearAcceleration += ""+x+",";
                    zLinearAcceleration += ""+z+",";
                    linearAccelerationSum = linearAccelerationSum + y;
                    xlinearAccelerationSum = xlinearAccelerationSum + x;
                    zlinearAccelerationSum = zlinearAccelerationSum + z;
                    AccelerometerValue.setText("x: "+x+"\ny: "+y+"\nz: "+z);
                }

                output += ""+total+",";
                xMeasure += ""+x+"\n";
                yMeasure += ""+y+"\n";
                zMeasure += ""+z+"\n";
                count++;
                if(count==samplingTime)
                {
                    termX = Math.round(x*100d)/100d;
                    termY = Math.round(y*100d)/100d;
                    termZ = Math.round(z*100d)/100d;
                    getAngle(termX,termY,termZ,false);
                    AccelerometerToggleButton.callOnClick();
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {}
        };
        AccelerometerToggleButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if(Activate)
                {
                    xMagList.clear();
                    yMagList.clear();
                    zMagList.clear();
                    linearAccelerationSum = 0;
                    xlinearAccelerationSum = 0;
                    zlinearAccelerationSum = 0;
                    count = 0;
                    gravity[0] = 0;
                    gravity[1] = 0;
                    gravity[2] = 0;
                    output = "";
                    xMeasure = "";
                    yMeasure = "";
                    zMeasure = "";
                    xLinearAcceleration = "";
                    yLinearAcceleration = "";
                    zLinearAcceleration = "";
                    AccelerometerManager.registerListener(AccelerometerListener, Accelerometer, samplingRate);
                    AccelerometerToggleButton.setText("Deactivate Accelerometer");
                    Activate = false;
                }
                else
                {
                    AccelerometerValue.setText("Accelerometer Off");
                    AccelerometerManager.unregisterListener(AccelerometerListener);
                    AccelerometerToggleButton.setText("Activate Accelerometer");
                    writeToFile(output);
                    Activate = true;
                }
            }
        });

        CalibrateAccelerometer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                if(!Activate)
                {
                    AccelerometerValue.setText("Accelerometer Off");
                    AccelerometerManager.unregisterListener(AccelerometerListener);
                    AccelerometerToggleButton.setText("Activate Accelerometer");
                    Activate = true;
                }
                createCalibraterDialog();
            }
        });
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if(!Activate)
        {
            AccelerometerValue.setText("Accelerometer Off");
            AccelerometerManager.unregisterListener(AccelerometerListener);
            AccelerometerToggleButton.setText("Activate Accelerometer");
            Activate = true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                finish();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void createCalibraterDialog()
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(this,R.style.MyDialogTheme);
        String titleText = "Calibrate accelerometer";
        ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(Color.WHITE);
        SpannableStringBuilder ssBuilder = new SpannableStringBuilder(titleText);
        ssBuilder.setSpan(foregroundColorSpan, 0, titleText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        alert.setTitle(ssBuilder);
        LayoutInflater inflater = getLayoutInflater();
        View alertLayout = inflater.inflate(R.layout.dialog_calibrater, null);
        alert.setView(alertLayout);
        alert.setCancelable(false);
        Button Calibrate = alertLayout.findViewById(R.id.calibrateDialogButton);
        final CheckBox checkBox = alertLayout.findViewById(R.id.sendResults);
        final EditText SamplingRate = alertLayout.findViewById(R.id.samplingRate);
        final EditText SamplingTime = alertLayout.findViewById(R.id.samplingTime);
        Calibrate.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                final SensorEventListener sensorEventListener = new SensorEventListener() {
                    @Override
                    public void onSensorChanged(SensorEvent event)
                    {
                        float xNoise = event.values[0];
                        float yNoise = event.values[1];
                        float zNoise = event.values[2];
                        noise = Math.sqrt(xNoise * xNoise + yNoise * yNoise + zNoise * zNoise);
                    }

                    @Override
                    public void onAccuracyChanged(Sensor sensor, int i)
                    {

                    }
                };
                AccelerometerManager.registerListener(sensorEventListener, Accelerometer, samplingRate);
            }
        });
        SamplingRate.setText(""+samplingRate);
        SamplingTime.setText(""+samplingTime);
        alert.setPositiveButton("OK", new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                samplingRate = Integer.valueOf(SamplingRate.getText().toString());
                samplingTime = Integer.valueOf(SamplingTime.getText().toString());
                sendResults = checkBox.isChecked();
            }
        });
        AlertDialog dialog = alert.create();
        dialog.show();
    }

    public void writeToFile(String data)
    {
        try
        {
            File root = new File(String.valueOf(Environment.getExternalStorageDirectory()));
            File gpxfile = new File(root, "/Alarms/output"+System.currentTimeMillis()+".txt");
            FileWriter writer = new FileWriter(gpxfile,false);
            ArrayList<Double> observations = new ArrayList<>();
            double sum = 0.0;
            String tempOutput[] = output.split(",");
            for(String temp:tempOutput)
            {
                if(!temp.isEmpty())
                {
                    observations.add(Double.valueOf(temp));
                    sum = sum + Double.valueOf(temp);
                }
            }
            double mean = sum/observations.size();
            double varianceSum = 0.0;
            for(double temp:observations)
            {
                double temp1 = (temp-mean)*(temp-mean);
                varianceSum = varianceSum+temp1;
            }
            double variance = varianceSum/observations.size();
            variance = (double)Math.round(variance * 100d) / 100d;
            writer.write(data);
            writer.append("\n\nSum: "+sum+"\nCount: "+observations.size()+"\nAverage: "+mean+"\nVariance: "+variance+"\n\n\n");

            double averageLinearAcceleration = (double)Math.round((linearAccelerationSum/20) * 100d) / 100d;
            double xaverageLinearAcceleration = (double)Math.round((xlinearAccelerationSum/20) * 100d) / 100d;
            double zaverageLinearAcceleration = (double)Math.round((zlinearAccelerationSum/20) * 100d) / 100d;

            double angleDifferenceY = termAngleY - initAngleY;
            double angleDifferenceX = termAngleX - initAngleX;
            double angleDifferenceZ = termAngleZ - initAngleZ;
            if(angleDifferenceY<0)angleDifferenceY = 0 - angleDifferenceY;
            if(angleDifferenceX<0)angleDifferenceX = 0 - angleDifferenceX;
            if(angleDifferenceZ<0)angleDifferenceZ = 0 - angleDifferenceZ;
            AccelerometerValue.setText("Avg X Acc: "+xaverageLinearAcceleration+"\nAvg Y Acc: "+averageLinearAcceleration+"\nAvg Z Acc: "+zaverageLinearAcceleration+"\nVariance: "+variance+"\nAngleX: "+angleDifferenceX+"\nAngleY: "+angleDifferenceY+"\nAngleZ: "+angleDifferenceZ);
            writer.append("\nX Average: "+xaverageLinearAcceleration+"\nY Average: "+averageLinearAcceleration+"\nZ Average: "+zaverageLinearAcceleration+"\n\nInit: "+initX+","+initY+","+initZ+"\nTerm: " + termX + "," + termY + "," + termZ+"\n\nxAngle: "+angleDifferenceX+"\nyAngle: "+angleDifferenceY+"\nzAngle: "+angleDifferenceZ+"\n\n\nxAcc: "+xLinearAcceleration+"\nxMax: "+Collections.max(xMagList)+" xMin: "+Collections.min(xMagList)+"\n\nyAcc: "+yLinearAcceleration+"\nyMax: "+Collections.max(yMagList)+" yMin: "+Collections.min(yMagList)+"\n\nzAcc: "+zLinearAcceleration+"\nzMax: "+Collections.max(zMagList)+" zMin: "+Collections.min(zMagList)+"\n\nxGravityAngle: "+xAngleGravity+"\nyGravityAngle: "+yAngleGravity+"\nzGravityAngle: "+zAngleGravity);
            writer.flush();
            writer.close();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        if(sendResults)sendData(output);
    }


    public void sendData(String data)
    {
        HttpClient httpClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(PHP_URL);
        List<NameValuePair> nameValuePairs = new ArrayList<>(1);
        nameValuePairs.add(new BasicNameValuePair("accelerometer_data", data));
        try
        {
            httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            httpClient.execute(httpPost);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void findMotionDirection()
    {
        double tempInitX = initX,tempInitY=initY,tempInitZ=initZ;
        if(tempInitX<0)tempInitX=0-tempInitX;
        if(tempInitY<0)tempInitY=0-tempInitY;
        if(tempInitZ<0)tempInitZ=0-tempInitZ;
        xAngleGravity = Math.round(((Math.acos(tempInitX/9.8)*180.0d)/Math.PI)*100d)/100d;
        yAngleGravity = Math.round(((Math.acos(tempInitY/9.8)*180.0d)/Math.PI)*100d)/100d;
        zAngleGravity = Math.round(((Math.acos(tempInitZ/9.8)*180.0d)/Math.PI)*100d)/100d;
    }

    public void getAngle(double x, double y, double z, boolean init)
    {
        double tempY = Math.round(((Math.asin(y/9.8)*180.0d)/Math.PI)*100d)/100d;
        double tempZ = Math.round(((Math.asin(x/9.8)*180.0d)/Math.PI)*100d)/100d;
        double tempX = Math.round(((Math.asin(z/9.8)*180.0d)/Math.PI)*100d)/100d;
        if(init)
        {
            initAngleX = tempX;
            initAngleY = tempY;
            initAngleZ = tempZ;
        }
        else
        {
            termAngleX = tempX;
            termAngleY = tempY;
            termAngleZ = tempZ;
        }
    }

    public void computeResultant()
    {
        ArrayList<Double> angleArrayList = new ArrayList<>();
        angleArrayList.add(xAngleGravity);
        angleArrayList.add(yAngleGravity);
        angleArrayList.add(zAngleGravity);
        int gravityAxis = -1;
        for(int i=0;i<angleArrayList.size();i++)
        {
            if(angleArrayList.get(i)<20)
            {
                gravityAxis = i;
            }
        }

        if(gravityAxis!=-1)
        {

        }
    }
}