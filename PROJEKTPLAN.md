# Projektplan – ARP

Stand vom 18. November 2026.

## Ziel und Ausgangspunkt

Ein Roboter mit zwei Kettenantrieben soll einem roten Ball folgen. Ein befestigtes Android-Handy erkennt den Ball über seine Kamera und berechnet die Fahrbefehle. Das ORB-Board empfängt diese über Bluetooth und steuert die Motoren.

Die manuelle Vorwärts-/Rückwärtssteuerung ist vorhanden. Build und Motorbefehle wurden geprüft, der Test am echten Roboter steht aus. Auf dieser Grundlage entstehen Kameravorschau, ML-Erkennung und automatische Fahrregelung.

Der Plan setzt vorläufig sechs Wochen ab Wiederaufnahme der Entwicklung an. Abgabetermin, Teamgröße, Handy und endgültiger Hardwareaufbau sind noch festzulegen. Zeitangaben und Testgrenzen sind Planungsvorschläge.

## Meilensteine und Arbeitspakete

| Zeitraum | Arbeiten | Fertig, wenn … |
| --- | --- | --- |
| Woche 1 – Fahrbasis | Handy befestigen, Stromversorgung prüfen, M1/M4 den Ketten zuordnen, Verbindung und beide Fahrtrichtungen testen. Verhalten bei Verbindungsverlust prüfen. | Der Roboter fährt beim Halten durchgehend und hält nach dem Loslassen an. Motorzuordnung und Stoppverhalten sind dokumentiert. |
| Woche 2 – Kamera | Kameraberechtigung, Vorschau und CameraX-Bildanalyse ergänzen. Drehung und Bildkoordinaten prüfen. Bildauswertung im Hintergrund ausführen. | Das Handy liefert aktuelle, korrekt ausgerichtete Bilder. Pausieren und Wiederöffnen der App funktionieren. |
| Woche 3 – ML-Modell | Bilder aufnehmen, Ballpositionen markieren, Daten aufteilen und ein kleines geeignetes Modell durch Transfer Learning anpassen. | Ein exportierbares Modell erkennt den Ball auf zurückgehaltenen Aufnahmen. Modellformat und Android-Laufzeit passen zusammen. |
| Woche 4 – Erkennung in der App | Modell einbinden. Rechteck, Mittelpunkt und Konfidenz im Kamerabild anzeigen. Laufzeit und Fehlerfälle messen. | Erkennung und Anzeige funktionieren auf dem vorgesehenen Handy. Auch Aufnahmen ohne Ball sind geprüft. |
| Woche 5 – Automatisches Folgen | Start/Stopp für die Automatik ergänzen, Motoren einzeln ansteuern, Lenken und Annäherung abstimmen. Bei Zielverlust oder veralteten Bildern stoppen. | Der Roboter folgt dem Ball in der vereinbarten Testumgebung. Manuelle Eingabe beendet die Automatik. |
| Woche 6 – Abschluss | Wiederholbare Tests durchführen, Fehler beheben, Ergebnisse und Grenzen dokumentieren. APK und Vorführung vorbereiten. | Ein anderes Teammitglied kann die App einrichten, die Tests nachvollziehen und den Roboter vorführen. |

Kamera, Modellentwicklung und Hardwaretests lassen sich nach dem ersten Meilenstein im Team aufteilen. Die Verfolgung wird integriert, sobald die Erkennung auf dem Handy ausreichend schnell und zuverlässig arbeitet.

## Einfache technische Umsetzung

Die bestehende Java-App wird schrittweise erweitert. Die Activity übernimmt Bedienung und Lebenszyklus. Für die Bildauswertung und die Berechnung der Motorwerte werden bei Bedarf jeweils eine verständliche Klasse ergänzt. Die vorhandene ORB-Bibliothek überträgt die Befehle.

Die Kamera liefert das aktuellste Bild an die Analyse. Jedes Bild wird nach der Verarbeitung freigegeben. Erkennungsergebnisse enthalten Mittelpunkt, Größe, Konfidenz und den Zeitstempel der Aufnahme. Vor jedem Fahrbefehl prüft die Regelung, ob das Ergebnis noch aktuell und gültig ist. [CameraX Image Analysis](https://developer.android.com/media/camera/camerax/analyze)

Für den ML-Schritt wird ein kleines Objekterkennungsmodell mit der Klasse `roter_ball` angepasst. Vor dem längeren Training wird ein Beispielmodell auf dem Zielhandy geladen, damit Exportformat und Android-Laufzeit bereits geprüft sind. Als Ausgangspunkt kommt eine Java-kompatible LiteRT-Objekterkennung infrage. [Google AI Edge – Objekterkennung](https://developers.google.com/edge/litert/libraries/task_library/object_detector)

## Daten und Modell

1. Den verwendeten Ball in der vorgesehenen Umgebung aufnehmen. Entfernungen, Bildpositionen, Beleuchtung und Hintergründe variieren.
2. Den Ball in jedem Bild mit einem Rechteck und der Klasse `roter_ball` markieren. Bilder ohne Ball sowie andere rote Gegenstände aufnehmen.
3. Ganze Aufnahmeserien auf Training, Validierung und Test verteilen. Als Startaufteilung sind etwa 70 %, 15 % und 15 % vorgesehen. Benachbarte Bilder derselben Aufnahme bleiben in derselben Gruppe.
4. Ein kleines vortrainiertes Modell anpassen. Modellgröße, Eingabeauflösung und Konfidenzschwelle anhand der Validierungsdaten wählen.
5. Das fertige Modell einmal auf den zurückgehaltenen Testaufnahmen bewerten. Treffer, übersehene Bälle und Fehlalarme erfassen.
6. Modellversion, verwendete Daten und Verarbeitungszeit auf dem Handy dokumentieren. Bei Änderungen die betroffenen Tests wiederholen.

Der erste Datensatz soll überschaubar bleiben und alle vorgesehenen Testsituationen abdecken. Zusätzliche Bilder werden gezielt für beobachtete Fehler gesammelt. Über die Freigabe für den Fahrbetrieb entscheidet die gemessene Erkennungsqualität in der Testumgebung.

## Fahrregelung und Übergänge

Für die erste Version reicht eine einfache Regelung anhand der Ballposition und Bildgröße.

- Liegt der Ball deutlich neben der Bildmitte, dreht der Roboter zunächst in seine Richtung.
- Liegt er nahe der Bildmitte und erscheint kleiner als beim gewünschten Abstand, fährt der Roboter langsam vorwärts. Kleine seitliche Abweichungen korrigiert er über die beiden Motorgeschwindigkeiten.
- Erreicht oder überschreitet die Ballgröße den kalibrierten Zielbereich, hält der Roboter an.
- Fehlt eine ausreichend sichere Erkennung oder ist die Aufnahme zu alt, werden beide Motorwerte null.

Ein kleiner Toleranzbereich um die Bildmitte und den Zielabstand soll häufiges Umschalten vermeiden. Geschwindigkeiten, Toleranzen und maximale Bildalterung werden am Roboter eingestellt und zusammen mit den Testergebnissen festgehalten. Für mehrere erkannte Bälle wird vor der Umsetzung eine eindeutige Auswahlregel festgelegt, beispielsweise die höchste Konfidenz.

Manuelle Bedienung, Automatik und Stopp verwenden dieselbe Stelle zum Setzen der Motorwerte. Eine manuelle Fahrbetätigung beendet die Automatik. Beim Loslassen bleibt der Roboter stehen, bis eine neue Eingabe erfolgt. Das Beenden der Automatik, Fokusverlust und Kamerafehler setzen beide Motorwerte auf null und verhindern, dass ein verspätetes Analyseergebnis die Fahrt erneut startet.

Bei unterbrochenem Bluetooth ist ein Stopp vom Handy aus möglicherweise nicht mehr übertragbar. In Woche 1 wird deshalb geprüft, ob die Board-Firmware bei ausbleibenden Fahrbefehlen selbst anhält. Fehlt diese Funktion, wird ein Timeout auf dem Board als zusätzliche Arbeit eingeplant und getestet.

## Testplan

Die folgenden Fälle sind geplante Hardware- und Integrationstests. Nach jedem Durchlauf werden Aufbau, Softwarestand, Beobachtung und Ergebnis als bestanden oder fehlgeschlagen dokumentiert. Die genauen Entfernungen und Lichtbedingungen werden vor den Tests festgelegt.

| Test | Durchführung | Erwartetes Ergebnis |
| --- | --- | --- |
| Dauerhaft halten | Vorwärts und rückwärts jeweils 10 Sekunden mit stillgehaltenem Finger drücken | Durchgehende Bewegung in der jeweiligen Richtung |
| Loslassen | Während der Fahrt den Finger abheben | Beide Motorwerte werden null. Reaktionszeit und tatsächlicher Anhalteweg werden erfasst. |
| Eingabe unterbrechen | Zweiten Finger aufsetzen, App wechseln oder eine Systemansicht öffnen | Wiederholung endet und Stopp wird ausgelöst |
| Verbindung verlieren | Bluetooth während langsamer Fahrt unterbrechen, zunächst mit frei laufenden Ketten | Board hält gemäß seinem überprüften Timeout an. Zeitpunkt messen. |
| Ballposition | Ball links, mittig und rechts sowie in unterschiedlichen Entfernungen zeigen | Erkennung markiert den Ball an der richtigen Bildposition |
| Störende Gegenstände | Andere rote Objekte und Szenen ohne Ball zeigen | Fehlalarme werden erfasst. Unsichere Erkennung löst einen Stopp aus. |
| Folgen | Ball langsam vor dem Roboter bewegen | Roboter richtet sich aus, folgt und hält im vereinbarten Zielbereich |
| Zielverlust | Ball verdecken oder aus dem Bild bewegen | Roboter hält innerhalb der festgelegten Reaktionsgrenze an |
| Bildausfall | Kamera beenden oder Analyse gezielt verzögern | Veraltete Ergebnisse erzeugen keine weiteren Fahrbefehle |
| Modus wechseln | Während der Automatik einen Fahrbutton drücken und loslassen | Manuelle Steuerung übernimmt. Loslassen stoppt. Automatik startet erst nach erneuter Auswahl. |

Für den gesamten Ablauf werden mehrere Wiederholungen bei unterschiedlichen Lichtbedingungen durchgeführt. Die Abschlussdokumentation enthält auch Fehlversuche und die daraus vorgenommenen Änderungen.

## Aufgaben im Team

Die Namen werden zu Projektbeginn zugeordnet. Bei einem kleinen Team kann eine Person mehrere Bereiche übernehmen.

| Bereich | Verantwortung |
| --- | --- |
| Hardware und Fahrsteuerung | Aufbau, Motorzuordnung, Fahrparameter und Stoppversuche |
| Android und Kamera | Bedienung, Berechtigungen, Kamera und Modelleinsatz auf dem Handy |
| Daten und ML | Aufnahmen, Markierungen, Training und Bewertung der Erkennung |
| Gemeinsam | Integration, Tests, Dokumentation und Vorführung |

Jedes Arbeitspaket erhält ein GitHub-Issue mit Aufgabe und Abnahmekriterium. Zusammengehörige Änderungen werden in kleinen Commits festgehalten. Nach jedem Meilenstein wird der Stand gemeinsam auf dem Zielgerät geprüft.

## Offene Entscheidungen und Abgabe

Vor dem Start werden Abgabetermin, Teamaufteilung, Handymodell, Ballgröße und gewünschter Folgeabstand festgehalten. Die genaue Board-Version, Motorzuordnung und das Timeout-Verhalten werden am Aufbau geprüft. Die Auswahl des ML-Modells folgt dem ersten Kompatibilitäts- und Geschwindigkeitstest auf dem Handy.

Zur Abgabe gehören der nachvollziehbare Quellcode, eine installierbare APK, das verwendete Modell mit Angaben zu Training und Testdaten, ein Aufbauplan mit Motoranschlüssen sowie Testprotokolle und eine kurze Vorführung. Die Dokumentation erklärt die Anforderungen, den Entwicklungsablauf, die Architektur, die gemessenen Ergebnisse und die Grenzen der Ballverfolgung.
