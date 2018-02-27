package ps.uiet.chd.sensortasks;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.widget.Toast;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class BackgroundService extends JobService
{
    public Context context=this;
    public Handler handler = null;
    public Runnable runnable = null;
    @Override
    public void onCreate()
    {
        super.onCreate();
        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show();
        performTask();
    }


    @Override
    public void onDestroy()
    {
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
        super.onDestroy();
    }

    @Override
    public boolean onStartJob(JobParameters params)
    {
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    public void performTask()
    {
        handler = new Handler();
        runnable = new Runnable()
        {
            public void run()
            {
                Toast.makeText(context, "Service running", Toast.LENGTH_SHORT).show();
                handler.postDelayed(runnable, 3000);
            }
        };
        handler.postDelayed(runnable, 0);
    }
}
