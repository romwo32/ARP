package com.hbrs;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

import com.hbrs.ORB.ORB;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Handles Bluetooth, robot connection, and the four manual drive buttons. */
final class RobotController {
    private static final int DRIVE_SPEED = 1500;
    private static final int COMMAND_INTERVAL_MS = 100;

    private final ComponentActivity activity;
    private final ORB orb;
    private final Button connectButton;
    private final Button forwardButton;
    private final Button reverseButton;
    private final Button leftButton;
    private final Button rightButton;
    private final TextView statusView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService connectionWorker = Executors.newSingleThreadExecutor();

    private View heldButton;
    private int leftSpeed;
    private int rightSpeed;

    RobotController(ComponentActivity activity, ORB orb, Button connectButton, TextView statusView,
                    Button forwardButton, Button reverseButton, Button leftButton, Button rightButton) {
        this.activity = activity;
        this.orb = orb;
        this.connectButton = connectButton;
        this.statusView = statusView;
        this.forwardButton = forwardButton;
        this.reverseButton = reverseButton;
        this.leftButton = leftButton;
        this.rightButton = rightButton;

        setDriveEnabled(false);
        addDriveListener(forwardButton, DRIVE_SPEED, DRIVE_SPEED);
        addDriveListener(reverseButton, -DRIVE_SPEED, -DRIVE_SPEED);
        addDriveListener(leftButton, -DRIVE_SPEED, DRIVE_SPEED);
        addDriveListener(rightButton, DRIVE_SPEED, -DRIVE_SPEED);
    }

    void selectRobot() {
        stopDriving();
        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            statusView.setText(R.string.bluetooth_off);
            return;
        }

        BluetoothDevice[] devices = adapter.getBondedDevices().toArray(new BluetoothDevice[0]);
        if (devices.length == 0) {
            statusView.setText(R.string.no_devices);
            return;
        }

        showRobotPicker(devices);
    }

    void showPermissionDenied() {
        statusView.setText(R.string.permission_denied);
    }

    void stopDriving() {
        if (heldButton == null) return;

        handler.removeCallbacks(repeatDrive);
        setMotorSpeeds(0, 0);
        heldButton.setKeepScreenOn(false);
        heldButton.setPressed(false);
        heldButton = null;
    }

    void close() {
        stopDriving();
        connectionWorker.execute(orb::close);
        connectionWorker.shutdown();
    }

    private BluetoothAdapter getBluetoothAdapter() {
        BluetoothManager manager = activity.getSystemService(BluetoothManager.class);
        return manager == null ? null : manager.getAdapter();
    }

    private void showRobotPicker(BluetoothDevice[] devices) {
        String[] names = new String[devices.length];
        for (int i = 0; i < devices.length; i++) {
            String name = devices[i].getName();
            String address = devices[i].getAddress();
            names[i] = name == null ? address : name + "\n" + address;
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.select_robot)
                .setItems(names, (dialog, index) -> connectTo(devices[index]))
                .show();
    }

    private void connectTo(BluetoothDevice device) {
        setDriveEnabled(false);
        connectButton.setEnabled(false);
        statusView.setText(R.string.connecting);

        connectionWorker.execute(() -> {
            orb.openBT(device);
            activity.runOnUiThread(this::finishConnection);
        });
    }

    private void finishConnection() {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        boolean connected = orb.orb_BT.isConnected();
        if (connected) configureMotors();

        setDriveEnabled(connected);
        connectButton.setEnabled(true);
        statusView.setText(connected ? R.string.connected : R.string.connection_failed);
    }

    private void configureMotors() {
        synchronized (orb.configToORB) {
            orb.configMotor(ORB.M1, 144, 50, 50, 30);
            orb.configMotor(ORB.M4, 144, 50, 50, 30);
        }
        setMotorSpeeds(0, 0);
    }

    private void setDriveEnabled(boolean enabled) {
        forwardButton.setEnabled(enabled);
        reverseButton.setEnabled(enabled);
        leftButton.setEnabled(enabled);
        rightButton.setEnabled(enabled);
    }

    private void addDriveListener(Button button, int leftSpeed, int rightSpeed) {
        button.setOnTouchListener((view, event) ->
                handleDriveTouch(view, event, leftSpeed, rightSpeed));
    }

    private boolean handleDriveTouch(View button, MotionEvent event,
                                     int nextLeftSpeed, int nextRightSpeed) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                heldButton = button;
                leftSpeed = nextLeftSpeed;
                rightSpeed = nextRightSpeed;
                button.setPressed(true);
                button.setKeepScreenOn(true);
                repeatDrive.run();
                break;
            case MotionEvent.ACTION_UP:
                stopDriving();
                button.performClick();
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                stopDriving();
                break;
        }
        return true;
    }

    // Keep sending while a direction stays pressed; some robot boards stop without refreshes.
    private final Runnable repeatDrive = new Runnable() {
        @Override
        public void run() {
            if (heldButton == null) return;
            if (!orb.orb_BT.isConnected()) {
                stopDriving();
                setDriveEnabled(false);
                statusView.setText(R.string.disconnected);
                return;
            }

            setMotorSpeeds(leftSpeed, rightSpeed);
            handler.postDelayed(this, COMMAND_INTERVAL_MS);
        }
    };

    private void setMotorSpeeds(int leftSpeed, int rightSpeed) {
        // M4 is mounted in the opposite direction from M1.
        synchronized (orb.propToORB) {
            orb.setMotor(ORB.M1, ORB.SPEED_MODE, leftSpeed, 0);
            orb.setMotor(ORB.M4, ORB.SPEED_MODE, -rightSpeed, 0);
        }
    }
}
