package com.example.silentguardian; // MATCH YOUR PACKAGE NAME

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // AUDIO CONFIG
    private static final int SAMPLE_RATE = 16000;
    private static final int BUFFER_SIZE = 15600; // 0.975 seconds

    // TEMPORAL SMOOTHING (The fix for flickering)
    private static final int HISTORY_SIZE = 5; // Remember last 5 sounds (~5 seconds)
    private final List<String> historyBuffer = new ArrayList<>();
    private final int DANGER_VOTE_THRESHOLD = 3; // Must hear danger 3 times to alert

    // UI ELEMENTS
    private CardView cardStatus;
    private TextView txtLabel;
    private ImageView iconStatus;
    private Button btnToggle;

    // LOGIC
    private boolean isRecording = false;
    private AudioRecord audioRecord;
    private AudioClassifier classifier;
    private Vibrator vibrator;
    private Thread recordingThread;

    // CRITICAL DANGER LIST
    private final List<String> DANGER_WORDS = Arrays.asList(
            "Smoke detector, smoke alarm", "Fire alarm", "Siren",
            "Civil defense siren", "Crying, sobbing", "Baby cry, infant cry",
            "Glass breaking", "Screaming"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Keep screen on so it works while you watch (since we don't have a Service)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // UI Bindings
        cardStatus = findViewById(R.id.cardStatus);
        txtLabel = findViewById(R.id.txtLabel);
        iconStatus = findViewById(R.id.iconStatus);
        btnToggle = findViewById(R.id.btnToggle);

        // Init Tools
        classifier = new AudioClassifier(this);
        initVibrator();

        // Button Click Listener
        btnToggle.setOnClickListener(v -> {
            if (isRecording) {
                stopListening();
            } else {
                if (checkPermission()) {
                    startListening();
                } else {
                    requestPermission();
                }
            }
        });

        updateUI("Disabled", false, false);
    }

    private void initVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = manager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    private void startListening() {
        isRecording = true;
        btnToggle.setText("DEACTIVATE GUARDIAN");
        btnToggle.setBackgroundColor(Color.parseColor("#444444"));

        // 1. Setup Mic
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;

        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(minBuf, BUFFER_SIZE * 2)
        );

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            updateUI("Mic Error", false, false);
            isRecording = false;
            return;
        }

        audioRecord.startRecording();
        updateUI("Monitoring...", false, false);

        // 2. Start Background Thread
        recordingThread = new Thread(this::audioLoop);
        recordingThread.start();
    }

    private void audioLoop() {
        short[] buffer = new short[BUFFER_SIZE];
        while (isRecording) {
            // Read Audio
            int read = audioRecord.read(buffer, 0, BUFFER_SIZE);
            if (read == BUFFER_SIZE) {
                // Classify
                String result = classifier.classify(buffer);

                // Process Logic on UI Thread
                runOnUiThread(() -> checkDangerStatus(result));
            }
        }
    }

    // --- TEMPORAL SMOOTHING (THE VOTING SYSTEM) ---
    private void checkDangerStatus(String currentSound) {
        // 1. Add current sound to history
        historyBuffer.add(currentSound);
        if (historyBuffer.size() > HISTORY_SIZE) {
            historyBuffer.remove(0); // Remove oldest
        }

        // 2. Count Danger Votes
        int dangerCount = 0;
        String latestDangerSound = currentSound;

        for (String sound : historyBuffer) {
            if (isDangerEvent(sound)) {
                dangerCount++;
                if (dangerCount == 1) latestDangerSound = sound; // Save the name
            }
        }

        // 3. Decide: Alert OR Normal
        if (dangerCount >= DANGER_VOTE_THRESHOLD) {
            // CONFIRMED DANGER (3 out of 5 frames were dangerous)
            updateUI(latestDangerSound, true, true);
        } else {
            // NORMAL (Even if 1 frame was danger, we ignore it until it's confirmed)
            // We pass 'false' for triggerAlert
            updateUI(currentSound, isDangerEvent(currentSound), false);
        }
    }

    private void stopListening() {
        isRecording = false;
        btnToggle.setText("ACTIVATE GUARDIAN");
        // Use your custom color or a default one
        btnToggle.setBackgroundColor(Color.parseColor("#303F9F"));

        try {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
            if (recordingThread != null) recordingThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        historyBuffer.clear();
        updateUI("Disabled", false, false);
    }

    // UPDATED UI METHOD
    private void updateUI(String sound, boolean currentlyDangerous, boolean triggerAlert) {
        txtLabel.setText(sound);

        if (triggerAlert) {
            // --- RED ALERT ---
            cardStatus.setCardBackgroundColor(Color.RED);
            iconStatus.setImageResource(android.R.drawable.ic_dialog_alert);
            getWindow().getDecorView().setBackgroundColor(Color.RED);
            triggerVibration();
        } else if (sound.equals("Monitoring...") || currentlyDangerous) {
            // --- MONITORING (Green) ---
            // Note: If 'currentlyDangerous' is true but 'triggerAlert' is false,
            // it means we saw a danger sound but it hasn't reached 3 votes yet.
            // We keep it green/neutral until confirmed.
            cardStatus.setCardBackgroundColor(Color.parseColor("#4CAF50"));
            iconStatus.setImageResource(android.R.drawable.ic_lock_idle_low_battery);
            getWindow().getDecorView().setBackgroundColor(Color.parseColor("#F8F8F8"));
        } else if (sound.equals("Disabled")) {
            // --- OFF (Brown) ---
            cardStatus.setCardBackgroundColor(Color.parseColor("#795548"));
            iconStatus.setImageResource(android.R.drawable.ic_lock_power_off);
        } else {
            // --- SAFE / BACKGROUND (Dark Green) ---
            cardStatus.setCardBackgroundColor(Color.parseColor("#2E7D32"));
            iconStatus.setImageResource(android.R.drawable.ic_lock_idle_low_battery);
            getWindow().getDecorView().setBackgroundColor(Color.parseColor("#F8F8F8"));
        }
    }

    private boolean isDangerEvent(String sound) {
        for (String danger : DANGER_WORDS) {
            if (sound.toLowerCase().contains(danger.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void triggerVibration() {
        if (vibrator != null) {
            long[] pattern = {0, 500, 200, 500};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(500);
            }
        }
    }

    // --- PERMISSIONS ---
    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.VIBRATE
        }, 100);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        }
    }
}