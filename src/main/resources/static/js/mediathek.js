(function () {
  'use strict';

  function openModal(id) {
    var overlay = document.getElementById(id);
    if (!overlay) {
      return;
    }
    overlay.classList.add('active');
    document.body.classList.add('modal-open');
  }

  function closeModal(overlay) {
    if (!overlay) {
      return;
    }
    overlay.classList.remove('active');
    if (!document.querySelector('.modal-overlay.active')) {
      document.body.classList.remove('modal-open');
    }
  }

  function syncAclInherit() {
    var inherit = document.getElementById('mediathek-inherit-acl');
    var entries = document.getElementById('mediathek-acl-entries');
    var inherited = document.getElementById('mediathek-inherited-acl');
    var hint = document.getElementById('mediathek-acl-inherit-hint');
    var addBtn = document.getElementById('mediathek-acl-add');
    var inheriting = !!(inherit && inherit.checked);
    if (entries) {
      entries.hidden = inheriting;
    }
    if (inherited) {
      inherited.hidden = !inheriting;
    }
    if (hint) {
      hint.hidden = !inheriting;
    }
    if (addBtn) {
      addBtn.disabled = inheriting;
    }
    if (entries) {
      entries.querySelectorAll('select, button[data-remove-row]').forEach(function (el) {
        el.disabled = inheriting;
      });
    }
  }

  function addAclRow() {
    var table = document.getElementById('mediathek-acl-table');
    var tbody = table ? table.querySelector('tbody') : null;
    var tpl = document.getElementById('mediathek-acl-row-template');
    var inherit = document.getElementById('mediathek-inherit-acl');
    if (inherit && inherit.checked) {
      return;
    }
    if (!tbody || !tpl) {
      return;
    }
    tbody.appendChild(tpl.content.cloneNode(true));
  }

  document.addEventListener('click', function (ev) {
    var openBtn = ev.target.closest('[data-open-modal]');
    if (openBtn) {
      ev.preventDefault();
      openModal(openBtn.getAttribute('data-open-modal'));
      return;
    }
    var closeBtn = ev.target.closest('[data-close-modal]');
    if (closeBtn) {
      ev.preventDefault();
      closeModal(closeBtn.closest('.modal-overlay'));
      return;
    }
    var overlay = ev.target;
    if (overlay.classList && overlay.classList.contains('modal-overlay') && overlay.classList.contains('active')) {
      closeModal(overlay);
      return;
    }
    var addBtn = ev.target.closest('#mediathek-acl-add');
    if (addBtn) {
      ev.preventDefault();
      addAclRow();
      return;
    }
    var removeBtn = ev.target.closest('[data-remove-row]');
    if (removeBtn) {
      ev.preventDefault();
      var row = removeBtn.closest('tr');
      if (row) {
        row.remove();
      }
    }
  });

  document.addEventListener('change', function (ev) {
    if (ev.target && ev.target.id === 'mediathek-inherit-acl') {
      syncAclInherit();
    }
  });

  document.addEventListener('keydown', function (ev) {
    if (ev.key !== 'Escape') {
      return;
    }
    var active = document.querySelector('.modal-overlay.active');
    if (active) {
      closeModal(active);
    }
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', syncAclInherit);
  } else {
    syncAclInherit();
  }
})();
