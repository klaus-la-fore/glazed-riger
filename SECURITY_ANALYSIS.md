# Sign Translation Exploit Protection - Tiefgehende Sicherheitsanalyse

**Datum:** 2026-04-17  
**Analysierte Implementierung:** Glazed Addon v1.21.4-n-16.1  
**Referenz:** OpSec Mod (sign-translation-exploit-analysis/)  
**Status:** ⚠️ KRITISCHE SCHWACHSTELLEN GEFUNDEN

---

## Executive Summary

Die aktuelle Implementierung ist **NICHT VOLLSTÄNDIG SICHER** gegen den Sign Translation Exploit. Es gibt mehrere kritische Lücken in der Architektur, fehlende Komponenten und potenzielle Detection-Vektoren.

**Schutzgrad:** ~40% (Infrastruktur vorhanden, aber unvollständig)

---

## 1. Architektur & Konsistenz

### ✅ Korrekt implementiert:

1. **Layer-Struktur vorhanden:**
   - Layer 1: PacketContext Tracking (DecoderHandlerMixin + ClientConnectionMixin)
   - Layer 2: Content Tagging (TranslatableTextContentMixin + KeybindTextContentMixin)
   - Layer 3: Resolution Interception (beide Mixins)
   - Layer 4: Alert & Logging (TranslationProtectionHandler)

2. **ThreadLocal-basiertes Tracking:**
   - PacketContext nutzt ThreadLocal korrekt
   - Beide Entry-Points (eager + lazy deserialization) werden abgefangen

3. **Grundlegende Whitelist-Logik:**
   - Vanilla keys werden erlaubt
   - Server resource pack keys werden erlaubt
   - Mod keys werden blockiert

### ❌ Kritische Abweichungen:

#### 1.1 Fehlende ClientLanguageMixin

**Referenz:** `ClientLanguageMixin.java` + `ClientLanguageAccessor.java`  
**Aktuell:** Nicht vorhanden

**Problem:**

- Keine automatische Erkennung von Translation Keys aus Language Files
- ModRegistry bleibt leer → Kann nicht zwischen Vanilla/Mod/ServerPack unterscheiden
- Alle Keys werden als "unbekannt" behandelt

**Impact:** 🔴 KRITISCH

- Vanilla keys werden möglicherweise blockiert (False Positives)
- Oder: Mod keys werden durchgelassen (False Negatives)
- Keine Basis für intelligente Whitelist-Entscheidungen

#### 1.2 Fehlende Mod-Tracking-Infrastruktur

**Referenz:** `ModRegistry.java` mit vollständiger Mod-Tracking-Logik  
**Aktuell:** Stark vereinfachte Version ohne:

- `ModInfo` Klasse
- Channel-Tracking
- Keybind-zu-Mod-Mapping
- Whitelist-Modi (OFF/AUTO/CUSTOM)

**Problem:**

- Keine Möglichkeit, Mods zu whitelisten
- Keine Auto-Detection von "sicheren" Mods
- Keine Granularität in der Schutzlogik

**Impact:** 🟡 HOCH

- Benutzer kann nicht kontrollieren, welche Mods exponiert werden
- Keine Flexibilität für verschiedene Server-Szenarien

#### 1.3 Fehlende Spoofing-Modi

**Referenz:** `ClientSpoofer.java` + `ForgeTranslations.java`  
**Aktuell:** Nicht vorhanden

**Problem:**

- Nur "Block Everything"-Modus
- Kein Vanilla/Fabric/Forge-Spoofing
- Keine konsistente Client-Identität

**Impact:** 🟡 HOCH

- Client-Verhalten ist inkonsistent
- Leicht als "modifiziert" erkennbar
- Keine Möglichkeit, als Forge-Client zu erscheinen

---

## 2. Datenfluss & Hooking

### ✅ Korrekt implementiert:

1. **Eager Deserialization abgefangen:**
   - `DecoderHandlerMixin` wraps `PacketCodec.decode()`
   - Setzt `PacketContext.setProcessingPacket(true)` korrekt

2. **Lazy Deserialization abgefangen:**
   - `ClientConnectionMixin` wraps `Packet.apply()`
   - Setzt Packet-Name und Processing-Flag

3. **Content-Tagging:**
   - `TranslatableTextContentMixin` taggt Content im Constructor
   - `KeybindTextContentMixin` taggt Content im Constructor

4. **Resolution-Interception:**
   - `TranslatableTextContentMixin` wraps `Language.get()`
   - `KeybindTextContentMixin` wraps `Supplier.get()`

### ⚠️ Potenzielle Lücken:

#### 2.1 Fehlende Packet-Typ-Filterung

**Referenz:** OpSec filtert nach Packet-Typ (z.B. nur Chat, Signs, Anvils)  
**Aktuell:** Alle Packets werden gleich behandelt

**Problem:**

- Overhead bei jedem Packet
- Keine Optimierung für "sichere" Packets
- Potenzielle Performance-Issues

**Impact:** 🟢 NIEDRIG (funktional korrekt, aber ineffizient)

#### 2.2 Fehlende Resource Pack Tracking

**Referenz:** `ClientLanguageMixin.appendFrom()` trackt Server Pack Keys  
**Aktuell:** `ModRegistry.recordServerPackKey()` existiert, wird aber nie aufgerufen

**Problem:**

- Server Resource Pack Keys werden nicht erkannt
- Könnten fälschlicherweise blockiert werden
- Oder: Werden als "unbekannt" durchgelassen

**Impact:** 🔴 KRITISCH

- Server Resource Packs funktionieren möglicherweise nicht
- Oder: Server kann erkennen, dass Client modifiziert ist (fehlende Pack-Responses)

#### 2.3 Fehlende Dedup-Clearing bei Disconnect

**Referenz:** `OpsecClient.java` registriert Disconnect-Handler  
**Aktuell:** `TranslationProtectionHandler.clearCache()` existiert, wird aber nie aufgerufen

**Problem:**

- Dedup-Caches wachsen unbegrenzt über Sessions hinweg
- Memory Leak bei langen Sessions
- Alte Alerts werden nicht mehr angezeigt

**Impact:** 🟡 MITTEL (Memory Leak + UX-Problem)

---

## 3. Sicherheitsanalyse

### 🔴 KRITISCHE SCHWACHSTELLEN:

#### 3.1 Vanilla Key Detection fehlt komplett

**Szenario:**

```
Server sendet: Component.translatable("key.attack")
Aktuell: ModRegistry.isVanillaTranslationKey("key.attack") → false (Registry leer!)
Ergebnis: Key wird blockiert → Client zeigt "key.attack" statt "Left Click"
Server sieht: Vanilla-Client würde "Left Click" zeigen
→ SERVER ERKENNT: Client ist modifiziert!
```

**Bypass-Vektor:** ✅ Server kann Client als modifiziert erkennen

#### 3.2 Server Resource Pack Keys werden nicht getrackt

**Szenario:**

```
Server sendet Resource Pack mit: "custom.server.key" → "Server Text"
Server sendet später: Component.translatable("custom.server.key")
Aktuell: Key ist nicht in ModRegistry → wird blockiert
Ergebnis: Client zeigt "custom.server.key" statt "Server Text"
Server sieht: Vanilla-Client würde "Server Text" zeigen
→ SERVER ERKENNT: Client ist modifiziert!
```

**Bypass-Vektor:** ✅ Server kann Client als modifiziert erkennen

#### 3.3 Keine Fake Default Keybinds

**Szenario:**

```
Server sendet: Component.keybind("key.attack")
User hat: "key.attack" auf "Q" gebunden
Aktuell: KeybindDefaults.getDefault("key.attack") → "Left Button"
Ergebnis: Client zeigt "Left Button"
Server sieht: "Left Button" (korrekt)
```

**Status:** ✅ Funktioniert korrekt (aber nur weil KeybindDefaults vorhanden ist)

#### 3.4 Mod Keys mit ungewöhnlichen Präfixen

**Szenario:**

```
Mod verwendet: "gui.xaero_toggle_slime" (nicht "key.xaero...")
Server sendet: Component.translatable("gui.xaero_toggle_slime")
Aktuell: Nicht in vanillaTranslationKeys → wird blockiert
Ergebnis: Client zeigt "gui.xaero_toggle_slime"
Server sieht: Vanilla-Client würde auch "gui.xaero_toggle_slime" zeigen
```

**Status:** ✅ Funktioniert korrekt (wird blockiert, sieht aus wie Vanilla)

### ⚠️ EDGE CASES:

#### 3.5 Verschachtelte TranslatableContents

**Szenario:**

```java
Component.translatable("chat.type.text",
    Component.translatable("key.meteor-client.open-gui"))
```

**Aktuell:**

- Äußeres Component: `glazed$fromPacket = true`
- Inneres Component: `glazed$fromPacket = true` (korrekt!)
- Beide werden intercepted

**Status:** ✅ Funktioniert korrekt (ThreadLocal wird vererbt)

#### 3.6 JSON Components mit Translation

**Szenario:**

```json
{
  "translate": "key.meteor-client.open-gui",
  "with": [{ "text": "test" }]
}
```

**Aktuell:**

- Wird als `TranslatableTextContent` deserialisiert
- Constructor wird während `PacketContext.isProcessingPacket() == true` aufgerufen
- Wird korrekt getaggt und blockiert

**Status:** ✅ Funktioniert korrekt

#### 3.7 Lazy vs Eager Deserialization

**Szenario:**

- Eager: Component wird während `PacketCodec.decode()` erstellt
- Lazy: Component wird während `Packet.apply()` erstellt

**Aktuell:**

- Beide Fälle werden durch separate Mixins abgefangen
- `DecoderHandlerMixin` für eager
- `ClientConnectionMixin` für lazy

**Status:** ✅ Funktioniert korrekt

---

## 4. Detection Resistance (OPSEC)

### 🔴 KRITISCHE DETECTION-VEKTOREN:

#### 4.1 Vanilla Key Blocking

**Wie Server erkennen kann:**

```
1. Server sendet Sign mit "key.attack"
2. Vanilla Client zeigt: "Left Click" (oder user's binding)
3. Glazed Client zeigt: "key.attack" (weil nicht in Registry)
4. Server fragt: "Was steht auf dem Schild?"
5. User antwortet: "key.attack"
→ SERVER WEISS: Client ist modifiziert
```

**Wahrscheinlichkeit:** 🔴 SEHR HOCH (100% wenn Server aktiv testet)

#### 4.2 Server Resource Pack Detection

**Wie Server erkennen kann:**

```
1. Server sendet Resource Pack mit "custom.key" → "Custom Text"
2. Server sendet Sign mit "custom.key"
3. Vanilla Client zeigt: "Custom Text"
4. Glazed Client zeigt: "custom.key" (nicht getrackt)
5. Server sieht unterschiedliches Verhalten
→ SERVER WEISS: Client ist modifiziert
```

**Wahrscheinlichkeit:** 🔴 HOCH (wenn Server Resource Packs nutzt)

#### 4.3 Timing-basierte Detection

**Wie Server erkennen kann:**

```
1. Server sendet 1000 Signs mit verschiedenen Keys
2. Vanilla Client: Konstante Render-Zeit
3. Glazed Client: Längere Render-Zeit (Mixin-Overhead)
4. Server misst Response-Zeit
→ SERVER KÖNNTE: Anomalie erkennen
```

**Wahrscheinlichkeit:** 🟡 MITTEL (schwer zu messen, aber möglich)

#### 4.4 Fehlende Mod-Channels

**Wie Server erkennen kann:**

```
1. Server sendet Custom Payload auf "meteor:channel"
2. Vanilla/Fabric Client: Ignoriert (kein Handler)
3. Glazed Client: Ignoriert (kein Handler)
4. Server erwartet: Keine Response
→ Kein Detection-Vektor (korrekt)
```

**Status:** ✅ Sicher (keine Channels exponiert)

### ✅ KORREKTE OPSEC-MASSNAHMEN:

1. **Keine zusätzlichen Network-Requests:**
   - Keine Update-Checks während Multiplayer
   - Keine Telemetrie

2. **Keine auffälligen Logs:**
   - Logs nur lokal
   - Keine Server-sichtbaren Fehler

3. **Konsistentes Blocking:**
   - Alle Mod-Keys werden gleich behandelt
   - Keine partiellen Leaks

---

## 5. Vollständigkeit

### ❌ FEHLENDE KOMPONENTEN:

#### 5.1 ClientLanguageMixin (KRITISCH)

**Funktion:** Trackt Translation Keys aus Language Files  
**Status:** ❌ Fehlt komplett  
**Impact:** Registry bleibt leer → Keine Vanilla-Detection

#### 5.2 ClientLanguageAccessor (KRITISCH)

**Funktion:** Accessor für Language.storage Map  
**Status:** ❌ Fehlt (aber TranslationStorageAccessor existiert)  
**Impact:** Kann echte Werte nicht lesen (aber Workaround vorhanden)

#### 5.3 KeybindRegistryMixin (HOCH)

**Funktion:** Trackt registrierte Keybinds  
**Status:** ❌ Fehlt komplett  
**Impact:** Keine Keybind-zu-Mod-Zuordnung

#### 5.4 ClientSpoofer (HOCH)

**Funktion:** Brand + Channel Spoofing  
**Status:** ❌ Fehlt komplett  
**Impact:** Keine Spoofing-Modi, keine Konsistenz

#### 5.5 ForgeTranslations (MITTEL)

**Funktion:** Fake Forge Keys für Forge-Spoofing  
**Status:** ❌ Fehlt komplett  
**Impact:** Kann nicht als Forge-Client erscheinen

#### 5.6 MeteorMixinCanceller (NIEDRIG)

**Funktion:** Deaktiviert Meteor's kaputten SignEditScreenMixin  
**Status:** ✅ Vorhanden (aber nicht getestet)  
**Impact:** Verhindert Meteor-Interferenz

#### 5.7 Disconnect-Handler (MITTEL)

**Funktion:** Cleared Caches bei Disconnect  
**Status:** ❌ Fehlt  
**Impact:** Memory Leak + alte Alerts

#### 5.8 Config-System (HOCH)

**Funktion:** User-konfigurierbare Whitelist + Modi  
**Status:** ❌ Fehlt komplett  
**Impact:** Keine User-Kontrolle

### ✅ VORHANDENE KOMPONENTEN:

1. ✅ PacketContext (vollständig)
2. ✅ TranslationProtectionHandler (vereinfacht, aber funktional)
3. ✅ ModRegistry (Grundstruktur vorhanden, aber leer)
4. ✅ KeybindDefaults (vollständig)
5. ✅ DecoderHandlerMixin (korrekt)
6. ✅ ClientConnectionMixin (korrekt)
7. ✅ TranslatableTextContentMixin (korrekt, aber ohne Vanilla-Detection)
8. ✅ KeybindTextContentMixin (korrekt)
9. ✅ TranslationStorageMixin (vereinfacht)
10. ✅ TranslationStorageAccessor (korrekt)

---

## 6. Fazit

### ⚠️ IST DIE IMPLEMENTIERUNG SICHER?

**NEIN.** Die Implementierung ist **NICHT SICHER** gegen den Sign Translation Exploit.

### 🔴 KRITISCHE SCHWACHSTELLEN:

1. **Vanilla Key Detection fehlt komplett**
   - Server kann erkennen, dass Vanilla-Keys blockiert werden
   - Client verhält sich anders als echter Vanilla-Client
   - **Bypass:** Server sendet "key.attack" → Client zeigt "key.attack" statt "Left Click"

2. **Server Resource Pack Keys werden nicht getrackt**
   - Server kann erkennen, dass Pack-Keys nicht aufgelöst werden
   - Client verhält sich anders als Vanilla mit Pack
   - **Bypass:** Server sendet Pack + Key → Client zeigt Key statt Pack-Value

3. **Keine Mod-Tracking-Infrastruktur**
   - ModRegistry bleibt leer
   - Keine Basis für intelligente Entscheidungen
   - Alle Keys werden als "unbekannt" behandelt

### 🟡 HOHE RISIKEN:

1. **Keine Whitelist-Funktionalität**
   - User kann nicht kontrollieren, welche Mods exponiert werden
   - Keine Flexibilität für verschiedene Szenarien

2. **Keine Spoofing-Modi**
   - Client kann nicht als Vanilla/Fabric/Forge erscheinen
   - Inkonsistentes Verhalten

3. **Memory Leak bei langen Sessions**
   - Dedup-Caches werden nie geleert
   - Potenzielle Performance-Degradation

### ✅ WAS FUNKTIONIERT:

1. **Grundlegende Architektur ist korrekt**
   - Layer-Struktur entspricht Referenz
   - Packet-Tracking funktioniert
   - Content-Tagging funktioniert

2. **Mod-Key-Blocking funktioniert**
   - Mod-Keys werden korrekt blockiert
   - Fallback-Werte werden zurückgegeben

3. **Keybind-Protection funktioniert**
   - Vanilla-Keybinds zeigen Defaults
   - Mod-Keybinds werden blockiert

---

## 7. Verbesserungsvorschläge (Priorität)

### 🔴 KRITISCH (Sofort beheben):

1. **ClientLanguageMixin implementieren**

   ```java
   @Inject(method = "load", at = @At("HEAD"))
   private static void onLoadStart(...) {
       ModRegistry.clearTranslationKeys();
   }

   @Inject(method = "appendFrom", at = @At("RETURN"))
   private void onAppendFrom(InputStream inputStream, BiConsumer consumer, CallbackInfo ci) {
       // Track keys by pack type
       if (isVanillaPack()) {
           ModRegistry.recordVanillaTranslationKey(key);
       } else if (isServerPack()) {
           ModRegistry.recordServerPackKey(key);
       } else {
           ModRegistry.recordTranslationKey(modId, key);
       }
   }
   ```

2. **Server Resource Pack Tracking**
   - Detect when server sends resource pack
   - Track all keys from server packs
   - Clear on disconnect

3. **Disconnect-Handler**
   ```java
   ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
       TranslationProtectionHandler.clearCache();
       ModRegistry.clearServerPackKeys();
   });
   ```

### 🟡 HOCH (Bald beheben):

4. **Mod-Tracking-Infrastruktur**
   - `ModInfo` Klasse mit Channels, Keys, Keybinds
   - Auto-Detection von Mods
   - Whitelist-System

5. **Config-System**
   - User-konfigurierbare Whitelist
   - Modi: VANILLA / FABRIC / FORGE
   - Fake Default Keybinds Toggle

6. **ClientSpoofer + ForgeTranslations**
   - Brand-Spoofing
   - Channel-Filtering
   - Forge-Key-Fabrication

### 🟢 MITTEL (Nice-to-have):

7. **Performance-Optimierung**
   - Packet-Typ-Filterung
   - Cache-Optimierung
   - Lazy-Initialization

8. **Debug-Modus**
   - Detaillierte Logs
   - Alle Keys anzeigen (auch erlaubte)
   - Performance-Metriken

9. **Testing-Suite**
   - Unit-Tests für alle Komponenten
   - Integration-Tests für Exploit-Szenarien
   - Performance-Tests

---

## 8. Exploit-Szenarien (Proof of Concept)

### Szenario 1: Vanilla Key Detection

```java
// Server-Code:
player.sendMessage(Component.translatable("key.attack"));

// Vanilla Client zeigt: "Left Click"
// Glazed Client zeigt: "key.attack"
// → Server erkennt: Client ist modifiziert
```

### Szenario 2: Resource Pack Detection

```java
// Server sendet Pack mit: "custom.key" → "Custom Text"
// Server-Code:
player.sendMessage(Component.translatable("custom.key"));

// Vanilla Client zeigt: "Custom Text"
// Glazed Client zeigt: "custom.key"
// → Server erkennt: Client ist modifiziert
```

### Szenario 3: Mod Key Probing (funktioniert korrekt)

```java
// Server-Code:
player.sendMessage(Component.translatable("key.meteor-client.open-gui"));

// Vanilla Client zeigt: "key.meteor-client.open-gui"
// Glazed Client zeigt: "key.meteor-client.open-gui"
// → Server kann NICHT erkennen, dass Meteor installiert ist ✅
```

---

## 9. Zusammenfassung

**Schutzgrad:** ~40%

**Funktioniert:**

- ✅ Mod-Key-Blocking
- ✅ Keybind-Protection
- ✅ Grundlegende Architektur

**Funktioniert NICHT:**

- ❌ Vanilla-Key-Detection
- ❌ Server-Pack-Tracking
- ❌ Whitelist-System
- ❌ Spoofing-Modi

**Empfehlung:**

1. ClientLanguageMixin SOFORT implementieren
2. Server Pack Tracking hinzufügen
3. Disconnect-Handler registrieren
4. Dann: Whitelist + Config + Spoofing

**Zeitaufwand (geschätzt):**

- Kritische Fixes: 4-6 Stunden
- Hohe Priorität: 8-12 Stunden
- Vollständige Implementierung: 20-30 Stunden

**Risiko ohne Fixes:**

- Server können Client als modifiziert erkennen
- Schutz ist ineffektiv gegen aktive Detection
- False Positives (Vanilla-Keys werden blockiert)
