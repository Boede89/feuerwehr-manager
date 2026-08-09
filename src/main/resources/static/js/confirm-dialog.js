(function () {
  'use strict';

  var modalEl = null;
  var titleEl = null;
  var messageEl = null;
  var checkboxesEl = null;
  var confirmBtn = null;
  var cancelBtn = null;
  var resolveFn = null;
  var activeCheckboxes = [];
  var emailSelectActive = false;
  var textInputActive = false;

  function isTestMode() {
    return document.body && document.body.getAttribute('data-test-mode') === 'true';
  }

  function testModeEmailSelectMarkup() {
    return (
      '<div class="confirm-dialog__email-select" id="fw-confirm-testmode-email-wrap">' +
      '  <label for="fw-confirm-testmode-email">E-Mail im Testmodus</label>' +
      '  <select id="fw-confirm-testmode-email" name="testModeEmailDelivery" class="field">' +
      '    <option value="NONE" selected>Keine E-Mail senden</option>' +
      '    <option value="SELF">An mich</option>' +
      '    <option value="CONFIGURED">An hinterlegte Person</option>' +
      '  </select>' +
      '  <p class="hint" style="margin:0;">Im Produktivbetrieb könnte hier automatisch eine E-Mail versendet werden.</p>' +
      '</div>'
    );
  }

  function appendTestModeEmailSelect(html) {
    if (!isTestMode()) {
      return html;
    }
    emailSelectActive = true;
    return (html || '') + testModeEmailSelectMarkup();
  }

  function collectTestModeEmailDelivery() {
    var select = document.getElementById('fw-confirm-testmode-email');
    return select && select.value ? select.value : 'NONE';
  }

  function appendHiddenField(form, name, value) {
    if (!form) {
      return;
    }
    form.querySelectorAll('input[name="' + name + '"]').forEach(function (el) {
      el.remove();
    });
    var input = document.createElement('input');
    input.type = 'hidden';
    input.name = name;
    input.value = value;
    form.appendChild(input);
  }

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
      '    <div id="fw-confirm-dialog-message" class="confirm-dialog__message"></div>' +
      '    <div id="fw-confirm-dialog-text-wrap" class="confirm-dialog__text-wrap" hidden>' +
      '      <label for="fw-confirm-dialog-text" id="fw-confirm-dialog-text-label" class="confirm-dialog__text-label"></label>' +
      '      <textarea id="fw-confirm-dialog-text" class="field" rows="3"></textarea>' +
      '    </div>' +
      '    <div id="fw-confirm-dialog-checkboxes" class="confirm-dialog__checkboxes" hidden></div>' +
      '  </div>' +
      '  <div class="modal__footer confirm-dialog__footer">' +
      '    <button type="button" class="btn btn--outline" id="fw-confirm-dialog-cancel">Abbrechen</button>' +
      '    <button type="button" class="btn btn--primary" id="fw-confirm-dialog-confirm">Bestätigen</button>' +
      '  </div>' +
      '</div>';
    document.body.appendChild(modalEl);

    titleEl = modalEl.querySelector('#fw-confirm-dialog-title');
    messageEl = modalEl.querySelector('#fw-confirm-dialog-message');
    checkboxesEl = modalEl.querySelector('#fw-confirm-dialog-checkboxes');
    confirmBtn = modalEl.querySelector('#fw-confirm-dialog-confirm');
    cancelBtn = modalEl.querySelector('#fw-confirm-dialog-cancel');
    var textWrapEl = modalEl.querySelector('#fw-confirm-dialog-text-wrap');
    var textLabelEl = modalEl.querySelector('#fw-confirm-dialog-text-label');
    var textInputEl = modalEl.querySelector('#fw-confirm-dialog-text');
    modalEl._textWrapEl = textWrapEl;
    modalEl._textLabelEl = textLabelEl;
    modalEl._textInputEl = textInputEl;

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

  function renderCheckboxes(checkboxes) {
    if (!checkboxesEl) {
      return;
    }
    activeCheckboxes = checkboxes || [];
    if (!activeCheckboxes.length) {
      checkboxesEl.hidden = true;
      checkboxesEl.innerHTML = '';
      return;
    }
    var html = '';
    activeCheckboxes.forEach(function (cb) {
      var id = cb.id || cb.name;
      var checked = cb.checked ? ' checked' : '';
      html +=
        '<label class="confirm-dialog__checkbox" for="' + id + '">' +
        '<input type="checkbox" id="' + id + '" name="' + (cb.name || cb.id) + '"' + checked + '/>' +
        '<span>' + (cb.label || '') + '</span>' +
        '</label>';
    });
    checkboxesEl.innerHTML = html;
    checkboxesEl.hidden = false;
  }

  function collectCheckboxValues() {
    var values = { ok: true };
    activeCheckboxes.forEach(function (cb) {
      var key = cb.name || cb.id;
      var input = checkboxesEl ? checkboxesEl.querySelector('#' + cb.id) : null;
      values[key] = input ? input.checked : false;
    });
    if (emailSelectActive || document.getElementById('fw-confirm-testmode-email')) {
      values.testModeEmailDelivery = collectTestModeEmailDelivery();
    }
    if (textInputActive && modalEl && modalEl._textInputEl) {
      values.textValue = (modalEl._textInputEl.value || '').trim();
    }
    return values;
  }

  function close(result) {
    if (!modalEl) {
      return;
    }
    modalEl.hidden = true;
    modalEl.classList.remove('active', 'confirm-dialog--release-validation');
    document.body.classList.remove('modal-open');
    if (resolveFn) {
      if (result && (activeCheckboxes.length || emailSelectActive || textInputActive)) {
        var values = collectCheckboxValues();
        if (!activeCheckboxes.length) {
          values.ok = true;
        }
        resolveFn(values);
      } else if (activeCheckboxes.length || emailSelectActive || textInputActive) {
        resolveFn({ ok: false });
      } else {
        resolveFn(!!result);
      }
      resolveFn = null;
    }
    activeCheckboxes = [];
    emailSelectActive = false;
    textInputActive = false;
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
      messageEl.className = 'confirm-dialog__message';
      messageEl.textContent = opts.message || '';
      confirmBtn.textContent = opts.confirmLabel || 'Bestätigen';
      cancelBtn.textContent = opts.cancelLabel || 'Abbrechen';
      applyVariant(opts.variant || 'primary');
      emailSelectActive = false;
      textInputActive = false;
      renderCheckboxes(opts.checkboxes);
      if (modalEl._textWrapEl && modalEl._textInputEl && modalEl._textLabelEl) {
        if (opts.textInputLabel) {
          textInputActive = true;
          modalEl._textWrapEl.hidden = false;
          modalEl._textLabelEl.textContent = opts.textInputLabel;
          modalEl._textInputEl.value = opts.textInputValue || '';
          modalEl._textInputEl.placeholder = opts.textInputPlaceholder || '';
        } else {
          modalEl._textWrapEl.hidden = true;
          modalEl._textInputEl.value = '';
        }
      }
      if (opts.emailSelect && isTestMode()) {
        emailSelectActive = true;
        if (checkboxesEl) {
          checkboxesEl.innerHTML = (checkboxesEl.innerHTML || '') + testModeEmailSelectMarkup();
          checkboxesEl.hidden = false;
        }
      }
      resolveFn = resolve;
      modalEl.hidden = false;
      modalEl.classList.add('active');
      document.body.classList.add('modal-open');
      window.setTimeout(function () {
        if (textInputActive && modalEl._textInputEl) {
          modalEl._textInputEl.focus();
        } else {
          confirmBtn.focus();
        }
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

  var EINSATZ_RELEASE_FIELD_NAMES = ['printReport', 'createGeraetewart', 'printGeraetewart', 'printMaengel'];

  function releaseDefaultsFromElement(el) {
    if (!el || !el.dataset) {
      return {};
    }
    return {
      createGeraetewart: el.dataset.releaseCreateGeraetewart === 'true',
      printReport: el.dataset.releasePrintReport === 'true',
      printGeraetewart: el.dataset.releasePrintGeraetewart === 'true',
      printMaengel: el.dataset.releasePrintMaengel === 'true',
      hasMaterialDamages: el.dataset.releaseHasMaterialDamages === 'true',
      hasDeployedEquipment: el.dataset.releaseHasDeployedEquipment === 'true'
    };
  }

  function checkboxMarkup(id, name, label, checked) {
    var isChecked = checked ? ' checked' : '';
    return (
      '<label class="confirm-dialog__checkbox" for="' + id + '">' +
      '<input type="checkbox" id="' + id + '" name="' + name + '"' + isChecked + '/>' +
      '<span>' + label + '</span>' +
      '</label>'
    );
  }

  function bindEinsatzReleaseCheckboxInteractions(defaults) {
    var createCb = document.getElementById('fw-confirm-create-geraetewart');
    var gwmPrintWrap = document.getElementById('fw-confirm-print-geraetewart-wrap');
    var gwmPrintCb = document.getElementById('fw-confirm-print-geraetewart');

    function syncGeraetewartPrintVisibility() {
      var show = !!(createCb && createCb.checked);
      if (gwmPrintWrap) {
        gwmPrintWrap.hidden = !show;
      }
      if (!show && gwmPrintCb) {
        gwmPrintCb.checked = false;
      }
    }

    if (createCb) {
      createCb.addEventListener('change', syncGeraetewartPrintVisibility);
    }
    syncGeraetewartPrintVisibility();

    if (defaults && defaults.printGeraetewart && createCb && createCb.checked && gwmPrintCb) {
      gwmPrintCb.checked = true;
    }
  }

  function renderEinsatzReleaseCheckboxes(defaults) {
    if (!checkboxesEl) {
      return;
    }
    var d = defaults || {};
    var hasMaengel = !!d.hasMaterialDamages;
    var hasGeraete = !!d.hasDeployedEquipment;
    activeCheckboxes = [
      {
        id: 'fw-confirm-print-report',
        name: 'printReport',
        label: 'Einsatzbericht drucken',
        checked: !!d.printReport
      }
    ];
    if (hasGeraete) {
      activeCheckboxes.push(
        {
          id: 'fw-confirm-create-geraetewart',
          name: 'createGeraetewart',
          label: 'Gerätewartmitteilung erstellen',
          checked: !!d.createGeraetewart
        },
        {
          id: 'fw-confirm-print-geraetewart',
          name: 'printGeraetewart',
          label: 'Gerätewartmitteilung drucken',
          checked: !!d.printGeraetewart
        }
      );
    }
    if (hasMaengel) {
      activeCheckboxes.push({
        id: 'fw-confirm-print-maengel',
        name: 'printMaengel',
        label: 'Mängelbericht drucken',
        checked: !!d.printMaengel
      });
    }

    var html = checkboxMarkup(
      'fw-confirm-print-report',
      'printReport',
      'Einsatzbericht drucken',
      d.printReport
    );
    if (hasGeraete) {
      html +=
        '<div class="confirm-dialog__checkbox-row">' +
        checkboxMarkup(
          'fw-confirm-create-geraetewart',
          'createGeraetewart',
          'Gerätewartmitteilung erstellen',
          d.createGeraetewart
        ) +
        '<div class="confirm-dialog__checkbox-dependent" id="fw-confirm-print-geraetewart-wrap" hidden>' +
        checkboxMarkup(
          'fw-confirm-print-geraetewart',
          'printGeraetewart',
          'Gerätewartmitteilung drucken',
          d.printGeraetewart
        ) +
        '</div></div>';
    }
    if (hasMaengel) {
      html += checkboxMarkup(
        'fw-confirm-print-maengel',
        'printMaengel',
        'Mängelbericht drucken',
        d.printMaengel
      );
    }

    checkboxesEl.innerHTML = appendTestModeEmailSelect(html);
    checkboxesEl.hidden = false;
    if (hasGeraete) {
      bindEinsatzReleaseCheckboxInteractions(d);
    }
  }

  function renderAnwesenheitReleaseCheckboxes(defaults) {
    if (!checkboxesEl) {
      return;
    }
    var d = defaults || {};
    var hasMaengel = !!d.hasMaterialDamages;
    var hasGeraete = !!d.hasDeployedEquipment;
    activeCheckboxes = [
      {
        id: 'fw-confirm-print-report',
        name: 'printReport',
        label: 'Anwesenheitsliste drucken',
        checked: !!d.printReport
      }
    ];
    if (hasGeraete) {
      activeCheckboxes.push(
        {
          id: 'fw-confirm-create-geraetewart',
          name: 'createGeraetewart',
          label: 'Gerätewartmitteilung erstellen',
          checked: !!d.createGeraetewart
        },
        {
          id: 'fw-confirm-print-geraetewart',
          name: 'printGeraetewart',
          label: 'Gerätewartmitteilung drucken',
          checked: !!d.printGeraetewart
        }
      );
    }
    if (hasMaengel) {
      activeCheckboxes.push({
        id: 'fw-confirm-print-maengel',
        name: 'printMaengel',
        label: 'Mängelbericht drucken',
        checked: !!d.printMaengel
      });
    }

    var html = checkboxMarkup(
      'fw-confirm-print-report',
      'printReport',
      'Anwesenheitsliste drucken',
      d.printReport
    );
    if (hasGeraete) {
      html +=
        '<div class="confirm-dialog__checkbox-row">' +
        checkboxMarkup(
          'fw-confirm-create-geraetewart',
          'createGeraetewart',
          'Gerätewartmitteilung erstellen',
          d.createGeraetewart
        ) +
        '<div class="confirm-dialog__checkbox-dependent" id="fw-confirm-print-geraetewart-wrap" hidden>' +
        checkboxMarkup(
          'fw-confirm-print-geraetewart',
          'printGeraetewart',
          'Gerätewartmitteilung drucken',
          d.printGeraetewart
        ) +
        '</div></div>';
    }
    if (hasMaengel) {
      html += checkboxMarkup(
        'fw-confirm-print-maengel',
        'printMaengel',
        'Mängelbericht drucken',
        d.printMaengel
      );
    }

    checkboxesEl.innerHTML = appendTestModeEmailSelect(html);
    checkboxesEl.hidden = false;
    if (hasGeraete) {
      bindEinsatzReleaseCheckboxInteractions(d);
    }
  }

  function appendReleaseOptions(form, result, fieldNames) {
    if (!result || !result.ok) {
      return;
    }
    (fieldNames || EINSATZ_RELEASE_FIELD_NAMES).forEach(function (name) {
      form.querySelectorAll('input[name="' + name + '"]').forEach(function (el) {
        el.remove();
      });
      if (result[name]) {
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = 'true';
        form.appendChild(input);
      }
    });
    if (result.testModeEmailDelivery) {
      appendHiddenField(form, 'testModeEmailDelivery', result.testModeEmailDelivery);
    } else if (isTestMode()) {
      appendHiddenField(form, 'testModeEmailDelivery', 'NONE');
    }
  }

  function releaseValidationIssues(issues, options) {
    return new Promise(function (resolve) {
      var list = issues || [];
      var opts = options || {};
      ensureModal();
      titleEl.textContent = 'Freigabe nicht möglich';
      checkboxesEl.hidden = true;
      checkboxesEl.innerHTML = '';
      activeCheckboxes = [];
      messageEl.className = 'confirm-dialog__message confirm-dialog__message--issues';
      messageEl.innerHTML =
        '<p class="release-validation-intro">Bitte folgende Pflichtfelder ausfüllen:</p>';

      var listEl = document.createElement('ul');
      listEl.className = 'release-validation-issue-list';
      listEl.setAttribute('role', 'list');
      list.forEach(function (issue) {
        var item = document.createElement('li');
        item.className = 'release-validation-issue-item';
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'release-validation-issue-link';
        btn.textContent = issue.label;
        btn.addEventListener('click', function () {
          modalEl.hidden = true;
          modalEl.classList.remove('active', 'confirm-dialog--release-validation');
          document.body.classList.remove('modal-open');
          resolveFn = null;
          if (window.BerichteEinsatzRelease && opts.reportId && opts.unitId) {
            window.BerichteEinsatzRelease.storeIssuesAndNavigate(list, opts.reportId, opts.unitId, issue);
          }
          resolve(false);
        });
        item.appendChild(btn);
        listEl.appendChild(item);
      });
      messageEl.appendChild(listEl);

      modalEl.classList.add('confirm-dialog--release-validation');

      confirmBtn.textContent = 'Zum Bearbeiten';
      cancelBtn.textContent = 'Abbrechen';
      applyVariant('primary');
      resolveFn = function (confirmed) {
        if (confirmed && window.BerichteEinsatzRelease && opts.reportId && opts.unitId) {
          window.BerichteEinsatzRelease.storeIssuesAndNavigate(list, opts.reportId, opts.unitId);
          resolve('edit');
        } else {
          resolve(false);
        }
        resolveFn = null;
      };

      modalEl.hidden = false;
      modalEl.classList.add('active');
      document.body.classList.add('modal-open');
      window.setTimeout(function () {
        confirmBtn.focus();
      }, 0);
    });
  }

  function bindFormConfirms() {
    document.addEventListener(
      'submit',
      function (e) {
        var form = e.target.closest('form[data-confirm-einsatz-release]');
        if (form && form.dataset.confirmSubmitting !== 'true') {
          e.preventDefault();
          e.stopImmediatePropagation();
          var unitInput = form.querySelector('input[name="unit"]');
          var unitId = unitInput ? unitInput.value : '';
          var reportId = window.BerichteEinsatzRelease
            ? window.BerichteEinsatzRelease.parseReportIdFromAction(form.getAttribute('action'))
            : null;
          function proceedRelease(prep) {
            var defaults = releaseDefaultsFromElement(form);
            if (prep && prep.hasMaterialDamages) {
              defaults.hasMaterialDamages = true;
            }
            if (prep && prep.hasDeployedEquipment) {
              defaults.hasDeployedEquipment = true;
            }
            if (!defaults.hasDeployedEquipment) {
              defaults.createGeraetewart = false;
              defaults.printGeraetewart = false;
            }
            window.FwConfirm.releaseEinsatzbericht(defaults).then(function (result) {
              if (result && result.ok) {
                appendReleaseOptions(form, result, EINSATZ_RELEASE_FIELD_NAMES);
                form.dataset.confirmSubmitting = 'true';
                if (typeof form.requestSubmit === 'function') {
                  form.requestSubmit();
                } else {
                  form.submit();
                }
              }
            });
          }
          if (window.BerichteEinsatzRelease && reportId && unitId) {
            window.BerichteEinsatzRelease.ensureValidBeforeRelease(reportId, unitId).then(function (check) {
              if (check && check.ok) {
                proceedRelease(check);
              }
            });
          } else {
            proceedRelease({});
          }
          return;
        }

        form = e.target.closest('form[data-confirm-anwesenheit-release]');
        if (form && form.dataset.confirmSubmitting !== 'true') {
          e.preventDefault();
          e.stopImmediatePropagation();
          var unitInputAnw = form.querySelector('input[name="unit"]');
          var unitIdAnw = unitInputAnw ? unitInputAnw.value : '';
          var reportIdAnw = window.BerichteAnwesenheitRelease
            ? window.BerichteAnwesenheitRelease.parseReportIdFromAction(form.getAttribute('action'))
            : null;
          function proceedAnwesenheitRelease(prep) {
            var defaults = releaseDefaultsFromElement(form);
            if (prep && prep.hasMaterialDamages) {
              defaults.hasMaterialDamages = true;
            }
            if (prep && prep.hasDeployedEquipment) {
              defaults.hasDeployedEquipment = true;
            }
            if (!defaults.hasDeployedEquipment) {
              defaults.createGeraetewart = false;
              defaults.printGeraetewart = false;
            }
            window.FwConfirm.releaseAnwesenheitsliste(defaults).then(function (result) {
              if (result && result.ok) {
                var merged = Object.assign({}, result, prep || {});
                appendReleaseOptions(form, merged, EINSATZ_RELEASE_FIELD_NAMES.concat(['assignRemainingToWache']));
                form.dataset.confirmSubmitting = 'true';
                if (typeof form.requestSubmit === 'function') {
                  form.requestSubmit();
                } else {
                  form.submit();
                }
              }
            });
          }
          if (window.BerichteAnwesenheitRelease && reportIdAnw && unitIdAnw) {
            window.BerichteAnwesenheitRelease.prepareRelease(reportIdAnw, unitIdAnw).then(proceedAnwesenheitRelease);
          } else {
            proceedAnwesenheitRelease({ assignRemainingToWache: false });
          }
          return;
        }

        form = e.target.closest('form[data-testmode-email-prompt]');
        if (form && isTestMode() && form.dataset.confirmSubmitting !== 'true') {
          // Bereits gewählt (z. B. Speichern & Freigeben) — keine zweite Abfrage
          if (form.querySelector('input[name="testModeEmailDelivery"]')) {
            return;
          }
          e.preventDefault();
          e.stopImmediatePropagation();
          window.FwConfirm.askTestModeEmail().then(function (result) {
            if (result && result.ok) {
              appendHiddenField(form, 'testModeEmailDelivery', result.testModeEmailDelivery || 'NONE');
              form.dataset.confirmSubmitting = 'true';
              if (typeof form.requestSubmit === 'function') {
                form.requestSubmit();
              } else {
                form.submit();
              }
            }
          });
          return;
        }

        form = e.target.closest('form[data-confirm], form[data-confirm-message]');
        if (!form || form.dataset.confirmSubmitting === 'true') {
          return;
        }
        e.preventDefault();
        e.stopImmediatePropagation();
        var opts = optionsFromForm(form);
        if (isTestMode() && form.getAttribute('data-testmode-email') === 'true') {
          opts.emailSelect = true;
        }
        show(opts).then(function (result) {
          var ok = result === true || (result && result.ok);
          if (!ok) {
            return;
          }
          if (result && result.testModeEmailDelivery) {
            appendHiddenField(form, 'testModeEmailDelivery', result.testModeEmailDelivery);
          } else if (isTestMode() && form.getAttribute('data-testmode-email') === 'true') {
            appendHiddenField(form, 'testModeEmailDelivery', 'NONE');
          }
          form.dataset.confirmSubmitting = 'true';
          if (typeof form.requestSubmit === 'function') {
            form.requestSubmit();
          } else {
            form.submit();
          }
        });
      },
      true
    );
  }

  window.FwConfirm = {
    show: show,
    isTestMode: isTestMode,
    ask: function (message, title) {
      return show({ title: title || 'Bitte bestätigen', message: message || '' });
    },
    askTestModeEmail: function () {
      if (!isTestMode()) {
        return Promise.resolve({ ok: true, testModeEmailDelivery: 'NONE' });
      }
      return show({
        title: 'E-Mail im Testmodus',
        message:
          'Im Produktivbetrieb könnte bei dieser Aktion automatisch eine E-Mail versendet werden. ' +
          'Wie soll im Testmodus verfahren werden?',
        confirmLabel: 'Weiter',
        variant: 'primary',
        emailSelect: true
      }).then(function (result) {
        if (result === true) {
          return { ok: true, testModeEmailDelivery: 'NONE' };
        }
        if (result && result.ok) {
          return result;
        }
        return { ok: false };
      });
    },
    applyTestModeEmailExtra: function (extras, result) {
      var out = extras || {};
      if (result && result.testModeEmailDelivery) {
        out.testModeEmailDelivery = result.testModeEmailDelivery;
      } else if (isTestMode()) {
        out.testModeEmailDelivery = 'NONE';
      }
      return out;
    },
    releaseReport: function (reportLabel) {
      return show({
        title: (reportLabel || 'Bericht') + ' freigeben?',
        message:
          'Nach der Freigabe ist der Eintrag für die normale Bearbeitung gesperrt. ' +
          'Administratoren können weiterhin Änderungen vornehmen.',
        confirmLabel: 'Freigeben',
        variant: 'success',
        emailSelect: isTestMode()
      });
    },
    releaseValidationIssues: releaseValidationIssues,
    releaseEinsatzbericht: function (defaults) {
      return new Promise(function (resolve) {
        ensureModal();
        titleEl.textContent = 'Einsatzbericht freigeben?';
        messageEl.className = 'confirm-dialog__message';
        modalEl.classList.remove('confirm-dialog--release-validation');
        messageEl.textContent =
          'Nach der Freigabe ist der Bericht für die normale Bearbeitung gesperrt. ' +
          'Administratoren können weiterhin Änderungen vornehmen.';
        confirmBtn.textContent = 'Freigeben';
        cancelBtn.textContent = 'Abbrechen';
        applyVariant('success');
        emailSelectActive = false;
        renderEinsatzReleaseCheckboxes(defaults);
        resolveFn = resolve;
        modalEl.hidden = false;
        modalEl.classList.add('active');
        document.body.classList.add('modal-open');
        window.setTimeout(function () {
          confirmBtn.focus();
        }, 0);
      });
    },
    releaseAnwesenheitsliste: function (defaults) {
      return new Promise(function (resolve) {
        ensureModal();
        titleEl.textContent = 'Anwesenheitsliste freigeben?';
        messageEl.className = 'confirm-dialog__message';
        modalEl.classList.remove('confirm-dialog--release-validation');
        messageEl.textContent =
          'Nach der Freigabe ist die Liste für die normale Bearbeitung gesperrt. ' +
          'Administratoren können weiterhin Änderungen vornehmen.';
        confirmBtn.textContent = 'Freigeben';
        cancelBtn.textContent = 'Abbrechen';
        applyVariant('success');
        emailSelectActive = false;
        renderAnwesenheitReleaseCheckboxes(defaults);
        resolveFn = resolve;
        modalEl.hidden = false;
        modalEl.classList.add('active');
        document.body.classList.add('modal-open');
        window.setTimeout(function () {
          confirmBtn.focus();
        }, 0);
      });
    },
    archiveReport: function (reportLabel) {
      return show({
        title: (reportLabel || 'Bericht') + ' ins Archiv verschieben?',
        message: 'Der Eintrag wird ins Archiv verschoben und erscheint standardmäßig nicht mehr in der aktiven Liste.',
        confirmLabel: 'Ins Archiv verschieben',
        variant: 'primary',
        emailSelect: isTestMode()
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
    deleteTermin: function (options) {
      var opts = options || {};
      var checkboxes = [];
      if (opts.offerDeleteAttendance !== false) {
        checkboxes.push({
          id: 'fw-confirm-delete-attendance',
          name: 'deleteAttendance',
          label: 'Zugehörige Anwesenheitsliste ebenfalls löschen',
          checked: opts.deleteAttendance !== false
        });
      }
      return show({
        title: 'Termin löschen?',
        message: checkboxes.length
          ? 'Der Termin wird gelöscht. Optional kann die verknüpfte Anwesenheitsliste mitgelöscht werden.'
          : 'Termin wirklich löschen?',
        confirmLabel: 'Löschen',
        variant: 'danger',
        checkboxes: checkboxes
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
