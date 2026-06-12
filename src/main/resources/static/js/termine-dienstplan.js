(function () {
  'use strict';

  var panel = document.getElementById('termine-dienstplan-panel');
  if (!panel) {
    return;
  }

  var unitId = panel.getAttribute('data-unit-id');
  var canWrite = panel.getAttribute('data-can-write') === 'true';

  function getCsrfToken() {
    var meta = document.querySelector('meta[name="csrf-token"]');
    return meta ? meta.getAttribute('content') : '';
  }

  function getCsrfHeader() {
    var meta = document.querySelector('meta[name="csrf-header"]');
    return meta ? meta.getAttribute('content') : 'X-XSRF-TOKEN';
  }

  function apiFetch(url, options) {
    options = options || {};
    options.headers = options.headers || {};
    options.headers['Content-Type'] = 'application/json';
    options.headers[getCsrfHeader()] = getCsrfToken();
    return fetch(url, options).then(function (res) {
      return res.json();
    });
  }

  function notifyResult(result) {
    if (typeof toast === 'function') {
      toast(result.message || (result.ok ? 'Gespeichert.' : 'Fehler.'), result.ok ? 'success' : 'error');
    }
    if (result.ok) {
      window.setTimeout(function () {
        window.location.reload();
      }, 400);
    }
  }

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

  function resetDienstplanModal() {
    var datum = document.getElementById('dienstplan-termin-datum');
    var thema = document.getElementById('dienstplan-termin-thema');
    var beginn = document.getElementById('dienstplan-termin-beginn');
    var ende = document.getElementById('dienstplan-termin-ende');
    var ausbilder = document.getElementById('dienstplan-termin-ausbilder');
    if (datum) {
      datum.value = '';
    }
    if (thema) {
      thema.value = '';
    }
    if (beginn) {
      beginn.value = '19:00';
    }
    if (ende) {
      ende.value = '22:00';
    }
    if (ausbilder) {
      ausbilder.value = '';
    }
  }

  function openCreateModal() {
    resetDienstplanModal();
    openModal('modal-dienstplan-termin');
    var thema = document.getElementById('dienstplan-termin-thema');
    if (thema) {
      thema.focus();
    }
  }

  function saveDienstplanTermin() {
    var datum = document.getElementById('dienstplan-termin-datum');
    var thema = document.getElementById('dienstplan-termin-thema');
    var beginn = document.getElementById('dienstplan-termin-beginn');
    var ende = document.getElementById('dienstplan-termin-ende');
    var ausbilder = document.getElementById('dienstplan-termin-ausbilder');
    if (!datum || !thema || !beginn || !ende) {
      return;
    }
    var body = {
      terminDatum: datum.value,
      thema: thema.value.trim(),
      dienstBeginn: beginn.value,
      dienstEnde: ende.value,
      instructorPersonId: ausbilder && ausbilder.value ? Number(ausbilder.value) : null
    };
    if (!body.terminDatum || !body.thema || !body.dienstBeginn || !body.dienstEnde) {
      if (typeof toast === 'function') {
        toast('Bitte Datum, Thema, Dienstbeginn und Dienstende ausfüllen.', 'warning');
      }
      return;
    }
    apiFetch('/termine/api/dienstplan?unit=' + encodeURIComponent(unitId), {
      method: 'POST',
      body: JSON.stringify(body)
    }).then(notifyResult);
  }

  document.querySelectorAll('[data-close-modal]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      closeModal(btn.closest('.modal-overlay'));
    });
  });

  document.querySelectorAll('.modal-overlay').forEach(function (overlay) {
    overlay.addEventListener('click', function (e) {
      if (e.target === overlay) {
        closeModal(overlay);
      }
    });
  });

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      document.querySelectorAll('.modal-overlay.active').forEach(closeModal);
    }
  });

  if (canWrite) {
    var newBtn = document.getElementById('termine-new-dienstplan-btn');
    var emptyBtn = document.getElementById('termine-new-dienstplan-empty-btn');
    var saveBtn = document.getElementById('dienstplan-save-termin');
    if (newBtn) {
      newBtn.addEventListener('click', openCreateModal);
    }
    if (emptyBtn) {
      emptyBtn.addEventListener('click', openCreateModal);
    }
    if (saveBtn) {
      saveBtn.addEventListener('click', saveDienstplanTermin);
    }
  }
})();
