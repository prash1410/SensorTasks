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

    ArrayList<Double> xRawMagList = new ArrayList<>();
    ArrayList<Double> yRawMagList = new ArrayList<>();
    ArrayList<Double> zRawMagList = new ArrayList<>();

    ArrayList<Double> xMagList = new ArrayList<>();
    ArrayList<Double> yMagList = new ArrayList<>();
    ArrayList<Double> zMagList = new ArrayList<>();

    ArrayList<Double> xRawMagListFiltered = new ArrayList<>();
    ArrayList<Double> yRawMagListFiltered = new ArrayList<>();
    ArrayList<Double> zRawMagListFiltered = new ArrayList<>();

    ArrayList<Double> xMagListFiltered = new ArrayList<>();
    ArrayList<Double> yMagListFiltered = new ArrayList<>();
    ArrayList<Double> zMagListFiltered = new ArrayList<>();

    ArrayList<Double> resultant = new ArrayList<>();

    double xAngleGravity, yAngleGravity, zAngleGravity;
    double gravity[] = new double[3];

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

        }
        catch (IOException e) { e.printStackTrace(); }

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
                double rawX = x;
                double y = event.values[1];
                double rawY = y;
                double z = event.values[2];
                double rawZ = z;
                //double total = Math.round((Math.sqrt(x * x + y * y + z * z)) * 100d) / 100d;

                gravity[0] = alpha * gravity[0] + (1 - alpha) * x;
                gravity[1] = alpha * gravity[1] + (1 - alpha) * y;
                gravity[2] = alpha * gravity[2] + (1 - alpha) * z;

                if (sampleCount == 0) findMotionDirection(x, y, z);

                x = rawX - gravity[0];
                y = rawY - gravity[1];
                z = rawZ - gravity[2];

                x = x * Math.sin(Math.toRadians(xAngleGravity));
                y = y * Math.sin(Math.toRadians(yAngleGravity));
                z = z * Math.sin(Math.toRadians(zAngleGravity));

                if (sampleCount >= 5)
                {
                    xMagList.add(x);
                    yMagList.add(y);
                    zMagList.add(z);

                    xRawMagList.add(rawX);
                    yRawMagList.add(rawY);
                    zRawMagList.add(rawZ);

                    //String[] data = {"" + rawX, "" + rawY, "" + rawZ, "" + x, "" + y, "" + z, ""+total, label};
                    //rawDataCSVWriter.writeNext(data);
                }
                if (sampleCount == 68) produceFinalResults();
                if (sampleCount > 69 && (sampleCount + 1) % 32 == 0)
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

    public void findMotionDirection(double tempInitX, double tempInitY, double tempInitZ)
    {
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
        for (int i = 10; i < xMagList.size(); i++)
        {
            xMagList.set(i - 10, xMagList.get(i));
            yMagList.set(i - 10, yMagList.get(i));
            zMagList.set(i - 10, zMagList.get(i));
            resultant.set(i - 10, resultant.get(i));
        }

        for (int i = 0; i < 10; i++)
        {
            xMagList.remove(64);
            yMagList.remove(64);
            zMagList.remove(64);
            resultant.remove(64);
        }
    }

    public int getPeaks()
    {
        int peakCounter = 0;
        double threshold = Collections.max(resultant) * 0.95;
        for(double element:resultant)if(element>=threshold)peakCounter++;
        return peakCounter;
    }

    public void filterData()
    {

    }
}
