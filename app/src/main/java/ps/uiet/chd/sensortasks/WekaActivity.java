package ps.uiet.chd.sensortasks;

import android.content.res.AssetManager;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

public class WekaActivity extends AppCompatActivity implements View.OnClickListener
{
    private Classifier classifier = null;
    Button loaderButton,predictButton;
    RelativeLayout wekaLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weka);
        loaderButton = (Button)findViewById(R.id.loaderButton);
        loaderButton.setOnClickListener(this);
        predictButton = (Button)findViewById(R.id.predictButton);
        predictButton.setOnClickListener(this);
        wekaLayout = (RelativeLayout)findViewById(R.id.WekaLayout);
    }

    @Override
    public void onClick(View v)
    {
        switch (v.getId())
        {
            case R.id.loaderButton:
                loadModel();
                break;
            case R.id.predictButton:
                predict();
                break;
            default:
                break;
        }
    }

    public void loadModel()
    {
        AssetManager assetManager = getAssets();
        try
        {
            classifier = (Classifier) weka.core.SerializationHelper.read(assetManager.open("Latest.model"));

        } catch (Exception e)
        {
            e.printStackTrace();
        }
        if(classifier!=null)
        {
            Snackbar snackbar = Snackbar
                    .make(wekaLayout, "Model loaded", Snackbar.LENGTH_LONG)
                    .setAction("OK", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {}
                    });

            snackbar.show();
        }
    }

    public void predict()
    {
        if(classifier==null)
        {
            Snackbar snackbar = Snackbar
                    .make(wekaLayout, "Model not loaded", Snackbar.LENGTH_LONG)
                    .setAction("OK", new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View view) {}
                    });
            snackbar.show();
            return;
        }
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
                    setValue(attributeVariance, 0.61);
                    setValue(attributeXZeroCrossings, 12);
                    setValue(attributeYZeroCrossings, 14);
                    setValue(attributeZZeroCrossings, 10);
                }
            };
            newInstance.setDataset(dataUnpredicted);
            double result = classifier.classifyInstance(newInstance);
            Toast.makeText(getApplicationContext(),""+result,Toast.LENGTH_LONG).show();
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
