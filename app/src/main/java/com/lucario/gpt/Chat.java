package com.lucario.gpt;

import java.io.File;
import java.io.Serializable;

public class Chat implements Serializable {
    private int mProfileLogo;
    private String chatName;
    private String mLatestChat;
    private int chatId;

    public boolean isFirstPrompt() {
        return isFirstPrompt;
    }

    public void setFirstPrompt(boolean firstPrompt) {
        isFirstPrompt = firstPrompt;
    }

    private boolean isFirstPrompt;

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    private String sessionKey;
    File chatArray;

    public long getSessionStartTime() {
        return sessionStartTime;
    }

    public void setSessionStartTime(long sessionStartTime) {
        this.sessionStartTime = sessionStartTime;
    }

    long sessionStartTime;
    public Chat(int chatId, int profileLogo, String profileName, String latestChat, File chatArray, boolean isFirstPrompt, String sessionKey, long sessionStartTime) {
        this.chatId = chatId;
        this.mProfileLogo = profileLogo;
        this.chatName = profileName;
        this.mLatestChat = latestChat;
        this.chatArray = chatArray;
        this.isFirstPrompt = isFirstPrompt;
        this.sessionKey = sessionKey;
        this.sessionStartTime = sessionStartTime;
    }

    public int getProfileLogo() {
        return mProfileLogo;
    }

    public String getChatName() {
        return chatName;
    }

    public void setChatName(String name){
        chatName = name;
    }

    public String getLatestChat() {
        return mLatestChat;
    }

    public void setLatestChat(String chat){mLatestChat = chat;}
    public int getChatId(){
        return chatId;
    }

    public File getChatArray(){
        return chatArray;
    }


}
