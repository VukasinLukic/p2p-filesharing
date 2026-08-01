# P2P File Sharing — Računarske Mreže i Tehnologije

Hibridna P2P aplikacija: centralni **Tracker** (samo metapodaci) + direktan **peer-to-peer** transfer
fajlova preko TCP soketa. Detaljan dizajn i obrazloženja u [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md),
zahtevi u [prd.md](prd.md).

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
└── frontend/    React (Vite) — GUI, port 5173
```

## Pokretanje (demo na jednoj mašini — 2 peer-a)

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
gledao Peer B, otvori `http://localhost:5173/?port=7002`.

## Pokretanje na 2 fizička računara (pravi LAN demo)

Na oba računara pokreni tracker na jednom od njih (npr. Računar A), zatim na svakom računaru pokreni
peer sa `--tracker-url http://<IP-Računara-A>:8080`. Tracker automatski detektuje IP adresu svakog
peer-a preko dolazne konekcije, tako da peer ne mora sam da prijavljuje svoj IP. Proveri da Windows
Firewall dozvoljava Java na privatnoj mreži (inače TCP/HTTP portovi neće biti dostupni sa druge
mašine).

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
cd tracker;   .\test.ps1     # 20 testova: Json, TrackerRegistry, puni REST API lifecycle preko pravog HttpServer-a
cd peer-node; .\test.ps1     # 33 testa: Json, SharedFolderScanner, TransferProtocol, DownloadManager,
                              #           DownloadService (uspeh/korupcija/fallback/nedostupni peer-ovi preko
                              #           lažnih TCP servera), multi-source download (pravi FileServer instance),
                              #           PeerApiServer (preko lažnog trackera)
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

## Poznata ograničenja / za dalje

- Multi-source download nema per-chunk hash (samo tracker-ov hash celog fajla), pa se verifikacija radi
  tek posle sastavljanja svih delova — dovoljno za demo, ali sporije otkriva koji je peer poslao loš deo
  u odnosu na pravi BitTorrent-stil protokol sa hash-om po komadu.
- SSE umesto pollinga za `/api/downloads` nije implementirano (polling na 500ms je dovoljno "realtime" za
  demo i jednostavniji za odbranu).
- Pravi test na 2 fizička računara u LAN-u nije automatizovan (zahteva 2 mašine) — uputstvo je gore, ali
  izvedi ga ručno pre odbrane ako je moguće.
