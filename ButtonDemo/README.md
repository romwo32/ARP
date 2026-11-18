# ButtonDemo

Mit dieser Android-App fährt der ORB-Roboter geradeaus vorwärts oder rückwärts.

## Starten

1. Diesen Ordner in Android Studio öffnen und die App auf einem Android-Handy starten (ab Android 6).
2. Den eingeschalteten Roboter in den Bluetooth-Einstellungen des Handys koppeln.
3. In der App **Verbinden** antippen, bei Bedarf den Bluetooth-Zugriff erlauben und den Roboter auswählen.
4. **Vorwärts** oder **Rückwärts** gedrückt halten. Der Finger kann dabei still bleiben. Zum Anhalten loslassen.

## Vom Tastendruck zum Motor

Die Fahrlogik steht in [MainActivity.java](app/src/main/java/com/hbrs/MainActivity.java).

1. Beim Aufsetzen des Fingers merkt sich `onDriveTouch` die gedrückte Taste und startet `repeatDrive` sofort.
2. `repeatDrive` gibt alle 100 ms einen neuen Fahrbefehl an die ORB-Bibliothek. Die Motoren sind spiegelverkehrt montiert. Vorwärts bekommt M1 deshalb +1500 und M4 −1500. Rückwärts tauschen die Vorzeichen.
3. Beim Loslassen entfernt `stopDriving` die geplante Wiederholung und setzt beide Motorwerte auf 0. Das passiert auch bei einem zweiten Finger, einem Touch-Abbruch oder wenn die App den Fokus verliert.

Die Bluetooth-Verbindung läuft im Hintergrund. Der ORB-Sendethread übernimmt die beiden Motorwerte gemeinsam und überträgt sie an den Roboter.

## Ohne Roboter testen

Der Android-Test [DriveTouchTest.java](app/src/androidTest/java/com/hbrs/DriveTouchTest.java) startet die App auf einem Emulator und simuliert eine bestehende Bluetooth-Verbindung. Er betätigt die Fahrbuttons über Androids Touch-Verarbeitung und liest die tatsächlich erzeugten ORB-Motorpakete aus.

1. In Android Studio den Gradle-Sync ausführen.
2. Unter **Tools → Device Manager** einen Emulator starten.
3. Die Datei `app/src/androidTest/java/com/hbrs/DriveTouchTest.java` öffnen.
4. Auf den grünen Pfeil neben der Klasse klicken und **Run 'DriveTouchTest'** wählen. Als Ziel den Emulator auswählen.

Die Tests halten Vorwärts und Rückwärts jeweils zehn Sekunden lang. Ein einziges `ACTION_DOWN` startet die Fahrt, anschließend wartet der Test ohne Fingerbewegung auf neue Motorpakete. Jedes Paket muss die richtigen Vorzeichen haben. Bleibt ein weiterer Befehl länger als 500 ms aus, schlägt der Test fehl. Dieser Spielraum berücksichtigt die zeitliche Verzögerung von Android-Timern unter Last. Die App plant ihre Befehle weiterhin alle 100 ms.

Ein `ACTION_UP` beendet das Halten. Der Test verlangt danach ein Paket mit M1 = 0 und M4 = 0 und prüft eine weitere halbe Sekunde lang, dass die Wiederholung beendet ist. Weitere Tests prüfen Touch-Abbruch, Fokusverlust und Pausieren der Activity. Im Testfenster erscheinen fünf Ergebnisse. Logcat zeigt unter `tag:DriveTouchTest` die Anzahl der Fahrpakete und den größten gemessenen Abstand.

Im Terminal des Projektordners lässt sich derselbe Test mit einem gestarteten Emulator ausführen.

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Der HTML-Bericht entsteht unter `app/build/reports/androidTests/connected/debug/index.html`. Ein erfolgreicher Test bestätigt die Bedien- und Befehlslogik. Bluetooth-Zustellung, tatsächliche Motorbewegung und Anhalteweg werden anschließend am Roboter geprüft.
