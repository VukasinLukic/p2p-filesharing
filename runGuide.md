PRIPREMA (dan pre, ne na samom pozivu)
Ti: skini cloudflared.exe (uputstvo od malopre) i stavi ga u npr. C:\cloudflared\.
Ti: pokreni START-PEER1.bat jednom i pogledaj u tom prozoru da li piše:
[UPnP] mapped external TCP port 9001... → super, radi automatski.
[UPnP] no UPnP-capable gateway found... → moraš ručno na svom ruteru (admin panel, obično 192.168.1.1) da otvoriš TCP port 9001 ka svom računaru (port forwarding / virtual server). Bez ovoga Teodora neće moći da preuzme fajl od tebe.
Ti i Teodora: povucite najnoviju verziju sa GitHub-a (git pull) da imate iste izmene.
NA SAMOM POZIVU
Korak	Ko	Šta radi
1	Ti	Duplo klik START-TRACKER.bat. Ostavi prozor otvoren.
2	Ti	Novi PowerShell prozor → cd C:\cloudflared → .\cloudflared.exe tunnel --url http://localhost:8080. Sačekaj link (https://...trycloudflare.com). Ostavi prozor otvoren.
3	Ti	Taj link pošalji Teodori na chat (Viber/WhatsApp/Discord — gde god ste vezi tokom poziva).
4	Ti	Ubaci fajl koji deliš u folder peer-node\shared-a\ pre sledećeg koraka (ili posle, pa ga dodaš kroz Biblioteka → Upload u frontendu).
5	Ti	Duplo klik START-PEER1.bat. Otvoriće se i frontend u browseru.
6	Teodora	Duplo klik START-PEER2.bat. Kad zatraži "Tracker IP" — unosi tvoj link iz koraka 2 (ceo, sa https://, bez :8080 na kraju).
7	Teodora	U frontendu koji joj se otvori: kartica Pretraga, ukuca ime fajla, klikne Preuzmi.
8	Teodora	Kartica Preuzimanja — vidi da je fajl stigao 100%.
Šta ostaje upaljeno ceo poziv: kod tebe — tracker prozor, cloudflared prozor, peer1 prozor; kod Teodore — peer2 prozor.

Ako download stane na 0% — najverovatnije port 9001 nije otvoren kod tebe (korak 2 iz pripreme). Rezervna opcija: profesoru unapred snimite video kao što je i sam predložio, za slučaj da port-forward na tvojoj mreži ne uspe na licu mesta.