package com.oxsoft.irasutoya;

import android.content.ClipDescription;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v13.view.inputmethod.EditorInfoCompat;
import android.support.v13.view.inputmethod.InputConnectionCompat;
import android.support.v13.view.inputmethod.InputContentInfoCompat;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import rx.Single;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MainService extends InputMethodService {

    @Override
    public View onCreateInputView() {
        View inputView = getLayoutInflater().inflate(R.layout.view_keyboard, null);
        TextView search = (TextView) inputView.findViewById(R.id.search);
        search.setOnClickListener(v -> Toast.makeText(this, R.string.todo, Toast.LENGTH_SHORT).show());
        HorizontalScrollView contentsScrollView = (HorizontalScrollView) inputView.findViewById(R.id.view_keyboard_contents_scroll_view);
        LinearLayout contents = (LinearLayout) inputView.findViewById(R.id.view_keyboard_contents);
        LinearLayout labels = (LinearLayout) inputView.findViewById(R.id.view_keyboard_labels);
        fetchLabels().subscribe(labelStrings -> {
            for (String label : labelStrings) {
                TextView textView = new TextView(this);
                textView.setText(label);
                textView.setTextSize(16f);
                textView.setPadding(dp2px(8), dp2px(8), dp2px(8), dp2px(8));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = dp2px(8);
                textView.setLayoutParams(lp);
                textView.setOnClickListener(v0 -> {
                    contents.removeAllViews();
                    contentsScrollView.post(() -> contentsScrollView.scrollTo(0, 0));
                    for (int i = 0; i < labels.getChildCount(); i++) {
                        labels.getChildAt(i).setBackgroundColor(Color.TRANSPARENT);
                    }
                    textView.setBackgroundResource(R.drawable.label_background);
                    searchLabel(label).subscribe(searchResult -> {
                        for (SearchResult.Image image : searchResult.getImages()) {
                            ImageView imageView = new ImageView(this);
                            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dp2px(120), dp2px(120));
                            layoutParams.rightMargin = dp2px(8);
                            imageView.setLayoutParams(layoutParams);
                            imageView.setBackgroundColor(Color.WHITE);
                            contents.addView(imageView);
                            download(image.getUrl()).subscribe(uri -> {
                                Glide.with(this).load(uri).into(imageView);
                                imageView.setOnClickListener(v1 -> commitPngImage(uri, image.getDescription(), Uri.parse(image.getUrl())));
                            }, Throwable::printStackTrace);
                        }
                    }, Throwable::printStackTrace);
                });
                labels.addView(textView);
            }
            labels.getChildAt(0).performClick();
        }, Throwable::printStackTrace);
        return inputView;
    }

    @Override
    public void onStartInputView(EditorInfo editorInfo, boolean restarting) {
        String[] mimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo);

        boolean pngSupported = false;
        for (String mimeType : mimeTypes) {
            if (ClipDescription.compareMimeTypes(mimeType, "image/png")) {
                pngSupported = true;
            }
        }

        if (pngSupported) {
            // the target editor supports GIFs. enable corresponding content
        } else {
            // the target editor does not support GIFs. disable corresponding content
        }
    }

    private void commitPngImage(Uri contentUri, String imageDescription, Uri linkUri) {
        InputContentInfoCompat inputContentInfo = new InputContentInfoCompat(contentUri, new ClipDescription(imageDescription, new String[]{"image/png"}), linkUri);
        InputConnection inputConnection = getCurrentInputConnection();
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        int flags = 0;
        if (android.os.Build.VERSION.SDK_INT >= 25) {
            flags |= InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
        }
        InputConnectionCompat.commitContent(inputConnection, editorInfo, inputContentInfo, flags, null);
    }


    private Single<Uri> download(String url) {
        String[] segment = url.split("/");
        String fileName = segment[segment.length - 1];
        File file = getFileStreamPath(fileName);
        Uri uri = Uri.parse("content://com.oxsoft.irasutoya.FileContentProvider/" + fileName);
        if (file.exists() && file.isFile()) {
            return Single.just(uri);
        }
        return Single.create((Single.OnSubscribe<Uri>) singleSubscriber -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();
                InputStream input = response.body().byteStream();
                OutputStream output = openFileOutput(fileName, MODE_PRIVATE);
                byte[] buffer = new byte[16777216];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                response.body().close();
                singleSubscriber.onSuccess(uri);
            } catch (IOException e) {
                singleSubscriber.onError(e);
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    private Single<String[]> fetchLabels() {
        return Single.create((Single.OnSubscribe<String[]>) singleSubscriber -> {
            try {
                Document document = Jsoup.connect("http://www.irasutoya.com").get();
                Element content = document.getElementsByClass("widget-content list-label-widget-content").first();
                Elements links = content.getElementsByTag("a");
                String[] labels = new String[links.size()];
                for (int i = 0; i < labels.length; i++) {
                    labels[i] = links.get(i).text();
                }
                singleSubscriber.onSuccess(labels);
            } catch (Exception e) {
                singleSubscriber.onError(e);
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    private Single<SearchResult> searchQuery(@NonNull String query) {
        return search("http://www.irasutoya.com/search?q=" + query);
    }

    private Single<SearchResult> searchLabel(@NonNull String label) {
        return search("http://www.irasutoya.com/search/label/" + label);
    }

    private Single<SearchResult> search(@NonNull String url) {
        return Single.create((Single.OnSubscribe<SearchResult>) singleSubscriber -> {
            try {
                Document document = Jsoup.connect(url).get();
                Elements boxim = document.getElementsByClass("boxim");
                SearchResult.Image[] images = new SearchResult.Image[boxim.size()];
                for (int i = 0; i < images.length; i++) {
                    String script = boxim.get(i).getElementsByTag("script").last().html();
                    final String A = "document.write(bp_thumbnail_resize(\"";
                    final String B = "\",\"";
                    final String C = "\"));";
                    String imageUrl = script.substring(A.length(), script.indexOf(B)).replace("s72-c", "s180");
                    String imageDesc = script.substring(script.indexOf(B) + B.length(), script.indexOf(C));
                    images[i] = new SearchResult.Image(imageUrl, decode(imageDesc));
                }
                Element nextLink = document.getElementById("Blog1_blog-pager-older-link");
                String next = nextLink != null ? nextLink.attr("href") : null;
                singleSubscriber.onSuccess(new SearchResult(images, next));
            } catch (Exception e) {
                singleSubscriber.onError(e);
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    private static String decode(String str) {
        Pattern pattern = Pattern.compile("&#(\\d+);|&#([\\da-fA-F]+);");
        Matcher matcher = pattern.matcher(str);
        StringBuffer sb = new StringBuffer();
        Character buf;
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                buf = (char) Integer.parseInt(matcher.group(1));
            } else {
                buf = (char) Integer.parseInt(matcher.group(2), 16);
            }
            matcher.appendReplacement(sb, buf.toString());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private int dp2px(int dp) {
        return Math.round(getResources().getDisplayMetrics().density * dp);
    }
}
