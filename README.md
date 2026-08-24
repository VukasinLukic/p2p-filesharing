# P2P File Sharing

Aplikacija koristi tracker za pronalaženje peer-ova, a fajlove prenosi direktno između peer-ova preko TCP veze. Tracker nikad ne dodiruje sadržaj fajlova - samo vodi evidenciju o tome ko je online i šta deli.

## Arhitektura

- **Tracker** (`tracker/`, Java) - centralni server (`com.sun.net.httpserver.HttpServer`, REST/JSON API). Peer/file registar i korisnički nalozi se čuvaju u `tracker/data/*.json` i preživljavaju restart trackera (vidi [Perzistencija](#perzistencija-na-trackeru)).
- **Peer node** (`peer-node/`, Java) - instanca koju pokreće svaki korisnik. Skenira svoj `shared` folder, prijavljuje se na tracker, i prima direktne TCP konekcije od drugih peer-ova radi preuzimanja fajlova.
- **Frontend** (`frontend/`, React + Vite) - web interfejs koji razgovara isključivo sa lokalnim peer-node REST API-jem (pretraga, praćenje download-a, status). Ne zove tracker direktno.

Nema Maven/Gradle - tracker i peer-node se kompajliraju čistim `javac`-om preko PowerShell skripti (`build.ps1`/`run.ps1`/`test.ps1` u svakom modulu).

## Preduslovi

- JDK 17+
- Node.js 18+

## Lokalni demo — jedan računar

1. Zatvoriti prethodno pokrenute P2P prozore.
2. Pokrenuti `START-LOKALNI-DEMO.bat`.
3. Sačekati poruku `Demo ready`.
4. U tabu za Peer 2 otvoriti **Pretraga**, pronaći `demo-10mb.bin` i kliknuti **Preuzmi**.

Skripta pokreće tracker, Peer 1, Peer 2 i frontend. Peer 1 deli test fajl, a Peer 2 ga preuzima.

## Dva računara u istoj mreži

Na prvom računaru:

1. Pokrenuti `START-TRACKER.bat`.
2. Pokrenuti `START-PEER1.bat`.

Na drugom računaru:

1. Pokrenuti `START-PEER2.bat`.
2. Uneti LAN IP adresu računara sa trackerom.

Peer 1 koristi HTTP port `7001` i TCP port `9001`. Peer 2 koristi HTTP port `7002` i TCP port `9002`.

Za LAN transfer Windows Firewall mora dozvoliti Java aplikaciji privatnu mrežu, posebno TCP port `9001` na računaru Peer 1.

## Rad preko interneta (peer-ovi na različitim mrežama)

Da bi dva peer-a na potpuno različitim mrežama (ne isti LAN/WiFi) mogla da se nađu i razmene fajl, potrebno je da:

1. **Tracker bude dostupan sa interneta.** Peer-ovi se onda pokreću sa tim javnim tracker URL-om (za peer 2 na drugoj mreži: `START-PEER2.bat` će interaktivno pitati za tracker IP - tu se unese javna adresa/host trackera, ne LAN IP). Tracker već automatski beleži pravu javnu IP adresu svakog peer-a (`TrackerMain.resolveRemoteHost`/`chooseHost`), tako da tu nije potrebna dodatna konfiguracija na strani peer-a. Za sam tracker, dve isprobane opcije:
   - **Cloudflare Tunnel (najbrže, bez naloga)** - tracker ostaje da radi lokalno (`START-TRACKER.bat`), a u posebnom prozoru se pusti tunel ka njemu:
     ```powershell
     cloudflared tunnel --url http://localhost:8080
     ```
     (`cloudflared.exe` se skine sa [github.com/cloudflare/cloudflared/releases](https://github.com/cloudflare/cloudflared/releases), nije deo repozitorijuma). Ispiše javni `https://<nasumicno>.trycloudflare.com` link - taj link je tracker URL koji se daje peer-ovima na drugim mrežama. Cloudflare uz svaki zahtev šalje i pravu IP adresu posetioca u `Cf-Connecting-Ip` headeru, koji `resolveRemoteHost` čita umesto adrese `cloudflared`-a (koja bi inače uvek izgledala kao loopback). Link važi dok je tunel otvoren - dovoljno za demo poziv, nije trajno rešenje.
   - **Pravi VPS sa javnom IP adresom** (npr. Oracle Cloud Always Free) - trajnije rešenje, tracker se pokreće na serveru isto kao lokalno (`tracker/run.ps1`/ekvivalentna Linux komanda), samo treba otvoriti port `8080` u firewall-u servera.
2. **Svaki peer otvori svoj TCP port** (podrazumevano `9001`/`9002`) na sopstvenom ruteru, da bi drugi peer-ovi mogli direktno da mu se povežu za download. Ovo se pokušava automatski preko UPnP-a pri svakom pokretanju peer-a (`UpnpPortMapper`, koristi `weupnp` biblioteku iz `peer-node/lib/`) - u konzoli peer-a piše da li je mapiranje uspelo.
   - **Ako ruter ne podržava UPnP (ili je isključen)**: peer i dalje normalno radi, samo ispiše upozorenje. U tom slučaju treba ručno prosledi TCP port na ruter admin panelu (port forwarding / virtual server) ka mašini na kojoj peer radi, na isti broj porta koji je peer prijavio trackeru (`--tcp-port` / `-TcpPort`).

## Perzistencija na trackeru

Tracker čuva svoj peer/file registar u `tracker/data/peers.json` i korisničke naloge u `tracker/data/users.json` (obe datoteke se pišu na svaku izmenu, atomic write-then-rename). Restart trackera ne briše ništa - učitani peer-ovi koji se ne jave heartbeat-om u narednih 30s se ipak uklone istim mehanizmom kao i inače, tako da lista uvek odražava ko je stvarno živ.

## Funkcije

- Dodavanje fajla u `shared` folder iz kartice **Biblioteka**
- Pretraga fajlova preko trackera
- Direktno P2P preuzimanje sa prikazom napretka
- Pregled i otvaranje završenih fajlova iz kartice **Preuzimanja**

## Poznata ograničenja

- Nalozi/login (`UserStore`/`SessionStore`) postoje, ali nisu obavezni za pretragu/deljenje fajlova - peer/file API radi i bez naloga.
- Peer-ovi iza rutera bez UPnP-a i bez mogućnosti ručnog port-forwarda (npr. strogi CGNAT na mobilnim mrežama) trenutno nisu dohvatljivi spolja - hole punching nije implementiran.

## Provera

```powershell
cd tracker; .\test.ps1
cd peer-node; .\test.ps1
cd frontend; npm run build
```
