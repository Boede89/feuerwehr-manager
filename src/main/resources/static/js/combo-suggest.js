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
      };
    });

    var minChars = Number(root.getAttribute('data-combo-min') || '1');
    var activeIndex = -1;

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

    function filterOptions(query) {
      var q = (query || '').trim().toLocaleLowerCase('de');
      var visibleCount = 0;
      options.forEach(function (opt) {
        var show = q.length >= minChars && opt.search.indexOf(q) !== -1;
        opt.element.style.display = show ? '' : 'none';
        if (show) {
          visibleCount++;
        }
      });
      if (visibleCount > 0 && q.length >= minChars) {
        openList();
        setActive(0);
      } else {
        closeList();
      }
    }

    function selectOption(opt) {
      input.value = opt.value;
      closeList();
      input.dispatchEvent(new Event('change', { bubbles: true }));
    }

    input.addEventListener('input', function () {
      filterOptions(input.value);
    });

    input.addEventListener('focus', function () {
      if (input.value.trim().length >= minChars) {
        filterOptions(input.value);
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
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-combo-suggest]').forEach(initComboSuggest);
  });
})();
