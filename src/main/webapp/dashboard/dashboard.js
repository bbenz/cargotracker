/*
 * Shipment dashboard for Cargo Tracker. It reads /rest/dashboard, listens to the existing
 * server-sent events at /rest/cargo, and asks the guarded assistant at /rest/assistant.
 */
(function () {
  'use strict';

  const API = '../rest';
  const OFFSETS = [-360, 0, 360];
  const HOUR = 3600000;

  const STATUS = {
    ONBOARD_CARRIER: { label: 'In transit', tone: 'transit' },
    IN_PORT: { label: 'In port', tone: 'port' },
    NOT_RECEIVED: { label: 'Booked', tone: 'booked' },
    AT_DESTINATION: { label: 'Arrived', tone: 'done' },
    CLAIMED: { label: 'Delivered', tone: 'done' },
    MISDIRECTED: { label: 'Misdirected', tone: 'critical' },
    MISROUTED: { label: 'Misrouted', tone: 'critical' },
    NOT_ROUTED: { label: 'Not routed', tone: 'warning' },
    UNKNOWN: { label: 'Unknown', tone: 'warning' }
  };
  const COLORS = {
    transit: '#22d3ee', port: '#34d399', booked: '#a78bfa', done: '#94a3b8',
    warning: '#fbbf24', critical: '#fb7185', highlight: '#facc15'
  };
  const RANK = { critical: 4, warning: 3, transit: 2, port: 1, booked: 1, done: 0 };
  const OUTCOMES = {
    ANSWERED: 'Answered',
    REFUSED: 'Refused by the input guard',
    CHECK_FAILED: 'Stopped by the output check',
    FALLBACK: 'Fallback',
    OFF: 'Assistant off'
  };
  const SUGGESTIONS = [
    ['Needs attention', 'Which shipments need attention, and why?'],
    ['JKL567', 'Where is JKL567, and what should we do next?'],
    ['Rotterdam notice', 'Which shipments are affected by the Rotterdam port notice?'],
    ['To Helsinki', 'What cargo is going to Helsinki?'],
    ['DEF789 deadline', 'What is the deadline for DEF789?'],
    ['Off-topic test', 'Write a poem about cats']
  ];
  // Port labels that sit on the left, so they do not collide with nearby ports or ships.
  const LABEL_LEFT = new Set(['CNHGH', 'USLAX', 'USNYC', 'JNTKO']);
  // Ships that wait in port are drawn this many pixels above the port.
  const DOCKED_OFFSET = 14;
  const FILTER_LABELS = {
    all: 'All shipments', transit: 'In transit', port: 'In port', booked: 'Booked',
    done: 'Delivered', attention: 'Needs attention'
  };

  const state = {
    ports: {},
    voyages: {},
    shipments: [],
    byId: new Map(),
    notices: [],
    traceUrl: null,
    clockOffset: 0,
    filter: 'all',
    search: '',
    highlighted: new Set(),
    selected: null,
    projection: 0,
    pings: new Map(),
    kpis: {},
    loaded: false
  };

  // ---------- Helpers

  function $(id) { return document.getElementById(id); }

  function esc(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function now() { return Date.now() + state.clockOffset; }
  function viewTime() { return now() + state.projection * HOUR; }

  function fmtDate(ms) {
    if (ms == null) { return ''; }
    return new Date(ms).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  }

  function fmtDay(iso) {
    if (!iso) { return ''; }
    return new Date(iso + 'T00:00:00').toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  }

  function fmtClock(ms) {
    return new Date(ms).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  }

  function rel(ms) {
    if (ms == null) { return ''; }
    const diff = ms - now();
    const abs = Math.abs(diff);
    let text;
    if (abs < HOUR) { text = Math.max(1, Math.round(abs / 60000)) + ' min'; }
    else if (abs < 48 * HOUR) { text = Math.round(abs / HOUR) + ' h'; }
    else { text = Math.round(abs / (24 * HOUR)) + ' days'; }
    return diff >= 0 ? 'in ' + text : text + ' ago';
  }

  function seconds(ms) { return ms == null ? '-' : (ms / 1000).toFixed(1) + ' s'; }

  function portName(code) { return state.ports[code] ? state.ports[code].name : code; }

  function tone(s) {
    if (s.attention) { return s.attention.level === 'critical' ? 'critical' : 'warning'; }
    return (STATUS[s.status] || STATUS.UNKNOWN).tone;
  }

  function statusLabel(s) {
    if (s.attention && s.attention.issue === 'LATE') { return 'Late'; }
    return (STATUS[s.status] || STATUS.UNKNOWN).label;
  }

  function category(s) {
    if (s.status === 'CLAIMED' || s.status === 'AT_DESTINATION') { return 'done'; }
    if (s.transportStatus === 'ONBOARD_CARRIER') { return 'transit'; }
    if (s.transportStatus === 'NOT_RECEIVED') { return 'booked'; }
    if (s.transportStatus === 'IN_PORT') { return 'port'; }
    return 'other';
  }

  function matchesFilter(s) {
    if (state.filter === 'all') { return true; }
    if (state.filter === 'attention') { return !!s.attention; }
    return category(s) === state.filter;
  }

  function matchesSearch(s) {
    const q = state.search.trim().toLowerCase();
    if (!q) { return true; }
    return [s.trackingId, s.originName, s.destinationName, s.locationName]
      .some(function (v) { return v && v.toLowerCase().indexOf(q) >= 0; });
  }

  function worstTone(items) {
    let best = null;
    items.forEach(function (s) {
      const t = tone(s);
      if (best === null || RANK[t] > RANK[best]) { best = t; }
    });
    return best || 'port';
  }

  function verb(type) {
    return { RECEIVE: 'Receive in', LOAD: 'Load in', UNLOAD: 'Unload in', CLAIM: 'Pickup in', CUSTOMS: 'Customs in' }[type] || type;
  }

  function describeEvent(e) {
    switch (e.type) {
      case 'RECEIVE': return 'Received in ' + e.locationName;
      case 'LOAD': return 'Loaded in ' + e.locationName + ' onto ' + e.voyage;
      case 'UNLOAD': return 'Unloaded in ' + e.locationName + ' from ' + e.voyage;
      case 'CLAIM': return 'Picked up in ' + e.locationName;
      case 'CUSTOMS': return 'Customs in ' + e.locationName;
      default: return e.type + ' in ' + e.locationName;
    }
  }

  function nextStep(s) {
    const n = s.nextActivity;
    if (!n) { return s.attention ? 'Needs a decision' : ''; }
    return verb(n.type) + ' ' + n.locationName + (n.voyage ? ' on ' + n.voyage : '') + (n.time ? ', ' + rel(n.time) : '');
  }

  function whereNow(s) {
    const p = s._pos;
    if (s.transportStatus === 'ONBOARD_CARRIER' && p) {
      if (p.from && p.to) {
        const how = p.mode === 'rail' ? 'By rail' : p.mode === 'air' ? 'By air' : 'At sea';
        return how + ', ' + portName(p.from) + ' to ' + portName(p.to);
      }
      if (p.docked && p.at) { return 'On ' + s.voyage + ', in port at ' + portName(p.at); }
    }
    return s.locationName;
  }

  function toast(message, kind) {
    const box = $('toast');
    box.textContent = message;
    box.className = 'toast ' + (kind || '');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(function () { box.className = 'toast hidden'; }, kind === 'info' ? 3500 : 7000);
  }

  function showModal(title, text) {
    $('modal-title').textContent = title;
    $('modal-text').textContent = text;
    $('modal').className = 'modal';
  }

  function hideModal() { $('modal').className = 'modal hidden'; }

  function getJson(path) {
    return fetch(API + path, { headers: { Accept: 'application/json' } }).then(function (r) {
      if (!r.ok) { throw new Error('HTTP ' + r.status); }
      return r.json();
    });
  }

  function sendJson(method, path, body) {
    return fetch(API + path, {
      method: method,
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    }).then(function (r) {
      if (!r.ok) { throw new Error('HTTP ' + r.status); }
      return r.json();
    });
  }

  // ---------- Map

  const map = L.map('map', {
    zoomSnap: 0.25,
    zoomDelta: 0.5,
    minZoom: 1.5,
    maxZoom: 7,
    worldCopyJump: false,
    maxBounds: [[-80, -420], [85, 420]],
    maxBoundsViscosity: 0.7
  });
  map.attributionControl.setPrefix(false);
  map.attributionControl.addAttribution(
    'Land and lakes: <a href="https://www.naturalearthdata.com/" target="_blank" rel="noopener">Natural Earth</a>');
  [['land', 250], ['lanes', 410], ['routes', 420], ['rings', 630], ['ports', 640], ['ships', 660], ['notices', 670]]
    .forEach(function (pane) {
      map.createPane(pane[0]);
      map.getPane(pane[0]).style.zIndex = pane[1];
    });
  map.getPane('rings').style.pointerEvents = 'none';

  const landRenderer = L.canvas({ pane: 'land', padding: 0.3 });
  const layers = {
    lanes: L.layerGroup().addTo(map),
    routes: L.layerGroup().addTo(map),
    rings: L.layerGroup().addTo(map),
    ports: L.layerGroup().addTo(map),
    ships: L.layerGroup().addTo(map),
    notices: L.layerGroup().addTo(map)
  };

  map.fitBounds([[-42, -122], [62, 150]]);
  map.on('zoomend', function () {
    map.getContainer().classList.toggle('zoom-low', map.getZoom() < 3);
  });
  map.getContainer().classList.toggle('zoom-low', map.getZoom() < 3);
  map.on('click', function () { select(null); });

  function drawGraticule() {
    const style = { pane: 'land', renderer: landRenderer, interactive: false, color: '#ffffff', opacity: 0.05, weight: 1 };
    for (let lat = -60; lat <= 60; lat += 30) {
      L.polyline([[lat, -540], [lat, 540]], style).addTo(map);
    }
    for (let lon = -540; lon <= 540; lon += 30) {
      L.polyline([[-80, lon], [85, lon]], style).addTo(map);
    }
  }

  function shifted(feature, dx) {
    const shift = function (c) { return typeof c[0] === 'number' ? [c[0] + dx, c[1]] : c.map(shift); };
    return { type: 'Feature', properties: {}, geometry: { type: feature.geometry.type, coordinates: shift(feature.geometry.coordinates) } };
  }

  function drawBasemap() {
    drawGraticule();
    Promise.all([
      fetch('data/land.json').then(function (r) { return r.json(); }),
      fetch('data/lakes.json').then(function (r) { return r.json(); })
    ]).then(function (data) {
      OFFSETS.forEach(function (dx) {
        L.geoJSON(shifted(data[0], dx), {
          pane: 'land', renderer: landRenderer, interactive: false,
          style: { color: '#2a5277', weight: 0.7, fillColor: '#15304b', fillOpacity: 1 }
        }).addTo(map);
        L.geoJSON(shifted(data[1], dx), {
          pane: 'land', renderer: landRenderer, interactive: false,
          style: { color: '#2a5277', weight: 0.5, fillColor: '#0a1a2b', fillOpacity: 1 }
        }).addTo(map);
      });
    }).catch(function () {
      toast('Could not load the map outlines. Shipments still show.', 'info');
    });
  }

  // ---------- Positions

  function portPoint(code, extra) {
    const p = state.ports[code];
    if (!p) { return null; }
    return Object.assign({ lat: p.latitude, lon: p.longitude, heading: 0, at: code }, extra || {});
  }

  function along(from, to, fraction) {
    const r = SeaLanes.route(from, to, state.ports);
    if (!r) { return null; }
    const p = SeaLanes.pointAt(r, fraction);
    return { lat: p.lat, lon: p.lon, heading: p.heading, mode: r.mode, from: from, to: to };
  }

  /** Where a shipment is at time t. Cargo at sea follows its voyage schedule, then its leg. */
  function position(s, t) {
    if (s.transportStatus !== 'ONBOARD_CARRIER') { return portPoint(s.location); }
    const leg = s.currentLeg != null ? s.legs[s.currentLeg] : null;
    if (leg && state.projection > 0 && leg.unloadTime > now() && t >= leg.unloadTime) {
      return portPoint(leg.to, { projected: true });
    }
    const voyage = state.voyages[s.voyage];
    if (voyage && voyage.movements.length) {
      const ms = voyage.movements;
      if (t >= ms[0].departs && t <= ms[ms.length - 1].arrives) {
        for (let i = 0; i < ms.length; i++) {
          const m = ms[i];
          if (t >= m.departs && t <= m.arrives) {
            return along(m.from, m.to, (t - m.departs) / Math.max(1, m.arrives - m.departs));
          }
          if (i < ms.length - 1 && t > m.arrives && t < ms[i + 1].departs) {
            return portPoint(m.to, { docked: true });
          }
        }
      }
    }
    if (leg) {
      const f = (t - leg.loadTime) / Math.max(1, leg.unloadTime - leg.loadTime);
      return along(leg.from, leg.to, Math.min(Math.max(f, 0), 0.985));
    }
    return portPoint(s.location);
  }

  function computePositions() {
    const t = viewTime();
    state.shipments.forEach(function (s) { s._pos = position(s, t); });
  }

  // ---------- Map rendering

  function shipIcon(group, t) {
    const color = COLORS[t];
    const shape = group.mode === 'rail'
      ? '<rect x="6" y="6" width="14" height="14" rx="3" fill="' + color + '" stroke="#04101c" stroke-width="1.5"/>'
      : '<path d="M13 2 L21 22 L13 17.5 L5 22 Z" fill="' + color + '" stroke="#04101c" stroke-width="1.5"/>';
    const rotation = group.docked || group.mode === 'rail' ? 0 : group.heading;
    const count = group.items.length > 1 ? '<span class="n">' + group.items.length + '</span>' : '';
    const lift = group.docked ? DOCKED_OFFSET : 0;
    return L.divIcon({
      className: '',
      html: '<div class="ship"><svg viewBox="0 0 26 26" style="transform:rotate(' + rotation + 'deg)">' + shape + '</svg>' + count + '</div>',
      iconSize: [26, 26],
      iconAnchor: [13, 13 + lift]
    });
  }

  function cargoLines(items) {
    return items.map(function (s) {
      const extra = s.attention ? ' <span style="color:' + COLORS[tone(s)] + '">' + esc(statusLabel(s)) + '</span>' : '';
      return '<b>' + esc(s.trackingId) + '</b> ' + esc(s.originName) + ' to ' + esc(s.destinationName) + extra;
    }).join('<br>');
  }

  function addCopies(factory) {
    OFFSETS.forEach(function (dx) { factory(dx); });
  }

  function renderMap() {
    computePositions();
    Object.keys(layers).forEach(function (k) { layers[k].clearLayers(); });

    const ships = new Map();
    const atPort = new Map();
    state.shipments.forEach(function (s) {
      const p = s._pos;
      if (!p || s.status === 'CLAIMED' || !matchesFilter(s)) { return; }
      if (s.transportStatus === 'ONBOARD_CARRIER' && !p.projected) {
        const key = (s.voyage || s.trackingId) + '@' + p.lat.toFixed(2) + ',' + p.lon.toFixed(2);
        if (!ships.has(key)) {
          ships.set(key, { lat: p.lat, lon: p.lon, heading: p.heading, mode: p.mode, docked: p.docked, from: p.from, to: p.to, voyage: s.voyage, items: [] });
        }
        ships.get(key).items.push(s);
      } else if (p.at) {
        if (!atPort.has(p.at)) { atPort.set(p.at, []); }
        atPort.get(p.at).push(s);
      }
    });

    drawLanes(ships);
    drawPorts(atPort);
    drawShips(ships);
    drawRings();
    drawRoutes();
    drawNotices();
  }

  function drawLanes(ships) {
    const seen = new Set();
    ships.forEach(function (ship) {
      if (!ship.from || !ship.to) { return; }
      const key = ship.from + '>' + ship.to;
      if (seen.has(key)) { return; }
      seen.add(key);
      const r = SeaLanes.route(ship.from, ship.to, state.ports);
      if (!r) { return; }
      const style = {
        pane: 'lanes', interactive: false, color: '#38bdf8', weight: 1.4, opacity: 0.28,
        dashArray: r.mode === 'rail' ? '5 6' : r.mode === 'air' ? '1 6' : null
      };
      addCopies(function (dx) {
        L.polyline(r.points.map(function (p) { return [p[0], p[1] + dx]; }), style).addTo(layers.lanes);
      });
    });
  }

  function drawPorts(atPort) {
    Object.keys(state.ports).forEach(function (code) {
      const port = state.ports[code];
      const items = atPort.get(code) || [];
      const t = items.length ? worstTone(items) : null;
      const radius = items.length ? 4 + Math.min(9, Math.sqrt(items.length) * 3) : 3;
      const tip = '<b>' + esc(port.name) + '</b> (' + esc(code) + ')' +
        (items.length ? '<br>' + cargoLines(items) : '<br><span class="muted">No cargo here now</span>');
      addCopies(function (dx) {
        const latlng = [port.latitude, port.longitude + dx];
        const marker = L.circleMarker(latlng, {
          pane: 'ports', radius: radius, color: '#04101c', weight: 1.5,
          fillColor: t ? COLORS[t] : '#5b7a99', fillOpacity: t ? 0.95 : 0.85, bubblingMouseEvents: false
        }).bindTooltip(tip, { className: 'tip', direction: 'top', offset: [0, -6] });
        if (items.length) {
          marker.on('click', function () { focusGroup(items); });
        }
        marker.addTo(layers.ports);
        L.marker(latlng, {
          pane: 'ports', interactive: false, keyboard: false,
          icon: L.divIcon({
            className: 'port-label-icon' + (items.length ? '' : ' minor') + (LABEL_LEFT.has(code) ? ' left' : ''),
            html: '<span>' + esc(port.name) + '</span>',
            iconSize: null,
            iconAnchor: LABEL_LEFT.has(code) ? [0, 8] : [-9, 8]
          })
        }).addTo(layers.ports);
      });
    });
  }

  function drawShips(ships) {
    ships.forEach(function (ship) {
      const t = worstTone(ship.items);
      const how = ship.mode === 'rail' ? 'Train' : ship.mode === 'air' ? 'Flight' : 'Ship';
      const dockedAt = ship.items[0]._pos && ship.items[0]._pos.at;
      const leg = ship.docked
        ? 'In port at ' + esc(portName(dockedAt))
        : (ship.from ? esc(portName(ship.from)) + ' to ' + esc(portName(ship.to)) : '');
      const tip = '<b>' + how + ' ' + esc(ship.voyage || '') + '</b><br>' + leg + '<br>' + cargoLines(ship.items);
      addCopies(function (dx) {
        L.marker([ship.lat, ship.lon + dx], { pane: 'ships', icon: shipIcon(ship, t), keyboard: false })
          .bindTooltip(tip, { className: 'tip', direction: 'top', offset: [0, -12] })
          .on('click', function () { focusGroup(ship.items); })
          .addTo(layers.ships);
      });
    });
  }

  function markerPoint(s) {
    const p = s._pos;
    return p ? [p.lat, p.lon] : null;
  }

  function drawRings() {
    const nowMs = Date.now();
    state.shipments.forEach(function (s) {
      const point = markerPoint(s);
      if (!point || s.status === 'CLAIMED' || !matchesFilter(s)) { return; }
      const docked = s.transportStatus === 'ONBOARD_CARRIER' && s._pos.docked;
      const rings = [];
      if (s.attention) { rings.push([tone(s), 38]); }
      if (state.highlighted.has(s.trackingId) || state.selected === s.trackingId) { rings.push(['highlight', 50]); }
      const ping = state.pings.get(s.trackingId);
      if (ping && nowMs - ping < 4000) { rings.push(['ping', 46]); }
      rings.forEach(function (ring) {
        const size = ring[1];
        const lift = docked ? DOCKED_OFFSET : 0;
        addCopies(function (dx) {
          L.marker([point[0], point[1] + dx], {
            pane: 'rings', interactive: false, keyboard: false,
            icon: L.divIcon({
              className: '',
              html: '<div class="ring ' + ring[0] + '" style="width:100%;height:100%"></div>',
              iconSize: [size, size],
              iconAnchor: [size / 2, size / 2 + lift]
            })
          }).addTo(layers.rings);
        });
      });
    });
  }

  function drawRoutes() {
    const ids = new Set(state.highlighted);
    if (state.selected) { ids.add(state.selected); }
    ids.forEach(function (id) {
      const s = state.byId.get(id);
      if (!s) { return; }
      if (!s.legs.length) {
        const r = SeaLanes.route(s.origin, s.destination, state.ports);
        if (r) { drawRoute(r, { color: COLORS.warning, weight: 2, opacity: 0.9, dashArray: '2 8' }); }
        return;
      }
      s.legs.forEach(function (leg) {
        const r = SeaLanes.route(leg.from, leg.to, state.ports);
        if (!r) { return; }
        if (leg.state === 'DONE') {
          drawRoute(r, { color: '#7c93ad', weight: 2, opacity: 0.75 });
        } else if (leg.state === 'CURRENT') {
          drawRoute(r, { color: COLORS.transit, weight: 3.2, opacity: 0.95, className: 'route-flow' });
        } else {
          drawRoute(r, { color: '#dbe7f3', weight: 2, opacity: 0.8, dashArray: '6 8' });
        }
      });
    });
  }

  function drawRoute(r, style) {
    addCopies(function (dx) {
      L.polyline(r.points.map(function (p) { return [p[0], p[1] + dx]; }),
        Object.assign({ pane: 'routes', interactive: false }, style)).addTo(layers.routes);
    });
  }

  function drawNotices() {
    state.notices.forEach(function (notice) {
      const port = state.ports[notice.port];
      if (!port) { return; }
      addCopies(function (dx) {
        L.marker([port.latitude + 1.6, port.longitude - 1.6 + dx], {
          pane: 'notices', keyboard: false,
          icon: L.divIcon({ className: '', html: '<div class="notice-icon">!</div>', iconSize: [20, 18], iconAnchor: [10, 9] })
        }).bindTooltip('<b>' + esc(notice.title) + '</b><br>' + esc(notice.summary), { className: 'tip', direction: 'top', offset: [0, -8] })
          .on('click', function () { openSource(notice.source, notice.title); })
          .addTo(layers.notices);
      });
    });
  }

  function focusGroup(items) {
    if (items.length === 1) {
      select(items[0].trackingId, false);
    } else {
      highlight(items.map(function (s) { return s.trackingId; }), false);
      select(items[0].trackingId, false);
    }
  }

  function flyTo(ids) {
    const points = [];
    ids.forEach(function (id) {
      const s = state.byId.get(id);
      const point = s && markerPoint(s);
      if (point) { points.push(point); }
    });
    if (!points.length) { return; }
    const base = map.getCenter().lng;
    const normalized = points.map(function (p) {
      let lon = p[1];
      while (lon - base > 180) { lon -= 360; }
      while (lon - base < -180) { lon += 360; }
      return [p[0], lon];
    });
    if (normalized.length === 1) {
      map.flyTo(normalized[0], Math.max(map.getZoom(), 3.5), { duration: 1.1 });
    } else {
      map.flyToBounds(L.latLngBounds(normalized).pad(0.35), { duration: 1.1, maxZoom: 4.5 });
    }
  }

  // ---------- Panels

  function renderKpis() {
    const counts = { all: 0, transit: 0, port: 0, booked: 0, done: 0, attention: 0 };
    state.shipments.forEach(function (s) {
      counts.all++;
      const c = category(s);
      if (counts[c] !== undefined) { counts[c]++; }
      if (s.attention) { counts.attention++; }
    });
    Object.keys(counts).forEach(function (key) {
      const el = $('kpi-' + key);
      const tile = el.parentElement;
      if (state.kpis[key] !== undefined && state.kpis[key] !== counts[key]) {
        tile.classList.remove('bump');
        void tile.offsetWidth;
        tile.classList.add('bump');
      }
      el.textContent = counts[key];
      tile.classList.toggle('active', state.filter === key && key !== 'all');
    });
    state.kpis = counts;
  }

  function sorted(list) {
    return list.slice().sort(function (a, b) {
      const ra = a.attention ? RANK[tone(a)] : 0;
      const rb = b.attention ? RANK[tone(b)] : 0;
      return rb - ra || a.trackingId.localeCompare(b.trackingId);
    });
  }

  function etaText(s) {
    if (s.eta == null) { return ''; }
    const passed = s.eta < now() && s.status !== 'CLAIMED' && s.status !== 'AT_DESTINATION';
    return fmtDate(s.eta) + (passed ? ' (passed)' : '');
  }

  function renderTable() {
    const body = document.querySelector('#shipments tbody');
    const rows = sorted(state.shipments).filter(function (s) { return matchesFilter(s) && matchesSearch(s); });
    body.innerHTML = rows.map(function (s) {
      const classes = [];
      if (state.highlighted.has(s.trackingId)) { classes.push('highlighted'); }
      if (state.selected === s.trackingId) { classes.push('selected'); }
      return '<tr data-id="' + esc(s.trackingId) + '" class="' + classes.join(' ') + '">' +
        '<td class="id">' + esc(s.trackingId) + '</td>' +
        '<td>' + esc(s.originName) + ' &rarr; ' + esc(s.destinationName) + '</td>' +
        '<td><span class="status ' + tone(s) + '">' + esc(statusLabel(s)) + '</span></td>' +
        '<td>' + esc(whereNow(s)) + '</td>' +
        '<td>' + esc(nextStep(s)) + '</td>' +
        '<td>' + esc(etaText(s)) + '</td>' +
        '<td>' + esc(fmtDay(s.arrivalDeadline)) + '</td></tr>';
    }).join('');
    $('filter-label').textContent = 'Showing ' + rows.length + ' of ' + state.shipments.length +
      (state.filter !== 'all' ? ', ' + FILTER_LABELS[state.filter].toLowerCase() : '');
  }

  function renderAttention() {
    const items = sorted(state.shipments.filter(function (s) { return s.attention; }));
    $('attention-count').textContent = items.length;
    $('attention-list').innerHTML = items.map(function (s) {
      return '<li data-id="' + esc(s.trackingId) + '" class="' + (state.highlighted.has(s.trackingId) ? 'highlighted' : '') + '">' +
        '<span class="tag"><span class="status ' + tone(s) + '">' + esc(statusLabel(s)) + '</span></span>' +
        '<span class="id">' + esc(s.trackingId) + ' <span class="muted small">' + esc(s.originName) + ' &rarr; ' + esc(s.destinationName) + '</span></span>' +
        '<span class="desc">' + esc(s.attention.summary) + '</span></li>';
    }).join('') || '<li class="muted">Nothing needs attention.</li>';
  }

  function renderDetails() {
    const box = $('details');
    const s = state.selected && state.byId.get(state.selected);
    if (!s) {
      box.className = 'details hidden';
      return;
    }
    const legs = s.legs.map(function (leg) {
      const when = leg.state === 'DONE' ? 'arrived ' + fmtDate(leg.unloadTime)
        : leg.state === 'CURRENT' ? 'arrives ' + fmtDate(leg.unloadTime)
          : 'leaves ' + fmtDate(leg.loadTime);
      return '<li class="' + leg.state + '">' + esc(leg.fromName) + ' to ' + esc(leg.toName) +
        ' <span class="muted">on ' + esc(leg.voyage) + ', ' + esc(when) + '</span></li>';
    }).join('');
    box.className = 'details';
    box.innerHTML =
      '<button type="button" class="ghost small close" id="details-close">Close</button>' +
      '<h3>' + esc(s.trackingId) + '</h3>' +
      '<div class="route">' + esc(s.originName) + ' &rarr; ' + esc(s.destinationName) + '</div>' +
      '<span class="status ' + tone(s) + '">' + esc(statusLabel(s)) + '</span>' +
      (s.attention ? '<div class="problem ' + tone(s) + '">' + esc(s.attention.summary) + '</div>' : '') +
      '<dl>' +
      '<dt>Where now</dt><dd>' + esc(whereNow(s)) + '</dd>' +
      '<dt>Next step</dt><dd>' + esc(nextStep(s) || 'None') + '</dd>' +
      '<dt>ETA</dt><dd>' + esc(etaText(s) || 'Unknown') + '</dd>' +
      '<dt>Deadline</dt><dd>' + esc(fmtDay(s.arrivalDeadline)) + '</dd>' +
      '</dl>' +
      (legs ? '<ul class="legs">' + legs + '</ul>' : '<div class="muted small">No route yet.</div>') +
      '<div class="actions">' +
      '<button type="button" id="details-ask">Ask the assistant</button>' +
      '<a href="../admin/show.xhtml?trackingId=' + encodeURIComponent(s.trackingId) + '" target="_blank" rel="noopener">Open in Cargo Tracker</a>' +
      '</div>';
    $('details-close').addEventListener('click', function () { select(null); });
    $('details-ask').addEventListener('click', function () {
      ask('What is the status of ' + s.trackingId + ', and what should we do next?');
    });
  }

  function addFeed(t, id, text, time) {
    const list = $('feed');
    const li = document.createElement('li');
    li.className = 'new';
    li.dataset.id = id;
    li.innerHTML = '<span class="tag"><i class="dot ' + t + '"></i></span>' +
      '<span><span class="id">' + esc(id) + '</span> <span class="desc">' + esc(text) + '</span></span>' +
      '<span class="when">' + esc(fmtClock(time || now())) + '</span>';
    list.insertBefore(li, list.firstChild);
    while (list.children.length > 40) { list.removeChild(list.lastChild); }
  }

  function seedFeed() {
    state.shipments
      .filter(function (s) { return s.lastEvent && s.lastEvent.time; })
      .sort(function (a, b) { return a.lastEvent.time - b.lastEvent.time; })
      .slice(-6)
      .forEach(function (s) {
        addFeed(tone(s), s.trackingId, describeEvent(s.lastEvent), s.lastEvent.time);
      });
    Array.prototype.forEach.call($('feed').children, function (li) { li.classList.remove('new'); });
  }

  function diff(previous, next) {
    const old = new Map(previous.map(function (s) { return [s.trackingId, s]; }));
    next.forEach(function (s) {
      const o = old.get(s.trackingId);
      if (!o) {
        addFeed('booked', s.trackingId, 'Booked: ' + s.originName + ' to ' + s.destinationName);
        return;
      }
      const a = o.lastEvent;
      const b = s.lastEvent;
      if (b && (!a || a.type !== b.type || a.location !== b.location || a.time !== b.time)) {
        addFeed(tone(s), s.trackingId, describeEvent(b), b.time);
        state.pings.set(s.trackingId, Date.now());
      }
      if (s.status === 'MISDIRECTED' && o.status !== 'MISDIRECTED') {
        addFeed('critical', s.trackingId, 'Misdirected. ' + s.attention.summary);
        toast(s.trackingId + ' is misdirected. ' + s.attention.summary);
      } else if (s.attention && !o.attention) {
        addFeed(tone(s), s.trackingId, s.attention.summary);
      } else if (!s.attention && o.attention) {
        addFeed('port', s.trackingId, 'Back on plan.');
      }
    });
  }

  // ---------- Data

  let loading = false;
  let reloadQueued = false;

  function loadSnapshot() {
    if (loading) {
      reloadQueued = true;
      return;
    }
    loading = true;
    getJson('/dashboard').then(function (snapshot) {
      state.clockOffset = snapshot.generatedAt - Date.now();
      state.traceUrl = snapshot.traceUrl;
      state.notices = snapshot.notices || [];
      state.ports = {};
      snapshot.ports.forEach(function (p) { state.ports[p.code] = p; });
      state.voyages = {};
      snapshot.voyages.forEach(function (v) { state.voyages[v.number] = v; });
      const previous = state.shipments;
      state.shipments = snapshot.shipments;
      state.byId = new Map(state.shipments.map(function (s) { return [s.trackingId, s]; }));
      if (state.loaded) {
        diff(previous, state.shipments);
      } else {
        seedFeed();
      }
      state.loaded = true;
      render();
    }).catch(function (e) {
      toast('Could not load shipments: ' + e.message);
    }).then(function () {
      loading = false;
      if (reloadQueued) {
        reloadQueued = false;
        loadSnapshot();
      }
    });
  }

  function render() {
    renderMap();
    renderKpis();
    renderAttention();
    renderTable();
    renderDetails();
  }

  let refreshTimer = null;
  function scheduleRefresh() {
    clearTimeout(refreshTimer);
    refreshTimer = setTimeout(loadSnapshot, 700);
  }

  function connectLive() {
    const pill = $('live-pill');
    const source = new EventSource(API + '/cargo');
    source.onopen = function () { pill.className = 'pill ok'; pill.textContent = 'Live'; };
    source.onmessage = scheduleRefresh;
    source.onerror = function () { pill.className = 'pill warn'; pill.textContent = 'Reconnecting'; };
  }

  // ---------- Selection

  function highlight(ids, fly) {
    state.highlighted = new Set((ids || []).filter(function (id) { return state.byId.has(id); }));
    renderMap();
    renderAttention();
    renderTable();
    if (fly && state.highlighted.size) { flyTo(Array.from(state.highlighted)); }
  }

  function select(id, fly) {
    state.selected = id && state.byId.has(id) ? id : null;
    renderDetails();
    renderMap();
    renderTable();
    if (fly && state.selected) { flyTo([state.selected]); }
  }

  // ---------- Assistant

  function ask(question) {
    const text = (question || '').trim();
    if (!text) { return; }
    $('question').value = text;
    const box = $('answer');
    box.className = 'answer thinking';
    box.innerHTML = '<div class="question">' + esc(text) + '</div><p class="text">Asking the assistant</p>';
    $('ask-button').disabled = true;
    const started = performance.now();
    sendJson('POST', '/assistant', { question: text })
      .then(function (reply) { renderReply(text, reply); })
      .catch(function (e) {
        renderReply(text, {
          outcome: 'FALLBACK',
          result: { answer: '', trackingIds: [], sources: [] },
          message: 'The assistant did not respond. The rest of the page still works.',
          reason: e.message,
          latencyMillis: Math.round(performance.now() - started),
          modelCalls: 0
        });
      })
      .then(function () {
        $('ask-button').disabled = false;
        refreshAiStatus();
      });
  }

  function renderReply(question, reply) {
    const box = $('answer');
    const result = reply.result || { answer: '', trackingIds: [], sources: [] };
    const answered = reply.outcome === 'ANSWERED';
    const meta = [];
    if (reply.model) { meta.push(esc(reply.model)); }
    meta.push(seconds(reply.latencyMillis));
    if (reply.modelCalls) { meta.push(reply.modelCalls + (reply.modelCalls === 1 ? ' model call' : ' model calls')); }
    if (reply.inputTokens != null) { meta.push(reply.inputTokens + ' in / ' + (reply.outputTokens || 0) + ' out tokens'); }
    if (reply.traceId && state.traceUrl) {
      meta.push('<a href="' + esc(state.traceUrl.replace('{traceId}', reply.traceId)) + '" target="_blank" rel="noopener">Open trace</a>');
    } else if (reply.traceId) {
      meta.push('trace ' + esc(reply.traceId.slice(0, 8)));
    }
    let html = '<div class="question">' + esc(question) + '</div>' +
      '<div class="meta"><span class="badge ' + esc(reply.outcome) + '">' + esc(OUTCOMES[reply.outcome] || reply.outcome) + '</span>' +
      meta.map(function (m) { return '<span>' + m + '</span>'; }).join('') + '</div>';
    if (answered && result.trackingIds.length) {
      html += '<div class="row"><span class="row-label">On the map</span>' + result.trackingIds.map(function (id) {
        return '<button type="button" class="chip id" data-id="' + esc(id) + '">' + esc(id) + '</button>';
      }).join('') + '</div>';
    }
    if (answered && result.sources.length) {
      html += '<div class="row"><span class="row-label">Sources</span>' + result.sources.map(function (source) {
        const tool = source.indexOf('tool:') === 0;
        return '<button type="button" class="chip source' + (tool ? ' tool' : '') + '" data-source="' + esc(source) + '"' +
          (tool ? ' title="Live data from a read-only tool"' : ' title="Open the document"') + '>' + esc(source) + '</button>';
      }).join('') + '</div>';
    }
    html += '<p class="text">' + esc(answered ? result.answer : reply.message) + '</p>';
    if (!answered && reply.reason) {
      html += '<div class="muted small">Reason: ' + esc(reply.reason) + '</div>';
    }
    box.className = 'answer';
    box.innerHTML = html;
    box.querySelectorAll('.chip.id').forEach(function (chip) {
      chip.addEventListener('click', function () { select(chip.dataset.id, true); });
    });
    box.querySelectorAll('.chip.source').forEach(function (chip) {
      chip.addEventListener('click', function () {
        const source = chip.dataset.source;
        if (source.indexOf('tool:') === 0) {
          toast(source.slice(5) + ' is a read-only tool. It reads live cargo data and cannot change anything.', 'info');
        } else {
          openSource(source, source);
        }
      });
    });
    highlight(answered ? result.trackingIds : [], answered);
  }

  function openSource(source, title) {
    fetch(API + '/assistant/sources/' + encodeURIComponent(source))
      .then(function (r) { if (!r.ok) { throw new Error('HTTP ' + r.status); } return r.text(); })
      .then(function (text) { showModal(title, text); })
      .catch(function () { toast('Could not open ' + source + '.', 'info'); });
  }

  function refreshAiStatus() {
    getJson('/assistant/status').then(function (status) {
      const pill = $('ai-pill');
      const stats = status.stats || {};
      let cls = 'ok';
      let text = 'Assistant: ' + status.model;
      if (!status.enabled) {
        cls = 'off';
        text = 'Assistant off';
      } else if (!status.configured) {
        cls = 'warn';
        text = 'Assistant: no key';
      } else if (stats.lastOutcome === 'FALLBACK') {
        cls = 'bad';
        text = 'Assistant: fallback';
      }
      pill.className = 'pill ' + cls;
      pill.textContent = text;
      pill.title = status.api + ' API, ' + status.provider + ', timeout ' + status.timeoutSeconds + ' s' +
        (stats.lastReason ? '. Last problem: ' + stats.lastReason : '');
      $('ai-stats').textContent = stats.questions
        ? 'p95 ' + seconds(stats.p95LatencyMillis) + ', ' + stats.answered + ' answered, ' + stats.fallbacks + ' fallback' + (stats.fallbacks === 1 ? '' : 's')
        : (status.configured ? status.model : '');
    }).catch(function () {
      $('ai-pill').className = 'pill bad';
      $('ai-pill').textContent = 'Assistant: no status';
    });
  }

  // ---------- Live traffic

  function refreshTraffic() {
    getJson('/demo/traffic').then(function (t) { $('traffic-toggle').checked = t.enabled; }).catch(function () {});
  }

  // ---------- Projection

  let playing = null;

  function setProjection(hours) {
    state.projection = hours;
    $('projection').value = hours;
    const label = $('projection-label');
    if (hours === 0) {
      label.textContent = 'Now';
      label.className = 'projection-label';
    } else {
      label.textContent = '+' + hours + ' h, ' + new Date(viewTime()).toLocaleDateString(undefined, { weekday: 'short' }) + ' ' + fmtClock(viewTime());
      label.className = 'projection-label future';
    }
    renderMap();
  }

  function play() {
    if (playing) {
      cancelAnimationFrame(playing);
      playing = null;
      $('play').textContent = 'Play 4 days';
      return;
    }
    const start = state.projection >= 96 ? 0 : state.projection;
    const began = performance.now();
    const duration = (96 - start) / 96 * 12000;
    $('play').textContent = 'Stop';
    function frame(time) {
      const f = Math.min(1, (time - began) / Math.max(1, duration));
      setProjection(Math.round(start + (96 - start) * f));
      if (f < 1) {
        playing = requestAnimationFrame(frame);
      } else {
        playing = null;
        $('play').textContent = 'Play 4 days';
      }
    }
    playing = requestAnimationFrame(frame);
  }

  // ---------- Wiring

  function wire() {
    $('suggestions').innerHTML = SUGGESTIONS.map(function (s) {
      return '<button type="button" class="chip" title="' + esc(s[1]) + '" data-question="' + esc(s[1]) + '">' + esc(s[0]) + '</button>';
    }).join('');
    $('suggestions').querySelectorAll('.chip').forEach(function (chip) {
      chip.addEventListener('click', function () { ask(chip.dataset.question); });
    });
    $('ask-form').addEventListener('submit', function (e) {
      e.preventDefault();
      ask($('question').value);
    });

    document.querySelectorAll('.kpi').forEach(function (tile) {
      tile.addEventListener('click', function () {
        const f = tile.dataset.filter;
        state.filter = state.filter === f ? 'all' : f;
        render();
      });
    });

    $('search').addEventListener('input', function (e) {
      state.search = e.target.value;
      renderTable();
    });

    document.querySelector('#shipments tbody').addEventListener('click', function (e) {
      const row = e.target.closest('tr');
      if (row) { select(row.dataset.id, true); }
    });
    $('attention-list').addEventListener('click', function (e) {
      const li = e.target.closest('li[data-id]');
      if (li) { select(li.dataset.id, true); }
    });
    $('feed').addEventListener('click', function (e) {
      const li = e.target.closest('li[data-id]');
      if (li && state.byId.has(li.dataset.id)) { select(li.dataset.id, true); }
    });

    $('traffic-toggle').addEventListener('change', function (e) {
      sendJson('PUT', '/demo/traffic', { enabled: e.target.checked }).then(function (t) {
        toast(t.enabled
          ? 'Live traffic is on. A port system reports one handling event every ' + t.intervalSeconds + ' seconds.'
          : 'Live traffic is off.', 'info');
      }).catch(function () { toast('Could not change live traffic.', 'info'); });
    });
    $('traffic-step').addEventListener('click', function () {
      sendJson('POST', '/demo/traffic/step').then(function (t) {
        toast('Handling report sent: ' + t.lastReport, 'info');
      }).catch(function () { toast('Could not send a handling report.', 'info'); });
    });

    $('projection').addEventListener('input', function (e) {
      if (playing) { play(); }
      setProjection(Number(e.target.value));
    });
    $('play').addEventListener('click', play);

    $('modal-close').addEventListener('click', hideModal);
    $('modal').addEventListener('click', function (e) { if (e.target === $('modal')) { hideModal(); } });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') {
        hideModal();
        select(null);
      }
    });
  }

  function tick() {
    $('clock').textContent = new Date(now()).toLocaleString(undefined, {
      weekday: 'short', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
  }

  wire();
  drawBasemap();
  connectLive();
  loadSnapshot();
  refreshAiStatus();
  refreshTraffic();
  tick();
  setInterval(tick, 1000);
  setInterval(refreshAiStatus, 10000);
  setInterval(refreshTraffic, 15000);
  setInterval(loadSnapshot, 30000);
})();
