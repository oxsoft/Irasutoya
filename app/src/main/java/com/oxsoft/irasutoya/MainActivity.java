package com.oxsoft.irasutoya;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.File;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {
    private static final int MIN_IMAGE_SIZE = 100;
    private static final int MAX_IMAGE_SIZE = 1000;
    private static final int IMAGE_SIZE_STEP = 50;

    private int imageSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.label);

        final ImageKeyboardSupportEditText imageKeyboardSupportEditText = (ImageKeyboardSupportEditText) findViewById(R.id.image_keyboard_support_edit_text);
        final TextView descriptionLabel = (TextView) findViewById(R.id.description_label);
        final ImageView imageView = (ImageView) findViewById(R.id.image_view);
        final Button openSettings = (Button) findViewById(R.id.open_settings);
        final Button clearCache = (Button) findViewById(R.id.clear_cache);
        final Button changeImageSize = (Button) findViewById(R.id.change_image_size);

        imageKeyboardSupportEditText.getDescription().subscribe(descriptionLabel::setText);
        imageKeyboardSupportEditText.getContentUri().subscribe(uri -> Glide.with(this).load(uri).into(imageView));
        openSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        clearCache.setOnClickListener(v -> clearCache(true));
        changeImageSize.setOnClickListener(v -> changeImageSize());
    }

    private void clearCache(boolean showToast) {
        Observable.fromArray(getFileStreamPath(".").listFiles((dir, name) -> name.endsWith(".png"))).subscribe(File::delete);
        Completable.fromAction(() -> OrmaDatabase.builder(this).build().deleteAll()).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe();
        if (showToast) Toast.makeText(this, R.string.clear_cache_done, Toast.LENGTH_SHORT).show();
    }

    private void changeImageSize() {
        Preferences preferences = new Preferences(this);
        View view = getLayoutInflater().inflate(R.layout.view_image_size, null);
        TextView currentImageSize = (TextView) view.findViewById(R.id.current_image_size);
        currentImageSize.setText(getString(R.string.current_image_size, preferences.loadImageSize()));
        SeekBar seekBar = (SeekBar) view.findViewById(R.id.seek_bar);
        seekBar.setMax((MAX_IMAGE_SIZE - MIN_IMAGE_SIZE) / IMAGE_SIZE_STEP);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                imageSize = progress * IMAGE_SIZE_STEP + MIN_IMAGE_SIZE;
                currentImageSize.setText(getString(R.string.current_image_size, imageSize));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        seekBar.setProgress((preferences.loadImageSize() - MIN_IMAGE_SIZE) / IMAGE_SIZE_STEP);
        new AlertDialog.Builder(this)
                .setTitle(R.string.change_image_size)
                .setView(view)
                .setPositiveButton(R.string.change_image_size_ok, ((dialog, which) -> {
                    preferences.saveImageSize(imageSize);
                    clearCache(false);
                    Toast.makeText(this, getString(R.string.change_image_size_done, imageSize), Toast.LENGTH_SHORT).show();
                }))
                .setNegativeButton(R.string.change_image_size_cancel, null)
                .show();
    }
}
