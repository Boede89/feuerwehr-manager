(function () {
  const ricList = document.getElementById('ricEditList');
  const addRicBtn = document.getElementById('addRicBtn');

  function createRicRow(value) {
    const row = document.createElement('div');
    row.className = 'ric-edit-row';
    const input = document.createElement('input');
    input.type = 'text';
    input.name = 'ricCodes';
    input.maxLength = 64;
    input.placeholder = 'RIC';
    input.value = value || '';
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'btn btn-secondary ric-remove';
    btn.setAttribute('aria-label', 'RIC entfernen');
    btn.textContent = '×';
    btn.addEventListener('click', () => row.remove());
    row.appendChild(input);
    row.appendChild(btn);
    return row;
  }

  if (ricList) {
    ricList.querySelectorAll('.ric-remove').forEach((btn) => {
      btn.addEventListener('click', () => btn.closest('.ric-edit-row')?.remove());
    });
    if (ricList.children.length === 0) {
      ricList.appendChild(createRicRow(''));
    }
  }

  if (addRicBtn && ricList) {
    addRicBtn.addEventListener('click', () => {
      ricList.appendChild(createRicRow(''));
      ricList.lastElementChild?.querySelector('input')?.focus();
    });
  }
})();
