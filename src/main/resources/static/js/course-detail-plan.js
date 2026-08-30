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

  function bindDrag(table) {
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
  }

  function itemIdFromHash() {
    var hash = (window.location.hash || '').replace(/^#/, '');
    if (hash.indexOf('item-') === 0) {
      return hash.slice(5);
    }
    return '';
  }

  function applyFilters() {
    var q = (document.getElementById('course-detail-search') || {}).value || '';
    q = q.trim().toLowerCase();
    var seatsOnly = !!(document.getElementById('course-detail-seats-only') || {}).checked;
    document.querySelectorAll('.course-detail-panel.is-active tbody tr[data-entry-id]').forEach(function (row) {
      var hay = (row.getAttribute('data-search') || '').toLowerCase();
      var matchSearch = !q || hay.indexOf(q) !== -1;
      var matchSeats = !seatsOnly || row.classList.contains('course-detail-row--seat');
      row.hidden = !(matchSearch && matchSeats);
    });
  }

  function activateItem(itemId) {
    var buttons = document.querySelectorAll('.course-detail-nav-btn');
    var panels = document.querySelectorAll('.course-detail-panel');
    if (!buttons.length || !panels.length) return;
    var found = false;
    buttons.forEach(function (btn) {
      var active = String(btn.getAttribute('data-item-id')) === String(itemId);
      btn.classList.toggle('is-active', active);
      if (active) found = true;
    });
    if (!found) {
      itemId = buttons[0].getAttribute('data-item-id');
      buttons[0].classList.add('is-active');
    }
    panels.forEach(function (panel) {
      var id = panel.getAttribute('data-item-id') || (panel.id || '').replace(/^item-/, '');
      var active = String(id) === String(itemId);
      panel.classList.toggle('is-active', active);
    });
    var activeBtn = document.querySelector('.course-detail-nav-btn.is-active');
    var nav = document.querySelector('.course-detail-nav');
    if (activeBtn && nav) {
      var btnRect = activeBtn.getBoundingClientRect();
      var navRect = nav.getBoundingClientRect();
      if (btnRect.top < navRect.top) {
        nav.scrollTop += btnRect.top - navRect.top - 8;
      } else if (btnRect.bottom > navRect.bottom) {
        nav.scrollTop += btnRect.bottom - navRect.bottom + 8;
      }
    }
    if (itemId && history.replaceState) {
      var url = window.location.pathname + window.location.search + '#item-' + itemId;
      history.replaceState(null, '', url);
    }
    applyFilters();
  }

  onReady(function () {
    document.querySelectorAll('form[data-confirm]').forEach(function (form) {
      form.addEventListener('submit', function (e) {
        var msg = form.getAttribute('data-confirm');
        if (msg && !window.confirm(msg)) e.preventDefault();
      });
    });

    document.querySelectorAll('.course-detail-entry-table').forEach(bindDrag);

    var nav = document.querySelector('.course-detail-nav');
    if (nav) {
      if ('scrollRestoration' in history) {
        history.scrollRestoration = 'manual';
      }
      nav.addEventListener('click', function (e) {
        var btn = e.target.closest('.course-detail-nav-btn');
        if (!btn || !nav.contains(btn)) return;
        activateItem(btn.getAttribute('data-item-id'));
      });
      activateItem(itemIdFromHash());
      window.scrollTo(0, 0);
    }

    var search = document.getElementById('course-detail-search');
    if (search) search.addEventListener('input', applyFilters);
    var seatsOnly = document.getElementById('course-detail-seats-only');
    if (seatsOnly) seatsOnly.addEventListener('change', applyFilters);

    document.querySelectorAll('.course-seat-tile').forEach(function (tile) {
      var box = tile.querySelector('input[type="checkbox"]');
      var seats = tile.querySelector('input[type="number"]');
      function sync() {
        tile.classList.toggle('is-selected', !!(box && box.checked));
      }
      if (box) box.addEventListener('change', sync);
      if (seats && box) {
        seats.addEventListener('input', function () {
          if (parseInt(seats.value, 10) > 0) box.checked = true;
          sync();
        });
      }
    });
  });
})();
