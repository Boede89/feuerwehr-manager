(function () {
  var search = document.getElementById('personal-search');
  var archiveToggle = document.getElementById('personal-archive-toggle');

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

  if (archiveToggle) {
    archiveToggle.addEventListener('change', function () {
      var url = archiveToggle.checked
        ? archiveToggle.getAttribute('data-archiv-url')
        : archiveToggle.getAttribute('data-aktiv-url');
      if (url) {
        window.location.assign(url);
      }
    });
  }

  if (!search) {
    return;
  }

  function filter() {
    var q = search.value.trim().toLowerCase();
    rows().forEach(function (row) {
      var hay = (row.getAttribute('data-search') || '').toLowerCase();
      row.style.display = !q || hay.indexOf(q) !== -1 ? '' : 'none';
    });
  }

  search.addEventListener('input', filter);
})();
