package com.example.silentguardian; // MATCH YOUR PACKAGE NAME

import android.content.Context;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class AudioClassifier {
    private Interpreter interpreter;
    private List<String> labels;

    // LOWERED THRESHOLD: Makes the app more sensitive to sounds like crying/alarms
    private final float CONFIDENCE_THRESHOLD = 0.25f;

    public AudioClassifier(Context context) {
        try {
            MappedByteBuffer model = FileUtil.loadMappedFile(context, "yamnet.tflite");
            interpreter = new Interpreter(model);
            labels = FileUtil.loadLabels(context, "yamnet_labels.txt");
        } catch (IOException e) {
            e.printStackTrace();
            labels = new ArrayList<>();
        }
    }

    public String classify(short[] audioBuffer) {
        if (interpreter == null || labels.isEmpty()) return "Error: Model not loaded";

        // 1. Prepare Input: YAMNet expects [1][15600] float array
        float[][] inputFloats = new float[1][15600];

        for (int i = 0; i < Math.min(audioBuffer.length, 15600); i++) {
            inputFloats[0][i] = audioBuffer[i] / 32768.0f; // Normalize
        }

        // 2. Prepare Output: [1][521] array
        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(new int[]{1, 521}, DataType.FLOAT32);

        // 3. Run Inference
        interpreter.run(inputFloats, outputBuffer.getBuffer().rewind());

        // 4. Find Max Probability
        float[] scores = outputBuffer.getFloatArray();
        int maxIndex = -1;
        float maxScore = 0.0f;

        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > maxScore) {
                maxScore = scores[i];
                maxIndex = i;
            }
        }

        // 5. Threshold Check
        if (maxScore > CONFIDENCE_THRESHOLD && maxIndex < labels.size() && maxIndex >= 0) {
            return labels.get(maxIndex);
        } else {
            return "Background Noise";
        }
    }
}