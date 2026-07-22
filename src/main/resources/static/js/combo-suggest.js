(function () {
  'use strict';

  function initComboSuggest(root) {
    var input = root.querySelector('.combo-suggest__input');
    var list = root.querySelector('.combo-suggest__list');
    if (!input || !list) {
      return;
    }

    var options = Array.from(list.querySelectorAll('.combo-suggest__option')).map(function (el) {
      return {
        value: el.getAttribute('data-value') || el.textContent.trim(),
        element: el,
        search: (el.getAttribute('data-value') || el.textContent || '').trim().toLocaleLowerCase('de'),
        category: (el.getAttribute('data-category') || '').trim().toLocaleLowerCase('de')
      };
    });

    var minChars = Number(root.getAttribute('data-combo-min') || '1');
    var categorySelectId = root.getAttribute('data-combo-category-select');
    var categorySelect = categorySelectId ? document.getElementById(categorySelectId) : null;
    var activeIndex = -1;

    function currentCategory() {
      if (!categorySelect) {
        return '';
      }
      return (categorySelect.value || '').trim().toLocaleLowerCase('de');
    }

    function optionMatchesCategory(opt) {
      if (!categorySelect) {
        return true;
      }
      if (!opt.category) {
        return true;
      }
      return opt.category === currentCategory();
    }

    function closeList() {
      list.hidden = true;
      activeIndex = -1;
      list.querySelectorAll('.combo-suggest__option--active').forEach(function (el) {
        el.classList.remove('combo-suggest__option--active');
      });
    }

    function openList() {
      list.hidden = false;
    }

    function setActive(index) {
      var visible = options.filter(function (o) {
        return o.element.style.display !== 'none';
      });
      list.querySelectorAll('.combo-suggest__option--active').forEach(function (el) {
        el.classList.remove('combo-suggest__option--active');
      });
      if (index < 0 || index >= visible.length) {
        activeIndex = -1;
        return;
      }
      activeIndex = index;
      visible[index].element.classList.add('combo-suggest__option--active');
      visible[index].element.scrollIntoView({ block: 'nearest' });
    }

    function filterOptions(query, showAllOnFocus) {
      var q = (query || '').trim().toLocaleLowerCase('de');
      var visibleCount = 0;
      options.forEach(function (opt) {
        var show;
        if (!optionMatchesCategory(opt)) {
          show = false;
        } else if (showAllOnFocus && q.length === 0) {
          show = true;
        } else {
          show = q.length >= minChars && opt.search.indexOf(q) !== -1;
        }
        opt.element.style.display = show ? '' : 'none';
        if (show) {
          visibleCount++;
        }
      });
      if (visibleCount > 0 && (showAllOnFocus || q.length >= minChars)) {
        openList();
        setActive(0);
      } else {
        closeList();
      }
    }

    function syncPersonIdFromValue() {
      var value = (input.value || '').trim();
      if (!value) {
        delete input.dataset.personId;
        return;
      }
      var match = options.find(function (opt) {
        return optionMatchesCategory(opt)
          && opt.value.localeCompare(value, 'de', { sensitivity: 'accent' }) === 0;
      });
      if (match && match.element.getAttribute('data-person-id')) {
        input.dataset.personId = match.element.getAttribute('data-person-id');
      } else {
        delete input.dataset.personId;
      }
    }

    function selectOption(opt) {
      input.value = opt.value;
      var personId = opt.element.getAttribute('data-person-id');
      if (personId) {
        input.dataset.personId = personId;
      } else {
        delete input.dataset.personId;
      }
      closeList();
      input.dispatchEvent(new Event('change', { bubbles: true }));
    }

    input.addEventListener('input', function () {
      syncPersonIdFromValue();
      filterOptions(input.value);
    });

    input.addEventListener('focus', function () {
      filterOptions(input.value, true);
    });

    input.addEventListener('click', function () {
      if (document.activeElement === input) {
        filterOptions(input.value, true);
      }
    });

    input.addEventListener('keydown', function (e) {
      if (list.hidden) {
        return;
      }
      var visible = options.filter(function (o) {
        return o.element.style.display !== 'none';
      });
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setActive(Math.min(activeIndex + 1, visible.length - 1));
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setActive(Math.max(activeIndex - 1, 0));
      } else if (e.key === 'Enter' && activeIndex >= 0 && visible[activeIndex]) {
        e.preventDefault();
        selectOption(visible[activeIndex]);
      } else if (e.key === 'Escape') {
        closeList();
      }
    });

    list.addEventListener('mousedown', function (e) {
      var option = e.target.closest('.combo-suggest__option');
      if (!option) {
        return;
      }
      e.preventDefault();
      var match = options.find(function (o) {
        return o.element === option;
      });
      if (match) {
        selectOption(match);
      }
    });

    document.addEventListener('click', function (e) {
      if (!root.contains(e.target)) {
        closeList();
      }
    });

    if (categorySelect) {
      categorySelect.addEventListener('change', function () {
        syncPersonIdFromValue();
        if (document.activeElement === input) {
          filterOptions(input.value, true);
        } else {
          closeList();
        }
      });
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-combo-suggest]').forEach(initComboSuggest);
  });
})();
