(function () {
  'use strict';

  function switchTab(idx) {
    document.querySelectorAll('.incident-tab').forEach(function (btn) {
      btn.classList.toggle('tab-btn--active', Number(btn.dataset.tab) === idx);
    });
    document.querySelectorAll('.incident-tab-panel').forEach(function (panel) {
      var active = Number(panel.dataset.panel) === idx;
      panel.hidden = !active;
    });
  }

  function bindDamageAutoEnable(radioName, fieldIds) {
    function enableIfHasValues() {
      var hasValue = fieldIds.some(function (id) {
        var input = document.getElementById(id);
        return input && Number(input.value) > 0;
      });
      if (!hasValue) {
        return;
      }
      var yesRadio = document.querySelector('input[name="' + radioName + '"][value="true"]');
      if (yesRadio && !yesRadio.checked && !yesRadio.disabled) {
        yesRadio.checked = true;
      }
    }
    fieldIds.forEach(function (id) {
      var input = document.getElementById(id);
      if (!input || input.readOnly) {
        return;
      }
      input.addEventListener('input', enableIfHasValues);
      input.addEventListener('change', enableIfHasValues);
    });
  }

  function isAnwesenheitForm(scope) {
    var page = (scope || document).querySelector('[data-berichte-form="anwesenheit"]');
    return !!page;
  }

  var RELEASE_REQUIRED_FIELD_IDS = [
    'incidentDate',
    'alarmTime',
    'endTime',
    'stichwort',
    'alarmierungDurch',
    'location',
    'postalCode',
    'incidentCommander'
  ];

  function isReleaseFieldEmpty(field) {
    return !field || String(field.value || '').trim() === '';
  }

  function updateReleaseFieldEmptyState(field) {
    if (!field || field.readOnly || field.disabled) {
      field.classList.remove('release-field-empty');
      return;
    }
    field.classList.toggle('release-field-empty', isReleaseFieldEmpty(field));
  }

  function initReleaseRequiredMarkers(scope) {
    if ((scope || document).querySelector('.einsatzbericht-view-page')) {
      return;
    }
    RELEASE_REQUIRED_FIELD_IDS.forEach(function (fieldId) {
      var field = document.getElementById(fieldId);
      if (!field) {
        return;
      }
      updateReleaseFieldEmptyState(field);
      if (field.dataset.releaseEmptyBound === 'true') {
        return;
      }
      field.dataset.releaseEmptyBound = 'true';
      field.addEventListener('input', function () {
        updateReleaseFieldEmptyState(field);
      });
      field.addEventListener('change', function () {
        updateReleaseFieldEmptyState(field);
      });
    });
  }

  function focusReleaseField(anchorId, tabIndex) {
    if (tabIndex != null) {
      switchTab(tabIndex);
      if (window.BerichteKraefte && (tabIndex === 1 || tabIndex === 2)) {
        window.BerichteKraefte.onTabShow(tabIndex);
      }
    }
    if (!anchorId) {
      return;
    }
    window.setTimeout(function () {
      var el = document.getElementById(anchorId);
      if (!el) {
        return;
      }
      el.classList.add('release-field-highlight');
      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      var input = el.matches('input, textarea, select')
        ? el
        : el.querySelector('input:not([type="hidden"]), textarea, select');
      if (input && !input.readOnly && !input.disabled) {
        input.focus();
      }
    }, 120);
  }

  function initReleaseIssueHints() {
    if (document.querySelector('.einsatzbericht-view-page')) {
      return;
    }
    var issues = null;
    var storageKey = window.BerichteEinsatzRelease && window.BerichteEinsatzRelease.STORAGE_KEY;
    if (storageKey) {
      try {
        var raw = sessionStorage.getItem(storageKey);
        if (raw) {
          issues = JSON.parse(raw);
          sessionStorage.removeItem(storageKey);
        }
      } catch (e) {
        issues = null;
      }
    }
    var params = new URLSearchParams(window.location.search);
    var focus = params.get('focus');
    var tab = params.get('tab');
    if (!issues || !issues.length) {
      if (focus || tab) {
        focusReleaseField(focus, tab != null ? Number(tab) : null);
      }
      return;
    }
    var form = document.getElementById('einsatzbericht-form');
    if (!form) {
      return;
    }
    var banner = document.createElement('div');
    banner.className = 'release-validation-banner';
    banner.setAttribute('role', 'alert');
    var title = document.createElement('strong');
    title.textContent = 'Freigabe noch nicht möglich: ';
    banner.appendChild(title);
    banner.appendChild(document.createTextNode('Bitte folgende Pflichtfelder ausfüllen: '));
    issues.forEach(function (issue, index) {
      if (index > 0) {
        banner.appendChild(document.createTextNode(', '));
      }
      var link = document.createElement('button');
      link.type = 'button';
      link.className = 'release-validation-banner__link';
      link.textContent = issue.label;
      link.addEventListener('click', function () {
        focusReleaseField(issue.anchorId, issue.tabIndex);
      });
      banner.appendChild(link);
    });
    form.parentNode.insertBefore(banner, form);
    issues.forEach(function (issue) {
      if (issue.anchorId) {
        var target = document.getElementById(issue.anchorId);
        if (target) {
          target.classList.add('release-field-highlight');
        }
      }
    });
    var first = issues[0];
    focusReleaseField(first.anchorId, first.tabIndex);
  }

  function init(root) {
    var scope = root || document;
    var anwesenheit = isAnwesenheitForm(scope);
    scope.querySelectorAll('.incident-tab').forEach(function (btn) {
      if (btn.dataset.bound === 'true') {
        return;
      }
      btn.dataset.bound = 'true';
      btn.addEventListener('click', function () {
        var idx = Number(btn.dataset.tab);
        switchTab(idx);
        if (window.BerichteKraefte && (idx === 1 || idx === 2)) {
          window.BerichteKraefte.onTabShow(idx);
        }
        if (idx === 3 && window.BerichteGeraete) {
          window.BerichteGeraete.onTabShow();
        }
        if (!anwesenheit && idx === 4 && window.BerichteSchaeden) {
          window.BerichteSchaeden.init(scope);
        }
        var attachmentsTab = anwesenheit ? 5 : 6;
        if (idx === attachmentsTab && window.BerichteAnhaenge) {
          window.BerichteAnhaenge.load();
        }
      });
    });

    if (!anwesenheit && window.BerichteSchaeden) {
      window.BerichteSchaeden.init(scope);
    }

    if (!anwesenheit) {
      bindDamageAutoEnable('personDamagesEnabled', [
        'personsRescued',
        'personsInjured',
        'personsRecovered',
        'personsDead'
      ]);
      bindDamageAutoEnable('animalDamagesEnabled', [
        'animalsRescued',
        'animalsInjured',
        'animalsRecovered',
        'animalsDead'
      ]);
    }

    var dateInput = document.getElementById('incidentDate');
    var numberInput = document.getElementById('incidentNumber');
    if (dateInput && numberInput && numberInput.dataset.autoNumber === 'true') {
      function refreshSuggestedNumber() {
        if (!dateInput.value || !numberInput.dataset.unitId) {
          return;
        }
        var apiBase = window.BerichteApiBase ? window.BerichteApiBase.path() : '/berichte/einsatzberichte';
        var url = apiBase + '/suggest-number?unit='
          + encodeURIComponent(numberInput.dataset.unitId)
          + '&date=' + encodeURIComponent(dateInput.value);
        fetch(url, { credentials: 'same-origin' })
          .then(function (res) {
            if (!res.ok) {
              throw new Error('Vorschlag fehlgeschlagen');
            }
            return res.text();
          })
          .then(function (suggested) {
            numberInput.value = suggested;
          })
          .catch(function () {
            numberInput.value = dateInput.value + '-01';
          });
      }
      dateInput.addEventListener('change', refreshSuggestedNumber);
    }

    if (anwesenheit && window.BerichteAnwesenheitAddress) {
      window.BerichteAnwesenheitAddress.init(scope);
    }

    switchTab(0);
    if (!anwesenheit) {
      initReleaseRequiredMarkers(scope);
      initReleaseIssueHints();
    }
  }

  window.BerichteEinsatzForm = { init: init, switchTab: switchTab, focusReleaseField: focusReleaseField };

  document.addEventListener('DOMContentLoaded', function () {
    if (document.querySelector('.einsatzbericht-form-page')) {
      init(document);
    }
  });
})();
