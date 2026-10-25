# ARK-launcherin tietosuojaseloste

*Päivitetty: 21.10.2026 · koskee ARK-launcherin versiota 0.8.0 ja uudempia
([version 0.7.11 seloste](https://github.com/jrs8205/ARK-launcher/blob/v0.7.11/PRIVACY.fi.md))*

*This policy in English: [PRIVACY.md](PRIVACY.md)*

ARK-launcher on ilmainen, avoimen lähdekoodin Android-kotinäyttö (Apache 2.0).
Tämä seloste kuvaa tarkalleen, mitä tietoja sovellus käsittelee. Lähdekoodi on
julkinen, joten jokaisen väitteen voi tarkistaa itse:
<https://github.com/jrs8205/ARK-launcher>.

## Lyhyt versio

- Kotinäytön asettelu, asetukset, sovelluslista ja ilmoitustiedot **pysyvät
  laitteellasi**. Sovelluksessa ei ole telemetriaa, analytiikkaa, mainoksia
  eikä minkäänlaista seurantaa.
- Verkkoa käytetään täsmälleen yhteen tarkoitukseen: sään näyttämiseen
  kellowidgetissä (mukaan lukien kuntanimen selvittäminen). Mikään muu
  sovelluksessa ei käytä internetiä.
- Sovelluksessa ei ole käyttäjätilejä, eikä se koskaan lähetä käyttötietoja,
  analytiikkaa tai mainostunnisteita kehittäjälle tai millekään kolmannelle
  osapuolelle.
- Versiosta 0.7.12 alkaen sovelluksessa ei ole sisäistä päivitintä eikä
  Google Drive -varmuuskopiointia: päivitykset tulevat valitsemastasi
  sovelluskaupasta (tai GitHubista), ja varmuuskopiot ovat paikallisia
  tiedostoja, jotka viet itse.

## Laitteelle tallennettavat tiedot

- **Kotinäytön asettelu** — kuvakkeiden, kansioiden ja widgetien sijainnit;
  tallennetaan paikalliseen tietokantaan.
- **Asetukset** — ruudukon koko, eleet, kuvakepaketti, piilotetut sovellukset,
  omat sovellusnimet, käytetyimmät-järjestys ja vastaavat; tallennetaan
  paikallisesti.
- **Ilmoitustiedot** — jos myönnät ilmoitusten käyttöoikeuden, ilmoituksia
  luetaan pistemerkkien, tilarivin kuvakkeiden sekä valinnaisten ilmoitus- ja
  henkilöwidgettien näyttämiseen ja niiden avaus-, vastaus- ja poistotoimintoihin.
  Samoista tiedoista näytetään sovelluksen uusimmat ilmoitukset sen pitkän
  painalluksen valikossa, viestisovelluksen pistemerkissä lähettäjän kuva,
  sovelluksen kuvakkeen lyhyt liike uuden ilmoituksen saapuessa sekä
  käynnissä olevat asiat (ajastimet, navigointi, puhelut, lataukset) elävänä
  pillerinä aloitusnäytöllä ja tehtäväpalkissa. Pitkällä painalluksella avattuna
  pilleri näyttää myös ilmoituksen pidemmän tekstin ja sen omat painikkeet (tauko,
  pysäytä...); painikkeen painaminen lähettää sen toiminnon sovellukselle, kuten
  ilmoitusvarjo tekisi. Kunkin voi poistaa käytöstä
  kohdassa Asetukset ▸ Ilmoitukset, eikä mitään niistä tallenneta.
  Viestitekstit käsitellään muistissa; launcher ei tallenna niitä pysyvästi eikä
  lähetä niitä minnekään. Vastaukset välitetään alkuperäisen sovelluksen kautta.
  Kiinnittämäsi henkilöt ja luomasi henkilöllisyyksien yhdistämiset tallennetaan
  asetuksiin ja paikallisiin varmuuskopioihin. Näihin voi sisältyä yhteystiedosta
  löytynyt nimi, numero, kuvaviite ja yhteystiedon URI. Valinnainen eräposti
  viivästyttää vain launcherin näkymiä; Androidin ilmoitukset, äänet ja
  ponnahdusilmoitukset toimivat normaalisti. Ilmoitusten tunnisteet ja jakoajat
  tallennetaan laitteelle odotustilan säilyttämiseksi uudelleenkäynnistyksessä,
  mutta niitä ei sisällytetä varmuuskopioihin.
- **Leikepöydän historia** — vain kun leikepöydän historia -widget on
  aloitusnäytöllä, Folark säilyttää 30 viimeksi kopioimaasi tekstiä, jotta ne
  voi näyttää ja kopioida uudelleen. Android antaa vain edessä olevan
  sovelluksen lukea leikepöytää, joten Folark katsoo sitä, kun aloitusnäyttö
  tulee näkyviin (Android voi silloin näyttää ilmoituksen leikepöydän
  lukemisesta). Tekstejä, jotka Android merkitsee arkaluonteisiksi, kuten
  salasanoja ja kertakoodeja, ei koskaan säilytetä. Lista tallennetaan
  laitteen yksityiseen tiedostoon, se ei kuulu varmuuskopioihin eikä sitä
  lähetetä minnekään; widgetin "Tyhjennä kaikki", leikkeen poisto tai
  sovelluksen poisto poistaa sen.
- **Varmuuskopiot** — Asetukset ▸ Varmuuskopiointi ja palautus kirjoittaa
  varmuuskopiotiedoston valitsemaasi sijaintiin, ja jos otat automaattisen
  varmuuskopion käyttöön, pitää yhden ajantasaisen kopion valitsemassasi
  kansiossa ja päivittää sen enintään kerran päivässä. Varmuuskopio sisältää
  molempien näyttöjen kotinäytön asettelun, kaikki asetukset (myös kiinnitetyt
  henkilöt ja käytetyimmät-järjestyksen) sekä kotinäytön taustakuvat — ei
  mitään muuta. Siihen ei koskaan tallenneta ilmoitusten tekstejä eikä
  odottavien ilmoitusten tilaa. Sovellus ei itse lähetä varmuuskopiota
  minnekään; jos valitset tiedostovalitsimessa pilvikansion, kyseinen sovellus
  tallentaa tiedoston. Varmuuskopio palautetaan vain, kun itse valitset sen,
  asetuksista tai uudelleenasennuksen jälkeen esittelystä. Sovellus on
  jättäytynyt Androidin järjestelmävarmuuskopioinnin ulkopuolelle
  (`allowBackup="false"`), joten sen tietoja ei koskaan kopioida Google-tilisi
  laitevarmuuskopioon eikä siirretä laitteelta toiselle; tallentamasi
  tiedostot ovat ainoat varmuuskopiot.

## Verkon käyttö

1. **Sää** (vain kun särivi on käytössä tai aloitusnäytöllä on säävidget, ja
   joko sijaintilupa on myönnetty tai olet kirjoittanut sään sijainnin):
   sovellus hakee säätilan ja ennusteen avaimettomasta
   [Open-Meteo](https://open-meteo.com)-rajapinnasta. Koordinaatit pyöristetään
   noin kilometrin tarkkuuteen ennen pyyntöä; tarkka sijainti ei koskaan
   poistu laitteelta. Jos kirjoitat paikkakunnan kohtaan Asetukset ▸ Sään
   sijainti, kyseinen teksti ja laitteen käyttöliittymäkieli lähetetään kerran
   Open-Meteon paikkahakuun koordinaattien selvittämiseksi, eikä laitteen omaa
   sijaintia sen jälkeen käytetä säähän lainkaan.
2. **Kuntanimi** sään vieressä: nimi selvitetään ensisijaisesti laitteen
   omalla geokooderilla. Jos laitteessa ei ole toimivaa geokooderia (joissakin
   malleissa ei ole), sovellus käyttää varalla avaimetonta
   [BigDataCloud](https://www.bigdatacloud.com)-käänteisgeokoodausrajapintaa
   ja lähettää sinne samat ~1 km:n tarkkuuteen pyöristetyt koordinaatit sekä
   laitteen käyttöliittymäkielen — enintään kerran 30 minuutin säähakua
   kohden, ja vain kun paikallinen geokooderi epäonnistui.

Tämä on koko lista. Asetusten "Tarkista päivitykset" -rivi vain avaa GitHubin
julkaisusivun selaimessa — sovellus itse ei tee päivitystarkistuksia eikä
latauksia.

Kumpikaan pyyntö ei sisällä tilitietoja, evästeitä tai mainostunnisteita.
Kuten kaikki internet-liikenne, ne väistämättä paljastavat IP-osoitteesi
palvelimelle; pyynnöt tunnistautuvat yleisluontoisella
`ARK-launcher/<versio>`-User-Agent-otsakkeella, jossa ei ole laitetietoja.

## Luvat

| Lupa | Käyttötarkoitus |
|---|---|
| `QUERY_ALL_PACKAGES` | Asennettujen sovellusten listaus ja käynnistys — launcherin ydintehtävä |
| `ACCESS_HIDDEN_PROFILES` | Android 15+: Yksityisen tilan (Private Space) näyttäminen sovelluslaatikossa erillisenä, lukittuna osiona. Lukittuna Folark ei listaa sen sovelluksia lainkaan; avattuna ne näkyvät vain tuossa osiossa (ei koskaan aloitusnäytöllä, haussa, widgeteissä tai käyttömäärissä). Lukitus ja avaus tapahtuvat Androidin kautta, joka kysyy näytön lukituksen. Mitään siitä ei tallenneta eikä lähetetä |
| Ilmoitusten käyttöoikeus (`BIND_NOTIFICATION_LISTENER_SERVICE`) | Pistemerkit (valinnaisesti lähettäjän kuvalla), tilarivin kuvakkeet, ilmoitus- ja henkilöwidgetit, ilmoitukset pitkän painalluksen valikossa, elävä toimintapilleri sekä paikallinen eräposti |
| Esteettömyyspalvelu (`BIND_ACCESSIBILITY_SERVICE`, valinnainen) | Tuplanapautuslukitus sekä, jos otat sen asetuksista käyttöön, Folarkin tehtäväpalkki avatulla näytöllä (palvelu piirtää tehtäväpalkin kulmien kosketusalueet ja sovelluskuvakkeet päällekkäisikkunoihin, käynnistää valitsemasi sovelluksen ja pyytää Androidia jakamaan näytön, kun vedät kuvakkeen jaettuun näyttöön). Kun pudotat Folarkin Viimeisimmät-näkymässä sovelluksen toisen päälle liitettyä näkymää varten, palvelu tekee yhden kosketuksen jaetun näytön jakajaan siirtääkseen sitä, kuten sormesi tekisi. Vain jos asetat sovelluskohtaisia sääntöjä kohdassa Asetukset ▸ Taskbar (piilota tai näytä aina valituissa sovelluksissa) tai otat käyttöön kohdan Asetukset ▸ Recents ▸ Korvaa Androidin Viimeisimmät, palvelulle kerrotaan, mikä sovellus tai näkymä tulee etualalle (sen paketin ja näkymän nimi, ei mitään muuta); tieto käytetään heti eikä sitä tallenneta. Kun Androidin Viimeisimmät-näkymä avautuu, palvelu siirtyy aloitusnäyttöön ja avaa sen sijaan Folarkin Viimeisimmät. Jos asennat Vectorin (LSPosed) ja otat siellä Folarkin moduulin käyttöön järjestelmän käynnistimelle, käynnistin avaa Folarkin Viimeisimmät itse; se kysyy Folarkilta vain, onko kytkin päällä (kyllä/ei). Jos otat moduulin käyttöön myös järjestelmän käyttöliittymälle (System UI), se kertoo Folarkille heti, kun lukitusnäyttö alkaa poistua, ja minkä sovelluksen se paljastaa (sen sovelluksen paketin nimi), jotta Folarkin animaatio lukituksen avaamisesta kotinäyttöön alkaa samaan tahtiin (Asetukset ▸ kumpikin näyttö ▸ Also when unlocking ▸ Sync with the lock screen). Tieto käytetään heti eikä sitä tallenneta; Folark säilyttää vain viimeisimmän ilmoituksen ajan näyttääkseen Asetuksissa, että moduuli on käytössä. Järjestelmän käyttöliittymässä moduuli ei koskaan muuta lukitusnäytön toimintaa. Tämän ilmoituksen lisäksi se muuttaa jaettua näyttöä vain, kun pyydät sitä Folarkilta: lisää kolmannen sovelluksen (Asetukset ▸ Recents ▸ Real three-app split, tallennetut sovellusparit kolmannella sovelluksella), jolloin Folark lähettää järjestelmän käyttöliittymälle sovelluksen tehtävänumeron tai linkin, joka avaa sovelluksen; tai vaihtaa jaetun näytön Androidin kurkistusnäkymään tai takaisin puoliksi (Viimeisimmät, tehtäväpalkin jaetun näytön valikko, kurkistus-sovellusparit). Järjestelmän käyttöliittymä vastaa kyllä tai ei. Järjestelmän käyttöliittymä hyväksyy pyynnön vain, kun Android vahvistaa sen tulleen Folarkilta; Folark säilyttää vain ajan, jolloin järjestelmän käyttöliittymä viimeksi ilmoitti olevansa valmis. Folarkin Viimeisimmät voivat avata vain sovellukset, joilla on lupa käynnistää sovelluksia Viimeisimmistä (järjestelmän käynnistin). Muuten palvelu ei vastaanota esteettömyystapahtumia eikä voi lukea näytön sisältöä, ja se on käynnissä vain, jos itse otat sen käyttöön |
| `DUMP` (valinnainen, vain itse suorittamallasi kertaluonteisella `adb shell pm grant` -komennolla) | Tehtäväpalkin jaettu näyttö: Folark lukee Androidin viimeisimpien tehtävien luettelosta juuri avaamansa sovelluksen tehtävänumeron ja lähettää Androidin jaetun näytön komennot; jaetun näytön painike lukee myös, mitkä sovellukset ovat jaetussa näytössä, jotta puolet voi vaihtaa tai yhden sovelluksen avata koko näytölle. Luetaan laitteella vain sillä hetkellä, eikä sitä tallenneta tai lähetetä minnekään. Sen avulla Folarkin oma Viimeisimmät-näkymä (Asetukset ▸ Recents) lukee myös, mitkä sovellukset ovat auki, ja tehtäväpalkki voi näyttää ne; Sulje kaikki käyttää Androidin omaa komentoa |
| Root (valinnainen, vain jos puhelimesi on rootattu ja sallit Folarkin root-hallinnassa) | Folarkin Viimeisimmät: Androidin tallentamat kuvakaappaukset avoimista sovelluksista (pidetään muistissa vain näkymän ollessa auki, eikä niitä kirjoiteta tai lähetetä minnekään) sekä yksittäisen sovelluksen sulkeminen. Kiinnitetyt sovellukset (Asetukset ▸ Recents) voidaan pitää muistissa rootilla: Folark vapauttaa ne akun optimoinnista, siirtää ne Androidin "aktiiviseen" ryhmään ja nostaa niiden prioriteettia, ja peruu vapautuksen, kun poistat kiinnityksen. Kumpikin on kytkettävissä kohdassa Asetukset ▸ Recents; ilman rootia nämä kaksi osaa vain puuttuvat. Asetukset ▸ Root voi lisäksi, kukin vain jos otat sen käyttöön: lukita näytön, avata Androidin Viimeisimmät ja vetää ilmoitusvarjon alas rootilla; muuttaa Androidin piilotettujen tilarivin kuvakkeiden luetteloa rootilla, jos WRITE_SECURE_SETTINGS-lupaa ei ole; sekä antaa Folarkille rootilla ADB:n tai Androidin asetussivujen sijaan tässä luetellut luvat (DUMP, käyttötietojen käyttö, WRITE_SECURE_SETTINGS, ilmoitusten käyttöoikeus, sen saavutettavuuspalvelu, widgettien lisääminen) tai asettaa sen oletuskotisovellukseksi. Ensimmäisellä käynnistyskerralla Luvat-sivu tarjoaa "Use root" -painikkeen: vain kun napautat sitä (ja root-hallinta sallii sen), Folark antaa itselleen kaikki nämä kerralla sekä sivun pyytämät luvat (kalenteri, yhteystiedot, puhelimen tila, sijainti, lähellä olevat laitteet) ja asettuu oletuskotisovellukseksi; jokaisen voi perua Androidin asetuksista. Root suorittaa vain kiinteitä komentoja Folarkin omilla nimillä; kun "Use root" on pois päältä, Folark ei koskaan pyydä rootia |
| Käyttötietojen käyttö (`PACKAGE_USAGE_STATS`, valinnainen) | Tehtäväpalkin viimeisimmät sovellukset: mitkä sovellukset on tuotu etualalle viime tunteina. Luetaan laitteella tehtäväpalkin näkyessä, eikä niitä tallenneta tai lähetetä minnekään. Vain jos sallit sen Androidin asetuksista |
| `WRITE_SECURE_SETTINGS` (valinnainen, vain itse suorittamallasi kertaluonteisella `adb shell pm grant` -komennolla) | Asetukset ▸ Käyttöoikeudet ▸ Järjestelmän säätimet: Folarkin oman saavutettavuuspalvelun käynnistäminen uudelleen, jos Android on sammuttanut sen, Androidin suurin virkistystaajuus, Androidin animaationopeus sekä luettelo tilarivin kuvakkeista, jotka Android piilottaa (Asetukset ▸ Appearance ▸ Android's status bar icons; vain siellä tarjottuja kuvakkeita muutetaan). Folark muuttaa vain näitä asetuksia ja vain valinnastasi; niistä luetaan vain nykyinen arvo, eikä mitään lähetetä minnekään |
| `READ_CALENDAR` | Seuraavan tapahtuman näyttäminen kellowidgetissä |
| `READ_CONTACTS` | Yhteystulokset sovellushaussa ja henkilöwidgetin yhteystietojen yhdistäminen (valinnainen) |
| `READ_PHONE_STATE` | Signaalinvoimakkuus tilarivillä |
| `SET_ALARM` | Hakutoiminnot: "timer 10 min" tai "alarm 7:30" antaa kellosovellukselle ajastimen tai herätyksen, jonka se näyttää vahvistettavaksi. Folark ei lue herätyksiä. Hakutoiminnot (ajastimet, herätykset, tapahtumat, reittiohjeet, sähköposti, muistiinpanot, puhelinnumerot) vain avaavat sopivan sovelluksen kirjoitettu teksti valmiiksi täytettynä; Folark ei tallenna eikä lähetä kirjoitettua tekstiä |
| `ACCESS_COARSE_LOCATION` | Sää kellowidgetissä |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `BLUETOOTH` (≤ Android 11) | Yhteysilmaisimet tilarivillä |
| `INTERNET` | Yllä kuvatut sääpyynnöt |
| `REQUEST_DELETE_PACKAGES` | Kuvakkeen pitkän painalluksen "poista asennus" -toiminto |
| `EXPAND_STATUS_BAR` | Ilmoitusten avaus alaspyyhkäisyllä |

Kalenteri-, yhteystieto- ja puhelintilatiedot luetaan tarvittaessa yllä
kuvattuja ominaisuuksia varten, niitä käytetään vain laitteella, eikä niitä
koskaan tallenneta muistivälimuistia pidemmälle tai lähetetä minnekään.
Sijaintia käsitellään laitteella samalla tavalla; ainoa laitteelta poistuva
sijaintitieto on ~1 km:n tarkkuuteen pyöristetyt koordinaatit, jotka
lähetetään kohdassa *Verkon käyttö* kuvatuille sää- ja
käänteisgeokoodauspalveluille.

## Lapset

ARK-launcher on yleiskäyttöinen apuohjelma, jossa ei ole omaa sisältöä,
mainoksia eikä ostoja. Sitä ei ole suunnattu lapsille, eikä se tietoisesti
kerää henkilötietoja lapsilta — eikä keneltäkään muultakaan: sovelluksessa ei
ole tilejä, eikä se kerää henkilötietoja lainkaan.

## Muutokset ja yhteydenotot

Tähän selosteeseen tehtävät muutokset tehdään julkisessa repositoriossa, jossa
koko muutoshistoria on näkyvissä. Kysymykset ja ilmoitukset:
<https://github.com/jrs8205/ARK-launcher/issues>.
