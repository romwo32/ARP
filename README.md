# ARP – Android Robotic Project

Ein Android-Handy steuert über Bluetooth einen Kettenroboter mit Open Robotic Board (ORB). Später soll der Roboter einen roten Ball erkennen und verfolgen.

**Die aktuelle gemeinsame App liegt in [Fernsteuerung-Einfach](Fernsteuerung-Einfach).** Sie verbindet die einfache Fahrsteuerung mit der Kameravorschau aus dem Branch `feature/camera-preview`.

## Was schon funktioniert

- Vier Richtungstasten: halten zum Fahren, loslassen zum Stoppen.
- Zwei direkte Regler für Geschwindigkeit und Lenkung.
- Bluetooth-Verbindung zum ORB: M1 links, M4 rechts.
- Livebild der Handykamera in einer eigenen Ansicht.
- Beim Öffnen der Kamera stoppt die Fahrt. Nach dem Zurückwechseln bleibt der Roboter stehen.

Ballerkennung, Machine Learning und autonomes Folgen sind noch geplant. Die tatsächliche Fahrt und Kamera müssen auf dem verwendeten Handy mit dem Roboter geprüft werden.

## In Android Studio öffnen

1. Repository klonen oder herunterladen.
2. Den Ordner **ARP/Fernsteuerung-Einfach** öffnen.
3. Gradle synchronisieren; JDK 21 und Android SDK 36 verwenden.
4. Handy per USB verbinden und **app** starten.
5. Auf dem Handy **Roboter 4 Tasten** öffnen.

Die [App-Anleitung](Fernsteuerung-Einfach/README.md) erklärt Bedienung, Code, Motorberechnung und Gerätetests.

## Wo steht was?

| Pfad | Inhalt |
| --- | --- |
| [Fernsteuerung-Einfach/](Fernsteuerung-Einfach) | Aktuelle gemeinsame App. |
| [MainActivity2.java](Fernsteuerung-Einfach/app/src/main/java/com/hbrs/MainActivity2.java) | Fahrbuttons, Regler, Bluetooth und Wechsel zur Kamera. |
| [CameraPreviewController.java](Fernsteuerung-Einfach/app/src/main/java/com/hbrs/CameraPreviewController.java) | Kameravorschau öffnen und schließen. |
| `Fernsteuerung-Einfach/app/src/main/java/com/hbrs/ORB/` | Kommunikation mit dem Board. |
| `ButtonDemo/`, `Fernsteuerung-2026-11-23/` | Frühere Versionen; bei der Zusammenführung unverändert gelassen. |
| [PROJEKTPLAN.md](PROJEKTPLAN.md) | Planung für Ballerkennung und automatisches Folgen. |
| Die beiden ZIP-Dateien | Frühere Projektstände und Kursmaterial. |

## Weiterarbeiten

Neue Funktionen auf einem Branch entwickeln, prüfen und anschließend in die gemeinsame App übernehmen. Die alten Ordner dienen als Vergleich; neue Änderungen gehören nach `Fernsteuerung-Einfach`.

Die ORB-Klassen stammen aus dem Kursmaterial von Thomas Breuer. Herkunftshinweise stehen in den Dateien. [MIT-Lizenz](LICENSE)
