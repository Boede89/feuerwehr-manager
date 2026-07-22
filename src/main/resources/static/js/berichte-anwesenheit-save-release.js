(function () {
  'use strict';

  function syncFormBeforeRelease() {
    if (window.BerichteKraefte && window.BerichteSchaeden) {
      // no-op placeholder for clarity
    }
    if (window.BerichteSchaeden && window.BerichteSchaeden.syncBeforeSave) {
      window.BerichteSchaeden.syncBeforeSave();
    }
    if (window.BerichteAnwesenheitCrewInjury && window.BerichteAnwesenheitCrewInjury.sync) {
      window.BerichteAnwesenheitCrewInjury.sync();
    }
    if (window.BerichteGeraete && window.BerichteGeraete.sync) {
      window.BerichteGeraete.sync();
    }
    var crewHidden = document.getElementById('crewAssignmentsJson');
    if (crewHidden && window.BerichteKraefte) {
      // refreshBoard already syncs on interactions; trigger submit listeners via form sync
    }
  }

  function setHiddenFlag(form, name, value) {
    form.querySelectorAll('input[name="' + name + '"]').forEach(function (el) {
      el.remove();
    });
    if (!value) {
      return;
    }
    var input = document.createElement('input');
    input.type = 'hidden';
    input.name = name;
    input.value = 'true';
    form.appendChild(input);
  }

  function defaultsFromButton(btn) {
    return {
      printReport: btn.dataset.releasePrintReport === 'true',
      createGeraetewart: btn.dataset.releaseCreateGeraetewart === 'true',
      printGeraetewart: btn.dataset.releasePrintGeraetewart === 'true',
      printMaengel: btn.dataset.releasePrintMaengel === 'true',
      hasMaterialDamages: btn.dataset.releaseHasMaterialDamages === 'true'
        || hasMaterialDamagesInForm(),
      hasDeployedEquipment: btn.dataset.releaseHasDeployedEquipment === 'true'
        || hasDeployedEquipmentInForm()
    };
  }

  function hasMaterialDamagesInForm() {
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

  function hasDeployedEquipmentInForm() {
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

  function bind() {
    var btn = document.getElementById('btn-anwesenheit-save-release');
    var form = document.getElementById('anwesenheitsliste-form');
    if (!btn || !form || btn.dataset.bound === 'true') {
      return;
    }
    btn.dataset.bound = 'true';
    btn.addEventListener('click', function () {
      syncFormBeforeRelease();
      var warn = window.FwConfirm && window.FwConfirm.ask
        ? window.FwConfirm.ask(
          'Nach dem Speichern & Freigeben kann die Anwesenheitsliste von normalen Benutzern nicht mehr geändert werden. Nur Administratoren können danach noch Änderungen vornehmen. Fortfahren?',
          'Speichern & Freigeben'
        )
        : Promise.resolve(window.confirm('Nach dem Speichern & Freigeben kann nichts mehr geändert werden. Fortfahren?'));

      warn.then(function (ok) {
        if (!ok) {
          return;
        }
        var reportId = btn.dataset.reportId ? Number(btn.dataset.reportId) : null;
        var unitId = btn.dataset.unitId;
        var prepare = window.BerichteAnwesenheitRelease
          ? window.BerichteAnwesenheitRelease.prepareRelease(reportId, unitId)
          : Promise.resolve({ assignRemainingToWache: false });

        prepare.then(function (prep) {
          var defaults = defaultsFromButton(btn);
          if (prep && prep.hasMaterialDamages) {
            defaults.hasMaterialDamages = true;
          }
          if (prep && prep.hasDeployedEquipment != null) {
            defaults.hasDeployedEquipment = !!prep.hasDeployedEquipment;
          }
          if (!defaults.hasDeployedEquipment) {
            defaults.createGeraetewart = false;
            defaults.printGeraetewart = false;
          }
          return window.FwConfirm.releaseAnwesenheitsliste(defaults).then(function (result) {
            if (!result || !result.ok) {
              return;
            }
            setHiddenFlag(form, 'releaseAfterSave', true);
            ['printReport', 'createGeraetewart', 'printGeraetewart', 'printMaengel', 'assignRemainingToWache']
              .forEach(function (name) {
                setHiddenFlag(form, name, !!(result[name] || (prep && prep[name])));
              });
            if (typeof form.requestSubmit === 'function') {
              form.requestSubmit();
            } else {
              form.submit();
            }
          });
        });
      });
    });
  }

  document.addEventListener('DOMContentLoaded', bind);
})();
