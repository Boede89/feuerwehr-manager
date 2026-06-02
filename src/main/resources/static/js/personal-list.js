(function () {
  const search = document.getElementById('personSearch');
  const list = document.getElementById('personList');
  const emptyHint = document.getElementById('personSearchEmpty');
  const countEl = document.getElementById('personCountVisible');
  if (!search || !list) return;

  const items = () => Array.from(list.querySelectorAll('.person-list-item'));

  function filter() {
    const q = search.value.trim().toLowerCase();
    let visible = 0;
    items().forEach((el) => {
      const hay = el.getAttribute('data-search') || '';
      const show = !q || hay.includes(q);
      el.classList.toggle('is-hidden', !show);
      if (show) visible++;
    });
    if (emptyHint) {
      emptyHint.style.display = q && visible === 0 && items().length > 0 ? 'block' : 'none';
    }
    if (countEl && items().length > 0) {
      countEl.textContent = String(visible);
    }
  }

  search.addEventListener('input', filter);
})();
