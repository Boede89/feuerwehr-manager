(function () {
  'use strict';

  var overlay = document.getElementById('login-overlay');
  var openBtn = document.getElementById('start-login-open');
  var closeBtn = document.getElementById('start-login-close');
  var userInput = document.getElementById('username');

  var bugOverlay = document.getElementById('bug-report-overlay');
  var bugOpenBtn = document.getElementById('start-bug-report-open');
  var bugCancelBtn = document.getElementById('start-bug-report-cancel');
  var bugForm = document.getElementById('start-bug-report-form');
  var bugError = document.getElementById('bug-report-error');
  var bugSubmitBtn = document.getElementById('start-bug-report-submit');
  var bugNameInput = document.getElementById('bug-report-name');

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
    if (typeof showLoginView === 'function') {
      showLoginView();
    }
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
  if (bugCancelBtn) {
    bugCancelBtn.addEventListener('click', closeBugReport);
  }

  if (bugForm) {
    bugForm.addEventListener('submit', function (event) {
      event.preventDefault();
      setBugError('');
      if (bugForm.dataset.submitting === 'true') {
        return;
      }
      bugForm.dataset.submitting = 'true';
      var payload = {
        reporterName: (document.getElementById('bug-report-name') || {}).value || '',
        reporterEmail: (document.getElementById('bug-report-email') || {}).value || '',
        area: (document.getElementById('bug-report-area') || {}).value || '',
        description: (document.getElementById('bug-report-description') || {}).value || '',
        pageUrl: window.location.href
      };
      var request = fetch('/login/bug-report', {
        method: 'POST',
        credentials: 'same-origin',
        headers: csrfHeaders(),
        body: JSON.stringify(payload)
      });
      if (window.FwBusy && bugSubmitBtn) {
        request = window.FwBusy.wrapPromise(bugSubmitBtn, request, {
          message: 'Fehlermeldung wird gesendet …',
          container: bugOverlay ? bugOverlay.querySelector('.modal') : null,
          buttonLabel: 'Wird gesendet …'
        });
      } else if (bugSubmitBtn) {
        bugSubmitBtn.disabled = true;
      }
      request
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
          bugForm.dataset.submitting = 'false';
        });
    });
  }

  document.addEventListener('keydown', function (event) {
    // Modale schließen sich nur über Buttons, nicht per Escape / Klick daneben.
    if (event.key === 'Escape' && (isOpen() || isBugOpen())) {
      event.preventDefault();
    }
  });

  var loginForm = document.getElementById('login-form');
  var registerForm = document.getElementById('register-form');
  var showRegisterBtn = document.getElementById('login-show-register');
  var backLoginBtn = document.getElementById('register-back-login');
  var registerError = document.getElementById('register-error');
  var registerSubmitBtn = document.getElementById('register-submit');
  var loginTitle = document.getElementById('login-overlay-title');
  var loginSubtitle = loginTitle ? loginTitle.parentElement.querySelector('p') : null;

  function setRegisterError(message) {
    if (!registerError) {
      return;
    }
    if (message) {
      registerError.textContent = message;
      registerError.hidden = false;
    } else {
      registerError.textContent = '';
      registerError.hidden = true;
    }
  }

  function showRegisterView() {
    if (!registerForm || !loginForm) {
      return;
    }
    setRegisterError('');
    loginForm.hidden = true;
    registerForm.hidden = false;
    if (loginTitle) {
      loginTitle.textContent = 'Registrieren';
    }
    if (loginSubtitle) {
      loginSubtitle.textContent = 'Konto anlegen — Freischaltung durch Administrator';
    }
    var first = document.getElementById('reg-first-name');
    if (first) {
      window.setTimeout(function () {
        first.focus();
      }, 30);
    }
  }

  function showLoginView() {
    if (!registerForm || !loginForm) {
      return;
    }
    setRegisterError('');
    registerForm.hidden = true;
    loginForm.hidden = false;
    if (loginTitle) {
      loginTitle.textContent = 'Anmelden';
    }
    if (loginSubtitle) {
      loginSubtitle.textContent = 'Zugang für berechtigte Einsatzkräfte';
    }
    if (userInput) {
      window.setTimeout(function () {
        userInput.focus();
      }, 30);
    }
  }

  function csrfFormHeaders() {
    var headers = {
      Accept: 'application/json',
      'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
    };
    var tokenMeta = document.querySelector('meta[name="csrf-token"]');
    var headerMeta = document.querySelector('meta[name="csrf-header"]');
    if (tokenMeta && headerMeta) {
      headers[headerMeta.getAttribute('content')] = tokenMeta.getAttribute('content');
    }
    return headers;
  }

  if (showRegisterBtn) {
    showRegisterBtn.addEventListener('click', showRegisterView);
  }
  if (backLoginBtn) {
    backLoginBtn.addEventListener('click', showLoginView);
  }

  var successOverlay = document.getElementById('modal-register-success');
  var successMessageEl = document.getElementById('modal-register-success-message');
  var successOkBtn = document.getElementById('modal-register-success-ok');

  function showRegisterSuccess(message) {
    return new Promise(function (resolve) {
      if (!successOverlay) {
        window.alert(message || 'Ihre Registrierung wurde übermittelt.');
        resolve();
        return;
      }
      if (successMessageEl) {
        successMessageEl.textContent =
          message ||
          'Ihre Registrierung wurde erfolgreich übermittelt. Ein Administrator muss Ihr Konto noch freischalten.';
      }
      successOverlay.hidden = false;
      successOverlay.classList.add('active');
      successOverlay.setAttribute('aria-hidden', 'false');
      document.body.classList.add('modal-open');

      function finish() {
        successOverlay.classList.remove('active');
        successOverlay.setAttribute('aria-hidden', 'true');
        successOverlay.hidden = true;
        if (successOkBtn) {
          successOkBtn.removeEventListener('click', onOk);
        }
        resolve();
      }

      function onOk(event) {
        event.preventDefault();
        finish();
      }

      if (successOkBtn) {
        successOkBtn.addEventListener('click', onOk);
      }
      window.setTimeout(function () {
        if (successOkBtn) {
          successOkBtn.focus();
        }
      }, 30);
    });
  }

  if (registerForm) {
    registerForm.addEventListener('submit', function (event) {
      event.preventDefault();
      setRegisterError('');
      if (registerForm.dataset.submitting === 'true') {
        return;
      }
      registerForm.dataset.submitting = 'true';
      var body = new URLSearchParams();
      body.set('firstName', (document.getElementById('reg-first-name') || {}).value || '');
      body.set('lastName', (document.getElementById('reg-last-name') || {}).value || '');
      body.set('email', (document.getElementById('reg-email') || {}).value || '');
      body.set('birthdate', (document.getElementById('reg-birthdate') || {}).value || '');
      body.set('unitId', (document.getElementById('reg-unit') || {}).value || '');
      var request = fetch('/login/register', {
        method: 'POST',
        credentials: 'same-origin',
        headers: csrfFormHeaders(),
        body: body.toString()
      });
      if (window.FwBusy && registerSubmitBtn) {
        request = window.FwBusy.wrapPromise(registerSubmitBtn, request, {
          message: 'Registrierung wird übermittelt …',
          container: overlay ? overlay.querySelector('.auth-card') : null,
          buttonLabel: 'Wird gesendet …'
        });
      } else if (registerSubmitBtn) {
        registerSubmitBtn.disabled = true;
      }
      request
        .then(function (res) {
          return res.json().then(function (data) {
            return { ok: res.ok, data: data };
          });
        })
        .then(function (result) {
          var data = result.data || {};
          if (!result.ok || !data.ok) {
            throw new Error(data.message || 'Registrierung fehlgeschlagen.');
          }
          registerForm.reset();
          showLoginView();
          if (overlay) {
            overlay.classList.remove('active');
            overlay.setAttribute('aria-hidden', 'true');
          }
          return showRegisterSuccess(data.message).then(function () {
            if (!isBugOpen()) {
              document.body.classList.remove('modal-open');
            }
            if (openBtn) {
              openBtn.focus();
            }
          });
        })
        .catch(function (err) {
          setRegisterError(err.message || 'Registrierung fehlgeschlagen.');
        })
        .finally(function () {
          registerForm.dataset.submitting = 'false';
          if (registerSubmitBtn && !(window.FwBusy)) {
            registerSubmitBtn.disabled = false;
          }
        });
    });
  }

  if (isOpen()) {
    document.body.classList.add('modal-open');
  }
})();
