package ps.uiet.chd.sensortasks;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import java.util.Objects;

public class dataCollection extends AppCompatActivity implements View.OnClickListener
{
    static String label = "Walking";
    boolean accelerometerActive = false;
    Button toggleAccelerometer;
    RadioGroup labelRG;
    RadioButton walkingRB, neitherRB, drivingRB;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_collection);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        toggleAccelerometer = findViewById(R.id.toggleAccelerometer);
        toggleAccelerometer.setOnClickListener(this);
        labelRG = findViewById(R.id.activityRG);
        walkingRB = findViewById(R.id.walkingRB);
        walkingRB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if(isChecked)label = "Walking";
            }
        });
        neitherRB = findViewById(R.id.neitherRB);
        neitherRB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked)label = "Neither";
            }
        });
        drivingRB = findViewById(R.id.drivingRB);
        drivingRB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked)label = "Driving";
            }
        });
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onClick(View v)
    {
        switch (v.getId())
        {
            case R.id.toggleAccelerometer:
            {
                if(!accelerometerActive)
                {
                    Intent intent = new Intent(this, dataCollectionService.class);
                    intent.putExtra("Label",label);
                    startService(intent);
                    toggleAccelerometer.setText("Stop accelerometer");
                    disableRadioGroup();
                    accelerometerActive = true;
                }
                else
                {
                    stopService(new Intent(this, dataCollectionService.class));
                    toggleAccelerometer.setText("Start accelerometer");
                    enableRadioGroup();
                    accelerometerActive = false;
                }
                break;
            }
            default:
                break;
        }
    }

    public void disableRadioGroup()
    {
        for (int i = 0; i < labelRG.getChildCount(); i++) labelRG.getChildAt(i).setEnabled(false);
    }

    public void enableRadioGroup()
    {
        for (int i = 0; i < labelRG.getChildCount(); i++) labelRG.getChildAt(i).setEnabled(true);
    }
}
