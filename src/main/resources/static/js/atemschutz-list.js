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
  if (!table) return;

  table.querySelectorAll('.carrier-row').forEach(function (row) {
    function go() {
      var href = row.getAttribute('data-href');
      if (href) window.location.href = href;
    }
    row.addEventListener('click', function (e) {
      if (e.target.closest('button, a, form, input')) return;
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
      return { total: 0, tauglich: 0, warnung: 0, uebung: 0, nicht: 0 };
    }
    var prefix = includePausedRows ? 'all' : 'active';
    return {
      total: parseInt(kpiGrid.getAttribute('data-' + prefix + '-total'), 10) || 0,
      tauglich: parseInt(kpiGrid.getAttribute('data-' + prefix + '-tauglich'), 10) || 0,
      warnung: parseInt(kpiGrid.getAttribute('data-' + prefix + '-warnung'), 10) || 0,
      uebung: parseInt(kpiGrid.getAttribute('data-' + prefix + '-uebung'), 10) || 0,
      nicht: parseInt(kpiGrid.getAttribute('data-' + prefix + '-nicht'), 10) || 0
    };
  }

  function updateKpis() {
    var counts = kpiStats(includePaused());
    if (kpiTotal) kpiTotal.textContent = String(counts.total);
    if (kpiTauglich) kpiTauglich.textContent = String(counts.tauglich);
    if (kpiWarnung) kpiWarnung.textContent = String(counts.warnung);
    if (kpiUebung) kpiUebung.textContent = String(counts.uebung);
    if (kpiNicht) kpiNicht.textContent = String(counts.nicht);
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
      if (visible) count++;
    });
    if (visibleCount) {
      visibleCount.textContent = String(count);
    }
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

  applyTableFilters();
})();
