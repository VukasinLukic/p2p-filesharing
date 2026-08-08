# P2P File Sharing — Računarske Mreže i Tehnologije

Hibridna P2P aplikacija: centralni **Tracker** (samo metapodaci) + direktan **peer-to-peer** transfer
fajlova preko TCP soketa. Detaljan dizajn i obrazloženja u [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md),
zahtevi u [prd.md](prd.md). Spremaš prezentaciju? Prati [DEMO_SCRIPT.md](DEMO_SCRIPT.md) — korak-po-korak
scenario sa svim DoD kriterijumima i verovatnim pitanjima komisije.

Nema Spring/Spring Boot, nema Maven/Gradle — Tracker i Peer Node su čist Java 17
(`com.sun.net.httpserver.HttpServer`, `java.net.http.HttpClient`, `java.net.Socket`).

## Preduslovi

- JDK 17+ (`java -version`)
- Node.js 18+ i npm (za frontend)

## Struktura

```
p2p-filesharing/
├── tracker/     Java — centralni registar (port 8080)
├── peer-node/   Java — TCP transfer + lokalni REST API (npr. TCP 9001 / HTTP 7001)
├── frontend/    React (Vite) — GUI, port 5173
├── scripts/     PowerShell logika za "1-click" pokretanje
└── START-*.bat  dupli klik → pokreće sve što treba
```

## Pokretanje na „jedan klik" (bez terminala)

Dupli klik na odgovarajući `.bat` fajl u korenu projekta — skripta sama kompajlira Java module,
kreira foldere, pokreće Vite dev server ako već ne radi i otvara GUI u pretraživaču:

| Fajl | Šta radi |
|------|----------|
| `START-LOKALNI-DEMO.bat` | **Za prezentaciju.** Sve na jednoj mašini: Tracker, Peer A, Peer B i frontend, svaki u svom prozoru, plus dva otvorena taba (`?port=7001` i `?port=7002`). Ako je `peer-node/shared-a` prazan, napravi 10MB test fajl da demo ima šta da prenese. |
| `START-TRACKER.bat` | Samo Tracker na 8080. Ispisuje IP adresu ovog računara — tu adresu unosi druga mašina. |
| `START-MOJ-PEER.bat` | Peer A: `peer-node/shared-a`, `peer-node/downloads-a`, TCP 9001, HTTP 7001 + otvara GUI. |
| `START-KOLEGINICA-LAN-PEER.bat` | Peer B na **drugom računaru**: pita za IP trackera (pamti ga u `scripts/settings.json`, sledeći put je dovoljan Enter), proverava da li je tracker dostupan, pa pokreće peer (TCP 9002, HTTP 7002) + GUI. |

Zaustavljanje: `Ctrl+C` ili zatvaranje prozora. Skripte traže **JDK 17+** — ako je `java` u PATH-u
stara verzija (npr. Oracle-ov Java 8 shim), `scripts/java-tools.ps1` automatski uzima JDK iz
`JAVA_HOME`, pa nema `UnsupportedClassVersionError`.

## Podešavanja mreže iz GUI-ja

Zupčanik u gornjem desnom uglu otvara **Podešavanja mreže**, gde možeš bez restarta:

- videti adresu/port lokalnog peer-a, njegov `peerId`, TCP port, tracker URL i deljeni folder;
- prebaciti GUI na drugi peer (`7002`, `192.168.0.15:7001` ili puni URL) — čuva se u `localStorage`
  pod ključem `p2p.peerApiBase`, tako da tab pamti izbor posle reload-a;
- proveriti vezu sa trackerom i pozvati **Osveži konekciju** — to poziva `POST /api/tracker/reconnect`
  na peer-u, koji se odmah re-registruje i ponovo najavi fajlove umesto da čeka sledeći heartbeat (10s).

`?port=` iz URL-a ima prioritet nad sačuvanom vrednošću — zato `START-*.bat` skripte i dalje otvaraju
tačno onaj peer koji su pokrenule.

## Pokretanje iz terminala (demo na jednoj mašini — 2 peer-a)

Otvori 4 terminala:

**1. Tracker**
```powershell
cd tracker
.\run.ps1                      # port 8080
```

**2. Peer A** (deli fajlove iz `peer-node/shared-a`)
```powershell
cd peer-node
.\run.ps1 -SharedDir ./shared-a -DownloadDir ./downloads-a -TcpPort 9001 -HttpPort 7001
```

**3. Peer B** (deli fajlove iz `peer-node/shared-b`)
```powershell
cd peer-node
.\run.ps1 -SharedDir ./shared-b -DownloadDir ./downloads-b -TcpPort 9002 -HttpPort 7002
```

Ubaci neki fajl (npr. 10MB test fajl) u `peer-node/shared-a/` **pre** pokretanja Peer A-a — deljeni
folder se skenira jednom, pri startu.

**4. Frontend**
```powershell
cd frontend
npm install    # samo prvi put
npm run dev    # http://localhost:5173
```

GUI se po difoltu povezuje na Peer A (`http://localhost:7001/api`). Da bi u drugom browser tab-u
gledao Peer B, otvori `http://localhost:5173/?port=7002` (ili prebaci peer kroz Podešavanja mreže).

## Pokretanje na 2 fizička računara (pravi LAN demo)

Najlakše: na jednom računaru `START-TRACKER.bat` (ispisuje svoj IP), na drugom
`START-KOLEGINICA-LAN-PEER.bat` i unese se taj IP.

Ručno: pokreni tracker na jednom računaru (npr. Računar A), zatim na svakom računaru pokreni peer sa
`--tracker-url http://<IP-Računara-A>:8080`. Tracker automatski detektuje IP adresu svakog peer-a
preko dolazne konekcije, tako da peer ne mora sam da prijavljuje svoj IP. Proveri da Windows Firewall
dozvoljava Java na privatnoj mreži (inače TCP/HTTP portovi neće biti dostupni sa druge mašine).

## Provera Definition of Done (iz PRD-a)

| # | Kriterijum | Kako proveriti |
|---|---|---|
| 1 | Tracker čuva aktivne peer-ove u memoriji | `curl http://localhost:8080/api/peers` posle registracije |
| 2 | Peer pronalazi drugog peer-a preko trackera | Pretraga u GUI-ju vraća fajl sa `peerCount >= 1` |
| 3 | 10MB fajl prenet bez oštećenja (TCP socket) | Download u GUI-ju → status `COMPLETED`; uporedi `sha256sum` originala i preuzetog fajla |
| 4 | GUI u realnom vremenu prati transfer | Tab "Preuzimanja" — progress bar i brzina se ažuriraju uživo |

Sve četiri stavke su ručno i automatski verifikovane (vidi sekciju o testovima ispod).

## Build bez pokretanja (samo kompajliranje)

```powershell
cd tracker;   .\build.ps1
cd peer-node; .\build.ps1
cd frontend;  npm run build
```

## Automatski testovi

Bez JUnit-a/Maven-a — mali ručno pisan test harness (`testutil/Assert.java` + `testutil/TestRunner.java`,
reflection-based runner koji poziva svaku `testXxx()` metodu) u oba Java modula, konzistentno sa
"bez frameworka" pristupom celog projekta.

```powershell
cd tracker;   .\test.ps1     # 38 testova: Json, TrackerRegistry, puni REST API lifecycle preko pravog
                              #            HttpServer-a, UserStore (hash/salt/validacija/perzistencija),
                              #            SessionStore (tokeni/istek/logout), auth REST API
cd peer-node; .\test.ps1     # 46 testova: Json, SharedFolderScanner, ChunkHasher (per-chunk SHA-256),
                              #            TransferProtocol, DownloadManager, DownloadService (uspeh/korupcija/
                              #            fallback/nedostupni peer-ovi preko lažnih TCP servera), multi-source
                              #            download (pravi FileServer instance), chunk manifest preko TCP-a,
                              #            PeerApiServer (uključujući /tracker/reconnect, preko lažnog trackera)
```

Pokrivene su i "hostile" ivice: peer koji vrati oštećen fajl (hash mismatch → `.part` se briše, sledeći
peer se automatski proba), peer koji prihvati konekciju pa je odmah prekine, prazna lista peer-ova,
nedostupan host, i nevalidni/nedostajući JSON parametri u REST zahtevima (400/409 umesto 500).

**Uživo (van automatskih testova) je takođe potvrđeno:**
- Restart trackera usred rada → peer sam detektuje prekid, a zatim se automatski ponovo registruje
  čim tracker ponovo postane dostupan (nova `peerId`, fajlovi ponovo najavljeni).
- Multi-source paralelni download: kad ≥2 peer-a nude isti fajl veći od ~1MB, fajl se deli na delove i
  preuzima paralelno sa više izvora istovremeno (pool niti po delu), uz ceo-fajl SHA-256 verifikaciju
  na kraju; svaki neuspeh (mrtav/spor izvor, loš deo) transparentno pada nazad na proveren
  single-source put umesto da pokvari ceo download.

## Pripremljena nadogradnja (iz [noveStvari.md](noveStvari.md))

Dve stvari iz `noveStvari.md` su **arhitektonski pripremljene i pokrivene testovima**, ali svesno još
nisu uključene u glavni tok — da postojeće, verifikovano ponašanje ostane nepromenjeno.

**1. Heširanje po delovima fajla (per-chunk SHA-256)**

- `ChunkManifest` (`peer-node/.../model`) — hash po bloku od 512KB + matematika offset/dužina i
  `verifyChunk(index, bytes, length)`; odbija i pokvaren blok i tačan blok na pogrešnom indeksu.
- `ChunkHasher` (`.../share`) — streaming izračunavanje (fajl se nikad ne učitava ceo u memoriju),
  keširano po fajlu uz invalidaciju na promenu veličine/mtime.
- `GET /api/files/{fileHash}/chunks` na peer-u i `CHUNKS_REQUEST`/`CHUNKS_RESPONSE` u TCP protokolu
  (`UploadHandler` servira, `ChunkManifestClient` preuzima manifest od drugog peer-a).
- **Šta ostaje:** `DownloadService` još verifikuje samo ceo sastavljen fajl. Sledeći korak je da
  `attemptMultiSourceDownload` poravna opsege na granice blokova i proveri svaki blok odmah po prijemu
  — tada se loš izvor otkriva u toku prenosa, a ponovo se preuzima samo taj blok.

**2. Baza korisnika i prijava (osnova za „little social" deo)**

- `UserStore` (`tracker/.../users`) — nalozi u `tracker/data/users.json`, atomičan upis
  (temp + rename), validacija korisničkog imena/lozinke, odbija duplikate; pokvaren fajl podiže
  grešku umesto da tiho krene sa praznim spiskom.
- `PasswordHasher` — PBKDF2-HMAC-SHA256, 120k iteracija, salt po korisniku, konstantno-vremensko
  poređenje. Lozinke se nikad ne čuvaju u čitljivom obliku.
- `SessionStore` — bearer tokeni sa rokom trajanja (12h) i logout-om.
- REST: `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`,
  `GET /api/users`.
- JSON umesto SQLite/H2 zato što bi oba zahtevala JAR driver — projekat se kompajlira čistim `javac`-om
  bez dependency managera. Interfejs (`register`/`authenticate`/`findById`) je taj koji je važan:
  prelazak na pravu bazu menja samo `UserStore`.
- **Šta ostaje:** frontend još nema login ekran, i deljenje fajlova namerno **ne** zahteva nalog —
  autentikacija je dodatak, a ne uslov, pa postojeći demo radi bez prijave.

## Poznata ograničenja / za dalje

- Multi-source download još verifikuje ceo fajl (vidi gore) — dovoljno za demo, ali sporije otkriva koji
  je peer poslao loš deo u odnosu na pravi BitTorrent-stil protokol sa hash-om po komadu.
- SSE umesto pollinga za `/api/downloads` nije implementirano (polling na 500ms je dovoljno "realtime" za
  demo i jednostavniji za odbranu).
- Deljeni folder se skenira samo pri startu peer-a — fajl ubačen kasnije se ne vidi do restarta.
- Pravi test na 2 fizička računara u LAN-u nije automatizovan (zahteva 2 mašine) — uputstvo je gore, ali
  izvedi ga ručno pre odbrane ako je moguće.
