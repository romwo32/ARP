package com.hbrs;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.hbrs.ORB.ORB;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int DRIVE_SPEED = 1500;
    private static final int REFRESH_MS = 100;
    private static final int BLUETOOTH_PERMISSION = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService connectionWorker = Executors.newSingleThreadExecutor();
    private ORB orb;
    private Button connect, forward, reverse;
    private TextView status;
    private View heldButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        orb = new ORB(this);
        connect = findViewById(R.id.connect);
        forward = findViewById(R.id.forward);
        reverse = findViewById(R.id.reverse);
        status = findViewById(R.id.status);
        connect.setOnClickListener(v -> selectRobot());
        forward.setOnTouchListener(this::onDriveTouch);
        reverse.setOnTouchListener(this::onDriveTouch);
        setDriveEnabled(false);
    }

    private boolean onDriveTouch(View button, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                heldButton = button;
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

    // Auch ein still gehaltener Finger braucht regelmäßig einen neuen ORB-Fahrbefehl.
    private final Runnable repeatDrive = new Runnable() {
        @Override
        public void run() {
            if (!orb.orb_BT.isConnected()) {
                stopDriving();
                setDriveEnabled(false);
                status.setText(R.string.disconnected);
                return;
            }
            setMotorSpeed(heldButton == forward ? DRIVE_SPEED : -DRIVE_SPEED);
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private void stopDriving() {
        if (heldButton == null) return;
        handler.removeCallbacks(repeatDrive);
        setMotorSpeed(0);
        heldButton.setKeepScreenOn(false);
        heldButton.setPressed(false);
        heldButton = null;
    }

    private void setMotorSpeed(int speed) {
        // Die Motoren sind spiegelverkehrt montiert. Beide Werte gehören in dasselbe Paket.
        synchronized (orb.propToORB) {
            orb.setMotor(ORB.M1, ORB.SPEED_MODE, speed, 0);
            orb.setMotor(ORB.M4, ORB.SPEED_MODE, -speed, 0);
        }
    }

    private void setDriveEnabled(boolean enabled) {
        forward.setEnabled(enabled);
        reverse.setEnabled(enabled);
    }

    private void selectRobot() {
        stopDriving();
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BLUETOOTH_PERMISSION);
            return;
        }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            status.setText(R.string.bluetooth_off);
            return;
        }
        BluetoothDevice[] devices = adapter.getBondedDevices().toArray(new BluetoothDevice[0]);
        if (devices.length == 0) {
            status.setText(R.string.no_devices);
            return;
        }
        String[] labels = new String[devices.length];
        for (int i = 0; i < devices.length; i++) {
            String name = devices[i].getName();
            String address = devices[i].getAddress();
            labels[i] = name == null ? address : name + "\n" + address;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_robot)
                .setItems(labels, (dialog, i) -> connectRobot(devices[i]))
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != BLUETOOTH_PERMISSION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            selectRobot();
        } else {
            status.setText(R.string.permission_denied);
        }
    }

    private void connectRobot(BluetoothDevice device) {
        setDriveEnabled(false);
        connect.setEnabled(false);
        status.setText(R.string.connecting);
        connectionWorker.execute(() -> {
            orb.openBT(device);
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                boolean connected = orb.orb_BT.isConnected();
                if (connected) {
                    synchronized (orb.configToORB) {
                        orb.configMotor(ORB.M1, 144, 50, 50, 30);
                        orb.configMotor(ORB.M4, 144, 50, 50, 30);
                    }
                    setMotorSpeed(0);
                }
                setDriveEnabled(connected);
                connect.setEnabled(true);
                status.setText(connected ? R.string.connected : R.string.connection_failed);
            });
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) stopDriving();
    }

    @Override
    protected void onPause() {
        stopDriving();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        connectionWorker.execute(orb::close);
        connectionWorker.shutdown();
        super.onDestroy();
    }
}
