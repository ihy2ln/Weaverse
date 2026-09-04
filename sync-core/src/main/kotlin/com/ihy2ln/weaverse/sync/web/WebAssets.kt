package com.ihy2ln.weaverse.sync.web

fun webIndexHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Weaverse — Write</title>
  <link rel="stylesheet" href="/app.css" />
</head>
<body>
  <div class="app">
    <header class="chrome">
      <div class="brand"><span class="mark">IF</span><strong>Weaverse</strong></div>
      <nav id="tabs" class="tabs" aria-label="Workspace">
        <button data-tab="plan" class="on">Plan</button>
        <button data-tab="write">Write</button>
        <button data-tab="chat">Chat</button>
        <button data-tab="review">Review</button>
        <button data-tab="roleplay">Roleplay</button>
        <button data-tab="notes">Notes</button>
        <button data-tab="pictures">Pictures</button>
      </nav>
      <div class="chrome-right">
        <input id="importZip" type="file" accept=".zip,.json,application/zip,application/json" hidden />
        <button id="importBtn" type="button" title="Import novels, roleplay, or notes">Import</button>
        <button id="exportBtn" type="button" title="Export novels, roleplay, and notes">Export</button>
        <div id="passwordBox" class="pin" title="Single sync password">••••••</div>
        <div id="statusChip" class="chip">Connecting…</div>
      </div>
    </header>

    <div class="workspace">
      <aside class="rail" id="rail">
        <h2 id="railTitle">Manuscript</h2>
        <ul id="railList" class="list"></ul>
      </aside>
      <main class="stage">
        <div id="view-plan" class="view on">
          <h2>Plan</h2>
          <p class="lead">Acts, chapters, and scenes — same outline as the Android / desktop novel workspace. Import a Novelcrafter ZIP (novel.md or novel.docx + characters/) to fill this.</p>
          <div id="planArt" class="art-row"></div>
          <div id="planGrid" class="cards"></div>
        </div>
        <div id="view-write" class="view">
          <div id="writeArt" class="art-row"></div>
          <input id="sceneTitle" placeholder="Scene title" />
          <input id="sceneSummary" placeholder="Scene summary / beats" />
          <textarea id="sceneBody" placeholder="Write the scene…"></textarea>
          <div class="row">
            <button id="saveSceneBtn" type="button">Save scene</button>
            <span id="sceneMeta" class="meta"></span>
          </div>
          <p id="writeMsg" class="msg"></p>
        </div>
        <div id="view-chat" class="view">
          <h2>Workshop chat</h2>
          <div id="chatLog" class="log"></div>
        </div>
        <div id="view-review" class="view">
          <h2>Review</h2>
          <p class="lead">Word counts and scene status across the manuscript. Roleplay / manga / DM stay on the Roleplay tab.</p>
          <div id="reviewList" class="cards"></div>
        </div>
        <div id="view-roleplay" class="view">
          <h2>Roleplay · Messenger · DM · Manga</h2>
          <p class="lead">Adams Haven RPG scenes and gacha cards seed into Roleplay. Chats stay mode-isolated (Messenger, 3×3 DM, 6×6 manga).</p>
          <div id="rpgScenes" class="cards"></div>
          <div id="rpArt" class="art-row"></div>
          <div id="rpLog" class="log"></div>
        </div>
        <div id="view-notes" class="view">
          <input id="noteTitle" placeholder="Note title" />
          <textarea id="noteBody" placeholder="Speak, type, or paste…"></textarea>
          <div class="row">
            <button id="newNoteBtn" type="button">New note</button>
            <button id="saveNoteBtn" type="button">Save note</button>
            <button id="pullBtn" type="button" class="ghost">Download package</button>
          </div>
          <p id="editMsg" class="msg"></p>
        </div>
        <div id="view-pictures" class="view">
          <h2>Pictures</h2>
          <p class="lead">Shared gallery — story and picture focus. Codex and notes stay visible in every mode.</p>
          <div id="pictureGrid" class="art-row"></div>
        </div>
      </main>
      <aside class="codex" id="codexPane">
        <h2>Codex · shared</h2>
        <p class="lead">Every book and mode</p>
        <ul id="codexList" class="list"></ul>
        <h2>Notes · shared</h2>
        <ul id="notesSideList" class="list"></ul>
      </aside>
    </div>
  </div>
  <script src="/app.js"></script>
</body>
</html>
""".trimIndent()

fun webAppCss(): String = """
:root {
  --bg0: #f4efe6;
  --bg1: #fffaf2;
  --ink: #2a2118;
  --muted: #6f6254;
  --line: rgba(42, 33, 24, 0.12);
  --accent: #c47a3a;
  --accent-ink: #fff8ef;
  --card: #fffdf8;
  --ok: #3d7a4a;
  --chrome: #2b241c;
  --chrome-ink: #f6ead8;
  font-family: "Iowan Old Style", "Palatino Linotype", Palatino, Georgia, serif;
}
* { box-sizing: border-box; }
html, body { margin: 0; height: 100%; color: var(--ink); background: var(--bg0); }
.app { min-height: 100%; display: flex; flex-direction: column; }
.chrome {
  display: flex; align-items: center; gap: 16px; flex-wrap: wrap;
  padding: 10px 16px; background: var(--chrome); color: var(--chrome-ink);
  position: sticky; top: 0; z-index: 5;
}
.brand { display: flex; align-items: center; gap: 8px; font-size: 1.15rem; }
.mark {
  width: 28px; height: 28px; border-radius: 8px; display: grid; place-items: center;
  background: var(--accent); color: var(--accent-ink); font-size: 0.75rem; font-weight: 700;
}
.tabs { display: flex; flex-wrap: wrap; gap: 6px; }
.tabs button, button {
  border: 0; border-radius: 999px; padding: 8px 14px; font: inherit; cursor: pointer;
  background: transparent; color: var(--chrome-ink);
}
.tabs button.on, button:not(.ghost):not(.tabs button) {
  background: var(--accent); color: var(--accent-ink); font-weight: 700;
}
.tabs button { border: 1px solid rgba(246,234,216,0.2); }
.chrome-right { margin-left: auto; display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.pin {
  font-size: 1.15rem; letter-spacing: 0.18em; font-weight: 700;
  padding: 6px 12px; border-radius: 10px; background: rgba(255,255,255,0.08);
}
.chip { font-size: 0.85rem; color: #c9b8a2; }
.workspace {
  flex: 1; display: grid;
  grid-template-columns: minmax(200px, 260px) minmax(0, 1fr) minmax(180px, 240px);
  min-height: 0;
}
.rail, .codex, .stage { padding: 16px; overflow: auto; }
.rail, .codex { background: var(--bg1); border-right: 1px solid var(--line); }
.codex { border-right: 0; border-left: 1px solid var(--line); }
.stage { background: var(--card); }
.view { display: none; }
.view.on { display: block; }
h2 { margin: 0 0 10px; font-size: 1.05rem; }
.lead { color: var(--muted); margin-top: 0; }
.list { list-style: none; padding: 0; margin: 0; }
.list li {
  padding: 10px 8px; border-bottom: 1px solid var(--line); cursor: pointer;
}
.list li:hover, .list li.on { background: rgba(196,122,58,0.12); }
.list .sub { color: var(--muted); font-size: 0.85rem; }
input, textarea {
  width: 100%; font: inherit; color: var(--ink);
  background: #fff; border: 1px solid var(--line); border-radius: 10px;
  padding: 10px 12px; margin: 0 0 10px;
}
textarea { min-height: 280px; resize: vertical; }
.row { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
button.ghost { background: transparent; color: var(--ink); border: 1px solid var(--line); }
.cards { display: grid; gap: 10px; }
.card {
  border: 1px solid var(--line); border-radius: 12px; padding: 12px; background: #fff;
}
#rpgScenes .card { cursor: pointer; }
.log { display: flex; flex-direction: column; gap: 8px; }
.bubble { border: 1px solid var(--line); border-radius: 12px; padding: 10px 12px; background: #fff; }
.bubble .who { font-size: 0.75rem; letter-spacing: 0.06em; color: var(--muted); }
.msg { min-height: 1.2em; color: var(--muted); }
.msg.ok { color: var(--ok); }
.msg.bad { color: #a33; }
.meta { color: var(--muted); font-size: 0.9rem; }
.art-row { display: flex; flex-wrap: wrap; gap: 10px; margin: 0 0 14px; }
.art-row figure { margin: 0; width: min(220px, 42vw); }
.art-row img {
  width: 100%; height: auto; display: block; border-radius: 12px;
  border: 1px solid var(--line); background: #1a1420;
}
.art-row figcaption { color: var(--muted); font-size: 0.8rem; padding-top: 4px; }
@media (max-width: 900px) {
  .workspace { grid-template-columns: 1fr; }
  .rail, .codex { border: 0; border-top: 1px solid var(--line); max-height: 240px; }
}
""".trimIndent()

fun webAppJs(): String = """
(() => {
  const state = {
    token: localStorage.getItem('weaverseToken') || '',
    tab: 'plan',
    workspace: { books: [], scenes: [], codex: [], notes: [], threads: [], rpChats: [], media: [] },
    sceneId: '',
    noteId: '',
    threadId: '',
    rpId: ''
  };
  const el = (id) => document.getElementById(id);
  const setMsg = (id, text, ok) => {
    const n = el(id); if (!n) return;
    n.textContent = text || '';
    n.className = 'msg ' + (ok === true ? 'ok' : ok === false ? 'bad' : '');
  };
  const esc = (s) => String(s || '').replace(/[&<>"']/g, (c) => ({
    '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
  })[c]);

  async function api(path, options = {}) {
    const headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
    if (state.token) headers['X-Weaverse-Token'] = state.token;
    return fetch(path, Object.assign({}, options, { headers }));
  }

  async function unlock(password) {
    const res = await fetch('/api/pair', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pin: password })
    });
    const data = await res.json();
    if (!data.ok || !data.token) return false;
    state.token = data.token;
    localStorage.setItem('weaverseToken', state.token);
    return true;
  }

  function showTab(tab) {
    state.tab = tab;
    document.querySelectorAll('#tabs button').forEach((b) => b.classList.toggle('on', b.dataset.tab === tab));
    document.querySelectorAll('.view').forEach((v) => v.classList.toggle('on', v.id === 'view-' + tab));
    const titles = { plan: 'Manuscript', write: 'Manuscript', chat: 'Workshop', review: 'Manuscript', roleplay: 'Roleplay', notes: 'Notes', pictures: 'Pictures' };
    el('railTitle').textContent = titles[tab] || 'Library';
    renderRail();
  }

  function renderRail() {
    const list = el('railList');
    if (state.tab === 'notes') {
      list.innerHTML = (state.workspace.notes || []).map((n) =>
        '<li data-kind="note" data-id="' + esc(n.id) + '"><strong>' + esc(n.title) + '</strong><div class="sub">' + esc(n.bodyPreview) + '</div></li>'
      ).join('') || '<li>No notes yet</li>';
    } else if (state.tab === 'chat') {
      list.innerHTML = (state.workspace.threads || []).map((t) =>
        '<li data-kind="thread" data-id="' + esc(t.id) + '"><strong>' + esc(t.name) + '</strong></li>'
      ).join('') || '<li>No workshop threads</li>';
    } else if (state.tab === 'roleplay') {
      list.innerHTML = (state.workspace.rpChats || []).map((c) =>
        '<li data-kind="rp" data-id="' + esc(c.id) + '"><strong>' + esc(c.title) + '</strong><div class="sub">' + esc(c.displayMode) + '</div></li>'
      ).join('') || '<li>No roleplay chats</li>';
    } else if (state.tab === 'pictures') {
      list.innerHTML = (state.workspace.media || []).map((m) =>
        '<li data-kind="picture" data-id="' + esc(m.id) + '"><strong>' + esc(m.caption || m.id) + '</strong><div class="sub">' + esc(m.section) + '</div></li>'
      ).join('') || '<li>No pictures yet</li>';
    } else {
      list.innerHTML = (state.workspace.scenes || []).map((s) =>
        '<li data-kind="scene" data-id="' + esc(s.id) + '"><strong>' + esc(s.title) + '</strong><div class="sub">' +
        esc([s.actTitle, s.chapterTitle, s.wordCount + ' words'].filter(Boolean).join(' · ')) + '</div></li>'
      ).join('') || '<li>No scenes — Push from the Android app</li>';
    }
    list.querySelectorAll('li[data-id]').forEach((li) => {
      li.addEventListener('click', () => onRail(li.dataset.kind, li.dataset.id, li));
    });
  }

  function renderCodex() {
    el('codexList').innerHTML = (state.workspace.codex || []).map((c) =>
      '<li><strong>' + esc(c.name) + '</strong><div class="sub">' + esc(c.category || c.bodyPreview) + '</div></li>'
    ).join('') || '<li>No codex entries</li>';
    const notesSide = el('notesSideList');
    if (notesSide) {
      notesSide.innerHTML = (state.workspace.notes || []).map((n) =>
        '<li data-kind="note" data-id="' + esc(n.id) + '"><strong>' + esc(n.title) + '</strong><div class="sub">' + esc(n.bodyPreview) + '</div></li>'
      ).join('') || '<li>No notes yet</li>';
      notesSide.querySelectorAll('li[data-id]').forEach((li) => {
        li.addEventListener('click', () => onRail(li.dataset.kind, li.dataset.id, li));
      });
    }
  }

  function artHtml(sections) {
    const wanted = new Set(sections);
    return (state.workspace.media || []).filter((m) => wanted.has(m.section)).map((m) =>
      '<figure><img src="/api/media/' + encodeURIComponent(m.id) + '" alt="' + esc(m.caption) + '" />' +
      '<figcaption>' + esc(m.caption || m.section) + '</figcaption></figure>'
    ).join('');
  }

  function renderArt() {
    const plan = el('planArt'); if (plan) plan.innerHTML = artHtml(['novel']);
    const write = el('writeArt'); if (write) write.innerHTML = artHtml(['novel']);
    const rp = el('rpArt'); if (rp) rp.innerHTML = artHtml(['roleplay', 'manga']);
    const pictures = el('pictureGrid');
    if (pictures) {
      pictures.innerHTML = (state.workspace.media || []).map((m) =>
        '<figure><img src="/api/media/' + encodeURIComponent(m.id) + '" alt="' + esc(m.caption) + '" />' +
        '<figcaption>' + esc(m.caption || m.section) + '</figcaption></figure>'
      ).join('') || '<p class="lead">No pictures yet. Import a Novelcrafter ZIP or add media on Android.</p>';
    }
  }

  function renderRpg() {
    const box = el('rpgScenes');
    if (!box) return;
    const scenes = (state.workspace.rpChats || []).filter((c) => (c.id || '').indexOf('ah-rpg-scene-') === 0);
    box.innerHTML = scenes.map((c) =>
      '<article class="card" data-kind="rp" data-id="' + esc(c.id) + '"><strong>' + esc(c.title) +
      '</strong><div class="sub">' + esc(c.displayMode) + '</div></article>'
    ).join('') || '<article class="card">Adams Haven RPG scenes appear here after Android seeds Roleplay (or you Push a library that already has them).</article>';
    box.querySelectorAll('article[data-id]').forEach((card) => {
      card.addEventListener('click', () => onRail(card.dataset.kind, card.dataset.id, null));
    });
  }

  function renderPlan() {
    renderArt();
    renderRpg();
    el('planGrid').innerHTML = (state.workspace.scenes || []).map((s) =>
      '<article class="card"><strong>' + esc(s.title) + '</strong><div class="sub">' +
      esc([s.actTitle, s.chapterTitle, s.status, s.wordCount + ' words'].filter(Boolean).join(' · ')) +
      '</div><p>' + esc(s.summary) + '</p></article>'
    ).join('') || '<article class="card">Import a Novelcrafter ZIP or Push from Android to fill Plan / Write.</article>';
    el('reviewList').innerHTML = (state.workspace.scenes || []).map((s) =>
      '<article class="card"><strong>' + esc(s.title) + '</strong> — ' + esc(s.status) + ' · ' + s.wordCount + ' words</article>'
    ).join('');
  }

  async function onRail(kind, id, li) {
    document.querySelectorAll('#railList li').forEach((n) => n.classList.remove('on'));
    if (li) li.classList.add('on');
    if (kind === 'scene') {
      showTab('write');
      const res = await api('/api/scenes/' + encodeURIComponent(id));
      if (!res.ok) return;
      const scene = await res.json();
      state.sceneId = scene.id;
      el('sceneTitle').value = scene.title || '';
      el('sceneSummary').value = scene.summary || '';
      el('sceneBody').value = scene.body || '';
      el('sceneMeta').textContent = (scene.wordCount || 0) + ' words · ' + (scene.status || 'draft');
    } else if (kind === 'note') {
      showTab('notes');
      const res = await api('/api/notes/' + encodeURIComponent(id));
      if (!res.ok) return;
      const note = await res.json();
      state.noteId = note.id;
      el('noteTitle').value = note.title || '';
      el('noteBody').value = note.body || '';
    } else if (kind === 'thread') {
      showTab('chat');
      state.threadId = id;
      const res = await api('/api/threads/' + encodeURIComponent(id) + '/messages');
      const lines = res.ok ? await res.json() : [];
      el('chatLog').innerHTML = (lines || []).map((m) =>
        '<div class="bubble"><div class="who">' + esc(m.role) + '</div>' + esc(m.text) + '</div>'
      ).join('') || '<div class="bubble">Empty thread</div>';
    } else if (kind === 'rp') {
      showTab('roleplay');
      state.rpId = id;
      const res = await api('/api/rp/' + encodeURIComponent(id) + '/messages');
      const lines = res.ok ? await res.json() : [];
      el('rpLog').innerHTML = (lines || []).map((m) =>
        '<div class="bubble"><div class="who">' + esc(m.role) + '</div>' + esc(m.text) + '</div>'
      ).join('') || '<div class="bubble">Empty chat — write on Android (Messenger / DM / Manga)</div>';
    }
  }

  async function loadWorkspace() {
    if (!state.token) return;
    const res = await api('/api/workspace');
    if (!res.ok) return;
    state.workspace = await res.json();
    renderRail();
    renderCodex();
    renderPlan();
  }

  document.querySelectorAll('#tabs button').forEach((b) => {
    b.addEventListener('click', () => showTab(b.dataset.tab));
  });
  el('saveSceneBtn').addEventListener('click', async () => {
    if (!state.sceneId) { setMsg('writeMsg', 'Pick a scene in the manuscript rail', false); return; }
    const body = {
      id: state.sceneId,
      title: el('sceneTitle').value || 'Untitled',
      summary: el('sceneSummary').value || '',
      body: el('sceneBody').value || ''
    };
    const res = await api('/api/scenes/' + encodeURIComponent(state.sceneId), { method: 'PUT', body: JSON.stringify(body) });
    setMsg('writeMsg', res.ok ? 'Scene saved on the web hub — Android can Pull' : 'Save failed', res.ok);
    if (res.ok) loadWorkspace();
  });
  el('newNoteBtn').addEventListener('click', () => {
    state.noteId = (crypto.randomUUID ? crypto.randomUUID() : String(Date.now()));
    el('noteTitle').value = 'New note';
    el('noteBody').value = '';
  });
  el('saveNoteBtn').addEventListener('click', async () => {
    if (!state.noteId) state.noteId = (crypto.randomUUID ? crypto.randomUUID() : String(Date.now()));
    const body = { id: state.noteId, title: el('noteTitle').value || 'Untitled', body: el('noteBody').value || '' };
    const res = await api('/api/notes/' + encodeURIComponent(state.noteId), { method: 'PUT', body: JSON.stringify(body) });
    setMsg('editMsg', res.ok ? 'Note saved on the web hub' : 'Save failed', res.ok);
    if (res.ok) loadWorkspace();
  });
  el('exportBtn').addEventListener('click', () => {
    const blob = new Blob([JSON.stringify(state.workspace || {}, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'weaverse-export.json';
    a.click();
    el('statusChip').textContent = 'Exported novels · roleplay · notes';
  });
  el('importBtn').addEventListener('click', () => el('importZip').click());
  el('importZip').addEventListener('change', async (ev) => {
    const file = ev.target.files && ev.target.files[0];
    ev.target.value = '';
    if (!file) return;
    el('statusChip').textContent = 'Importing…';
    const res = await fetch('/api/import', {
      method: 'POST',
      headers: { 'X-Weaverse-Token': state.token, 'Content-Type': 'application/zip' },
      body: file
    });
    const data = await res.json().catch(() => ({ ok: false, message: 'Import failed' }));
    el('statusChip').textContent = data.ok ? ('Imported ' + (data.bookTitle || 'book')) : 'Import failed';
    if (data.ok) await loadWorkspace();
    else setMsg('writeMsg', data.message || 'Import failed', false);
  });
  el('pullBtn').addEventListener('click', async () => {
    const res = await api('/api/sync/pull');
    if (!res.ok) { setMsg('editMsg', 'Nothing to download yet', false); return; }
    const blob = await res.blob();
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'weaverse-sync.zip';
    a.click();
    setMsg('editMsg', 'Downloaded sync package', true);
  });

  (async () => {
    try {
      const status = await (await fetch('/api/status')).json();
      const password = status.pairPin || '';
      el('passwordBox').textContent = password || '—';
      el('statusChip').textContent = 'Web hub · live sync';
      if (password) await unlock(password);
      await loadWorkspace();
      showTab('plan');
    } catch (e) {
      el('statusChip').textContent = 'Web hub offline';
    }
  })();
})();
""".trimIndent()
