package com.teamopensourcesmartglasses.factchecker;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.os.IBinder;
import android.text.method.LinkMovementMethod;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import com.teamopensourcesmartglasses.factchecker.databinding.ActivityMainBinding;
import com.teamopensourcesmartglasses.factchecker.events.OpenAIApiKeyProvidedEvent;

import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "SmartGlassesFactChecker_MainActivity";
    boolean mBound;
    public FactCheckerService mService;
    private ActivityMainBinding binding;
    private Button submitButton;
    EditText messageEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        setupHyperlink();

        startFactCheckerService();

        // Show toasts if we have or don't have a key saved
        SharedPreferences sharedPreferences = getSharedPreferences("user.config", Context.MODE_PRIVATE);
        if (sharedPreferences.contains("openAiKey")) {
            String savedKey = sharedPreferences.getString("openAiKey", "");
            Toast.makeText(this, "OpenAI key found", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "No OpenAI key found, please add one", Toast.LENGTH_LONG).show();
        }

        // UI handlers
        messageEditText = findViewById(R.id.edittext_input);
        submitButton = findViewById(R.id.submit_button);
        submitButton.setOnClickListener((v) -> {
            String apiKey = messageEditText.getText().toString().trim();
            messageEditText.setText(""); // Empty text

            // Save to shared preference
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("openAiKey", apiKey);
            editor.apply();
            EventBus.getDefault().post(new OpenAIApiKeyProvidedEvent(apiKey));

            // Toast to inform user that key has been saved
            Toast.makeText(this, "OpenAi key saved for future sessions", Toast.LENGTH_LONG).show();
        });
    }

    private void setupHyperlink() {
        TextView linkTextView = findViewById(R.id.textview_first);
        linkTextView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    /* SGMLib */
    @Override
    protected void onResume() {
        super.onResume();

        //bind to foreground service
        bindFactCheckerService();
    }

    @Override
    protected void onPause() {
        super.onPause();

        //unbind foreground service
        unbindFactCheckerService();
    }

    public void stopFactCheckerService() {
        unbindFactCheckerService();
        if (!isMyServiceRunning(FactCheckerService.class)) return;
        Intent stopIntent = new Intent(this, FactCheckerService.class);
        stopIntent.setAction(FactCheckerService.ACTION_STOP_FOREGROUND_SERVICE);
        startService(stopIntent);
    }

    public void sendFactCheckerServiceMessage(String message) {
        if (!isMyServiceRunning(FactCheckerService.class)) return;
        Intent messageIntent = new Intent(this, FactCheckerService.class);
        messageIntent.setAction(message);
        startService(messageIntent);
    }

    public void startFactCheckerService() {
        if (isMyServiceRunning(FactCheckerService.class)){
            Log.d(TAG, "Not starting service.");
            return;
        }
        Log.d(TAG, "Starting service.");
        Intent startIntent = new Intent(this, FactCheckerService.class);
        startIntent.setAction(FactCheckerService.ACTION_START_FOREGROUND_SERVICE);
        startService(startIntent);
        bindFactCheckerService();
    }

    //check if service is running
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void bindFactCheckerService(){
        if (!mBound){
            Intent intent = new Intent(this, FactCheckerService.class);
            bindService(intent, factCheckerAppServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    public void unbindFactCheckerService() {
        if (mBound){
            unbindService(factCheckerAppServiceConnection);
            mBound = false;
        }
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection factCheckerAppServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            FactCheckerService.LocalBinder sgmLibServiceBinder = (FactCheckerService.LocalBinder) service;
            mService = (FactCheckerService) sgmLibServiceBinder.getService();
            mBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
}