(function () {
  function onReady(fn) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', fn);
    } else {
      fn();
    }
  }

  function csrfToken() {
    var meta = document.querySelector('meta[name="csrf-token"]');
    return meta ? meta.getAttribute('content') : '';
  }

  function csrfParam() {
    var meta = document.querySelector('meta[name="csrf-param"]');
    return meta ? meta.getAttribute('content') : '_csrf';
  }

  function submitReorder(table) {
    var url = table.getAttribute('data-reorder-url');
    if (!url) return;
    var form = document.createElement('form');
    form.method = 'post';
    form.action = url;
    function hidden(name, value) {
      var input = document.createElement('input');
      input.type = 'hidden';
      input.name = name;
      input.value = value;
      form.appendChild(input);
    }
    hidden(csrfParam(), csrfToken());
    hidden('unit', table.getAttribute('data-unit') || '');
    hidden('jahr', table.getAttribute('data-jahr') || '');
    table.querySelectorAll('tbody tr[data-entry-id]').forEach(function (row) {
      hidden('entryIds', row.getAttribute('data-entry-id'));
    });
    document.body.appendChild(form);
    form.submit();
  }

  onReady(function () {
    document.querySelectorAll('form[data-confirm]').forEach(function (form) {
      form.addEventListener('submit', function (e) {
        var msg = form.getAttribute('data-confirm');
        if (msg && !window.confirm(msg)) e.preventDefault();
      });
    });

    document.querySelectorAll('.course-detail-entry-table').forEach(function (table) {
      if (table.getAttribute('data-can-write') !== 'true') return;
      var tbody = table.querySelector('tbody');
      if (!tbody) return;
      var dragged = null;

      tbody.querySelectorAll('tr[data-entry-id]').forEach(function (row) {
        row.addEventListener('dragstart', function (e) {
          if (e.target.closest('a, button, input, label, form')) {
            e.preventDefault();
            return;
          }
          dragged = row;
          row.classList.add('course-detail-row--dragging');
          if (e.dataTransfer) {
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', row.getAttribute('data-entry-id') || '');
          }
        });
        row.addEventListener('dragend', function () {
          row.classList.remove('course-detail-row--dragging');
          dragged = null;
        });
        row.addEventListener('dragover', function (e) {
          if (!dragged || dragged === row) return;
          e.preventDefault();
          var rect = row.getBoundingClientRect();
          var before = e.clientY < rect.top + rect.height / 2;
          tbody.insertBefore(dragged, before ? row : row.nextSibling);
        });
        row.addEventListener('drop', function (e) {
          e.preventDefault();
          if (dragged) {
            submitReorder(table);
          }
        });
      });
    });
  });
})();
