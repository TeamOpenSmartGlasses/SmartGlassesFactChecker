package com.teamopensourcesmartglasses.factchecker.events;

public class OpenAIApiKeyProvidedEvent {
    public final String token;
    public OpenAIApiKeyProvidedEvent(String userToken) {
        token = userToken;
    }
}
