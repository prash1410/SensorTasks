package ps.uiet.chd.sensortasks;

import android.os.AsyncTask;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class uploadSampleTask extends AsyncTask<String, Integer, Integer>
{

    @Override
    protected Integer doInBackground(String... strings)
    {
        int serverResponseCode;
        String lastFile = strings[0];
        String deviceIDLocationBundle = strings[1];
        String PHP_URL = "http://192.168.42.67/PHPScripts/test.php";
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
            conn.setConnectTimeout(500);
            conn.setReadTimeout(500);
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("ENCTYPE", "multipart/form-data");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            dos = new DataOutputStream(conn.getOutputStream());
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"uploaded_file\"; filename=\"" + lastFile+";"+deviceIDLocationBundle + "\"" + lineEnd);
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
            serverResponseCode = conn.getResponseCode();
            String serverResponseMessage = conn.getResponseMessage();
            Log.i("AcousticSampleUpload: ", "HTTP Response is : " + serverResponseMessage + ": " + serverResponseCode);
            fileInputStream.close();

            /*
            InputStream input = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
            {
                result.append(line);
            }
            output = result.toString();
            input.close();
            */

            dos.flush();
            dos.close();
        } catch (IOException e)
        {
            e.printStackTrace();
            return -1;
        }

        return serverResponseCode;
    }

    @Override
    protected void onPostExecute(Integer integer) {
        super.onPostExecute(integer);
    }
}
