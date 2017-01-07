package com.oxsoft.irasutoya;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final ImageKeyboardSupportEditText imageKeyboardSupportEditText = (ImageKeyboardSupportEditText) findViewById(R.id.image_keyboard_support_edit_text);
        final ImageView imageView = (ImageView) findViewById(R.id.image_view);
        imageKeyboardSupportEditText.getUri().subscribe(uri -> Glide.with(this).load(uri).into(imageView));
    }
}
