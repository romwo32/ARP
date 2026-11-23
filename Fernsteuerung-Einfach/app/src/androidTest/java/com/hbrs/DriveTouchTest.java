package com.hbrs;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

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
import static org.junit.Assert.assertTrue;

public class DriveTouchTest {
    private static final long HOLD_MS = 10_000;
    // Android-Timer können unter Last verzögert werden. Größere Lücken schlagen fehl.
    private static final long MAX_COMMAND_GAP_MS = 500;

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;
    private ORB orb;
    private View forward;
    private View reverse;
    private long downTime;

    @Before
    public void prepareConnectedRobot() throws Exception {
        activity = (MainActivity) instrumentation.startActivitySync(
                new Intent(instrumentation.getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        forward = activity.findViewById(R.id.forward);
        reverse = activity.findViewById(R.id.reverse);
        assertFalse("Vor dem Verbinden sind die Fahrbuttons gesperrt", forward.isEnabled());

        // Der Test liest die ORB-Pakete selbst, deshalb zuerst den Sendethread beenden.
        Field orbField = MainActivity.class.getDeclaredField("orb");
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
                return true;
            }
        };
        orb.settingsToORB.isNew = false;
        orb.configToORB.isNew = false;
        nextPacket();
        instrumentation.runOnMainSync(() -> {
            forward.setEnabled(true);
            reverse.setEnabled(true);
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
        holdThenRelease(forward, 1500);
    }

    @Test
    public void reverseKeepsSendingUntilReleased() {
        holdThenRelease(reverse, -1500);
    }

    @Test
    public void cancelledTouchStopsDriving() {
        touch(forward, MotionEvent.ACTION_DOWN);
        expectMotorCommand(1500);
        touch(forward, MotionEvent.ACTION_CANCEL);
        expectStopped();
    }

    @Test
    public void losingFocusStopsDriving() {
        touch(forward, MotionEvent.ACTION_DOWN);
        expectMotorCommand(1500);
        instrumentation.runOnMainSync(() -> activity.onWindowFocusChanged(false));
        expectStopped();
    }

    @Test
    public void pausingActivityStopsDriving() {
        touch(reverse, MotionEvent.ACTION_DOWN);
        expectMotorCommand(-1500);
        instrumentation.runOnMainSync(() -> instrumentation.callActivityOnPause(activity));
        expectStopped();
    }

    private void holdThenRelease(View button, int speed) {
        touch(button, MotionEvent.ACTION_DOWN);
        long start = SystemClock.uptimeMillis();
        long previousCommand = start;
        long largestGap = 0;
        int commands = 0;

        // Ein einziges ACTION_DOWN. Während des Haltens gibt es keine MOVE-Ereignisse.
        do {
            expectMotorCommand(speed);
            long now = SystemClock.uptimeMillis();
            largestGap = Math.max(largestGap, now - previousCommand);
            previousCommand = now;
            commands++;
        } while (SystemClock.uptimeMillis() - start < HOLD_MS);

        touch(button, MotionEvent.ACTION_UP);
        expectStopped();
        Log.i("DriveTouchTest", "M1=" + speed + ", M4=" + -speed
                + ", Halten=" + HOLD_MS + " ms, Fahrpakete=" + commands
                + ", größte Lücke=" + largestGap + " ms, danach Stopp geprüft");
    }

    private void touch(View button, int action) {
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

    private void expectMotorCommand(int speed) {
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
        assertEquals("Geschwindigkeit M1", speed, packet.getShort(5));
        assertEquals("Geschwindigkeit M4", -speed, packet.getShort(26));
    }

    private void expectStopped() {
        expectMotorCommand(0);
        long deadline = SystemClock.uptimeMillis() + MAX_COMMAND_GAP_MS;
        do {
            assertEquals("Nach dem Stopp darf kein weiterer Fahrbefehl entstehen", 3, nextPacket().get(2));
            SystemClock.sleep(10);
        } while (SystemClock.uptimeMillis() < deadline);
        assertTrue("Beide Tasten sind losgelassen", !forward.isPressed() && !reverse.isPressed());
    }
}
