package com.lucario.gpt;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ChatAdapter.onClick, ChatAdapter.deleteItem{

    private ChatAdapter mAdapter;
    private List<Chat> mChatList;
    private ActivityResultLauncher<Intent> launcher;
    private Intent messageViewIntent;

    private int consentRequestTimes = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RecyclerView mRecyclerView = findViewById(R.id.chat_recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        String investigatorName = getSharedPreferences("cred", MODE_PRIVATE).getString("inv-name", null);
        ImageButton settingsButton = findViewById(R.id.toolbar_settings);
        settingsButton.setOnClickListener(e->{
            SettingsDialogFragment dialog = new SettingsDialogFragment();
            dialog.show(getSupportFragmentManager(), "SettingsDialogFragment");
        });

        ImageButton logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(e->{
            SharedPreferences sharedPreferences = getSharedPreferences("cred", MODE_PRIVATE);
            sharedPreferences.edit().putString("user", "null").apply();
            sharedPreferences.edit().putString("password", "null").apply();
            getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS).delete();
            saveChatList(new ArrayList<>());
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });

        launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            String name = data.getStringExtra("name");
                            String filename = data.getStringExtra("filename");
                            messageViewIntent = new Intent(MainActivity.this, MessageView.class);
                            messageViewIntent.putExtra("filename", filename);
                            messageViewIntent.putExtra("name", name);
                            messageViewIntent.putExtra("roleml", data.getStringExtra("roleml"));
                            messageViewIntent.putExtra("role", data.getStringExtra("role"));
                            Chat chat = new Chat(mChatList.size()+1, 0, "null", "null", new File(String.valueOf(System.currentTimeMillis())), true, null, 0);
                            mChatList.add(chat);
                            saveChatList();
                            messageViewIntent.putExtra("chat", mChatList.size()-1);
                            messageViewIntent.putExtra("chatList", (Serializable) mChatList);
                            startActivity(messageViewIntent);
                            mAdapter.setChatList(mChatList);
                            mAdapter.notifyItemInserted(mChatList.size());
                            finish();
//                            System.out.println(filename);
//                            new MessageView.UploadConsentTask().doInBackground(filename, name);
                        }
                    } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                        if(consentRequestTimes > 3){
                            Toast.makeText(this, "Maximum tries exceeded", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        FloatingActionButton newChatButton = findViewById(R.id.fab_new_chat);
        mChatList = new ArrayList<>();
        loadChatList();

        newChatButton.setOnClickListener(e->{
            if(investigatorName==null){
                logoutButton.performClick();
            }
            launcher.launch(new Intent(MainActivity.this, GetConsent.class).putExtra("sessionKey", String.valueOf(Math.random())).putExtra("name", investigatorName));
        });
        mAdapter = new ChatAdapter(this, mChatList, this, this);
        mRecyclerView.setAdapter(mAdapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new SwipeToDeleteCallback(mAdapter));
        itemTouchHelper.attachToRecyclerView(mRecyclerView);
    }

    private void saveChatList() {
        // Write the chat list to a file
        try {
            FileOutputStream fos = openFileOutput("chat_list.ser", MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(mChatList);
            oos.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveChatList(List <Chat> clear) {
        // Write the chat list to a file
        try {
            FileOutputStream fos = openFileOutput("chat_list.ser", MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(clear);
            oos.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadChatList() {
        // Read the chat list from a file
        try {
            FileInputStream fis = openFileInput("chat_list.ser");
            ObjectInputStream ois = new ObjectInputStream(fis);
            mChatList = (ArrayList<Chat>) ois.readObject();
            ois.close();
            fis.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
//            Toast.makeText(this, "Load failed", Toast.LENGTH_SHORT).show();
        }

        // Initialize the chat list if it doesn't exist
        if (mChatList == null) {
            mChatList = new ArrayList<>();
        }
    }

    @Override
    public void clicked(int position) {
        Chat item = mChatList.get(position);
        // Create an Intent to start a new activity
        Intent intent = new Intent(MainActivity.this, MessageView.class);
        intent.putExtra("chat", position);
        intent.putExtra("old-chat", true);
        intent.putExtra("chatList", (Serializable) mChatList);
        // Start the new activity
        startActivity(intent);
        finish();
    }

    @Override
    public void deleteItem(int position) {
        ArrayList<Chat> temp = new ArrayList<>(mChatList.size());
        for(int i = 0; i < mChatList.size(); i++){
            if(i == position){
            } else if (i>position){
                Chat chat = mChatList.get(i);
                temp.add(chat);
            } else {
                temp.add(mChatList.get(i));
            }
        }
        mChatList = temp;
        saveChatList();
        mAdapter.setChatList(mChatList);
        mAdapter.notifyItemRemoved(position);
    }
}