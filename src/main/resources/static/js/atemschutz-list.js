(function () {
  var table = document.getElementById('atemschutz-table');
  var search = document.getElementById('atemschutz-search');
  var showPaused = document.getElementById('atemschutz-show-paused');
  var visibleCount = document.getElementById('atemschutz-visible-count');
  var kpiGrid = document.getElementById('atemschutz-kpi-grid');
  var kpiTotal = document.getElementById('kpi-value-total');
  var kpiTauglich = document.getElementById('kpi-value-tauglich');
  var kpiWarnung = document.getElementById('kpi-value-warnung');
  var kpiUebung = document.getElementById('kpi-value-uebung');
  var kpiNicht = document.getElementById('kpi-value-nicht');
  var kpiCsa = document.getElementById('kpi-value-csa');
  var pdfLink = document.getElementById('atemschutz-pdf-link');
  var selectAll = document.getElementById('atemschutz-select-all');
  var bulkOpen = document.getElementById('atemschutz-bulk-open');
  var bulkHint = document.getElementById('atemschutz-bulk-hint');
  var bulkModal = document.getElementById('modal-atemschutz-bulk');
  var bulkCount = document.getElementById('atemschutz-bulk-count');
  var bulkInputs = document.getElementById('atemschutz-bulk-carrier-inputs');
  var remindOpen = document.getElementById('atemschutz-remind-open');
  var remindForm = document.getElementById('atemschutz-remind-form');
  var remindInputs = document.getElementById('atemschutz-remind-carrier-inputs');
  if (!table) return;

  table.querySelectorAll('.carrier-row').forEach(function (row) {
    function go() {
      var href = row.getAttribute('data-href');
      if (href) window.location.href = href;
    }
    row.addEventListener('click', function (e) {
      var selectCol = e.target.closest('td.atemschutz-select-col');
      if (selectCol) {
        e.preventDefault();
        e.stopPropagation();
        var cb = selectCol.querySelector('.atemschutz-row-select');
        if (!cb || cb.disabled) {
          return;
        }
        // Direkter Klick aufs Kästchen: Browser schaltet bereits um.
        if (e.target === cb || e.target.closest('input.atemschutz-row-select')) {
          return;
        }
        cb.checked = !cb.checked;
        cb.dispatchEvent(new Event('change', { bubbles: true }));
        return;
      }
      if (e.target.closest('button, a, form, input, label')) return;
      go();
    });
    row.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        go();
      }
    });
  });

  function includePaused() {
    return showPaused && showPaused.checked;
  }

  function kpiStats(includePausedRows) {
    if (!kpiGrid) {
      return { total: 0, tauglich: 0, warnung: 0, uebung: 0, nicht: 0, csa: 0 };
    }
    var prefix = includePausedRows ? 'all' : 'active';
    return {
      total: parseInt(kpiGrid.getAttribute('data-' + prefix + '-total'), 10) || 0,
      tauglich: parseInt(kpiGrid.getAttribute('data-' + prefix + '-tauglich'), 10) || 0,
      warnung: parseInt(kpiGrid.getAttribute('data-' + prefix + '-warnung'), 10) || 0,
      uebung: parseInt(kpiGrid.getAttribute('data-' + prefix + '-uebung'), 10) || 0,
      nicht: parseInt(kpiGrid.getAttribute('data-' + prefix + '-nicht'), 10) || 0,
      csa: parseInt(kpiGrid.getAttribute('data-' + prefix + '-csa'), 10) || 0
    };
  }

  function updateKpis() {
    var counts = kpiStats(includePaused());
    if (kpiTotal) kpiTotal.textContent = String(counts.total);
    if (kpiTauglich) kpiTauglich.textContent = String(counts.tauglich);
    if (kpiWarnung) kpiWarnung.textContent = String(counts.warnung);
    if (kpiUebung) kpiUebung.textContent = String(counts.uebung);
    if (kpiNicht) kpiNicht.textContent = String(counts.nicht);
    if (kpiCsa) kpiCsa.textContent = String(counts.csa);
  }

  function updatePdfLink() {
    if (!pdfLink) return;
    var unitId = pdfLink.getAttribute('data-unit-id');
    var filter = pdfLink.getAttribute('data-filter') || 'all';
    if (!unitId) return;
    var paused = includePaused();
    pdfLink.href = '/atemschutz/drucken?unit=' + encodeURIComponent(unitId)
      + '&filter=' + encodeURIComponent(filter)
      + '&paused=' + (paused ? 'true' : 'false');
  }

  function applyRowStripes() {
    var visibleIndex = 0;
    table.querySelectorAll('.carrier-row').forEach(function (row) {
      row.classList.remove('carrier-row--alt');
      if (row.style.display === 'none') {
        return;
      }
      if (visibleIndex % 2 === 1) {
        row.classList.add('carrier-row--alt');
      }
      visibleIndex++;
    });
  }

  function visibleRows() {
    return Array.prototype.filter.call(table.querySelectorAll('.carrier-row'), function (row) {
      return row.style.display !== 'none';
    });
  }

  function selectedCarrierIds() {
    var ids = [];
    table.querySelectorAll('.atemschutz-row-select:checked').forEach(function (cb) {
      if (cb.closest('.carrier-row') && cb.closest('.carrier-row').style.display !== 'none') {
        ids.push(cb.value);
      }
    });
    return ids;
  }

  function hideBulkHint() {
    if (bulkHint) bulkHint.hidden = true;
  }

  function updateBulkState() {
    hideBulkHint();
    if (selectAll) {
      var rows = visibleRows();
      var checkedVisible = rows.filter(function (row) {
        var cb = row.querySelector('.atemschutz-row-select');
        return cb && cb.checked;
      }).length;
      selectAll.checked = rows.length > 0 && checkedVisible === rows.length;
      selectAll.indeterminate = checkedVisible > 0 && checkedVisible < rows.length;
    }
  }

  function applyTableFilters() {
    var q = search ? search.value.trim().toLowerCase() : '';
    var includePausedRows = includePaused();
    var count = 0;
    table.querySelectorAll('.carrier-row').forEach(function (row) {
      var status = row.getAttribute('data-status') || 'ACTIVE';
      var statusVisible = includePausedRows || status !== 'PAUSED';
      var text = (row.getAttribute('data-search') || '').toLowerCase();
      var searchVisible = !q || text.indexOf(q) !== -1;
      var visible = statusVisible && searchVisible;
      row.style.display = visible ? '' : 'none';
      row.classList.remove('carrier-row--alt');
      if (visible) count++;
    });
    applyRowStripes();
    if (visibleCount) {
      visibleCount.textContent = String(count);
    }
    updatePdfLink();
    updateBulkState();
  }

  function onPausedToggle() {
    updateKpis();
    applyTableFilters();
  }

  if (search) {
    search.addEventListener('input', applyTableFilters);
  }
  if (showPaused) {
    showPaused.addEventListener('change', onPausedToggle);
  }

  if (selectAll) {
    selectAll.addEventListener('change', function () {
      var checked = selectAll.checked;
      visibleRows().forEach(function (row) {
        var cb = row.querySelector('.atemschutz-row-select');
        if (cb) cb.checked = checked;
      });
      updateBulkState();
    });
  }

  table.querySelectorAll('.atemschutz-row-select').forEach(function (cb) {
    cb.addEventListener('change', updateBulkState);
  });

  if (bulkOpen && bulkModal) {
    bulkOpen.addEventListener('click', function () {
      var ids = selectedCarrierIds();
      if (ids.length === 0) {
        if (bulkHint) bulkHint.hidden = false;
        return;
      }
      hideBulkHint();
      if (bulkCount) bulkCount.textContent = String(ids.length);
      if (bulkInputs) {
        bulkInputs.innerHTML = '';
        ids.forEach(function (id) {
          var input = document.createElement('input');
          input.type = 'hidden';
          input.name = 'carrierIds';
          input.value = id;
          bulkInputs.appendChild(input);
        });
      }
      bulkModal.classList.add('active');
      document.body.classList.add('modal-open');
      var dateInput = document.getElementById('bulk-record-valid-from');
      if (dateInput && !dateInput.value) {
        var today = new Date();
        dateInput.value = today.getFullYear() + '-'
          + String(today.getMonth() + 1).padStart(2, '0') + '-'
          + String(today.getDate()).padStart(2, '0');
      }
    });
  }

  if (remindOpen && remindForm) {
    remindOpen.addEventListener('click', function () {
      var ids = selectedCarrierIds();
      if (ids.length === 0) {
        if (bulkHint) bulkHint.hidden = false;
        return;
      }
      hideBulkHint();
      if (remindInputs) {
        remindInputs.innerHTML = '';
        ids.forEach(function (id) {
          var input = document.createElement('input');
          input.type = 'hidden';
          input.name = 'carrierIds';
          input.value = id;
          remindInputs.appendChild(input);
        });
      }
      var countLabel = ids.length === 1 ? '1 ausgewählten Geräteträger' : (ids.length + ' ausgewählte Geräteträger');
      remindForm.setAttribute(
        'data-confirm-message',
        'Es werden nur die ' + countLabel + ' benachrichtigt — und nur, wenn aktuell eine Warnung oder ein Ablauf vorliegt. Personen ohne Warnung erhalten keine E-Mail. Mehrere fällige Nachweise einer Person stehen in derselben Mail.'
      );
      if (typeof remindForm.requestSubmit === 'function') {
        remindForm.requestSubmit();
      } else {
        remindForm.submit();
      }
    });
  }

  var bulkType = document.getElementById('bulk-record-type');
  var bulkFrom = document.getElementById('bulk-record-valid-from');
  var bulkUntil = document.getElementById('bulk-record-valid-until');
  if (bulkType && bulkFrom && bulkUntil) {
    bulkFrom.addEventListener('change', function () {
      bulkUntil.value = '';
      bulkUntil.placeholder = 'wird pro Person berechnet';
    });
    bulkType.addEventListener('change', function () {
      bulkUntil.value = '';
      bulkUntil.placeholder = 'wird pro Person berechnet';
    });
    bulkUntil.placeholder = 'wird pro Person berechnet';
  }

  applyTableFilters();
})();
