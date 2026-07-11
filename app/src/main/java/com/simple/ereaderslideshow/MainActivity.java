package com.simple.ereaderslideshow;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    private static final long INTERVAL_MS = 60 * 60 * 1000; // 1시간

    private static final String[] IMAGE_EXTENSIONS = {
            ".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp"
    };

    private ImageView imageView;
    private TextView statusText;
    private final Handler handler = new Handler();
    private final List<File> imageFiles = new ArrayList<>();
    private int currentIndex = 0;
    private Bitmap currentBitmap;

    private final Runnable slideRunnable = new Runnable() {
        @Override
        public void run() {
            showNextImage();
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);
        imageView = (ImageView) findViewById(R.id.imageView);
        statusText = (TextView) findViewById(R.id.statusText);

        startSlideshow();
    }

    /** 물리 버튼(좌/우) 입력 처리 - 기기마다 키코드가 달라서 흔한 후보를 모두 처리 */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                manualNext();
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                manualPrevious();
                return true;

            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    /** 버튼으로 수동 전환 시, 자동 전환 타이머를 리셋해서 곧바로 또 넘어가지 않게 함 */
    private void manualNext() {
        handler.removeCallbacks(slideRunnable);
        showNextImage();
        handler.postDelayed(slideRunnable, INTERVAL_MS);
    }

    private void manualPrevious() {
        handler.removeCallbacks(slideRunnable);
        showPreviousImage();
        handler.postDelayed(slideRunnable, INTERVAL_MS);
    }

    private void startSlideshow() {
        showNextImage();
        handler.postDelayed(slideRunnable, INTERVAL_MS);
    }

    private File findDownloadDir() {
        File root = Environment.getExternalStorageDirectory();
        if (root != null && root.exists()) {
            File[] children = root.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isDirectory() && child.getName().equalsIgnoreCase("download")) {
                        return child;
                    }
                }
                for (File child : children) {
                    if (child.isDirectory() && child.getName().equalsIgnoreCase("downloads")) {
                        return child;
                    }
                }
            }
        }
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    private void loadImageList() {
        imageFiles.clear();
        File downloadDir = findDownloadDir();

        if (downloadDir != null && downloadDir.exists()) {
            File[] files = downloadDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && isImage(f.getName())) {
                        imageFiles.add(f);
                    }
                }
            }
        }
        Collections.sort(imageFiles);
    }

    private boolean isImage(String name) {
        String lower = name.toLowerCase();
        for (String ext : IMAGE_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private void updateStatusText() {
        if (statusText != null) {
            statusText.setText("이미지 " + imageFiles.size() + "장");
        }
    }

    private void showNextImage() {
        loadImageList();
        updateStatusText();

        if (imageFiles.isEmpty()) {
            imageView.setImageBitmap(null);
            currentIndex = 0;
            return;
        }

        if (currentIndex >= imageFiles.size()) {
            currentIndex = 0;
        }

        displayImage(imageFiles.get(currentIndex));
        currentIndex++;
    }

    private void showPreviousImage() {
        loadImageList();
        updateStatusText();

        if (imageFiles.isEmpty()) {
            imageView.setImageBitmap(null);
            currentIndex = 0;
            return;
        }

        currentIndex -= 2;
        if (currentIndex < 0) {
            currentIndex = imageFiles.size() - 1;
        }

        displayImage(imageFiles.get(currentIndex));
        currentIndex++;
    }

    private void displayImage(File file) {
        Bitmap newBitmap = decodeSampledBitmap(file);
        if (newBitmap != null) {
            imageView.setImageBitmap(newBitmap);
            if (currentBitmap != null && !currentBitmap.isRecycled()) {
                currentBitmap.recycle();
            }
            currentBitmap = newBitmap;
        }
    }

    private Bitmap decodeSampledBitmap(File file) {
        try {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int targetW = metrics.widthPixels;
            int targetH = metrics.heightPixels;

            BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
            boundsOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), boundsOptions);

            int sampleSize = calculateInSampleSize(boundsOptions, targetW, targetH);

            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = sampleSize;
            decodeOptions.inPreferredConfig = Bitmap.Config.RGB_565;

            return BitmapFactory.decodeFile(file.getAbsolutePath(), decodeOptions);
        } catch (Throwable t) {
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(slideRunnable);
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
        }
    }
}
