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

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.incident-tab').forEach(function (btn) {
      btn.addEventListener('click', function () {
        switchTab(Number(btn.dataset.tab));
      });
    });

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

    var dateInput = document.getElementById('incidentDate');
    var numberInput = document.getElementById('incidentNumber');
    if (dateInput && numberInput && numberInput.dataset.autoNumber === 'true') {
      dateInput.addEventListener('change', function () {
        if (dateInput.value) {
          numberInput.value = dateInput.value + '-01';
        }
      });
    }

    switchTab(0);
  });
})();
