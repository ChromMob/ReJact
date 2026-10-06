(function () {
var cfgEl = document.getElementById('rjcfg');
if (!cfgEl) return;
var cfg = JSON.parse(cfgEl.textContent);
var view = (window.crypto && crypto.randomUUID) ? crypto.randomUUID()
  : String(Math.random()).slice(2) + String(Date.now());
var nodes = {};
var ws;
var el, uid, dv, u8;
var enc = new TextEncoder();
var dec = new TextDecoder();
var EVN = [];
function evname(code) {
  if (!EVN.length) { for (var k in RJ_CODES) EVN[RJ_CODES[k]] = k; }
  return EVN[code];
}

function frame(type, ev, uid, payload) {
  var p = enc.encode(JSON.stringify(payload || {}));
  var u = enc.encode(uid || '');
  var buf = new Uint8Array(5 + u.length + p.length);
  var dv = new DataView(buf.buffer);
  buf[0] = type; buf[1] = ev; buf[2] = u.length;
  buf.set(u, 3);
  dv.setUint16(3 + u.length, p.length, true);
  buf.set(p, 5 + u.length);
  return buf.buffer;
}
function post(buf) { if (ws && ws.readyState === 1) ws.send(buf); }
function values() {
  var v = {};
  for (var uid in nodes) {
    var n = nodes[uid];
    if (n && 'value' in n && n.type !== 'file') v[uid] = n.value;
  }
  return v;
}

var E = encodeURIComponent;
var BINDS = {}, V = {};
function ev(p) {
  var o = p[0];
  return o === 0 ? p[1] : o === 1 ? (V[p[1]] ? V[p[1]][0] : 0) : o === 2 ? Date.now()
    : o === 3 ? ev(p[1]) + ev(p[2]) : o === 4 ? ev(p[1]) - ev(p[2])
    : o === 5 ? (ev(p[1]) / ev(p[2])) | 0 : o === 6 ? ev(p[1]) % ev(p[2])
    : o === 7 ? p[1].map(ev).join('') : o === 8 ? ('0' + ev(p[1])).slice(-2)
    : o === 9 ? Math.max(ev(p[1]), ev(p[2])) : V[p[1]] ? Date.now() - V[p[1]][1] : 0;
}
function vp(p) { p.sy = window.scrollY | 0; p.w = window.innerWidth; return p; }

// ---- registration: every node is wired once and released with its element ----
function drop(d) {
  var u = d.getAttribute('data-rj');
  delete nodes[u];
  delete BINDS[u];
}
function purge(node) {
  if (node.getAttribute && node.getAttribute('data-rj')) drop(node);
  node.querySelectorAll('[data-rj]').forEach(drop);
}
function purgeKids(node) { node.querySelectorAll('[data-rj]').forEach(drop); }
function attach(uid, el, def) {
  if (nodes[uid] === el) return;
  nodes[uid] = el;
  var events = (def && def.e) || [];
  var prevent = (def && def.p) || [];
  events.forEach(function (ev) {
    el.addEventListener(ev, function (e) {
      if (prevent.indexOf(ev) >= 0) e.preventDefault();
      var payload = {};
      try { payload = (RJ_PULL[ev] || function () { return {}; })(e); } catch (err) {}
      post(frame(2, RJ_CODES[ev] || 0, uid, vp(payload)));
    });
  });
  if (el && 'value' in el && el.type !== 'file') {
    ['input', 'change'].forEach(function (ev) {
      if (events.indexOf(ev) < 0) {
        el.addEventListener(ev, function () {
          post(frame(2, RJ_CODES[ev] || 0, uid,
              vp({ value: el.value, checked: !!el.checked })));
        });
      }
    });
  }
}
function regKids(parent, subs) {
  subs = subs || {};
  parent.querySelectorAll('[data-rj]').forEach(function (d) {
    attach(d.getAttribute('data-rj'), d, subs[d.getAttribute('data-rj')]);
  });
}
function reg(node, subs) {
  subs = subs || {};
  if (node.getAttribute && node.getAttribute('data-rj')) {
    attach(node.getAttribute('data-rj'), node, subs[node.getAttribute('data-rj')]);
  }
  regKids(node, subs);
}
function regNew(parent, prev, subs) {
  var n = prev ? prev.nextElementSibling : parent.firstElementChild;
  for (; n; n = n.nextElementSibling) reg(n, subs);
}
reg(document, cfg.subs || {});

function dstr(o, w) {
  var n = w ? dv.getUint16(o, true) : dv.getUint8(o); o += w ? 2 : 1;
  return [dec.decode(u8.subarray(o, o + n)), o + n];
}
function rstr(o) { return dstr(o, 0); }
function sstr(o) { return dstr(o, 1); }
function submap(o) {
  var n = dv.getUint8(o); o += 1; var out = {};
  for (var i = 0; i < n; i++) {
    var r = rstr(o); o = r[1];
    var m = dv.getUint8(o); o += 1;
    var def = { e: [], p: [] };
    for (var j = 0; j < m; j++) {
      var name = evname(dv.getUint8(o)); var pv = dv.getUint8(o + 1); o += 2;
      def.e.push(name); if (pv) def.p.push(name);
    }
    out[r[0]] = def;
  }
  return [out, o];
}

var OPS = {
  1: function (o) { var t = sstr(o); if (el) { purgeKids(el); el.textContent = t[0]; } delete BINDS[uid]; return t[1]; },
  2: function (o) { var t = sstr(o); if (el) el.value = t[0]; return t[1]; },
  3: function (o) {
    var n = sstr(o); var len = dv.getUint16(n[1], true); o = n[1] + 2;
    if (len === 0xffff) { if (el) el.removeAttribute(n[0]); return o; }
    var v = dec.decode(u8.subarray(o, o + len)); o += len;
    if (el) el.setAttribute(n[0], v);
    return o;
  },
  4: function (o) {
    var p = sstr(o); var v = sstr(p[1]);
    if (el) el.style.setProperty(p[0], v[0]);
    return v[1];
  },
  5: function (o) {
    var na = dv.getUint8(o); o += 1;
    for (var i = 0; i < na; i++) { var t = sstr(o); o = t[1]; if (el) el.classList.add(t[0]); }
    var nr = dv.getUint8(o); o += 1;
    for (var j = 0; j < nr; j++) { var t2 = sstr(o); o = t2[1]; if (el) el.classList.remove(t2[0]); }
    return o;
  },
  6: function (o) {
    var h = sstr(o); o = h[1];
    var sm = submap(o); o = sm[1];
    if (el) {
      var scrollable = el.scrollHeight > el.clientHeight + 10;
      var near = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
      var prev = el.lastElementChild;
      el.insertAdjacentHTML('beforeend', h[0]);
      regNew(el, prev, sm[0]);
      if (scrollable && near && el.lastElementChild) {
        el.lastElementChild.scrollIntoView({ block: 'end' });
      }
    }
    return o;
  },
  7: function (o) {
    var h = sstr(o); o = h[1];
    var sm = submap(o); o = sm[1];
    if (el) { purgeKids(el); el.innerHTML = h[0]; regKids(el, sm[0]); }
    return o;
  },
  8: function (o) { if (el) { purge(el); el.remove(); } return o; },
  9: function (o) { if (el) el.focus(); return o; },
  10: function (o) { if (el) el.scrollIntoView({ block: 'end' }); return o; },
  11: function (o) { var t = sstr(o); location.href = t[0]; return t[1]; },
  12: function (o) {
    var n = sstr(o); var v = sstr(n[1]);
    document.cookie = n[0] + '=' + E(v[0]) + '; Path=/; SameSite=Lax';
    return v[1];
  },
  13: function (o) {
    var f = el && el.files && el.files[0];
    var meta = f
      ? JSON.stringify({ name: f.name, type: f.type, size: f.size, lastModified: f.lastModified })
      : JSON.stringify({ name: '', type: '', size: 0, lastModified: 0 });
    fetch('/_rejact/upload?view=' + view + '&el=' + uid, {
      method: 'POST',
      body: f || new Blob([]),
      headers: { 'X-Rj-Meta': E(meta) }
    });
    return o;
  },
  14: function (o) { window.scrollTo(0, dv.getInt32(o, true)); return o + 4; },
  15: function (o) {
    var a = sstr(o); var b = sstr(a[1]);
    if (dv.getUint8(b[1])) history.pushState(null, '', '#' + E(a[0]) + '=' + E(b[0]));
    document.documentElement.setAttribute('data-rj-site', a[0]);
    return b[1] + 1;
  },
  16: function (o) { history.back(); return o; },
  17: function (o) {
    var t = sstr(o); BINDS[uid] = JSON.parse(t[0]);
    if (el) { purgeKids(el); el.textContent = ev(BINDS[uid]); }
    timer();
    return t[1];
  },
  18: function (o) {
    var n = sstr(o); V[n[0]] = [dv.getInt32(n[1], true), Date.now()]; return n[1] + 4;
  }
};

function apply(o) {
  var op = dv.getUint8(o); o += 1;
  var r = rstr(o);
  el = nodes[r[0]]; uid = r[0];
  var fn = OPS[op];
  return fn ? fn(r[1]) : r[1];
}

// ---- connection: backoff with jitter, offline badge, authoritative resync ----
var retry = 0, badge = null, tick = 0;
function dot(on) {
  if (on) {
    if (!badge) {
      badge = document.createElement('div');
      badge.textContent = 'offline';
      badge.style.cssText = 'position:fixed;left:8px;bottom:8px;z-index:99;font:11px/1.4 sans-serif;'
        + 'background:#1f2937;color:#f9fafb;padding:3px 8px;border-radius:6px;opacity:.85';
      (document.body || document.documentElement).appendChild(badge);
    }
  } else if (badge) { badge.remove(); badge = null; }
}
function connect() {
  ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://')
    + location.host + '/_rejact/ws?view=' + view
    + '&path=' + encodeURIComponent(cfg.path)
    + '&sid=' + encodeURIComponent(cfg.sid || ''));
  ws.binaryType = 'arraybuffer';
  ws.onopen = function () {
    var re = retry;
    retry = 0;
    dot(false);
    post(frame(1, 0, '', vp({ v: values(), h: location.hash, r: re ? 1 : 0 })));
  };
  ws.onmessage = function (m) {
    if (typeof m.data === 'string') return;
    dv = new DataView(m.data); u8 = new Uint8Array(m.data);
    if (u8[0] !== 0xb1) return;
    var count = u8[1], o = 2;
    for (var i = 0; i < count; i++) o = apply(o);
  };
  ws.onclose = function () {
    dot(true);
    setTimeout(connect, Math.min(30000, 1000 << Math.min(retry++, 5))
      + Math.floor(Math.random() * 400));
  };
}
connect();
function timer() {
  if (tick) return;
  tick = setInterval(function () {
    var n = 0;
    for (var u in BINDS) { el = nodes[u]; if (el) { purgeKids(el); el.textContent = ev(BINDS[u]); n++; } }
    if (!n) { clearInterval(tick); tick = 0; }
  }, 100);
}
addEventListener('popstate', function () { post(frame(4, 0, '', vp({ h: location.hash }))); });

addEventListener('pagehide', function () {
  post(frame(3, 0, '', null));
});
})();
