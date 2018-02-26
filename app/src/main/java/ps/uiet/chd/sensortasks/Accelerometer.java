package ps.uiet.chd.sensortasks;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Environment;
import android.os.StrictMode;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import au.com.bytecode.opencsv.CSVWriter;

public class Accelerometer extends AppCompatActivity
{
    int xZeroCrossings,yZeroCrossings,zZeroCrossings;
    RelativeLayout AccelerometerLayout;
    ArrayList <Double> xMagList = new ArrayList<>();
    ArrayList <Double> yMagList = new ArrayList<>();
    ArrayList <Double> zMagList = new ArrayList<>();
    double xAngleGravity = 0,yAngleGravity = 0,zAngleGravity = 0;
    double gravity[] = new double[3];
    static double initX,initY,initZ;
    static String yLinearAcceleration = "";
    static String xLinearAcceleration = "";
    static String zLinearAcceleration = "";
    static int count = 0;
    static int samplingTime = 30;
    Boolean Activate = true;
    String output = "",xMeasure = "",yMeasure = "",zMeasure = "";
    Button AccelerometerToggleButton;
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
                double total = (Math.sqrt(x * x + y * y + z * z));
                gravity[0] = alpha * gravity[0] + (1 - alpha) * x;
                gravity[1] = alpha * gravity[1] + (1 - alpha) * y;
                gravity[2] = alpha * gravity[2] + (1 - alpha) * z;

                if(count==0)
                {
                    initX = Math.round(x*100d)/100d;
                    initY = Math.round(y*100d)/100d;
                    initZ = Math.round(z*100d)/100d;
                    findMotionDirection();
                }

                x = Math.round((event.values[0]-gravity[0])*100d)/100d;
                y = Math.round((event.values[1]-gravity[1])*100d)/100d;
                z = Math.round((event.values[2]-gravity[2])*100d)/100d;

                x = Math.round((x*Math.sin(Math.toRadians(xAngleGravity)))*100d)/100d;
                y = Math.round((y*Math.sin(Math.toRadians(yAngleGravity)))*100d)/100d;
                z = Math.round((z*Math.sin(Math.toRadians(zAngleGravity)))*100d)/100d;

                if(count>=0 && count<5)
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
                AccelerometerValue.setText("x: "+x+"\ny: "+y+"\nz: "+z);

                output += ""+total+",";
                xMeasure += ""+x+"\n";
                yMeasure += ""+y+"\n";
                zMeasure += ""+z+"\n";
                count++;
                if(count==samplingTime)
                {
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
                    AccelerometerManager.registerListener(AccelerometerListener, Accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
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

            xZeroCrossings = getZeroCrossings(xMagList);
            yZeroCrossings = getZeroCrossings(yMagList);
            zZeroCrossings = getZeroCrossings(zMagList);

            AccelerometerValue.setText("Variance: "+variance+"\nX Zero Crossings: "+xZeroCrossings+"\nY Zero Crossings: "+yZeroCrossings+"\nZ Zero Crossings: "+zZeroCrossings);
            writer.append("xAcc: "+xLinearAcceleration+"\n\nyAcc: "+yLinearAcceleration+"\n\nzAcc: "+zLinearAcceleration+"\n\nxGravityAngle: "+xAngleGravity+"\nyGravityAngle: "+yAngleGravity+"\nzGravityAngle: "+zAngleGravity+"\n\n\nX Zero Crossings: "+xZeroCrossings+"\nY Zero Crossings: "+yZeroCrossings+"\nZ Zero Crossings: "+zZeroCrossings);
            writer.flush();
            writer.close();
            writeToCSV(variance);
        } catch (IOException e)
        {
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

    public void writeToCSV(double variance)
    {
        try
        {
            File baseDir = new File(String.valueOf(Environment.getExternalStorageDirectory()));
            File csvFile = new File(baseDir, "/Alarms/Output.csv");
            FileWriter fileWriter = new FileWriter(csvFile,true);
            CSVWriter writer = new CSVWriter(fileWriter);
            String[] data = {""+variance, ""+xZeroCrossings,""+yZeroCrossings,""+zZeroCrossings};

            String result = "Inconclusive";
            int predictionResult = SVC.main((""+variance+","+xZeroCrossings+","+yZeroCrossings+","+zZeroCrossings).split(","),assetReader());
            if(predictionResult==0)result = "Driving";
            if(predictionResult==2)result = "Walking";
            if(predictionResult==1)result = "Still";
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

    public double[][] assetReader()
    {
        double[][] vectorsArray = new double[80][4];
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new InputStreamReader(getAssets().open("vectors.txt")));
            String line;
            int linesCount = 0;
            while( (line = reader.readLine() ) != null)
            {
                if(linesCount<80)
                {
                    String tempLine[] = line.split(" ");
                    for(int i=0;i<4;i++)
                    {
                        vectorsArray[linesCount][i] = Double.valueOf(tempLine[i]);
                    }
                }
                linesCount++;
            }
        } catch (IOException ignored)
        {
        } finally
        {
            if (reader != null)
            {try
            {reader.close();} catch (IOException ignored) {}
            }
        }
        return vectorsArray;
    }

    public int getZeroCrossings(ArrayList<Double> magList)
    {
        boolean positive;
        int counter = 5,zeroCrossings = 0;
        positive = magList.get(counter) > 0;
        counter++;
        while(counter<magList.size())
        {
            boolean tempPositive = magList.get(counter)>0;
            if(positive!=tempPositive)
            {
                zeroCrossings++;
                positive = tempPositive;
            }
            counter++;
        }
        return zeroCrossings;
    }
}