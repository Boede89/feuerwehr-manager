(function () {
  'use strict';

  var root = document.getElementById('berichte-maengel-panel');
  if (!root) {
    return;
  }

  var unitId = root.dataset.unitId;
  var currentUserId = root.dataset.currentUserId ? Number(root.dataset.currentUserId) : null;
  var isAdmin = root.dataset.isAdmin === 'true';
  var csrfToken = root.dataset.csrfToken || '';
  var csrfParam = root.dataset.csrfParam || '_csrf';
  var FILTER_STORAGE_PREFIX = 'feuerwehr.berichte.filters.';

  function loadStoredFilters(tab, defaults) {
    if (window.BerichteListFilters && typeof window.BerichteListFilters.load === 'function') {
      return window.BerichteListFilters.load(unitId, tab, defaults);
    }
    var base = Object.assign({}, defaults || {});
    try {
      var raw = sessionStorage.getItem(FILTER_STORAGE_PREFIX + String(unitId || '') + '.' + tab);
      if (!raw) {
        return base;
      }
      var parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object') {
        return base;
      }
      Object.keys(parsed).forEach(function (key) {
        base[key] = parsed[key];
      });
      return base;
    } catch (e) {
      return base;
    }
  }

  function persistFilters() {
    var payload = { year: filters.year };
    if (window.BerichteListFilters && typeof window.BerichteListFilters.save === 'function') {
      window.BerichteListFilters.save(unitId, 'maengel', payload);
      return;
    }
    try {
      sessionStorage.setItem(FILTER_STORAGE_PREFIX + String(unitId || '') + '.maengel', JSON.stringify(payload));
    } catch (e) {
      // ignore
    }
  }

  var filters = loadStoredFilters('maengel', {
    year: Number(root.dataset.filterYear) || new Date().getFullYear()
  });
  filters.year = Number(filters.year) || new Date().getFullYear();
  var allItems = [];

  persistFilters();

  function esc(text) {
    var div = document.createElement('div');
    div.textContent = text == null ? '' : String(text);
    return div.innerHTML;
  }

  function fmtDate(iso) {
    if (!iso) {
      return '—';
    }
    var parts = iso.split('-');
    if (parts.length !== 3) {
      return iso;
    }
    return parts[2] + '.' + parts[1] + '.' + parts[0];
  }

  function canEditItem(item) {
    if (isAdmin) {
      return true;
    }
    return item.createdByUserId != null && item.createdByUserId === currentUserId;
  }

  function canDeleteItem(item) {
    return canEditItem(item);
  }

  function listReturnPath() {
    return '/berichte?tab=maengel&year=' + filters.year;
  }

  function tablePdfPrintActions(reportId) {
    var base = '/berichte/maengelberichte/' + reportId;
    return '<a class="btn btn--outline btn--sm" href="' + base +
      '/pdf?unit=' + encodeURIComponent(unitId) + '">PDF herunterladen</a>' +
      '<form method="post" action="' + base + '/drucken" class="table-inline-form">' +
      '<input type="hidden" name="' + esc(csrfParam) + '" value="' + esc(csrfToken) + '"/>' +
      '<input type="hidden" name="unit" value="' + esc(unitId) + '"/>' +
      '<input type="hidden" name="returnUrl" value="' + esc(listReturnPath()) + '"/>' +
      '<button type="submit" class="btn btn--outline btn--sm">Drucken</button></form>';
  }

  function closeModal() {
    var modal = document.getElementById('modal-maengel');
    if (modal) {
      modal.style.display = 'none';
    }
    document.body.classList.remove('modal-open');
    var scroll = document.getElementById('modal-maengel-body-scroll');
    if (scroll) {
      scroll.innerHTML = '';
    }
    var footer = document.getElementById('modal-maengel-footer');
    if (footer) {
      footer.innerHTML = '';
    }
  }

  function postAction(url, returnPath) {
    var form = document.createElement('form');
    form.method = 'post';
    form.action = url;
    form.innerHTML =
      '<input type="hidden" name="' + esc(csrfParam) + '" value="' + esc(csrfToken) + '"/>' +
      '<input type="hidden" name="unit" value="' + esc(unitId) + '"/>' +
      '<input type="hidden" name="returnUrl" value="' + esc(returnPath) + '"/>';
    document.body.appendChild(form);
    form.submit();
  }

  function buildFooter(meta) {
    var footer = document.getElementById('modal-maengel-footer');
    if (!footer) {
      return;
    }
    var returnPath = listReturnPath();
    var html = '';
    html += '<a class="btn btn--outline" href="/berichte/maengelberichte/' + meta.reportId +
      '/pdf?unit=' + encodeURIComponent(unitId) + '">PDF herunterladen</a>';
    html += '<button type="button" class="btn btn--outline" id="btn-maengel-modal-print">Drucken</button>';
    if (meta.canEdit === 'true') {
      html += '<a class="btn btn--primary" href="/berichte/maengelberichte/' + meta.reportId +
        '/bearbeiten?unit=' + encodeURIComponent(unitId) + '">Bearbeiten</a>';
    }
    html += '<button type="button" class="btn btn--outline" id="btn-maengel-modal-close-footer">Schließen</button>';
    footer.innerHTML = html;
    document.getElementById('btn-maengel-modal-close-footer')?.addEventListener('click', closeModal);
    document.getElementById('btn-maengel-modal-print')?.addEventListener('click', function () {
      postAction('/berichte/maengelberichte/' + meta.reportId + '/drucken', returnPath);
    });
  }

  function openModal(id) {
    var modal = document.getElementById('modal-maengel');
    var title = document.getElementById('modal-maengel-title');
    var scroll = document.getElementById('modal-maengel-body-scroll');
    if (!modal || !scroll) {
      return;
    }
    title.textContent = 'Lade...';
    scroll.innerHTML = '<p class="hint">Lade...</p>';
    modal.style.display = 'flex';
    document.body.classList.add('modal-open');

    fetch('/berichte/maengelberichte/' + id + '/modal?unit=' + encodeURIComponent(unitId), {
      credentials: 'same-origin'
    })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('Mängelbericht konnte nicht geladen werden');
        }
        return res.text();
      })
      .then(function (html) {
        scroll.innerHTML = html;
        var metaRoot = scroll.querySelector('.einsatzbericht-modal-root');
        if (!metaRoot) {
          throw new Error('Ungültige Antwort');
        }
        title.textContent = metaRoot.dataset.title || 'Mängelbericht';
        buildFooter(metaRoot.dataset);
      })
      .catch(function (err) {
        title.textContent = 'Fehler';
        scroll.innerHTML = '<p class="error-msg">' + esc(err.message) + '</p>';
      });
  }

  function renderTable() {
    var wrap = document.getElementById('maengel-table-wrap');
    if (!wrap) {
      return;
    }
    if (!allItems.length) {
      wrap.innerHTML = '<p class="hint" style="margin:0;padding:20px 0;">Keine Mängelberichte für das gewählte Jahr gefunden.</p>';
      return;
    }
    wrap.innerHTML =
      '<div style="overflow-x:auto">' +
      '<table class="data-table"><thead><tr>' +
      '<th>Datum</th><th>Standort</th><th>Mangel an</th><th>Bezeichnung</th><th>Aufgenommen durch</th><th>Aktionen</th>' +
      '</tr></thead><tbody>' +
      allItems.map(function (r) {
        return '<tr>' +
          '<td>' + esc(fmtDate(r.aufgenommenAm)) + '</td>' +
          '<td>' + esc(r.standortLabel || '—') + '</td>' +
          '<td>' + esc(r.mangelAnLabel || '—') + '</td>' +
          '<td>' + esc(r.bezeichnung || '—') + '</td>' +
          '<td>' + esc(r.recordedByDisplay || '—') + '</td>' +
          '<td><div class="btn-group">' +
          '<button type="button" class="btn btn--outline btn--sm" data-action="view" data-id="' + r.id + '">Anzeigen</button>' +
          tablePdfPrintActions(r.id) +
          (canEditItem(r) ? '<a class="btn btn--outline btn--sm" href="/berichte/maengelberichte/' + r.id +
            '/bearbeiten?unit=' + encodeURIComponent(unitId) + '">Bearbeiten</a>' : '') +
          (canDeleteItem(r) ?
            '<form method="post" action="/berichte/maengelberichte/' + r.id + '/delete" class="table-inline-form" ' +
            'data-confirm data-confirm-title="Mängelbericht löschen?" ' +
            'data-confirm-message="Diese Aktion kann nicht rückgängig gemacht werden." ' +
            'data-confirm-label="Löschen" data-confirm-variant="danger">' +
            '<input type="hidden" name="' + esc(csrfParam) + '" value="' + esc(csrfToken) + '"/>' +
            '<input type="hidden" name="unit" value="' + esc(unitId) + '"/>' +
            '<button type="submit" class="btn btn--danger btn--sm">Löschen</button></form>' : '') +
          '</div></td></tr>';
      }).join('') +
      '</tbody></table></div>';

    wrap.querySelectorAll('[data-action="view"]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        openModal(btn.dataset.id);
      });
    });
  }

  function loadList() {
    fetch('/berichte/maengelberichte/list?unit=' + encodeURIComponent(unitId) +
      '&year=' + encodeURIComponent(filters.year), { credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('Liste konnte nicht geladen werden');
        }
        return res.json();
      })
      .then(function (data) {
        allItems = data.items || [];
        renderTable();
      })
      .catch(function (err) {
        var wrap = document.getElementById('maengel-table-wrap');
        if (wrap) {
          wrap.innerHTML = '<p class="error-msg">' + esc(err.message) + '</p>';
        }
      });
  }

  function initYearFilter() {
    var select = document.getElementById('filter-year-maengel');
    if (!select) {
      return;
    }
    var currentYear = new Date().getFullYear();
    for (var y = currentYear + 1; y >= currentYear - 5; y--) {
      var opt = document.createElement('option');
      opt.value = String(y);
      opt.textContent = String(y);
      if (y === filters.year) {
        opt.selected = true;
      }
      select.appendChild(opt);
    }
    select.addEventListener('change', function () {
      filters.year = Number(select.value);
      persistFilters();
      loadList();
    });
  }

  document.getElementById('btn-close-maengel-modal')?.addEventListener('click', closeModal);
  document.getElementById('modal-maengel')?.querySelector('.modal__backdrop')?.addEventListener('click', closeModal);

  initYearFilter();
  loadList();
})();
