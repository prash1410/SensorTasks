package ps.uiet.chd.sensortasks;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
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
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

public class AccelerometerBackgroundService extends Service
{
    static String model = "Ham Polynomial Kernel Exponent 3 C 100.0 94.0.model";

    String lastLocation;
    FusedLocationProviderClient fusedLocationProviderClient;
    LocationCallback locationCallback;

    Handler handler = null;
    Handler subsequentSamplesHandler = null;
    Runnable subsequentSamplesRunnable = null;
    Handler serverTaskHandler = null;
    Runnable serverTaskRunnable = null;
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

    ArrayList<Double> xDummyRawMagList = new ArrayList<>();
    ArrayList<Double> yDummyRawMagList = new ArrayList<>();
    ArrayList<Double> zDummyRawMagList = new ArrayList<>();

    ArrayList<Double> xDummyMagList = new ArrayList<>();
    ArrayList<Double> yDummyMagList = new ArrayList<>();
    ArrayList<Double> zDummyMagList = new ArrayList<>();

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
    double xSpectralEnergy, ySpectralEnergy, zSpectralEnergy, totalSpectralEnergy;
    double xEntropy, yEntropy, zEntropy, totalEntropy;
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
    public void onCreate()  // Method called when service starts
    {
        super.onCreate();
        createDirectoryIfNotExists();   // Create directories for logs, output, etc.
        deviceID = createDeviceID();    // Create a DeviceID to be uniquely identified by the server
        createNotificationChannel();    // Create notification channel needed for showing notification (Oreo and above)
        Thread.setDefaultUncaughtExceptionHandler(new CustomizedExceptionHandler(Environment.getExternalStorageDirectory().getPath()));     // Output unhandled exceptions to log file
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)   // Method called when service starts in addition to onCreate()
    {
        model = intent.getStringExtra("Model");   // Get model selected by user
        createNotification(intent);  // Create persistent service running notification
        if (!accelerometerActive && checkPermissions()) getInitialSamples(); getSubsequentSamples(); activateAccelerometer();   // Check if accelerometer is not already active and app has the needed permissions and if true, activate accelerometer
        handler = new Handler();
        serverTaskHandler = new Handler();
        serverTaskRunnable = new Runnable()
        {
            public void run()
            {
                new GetServerUpdates().execute("" + deviceID);   // Execute getServerUpdates AsyncTask every 20 seconds to get updates from server
                serverTaskHandler.postDelayed(serverTaskRunnable, 20000);
            }
        };
        serverTaskHandler.postDelayed(serverTaskRunnable, 30000);   // First time delay is set to be 30 seconds
        return Service.START_STICKY_COMPATIBILITY;   // Start sticky service
    }

    @Override
    public void onDestroy()  // Method called when the service is stopped normally
    {
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
        if (accelerometerActive) deactivateAccelerometer(); // Deactivate accelerometer if accelerometer is active
        if (recording)
        {
            stopRecording();  // If audio is being recorded, stop recording
            handler.removeCallbacksAndMessages(null);   // Unregister recording handler
        }
        subsequentSamplesHandler.removeCallbacksAndMessages(null);  // Stop getting subsequent samples from server
        serverTaskHandler.removeCallbacksAndMessages(null);  // Unregister server updates handler and stop getting updates from server
    }

    public void createDirectoryIfNotExists()
    {
        File csvFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/AcousticData/CSVData");
        File audioFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/AcousticData/AudioData");
        File serverDataFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/AcousticData/ServerData");
        File crashLogsFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/AcousticData/CrashLogs");
        if (!serverDataFolder.exists()) serverDataFolder.mkdirs();
        if (!csvFolder.exists()) csvFolder.mkdirs();
        if (!audioFolder.exists()) audioFolder.mkdirs();
        if (!crashLogsFolder.exists()) crashLogsFolder.mkdirs();
        rootDirectory = Environment.getExternalStorageDirectory().getAbsolutePath() + "/AcousticData";
    }

    public void activateAccelerometer()
    {
        initializeVariables();    // Initialize (empty) the variables
        accelerometerActive = true;
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
                double rawX = x;    // raw acceleration along x-axis
                double y = event.values[1];
                double rawY = y;    // along y-axis
                double z = event.values[2];
                double rawZ = z;    // along z-axis

                gravity[0] = alpha * gravity[0] + (1 - alpha) * x;      // Isolating gravity along each axis
                gravity[1] = alpha * gravity[1] + (1 - alpha) * y;
                gravity[2] = alpha * gravity[2] + (1 - alpha) * z;

                if (sampleCount == 0) findMotionDirection(x, y, z);    // Get orientation angles on getting the very first sample

                x = rawX - gravity[0];   // acceleration along x-axis excluding gravity
                y = rawY - gravity[1];   // along y-axis
                z = rawZ - gravity[2];   // along z-axis

                x = x * Math.sin(Math.toRadians(xAngleGravity));    // Taking orientation effect into consideration along each axis
                y = y * Math.sin(Math.toRadians(yAngleGravity));    // This is essentially just multiplying the value with the angle
                z = z * Math.sin(Math.toRadians(zAngleGravity));

                xDummyMagList.add(Math.round(x * 100d) / 100d);
                yDummyMagList.add(Math.round(y * 100d) / 100d);
                zDummyMagList.add(Math.round(z * 100d) / 100d);

                xDummyRawMagList.add(Math.round(rawX * 100d) / 100d);
                yDummyRawMagList.add(Math.round(rawY * 100d) / 100d);
                zDummyRawMagList.add(Math.round(rawZ * 100d) / 100d);

                sampleCount = 1;    // Setting sample count to any integer other than zero to indicate it's not the first sample
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {}
        };
        AccelerometerManager.registerListener(AccelerometerListener, Accelerometer, SensorManager.SENSOR_DELAY_NORMAL);     // Register listener with accelerometer and start getting the values
    }

    public void deactivateAccelerometer()    // Called when service is stopped and accelerometer is to be deactivated
    {
        accelerometerActive = false;
        if (AccelerometerManager != null && AccelerometerListener != null) AccelerometerManager.unregisterListener(AccelerometerListener);      // Unregister accelerometer listener
        initializeVariables();      // Empty the variables
    }

    public void initializeVariables()   // Initialize (empty) the variables
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
        // To calculate angle made with gravity vector in degrees
        // Math.acos is Inverse Cosine function
        // Math.PI is simply the value of π which is 3.14
        // 9.8 m/s^2 has been taken to be the magnitude of gravity vector

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
        String result;
        if(variance == 0 || varianceFiltered == 0) result = "Still";    // If variance is found to be zero, the device is assumed to be at rest. Classifier won't be queried
        else result = wekaPredict();    // Else, call the function which queries the classifier
        updateNotification(result);     // Update the service notification to show the activity

        if (result.equals("Driving")) drivingCounter++;    // Increment counter if found in a moving vehicle
        else if(drivingCounter>0)drivingCounter = 0;       // Else set the counter to zero

        SimpleDateFormat simpleDateFormat;      // Get current time for timestamp
        Calendar calender = Calendar.getInstance();
        simpleDateFormat = new SimpleDateFormat("hh:mm:s a");
        String time = simpleDateFormat.format(calender.getTime());

        try
        {
            File logsFile = new File(rootDirectory + "/CSVData/Activity Log.txt");     // Write activity log to text file
            FileWriter fileWriter1 = new FileWriter(logsFile, true);
            fileWriter1.write(result + ", " + time + ", " + lastLocation + "\n");
            fileWriter1.flush();
            fileWriter1.close();

        }
        catch (IOException e) { e.printStackTrace(); }

        if (drivingCounter >= 3 && !recording)      // If classifier says driving 3 consecutive times or more and sound is already not being recorded
        {
            getSoundSample();      // Get acoustic (sound) sample
            getLocation();      // Get instantaneous location
        }

    }

    public void trimArrayLists()     // This method performs windowing of accelerometer data with 50% overlap
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

    public double calculateVariance(ArrayList<Double> dataList)     // Calculates variance of the passed ArrayList
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

    public int getZeroCrossings(ArrayList<Double> magList)      // Gets number of zero crossings of the passed ArrayList
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

    public String wekaPredict()     // Method which queries classifier for classification
    {
        String result = "Inconclusive";
        if (classifier == null)     // If classifier object is empty
        {
            AssetManager assetManager = getAssets();
            try
            {
                classifier = (Classifier) weka.core.SerializationHelper.read(assetManager.open(model));    // Load user specified model from assets folder
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (classifier != null)     // If classifier object is NOT empty
        {
            // Create attributes
            // The names and order of these attributes should be identical to that of dataset used to train model
            // If you change attributes in dataset, make sure you make changes here too
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
                final List<String> classes = new ArrayList<String>()
                {
                    {
                        add("Dummy");
                    }
                };

                // Create an ArrayList of the above attributes
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
                // Create a DenseInstance
                // Pass values extracted from accelerometer data here to the variables
                DenseInstance newInstance = new DenseInstance(dataUnpredicted.numAttributes()) {
                    {
                        setValue(attributeMean, mean);
                        setValue(attributeMeanFiltered, meanFiltered);
                        setValue(attributeVariance, variance);
                        setValue(attributeVarianceFiltered, varianceFiltered);
                        setValue(attributeXVariance, xVariance);
                        setValue(attributeYVariance, yVariance);
                        setValue(attributeZVariance, zVariance);
                        setValue(attributeXZeroCrossings, xZeroCrossings);
                        setValue(attributeYZeroCrossings, yZeroCrossings);
                        setValue(attributeZZeroCrossings, zZeroCrossings);
                        setValue(attributePeaks, peaks);
                        setValue(attributePeaksFiltered, peaksFiltered);
                        setValue(attributeResultantZeroCrossings, resultantZeroCrossings);
                        setValue(attributeResultantFilteredZeroCrossings, resultantFilteredZeroCrossings);
                        setValue(attributeXDCComponent, xDCComponent);
                        setValue(attributeYDCComponent, yDCComponent);
                        setValue(attributeZDCComponent, zDCComponent);
                        setValue(attributeXSpectralEnergy, xSpectralEnergy);
                        setValue(attributeYSpectralEnergy, ySpectralEnergy);
                        setValue(attributeZSpectralEnergy, zSpectralEnergy);
                        setValue(attributeTotalSpectralEnergy, totalSpectralEnergy);
                        setValue(attributeXEntropy, xEntropy);
                        setValue(attributeYEntropy, yEntropy);
                        setValue(attributeZEntropy, zEntropy);
                        setValue(attributeTotalEntropy, totalEntropy);
                        setValue(attributeXFFTZeroCrossings, xFFTZeroCrossings);
                        setValue(attributeYFFTZeroCrossings, yFFTZeroCrossings);
                        setValue(attributeZFFTZeroCrossings, zFFTZeroCrossings);
                    }
                };
                newInstance.setDataset(dataUnpredicted);
                double predictedResult = classifier.classifyInstance(newInstance);   // This line makes the classification from classifier

                // For different models this order can be different
                if (predictedResult == 0.0) result = "Driving";
                if (predictedResult == 1.0) result = "Neither";
                if (predictedResult == 2.0) result = "Walking";

            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        return result;
    }

    public void getSoundSample()     // This method starts and stop recording
    {
        startRecording();    // Start recording
        handler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                stopRecording();    // Stop recording
                sendSoundSample();  // Send sound sample to the server
            }
        }, 10000);   // Stop recording after 10 seconds
    }

    public void startRecording()    //  This method starts recording
    {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("HH_mm_ss__dd_MM_yyyy");    // Getting date and time for sound sample's filename
        String currentTimestamp = sdf.format(new Date());

        lastFile = rootDirectory + "/AudioData/Sample_" + currentTimestamp + ".amr";    // Set path and filename for saving sound sample
        try
        {
            mediaRecorder = new MediaRecorder();    // Get MediaRecorder
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);     // Set recording source and format
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
            mediaRecorder.setOutputFile(lastFile);
            mediaRecorder.prepare();      // Acquire lock on MediaRecorder
            mediaRecorder.start();
        }
        catch (IOException | IllegalStateException e) { e.printStackTrace(); }
        recording = true;      // Set global boolean recording to true
    }

    public void stopRecording()     // This method stops recording
    {
        if (mediaRecorder != null)
        {
            try
            {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }
            catch (RuntimeException e) { e.printStackTrace(); }
        }
        recording = false;      // Set global boolean recording to false
    }

    public int createDeviceID()     // This method creates DeviceID
    {
        Random r = new Random();
        int Low = 1000;
        int High = 10000;
        return r.nextInt(High - Low) + Low;
    }

    public void sendSoundSample()       // This method calls AsyncTask for sending sound sample and GPS data along with DeviceID to server
    {
        try
        {
            int uploadResult = new uploadSampleTask().execute(lastFile, "" + deviceID+";"+lastLocation).get();      // Calling AsyncTask and passing location of last sound file, deviceID and location data
            if (uploadResult == 200) (new File(lastFile)).delete();     // If response from the server indicates success, delete the sound sample from device's storage
        }
        catch (InterruptedException | ExecutionException e) { e.printStackTrace(); }
    }

    @SuppressLint("MissingPermission")
    public void getLocation()     // This method gets instantaneous location data from GPS
    {
        try
        {
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
            }, 6000);     // GPS update receiver will be removed after 6 seconds
        }
        catch (Exception e) { e.printStackTrace(); }
    }

    public int getPeaks(ArrayList<Double> dataList)     // Computation of peaks attribute
    {
        int peakCounter = 0;
        double threshold = Collections.max(dataList) * 0.95;
        for(double element:dataList)if(element>=threshold)peakCounter++;
        return peakCounter;
    }

    public boolean checkPermissions()   // Check for various permissions required by service and return a boolean
    {
        boolean proceed = true;
        if(ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)proceed=false;
        if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)proceed=false;
        if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)proceed=false;
        if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED)proceed=false;
        if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)proceed=false;
        return proceed;
    }

    public void filterData()    // This function applies median filtering to data acquired by accelerometer
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

    public void getTimeDomainFeatures()     // This function gets Time Domain features
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

    public void getFrequencyDomainFeatures()      // This function gets Frequency Domain features
    {
        // Create(Instantiate) empty double arrays of size 64 for each axis
        double[] realX = new double[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0};
        double[] realY = new double[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0};
        double[] realZ = new double[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0};

        // Since we're dealing with time domain data, imaginary input part will always be zero
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

        totalSpectralEnergy = Math.round((xSpectralEnergy+ySpectralEnergy+zSpectralEnergy)*1000d)/1000d;
        totalEntropy = Math.round((xEntropy+yEntropy+zEntropy)*1000d)/1000d;
    }

    public String performFFT(double[] real, double[] imag)      // This function performs FFT
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

        // Following code computes spectral energy from FFT output
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

    public double calculateEntropy(ArrayList<Double> psdList)   //This function computes Entropy from FFT output
    {
        double entropySum = 0;
        for(double temp:psdList)
        {
            double entropyTemp = temp*(Math.log(temp)/Math.log(2));
            entropySum = entropySum + entropyTemp;
        }
        return entropySum/psdList.size();
    }

    public ArrayList<Double> computeLaplacian(ArrayList<Double> dataList)   // Performs double-derivative of FFT output and then calls to find zero crossings
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

    public void createNotification(Intent intent)     // Method for creating notification
    {
        if (Objects.requireNonNull(intent.getAction()).equals("Start"))
        {

            Intent notificationIntent = new Intent(this, MainActivity.class);
            notificationIntent.setAction("Main");
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            Intent stopIntent = new Intent(this, AccelerometerBackgroundService.class);
            stopIntent.setAction("Stop");
            PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, 0);
            Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_settings_remote_black_48dp);
            Notification.Action action = new Notification.Action(R.drawable.ic_stop_black_18dp, "Stop", stopPendingIntent);
            Notification notification = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) // To create notification on devices running Android O and above
            {
                notification = new Notification.Builder(this,"SensorTasks1410")
                        .setContentTitle("Activity recognition service")
                        .setTicker("Activity recognition service")
                        .setContentText("Service running")
                        .setOnlyAlertOnce(true)
                        .setStyle(new Notification.BigTextStyle().bigText("Service running"))
                        .setSmallIcon(R.drawable.ic_settings_remote_black_18dp)
                        .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .setWhen(System.currentTimeMillis())
                        .setShowWhen(true)
                        .setColor(Color.CYAN)
                        .addAction(action).build();
            }
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)      // To create notification on devices running Android Lollipop to Nougat
                {
                    notification = new Notification.Builder(this)
                            .setContentTitle("Activity recognition service")
                            .setTicker("Activity recognition service")
                            .setContentText("Service running")
                            .setOnlyAlertOnce(true)
                            .setStyle(new Notification.BigTextStyle().bigText("Service running"))
                            .setSmallIcon(R.drawable.ic_settings_remote_black_18dp)
                            .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                            .setContentIntent(pendingIntent)
                            .setOngoing(true)
                            .setWhen(System.currentTimeMillis())
                            .setShowWhen(true)
                            .setColor(Color.CYAN)
                            .addAction(action).build();
                }
                else    // To create notification on devices running below Android Lollipop
                {
                    notification = new Notification.Builder(this)
                            .setContentTitle("Activity recognition service")
                            .setTicker("Activity recognition service")
                            .setContentText("Service running")
                            .setOnlyAlertOnce(true)
                            .setStyle(new Notification.BigTextStyle().bigText("Service running"))
                            .setSmallIcon(R.drawable.ic_settings_remote_black_18dp)
                            .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                            .setContentIntent(pendingIntent)
                            .setOngoing(true)
                            .setWhen(System.currentTimeMillis())
                            .setShowWhen(true).build();
                }
            }
            startForeground(101, notification);
        }
        else if (intent.getAction().equals("Stop"))     // Handles the stop action when notification action is clicked
        {
            stopForeground(true);
            stopSelf();
        }
    }

    public void createNotificationChannel()     // Creates notification channel on devices running Android O or above. This is essential starting with Android O
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            String channelID = "SensorTasks1410";
            CharSequence channelName = "SensorTasks notification channel";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel notificationChannel = new NotificationChannel(channelID, channelName, importance);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    public void updateNotification(String status)       // Identical to createNotificationMethod() just used for updating notification text
    {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction("Main");
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Intent stopIntent = new Intent(this, AccelerometerBackgroundService.class);
        stopIntent.setAction("Stop");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, 0);
        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_settings_remote_black_48dp);
        Notification.Action action = new Notification.Action(R.drawable.ic_stop_black_18dp, "Stop", stopPendingIntent);
        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            notification = new Notification.Builder(this,"SensorTasks1410")
                    .setContentTitle("Activity recognition service")
                    .setTicker("Activity recognition service")
                    .setContentText(status)
                    .setOnlyAlertOnce(true)
                    .setStyle(new Notification.BigTextStyle().bigText(status))
                    .setSmallIcon(R.drawable.ic_settings_remote_black_18dp)
                    .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true)
                    .setColor(Color.CYAN)
                    .addAction(action).build();
        }
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            {
                notification = new Notification.Builder(this)
                        .setContentTitle("Activity recognition service")
                        .setTicker("Activity recognition service")
                        .setContentText(status)
                        .setOnlyAlertOnce(true)
                        .setStyle(new Notification.BigTextStyle().bigText(status))
                        .setSmallIcon(R.drawable.ic_settings_remote_black_18dp)
                        .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .setWhen(System.currentTimeMillis())
                        .setShowWhen(true)
                        .setColor(Color.CYAN)
                        .addAction(action).build();
            }
            else
            {
                notification = new Notification.Builder(this)
                        .setContentTitle("Activity recognition service")
                        .setTicker("Activity recognition service")
                        .setContentText(status)
                        .setOnlyAlertOnce(true)
                        .setStyle(new Notification.BigTextStyle().bigText(status))
                        .setSmallIcon(R.drawable.ic_settings_remote_black_18dp)
                        .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .setWhen(System.currentTimeMillis())
                        .setShowWhen(true).build();
            }
        }

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.notify(101, notification);
    }

    public void getInitialSamples()     // This runnable gets the first 64 accelerometer samples after ignoring first 5 - 10 samples
    {
        Handler initialSampleCollector = new Handler();
        initialSampleCollector.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                ArrayList<Double> x = xDummyMagList;
                ArrayList<Double> y = yDummyMagList;
                ArrayList<Double> z = zDummyMagList;

                ArrayList<Double> xRaw = xDummyRawMagList;
                ArrayList<Double> yRaw = yDummyRawMagList;
                ArrayList<Double> zRaw = zDummyRawMagList;
                int mod = x.size()/64;
                Log.e("SensorTasks ",""+x.size());
                if(mod==1)
                {
                    Log.e("SensorTasks ","1st condition");
                    if(x.size()!=64)
                    {
                        int skipIndex = x.size()%64 - 1;
                        for(;skipIndex<x.size()-1;skipIndex++)
                        {
                            xMagList.add(x.get(skipIndex));
                            yMagList.add(y.get(skipIndex));
                            zMagList.add(z.get(skipIndex));

                            xRawMagList.add(xRaw.get(skipIndex));
                            yRawMagList.add(yRaw.get(skipIndex));
                            zRawMagList.add(zRaw.get(skipIndex));
                        }
                    }
                    else
                    {
                        xMagList = x;
                        yMagList = y;
                        zMagList = z;

                        xRawMagList = xRaw;
                        yRawMagList = yRaw;
                        zRawMagList = zRaw;
                    }
                }
                else if(mod > 1)
                {
                    Log.e("SensorTasks ","2nd condition");
                    int skipIndex = 0;
                    if(x.size()%64!=0)skipIndex = x.size()%64 - 1;
                    for(; skipIndex < x.size()-1; skipIndex = skipIndex + mod)
                    {
                        xMagList.add(x.get(skipIndex));
                        yMagList.add(y.get(skipIndex));
                        zMagList.add(z.get(skipIndex));

                        xRawMagList.add(xRaw.get(skipIndex));
                        yRawMagList.add(yRaw.get(skipIndex));
                        zRawMagList.add(zRaw.get(skipIndex));
                    }
                }
                else if(mod == 0)
                {
                    Log.e("SensorTasks ","3rd condition");
                    xMagList = x;
                    yMagList = y;
                    zMagList = z;

                    xRawMagList = xRaw;
                    yRawMagList = yRaw;
                    zRawMagList = zRaw;

                    for(int i=0; i<64-x.size();i++)
                    {
                        xMagList.add(0.0);
                        yMagList.add(0.0);
                        zMagList.add(0.0);

                        xRawMagList.add(0.0);
                        yRawMagList.add(0.0);
                        zRawMagList.add(0.0);
                    }
                }
                Log.e("SensorTasks ",""+xMagList.size());
                xDummyMagList.clear();
                yDummyMagList.clear();
                zDummyMagList.clear();
                xDummyRawMagList.clear();
                yDummyRawMagList.clear();
                zDummyRawMagList.clear();
                filterData();
            }
        }, 15000);
    }

    public void getSubsequentSamples()      // After getting first 64 samples, get every subsequent 32 samples
    {
        subsequentSamplesHandler = new Handler();
        subsequentSamplesRunnable = new Runnable()
        {
            public void run()
            {
                ArrayList<Double> x = xDummyMagList;
                ArrayList<Double> y = yDummyMagList;
                ArrayList<Double> z = zDummyMagList;

                ArrayList<Double> xRaw = xDummyRawMagList;
                ArrayList<Double> yRaw = yDummyRawMagList;
                ArrayList<Double> zRaw = zDummyRawMagList;
                int mod = x.size()/32;
                Log.e("SensorTasks ",""+x.size());
                if(mod==1)
                {
                    Log.e("SensorTasks ","1st condition");
                    if(x.size()!=32)
                    {
                        int skipIndex = x.size()%32 - 1;
                        for(;skipIndex<x.size()-1;skipIndex++)
                        {
                            xMagList.add(x.get(skipIndex));
                            yMagList.add(y.get(skipIndex));
                            zMagList.add(z.get(skipIndex));

                            xRawMagList.add(xRaw.get(skipIndex));
                            yRawMagList.add(yRaw.get(skipIndex));
                            zRawMagList.add(zRaw.get(skipIndex));
                        }
                    }
                    else
                    {
                        for(int i = 0; i<x.size();i++)
                        {
                            xMagList.add(x.get(i));
                            yMagList.add(y.get(i));
                            zMagList.add(z.get(i));

                            xRawMagList.add(xRaw.get(i));
                            yRawMagList.add(yRaw.get(i));
                            zRawMagList.add(zRaw.get(i));
                        }
                    }

                }
                else if(mod > 1)
                {
                    Log.e("SensorTasks ","2nd condition");
                    int skipIndex = 0;
                    if(x.size()%32!=0)skipIndex = x.size()%32 - 1;
                    for(; skipIndex < x.size()-1; skipIndex = skipIndex + mod)
                    {
                        xMagList.add(x.get(skipIndex));
                        yMagList.add(y.get(skipIndex));
                        zMagList.add(z.get(skipIndex));

                        xRawMagList.add(xRaw.get(skipIndex));
                        yRawMagList.add(yRaw.get(skipIndex));
                        zRawMagList.add(zRaw.get(skipIndex));
                    }
                }
                else if(mod == 0)
                {
                    Log.e("SensorTasks ","3rd condition");

                    for(int i=0; i<x.size();i++)
                    {
                        xMagList.add(x.get(i));
                        yMagList.add(y.get(i));
                        zMagList.add(z.get(i));

                        xRawMagList.add(xRaw.get(i));
                        yRawMagList.add(yRaw.get(i));
                        zRawMagList.add(zRaw.get(i));
                    }

                    for(int i=0; i<32-x.size();i++)
                    {
                        xMagList.add(0.0);
                        yMagList.add(0.0);
                        zMagList.add(0.0);

                        xRawMagList.add(0.0);
                        yRawMagList.add(0.0);
                        zRawMagList.add(0.0);
                    }
                }
                Log.e("SensorTasks ",""+xMagList.size());
                xDummyMagList.clear();
                yDummyMagList.clear();
                zDummyMagList.clear();
                xDummyRawMagList.clear();
                yDummyRawMagList.clear();
                zDummyRawMagList.clear();
                trimArrayLists();
                subsequentSamplesHandler.postDelayed(subsequentSamplesRunnable, 7500);
            }
        };
        subsequentSamplesHandler.postDelayed(subsequentSamplesRunnable, 22500);
    }
}