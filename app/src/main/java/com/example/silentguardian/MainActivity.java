package com.example.silentguardian;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
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
    private View indicatorDot;
    private FrameLayout statusRing;

    // LOGIC
    private boolean isRecording = false;
    private AudioRecord audioRecord;
    private AudioClassifier classifier;
    private Vibrator vibrator;
    private Thread recordingThread;
    
    // TRIGGER STATE
    private String lastSavedSound = "";

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
        indicatorDot = findViewById(R.id.indicatorDot);
        statusRing = findViewById(R.id.statusRing);

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

        setupSOS();

        // STT Button Logic
        Button btnSpeechToText = findViewById(R.id.btnSpeechToText);
        btnSpeechToText.setOnClickListener(v -> {
            // Stop monitoring if active before switching
            if (isRecording) stopListening();
            
            android.content.Intent intent = new android.content.Intent(MainActivity.this, SpeechToTextActivity.class);
            startActivity(intent);
        });

        // History Button Logic
        Button btnHistory = findViewById(R.id.btnHistory);
        btnHistory.setOnClickListener(v -> {
            // Stop monitoring if active before switching
            if (isRecording) stopListening();
            
            android.content.Intent intent = new android.content.Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
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
        btnToggle.setText("DEACTIVATE");
        btnToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828"))); // Dark Red when active

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
            if (!latestDangerSound.equals(lastSavedSound)) {
                HistoryManager.saveEvent(this, latestDangerSound);
                lastSavedSound = latestDangerSound;
            }
            updateUI(latestDangerSound, true, true);
        } else {
            // NORMAL (Even if 1 frame was danger, we ignore it until it's confirmed)
            if (!currentSound.equals(lastSavedSound)) {
                HistoryManager.saveEvent(this, currentSound);
                lastSavedSound = currentSound;
            }
            // We pass 'false' for triggerAlert
            updateUI(currentSound, isDangerEvent(currentSound), false);
        }
    }

    private void stopListening() {
        isRecording = false;
        btnToggle.setText("ACTIVATE");
        btnToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32"))); // Dark Green when inactive

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

    // UPDATED UI METHOD FOR NEW LAYOUT
    private void updateUI(String sound, boolean currentlyDangerous, boolean triggerAlert) {
        txtLabel.setText(sound);
        
        // Define colors
        int colorDanger = Color.parseColor("#FF5252"); // Red
        int colorSafe = Color.parseColor("#4CAF50");   // Green
        int colorInactive = Color.parseColor("#E0E0E0"); // Grey
        int colorTextDark = Color.parseColor("#1A1C1E");

        if (triggerAlert) {
            // --- RED ALERT ---
            // Update Ring Color (Programmatically changing stroke if possible, or tint)
            updateRingColor(colorDanger);
            
            iconStatus.setImageResource(android.R.drawable.ic_dialog_alert);
            iconStatus.setColorFilter(colorDanger);
            
            indicatorDot.setBackgroundColor(colorDanger);
            
            triggerVibration();
        } else if (sound.equals("Monitoring...") || currentlyDangerous) {
            // --- MONITORING (Green) ---
            updateRingColor(colorSafe);
            
            iconStatus.setImageResource(android.R.drawable.ic_btn_speak_now);
            iconStatus.setColorFilter(colorSafe);
            
            indicatorDot.setBackgroundColor(colorSafe);
            
        } else if (sound.equals("Disabled")) {
            // --- OFF (Grey) ---
            updateRingColor(colorInactive);
            
            iconStatus.setImageResource(android.R.drawable.ic_lock_power_off);
            iconStatus.setColorFilter(Color.GRAY);
            
            indicatorDot.setBackgroundColor(Color.GRAY);
        } else {
            // --- SAFE / BACKGROUND (Green) ---
            updateRingColor(colorSafe);
            
            iconStatus.setImageResource(android.R.drawable.ic_btn_speak_now);
            iconStatus.setColorFilter(colorSafe);
            
            indicatorDot.setBackgroundColor(colorSafe);
        }
    }

    private void updateRingColor(int color) {
        // Create a new shape or modify existing one
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setStroke(12, color); // 12px stroke
        shape.setSize(200, 200);
        shape.setColor(Color.TRANSPARENT);
        
        statusRing.setBackground(shape);
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

    // --- SOS LOGIC ---
    private void setupSOS() {
        ImageButton btnSOS = findViewById(R.id.btnSOS);
        btnSOS.setOnClickListener(v -> handleSOSClick());
        btnSOS.setOnLongClickListener(v -> {
            showSetContactDialog();
            return true;
        });
    }

    private void handleSOSClick() {
        android.content.SharedPreferences prefs = getSharedPreferences("SilentGuardianPrefs", MODE_PRIVATE);
        String emergencyNumber = prefs.getString("emergency_contact", null);

        if (emergencyNumber == null) {
            showSetContactDialog();
        } else {
            makeCall(emergencyNumber);
        }
    }

    private void showSetContactDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Set Emergency Contact");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        input.setHint("Enter phone number");
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String number = input.getText().toString();
            if (!number.isEmpty()) {
                getSharedPreferences("SilentGuardianPrefs", MODE_PRIVATE)
                        .edit()
                        .putString("emergency_contact", number)
                        .apply();
                android.widget.Toast.makeText(this, "Emergency Contact Saved", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void makeCall(String number) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, 101);
        } else {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CALL);
            intent.setData(android.net.Uri.parse("tel:" + number));
            startActivity(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            handleSOSClick(); // Retry call after permission granted
        }
    }
}