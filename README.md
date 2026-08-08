# P2P File Sharing

Java i React aplikacija za deljenje fajlova u lokalnoj mreži. Tracker vodi evidenciju aktivnih peer-ova i njihovih fajlova, dok se sam prenos odvija direktno između peer-ova preko TCP konekcije.

## Komponente

- `tracker` — centralni registar peer-ova, port `8080`
- `peer-node` — lokalni REST API i TCP server za prenos fajlova
- `frontend` — React korisnički interfejs, port `8888`

## Pokretanje

Potreban je JDK 17+ i Node.js 18+.

Na računaru koji deli fajl:

1. Pokrenuti `START-TRACKER.bat`.
2. Pokrenuti `START-MOJ-PEER.bat`.

Na drugom računaru:

1. Pokrenuti `START-KOLEGINICA-LAN-PEER.bat`.
2. Uneti LAN IP adresu računara na kojem je tracker.

Peer A koristi HTTP port `7001` i TCP port `9001`. Peer B koristi HTTP port `7002` i TCP port `9002`.

## Korišćenje

- Kartica **Biblioteka** prikazuje lokalne fajlove i omogućava dodavanje fajla u `shared` folder.
- Kartica **Pretraga** pronalazi fajlove koje nude drugi peer-ovi.
- Kartica **Preuzimanja** prikazuje napredak transfera i sadržaj lokalnog `downloads` foldera.
- Zeleno svetlo označava da je lokalni peer povezan sa trackerom.

Za LAN prenos Windows Firewall mora dozvoliti Java aplikaciji privatnu mrežu, uključujući TCP port `9001` na računaru Peer A.

## Provera

```powershell
cd tracker; .\test.ps1
cd peer-node; .\test.ps1
cd frontend; npm run build
```
