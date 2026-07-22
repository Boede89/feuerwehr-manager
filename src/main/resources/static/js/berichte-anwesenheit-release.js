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
      return Promise.resolve({ unassignedCount: countUnassignedFromBoard() });
    }
    var boardCount = countUnassignedFromBoard();
    if (boardCount > 0) {
      return Promise.resolve({ unassignedCount: boardCount });
    }
    return fetch(apiBase() + '/' + encodeURIComponent(reportId) + '/freigabe-check?unit='
      + encodeURIComponent(unitId), { credentials: 'same-origin' })
      .then(function (res) {
        if (!res.ok) {
          return { unassignedCount: 0 };
        }
        return res.json();
      })
      .catch(function () {
        return { unassignedCount: 0 };
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
      if (count <= 0) {
        return { assignRemainingToWache: false };
      }
      return promptWacheAssignment(count).then(function (yes) {
        if (yes && window.BerichteKraefte && window.BerichteKraefte.assignRemainingToWache) {
          window.BerichteKraefte.assignRemainingToWache();
        }
        return { assignRemainingToWache: yes === true };
      });
    });
  }

  window.BerichteAnwesenheitRelease = {
    parseReportIdFromAction: parseReportIdFromAction,
    prepareRelease: prepareRelease
  };
})();
