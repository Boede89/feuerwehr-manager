(function () {
  'use strict';

  var root = document.getElementById('berichte-geraetewart-panel');
  if (!root) {
    return;
  }

  var unitId = root.dataset.unitId;
  var currentUserId = root.dataset.currentUserId ? Number(root.dataset.currentUserId) : null;
  var isAdmin = root.dataset.isAdmin === 'true';
  var csrfToken = root.dataset.csrfToken || '';
  var csrfParam = root.dataset.csrfParam || '_csrf';
  var filters = { year: Number(root.dataset.filterYear) || new Date().getFullYear() };
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
    return item.createdByUserId != null && item.createdByUserId === currentUserId;
  }

  function canDeleteItem(item) {
    return canEditItem(item);
  }

  function closeModal() {
    var modal = document.getElementById('modal-geraetewart');
    if (modal) {
      modal.style.display = 'none';
    }
    document.body.classList.remove('modal-open');
    var scroll = document.getElementById('modal-geraetewart-body-scroll');
    if (scroll) {
      scroll.innerHTML = '';
    }
    var footer = document.getElementById('modal-geraetewart-footer');
    if (footer) {
      footer.innerHTML = '';
    }
  }

  function buildFooter(meta) {
    var footer = document.getElementById('modal-geraetewart-footer');
    if (!footer) {
      return;
    }
    var html = '';
    html += '<a class="btn btn--outline" href="/berichte/geraetewartmitteilungen/' + meta.reportId +
      '/pdf?unit=' + encodeURIComponent(unitId) + '">PDF herunterladen</a>';
    if (meta.canEdit === 'true') {
      html += '<a class="btn btn--primary" href="/berichte/geraetewartmitteilungen/' + meta.reportId +
        '/bearbeiten?unit=' + encodeURIComponent(unitId) + '">Bearbeiten</a>';
    }
    html += '<button type="button" class="btn btn--outline" id="btn-gwm-modal-close-footer">Schließen</button>';
    footer.innerHTML = html;
    document.getElementById('btn-gwm-modal-close-footer')?.addEventListener('click', closeModal);
  }

  function openModal(id) {
    var modal = document.getElementById('modal-geraetewart');
    var title = document.getElementById('modal-geraetewart-title');
    var scroll = document.getElementById('modal-geraetewart-body-scroll');
    if (!modal || !scroll) {
      return;
    }
    title.textContent = 'Lade...';
    scroll.innerHTML = '<p class="hint">Lade...</p>';
    modal.style.display = 'flex';
    document.body.classList.add('modal-open');

    fetch('/berichte/geraetewartmitteilungen/' + id + '/modal?unit=' + encodeURIComponent(unitId), {
      credentials: 'same-origin'
    })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('Gerätewartmitteilung konnte nicht geladen werden');
        }
        return res.text();
      })
      .then(function (html) {
        scroll.innerHTML = html;
        var metaRoot = scroll.querySelector('.einsatzbericht-modal-root');
        if (!metaRoot) {
          throw new Error('Ungültige Antwort');
        }
        title.textContent = metaRoot.dataset.title || 'Gerätewartmitteilung';
        buildFooter(metaRoot.dataset);
        if (window.BerichteGeraetewartForm && window.BerichteGeraetewartForm.init) {
          window.BerichteGeraetewartForm.init(scroll);
        }
      })
      .catch(function (err) {
        title.textContent = 'Fehler';
        scroll.innerHTML = '<p class="error-msg">' + esc(err.message) + '</p>';
      });
  }

  function renderTable() {
    var wrap = document.getElementById('geraetewart-table-wrap');
    if (!wrap) {
      return;
    }
    if (!allItems.length) {
      wrap.innerHTML = '<p class="hint" style="margin:0;padding:20px 0;">Keine Gerätewartmitteilungen für das gewählte Jahr gefunden.</p>';
      return;
    }
    wrap.innerHTML =
      '<div style="overflow-x:auto">' +
      '<table class="data-table"><thead><tr>' +
      '<th>Datum</th><th>Typ</th><th>Leiter</th><th>Fahrzeuge</th><th>Einsatzbereitschaft</th><th>Aktionen</th>' +
      '</tr></thead><tbody>' +
      allItems.map(function (r) {
        return '<tr>' +
          '<td>' + esc(fmtDate(r.eventDate)) + '</td>' +
          '<td>' + esc(r.typLabel || '—') + '</td>' +
          '<td>' + esc(r.leaderDisplay || '—') + '</td>' +
          '<td>' + esc(String(r.vehicleCount || 0)) + '</td>' +
          '<td><span class="text-muted text-sm">' + esc(r.readinessLabel || '—') + '</span></td>' +
          '<td><div class="btn-group">' +
          '<button type="button" class="btn btn--outline btn--sm" data-action="view" data-id="' + r.id + '">Anzeigen</button>' +
          (canEditItem(r) ? '<a class="btn btn--outline btn--sm" href="/berichte/geraetewartmitteilungen/' + r.id +
            '/bearbeiten?unit=' + encodeURIComponent(unitId) + '">Bearbeiten</a>' : '') +
          (canDeleteItem(r) ?
            '<form method="post" action="/berichte/geraetewartmitteilungen/' + r.id + '/delete" class="table-inline-form" ' +
            'data-confirm data-confirm-title="Gerätewartmitteilung löschen?" ' +
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
    fetch('/berichte/geraetewartmitteilungen/list?unit=' + encodeURIComponent(unitId) +
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
        var wrap = document.getElementById('geraetewart-table-wrap');
        if (wrap) {
          wrap.innerHTML = '<p class="error-msg">' + esc(err.message) + '</p>';
        }
      });
  }

  function initYearFilter() {
    var select = document.getElementById('filter-year-geraetewart');
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
      loadList();
    });
  }

  document.getElementById('btn-close-geraetewart-modal')?.addEventListener('click', closeModal);
  document.getElementById('modal-geraetewart')?.querySelector('.modal__backdrop')?.addEventListener('click', closeModal);

  initYearFilter();
  loadList();
})();
