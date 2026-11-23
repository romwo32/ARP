# ARP – Fernsteuerung mit vier Tasten und zwei Reglern

**Aktueller lokaler Projektstand, veröffentlicht am 23. November 2026.**

Dieser Ordner enthält das vollständige Android-Studio-Projekt der erweiterten Fernsteuerung. Die vorher veröffentlichte Version bleibt im Ordner `ButtonDemo` unverändert erhalten.

**Für die aktuelle Reglersteuerung ist `app/src/main/java/com/hbrs/MainActivity2.java` die richtige Datei.**

## In Android Studio öffnen

1. Das Repository klonen oder herunterladen.
2. Über **File → Open** diesen Ordner **Fernsteuerung-2026-11-23** öffnen. Hier liegt `settings.gradle`.
3. Gradle synchronisieren lassen. Der Gradle Wrapper ist vollständig enthalten.
4. Das Android-Handy auswählen und die Konfiguration **app** starten.
5. Auf dem Handy den App-Eintrag **Roboter 4 Tasten** öffnen. `ButtonDemo` öffnet die zusätzliche ältere Activity im selben Projekt.

Die App benötigt Android 6 oder neuer. Der Roboter muss zuvor in den Bluetooth-Einstellungen des Handys gekoppelt werden. Anschließend in der App **Verbinden** wählen.

## Bedienung

- **Vorwärts/Rückwärts/Links/Rechts:** Taste gedrückt halten; Loslassen stoppt. Links und Rechts drehen auf der Stelle.
- **Geschwindigkeitsregler:** 0 = voll rückwärts, 50 = Stopp, 100 = voll vorwärts.
- **Lenkregler:** 0 = links, 50 = geradeaus, 100 = rechts.
- Die Regler steuern direkt. Nach dem Loslassen bleibt ihr Wert aktiv. Eine zusätzliche Fahrtaste ist nicht nötig.
- Geschwindigkeit 50 stoppt beide Motoren bei jeder Lenkstellung.
- Beim Verlassen der App, Fokusverlust oder erkanntem Verbindungsabbruch werden die Fahrt beendet und die Regler zurückgesetzt.
- Die Richtungstasten übernehmen beim Drücken die Steuerung und setzen die Regler auf 50 zurück.

## Die Berechnung

`getProgress()` liefert einen Wert zwischen 0 und 100. `mitReglernFahren()` rechnet ihn in einen Faktor zwischen −1 und +1 um:

```text
tempo   = (Geschwindigkeit - 50) / 50
lenkung = (Lenkung - 50) / 50

Rechtskurve: links = tempo; rechts = tempo × (1 - lenkung)
Linkskurve:  links = tempo × (1 + lenkung); rechts = tempo
```

Danach werden beide Werte mit `DRIVE_SPEED` multipliziert und gerundet. Dieser lokale Stand verwendet `DRIVE_SPEED = 5100`. Die Prozentwerte beziehen sich auf die Höchstgeschwindigkeit, nicht auf die elektrische Motorleistung.

Beispiel: Geschwindigkeit 80 und Lenkung 75 ergeben links 60 Prozent und rechts 30 Prozent. Die innere Kette wird beim Lenken langsamer, höchstens bis zum Stillstand. Beim Rückwärtsfahren wirkt die Lenkung wie beim Auto.

M1 ist die linke Kette, M4 die rechte. `motoren()` berücksichtigt die spiegelverkehrte Montage. Der gemeinsame Timer erneuert die aktiven Fahrbefehle etwa alle 100 Millisekunden.

## Wichtige Dateien

- [MainActivity2.java](app/src/main/java/com/hbrs/MainActivity2.java): aktuelle Steuerung mit Buttons und Reglern.
- [activity_main2.xml](app/src/main/res/layout/activity_main2.xml): Oberfläche der aktuellen Steuerung.
- [MainActivity.java](app/src/main/java/com/hbrs/MainActivity.java): zusätzlich mitkopierte erste Activity im lokalen Stand.
- [ORB](app/src/main/java/com/hbrs/ORB): Kommunikation mit dem Roboter.
- [AndroidManifest.xml](app/src/main/AndroidManifest.xml): Berechtigungen und beide App-Einstiege.

## Prüfstand

Der letzte Build der direkten Reglersteuerung war erfolgreich. Die aktuelle Version enthält [MainActivity2TouchTest.java](app/src/androidTest/java/com/hbrs/MainActivity2TouchTest.java) mit Prüfungen für Touch-Bedienung, Motorwerte und Unterbrechungen. Der vollständige Emulator-Testlauf dieser direkten Regler-Version wurde nicht abgeschlossen. Ein erfolgreicher Gesamtlauf wird hier daher nicht behauptet.

`DriveTouchTest.java` stammt aus dem früheren Stand und verwendet dessen feste Motorwerte; er ist kein unveränderter Nachweis für die inzwischen lokal angepasste erste Activity.

Die vorhandenen Quelldateien wurden für diese Veröffentlichung übernommen. Lokale SDK-Pfade, IDE-Zustand und erzeugte Build-Dateien sind nicht enthalten.
