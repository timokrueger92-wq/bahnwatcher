# BahnWatcher – Übergabe-Notiz für Claude Code

## Projektübersicht
BahnWatcher ist eine **DSGVO-konforme PWA** (Progressive Web App) als einzelne HTML-Datei (`bahnwatcher.html`).
Kein Backend, kein Server, keine Kosten. Alles läuft im Browser.

---

## Technischer Stack
- **Frontend:** Vanilla HTML/CSS/JavaScript (eine einzige Datei)
- **Bahndaten:** [transport.rest](https://v6.db.transport.rest) – kostenlose Open-Source DB-API, kein API-Key
- **Push:** [ntfy.sh](https://ntfy.sh) – kostenloser Push-Dienst, kein Account nötig
- **Speicherung:** `localStorage` (Key: `bw4`) – nur lokal auf dem Gerät
- **Fonts:** Systemschriften (keine Google Fonts – DSGVO!)
- **PWA:** Inline Service Worker via Blob URL, Inline Manifest via data URI

---

## Nutzerkontext
- Fährt die Strecke **Mainz-Kostheim → Karlsruhe** (Fernverkehr + Regional)
- Relevante Stationen: Mainz, Wiesbaden, Frankfurt, Mannheim, Heidelberg, Ludwigshafen, Neustadt/Weinstraße, Bruchsal, Germersheim, Karlsruhe
- Lokal aktiv in: **Mainz** (Bus/Tram MVG/RMV), **Karlsruhe** (KVV Stadtbahn), **Bruchsal** (S3/S32)
- Nutzt **Android**, will App über **GitHub Pages** hosten und als PWA installieren

---

## App-Struktur (4 Tabs)

### 1. Favoriten
- Gespeicherte Verbindungen mit Echtzeit-Status (pünktlich / verspätet / Ausfall)
- Monitoring-Toggle: prüft alle 2 Min automatisch per `setInterval`
- Statusfarben: grün / gelb / rot mit Indikatorbalken links
- Buttons: „Jetzt prüfen", „🚶 Alternativen", „🗑 Löschen"

### 2. Verbindungen suchen (Haupt-Feature)
- Von/Nach mit **Autocomplete-Dropdown** (transport.rest `/locations`)
- Datum + Uhrzeit wählbar, Toggle Abfahrt/Ankunft
- Suche via transport.rest `/journeys`
- Ergebnisse zeigen: Linie, Umstieg, Dauer, Echtzeit-Verspätung
- Jede Verbindung hat **„＋ Speichern"-Button** → direkt als Favorit anlegen
- Monitoring-Zeitfenster wird automatisch auf ±30 Min um Abfahrtszeit gesetzt

### 3. 🚶 Alternativen
- Findet Haltestellen im Gehradius (5/10/15 Min wählbar, ~75m/min)
- Prüft ob Abfahrt noch erreichbar ist (Gehzeit + 2 Min Puffer)
- 🟢 / 🟡 / 🔴 je nach Zeitpuffer
- 🗺️-Button öffnet Google Maps Walking-Route
- GPS via `navigator.geolocation` (nur mit Consent)
- Kann auch von Favoriten-Card aus geöffnet werden (Startpunkt vorausgefüllt)

### 4. Einstellungen
- ntfy.sh Kanalname + Test-Push
- Monitoring Toggle
- Verspätungsschwelle (3/5/10 Min)
- Normalisierungs-Alert Toggle
- Link zur Datenschutzerklärung
- „Alle Daten löschen"

---

## DSGVO-Implementierung

### Consent Layer (beim ersten Start)
- Zeigt sich vor App-Start
- Technisch notwendig (localStorage, transport.rest): nicht abwählbar
- Optional: GPS (Art. 9 DSGVO), ntfy.sh
- Einwilligung gespeichert unter `localStorage('bw_consent')`
- Widerruf möglich → löscht alle Daten + reload

### Consent-Gates im Code
```javascript
consent = { given: bool, gps: bool, ntfy: bool }
requestGPS()        // prüft consent.gps vor Geolocation-Aufruf
testPush()          // prüft consent.ntfy vor ntfy-Aufruf
```

### Datenschutzerklärung
- Eigene Seite (Tab `page-privacy`) mit Rechtsgrundlagen
- Tabelle aller verarbeiteten Daten
- Widerrufs-Button

---

## Bekannte Probleme / Offene Punkte

### 🔴 Kritisch
- **CORS-Problem:** API funktioniert nur wenn die Datei von einem echten Webserver (https) geöffnet wird, nicht als `file://`. Lösung: GitHub Pages oder lokaler Server (`python3 -m http.server`)
- **Service Worker:** Blob-URL für SW funktioniert nicht in allen Browser-Kontexten. Für echte PWA auf GitHub Pages sollte `sw.js` als separate Datei ausgelagert werden.

### 🟡 Verbesserungswürdig
- Manifest ist als data-URI eingebettet – für echte PWA-Installation besser als `manifest.json` auslagern
- Favoriten haben keine Bearbeitungsfunktion (Zeiten/Tage nach dem Speichern ändern)
- Keine Sortierung der Favoriten
- Alternativensuche lädt alle Stops sequenziell – könnte mit `Promise.all` optimiert werden (teilweise schon, aber max. 8 Stops)
- ntfy.sh sendet an US-Server by default – für volle DSGVO-Konformität EU-Server in Einstellungen wählbar machen

### 🟢 Nice to have
- Dark/Light Mode Toggle
- Mehrsprachigkeit (DE/EN)
- Verbindung teilen (Share-API)
- Widget für Android (nicht möglich als PWA, bräuchte echte App)
- Offline-Fahrplan für Stammstrecken cachen

---

## Datenstruktur (localStorage)

```javascript
// Key: 'bw4'
{
  favorites: [{
    id: 'fav_1234567890',
    name: 'Pendelstrecke Hin',
    fromId: '8000240',      // transport.rest Station-ID
    fromName: 'Mainz Hbf',
    toId: '8000191',        // transport.rest Station-ID
    toName: 'Karlsruhe Hbf',
    tf: '06:30',            // Zeitfenster von
    tt: '09:00',            // Zeitfenster bis
    days: [1,2,3,4,5]       // 0=So, 1=Mo ... 6=Sa
  }],
  settings: {
    ntfyChannel: 'mein-kanal-42',
    monitoring: false,
    delayThreshold: 5,      // Minuten
    notifyNorm: true
  }
}

// Key: 'bw_consent'
{ given: true, gps: true, ntfy: false }

// Key: 'bw_install_dismissed'
'1'  // PWA-Banner wurde weggeklickt
```

---

## transport.rest API Endpoints

```
Basis-URL: https://v6.db.transport.rest

Haltestellen suchen:
GET /locations?query=Mainz&results=8&stops=true&addresses=false&poi=false

Verbindungen:
GET /journeys?from=ID&to=ID&departure=ISO8601&results=6&language=de
GET /journeys?from=ID&to=ID&arrival=ISO8601&results=6&language=de

Abfahrten:
GET /stops/ID/departures?when=ISO8601&duration=60&results=20&language=de

Nearby:
GET /stops/nearby?latitude=49.0&longitude=8.4&distance=800&results=12
```

---

## Empfohlene nächste Schritte in Claude Code

### 🎯 Ziel: Fertige PWA auf GitHub Pages (Option A)

**Schritt 1 — Projekt aufteilen**

Gewünschte Struktur:
```
bahnwatcher/
├── index.html       # Struktur
├── app.js           # App-Logik
├── styles.css       # Styles
├── sw.js            # Service Worker (echte Datei, kein Blob mehr)
├── manifest.json    # PWA Manifest (echte Datei, kein data-URI mehr)
├── icons/
│   ├── icon-192.png
│   └── icon-512.png
└── UEBERGABE.md
```

**Schritt 2 — Service Worker richtig aufsetzen**

`sw.js` soll:
- App-Shell cachen (HTML/CSS/JS) → offline nutzbar
- API-Requests (transport.rest) immer live → nie gecacht (Echtzeitdaten!)
- Automatisches Update beim neuen Deploy

**Schritt 3 — Lokal testen**
```bash
python3 -m http.server 8080
# Chrome → http://localhost:8080
# DevTools → Application → Manifest & Service Worker prüfen
```

**Schritt 4 — GitHub Pages deployen**
```bash
git init
git add .
git commit -m "BahnWatcher PWA v1.0"
git remote add origin https://github.com/USERNAME/bahnwatcher.git
git push -u origin main
# → GitHub: Settings → Pages → Branch: main → Save
```

**Schritt 5 — Auf Android installieren**
1. `https://USERNAME.github.io/bahnwatcher/` in Chrome öffnen
2. Menü `⋮` → „App installieren"
3. BahnWatcher erscheint als Icon auf dem Homescreen 🚆

---

### ✂️ Prompt für Claude Code (einfach kopieren)

```
Ich habe eine BahnWatcher PWA die wir in Claude.ai entwickelt haben.
Lies bitte UEBERGABE.md für den vollständigen Kontext und bahnwatcher.html
für den aktuellen Code.

Aufgabe: Mach daraus eine vollständige, installierbare PWA für GitHub Pages.

1. Teile die Datei auf in: index.html, app.js, styles.css, sw.js, manifest.json
2. Erstelle echte PWA-Icons (192x192 und 512x512) als PNG
3. Service Worker: App-Shell cachen, API-Requests immer live
4. Teste lokal mit python3 -m http.server 8080
5. Stelle sicher dass Autocomplete und Verbindungssuche funktionieren

Ziel: Funktionierende PWA auf GitHub Pages, installierbar auf Android.
```

---

*Erstellt im Claude.ai Chat · Stand März 2026*
