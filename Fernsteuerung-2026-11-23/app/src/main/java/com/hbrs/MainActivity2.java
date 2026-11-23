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
import android.widget.SeekBar;
import android.widget.TextView;

import com.hbrs.ORB.ORB;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity2 extends Activity {
    private static final int DRIVE_SPEED = 5100;
    private static final int REFRESH_MS = 100;
    private static final int BLUETOOTH_PERMISSION = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService connectionWorker = Executors.newSingleThreadExecutor();
    private ORB orb;
    private Button connect, forward, reverse, left, right;
    private TextView status, tempoText, lenkText;
    private SeekBar tempoRegler, lenkRegler;
    private View heldButton;
    private boolean reglerAktiv;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main2);
        orb = new ORB(this);

        connect = findViewById(R.id.connect);
        forward = findViewById(R.id.forward);
        reverse = findViewById(R.id.reverse);
        left = findViewById(R.id.left);
        right = findViewById(R.id.right);
        status = findViewById(R.id.status);
        //slider
        tempoRegler = findViewById(R.id.speed_slider);
        lenkRegler = findViewById(R.id.steering_slider);
        tempoText = findViewById(R.id.speed_value);
        lenkText = findViewById(R.id.steering_value);

        // Änderungen von Hand steuern direkt. Zurücksetzen im Code startet keine Fahrt.
        SeekBar.OnSeekBarChangeListener anzeigen = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateSliderLabels();
                if (fromUser) reglerGeaendert();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        tempoRegler.setOnSeekBarChangeListener(anzeigen);
        lenkRegler.setOnSeekBarChangeListener(anzeigen);
        updateSliderLabels();

        connect.setOnClickListener(v -> selectRobot());
        // Alle Fahrbuttons benutzen dieselbe Logik zum Halten und Loslassen.
        forward.setOnTouchListener(this::onDriveTouch);
        reverse.setOnTouchListener(this::onDriveTouch);
        left.setOnTouchListener(this::onDriveTouch);
        right.setOnTouchListener(this::onDriveTouch);
        setDriveEnabled(false);
    }

    // Positiv heißt: Diese Kette soll vorwärts laufen. Negativ heißt: rückwärts.
    private void vorwaerts() {
        motoren(DRIVE_SPEED, DRIVE_SPEED);
    }

    private void rueckwaerts() {
        motoren(-DRIVE_SPEED, -DRIVE_SPEED);
    }

    // Zum Drehen auf der Stelle laufen die Ketten gegeneinander.
    private void linksDrehen() {
        motoren(-DRIVE_SPEED, DRIVE_SPEED);
    }

    private void rechtsDrehen() {
        motoren(DRIVE_SPEED, -DRIVE_SPEED);
    }

    private void updateSliderLabels() {
        tempoText.setText(getString(R.string.speed_value, tempoRegler.getProgress(),
                (tempoRegler.getProgress() - 50) * 2));
        lenkText.setText(getString(R.string.steering_value, lenkRegler.getProgress()));
    }

    // Jede Änderung von Hand wirkt sofort. Loslassen lässt den Reglerwert bestehen.
    private void reglerGeaendert() {
        handler.removeCallbacks(repeatDrive);
        if (heldButton != null) {
            heldButton.setKeepScreenOn(false);
            heldButton.setPressed(false);
            heldButton = null;
        }
        reglerAktiv = tempoRegler.getProgress() != 50;
        tempoRegler.setKeepScreenOn(reglerAktiv);
        if (reglerAktiv) {
            repeatDrive.run();
        } else {
            motoren(0, 0);
        }
    }

    private void mitReglernFahren() {
        // Aus 0..100 wird -1..+1. Die Mitte 50 ergibt 0.
        float tempo = (tempoRegler.getProgress() - 50) / 50f;
        float lenkung = (lenkRegler.getProgress() - 50) / 50f;
        float links = tempo;
        float rechts = tempo;

        if (lenkung > 0) {
            // Rechtskurve: rechte Kette langsamer, höchstens bis zum Stillstand.
            rechts = tempo * (1 - lenkung);
        } else {
            // Linkskurve: linke Kette langsamer.
            links = tempo * (1 + lenkung);
        }

        motoren(Math.round(DRIVE_SPEED * links), Math.round(DRIVE_SPEED * rechts));
    }

    private void motoren(int linkeKette, int rechteKette) {
        // M1 ist links, M4 rechts. Das Minus bei M1 gleicht die Montage aus.
        // Beide Werte zusammen vorbereiten, bevor die ORB-Bibliothek sie versendet.
        synchronized (orb.propToORB) {
            orb.setMotor(ORB.M1, ORB.SPEED_MODE, -linkeKette, 0);
            orb.setMotor(ORB.M4, ORB.SPEED_MODE, rechteKette, 0);
        }
    }

    private boolean onDriveTouch(View button, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: // Finger drauf: sofort starten.
                stopDriving();
                heldButton = button;
                button.setPressed(true);
                button.setKeepScreenOn(true);
                repeatDrive.run();
                break;
            case MotionEvent.ACTION_UP: // Finger losgelassen: stoppen.
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

    // Aktive Regler oder einen gehaltenen Button etwa alle 100 ms erneut auswerten.
    private final Runnable repeatDrive = new Runnable() {
        @Override
        public void run() {
            if (heldButton == null && !reglerAktiv) return;
            if (!orb.orb_BT.isConnected()) {
                stopDriving();
                setDriveEnabled(false);
                status.setText(R.string.disconnected);
                return;
            }

            if (reglerAktiv) {
                mitReglernFahren();
            } else if (heldButton == forward) {
                vorwaerts();
            } else if (heldButton == reverse) {
                rueckwaerts();
            } else if (heldButton == left) {
                linksDrehen();
            } else if (heldButton == right) {
                rechtsDrehen();

            }

            handler.postDelayed(this, REFRESH_MS);
        }
    };

    // Wiederholung beenden und beide Ketten auf Geschwindigkeit 0 setzen.
    private void stopDriving() {
        handler.removeCallbacks(repeatDrive);
        if (heldButton != null || reglerAktiv) motoren(0, 0);
        if (heldButton != null) {
            heldButton.setKeepScreenOn(false);
            heldButton.setPressed(false);
            heldButton = null;
        }
        reglerAktiv = false;
        tempoRegler.setKeepScreenOn(false);
        // Nach Unterbrechungen oder einem Wechsel zu den Buttons wieder neutral starten.
        tempoRegler.setProgress(50);
        lenkRegler.setProgress(50);
    }

    private void setDriveEnabled(boolean enabled) {
        forward.setEnabled(enabled);
        reverse.setEnabled(enabled);
        left.setEnabled(enabled);
        right.setEnabled(enabled);
        tempoRegler.setEnabled(enabled);
        lenkRegler.setEnabled(enabled);
    }

    // Bluetooth prüfen und bereits gekoppelte Geräte zur Auswahl anzeigen.
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

        // Verbinden dauert eventuell etwas: Der Bildschirm soll dabei bedienbar bleiben.
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
                    motoren(0, 0);
                }
                setDriveEnabled(connected);
                connect.setEnabled(true);
                status.setText(connected ? R.string.connected_controls : R.string.connection_failed);
            });
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) stopDriving();
    }

    // Auch beim Verlassen oder Schließen des Bildschirms stoppen.
    @Override
    protected void onPause() {
        stopDriving();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopDriving();
        connectionWorker.execute(orb::close);
        connectionWorker.shutdown();
        super.onDestroy();
    }
}
