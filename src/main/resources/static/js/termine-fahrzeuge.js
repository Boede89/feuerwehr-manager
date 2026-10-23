(function () {
  'use strict';

  var panel = document.getElementById('termine-fahrzeuge-panel');
  if (!panel) return;

  var ZONE = 'Europe/Berlin';
  var unitId = panel.dataset.unitId;
  var board = document.getElementById('fb-board');
  var summary = document.getElementById('fb-summary');
  var emptyEl = document.getElementById('fb-empty');
  var loadingEl = document.getElementById('fb-loading');
  var rangeLabel = document.getElementById('fb-range-label');
  var detailOverlay = document.getElementById('fb-detail');
  var detailTitle = document.getElementById('fb-detail-title');
  var detailBody = document.getElementById('fb-detail-body');
  var detailLink = document.getElementById('fb-detail-link');

  var weekStart = null;
  var occupancy = null;

  function parseBoot() {
    var el = document.getElementById('fahrzeug-belegung-data');
    if (!el) return null;
    try {
      return JSON.parse(el.textContent || '{}');
    } catch (e) {
      return null;
    }
  }

  function berlinParts(date) {
    var parts = new Intl.DateTimeFormat('en-GB', {
      timeZone: ZONE,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      weekday: 'short',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    }).formatToParts(date);
    function p(type) {
      var found = parts.find(function (x) { return x.type === type; });
      return found ? found.value : '';
    }
    var hour = p('hour');
    if (hour === '24') hour = '00';
    return {
      year: Number(p('year')),
      month: Number(p('month')),
      day: Number(p('day')),
      weekday: p('weekday'),
      hour: hour,
      minute: p('minute')
    };
  }

  function startOfBerlinDay(y, m, d) {
    // Approximate: construct as local then convert via formatter offset for Berlin noon trick
    var probe = new Date(Date.UTC(y, m - 1, d, 12, 0, 0));
    var fmt = new Intl.DateTimeFormat('en-US', {
      timeZone: ZONE,
      timeZoneName: 'shortOffset'
    });
    var tzPart = fmt.formatToParts(probe).find(function (x) { return x.type === 'timeZoneName'; });
    var offset = tzPart ? tzPart.value : 'GMT+1';
    var match = /GMT([+-])(\d+)(?::?(\d+))?/.exec(offset);
    var sign = match && match[1] === '-' ? -1 : 1;
    var oh = match ? Number(match[2] || 0) : 1;
    var om = match ? Number(match[3] || 0) : 0;
    var offsetMin = sign * (oh * 60 + om);
    return new Date(Date.UTC(y, m - 1, d, 0, 0, 0) - offsetMin * 60 * 1000);
  }

  function mondayOf(date) {
    var p = berlinParts(date);
    var utcNoon = Date.UTC(p.year, p.month - 1, p.day, 12, 0, 0);
    var dow = new Date(utcNoon).getUTCDay(); // 0=Sun
    var delta = dow === 0 ? -6 : 1 - dow;
    var mon = new Date(utcNoon);
    mon.setUTCDate(mon.getUTCDate() + delta);
    return startOfBerlinDay(mon.getUTCFullYear(), mon.getUTCMonth() + 1, mon.getUTCDate());
  }

  function addDays(date, days) {
    var p = berlinParts(date);
    var utc = Date.UTC(p.year, p.month - 1, p.day + days, 12, 0, 0);
    var d = new Date(utc);
    return startOfBerlinDay(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate());
  }

  function toIso(date) {
    return date.toISOString();
  }

  function formatDayHeader(date) {
    var p = berlinParts(date);
    var wdMap = { Mon: 'Mo', Tue: 'Di', Wed: 'Mi', Thu: 'Do', Fri: 'Fr', Sat: 'Sa', Sun: 'So' };
    var wd = wdMap[p.weekday] || p.weekday;
    return {
      weekday: wd,
      day: String(p.day).padStart(2, '0') + '.' + String(p.month).padStart(2, '0'),
      isToday: isSameBerlinDay(date, new Date())
    };
  }

  function isSameBerlinDay(a, b) {
    var pa = berlinParts(a);
    var pb = berlinParts(b);
    return pa.year === pb.year && pa.month === pb.month && pa.day === pb.day;
  }

  function formatRangeLabel(start, endExclusive) {
    var a = berlinParts(start);
    var last = addDays(endExclusive, -1);
    var b = berlinParts(last);
    var left = String(a.day).padStart(2, '0') + '.' + String(a.month).padStart(2, '0') + '.' + a.year;
    var right = String(b.day).padStart(2, '0') + '.' + String(b.month).padStart(2, '0') + '.' + b.year;
    return left + ' – ' + right;
  }

  function formatTimeRange(startIso, endIso) {
    var s = berlinParts(new Date(startIso));
    var e = berlinParts(new Date(endIso));
    function stamp(p) {
      return String(p.day).padStart(2, '0') + '.' + String(p.month).padStart(2, '0') + '.' + p.year
        + ', ' + p.hour + ':' + p.minute;
    }
    return stamp(s) + ' – ' + stamp(e);
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function clamp(n, min, max) {
    return Math.max(min, Math.min(max, n));
  }

  function barGeometry(startIso, endIso, rangeStart, rangeEnd) {
    var start = new Date(startIso).getTime();
    var end = new Date(endIso).getTime();
    var from = rangeStart.getTime();
    var to = rangeEnd.getTime();
    var span = to - from;
    if (span <= 0) return null;
    var left = ((start - from) / span) * 100;
    var right = ((end - from) / span) * 100;
    left = clamp(left, 0, 100);
    right = clamp(right, 0, 100);
    if (right - left < 0.6) {
      right = Math.min(100, left + 0.6);
    }
    if (right <= left) return null;
    return { left: left, width: right - left };
  }

  function nowMarkerPercent(rangeStart, rangeEnd) {
    var now = Date.now();
    var from = rangeStart.getTime();
    var to = rangeEnd.getTime();
    if (now < from || now > to) return null;
    return ((now - from) / (to - from)) * 100;
  }

  function vehicleStatus(vehicleId, bookings, rangeStart, rangeEnd) {
    var now = Date.now();
    var active = bookings.filter(function (b) {
      if (Number(b.vehicleId) !== Number(vehicleId)) return false;
      var s = new Date(b.startAt).getTime();
      var e = new Date(b.endAt).getTime();
      return s < rangeEnd.getTime() && e > rangeStart.getTime();
    });
    var nowBusy = active.some(function (b) {
      var s = new Date(b.startAt).getTime();
      var e = new Date(b.endAt).getTime();
      return s <= now && e > now && b.status === 'APPROVED';
    });
    if (nowBusy) return { key: 'busy', label: 'Jetzt belegt' };
    var weekApproved = active.some(function (b) { return b.status === 'APPROVED'; });
    if (weekApproved) return { key: 'partial', label: 'Belegt in der Woche' };
    var weekPending = active.some(function (b) { return b.status === 'PENDING'; });
    if (weekPending) return { key: 'pending', label: 'Antrag offen' };
    return { key: 'free', label: 'Frei' };
  }

  function renderSummary(vehicles, bookings, rangeStart, rangeEnd) {
    if (!summary) return;
    if (!vehicles.length) {
      summary.hidden = true;
      return;
    }
    var free = [];
    var busy = [];
    vehicles.forEach(function (v) {
      var st = vehicleStatus(v.id, bookings, rangeStart, rangeEnd);
      if (st.key === 'busy') busy.push(v.name);
      if (st.key === 'free') free.push(v.name);
    });
    var bits = [];
    if (busy.length) {
      bits.push('<span><strong>Jetzt belegt:</strong> ' + escapeHtml(busy.join(', ')) + '</span>');
    } else {
      bits.push('<span><strong>Jetzt:</strong> alle Fahrzeuge frei</span>');
    }
    if (free.length && free.length !== vehicles.length) {
      bits.push('<span><strong>Frei in der Woche:</strong> ' + escapeHtml(free.join(', ')) + '</span>');
    }
    summary.innerHTML = bits.join('');
    summary.hidden = false;
  }

  function openDetail(booking) {
    if (!detailOverlay || !booking) return;
    if (detailTitle) {
      detailTitle.textContent = booking.status === 'PENDING' ? 'Offener Antrag' : 'Genehmigte Reservierung';
    }
    if (detailBody) {
      detailBody.innerHTML =
        '<dl class="fahrzeug-belegung-detail">'
        + '<div><dt>Zeitraum</dt><dd>' + escapeHtml(formatTimeRange(booking.startAt, booking.endAt)) + '</dd></div>'
        + '<div><dt>Antragsteller</dt><dd>' + escapeHtml(booking.requesterName || '—') + '</dd></div>'
        + '<div><dt>Grund</dt><dd>' + escapeHtml(booking.reason || '—') + '</dd></div>'
        + '<div><dt>Ort</dt><dd>' + escapeHtml(booking.location || '—') + '</dd></div>'
        + '</dl>';
    }
    if (detailLink) {
      detailLink.href = '/reservierungen?unit=' + encodeURIComponent(unitId) + '&tab=verwaltung';
    }
    detailOverlay.classList.add('active');
    detailOverlay.setAttribute('aria-hidden', 'false');
    document.body.classList.add('modal-open');
  }

  function closeDetail() {
    if (!detailOverlay) return;
    detailOverlay.classList.remove('active');
    detailOverlay.setAttribute('aria-hidden', 'true');
    if (!document.querySelector('.modal-overlay.active')) {
      document.body.classList.remove('modal-open');
    }
  }

  function renderBoard(data) {
    occupancy = data || { vehicles: [], bookings: [] };
    var vehicles = occupancy.vehicles || [];
    var bookings = occupancy.bookings || [];
    var rangeStart = occupancy.rangeStart ? new Date(occupancy.rangeStart) : weekStart;
    var rangeEnd = occupancy.rangeEnd ? new Date(occupancy.rangeEnd) : addDays(weekStart, 7);
    weekStart = rangeStart;

    if (rangeLabel) {
      rangeLabel.textContent = formatRangeLabel(rangeStart, rangeEnd);
    }

    if (!vehicles.length) {
      if (board) board.innerHTML = '';
      if (emptyEl) emptyEl.hidden = false;
      if (summary) summary.hidden = true;
      return;
    }
    if (emptyEl) emptyEl.hidden = true;

    renderSummary(vehicles, bookings, rangeStart, rangeEnd);

    var days = [];
    for (var i = 0; i < 7; i++) {
      days.push(formatDayHeader(addDays(rangeStart, i)));
    }

    var nowPct = nowMarkerPercent(rangeStart, rangeEnd);
    var html = '';
    html += '<div class="fahrzeug-belegung__head">';
    html += '<div class="fahrzeug-belegung__corner">Fahrzeug</div>';
    html += '<div class="fahrzeug-belegung__days">';
    days.forEach(function (d) {
      html += '<div class="fahrzeug-belegung__day' + (d.isToday ? ' fahrzeug-belegung__day--today' : '') + '">'
        + '<span class="fahrzeug-belegung__day-wd">' + escapeHtml(d.weekday) + '</span>'
        + '<span class="fahrzeug-belegung__day-num">' + escapeHtml(d.day) + '</span>'
        + '</div>';
    });
    html += '</div></div>';

    vehicles.forEach(function (vehicle) {
      var st = vehicleStatus(vehicle.id, bookings, rangeStart, rangeEnd);
      html += '<div class="fahrzeug-belegung__row">';
      html += '<div class="fahrzeug-belegung__vehicle">'
        + '<strong class="fahrzeug-belegung__name">' + escapeHtml(vehicle.name) + '</strong>'
        + '<span class="fahrzeug-belegung__status fahrzeug-belegung__status--' + st.key + '">'
        + escapeHtml(st.label) + '</span>'
        + '</div>';
      html += '<div class="fahrzeug-belegung__track">';
      html += '<div class="fahrzeug-belegung__grid" aria-hidden="true">';
      for (var d = 0; d < 7; d++) {
        html += '<span class="fahrzeug-belegung__grid-cell'
          + (days[d].isToday ? ' fahrzeug-belegung__grid-cell--today' : '')
          + '"></span>';
      }
      html += '</div>';
      if (nowPct != null) {
        html += '<div class="fahrzeug-belegung__now" style="left:' + nowPct.toFixed(3) + '%" title="Jetzt"></div>';
      }
      bookings.forEach(function (b) {
        if (Number(b.vehicleId) !== Number(vehicle.id)) return;
        var geo = barGeometry(b.startAt, b.endAt, rangeStart, rangeEnd);
        if (!geo) return;
        var statusClass = b.status === 'PENDING'
          ? 'fahrzeug-belegung__bar--pending'
          : 'fahrzeug-belegung__bar--approved';
        var title = (b.reason || 'Reservierung') + ' · ' + formatTimeRange(b.startAt, b.endAt);
        html += '<button type="button" class="fahrzeug-belegung__bar ' + statusClass + '"'
          + ' style="left:' + geo.left.toFixed(3) + '%;width:' + geo.width.toFixed(3) + '%"'
          + ' data-reservation-id="' + escapeHtml(b.reservationId) + '"'
          + ' data-vehicle-id="' + escapeHtml(b.vehicleId) + '"'
          + ' title="' + escapeHtml(title) + '">'
          + '<span class="fahrzeug-belegung__bar-text">' + escapeHtml(b.reason || 'Reservierung') + '</span>'
          + '</button>';
      });
      html += '</div></div>';
    });

    board.innerHTML = html;
    board.querySelectorAll('.fahrzeug-belegung__bar').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var rid = Number(btn.dataset.reservationId);
        var vid = Number(btn.dataset.vehicleId);
        var booking = bookings.find(function (b) {
          return Number(b.reservationId) === rid && Number(b.vehicleId) === vid;
        });
        openDetail(booking);
      });
    });
  }

  function setLoading(on) {
    if (loadingEl) loadingEl.hidden = !on;
    if (board) board.classList.toggle('is-loading', !!on);
  }

  function loadRange(start) {
    weekStart = start;
    var rangeEnd = addDays(weekStart, 7);
    setLoading(true);
    var url = '/termine/api/fahrzeuge/belegung?unit=' + encodeURIComponent(unitId)
      + '&from=' + encodeURIComponent(toIso(weekStart))
      + '&to=' + encodeURIComponent(toIso(rangeEnd));
    return fetch(url, { credentials: 'same-origin', headers: { Accept: 'application/json' } })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        if (data && data.ok === false) {
          throw new Error(data.message || 'Fehler');
        }
        renderBoard(data);
      })
      .catch(function () {
        if (board) {
          board.innerHTML = '<p class="hint" style="padding:1rem;">Belegung konnte nicht geladen werden.</p>';
        }
      })
      .finally(function () {
        setLoading(false);
      });
  }

  document.getElementById('fb-prev')?.addEventListener('click', function () {
    loadRange(addDays(weekStart, -7));
  });
  document.getElementById('fb-next')?.addEventListener('click', function () {
    loadRange(addDays(weekStart, 7));
  });
  document.getElementById('fb-today')?.addEventListener('click', function () {
    loadRange(mondayOf(new Date()));
  });
  document.querySelectorAll('[data-fb-close-detail]').forEach(function (btn) {
    btn.addEventListener('click', function (ev) {
      ev.preventDefault();
      closeDetail();
    });
  });

  var boot = parseBoot();
  if (boot && boot.rangeStart) {
    weekStart = mondayOf(new Date(boot.rangeStart));
    renderBoard(boot);
  } else {
    weekStart = mondayOf(new Date());
    loadRange(weekStart);
  }
})();
