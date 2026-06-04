(function () {
  function onReady(fn) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', fn);
    } else {
      fn();
    }
  }

  onReady(function () {
    document.querySelectorAll('form[data-confirm]').forEach(function (form) {
      form.addEventListener('submit', function (e) {
        var msg = form.getAttribute('data-confirm');
        if (msg && !window.confirm(msg)) e.preventDefault();
      });
    });

    document.querySelectorAll('.vehicle-row').forEach(function (row) {
      row.addEventListener('click', function (e) {
        if (e.target.closest('.table-order-actions, button, form')) return;
        var href = row.getAttribute('data-href');
        if (href) window.location.href = href;
      });
    });

    var search = document.getElementById('vehicle-search');
    if (search) {
      search.addEventListener('input', function () {
        var q = search.value.toLowerCase();
        document.querySelectorAll('.vehicle-row').forEach(function (tr) {
          tr.style.display = tr.textContent.toLowerCase().includes(q) ? '' : 'none';
        });
      });
    }

    var equipmentSearch = document.getElementById('equipment-search');
    var equipmentTbody = document.getElementById('equipment-tbody');

    function filterEquipmentRows() {
      if (!equipmentTbody || !equipmentSearch) return;
      var q = equipmentSearch.value.trim().toLowerCase();
      equipmentTbody.querySelectorAll('.equipment-row').forEach(function (tr) {
        if (!q) {
          tr.style.display = '';
          return;
        }
        var name = (tr.getAttribute('data-sort-name') || '').toLowerCase();
        var cat = (tr.getAttribute('data-sort-category') || '').toLowerCase();
        tr.style.display = name.indexOf(q) !== -1 || cat.indexOf(q) !== -1 ? '' : 'none';
      });
    }

    if (equipmentSearch) {
      equipmentSearch.addEventListener('input', filterEquipmentRows);
    }

    function initEquipmentTableSort() {
      var table = document.getElementById('equipment-table');
      if (!table || !equipmentTbody) return;
      var headers = table.querySelectorAll('th[data-sort-key]');
      var sortState = { key: null, dir: 1 };

      function applySort(key) {
        if (sortState.key === key) {
          sortState.dir *= -1;
        } else {
          sortState.key = key;
          sortState.dir = 1;
        }
        var rows = Array.prototype.slice.call(equipmentTbody.querySelectorAll('.equipment-row'));
        rows.sort(function (a, b) {
          var av = (a.getAttribute('data-sort-' + key) || '').toLowerCase();
          var bv = (b.getAttribute('data-sort-' + key) || '').toLowerCase();
          if (av < bv) return -1 * sortState.dir;
          if (av > bv) return 1 * sortState.dir;
          return 0;
        });
        rows.forEach(function (r) {
          equipmentTbody.appendChild(r);
        });
        headers.forEach(function (h) {
          var active = h.getAttribute('data-sort-key') === key;
          h.classList.remove('table__th-sort--asc', 'table__th-sort--desc');
          h.setAttribute('aria-sort', active ? (sortState.dir === 1 ? 'ascending' : 'descending') : 'none');
          if (active) {
            h.classList.add(sortState.dir === 1 ? 'table__th-sort--asc' : 'table__th-sort--desc');
          }
        });
        filterEquipmentRows();
      }

      headers.forEach(function (th) {
        function activate() {
          applySort(th.getAttribute('data-sort-key'));
        }
        th.addEventListener('click', activate);
        th.addEventListener('keydown', function (e) {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            activate();
          }
        });
      });
    }

    initEquipmentTableSort();

    /* Optional: Tabs ohne Seitenreload (Links funktionieren auch ohne JS) */
    var tabBar = document.getElementById('vehicle-detail-tabs');
    if (tabBar) {
      function showVehicleTab(tab) {
        if (!tab) tab = 'uebersicht';
        tabBar.querySelectorAll('.tab-btn').forEach(function (b) {
          var t = b.getAttribute('data-vehicle-tab');
          if (t) b.classList.toggle('tab-btn--active', t === tab);
        });
        document.querySelectorAll('.vehicle-tab-panel').forEach(function (panel) {
          panel.classList.toggle('active', panel.id === 'tab-vehicle-' + tab);
        });
      }

      tabBar.querySelectorAll('a.tab-btn[data-vehicle-tab]').forEach(function (link) {
        link.addEventListener('click', function (e) {
          e.preventDefault();
          showVehicleTab(link.getAttribute('data-vehicle-tab'));
          var url = new URL(link.href, window.location.origin);
          window.history.replaceState(null, '', url.pathname + url.search);
        });
      });
    }

    document.querySelectorAll('.room-row').forEach(function (row) {
      row.addEventListener('click', function (e) {
        if (e.target.closest('button, a, input, form')) return;
        document.getElementById('edit-room-id').value = row.getAttribute('data-id') || '';
        document.getElementById('edit-room-name').value = row.getAttribute('data-name') || '';
        document.getElementById('edit-room-desc').value = row.getAttribute('data-desc') || '';
        document.getElementById('edit-room-active').checked = row.getAttribute('data-active') === 'true';
        var overlay = document.getElementById('modal-room-edit');
        if (overlay) {
          overlay.classList.add('active');
          document.body.classList.add('modal-open');
        }
      });
    });

    document.querySelectorAll('[data-open-modal="modal-equipment-edit"]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        document.getElementById('edit-equipment-id').value = btn.getAttribute('data-id') || '';
        document.getElementById('eqNameEdit').value = btn.getAttribute('data-name') || '';
        var catId = btn.getAttribute('data-category-id') || '';
        var sel = document.getElementById('eqCategoryEdit');
        if (sel) sel.value = catId;
      });
    });

    var tplItemsWrap = document.getElementById('tpl-items-wrap');
    var btnAddTplItem = document.getElementById('btn-add-tpl-item');
    var formChecklistTemplate = document.getElementById('form-checklist-template');

    function addTemplateItemRow(focus) {
      if (!tplItemsWrap) return;
      var idx = tplItemsWrap.children.length;
      var div = document.createElement('div');
      div.className = 'tpl-item-row';
      div.innerHTML =
        '<input type="text" name="itemLabel" class="tpl-item-input field" maxlength="200" ' +
        'placeholder="Prüfpunkt ' + (idx + 1) + '" />' +
        '<button type="button" class="tpl-item-remove" title="Entfernen" aria-label="Entfernen">✕</button>';
      div.querySelector('.tpl-item-remove').addEventListener('click', function () {
        div.remove();
      });
      tplItemsWrap.appendChild(div);
      if (focus) div.querySelector('input').focus();
    }

    function resetTemplateModal() {
      var nameEl = document.getElementById('tpl-name');
      var intervalEl = document.getElementById('tpl-interval');
      if (nameEl) nameEl.value = '';
      if (intervalEl) intervalEl.value = 'manuell';
      if (tplItemsWrap) {
        tplItemsWrap.innerHTML = '';
        addTemplateItemRow(false);
      }
    }

    document.querySelectorAll('[data-open-modal="modal-checklist-template"]').forEach(function (btn) {
      btn.addEventListener('click', resetTemplateModal);
    });

    if (btnAddTplItem) {
      btnAddTplItem.addEventListener('click', function () {
        addTemplateItemRow(true);
      });
    }

    if (formChecklistTemplate) {
      formChecklistTemplate.addEventListener('submit', function (e) {
        var labels = formChecklistTemplate.querySelectorAll('input[name="itemLabel"]');
        var any = false;
        labels.forEach(function (inp) {
          if (inp.value.trim()) any = true;
        });
        if (!any) {
          e.preventDefault();
          window.alert('Mindestens einen Prüfpunkt eingeben.');
        }
      });
    }

    function escHtml(s) {
      var d = document.createElement('div');
      d.textContent = s;
      return d.innerHTML;
    }

    document.querySelectorAll('.btn-fill-checklist').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var templateId = btn.getAttribute('data-template-id');
        var templateName = btn.getAttribute('data-template-name') || 'Checkliste';
        var itemsJson = btn.getAttribute('data-items-json') || '[]';
        var items;
        try {
          items = JSON.parse(itemsJson);
        } catch (err) {
          items = [];
        }
        document.getElementById('fill-template-id').value = templateId || '';
        document.getElementById('modal-fill-title').textContent = templateName + ' ausfüllen';
        document.getElementById('fill-notes').value = '';
        document.getElementById('fill-notes').disabled = false;

        var wrap = document.getElementById('fill-items-wrap');
        wrap.innerHTML = items
          .map(function (item, i) {
            return (
              '<div class="fill-item">' +
              '<div class="fill-item__label">' + escHtml(item.label || '') + '</div>' +
              '<div class="fill-item__radios">' +
              '<input type="hidden" name="itemId" value="' + escHtml(String(item.id)) + '"/>' +
              '<label class="fill-radio-label text-success">' +
              '<input type="radio" name="result" value="ok"' + (i === 0 ? '' : '') + ' checked /> OK</label>' +
              '<label class="fill-radio-label text-danger">' +
              '<input type="radio" name="result" value="mangel" /> Mangel</label>' +
              '<label class="fill-radio-label text-muted">' +
              '<input type="radio" name="result" value="nicht_geprueft" /> Nicht geprüft</label>' +
              '<input type="text" name="itemNote" class="field field--sm" maxlength="300" placeholder="Notiz…"/>' +
              '</div></div>'
            );
          })
          .join('');

        var overlay = document.getElementById('modal-checklist-fill');
        if (overlay) {
          overlay.classList.add('active');
          document.body.classList.add('modal-open');
        }
      });
    });

    var detailModal = document.getElementById('modal-checklist-detail');
    if (detailModal) {
      detailModal.classList.add('active');
      document.body.classList.add('modal-open');
    }

    function reopenModalIfRequested(openModalKey, modalId, focusElId) {
      var urlParams = new URLSearchParams(window.location.search);
      if (urlParams.get('openModal') !== openModalKey) return;
      var overlay = document.getElementById(modalId);
      if (overlay) {
        overlay.classList.add('active');
        document.body.classList.add('modal-open');
        if (focusElId) {
          var focusEl = document.getElementById(focusElId);
          if (focusEl) focusEl.focus();
        }
      }
      urlParams.delete('openModal');
      var qs = urlParams.toString();
      window.history.replaceState(
        null,
        '',
        window.location.pathname + (qs ? '?' + qs : '') + window.location.hash
      );
    }

    reopenModalIfRequested('equipment-categories', 'modal-equipment-categories', 'newCategoryName');
    reopenModalIfRequested('vehicle-types', 'modal-vehicle-types', 'newTypeKey');
  });
})();
