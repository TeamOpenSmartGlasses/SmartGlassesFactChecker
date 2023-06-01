package com.teamopensourcesmartglasses.factchecker;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.teamopensmartglasses.sgmlib.DataStreamType;
import com.teamopensmartglasses.sgmlib.FocusStates;
import com.teamopensmartglasses.sgmlib.SGMCommand;
import com.teamopensmartglasses.sgmlib.SGMLib;
import com.teamopensmartglasses.sgmlib.SmartGlassesAndroidService;
import com.teamopensourcesmartglasses.factchecker.events.ChatErrorEvent;
import com.teamopensourcesmartglasses.factchecker.events.FactCheckedEvent;
import com.teamopensourcesmartglasses.factchecker.events.OpenAIApiKeyProvidedEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FactCheckerService extends SmartGlassesAndroidService {
    public final String TAG = "SmartGlassesChatGpt_FactCheckerService";
    static final String appName = "SmartGlassesFactChecker";

    //our instance of the SGM library
    public SGMLib sgmLib;
    public FocusStates focusState;
    public FactCheckerBackend factCheckerBackend;
    public boolean includeUndecided = false;
    private boolean openAiKeyProvided = false;
    private FactCheckerAppMode mode = FactCheckerAppMode.Inactive;

    public FactCheckerService() {
        super(MainActivity.class,
                "factchecker_app",
                1211,
                appName,
                "Fact checker for smart glasses", com.google.android.material.R.drawable.notify_panel_notification_icon_bg);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        focusState = FocusStates.OUT_FOCUS;

        /* Handle SGMLib specific things */

        // Create SGMLib instance with context: this
        sgmLib = new SGMLib(this);

        // Define command
        UUID factCheckerUUID = UUID.fromString("aef7e07f-5c36-42f2-a808-21074b32bb28");
        String[] phrases = new String[]{"Fact Checker", "Fact check"};
        String description = "Fact check your conversation with ChatGPT!";
        SGMCommand startFactCheckerCommand = new SGMCommand(appName, factCheckerUUID, phrases, description);
        sgmLib.registerCommand(startFactCheckerCommand, this::startFactCheckerCommandCallback);

        //Subscribe to transcription stream
        sgmLib.subscribe(DataStreamType.TRANSCRIPTION_ENGLISH_STREAM, this::processTranscriptionCallback);

        Log.d(TAG, "onCreate: ChatGPT service started!");

        /* Handle SmartGlassesFactChecker specific things */

        EventBus.getDefault().register(this);
        factCheckerBackend = new FactCheckerBackend();

        initPreferences();
    }

    public void initPreferences() {
        // Putting a separate sharedPreferences here instead of through the event bus from mainActivity
        // so I don't have to deal with waiting for this service to finish its startup
        SharedPreferences sharedPreferences = getSharedPreferences("user.config", Context.MODE_PRIVATE);
        if (sharedPreferences.contains("openAiKey")) {
            String savedKey = sharedPreferences.getString("openAiKey", "");
            openAiKeyProvided = true;
            factCheckerBackend.initFactCheckerService(savedKey);
        } else {
            Log.d(TAG, "ChatGptService: No key exists");
        }

        if (sharedPreferences.contains("includeUndecided")) {
            if (sharedPreferences.getString("includeUndecided", "") == "true") {
                Log.d(TAG, "Including undecided facts");
                includeUndecided = true;
            }
            else {
                Log.d(TAG, "DO NOT INCLUDE UNDECIDED 1");
            }
        }
        else {
            Log.d(TAG, "DO NOT INCLUDE UNDECIDED 2");
        }

    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: Called");
        EventBus.getDefault().unregister(this);
        sgmLib.deinit();
        super.onDestroy();
    }

    public void startFactCheckerCommandCallback(String args, long commandTriggeredTime) {
        Log.d(TAG, "startFactCheckerCommandCallback: Start ChatGPT command callback called");
        Log.d(TAG, "startFactCheckerCommandCallback: OpenAiApiKeyProvided:" + openAiKeyProvided);
        sgmLib.sendReferenceCard(appName, "Listening...");
        //TODO: eval necessary
        sgmLib.requestFocus(this::focusChangedCallback);
    }

    public void focusChangedCallback(FocusStates focusState) {
        Log.d(TAG, "Focus callback called with state: " + focusState);
        this.focusState = focusState;
    }

    public void processTranscriptionCallback(String transcript, long timestamp, boolean isFinal) {
        if (!focusState.equals(FocusStates.IN_FOCUS)) {// || mode == FactCheckerAppMode.Inactive){
            Log.d(TAG, "GOT TRANSCRIPT BUT APP INACTIVE :(");
            return;
        }

        if (isFinal) {
            trySendChat(transcript);
        }
    }

    /* Subscriptions */

    @Subscribe
    public void onFactCheckedEvent(FactCheckedEvent event) {
        sgmLib.requestFocus(this::focusChangedCallback);

        String fact = event.getFact();
        String validity = event.getValidity();
        String explanation = event.getExplanation();

        if(includeUndecided == false && validity.toLowerCase().equals("undecided"))
            return;

        String title = prettifyFactTitle(fact);
        String body = prettifyFactBody(event);

        sgmLib.sendReferenceCard(title, body);
    }

    @Subscribe
    public void onChatError(ChatErrorEvent event) {
        sgmLib.sendReferenceCard("Something wrong with ChatGpt", event.getErrorMessage());
    }

    @Subscribe
    public void onOpenAIApiKeyProvided(OpenAIApiKeyProvidedEvent event) {
        Log.d(TAG, "onOpenAIApiKeyProvided: Enabling ChatGpt command");
        openAiKeyProvided = true;
        factCheckerBackend.initFactCheckerService(event.token);
    }

    /*  Helpers  */

    public String prettifyFactBody(FactCheckedEvent evt) {
        String validity = evt.getValidity();
        validity = validity.toUpperCase() + "!";

        String explanation = evt.getExplanation();

        if (explanation.contains("N/A")) {
            return validity;
        } else {
            return String.format("%s\n\nExplanation: %s", validity, explanation);
        }
    }

    public String prettifyFactTitle(String fact) {
        String ret = fact.substring(0, 1).toUpperCase() + fact.substring(1); //capitalize first
        if(ret.length() > 28){
            ret = ret.substring(0, 27) + "...";
        }

        ret = String.format("\"%s\"", ret); //add quotes
        return ret;
    }

    public int getWordCount(String message) {
        String[] words = message.split("\\s+");
        return words.length;
    }

    public void trySendChat(String transcript) {
        // Anything under 4 words is basically garbage
        if (openAiKeyProvided && getWordCount(transcript) > 3) {
            Log.d("SHIT", "Running w: " + transcript);
            factCheckerBackend.sendChat(transcript);
        }
    }
}
