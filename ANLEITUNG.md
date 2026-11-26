# ManHunt – Ausführliche Anleitung

Diese Anleitung richtet sich an Server-Administratoren und Spieler. Sie beschreibt den normalen Ablauf über die Ingame-Menüs. Die Befehle sind als Alternative und für fortgeschrittene Verwaltung aufgeführt.

- Projekt-README: [README.md](README.md)
- Repository: https://github.com/Jawollo07/mc-manhunt-plugin

## 1. Installation und erster Start

### Voraussetzungen

- Paper-Server mit einer zur Plugin-Version kompatiblen Paper-Version. Das Projekt wird aktuell gegen Paper API 26.3 kompiliert.
- Java-Laufzeit passend zur eingesetzten Paper-Version; zum Bauen des Projekts wird Java 25 verwendet.
- Zugriff auf den Server-Weltordner.
- Schreibrechte für `plugins/ManHunt/` und den Weltordner.

### Installation

1. Server herunterfahren.
2. Die Plugin-JAR in `plugins/` kopieren.
3. Server starten.
4. In der Konsole prüfen, ob ManHunt ohne Fehler geladen wurde.
5. Administratoren die Berechtigung `manhunt.admin` geben oder sie zu OP machen.
6. Im Spiel `/manhunt arenas` eingeben und die erste Arena einrichten.

Beim ersten Start legt das Plugin seine Konfiguration und SQLite-Datenbank an. Die Standardkonfiguration liegt unter `plugins/ManHunt/config.yml`.

> **Hinweis:** Teste neue Builds zuerst auf einem Testserver und sichere vor Updates sowohl die Plugin-Daten als auch die verwendeten Weltvorlagen.

## 2. Berechtigungen

| Berechtigung | Standard | Beschreibung |
|---|---|---|
| `manhunt.admin` | OP | Arena-Verwaltung, Admin-GUI, Runde erstellen/starten/stoppen/zurücksetzen und Countdown steuern |

Spieler benötigen für die normalen Lobby-, Spiel- und Statistikbefehle keine besondere Plugin-Berechtigung.

## 3. Kartenvorlagen vorbereiten

Eine Arena verwendet eine vorhandene Minecraft-Welt als Kartenvorlage.

1. Lege die gewünschte Welt in den Weltordner des Servers.
2. Der Weltordner muss eine gültige Minecraft-Welt mit `level.dat` enthalten. Bei modernen Paper-Dimensionen werden auch unterstützte Unterordner unter `dimensions/<namespace>/<dimension>/` erkannt.
3. Die Welt darf beim Erstellen der Spielkopie nicht geladen sein.
4. Öffne `/manhunt arenas` und wähle **Kartenvorlagen**.

Die Vorlagenliste zeigt erkannte Weltordner, den Prüfstatus, den letzten Änderungszeitpunkt und den Ordnerpfad. Ungültige Ordner können nicht als Arena-Vorlage verwendet werden.

**Wichtig:** Die Vorlage ist die Originalkarte. Für eine Runde wird eine temporäre Arbeitskopie erstellt. Die Vorlage selbst wird nicht verschoben oder verändert. Nach der Runde wird die Arbeitskopie gemäß Konfiguration entfernt.

## 4. Arena mit dem Setup-Assistenten erstellen

Der empfohlene Weg ist die geführte GUI:

1. `/manhunt arenas` öffnen.
2. **Kartenvorlagen** auswählen.
3. Eine gültige Welt anklicken.
4. In der Vorlagen-Detailansicht **Arena erstellen** auswählen.
5. Arena-ID und Anzeigename festlegen, falls abgefragt.
6. Der Assistent öffnet die neu angelegte Arena und führt durch die Einrichtung.

Der Assistent besteht aus sechs Schritten:

### Schritt 1 – Kartenvorlage auswählen

Die ausgewählte Welt wird der Arena zugeordnet. Die Vorlage muss gültig sein. Eine Arena wird direkt aus dieser Vorlage angelegt.

### Schritt 2 – Vorlagenwelt betreten

Der Assistent lädt die Vorlagenwelt zum Bearbeiten und teleportiert dich hinein. Du kannst dort die Karte prüfen und die Spawnpositionen festlegen.

### Schritt 3 – Standard-Spawn festlegen

Stelle dich an die gewünschte allgemeine Startposition und klicke im Setup auf **Standard-Spawn festlegen**. Die Position inklusive Blickrichtung wird gespeichert.

Wenn kein Standard-Spawn gesetzt ist, kann der Weltspawn als Fallback verwendet werden. Für eine sauber konfigurierte Arena wird ein eigener Spawn empfohlen.

### Schritt 4 – Spielmodi freigeben

Wähle aus, welche Spielmodi diese Arena verwenden dürfen. Wenn keine Modusliste eingeschränkt wurde, gelten grundsätzlich alle registrierten Modi als freigegeben.

Aktiviere nur Modi, für die du die benötigten Spawnpunkte einrichten möchtest.

### Schritt 5 – Team-Spawnpunkte einrichten

Der Assistent zeigt die Teams an, die von den freigegebenen Modi benötigt werden. Stelle dich jeweils an die gewünschte Position und klicke auf das passende Team. Jeder Klick fügt einen weiteren Spawnpunkt an deiner aktuellen Position hinzu.

Mehrere Spawnpunkte pro Team sind möglich. Das Spiel kann Spieler bei der Zuweisung zufällig auf diese Punkte verteilen.

Benötigte Teams:

| Modus | Team-Spawnpunkte |
|---|---|
| Assassin Manhunt | `assassins` |
| Hitman | `hitmen`, `survivors` |
| Juggernaut | `juggernaut`, `hunters` |
| Infection | `infected`, `survivors` |

Bei Hitman kann zusätzlich ein **Evakuierungspunkt** gesetzt werden. Er ist optional und wird nur benötigt, wenn die Evakuierung in den Hitman-Einstellungen aktiviert ist.

### Schritt 6 – Abschlussprüfung

Die Prüfung kontrolliert die Arena anhand der freigegebenen Modi. Sie zeigt fehlende Pflicht-Spawnpunkte, eine ungültige Vorlage und weitere Hinweise.

Fehler bei Pflicht-Spawnpunkten müssen vor dem Start behoben werden. Hinweise wie ein fehlender Standard-Spawn können je nach Fallback trotzdem zulässig sein.

### Setup verlassen und fortsetzen

Die Änderungen an Arena, Spawnpunkten und Einstellungen werden direkt gespeichert. Du kannst den Assistenten verlassen und später über `/manhunt arenas` die Arena erneut öffnen und weiter bearbeiten.

Wenn du die Vorlagenwelt verlassen möchtest, nutze **Vorlage verlassen** oder:

```
/manhunt arena leave
```

Dadurch wirst du an deinen vorherigen Standort zurückgebracht. Die Welt wird nur entladen, wenn sie vom Editor geladen wurde und nicht mehr von Spielern verwendet wird.

## 5. Arena-Verwaltung

Die GUI erreichst du mit:

```
/manhunt arenas
/manhunt arena gui
```

In der Verwaltung kannst du:

- Arenen anzeigen und öffnen.
- Kartenvorlagen erneut scannen.
- Arenen erstellen, duplizieren und löschen.
- Anzeigenamen und Kartenvorlage ändern.
- Spawnpunkte ansehen, setzen, teleportieren und entfernen.
- Freigegebene Spielmodi ändern.
- Modusbezogene Einstellungen anpassen.
- Die Arena-Prüfung aufrufen.

Eine Arena, die gerade von einer aktiven Runde verwendet wird, kann nicht gelöscht werden.

### Nützliche Arena-Befehle

Alle folgenden Befehle benötigen `manhunt.admin`.

| Befehl | Funktion |
|---|---|
| `/manhunt arena list` | Alle Arenen auflisten |
| `/manhunt arena templates` | Erkannte Kartenvorlagen auflisten |
| `/manhunt arena info <id>` | Informationen und Spawnpunkte einer Arena anzeigen |
| `/manhunt arena validate <id>` | Kartenvorlage und Arena prüfen |
| `/manhunt arena create <id> <template> [Anzeigename]` | Arena direkt aus einer Vorlage erstellen |
| `/manhunt arena duplicate <quelle> <neue-id> [Anzeigename]` | Arena samt Spawnpunkten und Einstellungen kopieren |
| `/manhunt arena rename <id> <neuer Anzeigename>` | Anzeigenamen ändern |
| `/manhunt arena settemplate <id> <template>` | Vorlage ändern; bisherige Spawnpunkte werden zurückgesetzt |
| `/manhunt arena delete <id>` | Arena löschen |
| `/manhunt arena edit <Vorlagen-ID>` | Vorlagenwelt zum Bearbeiten betreten |
| `/manhunt arena leave` | Vorlagenwelt verlassen |

### Team-Spawnpunkte per Befehl

Du musst dich zum Setzen eines Spawnpunkts in der passenden Vorlagenwelt befinden. Falls nötig:

```
/manhunt arena edit <Vorlagen-ID>
```

Danach kannst du Spawnpunkte setzen:

```
/manhunt arena spawn set <arena> <team>
/manhunt arena spawn list <arena>
/manhunt arena spawn tp <arena> <team> <nummer>
/manhunt arena spawn remove <arena> <team> <nummer>
/manhunt arena spawn clear <arena> <team>
```

Erlaubte Teamnamen:

- `assassins`
- `hitmen`
- `survivors`
- `juggernaut`
- `hunters`
- `infected`

Die Nummer wird beim Setzen automatisch vergeben. Beim Entfernen werden die verbleibenden Spawnpunkte des Teams neu durchnummeriert.

Beispiel:

```
/manhunt arena edit forest
/manhunt arena spawn set forest hitmen
/manhunt arena spawn set forest hitmen
/manhunt arena spawn set forest survivors
/manhunt arena spawn list forest
/manhunt arena leave
```

## 6. Modusbezogene Arena-Einstellungen

Öffne eine Arena in der Arena-Verwaltung und dort die Modus-/Einstellungsansicht. Die Werte werden pro Arena gespeichert.

### Assassin Manhunt

- **Zielverteilung:** kreisförmig oder zufällig.
- **Zeitlimit:** Arena-Wert oder globales Server-Zeitlimit.

Jeder Spieler bekommt ein geheimes Ziel. Wenn ein Spieler eliminiert wird, übernimmt sein erfolgreicher Angreifer dessen Ziel. Spieler, deren Ziel ausscheidet, erhalten ein neues Ziel.

### Hitman

- **Hitman-Anzahl:** automatisch oder feste Anzahl (1, 2 oder 3; die Zahl wird auf die Spielerzahl begrenzt).
- **Evakuierung:** aktivieren oder deaktivieren.
- **Evakuierungsradius:** 2, 4, 6, 8 oder 12 Blöcke.
- **Zeitlimit:** Arena-Wert oder globales Server-Zeitlimit.

Hitmen gewinnen, wenn alle Überlebenden ausgeschieden sind. Überlebende gewinnen, wenn sie den Evakuierungspunkt erreichen oder das Zeitlimit überstehen. Ist die Evakuierung deaktiviert, entfällt dieser Fluchtweg.

### Juggernaut

- **Boss-Leben:** 20, 30, 40 oder 60 HP.
- **Fähigkeiten:** Ground Slam, Charge, Roar und Regeneration einzeln aktivieren/deaktivieren.
- **Zeitlimit:** Arena-Wert oder globales Server-Zeitlimit.

Ein Spieler wird Juggernaut, alle anderen werden Jäger. Der Juggernaut gewinnt, wenn alle Jäger ausgeschieden sind oder er das Zeitlimit überlebt. Die Jäger gewinnen, wenn der Juggernaut ausgeschieden ist.

### Infection

- **Start-Infizierte:** automatisch oder feste Anzahl (1, 2 oder 3; maximal Spielerzahl minus eins).
- **Zeitlimit:** Arena-Wert oder globales Server-Zeitlimit.

Ausgeschiedene Überlebende wechseln in das Infizierten-Team. Die Infizierten gewinnen, wenn keine Überlebenden mehr übrig sind. Die Überlebenden gewinnen, wenn sie bis zum Zeitlimit durchhalten.

### Zeitlimit

Ein Modus kann ein eigenes Zeitlimit haben. Der Wert 0 verwendet das globale Zeitlimit aus der Plugin-Konfiguration. Auch das globale Zeitlimit 0 bedeutet unbegrenzt.

## 7. Runde erstellen und starten

Es kann jeweils nur eine Runde gleichzeitig aktiv sein.

### Empfohlener Weg über die Admin-GUI

1. `/manhunt admin` öffnen.
2. Einen Spielmodus auswählen.
3. Eine Arena auswählen, die für diesen Modus freigegeben und gültig ist.
4. Das Spielerlimit und die verfügbaren Rundenoptionen einstellen.
5. Die Runde erstellen.

Alternativ:

```
/manhunt game create <modus> <arena>
```

Gültige eingebaute Modus-IDs:

- `assassin`
- `hitman`
- `juggernaut`
- `infection`

Beispiel:

```
/manhunt game create infection forest
```

### Spieler beitreten lassen

Spieler öffnen das Lobby-Menü:

```
/manhunt menu
```

Oder sie treten per Befehl bei:

```
/manhunt game join
```

Zum Verlassen der Lobby:

```
/manhunt game leave
```

Die Lobby zeigt Modus, Arena, Online-Spieler, freie Plätze, Teams und Startbereitschaft. Offline-Spieler zählen nicht zur Mindestspielerzahl.

Spieler können ihre Bereitschaft über den Lobby-Menüpunkt **Bereitschaft** oder per Befehl umschalten:

```
/manhunt game ready
```

Die Bereitschaft ist standardmäßig optional. Mit `game.require-ready: true` müssen alle online teilnehmenden Spieler bereit sein, bevor der automatische oder manuelle Countdown starten kann. Wenn während des Countdowns jemand seine Bereitschaft zurücknimmt, wird der Countdown pausiert.

Wenn Arena-Abstimmungen aktiviert sind, können Spieler eine Arena vorschlagen und dafür stimmen:

```
/manhunt game vote <arena>
```

Jeder Spieler hat eine Stimme und kann sie ändern. Bei Gleichstand entscheidet die alphabetische Arena-ID. Die Gewinner-Arena wird beim Start des Countdowns übernommen; sie muss den aktuellen Spielmodus unterstützen. Ein Admin kann ihn auch manuell starten:

```
/manhunt game start
```

Countdown abbrechen:

```
/manhunt game cancel
```

## 8. Während einer Runde

Nützliche Befehle und Menüs:

| Befehl | Beschreibung |
|---|---|
| `/manhunt menu` | Lobby- bzw. Rundenübersicht |
| `/manhunt players` | Spielerübersicht mit Team/Rolle und Verbindungsstatus |
| `/manhunt game status` | Status der aktiven Runde |
| `/manhunt admin` | Admin-Übersicht mit Stoppen und Reset |

Während des Spiels zeigt das HUD die wichtigsten Informationen wie Modus, Zeit, Ziel bzw. Teamaufgabe. Bei Hitman kann die Entfernung zum Evakuierungspunkt angezeigt werden. Assassin-Spieler sehen ihr aktuelles Ziel in der Actionbar.

### Disconnects

Ein Spieler, der die Verbindung verliert, wird nicht sofort als ausgeschieden behandelt. Pro Arena lässt sich unter den allgemeinen Arena-Einstellungen die Disconnect-Kulanz konfigurieren. Der Standardwert beträgt 60 Sekunden. Nach Ablauf der Kulanz wird der Spieler aus der aktiven Runde entfernt. Ein Wert von 0 deaktiviert die Zeitbegrenzung.

## 9. Runde beenden und zurücksetzen

Eine Runde endet automatisch durch die Siegbedingung des Modus oder das Zeitlimit. Die Spieler erhalten eine Ergebnisanzeige. Anschließend wird die temporäre Kartenkopie aufgeräumt und der vorherige Spielerzustand wiederhergestellt.

Administratoren können eine laufende Runde beenden:

```
/manhunt game stop [Grund]
```

Oder die Runde zurücksetzen:

```
/manhunt game reset
```

Die Admin-GUI bietet dieselben Aktionen. Ein Reset sollte nur genutzt werden, wenn die Runde beendet bzw. die Arena freigegeben werden soll.

### Rematch

Nach dem vollständigen Reset kann ein Administrator dieselbe Konfiguration erneut erstellen:

```
/manhunt game rematch
```

Das Rematch übernimmt Modus, Arena und Rundenlimits der letzten Runde. Frühere Teilnehmer, die noch online sind, werden automatisch wieder in die neue Lobby eingeladen. Alternativ ist das Rematch im Lobby-Menü verfügbar, solange keine Runde aktiv ist.

## 10. Statistiken

Spieler können ihre Statistiken ansehen:

```
/manhunt stats
/manhunt stats assassin
/manhunt stats hitman
/manhunt stats juggernaut
/manhunt stats infection
```

Die Bestenliste:

```
/manhunt leaderboard
/manhunt leaderboard infection
```

Es werden bewusst einfache Minigame-Statistiken angezeigt: gespielte Runden und Siege. Die Bestenliste sortiert nach Siegen und anschließend nach gespielten Runden.

## 11. Konfiguration

Die Standarddatei befindet sich unter:

```
plugins/ManHunt/config.yml
```

Wichtige Werte:

| Schlüssel | Standard | Bedeutung |
|---|---:|---|
| `database.file` | `data/manhunt.db` | SQLite-Datei relativ zum Plugin-Datenordner |
| `game.min-players` | `2` | Globale Mindestspielerzahl |
| `game.max-players` | `8` | Globale Standard-Obergrenze |
| `game.countdown-seconds` | `10` | Lobby-Countdown |
| `game.time-limit-seconds` | `2400` | Globales Zeitlimit (40 Minuten) |
| `game.allow-late-join` | `false` | Später Beitritt |
| `game.allow-spectators` | `true` | Zuschauer zulassen |
| `game.result-display-seconds` | `8` | Ergebnisanzeige vor dem Reset (0–60) |
| `game.require-ready` | `false` | Alle Online-Spieler müssen vor dem Countdown bereit sein |
| `game.arena-voting` | `true` | Arena-Abstimmungen in der Lobby erlauben |
| `maps.create-working-copy` | `true` | Temporäre Kartenkopie erstellen |
| `maps.delete-working-copy-after-game` | `true` | Temporäre Kopie nach der Runde entfernen |
| `general.debug` | `false` | Debug-Ausgaben |

Nach Änderungen an der Konfiguration ist ein Serverneustart der sichere Weg, damit alle Komponenten die neuen Werte übernehmen.

## 12. Fehlerbehebung

### Es werden keine Kartenvorlagen angezeigt

- Prüfe, ob die Welt im Server-Weltordner liegt.
- Prüfe, ob der Weltordner eine gültige `level.dat` enthält.
- Öffne die Kartenvorlagenliste erneut und scanne sie neu.
- Achte darauf, dass du keinen Plugin-, Config- oder sonstigen Datenordner als Welt verwendest.

### Die Arena kann nicht gestartet werden

- `/manhunt arena validate <arena>` ausführen.
- In der GUI die Abschlussprüfung öffnen.
- Fehlende Team-Spawnpunkte für alle freigegebenen Modi setzen.
- Prüfen, ob die Arena-Vorlage gültig und nicht mehr zum Bearbeiten geladen ist.
- Prüfen, ob die Arena den gewählten Modus freigibt.

### Die Vorlage kann nicht kopiert werden

Eine geladene Vorlagenwelt kann nicht kopiert werden. Verlasse den Editor mit `/manhunt arena leave` und stelle sicher, dass keine anderen Spieler oder Plugins die Vorlage geladen halten.

### Der Countdown startet nicht

- Prüfen, ob die Mindestzahl an Spielern tatsächlich online ist.
- Prüfen, ob die Runde in `LOBBY` oder `WAITING` ist.
- Prüfen, ob ein anderer Countdown oder eine aktive Runde existiert.
- Für Hitman sind mindestens drei Spieler erforderlich.

### Spieler werden nach einer Runde nicht korrekt wiederhergestellt

- Nicht direkt den Serverprozess beenden, während eine Runde läuft.
- Serverlogs auf Fehler beim Reset bzw. bei der Wiederherstellung prüfen.
- Vor Updates Plugin-Datenbank und Weltvorlagen sichern.
- Bei reproduzierbaren Fehlern Logs, Modus, Arena und die genaue Abfolge der Schritte im GitHub-Repository melden.

## 13. Entwicklung und Build

Das Projekt verwendet Gradle, Java 25, Paper API 26.3, JUnit 5 und SQLite.

```bash
gradle clean test build
```

Die Tests laufen mit JUnit Platform. Der GitHub-Actions-Workflow führt Tests und Build auf Pushes nach `main` und `feature/**` aus.

Fehlerberichte und Verbesserungsvorschläge: https://github.com/Jawollo07/mc-manhunt-plugin/issues
