(function () {
  'use strict';

  var root = document.getElementById('berichte-einsatz-panel');
  if (!root) {
    return;
  }

  var unitId = root.dataset.unitId;
  var currentUserId = root.dataset.currentUserId ? Number(root.dataset.currentUserId) : null;
  var isAdmin = root.dataset.isAdmin === 'true';
  var canApprove = root.dataset.canApprove === 'true';
  var csrfToken = root.dataset.csrfToken || '';
  var csrfHeader = root.dataset.csrfHeader || 'X-XSRF-TOKEN';

  var filters = {
    year: Number(root.dataset.filterYear) || new Date().getFullYear(),
    stichwort: '',
    status: ''
  };

  var allItems = [];
  var stichworte = [];

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
      if (filters.stichwort && item.stichwort !== filters.stichwort) {
        return false;
      }
      if (filters.status && item.statusKey !== filters.status) {
        return false;
      }
      return true;
    });
  }

  function releaseDefaults() {
    return {
      createGeraetewart: root.dataset.releaseCreateGeraetewart === 'true',
      printReport: root.dataset.releasePrintReport === 'true',
      printGeraetewart: root.dataset.releasePrintGeraetewart === 'true'
    };
  }

  function listReturnPath() {
    var path = '/berichte?tab=einsatz&year=' + filters.year;
    if (filters.stichwort) {
      path += '&stichwort=' + encodeURIComponent(filters.stichwort);
    }
    if (filters.status) {
      path += '&status=' + encodeURIComponent(filters.status);
    }
    return path;
  }

  function tablePdfPrintActions(reportId) {
    var csrfParam = root.dataset.csrfParam || '_csrf';
    var base = '/berichte/einsatzberichte/' + reportId;
    return '<a class="btn btn--outline btn--sm" href="' + base +
      '/pdf?unit=' + encodeURIComponent(unitId) + '">PDF herunterladen</a>' +
      '<form method="post" action="' + base + '/drucken" class="table-inline-form">' +
      '<input type="hidden" name="' + esc(csrfParam) + '" value="' + esc(csrfToken) + '"/>' +
      '<input type="hidden" name="unit" value="' + esc(unitId) + '"/>' +
      '<input type="hidden" name="returnUrl" value="' + esc(listReturnPath()) + '"/>' +
      '<button type="submit" class="btn btn--outline btn--sm">Drucken</button></form>';
  }

  function renderTable() {
    var wrap = document.getElementById('incident-table-wrap');
    if (!wrap) {
      return;
    }
    var items = filteredItems();
    if (!items.length) {
      wrap.innerHTML = '<p class="hint" style="margin:0;padding:20px 0;">Keine Einsatzberichte für die gewählten Filter gefunden.</p>';
      return;
    }
    wrap.innerHTML =
      '<div style="overflow-x:auto">' +
      '<table class="data-table"><thead><tr>' +
      '<th>Nr.</th><th>Datum</th><th>Stichwort</th><th>Ort</th><th>Status</th><th>Quelle</th><th>Aktionen</th>' +
      '</tr></thead><tbody>' +
      items.map(function (r) {
        return '<tr>' +
          '<td class="text-muted text-xs">' + esc(r.incidentNumber || '—') + '</td>' +
          '<td>' + esc(fmtDate(r.incidentDate)) + '</td>' +
          '<td>' + esc(r.stichwort || '—') + '</td>' +
          '<td>' + esc(r.location || '—') + '</td>' +
          '<td><span class="incident-status-pill incident-status-pill--' + esc(r.statusKey) + '">' +
          esc(r.statusLabel) + '</span></td>' +
          '<td><span class="text-muted text-sm">' + (r.diveraSource ? 'DIVERA' : 'Manuell') + '</span></td>' +
          '<td><div class="btn-group">' +
          '<button type="button" class="btn btn--outline btn--sm" data-action="view" data-id="' + r.id + '">Anzeigen</button>' +
          tablePdfPrintActions(r.id) +
          (canEditItem(r) ? '<a class="btn btn--outline btn--sm" href="/berichte/einsatzberichte/' + r.id +
            '/bearbeiten?unit=' + encodeURIComponent(unitId) + '">Bearbeiten</a>' : '') +
          (canDeleteItem(r) ?
            '<form method="post" action="/berichte/einsatzberichte/' + r.id + '/delete" class="table-inline-form" ' +
            'data-confirm data-confirm-title="Einsatzbericht löschen?" ' +
            'data-confirm-message="Diese Aktion kann nicht rückgängig gemacht werden." ' +
            'data-confirm-label="Löschen" data-confirm-variant="danger">' +
            '<input type="hidden" name="' + esc(root.dataset.csrfParam || '_csrf') + '" value="' + esc(csrfToken) + '"/>' +
            '<input type="hidden" name="unit" value="' + esc(unitId) + '"/>' +
            '<input type="hidden" name="year" value="' + filters.year + '"/>' +
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

  function fillStichwortFilter() {
    var sel = document.getElementById('filter-stichwort');
    if (!sel) {
      return;
    }
    var current = filters.stichwort;
    sel.innerHTML = '<option value="">Alle Stichworte</option>' +
      stichworte.map(function (s) {
        return '<option value="' + esc(s) + '"' + (s === current ? ' selected' : '') + '>' + esc(s) + '</option>';
      }).join('');
  }

  function loadList() {
    var wrap = document.getElementById('incident-table-wrap');
    if (wrap) {
      wrap.innerHTML = '<p class="text-muted text-sm">Lade...</p>';
    }
    return fetch('/berichte/einsatzberichte/list?unit=' + encodeURIComponent(unitId) +
      '&year=' + encodeURIComponent(filters.year), { credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('Liste konnte nicht geladen werden');
        }
        return res.json();
      })
      .then(function (data) {
        allItems = data.items || [];
        stichworte = data.stichworte || [];
        fillStichwortFilter();
        renderTable();
      })
      .catch(function (err) {
        if (wrap) {
          wrap.innerHTML = '<p class="error-msg">' + esc(err.message) + '</p>';
        }
      });
  }

  function closeModal() {
    var modal = document.getElementById('modal-incident');
    if (modal) {
      modal.style.display = 'none';
    }
    document.body.classList.remove('modal-open');
    var scroll = document.getElementById('modal-body-scroll');
    if (scroll) {
      scroll.innerHTML = '';
    }
    var footer = document.getElementById('modal-footer');
    if (footer) {
      footer.innerHTML = '';
    }
  }

  function postAction(url, returnPath, extraFields) {
    var form = document.createElement('form');
    form.method = 'post';
    form.action = url;
    form.innerHTML =
      '<input type="hidden" name="' + esc(root.dataset.csrfParam || '_csrf') + '" value="' + esc(csrfToken) + '"/>' +
      '<input type="hidden" name="unit" value="' + esc(unitId) + '"/>' +
      '<input type="hidden" name="returnUrl" value="' + esc(returnPath) + '"/>';
    Object.keys(extraFields || {}).forEach(function (key) {
      if (extraFields[key]) {
        form.innerHTML += '<input type="hidden" name="' + esc(key) + '" value="true"/>';
      }
    });
    document.body.appendChild(form);
    form.submit();
  }

  function buildFooter(meta) {
    var footer = document.getElementById('modal-footer');
    if (!footer) {
      return;
    }
    var returnPath = listReturnPath();
    var html = '';
    html += '<a class="btn btn--outline" href="/berichte/einsatzberichte/' + meta.reportId +
      '/pdf?unit=' + encodeURIComponent(unitId) + '">PDF herunterladen</a>';
    html += '<button type="button" class="btn btn--outline" id="btn-modal-print">Drucken</button>';
    if (meta.canEdit === 'true') {
      html += '<a class="btn btn--primary" href="/berichte/einsatzberichte/' + meta.reportId +
        '/bearbeiten?unit=' + encodeURIComponent(unitId) + '">Bearbeiten</a>';
    }
    if (meta.canRelease === 'true') {
      html += '<button type="button" class="btn btn--primary btn--success" id="btn-modal-release">Freigeben</button>';
    }
    if (meta.canArchive === 'true') {
      html += '<button type="button" class="btn btn--outline" id="btn-modal-archive">Archivieren</button>';
    }
    html += '<button type="button" class="btn btn--outline" id="btn-modal-close-footer">Schließen</button>';
    footer.innerHTML = html;

    document.getElementById('btn-modal-close-footer')?.addEventListener('click', closeModal);
    document.getElementById('btn-modal-print')?.addEventListener('click', function () {
      postAction('/berichte/einsatzberichte/' + meta.reportId + '/drucken', returnPath);
    });
    document.getElementById('btn-modal-release')?.addEventListener('click', function () {
      var ask = window.FwConfirm && window.FwConfirm.releaseEinsatzbericht
        ? window.FwConfirm.releaseEinsatzbericht(releaseDefaults())
        : Promise.resolve(window.confirm('Einsatzbericht wirklich freigeben?'));
      ask.then(function (result) {
        var ok = result === true || (result && result.ok);
        if (!ok) {
          return;
        }
        var extras = {};
        if (result && result.createGeraetewart) {
          extras.createGeraetewart = true;
        }
        if (result && result.printReport) {
          extras.printReport = true;
        }
        if (result && result.printGeraetewart) {
          extras.printGeraetewart = true;
        }
        postAction('/berichte/einsatzberichte/' + meta.reportId + '/freigeben', returnPath, extras);
      });
    });
    document.getElementById('btn-modal-archive')?.addEventListener('click', function () {
      var ask = window.FwConfirm && window.FwConfirm.archiveReport
        ? window.FwConfirm.archiveReport('Einsatzbericht')
        : Promise.resolve(window.confirm('Einsatzbericht wirklich archivieren?'));
      ask.then(function (ok) {
        if (ok) {
          postAction('/berichte/einsatzberichte/' + meta.reportId + '/archivieren', returnPath);
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
    if (window.BerichteSchaeden && window.BerichteSchaeden.init) {
      window.BerichteSchaeden.init(container);
    }
  }

  function openModal(id) {
    var modal = document.getElementById('modal-incident');
    var title = document.getElementById('modal-incident-title');
    var scroll = document.getElementById('modal-body-scroll');
    if (!modal || !scroll) {
      return;
    }
    title.textContent = 'Lade...';
    scroll.innerHTML = '<p class="text-muted text-sm">Lade...</p>';
    modal.style.display = 'flex';
    document.body.classList.add('modal-open');

    fetch('/berichte/einsatzberichte/' + id + '/modal?unit=' + encodeURIComponent(unitId), {
      credentials: 'same-origin'
    })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('Bericht konnte nicht geladen werden');
        }
        return res.text();
      })
      .then(function (html) {
        scroll.innerHTML = html;
        var metaRoot = scroll.querySelector('.einsatzbericht-modal-root');
        if (!metaRoot) {
          throw new Error('Ungültige Antwort');
        }
        title.textContent = metaRoot.dataset.title || 'Einsatzbericht';
        buildFooter(metaRoot.dataset);
        initModalContent(scroll);
      })
      .catch(function (err) {
        title.textContent = 'Fehler';
        scroll.innerHTML = '<p class="error-msg">' + esc(err.message) + '</p>';
      });
  }

  function initFilters() {
    var yearSel = document.getElementById('filter-year');
    var stichwortSel = document.getElementById('filter-stichwort');
    var statusSel = document.getElementById('filter-status');

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
        filters.stichwort = '';
        loadList();
      });
    }

    if (stichwortSel) {
      stichwortSel.addEventListener('change', function () {
        filters.stichwort = stichwortSel.value;
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

  document.getElementById('btn-close-modal')?.addEventListener('click', closeModal);
  document.querySelector('#modal-incident .modal__backdrop')?.addEventListener('click', closeModal);

  initFilters();
  loadList();
})();
