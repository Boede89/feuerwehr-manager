(function () {
  var page = document.getElementById('strecke-planung-page');
  if (!page) return;

  var unitId = page.getAttribute('data-unit');
  var canWrite = page.getAttribute('data-can-write') === 'true';
  var draggedBadge = null;

  function getCsrfToken() {
    var meta = document.querySelector('meta[name="csrf-token"]');
    return meta ? meta.getAttribute('content') : '';
  }

  function getCsrfHeader() {
    var meta = document.querySelector('meta[name="csrf-header"]');
    return meta ? meta.getAttribute('content') : 'X-XSRF-TOKEN';
  }

  function apiFetch(url, options) {
    options = options || {};
    options.headers = options.headers || {};
    options.headers['Content-Type'] = 'application/json';
    options.headers[getCsrfHeader()] = getCsrfToken();
    return fetch(url, options).then(function (res) {
      return res.json();
    });
  }

  function reloadSoon() {
    window.location.reload();
  }

  function notifyResult(result) {
    if (typeof toast === 'function') {
      toast(result.message || (result.ok ? 'Gespeichert.' : 'Fehler.'), result.ok ? 'success' : 'error');
    }
    if (result.ok) {
      setTimeout(reloadSoon, 400);
    }
  }

  function initDragDrop() {
    document.querySelectorAll('.strecke-carrier-badge[draggable="true"]').forEach(function (badge) {
      badge.addEventListener('dragstart', function (e) {
        if (!canWrite) return;
        draggedBadge = badge;
        badge.classList.add('is-dragging');
        e.dataTransfer.effectAllowed = 'move';
      });
      badge.addEventListener('dragend', function () {
        badge.classList.remove('is-dragging');
        draggedBadge = null;
        document.querySelectorAll('.strecke-drop-zone.is-dragover').forEach(function (z) {
          z.classList.remove('is-dragover');
        });
      });
    });

    document.querySelectorAll('[data-drop-zone]').forEach(function (zone) {
      zone.addEventListener('dragover', function (e) {
        if (!canWrite || !draggedBadge) return;
        e.preventDefault();
        zone.classList.add('is-dragover');
      });
      zone.addEventListener('dragleave', function () {
        zone.classList.remove('is-dragover');
      });
      zone.addEventListener('drop', function (e) {
        if (!canWrite || !draggedBadge) return;
        e.preventDefault();
        zone.classList.remove('is-dragover');
        var carrierId = draggedBadge.getAttribute('data-carrier-id');
        var zoneType = zone.getAttribute('data-drop-zone');
        if (zoneType === 'pool') {
          apiFetch('/atemschutz/strecke-planung/api/zuordnung?unit=' + unitId, {
            method: 'POST',
            body: JSON.stringify({ action: 'zurueck_in_pool', carrierId: parseInt(carrierId, 10) })
          }).then(notifyResult);
          return;
        }
        var terminId = zone.getAttribute('data-termin-id');
        apiFetch('/atemschutz/strecke-planung/api/zuordnung?unit=' + unitId, {
          method: 'POST',
          body: JSON.stringify({
            action: 'zuordnen',
            terminId: parseInt(terminId, 10),
            carrierId: parseInt(carrierId, 10)
          })
        }).then(notifyResult);
      });
    });
  }

  function addTerminRow(values) {
    var container = document.getElementById('strecke-termin-rows');
    if (!container) return;
    var row = document.createElement('div');
    row.className = 'strecke-termin-row form-grid form-grid--3';
    row.innerHTML =
      '<div class="form-group">' +
      '<label>Datum</label>' +
      '<input class="field strecke-row-datum" type="date" required value="' + (values && values.datum ? values.datum : '') + '"/>' +
      '</div>' +
      '<div class="form-group">' +
      '<label>Uhrzeit</label>' +
      '<input class="field strecke-row-zeit" type="time" required value="' + (values && values.zeit ? values.zeit : '09:00') + '"/>' +
      '</div>' +
      '<div class="form-group">' +
      '<label>Max. Teilnehmer</label>' +
      '<input class="field strecke-row-max" type="number" min="1" max="100" value="' + (values && values.max ? values.max : '6') + '"/>' +
      '</div>';
    container.appendChild(row);
  }

  function resetTerminModal() {
    document.getElementById('strecke-edit-termin-id').value = '';
    document.getElementById('strecke-termin-ort').value = '';
    document.getElementById('strecke-termin-bemerkung').value = '';
    document.getElementById('strecke-termin-modal-title').textContent = 'Neue Termine erstellen';
    var rows = document.getElementById('strecke-termin-rows');
    if (rows) rows.innerHTML = '';
    addTerminRow(null);
    var addBtn = document.getElementById('strecke-add-termin-row');
    if (addBtn) addBtn.style.display = '';
  }

  function openEditModal(btn) {
    resetTerminModal();
    document.getElementById('strecke-edit-termin-id').value = btn.getAttribute('data-termin-id');
    document.getElementById('strecke-termin-ort').value = btn.getAttribute('data-ort') || '';
    document.getElementById('strecke-termin-bemerkung').value = btn.getAttribute('data-bemerkung') || '';
    document.getElementById('strecke-termin-modal-title').textContent = 'Termin bearbeiten';
    var rows = document.getElementById('strecke-termin-rows');
    if (rows) rows.innerHTML = '';
    addTerminRow({
      datum: btn.getAttribute('data-datum'),
      zeit: (btn.getAttribute('data-zeit') || '09:00').substring(0, 5),
      max: btn.getAttribute('data-max') || '6'
    });
    var addBtn = document.getElementById('strecke-add-termin-row');
    if (addBtn) addBtn.style.display = 'none';
    var overlay = document.getElementById('modal-strecke-termin');
    if (overlay) {
      overlay.classList.add('active');
      document.body.classList.add('modal-open');
    }
  }

  function collectTerminRows() {
    var rows = [];
    document.querySelectorAll('.strecke-termin-row').forEach(function (row) {
      var datum = row.querySelector('.strecke-row-datum').value;
      var zeit = row.querySelector('.strecke-row-zeit').value;
      var max = parseInt(row.querySelector('.strecke-row-max').value, 10) || 6;
      if (datum) {
        rows.push({
          terminDatum: datum,
          terminZeit: zeit || '09:00',
          maxTeilnehmer: max
        });
      }
    });
    return rows;
  }

  function saveTermin() {
    var editId = document.getElementById('strecke-edit-termin-id').value;
    var ort = document.getElementById('strecke-termin-ort').value;
    var bemerkung = document.getElementById('strecke-termin-bemerkung').value;
    var rows = collectTerminRows();
    if (!rows.length) {
      if (typeof toast === 'function') toast('Bitte mindestens ein Datum angeben.', 'warning');
      return;
    }
    if (editId) {
      var body = rows[0];
      body.ort = ort;
      body.bemerkung = bemerkung;
      apiFetch('/atemschutz/strecke-planung/api/termine/' + editId + '?unit=' + unitId, {
        method: 'PUT',
        body: JSON.stringify(body)
      }).then(notifyResult);
      return;
    }
    var payload = {
      termine: rows.map(function (row) {
        return {
          terminDatum: row.terminDatum,
          terminZeit: row.terminZeit,
          ort: ort,
          bemerkung: bemerkung,
          maxTeilnehmer: row.maxTeilnehmer
        };
      })
    };
    apiFetch('/atemschutz/strecke-planung/api/termine?unit=' + unitId, {
      method: 'POST',
      body: JSON.stringify(payload)
    }).then(notifyResult);
  }

  document.querySelectorAll('[data-open-modal="modal-strecke-termin"]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      resetTerminModal();
    });
  });

  var addRowBtn = document.getElementById('strecke-add-termin-row');
  if (addRowBtn) {
    addRowBtn.addEventListener('click', function () {
      addTerminRow(null);
    });
  }

  var saveBtn = document.getElementById('strecke-save-termin');
  if (saveBtn) {
    saveBtn.addEventListener('click', saveTermin);
  }

  document.querySelectorAll('.strecke-edit-termin').forEach(function (btn) {
    btn.addEventListener('click', function () {
      openEditModal(btn);
    });
  });

  document.querySelectorAll('.strecke-delete-termin').forEach(function (btn) {
    btn.addEventListener('click', function () {
      if (!confirm('Termin wirklich löschen? Alle Zuordnungen werden entfernt.')) return;
      var id = btn.getAttribute('data-termin-id');
      apiFetch('/atemschutz/strecke-planung/api/termine/' + id + '?unit=' + unitId, {
        method: 'DELETE'
      }).then(notifyResult);
    });
  });

  document.querySelectorAll('.strecke-carrier-badge__remove').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.stopPropagation();
      var carrierId = btn.getAttribute('data-carrier-id');
      var terminId = btn.getAttribute('data-termin-id');
      apiFetch('/atemschutz/strecke-planung/api/zuordnung?unit=' + unitId, {
        method: 'POST',
        body: JSON.stringify({
          action: 'entfernen',
          terminId: parseInt(terminId, 10),
          carrierId: parseInt(carrierId, 10)
        })
      }).then(notifyResult);
    });
  });

  var autoBtn = document.getElementById('strecke-auto-zuordnung');
  if (autoBtn) {
    autoBtn.addEventListener('click', function () {
      if (!confirm('Alle nicht zugeordneten Geräteträger automatisch auf Termine verteilen?')) return;
      apiFetch('/atemschutz/strecke-planung/api/zuordnung?unit=' + unitId, {
        method: 'POST',
        body: JSON.stringify({ action: 'auto_zuordnung' })
      }).then(notifyResult);
    });
  }

  var clearBtn = document.getElementById('strecke-clear-zuordnungen');
  if (clearBtn) {
    clearBtn.addEventListener('click', function () {
      if (!confirm('Alle Zuordnungen wirklich löschen? Die Termine bleiben erhalten.')) return;
      apiFetch('/atemschutz/strecke-planung/api/zuordnung?unit=' + unitId, {
        method: 'POST',
        body: JSON.stringify({ action: 'alle_loeschen' })
      }).then(notifyResult);
    });
  }

  if (canWrite) {
    resetTerminModal();
  }
  initDragDrop();
})();
