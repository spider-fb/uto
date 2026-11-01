(function () {
  "use strict";
  var LABEL = { published: "منشور", draft: "مسودة", pending: "قيد الانتظار", failed: "فشل" };
  var $ = function (id) { return document.getElementById(id); };
  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined && text !== null) e.textContent = text;
    return e;
  }
  function fmt(iso) {
    return iso ? new Date(iso).toLocaleString("ar-EG-u-nu-latn", { dateStyle: "medium", timeStyle: "short" }) : "—";
  }
  function safe(u) { return /^https?:\/\//i.test(u || "") ? u : null; }
  function link(text, href, cls) {
    var h = safe(href);
    if (!h) return el("span", cls, text);
    var a = el("a", cls, text); a.href = h; a.target = "_blank"; a.rel = "noopener noreferrer"; return a;
  }
  function table(t, head, rows) {
    t.textContent = "";
    var tr = el("tr");
    head.forEach(function (h) { tr.appendChild(el("th", null, h)); });
    t.appendChild(el("thead")).appendChild(tr);
    var tb = el("tbody");
    if (!rows.length) { var r = el("tr"), c = el("td", "empty", "لا توجد بيانات بعد"); c.colSpan = head.length; r.appendChild(c); tb.appendChild(r); }
    rows.forEach(function (cells) {
      var r = el("tr");
      cells.forEach(function (c) { var td = el("td"); td.appendChild(typeof c === "object" ? c : document.createTextNode(c)); r.appendChild(td); });
      tb.appendChild(r);
    });
    t.appendChild(tb);
  }
  function repoBase() {
    var m = location.hostname.match(/^(.+)\.github\.io$/), p = location.pathname.split("/")[1];
    return m && p ? "https://github.com/" + m[1] + "/" + p : null;
  }
  var S = null;

  function drawBooks() {
    var f = $("filter").value, ul = $("books");
    ul.textContent = "";
    var list = S.books.filter(function (b) { return !f || b.status === f; });
    if (!list.length) { ul.appendChild(el("li", "empty", "لا توجد كتب بهذه الحالة")); return; }
    list.forEach(function (b) {
      var li = el("li"), left = el("div");
      left.appendChild(link(b.title, b.postUrl || b.link));
      left.appendChild(el("br"));
      left.appendChild(el("small", null, b.source + " — " + fmt(b.publishedAt || b.discoveredAt)));
      if (b.error) { left.appendChild(el("br")); left.appendChild(el("small", "bad", b.error)); }
      li.appendChild(left);
      li.appendChild(el("span", "pill " + b.status, LABEL[b.status] || b.status));
      ul.appendChild(li);
    });
  }

  function draw() {
    var c = S.counts;
    $("meta").textContent = "آخر مزامنة: " + fmt(S.lastSync) + " — الهدف: " + S.target + (S.targetUrl ? " (" + S.targetUrl + ")" : "") +
      " — الوضع: " + (S.publishMode === "live" ? "نشر مباشر" : "مسودات");
    var tally = $("tally"); tally.textContent = "";
    [["total", "كتب مكتشفة"], ["published", "منشورة"], ["drafts", "مسودات"], ["pending", "قيد الانتظار"], ["failed", "فشلت"]].forEach(function (x) {
      var d = el("div"); d.appendChild(el("b", null, String(c[x[0]] || 0))); d.appendChild(el("span", null, x[1])); tally.appendChild(d);
    });
    var base = repoBase(), nav = $("actions"); nav.textContent = "";
    if (base) {
      [["تشغيل يدوي", base + "/actions/workflows/sync.yml"], ["تعديل المصادر", base + "/edit/main/data/sources.json"], ["تعديل الإعدادات", base + "/edit/main/data/settings.json"]].forEach(function (x) {
        var a = el("a", null, x[0]); a.href = x[1]; a.target = "_blank"; a.rel = "noopener noreferrer"; nav.appendChild(a);
      });
    }
    table($("sources"), ["المصدر", "الحالة", "العناصر", "آخر فحص"], S.sources.map(function (s) {
      var st = !s.enabled ? el("span", "off", "معطّل") : s.ok ? el("span", "ok", "يعمل") : el("span", "bad", "خطأ: " + (s.error || "غير معروف"));
      return [link(s.name || s.url, s.url), st, String(s.items || 0), fmt(s.checkedAt)];
    }));
    table($("history"), ["الوقت", "الهدف", "جديدة", "منشورة", "مسودات", "فشل", "انتظار"], S.history.map(function (h) {
      return [fmt(h.finishedAt), h.target || "", String(h.discovered), String(h.published), String(h.drafts), String(h.failed), String(h.pending)];
    }));
    var eu = $("errors"); eu.textContent = "";
    var n = 0;
    S.history.forEach(function (h) {
      (h.errors || []).forEach(function (m) {
        if (n++ > 40) return;
        var li = el("li"); li.appendChild(el("span", "bad", m)); li.appendChild(el("small", null, fmt(h.finishedAt))); eu.appendChild(li);
      });
    });
    if (!n) eu.appendChild(el("li", "empty", "لا توجد أخطاء في آخر التشغيلات"));
    drawBooks();
  }

  $("filter").addEventListener("change", function () { if (S) drawBooks(); });
  fetch("data/status.json?t=" + Date.now(), { cache: "no-store" })
    .then(function (r) { if (!r.ok) throw new Error("HTTP " + r.status); return r.json(); })
    .then(function (j) { S = j; draw(); })
    .catch(function (e) { $("meta").textContent = "تعذر تحميل بيانات الحالة: " + e.message; });
})();
