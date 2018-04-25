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
import android.util.Log;
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

    ArrayList<Double> resultantFiltered = new ArrayList<>();
    ArrayList<Double> resultant = new ArrayList<>();

    double variance, varianceFiltered, xVariance, yVariance, zVariance;
    double mean, meanFiltered;
    int peaks, peaksFiltered, xZeroCrossings, yZeroCrossings, zZeroCrossings;
    double xDCComponent, yDCComponent, zDCComponent;
    double xSpectralEnergy, ySpectralEnergy, zSpectralEnergy;
    double xEntropy, yEntropy, zEntropy;

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
            String[] data = {"rawX", "rawY", "rawZ", "noGravityX", "noGravityY", "noGravityZ", "filteredX", "filteredY", "filteredZ", "filteredNoGravityX", "filteredNoGravityY", "filteredNoGravityZ", "resultant", "resultantFiltered", "label"};
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
                    xMagList.add(Math.round(x * 100d) / 100d);
                    yMagList.add(Math.round(y * 100d) / 100d);
                    zMagList.add(Math.round(z * 100d) / 100d);

                    xRawMagList.add(Math.round(rawX * 100d) / 100d);
                    yRawMagList.add(Math.round(rawY * 100d) / 100d);
                    zRawMagList.add(Math.round(rawZ * 100d) / 100d);
                }
                if (sampleCount == 68) filterData();
                if (sampleCount > 69 && (sampleCount - 4) % 32 == 0) trimArrayLists();
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

        for(int i = 0;i < xMagList.size(); i++)
        {
            String[] data = {""+xRawMagList.get(i), ""+yRawMagList.get(i), ""+zRawMagList.get(i), ""+xMagList.get(i), ""+yMagList.get(i), ""+zMagList.get(i), ""+xRawMagListFiltered.get(i), ""+yRawMagListFiltered.get(i), ""+zRawMagListFiltered.get(i), ""+xMagListFiltered.get(i), ""+yMagListFiltered.get(i), ""+zMagListFiltered.get(i), ""+resultant.get(i), ""+resultantFiltered.get(i), label};
            rawDataCSVWriter.writeNext(data);
        }

        if(varianceFiltered == 0 || variance == 0)return;
        try
        {
            boolean fileExists = true;
            File csvFile = new File(rootDirectory + "/Data.csv");
            if(!csvFile.exists())fileExists = false;
            FileWriter fileWriter = new FileWriter(csvFile, true);
            CSVWriter writer = new CSVWriter(fileWriter);
            String header[] = {"Mean", "MeanFiltered", "Variance", "VarianceFiltered", "xVariance", "yVariance", "zVariance", "xZeroCrossings", "yZeroCrossings", "zZeroCrossings", "Peaks", "PeaksFiltered", "xDCComponent", "yDCComponent", "zDCComponent", "xSpectralEnergy", "ySpectralEnergy", "zSpectralEnergy", "xEntropy", "yEntropy", "zEntropy", "Activity"};
            if(!fileExists) writer.writeNext(header);
            String[] data = {"" + mean, "" + meanFiltered, "" + variance, "" + varianceFiltered, "" + xVariance, "" + yVariance, "" + zVariance, "" + xZeroCrossings, "" + yZeroCrossings, "" + zZeroCrossings, "" + peaks, "" + peaksFiltered, "" + xDCComponent, "" + yDCComponent, "" + zDCComponent, "" + xSpectralEnergy, "" + ySpectralEnergy, "" + zSpectralEnergy, "" + xEntropy, "" + yEntropy, "" + zEntropy, label};
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

    public double calculateVariance(ArrayList<Double> dataList)
    {
        int count = dataList.size();
        double sum = 0;
        for(double tempElement:dataList)sum = sum+tempElement;
        double average = sum / count;
        double squareSum = 0;
        for (double tempElement : dataList)
        {
            double temp = tempElement - average;
            temp = temp * temp;
            squareSum = squareSum + temp;
        }
        return Math.round((squareSum / count) * 1000d) / 1000d;
    }

    public void trimArrayLists()
    {
        for (int i = 32; i < xMagList.size(); i++)
        {
            xMagList.set(i - 32, xMagList.get(i));
            yMagList.set(i - 32, yMagList.get(i));
            zMagList.set(i - 32, zMagList.get(i));

            xRawMagList.set(i - 32, xRawMagList.get(i));
            yRawMagList.set(i - 32, yRawMagList.get(i));
            zRawMagList.set(i - 32, zRawMagList.get(i));
        }

        for (int i = 0; i < 32; i++)
        {
            xMagList.remove(64);
            yMagList.remove(64);
            zMagList.remove(64);

            xRawMagList.remove(64);
            yRawMagList.remove(64);
            zRawMagList.remove(64);
        }
        filterData();
    }

    public int getPeaks(ArrayList<Double> dataList)
    {
        int peakCounter = 0;
        double threshold = Collections.max(dataList) * 0.95;
        for(double element:dataList)if(element>=threshold)peakCounter++;
        return peakCounter;
    }

    public void filterData()
    {
        xMagListFiltered.clear();
        yMagListFiltered.clear();
        zMagListFiltered.clear();

        xRawMagListFiltered.clear();
        yRawMagListFiltered.clear();
        zRawMagListFiltered.clear();

        xMagListFiltered.add(xMagList.get(0));
        yMagListFiltered.add(yMagList.get(0));
        zMagListFiltered.add(zMagList.get(0));

        xRawMagListFiltered.add(xRawMagList.get(0));
        yRawMagListFiltered.add(yRawMagList.get(0));
        zRawMagListFiltered.add(zRawMagList.get(0));

        for(int i = 1;i < xMagList.size() - 1;i++)
        {
            ArrayList<Double> tempX = new ArrayList<>();
            ArrayList<Double> tempY = new ArrayList<>();
            ArrayList<Double> tempZ = new ArrayList<>();

            ArrayList<Double> tempRawX = new ArrayList<>();
            ArrayList<Double> tempRawY = new ArrayList<>();
            ArrayList<Double> tempRawZ = new ArrayList<>();

            for(int j = -1;j < 2;j++)
            {
                tempX.add(xMagList.get(i + j));
                tempY.add(yMagList.get(i + j));
                tempZ.add(zMagList.get(i + j));

                tempRawX.add(xRawMagList.get(i + j));
                tempRawY.add(yRawMagList.get(i + j));
                tempRawZ.add(zRawMagList.get(i + j));
            }

            Collections.sort(tempX);
            Collections.sort(tempY);
            Collections.sort(tempZ);
            Collections.sort(tempRawX);
            Collections.sort(tempRawY);
            Collections.sort(tempRawZ);

            xMagListFiltered.add(tempX.get(1));
            yMagListFiltered.add(tempY.get(1));
            zMagListFiltered.add(tempZ.get(1));

            xRawMagListFiltered.add(tempRawX.get(1));
            yRawMagListFiltered.add(tempRawY.get(1));
            zRawMagListFiltered.add(tempRawZ.get(1));
        }

        xMagListFiltered.add(xMagList.get(63));
        yMagListFiltered.add(yMagList.get(63));
        zMagListFiltered.add(zMagList.get(63));

        xRawMagListFiltered.add(xRawMagList.get(63));
        yRawMagListFiltered.add(yRawMagList.get(63));
        zRawMagListFiltered.add(zRawMagList.get(63));

        computeResultant();
    }

    public void computeResultant()
    {
        resultant.clear();
        resultantFiltered.clear();

        double x,y,z;
        for(int i = 0;i < xRawMagList.size();i++)
        {
            x = xRawMagList.get(i);
            y = yRawMagList.get(i);
            z = zRawMagList.get(i);
            resultant.add(Math.round((Math.sqrt(x*x + y*y + z*z)) * 1000d) / 1000d);

            x = xRawMagListFiltered.get(i);
            y = yRawMagListFiltered.get(i);
            z = zRawMagListFiltered.get(i);
            resultantFiltered.add(Math.round((Math.sqrt(x*x + y*y + z*z)) * 1000d) / 1000d);
        }
        getTimeDomainFeatures();
        getFrequencyDomainFeatures();
        produceFinalResults();
    }

    public void getTimeDomainFeatures()
    {
        mean = calculateMean(resultant);
        meanFiltered = calculateMean(resultantFiltered);

        variance = calculateVariance(resultant);
        varianceFiltered = calculateVariance(resultantFiltered);
        xVariance = calculateVariance(xMagListFiltered);
        yVariance = calculateVariance(yMagListFiltered);
        zVariance = calculateVariance(zMagListFiltered);

        peaks = getPeaks(resultant);
        peaksFiltered = getPeaks(resultantFiltered);

        xZeroCrossings = getZeroCrossings(xMagListFiltered);
        yZeroCrossings = getZeroCrossings(yMagListFiltered);
        zZeroCrossings = getZeroCrossings(zMagListFiltered);
    }

    public double calculateMean(ArrayList<Double> dataList)
    {
        int count = dataList.size();
        double sum = 0;
        for(double tempElement:dataList)sum = sum+tempElement;
        return Math.round((sum / count) * 1000d) / 1000d;
    }

    public void getFrequencyDomainFeatures()
    {
        double[] realX = new double[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0};
        double[] realY = new double[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0};
        double[] realZ = new double[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0};
        double[] imag = new double[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0};

        for(int i = 0; i < xMagList.size(); i++)
        {
            realX[i] = xMagList.get(i);
            realY[i] = yMagList.get(i);
            realZ[i] = zMagList.get(i);
        }
        String xFeatures[] = performFFT(realX,imag).split(";");
        String yFeatures[] = performFFT(realY,imag).split(";");
        String zFeatures[] = performFFT(realZ,imag).split(";");

        xDCComponent = Double.valueOf(xFeatures[0]);
        yDCComponent = Double.valueOf(yFeatures[0]);
        zDCComponent = Double.valueOf(zFeatures[0]);

        xSpectralEnergy = Double.valueOf(xFeatures[1]);
        ySpectralEnergy = Double.valueOf(yFeatures[1]);
        zSpectralEnergy = Double.valueOf(zFeatures[1]);

        xEntropy = Double.valueOf(xFeatures[2]);
        yEntropy = Double.valueOf(yFeatures[2]);
        zEntropy = Double.valueOf(zFeatures[2]);
    }

    public String performFFT(double[] real, double[] imag)
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

        ArrayList<Double> psdList = new ArrayList<>();
        double psdSum = 0, dcComponent = 0;
        for(int i=0;i<32;i++)
        {
            double realSqTemp = real[i]*real[i];
            double imagSqTemp = imag[i]*imag[i];
            double tempSum = Math.round((realSqTemp + imagSqTemp)*1000d)/1000d;
            if(i==0)dcComponent = Math.round((Math.sqrt(tempSum))*1000d)/1000d;
            else
            {
                psdList.add(tempSum);
                psdSum = psdSum + tempSum;
            }
        }
        double psd =  Math.round((psdSum/psdList.size())*1000d)/1000d;
        double entropy = Math.round((calculateEntropy(psdList))*1000d)/1000d;
        return ""+dcComponent+";"+psd+";"+entropy;
    }

    public double calculateEntropy(ArrayList<Double> psdList)
    {
        double entropySum = 0;
        for(double temp:psdList)
        {
            double entropyTemp = temp*(Math.log(temp)/Math.log(2));
            entropySum = entropySum + entropyTemp;
        }
        return (0 - entropySum)/psdList.size();
    }
}
