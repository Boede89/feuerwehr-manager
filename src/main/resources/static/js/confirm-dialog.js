(function () {
  'use strict';

  var modalEl = null;
  var titleEl = null;
  var messageEl = null;
  var confirmBtn = null;
  var cancelBtn = null;
  var resolveFn = null;

  function ensureModal() {
    if (modalEl) {
      return modalEl;
    }
    modalEl = document.createElement('div');
    modalEl.id = 'fw-confirm-dialog';
    modalEl.className = 'modal-overlay confirm-dialog';
    modalEl.setAttribute('role', 'dialog');
    modalEl.setAttribute('aria-modal', 'true');
    modalEl.setAttribute('aria-labelledby', 'fw-confirm-dialog-title');
    modalEl.setAttribute('aria-describedby', 'fw-confirm-dialog-message');
    modalEl.hidden = true;
    modalEl.innerHTML =
      '<div class="modal confirm-dialog__box">' +
      '  <div class="modal__header confirm-dialog__header">' +
      '    <h3 id="fw-confirm-dialog-title"></h3>' +
      '  </div>' +
      '  <div class="modal__body">' +
      '    <p id="fw-confirm-dialog-message" class="confirm-dialog__message"></p>' +
      '  </div>' +
      '  <div class="modal__footer confirm-dialog__footer">' +
      '    <button type="button" class="btn btn--outline" id="fw-confirm-dialog-cancel">Abbrechen</button>' +
      '    <button type="button" class="btn btn--primary" id="fw-confirm-dialog-confirm">Bestätigen</button>' +
      '  </div>' +
      '</div>';
    document.body.appendChild(modalEl);

    titleEl = modalEl.querySelector('#fw-confirm-dialog-title');
    messageEl = modalEl.querySelector('#fw-confirm-dialog-message');
    confirmBtn = modalEl.querySelector('#fw-confirm-dialog-confirm');
    cancelBtn = modalEl.querySelector('#fw-confirm-dialog-cancel');

    cancelBtn.addEventListener('click', function () {
      close(false);
    });
    confirmBtn.addEventListener('click', function () {
      close(true);
    });
    modalEl.addEventListener('click', function (e) {
      if (e.target === modalEl) {
        close(false);
      }
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && modalEl && !modalEl.hidden) {
        close(false);
      }
    });

    return modalEl;
  }

  function close(result) {
    if (!modalEl) {
      return;
    }
    modalEl.hidden = true;
    modalEl.classList.remove('active');
    document.body.classList.remove('modal-open');
    if (resolveFn) {
      resolveFn(!!result);
      resolveFn = null;
    }
  }

  function applyVariant(variant) {
    confirmBtn.classList.remove('btn--primary', 'btn--success', 'btn--danger');
    if (variant === 'danger') {
      confirmBtn.classList.add('btn--danger');
    } else if (variant === 'success') {
      confirmBtn.classList.add('btn--success');
    } else {
      confirmBtn.classList.add('btn--primary');
    }
  }

  function show(options) {
    return new Promise(function (resolve) {
      var opts = options || {};
      ensureModal();
      titleEl.textContent = opts.title || 'Bitte bestätigen';
      messageEl.textContent = opts.message || '';
      confirmBtn.textContent = opts.confirmLabel || 'Bestätigen';
      cancelBtn.textContent = opts.cancelLabel || 'Abbrechen';
      applyVariant(opts.variant || 'primary');
      resolveFn = resolve;
      modalEl.hidden = false;
      modalEl.classList.add('active');
      document.body.classList.add('modal-open');
      window.setTimeout(function () {
        confirmBtn.focus();
      }, 0);
    });
  }

  function optionsFromForm(form) {
    return {
      title: form.getAttribute('data-confirm-title') || 'Bitte bestätigen',
      message: form.getAttribute('data-confirm-message') || form.getAttribute('data-confirm') || '',
      confirmLabel: form.getAttribute('data-confirm-label') || 'Bestätigen',
      cancelLabel: form.getAttribute('data-confirm-cancel') || 'Abbrechen',
      variant: form.getAttribute('data-confirm-variant') || 'primary'
    };
  }

  function bindFormConfirms() {
    document.addEventListener(
      'submit',
      function (e) {
        var form = e.target.closest('form[data-confirm], form[data-confirm-message]');
        if (!form || form.dataset.confirmSubmitting === 'true') {
          return;
        }
        e.preventDefault();
        e.stopImmediatePropagation();
        show(optionsFromForm(form)).then(function (ok) {
          if (ok) {
            form.dataset.confirmSubmitting = 'true';
            if (typeof form.requestSubmit === 'function') {
              form.requestSubmit();
            } else {
              form.submit();
            }
          }
        });
      },
      true
    );
  }

  window.FwConfirm = {
    show: show,
    ask: function (message, title) {
      return show({ title: title || 'Bitte bestätigen', message: message || '' });
    },
    releaseReport: function (reportLabel) {
      return show({
        title: (reportLabel || 'Bericht') + ' freigeben?',
        message:
          'Nach der Freigabe ist der Eintrag für die normale Bearbeitung gesperrt. ' +
          'Administratoren können weiterhin Änderungen vornehmen.',
        confirmLabel: 'Freigeben',
        variant: 'success'
      });
    },
    archiveReport: function (reportLabel) {
      return show({
        title: (reportLabel || 'Bericht') + ' archivieren?',
        message: 'Der Eintrag wird ins Archiv verschoben und erscheint standardmäßig nicht mehr in der aktiven Liste.',
        confirmLabel: 'Archivieren',
        variant: 'primary'
      });
    },
    deleteReport: function (reportLabel) {
      return show({
        title: (reportLabel || 'Eintrag') + ' löschen?',
        message: 'Diese Aktion kann nicht rückgängig gemacht werden.',
        confirmLabel: 'Löschen',
        variant: 'danger'
      });
    },
    deleteAttachment: function () {
      return show({
        title: 'Anhang löschen?',
        message: 'Die Datei wird dauerhaft aus dem Bericht entfernt.',
        confirmLabel: 'Löschen',
        variant: 'danger'
      });
    }
  };

  document.addEventListener('DOMContentLoaded', bindFormConfirms);
})();
