package ps.uiet.chd.sensortasks;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.widget.Toast;

public class AccelerometerBackgroundService extends Service
{

    @Override
    public void onCreate()
    {
        super.onCreate();
        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show();
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
        return Service.START_STICKY_COMPATIBILITY;
    }

    @Override
    public void onDestroy()
    {
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
    }
}
