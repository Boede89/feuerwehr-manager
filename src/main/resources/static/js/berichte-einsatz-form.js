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

  function bindDamageToggle(radioName, fieldsId) {
    var fields = document.getElementById(fieldsId);
    if (!fields) {
      return;
    }
    function sync() {
      var selected = document.querySelector('input[name="' + radioName + '"]:checked');
      var enabled = selected && selected.value === 'true';
      fields.hidden = !enabled;
    }
    document.querySelectorAll('input[name="' + radioName + '"]').forEach(function (radio) {
      radio.addEventListener('change', sync);
    });
    sync();
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.incident-tab').forEach(function (btn) {
      btn.addEventListener('click', function () {
        switchTab(Number(btn.dataset.tab));
      });
    });

    bindDamageToggle('personDamagesEnabled', 'person-damage-fields');
    bindDamageToggle('animalDamagesEnabled', 'animal-damage-fields');

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
