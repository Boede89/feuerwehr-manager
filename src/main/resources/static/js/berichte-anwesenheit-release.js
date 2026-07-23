(function () {
  'use strict';

  function parseReportIdFromAction(action) {
    if (!action) {
      return null;
    }
    var match = String(action).match(/\/anwesenheitslisten\/(\d+)\/freigeben/);
    return match ? Number(match[1]) : null;
  }

  function apiBase() {
    // Immer Anwesenheits-API — auf der Listen-Seite fehlt oft das Meta-Tag,
    // und BerichteApiBase fällt sonst auf /berichte/einsatzberichte zurück.
    var fromMeta = window.BerichteApiBase ? window.BerichteApiBase.path() : '';
    if (fromMeta && fromMeta.indexOf('/anwesenheitslisten') !== -1) {
      return fromMeta;
    }
    return '/berichte/anwesenheitslisten';
  }

  function countUnassignedFromBoard() {
    if (window.BerichteKraefte && window.BerichteKraefte.countUnassignedInvolved) {
      return window.BerichteKraefte.countUnassignedInvolved();
    }
    return 0;
  }

  function fetchUnassignedCount(reportId, unitId) {
    var fromForm = hasDeployedEquipmentFromForm();
    if (!reportId || !unitId) {
      return Promise.resolve({
        unassignedCount: countUnassignedFromBoard(),
        hasMaterialDamages: false,
        hasDeployedEquipment: fromForm
      });
    }
    var boardCount = countUnassignedFromBoard();
    return fetch(apiBase() + '/' + encodeURIComponent(reportId) + '/freigabe-check?unit='
      + encodeURIComponent(unitId), { credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) {
          return {
            unassignedCount: boardCount,
            hasMaterialDamages: false,
            hasDeployedEquipment: fromForm
          };
        }
        return res.json().then(function (data) {
          return {
            unassignedCount: boardCount > 0 ? boardCount : (Number(data.unassignedCount) || 0),
            hasMaterialDamages: !!data.hasMaterialDamages,
            // Formular hat Vorrang: Auswahl oft noch nicht gespeichert
            hasDeployedEquipment: fromForm || !!data.hasDeployedEquipment
          };
        });
      })
      .catch(function () {
        return {
          unassignedCount: boardCount,
          hasMaterialDamages: false,
          hasDeployedEquipment: fromForm
        };
      });
  }

  function hasDeployedEquipmentFromForm() {
    var field = document.getElementById('deployedEquipmentJson');
    if (!field || !field.value) {
      return false;
    }
    try {
      var data = JSON.parse(field.value);
      if (!Array.isArray(data)) {
        return false;
      }
      return data.some(function (row) {
        var ids = row && row.equipmentIds ? row.equipmentIds : [];
        var custom = row && row.customEquipment ? row.customEquipment : [];
        return (ids && ids.length > 0) || (custom && custom.length > 0);
      });
    } catch (e) {
      return false;
    }
  }

  function promptWacheAssignment(count) {
    var message = count + ' Personen sind noch keinem Fahrzeug oder der Wache zugeordnet. '
      + 'Sollen diese automatisch der Wache zugewiesen werden?';
    if (window.FwConfirm && window.FwConfirm.ask) {
      return window.FwConfirm.ask(message, 'Personal zuweisen');
    }
    return Promise.resolve(window.confirm(message));
  }

  function prepareRelease(reportId, unitId) {
    if (window.BerichteGeraete && typeof window.BerichteGeraete.sync === 'function') {
      window.BerichteGeraete.sync();
    }
    return fetchUnassignedCount(reportId, unitId).then(function (data) {
      var count = Number(data && data.unassignedCount) || 0;
      var hasMaterialDamages = !!(data && data.hasMaterialDamages);
      var hasDeployedEquipment = !!(data && data.hasDeployedEquipment);
      var base = {
        assignRemainingToWache: false,
        hasMaterialDamages: hasMaterialDamages,
        hasDeployedEquipment: hasDeployedEquipment
      };
      if (count <= 0) {
        return base;
      }
      return promptWacheAssignment(count).then(function (yes) {
        if (yes && window.BerichteKraefte && window.BerichteKraefte.assignRemainingToWache) {
          window.BerichteKraefte.assignRemainingToWache();
        }
        return {
          assignRemainingToWache: yes === true,
          hasMaterialDamages: hasMaterialDamages,
          hasDeployedEquipment: hasDeployedEquipment
        };
      });
    });
  }

  window.BerichteAnwesenheitRelease = {
    parseReportIdFromAction: parseReportIdFromAction,
    prepareRelease: prepareRelease
  };
})();
