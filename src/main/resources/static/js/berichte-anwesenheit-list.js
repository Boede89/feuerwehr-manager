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

  var filters = window.BerichteListFilters
    ? window.BerichteListFilters.load(unitId, 'anwesenheit', {
      year: Number(root.dataset.filterYear) || new Date().getFullYear(),
      zeitraum: 'aktuell',
      category: '',
      status: 'entwurf'
    })
    : {
      year: Number(root.dataset.filterYear) || new Date().getFullYear(),
      zeitraum: 'aktuell',
      category: '',
      status: 'entwurf'
    };
  filters.year = Number(filters.year) || new Date().getFullYear();
  filters.zeitraum = filters.zeitraum || 'aktuell';
  filters.category = filters.category || '';
  filters.status = filters.status || 'entwurf';

  var allItems = [];

  function persistFilters() {
    if (window.BerichteListFilters) {
      window.BerichteListFilters.save(unitId, 'anwesenheit', {
        year: filters.year,
        zeitraum: filters.zeitraum,
        category: filters.category,
        status: filters.status
      });
    }
  }

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

  function listReturnPath() {
    var path = '/berichte?tab=anwesenheit&year=' + filters.year;
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
    function proceed(prep) {
      var ask = window.FwConfirm && window.FwConfirm.releaseAnwesenheitsliste
        ? window.FwConfirm.releaseAnwesenheitsliste(releaseDefaults({
          hasMaterialDamages: !!(prep && prep.hasMaterialDamages),
          hasDeployedEquipment: !!(prep && prep.hasDeployedEquipment),
          createGeraetewart: !!(prep && prep.hasDeployedEquipment) && root.dataset.releaseCreateGeraetewart === 'true',
          printGeraetewart: !!(prep && prep.hasDeployedEquipment) && root.dataset.releasePrintGeraetewart === 'true'
        }))
        : Promise.resolve(window.confirm('Anwesenheitsliste wirklich freigeben?'));
      ask.then(function (result) {
        var ok = result === true || (result && result.ok);
        if (!ok) {
          return;
        }
        postAction(
          '/berichte/anwesenheitslisten/' + reportId + '/freigeben',
          listReturnPath(),
          releaseExtrasFromResult(result, prep)
        );
      });
    }
    if (window.BerichteAnwesenheitRelease) {
      window.BerichteAnwesenheitRelease.prepareRelease(reportId, unitId).then(proceed);
    } else {
      proceed({ assignRemainingToWache: false });
    }
  }

  function tablePdfPrintActions(reportId) {
    var base = '/berichte/anwesenheitslisten/' + reportId;
    return '<a class="btn btn--outline btn--sm" href="' + base +
      '/pdf?unit=' + encodeURIComponent(unitId) + '">PDF herunterladen</a>' +
      '<form method="post" action="' + base + '/drucken" class="table-inline-form">' +
      '<input type="hidden" name="' + esc(csrfParam) + '" value="' + esc(csrfToken) + '"/>' +
      '<input type="hidden" name="unit" value="' + esc(unitId) + '"/>' +
      '<input type="hidden" name="returnUrl" value="' + esc(listReturnPath()) + '"/>' +
      '<button type="submit" class="btn btn--outline btn--sm">Drucken</button></form>';
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

  function postAction(url, returnPath, extraFields) {
    var form = document.createElement('form');
    form.method = 'post';
    form.action = url;
    form.innerHTML =
      '<input type="hidden" name="' + esc(csrfParam) + '" value="' + esc(csrfToken) + '"/>' +
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

  function releaseDefaults(extra) {
    return Object.assign({
      printReport: root.dataset.releasePrintReport === 'true',
      createGeraetewart: root.dataset.releaseCreateGeraetewart === 'true',
      printGeraetewart: root.dataset.releasePrintGeraetewart === 'true',
      printMaengel: root.dataset.releasePrintMaengel === 'true',
      hasMaterialDamages: false,
      hasDeployedEquipment: false
    }, extra || {});
  }

  function releaseExtrasFromResult(result, prep) {
    var extras = {};
    ['printReport', 'createGeraetewart', 'printGeraetewart', 'printMaengel'].forEach(function (key) {
      if (result && result[key]) {
        extras[key] = true;
      }
    });
    if (prep && prep.assignRemainingToWache) {
      extras.assignRemainingToWache = true;
    }
    return extras;
  }

  function buildFooter(meta) {
    var footer = document.getElementById('modal-attendance-footer');
    if (!footer) {
      return;
    }
    var returnPath = listReturnPath();
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
      html += '<button type="button" class="btn btn--outline" id="btn-attendance-modal-archive">Ins Archiv verschieben</button>';
    }
    if (meta.canDelete === 'true') {
      html += '<button type="button" class="btn btn--danger" id="btn-attendance-modal-delete">Löschen</button>';
    }
    html += '<button type="button" class="btn btn--outline" id="btn-attendance-modal-close-footer">Schließen</button>';
    footer.innerHTML = html;

    document.getElementById('btn-attendance-modal-close-footer')?.addEventListener('click', closeModal);
    document.getElementById('btn-attendance-modal-print')?.addEventListener('click', function () {
      postAction('/berichte/anwesenheitslisten/' + meta.reportId + '/drucken', returnPath);
    });
    document.getElementById('btn-attendance-modal-release')?.addEventListener('click', function () {
      function proceed(prep) {
        var ask = window.FwConfirm && window.FwConfirm.releaseAnwesenheitsliste
          ? window.FwConfirm.releaseAnwesenheitsliste(releaseDefaults({
            hasMaterialDamages: !!(prep && prep.hasMaterialDamages),
            hasDeployedEquipment: !!(prep && prep.hasDeployedEquipment),
            createGeraetewart: !!(prep && prep.hasDeployedEquipment) && root.dataset.releaseCreateGeraetewart === 'true',
            printGeraetewart: !!(prep && prep.hasDeployedEquipment) && root.dataset.releasePrintGeraetewart === 'true'
          }))
          : Promise.resolve(window.confirm('Anwesenheitsliste wirklich freigeben?'));
        ask.then(function (result) {
          var ok = result === true || (result && result.ok);
          if (!ok) {
            return;
          }
          postAction(
            '/berichte/anwesenheitslisten/' + meta.reportId + '/freigeben',
            returnPath,
            releaseExtrasFromResult(result, prep)
          );
        });
      }
      if (window.BerichteAnwesenheitRelease) {
        window.BerichteAnwesenheitRelease.prepareRelease(meta.reportId, unitId).then(proceed);
      } else {
        proceed({ assignRemainingToWache: false });
      }
    });
    document.getElementById('btn-attendance-modal-archive')?.addEventListener('click', function () {
      var ask = window.FwConfirm && window.FwConfirm.archiveReport
        ? window.FwConfirm.archiveReport('Anwesenheitsliste')
        : Promise.resolve(window.confirm('Anwesenheitsliste wirklich ins Archiv verschieben?'));
      ask.then(function (ok) {
        if (ok) {
          postAction('/berichte/anwesenheitslisten/' + meta.reportId + '/archivieren', returnPath);
        }
      });
    });
    document.getElementById('btn-attendance-modal-delete')?.addEventListener('click', function () {
      var ask = window.FwConfirm && window.FwConfirm.deleteReport
        ? window.FwConfirm.deleteReport('Anwesenheitsliste')
        : Promise.resolve(window.confirm('Anwesenheitsliste wirklich löschen?'));
      ask.then(function (ok) {
        if (ok) {
          postAction('/berichte/anwesenheitslisten/' + meta.reportId + '/delete', returnPath);
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
          tableDocumentActions(r) +
          (canEditItem(r) ? '<a class="btn btn--outline btn--sm" href="/berichte/anwesenheitslisten/' + r.id +
            '/bearbeiten?unit=' + encodeURIComponent(unitId) + '">Bearbeiten</a>' : '') +
          (canArchiveItem(r) ?
            '<form method="post" action="/berichte/anwesenheitslisten/' + r.id + '/archivieren" class="table-inline-form" ' +
            'data-confirm data-confirm-title="Anwesenheitsliste ins Archiv verschieben?" ' +
            'data-confirm-message="Die Liste wird ins Archiv verschoben und erscheint standardmäßig nicht mehr in der aktiven Liste." ' +
            'data-confirm-label="Ins Archiv verschieben">' +
            '<input type="hidden" name="' + esc(csrfParam) + '" value="' + esc(csrfToken) + '"/>' +
            '<input type="hidden" name="unit" value="' + esc(unitId) + '"/>' +
            '<input type="hidden" name="returnUrl" value="' + esc(listReturnPath()) + '"/>' +
            '<button type="submit" class="btn btn--outline btn--sm">Ins Archiv verschieben</button></form>' : '') +
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
    wrap.querySelectorAll('[data-action="release"]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        releaseFromTable(btn.dataset.id);
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
        persistFilters();
        loadList();
      });
    }

    if (zeitraumSel) {
      zeitraumSel.value = filters.zeitraum;
      zeitraumSel.addEventListener('change', function () {
        filters.zeitraum = zeitraumSel.value;
        persistFilters();
        renderTable();
      });
    }

    if (categorySel) {
      categorySel.value = filters.category;
      categorySel.addEventListener('change', function () {
        filters.category = categorySel.value;
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

  document.getElementById('btn-close-attendance-modal')?.addEventListener('click', closeModal);
  document.querySelector('#modal-attendance .modal__backdrop')?.addEventListener('click', closeModal);

  initFilters();
  loadList();
})();
