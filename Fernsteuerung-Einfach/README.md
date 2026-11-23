# ARP – einfache Fernsteuerung

Die aktuelle Steuerung steht in [MainActivity2.java](app/src/main/java/com/hbrs/MainActivity2.java). Sie hat vier Richtungstasten und zwei Regler. Die erste `MainActivity` bleibt als zusätzliche ältere Ansicht im Projekt.

## Starten

1. Diesen Projektordner mit `settings.gradle` in Android Studio öffnen.
2. Gradle synchronisieren, das Handy auswählen und **app** starten.
3. Auf dem Handy **Roboter 4 Tasten** öffnen.
4. Den Roboter vorher in den Bluetooth-Einstellungen koppeln und in der App **Verbinden** wählen.

## Bedienen

- **Vorwärts, Rückwärts, Links, Rechts:** gedrückt halten zum Fahren, loslassen zum Stoppen.
- **Geschwindigkeit:** 0 = rückwärts, 50 = Stopp, 100 = vorwärts.
- **Lenkung:** 0 = links, 50 = geradeaus, 100 = rechts.
- Die Regler steuern sofort. Nach dem Loslassen bleibt ihr Wert eingestellt.
- Geschwindigkeit 50 stoppt beide Motoren bei jeder Lenkstellung.
- Beim Verlassen der App oder bei erkanntem Verbindungsverlust hält die Steuerung an und setzt die Regler zurück.

Die Buttons übernehmen beim Drücken die Steuerung. Links und Rechts drehen auf der Stelle. Bei der Reglersteuerung wird zum Lenken eine Kette langsamer.

## Den Code lesen

| Name | Aufgabe |
| --- | --- |
| `onCreate` | Bildschirm laden und Buttons und Regler zuordnen. |
| `onClick` | Geräteauswahl öffnen. |
| `onTouch` | Drücken und Loslassen der Fahrbuttons behandeln. |
| `onProgressChanged` | Auf eine Regleränderung reagieren. |
| `vorwaerts`, `rueckwaerts`, `linksDrehen`, `rechtsDrehen` | Jeweils eine Richtung einstellen. |
| `mitReglernFahren` | Geschwindigkeit für beide Ketten berechnen. |
| `motoren` | Die beiden Werte an die ORB-Bibliothek übergeben. |
| `fahrWiederholung` | Den aktiven Fahrbefehl etwa alle 100 Millisekunden erneuern. |
| `anhalten` | Wiederholung beenden und die Motorwerte auf null setzen. |
| `verbinden` / `verbindungAbschliessen` | Bluetooth verbinden, danach Motoren und Anzeige vorbereiten. |

Die Android-Bedienereignisse stehen als normale Methoden in der Activity. Die Bluetooth-Verbindung arbeitet weiterhin im Hintergrund, damit der Bildschirm bedienbar bleibt. Die ORB-Bibliothek übernimmt das Verschicken der Befehle.

## Die Rechnung

`(Reglerwert - 50) / 50f` ergibt einen Faktor von −1 bis +1.

Rechtskurve: linke Kette = Tempo, rechte Kette = Tempo × (1 − Lenkung).

Linkskurve: linke Kette = Tempo × (1 + Lenkung), rechte Kette = Tempo.

Anschließend werden beide Werte mit `DRIVE_SPEED` multipliziert. Die Prozentangabe bezieht sich auf diese Höchstgeschwindigkeit. M1 ist links, M4 rechts. `motoren` berücksichtigt die spiegelverkehrte Montage.

Beispiel: Geschwindigkeit 80 und Lenkung 75 ergeben links 60 Prozent und rechts 30 Prozent. Bei voller Lenkung steht die innere Kette; sie fährt nicht in die Gegenrichtung. Rückwärts verhält sich die Lenkung wie beim Auto.

## Prüfung dieses Stands

Die App und die vorhandenen Android-Testklassen lassen sich erfolgreich kompilieren. Die sechs Methoden für Richtungen, Reglerberechnung und Motorwerte wurden beim Vereinfachen inhaltlich unverändert übernommen.

Es wurde kein Emulator gestartet und kein neuer Gerätetest durchgeführt. Die tatsächliche Fahrt lässt sich direkt am Roboter prüfen.

Die Tests unter `app/src/androidTest` sind weiterhin vorhanden. `DriveTouchTest` enthält feste Werte des älteren Stands; für die aktuelle Activity ist `MainActivity2TouchTest` vorgesehen.
