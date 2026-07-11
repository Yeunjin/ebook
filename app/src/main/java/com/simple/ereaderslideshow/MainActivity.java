package com.simple.ereaderslideshow;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
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
    private static final long FLASH_STEP_MS = 150; // 잔상 제거용 깜빡임 간격

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
            goToNext();
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

        loadImageList();
        updateStatusText();
        goToNext(); // 첫 이미지도 동일하게 플래싱 후 표시
        handler.postDelayed(slideRunnable, INTERVAL_MS);
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
        goToNext();
        handler.postDelayed(slideRunnable, INTERVAL_MS);
    }

    private void manualPrevious() {
        handler.removeCallbacks(slideRunnable);
        goToPrevious();
        handler.postDelayed(slideRunnable, INTERVAL_MS);
    }

    private void goToNext() {
        loadImageList();
        updateStatusText();

        if (imageFiles.isEmpty()) {
            flashThenClear();
            currentIndex = 0;
            return;
        }
        if (currentIndex >= imageFiles.size()) {
            currentIndex = 0;
        }

        final File file = imageFiles.get(currentIndex);
        currentIndex++;
        flashThenShow(file);
    }

    private void goToPrevious() {
        loadImageList();
        updateStatusText();

        if (imageFiles.isEmpty()) {
            flashThenClear();
            currentIndex = 0;
            return;
        }

        currentIndex -= 2;
        if (currentIndex < 0) {
            currentIndex = imageFiles.size() - 1;
        }

        final File file = imageFiles.get(currentIndex);
        currentIndex++;
        flashThenShow(file);
    }

    /**
     * 이잉크 화면 잔상 제거용 깜빡임 처리.
     * 검정 -> 흰색 -> 검정 순으로 화면을 크게 반전시켜서
     * 기기의 완전 새로고침(풀 리프레시)을 유도한 뒤 실제 이미지를 표시한다.
     */
    private void flashThenShow(final File file) {
        flashSequence(new Runnable() {
            @Override
            public void run() {
                displayImage(file);
            }
        });
    }

    private void flashThenClear() {
        flashSequence(new Runnable() {
            @Override
            public void run() {
                imageView.setImageBitmap(null);
                if (currentBitmap != null && !currentBitmap.isRecycled()) {
                    currentBitmap.recycle();
                    currentBitmap = null;
                }
            }
        });
    }

    private void flashSequence(final Runnable onFinished) {
        imageView.setImageBitmap(null);
        imageView.setBackgroundColor(Color.BLACK);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                imageView.setBackgroundColor(Color.WHITE);

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        imageView.setBackgroundColor(Color.BLACK);

                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                imageView.setBackgroundColor(Color.WHITE);
                                onFinished.run();
                            }
                        }, FLASH_STEP_MS);
                    }
                }, FLASH_STEP_MS);
            }
        }, FLASH_STEP_MS);
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

    private void displayImage(File file) {
        Bitmap decoded = decodeSampledBitmap(file);
        if (decoded == null) {
            return;
        }

        Bitmap grayscaled = toGrayscaleHighContrast(decoded);
        decoded.recycle();

        imageView.setImageBitmap(grayscaled);
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
        }
        currentBitmap = grayscaled;
    }

    /** 컬러 사진을 흑백(그레이스케일)으로 변환하고 대비를 강화해서 이잉크 화면에 최적화 */
    private Bitmap toGrayscaleHighContrast(Bitmap src) {
        int width = src.getWidth();
        int height = src.getHeight();

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(result);

        // 1) 채도 제거 -> 흑백
        ColorMatrix grayMatrix = new ColorMatrix();
        grayMatrix.setSaturation(0);

        // 2) 대비 강화 (1.0 = 원본, 값이 클수록 대비 강해짐)
        float contrast = 1.35f;
        float translate = (-0.5f * contrast + 0.5f) * 255f;
        ColorMatrix contrastMatrix = new ColorMatrix(new float[]{
                contrast, 0, 0, 0, translate,
                0, contrast, 0, 0, translate,
                0, 0, contrast, 0, translate,
                0, 0, 0, 1, 0
        });

        grayMatrix.postConcat(contrastMatrix);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(grayMatrix));
        canvas.drawBitmap(src, 0, 0, paint);

        return result;
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
