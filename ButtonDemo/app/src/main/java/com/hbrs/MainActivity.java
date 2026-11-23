package com.hbrs;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.camera.view.PreviewView;

import com.hbrs.ORB.ORB;

public class MainActivity extends ComponentActivity {
    private static final int BLUETOOTH_PERMISSION = 1;
    private static final int CAMERA_PERMISSION = 2;

    // The test uses the ORB object to check the motor commands.
    private ORB orb;

    private RobotController robotController;
    private CameraPreviewController cameraController;

    private Button connectButton;
    private Button retryCameraButton;
    private View manualControls;
    private View cameraPanel;
    private boolean cameraMode;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        orb = new ORB(this);
        createControllers();
        setClickListeners();
    }

    private void createControllers() {
        connectButton = findViewById(R.id.connect);
        TextView robotStatus = findViewById(R.id.status);
        Button forwardButton = findViewById(R.id.forward);
        Button reverseButton = findViewById(R.id.reverse);
        Button leftButton = findViewById(R.id.left);
        Button rightButton = findViewById(R.id.right);
        manualControls = findViewById(R.id.manual_controls);
        cameraPanel = findViewById(R.id.camera_panel);
        PreviewView previewView = findViewById(R.id.camera_preview);
        TextView cameraStatus = findViewById(R.id.camera_status);
        retryCameraButton = findViewById(R.id.camera_retry);

        robotController = new RobotController(this, orb, connectButton, robotStatus,
                forwardButton, reverseButton, leftButton, rightButton);
        cameraController = new CameraPreviewController(this, previewView,
                cameraStatus, retryCameraButton);
    }

    private void setClickListeners() {
        connectButton.setOnClickListener(view -> connectToRobot());
        findViewById(R.id.open_camera).setOnClickListener(view -> openCamera());
        findViewById(R.id.close_camera).setOnClickListener(view -> closeCamera());
        retryCameraButton.setOnClickListener(view -> retryCamera());
    }

    private void connectToRobot() {
        robotController.stopDriving();
        if (needsBluetoothPermission()) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    BLUETOOTH_PERMISSION);
            return;
        }
        robotController.selectRobot();
    }

    private boolean needsBluetoothPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED;
    }

    private void openCamera() {
        robotController.stopDriving();
        cameraMode = true;
        manualControls.setVisibility(View.GONE);
        cameraPanel.setVisibility(View.VISIBLE);
        cameraController.prepareToOpen();

        if (hasCameraPermission()) {
            cameraController.showPreview();
        } else {
            requestCameraPermission();
        }
    }

    private void retryCamera() {
        if (hasCameraPermission()) {
            cameraController.showPreview();
        } else {
            requestCameraPermission();
        }
    }

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
    }

    private void closeCamera() {
        cameraMode = false;
        cameraController.stopPreview();
        cameraPanel.setVisibility(View.GONE);
        manualControls.setVisibility(View.VISIBLE);
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == CAMERA_PERMISSION) {
            handleCameraPermission(results);
        } else if (request == BLUETOOTH_PERMISSION) {
            handleBluetoothPermission(results);
        }
    }

    private void handleCameraPermission(int[] results) {
        if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
            cameraController.showPermissionDenied();
        } else if (cameraMode) {
            cameraController.showPreview();
        }
    }

    private void handleBluetoothPermission(int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            robotController.selectRobot();
        } else {
            robotController.showPermissionDenied();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) robotController.stopDriving();
    }

    @Override
    protected void onPause() {
        robotController.stopDriving();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cameraController.close();
        robotController.close();
        super.onDestroy();
    }
}
