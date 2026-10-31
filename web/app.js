// Copyright (c) 2026 John Blanton. All rights reserved. See LICENSE.
(() => {
  const $ = id => document.getElementById(id);
  const state = { packs: [], pack: null, topic: 'all', list: [], pos: 0, drill: null, busy: false, progress: {} };
  const store = {
    get(k) { try { return JSON.parse(localStorage.getItem(k)); } catch { return null; } },
    set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* storage unavailable */ } }
  };
  const key = () => 'mcd2:' + state.pack.id;

  async function api(path, body) {
    const r = await fetch(path, body ? { method: 'POST', body: JSON.stringify(body) } : undefined);
    return r.json();
  }
  function esc(s) { return String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])); }
  function renderPrompt(t) { return esc(t).replace(/`([^`]+)`/g, '<code>$1</code>'); }

  function view(name) {
    for (const v of ['viewPacks', 'viewTopics', 'viewDrill']) $(v).hidden = v !== name;
    $('controls').hidden = name === 'viewPacks';
    $('toTopics').hidden = name !== 'viewDrill';
    $('topicSel').parentElement.hidden = name !== 'viewDrill';
    if (name !== 'viewDrill') $('bar').style.width = '0';
  }

  const progressFor = pack => store.get('mcd2:' + pack.id) || {};

  // ---- screen 1: packs ----
  async function showPacks() {
    state.packs = await api('/api/packs');
    $('packList').innerHTML = state.packs.map(p => {
      const prog = progressFor(p);
      const done = Object.keys(prog).length, right = Object.values(prog).filter(Boolean).length;
      return `<button class="tile" data-pack="${esc(p.id)}"><b>${esc(p.title)}</b>
        <span>${p.topics.length} topics, ${p.count} drills</span>
        <span class="mini">${done ? `${right} of ${done} answered correctly` : 'Not started'}</span></button>`;
    }).join('');
    view('viewPacks');
  }

  // ---- screen 2: topics ----
  function showTopics(pack) {
    state.pack = pack;
    state.progress = progressFor(pack);
    $('packTitle').textContent = pack.title;
    const stat = ids => {
      const done = ids.filter(id => id in state.progress).length;
      const right = ids.filter(id => state.progress[id]).length;
      return done ? `${right} of ${done} correct` : 'Not started';
    };
    api('/api/drills?pack=' + encodeURIComponent(pack.id)).then(all => {
      state.all = all;
      const tiles = [`<button class="tile all" data-topic="all"><b>All topics</b><span>${all.length} drills</span><span class="mini">${stat(all.map(d => d.id))}</span></button>`];
      for (const t of pack.topics) {
        const ids = all.filter(d => d.topic === t.id).map(d => d.id);
        tiles.push(`<button class="tile" data-topic="${esc(t.id)}"><b>${esc(t.title)}</b><span>${ids.length} drills</span><span class="mini">${stat(ids)}</span></button>`);
      }
      $('topicList').innerHTML = tiles.join('');
      view('viewTopics');
    });
  }

  // ---- screen 3: drill ----
  async function startTopic(topic) {
    state.topic = topic;
    state.progress = progressFor(state.pack);
    const all = state.all || await api('/api/drills?pack=' + encodeURIComponent(state.pack.id));
    state.all = all;
    state.list = all.filter(d => topic === 'all' || d.topic === topic);
    const firstOpen = state.list.findIndex(d => !(d.id in state.progress));
    state.pos = firstOpen < 0 ? state.list.length : firstOpen;
    $('topicSel').innerHTML = '<option value="all">All topics</option>' +
      state.pack.topics.map(t => `<option value="${esc(t.id)}">${esc(t.title)}</option>`).join('');
    $('topicSel').value = topic;
    view('viewDrill');
    await show();
  }

  function topicTitle(id) { const t = state.pack.topics.find(t => t.id === id); return t ? t.title : id; }

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
    for (const id of ['prompt', 'answer']) $(id).hidden = finished;
    document.querySelector('#viewDrill .actions').hidden = finished;
    $('givenBox').hidden = true;
    if (finished) {
      $('badge').textContent = state.topic === 'all' ? 'All topics' : topicTitle(state.topic);
      const right = state.list.filter(d => state.progress[d.id]).length;
      $('done').innerHTML = `<h2>Topic complete</h2><p>${right} of ${state.list.length} correct.</p>
        <p>Pick another topic from the menu above, or <button id="redo" class="ghost">start this topic over</button></p>`;
      $('redo').addEventListener('click', () => {
        for (const d of state.list) delete state.progress[d.id];
        store.set(key(), state.progress); startTopic(state.topic);
      });
      return;
    }
    const d = await api(`/api/drill?pack=${encodeURIComponent(state.pack.id)}&id=${state.list[state.pos].id}`);
    state.drill = d;
    $('badge').textContent = topicTitle(d.topic);
    $('prompt').innerHTML = renderPrompt(d.prompt);
    const g = d.given || [];
    $('givenBox').hidden = g.length === 0;
    $('given').textContent = g.join('\n');
    const ta = $('answer');
    ta.value = '';
    fit();
    ta.focus();
  }

  function fit() { const ta = $('answer'); ta.style.height = 'auto'; ta.style.height = Math.max(ta.scrollHeight, 64) + 'px'; }

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
    // Score the first attempt only; a later correct retry does not overwrite a miss.
    if (!(state.drill.id in state.progress)) state.progress[state.drill.id] = !!res.correct;
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
    $('retry').focus();
  }

  function retry() {
    $('modal').hidden = true;
    const ta = $('answer');
    ta.disabled = false; ta.readOnly = false;
    setTimeout(() => {
      ta.focus();
      ta.setSelectionRange(ta.value.length, ta.value.length);
      ta.scrollIntoView({ block: 'center' });
    }, 0);
  }

  async function next() { $('modal').hidden = true; state.pos++; await show(); }

  $('packList').addEventListener('click', e => {
    const b = e.target.closest('[data-pack]'); if (!b) return;
    state.all = null;
    showTopics(state.packs.find(p => p.id === b.dataset.pack));
  });
  $('topicList').addEventListener('click', e => {
    const b = e.target.closest('[data-topic]'); if (b) startTopic(b.dataset.topic);
  });
  $('brand').addEventListener('click', showPacks);
  $('toPacks').addEventListener('click', showPacks);
  $('toTopics').addEventListener('click', () => showTopics(state.pack));
  $('topicSel').addEventListener('change', () => startTopic($('topicSel').value));
  $('reset').addEventListener('click', () => {
    if (!confirm('Clear progress for this pack?')) return;
    state.progress = {}; store.set(key(), {}); showTopics(state.pack);
  });
  $('submit').addEventListener('click', submit);
  $('next').addEventListener('click', next);
  $('retry').addEventListener('click', retry);
  $('modal').addEventListener('click', e => { if (e.target === $('modal')) retry(); });
  $('answer').addEventListener('input', fit);
  $('answer').addEventListener('keydown', e => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); submit(); }
    else if (e.key === 'Tab') {
      e.preventDefault();
      const t = e.target;
      t.setRangeText('    ', t.selectionStart, t.selectionEnd, 'end'); fit();
    }
  });
  document.addEventListener('keydown', e => {
    if ($('modal').hidden) return;
    if (e.key === 'Escape') { e.preventDefault(); retry(); }
    else if (e.key === 'Enter' && document.activeElement !== $('retry')) { e.preventDefault(); next(); }
  });
  showPacks();
})();
