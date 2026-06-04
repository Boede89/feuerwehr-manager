(function () {
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

  var tabBar = document.getElementById('vehicle-detail-tabs');
  if (tabBar) {
    function showVehicleTab(tab) {
      tabBar.querySelectorAll('[data-vehicle-tab]').forEach(function (b) {
        b.classList.toggle('tab-btn--active', b.getAttribute('data-vehicle-tab') === tab);
      });
      document.querySelectorAll('.vehicle-tab-panel').forEach(function (panel) {
        panel.hidden = true;
      });
      var panel = document.getElementById('tab-vehicle-' + tab);
      if (panel) panel.hidden = false;
    }
    var initial = tabBar.getAttribute('data-active-tab') || 'uebersicht';
    showVehicleTab(initial);
    tabBar.querySelectorAll('[data-vehicle-tab]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        showVehicleTab(btn.getAttribute('data-vehicle-tab'));
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
})();
