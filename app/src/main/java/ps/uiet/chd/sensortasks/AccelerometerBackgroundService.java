package ps.uiet.chd.sensortasks;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import au.com.bytecode.opencsv.CSVWriter;

public class AccelerometerBackgroundService extends Service
{
    static String activityLogs = "";
    ArrayList<Double> xMagList = new ArrayList<>();
    ArrayList <Double> yMagList = new ArrayList<>();
    ArrayList <Double> zMagList = new ArrayList<>();
    double xAngleGravity,yAngleGravity,zAngleGravity;
    double initAngleY,termAngleY,initAngleX,termAngleX,initAngleZ,termAngleZ;
    double gravity[] = new double[3];
    double initX,initY,initZ,termX,termY,termZ;
    String yLinearAcceleration = "";
    String xLinearAcceleration = "";
    String zLinearAcceleration = "";
    double linearAccelerationSum = 0,xlinearAccelerationSum = 0,zlinearAccelerationSum = 0;
    int count = 0;
    int samplingRate = 50000;
    int samplingTime = 100;
    String output = "",xMeasure = "",yMeasure = "",zMeasure = "";

    SensorManager AccelerometerManager;
    Sensor Accelerometer;
    SensorEventListener AccelerometerListener;

    public Context context=this;
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
                    AccelerometerManager.unregisterListener(AccelerometerListener);
                    writeToFile(output);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {}
        };
        AccelerometerManager.registerListener(AccelerometerListener, Accelerometer, samplingRate);
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
            writer.append("\nX Average: "+xaverageLinearAcceleration+"\nY Average: "+averageLinearAcceleration+"\nZ Average: "+zaverageLinearAcceleration+"\n\nInit: "+initX+","+initY+","+initZ+"\nTerm: " + termX + "," + termY + "," + termZ+"\n\nxAngle: "+angleDifferenceX+"\nyAngle: "+angleDifferenceY+"\nzAngle: "+angleDifferenceZ+"\n\n\nxAcc: "+xLinearAcceleration+"\nxMax: "+ Collections.max(xMagList)+" xMin: "+Collections.min(xMagList)+"\nxVariance: "+xMagVariance+"\n\nyAcc: "+yLinearAcceleration+"\nyMax: "+Collections.max(yMagList)+" yMin: "+Collections.min(yMagList)+"\nyVariance: "+yMagVariance+"\n\nzAcc: "+zLinearAcceleration+"\nzMax: "+Collections.max(zMagList)+" zMin: "+Collections.min(zMagList)+"\nzVariance: "+zMagVariance+"\n\nxGravityAngle: "+xAngleGravity+"\nyGravityAngle: "+yAngleGravity+"\nzGravityAngle: "+zAngleGravity);
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

    public void predict(double variance,double angleX,double angleY,double angleZ,double maxVariance)
    {
        boolean driving;
        driving = variance < 2 && variance >= 0.05;
        SimpleDateFormat formatDate = new SimpleDateFormat("h:mm:ss a");
        String formattedDate = formatDate.format(new Date()).toString();
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
                Toast.makeText(context,"You're most probably driving",Toast.LENGTH_LONG).show();
                activityLogs += "\nDriving "+formattedDate;
                noneTrue =false;
            }
            if(!driving)
            {
                Toast.makeText(context,"You've most probably just picked up your phone",Toast.LENGTH_LONG).show();
                activityLogs += "\nPickup "+formattedDate;
                noneTrue =false;
            }
            if(noneTrue)
            {
                Toast.makeText(context,"Device moved, but most probably not driving",Toast.LENGTH_LONG).show();
                activityLogs += "\nNot driving "+formattedDate;
            }
        }
        if(variance>=2)
        {
            Toast.makeText(context,"You're most probably walking",Toast.LENGTH_LONG).show();
            activityLogs += "\nWalking "+formattedDate;
        }
        if(variance<0.05)
        {
            int axisCounter = 0;
            if(angleX>15)axisCounter++;
            if(angleY>15)axisCounter++;
            if(angleZ>15)axisCounter++;
            if(axisCounter>0)
            {
                Toast.makeText(context,"You've most probably just picked up your phone",Toast.LENGTH_LONG).show();
                activityLogs += "\nPickup "+formattedDate;
            }
            else
            {
                Toast.makeText(context,"Device has not moved significantly",Toast.LENGTH_LONG).show();
                activityLogs += "\nStill "+formattedDate;
            }
        }
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
