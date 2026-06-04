(function () {
  function onReady(fn) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', fn);
    } else {
      fn();
    }
  }

  onReady(function () {
    document.querySelectorAll('form[data-confirm]').forEach(function (form) {
      form.addEventListener('submit', function (e) {
        var msg = form.getAttribute('data-confirm');
        if (msg && !window.confirm(msg)) e.preventDefault();
      });
    });

    document.querySelectorAll('.vehicle-row').forEach(function (row) {
      row.addEventListener('click', function () {
        var href = row.getAttribute('data-href');
        if (href) window.location.href = href;
      });
    });

    var search = document.getElementById('vehicle-search');
    if (search) {
      search.addEventListener('input', function () {
        var q = search.value.toLowerCase();
        document.querySelectorAll('.vehicle-row').forEach(function (tr) {
          tr.style.display = tr.textContent.toLowerCase().includes(q) ? '' : 'none';
        });
      });
    }

    /* Optional: Tabs ohne Seitenreload (Links funktionieren auch ohne JS) */
    var tabBar = document.getElementById('vehicle-detail-tabs');
    if (tabBar) {
      function showVehicleTab(tab) {
        if (!tab) tab = 'uebersicht';
        tabBar.querySelectorAll('.tab-btn').forEach(function (b) {
          var t = b.getAttribute('data-vehicle-tab');
          if (t) b.classList.toggle('tab-btn--active', t === tab);
        });
        document.querySelectorAll('.vehicle-tab-panel').forEach(function (panel) {
          panel.classList.toggle('active', panel.id === 'tab-vehicle-' + tab);
        });
      }

      tabBar.querySelectorAll('a.tab-btn[data-vehicle-tab]').forEach(function (link) {
        link.addEventListener('click', function (e) {
          e.preventDefault();
          showVehicleTab(link.getAttribute('data-vehicle-tab'));
          var url = new URL(link.href, window.location.origin);
          window.history.replaceState(null, '', url.pathname + url.search);
        });
      });
    }

    document.querySelectorAll('.room-row').forEach(function (row) {
      row.addEventListener('click', function (e) {
        if (e.target.closest('button, a, input, form')) return;
        document.getElementById('edit-room-id').value = row.getAttribute('data-id') || '';
        document.getElementById('edit-room-name').value = row.getAttribute('data-name') || '';
        document.getElementById('edit-room-desc').value = row.getAttribute('data-desc') || '';
        document.getElementById('edit-room-active').checked = row.getAttribute('data-active') === 'true';
        var overlay = document.getElementById('modal-room-edit');
        if (overlay) {
          overlay.classList.add('active');
          document.body.classList.add('modal-open');
        }
      });
    });

    document.querySelectorAll('[data-open-modal="modal-equipment-edit"]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        document.getElementById('edit-equipment-id').value = btn.getAttribute('data-id') || '';
        document.getElementById('eqNameEdit').value = btn.getAttribute('data-name') || '';
        var catId = btn.getAttribute('data-category-id') || '';
        var sel = document.getElementById('eqCategoryEdit');
        if (sel) sel.value = catId;
      });
    });
  });
})();
