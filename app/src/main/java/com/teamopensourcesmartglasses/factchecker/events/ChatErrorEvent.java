package com.teamopensourcesmartglasses.factchecker.events;

public class ChatErrorEvent {
    private final String message;

    public ChatErrorEvent(String errorMessage) {
        message = errorMessage;
    }

    public String getErrorMessage() {
        return message;
    }
}
