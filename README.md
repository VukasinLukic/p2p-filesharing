# P2P File Sharing

Aplikacija koristi tracker za pronalaženje peer-ova, a fajlove prenosi direktno između peer-ova preko TCP veze.

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

## Funkcije

- Dodavanje fajla u `shared` folder iz kartice **Biblioteka**
- Pretraga fajlova preko trackera
- Direktno P2P preuzimanje sa prikazom napretka
- Pregled i otvaranje završenih fajlova iz kartice **Preuzimanja**

Za LAN transfer Windows Firewall mora dozvoliti Java aplikaciji privatnu mrežu, posebno TCP port `9001` na računaru Peer 1.

## Provera

```powershell
cd tracker; .\test.ps1
cd peer-node; .\test.ps1
cd frontend; npm run build
```
