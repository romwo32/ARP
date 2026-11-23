package com.hbrs;

import android.Manifest;
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

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

import com.hbrs.ORB.ORB;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity2 extends ComponentActivity
        implements View.OnClickListener, View.OnTouchListener, SeekBar.OnSeekBarChangeListener {
    private static final int DRIVE_SPEED = 5100;
    private static final int REFRESH_MS = 100;
    private static final int BLUETOOTH_PERMISSION = 1;
    private static final int CAMERA_PERMISSION = 2;

    private final Handler timer = new Handler(Looper.getMainLooper());
    // Bluetooth arbeitet im Hintergrund, damit die Bedienung nicht hängen bleibt.
    private final ExecutorService bluetoothArbeit = Executors.newSingleThreadExecutor();
    private ORB orb;
    private Button verbindenButton, vorwaertsButton, rueckwaertsButton, linksButton, rechtsButton;
    private TextView statusText, tempoText, lenkText;
    private SeekBar tempoRegler, lenkRegler;
    private View gedrueckterButton;
    private boolean reglerAktiv;
    private View steuerung, kameraAnsicht;
    private CameraPreviewController kamera;
    private final OnBackPressedCallback kameraZurueck = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            kameraSchliessen();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main2);
        orb = new ORB(this);

        verbindenButton = findViewById(R.id.connect);
        vorwaertsButton = findViewById(R.id.forward);
        rueckwaertsButton = findViewById(R.id.reverse);
        linksButton = findViewById(R.id.left);
        rechtsButton = findViewById(R.id.right);
        statusText = findViewById(R.id.status);
        // Regler
        tempoRegler = findViewById(R.id.speed_slider);
        lenkRegler = findViewById(R.id.steering_slider);
        tempoText = findViewById(R.id.speed_value);
        lenkText = findViewById(R.id.steering_value);

        tempoRegler.setOnSeekBarChangeListener(this);
        lenkRegler.setOnSeekBarChangeListener(this);
        zeigeReglerWerte();

        verbindenButton.setOnClickListener(this);
        // Alle Fahrbuttons benutzen dieselbe Logik zum Halten und Loslassen.
        vorwaertsButton.setOnTouchListener(this);
        rueckwaertsButton.setOnTouchListener(this);
        linksButton.setOnTouchListener(this);
        rechtsButton.setOnTouchListener(this);
        bedienungFreigeben(false);

        steuerung = findViewById(R.id.controls_scroll);
        kameraAnsicht = findViewById(R.id.camera_panel);
        kamera = new CameraPreviewController(this, findViewById(R.id.camera_preview),
                findViewById(R.id.camera_status), findViewById(R.id.camera_retry));
        findViewById(R.id.open_camera).setOnClickListener(this);
        findViewById(R.id.close_camera).setOnClickListener(this);
        findViewById(R.id.camera_retry).setOnClickListener(this);
        getOnBackPressedDispatcher().addCallback(this, kameraZurueck);
    }

    // Android ruft diese Methoden bei Klicks und Regleränderungen auf.
    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.connect) {
            roboterAuswaehlen();
        } else if (view.getId() == R.id.open_camera) {
            kameraOeffnen();
        } else if (view.getId() == R.id.close_camera) {
            kameraSchliessen();
        } else if (view.getId() == R.id.camera_retry) {
            kameraStarten();
        }
    }

    private void kameraOeffnen() {
        // Vor dem Wechsel zur Kamera die Fahrt beenden, auch bei aktiven Reglern.
        anhalten();
        steuerung.setVisibility(View.GONE);
        kameraAnsicht.setVisibility(View.VISIBLE);
        kameraZurueck.setEnabled(true);
        kameraStarten();
    }

    private void kameraStarten() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            kamera.starten();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        }
    }

    private void kameraSchliessen() {
        kamera.stoppen();
        kameraAnsicht.setVisibility(View.GONE);
        steuerung.setVisibility(View.VISIBLE);
        kameraZurueck.setEnabled(false);
    }

    @Override
    public void onProgressChanged(SeekBar regler, int wert, boolean vomBenutzer) {
        zeigeReglerWerte();
        if (vomBenutzer) {
            reglerGeaendert();
        }
    }

    // Loslassen verändert die eingestellte Geschwindigkeit nicht.
    @Override
    public void onStartTrackingTouch(SeekBar regler) {}

    @Override
    public void onStopTrackingTouch(SeekBar regler) {}

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

    private void zeigeReglerWerte() {
        tempoText.setText(getString(R.string.speed_value, tempoRegler.getProgress(),
                (tempoRegler.getProgress() - 50) * 2));
        lenkText.setText(getString(R.string.steering_value, lenkRegler.getProgress()));
    }

    // Jede Änderung von Hand wirkt sofort. Loslassen lässt den Reglerwert bestehen.
    private void reglerGeaendert() {
        timer.removeCallbacks(fahrWiederholung);
        buttonLoslassen();
        reglerAktiv = tempoRegler.getProgress() != 50;
        tempoRegler.setKeepScreenOn(reglerAktiv);
        if (reglerAktiv) {
            fahrWiederholung.run();
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

    @Override
    public boolean onTouch(View button, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: // Finger drauf: sofort starten.
                anhalten();
                gedrueckterButton = button;
                button.setPressed(true);
                button.setKeepScreenOn(true);
                fahrWiederholung.run();
                break;
            case MotionEvent.ACTION_UP: // Finger losgelassen: stoppen.
                anhalten();
                button.performClick();
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                anhalten();
                break;
        }
        return true;
    }

    // Aktive Regler oder einen gehaltenen Button etwa alle 100 ms erneut auswerten.
    private final Runnable fahrWiederholung = new Runnable() {
        @Override
        public void run() {
            if (gedrueckterButton == null && !reglerAktiv) {
                return;
            }
            if (!orb.orb_BT.isConnected()) {
                anhalten();
                bedienungFreigeben(false);
                statusText.setText(R.string.disconnected);
                return;
            }

            if (reglerAktiv) {
                mitReglernFahren();
            } else if (gedrueckterButton == vorwaertsButton) {
                vorwaerts();
            } else if (gedrueckterButton == rueckwaertsButton) {
                rueckwaerts();
            } else if (gedrueckterButton == linksButton) {
                linksDrehen();
            } else if (gedrueckterButton == rechtsButton) {
                rechtsDrehen();
            }

            timer.postDelayed(this, REFRESH_MS);
        }
    };

    // Wiederholung beenden und beide Ketten auf Geschwindigkeit 0 setzen.
    private void anhalten() {
        timer.removeCallbacks(fahrWiederholung);
        if (gedrueckterButton != null || reglerAktiv) {
            motoren(0, 0);
        }
        buttonLoslassen();
        reglerAktiv = false;
        tempoRegler.setKeepScreenOn(false);
        // Nach Unterbrechungen oder einem Wechsel zu den Buttons wieder neutral starten.
        tempoRegler.setProgress(50);
        lenkRegler.setProgress(50);
    }

    private void buttonLoslassen() {
        if (gedrueckterButton != null) {
            gedrueckterButton.setKeepScreenOn(false);
            gedrueckterButton.setPressed(false);
            gedrueckterButton = null;
        }
    }

    private void bedienungFreigeben(boolean enabled) {
        vorwaertsButton.setEnabled(enabled);
        rueckwaertsButton.setEnabled(enabled);
        linksButton.setEnabled(enabled);
        rechtsButton.setEnabled(enabled);
        tempoRegler.setEnabled(enabled);
        lenkRegler.setEnabled(enabled);
    }

    // Bluetooth prüfen und bereits gekoppelte Geräte zur Auswahl anzeigen.
    private void roboterAuswaehlen() {
        anhalten();
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BLUETOOTH_PERMISSION);
            return;
        }

        BluetoothManager manager = getSystemService(BluetoothManager.class);
        if (manager == null) {
            statusText.setText(R.string.bluetooth_off);
            return;
        }
        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            statusText.setText(R.string.bluetooth_off);
            return;
        }

        BluetoothDevice[] geraete = adapter.getBondedDevices().toArray(new BluetoothDevice[0]);
        if (geraete.length == 0) {
            statusText.setText(R.string.no_devices);
            return;
        }

        String[] auswahlTexte = new String[geraete.length];
        for (int i = 0; i < geraete.length; i++) {
            String name = geraete[i].getName();
            String adresse = geraete[i].getAddress();
            if (name == null) {
                auswahlTexte[i] = adresse;
            } else {
                auswahlTexte[i] = name + "\n" + adresse;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_robot)
                .setItems(auswahlTexte, (dialog, i) -> verbinden(geraete[i]))
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int anfrage, String[] berechtigungen, int[] ergebnisse) {
        super.onRequestPermissionsResult(anfrage, berechtigungen, ergebnisse);
        if (anfrage == CAMERA_PERMISSION) {
            if (kameraAnsicht.getVisibility() == View.VISIBLE) {
                if (ergebnisse.length > 0 && ergebnisse[0] == PackageManager.PERMISSION_GRANTED) {
                    kamera.starten();
                } else {
                    kamera.zeigeFehler(R.string.camera_permission_denied);
                }
            }
            return;
        }
        if (anfrage != BLUETOOTH_PERMISSION) {
            return;
        }
        if (ergebnisse.length > 0 && ergebnisse[0] == PackageManager.PERMISSION_GRANTED) {
            roboterAuswaehlen();
        } else {
            statusText.setText(R.string.permission_denied);
        }
    }

    private void verbinden(BluetoothDevice roboter) {
        bedienungFreigeben(false);
        verbindenButton.setEnabled(false);
        statusText.setText(R.string.connecting);

        // Verbinden dauert eventuell etwas: Der Bildschirm soll dabei bedienbar bleiben.
        bluetoothArbeit.execute(() -> {
            orb.openBT(roboter);
            runOnUiThread(this::verbindungAbschliessen);
        });
    }

    private void verbindungAbschliessen() {
        if (isDestroyed() || isFinishing()) {
            return;
        }
        boolean verbunden = orb.orb_BT.isConnected();
        if (verbunden) {
            synchronized (orb.configToORB) {
                orb.configMotor(ORB.M1, 144, 50, 50, 30);
                orb.configMotor(ORB.M4, 144, 50, 50, 30);
            }
            motoren(0, 0);
        }
        bedienungFreigeben(verbunden);
        verbindenButton.setEnabled(true);
        if (verbunden) {
            statusText.setText(R.string.connected_controls);
        } else {
            statusText.setText(R.string.connection_failed);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            anhalten();
        }
    }

    // Auch beim Verlassen oder Schließen des Bildschirms stoppen.
    @Override
    protected void onPause() {
        anhalten();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        anhalten();
        kamera.stoppen();
        bluetoothArbeit.execute(orb::close);
        bluetoothArbeit.shutdown();
        super.onDestroy();
    }
}
