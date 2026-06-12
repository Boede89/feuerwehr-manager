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
  }

  window.BerichteEinsatzForm = { init: init };

  document.addEventListener('DOMContentLoaded', function () {
    if (document.querySelector('.einsatzbericht-form-page')) {
      init(document);
    }
  });
})();
