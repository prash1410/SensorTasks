package ps.uiet.chd.sensortasks;

import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

public class WekaActivity extends AppCompatActivity implements View.OnClickListener
{
    static Classifier classifier = null;
    static String activityLogs = "";
    ArrayList<Double> xMagList = new ArrayList<>();
    ArrayList <Double> yMagList = new ArrayList<>();
    ArrayList <Double> zMagList = new ArrayList<>();
    ArrayList <Double> resultant = new ArrayList<>();
    double xAngleGravity = 0,yAngleGravity = 0,zAngleGravity = 0;
    double gravity[] = new double[3];
    double initX,initY,initZ;
    boolean runnableRunning = false;
    Button startStopRunnable;
    RelativeLayout wekaLayout;
    static int sampleCount = 0;

    SensorManager AccelerometerManager;
    Sensor Accelerometer;
    SensorEventListener AccelerometerListener;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weka);
        startStopRunnable = (Button)findViewById(R.id.startStopRunnable);
        startStopRunnable.setOnClickListener(this);
        wekaLayout = (RelativeLayout)findViewById(R.id.WekaLayout);
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

                if(sampleCount==34)
                {
                    produceFinalResults();
                }
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
        activityLogs = "";
    }

    public void trimArrayLists()
    {
        ArrayList <Double> xTempList = xMagList;
        ArrayList <Double> yTempList = yMagList;
        ArrayList <Double> zTempList = zMagList;
        ArrayList <Double> tempResultant = resultant;

        xMagList.clear();
        yMagList.clear();
        zMagList.clear();
        resultant.clear();

        for(int i=5;i<xTempList.size();i++)
        {
            xMagList.add(xTempList.get(i));
            yMagList.add(yTempList.get(i));
            zMagList.add(zTempList.get(i));
            resultant.add(tempResultant.get(i));
        }
        Toast.makeText(getApplicationContext(),""+xMagList.size(),Toast.LENGTH_SHORT).show();
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

    public void produceFinalResults()
    {
        String result = wekaPredict(calculateVariance(),getZeroCrossings(xMagList),getZeroCrossings(yMagList),getZeroCrossings(zMagList));
        SimpleDateFormat simpleDateFormat;
        Calendar calender = Calendar.getInstance();
        simpleDateFormat = new SimpleDateFormat("hh:mm:s a");
        String time = simpleDateFormat.format(calender.getTime());
        activityLogs += result+" "+time+"\n";
        Toast.makeText(getApplicationContext(),result,Toast.LENGTH_LONG).show();
    }

}
