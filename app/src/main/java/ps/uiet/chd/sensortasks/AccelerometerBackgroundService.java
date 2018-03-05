package ps.uiet.chd.sensortasks;

import android.app.Service;
import android.content.Intent;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import au.com.bytecode.opencsv.CSVWriter;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

public class AccelerometerBackgroundService extends Service
{
    static Classifier classifier = null;
    int xZeroCrossings,yZeroCrossings,zZeroCrossings;
    static String activityLogs = "";
    ArrayList<Double> xMagList = new ArrayList<>();
    ArrayList <Double> yMagList = new ArrayList<>();
    ArrayList <Double> zMagList = new ArrayList<>();
    double xAngleGravity = 0,yAngleGravity = 0,zAngleGravity = 0;
    double gravity[] = new double[3];
    double initX,initY,initZ;
    String yLinearAcceleration = "";
    String xLinearAcceleration = "";
    String zLinearAcceleration = "";
    int count = 0;
    int samplingTime = 30;
    String output = "",xMeasure = "",yMeasure = "",zMeasure = "";

    SensorManager AccelerometerManager;
    Sensor Accelerometer;
    SensorEventListener AccelerometerListener;

    public Handler handler = null;
    public Runnable runnable = null;
    @Override
    public void onCreate()
    {
        super.onCreate();
        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show();
        handler = new Handler();
        runnable = new Runnable()
        {
            public void run()
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
                startAccelerometer();
                handler.postDelayed(runnable, 7000);
            }
        };
        handler.postDelayed(runnable, 0);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        return Service.START_STICKY_COMPATIBILITY;
    }

    @Override
    public void onDestroy()
    {
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
        if(AccelerometerManager!=null&&AccelerometerListener!=null)AccelerometerManager.unregisterListener(AccelerometerListener);
        try
        {
            File logs = new File(String.valueOf(Environment.getExternalStorageDirectory()));
            File logsFile = new File(logs, "/Alarms/Activity Log.txt");
            FileWriter fileWriter = new FileWriter(logsFile,true);
            fileWriter.write(activityLogs);
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        activityLogs = "";
        handler.removeCallbacksAndMessages(null);
    }

    public void startAccelerometer()
    {
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

                output += ""+total+",";
                xMeasure += ""+x+"\n";
                yMeasure += ""+y+"\n";
                zMeasure += ""+z+"\n";
                count++;
                if(count==samplingTime)
                {
                    AccelerometerManager.unregisterListener(AccelerometerListener);
                    writeToFile(output);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {}
        };
        AccelerometerManager.registerListener(AccelerometerListener, Accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
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


    public void writeToCSV(double variance)
    {
        try
        {
            File baseDir = new File(String.valueOf(Environment.getExternalStorageDirectory()));
            File csvFile = new File(baseDir, "/Alarms/Output.csv");
            FileWriter fileWriter = new FileWriter(csvFile,true);
            CSVWriter writer = new CSVWriter(fileWriter);
            String[] data = {""+variance, ""+xZeroCrossings,""+yZeroCrossings,""+zZeroCrossings};

            /*
            String result = "Inconclusive";
            int predictionResult = SVC.main((""+variance+","+xZeroCrossings+","+yZeroCrossings+","+zZeroCrossings).split(","),assetReader());
            if(predictionResult==0)result = "Driving";
            if(predictionResult==2)result = "Walking";
            if(predictionResult==1)result = "Still";
            Toast.makeText(getApplicationContext(),result,Toast.LENGTH_LONG).show();
            activityLogs += result+"\n";
            */
            String result = wekaPredict(variance, xZeroCrossings,yZeroCrossings,zZeroCrossings);
            Toast.makeText(getApplicationContext(),result,Toast.LENGTH_LONG).show();
            writer.writeNext(data);
            writer.close();
            SimpleDateFormat simpleDateFormat;
            Calendar calender = Calendar.getInstance();
            simpleDateFormat = new SimpleDateFormat("hh:mm:s a");
            String time = simpleDateFormat.format(calender.getTime());
            activityLogs += result+" "+time+"\n";

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

    public String wekaPredict(final double var, final double xZeroCross, final double yZeroCross, final double zZeroCross)
    {
        String result = "Inconclusive";
        if(classifier==null)
        {
            AssetManager assetManager = getAssets();
            try
            {
                classifier = (Classifier) weka.core.SerializationHelper.read(assetManager.open("Latest.model"));
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        if(classifier!=null)
        {
            try
            {

                final Attribute attributeVariance = new Attribute("Variance");
                final Attribute attributeXZeroCrossings = new Attribute("xZeroCrossings");
                final Attribute attributeYZeroCrossings = new Attribute("yZeroCrossings");
                final Attribute attributeZZeroCrossings = new Attribute("zZeroCrossings");
                final List<String> classes = new ArrayList<String>() {
                    {
                        add("Dummy");
                    }
                };

                ArrayList<Attribute> attributeList = new ArrayList<Attribute>()
                {
                    {
                        add(attributeVariance);
                        add(attributeXZeroCrossings);
                        add(attributeYZeroCrossings);
                        add(attributeZZeroCrossings);
                        Attribute attributeClass = new Attribute("Activity", classes);
                        add(attributeClass);
                    }
                };

                Instances dataUnpredicted = new Instances("TestInstances", attributeList, 1);
                dataUnpredicted.setClassIndex(dataUnpredicted.numAttributes() - 1);
                DenseInstance newInstance = new DenseInstance(dataUnpredicted.numAttributes()) {
                    {
                        setValue(attributeVariance, var);
                        setValue(attributeXZeroCrossings, xZeroCross);
                        setValue(attributeYZeroCrossings, yZeroCross);
                        setValue(attributeZZeroCrossings, zZeroCross);
                    }
                };
                newInstance.setDataset(dataUnpredicted);
                double predictedResult = classifier.classifyInstance(newInstance);
                if(predictedResult==0.0)result = "Walking";
                if(predictedResult==1.0)result = "Still";
                if(predictedResult==2.0)result = "Driving";
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        return result;
    }
}
