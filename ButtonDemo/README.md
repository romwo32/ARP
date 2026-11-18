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
