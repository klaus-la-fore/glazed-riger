# Yarn Mappings Antworten für Minecraft 1.21.4

## 🎯 QUELLE

Alle Antworten basieren ausschließlich auf dem offiziellen Yarn 1.21.4+build.1 Javadoc:
`https://maven.fabricmc.net/docs/yarn-1.21.4+build.1/`

---

## ✅ ANTWORT 1: TranslatableTextContent Klasse

### Mojang → Yarn Mapping:

```
Vollständiger Package-Name: net.minecraft.text
                            (KEIN Sub-Package ".contents" – direkt in net.minecraft.text)

Klassen-Name: TranslatableTextContent

Feld-Namen:
- key:      private final String key
- fallback: private final @Nullable String fallback
- args:     private final Object[] args

Konstruktor-Signatur:
public TranslatableTextContent(String key, @Nullable String fallback, Object[] args) { ... }

Interne Methode die Language.get() aufruft:
- Methoden-Name: updateTranslations  (PRIVAT – intern aufgerufen durch visit())
- Signatur:      private void updateTranslations() { ... }

Öffentliche visit-Methoden (lösen updateTranslations() aus):
- public <T> Optional<T> visit(StringVisitable.Visitor<T> visitor)
- public <T> Optional<T> visit(StringVisitable.StyledVisitor<T> visitor, Style style)
```

**Zusatzfrage:** Welche `Language.get()`-Überladung wird aufgerufen?

- [ ] Nur `Language.get(String)`
- [x] Nur `Language.get(String, String)`
- [ ] Beide
- [ ] Andere

> **Erklärung:** `updateTranslations()` ruft `Language.getInstance().get(key, fallback != null ? fallback : key)` auf –
> also immer die Zwei-Argument-Variante `get(String key, String fallback)`.
> Die Einargument-Variante `get(String)` ist nur ein Wrapper der intern `get(key, key)` aufruft.

---

## ✅ ANTWORT 2: KeybindTextContent Klasse

### Yarn-Mapping (Minecraft 1.21.4):

```
Vollständiger Package-Name: net.minecraft.text

Klassen-Name: KeybindTextContent

Feld-Name:
- name/key: private final String key
- (gecachter Supplier): private @Nullable Supplier<Text> translated

Konstruktor-Signatur:
public KeybindTextContent(String key) { ... }

Methode die Supplier.get() aufruft:
- Methoden-Name: getTranslated  (PRIVAT)
- Signatur:      private Text getTranslated() { ... }
- Ruft auf:      Supplier<Text>.get()
  (d.h. das Feld 'translated', das ein Supplier<Text> ist, wird via .get() aufgerufen)
```

**Zusatzfrage:** Wie heißt die Methode in `KeyBinding` die den Name-Supplier zurückgibt?

```java
// Statische Methode die einen Supplier<Text> für einen Key-ID erstellt:
KeyBinding.getLocalizedName(String id)
// Vollständige Signatur:
public static Supplier<Text> getLocalizedName(String id) { ... }

// Für gebundenen Key-Text direkt (Instanzmethode):
keyBindingInstance.getBoundKeyLocalizedText()  // gibt Text zurück (kein Supplier)
```

---

## ✅ ANTWORT 3: Language Klasse

### Yarn-Mapping (Minecraft 1.21.4):

```
Vollständiger Package-Name: net.minecraft.util
                            (KEIN Sub-Package – direkt in net.minecraft.util)

Klassen-Name: Language  (abstrakte Klasse, kein Interface)

Methoden:
- getInstance():
    public static Language getInstance()

- get(String) – KONKRETE Methode (nicht abstrakt):
    public String get(String key)
    [Intern ruft diese get(key, key) auf – key ist also gleichzeitig Fallback]

- get(String, String) – ABSTRAKTE Methode:
    public abstract String get(String key, String fallback)
```

**Zusatzfrage:** Methode zum Laden von Translations:

```java
// Lädt eine JSON-Sprachdatei:
public static void load(InputStream inputStream, BiConsumer<String, String> entryConsumer) { ... }

// Interne Hilfsmethode (privat, mit Pfad):
private static void load(BiConsumer<String, String> entryConsumer, String path) { ... }
```

---

## ✅ ANTWORT 4: Language-Implementierung (Yarn: TranslationStorage)

> ⚠️ **WICHTIG:** In Yarn heißt die Client-Language-Implementierung **NICHT** `ClientLanguage`,
> sondern `TranslationStorage`!

### Yarn-Mapping (Minecraft 1.21.4):

```
Vollständiger Package-Name: net.minecraft.client.resource.language

Klassen-Name: TranslationStorage
              (erweitert net.minecraft.util.Language)

Feld-Name für Translations-Map:
    private final Map<String, String> translations

Methode zum Laden:
    public static TranslationStorage load(
        ResourceManager resourceManager,
        List<String> definitions,
        boolean rightToLeft
    )
```

---

## ✅ ANTWORT 5: Packet Interface

### Yarn-Mapping (Minecraft 1.21.4):

```
Vollständiger Package-Name: net.minecraft.network.packet

Interface-Name: Packet<T extends PacketListener>
                (T ist net.minecraft.network.listener.PacketListener)

Methoden:
- getPacketType():
    PacketType<? extends Packet<T>> getPacketType()

- apply() [NICHT handle()! In Yarn heißt diese Methode 'apply']:
    void apply(T listener)
```

**Zusatzfrage:** Wie bekommt man den Packet-Namen/ID?

```java
Packet<?> packet = ...;

// Option 1 – über PacketType:
String name = packet.getPacketType().toString();

// Option 2 – Klassenname als Fallback:
String name = packet.getClass().getSimpleName();

// Vollständiger Hinweis:
// PacketType<T extends Packet<?>> liegt in net.minecraft.network.packet.PacketType
```

---

## ✅ ANTWORT 6: Packet Decoder/Inflater

### Yarn-Mapping (Minecraft 1.21.4):

```
Gibt es eine Klasse "PacketDecoder"?
- [x] Ja, aber sie heißt anders und liegt in einem Sub-Package:
      net.minecraft.network.handler.DecoderHandler<T extends PacketListener>

Klasse: DecoderHandler<T>
Package: net.minecraft.network.handler

Methode die Packets dekodiert:
  protected void decode(
      ChannelHandlerContext context,
      ByteBuf buf,
      List<Object> objects
  ) throws Exception

  [Überschreibt ByteToMessageDecoder.decode()]

Kompressionshandler (für komprimierte Verbindungen):
  net.minecraft.network.handler.PacketInflater
  (ebenfalls im Package net.minecraft.network.handler)
```

---

## ✅ ANTWORT 7: Packet Handler (wo wird apply() aufgerufen?)

### Yarn-Mapping (Minecraft 1.21.4):

```
Wo wird packet.apply(listener) aufgerufen?
  Klasse:   net.minecraft.network.ClientConnection
  Package:  net.minecraft.network

Ist es eine innere Klasse?
  - [ ] Ja
  - [x] Nein – direkt in ClientConnection

Relevante Methoden in ClientConnection:
  // Eingehende Nachrichten (Netty-Thread):
  protected void channelRead0(ChannelHandlerContext context, Packet<?> packet)

  // Dispatching zum Game-Thread:
  private static <T extends PacketListener> void handlePacket(Packet<T> packet, PacketListener listener)
  [Diese Methode ruft intern packet.apply(listener) auf]
```

> **Hinweis zu NetworkThreadUtils:**
> Die Hilfsmethode `NetworkThreadUtils.forceMainThread()` in `net.minecraft.network.NetworkThreadUtils`
> stellt sicher, dass bestimmte Packet-Handler auf dem Haupt-Thread laufen
> (wirft `OffThreadException` wenn nicht auf dem richtigen Thread).

---

## ✅ ANTWORT 8: Text Interface

### Yarn-Mapping (Minecraft 1.21.4):

```
Vollständiger Package-Name: net.minecraft.text

Interface-Name: Text
                (erweitert StringVisitable und com.mojang.brigadier.Message)

Methoden:
- literal():
    static MutableText literal(String string)

- translatable() – ohne Args:
    static MutableText translatable(String key)

- translatable() – mit Args:
    static MutableText translatable(String key, Object[] args)

- translatableWithFallback():
    static MutableText translatableWithFallback(String key, @Nullable String fallback)
    static MutableText translatableWithFallback(String key, @Nullable String fallback, Object[] args)

- getString():
    default String getString()

- keybind():
    static MutableText keybind(String string)
```

---

## ✅ ANTWORT 9: KeyBinding Klasse

### Yarn-Mapping (Minecraft 1.21.4):

```
Vollständiger Package-Name: net.minecraft.client.option

Klassen-Name: KeyBinding

Methode zum Erstellen des Name-Suppliers (statisch):
    public static Supplier<Text> getLocalizedName(String id) { ... }
    [Rückgabetyp: Supplier<net.minecraft.text.Text>]

Weitere nützliche Methoden:
    public String getTranslationKey()            // Gibt den Translation-Key zurück
    public Text getBoundKeyLocalizedText()       // Gibt lokalisierten Text für den gebundenen Key
    public String getBoundKeyTranslationKey()    // Translation-Key des gebundenen Keys
    public boolean isPressed()                   // Ob die Taste gehalten wird
```

---

## ✅ ANTWORT 10: Resource Klasse

### Yarn-Mapping (Minecraft 1.21.4):

> ⚠️ **WICHTIG:** In Yarn 1.21.4 ist `Resource` eine **Klasse** (kein Interface)!

```
Vollständiger Package-Name: net.minecraft.resource

Klassen-Name: Resource  (public class, kein Interface)

Methode um ResourcePack zu bekommen:
    public ResourcePack getPack()
    [Rückgabetyp: net.minecraft.resource.ResourcePack (Interface)]

Weitere Methoden:
    public InputStream getInputStream()
    public String getPackId()
    public ResourceMetadata getMetadata()
```

---

## ✅ ANTWORT 11: ResourcePack Typen

> ⚠️ **WICHTIG:** In Yarn heißt das Interface `ResourcePack` (nicht `PackResources`).
> Alle Implementierungen liegen im Package `net.minecraft.resource`.

### Yarn-Mapping (Minecraft 1.21.4):

```
Package: net.minecraft.resource

Interface: ResourcePack
           (net.minecraft.resource.ResourcePack)

Implementierungen (Vergleich Mojang → Yarn):

- VanillaPackResources    → DefaultResourcePack
                             net.minecraft.resource.DefaultResourcePack

- FilePackResources       → ZipResourcePack
                             net.minecraft.resource.ZipResourcePack

- PathPackResources       → DirectoryResourcePack
                             net.minecraft.resource.DirectoryResourcePack

- CompositePackResources  → NICHT VORHANDEN in Yarn 1.21.4
                             Nächste Alternative: OverlayResourcePack
                             net.minecraft.resource.OverlayResourcePack
                             (oder AbstractFileResourcePack als Basis)

Alle bekannten ResourcePack-Implementierungen in 1.21.4:
  - AbstractFileResourcePack  (abstrakte Basisklasse für Datei-basierte Packs)
  - DefaultResourcePack       (Vanilla-Pack / eingebettete Ressourcen)
  - DirectoryResourcePack     (Ordner-basiertes Pack)
  - OverlayResourcePack       (Overlay über anderem Pack)
  - ZipResourcePack           (ZIP-Datei-basiertes Pack)
```

---

## ✅ ANTWORT 12: MinecraftClient

### Yarn-Mapping (Minecraft 1.21.4):

```
Vollständiger Package-Name: net.minecraft.client

Klassen-Name: MinecraftClient

Methoden:
- getInstance():
    public static MinecraftClient getInstance()

- hasSingleplayerServer() → IN YARN HEISST DAS ANDERS!
  Mojang: hasSingleplayerServer()
  Yarn:   isIntegratedServerRunning()
    public boolean isIntegratedServerRunning()
  [Prüft ob der integrierte Server läuft]

  Verwandte Methoden:
    public boolean isInSingleplayer()   // Ob im Singleplayer-Modus
    public @Nullable IntegratedServer getServer()  // Holt den integrierten Server (oder null)
    public boolean isConnectedToLocalServer()       // Ob mit lokalem Server verbunden

- isOnThread():
    public boolean isOnThread()
    [Geerbt von net.minecraft.util.thread.ThreadExecutor]
```

---

## ✅ ZUSÄTZLICHE INFORMATIONEN

### Minecraft Version:

```
Version:       1.21.4
Yarn Mappings: 1.21.4+build.1
Javadoc-URL:   https://maven.fabricmc.net/docs/yarn-1.21.4+build.1/
```

### Wichtige Besonderheiten in 1.21.4 (Yarn-spezifisch):

```
1. PACKET-METHODE: Heißt in Yarn 'apply(T listener)' – NICHT 'handle(T listener)'
   In Mojang-Mappings heißt sie 'handle', in Yarn 'apply'.

2. CLIENT-LANGUAGE: Heißt in Yarn 'TranslationStorage', NICHT 'ClientLanguage'
   Vollständiger Name: net.minecraft.client.resource.language.TranslationStorage

3. RESOURCE: Ist in Yarn 1.21.4 eine Klasse, kein Interface.
   Methode: getPack() (nicht source())

4. DECODER: Heißt 'DecoderHandler', liegt in net.minecraft.network.handler
   (nicht net.minecraft.network.PacketDecoder)

5. LANGUAGE: Liegt in net.minecraft.util.Language
   (nicht net.minecraft.locale.Language wie in Mojang)

6. hasSingleplayerServer(): Heißt in Yarn 'isIntegratedServerRunning()'

7. PackResources: In Yarn heißt das Interface 'ResourcePack'
   Alle Implementierungen liegen in net.minecraft.resource

8. TranslatableTextContent: Im Package net.minecraft.text
   (KEIN Sub-Package .contents wie in Mojang)

9. KeybindTextContent: Im Package net.minecraft.text
   (KEIN Sub-Package .contents wie in Mojang)
```

---

## 📝 SCHNELL-REFERENZ TABELLE

| Mojang Name | Yarn 1.21.4 Name | Package (Yarn) |
|---|---|---|
| `TranslatableContents` | `TranslatableTextContent` | `net.minecraft.text` |
| `KeybindContents` | `KeybindTextContent` | `net.minecraft.text` |
| `Language` | `Language` | `net.minecraft.util` |
| `ClientLanguage` | `TranslationStorage` | `net.minecraft.client.resource.language` |
| `Component` / `MutableComponent` | `Text` / `MutableText` | `net.minecraft.text` |
| `Packet<T>` | `Packet<T>` | `net.minecraft.network.packet` |
| `PacketListener` | `PacketListener` | `net.minecraft.network.listener` |
| `PacketDecoder` | `DecoderHandler<T>` | `net.minecraft.network.handler` |
| `PacketInflater` | `PacketInflater` | `net.minecraft.network.handler` |
| `Minecraft` | `MinecraftClient` | `net.minecraft.client` |
| `KeyMapping` | `KeyBinding` | `net.minecraft.client.option` |
| `PackResources` | `ResourcePack` | `net.minecraft.resource` |
| `VanillaPackResources` | `DefaultResourcePack` | `net.minecraft.resource` |
| `FilePackResources` | `ZipResourcePack` | `net.minecraft.resource` |
| `PathPackResources` | `DirectoryResourcePack` | `net.minecraft.resource` |
| `Resource.source()` | `Resource.getPack()` | `net.minecraft.resource` |
| `Packet.handle()` | `Packet.apply()` | `net.minecraft.network.packet` |
| `hasSingleplayerServer()` | `isIntegratedServerRunning()` | `MinecraftClient` |
| `Component.literal()` | `Text.literal()` | `net.minecraft.text` |
| `Component.translatable()` | `Text.translatable()` | `net.minecraft.text` |
