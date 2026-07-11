(function () {
  'use strict';

  function openModal(modal) {
    if (!modal) return;
    modal.classList.add('active');
    document.body.classList.add('modal-open');
    modal.setAttribute('aria-hidden', 'false');
  }

  function closeModal(modal) {
    if (!modal) return;
    modal.classList.remove('active');
    if (!document.querySelector('.modal-overlay.active')) {
      document.body.classList.remove('modal-open');
    }
    modal.setAttribute('aria-hidden', 'true');
  }

  document.addEventListener('DOMContentLoaded', function () {
    var modal = document.getElementById('modal-manual-alarm-start');
    if (!modal) return;

    var form = document.getElementById('manual-alarm-start-form');
    var subtitle = document.getElementById('manual-alarm-start-subtitle');

    document.querySelectorAll('.manual-alarm-start-btn').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var id = btn.getAttribute('data-alarm-id');
        var title = btn.getAttribute('data-alarm-title') || 'Einsatz';
        if (!form || !id) return;
        form.action = '/einsatz/manuell/' + encodeURIComponent(id) + '/start';
        if (subtitle) subtitle.textContent = title;
        openModal(modal);
      });
    });

    modal.querySelectorAll('[data-close-modal]').forEach(function (btn) {
      btn.addEventListener('click', function () { closeModal(modal); });
    });

    modal.addEventListener('click', function (e) {
      if (e.target === modal) closeModal(modal);
    });
  });
})();
