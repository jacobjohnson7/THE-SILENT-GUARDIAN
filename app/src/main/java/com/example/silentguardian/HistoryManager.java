package com.example.silentguardian;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryManager {

    private static final String FILE_NAME = "event_history.json";
    private static final int MAX_HISTORY_SIZE = 200; // Store last 200 events

    public static class HistoryEvent {
        public String sound;
        public String timestamp;

        public HistoryEvent(String sound, String timestamp) {
            this.sound = sound;
            this.timestamp = timestamp;
        }
        
        @Override
        public String toString() {
            return "[" + timestamp + "] " + sound;
        }
    }

    /**
     * Saves a new sound event with the current timestamp to internal storage.
     */
    public static void saveEvent(Context context, String sound) {
        JSONArray historyArray = loadHistoryJson(context);
        
        String timeStamp = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(new Date());

        try {
            JSONObject newEvent = new JSONObject();
            newEvent.put("sound", sound);
            newEvent.put("timestamp", timeStamp);

            // Add to top of list (index 0)
            JSONArray updatedArray = new JSONArray();
            updatedArray.put(newEvent);
            
            // Re-add existing events up to MAX limit
            for (int i = 0; i < historyArray.length() && i < MAX_HISTORY_SIZE - 1; i++) {
                updatedArray.put(historyArray.getJSONObject(i));
            }

            // Save to file
            FileOutputStream fos = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE);
            fos.write(updatedArray.toString().getBytes());
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads the list of history events from internal storage.
     */
    public static List<HistoryEvent> loadHistory(Context context) {
        List<HistoryEvent> events = new ArrayList<>();
        JSONArray historyArray = loadHistoryJson(context);
        
        try {
            for (int i = 0; i < historyArray.length(); i++) {
                JSONObject obj = historyArray.getJSONObject(i);
                events.add(new HistoryEvent(obj.getString("sound"), obj.getString("timestamp")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return events;
    }

    private static JSONArray loadHistoryJson(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            return new JSONArray();
        }

        try {
            FileInputStream fis = context.openFileInput(FILE_NAME);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return new JSONArray(sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONArray();
        }
    }
    
    /**
     * Clears the saved history file.
     */
    public static void clearHistory(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (file.exists()) {
            file.delete();
        }
    }
}
