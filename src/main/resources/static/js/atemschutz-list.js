(function () {
  var table = document.getElementById('atemschutz-table');
  var search = document.getElementById('atemschutz-search');
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

  if (search) {
    search.addEventListener('input', function () {
      var q = search.value.trim().toLowerCase();
      table.querySelectorAll('.carrier-row').forEach(function (row) {
        var text = (row.getAttribute('data-search') || '').toLowerCase();
        row.style.display = !q || text.indexOf(q) !== -1 ? '' : 'none';
      });
    });
  }
})();
