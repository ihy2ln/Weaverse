(() => {
  // The web version of Weaverse: the same modes as the phone, over the synced library.
  const MODES = [
    { key: 'home', label: 'Home' },
    { key: 'novel', label: 'Novel', eyebrow: 'Write & read', desc: 'Plan, write and read your books, with every scene and codex entry.' },
    { key: 'rpg', label: 'RPG', eyebrow: 'Adventure', desc: 'Your campaigns and roleplay chats, with party and story.' },
    { key: 'games', label: 'Games', eyebrow: 'Play', desc: 'Arcade and story games set in your worlds.' },
    { key: 'browser', label: 'WeaverBrowser', eyebrow: 'Browse & chat', desc: 'Servers and channels with your codex as knowledge.' },
    { key: 'manga', label: 'Manga Studio', eyebrow: 'Draw & read', desc: 'Read, translate and color manga and comics.' },
    { key: 'notes', label: 'Brainstorm/Notes', eyebrow: 'Think', desc: 'Notes and brainstorming beside your codex.' }
  ];
  const state = {
    token: localStorage.getItem('weaverseToken') || '',
    password: sessionStorage.getItem('weaversePassword') || '',
    workspace: { books: [], scenes: [], codex: [], notes: [], threads: [], rpChats: [], media: [] },
    status: {},
    route: { mode: 'home', kind: '', id: '' }
  };
  const el = (id) => document.getElementById(id);
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  const mode = (key) => MODES.find((m) => m.key === key) || MODES[0];
  const art = (key) => '/art/' + key + '.webp';

  // ---------------------------------------------------------------- server
  async function api(path, options) {
    const opts = Object.assign({}, options || {});
    const headers = Object.assign({ 'Content-Type': 'application/json' }, opts.headers || {});
    if (state.token) headers['X-Weaverse-Token'] = state.token;
    opts.headers = headers;
    return fetch(path, opts);
  }
  async function unlock(password) {
    const res = await fetch('/api/pair', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ pin: password }) });
    const data = await res.json().catch(() => ({}));
    if (!data.ok || !data.token) return false;
    state.token = data.token;
    state.password = password;
    localStorage.setItem('weaverseToken', state.token);
    sessionStorage.setItem('weaversePassword', password);
    return true;
  }
  async function loadWorkspace() {
    if (!state.token) return;
    const res = await api('/api/workspace');
    if (res.ok) state.workspace = Object.assign(state.workspace, await res.json());
  }

  // ---------------------------------------------------------------- library helpers
  const kindOfBook = (b) => {
    const t = String(b.workType || '').toLowerCase();
    if (t === 'server') return 'browser';
    if (t === 'storyboard' || t.indexOf('manga') >= 0) return 'manga';
    return 'novel';
  };
  const isMangaChat = (c) => /manga|storyboard/i.test(c.displayMode || '');
  const books = (kind) => (state.workspace.books || []).filter((b) => kindOfBook(b) === kind);
  const campaigns = () => (state.workspace.rpChats || []).filter((c) => !isMangaChat(c));
  const mangaChats = () => (state.workspace.rpChats || []).filter(isMangaChat);
  const mediaIn = (sections) => (state.workspace.media || []).filter((m) => sections.indexOf(m.section) >= 0);
  const scenesOf = (bookId) => (state.workspace.scenes || []).filter((s) => !bookId || s.bookId === bookId || !s.bookId);
  const ago = (t) => {
    if (!t) return '';
    const m = Math.round((Date.now() - t) / 60000);
    if (m < 60) return m <= 1 ? 'just now' : m + ' min ago';
    const h = Math.round(m / 60);
    if (h < 48) return h + ' h ago';
    return new Date(t).toLocaleDateString();
  };

  // ---------------------------------------------------------------- routing
  function go(modeKey, kind, id) {
    const hash = '#/' + modeKey + (kind ? '/' + kind + '/' + encodeURIComponent(id || '') : '');
    if (location.hash === hash) render(); else location.hash = hash;
  }
  function parseHash() {
    const parts = location.hash.replace(/^#\/?/, '').split('/');
    const key = MODES.some((m) => m.key === parts[0]) ? parts[0] : 'home';
    state.route = { mode: key, kind: parts[1] || '', id: decodeURIComponent(parts[2] || '') };
  }
  window.addEventListener('hashchange', () => { parseHash(); render(); });

  function setBackdrop(key, level) {
    const img = el('backdropImg');
    if (img.getAttribute('src') !== art(key)) img.setAttribute('src', art(key));
    el('backdrop').className = 'backdrop ' + (level || '');
  }

  function renderTabs() {
    el('tabs').innerHTML = MODES.map((m) =>
      '<button type="button" data-mode="' + m.key + '" class="' + (m.key === state.route.mode ? 'on' : '') + '">' + esc(m.label) + '</button>'
    ).join('');
    el('tabs').querySelectorAll('button').forEach((b) => b.addEventListener('click', () => go(b.dataset.mode)));
  }

  function render() {
    renderTabs();
    const r = state.route;
    const stage = el('stage');
    window.scrollTo(0, 0);
    if (r.mode === 'home') { setBackdrop('home', ''); stage.innerHTML = homeHtml(); wireHome(); return; }
    setBackdrop(r.mode, r.kind ? 'quiet' : 'hero');
    const views = { novel: novelView, rpg: rpgView, games: gamesView, browser: browserView, manga: mangaView, notes: notesView };
    (views[r.mode] || gamesView)(stage, r);
  }

  function banner(key, title, sub, back) {
    const m = mode(key);
    return '<section class="banner" style="background-image:url(' + art(key) + ')">' +
      (back ? '<button class="back" type="button" data-back="' + key + '" aria-label="Back">&#8592;</button>' : '') +
      '<div><div class="eyebrow">' + esc(m.label) + '</div><h1>' + esc(title) + '</h1>' + (sub ? '<div class="sub">' + esc(sub) + '</div>' : '') + '</div></section>';
  }
  function wireBack(stage) {
    stage.querySelectorAll('[data-back]').forEach((b) => b.addEventListener('click', () => go(b.dataset.back)));
    stage.querySelectorAll('[data-go]').forEach((b) => b.addEventListener('click', () => {
      const [m, k, id] = b.dataset.go.split('|');
      go(m, k, id);
    }));
  }
  const cardHtml = (go, title, sub) => '<button type="button" class="card" data-go="' + esc(go) + '"><h4>' + esc(title) + '</h4><div class="sub">' + esc(sub) + '</div></button>';
  const emptyHtml = (text) => '<div class="empty">' + esc(text) + '</div>';

  // ---------------------------------------------------------------- Home
  function lastTouched() {
    const items = []
      .concat(books('novel').map((b) => ({ t: b.updatedAt, label: b.title, where: 'Novel', go: 'novel|book|' + b.id })))
      .concat(campaigns().map((c) => ({ t: c.updatedAt, label: c.title, where: 'RPG', go: 'rpg|chat|' + c.id })))
      .concat(mangaChats().map((c) => ({ t: c.updatedAt, label: c.title, where: 'Manga Studio', go: 'manga|chat|' + c.id })))
      .concat((state.workspace.notes || []).map((n) => ({ t: n.updatedAt, label: n.title, where: 'Brainstorm/Notes', go: 'notes|note|' + n.id })));
    return items.sort((a, b) => (b.t || 0) - (a.t || 0))[0];
  }
  function homeHtml() {
    const hour = new Date().getHours();
    const greeting = hour < 5 ? 'Good night' : hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
    const last = lastTouched();
    const posters = MODES.filter((m) => m.key !== 'home').map((m) =>
      '<button type="button" class="poster" data-go="' + m.key + '" style="background-image:url(' + art(m.key) + ')"><div class="body">' +
      '<div class="eyebrow">' + esc(m.eyebrow) + '</div><h3>' + esc(m.label) + '</h3><p>' + esc(m.desc) + '</p></div></button>'
    ).join('');
    const novelBooks = books('novel');
    const rows = [];
    if (novelBooks.length) rows.push(row('Continue in Novel', novelBooks.length, novelBooks.slice(0, 12).map((b) => cardHtml('novel|book|' + b.id, b.title, scenesOf(b.id).length + ' scenes · ' + ago(b.updatedAt))).join('')));
    if (campaigns().length) rows.push(row('Continue in RPG', campaigns().length, campaigns().slice(0, 12).map((c) => cardHtml('rpg|chat|' + c.id, c.title, (c.displayMode || 'roleplay') + ' · ' + ago(c.updatedAt))).join('')));
    if ((state.workspace.notes || []).length) rows.push(row('Recent notes', state.workspace.notes.length, state.workspace.notes.slice(0, 12).map((n) => cardHtml('notes|note|' + n.id, n.title, n.bodyPreview)).join('')));
    return '<section class="home-hero">' +
      '<div class="wordmark"><span class="w">W</span><span>WEAVERSE</span></div>' +
      '<div class="eyebrow">' + greeting + '</div>' +
      '<div class="headline">Write it, play it, draw it, live it.</div>' +
      '<p class="lastTime">' + (last ? 'Last time: ' + esc(last.label) + ' · ' + esc(last.where) : 'Your synced library appears here once the phone pushes it.') + '</p>' +
      '<div class="cta">' +
      '<button id="resumeBtn" class="btn primary" type="button"' + (last ? ' data-go="' + esc(last.go) + '"' : ' disabled') + '>&#9654; Resume</button>' +
      '<button id="createBtn" class="btn secondary" type="button">+ Create</button>' +
      '<div id="createMenu" class="menu" hidden>' +
      '<button type="button" data-create="note">New note</button>' +
      '<div class="note">New novels, campaigns and manga projects are made on the phone and arrive here when it syncs.</div>' +
      '</div></div></section>' +
      '<div class="rowHead"><h2>Choose a mode</h2><span class="count">' + (MODES.length - 1) + ' modes</span></div>' +
      '<div class="posters">' + posters + '</div>' + rows.join('');
  }
  function row(title, count, cards) {
    return '<div class="rowHead"><h2>' + esc(title) + '</h2><span class="count">' + count + '</span></div><div class="cards">' + cards + '</div>';
  }
  function wireHome() {
    const stage = el('stage');
    wireBack(stage);
    el('createBtn').addEventListener('click', () => { const m = el('createMenu'); m.hidden = !m.hidden; });
    stage.querySelector('[data-create="note"]').addEventListener('click', () => go('notes', 'note', 'new'));
  }

  // ---------------------------------------------------------------- Novel
  function novelView(stage, r) {
    if (!r.kind) {
      const shelf = books('novel');
      stage.innerHTML = banner('novel', 'Bookshelf', shelf.length + ' books') +
        (shelf.length ? '<div class="cards">' + shelf.map((b) => cardHtml('novel|book|' + b.id, b.title, scenesOf(b.id).length + ' scenes · ' + ago(b.updatedAt))).join('') + '</div>'
          : emptyHtml('No books yet. Push from the phone, or import a Novelcrafter ZIP in Settings.'));
      wireBack(stage);
      return;
    }
    const bookId = r.kind === 'book' ? r.id : '';
    const sceneId = r.kind === 'scene' ? r.id : '';
    const scene = (state.workspace.scenes || []).find((s) => s.id === sceneId);
    const book = (state.workspace.books || []).find((b) => b.id === (bookId || (scene && scene.bookId))) || {};
    const scenes = scenesOf(book.id);
    let lastGroup = '';
    const list = scenes.map((s) => {
      const group = [s.actTitle, s.chapterTitle].filter(Boolean).join(' · ');
      const head = group && group !== lastGroup ? '<li class="group">' + esc(group) + '</li>' : '';
      lastGroup = group || lastGroup;
      return head + '<li data-scene="' + esc(s.id) + '" class="' + (s.id === sceneId ? 'on' : '') + '"><strong>' + esc(s.title) + '</strong><div class="sub">' + esc(s.wordCount + ' words · ' + (s.status || 'draft')) + '</div></li>';
    }).join('');
    stage.innerHTML = banner('novel', book.title || 'Manuscript', scenes.length + ' scenes', true) +
      '<div class="work"><div class="pane"><h3>Manuscript</h3><ul class="list">' + (list || '<li>No scenes</li>') + '</ul></div>' +
      '<div class="pane">' + (sceneId ? '<input id="sceneTitle" placeholder="Scene title" /><input id="sceneSummary" placeholder="Summary / beats" />' +
        '<textarea id="sceneBody" placeholder="Write the scene…"></textarea><div class="row"><button id="saveScene" class="solid" type="button">Save scene</button><span id="sceneMeta" class="meta"></span></div><p id="sceneMsg" class="msg"></p>'
        : emptyHtml('Pick a scene to read or write it. Saved scenes reach the phone when it pulls.')) + '</div>' +
      '<div class="pane codexPane"><h3>Codex</h3><ul class="list">' + codexHtml() + '</ul></div></div>';
    wireBack(stage);
    stage.querySelectorAll('[data-scene]').forEach((li) => li.addEventListener('click', () => go('novel', 'scene', li.dataset.scene)));
    if (sceneId) openScene(sceneId);
  }
  function codexHtml() {
    return (state.workspace.codex || []).map((c) => '<li><strong>' + esc(c.name) + '</strong><div class="sub">' + esc(c.category || c.bodyPreview) + '</div></li>').join('') || '<li>No codex entries</li>';
  }
  async function openScene(id) {
    const res = await api('/api/scenes/' + encodeURIComponent(id));
    if (!res.ok) { el('sceneMsg').textContent = 'Could not open this scene'; return; }
    const scene = await res.json();
    el('sceneTitle').value = scene.title || '';
    el('sceneSummary').value = scene.summary || '';
    el('sceneBody').value = scene.body || '';
    el('sceneMeta').textContent = (scene.wordCount || 0) + ' words · ' + (scene.status || 'draft');
    el('saveScene').addEventListener('click', async () => {
      const body = { id: id, title: el('sceneTitle').value || 'Untitled', summary: el('sceneSummary').value || '', body: el('sceneBody').value || '' };
      const save = await api('/api/scenes/' + encodeURIComponent(id), { method: 'PUT', body: JSON.stringify(body) });
      const msg = el('sceneMsg');
      msg.textContent = save.ok ? 'Saved — the phone gets it on its next pull.' : 'Save failed';
      msg.className = 'msg ' + (save.ok ? 'ok' : 'bad');
      if (save.ok) await loadWorkspace();
    });
  }

  // ---------------------------------------------------------------- chats (RPG, Manga, servers)
  function chatView(stage, r, key, title, items, empty, endpoint) {
    if (!r.kind) {
      stage.innerHTML = banner(key, title, items.length + ' total') +
        (items.length ? '<div class="cards">' + items.map((c) => cardHtml(key + '|chat|' + c.id, c.title || c.name, (c.displayMode || 'chat') + ' · ' + ago(c.updatedAt))).join('') + '</div>' : emptyHtml(empty));
      wireBack(stage);
      return false;
    }
    const chat = items.find((c) => c.id === r.id) || {};
    stage.innerHTML = banner(key, chat.title || chat.name || title, 'Read-only here — play and write on the phone', true) +
      '<div class="work two"><div class="pane"><h3>' + esc(title) + '</h3><ul class="list">' +
      items.map((c) => '<li data-chat="' + esc(c.id) + '" class="' + (c.id === r.id ? 'on' : '') + '"><strong>' + esc(c.title || c.name) + '</strong><div class="sub">' + esc(ago(c.updatedAt)) + '</div></li>').join('') +
      '</ul></div><div class="pane"><div id="chatLog" class="log"><p class="lead">Loading…</p></div></div></div>';
    wireBack(stage);
    stage.querySelectorAll('[data-chat]').forEach((li) => li.addEventListener('click', () => go(key, 'chat', li.dataset.chat)));
    api(endpoint(r.id)).then((res) => (res.ok ? res.json() : [])).then((lines) => {
      el('chatLog').innerHTML = (lines || []).map((m) =>
        '<div class="bubble ' + (m.role === 'user' ? 'user' : '') + '"><div class="who">' + esc(m.role) + '</div>' + esc(m.text) + '</div>'
      ).join('') || '<p class="lead">Nothing written in this chat yet.</p>';
    });
    return true;
  }
  function rpgView(stage, r) {
    chatView(stage, r, 'rpg', 'Campaigns', campaigns(), 'No campaigns yet. Start one on the phone and push it.', (id) => '/api/rp/' + encodeURIComponent(id) + '/messages');
  }
  function mangaView(stage, r) {
    const shown = chatView(stage, r, 'manga', 'Library', mangaChats(), 'No manga projects synced yet. Read and edit manga on the phone; projects arrive here when it pushes.', (id) => '/api/rp/' + encodeURIComponent(id) + '/messages');
    if (!shown) {
      const pictures = mediaIn(['manga', 'roleplay']);
      if (pictures.length) {
        stage.insertAdjacentHTML('beforeend', '<div class="rowHead"><h2>Pictures</h2><span class="count">' + pictures.length + '</span></div><div class="gallery">' +
          pictures.map((m) => '<figure><img loading="lazy" src="/api/media/' + encodeURIComponent(m.id) + '" alt="' + esc(m.caption) + '" /><figcaption>' + esc(m.caption || m.section) + '</figcaption></figure>').join('') + '</div>');
      }
    }
  }
  function browserView(stage, r) {
    const servers = books('browser');
    const threads = (state.workspace.threads || []).map((t) => ({ id: t.id, title: t.name, updatedAt: t.updatedAt, displayMode: 'channel' }));
    const shown = chatView(stage, r, 'browser', 'Channels', threads, 'No channels synced yet. Servers and chats live on the phone; they arrive here when it pushes.', (id) => '/api/threads/' + encodeURIComponent(id) + '/messages');
    if (!shown && servers.length) {
      stage.insertAdjacentHTML('beforeend', '<div class="rowHead"><h2>Your servers</h2><span class="count">' + servers.length + '</span></div><div class="cards">' +
        servers.map((s) => '<div class="card"><h4>' + esc(s.title) + '</h4><div class="sub">' + esc(ago(s.updatedAt)) + '</div></div>').join('') + '</div>');
    }
  }
  function gamesView(stage) {
    stage.innerHTML = banner('games', 'Games', 'Played on the phone') +
      emptyHtml('Games run on the phone. Their worlds come from your books and codex, which you can edit here under Novel.');
    wireBack(stage);
  }

  // ---------------------------------------------------------------- Notes
  function notesView(stage, r) {
    const notes = state.workspace.notes || [];
    const id = r.kind === 'note' ? r.id : '';
    stage.innerHTML = banner('notes', 'Notes', notes.length + ' notes', !!id) +
      '<div class="work two"><div class="pane"><div class="row" style="margin-bottom:8px"><button id="newNote" class="solid" type="button">+ New note</button></div><ul class="list">' +
      notes.map((n) => '<li data-note="' + esc(n.id) + '" class="' + (n.id === id ? 'on' : '') + '"><strong>' + esc(n.title) + '</strong><div class="sub">' + esc(n.bodyPreview) + '</div></li>').join('') +
      '</ul></div><div class="pane">' + (id ? '<input id="noteTitle" placeholder="Note title" /><textarea id="noteBody" placeholder="Speak, type, or paste…"></textarea>' +
        '<div class="row"><button id="saveNote" class="solid" type="button">Save note</button></div><p id="noteMsg" class="msg"></p>'
        : emptyHtml('Pick a note or start a new one.')) + '</div></div>';
    wireBack(stage);
    stage.querySelectorAll('[data-note]').forEach((li) => li.addEventListener('click', () => go('notes', 'note', li.dataset.note)));
    el('newNote').addEventListener('click', () => go('notes', 'note', 'new'));
    if (!id) return;
    const noteId = id === 'new' ? (crypto.randomUUID ? crypto.randomUUID() : String(Date.now())) : id;
    if (id === 'new') { el('noteTitle').value = 'New note'; }
    else api('/api/notes/' + encodeURIComponent(id)).then((res) => (res.ok ? res.json() : null)).then((note) => {
      if (!note) return;
      el('noteTitle').value = note.title || '';
      el('noteBody').value = note.body || '';
    });
    el('saveNote').addEventListener('click', async () => {
      const body = { id: noteId, title: el('noteTitle').value || 'Untitled', body: el('noteBody').value || '' };
      const res = await api('/api/notes/' + encodeURIComponent(noteId), { method: 'PUT', body: JSON.stringify(body) });
      const msg = el('noteMsg');
      msg.textContent = res.ok ? 'Saved — the phone gets it on its next pull.' : 'Save failed';
      msg.className = 'msg ' + (res.ok ? 'ok' : 'bad');
      if (res.ok) { await loadWorkspace(); if (id === 'new') go('notes', 'note', noteId); }
    });
  }

  // ---------------------------------------------------------------- Search
  function openSheet(id) { el(id).hidden = false; }
  document.querySelectorAll('[data-close]').forEach((b) => b.addEventListener('click', () => { el(b.dataset.close).hidden = true; }));
  document.querySelectorAll('.sheet').forEach((s) => s.addEventListener('click', (e) => { if (e.target === s) s.hidden = true; }));
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') document.querySelectorAll('.sheet').forEach((s) => { s.hidden = true; }); });
  el('searchBtn').addEventListener('click', () => { openSheet('search'); el('searchInput').focus(); runSearch(); });
  el('searchInput').addEventListener('input', runSearch);
  function runSearch() {
    const q = el('searchInput').value.trim().toLowerCase();
    const w = state.workspace;
    const hit = (s) => !q || String(s || '').toLowerCase().indexOf(q) >= 0;
    const results = []
      .concat((w.books || []).filter((b) => hit(b.title)).map((b) => [kindOfBook(b) === 'novel' ? 'novel|book|' + b.id : kindOfBook(b) + '|', b.title, mode(kindOfBook(b)).label]))
      .concat((w.scenes || []).filter((s) => hit(s.title) || hit(s.summary)).map((s) => ['novel|scene|' + s.id, s.title, 'Scene · ' + [s.actTitle, s.chapterTitle].filter(Boolean).join(' · ')]))
      .concat((w.codex || []).filter((c) => hit(c.name) || hit(c.bodyPreview)).map((c) => ['novel|', c.name, 'Codex · ' + (c.category || '')]))
      .concat((w.notes || []).filter((n) => hit(n.title) || hit(n.bodyPreview)).map((n) => ['notes|note|' + n.id, n.title, 'Note']))
      .concat((w.rpChats || []).filter((c) => hit(c.title)).map((c) => [(isMangaChat(c) ? 'manga' : 'rpg') + '|chat|' + c.id, c.title, isMangaChat(c) ? 'Manga Studio' : 'RPG']))
      .slice(0, 60);
    el('searchResults').innerHTML = results.map((r) => cardHtml(r[0], r[1], r[2])).join('') || '<p class="lead">Nothing found.</p>';
    el('searchResults').querySelectorAll('[data-go]').forEach((b) => b.addEventListener('click', () => {
      el('search').hidden = true;
      const [m, k, id] = b.dataset.go.split('|');
      go(m, k, id);
    }));
  }

  // ---------------------------------------------------------------- Settings
  el('settingsBtn').addEventListener('click', () => { openSheet('settings'); checkHarnesses(); });
  el('harnessRefresh').addEventListener('click', checkHarnesses);
  async function checkHarnesses() {
    const box = el('harnessList');
    box.innerHTML = '<p class="lead">Checking…</p>';
    const pin = state.status.pairPin || state.password;
    if (!pin) { box.innerHTML = '<p class="lead">Unlock with the sync password to see the PC harnesses.</p>'; return; }
    try {
      const res = await fetch('/api/ai/capabilities', { headers: { Authorization: 'Bearer ' + pin } });
      if (res.status === 404) { box.innerHTML = '<p class="lead">PC harnesses run in Weaverse Desktop on a PC; this host is the phone.</p>'; return; }
      if (!res.ok) { box.innerHTML = '<p class="lead">The PC rejected the password.</p>'; return; }
      const caps = await res.json();
      const names = { claude: 'Claude Code', codex: 'ChatGPT · Codex', comfyui: 'ComfyUI' };
      const fixes = {
        claude: 'Install Claude Code, run "claude" in a terminal and sign in with /login.',
        codex: 'Run "npm install -g @openai/codex", then "codex login" with your ChatGPT account.',
        comfyui: 'Start ComfyUI on this PC (it should answer at 127.0.0.1:8188).'
      };
      box.innerHTML = (caps.harnesses || []).map((h) =>
        '<div class="harness ' + (h.available ? 'ok' : '') + '"><span class="dot"></span><div><strong>' + esc(names[h.id] || h.id) + '</strong>' +
        '<div class="sub">' + esc(h.detail) + (h.models && h.models.length ? ' · ' + esc(h.models.join(', ')) : '') + '</div>' +
        (h.available ? (h.id === 'claude' ? '<div class="sub">If the phone reports it is signed out, run "claude" here and sign in with /login.</div>' : '')
          : '<div class="sub">' + esc(fixes[h.id] || '') + '</div>') + '</div></div>'
      ).join('');
    } catch (e) {
      box.innerHTML = '<p class="lead">Could not check: ' + esc(e.message) + '</p>';
    }
  }
  el('exportBtn').addEventListener('click', () => {
    const blob = new Blob([JSON.stringify(state.workspace || {}, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'weaverse-export.json';
    a.click();
  });
  el('importBtn').addEventListener('click', () => el('importZip').click());
  el('importZip').addEventListener('change', async (ev) => {
    const file = ev.target.files && ev.target.files[0];
    ev.target.value = '';
    if (!file) return;
    const msg = el('libraryMsg');
    msg.textContent = 'Importing…';
    const res = await fetch('/api/import', { method: 'POST', headers: { 'X-Weaverse-Token': state.token, 'Content-Type': 'application/zip' }, body: file });
    const data = await res.json().catch(() => ({ ok: false, message: 'Import failed' }));
    msg.textContent = data.ok ? 'Imported ' + (data.bookTitle || 'book') : (data.message || 'Import failed');
    msg.className = 'msg ' + (data.ok ? 'ok' : 'bad');
    if (data.ok) { await loadWorkspace(); render(); }
  });
  el('pullBtn').addEventListener('click', async () => {
    const res = await api('/api/sync/pull');
    const msg = el('libraryMsg');
    if (!res.ok) { msg.textContent = 'Nothing to download yet'; msg.className = 'msg bad'; return; }
    const blob = await res.blob();
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'weaverse-sync.zip';
    a.click();
    msg.textContent = 'Downloaded the sync package';
    msg.className = 'msg ok';
  });

  // ---------------------------------------------------------------- start
  (async () => {
    parseHash();
    render();
    try {
      state.status = await (await fetch('/api/status')).json();
      // The host only shows its password to a browser on the same device; elsewhere it is typed.
      const password = state.status.pairPin || '';
      el('passwordBox').textContent = password || 'Shown only on the PC running Weaverse';
      el('lanHint').textContent = state.status.lanHint
        ? 'Phone address: http://' + String(state.status.lanHint).split(',')[0].trim() + ':' + state.status.port
        : '';
      if (password) {
        await unlock(password);
      } else {
        const valid = !!state.token && !!(await (await api('/api/session')).json().catch(() => ({}))).ok;
        let tries = 0;
        while (!valid && tries < 5) {
          const typed = window.prompt('Enter the sync password shown on the PC running Weaverse:');
          if (typed === null) break;
          if (await unlock(typed.trim())) break;
          tries++;
        }
      }
      el('statusChip').textContent = state.token ? 'Synced library · ' + (state.status.deviceName || 'Weaverse') : 'Locked';
      await loadWorkspace();
      render();
    } catch (e) {
      el('statusChip').textContent = 'Offline';
    }
  })();
})();
