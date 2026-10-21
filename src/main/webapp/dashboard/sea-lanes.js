/*
 * Sea lanes for the dashboard map. The waypoints are hand-placed so that routes stay at sea
 * at world zoom. They are a drawing aid, not a navigation chart.
 */
(function (global) {
  'use strict';

  // [latitude, longitude]
  const NODES = {
    SING: [1.18, 103.95], SING_E: [1.5, 104.7], MAL_C: [2.6, 101.0], MAL_N: [5.7, 98.0],
    ANDAMAN_S: [6.3, 94.8], DONDRA: [5.4, 80.6], INDIA_SW: [7.6, 76.6], INDIA_W: [14.0, 72.9],
    MUMBAI_APP: [18.85, 72.55], ARAB_N: [21.6, 63.0], OMAN_G: [24.7, 58.6], HORMUZ: [26.55, 56.5],
    GULF_MID: [26.0, 55.2], JEBEL_APP: [25.3, 54.9], RAS_HADD: [22.4, 60.5], OMAN_S: [16.5, 56.0],
    ARAB_S: [10.5, 66.0], SOCOTRA_N: [13.4, 53.5], ADEN_E: [12.6, 50.5], ADEN_W: [12.0, 45.5],
    BAB: [12.65, 43.35], RED_S: [15.0, 41.8], RED_C: [20.5, 38.6], RED_N: [26.0, 35.2],
    GOS: [28.6, 33.0], SUEZ: [29.9, 32.55], PORT_SAID: [31.4, 32.35], MED_E: [33.0, 29.5],
    CRETE_S: [34.3, 24.5], MED_C: [35.6, 17.5], SICILY_CH: [37.35, 11.6], SARD_S: [38.4, 8.6],
    MED_W: [37.6, 2.0], ALBORAN: [36.05, -2.5], GIB: [35.95, -5.5], SVINCENT: [36.8, -9.7],
    LISBON_W: [38.8, -10.0], FINIST: [43.3, -10.0], USHANT: [48.6, -5.9], CHANNEL_W: [49.4, -4.0],
    CHANNEL_C: [50.1, -1.0], DOVER: [51.0, 1.5], NSEA_S: [51.9, 3.0], RTM_APP: [52.0, 3.95],
    TEXEL: [53.4, 4.4], BIGHT: [54.1, 7.4], ELBE_MOUTH: [53.95, 8.55], BRUNSBUTTEL: [53.9, 9.15],
    KIEL: [54.4, 10.2], KIEL_BAY: [54.55, 10.6], FEHMARN: [54.6, 11.35], MECKL: [54.55, 12.3],
    ARKONA: [54.85, 13.6], JUTLAND_W: [56.5, 7.5], SKAGERRAK: [57.75, 8.8], SKAGEN_N: [58.0, 10.6],
    GOT_APP: [57.65, 11.55], KATT: [56.9, 11.6], ORESUND_N: [56.1, 12.6], ORESUND_S: [55.25, 12.95],
    BORNHOLM_S: [54.85, 15.0], BALT_E: [56.6, 19.8], BALT_N: [58.6, 20.3], STO_APP: [59.45, 19.4],
    GULF_FIN: [59.65, 23.0], HEL_APP: [60.05, 24.95],
    SCS_S: [5.5, 106.0], SCS_C: [11.5, 110.8], SCS_N: [18.0, 114.0], HKG_SEA: [21.9, 114.5],
    GD_COAST: [23.0, 117.5], TAIWAN_STR: [24.4, 119.6], ECS_S: [27.5, 121.8], HZ_BAY: [30.55, 121.9],
    YANGTZE: [31.05, 122.5], ECS_E: [31.3, 126.5], KOREA_STR: [34.45, 128.85], OSUMI: [30.4, 130.6],
    JP_S: [31.2, 132.6], SHIKOKU_S: [32.6, 134.5], KII_S: [33.1, 136.5], JP_E: [34.4, 140.4],
    TOKYO_APP: [35.15, 139.8], LUZON: [21.2, 121.4], RYUKYU_E: [26.0, 130.5],
    NP_W: [40.0, 160.0], NP_C: [45.5, 180.0], NP_E: [41.0, -145.0], CAL_W: [35.5, -125.0],
    LA_APP: [33.6, -118.4], BAJA_W: [30.0, -118.0], MEX_W: [20.0, -109.0], CA_W: [11.0, -95.0],
    PAC_PAN: [6.8, -79.4], PANAMA_P: [8.9, -79.5], COLON: [9.4, -79.9], CARIB_C: [14.5, -76.5],
    WINDWARD: [20.0, -73.8], BAHAMAS_E: [26.5, -73.0], HATTERAS_E: [34.0, -74.3],
    NY_APP: [40.35, -73.7], GEORGES_S: [40.2, -66.0], ATL_NW: [41.3, -52.0], ATL_N: [44.5, -35.0],
    ATL_NE: [47.5, -15.0], AZORES_S: [36.3, -25.0], ATL_W: [38.0, -50.0], CANARY_W: [29.5, -19.0],
    CAPE_VERDE_W: [16.0, -26.5], EQ_ATL: [0.0, -30.0], BRAZIL_E: [-8.0, -33.5],
    BRAZIL_SE: [-20.0, -38.5], SANTOS_APP: [-24.3, -46.1], SATL_C: [-32.0, -20.0],
    CAPE_W: [-35.0, 15.0], CAPE: [-35.4, 19.8], AGULHAS_E: [-34.6, 25.5], DURBAN_APP: [-30.0, 31.3],
    MOZ_S: [-28.5, 35.5], MADAGASCAR_S: [-27.0, 46.0], IND_SW: [-15.0, 70.0], IND_C: [-5.0, 85.0],
    SUMATRA_W: [2.0, 94.5], KARIMATA: [-1.8, 108.9], JAVA_SEA: [-5.3, 111.0], LOMBOK: [-8.8, 115.85],
    IND_AU: [-14.0, 114.5], WA_W: [-25.0, 111.5], LEEUWIN: [-35.3, 114.6], BIGHT_AU: [-36.2, 130.0],
    VIC_W: [-38.9, 140.0], BASS_W: [-39.3, 143.6], PORT_PHILLIP: [-38.4, 144.75]
  };

  // Each chain adds an edge between neighbors.
  const CHAINS = [
    'SING MAL_C MAL_N ANDAMAN_S DONDRA ARAB_S SOCOTRA_N ADEN_E ADEN_W BAB RED_S RED_C RED_N GOS SUEZ PORT_SAID',
    'PORT_SAID MED_E CRETE_S MED_C SICILY_CH SARD_S MED_W ALBORAN GIB SVINCENT LISBON_W FINIST USHANT',
    'USHANT CHANNEL_W CHANNEL_C DOVER NSEA_S RTM_APP',
    'NSEA_S TEXEL BIGHT ELBE_MOUTH BRUNSBUTTEL',
    'BIGHT JUTLAND_W SKAGERRAK SKAGEN_N GOT_APP KATT ORESUND_N ORESUND_S ARKONA BORNHOLM_S BALT_E BALT_N STO_APP',
    'BALT_N GULF_FIN HEL_APP',
    'BRUNSBUTTEL KIEL KIEL_BAY FEHMARN MECKL ARKONA',
    'DONDRA INDIA_SW INDIA_W MUMBAI_APP ARAB_N OMAN_G HORMUZ GULF_MID JEBEL_APP',
    'OMAN_G RAS_HADD OMAN_S SOCOTRA_N',
    'SING SING_E SCS_S SCS_C SCS_N HKG_SEA',
    'HKG_SEA GD_COAST TAIWAN_STR ECS_S HZ_BAY YANGTZE ECS_E KOREA_STR',
    'ECS_E OSUMI JP_S SHIKOKU_S KII_S JP_E TOKYO_APP',
    'KOREA_STR OSUMI',
    'HKG_SEA LUZON RYUKYU_E KII_S',
    'JP_E NP_W NP_C NP_E CAL_W LA_APP',
    'LA_APP BAJA_W MEX_W CA_W PAC_PAN PANAMA_P COLON CARIB_C WINDWARD BAHAMAS_E HATTERAS_E NY_APP',
    'USHANT ATL_NE ATL_N ATL_NW GEORGES_S NY_APP',
    'SVINCENT AZORES_S ATL_W GEORGES_S',
    'SVINCENT CANARY_W CAPE_VERDE_W EQ_ATL BRAZIL_E BRAZIL_SE SANTOS_APP',
    'SANTOS_APP SATL_C CAPE_W CAPE AGULHAS_E DURBAN_APP',
    'DURBAN_APP MOZ_S MADAGASCAR_S IND_SW IND_C SUMATRA_W ANDAMAN_S',
    'SING_E KARIMATA JAVA_SEA LOMBOK IND_AU WA_W LEEUWIN BIGHT_AU VIC_W BASS_W PORT_PHILLIP'
  ];

  // The sea waypoint next to each port.
  const PORT_NODES = {
    CNHKG: 'HKG_SEA', CNSHA: 'YANGTZE', CNHGH: 'HZ_BAY', JNTKO: 'TOKYO_APP', KRPUS: 'KOREA_STR',
    SGSIN: 'SING', AUMEL: 'PORT_PHILLIP', INBOM: 'MUMBAI_APP', AEJEA: 'JEBEL_APP', ESALG: 'GIB',
    NLRTM: 'RTM_APP', DEHAM: 'BRUNSBUTTEL', SEGOT: 'GOT_APP', SESTO: 'STO_APP', FIHEL: 'HEL_APP',
    USNYC: 'NY_APP', USLAX: 'LA_APP', BRSSZ: 'SANTOS_APP', ZADUR: 'DURBAN_APP'
  };

  const graph = {};
  function addEdge(a, b) {
    if (!NODES[a] || !NODES[b]) {
      throw new Error('Unknown sea lane waypoint: ' + (NODES[a] ? b : a));
    }
    const km = distance(NODES[a], NODES[b]);
    (graph[a] = graph[a] || []).push({ to: b, km: km });
    (graph[b] = graph[b] || []).push({ to: a, km: km });
  }
  CHAINS.forEach(function (chain) {
    const names = chain.split(' ');
    for (let i = 1; i < names.length; i++) {
      addEdge(names[i - 1], names[i]);
    }
  });

  function toRad(d) { return d * Math.PI / 180; }
  function toDeg(r) { return r * 180 / Math.PI; }

  function distance(a, b) {
    const dLat = toRad(b[0] - a[0]);
    const dLon = toRad(b[1] - a[1]);
    const h = Math.sin(dLat / 2) ** 2 +
      Math.cos(toRad(a[0])) * Math.cos(toRad(b[0])) * Math.sin(dLon / 2) ** 2;
    return 2 * 6371 * Math.asin(Math.min(1, Math.sqrt(h)));
  }

  function shortestPath(start, goal) {
    const dist = {};
    const prev = {};
    const open = new Set(Object.keys(graph));
    Object.keys(graph).forEach(function (n) { dist[n] = Infinity; });
    dist[start] = 0;
    while (open.size) {
      let best = null;
      open.forEach(function (n) { if (best === null || dist[n] < dist[best]) { best = n; } });
      if (best === null || dist[best] === Infinity) { break; }
      open.delete(best);
      if (best === goal) { break; }
      graph[best].forEach(function (edge) {
        const candidate = dist[best] + edge.km;
        if (candidate < dist[edge.to]) {
          dist[edge.to] = candidate;
          prev[edge.to] = best;
        }
      });
    }
    if (dist[goal] === Infinity) { return null; }
    const path = [goal];
    while (path[0] !== start) { path.unshift(prev[path[0]]); }
    return path;
  }

  function greatCircle(a, b, steps) {
    const lat1 = toRad(a[0]), lon1 = toRad(a[1]), lat2 = toRad(b[0]), lon2 = toRad(b[1]);
    const d = 2 * Math.asin(Math.sqrt(Math.sin((lat2 - lat1) / 2) ** 2 +
      Math.cos(lat1) * Math.cos(lat2) * Math.sin((lon2 - lon1) / 2) ** 2));
    if (d === 0) { return [a, b]; }
    const points = [];
    for (let i = 0; i <= steps; i++) {
      const f = i / steps;
      const A = Math.sin((1 - f) * d) / Math.sin(d);
      const B = Math.sin(f * d) / Math.sin(d);
      const x = A * Math.cos(lat1) * Math.cos(lon1) + B * Math.cos(lat2) * Math.cos(lon2);
      const y = A * Math.cos(lat1) * Math.sin(lon1) + B * Math.cos(lat2) * Math.sin(lon2);
      const z = A * Math.sin(lat1) + B * Math.sin(lat2);
      points.push([toDeg(Math.atan2(z, Math.sqrt(x * x + y * y))), toDeg(Math.atan2(y, x))]);
    }
    return points;
  }

  // Keeps each longitude within 180 degrees of the one before, so lines do not jump across the map.
  function unwrap(points) {
    const out = [points[0].slice()];
    for (let i = 1; i < points.length; i++) {
      let lon = points[i][1];
      const before = out[i - 1][1];
      while (lon - before > 180) { lon -= 360; }
      while (lon - before < -180) { lon += 360; }
      out.push([points[i][0], lon]);
    }
    return out;
  }

  const cache = new Map();

  /**
   * Returns {mode, points, km, cumulative} for a trip between two locations. Mode is "sea",
   * "rail" for inland trips in the Americas, or "air" for other trips that start or end inland.
   */
  function route(from, to, ports) {
    const key = from + '>' + to;
    if (cache.has(key)) { return cache.get(key); }
    const a = ports[from];
    const b = ports[to];
    if (!a || !b) { return null; }
    const start = [a.latitude, a.longitude];
    const end = [b.latitude, b.longitude];
    let mode;
    let points;
    const path = PORT_NODES[from] && PORT_NODES[to] && !a.inland && !b.inland
      ? shortestPath(PORT_NODES[from], PORT_NODES[to]) : null;
    if (path) {
      mode = 'sea';
      points = [start].concat(path.map(function (n) { return NODES[n]; }), [end]);
    } else if (a.longitude < -30 && b.longitude < -30) {
      mode = 'rail';
      points = [start, end];
    } else {
      mode = 'air';
      points = greatCircle(start, end, 48);
    }
    points = unwrap(points);
    const cumulative = [0];
    for (let i = 1; i < points.length; i++) {
      cumulative.push(cumulative[i - 1] + distance(points[i - 1], points[i]));
    }
    const result = { mode: mode, points: points, km: cumulative[cumulative.length - 1], cumulative: cumulative };
    cache.set(key, result);
    return result;
  }

  function mercator(p) {
    return [toRad(p[1]), Math.log(Math.tan(Math.PI / 4 + toRad(Math.max(-85, Math.min(85, p[0]))) / 2))];
  }

  /** The point at a fraction of the way along a route, with the heading in degrees from north. */
  function pointAt(r, fraction) {
    const f = Math.max(0, Math.min(1, fraction));
    const target = f * r.km;
    let i = 1;
    while (i < r.points.length - 1 && r.cumulative[i] < target) { i++; }
    const a = r.points[i - 1];
    const b = r.points[i];
    const span = r.cumulative[i] - r.cumulative[i - 1];
    const t = span > 0 ? (target - r.cumulative[i - 1]) / span : 0;
    const pa = mercator(a);
    const pb = mercator(b);
    const heading = (toDeg(Math.atan2(pb[0] - pa[0], pb[1] - pa[1])) + 360) % 360;
    return { lat: a[0] + (b[0] - a[0]) * t, lon: a[1] + (b[1] - a[1]) * t, heading: heading };
  }

  global.SeaLanes = { route: route, pointAt: pointAt, nodes: NODES, portNodes: PORT_NODES };
})(window);
