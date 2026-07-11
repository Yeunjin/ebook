package com.simple.ereaderslideshow;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.DisplayMetrics;
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

        String pathInfo;
        if (downloadDir == null) {
            pathInfo = "폴더를 찾을 수 없음";
        } else if (!downloadDir.exists()) {
            pathInfo = "경로 없음: " + downloadDir.getAbsolutePath();
        } else {
            File[] files = downloadDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && isImage(f.getName())) {
                        imageFiles.add(f);
                    }
                }
            }
            pathInfo = downloadDir.getAbsolutePath() + " | 전체파일 "
                    + (files != null ? files.length : 0) + "개, 이미지 "
                    + imageFiles.size() + "개";
        }
        Collections.sort(imageFiles);

        if (statusText != null) {
            statusText.setText(pathInfo);
        }
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

    private void showNextImage() {
        loadImageList();

        if (imageFiles.isEmpty()) {
            imageView.setImageBitmap(null);
            currentIndex = 0;
            return;
        }

        if (currentIndex >= imageFiles.size()) {
            currentIndex = 0;
        }

        File file = imageFiles.get(currentIndex);
        Bitmap newBitmap = decodeSampledBitmap(file);

        if (newBitmap != null) {
            imageView.setImageBitmap(newBitmap);
            if (currentBitmap != null && !currentBitmap.isRecycled()) {
                currentBitmap.recycle();
            }
            currentBitmap = newBitmap;
        } else if (statusText != null) {
            statusText.setText(statusText.getText() + " | 디코딩 실패: " + file.getName());
        }

        currentIndex++;
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
