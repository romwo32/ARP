package com.hbrs;

import android.Manifest;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

/** Keeps the live camera preview separate from the robot controls. */
final class CameraPreviewController {
    private final ComponentActivity activity;
    private final PreviewView previewView;
    private final TextView statusView;
    private final Button retryButton;

    private ProcessCameraProvider cameraProvider;
    private Preview activePreview;
    private boolean isActive;
    private boolean isStarting;

    CameraPreviewController(ComponentActivity activity, PreviewView previewView,
                            TextView statusView, Button retryButton) {
        this.activity = activity;
        this.previewView = previewView;
        this.statusView = statusView;
        this.retryButton = retryButton;
    }

    void showPreview() {
        isActive = true;
        prepareToOpen();
        startCameraIfReady();
    }

    void prepareToOpen() {
        statusView.setText(R.string.camera_starting);
        retryButton.setVisibility(View.GONE);
    }

    void showPermissionDenied() {
        showError(R.string.camera_permission_denied);
    }

    void stopPreview() {
        isActive = false;
        if (cameraProvider != null && activePreview != null) {
            cameraProvider.unbind(activePreview);
            activePreview = null;
        }
    }

    void close() {
        isActive = false;
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            activePreview = null;
        }
    }

    private void startCameraIfReady() {
        if (!isActive || activePreview != null || isStarting) return;

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            showError(R.string.camera_permission_denied);
            return;
        }

        isStarting = true;
        ListenableFuture<ProcessCameraProvider> providerFuture =
                ProcessCameraProvider.getInstance(activity);
        providerFuture.addListener(() -> bindCamera(providerFuture),
                ContextCompat.getMainExecutor(activity));
    }

    private void bindCamera(ListenableFuture<ProcessCameraProvider> providerFuture) {
        isStarting = false;
        if (!isActive || activity.isFinishing() || activity.isDestroyed()) return;

        try {
            cameraProvider = providerFuture.get();
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            cameraProvider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, preview);
            activePreview = preview;
            statusView.setText(R.string.camera_live);
        } catch (Exception error) {
            showError(R.string.camera_unavailable);
        }
    }

    private void showError(int message) {
        statusView.setText(message);
        retryButton.setVisibility(View.VISIBLE);
    }
}
