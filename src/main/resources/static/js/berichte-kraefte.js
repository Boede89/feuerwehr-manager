(function () {
  'use strict';

  var draggedChip = null;
  var suppressChipClick = false;
  var roleMenuChip = null;
  var vehicleMenuChip = null;
  var activeCrewTab = 1;

  var ROLE_EF = 'EINHEITSFUEHRER';
  var ROLE_MA = 'MASCHINIST';
  var ACTION_PA = 'PA';
  var ACTION_CLEAR_ROLE = 'CLEAR_ROLE';
  var ACTION_CLEAR_PA = 'CLEAR_PA';
  var BETEILIGT_VEHICLE_ID = -3;

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

  function crewCountForCard(card) {
    var zone = card && card.querySelector('.incident-vehicle-dropzone');
    if (!zone) {
      return 0;
    }
    return zone.querySelectorAll('.incident-crew-chip').length;
  }

  function syncVehicleInvolvementUI(card) {
    if (!isRealVehicleCard(card)) {
      return;
    }
    var involved = card.dataset.involvedInIncident === 'true';
    card.classList.toggle('incident-vehicle-card--einsatz-beteiligt', involved);
    var toggle = card.querySelector('.incident-vehicle-involved-toggle');
    if (toggle) {
      toggle.setAttribute('aria-pressed', involved ? 'true' : 'false');
      toggle.classList.toggle('incident-vehicle-involved-toggle--active', involved);
    }
  }

  function applyCrewInvolvementAfterChange(card) {
    if (!isRealVehicleCard(card)) {
      return;
    }
    syncVehicleInvolvementUI(card);
  }

  function applyCrewInvolvementToAllVehicles() {
    document.querySelectorAll('#incident-vehicle-stack .incident-vehicle-card').forEach(applyCrewInvolvementAfterChange);
    syncHiddenJson();
  }

  function syncAllVehicleInvolvement() {
    document.querySelectorAll('#incident-vehicle-stack .incident-vehicle-card').forEach(syncVehicleInvolvementUI);
  }

  function toggleManualVehicleInvolvement(card) {
    if (!isRealVehicleCard(card) || isBoardReadonly()) {
      return;
    }
    var involved = card.dataset.involvedInIncident !== 'true';
    card.dataset.involvedInIncident = involved ? 'true' : 'false';
    card.dataset.manuallyInvolved = involved ? 'true' : 'false';
    syncVehicleInvolvementUI(card);
    syncHiddenJson();
  }

  function hideNonInvolvedVehiclesInView() {
    if (!isBoardReadonly()) {
      return;
    }
    document.querySelectorAll('#incident-vehicle-stack .incident-vehicle-card').forEach(function (card) {
      if (card.dataset.involvedInIncident !== 'true') {
        card.remove();
      }
    });
  }

  function isReserveChip(chip) {
    return !!(chip && chip.closest('.incident-person-pool--reserve'));
  }

  function isInvolvedZone(zone) {
    return zone && Number(zone.dataset.vehicleId) === BETEILIGT_VEHICLE_ID;
  }

  function isMirrorChip(chip) {
    return !!(chip && chip.classList.contains('incident-crew-chip--involved-mirror'));
  }

  function removeInvolvedMirrors() {
    var zone = involvedDropzone();
    if (!zone) {
      return;
    }
    zone.querySelectorAll('.incident-crew-chip--involved-mirror').forEach(function (chip) {
      chip.remove();
    });
  }

  function createInvolvedMirrorChip(sourceChip) {
    var mirror = document.createElement('div');
    mirror.className = 'incident-crew-chip incident-crew-chip--involved-mirror';
    if (sourceChip.classList.contains('incident-crew-chip--divera')) {
      mirror.classList.add('incident-crew-chip--divera');
    }
    mirror.dataset.personId = sourceChip.dataset.personId;
    mirror.dataset.qualTier = sourceChip.dataset.qualTier || '';
    mirror.dataset.personName = sourceChip.dataset.personName || '';
    if (sourceChip.dataset.poolSource) {
      mirror.dataset.poolSource = sourceChip.dataset.poolSource;
    }
    mirror.setAttribute('draggable', 'false');
    var nameEl = document.createElement('span');
    nameEl.className = 'incident-crew-chip__name';
    nameEl.textContent = sourceChip.dataset.personName || '';
    mirror.appendChild(nameEl);
    return mirror;
  }

  function resortInvolvedZoneChips(zone) {
    var chips = Array.from(zone.querySelectorAll('.incident-crew-chip'));
    chips.sort(function (a, b) {
      return chipSortName(a).localeCompare(chipSortName(b), 'de');
    });
    chips.forEach(function (chip) {
      zone.appendChild(chip);
    });
  }

  function refreshInvolvedDisplay() {
    var zone = involvedDropzone();
    if (!zone) {
      return;
    }
    removeInvolvedMirrors();
    if (activeCrewTab !== 1) {
      resortInvolvedZoneChips(zone);
      return;
    }
    var assignedByPersonId = {};
    document.querySelectorAll('.incident-vehicle-card .incident-vehicle-dropzone .incident-crew-chip').forEach(function (chip) {
      if (isMirrorChip(chip)) {
        return;
      }
      var personId = chip.dataset.personId;
      if (!personId) {
        return;
      }
      assignedByPersonId[personId] = chip;
    });
    Object.keys(assignedByPersonId).forEach(function (personId) {
      var canonical = assignedByPersonId[personId];
      if (isInvolvedChip(canonical)) {
        return;
      }
      zone.appendChild(createInvolvedMirrorChip(canonical));
    });
    resortInvolvedZoneChips(zone);
  }

  function placeInvolvedCard(tabIdx) {
    var card = document.getElementById('involved-card');
    var personalSlot = document.getElementById('personal-involved-slot');
    var fahrzeugeSlot = document.getElementById('fahrzeuge-involved-slot');
    if (!card) {
      return;
    }
    if (tabIdx === 1 && personalSlot) {
      personalSlot.appendChild(card);
      card.hidden = false;
    } else if (tabIdx === 2 && fahrzeugeSlot) {
      fahrzeugeSlot.appendChild(card);
      card.hidden = false;
    } else {
      card.hidden = true;
    }
  }

  function vehicleCardForChip(chip) {
    return chip ? chip.closest('.incident-vehicle-card') : null;
  }

  function roleBadgeLabel(role) {
    return role === ROLE_EF ? 'EF' : 'Ma';
  }

  function chipHasPa(chip) {
    return chip && chip.dataset.pa === 'true';
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

  function removeRoleBadge(chip) {
    var badge = chip.querySelector('.incident-crew-chip__role-badge');
    if (badge) {
      badge.remove();
    }
  }

  function removePaBadge(chip) {
    var badge = chip.querySelector('.incident-crew-chip__pa-badge');
    if (badge) {
      badge.remove();
    }
  }

  function clearChipVehicleRole(chip) {
    if (!chip) {
      return;
    }
    delete chip.dataset.vehicleRole;
    chip.classList.remove('incident-crew-chip--ef', 'incident-crew-chip--maschinist');
    removeRoleBadge(chip);
  }

  function clearChipPa(chip) {
    if (!chip) {
      return;
    }
    delete chip.dataset.pa;
    chip.classList.remove('incident-crew-chip--pa');
    removePaBadge(chip);
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
    var paBadge = chip.querySelector('.incident-crew-chip__pa-badge');
    if (paBadge) {
      chip.insertBefore(badge, paBadge);
    } else {
      chip.insertBefore(badge, chip.firstChild);
    }
  }

  function applyChipPa(chip) {
    if (!chip) {
      return;
    }
    ensureChipNameElement(chip);
    chip.dataset.pa = 'true';
    chip.classList.add('incident-crew-chip--pa');
    if (!chip.querySelector('.incident-crew-chip__pa-badge')) {
      var badge = document.createElement('span');
      badge.className = 'incident-crew-chip__pa-badge';
      badge.textContent = 'PA';
      var roleBadge = chip.querySelector('.incident-crew-chip__role-badge');
      if (roleBadge) {
        roleBadge.insertAdjacentElement('afterend', badge);
      } else {
        chip.insertBefore(badge, chip.firstChild);
      }
    }
  }

  function findRoleChipInVehicle(card, role) {
    if (!card) {
      return null;
    }
    return card.querySelector('.incident-vehicle-dropzone .incident-crew-chip[data-vehicle-role="' + role + '"]');
  }

  function isBoardReadonly() {
    var board = document.getElementById('incident-kraefte-board');
    return board && board.dataset.readonly === 'true';
  }

  function involvedDropzone() {
    return document.querySelector('.incident-vehicle-dropzone--involved');
  }

  function isInvolvedChip(chip) {
    return isInvolvedZone(chip && chip.closest('.incident-vehicle-dropzone'));
  }

  function chipSortName(chip) {
    return (chip.dataset.personName || chip.textContent || '').trim().toLocaleLowerCase('de');
  }

  function insertChipSortedByName(pool, chip) {
    var name = chipSortName(chip);
    var chips = Array.from(pool.querySelectorAll('.incident-crew-chip'));
    var insertBefore = null;
    for (var i = 0; i < chips.length; i++) {
      if (chips[i] === chip) {
        continue;
      }
      if (chipSortName(chips[i]).localeCompare(name, 'de') > 0) {
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

  function positionFloatingMenu(menu, chip) {
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

  function hideRoleMenu() {
    var menu = document.getElementById('incident-crew-role-menu');
    if (menu) {
      menu.hidden = true;
    }
    roleMenuChip = null;
  }

  function hideVehicleMenu() {
    var menu = document.getElementById('incident-vehicle-assign-menu');
    if (menu) {
      menu.hidden = true;
      menu.textContent = '';
    }
    vehicleMenuChip = null;
  }

  function hideAllMenus() {
    hideRoleMenu();
    hideVehicleMenu();
  }

  function listAssignableVehicleCards() {
    var cards = [];
    var stack = document.getElementById('incident-vehicle-stack');
    if (stack) {
      stack.querySelectorAll('.incident-vehicle-card').forEach(function (card) {
        cards.push(card);
      });
    }
    document.querySelectorAll('.incident-virtual-slots .incident-vehicle-card').forEach(function (card) {
      cards.push(card);
    });
    return cards;
  }

  function vehicleCardLabel(card) {
    var title = card.querySelector('.incident-kraefte-section__title, .incident-vehicle-card__name');
    return title ? title.textContent.trim() : 'Fahrzeug';
  }

  function assignChipToVehicle(chip, vehicleId) {
    var zone = document.querySelector('.incident-vehicle-dropzone[data-vehicle-id="' + vehicleId + '"]');
    if (!chip || !zone) {
      return;
    }
    removePersonFromBoard(chip.dataset.personId, chip);
    clearChipVehicleRole(chip);
    clearChipPa(chip);
    zone.appendChild(chip);
    var card = zone.closest('.incident-vehicle-card');
    if (isRealVehicleCard(card)) {
      chip.classList.add('incident-crew-chip--vehicle-role');
    } else {
      chip.classList.remove('incident-crew-chip--vehicle-role');
    }
    applyCrewInvolvementToAllVehicles();
    refreshBoard();
  }

  function moveChipToInvolved(chip) {
    var zone = involvedDropzone();
    if (!chip || !zone || isInvolvedChip(chip)) {
      return;
    }
    removePersonFromBoard(chip.dataset.personId, chip);
    clearChipVehicleRole(chip);
    clearChipPa(chip);
    chip.classList.remove('incident-crew-chip--vehicle-role');
    insertChipSortedByName(zone, chip);
    applyCrewInvolvementToAllVehicles();
    refreshBoard();
  }

  function showVehicleAssignMenu(chip) {
    var menu = document.getElementById('incident-vehicle-assign-menu');
    if (!menu || !isInvolvedChip(chip)) {
      return;
    }
    hideRoleMenu();
    var cards = listAssignableVehicleCards();
    if (cards.length === 0) {
      return;
    }
    menu.textContent = '';
    cards.forEach(function (card) {
      var vehicleId = card.dataset.vehicleId;
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'incident-crew-role-menu__btn';
      btn.setAttribute('role', 'menuitem');
      btn.textContent = vehicleCardLabel(card);
      btn.dataset.vehicleId = vehicleId;
      menu.appendChild(btn);
    });
    vehicleMenuChip = chip;
    menu.hidden = false;
    positionFloatingMenu(menu, chip);
  }

  function configureMenuButton(btn, hidden) {
    btn.hidden = hidden;
  }

  function showRoleMenu(chip) {
    var card = vehicleCardForChip(chip);
    var menu = document.getElementById('incident-crew-role-menu');
    if (!chip.closest('.incident-vehicle-dropzone') || !menu || isInvolvedChip(chip)) {
      return;
    }
    hideVehicleMenu();
    var onRealVehicle = isRealVehicleCard(card);
    var currentRole = chip.dataset.vehicleRole || '';
    var hasPa = chipHasPa(chip);
    var hasEf = onRealVehicle && !!findRoleChipInVehicle(card, ROLE_EF);
    var hasMa = onRealVehicle && !!findRoleChipInVehicle(card, ROLE_MA);

    menu.querySelectorAll('.incident-crew-role-menu__btn').forEach(function (btn) {
      var action = btn.dataset.action || btn.dataset.role;
      var hidden = true;
      if (action === ROLE_EF) {
        hidden = !onRealVehicle || !!currentRole || (hasEf && currentRole !== ROLE_EF);
      } else if (action === ROLE_MA) {
        hidden = !onRealVehicle || !!currentRole || (hasMa && currentRole !== ROLE_MA);
      } else if (action === ACTION_PA) {
        hidden = hasPa;
      } else if (action === ACTION_CLEAR_ROLE) {
        hidden = !onRealVehicle || !currentRole;
      } else if (action === ACTION_CLEAR_PA) {
        hidden = !hasPa;
      }
      configureMenuButton(btn, hidden);
    });

    var visibleButtons = menu.querySelectorAll('.incident-crew-role-menu__btn:not([hidden])');
    if (visibleButtons.length === 0) {
      hideRoleMenu();
      return;
    }

    roleMenuChip = chip;
    menu.hidden = false;
    positionFloatingMenu(menu, chip);
  }

  function handleMenuAction(action) {
    if (!roleMenuChip || !action) {
      return;
    }
    if (action === ACTION_CLEAR_ROLE) {
      clearChipVehicleRole(roleMenuChip);
    } else if (action === ACTION_CLEAR_PA) {
      clearChipPa(roleMenuChip);
    } else if (action === ACTION_PA) {
      applyChipPa(roleMenuChip);
    } else if (action === ROLE_EF || action === ROLE_MA) {
      var card = vehicleCardForChip(roleMenuChip);
      if (!isRealVehicleCard(card)) {
        hideRoleMenu();
        return;
      }
      var existing = findRoleChipInVehicle(card, action);
      if (existing && existing !== roleMenuChip) {
        clearChipVehicleRole(existing);
      }
      applyChipVehicleRole(roleMenuChip, action);
    }
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
        .filter(function (chip) {
          return !isMirrorChip(chip);
        })
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
        assignment.involvedInIncident = card.dataset.involvedInIncident === 'true';
        assignment.manuallyInvolvedInIncident = card.dataset.manuallyInvolved === 'true';
      }
      var paIds = Array.from(card.querySelectorAll('.incident-vehicle-dropzone .incident-crew-chip[data-pa="true"]'))
        .map(function (chip) {
          return Number(chip.dataset.personId);
        })
        .filter(function (id) {
          return !isNaN(id);
        });
      if (paIds.length > 0) {
        assignment.paPersonIds = paIds;
      }
      assignments.push(assignment);
    });
    hidden.value = JSON.stringify(assignments);
  }

  function refreshBoard() {
    refreshInvolvedDisplay();
    document.querySelectorAll('.incident-vehicle-card').forEach(updateVehicleStaerke);
    if (!isBoardReadonly()) {
      syncAllVehicleInvolvement();
    }
    hideNonInvolvedVehiclesInView();
    updatePoolCounts();
    updateActiveEmptyHint();
    syncHiddenJson();
  }

  function onDragStart(e) {
    var chip = e.target.closest('.incident-crew-chip');
    if (!chip || isMirrorChip(chip) || chip.getAttribute('draggable') !== 'true') {
      return;
    }
    hideAllMenus();
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
    if (isReserveChip(draggedChip) && !isInvolvedZone(zone)) {
      return;
    }
    var personId = draggedChip.dataset.personId;
    var targetCard = zone.closest('.incident-vehicle-card');
    removePersonFromBoard(personId, draggedChip);
    if (isInvolvedZone(zone)) {
      clearChipVehicleRole(draggedChip);
      clearChipPa(draggedChip);
      draggedChip.classList.remove('incident-crew-chip--vehicle-role');
      insertChipSortedByName(zone, draggedChip);
    } else {
      clearChipVehicleRole(draggedChip);
      zone.appendChild(draggedChip);
    }
    if (isRealVehicleCard(targetCard)) {
      draggedChip.classList.add('incident-crew-chip--vehicle-role');
    } else if (!isInvolvedZone(zone)) {
      draggedChip.classList.remove('incident-crew-chip--vehicle-role');
    }
    applyCrewInvolvementToAllVehicles();
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
    var source = chip.dataset.poolSource;
    if (source === 'divera') {
      return poolForTab('divera');
    }
    if (source === 'manual') {
      return poolForTab('manual');
    }
    if (chip.classList.contains('incident-crew-chip--divera')) {
      return poolForTab('divera');
    }
    return poolForTab('manual');
  }

  function onDropPersonPool(e) {
    e.preventDefault();
    if (!draggedChip) {
      return;
    }
    var homePool = homePoolForChip(draggedChip);
    if (!homePool || draggedChip.parentElement === homePool) {
      return;
    }
    removePersonFromBoard(draggedChip.dataset.personId, draggedChip);
    clearChipVehicleRole(draggedChip);
    clearChipPa(draggedChip);
    draggedChip.classList.remove('incident-crew-chip--vehicle-role');
    if (homePool.dataset.pool === 'divera') {
      draggedChip.classList.add('incident-crew-chip--divera');
    } else {
      draggedChip.classList.remove('incident-crew-chip--divera');
    }
    insertChipSorted(homePool, draggedChip);
    switchReserveTab(homePool.dataset.pool);
    var searchEl = document.getElementById('reserve-person-search');
    if (searchEl) {
      filterReservePool(searchEl.value);
    }
    applyCrewInvolvementToAllVehicles();
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
      if (suppressChipClick || isBoardReadonly()) {
        return;
      }
      var involvedToggle = e.target.closest('.incident-vehicle-involved-toggle');
      if (involvedToggle) {
        e.stopPropagation();
        toggleManualVehicleInvolvement(involvedToggle.closest('.incident-vehicle-card'));
        return;
      }
      var vehicleHead = e.target.closest('#incident-vehicle-stack .incident-vehicle-card__head');
      if (vehicleHead && activeCrewTab === 2 && !e.target.closest('.incident-vehicle-card__staerke')) {
        var vehicleCard = vehicleHead.closest('.incident-vehicle-card');
        if (vehicleCard && isRealVehicleCard(vehicleCard) && !e.target.closest('.incident-crew-chip')) {
          e.stopPropagation();
          toggleManualVehicleInvolvement(vehicleCard);
          return;
        }
      }
      var chip = e.target.closest('.incident-crew-chip');
      if (!chip) {
        return;
      }
      if (isReserveChip(chip) && activeCrewTab === 1) {
        e.stopPropagation();
        moveChipToInvolved(chip);
        return;
      }
      if (isMirrorChip(chip)) {
        return;
      }
      if (isInvolvedChip(chip) && activeCrewTab === 2) {
        e.stopPropagation();
        showVehicleAssignMenu(chip);
        return;
      }
      if (!chip.closest('.incident-vehicle-dropzone')) {
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
          handleMenuAction(btn.dataset.action || btn.dataset.role);
        });
      });
    }

    var vehicleMenu = document.getElementById('incident-vehicle-assign-menu');
    if (vehicleMenu) {
      vehicleMenu.addEventListener('click', function (e) {
        var btn = e.target.closest('.incident-crew-role-menu__btn');
        if (!btn || !vehicleMenuChip) {
          return;
        }
        e.stopPropagation();
        assignChipToVehicle(vehicleMenuChip, btn.dataset.vehicleId);
        hideVehicleMenu();
      });
    }

    document.addEventListener('click', function (e) {
      if (e.target.closest('#incident-crew-role-menu') || e.target.closest('#incident-vehicle-assign-menu')) {
        return;
      }
      if (e.target.closest('.incident-vehicle-dropzone .incident-crew-chip') ||
          e.target.closest('.incident-person-pool--reserve .incident-crew-chip')) {
        return;
      }
      hideAllMenus();
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
        if (draggedChip) {
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
        if (window.BerichteSchaeden && window.BerichteSchaeden.syncBeforeSave) {
          window.BerichteSchaeden.syncBeforeSave();
        }
        syncHiddenJson();
      }, true);
    }

    var diveraCountEl = document.getElementById('divera-pool-count');
    var hasDivera = diveraCountEl && Number(diveraCountEl.textContent) > 0;
    switchReserveTab(hasDivera ? 'divera' : 'manual');
    placeInvolvedCard(1);
    refreshBoard();
  }

  window.BerichteKraefte = {
    init: bindBoard,
    onTabShow: function (tabIdx) {
      activeCrewTab = tabIdx;
      placeInvolvedCard(tabIdx);
      hideAllMenus();
      refreshBoard();
    }
  };

  document.addEventListener('DOMContentLoaded', bindBoard);
})();
