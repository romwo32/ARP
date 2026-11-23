package com.hbrs;

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

// Diese Klasse kümmert sich nur um das Kamerabild, nicht um die Motoren.
final class CameraPreviewController {
    private final ComponentActivity activity;
    private final PreviewView kamerabild;
    private final TextView statusText;
    private final Button erneutButton;
    private ProcessCameraProvider kamera;
    private Preview vorschau;
    private boolean sichtbar;
    private boolean startet;

    CameraPreviewController(ComponentActivity activity, PreviewView kamerabild,
                            TextView statusText, Button erneutButton) {
        this.activity = activity;
        this.kamerabild = kamerabild;
        this.statusText = statusText;
        this.erneutButton = erneutButton;
    }

    void starten() {
        sichtbar = true;
        if (vorschau != null || startet) {
            return;
        }
        statusText.setText(R.string.camera_starting);
        erneutButton.setVisibility(View.GONE);
        startet = true;

        // Die Kamera ist erst verfügbar, wenn Android mit dem Öffnen fertig ist.
        ListenableFuture<ProcessCameraProvider> anfrage = ProcessCameraProvider.getInstance(activity);
        anfrage.addListener(() -> zeigeKamerabild(anfrage), ContextCompat.getMainExecutor(activity));
    }

    private void zeigeKamerabild(ListenableFuture<ProcessCameraProvider> anfrage) {
        startet = false;
        if (!sichtbar || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        try {
            kamera = anfrage.get();
            Preview bild = new Preview.Builder().build();
            bild.setSurfaceProvider(kamerabild.getSurfaceProvider());
            // CameraX pausiert die Kamera automatisch, wenn die App im Hintergrund ist.
            kamera.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, bild);
            vorschau = bild;
            statusText.setText(R.string.camera_live);
        } catch (Exception fehler) {
            zeigeFehler(R.string.camera_unavailable);
        }
    }

    void stoppen() {
        sichtbar = false;
        if (kamera != null && vorschau != null) {
            kamera.unbind(vorschau);
            vorschau = null;
        }
    }

    void zeigeFehler(int meldung) {
        statusText.setText(meldung);
        erneutButton.setVisibility(View.VISIBLE);
    }
}
