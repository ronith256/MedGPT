package com.lucario.gpt;

import java.io.Serializable;

public class Message implements Serializable {
    public static String SENT_BY_ME = "me";
    public static String SENT_BY_BOT= "bot";

    public static String FAILED_RESPONSE = "failed";

    String message;
    String sentBy;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSentBy() {
        return sentBy;
    }

    public void setSentBy(String sentBy) {
        this.sentBy = sentBy;
    }

    public Message(String message, String sentBy) {
        this.message = message;
        this.sentBy = sentBy;
    }
}