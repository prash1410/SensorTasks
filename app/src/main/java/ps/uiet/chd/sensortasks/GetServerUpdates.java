package ps.uiet.chd.sensortasks;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class GetServerUpdates extends AsyncTask<String, Integer, Integer>
{

    @SuppressLint("SimpleDateFormat")
    @Override
    protected Integer doInBackground(String... strings)
    {
        String deviceID = strings[0];
        String PHP_URL = "http://192.168.42.67/PHPScripts/fetchResults.php";
        InputStream inputStream = null;
        String line;
        String result = null;
        try
        {
            HttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(PHP_URL);
            List<NameValuePair> nameValuePairs = new ArrayList<>();
            nameValuePairs.add(new BasicNameValuePair("DeviceID", deviceID));
            try
            {
                httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            }
            catch (UnsupportedEncodingException e)
            {
                e.printStackTrace();
            }
            org.apache.http.HttpResponse response = httpClient.execute(httpPost);
            HttpEntity entity = response.getEntity();
            inputStream = entity.getContent();

        } catch (Exception e)
        {
            System.out.println("Error establishing a connection");
        }

        try
        {
            assert inputStream != null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "iso-8859-1"), 8);
            StringBuilder stringBuilder = new StringBuilder();
            while ((line = reader.readLine()) != null)
            {
                stringBuilder.append(line).append("\n");
            }
            result = stringBuilder.toString();
            System.out.print(result);
            inputStream.close();

        } catch (Exception e)
        {
            System.out.println("Error fetching data");
        }

        StringBuilder temp1= new StringBuilder();
        StringBuilder temp2= new StringBuilder();
        StringBuilder temp3= new StringBuilder();
        String[] arr1,arr2,arr3;
        try
        {
            JSONArray jsonArray = new JSONArray(result);
            int count = jsonArray.length();
            for (int i = 0; i < count; i++)
            {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                temp1.append(jsonObject.getString("filename")).append(";");
                temp2.append(jsonObject.getString("filesize")).append(";");
                temp3.append(jsonObject.getString("timestamp")).append(";");
            }
            arr1 = temp1.toString().split(";");
            arr2 = temp2.toString().split(";");
            arr3 = temp3.toString().split(";");

            File serverFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/AcousticData/ServerData/FetchedData.txt");
            FileWriter fileWriter = new FileWriter(serverFile,true);
            for(int i=0;i<arr1.length;i++)
            {
                fileWriter.write(arr1[i]+"   "+arr2[i]+"   "+arr3[i]+"\n");
            }
            fileWriter.flush();
            fileWriter.close();

        } catch (Exception e)
        {
            Log.e("", "" + e.getMessage());
        }
        return null;
    }

    @Override
    protected void onPostExecute(Integer integer)
    {
        super.onPostExecute(integer);
    }
}
