(function () {
  'use strict';

  var draggedChip = null;

  function computeStaerke(chips) {
    var zf = 0;
    var gf = 0;
    var m = 0;
    chips.forEach(function (chip) {
      var tier = chip.dataset.qualTier;
      if (tier === 'ZF') {
        zf++;
      } else if (tier === 'GF') {
        gf++;
      } else {
        m++;
      }
    });
    var sum = zf + gf + m;
    return zf + '/' + gf + '/' + m + '/' + sum;
  }

  function updateVehicleStaerke(vehicleCard) {
    var dropzone = vehicleCard.querySelector('.incident-vehicle-dropzone');
    var badge = vehicleCard.querySelector('[data-staerke-badge]');
    if (!dropzone || !badge) {
      return;
    }
    var chips = dropzone.querySelectorAll('.incident-crew-chip');
    badge.textContent = computeStaerke(Array.from(chips));
    var hint = vehicleCard.querySelector('.incident-vehicle-dropzone-hint');
    if (hint) {
      hint.hidden = chips.length > 0;
    }
  }

  function updatePoolEmptyState(pool) {
    if (!pool) {
      return;
    }
    var poolType = pool.dataset.pool;
    var emptyHint = document.getElementById(poolType + '-pool-empty');
    if (!emptyHint) {
      return;
    }
    emptyHint.hidden = pool.querySelectorAll('.incident-crew-chip').length > 0;
  }

  function findChipByPersonId(personId) {
    return document.querySelector('.incident-crew-chip[data-person-id="' + personId + '"]');
  }

  function removePersonFromBoard(personId, exceptChip) {
    document.querySelectorAll('.incident-crew-chip[data-person-id="' + personId + '"]').forEach(function (chip) {
      if (chip !== exceptChip) {
        chip.remove();
      }
    });
  }

  function syncHiddenJson() {
    var hidden = document.getElementById('crewAssignmentsJson');
    if (!hidden) {
      return;
    }
    var assignments = [];
    document.querySelectorAll('.incident-vehicle-card').forEach(function (card) {
      var vehicleId = Number(card.dataset.vehicleId);
      var personIds = Array.from(card.querySelectorAll('.incident-vehicle-dropzone .incident-crew-chip'))
        .map(function (chip) {
          return Number(chip.dataset.personId);
        })
        .filter(function (id) {
          return !isNaN(id);
        });
      assignments.push({ vehicleId: vehicleId, personIds: personIds });
    });
    hidden.value = JSON.stringify(assignments);
  }

  function refreshBoard() {
    document.querySelectorAll('.incident-vehicle-card').forEach(updateVehicleStaerke);
    updatePoolEmptyState(document.getElementById('manual-person-pool'));
    updatePoolEmptyState(document.getElementById('divera-person-pool'));
    syncHiddenJson();
  }

  function onDragStart(e) {
    var chip = e.target.closest('.incident-crew-chip');
    if (!chip || chip.getAttribute('draggable') !== 'true') {
      return;
    }
    draggedChip = chip;
    chip.classList.add('incident-crew-chip--dragging');
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', chip.dataset.personId || '');
  }

  function onDragEnd() {
    if (draggedChip) {
      draggedChip.classList.remove('incident-crew-chip--dragging');
    }
    draggedChip = null;
    document.querySelectorAll('.incident-vehicle-dropzone--active').forEach(function (zone) {
      zone.classList.remove('incident-vehicle-dropzone--active');
    });
  }

  function onDragOverDropzone(e) {
    if (!draggedChip) {
      return;
    }
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    var zone = e.currentTarget;
    zone.classList.add('incident-vehicle-dropzone--active');
  }

  function onDragLeaveDropzone(e) {
    var zone = e.currentTarget;
    if (!zone.contains(e.relatedTarget)) {
      zone.classList.remove('incident-vehicle-dropzone--active');
    }
  }

  function onDropVehicle(e) {
    e.preventDefault();
    var zone = e.currentTarget;
    zone.classList.remove('incident-vehicle-dropzone--active');
    if (!draggedChip) {
      return;
    }
    var personId = draggedChip.dataset.personId;
    removePersonFromBoard(personId, draggedChip);
    zone.appendChild(draggedChip);
    refreshBoard();
  }

  function chipSortOrder(chip) {
    var order = Number(chip.dataset.sortOrder);
    return isNaN(order) ? 999999 : order;
  }

  function insertChipSorted(pool, chip) {
    var order = chipSortOrder(chip);
    var chips = Array.from(pool.querySelectorAll('.incident-crew-chip'));
    var insertBefore = null;
    for (var i = 0; i < chips.length; i++) {
      if (chips[i] === chip) {
        continue;
      }
      if (chipSortOrder(chips[i]) > order) {
        insertBefore = chips[i];
        break;
      }
    }
    if (insertBefore) {
      pool.insertBefore(chip, insertBefore);
    } else {
      pool.appendChild(chip);
    }
  }

  function filterManualPool(query) {
    var pool = document.getElementById('manual-person-pool');
    if (!pool) {
      return;
    }
    var q = (query || '').trim().toLocaleLowerCase('de');
    pool.querySelectorAll('.incident-crew-chip').forEach(function (chip) {
      var name = (chip.dataset.personName || chip.textContent || '').trim().toLocaleLowerCase('de');
      chip.hidden = q.length > 0 && name.indexOf(q) === -1;
    });
  }

  function onDropManualPool(e) {
    e.preventDefault();
    if (!draggedChip || draggedChip.closest('#manual-person-pool')) {
      return;
    }
    var pool = document.getElementById('manual-person-pool');
    if (!pool) {
      return;
    }
    removePersonFromBoard(draggedChip.dataset.personId, draggedChip);
    insertChipSorted(pool, draggedChip);
    var searchEl = document.getElementById('manual-person-search');
    filterManualPool(searchEl ? searchEl.value : '');
    refreshBoard();
  }

  function bindBoard() {
    var board = document.getElementById('incident-kraefte-board');
    if (!board) {
      return;
    }

    board.addEventListener('dragstart', onDragStart);
    board.addEventListener('dragend', onDragEnd);

    board.querySelectorAll('.incident-vehicle-dropzone').forEach(function (zone) {
      zone.addEventListener('dragover', onDragOverDropzone);
      zone.addEventListener('dragleave', onDragLeaveDropzone);
      zone.addEventListener('drop', onDropVehicle);
    });

    var manualPool = document.getElementById('manual-person-pool');
    if (manualPool) {
      manualPool.addEventListener('dragover', function (e) {
        if (draggedChip) {
          e.preventDefault();
        }
      });
      manualPool.addEventListener('drop', onDropManualPool);
    }

    var manualSearch = document.getElementById('manual-person-search');
    if (manualSearch) {
      manualSearch.addEventListener('input', function () {
        filterManualPool(manualSearch.value);
      });
    }

    var form = document.getElementById('einsatzbericht-form');
    if (form) {
      form.addEventListener('submit', function () {
        syncHiddenJson();
      });
    }

    refreshBoard();
  }

  document.addEventListener('DOMContentLoaded', bindBoard);
})();
