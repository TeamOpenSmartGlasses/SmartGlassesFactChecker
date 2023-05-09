package com.teamopensourcesmartglasses.factchecker;

import android.util.Log;

import com.teamopensourcesmartglasses.factchecker.events.ChatErrorEvent;
import com.teamopensourcesmartglasses.factchecker.events.FactCheckedEvent;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FactCheckerBackend {
    public final String TAG = "SmartGlassesFactChecker_FactCheckerBackend";
    public final ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), Prompt.prompt);
    private OpenAiService service;
    private final List<ChatMessage> messages = new ArrayList<>();
    // private StringBuffer responseMessageBuffer = new StringBuffer();
    private final int chatGptMaxTokenSize = 4096;
    private final int maxSingleChatTokenSize = 200;
    private final int openAiServiceTimeoutDuration = 110;
    String previousStatement = "";

    public FactCheckerBackend(){}

    public void initFactCheckerService(String token) {
        // Setup ChatGpt with a token
        service = new OpenAiService(token, Duration.ofSeconds(openAiServiceTimeoutDuration));
        messages.clear();
        messages.add(systemMessage);
    }

    public void maybeTrimMessages(){
        if(messages.size() > 3){
            messages.remove(1); //remove earliest user/transcript message
        }
    }

    public void sendChat(String message){
        // Don't run if openAI service is not initialized yet
        if (service == null) {
            EventBus.getDefault().post(new ChatErrorEvent("OpenAi Key has not been provided yet. Please do so in the app."));
            return;
        }

        maybeTrimMessages();

        class DoGptStuff implements Runnable {
            public void run(){
                Log.d(TAG, "run: Doing gpt stuff, got message: " + message);
                messages.add(new ChatMessage(ChatMessageRole.USER.value(), message));

                // Todo: Change completions to streams
                ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                        .model("gpt-3.5-turbo")
                        .messages(messages)
                        .maxTokens(maxSingleChatTokenSize)
                        .temperature(0.0)
                        .n(1)
                        .build();

                try {
                    Log.d(TAG, "run: Running ChatGpt completions request");
                    ChatCompletionResult result = service.createChatCompletion(chatCompletionRequest);
                    List<ChatMessage> responses = result.getChoices()
                                                        .stream()
                                                        .map(ChatCompletionChoice::getMessage)
                                                        .collect(Collectors.toList());

                    // Make sure there is still space for next messages
                    // Just use a simple approximation, if current request is more than 75% of max, we clear half of it
                    long tokensUsed = result.getUsage().getTotalTokens();
                    Log.d(TAG, "run: tokens used: " + tokensUsed + "/" + chatGptMaxTokenSize);
                    if (tokensUsed >= chatGptMaxTokenSize * 0.75) {
                        for (int i = 0; i < messages.size() / 2; i++) {
                            messages.remove(1);
                        }
                    }

                    // Send an chat received response
                    ChatMessage response = responses.get(0);
                    Log.d(TAG, "run: " + response.getContent());
                    String opt = response.getContent();


                    //TODO: parse out stuff
                    JSONObject output = new JSONObject(opt);

                    String statement = output.getString("statement").toLowerCase();
                    String validity = output.getString("validity");
                    String correction = output.getString("correction");

                    if(editDistance(statement, previousStatement) > 2) {
                        EventBus.getDefault().post(new FactCheckedEvent(statement, validity, correction));
                    }
                    previousStatement = statement;

                } catch (Exception e){
                    Log.d(TAG, "run: encountered error: " + e.getMessage());
                    //EventBus.getDefault().post(new ChatErrorEvent(e.getMessage()));
                }
            }
        }
        new Thread(new DoGptStuff()).start();
    }

    // Example implementation of the Levenshtein Edit Distance
    // See http://rosettacode.org/wiki/Levenshtein_distance#Java
    public int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();

        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0)
                    costs[j] = j;
                else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1))
                            newValue = Math.min(Math.min(newValue, lastValue),
                                    costs[j]) + 1;
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0)
                costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }
}
