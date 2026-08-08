Odraditi da se pravi baza i da se u njoj cuvaju podaci o korisnicima loginovi i ceo little social deo . 

Odraditi da imamo Hesiranje po delovima fajlova. Da ne moramo da proveravamo da li je ceo poslat fajl dobar . Nego samo deo . 

promt claude code:
Molim te da unaprediš postojeći P2P File Sharing radni projekat tako da omogućiš jednostavno pokretanje na "jedan klik" (bez kucanja po terminalu) i dinamičko podešavanje mreže u samom React GUI-ju.

### 📋 Postojeće stanje projekta:
Projekat se sastoji od tri dela:
1. `tracker/` — Java 17 centralni registar (port 8080)
2. `peer-node/` — Java 17 P2P klijent/server (TCP transfer + HTTP REST API na npr. 7001 ili 7002)
3. `frontend/` — React (Vite) korisnički interfejs

---

### 🎯 Zahtevi za implementaciju:

#### 1. "1-Click" Skripte za pokretanje (Windows `.bat` & `.ps1`)
Kreiraj u korenskom direktorijumu projekta sledeće automatizovane skripte koje se pokreću dvostrukim klikom:
- **`START-TRACKER.bat`**: Pokreće samo Tracker na portu 8080.
- **`START-MOJ-PEER.bat`**: Pokreće Peer A (sa folderima `./peer-node/shared-a`, `./peer-node/downloads-a`, TCP 9001, HTTP 7001) i automatski otvara frontend u pretraživaču.
- **`START-KOLEGINICA-LAN-PEER.bat`**: Interaktivna batch skripta koja pri pokretanju pita za IP adresu računara gde radi Tracker (ili je učitava iz lokalnog podešavanja), a zatim pokreće Peer B i otvara frontend.
- **`START-LOKALNI-DEMO.bat`**: Pokreće sve odjednom na jednom računaru za potrebe demo prezentacije (Tracker, Peer A, Peer B i Frontend u zasebnim prozorima).

#### 2. GUI Podešavanja mreže u React-u (`frontend/src`)
Unapredi React aplikaciju u `frontend/src`:
- U komponentski fajl `StatusHeader.jsx` dodaj dugme sa zupčanikom za **"Podešavanja Mreže" (Network Settings)**.
- Omogući u modalnom prozoru da korisnik u samoj aplikaciji može:
  1. Da vidi trenutnu IP adresu/port svog lokalnog Peer-a.
  2. Da promeni i sačuva u `localStorage` port ili URL lokalnog Peer-a (npr. prebacivanje sa `http://localhost:7001/api` na `http://localhost:7002/api`).
  3. Da proveri status konekcije ka Trackeru sa mogućnošću ručnog osvežavanja (Refresh Connection).

#### 3. Beleške za nadogradnju (referenca na `noveStvari.md`)
Proveri fajl `noveStvari.md` i pripremi arhitektonske osnove ili komponente za:
- **Per-chunk Hashing / Verifikacija po delovima**: Pripremi API i logiku u Peer Node-u da u budućnosti deli fajl na blokove (npr. 512KB) i proverava SHA-256 po bloku.
- **Jednostavna baza korisnika i prijava (Social/Auth)**: Pripremi SQLite/H2 ili JSON storage strukturu za osnovnu autentičnost korisnika i profila.

Molim te da proveriš sve izmene, osiguraš da postojeća skripta i automatski testovi i dalje prolaze (`.\test.ps1` u `tracker` i `peer-node`), i sačuvaš funkcionalnost bez narušavanja postojećeg koda.
