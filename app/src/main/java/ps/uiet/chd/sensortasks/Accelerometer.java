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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import au.com.bytecode.opencsv.CSVWriter;

public class Accelerometer extends AppCompatActivity
{
    double[] xMag = new double[64];
    double[] yMag = new double[64];
    double[] zMag = new double[64];

    long tStart, tstop;
    int xZeroCrossings,yZeroCrossings,zZeroCrossings;
    RelativeLayout AccelerometerLayout;
    ArrayList <Double> xMagList = new ArrayList<>();
    ArrayList <Double> yMagList = new ArrayList<>();
    ArrayList <Double> zMagList = new ArrayList<>();
    double xAngleGravity = 0, yAngleGravity = 0,zAngleGravity = 0;
    double gravity[] = new double[3];
    static double initX,initY,initZ;
    static double termX,termY,termZ;
    static String yLinearAcceleration = "";
    static String xLinearAcceleration = "";
    static String zLinearAcceleration = "";
    static int count = 0;
    static int samplingTime = 64;
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
        AccelerometerToggleButton = findViewById(R.id.ToggleAccelerometer);
        AccelerometerLayout = findViewById(R.id.AccelerometerLayout);
        AccelerometerValue = findViewById(R.id.AccelerometerValue);
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

                xMag[count] = x;
                yMag[count] = y;
                zMag[count] = z;

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
                    tStart = System.currentTimeMillis();
                    AccelerometerManager.registerListener(AccelerometerListener, Accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
                    AccelerometerToggleButton.setText("Deactivate Accelerometer");
                    Activate = false;
                }
                else
                {
                    AccelerometerValue.setText("");
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


            double maxValue = Collections.max(observations);
            double cutOff = maxValue*0.95;
            int greaterCounter = 0;
            for(double element:observations) if(element>=cutOff)greaterCounter++;

            tstop = System.currentTimeMillis();
            long elapsedTime = tstop-tStart;
            //AccelerometerValue.setText("Variance: "+variance+"\nX Zero Crossings: "+xZeroCrossings+"\nY Zero Crossings: "+yZeroCrossings+"\nZ Zero Crossings: "+zZeroCrossings+"\n"+greaterCounter+"\n"+elapsedTime);
            writer.append("xAcc: "+xLinearAcceleration+"\n\nyAcc: "+yLinearAcceleration+"\n\nzAcc: "+zLinearAcceleration+"\n\nxGravityAngle: "+xAngleGravity+"\nyGravityAngle: "+yAngleGravity+"\nzGravityAngle: "+zAngleGravity+"\n\n\nX Zero Crossings: "+xZeroCrossings+"\nY Zero Crossings: "+yZeroCrossings+"\nZ Zero Crossings: "+zZeroCrossings);
            writer.flush();
            writer.close();

            double[] imag = new double[]{0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,0};

            writeToCSV();
            double energyX = transformRadix2(xMag,imag);
            double energyY = transformRadix2(yMag,imag);
            double energyZ = transformRadix2(zMag,imag);

            AccelerometerValue.setText("Variance: "+variance+"\n"+greaterCounter+"\n"+elapsedTime+"\n"+xAngleGravity+"\n"+yAngleGravity+"\n"+zAngleGravity);

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

    public void writeToCSV()
    {
        try
        {
            File baseDir = new File(String.valueOf(Environment.getExternalStorageDirectory()));
            File csvFile = new File(baseDir, "/Alarms/Output.csv");
            FileWriter fileWriter = new FileWriter(csvFile,true);
            CSVWriter writer = new CSVWriter(fileWriter);

            for(int i=0;i<xMagList.size();i++)
            {
                String[] data = {""+xMagList.get(i), ""+yMagList.get(i),""+zMagList.get(i)};
                writer.writeNext(data);
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
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


    public double transformRadix2(double[] real, double[] imag)
    {
        int n = real.length;
        if (n != imag.length)
            throw new IllegalArgumentException("Mismatched lengths");
        int levels = 31 - Integer.numberOfLeadingZeros(n);
        if (1 << levels != n)
            throw new IllegalArgumentException("Length is not a power of 2");

        double[] cosTable = new double[n / 2];
        double[] sinTable = new double[n / 2];
        for (int i = 0; i < n / 2; i++) {
            cosTable[i] = Math.cos(2 * Math.PI * i / n);
            sinTable[i] = Math.sin(2 * Math.PI * i / n);
        }

        for (int i = 0; i < n; i++)
        {
            int j = Integer.reverse(i) >>> (32 - levels);
            if (j > i) {
                double temp = real[i];
                real[i] = real[j];
                real[j] = temp;
                temp = imag[i];
                imag[i] = imag[j];
                imag[j] = temp;
            }
        }

        for (int size = 2; size <= n; size *= 2)
        {
            int halfsize = size / 2;
            int tablestep = n / size;
            for (int i = 0; i < n; i += size)
            {
                for (int j = i, k = 0; j < i + halfsize; j++, k += tablestep)
                {
                    int l = j + halfsize;
                    double tpre =  real[l] * cosTable[k] + imag[l] * sinTable[k];
                    double tpim = -real[l] * sinTable[k] + imag[l] * cosTable[k];
                    real[l] = real[j] - tpre;
                    imag[l] = imag[j] - tpim;
                    real[j] += tpre;
                    imag[j] += tpim;
                }
            }
            if (size == n)
                break;
        }

        double energy = 0;

        try
        {
            File baseDir = new File(String.valueOf(Environment.getExternalStorageDirectory()));
            File csvFile = new File(baseDir, "/Alarms/OutputFFT.csv");
            FileWriter fileWriter = new FileWriter(csvFile,true);
            CSVWriter writer = new CSVWriter(fileWriter);

            String[] header = {"Real", "Imaginary"};
            writer.writeNext(header);

            ArrayList<Double> magList = new ArrayList<>(), psdList = new ArrayList<>(), normPSDList = new ArrayList<>();
            double psdSum = 0;

            for(int i=1;i<32;i++)
            {
                double realSqTemp = real[i]*real[i];
                double imagSqTemp = imag[i]*imag[i];
                double tempSum = Math.round((realSqTemp + imagSqTemp)*1000d)/1000d;
                double magnitude = Math.round((Math.sqrt(tempSum))*1000d)/1000d;
                magList.add(magnitude);
                psdList.add(tempSum);
                psdSum = psdSum + tempSum;
                real[i] = Math.round(real[i]*1000d)/1000d;
                imag[i] = Math.round(imag[i]*1000d)/1000d;
                String[] data = {""+real[i], ""+imag[i]};
                writer.writeNext(data);
            }

            for (int i=0; i<psdList.size(); i++)
            {
                double tempNormPSD = Math.round((psdList.get(i)/psdSum)*1000d)/1000d;
                normPSDList.add(tempNormPSD);
            }
            writer.close();
            energy = calculateEntropy(psdList);
            //energy = magList.get(0);
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return energy;
    }

    public double calculateEntropy(ArrayList<Double> normPSDList)
    {
        double entropySum = 0;

        for(double temp:normPSDList)
        {
            double entropyTemp = temp*(Math.log(temp)/Math.log(2));
            entropySum = entropySum + entropyTemp;
        }
        return 0 - entropySum;
    }

    public double calculateEnergy(ArrayList<Double> psdList)
    {
        double energy = 0;
        for(double temp:psdList)
        {
            energy = energy + temp;
        }
        return energy/psdList.size();
    }

}