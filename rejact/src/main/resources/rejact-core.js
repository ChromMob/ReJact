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
function evname(code) { return RJ_N[code - 1]; }
function evcode(name) { return RJ_N.indexOf(name) + 1; }

function frame(type, ev, uid, payload) {
  var p = enc.encode(JSON.stringify(payload || {}));
  var u = enc.encode(uid || '');
  if (u.length > 255 || 7 + u.length + p.length > 4 * 1024 * 1024) throw new RangeError('ReJact event exceeds wire limits');
  var buf = new Uint8Array(7 + u.length + p.length);
  var dv = new DataView(buf.buffer);
  buf[0] = type; buf[1] = ev; buf[2] = u.length;
  buf.set(u, 3);
  dv.setUint32(3 + u.length, p.length, true);
  buf.set(p, 7 + u.length);
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
      try {
        var s = RJ_PULL[ev];
        if (s) {
          if (typeof s === 'string') s = RJ_PULL[ev] = s.split(',');
          for (var i = 0; i < s.length; i += 2) {
            var k = s[i], t = +s[i + 1], from = e, key = k;
            if (k.charAt(0) === '@') { key = k.slice(1); from = e.target || e; }
            var v = from[key];
            payload[key] = t === 3 ? (v ? v.length | 0 : 0) : t === 1 ? v | 0 : t === 2 ? !!v : v;
          }
        }
      } catch (err) {}
      post(frame(2, evcode(ev), uid, vp(payload)));
    });
  });
  if (el && 'value' in el && el.type !== 'file') {
    ['input', 'change'].forEach(function (ev) {
      if (events.indexOf(ev) < 0) {
        el.addEventListener(ev, function () {
          post(frame(2, evcode(ev), uid,
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
  var n = w ? dv.getUint32(o, true) : dv.getUint8(o); o += w ? 4 : 1;
  if (w && n === 0xffffffff) return [null, o];
  if (n > u8.length - o) throw new RangeError('ReJact truncated wire string');
  return [dec.decode(u8.subarray(o, o + n)), o + n];
}
function rstr(o) { return dstr(o, 0); }
function sstr(o) { return dstr(o, 1); }
function submap(o) {
  var n = dv.getUint32(o, true); o += 4; var out = {};
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

// Generic command interpreter: set(path) / call(method,args) / html / del / var / bind.
// Paths: * = text content, @attr, #style, .class, else a property.
function apply(o) {
  var t = dv.getUint8(o); o += 1;
  var r = rstr(o);
  el = nodes[r[0]]; uid = r[0]; o = r[1];
  if (t === 0) {
    var p = sstr(o); var v = sstr(p[1]); o = v[1];
    if (!el) return o;
    var path = p[0], val = v[0];
    if (path === '*') { purgeKids(el); el.textContent = val; delete BINDS[uid]; }
    else if (path.charAt(0) === '@') {
      var n = path.slice(1);
      if (val === null) el.removeAttribute(n); else el.setAttribute(n, val);
    } else if (path.charAt(0) === '#') el.style.setProperty(path.slice(1), val);
    else if (path.charAt(0) === '.') {
      if (val === null) el.classList.remove(path.slice(1));
      else el.classList.add(path.slice(1));
    } else el[path] = val;
    return o;
  }
  if (t === 1) {
    var m = sstr(o); var a = sstr(m[1]); o = a[1];
    var name = m[0], args = JSON.parse(a[0] || '[]');
    var dot = name.indexOf('.'), obj = el, fn = name;
    if (dot > 0) { obj = el && el[name.slice(0, dot)]; fn = name.slice(dot + 1); }
    if (obj && typeof obj[fn] === 'function') obj[fn].apply(obj, args);
    else if (SYS[name]) SYS[name](args, el, uid);
    return o;
  }
  if (t === 2) {
    var rp = dv.getUint8(o); o += 1;
    var h = sstr(o); var sm = submap(h[1]); o = sm[1];
    if (el) {
      if (rp) { purgeKids(el); el.innerHTML = h[0]; regKids(el, sm[0]); }
      else {
        var scrollable = el.scrollHeight > el.clientHeight + 10;
        var near = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
        var prev = el.lastElementChild;
        el.insertAdjacentHTML('beforeend', h[0]);
        regNew(el, prev, sm[0]);
        if (scrollable && near && el.lastElementChild) el.lastElementChild.scrollIntoView({ block: 'end' });
      }
    }
    return o;
  }
  if (t === 3) { if (el) { purge(el); el.remove(); } return o; }
  if (t === 4) { var vn = sstr(o); V[vn[0]] = [dv.getInt32(vn[1], true), Date.now()]; return vn[1] + 4; }
  if (t === 5) {
    var b = sstr(o); BINDS[uid] = JSON.parse(b[0]);
    if (el) { purgeKids(el); el.textContent = ev(BINDS[uid]); }
    timer(); return b[1];
  }
  return o;
}

// Host helpers (empty command target). DOM methods are preferred when the target has them.
var SYS = {
  navigate: function (a) { location.href = a[0]; },
  cookie: function (a) { document.cookie = a[0] + '=' + E(a[1]) + '; Path=/; SameSite=Lax'; },
  back: function () { history.back(); },
  scrollTo: function (a) { window.scrollTo(a[0], a[1]); },
  site: function (a) {
    if (a[2]) history.pushState(null, '', '#' + E(a[0]) + '=' + E(a[1]));
    document.documentElement.setAttribute('data-rj-site', a[0]);
  },
  // Reorder children to the given uid sequence by moving nodes, never re-creating them, so
  // focus, selection, scroll and running transitions all survive a sort.
  'rj.order': function (a, node) {
    if (!node) return;
    var at = node.firstChild;
    for (var i = 0; i < a.length; i++) {
      var child = nodes[a[i]];
      if (!child || child.parentNode !== node) continue;
      if (child === at) { at = at.nextSibling; continue; }
      node.insertBefore(child, at);
    }
  },
  readFile: function (a, node, id) {
    var f = node && node.files && node.files[0];
    var meta = f
      ? JSON.stringify({ name: f.name, type: f.type, size: f.size, lastModified: f.lastModified })
      : JSON.stringify({ name: '', type: '', size: 0, lastModified: 0 });
    fetch('/_rejact/upload?view=' + view + '&el=' + id, {
      method: 'POST',
      body: f || new Blob([]),
      headers: { 'X-Rj-Meta': E(meta) }
    });
  }
};

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
    + '&path=' + encodeURIComponent(cfg.path));
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
    if (u8[0] !== 0xb2) { ws.close(); return; }
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
