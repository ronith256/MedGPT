package com.lucario.gpt;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.print.PdfPrint;
import android.print.PrintAttributes;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MessageView extends AppCompatActivity implements MessageAdapter.MessageDoneListener, PdfPrint.uploadFile{
    RecyclerView recyclerView;
    EditText messageEditText;
    ImageButton sendButton;
    static List<Message> messageList;
    JSONArray messageArray;
    static MessageAdapter messageAdapter;

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

    private String question;

    long prevTime;

    private static final int PERMISSION_REQUEST_CODE = 111;
    private static WebSocket webSocket;
    private ActivityResultLauncher<Intent> launcher;

    private ImageButton endSessionButton;

    private String pdfFile;
    private String name;

    private int consentRequestTimes = 0;
    @Override
    protected void onStart() {
        super.onStart();
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pdfFile = getIntent().getStringExtra("filename");
        name = getIntent().getStringExtra("name");
        username = getSharedPreferences("cred", MODE_PRIVATE).getString("user", "null");
        password = getSharedPreferences("cred", MODE_PRIVATE).getString("password", "null");
        BASE_URL = getSharedPreferences("session", MODE_PRIVATE).getString("ip", "http://13.235.27.136");
        httpSessionTimeout = Integer.parseInt(getSharedPreferences("session", MODE_PRIVATE).getString("http", "300"));
        sessionTimeout = Integer.parseInt(getSharedPreferences("session", MODE_PRIVATE).getString("timeout", "3600"));
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

        endSessionButton = findViewById(R.id.endSession);

        endSessionButton.setOnClickListener(e->{
            Toast.makeText(MessageView.this, "Session Ended", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                OkHttpClient client = new OkHttpClient();

                Request request = new Request.Builder()
                        .url(BASE_URL + "/end-session/" + chat.getSessionKey())
                        .addHeader("accept", "application/json")
                        .post(RequestBody.create(null, new byte[0]))
                        .build();
                try {
                    Response response = client.newCall(request).execute();
                    chat.setSessionStartTime(System.currentTimeMillis() - (chat.getSessionTimeOutVal()+5));
                    saveChatList("chat_list.ser", mChatList);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }).start();
        });


        timerText = findViewById(R.id.timeout);
        mChatList = (List<Chat>) getIntent().getSerializableExtra("chatList");
        chat = mChatList.get(getIntent().getIntExtra("chat", 0));
        chatFile = chat.getChatArray();
        messageList = new ArrayList<>();
        messageArray = new JSONArray();

        loadChatList(chatFile);

        mTimer = new Timer();
        updateTimeTask = createTimerTask();

        new Thread(new Runnable() {
            @Override
            public void run() {
                while(!chat.sessionKeyExists());
                if(chat.getSessionKey()!=null && !chat.getConsent()){
                    new Handler(Looper.getMainLooper()).post(() -> new UploadConsentTask().doInBackground(pdfFile, name));
                }
            }
        }).start();

        if (chat.getSessionStartTime() != 0) {
            if (System.currentTimeMillis() - chat.getSessionStartTime() > chat.getSessionTimeOutVal()) {
                timerText.setText("00:00");
                endSessionButton.setVisibility(View.GONE);
            } else {
                endSessionButton.setVisibility(View.VISIBLE);
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
                    updateTimeTask.cancel();
                    mTimer.cancel();
                } else {
                    String time = getTimeDiffString(chat.getSessionStartTime());
                    runOnUiThread(() -> timerText.setText(time));
                }
            }
        };
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


    // Handle the permission request response
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                Toast.makeText(this, "Microphone permission is not granted!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getTimeDiffString(long originalTime) {
        if(System.currentTimeMillis() - chat.getSessionStartTime() > chat.getSessionTimeOutVal()){
            mTimer = null;
            updateTimeTask = null;
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
        endSessionButton.setVisibility(View.VISIBLE);
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
                    assert response.body() != null;
                    addResponse("Could not acquire a session key " + response.body().string(), true);
                }
            }
        });
    }


    private void showSessionKeyExpiredDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.session_expired_dialog, null);
        builder.setView(dialogView);

        TextView titleTextView = dialogView.findViewById(R.id.dialog_title);
        titleTextView.setText("Session Key Expired");

        TextView messageTextView = dialogView.findViewById(R.id.dialog_message);
        messageTextView.setText("Do you want to generate a new key?");

        builder.setPositiveButton("Yes", (dialog, which) -> {
            startSession(username, password, sessionTimeout, message);
            Toast.makeText(MessageView.this, "New key generated!", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
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


    private class UploadConsentTask {

        public Boolean doInBackground(String... strings) {
            WebView webView = findViewById(R.id.webview);
            webView.setWebViewClient(new WebViewClient(){
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    createWebPrintJob(view);
                }
            });
            webView.setVisibility(View.VISIBLE);
            String filename = strings[0];
            String name = strings[1];
            String currentDateTime = "Date: " + getCurrentDateTime();
            filename = imageToBase64(filename);
            String html = getHtml(currentDateTime, name, filename);
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
            return null;
        }

        private String getHtml(String date, String name, String filename){
            return "<html>\n" +
                    "\n" +
                    "<head>\n" +
                    "<meta http-equiv=Content-Type content=\"text/html; charset=utf-8\">\n" +
                    "<meta name=Generator content=\"Microsoft Word 15 (filtered)\">\n" +
                    "<style>\n" +
                    "<!--\n" +
                    " /* Font Definitions */\n" +
                    " @font-face\n" +
                    "\t{font-family:\"Cambria Math\";\n" +
                    "\tpanose-1:2 4 5 3 5 4 6 3 2 4;}\n" +
                    "@font-face\n" +
                    "\t{font-family:Calibri;\n" +
                    "\tpanose-1:2 15 5 2 2 2 4 3 2 4;}\n" +
                    "@font-face\n" +
                    "\t{font-family:\"Bahnschrift SemiCondensed\";\n" +
                    "\tpanose-1:2 11 5 2 4 2 4 2 2 3;}\n" +
                    "@font-face\n" +
                    "\t{font-family:\"Nirmala UI\";\n" +
                    "\tpanose-1:2 11 5 2 4 2 4 2 2 3;}\n" +
                    "@font-face\n" +
                    "\t{font-family:\"Segoe UI\";\n" +
                    "\tpanose-1:2 11 5 2 4 2 4 2 2 3;}\n" +
                    "@font-face\n" +
                    "\t{font-family:Kartika;}\n" +
                    " /* Style Definitions */\n" +
                    " p.MsoNormal, li.MsoNormal, div.MsoNormal\n" +
                    "\t{margin-top:0in;\n" +
                    "\tmargin-right:0in;\n" +
                    "\tmargin-bottom:8.0pt;\n" +
                    "\tmargin-left:0in;\n" +
                    "\tline-height:107%;\n" +
                    "\tfont-size:11.0pt;\n" +
                    "\tfont-family:\"Calibri\",sans-serif;}\n" +
                    "p\n" +
                    "\t{margin-right:0in;\n" +
                    "\tmargin-left:0in;\n" +
                    "\tfont-size:12.0pt;\n" +
                    "\tfont-family:\"Times New Roman\",serif;}\n" +
                    ".MsoChpDefault\n" +
                    "\t{font-family:\"Calibri\",sans-serif;}\n" +
                    ".MsoPapDefault\n" +
                    "\t{margin-bottom:8.0pt;\n" +
                    "\tline-height:107%;}\n" +
                    " /* Page Definitions */\n" +
                    " @page WordSection1\n" +
                    "\t{size:8.5in 11.0in;\n" +
                    "\tmargin:1.0in .5in 1.0in 31.5pt;}\n" +
                    "div.WordSection1\n" +
                    "\t{page:WordSection1;}\n" +
                    "\n" +
                    " .inline-text {\n" +
                    "    display: inline;\n" +
                    "    margin-right: 10px; /* Adjust the margin as needed */\n" +
                    "  }\n" +
                    "  .input-container {\n" +
                    "    display: inline-block;\n" +
                    "    vertical-align: middle;\n" +
                    "  }\n" +
                    "\n" +
                    "  .image-container {\n" +
                    "    float: left;\n" +
                    "    margin-right: 12px; /* Adjust the margin as needed */\n" +
                    "  }\n" +
                    "  .text-container {\n" +
                    "    overflow: hidden;\n" +
                    "  }\n" +
                    "  .image-text {\n" +
                    "    clear: left;\n" +
                    "    margin: 0;\n" +
                    "    padding: 0;\n" +
                    "  }\n" +
                    "</style>\n" +
                    "\n" +
                    "</head>\n" +
                    "\n" +
                    "<body lang=EN-US style='word-wrap:break-word'>\n" +
                    "\n" +
                    "<div class=WordSection1>\n" +
                    "\n" +
                    "<div>\n" +
                    "  <div class=\"image-container\">\n" +
                    "    <img src=\"data:image/jpeg;base64,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\" width=\"110\" height=\"105\">\n" +
                    "  </div>\n" +
                    "  <div class=\"text-container\">\n" +
                    "    <p class=\"image-text\">\n" +
                    "      <span style=\"font-size: 14.0pt; font-family: 'Bahnschrift SemiCondensed', sans-serif; color: black;\">\n" +
                    "        AMRITA INSTITUTE OF MEDICAL SCIENCES<br>SURVEY QUESTIONAIRE\n" +
                    "      </span>\n" +
                    "    </p>\n" +
                    "  </div>\n" +
                    "</div>\n" +
                    "\n" +
                    "<br>\n" +
                    "<p class=MsoNormal><span style='font-size:12.5pt;line-height:107%'>&nbsp;</span></p>\n" +
                    "<br>\n" +
                    "\n" +
                    "<div>\n" +
                    "  <p class=\"mso-normal inline-text\">\n" +
                    "    <span style=\"font-size: 12.5pt; line-height: 107%;\">Name: " + name + "</span>\n" +
                    "  </p>\n" +
                    "</div>\n" +
                    "\n" +
                    "<p class=MsoNormal><span style='font-size:12.5pt;line-height:107%'>The aim of\n" +
                    "the study is to compare the diagnostic process guided by GPT (computer program)\n" +
                    "with the usual process done by the physicians. Your treatment team, including\n" +
                    "nurses and physicians, will not be exposed to the computer-generated treatment\n" +
                    "plan and will deliver the usual care.</span></p>\n" +
                    "\n" +
                    "<p class=MsoNormal><span style='font-size:12.5pt;line-height:107%'>I hereby\n" +
                    "give my voluntary consent to participate in the Study on the Use of ChatGPT\n" +
                    "Interface in Healthcare by completing the questionnaire provided. </span></p>\n" +
                    "\n" +
                    "<p class=MsoNormal><span style='font-size:12.5pt;line-height:107%'>I agree that\n" +
                    "my data will be used for research purposes only. My participation is entirely\n" +
                    "voluntary, and I have the right to withdraw from the study at any time without\n" +
                    "any consequences. My participation or non-participation will not affect my treatment\n" +
                    "in any way.</span></p>\n" +
                    "\n" +
                    "<p class=MsoNormal><span style='font-size:12.5pt;line-height:107%'>By agreeing\n" +
                    "to participate in this study, I acknowledge that I have read and understand the\n" +
                    "information provided in this consent form.</span></p>\n" +
                    "\n" +
                    "<p class=MsoNormal><span style='font-size:12.5pt;line-height:107%'>                                                                                                                                                            </span></p>\n" +
                    "\n" +
                    "<p class=MsoNormal><span style='font-size:12.5pt;line-height:107%'>Sign:</span></p>\n" +
                    "<img src=\"" + filename + "\" id=\"sign\">\n" +
                    "<br>\n" +
                    "<div>\n" +
                    "  <p class=\"mso-normal inline-text\">\n" +
                    "    <span style=\"font-size: 12.5pt; line-height: 107%;\">" + date +"</span>\n" +
                    "  </p>\n" +
                    "</div>\n" +
                    "<p class=MsoNormal><span style='font-size:12.5pt;line-height:107%'>&nbsp;</span></p>\n" +
                    "\n" +
                    "<div>\n" +
                    "  <p class=\"mso-normal inline-text\">\n" +
                    "    <span style=\"font-size: 12.5pt; line-height: 107%;\">Investigator Name: " + name + "</span>\n" +
                    "  </p>\n" +
                    "</div>\n" +
                    "\n" +
                    "<div>\n" +
                    "  <p class=\"mso-normal inline-text\">\n" +
                    "    <span style=\"font-size: 12.5pt; line-height: 107%;\">Investigator Sign:</span>\n" +
                    "  </p>\n" +
                    "  <br>\n" +
                    "  <img src=\"" + filename + "\" id=\"inv-sign\">\n" +
                    "</div>\n" +
                    "\n" +
                    "</div>\n" +
                    "\n" +
                    "</body>\n" +
                    "\n" +
                    "</html>\n";
        }

        private String imageToBase64(String imagePath) {
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();

            // Prefix the Base64 string with the data type
            String base64Prefix = "data:image/png;base64,";
            String base64String = Base64.encodeToString(byteArray, Base64.DEFAULT);
            return base64Prefix + base64String + "\" alt=\"Image\" width=\"50\" height=\"50\"";
        }

        private String getCurrentDateTime() {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date currentDate = new Date();
            return dateFormat.format(currentDate);
        }

        private void createWebPrintJob(WebView webView) {
            String jobName = getString(R.string.app_name) + " Document";
            PrintAttributes attributes = new PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(new PrintAttributes.Resolution("pdf", "pdf", 600, 600))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build();
            File path = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            PdfPrint pdfPrint = new PdfPrint(attributes, MessageView.this);
            pdfPrint.print(webView.createPrintDocumentAdapter(jobName), path, chat.getSessionKey() + ".pdf");
            webView.setVisibility(View.GONE);
        }
    }

    @Override
    public void uploadFile() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                File uploadFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), chat.getSessionKey()+".pdf");
                OkHttpClient client = new OkHttpClient();

                MediaType mediaType = MediaType.parse("text/plain");

                RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("file", uploadFile.getAbsolutePath(), RequestBody.create(null, uploadFile))
                        .build();

                Request request = new Request.Builder()
                        .url(BASE_URL + "/upload-consent/" + chat.getSessionKey())
                        .addHeader("accept", "application/json")
                        .post(body)
                        .build();

                try {
                    Response response = client.newCall(request).execute();
                    System.out.println(response.body().string());
                    chat.setConsent(true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

}