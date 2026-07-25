(function () {
  'use strict';

  var modal = document.getElementById('modal-vehicle-sort-order');
  var list = document.getElementById('vehicle-sort-list');
  var sortSelect = document.getElementById('vehicleSortMode');
  var openBtn = document.getElementById('btn-vehicle-sort-order');
  var form = document.getElementById('form-vehicle-sort-order');
  var hiddenWrap = document.getElementById('vehicle-sort-hidden-inputs');
  var previousMode = sortSelect ? sortSelect.value : 'manual';

  function openModal() {
    if (!modal) return;
    modal.classList.add('active');
    document.body.classList.add('modal-open');
    syncMoveButtons();
  }

  function closeModal() {
    if (!modal) return;
    modal.classList.remove('active');
    if (!document.querySelector('.modal-overlay.active')) {
      document.body.classList.remove('modal-open');
    }
  }

  function syncOpenButton() {
    if (!openBtn || !sortSelect) return;
    openBtn.disabled = sortSelect.value !== 'manual';
  }

  function items() {
    return list ? Array.prototype.slice.call(list.querySelectorAll('.reservierungen-sort-item')) : [];
  }

  function syncMoveButtons() {
    var rows = items();
    rows.forEach(function (row, index) {
      var up = row.querySelector('[data-sort-dir="up"]');
      var down = row.querySelector('[data-sort-dir="down"]');
      if (up) up.disabled = index === 0;
      if (down) down.disabled = index === rows.length - 1;
    });
  }

  function moveRow(row, direction) {
    if (!list || !row) return;
    if (direction === 'up' && row.previousElementSibling) {
      list.insertBefore(row, row.previousElementSibling);
    } else if (direction === 'down' && row.nextElementSibling) {
      list.insertBefore(row.nextElementSibling, row);
    }
    syncMoveButtons();
  }

  function writeHiddenInputs() {
    if (!hiddenWrap) return;
    hiddenWrap.innerHTML = '';
    items().forEach(function (row) {
      var id = row.getAttribute('data-vehicle-id');
      if (!id) return;
      var input = document.createElement('input');
      input.type = 'hidden';
      input.name = 'vehicleIds';
      input.value = id;
      hiddenWrap.appendChild(input);
    });
  }

  if (list) {
    list.addEventListener('click', function (ev) {
      var btn = ev.target.closest('[data-sort-dir]');
      if (!btn || btn.disabled) return;
      var row = btn.closest('.reservierungen-sort-item');
      moveRow(row, btn.getAttribute('data-sort-dir'));
    });
  }

  if (form) {
    form.addEventListener('submit', function () {
      writeHiddenInputs();
    });
  }

  if (openBtn) {
    openBtn.addEventListener('click', function () {
      if (sortSelect) sortSelect.value = 'manual';
      syncOpenButton();
      openModal();
    });
  }

  if (sortSelect) {
    sortSelect.addEventListener('change', function () {
      syncOpenButton();
      if (sortSelect.value === 'manual' && previousMode !== 'manual') {
        openModal();
      }
      previousMode = sortSelect.value;
    });
  }

  document.querySelectorAll('[data-close-vehicle-sort]').forEach(function (btn) {
    btn.addEventListener('click', function (ev) {
      ev.preventDefault();
      closeModal();
    });
  });

  if (modal) {
    modal.addEventListener('click', function (ev) {
      if (ev.target === modal) closeModal();
    });
  }

  syncOpenButton();
  syncMoveButtons();
})();
