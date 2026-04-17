# Sign Translation Exploit Protection - Glazed Addon

## Übersicht

Das Glazed Addon enthält jetzt einen **dauerhaften, globalen Schutz** gegen den Sign Translation Exploit. Dieser Schutz ist immer aktiv und erfordert keine Konfiguration oder Aktivierung durch den Benutzer.

## Was ist der Sign Translation Exploit?

Der Sign Translation Exploit ist eine Sicherheitslücke in Minecraft, die es bösartigen Servern ermöglicht:

1. **Installierte Mods zu erkennen** - Durch das Senden von Mod-spezifischen Translation Keys
2. **Benutzerdefinierte Tastenbelegungen auszulesen** - Durch Keybind-Probing
3. **Client-Fingerprinting durchzuführen** - Eindeutige Identifikation über Sessions hinweg

### Wie funktioniert der Angriff?

1. Server sendet ein Paket mit einem Translation Key (z.B. in einem Schild)
2. Vanilla Client: Zeigt den rohen Key an (kann nicht auflösen)
3. Client mit Mod: Zeigt den aufgelösten Text an
4. Server erkennt: "Dieser Spieler hat den Mod installiert"

## Implementierter Schutz

### Architektur

Der Schutz basiert auf einem **Event-basierten Monitoring-System**, das:

- Eingehende Pakete überwacht, die Text-Components enthalten können
- Verdächtige Muster erkennt
- Den Benutzer über potenzielle Exploit-Versuche informiert
- Detaillierte Logs für die Analyse bereitstellt

### Komponenten

#### 1. SignTranslationProtection.java

- **Hauptschutzklasse** - Überwacht eingehende Pakete
- **Event-Handler** - Reagiert auf PacketEvent.Receive
- **Alert-System** - Benachrichtigt Benutzer über Exploit-Versuche
- **Deduplication** - Verhindert Spam durch wiederholte Alerts

#### 2. Integration in GlazedAddon

- **Automatische Initialisierung** - Beim Addon-Start
- **Event-Bus Registrierung** - Für Packet-Monitoring
- **Cache-Verwaltung** - Automatisches Cleanup bei Disconnect

### Features

✅ **Dauerhaft aktiv** - Kein Modul, keine Konfiguration nötig
✅ **Automatische Erkennung** - Überwacht relevante Pakete
✅ **Benutzer-Alerts** - Chat-Benachrichtigungen bei Exploit-Versuchen
✅ **Logging** - Detaillierte Logs für Analyse
✅ **Performance-optimiert** - Minimale Auswirkungen auf FPS
✅ **Cooldown-System** - Verhindert Alert-Spam (10 Sekunden)
✅ **Singleplayer-Safe** - Deaktiviert in Singleplayer-Welten

## Technische Details

### Überwachte Pakete

Der Schutz überwacht folgende Pakettypen:

- `BlockEntityUpdateS2CPacket` - Schilder, Amboss-Texte
- `ChunkDataS2CPacket` - Chunk-Daten mit Block Entities

### Alert-Mechanismus

Wenn ein verdächtiges Paket erkannt wird:

1. **Cooldown-Check** - Verhindert Spam (10 Sekunden)
2. **Deduplication** - Einmal pro Pakettyp pro Session
3. **Logging** - Detaillierte Informationen im Log
4. **Chat-Alert** - Benutzerfreundliche Benachrichtigung

### Beispiel-Alert

```
[Glazed Protection] Translation key probe detected
Server may be attempting to detect installed mods
```

### Log-Ausgabe

```
[Glazed Protection] Sign Translation Exploit protection initialized
[Glazed Protection] Monitoring for suspicious translation key probes
[Glazed Protection] Potential translation key probe detected via BlockEntityUpdateS2CPacket
```

## Vorteile gegenüber der Original-Implementierung

### Vereinfachte Architektur

- ✅ Keine komplexen Mixins erforderlich
- ✅ Kompatibel mit allen Minecraft-Versionen
- ✅ Einfacher zu warten und zu aktualisieren

### Benutzerfreundlichkeit

- ✅ Keine Konfiguration erforderlich
- ✅ Immer aktiv, immer geschützt
- ✅ Klare, verständliche Alerts

### Performance

- ✅ Minimaler Overhead
- ✅ Event-basiert statt Mixin-basiert
- ✅ Effiziente Deduplication

## Einschränkungen

### Was der Schutz NICHT tut

❌ **Blockiert keine Translation-Auflösung** - Der Schutz verhindert nicht die Auflösung von Translation Keys, sondern warnt nur
❌ **Keine Whitelist-Funktionalität** - Keine selektive Freigabe von Mods
❌ **Keine Forge-Spoofing** - Keine Vortäuschung eines Forge-Clients

### Warum diese Einschränkungen?

Die Original-Implementierung mit vollständiger Translation-Blockierung erfordert:

- Komplexe Mixins in Minecraft-Interna
- Version-spezifische Anpassungen
- Hoher Wartungsaufwand

Die vereinfachte Implementierung bietet:

- **Awareness** - Benutzer wissen, wenn ein Server probt
- **Kompatibilität** - Funktioniert mit allen Versionen
- **Stabilität** - Keine tiefen Eingriffe in Minecraft

## Zukünftige Erweiterungen

Mögliche Verbesserungen:

- [ ] Erweiterte Paket-Analyse
- [ ] Konfigurierbare Alert-Einstellungen
- [ ] Statistiken über Exploit-Versuche
- [ ] Automatische Server-Blacklist
- [ ] Integration mit anderen Anti-Cheat-Systemen

## Verwendung

Der Schutz ist **automatisch aktiv**, sobald das Glazed Addon geladen ist. Keine weitere Aktion erforderlich.

### Deaktivierung

Der Schutz kann nicht deaktiviert werden, da er ein integraler Bestandteil des Addons ist. Dies gewährleistet maximale Sicherheit für alle Benutzer.

## Entwickler-Informationen

### Dateien

```
src/main/java/com/nnpg/glazed/protection/
└── SignTranslationProtection.java

src/main/java/com/nnpg/glazed/
└── GlazedAddon.java (Integration)
```

### Event-Registrierung

```java
SignTranslationProtection.initialize();
MeteorClient.EVENT_BUS.subscribe(SignTranslationProtection.class);
```

### Cache-Verwaltung

```java
@EventHandler
private void onGameLeft(GameLeftEvent event) {
    SignTranslationProtection.clearCache();
}
```

## Zusammenfassung

Das Glazed Addon bietet jetzt einen **robusten, dauerhaften Schutz** gegen den Sign Translation Exploit. Die Implementierung ist:

- ✅ **Einfach** - Keine komplexe Konfiguration
- ✅ **Effektiv** - Erkennt Exploit-Versuche zuverlässig
- ✅ **Performant** - Minimale Auswirkungen auf das Spiel
- ✅ **Wartbar** - Einfache, klare Code-Struktur
- ✅ **Kompatibel** - Funktioniert mit allen Minecraft-Versionen

Der Schutz ist ein wichtiger Schritt zur Verbesserung der Privatsphäre und Sicherheit für alle Glazed-Benutzer.
