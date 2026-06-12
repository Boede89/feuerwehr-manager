(function () {
  'use strict';

  var root = document.getElementById('berichte-anwesenheit-panel');
  if (!root) {
    return;
  }

  var unitId = root.dataset.unitId;
  var currentUserId = root.dataset.currentUserId ? Number(root.dataset.currentUserId) : null;
  var isAdmin = root.dataset.isAdmin === 'true';
  var canApprove = root.dataset.canApprove === 'true';
  var csrfToken = root.dataset.csrfToken || '';
  var csrfParam = root.dataset.csrfParam || '_csrf';

  var filters = {
    year: Number(root.dataset.filterYear) || new Date().getFullYear(),
    category: '',
    status: ''
  };

  var allItems = [];

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
    if (item.statusKey !== 'entwurf') {
      return false;
    }
    if (canApprove) {
      return true;
    }
    return item.createdByUserId != null && item.createdByUserId === currentUserId;
  }

  function canDeleteItem(item) {
    if (isAdmin) {
      return true;
    }
    if (canApprove && (item.statusKey === 'freigegeben' || item.statusKey === 'archiviert')) {
      return true;
    }
    if (item.statusKey !== 'entwurf') {
      return false;
    }
    if (canApprove) {
      return true;
    }
    return item.createdByUserId != null && item.createdByUserId === currentUserId;
  }

  function filteredItems() {
    return allItems.filter(function (item) {
      if (filters.category && item.terminCategoryKey !== filters.category) {
        return false;
      }
      if (filters.status && item.statusKey !== filters.status) {
        return false;
      }
      return true;
    });
  }

  function renderTable() {
    var wrap = document.getElementById('attendance-table-wrap');
    if (!wrap) {
      return;
    }
    var items = filteredItems();
    if (!items.length) {
      wrap.innerHTML = '<p class="hint" style="margin:0;padding:20px 0;">Keine Anwesenheitslisten für die gewählten Filter gefunden.</p>';
      return;
    }
    wrap.innerHTML =
      '<div style="overflow-x:auto">' +
      '<table class="data-table"><thead><tr>' +
      '<th>Nr.</th><th>Datum</th><th>Bezeichnung</th><th>Bereich</th><th>Ort</th><th>Status</th><th>Quelle</th><th>Aktionen</th>' +
      '</tr></thead><tbody>' +
      items.map(function (r) {
        return '<tr>' +
          '<td class="text-muted text-xs">' + esc(r.reportNumber || '—') + '</td>' +
          '<td>' + esc(fmtDate(r.eventDate)) + '</td>' +
          '<td>' + esc(r.title || '—') + '</td>' +
          '<td>' + esc(r.terminCategoryLabel || '—') + '</td>' +
          '<td>' + esc(r.location || '—') + '</td>' +
          '<td><span class="incident-status-pill incident-status-pill--' + esc(r.statusKey) + '">' +
          esc(r.statusLabel) + '</span></td>' +
          '<td><span class="text-muted text-sm">' + (r.terminSource ? 'Termin' : 'Manuell') + '</span></td>' +
          '<td><div class="btn-group">' +
          '<a class="btn btn--outline btn--sm" href="/berichte/anwesenheitslisten/' + r.id +
            '?unit=' + encodeURIComponent(unitId) + '">Anzeigen</a>' +
          (canEditItem(r) ? '<a class="btn btn--outline btn--sm" href="/berichte/anwesenheitslisten/' + r.id +
            '/bearbeiten?unit=' + encodeURIComponent(unitId) + '">Bearbeiten</a>' : '') +
          (canDeleteItem(r) ?
            '<form method="post" action="/berichte/anwesenheitslisten/' + r.id + '/delete" class="table-inline-form" ' +
            'onsubmit="return confirm(\'Anwesenheitsliste wirklich löschen? Diese Aktion kann nicht rückgängig gemacht werden.\');">' +
            '<input type="hidden" name="' + esc(csrfParam) + '" value="' + esc(csrfToken) + '"/>' +
            '<input type="hidden" name="unit" value="' + esc(unitId) + '"/>' +
            '<input type="hidden" name="year" value="' + filters.year + '"/>' +
            '<button type="submit" class="btn btn--danger btn--sm">Löschen</button></form>' : '') +
          '</div></td></tr>';
      }).join('') +
      '</tbody></table></div>';
  }

  function loadList() {
    var wrap = document.getElementById('attendance-table-wrap');
    if (wrap) {
      wrap.innerHTML = '<p class="text-muted text-sm">Lade...</p>';
    }
    return fetch('/berichte/anwesenheitslisten/list?unit=' + encodeURIComponent(unitId) +
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
        if (wrap) {
          wrap.innerHTML = '<p class="error-msg">' + esc(err.message) + '</p>';
        }
      });
  }

  function initFilters() {
    var yearSel = document.getElementById('filter-year-anwesenheit');
    var categorySel = document.getElementById('filter-category-anwesenheit');
    var statusSel = document.getElementById('filter-status-anwesenheit');

    if (yearSel) {
      var curYear = new Date().getFullYear();
      yearSel.innerHTML = '';
      for (var y = curYear; y >= curYear - 10; y--) {
        var opt = document.createElement('option');
        opt.value = y;
        opt.textContent = y;
        if (y === filters.year) {
          opt.selected = true;
        }
        yearSel.appendChild(opt);
      }
      yearSel.addEventListener('change', function () {
        filters.year = Number(yearSel.value);
        loadList();
      });
    }

    if (categorySel) {
      categorySel.addEventListener('change', function () {
        filters.category = categorySel.value;
        renderTable();
      });
    }

    if (statusSel) {
      statusSel.addEventListener('change', function () {
        filters.status = statusSel.value;
        renderTable();
      });
    }
  }

  initFilters();
  loadList();
})();
