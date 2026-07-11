package com.simple.ereaderslideshow;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MainActivity extends Activity {

    private static final long FLASH_STEP_MS = 150;     // 잔상 제거용 깜빡임 간격
    private static final long DOUBLE_CLICK_TIME_MS = 400; // 더블 클릭 판정 시간 (0.4초)

    private static final String[] IMAGE_EXTENSIONS = {
            ".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp"
    };

    private static final long[] INTERVAL_OPTIONS_MS = {
            5 * 60 * 1000L,
            15 * 60 * 1000L,
            30 * 60 * 1000L,
            60 * 60 * 1000L,
            12 * 60 * 60 * 1000L,
            24 * 60 * 60 * 1000L
    };
    private static final String[] INTERVAL_LABELS = {
            "5min", "15min", "30min", "60min", "12hour", "24hour"
    };

    private ImageView imageView;
    private TextView statusText;
    private final Handler handler = new Handler();
    private final List<File> imageFiles = new ArrayList<>();
    private final Random random = new Random();

    private int currentIndex = -1;
    private int intervalIndex = 3;      // 기본값: 60분
    private long intervalMs = INTERVAL_OPTIONS_MS[intervalIndex];
    private boolean randomMode = false;

    private Bitmap currentBitmap;

    // 더블 클릭 판정을 위한 변수
    private long lastRightClickTime = 0;
    private long lastLeftClickTime = 0;
    
    // 크레마 기기 자체의 연타 입력을 방지하기 위한 디바운스 타이머 작업
    private final Runnable pendingRightClickRunnable = new Runnable() {
        @Override
        public void run() {
            manualNext();
        }
    };

    private final Runnable pendingLeftClickRunnable = new Runnable() {
        @Override
        public void run() {
            manualPrevious();
        }
    };

    private final Runnable slideRunnable = new Runnable() {
        @Override
        public void run() {
            goToNext();
            handler.postDelayed(this, intervalMs);
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
        goToNext();
        handler.postDelayed(slideRunnable, intervalMs);
    }

    // 크레마 물리 버튼은 OS 레벨 가로채기를 피하기 위해 onKeyUp에서만 처리합니다.
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                return true; // OS가 볼륨 조절 다이얼로그를 띄우지 못하게 이벤트를 소비합니다.
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        long currentTime = System.currentTimeMillis();

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                // 이전 클릭과의 간격이 0.4초 이내면 더블 클릭으로 판단
                if (currentTime - lastRightClickTime < DOUBLE_CLICK_TIME_MS) {
                    handler.removeCallbacks(pendingRightClickRunnable); // 단발성 클릭 예약 취소
                    cycleInterval(); // 주기 변경 실행
                    lastRightClickTime = 0; // 판정 시간 초기화
                } else {
                    lastRightClickTime = currentTime;
                    // 크레마가 다음 더블클릭 입력을 보낼 수 있으므로 잠시 실행을 대기(예약)합니다.
                    handler.removeCallbacks(pendingRightClickRunnable);
                    handler.postDelayed(pendingRightClickRunnable, DOUBLE_CLICK_TIME_MS);
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                // 이전 클릭과의 간격이 0.4초 이내면 더블 클릭으로 판단
                if (currentTime - lastLeftClickTime < DOUBLE_CLICK_TIME_MS) {
                    handler.removeCallbacks(pendingLeftClickRunnable); // 단발성 클릭 예약 취소
                    toggleRandomMode(); // 랜덤 모드 토글 실행
                    lastLeftClickTime = 0; // 판정 시간 초기화
                } else {
                    lastLeftClickTime = currentTime;
                    // 크레마가 다음 더블클릭 입력을 보낼 수 있으므로 잠시 실행을 대기(예약)합니다.
                    handler.removeCallbacks(pendingLeftClickRunnable);
                    handler.postDelayed(pendingLeftClickRunnable, DOUBLE_CLICK_TIME_MS);
                }
                return true;

            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    private void manualNext() {
        handler.removeCallbacks(slideRunnable);
        goToNext();
        handler.postDelayed(slideRunnable, intervalMs);
    }

    private void manualPrevious() {
        handler.removeCallbacks(slideRunnable);
        goToPrevious();
        handler.postDelayed(slideRunnable, intervalMs);
    }

    private void cycleInterval() {
        intervalIndex = (intervalIndex + 1) % INTERVAL_OPTIONS_MS.length;
        intervalMs = INTERVAL_OPTIONS_MS[intervalIndex];

        handler.removeCallbacks(slideRunnable);
        handler.postDelayed(slideRunnable, intervalMs);

        updateStatusText();
    }

    private void toggleRandomMode() {
        randomMode = !randomMode;
        updateStatusText();
    }

    private void goToNext() {
        loadImageList();

        if (imageFiles.isEmpty()) {
            flashThenClear();
            currentIndex = -1;
            updateStatusText();
            return;
        }

        int total = imageFiles.size();
        if (randomMode) {
            currentIndex = pickRandomIndex(total);
        } else {
            currentIndex = (currentIndex + 1 + total) % total;
        }

        updateStatusText();
        flashThenShow(imageFiles.get(currentIndex));
    }

    private void goToPrevious() {
        loadImageList();

        if (imageFiles.isEmpty()) {
            flashThenClear();
            currentIndex = -1;
            updateStatusText();
            return;
        }

        int total = imageFiles.size();
        if (randomMode) {
            currentIndex = pickRandomIndex(total);
        } else {
            currentIndex = (currentIndex - 1 + total) % total;
        }

        updateStatusText();
        flashThenShow(imageFiles.get(currentIndex));
    }

    private int pickRandomIndex(int total) {
        if (total <= 1) {
            return 0;
        }
        int newIndex;
        do {
            newIndex = random.nextInt(total);
        } while (newIndex == currentIndex);
        return newIndex;
    }

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
        if (statusText == null) {
            return;
        }
        int total = imageFiles.size();
        int current = (currentIndex >= 0 && total > 0) ? (currentIndex + 1) : 0;
        String timeLabel = INTERVAL_LABELS[intervalIndex];
        String orderLabel = randomMode ? "random" : "straight";

        statusText.setText(current + "_" + total + " images / " + timeLabel + " / " + orderLabel);
    }

    private void displayImage(File file) {
        Bitmap decoded = decodeSampledBitmap(file);
        if (decoded == null) {
            return;
        }

        Bitmap rotated = applyExifOrientation(decoded, file.getAbsolutePath());
        Bitmap grayscaled = toGrayscaleHighContrast(rotated);
        rotated.recycle();

        imageView.setImageBitmap(grayscaled);
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
        }
        currentBitmap = grayscaled;
    }

    private Bitmap applyExifOrientation(Bitmap src, String path) {
        Matrix matrix = new Matrix();
        boolean hasTransform = true;

        try {
            ExifInterface exif = new ExifInterface(path);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.setRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.setRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.setRotate(270);
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    matrix.setScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.setRotate(180);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    matrix.setRotate(90);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    matrix.setRotate(270);
                    matrix.postScale(-1, 1);
                    break;
                default:
                    hasTransform = false;
                    break;
            }
        } catch (IOException e) {
            hasTransform = false;
        }

        if (!hasTransform) {
            return src;
        }

        try {
            Bitmap result = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
            if (result != src) {
                src.recycle();
            }
            return result;
        } catch (Throwable t) {
            return src;
        }
    }

    private Bitmap toGrayscaleHighContrast(Bitmap src) {
        int width = src.getWidth();
        int height = src.getHeight();

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(result);

        ColorMatrix grayMatrix = new ColorMatrix();
        grayMatrix.setSaturation(0);

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
        handler.removeCallbacks(pendingRightClickRunnable);
        handler.removeCallbacks(pendingLeftClickRunnable);
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
        }
    }
}
