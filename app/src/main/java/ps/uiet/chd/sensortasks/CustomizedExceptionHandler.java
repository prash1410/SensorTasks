package ps.uiet.chd.sensortasks;

import android.annotation.SuppressLint;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CustomizedExceptionHandler implements Thread.UncaughtExceptionHandler {

    private Thread.UncaughtExceptionHandler defaultUEH;
    private String localPath;
    public CustomizedExceptionHandler(String localPath)
    {
        this.localPath = localPath;
        this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
    }

    public void uncaughtException(Thread t, Throwable e)
    {
        final Writer stringBuffSync = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(stringBuffSync);
        e.printStackTrace(printWriter);
        String stacktrace = stringBuffSync.toString();
        printWriter.close();
        if (localPath != null) writeToFile(stacktrace);

    }

    private void writeToFile(String currentStacktrace)
    {
        try
        {
            File crashLogsFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/AcousticData/CrashLogs");
            if (!crashLogsFolder.exists()) crashLogsFolder.mkdirs();
            @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
            Date date = new Date();
            String filename = dateFormat.format(date) + ".text";
            File reportFile = new File(crashLogsFolder, filename);
            FileWriter fileWriter = new FileWriter(reportFile);
            fileWriter.append(currentStacktrace);
            fileWriter.flush();
            fileWriter.close();
        } catch (Exception e) { Log.e("ExceptionHandler", e.getMessage()); }
    }

}
