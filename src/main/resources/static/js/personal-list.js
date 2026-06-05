(function () {
  var search = document.getElementById('personal-search');
  if (!search) return;

  function rows() {
    return Array.prototype.slice.call(document.querySelectorAll('.member-row'));
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
