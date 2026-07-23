(function () {
  'use strict';

  var STORAGE_KEY = 'einsatzReleaseIssues';

  function fetchValidation(reportId, unitId) {
    return fetch(
      '/berichte/einsatzberichte/' + encodeURIComponent(reportId) +
        '/release-validation?unit=' + encodeURIComponent(unitId),
      { credentials: 'same-origin' }
    ).then(function (res) {
      if (!res.ok) {
        throw new Error('Validierung konnte nicht geladen werden.');
      }
      return res.json();
    });
  }

  function editUrl(reportId, unitId, issue) {
    var url = '/berichte/einsatzberichte/' + encodeURIComponent(reportId) +
      '/bearbeiten?unit=' + encodeURIComponent(unitId);
    if (issue) {
      url += '&tab=' + encodeURIComponent(String(issue.tabIndex));
      if (issue.anchorId) {
        url += '&focus=' + encodeURIComponent(issue.anchorId);
      }
    }
    return url;
  }

  function storeIssuesAndNavigate(issues, reportId, unitId, focusIssue) {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(issues || []));
    } catch (e) {
      /* ignore */
    }
    var target = focusIssue || (issues && issues.length ? issues[0] : null);
    window.location.href = editUrl(reportId, unitId, target);
  }

  function fetchFreigabeCheck(reportId, unitId) {
    var fromForm = hasDeployedEquipmentFromForm();
    var fromFormMaengel = hasMaterialDamagesFromForm();
    if (!reportId || !unitId) {
      return Promise.resolve({
        hasMaterialDamages: fromFormMaengel,
        hasDeployedEquipment: fromForm
      });
    }
    return fetch(
      '/berichte/einsatzberichte/' + encodeURIComponent(reportId) +
        '/freigabe-check?unit=' + encodeURIComponent(unitId),
      { credentials: 'same-origin' }
    ).then(function (res) {
      if (!res.ok) {
        return {
          hasMaterialDamages: fromFormMaengel,
          hasDeployedEquipment: fromForm
        };
      }
      return res.json().then(function (data) {
        return {
          hasMaterialDamages: fromFormMaengel || !!data.hasMaterialDamages,
          hasDeployedEquipment: fromForm || !!data.hasDeployedEquipment
        };
      });
    }).catch(function () {
      return {
        hasMaterialDamages: fromFormMaengel,
        hasDeployedEquipment: fromForm
      };
    });
  }

  function hasDeployedEquipmentFromForm() {
    if (window.BerichteGeraete && typeof window.BerichteGeraete.sync === 'function') {
      window.BerichteGeraete.sync();
    }
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

  function hasMaterialDamagesFromForm() {
    var field = document.getElementById('materialDamageEntriesJson');
    if (!field || !field.value) {
      return false;
    }
    try {
      var data = JSON.parse(field.value);
      return Array.isArray(data) && data.length > 0;
    } catch (e) {
      return false;
    }
  }

  function ensureValidBeforeRelease(reportId, unitId) {
    return fetchValidation(reportId, unitId).then(function (result) {
      if (result && result.valid) {
        return fetchFreigabeCheck(reportId, unitId).then(function (check) {
          return {
            ok: true,
            hasDeployedEquipment: !!(check && check.hasDeployedEquipment),
            hasMaterialDamages: !!(check && check.hasMaterialDamages)
          };
        });
      }
      var issues = (result && result.issues) || [];
      if (window.FwConfirm && window.FwConfirm.releaseValidationIssues) {
        return window.FwConfirm.releaseValidationIssues(issues, {
          reportId: reportId,
          unitId: unitId
        }).then(function (action) {
          if (action === 'edit') {
            storeIssuesAndNavigate(issues, reportId, unitId);
          }
          return { ok: false };
        });
      }
      var labels = issues.map(function (issue) {
        return issue.label;
      }).join(', ');
      window.alert('Freigabe nicht möglich. Folgende Pflichtfelder fehlen noch: ' + labels);
      storeIssuesAndNavigate(issues, reportId, unitId);
      return { ok: false };
    });
  }

  function parseReportIdFromAction(action) {
    if (!action) {
      return null;
    }
    var match = String(action).match(/\/einsatzberichte\/(\d+)\/freigeben/);
    return match ? match[1] : null;
  }

  window.BerichteEinsatzRelease = {
    STORAGE_KEY: STORAGE_KEY,
    fetchValidation: fetchValidation,
    ensureValidBeforeRelease: ensureValidBeforeRelease,
    editUrl: editUrl,
    storeIssuesAndNavigate: storeIssuesAndNavigate,
    parseReportIdFromAction: parseReportIdFromAction
  };
})();
