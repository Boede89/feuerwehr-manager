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
      if (result && activeCheckboxes.length) {
        resolveFn(collectCheckboxValues());
      } else if (activeCheckboxes.length) {
        resolveFn({ ok: false });
      } else {
        resolveFn(!!result);
      }
      resolveFn = null;
    }
    activeCheckboxes = [];
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
      renderCheckboxes(opts.checkboxes);
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
    activeCheckboxes = [
      {
        id: 'fw-confirm-print-report',
        name: 'printReport',
        label: 'Einsatzbericht drucken',
        checked: !!d.printReport
      },
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
    ];
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
    if (hasMaengel) {
      html += checkboxMarkup(
        'fw-confirm-print-maengel',
        'printMaengel',
        'Mängelbericht drucken',
        d.printMaengel
      );
    }

    checkboxesEl.innerHTML = html;
    checkboxesEl.hidden = false;
    bindEinsatzReleaseCheckboxInteractions(d);
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

    checkboxesEl.innerHTML = html;
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
          function proceedRelease() {
            window.FwConfirm.releaseEinsatzbericht(releaseDefaultsFromElement(form)).then(function (result) {
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
                proceedRelease();
              }
            });
          } else {
            proceedRelease();
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

        form = e.target.closest('form[data-confirm], form[data-confirm-message]');
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
