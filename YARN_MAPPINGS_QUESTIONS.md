# Yarn Mappings Fragen für Minecraft 1.21.4

## 🎯 ZIEL

Diese Datei enthält ALLE Fragen zu Yarn-Mappings, die beantwortet werden müssen, um eine 100% vollständige und sichere Sign Translation Exploit Protection zu implementieren.

## 📋 ANLEITUNG

1. Öffne die Minecraft 1.21.4 Sources (dekompiliert mit Yarn-Mappings)
2. Beantworte jede Frage mit den exakten Klassen-/Methoden-Namen
3. Gib mir die ausgefüllte Datei zurück
4. Ich implementiere dann die vollständige Lösung

---

## ❓ FRAGE 1: TranslatableTextContent Klasse

### Mojang-Mapping (Vorbild):

```java
package net.minecraft.network.chat.contents;
public class TranslatableContents {
    private final String key;
    private final String fallback;

    public TranslatableContents(String key, String fallback, Object[] args) { ... }

    // Methode die Language.getOrDefault() aufruft:
    public <T> Optional<T> decompose(...) { ... }
}
```

### Yarn-Mapping (Minecraft 1.21.4):

**Bitte ausfüllen:**

```
Vollständiger Package-Name: net.minecraft.text._______________

Klassen-Name: _______________

Feld-Namen:
- key: _______________
- fallback: _______________

Konstruktor-Signatur:
public _______________(String ___, String ___, Object[] ___) { ... }

Methode die Language.get() aufruft:
- Methoden-Name: _______________
- Signatur: public <T> Optional<T> _______________(_______________) { ... }
```

**Zusatzfrage:** Ruft diese Methode `Language.get(String)` oder `Language.get(String, String)` auf?

- [ ] Nur `Language.get(String)`
- [ ] Nur `Language.get(String, String)`
- [ ] Beide
- [ ] Andere: ******\_\_\_******

---

## ❓ FRAGE 2: KeybindTextContent Klasse

### Mojang-Mapping (Vorbild):

```java
package net.minecraft.network.chat.contents;
public class KeybindContents {
    private final String name;

    public KeybindContents(String name) { ... }

    // Methode die Supplier.get() aufruft:
    public Component getNestedComponent() { ... }
}
```

### Yarn-Mapping (Minecraft 1.21.4):

**Bitte ausfüllen:**

```
Vollständiger Package-Name: net.minecraft.text._______________

Klassen-Name: _______________

Feld-Name:
- name/key: _______________

Konstruktor-Signatur:
public _______________(String ___) { ... }

Methode die Supplier.get() aufruft:
- Methoden-Name: _______________
- Signatur: _______________
- Ruft auf: Supplier<_______________>.get()
```

**Zusatzfrage:** Wie heißt die Methode in `KeyBinding` die den Namen zurückgibt?

```java
KeyBinding._______________  // z.B. getLocalizedName(), createNameSupplier(), etc.
```

---

## ❓ FRAGE 3: Language Klasse

### Mojang-Mapping (Vorbild):

```java
package net.minecraft.locale;
public abstract class Language {
    public static Language getInstance() { ... }

    public abstract String getOrDefault(String key);
    public abstract String getOrDefault(String key, String defaultValue);
}
```

### Yarn-Mapping (Minecraft 1.21.4):

**Bitte ausfüllen:**

```
Vollständiger Package-Name: net.minecraft.util._______________

Klassen-Name: _______________

Methoden:
- getInstance(): public static _______________ _______________() { ... }
- getOrDefault(String): public abstract String _______________(String ___) { ... }
- getOrDefault(String, String): public abstract String _______________(String ___, String ___) { ... }
```

**Zusatzfrage:** Gibt es eine Methode zum Laden von Translations?

```java
// Methode die InputStream und BiConsumer nimmt:
public static void _______________(InputStream stream, BiConsumer<String, String> consumer) { ... }
```

---

## ❓ FRAGE 4: Language Implementierung (ClientLanguage)

### Mojang-Mapping (Vorbild):

```java
package net.minecraft.client.resources.language;
public class ClientLanguage extends Language {
    private final Map<String, String> storage;

    public static ClientLanguage loadFrom(ResourceManager manager, List<String> definitions, boolean rightToLeft) { ... }
}
```

### Yarn-Mapping (Minecraft 1.21.4):

**Bitte ausfüllen:**

```
Vollständiger Package-Name: net.minecraft.client.resource.language._______________

Klassen-Name: _______________

Feld-Name für Translations-Map:
- storage/translations: private final Map<String, String> _______________;

Methode zum Laden:
- Methoden-Name: public static _______________ _______________(...) { ... }
- Parameter: (ResourceManager ___, List<String> ___, boolean ___)
```

---

## ❓ FRAGE 5: Packet Klasse

### Mojang-Mapping (Vorbild):

```java
package net.minecraft.network.protocol;
public interface Packet<T extends PacketListener> {
    PacketType<? extends Packet<T>> type();
    void handle(T listener);
}
```

### Yarn-Mapping (Minecraft 1.21.4):

**Bitte ausfüllen:**

```
Vollständiger Package-Name: net.minecraft.network.packet._______________

Klassen-/Interface-Name: _______________

Methoden:
- type(): _______________ _______________() { ... }
- handle(): void _______________(T ___) { ... }
```

**Zusatzfrage:** Wie bekommt man den Packet-Namen/ID?

```java
Packet<?> packet = ...;
String name = packet._______________._______________().toString();
// ODER
String name = packet.getClass().getSimpleName(); // Fallback
```

---

## ❓ FRAGE 6: Packet Decoder/Inflater

### Mojang-Mapping (Vorbild):

```java
package net.minecraft.network;
public class PacketDecoder {
    public void decode(...) {
        Packet<?> packet = StreamCodec.decode(buffer);
    }
}
```

### Yarn-Mapping (Minecraft 1.21.4):

**Bitte ausfüllen:**

```
Gibt es eine Klasse "PacketDecoder" oder "PacketInflater"?
- [ ] Ja: net.minecraft.network._______________
- [ ] Nein, es heißt: _______________

Methode die Packets dekodiert:
- Klasse: _______________
- Methode: public void _______________(_______________) { ... }
- Ruft auf: _______________.decode(_______________);
```

**Alternative:** Wenn es keine separate Decoder-Klasse gibt:

```
Wo werden Packets deserialisiert?
- Klasse: _______________
- Methode: _______________
```

---

## ❓ FRAGE 7: Packet Handler

### Mojang-Mapping (Vorbild):

```java
// In 1.21.9+:
package net.minecraft.network;
class PacketProcessor$ListenerAndPacket {
    void handle() {
        packet.handle(listener);
    }
}
```

### Yarn-Mapping (Minecraft 1.21.4):

**Bitte ausfüllen:**

```
Wo wird packet.handle() aufgerufen?
- Klasse: _______________
- Package: net.minecraft.network._______________ ODER net.minecraft.client.network._______________
- Methode: _______________

Ist es eine innere Klasse?
- [ ] Ja: _______________$_______________
- [ ] Nein: _______________

Vollständige Methoden-Signatur:
_______________
```

**Alternative:** Wenn es anders ist:

```
Beschreibe wo/wie Packets verarbeitet werden:
_______________
```

---

## ❓ FRAGE 8: Text/Component Klasse

### Mojang-Mapping (Vorbild):

```java
package net.minecraft.network.chat;
public interface Component {
    static MutableComponent literal(String text) { ... }
    static MutableComponent translatable(String key) { ... }
    String getString();
}
```

### Yarn-Mapping (Minecraft 1.21.4):

**Bitte ausfüllen:**

```
Vollständiger Package-Name: net.minecraft.text._______________

Interface-Name: _______________

Methoden:
- literal(): static _______________ _______________(String ___) { ... }
- translatable(): static _______________ _______________(String ___) { ... }
- getString(): String _______________() { ... }
```

---

## ❓ FRAGE 9: KeyBinding Klasse

### Mojang-Mapping (Vorbild):

```java
package net.minecraft.client;
public class KeyMapping {
    public static Supplier<Component> createNameSupplier(String name) { ... }
}
```

### Yarn-Mapping (Minecraft 1.21.4):

**Bitte ausfüllen:**

```
Vollständiger Package-Name: net.minecraft.client.option._______________

Klassen-Name: _______________

Methode zum Erstellen des Name-Suppliers:
- Methoden-Name: public static Supplier<_______________> _______________(String ___) { ... }
```

---

## ❓ FRAGE 10: Resource/ResourceManager

### Mojang-Mapping (Vorbild):

```java
package net.minecraft.server.packs.resources;
public interface Resource {
    PackResources source();
}
```

### Yarn-Mapping (Minecraft 1.21.4):

**Bitte ausfüllen:**

```
Vollständiger Package-Name: net.minecraft.resource._______________

Interface-Name: _______________

Methode um PackResources zu bekommen:
- Methoden-Name: _______________ _______________() { ... }
```

---

## ❓ FRAGE 11: PackResources Typen

### Mojang-Mapping (Vorbild):

```java
net.minecraft.server.packs.VanillaPackResources
net.minecraft.server.packs.FilePackResources
net.minecraft.server.packs.PathPackResources
net.minecraft.server.packs.CompositePackResources
```

### Yarn-Mapping (Minecraft 1.21.4):

**Bitte ausfüllen:**

```
Package: net.minecraft.resource._______________ ODER net.minecraft.server.packs._______________

Klassen-Namen:
- VanillaPackResources: _______________
- FilePackResources: _______________
- PathPackResources: _______________
- CompositePackResources: _______________
```

---

## ❓ FRAGE 12: MinecraftClient

### Mojang-Mapping (Vorbild):

```java
package net.minecraft.client;
public class Minecraft {
    public static Minecraft getInstance() { ... }
    public boolean hasSingleplayerServer() { ... }
}
```

### Yarn-Mapping (Minecraft 1.21.4):

**Bitte ausfüllen:**

```
Vollständiger Package-Name: net.minecraft.client._______________

Klassen-Name: _______________

Methoden:
- getInstance(): public static _______________ _______________() { ... }
- hasSingleplayerServer(): public boolean _______________() { ... }
- isOnThread(): public boolean _______________() { ... }
```

---

## ✅ ZUSÄTZLICHE INFORMATIONEN

### Minecraft Version:

```
Version: 1.21.4
Yarn Mappings: 1.21.4+build.1
Fabric Loader: _______________
```

### Besonderheiten:

```
Gibt es bekannte Änderungen in 1.21.4 die relevant sein könnten?
_______________
```

---

## 📝 NOTIZEN

Füge hier zusätzliche Informationen hinzu, die hilfreich sein könnten:

```
_______________
```

---

## 🎯 NACH DEM AUSFÜLLEN

1. Speichere diese Datei als `YARN_MAPPINGS_ANSWERS.md`
2. Gib mir die ausgefüllte Datei
3. Ich implementiere dann die vollständige, 100% sichere Lösung

**Vielen Dank für deine Hilfe!** Mit diesen Informationen kann ich eine perfekte Implementierung erstellen.
