# ARP – Fernsteuerung mit Kamera

Diese Version verbindet die einfache Steuerung mit der Kameravorschau aus `feature/camera-preview`.

## Öffnen und starten

1. In Android Studio diesen Ordner mit `settings.gradle` öffnen: `Fernsteuerung-Einfach`.
2. Gradle synchronisieren. Benötigt werden JDK 21 und Android SDK 36.
3. Handy per USB anschließen, USB-Debugging erlauben und **app** starten.
4. Auf dem Handy **Roboter 4 Tasten** öffnen. Die ältere `MainActivity` bleibt zusätzlich vorhanden.

## Fahren

Den Roboter zuerst in den Bluetooth-Einstellungen koppeln, dann in der App **Verbinden** wählen.

- **Vorwärts, Rückwärts, Links, Rechts:** gedrückt halten zum Fahren, loslassen zum Stoppen.
- **Geschwindigkeit:** 0 = rückwärts, 50 = Stopp, 100 = vorwärts.
- **Lenkung:** 0 = links, 50 = geradeaus, 100 = rechts.
- Die Regler wirken sofort und behalten beim Loslassen ihren Wert.
- Geschwindigkeit 50 stoppt beide Motoren bei jeder Lenkstellung.
- Die Richtungstasten übernehmen beim Drücken und setzen die Regler zurück.
- Links und Rechts drehen auf der Stelle. Mit den Reglern wird zum Lenken eine Kette langsamer.
- Fokusverlust, Verlassen der App oder erkannter Verbindungsverlust beenden die Fahrt.

M1 ist die linke Kette, M4 die rechte. `DRIVE_SPEED = 5100` und die bisherigen Motorvorzeichen bleiben erhalten. Nach einem Funkabbruch kann die App keinen Stopp mehr zuverlässig übertragen; das Verhalten des Boards muss am Roboter geprüft werden.

## Kamera

**Kamera öffnen** zeigt das Livebild der Rückkamera. Dafür ist keine Bluetooth-Verbindung nötig. Beim ersten Öffnen fragt Android nach der Kamera-Berechtigung.

Vor dem Wechsel stoppt die App die Fahrt und setzt beide Regler auf 50. **Zurück zur Steuerung** oder die Android-Zurück-Taste beendet die Vorschau. Die Fahrt startet danach nicht von selbst.

Bei verweigerter Berechtigung oder Kamerafehler erscheint eine Meldung mit **Erneut versuchen**. Falls Android nicht erneut fragt, die Kamera-Berechtigung in den App-Einstellungen erlauben. CameraX pausiert das Kamerabild im Hintergrund.

Die Kamera zeigt bisher nur ein Livebild. Ballerkennung und automatisches Fahren sind noch nicht umgesetzt.

## Den Code lesen

| Datei / Methode | Aufgabe |
| --- | --- |
| `MainActivity2.java` | Bedienung, Bluetooth und Fahrbefehle. |
| `onClick` | Verbinden oder Kamera öffnen/schließen. |
| `onTouch` | Gedrückte Fahrbuttons und Loslassen behandeln. |
| `onProgressChanged` | Auf Regleränderungen reagieren. |
| `vorwaerts`, `rueckwaerts`, `linksDrehen`, `rechtsDrehen` | Jeweils eine Fahrtrichtung setzen. |
| `mitReglernFahren` | Werte für beide Ketten berechnen. |
| `motoren` | Die Werte an die ORB-Bibliothek übergeben. |
| `fahrWiederholung` | Etwa alle 100 ms den aktiven Fahrbefehl erneuern. |
| `anhalten` | Wiederholung beenden und Fahrt zurücksetzen. |
| `kameraOeffnen`, `kameraStarten`, `kameraSchliessen` | Bildschirm wechseln und Berechtigung prüfen. |
| `CameraPreviewController.java` | Rückkamera öffnen, Livebild anzeigen und Kamera freigeben. |
| `activity_main2.xml` | Vier Tasten und zwei Regler. |
| `camera_preview.xml` | Kameraansicht. |
| `ORB/` | Verbindung und Motorprotokoll aus dem Kursmaterial. |

Es gibt keine zusätzliche RobotController-Schicht. Die Fahrfunktionen stehen weiterhin direkt in der Activity. Die Kamera hat eine eigene Klasse, weil sie eine andere Aufgabe erfüllt.

## Die Rechnung

`(Reglerwert - 50) / 50f` ergibt einen Faktor von −1 bis +1.

- Rechtskurve: links = Tempo, rechts = Tempo × (1 − Lenkung).
- Linkskurve: links = Tempo × (1 + Lenkung), rechts = Tempo.

Beide Werte werden mit `DRIVE_SPEED` multipliziert. Beispiel: Geschwindigkeit 80 und Lenkung 75 ergeben links 60 Prozent und rechts 30 Prozent. Bei voller Lenkung steht die innere Kette. Im Stand führt der Lenkregler allein zu keiner Bewegung.

## Prüfen

Bei der Zusammenführung am 23. November 2026 waren APK-Build, Kompilieren der Android-Testklassen und Android Lint erfolgreich. Lint meldete 0 Fehler und 21 Warnungen, unter anderem zu älteren Bibliotheksversionen und vorhandenen UI-/ORB-Stellen. Die Gerätetests wurden dabei nicht ausgeführt.

Im Terminal dieses Projektordners:

```powershell
.\gradlew.bat :app:assembleDebug :app:compileDebugAndroidTestJavaWithJavac :app:lintDebug
```

Die APK liegt anschließend in `app/build/outputs/apk/debug/app-debug.apk`.

Die Tests in `MainActivity2TouchTest` prüfen Halten, Loslassen, Reglerwerte und Unterbrechungen mit simulierter Bluetooth-Verbindung. Zwei zusätzliche Tests prüfen, dass der Wechsel zur Kamera sowohl eine Reglerfahrt als auch einen gehaltenen Fahrbutton beendet und die Rückkehr keine Fahrt startet. Die Tests benötigen ein Android-Gerät; das bloße Kompilieren führt sie nicht aus.

`DriveTouchTest` gehört zum alten Stand und verwendet dortige feste Motorwerte.

Für den Test am Handy:

1. Kamera ohne verbundenen Roboter öffnen, Berechtigung erlauben und Livebild prüfen.
2. Kamera schließen und erneut öffnen. Auch die Android-Zurück-Taste prüfen.
3. Kamera-Berechtigung in den App-Einstellungen verweigern; die Fahrsteuerung muss weiterhin nutzbar sein.
4. Jede Fahrtrichtung halten und loslassen; Regler einschließlich Geschwindigkeit 50 prüfen.
5. Während einer Reglerfahrt die Kamera öffnen: Die Motoren müssen stoppen. Nach dem Zurückwechseln müssen beide Regler bei 50 bleiben.
6. App in den Hintergrund schicken und wieder öffnen: keine selbstständige Fahrt.

Die Motorberechnung und Motorzuordnung wurden unverändert aus `Fernsteuerung-Einfach` übernommen. Ein neuer Fahr- oder Kameratest am echten Gerät wurde bei der Zusammenführung nicht ausgeführt.

Grundlage der Kameravorschau: [CameraX-Dokumentation](https://developer.android.com/media/camera/camerax/preview).
