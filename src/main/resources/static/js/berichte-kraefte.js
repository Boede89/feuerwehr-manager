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

  function activeReserveTab() {
    var active = document.querySelector('.incident-reserve-tab--active');
    return active ? active.dataset.reserveTab : 'divera';
  }

  function poolForTab(tab) {
    return document.getElementById(tab + '-person-pool');
  }

  function updatePoolCounts() {
    ['manual', 'divera'].forEach(function (tab) {
      var pool = poolForTab(tab);
      var countEl = document.getElementById(tab + '-pool-count');
      if (!pool || !countEl) {
        return;
      }
      countEl.textContent = String(pool.querySelectorAll('.incident-crew-chip').length);
    });
  }

  function updateActiveEmptyHint() {
    ['manual', 'divera'].forEach(function (tab) {
      var pool = poolForTab(tab);
      var emptyHint = document.getElementById(tab + '-pool-empty');
      if (!pool || !emptyHint) {
        return;
      }
      var hasChips = pool.querySelectorAll('.incident-crew-chip').length > 0;
      emptyHint.hidden = tab !== activeReserveTab() || hasChips;
    });
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
    updatePoolCounts();
    updateActiveEmptyHint();
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

  function filterReservePool(query) {
    var pool = poolForTab(activeReserveTab());
    if (!pool) {
      return;
    }
    var q = (query || '').trim().toLocaleLowerCase('de');
    pool.querySelectorAll('.incident-crew-chip').forEach(function (chip) {
      var name = (chip.dataset.personName || chip.textContent || '').trim().toLocaleLowerCase('de');
      chip.hidden = q.length > 0 && name.indexOf(q) === -1;
    });
  }

  function switchReserveTab(tab) {
    document.querySelectorAll('.incident-reserve-tab').forEach(function (btn) {
      var isActive = btn.dataset.reserveTab === tab;
      btn.classList.toggle('incident-reserve-tab--active', isActive);
      btn.setAttribute('aria-selected', isActive ? 'true' : 'false');
    });
    ['manual', 'divera'].forEach(function (poolTab) {
      var pool = poolForTab(poolTab);
      if (!pool) {
        return;
      }
      pool.hidden = poolTab !== tab;
      pool.classList.toggle('incident-person-pool--active', poolTab === tab);
    });
    var searchEl = document.getElementById('reserve-person-search');
    if (searchEl) {
      filterReservePool(searchEl.value);
    }
    updateActiveEmptyHint();
  }

  function homePoolForChip(chip) {
    if (chip.classList.contains('incident-crew-chip--divera')) {
      return document.getElementById('divera-person-pool');
    }
    return document.getElementById('manual-person-pool');
  }

  function onDropPersonPool(e) {
    e.preventDefault();
    var pool = e.currentTarget;
    if (!draggedChip || draggedChip.parentElement === pool) {
      return;
    }
    if (homePoolForChip(draggedChip) !== pool) {
      return;
    }
    removePersonFromBoard(draggedChip.dataset.personId, draggedChip);
    insertChipSorted(pool, draggedChip);
    var searchEl = document.getElementById('reserve-person-search');
    if (searchEl) {
      filterReservePool(searchEl.value);
    }
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

    ['manual-person-pool', 'divera-person-pool'].forEach(function (poolId) {
      var pool = document.getElementById(poolId);
      if (!pool) {
        return;
      }
      pool.addEventListener('dragover', function (e) {
        if (draggedChip && homePoolForChip(draggedChip) === pool) {
          e.preventDefault();
        }
      });
      pool.addEventListener('drop', onDropPersonPool);
    });

    document.querySelectorAll('.incident-reserve-tab').forEach(function (btn) {
      btn.addEventListener('click', function () {
        switchReserveTab(btn.dataset.reserveTab);
      });
    });

    var reserveSearch = document.getElementById('reserve-person-search');
    if (reserveSearch) {
      reserveSearch.addEventListener('input', function () {
        filterReservePool(reserveSearch.value);
      });
    }

    var form = document.getElementById('einsatzbericht-form');
    if (form) {
      form.addEventListener('submit', function () {
        syncHiddenJson();
      });
    }

    switchReserveTab('divera');
    refreshBoard();
  }

  document.addEventListener('DOMContentLoaded', bindBoard);
})();
