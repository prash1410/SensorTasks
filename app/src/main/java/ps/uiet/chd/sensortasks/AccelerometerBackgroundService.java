package ps.uiet.chd.sensortasks;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import au.com.bytecode.opencsv.CSVWriter;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

public class AccelerometerBackgroundService extends Service
{
    String lastLocation;
    FusedLocationProviderClient fusedLocationProviderClient;
    LocationCallback locationCallback;

    Handler handler = null;
    //Handler serverTaskHandler = null;
    //Runnable serverTaskRunnable = null;
    static int deviceID;
    String lastFile;
    int drivingCounter;
    static Classifier classifier = null;

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
    int resultantZeroCrossings, resultantFilteredZeroCrossings;
    double xDCComponent, yDCComponent, zDCComponent;
    double xSpectralEnergy, ySpectralEnergy, zSpectralEnergy;
    double xEntropy, yEntropy, zEntropy;
    double xFFTZeroCrossings, yFFTZeroCrossings, zFFTZeroCrossings;
    double xAngleGravity, yAngleGravity, zAngleGravity;
    double gravity[] = new double[3];

    boolean accelerometerActive = false, recording = false;
    static int sampleCount;

    MediaRecorder mediaRecorder;
    String rootDirectory = "";

    SensorManager AccelerometerManager;
    Sensor Accelerometer;
    SensorEventListener AccelerometerListener;

    @Override
    public void onCreate()
    {
        super.onCreate();
        createDirectoryIfNotExists();
        deviceID = createDeviceID();
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
        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show();
        if (!accelerometerActive && checkPermissions()) activateAccelerometer();

        /*
        handler = new Handler();
        serverTaskHandler = new Handler();
        serverTaskRunnable = new Runnable()
        {
            public void run() {
                new GetServerUpdates().execute("" + deviceID);
                serverTaskHandler.postDelayed(serverTaskRunnable, 20000);
            }
        };
        serverTaskHandler.postDelayed(serverTaskRunnable, 30000);
        */
        return Service.START_STICKY_COMPATIBILITY;
    }

    @Override
    public void onDestroy()
    {
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
        if (accelerometerActive) deactivateAccelerometer();
        if (recording)
        {
            stopRecording();
            handler.removeCallbacksAndMessages(null);
        }
        //serverTaskHandler.removeCallbacksAndMessages(null);
    }

    public void createDirectoryIfNotExists()
    {
        File csvFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/AcousticData/CSVData");
        File audioFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/AcousticData/AudioData");
        File serverDataFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/AcousticData/ServerData");
        if (!serverDataFolder.exists()) serverDataFolder.mkdirs();
        if (!csvFolder.exists()) csvFolder.mkdirs();
        if (!audioFolder.exists()) audioFolder.mkdirs();
        rootDirectory = Environment.getExternalStorageDirectory().getAbsolutePath() + "/AcousticData";
    }

    public void activateAccelerometer()
    {
        initializeVariables();
        accelerometerActive = true;
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

                x = rawX - gravity[0]; // acceleration along x-axis excluding gravity
                y = rawY - gravity[1]; // along y-axis
                z = rawZ - gravity[2]; // along z-axis

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
                /*
                if (sampleCount == 10000)
                {
                    stopService(new Intent(getApplicationContext(), AccelerometerBackgroundService.class));
                    startService(new Intent(getApplicationContext(), AccelerometerBackgroundService.class));
                }
                */
                sampleCount++;
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
            }
        };
        AccelerometerManager.registerListener(AccelerometerListener, Accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void deactivateAccelerometer()
    {
        accelerometerActive = false;
        if (AccelerometerManager != null && AccelerometerListener != null) AccelerometerManager.unregisterListener(AccelerometerListener);
        initializeVariables();
    }

    public void initializeVariables()
    {
        sampleCount = 0;
        xMagList.clear();
        yMagList.clear();
        zMagList.clear();
        resultant.clear();
        lastFile = "";
        lastLocation = "empty;empty";
        drivingCounter = 0;
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

    @SuppressLint("SimpleDateFormat")
    public void produceFinalResults()
    {
        double variance = calculateVariance();
        if(variance == 0)
        {
            drivingCounter = 0;
            return;
        }

        int xZeroCrossings = getZeroCrossings(xMagList);
        int yZeroCrossings = getZeroCrossings(yMagList);
        int zZeroCrossings = getZeroCrossings(zMagList);
        int peaks = getPeaks();

        String result = wekaPredict(variance, xZeroCrossings, yZeroCrossings, zZeroCrossings, peaks);
        if (result.equals("Driving")) drivingCounter++;
        else drivingCounter = 0;
        SimpleDateFormat simpleDateFormat;
        Calendar calender = Calendar.getInstance();
        simpleDateFormat = new SimpleDateFormat("hh:mm:s a");
        String time = simpleDateFormat.format(calender.getTime());

        try
        {
            File csvFile = new File(rootDirectory + "/CSVData/Output.csv");
            FileWriter fileWriter = new FileWriter(csvFile, true);
            CSVWriter writer = new CSVWriter(fileWriter);
            String[] data = {"" + variance, "" + xZeroCrossings, "" + yZeroCrossings, "" + zZeroCrossings, "" + peaks};
            writer.writeNext(data);
            writer.close();

            File logsFile = new File(rootDirectory + "/CSVData/Activity Log.txt");
            FileWriter fileWriter1 = new FileWriter(logsFile, true);
            fileWriter1.write(result + " " + time + "\n");
            fileWriter1.flush();
            fileWriter1.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (drivingCounter >= 7 && !recording)
        {
            getSoundSample();
            getLocation();
        }

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

    public String wekaPredict()
    {
        String result = "Inconclusive";
        if (classifier == null)
        {
            AssetManager assetManager = getAssets();
            try
            {
                classifier = (Classifier) weka.core.SerializationHelper.read(assetManager.open("PolyKernel.model"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (classifier != null)
        {
            try {

                final Attribute attributeMean = new Attribute("Mean");
                final Attribute attributeMeanFiltered = new Attribute("MeanFiltered");
                final Attribute attributeVariance = new Attribute("Variance");
                final Attribute attributeVarianceFiltered = new Attribute("VarianceFiltered");
                final Attribute attributeXVariance = new Attribute("xVariance");
                final Attribute attributeYVariance = new Attribute("yVariance");
                final Attribute attributeZVariance = new Attribute("zVariance");
                final Attribute attributeXZeroCrossings = new Attribute("xZeroCrossings");
                final Attribute attributeYZeroCrossings = new Attribute("yZeroCrossings");
                final Attribute attributeZZeroCrossings = new Attribute("zZeroCrossings");
                final Attribute attributePeaks = new Attribute("Peaks");
                final Attribute attributePeaksFiltered = new Attribute("PeaksFiltered");
                final Attribute attributeResultantZeroCrossings = new Attribute("ResultantZeroCrossings");
                final Attribute attributeResultantFilteredZeroCrossings = new Attribute("ResultantFilteredZeroCrossings");
                final Attribute attributeXDCComponent = new Attribute("xDCComponent");
                final Attribute attributeYDCComponent = new Attribute("yDCComponent");
                final Attribute attributeZDCComponent = new Attribute("zDCComponent");
                final Attribute attributeXSpectralEnergy = new Attribute("xSpectralEnergy");
                final Attribute attributeYSpectralEnergy = new Attribute("ySpectralEnergy");
                final Attribute attributeZSpectralEnergy = new Attribute("zSpectralEnergy");
                final Attribute attributeTotalSpectralEnergy = new Attribute("TotalSpectralEnergy");
                final Attribute attributeXEntropy = new Attribute("xEntropy");
                final Attribute attributeYEntropy = new Attribute("yEntropy");
                final Attribute attributeZEntropy = new Attribute("zEntropy");
                final Attribute attributeTotalEntropy = new Attribute("TotalEntropy");
                final Attribute attributeXFFTZeroCrossings = new Attribute("xFFTZeroCrossings");
                final Attribute attributeYFFTZeroCrossings = new Attribute("yFFTZeroCrossings");
                final Attribute attributeZFFTZeroCrossings = new Attribute("zFFTZeroCrossings");
                final List<String> classes = new ArrayList<String>() {
                    {
                        add("Dummy");
                    }
                };

                ArrayList<Attribute> attributeList = new ArrayList<Attribute>() {
                    {
                        add(attributeMean);
                        add(attributeMeanFiltered);
                        add(attributeVariance);
                        add(attributeVarianceFiltered);
                        add(attributeXVariance);
                        add(attributeYVariance);
                        add(attributeZVariance);
                        add(attributeXZeroCrossings);
                        add(attributeYZeroCrossings);
                        add(attributeZZeroCrossings);
                        add(attributePeaks);
                        add(attributePeaksFiltered);
                        add(attributeResultantZeroCrossings);
                        add(attributeResultantFilteredZeroCrossings);
                        add(attributeXDCComponent);
                        add(attributeYDCComponent);
                        add(attributeZDCComponent);
                        add(attributeXSpectralEnergy);
                        add(attributeYSpectralEnergy);
                        add(attributeZSpectralEnergy);
                        add(attributeTotalSpectralEnergy);
                        add(attributeXEntropy);
                        add(attributeYEntropy);
                        add(attributeZEntropy);
                        add(attributeTotalEntropy);
                        add(attributeXFFTZeroCrossings);
                        add(attributeYFFTZeroCrossings);
                        add(attributeZFFTZeroCrossings);
                        Attribute attributeClass = new Attribute("Activity", classes);
                        add(attributeClass);
                    }
                };

                
                Instances dataUnpredicted = new Instances("TestInstances", attributeList, 1);
                dataUnpredicted.setClassIndex(dataUnpredicted.numAttributes() - 1);
                DenseInstance newInstance = new DenseInstance(dataUnpredicted.numAttributes()) {
                    {
                        setValue(attributeMean, mean);
                        setValue(attributeVariance, var);
                        setValue(attributeXZeroCrossings, xZeroCross);
                        setValue(attributeYZeroCrossings, yZeroCross);
                        setValue(attributeZZeroCrossings, zZeroCross);
                        setValue(attributeZZeroCrossings, peaks);
                    }
                };
                newInstance.setDataset(dataUnpredicted);
                double predictedResult = classifier.classifyInstance(newInstance);
                if (predictedResult == 0.0) result = "Walking";
                if (predictedResult == 1.0) result = "Still";
                if (predictedResult == 2.0) result = "Driving";

            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        return result;
    }

    public void getSoundSample()
    {
        startRecording();
        handler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                stopRecording();
                //sendSoundSample();
            }
        }, 10000);
    }

    public void startRecording()
    {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("HH_mm_ss__dd_MM_yyyy");
        String currentTimestamp = sdf.format(new Date());

        lastFile = rootDirectory + "/AudioData/Sample_" + currentTimestamp + ".amr";
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
            mediaRecorder.setOutputFile(lastFile);
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (IOException | IllegalStateException e) {
            e.printStackTrace();
        }
        recording = true;
    }

    public void stopRecording()
    {
        if (mediaRecorder != null)
        {
            try
            {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;

            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        recording = false;
    }

    public int createDeviceID()
    {
        Random r = new Random();
        int Low = 1000;
        int High = 10000;
        return r.nextInt(High - Low) + Low;
    }

    public void sendSoundSample()
    {

        try
        {
            int uploadResult = new uploadSampleTask().execute(lastFile, "" + deviceID+";"+lastLocation).get();
            if (uploadResult == 200) (new File(lastFile)).delete();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        Toast.makeText(getApplicationContext(),"Driving!!!!!!",Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("MissingPermission")
    public void getLocation()
    {
        try {
            @SuppressLint("RestrictedApi") LocationRequest locationRequest = new LocationRequest();
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationRequest.setInterval(500);
            locationRequest.setFastestInterval(500);

            LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
            builder.addLocationRequest(locationRequest);
            LocationSettingsRequest locationSettingsRequest = builder.build();

            SettingsClient settingsClient = LocationServices.getSettingsClient(getApplicationContext());
            settingsClient.checkLocationSettings(locationSettingsRequest);

            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
            locationCallback = new LocationCallback()
            {
                @Override
                public void onLocationResult(LocationResult locationResult)
                {
                    String latitude = String.valueOf(locationResult.getLastLocation().getLatitude());
                    String longitude = String.valueOf(locationResult.getLastLocation().getLongitude());
                    lastLocation = latitude + ";" + longitude;
                }
            };
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());

            final Handler locationUpdatesStopper = new Handler();
            locationUpdatesStopper.postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    fusedLocationProviderClient.removeLocationUpdates(locationCallback);
                }
            }, 6000);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public int getPeaks(ArrayList<Double> dataList)
    {
        int peakCounter = 0;
        double threshold = Collections.max(dataList) * 0.95;
        for(double element:dataList)if(element>=threshold)peakCounter++;
        return peakCounter;
    }

    public boolean checkPermissions()
    {
        boolean proceed = true;
        if(ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)proceed=false;
        if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)proceed=false;
        if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)proceed=false;
        if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED)proceed=false;
        if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)proceed=false;
        return proceed;
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

        resultantZeroCrossings = getZeroCrossings(computeLaplacian(resultant));
        resultantFilteredZeroCrossings = getZeroCrossings(computeLaplacian(resultantFiltered));
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

        xFFTZeroCrossings = Integer.valueOf(xFeatures[3]);
        yFFTZeroCrossings = Integer.valueOf(yFeatures[3]);
        zFFTZeroCrossings = Integer.valueOf(zFeatures[3]);
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

        ArrayList<Double> psdList = new ArrayList<>(), fftMagList = new ArrayList<>();
        double psdSum = 0, dcComponent = 0;
        for(int i=0;i<real.length;i++)
        {
            double realSqTemp = real[i]*real[i];
            double imagSqTemp = imag[i]*imag[i];
            double tempSum = Math.round((realSqTemp + imagSqTemp)*1000d)/1000d;
            if(i==0)dcComponent = Math.round((Math.sqrt(tempSum))*1000d)/1000d;
            if(i > 0 && i < 32)
            {
                psdList.add(tempSum);
                psdSum = psdSum + tempSum;
            }
            fftMagList.add(Math.sqrt(tempSum));
        }
        double psd =  Math.round((psdSum/psdList.size())*1000d)/1000d;
        double entropy = Math.round((calculateEntropy(psdList))*1000d)/1000d;
        int fftLaplacian = getZeroCrossings(computeLaplacian(fftMagList));
        return ""+dcComponent+";"+psd+";"+entropy+";"+fftLaplacian;
    }

    public double calculateEntropy(ArrayList<Double> psdList)
    {
        double entropySum = 0;
        for(double temp:psdList)
        {
            double entropyTemp = temp*(Math.log(temp)/Math.log(2));
            entropySum = entropySum + entropyTemp;
        }
        return entropySum/psdList.size();
    }

    public ArrayList<Double> computeLaplacian(ArrayList<Double> dataList)
    {
        ArrayList<Double> laplaceList = new ArrayList<>();
        for (int i = 0; i < dataList.size(); i++)
        {
            double laplacian;
            if(i == 0 || i == dataList.size() - 1)laplacian = Math.round(dataList.get(i));
            else laplacian = Math.round((dataList.get(i-1) - 2*(dataList.get(i)) + dataList.get(i+1)));
            laplaceList.add(laplacian);
        }
        return laplaceList;
    }
}