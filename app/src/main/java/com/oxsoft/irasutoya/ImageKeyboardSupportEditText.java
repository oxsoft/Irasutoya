package com.oxsoft.irasutoya;

import android.content.Context;
import android.net.Uri;
import android.support.v13.view.inputmethod.EditorInfoCompat;
import android.support.v13.view.inputmethod.InputConnectionCompat;
import android.support.v4.os.BuildCompat;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;

public class ImageKeyboardSupportEditText extends EditText {
    private BehaviorSubject<Uri> contentUri = BehaviorSubject.create();
    private BehaviorSubject<CharSequence> description = BehaviorSubject.create();

    public ImageKeyboardSupportEditText(Context context) {
        super(context);
    }

    public ImageKeyboardSupportEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageKeyboardSupportEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
        final InputConnection ic = super.onCreateInputConnection(editorInfo);
        EditorInfoCompat.setContentMimeTypes(editorInfo, new String[]{MimeTypes.PNG});
        return InputConnectionCompat.createWrapper(ic, editorInfo, (inputContentInfo, flags, opts) -> {
            if (BuildCompat.isAtLeastNMR1() && (flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                try {
                    inputContentInfo.requestPermission();
                } catch (Exception e) {
                    return false;
                }
            }
            contentUri.onNext(inputContentInfo.getContentUri());
            description.onNext(inputContentInfo.getDescription().getLabel());
            inputContentInfo.releasePermission();
            return true;
        });
    }

    public Observable<Uri> getContentUri() {
        return contentUri.hide();
    }

    public Observable<CharSequence> getDescription() {
        return description.hide();
    }
}
