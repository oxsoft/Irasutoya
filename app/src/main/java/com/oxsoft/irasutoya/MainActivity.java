package com.oxsoft.irasutoya;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.File;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final ImageKeyboardSupportEditText imageKeyboardSupportEditText = (ImageKeyboardSupportEditText) findViewById(R.id.image_keyboard_support_edit_text);
        final TextView descriptionLabel = (TextView) findViewById(R.id.description_label);
        final ImageView imageView = (ImageView) findViewById(R.id.image_view);
        final Button openSettings = (Button) findViewById(R.id.open_settings);
        final Button clearCache = (Button) findViewById(R.id.clear_cache);
        final Button imageSize = (Button) findViewById(R.id.image_size);

        imageKeyboardSupportEditText.getDescription().subscribe(descriptionLabel::setText);
        imageKeyboardSupportEditText.getContentUri().subscribe(uri -> Glide.with(this).load(uri).into(imageView));
        openSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        clearCache.setOnClickListener(v -> clearCache());
        imageSize.setOnClickListener(v -> imageSize());
    }

    private void clearCache() {
        Observable.fromArray(getFileStreamPath(".").listFiles((dir, name) -> name.endsWith(".png"))).subscribe(File::delete);
        Completable.fromAction(() -> OrmaDatabase.builder(this).build().deleteAll()).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe();
        Toast.makeText(this, R.string.clear_cache_done, Toast.LENGTH_SHORT).show();
    }

    private void imageSize() {
        Toast.makeText(this, R.string.todo, Toast.LENGTH_SHORT).show();
    }
}
