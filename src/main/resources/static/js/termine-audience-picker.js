(function (global) {
  'use strict';

  function createTermineAudiencePicker(config) {
    var prefix = config.prefix;
    var groupCbClass = prefix + '-audience-group-cb';
    var personCbClass = prefix + '-audience-person-cb';
    var groupsSnapshot = null;
    var personsSnapshot = null;

    function groupSelector(value) {
      return '.' + groupCbClass + '[value="' + value + '"]';
    }

    function personSelector(value) {
      return '.' + personCbClass + '[value="' + value + '"]';
    }

    function displayName(cb) {
      if (!cb) {
        return '';
      }
      var fromAttr = cb.getAttribute('data-display-name');
      if (fromAttr) {
        return fromAttr.trim();
      }
      var label = cb.closest('.user-picker__item');
      if (!label) {
        return '';
      }
      var nameEl = label.querySelector('.user-picker__name');
      return nameEl ? nameEl.textContent.trim() : '';
    }

    function syncSummary(kind) {
      var isGroups = kind === 'groups';
      var cbClass = isGroups ? groupCbClass : personCbClass;
      var summary = document.getElementById(prefix + '-' + kind + '-summary');
      var empty = document.getElementById(prefix + '-' + kind + '-empty');
      var count = document.getElementById(prefix + '-' + kind + '-count');
      var checked = document.querySelectorAll('.' + cbClass + ':checked');
      var names = [];
      checked.forEach(function (cb) {
        var name = displayName(cb);
        if (name) {
          names.push(name);
        }
      });
      if (count) {
        count.textContent = String(names.length);
      }
      if (summary) {
        summary.innerHTML = '';
        names.forEach(function (name) {
          var chip = document.createElement('span');
          chip.className = 'termine-ausbilder-chip';
          chip.textContent = name;
          summary.appendChild(chip);
        });
        summary.hidden = names.length === 0;
      }
      if (empty) {
        empty.hidden = names.length > 0;
      }
    }

    function syncAllSummaries() {
      syncSummary('groups');
      syncSummary('persons');
      syncAudienceAllState();
    }

    function getAudienceAllCheckbox() {
      return document.getElementById(prefix + '-audience-all');
    }

    function getAudiencePick() {
      return document.getElementById(prefix + '-audience-pick');
    }

    function hasAudienceSelection() {
      return selectedIds('groups').length > 0 || selectedIds('persons').length > 0;
    }

    function syncAudienceAllState() {
      var audienceAll = getAudienceAllCheckbox();
      var pick = getAudiencePick();
      if (!audienceAll) {
        return;
      }
      if (hasAudienceSelection()) {
        audienceAll.checked = false;
      }
      if (pick) {
        pick.hidden = audienceAll.checked;
      }
    }

    function resetCheckboxes(kind) {
      var cbClass = kind === 'groups' ? groupCbClass : personCbClass;
      document.querySelectorAll('.' + cbClass).forEach(function (cb) {
        cb.checked = false;
      });
      syncSummary(kind);
    }

    function resetAll() {
      resetCheckboxes('groups');
      resetCheckboxes('persons');
    }

    function setFromCsv(kind, idsCsv) {
      resetCheckboxes(kind);
      if (!idsCsv) {
        return;
      }
      var cbClass = kind === 'groups' ? groupCbClass : personCbClass;
      idsCsv.split(',').forEach(function (rawId) {
        var id = rawId.trim();
        if (!id) {
          return;
        }
        var cb = document.querySelector('.' + cbClass + '[value="' + id + '"]');
        if (cb) {
          cb.checked = true;
        }
      });
      syncSummary(kind);
      syncAudienceAllState();
    }

    function snapshot(kind) {
      var cbClass = kind === 'groups' ? groupCbClass : personCbClass;
      return Array.from(document.querySelectorAll('.' + cbClass + ':checked')).map(function (cb) {
        return cb.value;
      });
    }

    function restore(kind, ids) {
      var cbClass = kind === 'groups' ? groupCbClass : personCbClass;
      document.querySelectorAll('.' + cbClass).forEach(function (cb) {
        cb.checked = false;
      });
      (ids || []).forEach(function (id) {
        var cb = document.querySelector('.' + cbClass + '[value="' + id + '"]');
        if (cb) {
          cb.checked = true;
        }
      });
      syncSummary(kind);
      syncAudienceAllState();
    }

    function resetSearch(kind) {
      var search = document.getElementById(prefix + '-' + kind + '-search');
      var picker = document.getElementById(prefix + '-' + kind + '-picker');
      var emptyHint = document.getElementById(prefix + '-' + kind + '-search-empty');
      if (!search || !picker) {
        return;
      }
      search.value = '';
      picker.querySelectorAll('.user-picker__item').forEach(function (item) {
        item.style.display = '';
      });
      if (emptyHint) {
        emptyHint.hidden = true;
      }
    }

    function openPicker(kind) {
      var overlay = document.getElementById('modal-' + prefix + '-' + kind);
      if (!overlay) {
        return;
      }
      if (kind === 'groups') {
        groupsSnapshot = snapshot('groups');
      } else {
        personsSnapshot = snapshot('persons');
      }
      resetSearch(kind);
      overlay.classList.add('active');
      document.body.classList.add('modal-open');
      var search = document.getElementById(prefix + '-' + kind + '-search');
      if (search) {
        window.setTimeout(function () {
          search.focus();
        }, 50);
      }
    }

    function closePicker(kind) {
      var overlay = document.getElementById('modal-' + prefix + '-' + kind);
      if (!overlay) {
        return;
      }
      overlay.classList.remove('active');
      if (!document.querySelector('.modal-overlay.active')) {
        document.body.classList.remove('modal-open');
      }
    }

    function applyPicker(kind) {
      if (kind === 'groups') {
        groupsSnapshot = null;
      } else {
        personsSnapshot = null;
      }
      syncSummary(kind);
      syncAudienceAllState();
      closePicker(kind);
    }

    function cancelPicker(kind) {
      if (kind === 'groups') {
        if (groupsSnapshot) {
          restore('groups', groupsSnapshot);
          groupsSnapshot = null;
        }
      } else if (personsSnapshot) {
        restore('persons', personsSnapshot);
        personsSnapshot = null;
      }
      closePicker(kind);
    }

    function cancelAllPickers() {
      cancelPicker('groups');
      cancelPicker('persons');
    }

    function selectedIds(kind) {
      var cbClass = kind === 'groups' ? groupCbClass : personCbClass;
      return Array.from(document.querySelectorAll('.' + cbClass + ':checked'))
        .map(function (cb) {
          return Number(cb.value);
        })
        .filter(function (id) {
          return Number.isFinite(id) && id > 0;
        });
    }

    function bindSearch(kind) {
      var search = document.getElementById(prefix + '-' + kind + '-search');
      var picker = document.getElementById(prefix + '-' + kind + '-picker');
      var emptyHint = document.getElementById(prefix + '-' + kind + '-search-empty');
      if (!search || !picker) {
        return;
      }
      search.addEventListener('input', function () {
        var query = search.value.trim().toLowerCase();
        var visible = 0;
        picker.querySelectorAll('.user-picker__item').forEach(function (item) {
          var haystack = (item.getAttribute('data-search') || '').toLowerCase();
          var match = !query || haystack.indexOf(query) !== -1;
          item.style.display = match ? '' : 'none';
          if (match) {
            visible++;
          }
        });
        if (emptyHint) {
          emptyHint.hidden = visible > 0;
        }
      });
    }

    function bind() {
      var groupsOpen = document.getElementById(prefix + '-groups-open-btn');
      var personsOpen = document.getElementById(prefix + '-persons-open-btn');
      if (groupsOpen) {
        groupsOpen.addEventListener('click', function () {
          openPicker('groups');
        });
      }
      if (personsOpen) {
        personsOpen.addEventListener('click', function () {
          openPicker('persons');
        });
      }
      var audienceAll = getAudienceAllCheckbox();
      if (audienceAll) {
        audienceAll.addEventListener('change', function () {
          if (audienceAll.checked) {
            resetAll();
          }
          syncAudienceAllState();
        });
      }

      ['groups', 'persons'].forEach(function (kind) {
        var applyBtn = document.getElementById(prefix + '-' + kind + '-apply');
        if (applyBtn) {
          applyBtn.addEventListener('click', function () {
            applyPicker(kind);
          });
        }
        document.querySelectorAll('[data-close-' + prefix + '-' + kind + '-modal]').forEach(function (btn) {
          btn.addEventListener('click', function () {
            cancelPicker(kind);
          });
        });
        // Schließen nur über Buttons / ✕, nicht per Hintergrundklick.
        bindSearch(kind);
      });
      syncAllSummaries();
    }

    return {
      bind: bind,
      reset: resetAll,
      setGroupsFromCsv: function (idsCsv) {
        setFromCsv('groups', idsCsv);
      },
      setPersonsFromCsv: function (idsCsv) {
        setFromCsv('persons', idsCsv);
      },
      getGroupIds: function () {
        return selectedIds('groups');
      },
      getPersonIds: function () {
        return selectedIds('persons');
      },
      cancelAllPickers: cancelAllPickers
    };
  }

  global.createTermineAudiencePicker = createTermineAudiencePicker;
})(window);
