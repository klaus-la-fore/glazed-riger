# 🔧 MIXIN PACKAGE PROBLEM – VOLLSTÄNDIGE LÖSUNG

## 🎯 Diagnose (was genau falsch ist)

### Das Problem in einem Satz:
Das Mixin-System erlaubt **nur ein einziges `"package"`** pro Config-Datei.  
Du hast Mixin-Klassen in **zwei verschiedenen Packages**:
- `com.nnpg.glazed.mixins.*` (mit **s**)
- `com.nnpg.glazed.mixin.*` (ohne **s**)

### Die Lösung in einem Satz:
**Zwei separate Mixin-Config-Dateien** → eine pro Package.

---

## 🚀 SCHRITT 1 – Diagnose-Script ausführen

Führe dieses Script im Projektverzeichnis aus, um exakt zu sehen was vorhanden ist:

```bash
cd ~/devhub/HelixCraft-Glazed

echo "=== PACKAGE mixins (MIT s) ==="
find src -path "*/com/nnpg/glazed/mixins/**/*.java" | sort \
  | sed 's|.*glazed/mixins/||; s|\.java||; s|/|.|g' \
  | awk '{print "    \"" $0 "\""}'

echo ""
echo "=== PACKAGE mixin (OHNE s) ==="
find src -path "*/com/nnpg/glazed/mixin/**/*.java" | sort \
  | sed 's|.*glazed/mixin/||; s|\.java||; s|/|.|g' \
  | awk '{print "    \"" $0 "\""}'

echo ""
echo "=== AKTUELLE mixins.json ==="
cat src/main/resources/mixins.json

echo ""
echo "=== AKTUELLE fabric.mod.json ==="
cat src/main/resources/fabric.mod.json
```

---

## 🚀 SCHRITT 2 – Automatischer Fix (empfohlen)

Führe dieses Script direkt aus — es erstellt automatisch die richtigen Config-Dateien:

```bash
cd ~/devhub/HelixCraft-Glazed
RESOURCES="src/main/resources"

# ────────────────────────────────────────────────────────────────
# 1) Mixin-Klassen in com.nnpg.glazed.mixins (MIT s) einsammeln
# ────────────────────────────────────────────────────────────────
MIXINS_S=$(find src -path "*/com/nnpg/glazed/mixins/**/*.java" | sort \
  | sed 's|.*glazed/mixins/||; s|\.java||; s|/|.|g' \
  | awk '{printf "    \"%s\"", $0; if (NR>1) printf ",\n"; else printf ""}' \
  | awk 'NR==1{line=$0; next} {print prev","} {prev=line; line=$0} END{print line}')

# ────────────────────────────────────────────────────────────────
# 2) Mixin-Klassen in com.nnpg.glazed.mixin (OHNE s) einsammeln
# ────────────────────────────────────────────────────────────────
MIXIN_NS=$(find src -path "*/com/nnpg/glazed/mixin/**/*.java" | sort \
  | sed 's|.*glazed/mixin/||; s|\.java||; s|/|.|g' \
  | awk '{printf "    \"%s\"", $0; if (NR>1) printf ",\n"; else printf ""}' \
  | awk 'NR==1{line=$0; next} {print prev","} {prev=line; line=$0} END{print line}')

echo "Gefunden in mixins (mit s):"
echo "$MIXINS_S"
echo ""
echo "Gefunden in mixin (ohne s):"
echo "$MIXIN_NS"
```

> Schaue dir die Ausgabe an und fahre dann mit Schritt 3 fort.

---

## 🚀 SCHRITT 3 – Config-Dateien manuell erstellen

### Datei 1: `src/main/resources/glazed-mixins.json`
*(für Package `com.nnpg.glazed.mixins` — MIT s)*

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.nnpg.glazed.mixins",
  "compatibilityLevel": "JAVA_21",
  "mixins": [],
  "client": [
    "HandledScreenMixin",
    "DefaultSettingsWidgetFactoryMixin",
    "DefaultSettingsWidgetFactoryAccessor"
  ],
  "server": [],
  "injectors": {
    "defaultRequire": 1
  }
}
```

> ⚠️ **Passe die Liste an!** Füge alle Klassen aus deiner `mixins`-Ausgabe ein.
> Nur den Klassenname ohne Package-Prefix (der ist bereits in `"package"` definiert).

---

### Datei 2: `src/main/resources/glazed-mixin.json`
*(für Package `com.nnpg.glazed.mixin` — OHNE s)*

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.nnpg.glazed.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": [],
  "client": [
    "protection.TranslatableTextContentMixin",
    "protection.KeybindTextContentMixin"
  ],
  "server": [],
  "injectors": {
    "defaultRequire": 1
  }
}
```

> ⚠️ **Passe die Liste an!** Füge alle Klassen aus deiner `mixin`-Ausgabe ein.
> Subpackages werden mit Punkt geschrieben: `"protection.MeinMixin"`

---

### Datei 3: `src/main/resources/fabric.mod.json`
*(Beide Config-Dateien registrieren)*

Suche den `"mixins"`-Block und ersetze ihn:

```json
"mixins": [
  "glazed-mixins.json",
  "glazed-mixin.json"
],
```

> Die alte `mixins.json` kann danach **gelöscht** werden.

---

## 🚀 SCHRITT 4 – Alte mixins.json löschen

```bash
cd ~/devhub/HelixCraft-Glazed
rm src/main/resources/mixins.json
```

---

## 🚀 SCHRITT 5 – Bauen und testen

```bash
cd ~/devhub/HelixCraft-Glazed
./gradlew build runClient
```

---

## 📋 Referenz: Wie Mixin-Namen in der JSON funktionieren

```
"package": "com.nnpg.glazed.mixins"

→ "HandledScreenMixin"
   bedeutet: com.nnpg.glazed.mixins.HandledScreenMixin  ✅

→ "protection.TranslatableTextContentMixin"
   bedeutet: com.nnpg.glazed.mixins.protection.TranslatableTextContentMixin  ✅

→ "com.nnpg.glazed.mixins.HandledScreenMixin"  ❌ FALSCH (doppelter Prefix)
```

---

## 🩺 Mögliche Fehler nach dem Fix

### `ClassNotFoundException: com.nnpg.glazed.mixins.XyzMixin`
**Ursache:** Klasse steht in JSON, existiert aber nicht im richtigen Package.  
**Fix:** Prüfe den echten Packagenamen der Klasse mit:
```bash
grep -r "^package" src/main/java/com/nnpg/glazed/mixins/XyzMixin.java
```

### `IllegalClassLoadError: GlazedAddon is in a defined mixin package`
**Ursache:** `fabric.mod.json` zeigt noch auf alte `mixins.json` mit `"package": "com.nnpg.glazed"`.  
**Fix:** Stelle sicher, dass die alte `mixins.json` gelöscht ist und `fabric.mod.json` nur `glazed-mixins.json` und `glazed-mixin.json` enthält.

### `Unable to locate obfuscation mapping for @Inject target`
**Ursache:** Der Mixin-Target existiert nicht (z.B. wegen Meteor-Client-internem Package).  
**Fix:** Kein Crash, nur eine Warnung — kann ignoriert werden wenn der Mixin-Effekt trotzdem funktioniert.

---

## 📁 Erwartete finale Dateistruktur

```
src/main/resources/
├── glazed-mixin.json          ← NEU (Package: com.nnpg.glazed.mixin)
├── glazed-mixins.json         ← NEU (Package: com.nnpg.glazed.mixins)
├── fabric.mod.json            ← GEÄNDERT (referenziert beide neuen JSONs)
└── mixins.json                ← GELÖSCHT

src/main/java/com/nnpg/glazed/
├── mixins/                    ← UNVERÄNDERT (Klassen bleiben wo sie sind)
│   ├── HandledScreenMixin.java
│   ├── DefaultSettingsWidgetFactoryMixin.java
│   └── DefaultSettingsWidgetFactoryAccessor.java
├── mixin/                     ← UNVERÄNDERT
│   └── protection/
│       ├── TranslatableTextContentMixin.java
│       └── KeybindTextContentMixin.java
├── addon/
│   └── GlazedAddon.java       ← KEIN Mixin, kein Problem mehr
└── modules/
    └── ...                    ← KEIN Mixin, kein Problem mehr
```

---

## ⚡ TL;DR – Kurzfassung

1. **Erstelle** `glazed-mixins.json` mit `"package": "com.nnpg.glazed.mixins"` + alle Klassen aus dem `mixins`-Package
2. **Erstelle** `glazed-mixin.json` mit `"package": "com.nnpg.glazed.mixin"` + alle Klassen aus dem `mixin`-Package  
3. **Ändere** `fabric.mod.json`: `"mixins": ["glazed-mixins.json", "glazed-mixin.json"]`
4. **Lösche** die alte `mixins.json`
5. `./gradlew build runClient`
