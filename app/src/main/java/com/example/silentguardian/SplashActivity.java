package com.example.silentguardian;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Hide action bar to make the logo full screen
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Delay for precisely 1.5 seconds (not over time), then navigate silently
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            
            // Finish splash activity to clear it from phone history
            finish();
        }, 1500);
    }
}
