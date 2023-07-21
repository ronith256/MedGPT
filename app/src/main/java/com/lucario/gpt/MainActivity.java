package com.lucario.gpt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ImageButton;

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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RecyclerView mRecyclerView = findViewById(R.id.chat_recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

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
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });

        FloatingActionButton newChatButton = findViewById(R.id.fab_new_chat);
        mChatList = new ArrayList<>();
        loadChatList();

        newChatButton.setOnClickListener(e->{
           Chat chat = new Chat(mChatList.size()+1, 0, "null", "null", new File(String.valueOf(System.currentTimeMillis())), true, null, 0);
           mChatList.add(chat);
           saveChatList();
           Intent intent = new Intent(MainActivity.this, MessageView.class);
           intent.putExtra("chat", mChatList.size()-1);
           intent.putExtra("chatList", (Serializable) mChatList);
           startActivity(intent);
           mAdapter.setChatList(mChatList);
           mAdapter.notifyItemInserted(mChatList.size());
           finish();
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
        intent.putExtra("chatList", (Serializable) mChatList);
        // Start the new activity
        startActivity(intent);
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