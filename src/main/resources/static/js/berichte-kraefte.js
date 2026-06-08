(function () {
  'use strict';

  var draggedChip = null;
  var suppressChipClick = false;
  var roleMenuChip = null;

  var ROLE_EF = 'EINHEITSFUEHRER';
  var ROLE_MA = 'MASCHINIST';

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

  function isRealVehicleCard(card) {
    return card && Number(card.dataset.vehicleId) > 0;
  }

  function vehicleCardForChip(chip) {
    return chip ? chip.closest('.incident-vehicle-card') : null;
  }

  function roleBadgeLabel(role) {
    return role === ROLE_EF ? 'EF' : 'Ma';
  }

  function ensureChipNameElement(chip) {
    if (!chip.querySelector('.incident-crew-chip__name')) {
      var name = chip.dataset.personName || chip.textContent.trim();
      chip.textContent = '';
      var nameEl = document.createElement('span');
      nameEl.className = 'incident-crew-chip__name';
      nameEl.textContent = name;
      chip.appendChild(nameEl);
    }
  }

  function clearChipVehicleRole(chip) {
    if (!chip) {
      return;
    }
    delete chip.dataset.vehicleRole;
    chip.classList.remove('incident-crew-chip--ef', 'incident-crew-chip--maschinist');
    var badge = chip.querySelector('.incident-crew-chip__role-badge');
    if (badge) {
      badge.remove();
    }
  }

  function applyChipVehicleRole(chip, role) {
    if (!chip || !role) {
      return;
    }
    ensureChipNameElement(chip);
    clearChipVehicleRole(chip);
    chip.dataset.vehicleRole = role;
    chip.classList.add(role === ROLE_EF ? 'incident-crew-chip--ef' : 'incident-crew-chip--maschinist');
    var badge = document.createElement('span');
    badge.className = 'incident-crew-chip__role-badge';
    badge.textContent = roleBadgeLabel(role);
    chip.insertBefore(badge, chip.firstChild);
  }

  function findRoleChipInVehicle(card, role) {
    if (!card) {
      return null;
    }
    return card.querySelector('.incident-vehicle-dropzone .incident-crew-chip[data-vehicle-role="' + role + '"]');
  }

  function hideRoleMenu() {
    var menu = document.getElementById('incident-crew-role-menu');
    if (menu) {
      menu.hidden = true;
    }
    roleMenuChip = null;
  }

  function showRoleMenu(chip) {
    var card = vehicleCardForChip(chip);
    var menu = document.getElementById('incident-crew-role-menu');
    if (!isRealVehicleCard(card) || !menu) {
      return;
    }
    var currentRole = chip.dataset.vehicleRole || '';
    if (currentRole) {
      clearChipVehicleRole(chip);
      hideRoleMenu();
      syncHiddenJson();
      return;
    }
    var hasEf = !!findRoleChipInVehicle(card, ROLE_EF);
    var hasMa = !!findRoleChipInVehicle(card, ROLE_MA);
    menu.querySelectorAll('.incident-crew-role-menu__btn').forEach(function (btn) {
      var role = btn.dataset.role;
      btn.hidden = (role === ROLE_EF && hasEf) || (role === ROLE_MA && hasMa);
    });
    var visibleButtons = menu.querySelectorAll('.incident-crew-role-menu__btn:not([hidden])');
    if (visibleButtons.length === 0) {
      hideRoleMenu();
      return;
    }
    roleMenuChip = chip;
    menu.hidden = false;
    var board = document.getElementById('incident-kraefte-board');
    var rect = chip.getBoundingClientRect();
    var boardRect = board ? board.getBoundingClientRect() : { left: 0, top: 0 };
    var left = rect.left - boardRect.left;
    var top = rect.bottom - boardRect.top + 6;
    if (board) {
      left = Math.max(4, Math.min(left, board.clientWidth - menu.offsetWidth - 4));
    }
    menu.style.left = left + 'px';
    menu.style.top = top + 'px';
  }

  function assignVehicleRole(role) {
    if (!roleMenuChip || !role) {
      return;
    }
    var card = vehicleCardForChip(roleMenuChip);
    if (!isRealVehicleCard(card)) {
      hideRoleMenu();
      return;
    }
    var existing = findRoleChipInVehicle(card, role);
    if (existing && existing !== roleMenuChip) {
      clearChipVehicleRole(existing);
    }
    applyChipVehicleRole(roleMenuChip, role);
    hideRoleMenu();
    syncHiddenJson();
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
      var assignment = { vehicleId: vehicleId, personIds: personIds };
      if (vehicleId > 0) {
        var efChip = findRoleChipInVehicle(card, ROLE_EF);
        var maChip = findRoleChipInVehicle(card, ROLE_MA);
        if (efChip) {
          assignment.einheitsfuehrerPersonId = Number(efChip.dataset.personId);
        }
        if (maChip) {
          assignment.maschinistPersonId = Number(maChip.dataset.personId);
        }
      }
      assignments.push(assignment);
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
    hideRoleMenu();
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
    suppressChipClick = true;
    window.setTimeout(function () {
      suppressChipClick = false;
    }, 120);
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
    var targetCard = zone.closest('.incident-vehicle-card');
    removePersonFromBoard(personId, draggedChip);
    clearChipVehicleRole(draggedChip);
    zone.appendChild(draggedChip);
    if (isRealVehicleCard(targetCard)) {
      draggedChip.classList.add('incident-crew-chip--vehicle-role');
    } else {
      draggedChip.classList.remove('incident-crew-chip--vehicle-role');
    }
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
    clearChipVehicleRole(draggedChip);
    draggedChip.classList.remove('incident-crew-chip--vehicle-role');
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

    board.addEventListener('click', function (e) {
      if (suppressChipClick) {
        return;
      }
      var chip = e.target.closest('.incident-crew-chip--vehicle-role');
      if (!chip || !chip.closest('.incident-vehicle-dropzone')) {
        return;
      }
      if (!isRealVehicleCard(vehicleCardForChip(chip))) {
        return;
      }
      e.stopPropagation();
      showRoleMenu(chip);
    });

    var roleMenu = document.getElementById('incident-crew-role-menu');
    if (roleMenu) {
      roleMenu.querySelectorAll('.incident-crew-role-menu__btn').forEach(function (btn) {
        btn.addEventListener('click', function (e) {
          e.stopPropagation();
          assignVehicleRole(btn.dataset.role);
        });
      });
    }

    document.addEventListener('click', function (e) {
      if (!e.target.closest('#incident-crew-role-menu') && !e.target.closest('.incident-crew-chip--vehicle-role')) {
        hideRoleMenu();
      }
    });

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
