(function () {
  document.querySelectorAll('.carrier-row[data-href]').forEach(function (row) {
    function go() {
      var href = row.getAttribute('data-href');
      if (href) window.location.href = href;
    }
    row.addEventListener('click', function () {
      go();
    });
    row.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        go();
      }
    });
  });
})();
