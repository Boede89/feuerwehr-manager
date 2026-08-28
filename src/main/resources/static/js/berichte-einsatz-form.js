(function () {
  'use strict';

  function parseIncidentTabIndex(value) {
    if (value == null || value === '') {
      return null;
    }
    if (!/^\d+$/.test(String(value))) {
      return null;
    }
    var idx = Number(value);
    return Number.isFinite(idx) && idx >= 0 && idx <= 6 ? idx : null;
  }

  function switchTab(idx, scope) {
    if (!Number.isFinite(idx)) {
      return;
    }
    var root = scope || document;
    root.querySelectorAll('.incident-tab').forEach(function (btn) {
      btn.classList.toggle('tab-btn--active', Number(btn.dataset.tab) === idx);
    });
    root.querySelectorAll('.incident-tab-panel').forEach(function (panel) {
      panel.hidden = Number(panel.dataset.panel) !== idx;
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

  function focusReleaseField(anchorId, tabIndex, scope) {
    var parsedTabIndex = parseIncidentTabIndex(tabIndex);
    if (parsedTabIndex != null) {
      switchTab(parsedTabIndex, scope);
      if (window.BerichteKraefte && (parsedTabIndex === 1 || parsedTabIndex === 2)) {
        window.BerichteKraefte.onTabShow(parsedTabIndex);
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
    var tabIndex = parseIncidentTabIndex(params.get('tab'));
    if (!issues || !issues.length) {
      if (focus || tabIndex != null) {
        focusReleaseField(focus, tabIndex);
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

  function initIncidentEndDateSync() {
    var incidentDateInput = document.getElementById('incidentDate');
    var endDateInput = document.getElementById('endDate');
    var alarmTimeInput = document.getElementById('alarmTime');
    var endTimeInput = document.getElementById('endTime');
    var overnightHint = document.getElementById('incident-end-overnight-hint');
    if (!incidentDateInput || !endDateInput || endDateInput.readOnly) {
      return;
    }

    function addDays(dateValue, days) {
      var parts = String(dateValue).slice(0, 10).split('-');
      if (parts.length !== 3) {
        return null;
      }
      var date = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
      date.setDate(date.getDate() + days);
      return date.getFullYear()
        + '-' + String(date.getMonth() + 1).padStart(2, '0')
        + '-' + String(date.getDate()).padStart(2, '0');
    }

    function updateOvernightHint() {
      if (!overnightHint) {
        return;
      }
      var overnight = !!(incidentDateInput.value && endDateInput.value
        && endDateInput.value > incidentDateInput.value);
      overnightHint.hidden = !overnight;
    }

    function applyOvernightEndDate() {
      if (!incidentDateInput.value || !alarmTimeInput || !endTimeInput) {
        updateOvernightHint();
        return;
      }
      if (!alarmTimeInput.value || !endTimeInput.value) {
        updateOvernightHint();
        return;
      }
      if (endTimeInput.value < alarmTimeInput.value) {
        var nextDay = addDays(incidentDateInput.value, 1);
        if (nextDay) {
          endDateInput.value = nextDay;
          endDateInput.dataset.userEdited = 'false';
        }
      } else if (endDateInput.dataset.userEdited !== 'true') {
        endDateInput.value = incidentDateInput.value;
      }
      updateOvernightHint();
    }

    incidentDateInput.addEventListener('change', function () {
      if (endDateInput.dataset.userEdited !== 'true') {
        endDateInput.value = incidentDateInput.value;
      }
      applyOvernightEndDate();
    });
    endDateInput.addEventListener('change', function () {
      endDateInput.dataset.userEdited = 'true';
      updateOvernightHint();
    });
    if (alarmTimeInput) {
      alarmTimeInput.addEventListener('change', applyOvernightEndDate);
    }
    if (endTimeInput) {
      endTimeInput.addEventListener('change', applyOvernightEndDate);
      endTimeInput.addEventListener('input', applyOvernightEndDate);
    }
    if (!endDateInput.value && incidentDateInput.value) {
      endDateInput.value = incidentDateInput.value;
    }
    applyOvernightEndDate();
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
        if (window.BerichteGeraete && typeof window.BerichteGeraete.sync === 'function') {
          window.BerichteGeraete.sync();
        }
        switchTab(idx, scope);
        if (window.BerichteKraefte && (idx === 1 || idx === 2)) {
          window.BerichteKraefte.onTabShow(idx);
        }
        if (idx === 3 && window.BerichteGeraete) {
          window.BerichteGeraete.onTabShow();
        }
        if (idx === 4 && window.BerichteSchaeden) {
          window.BerichteSchaeden.init(scope);
        }
        if (anwesenheit && idx === 4 && window.BerichteAnwesenheitCrewInjury) {
          window.BerichteAnwesenheitCrewInjury.init(scope);
        }
        var attachmentsTab = anwesenheit ? 5 : 6;
        if (idx === attachmentsTab && window.BerichteAnhaenge) {
          window.BerichteAnhaenge.load();
        }
      });
    });

    if (window.BerichteSchaeden) {
      window.BerichteSchaeden.init(scope);
    }
    if (anwesenheit && window.BerichteAnwesenheitCrewInjury) {
      window.BerichteAnwesenheitCrewInjury.init(scope);
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
    if (!anwesenheit) {
      initIncidentEndDateSync();
    }
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

    switchTab(0, scope);
    if (!anwesenheit) {
      initReleaseRequiredMarkers(scope);
      if (scope.querySelector('#einsatzbericht-form')) {
        initReleaseIssueHints();
      }
    }
  }

  window.BerichteEinsatzForm = { init: init, switchTab: switchTab, focusReleaseField: focusReleaseField };

  document.addEventListener('DOMContentLoaded', function () {
    if (document.querySelector('.einsatzbericht-form-page')) {
      init(document);
    }
  });
})();
