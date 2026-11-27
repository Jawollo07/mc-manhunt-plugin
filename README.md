# ManHunt

Ein modulares Minecraft-Manhunt-Minispiel-Plugin für Paper. Es bietet vier Spielmodi, eine Ingame-Lobby, eine Admin-Oberfläche und einen geführten Assistenten zum Erstellen und Konfigurieren von Arenen.

> **Projektstatus:** frühe Version (0.1.0). Das Plugin wird aktiv entwickelt. Vor dem Einsatz auf einem öffentlichen Server sollte es auf einem Testserver mit der konkret verwendeten Paper-Version geprüft werden.

## Funktionen

- Vier eingebaute Spielmodi: **Assassin Manhunt**, **Hitman**, **Juggernaut** und **Infection**.
- Ingame-Lobby mit Spielerliste, Teamübersicht, Bereitschaftssystem, Arena-Abstimmung, Beitreten und Verlassen.
- Spielereinladungen per Lobby-Befehl.
- Schnelles Rematch mit derselben Arena, demselben Modus und den zuvor teilnehmenden Online-Spielern.
- Zuschauer können über die Spielerübersicht aktive Spieler verfolgen.
- Admin-GUI zur Modus- und Arena-Auswahl, Spielerverwaltung und Steuerung der Runde.
- Geführtes Arena-Setup mit Kartenvorlagen, Spawnpunkten, Modusfreigaben und Abschlussprüfung.
- Mehrere Spawnpunkte pro Team; die Zuordnung kann zufällig verteilt werden.
- Modusbezogene Arena-Einstellungen, zum Beispiel Zeitlimit, Teamgrößen und Fähigkeiten.
- Eigene Startinventare pro Team und Arena – inklusive Items, Rüstung und Schildhand, direkt über die Ingame-GUI.
- Temporäre Arbeitskopien der Karten; die Kartenvorlage bleibt unverändert.
- Wiederherstellung der Spielerzustände nach einer Runde.
- SQLite-Datenspeicherung für Arenen, Einstellungen und Statistiken.
- Einfache Statistiken und Bestenliste.
- Automatisierte Tests und Build über GitHub Actions.

## Spielmodi

| Modus | Kurzbeschreibung |
|---|---|
| Assassin Manhunt | Jeder Spieler erhält ein geheimes Ziel. Wer sein Ziel eliminiert, übernimmt dessen Ziel. |
| Hitman | Ein oder mehrere Hitmen jagen die Überlebenden. Diese gewinnen durch Evakuierung oder wenn sie das Zeitlimit überstehen. |
| Juggernaut | Ein stark ausgerüsteter Juggernaut kämpft gegen alle übrigen Spieler als Jäger. |
| Infection | Infizierte jagen Überlebende. Ausgeschiedene Überlebende wechseln ins Infizierten-Team. |

Die genaue Konfiguration der Modi erfolgt pro Arena. Nicht jeder Modus benötigt dieselben Spawnpunkte.

## Voraussetzungen

- Ein Paper-Server mit einer zur verwendeten Paper-API kompatiblen Version (das Projekt wird gegen Paper API **26.3** gebaut).
- **Java 25** auf dem Build-System; für den Serverbetrieb ist ebenfalls eine zur eingesetzten Paper-Version passende Java-Laufzeit erforderlich.
- Gradle **9.8.1** für den im CI verwendeten Build.
- Schreibrechte für das Plugin-Datenverzeichnis und den Server-Weltordner.

## Installation

1. Lade das fertige Plugin-JAR aus einem erfolgreichen GitHub-Actions-Build herunter oder baue es selbst.
2. Lege die JAR-Datei in den Ordner `plugins/` deines Paper-Servers.
3. Starte den Server einmal, damit die Plugin-Konfiguration und Datenbank angelegt werden.
4. Vergib die Berechtigung `manhunt.admin` an die zuständigen Administratoren (standardmäßig für OP freigegeben).
5. Erstelle anschließend mindestens eine Arena. Die Schritte stehen in der [ausführlichen Anleitung](ANLEITUNG.md).

## Build aus dem Quellcode

Voraussetzungen: Git, Java 25 und Gradle 9.8.1.

```bash
git clone https://github.com/Jawollo07/mc-manhunt-plugin.git
cd mc-manhunt-plugin
gradle clean test build
```

Das Plugin-Artefakt liegt anschließend unter `build/libs/`. Verwende die erzeugte Plugin-/Shadow-JAR und kopiere sie in den Serverordner `plugins/`.

Die CI führt zuerst `gradle test` und danach `gradle build` aus. Der Status der Builds ist unter [GitHub Actions](https://github.com/Jawollo07/mc-manhunt-plugin/actions) einsehbar.

## Wichtige Befehle

Alle Befehle können auch mit dem Alias `/mh` verwendet werden.

| Befehl | Zweck |
|---|---|
| `/manhunt` | Hilfe anzeigen |
| `/manhunt modes` | Verfügbare Spielmodi anzeigen |
| `/manhunt menu` | Lobby-Menü öffnen |
| `/manhunt admin` | Admin-Menü öffnen |
| `/manhunt arenas` | Arena-Verwaltung öffnen |
| `/manhunt players` | Spielerübersicht der laufenden Runde |
| `/manhunt game create <modus> <arena>` | Runde erstellen (Admin) |
| `/manhunt game join` | Lobby beitreten |
| `/manhunt game leave` | Lobby verlassen |
| `/manhunt game ready` | Bereitschaft umschalten |
| `/manhunt game invite <spieler>` | Spieler in die Lobby einladen |
| `/manhunt game accept` | Lobby-Einladung annehmen |
| `/manhunt game vote <arena>` | Für eine Arena abstimmen |
| `/manhunt game rematch` | Letzte Runde erneut erstellen (Admin) |
| `/manhunt game start` | Countdown manuell starten (Admin) |
| `/manhunt game cancel` | Countdown abbrechen (Admin) |
| `/manhunt game status` | Rundenstatus anzeigen |
| `/manhunt game stop [grund]` | Runde stoppen (Admin) |
| `/manhunt game reset` | Runde zurücksetzen (Admin) |
| `/manhunt stats [modus]` | Eigene Statistiken anzeigen |
| `/manhunt leaderboard [modus]` | Bestenliste anzeigen |

Die Arena-Unterbefehle sind in der [Anleitung zur Arena-Verwaltung](ANLEITUNG.md#arenen-und-kartenvorlagen) beschrieben.

## Standardkonfiguration

Die Datei `plugins/ManHunt/config.yml` wird beim ersten Start angelegt.

```yaml
database:
  file: data/manhunt.db

game:
  min-players: 2
  max-players: 8
  countdown-seconds: 10
  time-limit-seconds: 2400
  allow-late-join: false
  allow-spectators: true
  result-display-seconds: 8
  require-ready: false
  arena-voting: true

maps:
  create-working-copy: true
  delete-working-copy-after-game: true

general:
  debug: false
```

- `min-players`: globale Mindestspielerzahl; ein Modus kann eine höhere Mindestzahl verlangen (Hitman: 3).
- `max-players`: globale Standard-Obergrenze; die Admin-Oberfläche kann das Limit einer Runde anpassen.
- `countdown-seconds`: Lobby-Countdown in Sekunden.
- `time-limit-seconds`: globales Zeitlimit; ein Arena-Moduswert kann es überschreiben.
- `result-display-seconds`: Dauer der Ergebnisanzeige vor dem Aufräumen (0 bis 60 Sekunden).
- `require-ready`: Wenn `true`, müssen alle aktuell online teilnehmenden Spieler vor dem Countdown bereit sein.
- `arena-voting`: Aktiviert die Arena-Abstimmung in der Lobby.
- `maps.create-working-copy` und `maps.delete-working-copy-after-game`: steuern das Erstellen und Entfernen temporärer Kartenkopien.

## Dokumentation

- [Ausführliche deutsche Anleitung](ANLEITUNG.md) – Installation, Berechtigungen, Arena-Setup, Modi, Lobby und Fehlerbehebung.
- [Quellcode](https://github.com/Jawollo07/mc-manhunt-plugin)
- [Builds und Tests](https://github.com/Jawollo07/mc-manhunt-plugin/actions)

## Lizenz

Eine Lizenzdatei ist derzeit nicht Bestandteil des Repositorys. Bitte kläre die Nutzungs- und Weiterverteilungsrechte vor einer Weitergabe des Plugins mit dem Projektinhaber.
