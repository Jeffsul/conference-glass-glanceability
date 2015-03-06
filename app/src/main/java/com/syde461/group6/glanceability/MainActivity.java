package com.syde461.group6.glanceability;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

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
import java.net.MalformedURLException;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String TAG = "glass-conference-glanceability";

    private static final boolean TEST_MODE = false;
    private static final Layout TEST_LAYOUT = Layout.DYNAMIC;

    private static final String API_PATH = "http://conference-glass.herokuapp.com/";
    private static final String SEND_ACTIVATION_ACTION = "web/activate";
    private static final String SEND_RESULT_ACTION = "web/continue";

    private static final long MASK_DURATION = 200;

    private static final String RANDOM_CHARS = "<>+-=|^";
    private static final int NONWORD_LENGTH = 8;

    private enum Question {
        PRIMARY, SECONDARY, TERTIARY, IMAGE, DONE
    }

    private enum Layout {
        STATIC, DYNAMIC
    }

    private Layout layoutType;

    private Handler handler;

    private View bodyLayout;
    private View fixationRect;

    private TextView primaryTextView;
    private TextView secondaryTextView;
    private TextView tertiaryTextView;
    private ImageView imageView;

    private long displayTimestamp;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // Stop the display from dimming.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.fixation_rect);

        // For posting delayed requests:
        handler = new Handler();

        sendActivation();
    }

    private void setLayoutType(Layout layoutType) {
        this.layoutType = layoutType;
        int layoutRes;
        switch (layoutType) {
            case STATIC:
                layoutRes = R.layout.trial_mask;
                break;
            case DYNAMIC:
                layoutRes = R.layout.trial_mask_dynamic;
                break;
            default:
                Log.e(TAG, "Layout type is null.");
                return;
        }
        setContentView(layoutRes);

        primaryTextView = (TextView) findViewById(R.id.user_name);
        secondaryTextView = (TextView) findViewById(R.id.user_employer);
        tertiaryTextView = (TextView) findViewById(R.id.user_position);
        imageView = (ImageView) findViewById(R.id.user_profile);

        bodyLayout = findViewById(R.id.body_layout);
        fixationRect = findViewById(R.id.fixation_rect);
    }

    private void showFixationRect() {
        bodyLayout.setVisibility(View.INVISIBLE);
        fixationRect.setVisibility(View.VISIBLE);
    }

    private void showMask() {
        showLayout(makeNonWord(), makeNonWord(), makeNonWord(), null);
    }

    private void showLayout(String primaryWord, String secondaryWord, String tertiaryWord, Bitmap image) {
        bodyLayout.setVisibility(View.VISIBLE);
        fixationRect.setVisibility(View.INVISIBLE);

        primaryTextView.setText(primaryWord);
        secondaryTextView.setText(secondaryWord);
        tertiaryTextView.setText(tertiaryWord);
        if (image == null) {
            imageView.setImageResource(getBlankProfile(layoutType));
        } else {
            imageView.setImageBitmap(image);
        }
    }

    private void setupTrial(final long duration, final String primaryWord,
            final String secondaryWord, final String tertiaryWord, final Bitmap image) {
        showMask();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                showLayout(primaryWord, secondaryWord, tertiaryWord, image);
                displayTimestamp = System.currentTimeMillis();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showMask();
                        sendResult();
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                showFixationRect();
                            }
                        }, MASK_DURATION);
                    }
                }, duration);
            }
        }, MASK_DURATION);
    }

    /** Send activation request to the server. */
    private void sendActivation() {
        if (TEST_MODE) {
            setLayoutType(TEST_LAYOUT);
            setupTrial(10000, "Jeffrey Sullivan", "Google", "Developer", null);
            return;
        }
        new ServerTask().execute(API_PATH + SEND_ACTIVATION_ACTION);
    }

    /** Passes stimulus timestamp back to server. */
    private void sendResult() {
        if (TEST_MODE) {
            return;
        }
        JSONObject data = new JSONObject();
        try {
            data.put("display_timestamp", displayTimestamp);
        } catch (JSONException e) {
            Log.e(TAG, "JSON Error sending result.", e);
        }
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

                if (layoutType == null) {
                    Layout newLayoutType;
                    if (resp.getString("display_type").equals(Layout.DYNAMIC.toString())) {
                        newLayoutType = Layout.DYNAMIC;
                    } else {
                        newLayoutType = Layout.STATIC;
                    }
                    setLayoutType(newLayoutType);
                }

                long duration = resp.getLong("duration");
                String primaryWord = resp.getString("primary_word");
                String secondaryWord = resp.getString("secondary_word");
                String tertiaryWord = resp.getString("tertiary_word");
                String imageUrl = resp.getString("image_url");

                new DownloadImageTask(duration, primaryWord, secondaryWord, tertiaryWord).execute(imageUrl);
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing JSON response.", e);
            }
        }
    }

    private class DownloadImageTask extends AsyncTask<String, String, Bitmap> {
        private final long duration;
        private final String primaryWord;
        private final String secondaryWord;
        private final String tertiaryWord;

        public DownloadImageTask(long duration, String primaryWord, String secondaryWord, String tertiaryWord) {
            this.duration = duration;
            this.primaryWord = primaryWord;
            this.secondaryWord = secondaryWord;
            this.tertiaryWord = tertiaryWord;
        }

        @Override
        protected Bitmap doInBackground(String... imageUrl) {
            Bitmap bmp = null;
            try {
                URL url = new URL(imageUrl[0]);
                bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
            } catch (MalformedURLException e) {
                Log.e(TAG, "Error loading image.", e);
            } catch (IOException e) {
                Log.e(TAG, "Error loading image.", e);
            }
            if (layoutType == Layout.DYNAMIC) {
                bmp = getRoundedCornerBitmap(bmp);
            }
            return bmp;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            setupTrial(duration, primaryWord, secondaryWord, tertiaryWord, result);
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

    /** Utility method to generate random nonword of NONWORD_LENGTH from RANDOM_CHARS. */
    private static String makeNonWord() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < NONWORD_LENGTH; i++) {
            sb.append(RANDOM_CHARS.charAt((int)(Math.random() * RANDOM_CHARS.length())));
        }
        return sb.toString();
    }

    private static int getBlankProfile(Layout layoutType) {
        switch (layoutType) {
            case DYNAMIC:
                return R.drawable.blank_circle;
            case STATIC:
            default:
                return R.drawable.blank_box;
        }
    }

    /** Credit: http://stackoverflow.com/questions/2459916/how-to-make-an-imageview-with-rounded-corners */
    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xffffffff;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawOval(rectF, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }
}
