package ps.uiet.chd.sensortasks;

import android.content.res.AssetManager;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

public class WekaActivity extends AppCompatActivity implements View.OnClickListener
{
    private Sample[] mSamples = new Sample[]{
            new Sample(1, 0, new double[]{5, 3.5, 2, 0.4}), // should be in the setosa domain
            new Sample(2, 1, new double[]{5.6, 3, 3.5, 1.2}), // should be in the versicolor domain
            new Sample(3, 2, new double[]{7, 3, 6.8, 2.1}) // should be in the virginica domain
    };
    private static final String WEKA_TEST = "WekaTest";
    private Random mRandom = new Random();
    private Classifier classifier = null;
    Button loaderButton;
    RelativeLayout wekaLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weka);
        loaderButton = (Button)findViewById(R.id.loaderButton);
        loaderButton.setOnClickListener(this);
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
        final Attribute attributeVariance = new Attribute("Variance");
        final Attribute attributeXZeroCrossings = new Attribute("xZeroCrossings");
        final Attribute attributeYZeroCrossings = new Attribute("yZeroCrossings");
        final Attribute attributeZZeroCrossings = new Attribute("zZeroCrossings");
        final List<String> classes = new ArrayList<String>() {
            {
                add("Still"); // cls nr 1
                add("Driving"); // cls nr 2
                add("Walking"); // cls nr 3
            }
        };

        ArrayList<Attribute> attributeList = new ArrayList<Attribute>(2) {
            {
                add(attributeVariance);
                add(attributeXZeroCrossings);
                add(attributeYZeroCrossings);
                add(attributeZZeroCrossings);
                Attribute attributeClass = new Attribute("@@class@@", classes);
                add(attributeClass);
            }
        };

        Instances dataUnpredicted = new Instances("Activity", attributeList, 1);
        // last feature is target variable
        dataUnpredicted.setClassIndex(dataUnpredicted.numAttributes() - 1);

        // create new instance: this one should fall into the setosa domain
        final Sample s = mSamples[mRandom.nextInt(mSamples.length)];
        DenseInstance newInstance = new DenseInstance(dataUnpredicted.numAttributes()) {
            {
                setValue(attributeVariance, s.features[0]);
                setValue(attributeXZeroCrossings, s.features[1]);
                setValue(attributeYZeroCrossings, s.features[2]);
                setValue(attributeZZeroCrossings, s.features[3]);
            }
        };
        // reference to dataset
        newInstance.setDataset(dataUnpredicted);

        try
        {
            double result = classifier.classifyInstance(newInstance);
            String className = classes.get(new Double(result).intValue());
            String msg = "Nr: " + s.nr + ", predicted: " + className + ", actual: " + classes.get(s.label);
            Log.d(WEKA_TEST, msg);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class Sample
    {
        public int nr;
        public int label;
        public double [] features;

        public Sample(int _nr, int _label, double[] _features) {
            this.nr = _nr;
            this.label = _label;
            this.features = _features;
        }

        @Override
        public String toString()
        {
            return "Nr " +
                    nr +
                    ", cls " + label +
                    ", feat: " + Arrays.toString(features);
        }
    }
}
