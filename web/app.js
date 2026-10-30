// Copyright (c) 2026 John Blanton. All rights reserved. See LICENSE.
(() => {
  const $ = id => document.getElementById(id);
  const state = { packs: [], pack: null, section: 'all', list: [], pos: 0, drill: null, busy: false, progress: {} };
  const store = {
    get(k) { try { return JSON.parse(localStorage.getItem(k)); } catch { return null; } },
    set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* storage unavailable */ } }
  };
  const key = () => 'mcd:' + state.pack.id;

  async function api(path, body) {
    const r = await fetch(path, body ? { method: 'POST', body: JSON.stringify(body) } : undefined);
    return r.json();
  }

  function esc(s) { return s.replace(/[&<>]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c])); }
  function renderPrompt(t) { return esc(t).replace(/`([^`]+)`/g, '<code>$1</code>'); }

  async function init() {
    state.packs = await api('/api/packs');
    $('pack').innerHTML = state.packs.map(p => `<option value="${p.id}">${esc(p.title)} (${p.count})</option>`).join('');
    const last = store.get('mcd:lastPack');
    $('pack').value = state.packs.some(p => p.id === last) ? last : state.packs[0].id;
    await choosePack();
  }

  async function choosePack() {
    state.pack = state.packs.find(p => p.id === $('pack').value);
    store.set('mcd:lastPack', state.pack.id);
    state.progress = store.get(key()) || {};
    $('section').innerHTML = '<option value="all">All sections</option>' +
      state.pack.sections.map(s => `<option value="${s.id}">${esc(s.title)} (${s.count})</option>`).join('');
    state.section = 'all';
    await chooseSection();
  }

  async function chooseSection() {
    state.section = $('section').value;
    const all = await api('/api/drills?pack=' + encodeURIComponent(state.pack.id));
    state.list = all.filter(d => state.section === 'all' || d.section === state.section);
    const firstOpen = state.list.findIndex(d => !(d.id in state.progress));
    state.pos = firstOpen < 0 ? state.list.length : firstOpen;
    await show();
  }

  function updateStats() {
    const answered = state.list.filter(d => d.id in state.progress);
    const right = answered.filter(d => state.progress[d.id]).length;
    $('counter').textContent = `Drill ${Math.min(state.pos + 1, state.list.length)} of ${state.list.length}`;
    $('score').textContent = answered.length ? `${right} correct of ${answered.length} answered` : '';
    $('bar').style.width = (state.list.length ? answered.length / state.list.length * 100 : 0) + '%';
  }

  async function show() {
    updateStats();
    const finished = state.pos >= state.list.length;
    $('done').hidden = !finished;
    for (const id of ['prompt', 'before', 'after']) $(id).parentElement.hidden = false;
    document.querySelector('.code').hidden = finished;
    document.querySelector('.actions').hidden = finished;
    $('prompt').hidden = finished;
    if (finished) {
      const right = state.list.filter(d => state.progress[d.id]).length;
      $('done').innerHTML = `<h2>Set complete</h2><p>${right} of ${state.list.length} correct.</p>`;
      return;
    }
    const d = await api(`/api/drill?pack=${encodeURIComponent(state.pack.id)}&id=${state.list[state.pos].id}`);
    state.drill = d;
    $('prompt').innerHTML = renderPrompt(d.prompt);
    $('before').textContent = d.before.replace(/\n$/, '');
    $('before').hidden = d.before.trim() === '';
    $('after').textContent = d.after.replace(/\n+$/, '');
    $('indent').textContent = d.indent;
    const ta = $('answer');
    ta.value = '';
    fit();
    ta.focus();
  }

  function fit() { const ta = $('answer'); ta.style.height = 'auto'; ta.style.height = ta.scrollHeight + 'px'; }

  async function submit() {
    if (state.busy || !state.drill) return;
    const answer = $('answer').value;
    if (!answer.trim()) { $('answer').focus(); return; }
    state.busy = true; $('submit').disabled = true; $('submit').textContent = 'Checking...';
    let res;
    try {
      res = await api('/api/submit', { pack: state.pack.id, id: state.drill.id, answer });
    } catch { res = { correct: false, message: 'Could not reach the server.', expected: '', why: null }; }
    state.busy = false; $('submit').disabled = false; $('submit').textContent = 'Submit';
    state.progress[state.drill.id] = !!res.correct;
    store.set(key(), state.progress);
    if (res.correct) { flash(); state.pos++; await show(); }
    else openModal(answer, res);
  }

  function flash() {
    const f = document.createElement('div');
    f.className = 'flash'; f.textContent = 'Correct';
    document.body.appendChild(f);
    setTimeout(() => f.remove(), 900);
  }

  function openModal(answer, res) {
    updateStats();
    $('yours').textContent = answer;
    $('expected').textContent = res.expected || '';
    $('why').textContent = res.why || '';
    $('why').hidden = !res.why;
    $('output').textContent = res.message || '';
    $('detail').hidden = !res.message;
    $('modal').hidden = false;
    $('next').focus();
  }

  async function next() {
    $('modal').hidden = true;
    state.pos++;
    await show();
  }

  $('submit').addEventListener('click', submit);
  $('next').addEventListener('click', next);
  $('pack').addEventListener('change', choosePack);
  $('section').addEventListener('change', chooseSection);
  $('restart').addEventListener('click', async () => {
    if (!confirm('Clear progress for this pack?')) return;
    state.progress = {}; store.set(key(), {}); await chooseSection();
  });
  $('answer').addEventListener('input', fit);
  $('answer').addEventListener('keydown', e => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); submit(); }
    else if (e.key === 'Tab') {
      e.preventDefault();
      const t = e.target, s = t.selectionStart;
      t.setRangeText('    ', s, t.selectionEnd, 'end'); fit();
    }
  });
  document.addEventListener('keydown', e => {
    if (!$('modal').hidden && (e.key === 'Enter' || e.key === 'Escape')) { e.preventDefault(); next(); }
  });
  init();
})();
