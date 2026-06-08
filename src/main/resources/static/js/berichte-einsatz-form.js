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

  function syncIncidentTypeLabel() {
    var select = document.getElementById('incidentTypeKey');
    var hidden = document.getElementById('incidentTypeLabel');
    if (!select || !hidden) return;
    var option = select.options[select.selectedIndex];
    hidden.value = option ? option.text : '';
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.incident-tab').forEach(function (btn) {
      btn.addEventListener('click', function () {
        switchTab(Number(btn.dataset.tab));
      });
    });

    var typeSelect = document.getElementById('incidentTypeKey');
    if (typeSelect) {
      typeSelect.addEventListener('change', syncIncidentTypeLabel);
      syncIncidentTypeLabel();
    }

    switchTab(0);
  });
})();
