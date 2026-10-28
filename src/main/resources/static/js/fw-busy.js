(function () {
  'use strict';

  var DEFAULT_MESSAGE = 'Bitte warten …';
  var EMAIL_MESSAGE = 'E-Mail wird gesendet …';
  var SAVE_MESSAGE = 'Wird gespeichert …';

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function resolveContainer(element) {
    if (!element) {
      return document.body;
    }
    if (element.classList && element.classList.contains('modal')) {
      return element;
    }
    var modal = element.closest('.modal');
    if (modal) {
      return modal;
    }
    var overlay = element.closest('.modal-overlay');
    if (overlay) {
      return overlay.querySelector('.modal') || overlay;
    }
    var page = element.closest(
      '.page-main, .start-page__content, .reservierungen-page, .auth-page, .einsatzbericht-form-page'
    );
    if (page) {
      return page;
    }
    return document.body;
  }

  function ensureOverlay(container) {
    var overlay = container.querySelector(':scope > .fw-busy-overlay');
    if (!overlay) {
      overlay = document.createElement('div');
      overlay.className = 'fw-busy-overlay';
      overlay.hidden = true;
      overlay.setAttribute('role', 'status');
      overlay.setAttribute('aria-live', 'polite');
      overlay.innerHTML =
        '<div class="fw-busy-overlay__panel">'
        + '<div class="fw-busy-spinner" aria-hidden="true"></div>'
        + '<p class="fw-busy-overlay__text"></p>'
        + '<p class="fw-busy-overlay__hint">Bitte nicht erneut klicken.</p>'
        + '</div>';
      if (getComputedStyle(container).position === 'static' && container !== document.body) {
        container.classList.add('fw-busy-host');
      }
      container.appendChild(overlay);
    }
    return overlay;
  }

  function showOverlay(container, message, pageLevel) {
    if (!container) {
      return;
    }
    var overlay = ensureOverlay(container);
    var text = overlay.querySelector('.fw-busy-overlay__text');
    if (text) {
      text.textContent = message || DEFAULT_MESSAGE;
    }
    overlay.classList.toggle(
      'fw-busy-overlay--page',
      pageLevel === true || container === document.body
    );
    overlay.hidden = false;
    container.classList.add('is-busy');
    container.setAttribute('aria-busy', 'true');
  }

  function hideOverlay(container) {
    if (!container) {
      return;
    }
    var overlay = container.querySelector(':scope > .fw-busy-overlay');
    if (overlay) {
      overlay.hidden = true;
    }
    container.classList.remove('is-busy');
    container.removeAttribute('aria-busy');
  }

  function findSubmitButton(trigger) {
    if (!trigger) {
      return null;
    }
    if (trigger.tagName === 'BUTTON' || (trigger.tagName === 'INPUT' && trigger.type === 'submit')) {
      return trigger;
    }
    if (trigger.querySelector) {
      return trigger.querySelector('button[type="submit"], input[type="submit"]');
    }
    return null;
  }

  function relatedSubmitButtons(form, primary) {
    var buttons = [];
    if (primary) {
      buttons.push(primary);
    }
    if (!form) {
      return buttons;
    }
    form.querySelectorAll('button[type="submit"], input[type="submit"]').forEach(function (btn) {
      if (buttons.indexOf(btn) === -1) {
        buttons.push(btn);
      }
    });
    if (form.id) {
      document.querySelectorAll('button[form="' + form.id + '"], input[form="' + form.id + '"]').forEach(function (btn) {
        if (buttons.indexOf(btn) === -1) {
          buttons.push(btn);
        }
      });
    }
    return buttons;
  }

  function setButtonBusy(button, busy, label) {
    if (!button) {
      return;
    }
    if (busy) {
      if (!button.dataset.fwBusyOriginalHtml) {
        button.dataset.fwBusyOriginalHtml = button.innerHTML;
      }
      button.disabled = true;
      button.classList.add('btn--busy');
      button.setAttribute('aria-busy', 'true');
      var text = label || button.textContent.trim() || 'Bitte warten …';
      button.innerHTML =
        '<span class="fw-btn-spinner" aria-hidden="true"></span>'
        + '<span class="fw-btn-label">' + escapeHtml(text) + '</span>';
      return;
    }
    if (button.dataset.fwBusyOriginalHtml) {
      button.innerHTML = button.dataset.fwBusyOriginalHtml;
      delete button.dataset.fwBusyOriginalHtml;
    }
    button.disabled = false;
    button.classList.remove('btn--busy');
    button.removeAttribute('aria-busy');
  }

  function lockRelatedButtons(form, primary, busy, label) {
    relatedSubmitButtons(form, primary).forEach(function (btn) {
      setButtonBusy(btn, busy, btn === primary ? label : undefined);
    });
  }

  function isPostForm(form) {
    if (!form || form.tagName !== 'FORM') {
      return false;
    }
    var method = (form.getAttribute('method') || 'get').toLowerCase();
    return method === 'post';
  }

  function awaitsConfirmation(form) {
    if (!form || form.dataset.confirmSubmitting === 'true') {
      return false;
    }
    if (form.hasAttribute('data-confirm') || form.hasAttribute('data-confirm-message')) {
      return true;
    }
    if (form.getAttribute('data-testmode-email-prompt') === 'true'
        && document.body
        && document.body.getAttribute('data-test-mode') === 'true'
        && !form.querySelector('input[name="testModeEmailDelivery"]')) {
      return true;
    }
    return false;
  }

  function busyMessageForForm(form) {
    if (!form) {
      return DEFAULT_MESSAGE;
    }
    var customBusy = form.getAttribute('data-busy-message');
    if (customBusy) {
      return customBusy;
    }
    var custom = form.getAttribute('data-email-busy-message');
    if (custom) {
      return custom;
    }
    if (form.getAttribute('data-email-busy') === 'true'
        || form.getAttribute('data-testmode-email-prompt') === 'true') {
      return EMAIL_MESSAGE;
    }
    var delivery = form.querySelector('input[name="passwordDelivery"]:checked');
    if (delivery && delivery.value === 'email') {
      return EMAIL_MESSAGE;
    }
    var submit = form.querySelector('button[type="submit"], input[type="submit"]')
      || (form.id ? document.querySelector('button[form="' + form.id + '"]') : null);
    var label = submit ? (submit.textContent || submit.value || '').replace(/\s+/g, ' ').trim().toLowerCase() : '';
    if (label.indexOf('lösch') !== -1 || label.indexOf('loesch') !== -1) {
      return 'Wird gelöscht …';
    }
    if (label.indexOf('druck') !== -1) {
      return 'Wird gedruckt …';
    }
    if (label.indexOf('send') !== -1 || label.indexOf('absend') !== -1 || label.indexOf('einreich') !== -1) {
      return 'Wird gesendet …';
    }
    if (label.indexOf('speicher') !== -1 || label.indexOf('übernehm') !== -1 || label.indexOf('uebernehm') !== -1
        || label.indexOf('freigeb') !== -1) {
      return SAVE_MESSAGE;
    }
    return DEFAULT_MESSAGE;
  }

  function shouldAttachFormBusy(form) {
    if (!form || form.dataset.fwBusyActive === 'true') {
      return false;
    }
    if (form.getAttribute('data-no-busy') === 'true') {
      return false;
    }
    if (!isPostForm(form)) {
      return false;
    }
    var target = (form.getAttribute('target') || '').toLowerCase();
    if (target && target !== '_self') {
      return false;
    }
    if (awaitsConfirmation(form)) {
      return false;
    }
    return true;
  }

  function beginAction(trigger, options) {
    options = options || {};
    var button = findSubmitButton(trigger);
    var form = options.form || (trigger && trigger.tagName === 'FORM' ? trigger : (trigger && trigger.form)) || null;
    var container = options.container || resolveContainer(trigger || button || form);
    var message = options.message || DEFAULT_MESSAGE;
    var pageLevel = options.pageLevel === true || container === document.body;
    lockRelatedButtons(form, button, true, options.buttonLabel);
    showOverlay(container, message, pageLevel);
    return { button: button, container: container, form: form };
  }

  function endAction(state) {
    if (!state) {
      return;
    }
    hideOverlay(state.container);
    lockRelatedButtons(state.form, state.button, false);
  }

  function attachFormSubmitBusy(form, submitter, message) {
    if (!form || form.dataset.fwBusyActive === 'true') {
      return null;
    }
    form.dataset.fwBusyActive = 'true';
    var pageLevel = form.getAttribute('data-busy-page') === 'true';
    return beginAction(submitter || form, {
      form: form,
      message: message || busyMessageForForm(form),
      container: pageLevel ? document.body : resolveContainer(submitter || form),
      pageLevel: pageLevel,
      buttonLabel: form.getAttribute('data-busy-button-label') || undefined
    });
  }

  function wrapPromise(trigger, promise, options) {
    var state = beginAction(trigger, options || {});
    return Promise.resolve(promise).finally(function () {
      endAction(state);
    });
  }

  document.addEventListener('submit', function (event) {
    var form = event.target;
    if (!form || form.tagName !== 'FORM') {
      return;
    }
    if (form.dataset.fwBusyActive === 'true') {
      event.preventDefault();
      event.stopImmediatePropagation();
      return;
    }
  }, true);

  document.addEventListener('submit', function (event) {
    var form = event.target;
    if (!form || form.tagName !== 'FORM' || !shouldAttachFormBusy(form)) {
      return;
    }
    var submitter = event.submitter;
    window.setTimeout(function () {
      if (event.defaultPrevented) {
        return;
      }
      if (!shouldAttachFormBusy(form)) {
        return;
      }
      attachFormSubmitBusy(form, submitter, busyMessageForForm(form));
    }, 0);
  });

  var originalSubmit = HTMLFormElement.prototype.submit;
  HTMLFormElement.prototype.submit = function () {
    if (this.dataset.fwBusyActive === 'true') {
      return;
    }
    if (shouldAttachFormBusy(this)) {
      attachFormSubmitBusy(this, null, busyMessageForForm(this));
    }
    return originalSubmit.call(this);
  };

  window.FwBusy = {
    showOverlay: showOverlay,
    hideOverlay: hideOverlay,
    setButtonBusy: setButtonBusy,
    beginAction: beginAction,
    endAction: endAction,
    attachFormSubmitBusy: attachFormSubmitBusy,
    wrapPromise: wrapPromise,
    resolveContainer: resolveContainer
  };
})();
