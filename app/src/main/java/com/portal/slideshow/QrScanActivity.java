package com.portal.slideshow;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.BarcodeFormat;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

public class QrScanActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private static final int REQ_CAMERA = 41;

    private SurfaceView preview;
    private TextView status;
    private Camera camera;
    private Camera.Size previewSize;
    private MultiFormatReader reader;
    private boolean surfaceReady;
    private boolean decoding;
    private boolean finished;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        reader = new MultiFormatReader();
        Hashtable<DecodeHintType, Object> hints = new Hashtable<DecodeHintType, Object>();
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        preview = new SurfaceView(this);
        preview.getHolder().addCallback(this);
        root.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View target = new View(this);
        target.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams targetLp = new FrameLayout.LayoutParams(dp(300), dp(300));
        targetLp.gravity = Gravity.CENTER;
        root.addView(target, targetLp);

        status = new TextView(this);
        status.setText("Point the Portal camera at a Google Photos QR code.");
        status.setTextColor(Color.WHITE);
        status.setTextSize(22f);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(Color.parseColor("#CC10131A"));
        status.setPadding(dp(24), dp(18), dp(24), dp(18));
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        statusLp.gravity = Gravity.BOTTOM;
        statusLp.leftMargin = dp(36);
        statusLp.rightMargin = dp(36);
        statusLp.bottomMargin = dp(36);
        root.addView(status, statusLp);

        Button close = new Button(this);
        close.setText("Close");
        close.setAllCaps(false);
        close.setTextSize(18f);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        closeLp.gravity = Gravity.TOP | Gravity.LEFT;
        closeLp.leftMargin = dp(18);
        closeLp.topMargin = dp(18);
        root.addView(close, closeLp);

        setContentView(root);
        if (hasCameraPermission()) {
            startCameraIfReady();
        } else {
            requestPermissions(new String[] { Manifest.permission.CAMERA }, REQ_CAMERA);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        startCameraIfReady();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        restartPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        stopCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraIfReady();
            } else {
                Toast.makeText(this, "Camera permission is needed to scan a QR code.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onPause() {
        stopCamera();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraIfReady();
    }

    @Override
    protected void onDestroy() {
        finished = true;
        stopCamera();
        super.onDestroy();
    }

    private void startCameraIfReady() {
        if (!surfaceReady || !hasCameraPermission() || camera != null || finished) return;
        try {
            int cameraId = chooseCameraId();
            camera = Camera.open(cameraId);
            Camera.Parameters params = camera.getParameters();
            params.setPreviewFormat(ImageFormat.NV21);
            previewSize = choosePreviewSize(params.getSupportedPreviewSizes());
            params.setPreviewSize(previewSize.width, previewSize.height);
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            camera.setParameters(params);
            camera.setPreviewDisplay(preview.getHolder());
            camera.setPreviewCallbackWithBuffer(this);
            addPreviewBuffers();
            camera.startPreview();
            status.setText("Point the Portal camera at a Google Photos QR code.");
        } catch (Exception e) {
            status.setText("Could not open the Portal camera. Close and try again.");
            stopCamera();
        }
    }

    private int chooseCameraId() {
        int count = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < count; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return i;
        }
        return count > 1 ? 1 : 0;
    }

    private Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
        Camera.Size best = sizes.get(0);
        int bestScore = Integer.MAX_VALUE;
        for (Camera.Size size : sizes) {
            int score = Math.abs(size.width - 1280) + Math.abs(size.height - 720);
            if (score < bestScore) {
                best = size;
                bestScore = score;
            }
        }
        return best;
    }

    private void restartPreview() {
        if (camera == null) return;
        try {
            camera.stopPreview();
            camera.setPreviewDisplay(preview.getHolder());
            addPreviewBuffers();
            camera.startPreview();
        } catch (Exception ignored) { }
    }

    private void addPreviewBuffers() {
        if (camera == null || previewSize == null) return;
        int bitsPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21);
        int bufferSize = previewSize.width * previewSize.height * bitsPerPixel / 8;
        camera.addCallbackBuffer(new byte[bufferSize]);
        camera.addCallbackBuffer(new byte[bufferSize]);
    }

    private void stopCamera() {
        if (camera == null) return;
        try {
            camera.setPreviewCallbackWithBuffer(null);
            camera.stopPreview();
            camera.release();
        } catch (Exception ignored) { }
        camera = null;
        decoding = false;
    }

    @Override
    public void onPreviewFrame(final byte[] data, final Camera camera) {
        if (decoding || finished || previewSize == null) {
            recycleBuffer(camera, data);
            return;
        }
        decoding = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    Result result = decode(data, previewSize.width, previewSize.height);
                    if (result != null) {
                        handleQrCode(result.getText());
                        return;
                    }
                } finally {
                    decoding = false;
                    recycleBuffer(camera, data);
                }
            }
        }).start();
    }

    private Result decode(byte[] data, int width, int height) {
        Result result = decodeBitmap(data, width, height);
        if (result != null) return result;
        byte[] rotated = rotateCounterClockwise(data, width, height);
        return decodeBitmap(rotated, height, width);
    }

    private Result decodeBitmap(byte[] data, int width, int height) {
        try {
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    data, width, height, 0, 0, width, height, false);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            return reader.decodeWithState(bitmap);
        } catch (NotFoundException ignored) {
            return null;
        } catch (ReaderException ignored) {
            return null;
        } catch (Exception ignored) {
            return null;
        } finally {
            reader.reset();
        }
    }

    private byte[] rotateCounterClockwise(byte[] data, int width, int height) {
        byte[] rotated = new byte[data.length];
        int index = 0;
        for (int x = width - 1; x >= 0; x--) {
            for (int y = 0; y < height; y++) {
                rotated[index++] = data[y * width + x];
            }
        }
        return rotated;
    }

    private void recycleBuffer(Camera camera, byte[] data) {
        try {
            if (camera != null && data != null && !finished) camera.addCallbackBuffer(data);
        } catch (Exception ignored) { }
    }

    private void handleQrCode(final String rawValue) {
        runOnUiThread(new Runnable() {
            public void run() {
                String albumUrl = rawValue == null ? "" : rawValue.trim();
                if (TextUtils.isEmpty(albumUrl) || !isValidWebUrl(albumUrl)) {
                    status.setText("QR code found, but it is not a web link.");
                    return;
                }
                finished = true;
                SharedPreferences.Editor editor = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit();
                editor.putString(MainActivity.KEY_ALBUM_URL, albumUrl);
                editor.putInt(MainActivity.KEY_MODE, MainActivity.MODE_GOOGLE_PHOTOS);
                editor.apply();

                Intent result = new Intent();
                result.putExtra("album_url", albumUrl);
                setResult(RESULT_OK, result);
                Toast.makeText(QrScanActivity.this, "Album QR code saved.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isValidWebUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
