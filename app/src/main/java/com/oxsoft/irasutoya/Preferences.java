package com.oxsoft.irasutoya;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {
    private static final String IMAGE_SIZE = "image_size";
    private static final int DEFAULT_IMAGE_SIZE = 400;

    private final SharedPreferences preferences;

    public Preferences(Context context) {
        preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE);
    }

    public void saveImageSize(int imageSize) {
        preferences.edit().putInt(IMAGE_SIZE, imageSize).apply();
    }

    public int loadImageSize() {
        return preferences.getInt(IMAGE_SIZE, DEFAULT_IMAGE_SIZE);
    }
}
