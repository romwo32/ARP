package com.hbrs;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;

import androidx.test.platform.app.InstrumentationRegistry;

import com.hbrs.ORB.ORB;
import com.hbrs.ORB.ORB_RemoteBT;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class MainActivity2TouchTest {
    private static final long HOLD_MS = 10_000;
    // Android-Timer können unter Last verzögert werden. Größere Lücken schlagen fehl.
    private static final long MAX_COMMAND_GAP_MS = 500;

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity2 activity;
    private ORB orb;
    private View forward;
    private View reverse;
    private View left;
    private View right;
    private SeekBar tempoRegler, lenkRegler;
    private int driveSpeed;
    private volatile boolean connected = true;
    private long downTime;

    @Before
    public void prepareConnectedRobot() throws Exception {
        activity = (MainActivity2) instrumentation.startActivitySync(
                new Intent(instrumentation.getTargetContext(), MainActivity2.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        forward = activity.findViewById(R.id.forward);
        reverse = activity.findViewById(R.id.reverse);
        left = activity.findViewById(R.id.left);
        right = activity.findViewById(R.id.right);
        tempoRegler = activity.findViewById(R.id.speed_slider);
        lenkRegler = activity.findViewById(R.id.steering_slider);
        Field speedField = MainActivity2.class.getDeclaredField("DRIVE_SPEED");
        speedField.setAccessible(true);
        driveSpeed = speedField.getInt(null);
        for (View button : new View[]{forward, reverse, left, right}) {
            assertFalse("Vor dem Verbinden sind alle Fahrbuttons gesperrt", button.isEnabled());
        }

        assertFalse("Tempo vor der Verbindung gesperrt", tempoRegler.isEnabled());
        assertFalse("Lenkung vor der Verbindung gesperrt", lenkRegler.isEnabled());

        // Der Test liest die ORB-Pakete selbst, deshalb zuerst den Sendethread beenden.
        Field orbField = MainActivity2.class.getDeclaredField("orb");
        orbField.setAccessible(true);
        orb = (ORB) orbField.get(activity);
        orb.close();
        Field threadField = ORB.class.getDeclaredField("mainThread");
        threadField.setAccessible(true);
        Thread sender = (Thread) threadField.get(orb);
        sender.join(3000);
        assertFalse("ORB-Sendethread wurde beendet", sender.isAlive());

        // Eine bestehende Verbindung simulieren. Touch-Listener und Motorprotokoll laufen echt.
        orb.orb_BT = new ORB_RemoteBT(orb) {
            @Override
            public boolean isConnected() {
                return connected;
            }
        };
        orb.settingsToORB.isNew = false;
        orb.configToORB.isNew = false;
        nextPacket();
        instrumentation.runOnMainSync(() -> {
            forward.setEnabled(true);
            reverse.setEnabled(true);
            left.setEnabled(true);
            right.setEnabled(true);
            tempoRegler.setEnabled(true);
            lenkRegler.setEnabled(true);
        });
    }

    @After
    public void closeActivity() {
        if (activity != null) {
            instrumentation.runOnMainSync(activity::finish);
            instrumentation.waitForIdleSync();
        }
    }

    @Test
    public void forwardKeepsSendingUntilReleased() {
        holdThenRelease(forward, -driveSpeed, driveSpeed);
    }

    @Test
    public void reverseKeepsSendingUntilReleased() {
        holdThenRelease(reverse, driveSpeed, -driveSpeed);
    }

    @Test
    public void leftKeepsSendingUntilReleased() {
        holdThenRelease(left, driveSpeed, driveSpeed);
    }

    @Test
    public void rightKeepsSendingUntilReleased() {
        holdThenRelease(right, -driveSpeed, -driveSpeed);
    }

    @Test
    public void secondFingerStopsDriving() {
        touch(left, MotionEvent.ACTION_DOWN);
        expectMotorCommand(driveSpeed, driveSpeed);
        instrumentation.runOnMainSync(() -> {
            MotionEvent.PointerProperties[] fingers = new MotionEvent.PointerProperties[2];
            MotionEvent.PointerCoords[] positions = new MotionEvent.PointerCoords[2];
            int[] location = new int[2];
            left.getLocationInWindow(location);
            for (int i = 0; i < 2; i++) {
                fingers[i] = new MotionEvent.PointerProperties();
                fingers[i].id = i;
                fingers[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
                positions[i] = new MotionEvent.PointerCoords();
                positions[i].x = location[0] + left.getWidth() / 2f + i * 10;
                positions[i].y = location[1] + left.getHeight() / 2f;
                positions[i].pressure = 1;
                positions[i].size = 1;
            }
            MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    2, fingers, positions, 0, 0, 1, 1, 0, 0, 0, 0);
            try {
                activity.dispatchTouchEvent(event);
            } finally {
                event.recycle();
            }
        });
        expectStopped();
    }

    @Test
    public void lostConnectionStopsRepeatingAndDisablesButtons() {
        touch(right, MotionEvent.ACTION_DOWN);
        expectMotorCommand(-driveSpeed, -driveSpeed);
        // Auf dem Hauptthread wechseln, damit kein Fahrbefehl mehr dazwischenkommt.
        instrumentation.runOnMainSync(() -> {
            connected = false;
            nextPacket();
        });
        expectStopped();
        instrumentation.runOnMainSync(() -> {
            for (View button : new View[]{forward, reverse, left, right}) {
                assertFalse("Nach Verbindungsverlust sind die Fahrbuttons gesperrt", button.isEnabled());
            }
        });
    }

    @Test
    public void cancelledTouchStopsDriving() {
        touch(forward, MotionEvent.ACTION_DOWN);
        expectMotorCommand(-driveSpeed, driveSpeed);
        touch(forward, MotionEvent.ACTION_CANCEL);
        expectStopped();
    }

    @Test
    public void losingFocusStopsDriving() {
        touch(forward, MotionEvent.ACTION_DOWN);
        expectMotorCommand(-driveSpeed, driveSpeed);
        instrumentation.runOnMainSync(() -> activity.onWindowFocusChanged(false));
        expectStopped();
    }

    @Test
    public void pausingActivityStopsDriving() {
        touch(reverse, MotionEvent.ACTION_DOWN);
        expectMotorCommand(driveSpeed, -driveSpeed);
        instrumentation.runOnMainSync(() -> instrumentation.callActivityOnPause(activity));
        expectStopped();
    }

    @Test
    public void programmaticSliderChangesDoNotStartDriving() {
        assertEquals(50, tempoRegler.getProgress());
        assertEquals(50, lenkRegler.getProgress());
        instrumentation.runOnMainSync(() -> {
            tempoRegler.setProgress(100);
            lenkRegler.setProgress(0);
        });
        expectNoMoreCommands();
    }

    @Test
    public void slidersKeepSendingAfterFingerIsReleased() {
        setSliders(80, 75); // Echte Touch-Ereignisse einschließlich Loslassen.
        long start = SystemClock.uptimeMillis();
        do {
            expectMotorCommand(-Math.round(driveSpeed * 0.6f), Math.round(driveSpeed * 0.3f));
        } while (SystemClock.uptimeMillis() - start < HOLD_MS);
        setSlider(tempoRegler, 50);
        expectStopped();
    }

    @Test
    public void steeringUpdatesWhileDriving() {
        setSliders(80, 50);
        expectMotorCommand(-Math.round(driveSpeed * 0.6f), Math.round(driveSpeed * 0.6f));
        setSlider(lenkRegler, 75);
        expectMotorCommand(-Math.round(driveSpeed * 0.6f), Math.round(driveSpeed * 0.3f));
        setSlider(tempoRegler, 50);
        expectStopped();
        setSlider(lenkRegler, 100);
        expectStopped(); // Auch volle Lenkung kann bei Tempo 50 keine Fahrt auslösen.
    }

    @Test
    public void sliderCombinationsProduceExpectedMotorSpeeds() {
        // Tempo-Regler, Lenk-Regler, linke Kette in %, rechte Kette in %.
        int[][] cases = {
                {0, 0, 0, -100}, {0, 25, -50, -100}, {0, 50, -100, -100},
                {0, 75, -100, -50}, {0, 100, -100, 0},
                {25, 0, 0, -50}, {25, 25, -25, -50}, {25, 50, -50, -50},
                {25, 75, -50, -25}, {25, 100, -50, 0},
                {50, 0, 0, 0}, {50, 25, 0, 0}, {50, 50, 0, 0},
                {50, 75, 0, 0}, {50, 100, 0, 0},
                {75, 0, 0, 50}, {75, 25, 25, 50}, {75, 50, 50, 50},
                {75, 75, 50, 25}, {75, 100, 50, 0},
                {100, 0, 0, 100}, {100, 25, 50, 100}, {100, 50, 100, 100},
                {100, 75, 100, 50}, {100, 100, 100, 0}
        };
        for (int[] values : cases) {
            setSliders(values[0], values[1]);
            expectMotorCommand(-Math.round(driveSpeed * values[2] / 100f),
                    Math.round(driveSpeed * values[3] / 100f));
            if (values[0] == 50) {
                expectNoMoreCommands();
            } else {
                setSlider(tempoRegler, 50);
                expectStopped();
            }
        }
    }

    @Test
    public void pausingResetsSlidersAndResumingDoesNotRestart() {
        setSliders(80, 75);
        expectMotorCommand(-Math.round(driveSpeed * 0.6f), Math.round(driveSpeed * 0.3f));
        instrumentation.runOnMainSync(() -> instrumentation.callActivityOnPause(activity));
        expectStopped();
        assertSlidersNeutral();
        instrumentation.runOnMainSync(() -> instrumentation.callActivityOnResume(activity));
        expectNoMoreCommands();
    }

    @Test
    public void focusLossResetsSliderDriving() {
        setSliders(100, 50);
        expectMotorCommand(-driveSpeed, driveSpeed);
        instrumentation.runOnMainSync(() -> activity.onWindowFocusChanged(false));
        expectStopped();
        assertSlidersNeutral();
        instrumentation.runOnMainSync(() -> activity.onWindowFocusChanged(true));
        expectNoMoreCommands();
    }

    @Test
    public void connectionLossStopsAndDisablesSliderDriving() {
        setSliders(0, 50);
        expectMotorCommand(driveSpeed, -driveSpeed);
        instrumentation.runOnMainSync(() -> {
            connected = false;
            nextPacket();
        });
        expectStopped();
        assertSlidersNeutral();
        instrumentation.runOnMainSync(() -> {
            assertFalse(tempoRegler.isEnabled());
            assertFalse(lenkRegler.isEnabled());
        });
    }

    @Test
    public void directionButtonReplacesSliderDriving() {
        setSliders(80, 75);
        expectMotorCommand(-Math.round(driveSpeed * 0.6f), Math.round(driveSpeed * 0.3f));
        touch(left, MotionEvent.ACTION_DOWN);
        expectMotorCommand(driveSpeed, driveSpeed);
        assertSlidersNeutral();
        touch(left, MotionEvent.ACTION_UP);
        expectStopped();
    }

    private void assertSlidersNeutral() {
        instrumentation.runOnMainSync(() -> {
            assertEquals(50, tempoRegler.getProgress());
            assertEquals(50, lenkRegler.getProgress());
            assertFalse(tempoRegler.getKeepScreenOn());
        });
    }

    @Test
    public void openingCameraStopsSlidersAndClosingDoesNotRestart() {
        allowCamera();
        setSliders(80, 75);
        expectMotorCommand(-Math.round(driveSpeed * 0.6f), Math.round(driveSpeed * 0.3f));

        instrumentation.runOnMainSync(() -> activity.findViewById(R.id.open_camera).performClick());
        expectStopped();
        assertSlidersNeutral();
        assertCameraVisible(true);

        instrumentation.runOnMainSync(() -> activity.findViewById(R.id.close_camera).performClick());
        assertCameraVisible(false);
        expectNoMoreCommands();
    }

    @Test
    public void openingCameraStopsHeldButtonAndBackReturnsToControls() {
        allowCamera();
        touch(forward, MotionEvent.ACTION_DOWN);
        expectMotorCommand(-driveSpeed, driveSpeed);

        instrumentation.runOnMainSync(() -> activity.findViewById(R.id.open_camera).performClick());
        expectStopped();
        assertCameraVisible(true);

        instrumentation.runOnMainSync(() -> activity.getOnBackPressedDispatcher().onBackPressed());
        assertCameraVisible(false);
        expectNoMoreCommands();
    }

    private void allowCamera() {
        instrumentation.getUiAutomation().grantRuntimePermission(
                instrumentation.getTargetContext().getPackageName(), Manifest.permission.CAMERA);
        instrumentation.waitForIdleSync();
    }

    private void assertCameraVisible(boolean visible) {
        instrumentation.runOnMainSync(() -> {
            assertEquals(visible ? View.VISIBLE : View.GONE,
                    activity.findViewById(R.id.camera_panel).getVisibility());
            assertEquals(visible ? View.GONE : View.VISIBLE,
                    activity.findViewById(R.id.controls_scroll).getVisibility());
        });
    }

    private void setSliders(int tempo, int lenkung) {
        setSlider(lenkRegler, lenkung);
        setSlider(tempoRegler, tempo);
    }

    private void setSlider(SeekBar slider, int progress) {
        instrumentation.runOnMainSync(() -> slider.requestRectangleOnScreen(
                new Rect(0, 0, slider.getWidth(), slider.getHeight()), true));
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            int[] position = new int[2];
            slider.getLocationInWindow(position);
            float x = position[0] + slider.getPaddingLeft()
                    + (slider.getWidth() - slider.getPaddingLeft() - slider.getPaddingRight())
                    * progress / 100f;
            float y = position[1] + slider.getHeight() / 2f;
            long down = SystemClock.uptimeMillis();
            for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
                MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(),
                        action, x, y, 0);
                try {
                    activity.dispatchTouchEvent(event);
                } finally {
                    event.recycle();
                }
            }
            assertEquals("Regler durch Berührung eingestellt", progress, slider.getProgress());
        });
    }

    private void holdThenRelease(View button, int m1, int m4) {
        touch(button, MotionEvent.ACTION_DOWN);
        long start = SystemClock.uptimeMillis();
        long previousCommand = start;
        long largestGap = 0;
        int commands = 0;

        // Ein einziges ACTION_DOWN. Während des Haltens gibt es keine MOVE-Ereignisse.
        do {
            expectMotorCommand(m1, m4);
            long now = SystemClock.uptimeMillis();
            largestGap = Math.max(largestGap, now - previousCommand);
            previousCommand = now;
            commands++;
        } while (SystemClock.uptimeMillis() - start < HOLD_MS);

        touch(button, MotionEvent.ACTION_UP);
        expectStopped();
        Log.i("MainActivity2TouchTest", "M1=" + m1 + ", M4=" + m4
                + ", Halten=" + HOLD_MS + " ms, Fahrpakete=" + commands
                + ", größte Lücke=" + largestGap + " ms, danach Stopp geprüft");
    }

    private void touch(View button, int action) {
        if (action == MotionEvent.ACTION_DOWN) {
            instrumentation.runOnMainSync(() -> button.requestRectangleOnScreen(
                    new Rect(0, 0, button.getWidth(), button.getHeight()), true));
            instrumentation.waitForIdleSync();
        }
        instrumentation.runOnMainSync(() -> {
            long now = SystemClock.uptimeMillis();
            if (action == MotionEvent.ACTION_DOWN) downTime = now;
            int[] position = new int[2];
            button.getLocationInWindow(position);
            MotionEvent event = MotionEvent.obtain(downTime, now, action,
                    position[0] + button.getWidth() / 2f,
                    position[1] + button.getHeight() / 2f, 0);
            try {
                activity.dispatchTouchEvent(event);
            } finally {
                event.recycle();
            }
        });
    }

    private ByteBuffer nextPacket() {
        ByteBuffer packet = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        orb.fill(packet);
        return packet;
    }

    private void expectMotorCommand(int m1, int m4) {
        long deadline = SystemClock.uptimeMillis() + MAX_COMMAND_GAP_MS;
        ByteBuffer packet = nextPacket();
        while (packet.get(2) == 3 && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(5);
            packet = nextPacket();
        }
        // fill() verbraucht jeden neuen Befehl. Ein alter Motorwert kann den Test nicht bestehen.
        assertEquals("Innerhalb von 500 ms muss ein neues Motorpaket entstehen", 1, packet.get(2));
        assertEquals("M1 im SPEED_MODE", ORB.SPEED_MODE, packet.get(4));
        assertEquals("M4 im SPEED_MODE", ORB.SPEED_MODE, packet.get(25));
        assertEquals("Geschwindigkeit M1", m1, packet.getShort(5));
        assertEquals("Geschwindigkeit M4", m4, packet.getShort(26));
    }

    private void expectStopped() {
        expectMotorCommand(0, 0);
        expectNoMoreCommands();
    }

    private void expectNoMoreCommands() {
        long deadline = SystemClock.uptimeMillis() + MAX_COMMAND_GAP_MS;
        do {
            assertEquals("Nach dem Stopp darf kein weiterer Fahrbefehl entstehen", 3, nextPacket().get(2));
            SystemClock.sleep(10);
        } while (SystemClock.uptimeMillis() < deadline);
        instrumentation.runOnMainSync(() -> {
            for (View button : new View[]{forward, reverse, left, right}) {
                assertFalse("Keine Taste bleibt gedrückt", button.isPressed());
            }
        });
    }
}
