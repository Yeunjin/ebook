package com.simple.ereaderslideshow;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 아주 가벼운 이북 리더용 슬라이드쇼 앱.
 * - Downloads 폴더의 이미지를 1분마다 자동 전환
 * - 화면이 꺼지거나 스크린세이버가 동작하지 않도록 유지
 * - 저사양 기기를 위해 라이브러리 의존성 없이 순수 SDK만 사용
 */
public class MainActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final long INTERVAL_MS = 60 * 60 * 1000; // 1시간

    private static final String[] IMAGE_EXTENSIONS = {
            ".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp"
    };

    private ImageView imageView;
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

        // 화면 꺼짐 / 스크린세이버 방지: 앱이 실행되는 동안 화면을 계속 켜둠
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);
        imageView = (ImageView) findViewById(R.id.imageView);

        if (needsPermission()) {
            requestPermission();
        } else {
            startSlideshow();
        }
    }

    private boolean needsPermission() {
        return android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        requestPermissions(
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSlideshow();
            }
        }
    }

    private void startSlideshow() {
        showNextImage();
        handler.postDelayed(slideRunnable, INTERVAL_MS);
    }

    /** Downloads 폴더를 스캔해서 이미지 파일 목록을 새로 만든다 (새로 받은 파일 반영용) */
    private void loadImageList() {
        imageFiles.clear();
        File downloadDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
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
        Collections.sort(imageFiles); // 파일명 순으로 정렬 (항상 같은 순서 보장)
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
        // 매 주기마다 폴더를 재스캔해서 새로 다운로드된 이미지도 반영
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
            // 이전 비트맵은 즉시 해제해서 저사양 기기의 메모리를 아낀다
            if (currentBitmap != null && !currentBitmap.isRecycled()) {
                currentBitmap.recycle();
            }
            currentBitmap = newBitmap;
        }

        currentIndex++;
    }

    /** 화면 해상도에 맞춰 다운샘플링 + RGB_565로 디코딩해서 메모리 사용을 최소화 */
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
            decodeOptions.inPreferredConfig = Bitmap.Config.RGB_565; // ARGB_8888 대비 메모리 절반

            return BitmapFactory.decodeFile(file.getAbsolutePath(), decodeOptions);
        } catch (Throwable t) {
            // 손상된 파일 등으로 디코딩 실패 시 앱이 죽지 않도록 방어
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
