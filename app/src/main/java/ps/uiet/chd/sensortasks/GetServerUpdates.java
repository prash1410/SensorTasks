package ps.uiet.chd.sensortasks;

import android.os.AsyncTask;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class GetServerUpdates extends AsyncTask<String, Integer, Integer>
{

    @Override
    protected Integer doInBackground(String... strings)
    {
        InputStream inputStream = null;
        String line;
        String result = null;
        try
        {
            HttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(PHP_URL+"ConfusionMatrix.php"); //Connect with the server
            List<NameValuePair> nameValuePairs = new ArrayList<>(2);
            nameValuePairs.add(new BasicNameValuePair("Classifier", Classifier+"_cm"));
            String Temp = Title.replaceAll("\\s+","");
            String[] ScriptName = Temp.split("Classifier");
            nameValuePairs.add(new BasicNameValuePair("ScriptName", ScriptName[0]+".py"));
            try
            {
                httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            } catch (UnsupportedEncodingException e)
            {
                e.printStackTrace();
            }
            org.apache.http.HttpResponse response = httpClient.execute(httpPost); //Get the file on the server
            HttpEntity entity = response.getEntity();
            inputStream = entity.getContent(); //Get the result of PHP script execution in Android inputstream

        } catch (Exception e)
        {
            System.out.println("Error establishing a connection");
        }

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "iso-8859-1"), 8);
            StringBuilder stringBuilder = new StringBuilder();
            while ((line = reader.readLine()) != null) //Convert InputStream into String
            {
                stringBuilder.append(line + "\n");
            }
            result = stringBuilder.toString();
            System.out.print(result);
            inputStream.close();

        } catch (Exception e)
        {
            System.out.println("Error fetching data");
        }

        String temp1="Output Class:",temp2="DOS:",temp3="Normal:",temp4="Probe:",temp5="R2L:",temp6="U2R:";
        String[] arr1,arr2,arr3,arr4,arr5,arr6;
        try
        {
            JSONArray jsonArray = new JSONArray(result);
            int count = jsonArray.length();
            for (int i = 0; i < count; i++)
            {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                temp1 += jsonObject.getString("Output_Class_CM") + ":";
                temp2 += jsonObject.getString("DOS_CM") + ":";
                temp3 += jsonObject.getString("Normal_CM") + ":";
                temp4 += jsonObject.getString("Probe_CM") + ":";
                temp5 += jsonObject.getString("R2L_CM") + ":";
                temp6 += jsonObject.getString("U2R_CM") + ":";
            }
            arr1 = temp1.split(":");
            arr2 = temp2.split(":");
            arr3 = temp3.split(":");
            arr4 = temp4.split(":");
            arr5 = temp5.split(":");
            arr6 = temp6.split(":");
            int LeftMargin=0;
            int TopMargin=0;
            int j=0;
            for(int i=1;i<=5;i++)
            {
                for(;j<6*i;j++)
                {

                }
                LeftMargin = 0;
                TopMargin = TopMargin+136+2;
            }
            j=0;
            for(int i=0;i<30;i=i+6)
            {
                
                j++;
            }

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
