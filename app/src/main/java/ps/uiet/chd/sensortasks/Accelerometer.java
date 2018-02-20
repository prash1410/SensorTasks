package ps.uiet.chd.sensortasks;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Environment;
import android.os.StrictMode;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import au.com.bytecode.opencsv.CSVWriter;

public class Accelerometer extends AppCompatActivity
{
    RelativeLayout AccelerometerLayout;
    ArrayList <Double> xMagList = new ArrayList<>();
    ArrayList <Double> yMagList = new ArrayList<>();
    ArrayList <Double> zMagList = new ArrayList<>();
    double xAngleGravity = 0,yAngleGravity = 0,zAngleGravity = 0;
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
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.activity_accelerometer);
        StrictMode.ThreadPolicy policy=new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        AccelerometerToggleButton = (Button)findViewById(R.id.ToggleAccelerometer);
        AccelerometerLayout = (RelativeLayout)findViewById(R.id.AccelerometerLayout);
        AccelerometerValue = (TextView)findViewById(R.id.AccelerometerValue);
        CalibrateAccelerometer = (Button)findViewById(R.id.CalibrateAccelerometer);
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

                    x = Math.round((x*Math.sin(Math.toRadians(xAngleGravity)))*100d)/100d;
                    y = Math.round((y*Math.sin(Math.toRadians(yAngleGravity)))*100d)/100d;
                    z = Math.round((z*Math.sin(Math.toRadians(zAngleGravity)))*100d)/100d;

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
            case R.id.open_file:
                Uri uri = Uri.fromFile(lastFileModified(String.valueOf(Environment.getExternalStorageDirectory())+"/Alarms"));
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "text/plain");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                break;
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
        Button Calibrate = (Button)alertLayout.findViewById(R.id.calibrateDialogButton);
        final CheckBox checkBox = (CheckBox)alertLayout.findViewById(R.id.sendResults);
        final EditText SamplingRate = (EditText)alertLayout.findViewById(R.id.samplingRate);
        final EditText SamplingTime = (EditText)alertLayout.findViewById(R.id.samplingTime);
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

            double angleDifferenceY = (double)Math.round((termAngleY - initAngleY) * 100d) / 100d;
            double angleDifferenceX = (double)Math.round((termAngleX - initAngleX) * 100d) / 100d;
            double angleDifferenceZ = (double)Math.round((termAngleZ - initAngleZ) * 100d) / 100d;
            if(angleDifferenceY<0)angleDifferenceY = 0 - angleDifferenceY;
            if(angleDifferenceX<0)angleDifferenceX = 0 - angleDifferenceX;
            if(angleDifferenceZ<0)angleDifferenceZ = 0 - angleDifferenceZ;

            double xMagVariance,yMagVariance,zMagVariance,magVarianceSum = 0;
            for(double xMag:xMagList)
            {
                double tempMag = (xMag-xaverageLinearAcceleration);
                tempMag = tempMag*tempMag;
                magVarianceSum = magVarianceSum+tempMag;
            }
            xMagVariance = (double)Math.round((magVarianceSum/20) * 100d) / 100d;
            magVarianceSum=0;
            for(double yMag:yMagList)
            {
                double tempMag = (yMag-averageLinearAcceleration);
                tempMag = tempMag*tempMag;
                magVarianceSum = magVarianceSum+tempMag;
            }
            yMagVariance = (double)Math.round((magVarianceSum/20) * 100d) / 100d;
            magVarianceSum=0;
            for(double zMag:zMagList)
            {
                double tempMag = (zMag-zaverageLinearAcceleration);
                tempMag = tempMag*tempMag;
                magVarianceSum = magVarianceSum+tempMag;
            }
            zMagVariance = (double)Math.round((magVarianceSum/20) * 100d) / 100d;
            AccelerometerValue.setText("Var X Acc: "+xMagVariance+"\nVar Y Acc: "+yMagVariance+"\nVar Z Acc: "+zMagVariance+"\nVariance: "+variance+"\nAngleX: "+angleDifferenceX+"\nAngleY: "+angleDifferenceY+"\nAngleZ: "+angleDifferenceZ);
            writer.append("\nX Average: "+xaverageLinearAcceleration+"\nY Average: "+averageLinearAcceleration+"\nZ Average: "+zaverageLinearAcceleration+"\n\nInit: "+initX+","+initY+","+initZ+"\nTerm: " + termX + "," + termY + "," + termZ+"\n\nxAngle: "+angleDifferenceX+"\nyAngle: "+angleDifferenceY+"\nzAngle: "+angleDifferenceZ+"\n\n\nxAcc: "+xLinearAcceleration+"\nxMax: "+Collections.max(xMagList)+" xMin: "+Collections.min(xMagList)+"\nxVariance: "+xMagVariance+"\n\nyAcc: "+yLinearAcceleration+"\nyMax: "+Collections.max(yMagList)+" yMin: "+Collections.min(yMagList)+"\nyVariance: "+yMagVariance+"\n\nzAcc: "+zLinearAcceleration+"\nzMax: "+Collections.max(zMagList)+" zMin: "+Collections.min(zMagList)+"\nzVariance: "+zMagVariance+"\n\nxGravityAngle: "+xAngleGravity+"\nyGravityAngle: "+yAngleGravity+"\nzGravityAngle: "+zAngleGravity);
            writer.flush();
            writer.close();
            ArrayList<Double>varList = new ArrayList<>();
            varList.add(xMagVariance);
            varList.add(yMagVariance);
            varList.add(zMagVariance);
            predict(variance,angleDifferenceX,angleDifferenceY,angleDifferenceZ,computeResultant(varList));
            writeToCSV(variance,angleDifferenceX,angleDifferenceY,angleDifferenceZ);
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
        double tempZ = Math.round(((Math.asin(z/9.8)*180.0d)/Math.PI)*100d)/100d;
        double tempX = Math.round(((Math.asin(x/9.8)*180.0d)/Math.PI)*100d)/100d;
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

    public double computeResultant(ArrayList<Double>varList)
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
            ArrayList<Double>tempList = new ArrayList<>();
            for(int i=0;i<varList.size();i++)
            {
                if(i!=gravityAxis)tempList.add(varList.get(i));
            }
            return Collections.max(tempList);
        }
        else return varList.get(angleArrayList.indexOf(Collections.max(angleArrayList)));
    }

    public void predict(double variance,double angleX,double angleY,double angleZ,double maxVariance)
    {
        boolean driving;
        driving = variance < 2 && variance >= 0.05;

        if(driving)
        {
            boolean noneTrue = true;
            int axisCounter = 0;
            if(angleX>15)axisCounter++;
            if(angleY>15)axisCounter++;
            if(angleZ>15)axisCounter++;
            driving = axisCounter < 1;
            if(driving && maxVariance<0.50)
            {
                /*
                Snackbar snackbar = Snackbar
                        .make(AccelerometerLayout, "You're most probably driving", Snackbar.LENGTH_LONG)
                        .setAction("OK", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {}
                        });

                snackbar.show();
                */
                noneTrue =false;
            }
            if(!driving)
            {
                /*
                Snackbar snackbar = Snackbar
                        .make(AccelerometerLayout, "You've most probably just picked up your phone", Snackbar.LENGTH_LONG)
                        .setAction("OK", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {}
                        });

                snackbar.show();
                */
                noneTrue = false;
            }
            if(noneTrue)
            {
                /*
                Snackbar snackbar = Snackbar
                        .make(AccelerometerLayout, "Device moved, but most probably not driving", Snackbar.LENGTH_LONG)
                        .setAction("OK", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {}
                        });

                snackbar.show();
                */
            }
        }
        if(variance>=2)
        {
            /*
            Snackbar snackbar = Snackbar
                    .make(AccelerometerLayout, "You're most probably walking", Snackbar.LENGTH_LONG)
                    .setAction("OK", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {}
                    });

            snackbar.show();
            */
        }
        if(variance<0.05)
        {
            int axisCounter = 0;
            if(angleX>15)axisCounter++;
            if(angleY>15)axisCounter++;
            if(angleZ>15)axisCounter++;
            if(axisCounter>0)
            {
                /*
                Snackbar snackbar = Snackbar
                        .make(AccelerometerLayout, "You've most probably just picked up your phone", Snackbar.LENGTH_LONG)
                        .setAction("OK", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {}
                        });

                snackbar.show();
                */
            }
            else
            {
                /*
                Snackbar snackbar = Snackbar
                        .make(AccelerometerLayout, "Device has not moved significantly", Snackbar.LENGTH_LONG)
                        .setAction("OK", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {}
                        });

                snackbar.show();
                */
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.open, menu);
        return super.onCreateOptionsMenu(menu);
    }

    public static File lastFileModified(String dir)
    {
        File fl = new File(dir);
        File[] files = fl.listFiles(new FileFilter()
        {
            public boolean accept(File file)
            {
                return file.isFile();
            }
        });
        long lastMod = Long.MIN_VALUE;
        File choice = null;
        for (File file : files) {
            if (file.lastModified() > lastMod) {
                choice = file;
                lastMod = file.lastModified();
            }
        }
        return choice;
    }

    public void writeToCSV(double variance, double xAngleDifference, double yAngleDifference, double zAngleDifference)
    {
        try
        {
            File baseDir = new File(String.valueOf(Environment.getExternalStorageDirectory()));
            File csvFile = new File(baseDir, "/Alarms/Output.csv");
            FileWriter fileWriter = new FileWriter(csvFile,true);
            CSVWriter writer = new CSVWriter(fileWriter);
            StringBuilder varianceString = new StringBuilder("" + variance);
            for(int i=0;i<19;i++)
            {
                varianceString.append(",0");
            }
            StringBuilder angleDifferenceString = new StringBuilder(""+xAngleDifference+","+yAngleDifference+","+zAngleDifference);
            for(int i=0;i<17;i++)
            {
                angleDifferenceString.append(",0");
            }
            String[] data = {varianceString.toString(),angleDifferenceString.toString(), removeLastChar(xLinearAcceleration), removeLastChar(yLinearAcceleration), removeLastChar(zLinearAcceleration)};
            String result = "Inconclusive";
            int predictionResult = SVC.main((varianceString.toString()+","+angleDifferenceString.toString()+","+xLinearAcceleration+yLinearAcceleration+removeLastChar(zLinearAcceleration)).split(","));
            if(predictionResult==0)result = "Driving";
            if(predictionResult==1)result = "Pickup";
            if(predictionResult==2)result = "Walking";
            Snackbar snackbar = Snackbar
                    .make(AccelerometerLayout, result, Snackbar.LENGTH_LONG)
                    .setAction("OK", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {}
                    });

            snackbar.show();
            writer.writeNext(data);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String removeLastChar(String str)
    {
        return str.substring(0, str.length() - 1);
    }
}