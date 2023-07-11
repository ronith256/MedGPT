package com.lucario.gpt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MessageView extends AppCompatActivity implements MessageAdapter.MessageDoneListener {
    RecyclerView recyclerView;
    EditText messageEditText;
    ImageButton sendButton;
    static List<Message> messageList;
    Thread microphoneThread;
    JSONArray messageArray;
    static MessageAdapter messageAdapter;

    private MediaRecorder mediaRecorder;

    TextView timerText;
    private OkHttpClient client;

    public static int sessionTimeout, httpSessionTimeout;
    private File chatFile;
    public static String BASE_URL = "";
    public static String WS_URL = "";
    private Chat chat;
    private List<Chat> mChatList;

    private String username, password;

    private TimerTask updateTimeTask;

    private Timer mTimer;

    private int prevHttpTimeOut;
    private String audioFilePath;

    private String question;

    long prevTime;
    private static final int PERMISSION_REQUEST_CODE = 111;
    private boolean microphonePermissionGranted = false;
    private static WebSocket webSocket;

    private static final AtomicBoolean microphoneOn = new AtomicBoolean(false);

    @Override
    protected void onStart() {
        super.onStart();
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        username = getSharedPreferences("cred", MODE_PRIVATE).getString("user", "null");
        password = getSharedPreferences("cred", MODE_PRIVATE).getString("password", "null");
        BASE_URL = getSharedPreferences("session", MODE_PRIVATE).getString("ip", "ip here");
        httpSessionTimeout = Integer.parseInt(getSharedPreferences("session", MODE_PRIVATE).getString("http", "300"));
        sessionTimeout = Integer.parseInt(getSharedPreferences("session", MODE_PRIVATE).getString("timeout", "600"));
        prevHttpTimeOut = httpSessionTimeout;
        WS_URL = BASE_URL.replace("http", "ws");

        setContentView(R.layout.message_view);
        client = new OkHttpClient.Builder().readTimeout(httpSessionTimeout, TimeUnit.SECONDS).writeTimeout(httpSessionTimeout, TimeUnit.SECONDS)
                .connectTimeout(httpSessionTimeout, TimeUnit.SECONDS).build();

        ImageButton settingsButton = findViewById(R.id.toolbar_settings);
        settingsButton.setOnClickListener(e -> {
            SettingsDialogFragment dialog = new SettingsDialogFragment();
            dialog.show(getSupportFragmentManager(), "SettingsDialogFragment");
        });

        microphoneThread = new Thread(() -> {
            chat.incrementRecordNumber(); startRecording(chat.getSessionKey());
        });

        timerText = findViewById(R.id.timeout);
        mChatList = (List<Chat>) getIntent().getSerializableExtra("chatList");
        chat = mChatList.get(getIntent().getIntExtra("chat", 0));
        chatFile = chat.getChatArray();
        messageList = new ArrayList<>();
        messageArray = new JSONArray();

        loadChatList(chatFile);

        requestMicrophonePermission();
        mTimer = new Timer();
        updateTimeTask = createTimerTask();

        if (chat.getSessionStartTime() != 0) {
            if (System.currentTimeMillis() - chat.getSessionStartTime() > chat.getSessionTimeOutVal()) {
                timerText.setText("00:00");
            } else {
                mTimer.schedule(updateTimeTask, 0, 1);
            }
        }

        recyclerView = findViewById(R.id.recycler_view);
        messageEditText = findViewById(R.id.message_edit_text);
        sendButton = findViewById(R.id.send_btn);

        //setup recycler view
        messageAdapter = new MessageAdapter(messageList, this);
        recyclerView.setAdapter(messageAdapter);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        recyclerView.setLayoutManager(llm);

        sendButton.setOnClickListener((v) -> {
            String question = messageEditText.getText().toString().trim().toLowerCase(Locale.ROOT);
            question = question.replaceAll("\n", " ");
            addToChat(question, Message.SENT_BY_ME);
            messageEditText.setText("");
            callAPI(question);
        });
    }

    private TimerTask createTimerTask() {
        return new TimerTask() {
            @Override
            public void run() {
                if (System.currentTimeMillis() - chat.getSessionStartTime() > chat.getSessionTimeOutVal()) {
                    if(webSocket!=null){
                        webSocket.close(1000, "Session Closed");
                        webSocket = null;
                    }
                    runOnUiThread(()->timerText.setText("00:00"));
                    stopRecording();
                    updateTimeTask.cancel();
                    mTimer.cancel();
                } else {
                    String time = getTimeDiffString(chat.getSessionStartTime());
                    runOnUiThread(() -> timerText.setText(time));
                }
            }
        };
    }

    private void uploadToS3(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    uploadMP3ToS3(new File(audioFilePath), "AKIAS6FWMJD7YEKOONZL","RGXy3/yO2HHtO2PeGfKgxzUsBhlmwpfIKDKTg+Mh", "medgpt-conversation-recordings", ("mp3s/"+chat.getSessionKey() + "_" + chat.getRecordNumber() + ".3gp"));
                } catch (Exception ignored){
                }
            }
        }).start();
    }

    void addToChat(String message, String sentBy) {
        runOnUiThread(() -> {
            messageList.add(new Message(message, sentBy));
            saveChatList(chatFile.getName(), messageList);
            messageAdapter.notifyDataSetChanged();
            recyclerView.smoothScrollToPosition(messageAdapter.getItemCount());
        });
    }

    private void saveChatList(String name, List saveList) {
        // Write the chat list to a file
        try {
            FileOutputStream fos = openFileOutput(name, MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(saveList);
            oos.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadChatList(File name) {
        // Read the chat list from a file
        try {
            FileInputStream fis = openFileInput(name.getName());
            ObjectInputStream ois = new ObjectInputStream(fis);
            messageList = (ArrayList<Message>) ois.readObject();
            ois.close();
            fis.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
//            Toast.makeText(this, "Load failed", Toast.LENGTH_SHORT).show();
        }
        // Initialize the chat list if it doesn't exist
        if (messageList == null) {
            messageList = new ArrayList<>();
        }
    }

    private void requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_CODE);
        } else {
            microphonePermissionGranted = true;
        }
    }

    // Handle the permission request response
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                microphonePermissionGranted = true;
            } else {
                Toast.makeText(this, "Microphone permission is not granted!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getTimeDiffString(long originalTime) {
        if(System.currentTimeMillis() - chat.getSessionStartTime() > chat.getSessionTimeOutVal()){
            mTimer = null;
            updateTimeTask = null;
            stopRecording();
            return "00:00";
        } else {
            long elapsedMs = System.currentTimeMillis() - originalTime;
            long remainingMs = chat.getSessionTimeOutVal() - elapsedMs;

            // Make sure the remaining time doesn't go below zero
            remainingMs = Math.max(remainingMs, 0);

            long minutes = remainingMs / (60 * 1000);
            long seconds = (remainingMs % (60 * 1000)) / 1000;

            return String.format("%02d:%02d", minutes, seconds);
        }
    }
    void addResponse(String response, boolean failed) {
//        messageList.remove(messageList.size()-1);
        if (failed) {
            addToChat(response, Message.FAILED_RESPONSE);
        } else {
            addToChat(response, Message.SENT_BY_BOT);
        }

        mChatList.set(chat.getChatId() - 1, chat);
        saveChatList("chat_list.ser", mChatList);
    }

    private OkHttpClient createHttpClient() {
        return new OkHttpClient.Builder().readTimeout(httpSessionTimeout, TimeUnit.SECONDS).writeTimeout(httpSessionTimeout, TimeUnit.SECONDS)
                .connectTimeout(httpSessionTimeout, TimeUnit.SECONDS).build();
    }

    void callAPI(String question) {
        if (httpSessionTimeout != prevHttpTimeOut) {
            client = createHttpClient();
            prevHttpTimeOut = httpSessionTimeout;
        }
        if ((System.currentTimeMillis() - chat.getSessionStartTime() > chat.getSessionTimeOutVal()) && chat.getSessionKey() != null) {
            showSessionKeyExpiredDialog(question);
            return;
        }
//        messageList.add(new Message(" ",Message.SENT_BY_BOT));
        if (chat.getChatName().equals("null") || !(chat.getChatName().length() > 1)) {
            if (question.length() > 15) {
                chat.setChatName(question.substring(0, 15));
            } else {
                chat.setChatName(question);
            }
            mChatList.set(chat.getChatId() - 1, chat);
            saveChatList("chat_list.ser", mChatList);
        }

        if (chat.getSessionKey() == null) {
            chat.setFirstPrompt(true);
        }

        if (chat.isFirstPrompt()) {
            startSession(username, password, sessionTimeout, question);
            chat.setFirstPrompt(false);
        }
        else {
            if (chat.getSessionKey() != null) {
                sendConvoMessage(chat.getSessionKey(), question);
            } else {
                addResponse("Session key is null", true);
            }
        }
    }

    private void sendConvoMessage(String sessionKey, String message) {
        if (mediaRecorder == null) {
            if(microphoneThread.getState() != Thread.State.RUNNABLE){
                microphoneThread.start();
            }
        }
        if (webSocket == null) {
            connectWebSocket(sessionKey);
        }
        addResponse("", false);
        prevTime = System.currentTimeMillis();
        question = message;
        sendMessage(message);

    }

    private void connectWebSocket(String sessionKey) {
        OkHttpClient client = createHttpClient();

        Request request = new Request.Builder()
                .url(WS_URL + "/convo/" + sessionKey)
                .build();

//        Request request = new Request.Builder()
//                .url("ws://192.168.19.239:8765")
//                .build();

        WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                // WebSocket connection is established
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                // Process the received message
                if (text.contains("[DONE]")) {
                    runOnUiThread(() -> messageAdapter.notifyItemChanged(messageList.size() - 1));
                    Message msg = messageList.get(messageList.size() - 1);
                    msg.setFinished(true);
                    if (msg.getMessage().length() > 20) {
                        chat.setLatestChat(msg.getMessage().substring(0, 17) + "...");
                    } else {
                        chat.setLatestChat(msg.getMessage());
                    }
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            saveChatList(chatFile.getName(), messageList);
                            mChatList.set(chat.getChatId() - 1, chat);
                            saveChatList("chat_list.ser", mChatList);
                        }
                    }).start();
                } else {
                    messageList.get(messageList.size() - 1).append(text);
//                    if (System.currentTimeMillis() - prevTime > 100) {
//                        prevTime = System.currentTimeMillis();
//
//                    }
                    runOnUiThread(() -> messageAdapter.notifyItemChanged(messageList.size() - 1));
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                System.out.println("Connection closed by server");
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if(System.currentTimeMillis() - chat.getSessionStartTime() > chat.getSessionTimeOutVal()){
                    messageList.remove(messageList.size()-1);
                    connectWebSocket(sessionKey);
                    addResponse("", false);
                    sendMessage(question);
                }
                Toast.makeText(MessageView.this, "Retrying", Toast.LENGTH_SHORT).show();
            }
        };

        webSocket = client.newWebSocket(request, listener);
    }

    private static void sendMessage(String message) {
        if (webSocket != null) {
            // Sending a message
            String jsonMessage = "{\"role\": \"user\", \"message\": \"" + message + "\"}";
            webSocket.send(jsonMessage);
        } else {
            System.out.println("WebSocket is not initialized. Make sure to connectWebSocket first.");
        }
    }


    public void startSession(String username, String password, int sessiontime, String message) {
        String url = BASE_URL + "/start-session?sessiontime=" + sessiontime;
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}");
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);
                        chat.setSessionKey(jsonObject.getString("session-key"));
                        chat.setSessionTimeOutVal(sessionTimeout* 1000L);
                        if (microphonePermissionGranted) {
                            microphoneOn.set(true);
                            microphoneThread.start();
                        }
                        connectWebSocket(chat.getSessionKey());
                        chat.setSessionStartTime(System.currentTimeMillis());
                        mChatList.set(chat.getChatId() - 1, chat);
                        saveChatList("chat_list.ser", mChatList);
                        mTimer = new Timer();
                        updateTimeTask = createTimerTask();
                        mTimer.schedule(updateTimeTask, 0, 1);
                        new Thread(()->sendConvoMessage(chat.getSessionKey(), message)).start();
                        response.close();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
//                    if (microphonePermissionGranted) {
//                        microphoneOn.set(true);
//                        microphoneThread.start();
//                    }
//                    chat.setSessionKey("test");
//                    connectWebSocket(chat.getSessionKey());
//                    chat.setSessionStartTime(System.currentTimeMillis() + (sessionTimeout * 1000L));
//                    mChatList.set(chat.getChatId() - 1, chat);
//                    saveChatList("chat_list.ser", mChatList);
//                    mTimer = new Timer();
//                    updateTimeTask = createTimerTask();
//                    mTimer.schedule(updateTimeTask, 0, 1);
//                    sendConvoMessage(chat.getSessionKey(), message);
                    assert response.body() != null;
                    addResponse("Could not acquire a session key " + response.body().string(), true);
                }
            }
        });
    }


    private void startRecording(String sessionKey) {
        File cacheDir = getCacheDir();
        audioFilePath = cacheDir.getAbsolutePath() + "/" + sessionKey + ".3gp";
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile(audioFilePath);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
           uploadToS3();
        }
    }

    public void uploadMP3ToS3(File file, String accessKey, String secretAccessKey, String bucketName, String objectKey) {
        // Set up the S3 client
        BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretAccessKey);
        AmazonS3Client s3Client = new AmazonS3Client(credentials);
        s3Client.setRegion(Region.getRegion(Regions.DEFAULT_REGION)); // Set the desired region if necessary
        String bucketEndpoint = "https://s3.ap-south-1.amazonaws.com";
        s3Client.setEndpoint(bucketEndpoint);
        // Upload the file to S3
        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectKey, file);
            s3Client.putObject(putObjectRequest);
            Log.i("S3Upload", "Successfully uploaded MP3 file to S3: " + file.getName());
//            Toast.makeText(MessageView.this, "Uploaded", Toast.LENGTH_SHORT).show();
        } catch (AmazonClientException e) {
            e.printStackTrace();
            Log.e("S3Upload", "Failed to upload MP3 file to S3: " + e.getMessage());
        }
    }

    private void showSessionKeyExpiredDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        stopRecording();
        View dialogView = LayoutInflater.from(this).inflate(R.layout.session_expired_dialog, null);
        builder.setView(dialogView);

        TextView titleTextView = dialogView.findViewById(R.id.dialog_title);
        titleTextView.setText("Session Key Expired");

        TextView messageTextView = dialogView.findViewById(R.id.dialog_message);
        messageTextView.setText("Do you want to generate a new key?");

        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startSession(username, password, sessionTimeout, message);
                Toast.makeText(MessageView.this, "New key generated!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        stopRecording();
        startActivity(new Intent(MessageView.this, MainActivity.class));
//        finish();
    }

    @Override
    public void setFirstTime(int position) {
        messageList.get(position).setFirstTime(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                saveChatList(chatFile.getName(), messageList);
            }
        }).start();
    }
}