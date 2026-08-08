# Demo Script — P2P File Sharing (za prezentaciju)

Ovo je scenario korak-po-korak za odbranu projekta. Cilj: jasno pokazati sva 4 kriterijuma iz PRD-a
(Definition of Done), bez konfuzije oko "already owned" (koja se dešava ako slučajno pretražuješ na
istom peer-u koji već ima fajl — zato demo koristi **dva odvojena peer-a**, jedan koji nudi fajl i
jedan koji ga preuzima).

---

## 0. Priprema — uradi OVO pre prezentacije (5 min)

1. Zatvori sve stare terminale/procese od ranijeg testiranja (da ne dođe do "port already in use").
   Ako naiđeš na `BindException: Address already in use`, neko od starih procesa je i dalje živ —
   pronađi ga (`Get-NetTCPConnection -LocalPort 8080,7001,7002 -State Listen`) i ugasi
   (`Stop-Process -Id <PID> -Force`).
2. Pripremi test fajl unapred, PRE pokretanja Peer A:
   ```powershell
   cd peer-node
   mkdir shared-a -Force
   mkdir shared-b -Force
   # bilo koji fajl 5-20MB je idealan za demo (progress bar traje par sekundi, ne treperi instant)
   Copy-Item "C:\putanja\do\neki-video-ili-zip.mp4" .\shared-a\
   ```
   **Važno:** deljeni folder se skenira samo JEDNOM, pri pokretanju peer-a. Ako dodaš fajl posle,
   moraš da restartuješ taj peer.
3. Build sve (ako nisi već):
   ```powershell
   cd tracker;   .\build.ps1
   cd peer-node; .\build.ps1
   cd frontend;  npm install; npm run build
   ```

---

## 1. Pokretanje

### Najlakše: dupli klik na `START-LOKALNI-DEMO.bat`

Skripta radi sve iz sekcije ispod: kompajlira oba Java modula, pokreće Tracker → Peer A → Peer B →
frontend (svaki u svom prozoru, u pravom redosledu, uz čekanje da se prethodni podigne) i otvara oba
browser taba (`?port=7001` i `?port=7002`). Ako je `peer-node/shared-a` prazan, napravi 10MB test fajl
da demo ima šta da prenese.

Za prezentaciju je i dalje korisno da znaš šta se ispod dešava — komisija to može pitati — pa je ručna
varijanta ostavljena u celini.

### Ručno: 4 terminala, ovim redosledom

**Terminal 1 — Tracker:**
```powershell
cd tracker
.\run.ps1
```
Ostaje otvoren tokom cele prezentacije. Ovde se vidi svaki `[REGISTER]`, `[ANNOUNCE]`, `[EVICT]` log —
dobro mesto da pokažeš komisiji da tracker *zna* ko je online, ali ne dira fajlove.

**Terminal 2 — Peer A** (izvor, ima fajl):
```powershell
cd peer-node
.\run.ps1 -SharedDir ./shared-a -DownloadDir ./downloads-a -TcpPort 9001 -HttpPort 7001
```

**Terminal 3 — Peer B** (prazan, on preuzima):
```powershell
cd peer-node
.\run.ps1 -SharedDir ./shared-b -DownloadDir ./downloads-b -TcpPort 9002 -HttpPort 7002
```

**Terminal 4 — Frontend:**
```powershell
cd frontend
npm run dev
```

Otvori DVA browser taba:
- Tab 1: `http://localhost:5173` → GUI za **Peer A**
- Tab 2: `http://localhost:5173/?port=7002` → GUI za **Peer B**

(Vite ume da izabere drugi port ako je 5173 zauzet — pogledaj šta ispiše u terminalu 4 i koristi taj port.)

---

## 2. Sam demo — šta pričaš i šta klikćeš

### Korak A — DoD #1: Tracker čuva registar u memoriji
- Otvori `http://localhost:8080/api/peers` u trećem browser tabu (ili `curl`).
- Pokaži da su OBA peer-a tu, sa `peerId`, `host`, `port`, `fileCount`.
- **Poenta za komisiju:** tracker ne vidi nijedan bajt fajla — samo metapodatke.

### Korak B — DoD #2: Peer pronalazi drugog peer-a preko trackera
- U **Peer B tabu** (port 7002), idi na "Pretraga", ukucaj deo imena fajla, klikni Pretraži.
- Rezultat pokazuje `1 izvor` (znači: tracker je Peer B-u rekao da Peer A ima taj fajl) — dugme kaže
  "Preuzmi" (NE "u biblioteci", jer Peer B ga stvarno nema).
- **Poenta:** ovo je peer discovery — Peer B nikad nije direktno pitao Peer A ništa, tracker je posrednik
  samo za ovaj korak.

### Korak C — DoD #3 + #4: Preuzimanje, progress uživo, integritet
- Klikni "Preuzmi" → GUI automatski prebacuje na tab "Preuzimanja".
- Pokaži progress bar kako raste, brzinu (KB/s ili MB/s) — to se ažurira na 500ms preko pravog REST poziva,
  ne fake animacija.
- Kad završi: status "Završeno" (zeleno).
- **Dokaz integriteta pred komisijom** (opciono, jako ubedljivo): u terminalu uporedi hash original vs.
  preuzeti fajl:
  ```powershell
  Get-FileHash .\peer-node\shared-a\<ime-fajla> -Algorithm SHA256
  Get-FileHash .\peer-node\downloads-b\<ime-fajla> -Algorithm SHA256
  ```
  Isti hash = fajl je prenet bit-po-bit identično preko direktne TCP konekcije.
- Idi na tab "Biblioteka" u Peer B tabu → fajl se sada pojavljuje tamo (Peer B je automatski postao seed).

---

## 3. Bonus scenariji (ako ostane vremena / za "wow efekat")

### Otpornost: tracker padne pa se vrati
1. Ugasi Terminal 1 (Ctrl+C ili zatvori prozor).
2. Sačekaj ~10-15s, pogledaj status header u GUI-ju → pretvara se u "Nije povezan".
3. Ponovo pokreni tracker (`.\run.ps1` u terminalu 1) — registar mu je sad prazan.
4. Sačekaj još ~10-15s → status se sam vrati na "Povezan sa trackerom" sa **novim** `peerId`.
   **Poenta:** peer se sam re-registruje, bez restarta.

### Multi-source: preuzimanje sa više izvora paralelno
1. Stavi ISTI fajl (>1MB) i u `shared-a` i u `shared-b`.
2. Pokreni i treći peer: `.\run.ps1 -SharedDir ./shared-c -DownloadDir ./downloads-c -TcpPort 9003 -HttpPort 7003`.
3. Preuzmi taj fajl iz Peer C tab-a (`?port=7003`) — u Terminal 3 (peer-node log) potraži liniju
   `multi-source completed ... using 2 parallel source(s)`.
   **Poenta:** fajl je stigao paralelno sa DVA izvora istovremeno, ne jedan po jedan.

### Korumpiran fajl se odbija
1. Posle uspešnog preuzimanja, ručno izmeni par bajtova preuzetog fajla u `downloads-b` (npr. otvori
   u Notepad-u i sačuvaj, ili prepiši par karaktera u hex editoru).
2. Obriši ga iz Peer B biblioteke (samo obriši fajl sa diska) i preuzmi ga ponovo — kad bi neko drugi
   servirao baš taj oštećen fajl, verifikacija bi ga odbila (status "Neuspešno", `.part` fajl se briše).
   Najbolje ovo objasniti kroz kod (`DownloadService.java`, `attemptSingleSourceDownload`) i pomenuti da
   je pokriveno automatskim testom (`DownloadServiceTest.testCorruptedDataIsRejectedAndPartFileCleanedUp`).

---

## 4. Automatski testovi (pokazati da postoji test suite, ne samo ručni klik)

```powershell
cd tracker;   .\test.ps1     # 20/20
cd peer-node; .\test.ps1     # 33/33
```
Ovo je jak argument za komisiju: 53 automatska testa pokrivaju i "hostile" slučajeve (mrtav peer,
korumpiran fajl, nedostupan tracker) koje je teško pouzdano ponoviti uživo.

---

## 5. Verovatna pitanja komisije (i kratki odgovori)

| Pitanje | Odgovor |
|---|---|
| Zašto nema Spring Boot-a? | Predmet je Računarske mreže — fokus na soketima i HTTP protokolu "iz prve ruke", ne na framework konfiguraciji. `com.sun.net.httpserver.HttpServer` je deo JDK-a, bez ijedne spoljne zavisnosti. |
| Zašto SHA-256 kao ID fajla? | Isti pristup kao BitTorrent info-hash — isti sadržaj kod više peer-ova dobija isti ID, i služi i za identifikaciju i za proveru integriteta u jednom koraku. |
| Šta ako se dva fajla različitog sadržaja zovu isto? | Nema veze — ID je hash sadržaja, ne ime; imena mogu da se razlikuju, hash ih razlikuje. |
| Šta ako peer padne usred slanja? | Downloader dobije IOException, hvata grešku, i automatski proba sledećeg peer-a sa liste (ako postoji) — pokriveno testom `testFallsBackToNextPeerWhenFirstIsDeadOrRefuses`. |
| Zašto polling a ne WebSocket/SSE? | Polling na 500ms je dovoljno "realno vreme" za progress bar, i mnogo jednostavniji za implementaciju i objašnjavanje pred komisijom. Pomenuto u planu kao svesna odluka. |
| Kako tracker zna IP adresu peer-a? | Čita je direktno iz TCP konekcije (`HttpExchange.getRemoteAddress()`), ne veruje peer-u da sam prijavi svoj IP — sigurnije i tačnije u LAN okruženju. |
| Šta se dešava ako je fajl veliki (GB)? | Nikad se ne učitava ceo u memoriju — čita/piše se u fiksnim blokovima (64KB za transfer, 8KB za hash), streaming kroz ceo proces. |
