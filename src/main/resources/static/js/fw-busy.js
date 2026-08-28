(function () {
  'use strict';

  var DEFAULT_MESSAGE = 'Wird gesendet …';
  var EMAIL_MESSAGE = 'E-Mail wird gesendet …';

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
    var page = element.closest('.page-main, .start-page__content, .reservierungen-page, .auth-page');
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
        + '</div>';
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

  function busyMessageForForm(form) {
    if (!form) {
      return EMAIL_MESSAGE;
    }
    var custom = form.getAttribute('data-email-busy-message');
    if (custom) {
      return custom;
    }
    var delivery = form.querySelector('input[name="passwordDelivery"]:checked');
    if (delivery && delivery.value === 'email') {
      return EMAIL_MESSAGE;
    }
    return EMAIL_MESSAGE;
  }

  function shouldAttachFormBusy(form) {
    if (!form || form.dataset.fwBusyActive === 'true') {
      return false;
    }
    if (form.dataset.confirmSubmitting === 'true') {
      return true;
    }
    if (form.getAttribute('data-email-busy') === 'true') {
      return true;
    }
    var delivery = form.querySelector('input[name="passwordDelivery"]:checked');
    return !!(delivery && delivery.value === 'email');
  }

  function beginAction(trigger, options) {
    options = options || {};
    var button = findSubmitButton(trigger);
    var container = options.container || resolveContainer(trigger || button);
    var message = options.message || DEFAULT_MESSAGE;
    var pageLevel = options.pageLevel === true || container === document.body;
    setButtonBusy(button, true, options.buttonLabel);
    showOverlay(container, message, pageLevel);
    return { button: button, container: container };
  }

  function endAction(state) {
    if (!state) {
      return;
    }
    hideOverlay(state.container);
    setButtonBusy(state.button, false);
  }

  function attachFormSubmitBusy(form, submitter, message) {
    if (!form || form.dataset.fwBusyActive === 'true') {
      return null;
    }
    form.dataset.fwBusyActive = 'true';
    return beginAction(submitter || form, {
      message: message || busyMessageForForm(form),
      container: resolveContainer(submitter || form)
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
    if (!form || form.tagName !== 'FORM' || !shouldAttachFormBusy(form)) {
      return;
    }
    attachFormSubmitBusy(form, event.submitter, busyMessageForForm(form));
  });

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
