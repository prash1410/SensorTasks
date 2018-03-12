package ps.uiet.chd.sensortasks;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import au.com.bytecode.opencsv.CSVWriter;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

public class WekaActivity extends AppCompatActivity implements View.OnClickListener
{
    static int deviceID;
    String lastFile = "";
    int drivingCounter = 0;
    String PHP_URL = "http://192.168.42.67/PHPScripts/test.php";
    static Classifier classifier = null;
    ArrayList <Double> xMagList = new ArrayList<>();
    ArrayList <Double> yMagList = new ArrayList<>();
    ArrayList <Double> zMagList = new ArrayList<>();
    ArrayList <Double> resultant = new ArrayList<>();
    double xAngleGravity = 0,yAngleGravity = 0,zAngleGravity = 0;
    double gravity[] = new double[3];
    double initX,initY,initZ;
    boolean runnableRunning = false, recording = false;
    Button startStopRunnable, serverTest, startStopRecording;
    static int sampleCount = 0;

    MediaRecorder mediaRecorder;
    String rootDirectory = "";

    SensorManager AccelerometerManager;
    Sensor Accelerometer;
    SensorEventListener AccelerometerListener;

    CheckBox sendData;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weka);
        StrictMode.ThreadPolicy policy=new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        startStopRunnable = (Button)findViewById(R.id.startStopRunnable);
        startStopRunnable.setOnClickListener(this);
        serverTest = (Button)findViewById(R.id.serverTest);
        serverTest.setOnClickListener(this);
        startStopRecording = (Button)findViewById(R.id.startStopRecording);
        startStopRecording.setOnClickListener(this);
        sendData = (CheckBox)findViewById(R.id.sendData);
        createDirectoryIfNotExists();
        deviceID = createDeviceID();
    }

    @Override
    public void onClick(View v)
    {
        switch (v.getId())
        {
            case R.id.startStopRunnable:
                if(!runnableRunning)startRunnable();
                else stopRunnable();
                break;
            case R.id.serverTest:
                testAsyncTask();
                break;
            case R.id.startStopRecording:
                if(!recording)startRecording();
                else stopRecording();
                break;
            default:
                break;
        }
    }

    public void startRunnable()
    {
        runnableRunning = true;
        startStopRunnable.setText("Stop runnable");

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
                double y = event.values[1];
                double z = event.values[2];
                double total = (Math.sqrt(x * x + y * y + z * z));

                gravity[0] = alpha * gravity[0] + (1 - alpha) * x;
                gravity[1] = alpha * gravity[1] + (1 - alpha) * y;
                gravity[2] = alpha * gravity[2] + (1 - alpha) * z;

                if(sampleCount==0)
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

                if (sampleCount >= 5)
                {
                    xMagList.add(x);
                    yMagList.add(y);
                    zMagList.add(z);
                    resultant.add(total);
                }
                if(sampleCount==34) produceFinalResults();
                if(sampleCount>35 && (sampleCount+1)%5==0)
                {
                    trimArrayLists();
                    produceFinalResults();
                }

                sampleCount++;
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {}
        };
        AccelerometerManager.registerListener(AccelerometerListener, Accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void stopRunnable()
    {
        runnableRunning = false;
        startStopRunnable.setText("Start runnable");
        Toast.makeText(getApplicationContext(),"Runnable stopped",Toast.LENGTH_LONG).show();
        if(AccelerometerManager!=null&&AccelerometerListener!=null)AccelerometerManager.unregisterListener(AccelerometerListener);
        initializeVariables();
    }

    public void initializeVariables()
    {
        sampleCount = 0;
        xMagList.clear();
        yMagList.clear();
        zMagList.clear();
        resultant.clear();
    }

    public void trimArrayLists()
    {
        for(int i=5;i<xMagList.size();i++)
        {
            xMagList.set(i-5,xMagList.get(i));
            yMagList.set(i-5,yMagList.get(i));
            zMagList.set(i-5,zMagList.get(i));
            resultant.set(i-5,resultant.get(i));
        }

        for(int i=0;i<5;i++)
        {
            xMagList.remove(30);
            yMagList.remove(30);
            zMagList.remove(30);
            resultant.remove(30);
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

    public double calculateVariance()
    {
        int count = resultant.size();
        double sum = 0;
        for(double tempElement:resultant)
        {
            sum = sum+tempElement;
        }
        double average = sum/count;
        double squareSum = 0;
        for (double tempElement:resultant)
        {
            double temp = tempElement-average;
            temp = temp*temp;
            squareSum = squareSum + temp;
        }
        return squareSum/count;
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

    @SuppressLint("SimpleDateFormat")
    public void produceFinalResults()
    {
        double variance = (double)Math.round(calculateVariance() * 100d) / 100d;
        int xZeroCrossings = getZeroCrossings(xMagList);
        int yZeroCrossings = getZeroCrossings(yMagList);
        int zZeroCrossings = getZeroCrossings(zMagList);

        String result = wekaPredict(variance,xZeroCrossings,yZeroCrossings,zZeroCrossings);
        if(result.equals("Driving"))drivingCounter++;
        else drivingCounter = 0;
        SimpleDateFormat simpleDateFormat;
        Calendar calender = Calendar.getInstance();
        simpleDateFormat = new SimpleDateFormat("hh:mm:s a");
        String time = simpleDateFormat.format(calender.getTime());

        try
        {
            File csvFile = new File(rootDirectory+"/CSVData/Output.csv");
            FileWriter fileWriter = new FileWriter(csvFile,true);
            CSVWriter writer = new CSVWriter(fileWriter);
            String[] data = {""+variance, ""+xZeroCrossings,""+yZeroCrossings,""+zZeroCrossings};
            writer.writeNext(data);
            writer.close();

            File logsFile = new File(rootDirectory+"/CSVData/Activity Log.txt");
            FileWriter fileWriter1 = new FileWriter(logsFile,true);
            fileWriter1.write(result+" "+time+"\n");
            fileWriter1.flush();
            fileWriter1.close();

        } catch (IOException e)
        {
            e.printStackTrace();
        }

        if(drivingCounter>=5 && !recording)getSoundSample();

    }

    public void serverTest()
    {
        DataOutputStream dos;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1024 * 1024;
        try
        {
            File sourceFile = new File(lastFile);
            FileInputStream fileInputStream = new FileInputStream(sourceFile);
            URL url = new URL(PHP_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("ENCTYPE", "multipart/form-data");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            conn.setRequestProperty("uploaded_file", lastFile);
            dos = new DataOutputStream(conn.getOutputStream());
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"uploaded_file\";filename=\"" + lastFile + "\"" + lineEnd);
            dos.writeBytes(lineEnd);
            bytesAvailable = fileInputStream.available();
            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            buffer = new byte[bufferSize];
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);
            while (bytesRead > 0)
            {
                dos.write(buffer, 0, bufferSize);
                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);
            }
            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            int serverResponseCode = conn.getResponseCode();
            String serverResponseMessage = conn.getResponseMessage();
            Log.i("Upload Data: ", "HTTP Response is : " + serverResponseMessage + ": " + serverResponseCode);
            if(serverResponseMessage.equals("OK") && serverResponseCode==200)Toast.makeText(getApplicationContext(),"Upload successful",Toast.LENGTH_SHORT).show();
            fileInputStream.close();
            dos.flush();
            dos.close();
        } catch (Exception e)
        {
            System.out.println("Error establishing a connection");
        }
    }

    public void startRecording()
    {
        SimpleDateFormat sdf = new SimpleDateFormat("HH_mm_ss__dd_MM_yyyy");
        String currentTimestamp = sdf.format(new Date());

        lastFile = rootDirectory+"/AudioData/Sample_"+currentTimestamp+".amr";
        try
        {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
            mediaRecorder.setOutputFile(lastFile);
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        startStopRecording.setText("Stop Recording");
        recording = true;
    }

    public void stopRecording()
    {
        if(mediaRecorder!=null)
        {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
        }
        startStopRecording.setText("Start Recording");
        recording = false;
    }

    public void createDirectoryIfNotExists()
    {
        File csvFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/AcousticData/CSVData");
        File audioFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/AcousticData/AudioData");
        if(!csvFolder.exists())csvFolder.mkdirs();
        if(!audioFolder.exists())audioFolder.mkdirs();
        rootDirectory = Environment.getExternalStorageDirectory().getAbsolutePath() + "/AcousticData";
    }

    public void getSoundSample()
    {
        startRecording();
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                stopRecording();
                if(sendData.isChecked())serverTest();
            }
        }, 5000);
    }

    public void testAsyncTask()
    {
        try
        {
            String uploadResult = new uploadSampleTask().execute(lastFile).get();
            Toast.makeText(getApplicationContext(),uploadResult,Toast.LENGTH_SHORT).show();
        }
        catch (InterruptedException | ExecutionException e)
        {
            e.printStackTrace();
        }
    }

    public int createDeviceID()
    {
        Random r = new Random();
        int Low = 1000;
        int High = 10000;
        return r.nextInt(High-Low) + Low;
    }

}
