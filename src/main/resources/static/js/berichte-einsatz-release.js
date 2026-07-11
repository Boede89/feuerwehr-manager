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

  function ensureValidBeforeRelease(reportId, unitId) {
    return fetchValidation(reportId, unitId).then(function (result) {
      if (result && result.valid) {
        return { ok: true };
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
