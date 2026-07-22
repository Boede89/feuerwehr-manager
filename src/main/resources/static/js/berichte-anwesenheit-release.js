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
    return window.BerichteApiBase ? window.BerichteApiBase.path() : '/berichte/anwesenheitslisten';
  }

  function countUnassignedFromBoard() {
    if (window.BerichteKraefte && window.BerichteKraefte.countUnassignedInvolved) {
      return window.BerichteKraefte.countUnassignedInvolved();
    }
    return 0;
  }

  function fetchUnassignedCount(reportId, unitId) {
    if (!reportId || !unitId) {
      return Promise.resolve({
        unassignedCount: countUnassignedFromBoard(),
        hasMaterialDamages: false
      });
    }
    var boardCount = countUnassignedFromBoard();
    return fetch(apiBase() + '/' + encodeURIComponent(reportId) + '/freigabe-check?unit='
      + encodeURIComponent(unitId), { credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) {
          return { unassignedCount: boardCount, hasMaterialDamages: false };
        }
        return res.json().then(function (data) {
          return {
            unassignedCount: boardCount > 0 ? boardCount : (Number(data.unassignedCount) || 0),
            hasMaterialDamages: !!data.hasMaterialDamages
          };
        });
      })
      .catch(function () {
        return { unassignedCount: boardCount, hasMaterialDamages: false };
      });
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
    return fetchUnassignedCount(reportId, unitId).then(function (data) {
      var count = Number(data && data.unassignedCount) || 0;
      var hasMaterialDamages = !!(data && data.hasMaterialDamages);
      var base = { assignRemainingToWache: false, hasMaterialDamages: hasMaterialDamages };
      if (count <= 0) {
        return base;
      }
      return promptWacheAssignment(count).then(function (yes) {
        if (yes && window.BerichteKraefte && window.BerichteKraefte.assignRemainingToWache) {
          window.BerichteKraefte.assignRemainingToWache();
        }
        return {
          assignRemainingToWache: yes === true,
          hasMaterialDamages: hasMaterialDamages
        };
      });
    });
  }

  window.BerichteAnwesenheitRelease = {
    parseReportIdFromAction: parseReportIdFromAction,
    prepareRelease: prepareRelease
  };
})();
