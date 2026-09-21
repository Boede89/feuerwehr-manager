(function () {
  'use strict';

  var bugOverlay = document.getElementById('bug-report-overlay');
  if (!bugOverlay) {
    return;
  }

  var bugOpenBtns = document.querySelectorAll('.bug-report-open');
  var bugCancelBtn = document.getElementById('start-bug-report-cancel');
  var bugForm = document.getElementById('start-bug-report-form');
  var bugError = document.getElementById('bug-report-error');
  var bugSubmitBtn = document.getElementById('start-bug-report-submit');
  var bugNameInput = document.getElementById('bug-report-name');
  var lastOpenBtn = null;

  function isBugOpen() {
    return bugOverlay.classList.contains('active');
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

  function openBugReport(event) {
    if (event && event.currentTarget) {
      lastOpenBtn = event.currentTarget;
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
    bugOverlay.classList.remove('active');
    bugOverlay.setAttribute('aria-hidden', 'true');
    if (!document.getElementById('login-overlay') ||
        !document.getElementById('login-overlay').classList.contains('active')) {
      document.body.classList.remove('modal-open');
    }
    if (lastOpenBtn) {
      lastOpenBtn.focus();
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

  bugOpenBtns.forEach(function (btn) {
    btn.addEventListener('click', openBugReport);
  });
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
          container: bugOverlay.querySelector('.modal'),
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
          var nameVal = (document.getElementById('bug-report-name') || {}).value || '';
          var emailVal = (document.getElementById('bug-report-email') || {}).value || '';
          bugForm.reset();
          if (document.getElementById('bug-report-name')) {
            document.getElementById('bug-report-name').value = nameVal;
          }
          if (document.getElementById('bug-report-email')) {
            document.getElementById('bug-report-email').value = emailVal;
          }
          bugForm.dataset.submitting = 'false';
          if (bugSubmitBtn) {
            bugSubmitBtn.disabled = false;
          }
          closeBugReport();
        })
        .catch(function (err) {
          setBugError(err.message || 'Die Fehlermeldung konnte nicht gesendet werden.');
          bugForm.dataset.submitting = 'false';
          if (bugSubmitBtn) {
            bugSubmitBtn.disabled = false;
          }
        });
    });
  }

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && isBugOpen()) {
      event.preventDefault();
    }
  });
})();
