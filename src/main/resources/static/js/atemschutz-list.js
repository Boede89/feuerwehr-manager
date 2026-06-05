(function () {
  var table = document.getElementById('atemschutz-table');
  var search = document.getElementById('atemschutz-search');
  var showPaused = document.getElementById('atemschutz-show-paused');
  var visibleCount = document.getElementById('atemschutz-visible-count');
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

  function applyFilters() {
    var q = search ? search.value.trim().toLowerCase() : '';
    var includePaused = showPaused && showPaused.checked;
    var count = 0;
    table.querySelectorAll('.carrier-row').forEach(function (row) {
      var status = row.getAttribute('data-status') || 'ACTIVE';
      var statusVisible = includePaused || status !== 'PAUSED';
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

  if (search) {
    search.addEventListener('input', applyFilters);
  }
  if (showPaused) {
    showPaused.addEventListener('change', applyFilters);
  }

  applyFilters();
})();
