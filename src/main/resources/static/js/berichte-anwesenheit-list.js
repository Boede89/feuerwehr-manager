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
    zeitraum: 'aktuell',
    category: '',
    status: 'entwurf'
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

  function todayIso() {
    var d = new Date();
    var m = String(d.getMonth() + 1).padStart(2, '0');
    var day = String(d.getDate()).padStart(2, '0');
    return d.getFullYear() + '-' + m + '-' + day;
  }

  function matchesZeitraum(eventDate) {
    if (!eventDate || filters.zeitraum === 'alle') {
      return true;
    }
    var today = todayIso();
    if (filters.zeitraum === 'aktuell') {
      return eventDate <= today;
    }
    if (filters.zeitraum === 'vergangene') {
      return eventDate < today;
    }
    return true;
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
      if (!matchesZeitraum(item.eventDate)) {
        return false;
      }
      if (filters.category && item.terminCategoryKey !== filters.category) {
        return false;
      }
      if (filters.status && item.statusKey !== filters.status) {
        return false;
      }
      return true;
    });
  }

  function closeModal() {
    var modal = document.getElementById('modal-attendance');
    if (modal) {
      modal.style.display = 'none';
    }
    document.body.classList.remove('modal-open');
    var scroll = document.getElementById('modal-attendance-body-scroll');
    if (scroll) {
      scroll.innerHTML = '';
    }
    var footer = document.getElementById('modal-attendance-footer');
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
    var footer = document.getElementById('modal-attendance-footer');
    if (!footer) {
      return;
    }
    var returnPath = '/berichte?tab=anwesenheit&year=' + filters.year;
    var html = '';
    html += '<a class="btn btn--outline" href="/berichte/anwesenheitslisten/' + meta.reportId +
      '/pdf?unit=' + encodeURIComponent(unitId) + '">PDF herunterladen</a>';
    html += '<button type="button" class="btn btn--outline" id="btn-attendance-modal-print">Drucken</button>';
    if (meta.canEdit === 'true') {
      html += '<a class="btn btn--primary" href="/berichte/anwesenheitslisten/' + meta.reportId +
        '/bearbeiten?unit=' + encodeURIComponent(unitId) + '">Bearbeiten</a>';
    }
    if (meta.canRelease === 'true') {
      html += '<button type="button" class="btn btn--primary btn--success" id="btn-attendance-modal-release">Freigeben</button>';
    }
    if (meta.canArchive === 'true') {
      html += '<button type="button" class="btn btn--outline" id="btn-attendance-modal-archive">Archivieren</button>';
    }
    html += '<button type="button" class="btn btn--outline" id="btn-attendance-modal-close-footer">Schließen</button>';
    footer.innerHTML = html;

    document.getElementById('btn-attendance-modal-close-footer')?.addEventListener('click', closeModal);
    document.getElementById('btn-attendance-modal-print')?.addEventListener('click', function () {
      postAction('/berichte/anwesenheitslisten/' + meta.reportId + '/drucken', returnPath);
    });
    document.getElementById('btn-attendance-modal-release')?.addEventListener('click', function () {
      var ask = window.FwConfirm && window.FwConfirm.releaseReport
        ? window.FwConfirm.releaseReport('Anwesenheitsliste')
        : Promise.resolve(window.confirm('Anwesenheitsliste wirklich freigeben?'));
      ask.then(function (ok) {
        if (ok) {
          postAction('/berichte/anwesenheitslisten/' + meta.reportId + '/freigeben', returnPath);
        }
      });
    });
    document.getElementById('btn-attendance-modal-archive')?.addEventListener('click', function () {
      var ask = window.FwConfirm && window.FwConfirm.archiveReport
        ? window.FwConfirm.archiveReport('Anwesenheitsliste')
        : Promise.resolve(window.confirm('Anwesenheitsliste wirklich archivieren?'));
      ask.then(function (ok) {
        if (ok) {
          postAction('/berichte/anwesenheitslisten/' + meta.reportId + '/archivieren', returnPath);
        }
      });
    });
  }

  function initModalContent(container) {
    if (window.BerichteEinsatzForm && window.BerichteEinsatzForm.init) {
      window.BerichteEinsatzForm.init(container);
    }
    if (window.BerichteKraefte && window.BerichteKraefte.init) {
      window.BerichteKraefte.init();
    }
    if (window.BerichteGeraete && window.BerichteGeraete.initView) {
      window.BerichteGeraete.initView();
    }
    if (window.BerichteAnhaenge && window.BerichteAnhaenge.load) {
      window.BerichteAnhaenge.load();
    }
    if (window.BerichteAnwesenheitAddress) {
      window.BerichteAnwesenheitAddress.init(container);
    }
  }

  function openModal(id) {
    var modal = document.getElementById('modal-attendance');
    var title = document.getElementById('modal-attendance-title');
    var scroll = document.getElementById('modal-attendance-body-scroll');
    if (!modal || !scroll) {
      return;
    }
    title.textContent = 'Lade...';
    scroll.innerHTML = '<p class="text-muted text-sm">Lade...</p>';
    modal.style.display = 'flex';
    document.body.classList.add('modal-open');

    fetch('/berichte/anwesenheitslisten/' + id + '/modal?unit=' + encodeURIComponent(unitId), {
      credentials: 'same-origin'
    })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('Anwesenheitsliste konnte nicht geladen werden');
        }
        return res.text();
      })
      .then(function (html) {
        scroll.innerHTML = html;
        var metaRoot = scroll.querySelector('.einsatzbericht-modal-root');
        if (!metaRoot) {
          throw new Error('Ungültige Antwort');
        }
        title.textContent = metaRoot.dataset.title || 'Anwesenheitsliste';
        buildFooter(metaRoot.dataset);
        initModalContent(scroll);
      })
      .catch(function (err) {
        title.textContent = 'Fehler';
        scroll.innerHTML = '<p class="error-msg">' + esc(err.message) + '</p>';
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
      '<th>Datum</th><th>Bezeichnung</th><th>Bereich</th><th>Ort</th><th>Status</th><th>Quelle</th><th>Aktionen</th>' +
      '</tr></thead><tbody>' +
      items.map(function (r) {
        return '<tr>' +
          '<td>' + esc(fmtDate(r.eventDate)) + '</td>' +
          '<td>' + esc(r.title || '—') + '</td>' +
          '<td>' + esc(r.terminCategoryLabel || '—') + '</td>' +
          '<td>' + esc(r.location || '—') + '</td>' +
          '<td><span class="incident-status-pill incident-status-pill--' + esc(r.statusKey) + '">' +
          esc(r.statusLabel) + '</span></td>' +
          '<td><span class="text-muted text-sm">' + (r.terminSource ? 'Termin' : 'Manuell') + '</span></td>' +
          '<td><div class="btn-group">' +
          '<button type="button" class="btn btn--outline btn--sm" data-action="view" data-id="' + r.id + '">Anzeigen</button>' +
          (canEditItem(r) ? '<a class="btn btn--outline btn--sm" href="/berichte/anwesenheitslisten/' + r.id +
            '/bearbeiten?unit=' + encodeURIComponent(unitId) + '">Bearbeiten</a>' : '') +
          (canDeleteItem(r) ?
            '<form method="post" action="/berichte/anwesenheitslisten/' + r.id + '/delete" class="table-inline-form" ' +
            'data-confirm data-confirm-title="Anwesenheitsliste löschen?" ' +
            'data-confirm-message="Diese Aktion kann nicht rückgängig gemacht werden." ' +
            'data-confirm-label="Löschen" data-confirm-variant="danger">' +
            '<input type="hidden" name="' + esc(csrfParam) + '" value="' + esc(csrfToken) + '"/>' +
            '<input type="hidden" name="unit" value="' + esc(unitId) + '"/>' +
            '<input type="hidden" name="year" value="' + filters.year + '"/>' +
            '<input type="hidden" name="status" value="' + esc(filters.status) + '"/>' +
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
    var zeitraumSel = document.getElementById('filter-zeitraum-anwesenheit');
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

    if (zeitraumSel) {
      zeitraumSel.value = filters.zeitraum;
      zeitraumSel.addEventListener('change', function () {
        filters.zeitraum = zeitraumSel.value;
        renderTable();
      });
    }

    if (categorySel) {
      categorySel.addEventListener('change', function () {
        filters.category = categorySel.value;
        renderTable();
      });
    }

    if (statusSel) {
      statusSel.value = filters.status;
      statusSel.addEventListener('change', function () {
        filters.status = statusSel.value;
        renderTable();
      });
    }
  }

  document.getElementById('btn-close-attendance-modal')?.addEventListener('click', closeModal);
  document.querySelector('#modal-attendance .modal__backdrop')?.addEventListener('click', closeModal);

  initFilters();
  loadList();
})();
