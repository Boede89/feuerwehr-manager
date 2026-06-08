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

  function initPickerSearch(searchSelector, emptySelector) {
    document.querySelectorAll(searchSelector).forEach(function (search) {
      var container = search.closest('.incident-section-block') || search.parentElement;
      var picker = container.querySelector('.user-picker');
      var emptyHint = container.querySelector(emptySelector);
      if (!picker) return;

      function filter() {
        var q = (search.value || '').trim().toLowerCase();
        var visible = 0;
        picker.querySelectorAll('.user-picker__item').forEach(function (item) {
          var hay = (item.dataset.search || '').toLowerCase();
          var show = !q || hay.indexOf(q) !== -1;
          item.hidden = !show;
          if (show) visible++;
        });
        if (emptyHint) {
          emptyHint.hidden = visible > 0 || !q;
        }
      }

      search.addEventListener('input', filter);
      filter();
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.incident-tab').forEach(function (btn) {
      btn.addEventListener('click', function () {
        switchTab(Number(btn.dataset.tab));
      });
    });

    var dateInput = document.getElementById('incidentDate');
    var numberInput = document.getElementById('incidentNumber');
    if (dateInput && numberInput && numberInput.dataset.autoNumber === 'true') {
      dateInput.addEventListener('change', function () {
        if (dateInput.value) {
          numberInput.value = dateInput.value + '-01';
        }
      });
    }

    initPickerSearch('.berichte-person-search', '.berichte-person-search-empty');
    initPickerSearch('.berichte-vehicle-search', '.berichte-vehicle-search-empty');

    switchTab(0);
  });
})();
