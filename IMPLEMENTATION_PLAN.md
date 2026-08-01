# Implementacioni Plan: P2P File Sharing Aplikacija
**Predmet:** Računarske Mreže i Tehnologije
**Arhitektura:** Hibridni P2P (centralni Tracker + direktan P2P transfer)
**Tehnologije:** Čist Java (bez Spring/Spring Boot, bez ijednog frameworka) + React

---

## 1. Arhitektura sistema (pregled)

```
┌─────────────────────┐         REST (HTTP/JSON)          ┌─────────────────────┐
│      TRACKER        │◄──────────────────────────────────│     PEER NODE A     │
│  (čist Java,         │   register / announce / search /  │  ┌───────────────┐  │
│   com.sun.net.       │   heartbeat / peers-for-file      │  │ TCP Server    │  │
│   httpserver)        │◄──────────────────────────────────│  │ port 9001     │  │
│   port 8080          │                                   │  └───────┬───────┘  │
│  In-memory registar   │                                   │  Lokalni REST │:7001│
│  ConcurrentHashMap    │                                   └───────────┼─────────┘
└─────────────────────┘                                                │
                                              direktna TCP konekcija    │ (fajl NIKAD
                                              (tok bajtova fajla)       │  ne ide kroz
                                                                        │  tracker!)
┌─────────────────────┐                                   ┌───────────▼─────────┐
│   REACT GUI (B)     │────── REST, localhost:7002 ──────►│     PEER NODE B     │
│   Vite dev :5173    │       search/download/status      │   TCP Server :9002  │
└─────────────────────┘                                   └─────────────────────┘
```

**Tri nezavisna modula (monorepo, BEZ Maven/Gradle — čist `javac`/`java` + PowerShell skripte):**
```
p2p-filesharing/
├── tracker/        → čist Java: HTTP REST API (centralni registar), com.sun.net.httpserver
├── peer-node/      → čist Java: TCP server + klijent + lokalni HTTP REST API
└── frontend/       → React (Vite) GUI, Y2K/glassmorphism
```

**Zašto bez frameworka:** Predmet je Računarske mreže — fokus treba da bude na soketima, HTTP protokolu "iz prve ruke" i thread-management-u, a ne na Spring konfiguraciji. `com.sun.net.httpserver.HttpServer` je deo standardnog JDK-a (nema spoljnih zavisnosti, nema Maven/Gradle potrebnog), a JSON (de)serijalizacija je ručno napisana (mali `Json` util, ~150 linija) jer su strukture poruka jednostavne i fiksnog oblika. Ovo takođe eliminiše potrebu za Maven-om, koji trenutno nije instaliran na razvojnoj mašini.

---

## 2. Ključne dizajnerske odluke (sa obrazloženjem)

| # | Odluka | Izbor | Obrazloženje |
|---|--------|-------|--------------|
| 1 | Identifikator fajla | **SHA-256 hash sadržaja** | Standard u P2P sistemima (BitTorrent info-hash princip). Isti fajl kod više peer-ova ima isti ID → tracker prirodno grupiše izvore. Služi i kao ID i kao verifikacija integriteta. |
| 2 | Threading model TCP servera | **Fixed ThreadPool (ExecutorService), blocking IO** | NIO/selektori su za hiljade konekcija; za projekat sa 2–10 peer-ova thread pool je jednostavniji, lakši za debug i odbranu, a ispunjava zahtev "paralelno slanje bez blokiranja". Pool od npr. 8 threadova = 8 istovremenih upload-ova. |
| 3 | Transfer protokol | **Custom binarni protokol preko TCP-a, chunk-ovi od 64KB** | Header (JSON linija, ručno parsirana raw-bajt čitačem da se izbegne "gutanje" bajtova narednog binarnog toka) + sirovi tok bajtova. Chunk buffer omogućava praćenje progresa i brzine u realnom vremenu. |
| 4 | Tracker skladište | **In-memory (ConcurrentHashMap)** | PRD eksplicitno: "Tracker uspešno čuva u memoriji". Nema baze — thread-safe mape + pozadinski `ScheduledExecutorService` čistač. |
| 5 | Heartbeat | **Peer šalje PUT svakih 10s; tracker briše posle 30s tišine** | TTL model: `ScheduledExecutorService.scheduleAtFixedRate` na trackeru upoređuje `lastSeen` timestamp. Peer koji nestane → automatski uklonjen sa svim fajlovima. |
| 6 | HTTP sloj (Tracker i Peer) | **`com.sun.net.httpserver.HttpServer` (JDK ugrađen)** | Nema Spring, nema servlet kontejnera, nema Maven zavisnosti. Ručno napisan mali `Router` (path-pattern matching sa `{param}` placeholder-ima) iznad `HttpServer` API-ja. |
| 7 | HTTP klijent (Peer → Tracker) | **`java.net.http.HttpClient`** (ugrađen od Java 11) | Sinhroni pozivi dovoljni za register/announce/heartbeat/search — nema potrebe za spoljnom bibliotekom. |
| 8 | JSON (de)serijalizacija | **Ručno pisan minimalni `Json` util** (`Map`/`List`/primitivi ↔ String) | Bez Gson/Jackson zavisnosti → nema potrebe za build alatom koji povlači zavisnosti sa interneta. Broj različitih poruka je mali i fiksnog oblika. |
| 9 | Build alat | **Bez Maven/Gradle — `javac` + PowerShell skripte (`build.ps1`, `run.ps1`)** | Maven nije dostupan na mašini; projekat nema spoljnih zavisnosti pa mu ni ne treba build alat sa upravljanjem zavisnostima. |
| 10 | Praćenje progresa u GUI | **Polling svakih 500ms** (`GET /api/downloads`) | Najjednostavnije i dovoljno "realno vreme" za progress bar. |
| 11 | Hash algoritam | **SHA-256** | PRD dozvoljava MD5 ili SHA-256; SHA-256 je moderan standard, MD5 je kriptografski slomljen. `MessageDigest` iz JDK-a. |
| 12 | Peer host detekcija na trackeru | **Tracker čita IP iz `HttpExchange.getRemoteAddress()`**, port šalje peer u telu zahteva | Robusnije od peer-a koji sam prijavljuje svoj IP (izbegava pogrešnu/lažnu adresu); u LAN demo okruženju daje tačnu adresu automatski. |
| 13 | Testiranje | **Ručno pisan test harness bez JUnit-a/Maven-a** (`testutil/Assert.java` + `testutil/TestRunner.java`, reflection runner za `testXxx()` metode) | Konzistentno sa "bez frameworka" pristupom; nema potrebe za build alatom da povuče JUnit sa interneta. Integracioni testovi (Tracker REST API, `DownloadService`) koriste **prave** `HttpServer`/`Socket` instance na efemernim portovima — nema mock biblioteka. |
| 14 | Otpornost na korumpiran fajl kod jednog peer-a | **Reset + probaj sledećeg peer-a**, ne odustaj odmah | Hash mismatch znači da JEDAN peer ima lošu kopiju, ne nužno svi — mreža realno može imati i dobre i loše kopije istog fajla. Poslednja konkretna greška se pamti (`lastError`) da finalna FAILED poruka bude smislena ako baš svi izvori otkažu. |
| 15 | Multi-source download protokol | **Novi `RANGE_REQUEST`/`RANGE_RESPONSE` tip poruke pored postojećeg `FILE_REQUEST`** (offset+length umesto celog fajla), `FileChannel` pozicioni upis bez zaključavanja | Nazad-kompatibilno (stari `FILE_REQUEST` i dalje radi za single-source slučaj); svaki chunk je nezavisna TCP konekcija na drugi peer, upisuje se na svoj offset u pre-alociran `.part` fajl — opsezi se ne preklapaju pa nije potrebna sinhronizacija između niti. Aktivira se samo kad ima ≥2 peer-a I fajl je veći od ~1MB (ispod toga overhead dodatnih konekcija nije isplativ); svaki neuspeh transparentno pada nazad na proveren single-source put. |

---

## 3. Specifikacija Tracker REST API-ja

Bazna putanja: `http://<tracker-host>:8080/api`

| Metoda | Endpoint | Telo / parametri | Odgovor | Namena |
|--------|----------|------------------|---------|--------|
| POST | `/peers/register` | `{peerId?, port}` | `{peerId, host}` | Registracija peer-a (peerId = UUID, generiše tracker ako nije prosleđen; host = detektovana IP adresa) |
| POST | `/peers/{peerId}/files` | `[{fileHash, fileName, size}]` | 200 | Announce — puna lista deljenih fajlova (replace semantika) |
| PUT | `/peers/{peerId}/heartbeat` | — | 200 / 404 | Osvežava `lastSeen`; 404 → peer se ponovo registruje |
| GET | `/files/search?q=naziv` | query string | `[{fileHash, fileName, size, peerCount}]` | Pretraga po nazivu (case-insensitive contains) |
| GET | `/files/{fileHash}/peers` | — | `[{peerId, host, port}]` | Peer discovery — ko poseduje fajl |
| DELETE | `/peers/{peerId}` | — | 200 | Uredno odjavljivanje (graceful shutdown) |
| GET | `/peers` | — | `[{peerId, host, port, fileCount, lastSeenAgoMs}]` | Debug/demo uvid u trenutno stanje registra |

**Model podataka (in-memory):**
```java
public class PeerInfo {
    final String peerId;
    final String host;
    final int port;
    volatile long lastSeenMillis;
    final Map<String, FileMeta> files; // fileHash -> FileMeta, ConcurrentHashMap
}
public record FileMeta(String fileHash, String fileName, long size) {}
```

**Eviction sweep (bez Spring @Scheduled → čist JDK):**
```java
ScheduledExecutorService evictor = Executors.newSingleThreadScheduledExecutor();
evictor.scheduleAtFixedRate(() -> {
    long cutoff = System.currentTimeMillis() - 30_000;
    peers.values().removeIf(p -> p.lastSeenMillis < cutoff);
}, 10, 10, TimeUnit.SECONDS);
```

---

## 4. Specifikacija Peer Node-a

### 4.1. Komponente (paketi)

```
peer-node/src/main/java/rs/rmt/peer/
├── PeerMain.java                  // main + startup orkestracija, shutdown hook
├── config/PeerConfig.java         // CLI argumenti + podrazumevane vrednosti (sharedDir, downloadDir, tcpPort, httpPort, trackerUrl)
├── tracker/TrackerClient.java     // java.net.http.HttpClient ka trackeru (register, announce, heartbeat, search, peersForFile, unregister)
├── share/
│   ├── SharedFolderScanner.java   // skenira folder, računa SHA-256 (streaming), keš po (path,size,mtime)
│   └── LibraryService.java        // katalog: fileHash → Path (lokalni fajlovi), thread-safe
├── transfer/
│   ├── TransferProtocol.java      // konstante tipova poruka + raw-line read/write helpers
│   ├── FileServer.java            // TCP ServerSocket + ExecutorService (upload strana, pool=8)
│   ├── UploadHandler.java         // obrada jednog zahteva: čita header, streamuje fajl
│   ├── DownloadService.java       // otvara Socket ka izvoru, prima bajtove, verifikuje hash
│   └── DownloadManager.java       // registar aktivnih transfera (progres, brzina, status)
├── api/
│   ├── PeerHttpServer.java        // com.sun.net.httpserver setup + CORS + Router
│   ├── SearchHandler.java         // GET /api/search?q= (proxy ka trackeru + alreadyOwned flag)
│   ├── DownloadsHandler.java      // POST /api/downloads, GET /api/downloads
│   ├── LibraryHandler.java        // GET /api/library
│   └── StatusHandler.java         // GET /api/status
└── util/
    ├── Json.java                  // isti minimalni JSON util kao u trackeru
    └── HttpUtil.java              // helperi za HttpExchange (read body, send json, query params)
```

### 4.2. Lifecycle peer-a (startup sekvenca)

1. Parsiraj CLI argumente / `PeerConfig` (podrazumevano: `sharedDir=./shared`, `downloadDir=./downloads`, `tcpPort=9001`, `httpPort=7001`, `trackerUrl=http://localhost:8080`) — omogućava pokretanje više instanci na istoj mašini za demo (`--tcp-port 9002 --http-port 7002 --shared-dir ./shared-b ...`).
2. `SharedFolderScanner`: skeniraj deljeni folder, za svaki fajl izračunaj SHA-256 (streaming, buffer 8KB — ne učitavati ceo fajl u memoriju!).
3. Pokreni `FileServer` (TCP listener) u posebnom threadu.
4. `TrackerClient.register()` → dobij peerId (i host koji je tracker detektovao).
5. `TrackerClient.announceFiles()` → pošalji metapodatke.
6. Pokreni heartbeat scheduler (`ScheduledExecutorService`, svakih 10s). Ako heartbeat vrati 404 → automatski re-register + re-announce (tracker je restartovan ili nas je izbacio). Uspeh/neuspeh se beleži u `connectedToTracker` flag za `/api/status`.
7. Pokreni `PeerHttpServer` (lokalni REST API) → GUI može da se poveže.
8. Shutdown hook (`Runtime.getRuntime().addShutdownHook`): `DELETE /peers/{peerId}`, gasi thread pool-ove.

### 4.3. Transfer protokol (custom, preko TCP-a)

**Zahtev (downloader → uploader):** jedna JSON linija terminirana `\n`:
```json
{"type":"FILE_REQUEST","fileHash":"a3f5..."}
```

**Odgovor (uploader → downloader):** jedna JSON linija + sirovi bajtovi:
```json
{"type":"FILE_RESPONSE","status":"OK","fileHash":"a3f5...","size":10485760}
```
→ zatim tačno `size` bajtova fajla (streaming, buffer 64KB).
Ako fajl ne postoji: `{"type":"FILE_RESPONSE","status":"NOT_FOUND"}` i zatvaranje konekcije.

**Bitna implementaciona napomena:** header linija se čita bajt-po-bajt (ne `BufferedReader`/`Scanner`) da se izbegne "pojedeni" prefiks binarnog sadržaja fajla u internom baferu čitača — ovo je čest bug kod custom TCP protokola koji mešaju tekst i binarne podatke na istom soketu.

**Downloader logika:**
```
1. GET /files/{hash}/peers sa trackera → lista izvora (isključi sebe ako se pojavljuje)
2. Izaberi prvog dostupnog (fallback na sledećeg ako konekcija padne ili vrati NOT_FOUND)
3. Socket connect (timeout 5s) → pošalji FILE_REQUEST
4. Čitaj header (raw-line) → čitaj `size` bajtova u downloadDir/<ime>.part
   - posle svakog chunk-a (64KB): ažuriraj DownloadManager (bytesReceived, brzina — klizni prozor)
   - hash se računa "u letu" (DigestInputStream ili ručni MessageDigest.update po chunk-u) — bez drugog prolaza kroz fajl
5. Uporedi izračunati SHA-256 sa fileHash iz trackera:
   - poklapanje  → preimenuj .part → finalno ime, status COMPLETED
   - razlika     → obriši .part, status FAILED (corrupted)
6. (Opciono, ako ostane vremena) Announce trackeru da i mi sada nudimo taj fajl → postajemo seed
```

**Uploader logika (`UploadHandler`, izvršava se u thread pool-u):**
```
accept() → submit u ExecutorService (pool 8 threadova)
→ pročitaj FILE_REQUEST (raw-line) → nađi fajl u LibraryService
→ pošalji header → streamuj fajl (64KB buffer) → zatvori socket
```
Svaki upload je nezavisan task → paralelno serviranje više korisnika bez blokiranja (zahtev 3.2).

### 4.3.1. Multi-source download (Faza 6, implementirano)

Kada tracker vrati ≥2 peer-a za traženi fajl I fajl je veći od ~1MB, `DownloadService` umesto jedne
konekcije otvara po jednu konekciju **po delu fajla** (do 4 paralelna dela), svaki ka drugom peer-u:

**Zahtev (downloader → uploader), po delu:**
```json
{"type":"RANGE_REQUEST","fileHash":"a3f5...","offset":2621440,"length":2621440}
```
**Odgovor:** `{"type":"RANGE_RESPONSE","status":"OK","offset":...,"length":...}` + tačno `length` sirovih
bajtova (ili `{"status":"NOT_FOUND"}` ako opseg nije validan ili peer nema fajl).

Downloader unapred alocira `.part` fajl na punu veličinu (`RandomAccessFile.setLength`), pa svaka nit
piše na svoj offset preko `FileChannel.write(buffer, position)` — pozicioni upis, bez potrebe za
zaključavanjem jer se opsezi nikad ne preklapaju. Kad svi delovi stignu, ceo `.part` fajl se hešuje
(SHA-256, streaming) i upoređuje sa tracker-ovim hash-om — tek tada se preimenuje u finalno ime.

**Ako bilo šta ne uspe** (mrtav/spor peer za jedan deo, odbijen range, hash mismatch na kraju) — `.part`
se briše i download se **u potpunosti vraća na proveren single-source put** (nova, obična `FILE_REQUEST`
konekcija, jedan po jedan peer, ista logika kao pre uvođenja multi-source). Ovo znači da multi-source
nikad ne može "pokvariti" download koji bi inače uspeo single-source putanjom — samo ubrzava srećan put.

### 4.4. Lokalni REST API (za React GUI)

Bazna putanja: `http://localhost:7001/api` (CORS: `Access-Control-Allow-Origin: *`, ručno dodato u svakom handler-u jer nema Spring auto-konfiguracije; OPTIONS preflight obrađen eksplicitno)

| Metoda | Endpoint | Odgovor |
|--------|----------|---------|
| GET | `/search?q=` | `[{fileHash, fileName, size, peerCount, alreadyOwned}]` |
| POST | `/downloads` `{fileHash, fileName}` | `{downloadId}` — pokreće transfer u pozadini (posebna nit) |
| GET | `/downloads` | `[{downloadId, fileName, size, bytesReceived, progressPct, speedBytesPerSec, status}]` — status ∈ IN_PROGRESS / VERIFYING / COMPLETED / FAILED |
| GET | `/library` | `[{fileHash, fileName, size}]` |
| GET | `/status` | `{connectedToTracker, peerId, tcpPort, httpPort, trackerUrl, sharedDir}` |

---

## 5. Frontend (React) — struktura i UI zahtevi

**Stack:** Vite + React 18 + čist CSS (custom glassmorphism, bez teških UI biblioteka).

**Stranice/tabovi (SPA, tab navigacija):**
1. **Pretraga** — search bar + tabela rezultata (ime, veličina, broj peer-ova, dugme Download)
2. **Preuzimanja (Dashboard)** — kartice aktivnih transfera: animirani progress bar, brzina (KB/s), status badge (In progress / Verifying / Completed / Failed)
3. **Moja biblioteka** — fajlovi koje nudim mreži
4. **Status header** — indikator konekcije sa trackerom (zelena/crvena tačka + puls animacija), peerId

**Y2K / Glassmorphism dizajn sistem:**
- Pozadina: gradijent (electric blue → purple → pink) + dekorativni blur "blob" elementi
- Paneli/kartice: `background: rgba(255,255,255,0.08); backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.25); border-radius: 20px`
- Y2K akcenti: chrome/metalik gradijenti na naslovima, neon glow na dugmićima, pixel/retro font za brojeve (npr. "Orbitron"/"VT323" preko Google Fonts CDN linka u `index.html`), zvezdice/sparkle dekoracije
- Animacije: modali sa scale+blur ulazom, progress bar sa "shimmer" efektom, hover glow
- Podaci: polling `GET /downloads` i `GET /status` na 500–1000ms (custom hook `usePolling`)

---

## 6. Faze implementacije (redosled rada)

### Faza 0 — Setup (30 min) ✅ ZAVRŠENO
- [x] Monorepo struktura, `.gitignore` (out/, out-test/, node_modules/, *.part, downloads/, shared/)
- [x] `tracker/` — folder struktura, PowerShell `build.ps1`/`run.ps1`/`test.ps1` (čist `javac`/`java`)
- [x] `peer-node/` — folder struktura, PowerShell `build.ps1`/`run.ps1`/`test.ps1`
- [x] `frontend/` — `npm create vite@latest` (React)

### Faza 1 — Tracker (2–3h) ✅ ZAVRŠENO
- [x] `util/Json.java` (minimalni parser/serializer)
- [x] Modeli (`PeerInfo`, `FileMeta`) + `TrackerRegistry` servis (ConcurrentHashMap)
- [x] `util/Router.java` + svi REST endpointi (tabela iz sekcije 3) preko `HttpServer`
- [x] Heartbeat eviction (`ScheduledExecutorService`)
- [x] Validacija ulaza (400 za nedostajući/nevalidan `port`, nevalidnu listu fajlova) umesto generičkog 500
- [x] Automatski testovi (`tracker/test.ps1`, 20/20 prolaze): `JsonTest`, `TrackerRegistryTest`, i
  `TrackerApiIntegrationTest` koji podiže pravi `HttpServer` i tera ceo REST lifecycle preko `HttpClient`
- **Milestone: DoD #1** — tracker čuva listu aktivnih korisnika u memoriji ✓ (potvrđeno uživo i testovima)

### Faza 2 — Peer Node: registracija i deljenje (2–3h) ✅ ZAVRŠENO
- [x] Konfiguracija (`PeerConfig`, CLI argumenti) + `SharedFolderScanner` (SHA-256 streaming, keš sa čišćenjem obrisanih fajlova)
- [x] `TrackerClient` (register, announce, heartbeat, re-register logika) preko `java.net.http.HttpClient`
- [x] `GET /api/status`, `GET /api/library` (lokalni `HttpServer`)
- [x] Automatski testovi: `SharedFolderScannerTest` (hash tačnost, keš, promena sadržaja, brisanje fajla)
- **Milestone: DoD #2** — peer pronalazi drugog peer-a preko trackera ✓ (potvrđeno uživo sa 2–3 instance)

### Faza 3 — TCP transfer engine (3–4h) ★ srce projekta ✅ ZAVRŠENO
- [x] `TransferProtocol` (raw-line header read/write) + `FileServer` + `UploadHandler` (thread pool)
- [x] `DownloadService` (in-flight SHA-256, .part fajl, verifikacija, peer fallback)
- [x] `DownloadManager` (thread-safe progres/brzina)
- [x] REST: `POST /downloads`, `GET /downloads`, `GET /search`
- [x] Socket timeout-i (upload strana 30s, download strana connect 5s / read 15s) da spor/mrtav peer ne zaglavi thread zauvek
- [x] Automatski testovi preko lažnih TCP servera (`FakeUploader`): uspešan transfer, korumpiran fajl
  (hash mismatch → `.part` obrisan), fallback na sledećeg peer-a (mrtav ili korumpiran prvi izvor),
  prazna lista peer-ova, nedostupan host — svi bez pravog operativnog transfera, deterministički i brzi
- [x] Uživo: transfer 10MB fajla između 2 lokalne instance → SHA-256 hash identičan originalu
- **Milestone: DoD #3** — 10MB fajl prenet bez oštećenja direktnom socket konekcijom ✓

### Faza 4 — React GUI (3–4h) ✅ ZAVRŠENO
- [x] Layout + glassmorphism dizajn sistem + status header
- [x] Pretraga tab (poziv `/api/search`, dugme Download, onemogućeno za već posedovane fajlove)
- [x] Dashboard tab (polling, progres barovi, brzina, statusi)
- [x] Biblioteka tab
- [x] Fetch timeout (8s) i jasne poruke grešaka kad lokalni peer ne odgovara
- **Milestone: DoD #4** — GUI u realnom vremenu odražava napredak ✓ (potvrđeno headless-browser sesijom, bez console grešaka)

### Faza 5 — Integracija i otpornost (1–2h) ✅ ZAVRŠENO
- [x] Scenario: peer nestane usred download-a (mrtav/korumpiran izvor) → status FAILED za taj izvor,
  automatski fallback na sledećeg peer-a → COMPLETED (testovi + uživo demo)
- [x] Scenario: tracker restart → peer detektuje prekid (`connectedToTracker=false`), zadržava
  `peerId`, i čim tracker ponovo postane dostupan automatski se re-registruje (nova `peerId`) i
  ponovo najavljuje fajlove — **potvrđeno uživo**: ubijen tracker proces, peer nastavlja heartbeat
  pokušaje, tracker vraćen sa praznim registrom, peer se re-registrovao u sledećem ciklusu (~13s)
- [x] Scenario: namerno oštećen fajl (flip bita u sadržaju) → hash provera ga odbija, `.part` se briše,
  finalni fajl se NIKAD ne pravi od korumpiranih podataka (test + uživo)
- [ ] Test na 2 fizička računara u istoj mreži (LAN IP umesto localhost) — **nije automatizovano**
  (zahteva 2 fizičke mašine); uputstvo u README, izvesti ručno pre odbrane ako je moguće

### Faza 6 — Poliranje ✅ DELIMIČNO ZAVRŠENO
- [x] **Multi-source download** — kada ≥2 peer-a nude fajl veći od ~1MB, `DownloadService` deli fajl na
  do 4 dela i preuzima ih paralelno (svaki deo = nova TCP konekcija sa `RANGE_REQUEST`/`RANGE_RESPONSE`,
  novi protokolski tip pored postojećeg `FILE_REQUEST`). Svaki deo piše na svoj offset u pre-alociran
  `.part` fajl preko `FileChannel` pozicionog upisa (bez potrebe za zaključavanjem, opsezi se ne
  preklapaju). Posle svih delova, ceo fajl se hešuje i upoređuje sa tracker-ovim hash-om; svaki neuspeh
  (mrtav izvor, odbijen range, loš hash) briše `.part` i **transparentno pada nazad na proveren
  single-source put** umesto da pokvari download. Testirano i sa pravim `FileServer` instancama (3
  paralelna izvora) i uživo (5MB fajl, 2 izvora, potvrđen SHA-256 na kraju).
- [x] Automatski re-announce preuzetih fajlova (seed ponašanje) — već deo `DownloadService`-a od Faze 3
  (`library.addFile(...)` posle uspešne verifikacije), potvrđeno i testovima i uživo demo-om.
- [ ] Server-Sent Events umesto pollinga — **nije urađeno** (namerno; polling na 500ms je dovoljno
  "realtime" za demo i mnogo jednostavniji za odbranu pred komisijom).

---

## 7. Rizici i kako ih rešavamo

| Rizik | Rešenje |
|-------|---------|
| Peer iza NAT-a / firewall | Demo u istoj LAN mreži; tracker čita IP iz `HttpExchange.getRemoteAddress()`. Windows Firewall: dozvoliti Java na privatnoj mreži (uputstvo u README). |
| Race conditions u registru | `ConcurrentHashMap` + `volatile`/`AtomicLong` polja; nema deljenog mutable stanja bez sinhronizacije. |
| Veliki fajlovi pune memoriju | Nikad `readAllBytes()` — isključivo streaming sa fiksnim buffer-om (64KB transfer, 8KB hashing). |
| Hash spor za velike fajlove pri skeniranju | Streaming digest; keš `(path, size, mtime) → hash` da se izbegne rehash nepromenjenih fajlova. |
| Header/binary mešanje na TCP soketu | Raw byte-by-byte čitanje header linije (bez `BufferedReader` na istom stream-u kao binarni podaci). |
| GUI ne vidi peer API (CORS) | Ručno dodat `Access-Control-Allow-Origin` header + eksplicitna obrada `OPTIONS` u svakom handleru. |
| Nema Maven/build alata za zavisnosti | Projekat namerno bez spoljnih zavisnosti — čist JDK 17 (`HttpServer`, `HttpClient`, `MessageDigest`) je dovoljan. |

---

## 8. Definition of Done — mapiranje

| PRD kriterijum | Faza | Kako dokazujemo |
|----------------|------|-----------------|
| Tracker čuva aktivne korisnike u memoriji | 1 | `GET /api/peers` debug endpoint / logovi |
| Peer pronalazi peer-a preko trackera | 2 | `GET /files/{hash}/peers` vraća drugu instancu |
| 10MB fajl prenet bez oštećenja (A→B, socket) | 3 | SHA-256 poklapanje + demo na 2 računara |
| GUI u realnom vremenu prati transfer | 4 | Progress bar + brzina uživo tokom demo-a |
