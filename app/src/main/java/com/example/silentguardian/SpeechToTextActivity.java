package com.example.silentguardian;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class SpeechToTextActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_SPEECH_INPUT = 1;

    private TextView tvResult;
    private Button btnListen;
    private Spinner sourceLanguageSpinner;
    private Spinner targetLanguageSpinner;
    private ScrollView scrollView;
    private ProgressBar progressBar;
    private TextView tvStatus;

    private String selectedSourceLangCode = "en-US";
    private String selectedTargetLangCode = TranslateLanguage.SPANISH; // Default target
    private String currentSTTLanguage = "en-US"; // Language for Speech Recognizer

    // Language Arrays
    // Display Names
    private final String[] languages = {
            "English", "Hindi", "Spanish", "French", "German", "Japanese", "Chinese"
    };

    // Speech Recognizer Codes (BCP-47)
    private final String[] sttLanguageCodes = {
            "en-US", "hi-IN", "es-ES", "fr-FR", "de-DE", "ja-JP", "zh-CN"
    };

    // ML Kit Translation Codes (BCP-47 tags)
    // Note: Use ISO codes for non-standard constants if needed
    private final String[] translationIsoCodes = {
            TranslateLanguage.ENGLISH,
            TranslateLanguage.HINDI,
            TranslateLanguage.SPANISH,
            TranslateLanguage.FRENCH,
            TranslateLanguage.GERMAN,
            TranslateLanguage.JAPANESE,
            TranslateLanguage.CHINESE
    };

    private Translator translator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech_to_text);

        tvResult = findViewById(R.id.tvResult);
        btnListen = findViewById(R.id.btnListen);
        sourceLanguageSpinner = findViewById(R.id.sourceLanguageSpinner);
        targetLanguageSpinner = findViewById(R.id.targetLanguageSpinner);
        scrollView = findViewById(R.id.scrollView);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);

        setupSpinners();

        btnListen.setOnClickListener(v -> startVoiceInput());
    }

    private void setupSpinners() {
        // Use custom layouts for visibility
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
                R.layout.spinner_item, 
                languages);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        
        // 1. Source Spinner
        sourceLanguageSpinner.setAdapter(adapter);
        sourceLanguageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSourceLangCode = translationIsoCodes[position];
                currentSTTLanguage = sttLanguageCodes[position];
                prepareTranslator();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 2. Target Spinner
        targetLanguageSpinner.setAdapter(adapter);
        targetLanguageSpinner.setSelection(2); // Default to Spanish to be different
        targetLanguageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedTargetLangCode = translationIsoCodes[position];
                prepareTranslator();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private int downloadRetryCount = 0;
    private static final int MAX_RETRIES = 2;

    private void prepareTranslator() {
        downloadRetryCount = 0; // Reset retry count
        startTranslatorDownload();
    }

    private void startTranslatorDownload() {
        if (translator != null) {
            translator.close();
        }

        // Check if languages are same
        if (selectedSourceLangCode.equals(selectedTargetLangCode)) {
            setStatus(false, "Original Text Only");
            return;
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(selectedSourceLangCode)
                .setTargetLanguage(selectedTargetLangCode)
                .build();
        
        translator = Translation.getClient(options);

        // Start Download
        setStatus(true, "Downloading Translation Model... (Attempt " + (downloadRetryCount + 1) + ")");
        btnListen.setEnabled(false); // Disable until ready to avoid errors
        btnListen.setAlpha(0.5f);

        DownloadConditions conditions = new DownloadConditions.Builder()
                .build();

        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    // Model ready
                    setStatus(false, "Model Ready");
                    Toast.makeText(SpeechToTextActivity.this, "Translation Ready", Toast.LENGTH_SHORT).show();
                    btnListen.setEnabled(true);
                    btnListen.setAlpha(1.0f);
                })
                .addOnFailureListener(e -> {
                    if (downloadRetryCount < MAX_RETRIES) {
                        downloadRetryCount++;
                        // Retry after short delay
                        new android.os.Handler().postDelayed(() -> {
                            Toast.makeText(SpeechToTextActivity.this, "Retrying download...", Toast.LENGTH_SHORT).show();
                            startTranslatorDownload();
                        }, 2000);
                    } else {
                        // Model download failed permanently
                        setStatus(false, "Download Error: " + e.getMessage());
                        Toast.makeText(SpeechToTextActivity.this, "Download failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        // We enable it anyway, but translation might fail
                        btnListen.setEnabled(true);
                        btnListen.setAlpha(1.0f);
                    }
                });
    }

    private void setStatus(boolean loading, String message) {
        if (loading) {
            progressBar.setVisibility(View.VISIBLE);
            tvStatus.setVisibility(View.VISIBLE);
        } else {
            progressBar.setVisibility(View.GONE);
            tvStatus.setVisibility(View.GONE);
        }
        tvStatus.setText(message);
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentSTTLanguage);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...");
        
        // REMOVED EXTRA_PREFER_OFFLINE to prevent "Voice Info not available" errors on devices without offline packs
        // intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
        } catch (Exception e) {
            Toast.makeText(this, "Speech input not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SPEECH_INPUT) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (result != null && !result.isEmpty()) {
                    String spokenText = result.get(0);
                    processAndTranslate(spokenText);
                }
            }
        }
    }

    private void processAndTranslate(String originalText) {
        // If source and target are same, just show original
        if (selectedSourceLangCode.equals(selectedTargetLangCode)) {
             appendResult(originalText, null);
             return;
        }

        // Translate
        if (translator != null) {
            translator.translate(originalText)
                .addOnSuccessListener(translatedText -> {
                    appendResult(originalText, translatedText);
                })
                .addOnFailureListener(e -> {
                    appendResult(originalText, "[Error: " + e.getMessage() + "]");
                });
        }
    }

    private void appendResult(String original, @Nullable String translated) {
        String timeStamp = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        
        StringBuilder sb = new StringBuilder();
        if (!tvResult.getText().toString().contains("Tap 'Start Listening'")) {
            sb.append("<br><br>");
        }
        
        sb.append("<font color='#8A92A6'><small>[").append(timeStamp).append("]</small></font> ");
        sb.append("<b><font color='#1A1C1E'>").append(original).append("</font></b><br>");
        
        if (translated != null) {
            sb.append("&nbsp;&nbsp;&nbsp;&nbsp;<font color='#2E7D32'><i>&#8627; ").append(translated).append("</i></font>");
        }

        CharSequence styledText = androidx.core.text.HtmlCompat.fromHtml(sb.toString(), androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY);

        if (tvResult.getText().toString().contains("Tap 'Start Listening'")) {
            tvResult.setText(styledText);
        } else {
            tvResult.append(styledText);
        }

        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (translator != null) {
            translator.close();
        }
    }
}
