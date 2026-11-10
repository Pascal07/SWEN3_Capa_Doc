# OCR-Worker Architektur- & Implementierungs-Dokumentation

Diese Dokumentation beschreibt die Architektur, Konfiguration und Implementierung des **OCR-Workers (`OcrWorker`)** im Projekt **CapaDoc** unter Verwendung von **Ghostscript (`gs`)** und **Tesseract OCR (`tesseract`)**, angebunden an **RabbitMQ**, **RustFS** und **PostgreSQL**.

---

## Inhaltsverzeichnis
1. [Architekturüberblick](#1-architekturüberblick)
2. [Mermaid Ablauf- und Architekturdiagramme](#2-mermaid-ablauf--und-architekturdiagramme)
   - [Komponenten- und Queue-Topologie](#komponenten--und-queue-topologie)
   - [End-to-End OCR-Sequenzdiagramm](#end-to-end-ocr-sequenzdiagramm)
   - [Lokale Ghostscript- & Tesseract-Pipeline im Temp-Verzeichnis](#lokale-ghostscript---tesseract-pipeline-im-temp-verzeichnis)
   - [Fehlerbehandlungs-, Retry- & Dead-Letter-Queue-Fluss](#fehlerbehandlungs--retry---dead-letter-queue-fluss)
3. [Containerisierung & Infrastruktur (`Dockerfile` & `docker-compose.yaml`)](#3-containerisierung--infrastruktur-dockerfile--docker-composeyaml)
   - [Multi-Stage Dockerfile (`eclipse-temurin:25-jre-noble`)](#multi-stage-dockerfile-eclipse-temurin25-jre-noble)
   - [Docker Compose Integration (`ocr-worker`)](#docker-compose-integration-ocr-worker)
4. [Konfiguration (`OcrProperties` & `RabbitMqConfig`)](#4-konfiguration-ocrproperties--rabbitmqconfig)
   - [Typsichere OCR-Properties (`ocr.*`)](#typsichere-ocr-properties-ocr)
   - [Exchanges, Queues und Nachrichtenformate](#exchanges-queues-und-nachrichtenformate)
5. [Implementierungsdetails (`OcrWorker`)](#5-implementierungsdetails-ocrworker)
   - [`OcrService` & `TesseractOcrService`](#ocrservice--tesseractocrservice)
   - [`CommandRunner` & `ProcessCommandRunner`](#commandrunner--processcommandrunner)
   - [`OcrMessageListener`, Idempotenz & `DocumentStatusUpdater`](#ocrmessagelistener-idempotenz--documentstatusupdater)
   - [Fehlerklassen & Ressourcenlimits](#fehlerklassen--ressourcenlimits)
6. [Tests & Qualitätssicherung](#6-tests--qualitätssicherung)

---

## 1. Architekturüberblick

Der **OCR-Worker (`OcrWorker`)** entkoppelt die rechenintensive Texterkennung vollständig vom REST-Server (`CapaDocAPI`). Alle OCR-Schritte laufen lokal im Container ohne externe Cloud-APIs:

1. **Nachrichtenkonsum (`ocr.queue`):** Nach einem PDF-Upload sendet `CapaDocAPI` (nach erfolgreichem DB-Commit) eine `OcrMessage` (`documentId`, `storageKey`, `filename`, `contentType`, `uploadedAt`) an RabbitMQ.
2. **Idempotenz-Prüfung (`PostgreSQL`):** `OcrMessageListener` prüft vorab den aktuellen `ocr_status` des Dokuments. Ist das Dokument bereits `DONE`, wird die Nachricht idempotent übersprungen.
3. **PDF-Download (`RustFS`):** Über `StorageService.download(storageKey)` lädt der Worker die PDF-Binärdaten aus dem S3-kompatiblen Bucket `documents` (`http://rustfs:9000` im Docker-Netz).
4. **Seiten-Rasterisierung (`Ghostscript`):** In einem isolierten temporären Verzeichnis (`Files.createTempDirectory`) wandelt Ghostscript (`gs`) jede PDF-Seite mit konfigurierbarer Auflösung (Default `300 DPI`) in ein 24-Bit-PNG-Bild (`page-001.png`, `page-002.png`, ...) um.
5. **Texterkennung (`Tesseract OCR`):** Für jedes erzeugte PNG wird `tesseract <page.png> stdout -l deu+eng` in exakter Seitenreihenfolge ausgeführt und der erkannte Text seitenweise zusammengefügt. Das temporäre Verzeichnis wird im `finally`-Block garantiert rekursiv gelöscht.
6. **Status-Update & Weiterleitung (`indexing.queue`):** Bei Erfolg setzt der Worker `ocr_status = DONE` in PostgreSQL und publiziert eine `IndexingMessage` (`documentId`, `storageKey`, `filename`, `extractedText`, `processedAt`) an `indexing.exchange` -> `indexing.queue`.

---

## 2. Mermaid Ablauf- und Architekturdiagramme

### Komponenten- und Queue-Topologie

```mermaid
flowchart LR
    subgraph API_Layer["REST Backend"]
        API["capadoc-api\n(Spring Boot :8081)"]
    end

    subgraph Storage_Layer["Persistenz & Object Storage"]
        RustFS[("capadoc-rustfs\nBucket: documents (:9000)")]
        DB[("capadoc-db\nPostgreSQL (:5432)")]
    end

    subgraph Broker["capadoc-rabbitmq (:5672 / :15672)"]
        OcrEx["ocr.exchange"]
        OcrQ[["ocr.queue"]]
        DlxEx["ocr.dlx"]
        DlqQ[["ocr.dlq"]]
        IdxEx["indexing.exchange"]
        IdxQ[["indexing.queue"]]
    end

    subgraph Worker_Container["capadoc-ocr-worker (Ubuntu + Java 25)"]
        Listener["OcrMessageListener"]
        OcrSvc["TesseractOcrService"]
        GS["Ghostscript (gs)"]
        Tess["Tesseract OCR (deu+eng)"]
    end

    API -->|"1. PutObject (documents/<id>.pdf)"| RustFS
    API -->|"2. INSERT (ocr_status=PENDING)"| DB
    API -->|"3. OcrMessage"| OcrEx
    OcrEx -->|"ocr.routingkey"| OcrQ
    OcrQ -->|"4. @RabbitListener"| Listener
    Listener -->|"5. GetObject"| RustFS
    Listener -->|"6. extractText(byte[])"| OcrSvc
    OcrSvc -->|"PNG-Rasterisierung"| GS
    OcrSvc -->|"Texterkennung"| Tess
    Listener -->|"7. UPDATE ocr_status (DONE / FAILED)"| DB
    Listener -->|"8. IndexingMessage"| IdxEx
    IdxEx -->|"indexing.routingkey"| IdxQ
    Listener -.->|"Nach erschöpften Retries"| DlxEx
    DlxEx -->|"ocr.dlq.routingkey"| DlqQ
```

---

### End-to-End OCR-Sequenzdiagramm

```mermaid
sequenceDiagram
    autonumber
    participant RMQ as "RabbitMQ (ocr.queue)"
    participant Listener as "OcrMessageListener"
    participant Status as "DatabaseDocumentStatusUpdater"
    participant Storage as "RustFsStorageService"
    participant OCR as "TesseractOcrService"
    participant Runner as "ProcessCommandRunner"
    participant Pub as "RabbitMqIndexingMessagePublisher"
    participant IdxQ as "RabbitMQ (indexing.queue)"

    RMQ->>Listener: handleOcrMessage(OcrMessage: documentId, storageKey, filename)
    Listener->>Status: getOcrStatus(documentId)
    
    alt ocrStatus == DONE (Duplikat)
        Status-->>Listener: Optional.of(DONE)
        Note over Listener: Überspringt Verarbeitung idempotent
    else ocrStatus != DONE
        Status-->>Listener: Optional.of(PENDING)
        Listener->>Storage: download(storageKey)
        Storage-->>Listener: byte[] pdfBytes
        
        Listener->>OCR: extractText(pdfBytes)
        Note over OCR: 1. Validiere PDF (%PDF Header & Größe)<br/>2. Erstelle Temp-Ordner (/tmp/capadoc-ocr/ocr-job-*)
        OCR->>Runner: run([gs, -dNOPAUSE, -dBATCH, -dSAFER, -sDEVICE=png16m, -r300, -sOutputFile=page-%03d.png, input.pdf])
        Runner-->>OCR: CommandResult(exitCode=0)
        
        loop Für jede Seite page-001.png .. page-NNN.png (sortiert)
            OCR->>Runner: run([tesseract, page-XXX.png, stdout, -l, deu+eng])
            Runner-->>OCR: CommandResult(exitCode=0, stdout="Seiten-Text")
        end
        
        Note over OCR: finally: Lösche Temp-Ordner rekursiv
        OCR-->>Listener: String extractedText
        
        Listener->>Status: updateOcrStatus(documentId, DONE)
        Listener->>Pub: publishIndexingMessage(IndexingMessage)
        Pub->>IdxQ: convertAndSend("indexing.exchange", "indexing.routingkey", IndexingMessage)
    end
```

---

### Lokale Ghostscript- & Tesseract-Pipeline im Temp-Verzeichnis

```mermaid
flowchart TD
    PDF["byte[] pdfBytes aus RustFS"] --> Validate{"Validierung:\n1. Nicht leer & <= maxPdfSizeBytes\n2. Beginnt mit %PDF"}
    Validate -- "Ungültig" --> PermErr["Wirft OcrPermanentException"]
    Validate -- "Gültig" --> TempDir["Files.createTempDirectory('ocr-job-')\nSchreibe input.pdf"]
    TempDir --> GS["Ghostscript (ProcessBuilder):\ngs -dNOPAUSE -dBATCH -dSAFER\n-sDEVICE=png16m -r300\n-sOutputFile=page-%03d.png input.pdf"]
    GS --> CheckPages{"Seitenanzahl prüfen:\n1 <= pages <= maxPages"}
    CheckPages -- "Überschritten / 0 Seiten" --> CleanupErr["finally: deleteDirectoryRecursively(workDir)"]
    CleanupErr --> PermErr
    CheckPages -- "OK (sortiert: page-001.png ...)" --> Loop["Für jede PNG-Seite in Reihenfolge:\ntesseract <page.png> stdout -l deu+eng"]
    Loop --> Join["Seiten-Texte mit '\\n\\n' verbinden\n(leere Seiten = leerer String, kein Fehler)"]
    Join --> CleanupOK["finally: deleteDirectoryRecursively(workDir)"]
    CleanupOK --> Result["Rückgabe: String extractedText"]
```

---

### Fehlerbehandlungs-, Retry- & Dead-Letter-Queue-Fluss

```mermaid
flowchart TD
    Start["Nachricht aus ocr.queue empfangen"] --> TryBlock["Versuch (attempt = 1 .. maxRetries)"]
    TryBlock --> Exec["RustFS Download + Ghostscript + Tesseract"]
    
    Exec -- "Erfolg" --> Done["1. ocr_status = DONE\n2. Sende IndexingMessage an indexing.queue"]
    
    Exec -- "StorageObjectNotFoundException\noder OcrPermanentException\n(404 in RustFS, korruptes/passwortgeschütztes PDF,\nmaxPages überschritten, Binary fehlt)" --> Perm["Kein Retry!\n1. Logge Fehler mit documentId (ohne Dokumentinhalt)\n2. Setze ocr_status = FAILED"]
    
    Exec -- "OcrTransientException oder StorageException\n(Prozess-Timeout, temporärer S3-/Netzwerkfehler)" --> CheckRetry{"attempt < maxRetries?"}
    
    CheckRetry -- "Ja" --> Backoff["Exponentieller Backoff:\nsleep(retryBackoffMs * 2^(attempt-1))"]
    Backoff --> TryBlock
    
    CheckRetry -- "Nein (Retries erschöpft)" --> Exhausted["1. Logge Fehler mit documentId\n2. Setze ocr_status = FAILED\n3. Sende OcrMessage an ocr.dlx -> ocr.dlq"]
```

---

## 3. Containerisierung & Infrastruktur (`Dockerfile` & `docker-compose.yaml`)

### Multi-Stage Dockerfile (`eclipse-temurin:25-jre-noble`)

In [`OcrWorker/Dockerfile`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/Dockerfile) wird ein zweistufiger Build verwendet:
* **Build-Stage (`eclipse-temurin:25-jdk`):** Kompiliert das Maven-Modul und erzeugt das ausführbare Spring-Boot-JAR.
* **Runtime-Stage (`eclipse-temurin:25-jre-noble`):** Basiert auf Ubuntu Noble und installiert die nativen OCR-Werkzeuge:
  ```dockerfile
  RUN apt-get update && \
      apt-get install -y --no-install-recommends \
          tesseract-ocr \
          tesseract-ocr-deu \
          tesseract-ocr-eng \
          ghostscript && \
      rm -rf /var/lib/apt/lists/*
  ```
* Im laufenden Container sind damit `tesseract 5.3.4` (Sprachpakete `deu`, `eng`, `osd`) und `gs 10.02.1` direkt im `PATH` verfügbar.

### Docker Compose Integration (`ocr-worker`)

In [`docker-compose.yaml`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/docker-compose.yaml) ist der Service `ocr-worker` wie folgt eingebunden:
* **`depends_on`:** Wartet auf `db`, `rabbitmq` und `rustfs` jeweils mit `condition: service_healthy`.
* **Umgebungsvariablen:** Teilt sich die Datenbank-, RabbitMQ- und RustFS-Credentials mit `api` aus der `.env`-Datei (`RUSTFS_ENDPOINT: http://rustfs:9000`, `OCR_LANGUAGES: ${OCR_LANGUAGES:-deu+eng}`, `OCR_DPI: ${OCR_DPI:-300}`).

---

## 4. Konfiguration (`OcrProperties` & `RabbitMqConfig`)

### Typsichere OCR-Properties (`ocr.*`)

Die Klasse [`OcrProperties`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/main/java/at/capadoc/ocrworker/config/OcrProperties.java) (`@ConfigurationProperties(prefix = "ocr")`) bindet alle OCR-Einstellungen aus [`OcrWorker/src/main/resources/application.yml`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/main/resources/application.yml) bzw. Umgebungsvariablen:

| Property | ENV-Variable | Default | Beschreibung |
| :--- | :--- | :--- | :--- |
| `ocr.languages` | `OCR_LANGUAGES` | `deu+eng` | Tesseract-Sprachkombination (`-l`) |
| `ocr.dpi` | `OCR_DPI` | `300` | Auflösung für Ghostscript-Rendering (`-r<dpi>`) |
| `ocr.timeout-per-page` | `OCR_TIMEOUT_PER_PAGE` | `60s` | Maximale Laufzeit pro Prozessaufruf |
| `ocr.tesseract-command` | `OCR_TESSERACT_COMMAND` | `tesseract` | Befehl/Pfad zur Tesseract-Binary |
| `ocr.ghostscript-command` | `OCR_GHOSTSCRIPT_COMMAND` | `gs` | Befehl/Pfad zur Ghostscript-Binary |
| `ocr.temp-dir` | `OCR_TEMP_DIR` | `/tmp/capadoc-ocr` | Basisverzeichnis für temporäre Job-Ordner |
| `ocr.max-pages` | `OCR_MAX_PAGES` | `200` | Maximale Seitenanzahl pro PDF |
| `ocr.max-pdf-size-bytes` | `OCR_MAX_PDF_SIZE_BYTES` | `52428800` (50 MB) | Maximale Dateigröße pro PDF |
| `ocr.max-retries` | `OCR_MAX_RETRIES` | `3` | Maximale Versuche bei transienten Fehlern |
| `ocr.retry-backoff-ms` | `OCR_RETRY_BACKOFF_MS` | `500` | Initiale Wartezeit (ms) für exponentiellen Backoff |

### Exchanges, Queues und Nachrichtenformate

In [`RabbitMqConfig`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/main/java/at/capadoc/ocrworker/config/RabbitMqConfig.java) sind alle benötigten Exchanges, Queues und Bindings deklariert:

* **Eingangs-Nachricht ([`OcrMessage`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/main/java/at/capadoc/ocrworker/model/OcrMessage.java)) aus `ocr.queue`:**
  ```json
  {
    "documentId": 1,
    "storageKey": "documents/1.pdf",
    "filename": "Rechnung.pdf",
    "contentType": "application/pdf",
    "uploadedAt": "2026-11-11T00:10:00"
  }
  ```
* **Ausgangs-Nachricht ([`IndexingMessage`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/main/java/at/capadoc/ocrworker/model/IndexingMessage.java)) an `indexing.queue`:**
  ```json
  {
    "documentId": 1,
    "storageKey": "documents/1.pdf",
    "filename": "Rechnung.pdf",
    "extractedText": "Erkannter Volltext aus allen Seiten...",
    "processedAt": "2026-11-11T00:10:02"
  }
  ```

---

## 5. Implementierungsdetails (`OcrWorker`)

### `OcrService` & `TesseractOcrService`
* **[`OcrService`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/main/java/at/capadoc/ocrworker/service/OcrService.java):** Definiert `String extractText(byte[] pdf)`.
* **[`TesseractOcrService`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/main/java/at/capadoc/ocrworker/service/TesseractOcrService.java):**
  * Prüft vorab PDF-Größe und `%PDF`-Header.
  * Legt pro Aufruf via `Files.createTempDirectory(..., "ocr-job-")` ein eigenes Arbeitsverzeichnis an.
  * Führt Ghostscript aus, sortiert die erzeugten `page-XXX.png`-Dateien lexikographisch nach Dateinamen, prüft `maxPages` und führt anschließend Tesseract pro Seite aus.
  * Seiten ohne erkennbaren Text (z. B. leere Seiten oder reine Trennblätter) liefern einen leeren String ohne Fehler.
  * Löscht das temporäre Arbeitsverzeichnis im `finally`-Block vollständig rekursiv (`deleteDirectoryRecursively`).

### `CommandRunner` & `ProcessCommandRunner`
* **[`CommandRunner`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/main/java/at/capadoc/ocrworker/service/CommandRunner.java) & [`ProcessCommandRunner`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/main/java/at/capadoc/ocrworker/service/ProcessCommandRunner.java):**
  * Kapselt `ProcessBuilder` hinter einem leicht mockbaren Interface.
  * Übergibt Argumente ausschließlich als `List<String>` (kein `sh -c`, Schutz vor Command Injection).
  * Liest `stdout` und `stderr` parallel in separaten Daemon-Threads aus, um Buffer-Deadlocks bei großen Textmengen zu verhindern.
  * Überwacht die Laufzeit via `process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)` und beendet hängende Prozesse zuverlässig mittels `process.destroyForcibly()`.

### `OcrMessageListener`, Idempotenz & `DocumentStatusUpdater`
* **[`OcrMessageListener`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/main/java/at/capadoc/ocrworker/service/OcrMessageListener.java):** Konsumiert Nachrichten aus `ocr.queue`, koordiniert Download, OCR-Extraktion, Status-Update und Weiterleitung an `indexing.queue` bzw. `ocr.dlq`.
* **[`DocumentStatusUpdater`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/main/java/at/capadoc/ocrworker/service/DocumentStatusUpdater.java) & [`DatabaseDocumentStatusUpdater`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/main/java/at/capadoc/ocrworker/service/DatabaseDocumentStatusUpdater.java):** Liest und aktualisiert `ocr_status` (`PENDING`, `DONE`, `FAILED`) in der Tabelle `documents`.

### Fehlerklassen & Ressourcenlimits
* **[`OcrPermanentException`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/main/java/at/capadoc/ocrworker/exception/OcrPermanentException.java) & [`StorageObjectNotFoundException`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/main/java/at/capadoc/ocrworker/exception/StorageObjectNotFoundException.java):** Kennzeichnen nicht wiederholbare Fehler (fehlendes Objekt in RustFS, ungültiges/passwortgeschütztes PDF, Exit-Code != 0, fehlendes Binary, Überschreitung von `maxPages` oder `maxPdfSizeBytes`).
* **[`OcrTransientException`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/main/java/at/capadoc/ocrworker/exception/OcrTransientException.java) & [`StorageException`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/main/java/at/capadoc/ocrworker/exception/StorageException.java):** Kennzeichnen vorübergehende Fehler (Timeouts, temporäre Netzwerk-/S3-Probleme), die per Backoff wiederholt werden.

---

## 6. Tests & Qualitätssicherung

* **[`TesseractOcrServiceTest`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/test/java/at/capadoc/ocrworker/service/TesseractOcrServiceTest.java):** Unit-Tests mit gemocktem `CommandRunner` für Einzelseite, Mehrseitigkeit in korrekter Reihenfolge, leere Seite, Ghostscript-/Tesseract-Exit-Code != 0, Timeout, Seitenlimit-Überschreitung, ungültigen PDF-Header und garantiertes Aufräumen des Temp-Verzeichnisses.
* **[`OcrMessageListenerTest`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/test/java/at/capadoc/ocrworker/service/OcrMessageListenerTest.java):** Unit-Tests mit gemocktem `StorageService`, `OcrService`, `DocumentStatusUpdater` und `IndexingMessagePublisher` für Erfolgsfall, fehlendes RustFS-Objekt, permanenten OCR-Fehler, transienten Fehler mit Retry + DLQ sowie Idempotenz bei bereits verarbeitetem Dokument.
* **[`TesseractOcrIntegrationTest`](file:///home/pascal/Downloads/SWEN3_Capa_Doc/SWEN3_Capa_Doc/OcrWorker/src/test/java/at/capadoc/ocrworker/service/TesseractOcrIntegrationTest.java):** Integrationstest mit echtem Ghostscript und Tesseract gegen ein generiertes `"Hello World"`-PDF (wird per JUnit `Assumptions.assumeTrue(...)` übersprungen, falls lokal keine passenden Binaries/Sprachdaten installiert sind).
