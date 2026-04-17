# Mixin Package Problem - Situationsanalyse

## Aktueller Status: CRASH beim Start

### Fehler

```
IllegalClassLoadError: com.nnpg.glazed.addon.GlazedAddon is in a defined mixin package
com.nnpg.glazed.* owned by mixins.json and cannot be referenced directly
```

## Problem-Ursache

In `src/main/resources/mixins.json`:

```json
{
  "package": "com.nnpg.glazed",
  ...
}
```

**Das bedeutet:** ALLE Klassen unter `com.nnpg.glazed.*` werden als Mixin-Klassen behandelt!

Das betrifft:

- ✅ `com.nnpg.glazed.mixin.*` - SOLL Mixin sein
- ✅ `com.nnpg.glazed.mixins.*` - SOLL Mixin sein
- ❌ `com.nnpg.glazed.addon.GlazedAddon` - SOLL KEIN Mixin sein (aber wird als Mixin behandelt!)
- ❌ `com.nnpg.glazed.MyScreen` - SOLL KEIN Mixin sein
- ❌ `com.nnpg.glazed.modules.*` - SOLLEN KEINE Mixins sein
- ❌ `com.nnpg.glazed.protection.*` - SOLLEN KEINE Mixins sein

## Bisherige Lösungsversuche (ALLE GESCHEITERT!)

### Versuch 1: GlazedAddon nach com.nnpg.glazed.addon verschieben

**Status:** ❌ GESCHEITERT

- Klasse verschoben von `com.nnpg.glazed.GlazedAddon` → `com.nnpg.glazed.addon.GlazedAddon`
- Entrypoint in fabric.mod.json angepasst
- **Problem:** `addon` ist Subpackage von `com.nnpg.glazed`
- Mixin-System behandelt es trotzdem als Mixin-Klasse
- **Crash:** Gleicher IllegalClassLoadError

### Versuch 2: Package in mixins.json auf "com.nnpg.glazed.mixin" ändern

**Status:** ❌ GESCHEITERT

- mixins.json geändert: `"package": "com.nnpg.glazed.mixin"`
- Mixin-Namen angepasst (z.B. `HandledScreenMixin` statt `mixins.HandledScreenMixin`)
- **Problem:** Alte Mixins sind in `com.nnpg.glazed.mixins` (mit 's' am Ende!)
- **Crash:** ClassNotFoundException für HandledScreenMixin, DefaultSettingsWidgetFactoryAccessor, etc.
- Mixins konnten nicht gefunden werden

### Versuch 3: Mixin-Namen mit vollständigem Pfad in mixins.json

**Status:** ❌ GESCHEITERT

- Versucht: `"mixins.HandledScreenMixin"` in client-Array
- Versucht: `"mixin.protection.TranslatableTextContentMixin"`
- **Problem:** Mixin-System erwartet relative Pfade zum Package
- **Crash:** Verschiedene ClassNotFoundException

### Versuch 4: Package zurück auf "com.nnpg.glazed" mit korrigierten Namen

**Status:** ❌ GESCHEITERT

- mixins.json: `"package": "com.nnpg.glazed"`
- Mixin-Namen: `"mixins.HandledScreenMixin"`, `"mixin.protection.TranslatableTextContentMixin"`
- **Problem:** Zurück zum ursprünglichen Problem
- **Crash:** IllegalClassLoadError - GlazedAddon wird als Mixin behandelt

### Versuch 5: Nur neue Protection-Mixins in mixins.json, alte entfernt

**Status:** ❌ GESCHEITERT

- Nur Protection-Mixins in client-Array behalten
- Alte Mixins (HandledScreenMixin etc.) entfernt
- **Problem:** Alte Mixins werden für andere Features benötigt
- **Crash:** Features funktionieren nicht mehr, Meteor-Integration kaputt

### Versuch 6: GlazedAddon komplett aus com.nnpg.glazed raus verschieben

**Status:** ❌ NICHT DURCHGEFÜHRT (zu aufwendig)

- Würde bedeuten: Alle Nicht-Mixin-Klassen verschieben
- Hunderte von Imports ändern
- Zu hohes Risiko für neue Fehler
- **Entscheidung:** Nicht umgesetzt

## Mögliche Lösungen

### Lösung A: Zwei separate Mixin-Configs erstellen ⭐ EMPFOHLEN

Erstelle zwei Mixin-Config-Dateien:

1. `mixins.json` - für alte Mixins in `com.nnpg.glazed.mixins`
2. `mixins.protection.json` - für neue Protection-Mixins in `com.nnpg.glazed.mixin.protection`

**Vorteile:**

- Keine Klassen müssen verschoben werden
- Saubere Trennung zwischen alten und neuen Mixins
- GlazedAddon bleibt wo es ist

**Umsetzung:**

1. Erstelle `src/main/resources/mixins.protection.json`:

```json
{
  "required": true,
  "package": "com.nnpg.glazed.mixin.protection",
  "compatibilityLevel": "JAVA_17",
  "mixins": [],
  "client": [
    "TranslatableTextContentMixin",
    "KeybindTextContentMixin",
    "TranslationStorageAccessor",
    "DecoderHandlerMixin",
    "ClientConnectionMixin",
    "TranslationStorageMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

2. Ändere `src/main/resources/mixins.json` zurück:

```json
{
  "required": true,
  "package": "com.nnpg.glazed.mixins",
  "compatibilityLevel": "JAVA_17",
  "mixins": [],
  "client": [
    "HandledScreenMixin",
    "DefaultSettingsWidgetFactoryAccessor",
    "DefaultSettingsWidgetFactoryMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

3. Registriere beide in `fabric.mod.json`:

```json
"mixins": [
  "mixins.json",
  "mixins.protection.json"
]
```

### Lösung B: Alle Nicht-Mixin-Klassen aus com.nnpg.glazed verschieben

Verschiebe ALLE Nicht-Mixin-Klassen in ein anderes Root-Package:

- `com.nnpg.glazed.addon.*` → `com.nnpg.glazedaddon.*`
- `com.nnpg.glazed.modules.*` → `com.nnpg.glazedaddon.modules.*`
- `com.nnpg.glazed.protection.*` → `com.nnpg.glazedaddon.protection.*`
- etc.

**Nachteile:**

- Massive Refactoring erforderlich
- Hunderte von Imports müssen geändert werden
- Hohe Fehleranfälligkeit

### Lösung C: Mixin Refmap Manipulation (NICHT EMPFOHLEN)

Versuche das Mixin-System zu überlisten durch Refmap-Manipulation.

**Nachteile:**

- Sehr komplex
- Fragil
- Kann bei Mixin-Updates brechen

## Empfohlene Vorgehensweise

**Lösung A** ist die beste Option:

1. Minimale Änderungen
2. Saubere Architektur
3. Keine Breaking Changes
4. Einfach zu warten

## Nächste Schritte - DRINGEND EXTERNE HILFE BENÖTIGT!

**ALLE bisherigen Versuche sind gescheitert!**

Das Problem ist komplex wegen:

1. Alte Mixins in `com.nnpg.glazed.mixins.*`
2. Neue Mixins in `com.nnpg.glazed.mixin.protection.*`
3. GlazedAddon in `com.nnpg.glazed.addon.*`
4. Mixin-System behandelt ALLES unter dem Package als Mixin

**Mögliche Lösungen (noch nicht getestet):**

### Lösung A: Zwei separate Mixin-Configs ⭐ NOCH NICHT GETESTET

1. Erstelle `mixins.protection.json` für neue Mixins
2. Behalte `mixins.json` für alte Mixins
3. Registriere beide in `fabric.mod.json`

### Lösung B: Alle Mixins in ein gemeinsames Package verschieben

1. Verschiebe alte Mixins von `mixins` → `mixin`
2. Dann kann Package auf `com.nnpg.glazed.mixin` gesetzt werden
3. **Problem:** Viel Refactoring, könnte andere Dinge brechen

### Lösung C: Komplettes Refactoring

1. Alle Nicht-Mixin-Klassen aus `com.nnpg.glazed` raus
2. Neues Root-Package für Addon-Code
3. **Problem:** Hunderte Dateien, sehr fehleranfällig

**BITTE EXTERNE KI/EXPERTEN KONSULTIEREN!**

## Crash Report Details (Letzter Versuch)

```
Time: 2026-04-16 23:47:15
Description: Initializing game

java.lang.RuntimeException: Exception during addon init "Glazed".
Caused by: net.fabricmc.loader.api.EntrypointException: Exception while loading entries for entrypoint 'meteor' provided by 'glazed'
Caused by: java.lang.RuntimeException: Mixin transformation of com.nnpg.glazed.GlazedAddon failed
Caused by: org.spongepowered.asm.mixin.transformer.throwables.IllegalClassLoadError:
com.nnpg.glazed.GlazedAddon is in a defined mixin package
com.nnpg.glazed.* owned by mixins.json and cannot be referenced directly
```

**Aktuelle mixins.json Konfiguration (beim letzten Crash):**

```json
{
  "package": "com.nnpg.glazed.mixin",
  "client": [
    "HandledScreenMixin",
    "DefaultSettingsWidgetFactoryAccessor",
    "DefaultSettingsWidgetFactoryMixin",
    "protection.TranslatableTextContentMixin",
    "protection.KeybindTextContentMixin",
    "protection.TranslationStorageAccessor",
    "protection.DecoderHandlerMixin",
    "protection.ClientConnectionMixin",
    "protection.TranslationStorageMixin"
  ]
}
```

**Problem:** Alte Mixins sind in `com.nnpg.glazed.mixins.*`, neue in `com.nnpg.glazed.mixin.protection.*`
→ Keine einzelne Package-Deklaration kann beide abdecken!

**Minecraft Version:** 1.21.4
**Glazed Version:** 1.21.4-n-16.1
**Java Version:** 21.0.9
**Fabric Loader:** 0.16.9
**Meteor Client:** 1.21.4-42

## Implementierungsstatus der Protection

Alle Protection-Mixins sind ERSTELLT und KOMPILIEREN:

- ✅ TranslatableTextContentMixin
- ✅ KeybindTextContentMixin
- ✅ TranslationStorageAccessor
- ✅ DecoderHandlerMixin
- ✅ ClientConnectionMixin
- ✅ TranslationStorageMixin

**Problem:** Sie können nicht geladen werden wegen des Mixin-Package-Konflikts!
