package ps.uiet.chd.sensortasks;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;

import au.com.bytecode.opencsv.CSVWriter;

public class dataCollectionService extends Service
{
    static String label;
    static String rootDirectory;

    File rawDataFile;
    FileWriter rawDataFileWriter;
    CSVWriter rawDataCSVWriter;

    ArrayList<Double> xMagList = new ArrayList<>();
    ArrayList<Double> yMagList = new ArrayList<>();
    ArrayList<Double> zMagList = new ArrayList<>();
    ArrayList<Double> resultant = new ArrayList<>();
    double xAngleGravity, yAngleGravity, zAngleGravity;
    double gravity[] = new double[3];
    double initX, initY, initZ;
    static int sampleCount;
    boolean accelerometerActive = false;

    SensorManager AccelerometerManager;
    Sensor Accelerometer;
    SensorEventListener AccelerometerListener;

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        createDirectoryIfNotExists();
        Toast.makeText(getApplicationContext(),"",Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        label = intent.getStringExtra("Label");
        if (!accelerometerActive) activateAccelerometer();
        return Service.START_STICKY_COMPATIBILITY;
    }

    public void createDirectoryIfNotExists()
    {
        File csvFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DataCollection");
        if (!csvFolder.exists()) csvFolder.mkdirs();
        rootDirectory = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DataCollection";
    }

    @SuppressLint("SimpleDateFormat")
    public void activateAccelerometer()
    {
        initializeVariables();
        accelerometerActive = true;

        try
        {
            SimpleDateFormat simpleDateFormat;
            Calendar calender = Calendar.getInstance();
            simpleDateFormat = new SimpleDateFormat("hh:mm:s a");
            String time = simpleDateFormat.format(calender.getTime());

            rawDataFile = new File(rootDirectory + "/RawData_"+time+".csv");
            rawDataFileWriter = new FileWriter(rawDataFile, true);
            rawDataCSVWriter = new CSVWriter(rawDataFileWriter);
            String[] data = {"rawX", "rawY", "rawZ", "noGravityX", "noGravityY", "noGravityZ", "resultant", "label"};
            rawDataCSVWriter.writeNext(data);

        } catch (IOException e) {
            e.printStackTrace();
        }

        AccelerometerManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        assert AccelerometerManager != null;
        Accelerometer = AccelerometerManager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
        AccelerometerListener = new SensorEventListener()
        {
            @Override
            public void onSensorChanged(SensorEvent event)
            {
                final float alpha = 0.8f;
                double x = event.values[0];
                double rawX = Math.round(x * 100d) / 100d;
                double y = event.values[1];
                double rawY = Math.round(y * 100d) / 100d;
                double z = event.values[2];
                double rawZ = Math.round(z * 100d) / 100d;
                double total = Math.round((Math.sqrt(x * x + y * y + z * z)) * 100d) / 100d;

                gravity[0] = alpha * gravity[0] + (1 - alpha) * x;
                gravity[1] = alpha * gravity[1] + (1 - alpha) * y;
                gravity[2] = alpha * gravity[2] + (1 - alpha) * z;

                if (sampleCount == 0) {
                    initX = Math.round(x * 100d) / 100d;
                    initY = Math.round(y * 100d) / 100d;
                    initZ = Math.round(z * 100d) / 100d;
                    findMotionDirection();
                }

                x = Math.round((event.values[0] - gravity[0]) * 100d) / 100d;
                y = Math.round((event.values[1] - gravity[1]) * 100d) / 100d;
                z = Math.round((event.values[2] - gravity[2]) * 100d) / 100d;

                x = Math.round((x * Math.sin(Math.toRadians(xAngleGravity))) * 100d) / 100d;
                y = Math.round((y * Math.sin(Math.toRadians(yAngleGravity))) * 100d) / 100d;
                z = Math.round((z * Math.sin(Math.toRadians(zAngleGravity))) * 100d) / 100d;

                if (sampleCount >= 5)
                {
                    xMagList.add(x);
                    yMagList.add(y);
                    zMagList.add(z);
                    resultant.add(total);

                    String[] data = {"" + rawX, "" + rawY, "" + rawZ, "" + x, "" + y, "" + z, ""+total, label};
                    rawDataCSVWriter.writeNext(data);
                }
                if (sampleCount == 63) produceFinalResults();
                if (sampleCount > 64 && (sampleCount + 1) % 64 == 0)
                {
                    trimArrayLists();
                    produceFinalResults();
                }
                sampleCount++;
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
            }
        };
        AccelerometerManager.registerListener(AccelerometerListener, Accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void initializeVariables()
    {
        sampleCount = 0;
        xMagList.clear();
        yMagList.clear();
        zMagList.clear();
        resultant.clear();
    }

    public void findMotionDirection()
    {
        double tempInitX = initX, tempInitY = initY, tempInitZ = initZ;
        if (tempInitX < 0) tempInitX = 0 - tempInitX;
        if (tempInitY < 0) tempInitY = 0 - tempInitY;
        if (tempInitZ < 0) tempInitZ = 0 - tempInitZ;
        xAngleGravity = Math.round(((Math.acos(tempInitX / 9.8) * 180.0d) / Math.PI) * 100d) / 100d;
        yAngleGravity = Math.round(((Math.acos(tempInitY / 9.8) * 180.0d) / Math.PI) * 100d) / 100d;
        zAngleGravity = Math.round(((Math.acos(tempInitZ / 9.8) * 180.0d) / Math.PI) * 100d) / 100d;
    }

    public void deactivateAccelerometer()
    {
        accelerometerActive = false;
        Toast.makeText(getApplicationContext(), "Service stopped", Toast.LENGTH_LONG).show();
        if (AccelerometerManager != null && AccelerometerListener != null)
            AccelerometerManager.unregisterListener(AccelerometerListener);
        try
        {
            rawDataCSVWriter.flush();
            rawDataFileWriter.close();
            rawDataCSVWriter.close();
            rawDataFileWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        initializeVariables();
    }

    @Override
    public void onDestroy()
    {
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
        if (accelerometerActive) deactivateAccelerometer();
    }

    @SuppressLint("SimpleDateFormat")
    public void produceFinalResults()
    {
        double variance = calculateVariance();
        if(variance==0)return;
        try
        {
            File csvFile = new File(rootDirectory + "/Data.csv");
            FileWriter fileWriter = new FileWriter(csvFile, true);
            CSVWriter writer = new CSVWriter(fileWriter);
            String[] data = {"" + variance, "" + getZeroCrossings(xMagList), "" + getZeroCrossings(yMagList), "" + getZeroCrossings(zMagList), "" + getPeaks(), label};
            writer.writeNext(data);
            writer.close();

        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public int getZeroCrossings(ArrayList<Double> magList)
    {
        boolean positive;
        int counter = 5, zeroCrossings = 0;
        positive = magList.get(counter) > 0;
        counter++;
        while (counter < magList.size()) {
            boolean tempPositive = magList.get(counter) > 0;
            if (positive != tempPositive) {
                zeroCrossings++;
                positive = tempPositive;
            }
            counter++;
        }
        return zeroCrossings;
    }

    public double calculateVariance()
    {
        int count = resultant.size();
        double sum = 0;
        for(double tempElement:resultant)sum = sum+tempElement;
        double average = sum / count;
        double squareSum = 0;
        for (double tempElement : resultant)
        {
            double temp = tempElement - average;
            temp = temp * temp;
            squareSum = squareSum + temp;
        }
        return Math.round((squareSum / count) * 100d) / 100d;
    }

    public void trimArrayLists()
    {
        for (int i = 10; i < xMagList.size(); i++) {
            xMagList.set(i - 10, xMagList.get(i));
            yMagList.set(i - 10, yMagList.get(i));
            zMagList.set(i - 10, zMagList.get(i));
            resultant.set(i - 10, resultant.get(i));
        }

        for (int i = 0; i < 10; i++)
        {
            xMagList.remove(30);
            yMagList.remove(30);
            zMagList.remove(30);
            resultant.remove(30);
        }
    }

    public int getPeaks()
    {
        int peakCounter = 0;
        double threshold = Collections.max(resultant) * 0.95;
        for(double element:resultant)if(element>=threshold)peakCounter++;
        return peakCounter;
    }
}
