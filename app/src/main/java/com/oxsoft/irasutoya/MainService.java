package com.oxsoft.irasutoya;

import android.content.ClipDescription;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Color;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v13.view.inputmethod.EditorInfoCompat;
import android.support.v13.view.inputmethod.InputConnectionCompat;
import android.support.v13.view.inputmethod.InputContentInfoCompat;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewTreeObserver;
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
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static android.view.View.GONE;

public class MainService extends InputMethodService {
    private static final long VIBRATION_TIME = 10L;

    private interface Action0 {
        void call();
    }

    private OrmaDatabase orma;
    private Vibrator vibrator;
    private int imageSize;
    private TextView unsupported;
    private CompositeDisposable subscriptions = new CompositeDisposable();
    private Action0 removeOnPreDrawListener = null;

    @Override
    public View onCreateInputView() {
        orma = OrmaDatabase.builder(this).build();
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        imageSize = new Preferences(this).loadImageSize();

        View inputView = getLayoutInflater().inflate(R.layout.view_keyboard, null);
        TextView search = (TextView) inputView.findViewById(R.id.search);
        search.setOnClickListener(v -> {
            vibrator.vibrate(VIBRATION_TIME);
            Toast.makeText(this, R.string.todo, Toast.LENGTH_SHORT).show();
        });
        HorizontalScrollView contentsScrollView = (HorizontalScrollView) inputView.findViewById(R.id.view_keyboard_contents_scroll_view);
        LinearLayout contents = (LinearLayout) inputView.findViewById(R.id.view_keyboard_contents);
        LinearLayout labels = (LinearLayout) inputView.findViewById(R.id.view_keyboard_labels);
        unsupported = (TextView) inputView.findViewById(R.id.unsupported);

        getSearchQueries().subscribe(searchQueries -> {
            for (SearchQuery searchQuery : searchQueries) {
                TextView textView = (TextView) getLayoutInflater().inflate(R.layout.view_label_text, labels, false);
                textView.setText(searchQuery.getName());
                textView.setOnClickListener(v -> {
                    vibrator.vibrate(VIBRATION_TIME);
                    if (removeOnPreDrawListener != null) removeOnPreDrawListener.call();
                    subscriptions.clear();
                    contents.removeAllViews();
                    contentsScrollView.post(() -> contentsScrollView.scrollTo(0, 0));
                    for (int i = 0; i < labels.getChildCount(); i++) {
                        labels.getChildAt(i).setBackgroundColor(Color.TRANSPARENT);
                    }
                    textView.setBackgroundResource(R.drawable.label_background);
                    Single<SearchResult> request = searchQuery.getType() == SearchQuery.TYPE_HISTORY ? searchHistory() : searchQuery.getType() == SearchQuery.TYPE_LATEST ? searchLatest() : searchLabel(searchQuery.getName());
                    subscriptions.add(request.subscribe(searchResult -> drawImages(contents, searchResult), Throwable::printStackTrace));
                });
                labels.addView(textView);
            }
            labels.getChildAt(orma.selectFromImageCache().count() > 0 ? 0 : 1).performClick();
        }, Throwable::printStackTrace);
        return inputView;
    }

    @Override
    public void onStartInputView(EditorInfo editorInfo, boolean restarting) {
        String[] mimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo);

        boolean pngSupported = false;
        for (String mimeType : mimeTypes) {
            if (ClipDescription.compareMimeTypes(mimeType, MimeTypes.PNG)) {
                pngSupported = true;
            }
        }

        unsupported.setVisibility(pngSupported ? GONE : View.VISIBLE);
    }

    private void commitPngImage(Uri contentUri, String imageDescription, Uri linkUri) {
        InputContentInfoCompat inputContentInfo = new InputContentInfoCompat(contentUri, new ClipDescription(imageDescription, new String[]{MimeTypes.PNG}), linkUri);
        InputConnection inputConnection = getCurrentInputConnection();
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        int flags = 0;
        if (android.os.Build.VERSION.SDK_INT >= 25) {
            flags |= InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
        }
        InputConnectionCompat.commitContent(inputConnection, editorInfo, inputContentInfo, flags, null);
    }

    private void drawImages(LinearLayout contents, SearchResult searchResult) {
        for (SearchResult.Image image : searchResult.getImages()) {
            ImageView imageView = (ImageView) getLayoutInflater().inflate(R.layout.view_item_image, contents, false);
            contents.addView(imageView);
            subscriptions.add(download(image.getUrl()).subscribe(uri -> {
                Glide.with(this).load(uri).into(imageView);
                imageView.setOnClickListener(v -> {
                    vibrator.vibrate(VIBRATION_TIME);
                    commitPngImage(uri, image.getDescription(), Uri.parse(image.getUrl()));
                    Single.fromCallable(() -> {
                        String key = getFileNameFromUrl(image.getUrl());
                        try {
                            return orma.insertIntoImageCache(new ImageCache(key, image.getUrl(), image.getDescription(), System.currentTimeMillis()));
                        } catch (SQLiteConstraintException e) {
                            return orma.updateImageCache().url(image.getUrl()).description(image.getDescription()).updated(System.currentTimeMillis()).keyEq(key).execute();
                        }
                    }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(id -> {
                    }, Throwable::printStackTrace);
                });
            }, Throwable::printStackTrace));
        }
        if (!TextUtils.isEmpty(searchResult.getNext())) {
            View lastImage = contents.getChildAt(contents.getChildCount() - 1);
            Rect rect = new Rect();
            ViewTreeObserver.OnPreDrawListener onPreDrawListener = () -> {
                if (lastImage.getLocalVisibleRect(rect)) {
                    if (removeOnPreDrawListener != null) removeOnPreDrawListener.call();
                    subscriptions.add(search(searchResult.getNext()).subscribe(nextSearchResult -> drawImages(contents, nextSearchResult), Throwable::printStackTrace));
                }
                return true;
            };
            removeOnPreDrawListener = () -> {
                lastImage.getViewTreeObserver().removeOnPreDrawListener(onPreDrawListener);
                removeOnPreDrawListener = null;
            };
            lastImage.getViewTreeObserver().addOnPreDrawListener(onPreDrawListener);
        }
    }

    private static String getFileNameFromUrl(String url) {
        String[] segment = url.split("/");
        return segment[segment.length - 1];
    }

    private Single<Uri> download(String url) {
        String fileName = getFileNameFromUrl(url);
        File file = getFileStreamPath(fileName);
        Uri uri = FileProvider.getUriForFile(this, "com.oxsoft.irasutoya.content", file);
        if (file.exists() && file.isFile()) {
            return Single.just(uri);
        }
        return Single.create((SingleOnSubscribe<Uri>) singleSubscriber -> {
            try {
                Request request = new Request.Builder().url(url).build();
                Response response = new OkHttpClient().newCall(request).execute();
                InputStream input = response.body().byteStream();
                OutputStream output = openFileOutput(fileName, MODE_PRIVATE);
                byte[] buffer = new byte[65536];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                response.body().close();
                singleSubscriber.onSuccess(uri);
            } catch (IOException e) {
//                singleSubscriber.onError(e);
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    private Single<SearchQuery[]> getSearchQueries() {
        return fetchLabels().map(labels -> {
            SearchQuery[] searchQueries = new SearchQuery[labels.length + 2];
            searchQueries[0] = new SearchQuery(SearchQuery.TYPE_HISTORY, getString(R.string.history));
            searchQueries[1] = new SearchQuery(SearchQuery.TYPE_LATEST, getString(R.string.latest));
            for (int i = 0; i < labels.length; i++) {
                searchQueries[i + 2] = new SearchQuery(SearchQuery.TYPE_LABEL, labels[i]);
            }
            return searchQueries;
        });
    }

    private Single<String[]> fetchLabels() {
        if (orma.selectFromLabel().count() > 0) {
            return orma.selectFromLabel().executeAsObservable().reduce(new ArrayList<String>(), (list, label) -> {
                list.add(label.label);
                return list;
            }).map(list -> list.toArray(new String[list.size()]));
        }
        return Single.create((SingleOnSubscribe<String[]>) singleSubscriber -> {
            try {
                Document document = Jsoup.connect("http://www.irasutoya.com").get();
                Element content = document.getElementsByClass("widget-content list-label-widget-content").first();
                Elements links = content.getElementsByTag("a");
                String[] labels = new String[links.size()];
                for (int i = 0; i < labels.length; i++) {
                    labels[i] = links.get(i).text();
                    orma.insertIntoLabel(new Label(labels[i]));
                }
                singleSubscriber.onSuccess(labels);
            } catch (Exception e) {
//                singleSubscriber.onError(e);
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    @SuppressWarnings("unused")
    private Single<SearchResult> searchQuery(@NonNull String query) {
        return search("http://www.irasutoya.com/search?q=" + query);
    }

    private Single<SearchResult> searchHistory() {
        return orma.selectFromImageCache().orderByUpdatedDesc().limit(20L).executeAsObservable().reduce(new ArrayList<SearchResult.Image>(), (imageList, imageCache) -> {
            imageList.add(new SearchResult.Image(imageCache.url, imageCache.description));
            return imageList;
        }).map(imageList -> {
            SearchResult.Image[] images = imageList.toArray(new SearchResult.Image[imageList.size()]);
            return new SearchResult(images, null);
        });
    }

    private Single<SearchResult> searchLatest() {
        return search("http://www.irasutoya.com/search");
    }

    private Single<SearchResult> searchLabel(@NonNull String label) {
        return search("http://www.irasutoya.com/search/label/" + label);
    }

    private Single<SearchResult> search(@NonNull String url) {
        return Single.create((SingleOnSubscribe<SearchResult>) singleSubscriber -> {
            try {
                Request request = new Request.Builder().url(url).build();
                String body = new OkHttpClient().newCall(request).execute().body().string();
                Document document = Jsoup.parse(body);
                Elements boxim = document.getElementsByClass("boxim");
                SearchResult.Image[] images = new SearchResult.Image[boxim.size()];
                for (int i = 0; i < images.length; i++) {
                    String script = boxim.get(i).getElementsByTag("script").last().html();
                    final String A = "document.write(bp_thumbnail_resize(\"";
                    final String B = "\",\"";
                    final String C = "\"));";
                    String imageUrl = script.substring(A.length(), script.indexOf(B)).replace("s72-c", "s" + imageSize);
                    String imageDesc = script.substring(script.indexOf(B) + B.length(), script.indexOf(C));
                    images[i] = new SearchResult.Image(imageUrl, decode(imageDesc));
                }
                Element nextLink = document.getElementById("Blog1_blog-pager-older-link");
                String next = nextLink != null ? nextLink.attr("href") : null;
                singleSubscriber.onSuccess(new SearchResult(images, next));
            } catch (IOException e) {
//                singleSubscriber.onError(e);
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
}
