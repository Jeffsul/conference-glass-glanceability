package com.syde461.group6.glanceability;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class MainActivity extends Activity {
    private static final String TAG = "glass-conference-glanceability";

    private static final String API_PATH = "http://conference-glass.herokuapp.com/";
    private static final String SEND_ACTIVATION_ACTION = "web/activate";
    private static final String SEND_RESULT_ACTION = "web/continue";

    private enum Question {
        PRIMARY, SECONDARY, TERTIARY, IMAGE, DONE
    }

    private ImageView maskHolder;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        sendActivation();

        setContentView(R.layout.trial_mask);
        maskHolder = (ImageView) findViewById(R.id.mask_holder);
    }

    private void setupTrial(Question question, long duration, String primaryWord,
                            String secondaryWord, String tertiaryWord, String imageUrl) {

    }

    /** Send activation request to the server. */
    private void sendActivation() {
        new ServerTask().execute(API_PATH + SEND_ACTIVATION_ACTION);
    }

    /** Passes stimulus timestamp back to server. */
    private void sendResult() throws JSONException {
        long timestamp = System.currentTimeMillis();
        JSONObject data = new JSONObject();
        data.put("display_timestamp", timestamp);
        new ServerTask(data).execute(API_PATH + SEND_RESULT_ACTION);
    }

    private class ServerTask extends AsyncTask<String, String, String> {
        private final JSONObject data;

        public ServerTask() {
            this.data = null;
        }

        public ServerTask(JSONObject data) {
            this.data = data;
        }

        @Override
        protected String doInBackground(String... uri) {
            HttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(uri[0]);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");
            // Check if JSON data should accompany the request.
            if (data != null) {
                try {
                    httpPost.setEntity(new StringEntity(data.toString()));
                } catch (UnsupportedEncodingException e) {
                    Log.e(TAG, "Error setting POST entity.", e);
                }
            }
            try {
                ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
                    @Override
                    public String handleResponse(HttpResponse httpResponse) throws IOException {
                        return EntityUtils.toString(httpResponse.getEntity());
                    }
                };
                return httpClient.execute(httpPost, responseHandler);
            } catch (Exception e) {
                Log.e(TAG, "Error executing POST request.", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.i(TAG, "Server response: " + result);
            // Handle response: the next trial.
            try {
                JSONObject resp = new JSONObject(result);
                Question question = parseQuestion(resp.getString("question_type"));
                if (question == Question.DONE) {
                    // Experiment is over. Do nothing.
                    return;
                }
                long duration = resp.getLong("duration");
                String primaryWord = resp.getString("primary_word");
                String secondaryWord = resp.getString("secondary_word");
                String tertiaryWord = resp.getString("tertiary_word");
                String imageUrl = resp.getString("image_url");

                setupTrial(question, duration, primaryWord, secondaryWord, tertiaryWord, imageUrl);
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing JSON response.", e);
            }
        }
    }

    /** Utility method to parse enum type from String. */
    private static Question parseQuestion(String questionType) {
        Question[] values = Question.values();
        for (Question type : values) {
            if (type.toString().equals(questionType)) {
                return type;
            }
        }
        return Question.DONE;
    }
}
