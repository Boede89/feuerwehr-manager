(function () {
  var search = document.getElementById('personal-search');

  function rows() {
    return Array.prototype.slice.call(document.querySelectorAll('.member-row'));
  }

  function navigate(row) {
    var href = row.getAttribute('data-href');
    if (href) {
      window.location.href = href;
    }
  }

  rows().forEach(function (row) {
    row.addEventListener('click', function () {
      navigate(row);
    });
    row.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        navigate(row);
      }
    });
  });

  if (!search) return;

  function filter() {
    var q = search.value.trim().toLowerCase();
    rows().forEach(function (row) {
      var hay = (row.getAttribute('data-search') || '').toLowerCase();
      row.style.display = !q || hay.indexOf(q) !== -1 ? '' : 'none';
    });
  }

  search.addEventListener('input', filter);
})();
