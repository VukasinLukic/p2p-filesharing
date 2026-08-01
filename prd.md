Product Requirements Document (PRD): P2P File Sharing Application
1. Pregled Proizvoda (Product Overview)
Sistem je hibridna Peer-to-Peer (P2P) aplikacija za deljenje fajlova, razvijena za potrebe univerzitetskog projekta. Cilj aplikacije je da omogući korisnicima pretragu i preuzimanje fajlova u mreži, pri čemu se otkrivanje korisnika vrši preko centralnog servera (Tracker), a sam transfer fajlova obavlja se direktno između dva klijenta (Peer-to-Peer).

2. Arhitektura i Tehnološki Stek

Tracker (Central Registry): Java (Spring Boot) - pruža REST API.

Peer Node (Core Engine): Java - upravlja mrežnim soketima i lokalnim fajlovima; izlaže lokalni REST API.

Frontend (GUI): React.js - komunicira isključivo sa lokalnim Peer Node API-jem.

3. Funkcionalni Zahtevi (Core Requirements)
3.1. Tracker (Centralni Registar)
Tracker je isključivo informativni čvor. Kroz njega ne prolaze fajlovi.

Registracija čvorova: Sistem mora omogućiti novim Peer-ovima da prijave svoje prisustvo na mreži.

Indeksiranje fajlova: Sistem mora primiti i sačuvati spisak fajlova koje određeni Peer nudi za deljenje.

Pretraga: Sistem mora omogućiti pretragu dostupnih fajlova po nazivu.

Uparivanje (Peer Discovery): Na zahtev za određenim fajlom, sistem mora vratiti mrežne podatke (IP adresa i port) svih Peer-ova koji trenutno poseduju taj fajl.

Održavanje stanja (Heartbeat): Sistem mora detektovati kada Peer napusti mrežu i automatski ukloniti njega i njegove fajlove iz registra.

3.2. Peer Node (Lokalni Klijent/Server)
Peer je aplikacija koja se vrti na računaru korisnika.

Inicijalizacija: Pri pokretanju, Peer mora skenirati svoj definisani lokalni deljeni direktorijum i poslati metapodatke o fajlovima Tracker-u.

Pružanje fajlova (Upload): Peer mora aktivno slušati dolazne zahteve od drugih Peer-ova i biti sposoban da paralelno šalje delove fajlova višestrukim korisnicima bez blokiranja.

Preuzimanje fajlova (Download): Na komandu sa GUI-ja, Peer mora otvoriti direktnu konekciju ka ciljnom Peer-u i preuzeti tok bajtova u lokalni folder.

Integritet podataka: Peer mora izračunati i uporediti hash vrednost (MD5 ili SHA-256) preuzetog fajla sa originalnom vrednošću kako bi potvrdio ispravnost transfera.

Lokalni API: Peer mora izložiti lokalni interfejs ka React GUI-ju za obradu komandi (pretraga, preuzimanje) i praćenje statusa (procenat preuzimanja).

3.3. Korisnički Interfejs (Frontend GUI)
Korisnik interaguje samo sa ovim delom sistema.

Pretraga i Mreža: Ekran sa pretraživačem i prikazom rezultata (ime fajla, veličina, dostupnost).

Upravljanje preuzimanjima: Nadzorna tabla (Dashboard) sa listom aktivnih prenosa. Mora sadržati dinamičke progress barove, prikaz brzine i statusne indikatore (In progress, Completed, Failed).

Lokalna biblioteka: Ekran koji prikazuje fajlove koje korisnik trenutno nudi mreži.

Status mreže: Vizuelni indikator koji pokazuje da li je lokalni čvor uspešno povezan sa Tracker-om.

4. UI/UX i Vizuelni Zahtevi
Dizajnerski jezik: Korisnički interfejs mora imati čist, pregledan i alternativni vizuelni identitet.

Estetika: Potrebno je integrisati elemente 2000s/Y2K stila, prilagođene modernim standardima.

Komponente: Obavezna je implementacija naprednih UI efekata, sa posebnim fokusom na "glassmorphism" (efekat zamućenog stakla) u panelima, karticama i animacijama modala.

5. Kriterijumi za uspeh iteracije (Definition of Done)
Tracker uspešno čuva u memoriji listu aktivnih korisnika.

Peer uspešno pronalazi drugog Peer-a preko Trackera.

Fajl od 10MB je uspešno i bez oštećenja prenesen sa Računara A na Računar B preko direktne Socket konekcije.

GUI jasno i u realnom vremenu komunicira sa lokalnim Java endžinom i vizuelno odražava napredak transfera.