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

  function wireModalClose(modal) {
    if (!modal) return;
    modal.querySelectorAll('[data-close-modal]').forEach(function (btn) {
      btn.addEventListener('click', function () { closeModal(modal); });
    });
    modal.addEventListener('click', function (e) {
      if (e.target === modal) closeModal(modal);
    });
  }

  function submitDepeschePrint(id, unitId, computeRoute, csrfName, csrfToken) {
    var form = document.createElement('form');
    form.method = 'post';
    form.action = '/einsatz/manuell/' + encodeURIComponent(id) + '/depesche';
    form.style.display = 'none';

    function addField(name, value) {
      var input = document.createElement('input');
      input.type = 'hidden';
      input.name = name;
      input.value = value;
      form.appendChild(input);
    }

    addField('unit', String(unitId));
    addField(csrfName, csrfToken);
    if (computeRoute) {
      addField('computeRoute', 'true');
    }

    document.body.appendChild(form);
    form.submit();
  }

  function triggerPdfDownload(id, unitId, computeRoute) {
    var url = '/einsatz/manuell/' + encodeURIComponent(id) + '/depesche.pdf'
        + '?unit=' + encodeURIComponent(unitId)
        + '&computeRoute=' + (computeRoute ? 'true' : 'false');
    window.location.assign(url);
  }

  document.addEventListener('DOMContentLoaded', function () {
    var startModal = document.getElementById('modal-manual-alarm-start');
    var startForm = document.getElementById('manual-alarm-start-form');
    var startSubtitle = document.getElementById('manual-alarm-start-subtitle');

    if (startModal) {
      document.querySelectorAll('.manual-alarm-start-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
          var id = btn.getAttribute('data-alarm-id');
          var title = btn.getAttribute('data-alarm-title') || 'Einsatz';
          if (!startForm || !id) return;
          startForm.action = '/einsatz/manuell/' + encodeURIComponent(id) + '/start';
          if (startSubtitle) startSubtitle.textContent = title;
          openModal(startModal);
        });
      });
      wireModalClose(startModal);
    }

    var depescheModal = document.getElementById('modal-manual-alarm-depesche');
    var depescheForm = document.getElementById('manual-alarm-depesche-form');
    var depescheSubtitle = document.getElementById('manual-alarm-depesche-subtitle');

    if (depescheModal && depescheForm) {
      var unitInput = depescheForm.querySelector('input[name="unit"]');
      var csrfInput = depescheForm.querySelector('input[name][type="hidden"]');
      var unitId = unitInput ? unitInput.value : '';
      var csrfName = csrfInput ? csrfInput.name : '_csrf';
      var csrfToken = csrfInput ? csrfInput.value : '';

      document.querySelectorAll('.manual-alarm-depesche-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
          var id = btn.getAttribute('data-alarm-id');
          var title = btn.getAttribute('data-alarm-title') || 'Einsatz';
          if (!id) return;
          depescheForm.dataset.alarmId = id;
          if (depescheSubtitle) depescheSubtitle.textContent = title;
          openModal(depescheModal);
        });
      });

      depescheForm.addEventListener('submit', function (e) {
        e.preventDefault();
        var id = depescheForm.dataset.alarmId;
        if (!id || !unitId) return;

        var actionInput = depescheForm.querySelector('input[name="depescheAction"]:checked');
        var action = actionInput ? actionInput.value : 'pdf';
        var computeRouteInput = depescheForm.querySelector('input[name="computeRoute"]');
        var computeRoute = computeRouteInput ? computeRouteInput.checked : true;

        closeModal(depescheModal);

        if (action === 'pdf') {
          triggerPdfDownload(id, unitId, computeRoute);
          return;
        }
        if (action === 'print') {
          submitDepeschePrint(id, unitId, computeRoute, csrfName, csrfToken);
          return;
        }
        if (action === 'both') {
          triggerPdfDownload(id, unitId, computeRoute);
          window.setTimeout(function () {
            submitDepeschePrint(id, unitId, computeRoute, csrfName, csrfToken);
          }, 800);
        }
      });

      wireModalClose(depescheModal);
    }
  });
})();
