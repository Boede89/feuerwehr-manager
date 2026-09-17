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
})();
