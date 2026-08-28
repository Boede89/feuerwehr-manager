(function () {
  'use strict';

  var overlay = document.getElementById('login-overlay');
  var openBtn = document.getElementById('start-login-open');
  var closeBtn = document.getElementById('start-login-close');
  var userInput = document.getElementById('username');

  var bugOverlay = document.getElementById('bug-report-overlay');
  var bugOpenBtn = document.getElementById('start-bug-report-open');
  var bugCloseBtn = document.getElementById('start-bug-report-close');
  var bugCancelBtn = document.getElementById('start-bug-report-cancel');
  var bugForm = document.getElementById('start-bug-report-form');
  var bugError = document.getElementById('bug-report-error');
  var bugSubmitBtn = document.getElementById('start-bug-report-submit');
  var bugNameInput = document.getElementById('bug-report-name');
  var reservationOpen = document.getElementById('start-reservation-open');

  if (!overlay) {
    return;
  }

  function isOpen() {
    return overlay.classList.contains('active');
  }

  function isBugOpen() {
    return bugOverlay && bugOverlay.classList.contains('active');
  }

  function openLogin() {
    overlay.classList.add('active');
    overlay.setAttribute('aria-hidden', 'false');
    document.body.classList.add('modal-open');
    if (userInput) {
      window.setTimeout(function () {
        userInput.focus();
      }, 30);
    }
  }

  function closeLogin() {
    overlay.classList.remove('active');
    overlay.setAttribute('aria-hidden', 'true');
    if (!isBugOpen()) {
      document.body.classList.remove('modal-open');
    }
    if (openBtn) {
      openBtn.focus();
    }
  }

  function setBugError(message) {
    if (!bugError) {
      return;
    }
    if (message) {
      bugError.textContent = message;
      bugError.hidden = false;
    } else {
      bugError.textContent = '';
      bugError.hidden = true;
    }
  }

  function openBugReport() {
    if (!bugOverlay) {
      return;
    }
    setBugError('');
    bugOverlay.classList.add('active');
    bugOverlay.setAttribute('aria-hidden', 'false');
    document.body.classList.add('modal-open');
    if (bugNameInput) {
      window.setTimeout(function () {
        bugNameInput.focus();
      }, 30);
    }
  }

  function closeBugReport() {
    if (!bugOverlay) {
      return;
    }
    bugOverlay.classList.remove('active');
    bugOverlay.setAttribute('aria-hidden', 'true');
    if (!isOpen()) {
      document.body.classList.remove('modal-open');
    }
    if (bugOpenBtn) {
      bugOpenBtn.focus();
    }
  }

  function csrfHeaders() {
    var headers = { Accept: 'application/json', 'Content-Type': 'application/json' };
    var tokenMeta = document.querySelector('meta[name="csrf-token"]');
    var headerMeta = document.querySelector('meta[name="csrf-header"]');
    if (tokenMeta && headerMeta) {
      headers[headerMeta.getAttribute('content')] = tokenMeta.getAttribute('content');
    }
    return headers;
  }

  if (openBtn) {
    openBtn.addEventListener('click', openLogin);
  }
  if (closeBtn) {
    closeBtn.addEventListener('click', closeLogin);
  }

  if (bugOpenBtn) {
    bugOpenBtn.addEventListener('click', openBugReport);
  }
  if (bugCloseBtn) {
    bugCloseBtn.addEventListener('click', closeBugReport);
  }
  if (bugCancelBtn) {
    bugCancelBtn.addEventListener('click', closeBugReport);
  }

  if (reservationOpen) {
    reservationOpen.addEventListener('click', function (event) {
      event.preventDefault();
      var target = reservationOpen.getAttribute('data-href');
      if (!target) {
        return;
      }
      var message =
        'Diese Funktion kann noch Fehler enthalten. Bitte melden Sie Fehler über „Fehler melden“ auf der Startseite.';
      var promise = window.FwConfirm && window.FwConfirm.show
        ? window.FwConfirm.show({
            title: 'Hinweis zur Reservierung',
            message: message,
            confirmLabel: 'OK',
            cancelLabel: 'Abbrechen'
          })
        : Promise.resolve(window.confirm(message));
      promise.then(function (confirmed) {
        if (confirmed) {
          window.location.href = target;
        }
      });
    });
  }

  overlay.addEventListener('click', function (event) {
    if (event.target === overlay) {
      closeLogin();
    }
  });

  if (bugOverlay) {
    bugOverlay.addEventListener('click', function (event) {
      if (event.target === bugOverlay) {
        closeBugReport();
      }
    });
  }

  if (bugForm) {
    bugForm.addEventListener('submit', function (event) {
      event.preventDefault();
      setBugError('');
      if (bugSubmitBtn) {
        bugSubmitBtn.disabled = true;
      }
      var payload = {
        reporterName: (document.getElementById('bug-report-name') || {}).value || '',
        reporterEmail: (document.getElementById('bug-report-email') || {}).value || '',
        area: (document.getElementById('bug-report-area') || {}).value || '',
        description: (document.getElementById('bug-report-description') || {}).value || '',
        pageUrl: window.location.href
      };
      fetch('/login/bug-report', {
        method: 'POST',
        credentials: 'same-origin',
        headers: csrfHeaders(),
        body: JSON.stringify(payload)
      })
        .then(function (res) {
          return res.json().then(function (data) {
            return { ok: res.ok, data: data };
          });
        })
        .then(function (result) {
          var data = result.data || {};
          if (!result.ok || !data.success) {
            throw new Error(data.message || 'Die Fehlermeldung konnte nicht gesendet werden.');
          }
          if (window.toast) {
            window.toast(data.message, 'success');
          }
          bugForm.reset();
          closeBugReport();
        })
        .catch(function (err) {
          setBugError(err.message || 'Die Fehlermeldung konnte nicht gesendet werden.');
        })
        .finally(function () {
          if (bugSubmitBtn) {
            bugSubmitBtn.disabled = false;
          }
        });
    });
  }

  document.addEventListener('keydown', function (event) {
    if (event.key !== 'Escape') {
      return;
    }
    var unknownModal = document.getElementById('modal-rfid-register-unknown');
    if (unknownModal && unknownModal.classList.contains('active')) {
      return;
    }
    if (isBugOpen()) {
      closeBugReport();
      return;
    }
    if (isOpen()) {
      closeLogin();
    }
  });

  if (isOpen()) {
    document.body.classList.add('modal-open');
  }
})();
