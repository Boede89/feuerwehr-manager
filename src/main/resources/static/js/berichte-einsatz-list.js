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
    var payload = {
      year: filters.year,
      stichwort: filters.stichwort,
      status: filters.status
    };
    if (window.BerichteListFilters && typeof window.BerichteListFilters.save === 'function') {
      window.BerichteListFilters.save(unitId, 'einsatz', payload);
      return;
    }
    try {
      sessionStorage.setItem(FILTER_STORAGE_PREFIX + String(unitId || '') + '.einsatz', JSON.stringify(payload));
    } catch (e) {
      // ignore
    }
  }

  var filters = loadStoredFilters('einsatz', {
    year: Number(root.dataset.filterYear) || new Date().getFullYear(),
    stichwort: '',
    status: ''
  });
  filters.year = Number(filters.year) || new Date().getFullYear();
  if (filters.stichwort == null) {
    filters.stichwort = '';
  }
  if (filters.status == null) {
    filters.status = '';
  }

  var allItems = [];
  var stichworte = [];

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
    if (item.statusKey !== 'entwurf') {
      return false;
    }
    if (canApprove) {
      return true;
    }
    return item.createdByUserId != null && item.createdByUserId === currentUserId;
  }

  function canDeleteItem(item) {
    return isAdmin && item.statusKey === 'archiviert';
  }

  function canArchiveItem(item) {
    if (item.statusKey === 'archiviert') {
      return false;
    }
    if (isAdmin) {
      return true;
    }
    if (item.statusKey === 'freigegeben') {
      return canApprove;
    }
    if (item.statusKey === 'entwurf') {
      return canApprove || (item.createdByUserId != null && item.createdByUserId === currentUserId);
    }
    return false;
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

  function releaseDefaults(meta, prep) {
    var hasGeraete = !!(prep && prep.hasDeployedEquipment)
      || (meta && meta.hasDeployedEquipment === 'true');
    var defaults = {
      createGeraetewart: hasGeraete && root.dataset.releaseCreateGeraetewart === 'true',
      printReport: root.dataset.releasePrintReport === 'true',
      printGeraetewart: hasGeraete && root.dataset.releasePrintGeraetewart === 'true',
      printMaengel: root.dataset.releasePrintMaengel === 'true',
      hasMaterialDamages: !!(prep && prep.hasMaterialDamages)
        || (meta && meta.hasMaterialDamages === 'true'),
      hasDeployedEquipment: hasGeraete
    };
    return defaults;
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

  function canReleaseItem(item) {
    return item.statusKey === 'entwurf' && (canApprove || isAdmin);
  }

  function isFinalizedStatus(item) {
    return item.statusKey === 'freigegeben' || item.statusKey === 'archiviert';
  }

  function tableDocumentActions(item) {
    if (isFinalizedStatus(item)) {
      return tablePdfPrintActions(item.id);
    }
    if (canReleaseItem(item)) {
      return '<button type="button" class="btn btn--primary btn--success btn--sm" data-action="release" data-id="' +
        item.id + '">Freigeben</button>';
    }
    return '';
  }

  function releaseFromTable(reportId) {
    function proceedRelease(prep) {
      var ask = window.FwConfirm && window.FwConfirm.releaseEinsatzbericht
        ? window.FwConfirm.releaseEinsatzbericht(releaseDefaults(null, prep))
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
        if (result && result.printMaengel) {
          extras.printMaengel = true;
        }
        postAction('/berichte/einsatzberichte/' + reportId + '/freigeben', listReturnPath(), extras);
      });
    }
    if (window.BerichteEinsatzRelease) {
      window.BerichteEinsatzRelease.ensureValidBeforeRelease(reportId, unitId).then(function (check) {
        if (check && check.ok) {
          proceedRelease(check);
        }
      });
      return;
    }
    proceedRelease({});
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
          tableDocumentActions(r) +
          (canEditItem(r) ? '<a class="btn btn--outline btn--sm" href="/berichte/einsatzberichte/' + r.id +
            '/bearbeiten?unit=' + encodeURIComponent(unitId) + '">Bearbeiten</a>' : '') +
          (canArchiveItem(r) ?
            '<form method="post" action="/berichte/einsatzberichte/' + r.id + '/archivieren" class="table-inline-form" ' +
            'data-confirm data-confirm-title="Einsatzbericht ins Archiv verschieben?" ' +
            'data-confirm-message="Der Bericht wird ins Archiv verschoben und erscheint standardmäßig nicht mehr in der aktiven Liste." ' +
            'data-confirm-label="Ins Archiv verschieben">' +
            '<input type="hidden" name="' + esc(root.dataset.csrfParam || '_csrf') + '" value="' + esc(csrfToken) + '"/>' +
            '<input type="hidden" name="unit" value="' + esc(unitId) + '"/>' +
            '<input type="hidden" name="returnUrl" value="' + esc(listReturnPath()) + '"/>' +
            '<button type="submit" class="btn btn--outline btn--sm">Ins Archiv verschieben</button></form>' : '') +
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
    wrap.querySelectorAll('[data-action="release"]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        releaseFromTable(btn.dataset.id);
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
      html += '<button type="button" class="btn btn--outline" id="btn-modal-archive">Ins Archiv verschieben</button>';
    }
    if (meta.canDelete === 'true') {
      html += '<button type="button" class="btn btn--danger" id="btn-modal-delete">Löschen</button>';
    }
    html += '<button type="button" class="btn btn--outline" id="btn-modal-close-footer">Schließen</button>';
    footer.innerHTML = html;

    document.getElementById('btn-modal-close-footer')?.addEventListener('click', closeModal);
    document.getElementById('btn-modal-print')?.addEventListener('click', function () {
      postAction('/berichte/einsatzberichte/' + meta.reportId + '/drucken', returnPath);
    });
    document.getElementById('btn-modal-release')?.addEventListener('click', function () {
      function proceedRelease(prep) {
        var ask = window.FwConfirm && window.FwConfirm.releaseEinsatzbericht
          ? window.FwConfirm.releaseEinsatzbericht(releaseDefaults(meta, prep))
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
          if (result && result.printMaengel) {
            extras.printMaengel = true;
          }
          postAction('/berichte/einsatzberichte/' + meta.reportId + '/freigeben', returnPath, extras);
        });
      }
      if (window.BerichteEinsatzRelease) {
        window.BerichteEinsatzRelease.ensureValidBeforeRelease(meta.reportId, unitId).then(function (check) {
          if (check && check.ok) {
            proceedRelease(check);
          }
        });
        return;
      }
      proceedRelease({});
    });
    document.getElementById('btn-modal-archive')?.addEventListener('click', function () {
      var ask = window.FwConfirm && window.FwConfirm.archiveReport
        ? window.FwConfirm.archiveReport('Einsatzbericht')
        : Promise.resolve(window.confirm('Einsatzbericht wirklich ins Archiv verschieben?'));
      ask.then(function (ok) {
        if (ok) {
          postAction('/berichte/einsatzberichte/' + meta.reportId + '/archivieren', returnPath);
        }
      });
    });
    document.getElementById('btn-modal-delete')?.addEventListener('click', function () {
      var ask = window.FwConfirm && window.FwConfirm.deleteReport
        ? window.FwConfirm.deleteReport('Einsatzbericht')
        : Promise.resolve(window.confirm('Einsatzbericht wirklich löschen?'));
      ask.then(function (ok) {
        if (ok) {
          postAction('/berichte/einsatzberichte/' + meta.reportId + '/delete', returnPath);
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
        persistFilters();
        loadList();
      });
    }

    if (stichwortSel) {
      stichwortSel.addEventListener('change', function () {
        filters.stichwort = stichwortSel.value;
        persistFilters();
        renderTable();
      });
    }

    if (statusSel) {
      statusSel.value = filters.status;
      statusSel.addEventListener('change', function () {
        filters.status = statusSel.value;
        persistFilters();
        renderTable();
      });
    }
  }

  document.getElementById('btn-close-modal')?.addEventListener('click', closeModal);
  document.querySelector('#modal-incident .modal__backdrop')?.addEventListener('click', closeModal);

  initFilters();
  loadList();
})();
