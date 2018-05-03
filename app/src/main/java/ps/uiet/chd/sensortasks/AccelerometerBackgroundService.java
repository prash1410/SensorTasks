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
    Handler serverTaskHandler = null;
    Runnable serverTaskRunnable = null;
    static int deviceID;
    String lastFile;
    int drivingCounter;
    static Classifier classifier = null;
    ArrayList<Double> xMagList = new ArrayList<>();
    ArrayList<Double> yMagList = new ArrayList<>();
    ArrayList<Double> zMagList = new ArrayList<>();
    ArrayList<Double> resultant = new ArrayList<>();
    double xAngleGravity, yAngleGravity, zAngleGravity;
    double gravity[] = new double[3];
    double initX, initY, initZ;
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
        handler = new Handler();
        serverTaskHandler = new Handler();
        serverTaskRunnable = new Runnable() {
            public void run() {
                new GetServerUpdates().execute("" + deviceID);
                serverTaskHandler.postDelayed(serverTaskRunnable, 20000);
            }
        };
        serverTaskHandler.postDelayed(serverTaskRunnable, 30000);
        return Service.START_STICKY_COMPATIBILITY;
    }

    @Override
    public void onDestroy()
    {
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
        if (accelerometerActive) deactivateAccelerometer();
        if (recording) {
            stopRecording();
            handler.removeCallbacksAndMessages(null);
        }
        serverTaskHandler.removeCallbacksAndMessages(null);
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
        AccelerometerListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                final float alpha = 0.8f;
                double x = event.values[0];
                double y = event.values[1];
                double z = event.values[2];
                double total = (Math.sqrt(x * x + y * y + z * z));

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

                if (sampleCount >= 5) {
                    xMagList.add(x);
                    yMagList.add(y);
                    zMagList.add(z);
                    resultant.add(total);
                }
                if (sampleCount == 34) produceFinalResults();
                if (sampleCount > 35 && (sampleCount - 4) % 10 == 0)
                {
                    trimArrayLists();
                    produceFinalResults();
                }
                if (sampleCount == 10000)
                {
                    stopService(new Intent(getApplicationContext(), AccelerometerBackgroundService.class));
                    startService(new Intent(getApplicationContext(), AccelerometerBackgroundService.class));
                }

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
        if (AccelerometerManager != null && AccelerometerListener != null)
            AccelerometerManager.unregisterListener(AccelerometerListener);
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

    public String wekaPredict(final double var, final int xZeroCross, final int yZeroCross, final int zZeroCross, final int peaks)
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

                final Attribute attributeVariance = new Attribute("Variance");
                final Attribute attributeXZeroCrossings = new Attribute("xZeroCrossings");
                final Attribute attributeYZeroCrossings = new Attribute("yZeroCrossings");
                final Attribute attributeZZeroCrossings = new Attribute("zZeroCrossings");
                final Attribute attributePeaks = new Attribute("Peaks");
                final List<String> classes = new ArrayList<String>() {
                    {
                        add("Dummy");
                    }
                };

                ArrayList<Attribute> attributeList = new ArrayList<Attribute>() {
                    {
                        add(attributeVariance);
                        add(attributeXZeroCrossings);
                        add(attributeYZeroCrossings);
                        add(attributeZZeroCrossings);
                        add(attributePeaks);
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
                sendSoundSample();
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
        if (mediaRecorder != null) {
            try {
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

    public int getPeaks()
    {
        int peakCounter = 0;
        double threshold = Collections.max(resultant) * 0.95;
        for(double element:resultant)if(element>=threshold)peakCounter++;
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
}