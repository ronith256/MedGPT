package com.lucario.gpt;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MessageView extends AppCompatActivity {
    RecyclerView recyclerView;
    EditText messageEditText;
    ImageButton sendButton;
    List<Message> messageList;

    JSONArray messageArray;
    MessageAdapter messageAdapter;

    TextView timerText;
    public static final MediaType JSON
            = MediaType.get("application/json; charset=utf-8");
    private OkHttpClient client = new OkHttpClient();

    public static int sessionTimeout;
    private File chatFile;
    private static final String BASE_URL = "http://184.72.112.114";
    private Chat chat;
    private List<Chat> mChatList;

    private String username, password, sessionKey;

    TimerTask updateTimeTask;

    Timer mTimer;
    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        username = getSharedPreferences("cred", MODE_PRIVATE).getString("user", "null");
        password = getSharedPreferences("cred", MODE_PRIVATE).getString("password", "null");
        setContentView(R.layout.message_view);
        timerText = findViewById(R.id.timeout);
        mChatList = (List<Chat>) getIntent().getSerializableExtra("chatList");
        chat = mChatList.get(getIntent().getIntExtra("chat", 0));
        chatFile = chat.getChatArray();
        messageList = new ArrayList<>();
        messageArray = new JSONArray();
        loadChatList(chatFile);
        mTimer = new Timer();
        updateTimeTask = new TimerTask() {
            @Override
            public void run() {
                if(System.currentTimeMillis() - chat.getSessionStartTime() > 600000){
                    mTimer.purge();
                }
                String time = getTimeDiffString(chat.getSessionStartTime());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        timerText.setText(time);
                    }
                });
            }
        };
        if(chat.getSessionStartTime()!=0){
            mTimer.schedule(updateTimeTask, 0,1);
        }

        recyclerView = findViewById(R.id.recycler_view);
        messageEditText = findViewById(R.id.message_edit_text);
        sendButton = findViewById(R.id.send_btn);

        //setup recycler view
        messageAdapter = new MessageAdapter(messageList);
        recyclerView.setAdapter(messageAdapter);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        recyclerView.setLayoutManager(llm);

        sendButton.setOnClickListener((v)->{
            String question = messageEditText.getText().toString().trim().toLowerCase(Locale.ROOT);
            addToChat(question,Message.SENT_BY_ME);
            messageEditText.setText("");
            callAPI(question);
        });
    }

    void addToChat(String message,String sentBy){
        runOnUiThread(() -> {
            messageList.add(new Message(message,sentBy));
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

    private String getTimeDiffString(long originalTime) {
        // Calculate the time difference in milliseconds
        long currentTime = System.currentTimeMillis();
        long diffMs = currentTime - originalTime;

        // Calculate the remaining time in milliseconds
        long remainingMs = 600000 - diffMs;

        // Convert remaining time to minutes and seconds
        long remainingMinutes = remainingMs / 60000;
        long remainingSeconds = (remainingMs % 60000) / 1000;

        String timeString = String.format("%02d:%02d", remainingMinutes, remainingSeconds);

        return timeString;
    }


    void addResponse(String response, boolean failed){
        messageList.remove(messageList.size()-1);
        if(failed){
            addToChat(response,Message.FAILED_RESPONSE);
        } else {
            addToChat(response,Message.SENT_BY_BOT);
        }
        if(response.length() > 20){
            chat.setLatestChat(response.substring(0, 17) + "...");
        } else {
            chat.setLatestChat(response);
        }
        mChatList.set(chat.getChatId()-1, chat);
        saveChatList("chat_list.ser", mChatList);
    }

    void callAPI(String question){
        if(System.currentTimeMillis() - chat.getSessionStartTime() > 600000 && chat.getSessionKey() != null){
            showSessionKeyExpiredDialog();
        }

        messageList.add(new Message("Typing... ",Message.SENT_BY_BOT));
        if(chat.getChatName().equals("null") || !(chat.getChatName().length() > 1)){
            if(question.length() > 15){
                chat.setChatName(question.substring(0,15));}
            else{
                chat.setChatName(question);}
            mChatList.set(chat.getChatId()-1, chat);
            saveChatList("chat_list.ser", mChatList);
        }

        if(chat.getSessionKey()==null){
            chat.setFirstPrompt(true);
        }

        if(chat.isFirstPrompt()){
            startSession(username, password, 600, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject jsonObject = new JSONObject(responseBody);
                            JSONObject session = jsonObject.getJSONObject("session-key");
                            chat.setSessionKey(session.getString("access_token"));
                            System.out.println(chat.getSessionKey());
                            String message = jsonObject.getString("message");
                            chat.setSessionStartTime(System.currentTimeMillis());
                            mTimer.schedule(updateTimeTask, 0,1);
                            sendConvoMessage(chat.getSessionKey(), question);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        addResponse("Could not acquire a session key " + response.body().string(), true);
                        return;
                    }
                }
            });
            chat.setFirstPrompt(false);
        } else {
            if(chat.getSessionKey()!=null){
                sendConvoMessage(chat.getSessionKey(), question);
            } else {
                addResponse("Session key is null", true);
            }
        }
    }

    private void sendConvoMessage(String sessionKey, String message) {
        String url = BASE_URL + "/convo?session_key=" + sessionKey;

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, "{\"message\": \"" + message + "\"}");
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);
                        String message = jsonObject.getString("message");
                        addResponse(message, false);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    addResponse("Failed to get response", true);
                }
            }
        });
    }

    public void startSession(String username, String password, int sessiontime, Callback callback) {
        String url = BASE_URL + "/start-session?sessiontime=" + sessiontime;
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}");
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(callback);
    }

    private void showSessionKeyExpiredDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.session_expired_dialog, null);
        builder.setView(dialogView);

        TextView titleTextView = dialogView.findViewById(R.id.dialog_title);
        titleTextView.setText("Session Key Expired");

        TextView messageTextView = dialogView.findViewById(R.id.dialog_message);
        messageTextView.setText("Do you want to generate a new key?");

        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                chat.setFirstPrompt(true);
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
    }
}