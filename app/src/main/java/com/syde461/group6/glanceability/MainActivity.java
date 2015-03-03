package com.syde461.group6.glanceability;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;

public class MainActivity extends Activity {
    private static final String TAG = "glass-conference-glanceability";

    private static final String API_PATH = "";
    private static final String SEND_ACTIVATION_ACTION = "";
    private static final String SEND_RESULT_ACTION = "";

    private ImageView maskHolder;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        sendActivation();

        setContentView(R.layout.trial_mask);
        maskHolder = (ImageView) findViewById(R.id.mask_holder);
    }

    /** Send activation request to the server. */
    private void sendActivation() {
        new SendActivationTask().execute(API_PATH + SEND_ACTIVATION_ACTION);
    }

    /** Passes stimulus timestamp back to server. */
    private void sendResult() {
        new SendResultTask().execute(API_PATH + SEND_RESULT_ACTION);
    }

    private static class SendActivationTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... uri) {
            HttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(uri[0]);
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.e(TAG, "Activation response: " + result);
        }
    }

    private static class SendResultTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... uri) {
            HttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(uri[0]);
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.e(TAG, "Result response: " + result);
        }
    }
}
