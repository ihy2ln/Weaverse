// Manga Studio for the web version: the same screens as the APK's Mihon-style Manga Studio
// (Library, Updates, History, Browse, More, title page, reader), running the APK's own sources
// on Weaverse Desktop through /api/manga.
(() => {
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  const token = () => localStorage.getItem('weaverseToken') || '';
  const ls = {
    get: (k, d) => { try { const v = localStorage.getItem('wvManga.' + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
    set: (k, v) => { try { localStorage.setItem('wvManga.' + k, JSON.stringify(v)); } catch (e) {} }
  };
  async function api(path, body) {
    const res = await fetch('/api/manga' + path, {
      method: body === undefined ? 'GET' : 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Weaverse-Token': token() },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || ('HTTP ' + res.status));
    return data;
  }
  const img = (url, referer) => url ? '/api/manga/image?u=' + encodeURIComponent(url) + '&r=' + encodeURIComponent(referer || '') + '&t=' + encodeURIComponent(token()) : '';
  const key = (m) => m.sourceId + ':' + m.remoteId;
  const LANGS = { en: 'English', ja: 'Japanese', ko: 'Korean', zh: 'Chinese', 'zh-hk': 'Chinese (HK)', es: 'Spanish', 'es-la': 'Spanish (LATAM)', fr: 'French', de: 'German', it: 'Italian', 'pt-br': 'Portuguese (BR)', pt: 'Portuguese', ru: 'Russian', id: 'Indonesian', vi: 'Vietnamese', th: 'Thai', tr: 'Turkish', pl: 'Polish', cs: 'Czech', ar: 'Arabic', la: 'Latin', uk: 'Ukrainian', all: 'All', multi: 'Multi' };
  const langLabel = (c) => LANGS[String(c || '').toLowerCase()] || String(c || '').toUpperCase();
  const chapterLabel = (c) => {
    const parts = [];
    if (c.volume) parts.push('Vol. ' + c.volume);
    if (c.chapterNumber) parts.push('Ch. ' + c.chapterNumber);
    const head = parts.join(' ');
    const title = (c.title || '').trim();
    if (!head) return title || 'Chapter';
    return title && title !== head && !/^chapter\s*[\d.]+$/i.test(title) ? head + ' — ' + title : head;
  };
  const when = (t) => {
    if (!t) return '';
    const d = new Date(t), today = new Date();
    const days = Math.floor((new Date(today.toDateString()) - new Date(d.toDateString())) / 86400000);
    if (days <= 0) return 'Today';
    if (days === 1) return 'Yesterday';
    if (days < 7) return days + ' days ago';
    return d.toLocaleDateString();
  };
  const ICON = {
    library: '<path d="M4 6H2v14a2 2 0 0 0 2 2h14v-2H4V6zm16-4H8a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2zm0 14H8V4h12v12zM10 9h8v2h-8zm0 3h4v2h-4zm0-6h8v2h-8z"/>',
    updates: '<path d="M23 12l-2.44-2.78.34-3.68-3.61-.82-1.89-3.18L12 3 8.6 1.54 6.71 4.72l-3.61.81.34 3.68L1 12l2.44 2.78-.34 3.69 3.61.82 1.89 3.18L12 21l3.4 1.46 1.89-3.18 3.61-.82-.34-3.68L23 12zm-10 5h-2v-2h2v2zm0-4h-2V7h2v6z"/>',
    history: '<path d="M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42A8.95 8.95 0 0 0 13 21a9 9 0 0 0 0-18zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z"/>',
    browse: '<path d="M12 10.9c-.61 0-1.1.49-1.1 1.1s.49 1.1 1.1 1.1c.61 0 1.1-.49 1.1-1.1s-.49-1.1-1.1-1.1zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm2.19 12.19L6 18l3.81-8.19L18 6l-3.81 8.19z"/>',
    more: '<path d="M6 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm12 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm-6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/>',
    back: '<path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/>',
    search: '<path d="M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>',
    refresh: '<path d="M17.65 6.35A7.96 7.96 0 0 0 12 4a8 8 0 1 0 7.73 10h-2.08A6 6 0 1 1 12 6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/>',
    globe: '<path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm6.93 6h-2.95a15.65 15.65 0 0 0-1.38-3.56A8.03 8.03 0 0 1 18.93 8zM12 4.04c.83 1.2 1.48 2.53 1.91 3.96h-3.82c.43-1.43 1.08-2.76 1.91-3.96zM4.26 14C4.1 13.36 4 12.69 4 12s.1-1.36.26-2h3.38c-.08.66-.14 1.32-.14 2s.06 1.34.14 2H4.26zm.82 2h2.95c.32 1.25.78 2.45 1.38 3.56A7.99 7.99 0 0 1 5.08 16zm2.95-8H5.08a7.99 7.99 0 0 1 4.33-3.56A15.65 15.65 0 0 0 8.03 8zM12 19.96c-.83-1.2-1.48-2.53-1.91-3.96h3.82c-.43 1.43-1.08 2.76-1.91 3.96zM14.34 14H9.66c-.09-.66-.16-1.32-.16-2s.07-1.35.16-2h4.68c.09.65.16 1.32.16 2s-.07 1.34-.16 2zm.25 5.56c.6-1.11 1.06-2.31 1.38-3.56h2.95a8.03 8.03 0 0 1-4.33 3.56zM16.36 14c.08-.66.14-1.32.14-2s-.06-1.34-.14-2h3.38c.16.64.26 1.31.26 2s-.1 1.36-.26 2h-3.38z"/>',
    trash: '<path d="M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>',
    add: '<path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>',
    heart: '<path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>',
    sort: '<path d="M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z"/>',
    close: '<path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>',
    prev: '<path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/>',
    next: '<path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/>'
  };
  const icon = (name, label) => '<svg class="mi" viewBox="0 0 24 24" aria-hidden="true">' + ICON[name] + '</svg>' + (label ? '<span>' + esc(label) + '</span>' : '');

  // ---------------------------------------------------------------- state
  const S = {
    tab: ls.get('tab', 'library'),
    browseTab: 'sources',
    sources: null,
    library: null,
    updates: null,
    category: ls.get('category', 'all'),
    libraryQuery: '',
    stack: [],          // pushed screens: {type:'catalog'|'manga', ...}
    root: null,
    reader: null
  };
  const R = { stage: null };

  async function loadSources() { if (!S.sources) S.sources = await api('/sources'); return S.sources; }
  async function loadLibrary() { S.library = await api('/library'); return S.library; }
  const inLibrary = (m) => !!(S.library && S.library.entries.some((e) => key(e.manga) === key(m)));
  const sourceName = (id) => ((S.sources || []).find((s) => s.id === id) || {}).name || id;
  const sourceBase = (id) => ((S.sources || []).find((s) => s.id === id) || {}).baseUrl || '';

  function render() {
    const root = R.stage.querySelector('#mangaRoot');
    if (!root) return;
    const top = S.stack[S.stack.length - 1];
    root.className = 'mihon';
    if (top && top.type === 'catalog') return catalogScreen(root, top);
    if (top && top.type === 'manga') return mangaScreen(root, top);
    const screens = { library: libraryScreen, updates: updatesScreen, history: historyScreen, browse: browseScreen, more: moreScreen };
    (screens[S.tab] || libraryScreen)(root);
  }
  function push(screen) { S.stack.push(screen); render(); window.scrollTo(0, 0); }
  function pop() { S.stack.pop(); render(); }

  function shell(root, title, actions, body, withNav) {
    root.innerHTML =
      '<div class="mHead">' + (S.stack.length ? '<button class="mIcon" data-act="back" title="Back">' + icon('back') + '</button>' : '') +
      '<h2>' + title + '</h2><div class="mActions">' + (actions || '') + '</div></div>' +
      '<div class="mBody">' + body + '</div>' +
      (withNav ? '<nav class="mNav">' + [['library', 'Library'], ['updates', 'Updates'], ['history', 'History'], ['browse', 'Browse'], ['more', 'More']].map(([k, label]) =>
        '<button data-tab="' + k + '" class="' + (S.tab === k ? 'on' : '') + '"><span class="pill">' + icon(k) + '</span><span>' + label + '</span></button>').join('') + '</nav>' : '');
    const back = root.querySelector('[data-act="back"]');
    if (back) back.addEventListener('click', pop);
    root.querySelectorAll('.mNav [data-tab]').forEach((b) => b.addEventListener('click', () => { S.tab = b.dataset.tab; ls.set('tab', S.tab); S.stack = []; render(); }));
  }
  const empty = (face, text) => '<div class="mEmpty"><div class="face">' + esc(face) + '</div><p>' + esc(text) + '</p></div>';
  const coverCard = (m, badge) =>
    '<button class="mCover" data-key="' + esc(key(m)) + '"><div class="art"><img loading="lazy" alt="" src="' + esc(img(m.coverUrl, sourceBase(m.sourceId) || m.canonicalUrl)) + '" />' +
    (badge ? '<span class="badge">' + esc(badge) + '</span>' : '') + (inLibrary(m) ? '<span class="inlib">In library</span>' : '') +
    '</div><div class="t">' + esc(m.title) + '</div></button>';

  // ---------------------------------------------------------------- Library
  async function libraryScreen(root) {
    shell(root, 'Library', '<button class="mIcon" data-act="lsearch" title="Search library">' + icon('search') + '</button>' +
      '<button class="mIcon" data-act="lrefresh" title="Update library">' + icon('refresh') + '</button>', '<p class="mLoading">Loading…</p>', true);
    try { await loadSources(); await loadLibrary(); } catch (e) { root.querySelector('.mBody').innerHTML = empty('(╬ಠ益ಠ)', e.message); return; }
    const lib = S.library;
    const cats = [{ id: 'all', name: 'All' }].concat(lib.categories);
    const q = S.libraryQuery.toLowerCase();
    const entries = lib.entries.filter((e) => (S.category === 'all' || e.categoryIds.indexOf(S.category) >= 0) && (!q || e.manga.title.toLowerCase().indexOf(q) >= 0));
    const unread = (e) => e.chapters.filter((c) => lib.readChapterIds.indexOf(c.sourceId + ':' + c.remoteId) < 0).length;
    root.querySelector('h2').innerHTML = 'Library <span class="count">' + lib.entries.length + '</span>';
    root.querySelector('.mBody').innerHTML =
      '<div class="mChips">' + cats.map((c) => '<button data-cat="' + esc(c.id) + '" class="' + (S.category === c.id ? 'on' : '') + '">' + esc(c.name) + '</button>').join('') + '</div>' +
      (S.libraryQuery !== '' ? '<input class="mSearch" id="libQuery" placeholder="Search library" value="' + esc(S.libraryQuery) + '" />' : '') +
      (entries.length ? '<div class="mGrid">' + entries.map((e) => coverCard(e.manga, unread(e) || '')).join('') + '</div>'
        : empty('(╬ಠ益ಠ)', lib.entries.length ? 'Nothing in this category' : 'Your library is empty — add titles from Browse.'));
    root.querySelectorAll('[data-cat]').forEach((b) => b.addEventListener('click', () => { S.category = b.dataset.cat; ls.set('category', S.category); render(); }));
    root.querySelectorAll('.mCover').forEach((b) => b.addEventListener('click', () => {
      const e = lib.entries.find((x) => key(x.manga) === b.dataset.key); if (e) push({ type: 'manga', manga: e.manga });
    }));
    root.querySelector('[data-act="lsearch"]').addEventListener('click', () => { S.libraryQuery = S.libraryQuery === '' ? ' ' : ''; render(); });
    const input = root.querySelector('#libQuery');
    if (input) { input.value = S.libraryQuery.trim(); input.focus(); input.addEventListener('input', () => { S.libraryQuery = input.value || ' '; const pos = input.selectionStart; render(); const n = R.stage.querySelector('#libQuery'); if (n) { n.focus(); n.setSelectionRange(pos, pos); } }); }
    root.querySelector('[data-act="lrefresh"]').addEventListener('click', async () => {
      root.querySelector('[data-act="lrefresh"]').classList.add('spin');
      try { S.updates = await api('/updates/refresh', {}); } catch (e) {}
      render();
    });
  }

  // ---------------------------------------------------------------- Updates
  async function updatesScreen(root) {
    shell(root, 'Updates', '<button class="mIcon" data-act="urefresh" title="Update library">' + icon('refresh') + '</button>', '<p class="mLoading">Loading…</p>', true);
    try { await loadSources(); await loadLibrary(); S.updates = S.updates || await api('/updates'); } catch (e) { root.querySelector('.mBody').innerHTML = empty('(╬ಠ益ಠ)', e.message); return; }
    const items = S.updates;
    let lastDay = '';
    root.querySelector('.mBody').innerHTML = items.length ? '<div class="mList">' + items.map((u, i) => {
      const day = when(u.chapter.dateUpload);
      const head = day !== lastDay ? '<div class="mGroup">' + esc(day || 'Unknown date') + '</div>' : '';
      lastDay = day;
      const read = S.library.readChapterIds.indexOf(u.chapter.sourceId + ':' + u.chapter.remoteId) >= 0;
      return head + '<button class="mRow' + (read ? ' read' : '') + '" data-i="' + i + '"><span class="thumb" style="background-image:url(\'' + esc(img(u.manga.coverUrl, sourceBase(u.manga.sourceId))) + '\')"></span>' +
        '<span class="txt"><strong>' + esc(u.manga.title) + '</strong><span class="sub">' + esc(chapterLabel(u.chapter)) + '</span></span></button>';
    }).join('') + '</div>' : empty('(・o・;)', S.library.entries.length ? 'No recent updates — tap refresh to check your library.' : 'Add titles to your library to see their new chapters here.');
    root.querySelectorAll('.mRow').forEach((b) => b.addEventListener('click', () => { const u = items[Number(b.dataset.i)]; openReader(u.manga, u.chapter, null); }));
    root.querySelector('[data-act="urefresh"]').addEventListener('click', async (ev) => {
      ev.currentTarget.classList.add('spin');
      try { S.updates = await api('/updates/refresh', {}); } catch (e) {}
      render();
    });
  }

  // ---------------------------------------------------------------- History
  async function historyScreen(root) {
    shell(root, 'History', '', '<p class="mLoading">Loading…</p>', true);
    try { await loadSources(); await loadLibrary(); } catch (e) { root.querySelector('.mBody').innerHTML = empty('(╬ಠ益ಠ)', e.message); return; }
    const items = S.library.history;
    let lastDay = '';
    root.querySelector('.mBody').innerHTML = items.length ? '<div class="mList">' + items.map((h, i) => {
      const day = when(h.readAt);
      const head = day !== lastDay ? '<div class="mGroup">' + esc(day) + '</div>' : '';
      lastDay = day;
      return head + '<div class="mRow"><button class="open" data-i="' + i + '"><span class="thumb" style="background-image:url(\'' + esc(img(h.manga.coverUrl, sourceBase(h.manga.sourceId))) + '\')"></span>' +
        '<span class="txt"><strong>' + esc(h.manga.title) + '</strong><span class="sub">' + esc(chapterLabel(h.chapter) + (h.pageCount ? ' · page ' + (h.page + 1) + '/' + h.pageCount : '') + ' · ' + new Date(h.readAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })) + '</span></span></button>' +
        '<button class="mIcon" data-del="' + i + '" title="Remove">' + icon('trash') + '</button></div>';
    }).join('') + '</div>' : empty('(っ˘ω˘ς )', 'Nothing read recently');
    root.querySelectorAll('.open').forEach((b) => b.addEventListener('click', () => { const h = items[Number(b.dataset.i)]; openReader(h.manga, h.chapter, null, h.page); }));
    root.querySelectorAll('[data-del]').forEach((b) => b.addEventListener('click', async () => {
      const h = items[Number(b.dataset.del)];
      S.library = await api('/history/remove', { sourceId: h.manga.sourceId, remoteId: h.manga.remoteId });
      render();
    }));
  }

  // ---------------------------------------------------------------- Browse
  async function browseScreen(root) {
    shell(root, 'Browse', '<button class="mIcon" data-act="gsearch" title="Search all sources">' + icon('search') + '</button>',
      '<div class="mTabs">' + [['sources', 'Sources'], ['extensions', 'Extensions'], ['migrate', 'Migrate']].map(([k, l]) =>
        '<button data-btab="' + k + '" class="' + (S.browseTab === k ? 'on' : '') + '">' + l + '</button>').join('') + '</div><div id="browseBody"><p class="mLoading">Loading…</p></div>', true);
    root.querySelectorAll('[data-btab]').forEach((b) => b.addEventListener('click', () => { S.browseTab = b.dataset.btab; render(); }));
    root.querySelector('[data-act="gsearch"]').addEventListener('click', () => push({ type: 'catalog', global: true, query: '', mode: 'search' }));
    const body = root.querySelector('#browseBody');
    try { await loadSources(); await loadLibrary(); } catch (e) { body.innerHTML = empty('(╬ಠ益ಠ)', e.message); return; }
    if (S.browseTab === 'extensions') {
      body.innerHTML = empty('(｡•́︿•̀｡)', 'Tachiyomi / Mihon extension APKs are Android apps, so they install in the phone app. The built-in sources under Sources work here.');
      return;
    }
    if (S.browseTab === 'migrate') {
      const lib = S.library.entries;
      body.innerHTML = lib.length ? '<div class="mList">' + lib.map((e, i) => '<button class="mRow" data-i="' + i + '"><span class="thumb" style="background-image:url(\'' + esc(img(e.manga.coverUrl, sourceBase(e.manga.sourceId))) + '\')"></span><span class="txt"><strong>' + esc(e.manga.title) + '</strong><span class="sub">' + esc(sourceName(e.manga.sourceId)) + ' · search other sources</span></span></button>').join('') + '</div>'
        : empty('(・_・)', 'Library titles can be moved to another source here.');
      body.querySelectorAll('.mRow').forEach((b) => b.addEventListener('click', () => push({ type: 'catalog', global: true, query: lib[Number(b.dataset.i)].manga.title, mode: 'search' })));
      return;
    }
    const pinned = ls.get('pinned', []);
    const groups = {};
    S.sources.forEach((s) => { const g = pinned.indexOf(s.id) >= 0 ? 'Pinned' : langLabel(s.language); (groups[g] = groups[g] || []).push(s); });
    const order = Object.keys(groups).sort((a, b) => (a === 'Pinned' ? -1 : b === 'Pinned' ? 1 : a.localeCompare(b)));
    body.innerHTML = order.map((g) => '<div class="mGroup">' + esc(g) + '</div>' + groups[g].map((s) =>
      '<div class="mSource"><button class="name" data-src="' + esc(s.id) + '"><span class="ico">' + icon('globe') + '</span><span class="txt"><strong>' + esc(s.name) + '</strong><span class="sub">' + esc(s.description) + '</span></span></button>' +
      (s.supportsLatest ? '<button class="latest" data-latest="' + esc(s.id) + '">Latest</button>' : '') +
      '<button class="mIcon pin' + (pinned.indexOf(s.id) >= 0 ? ' on' : '') + '" data-pin="' + esc(s.id) + '" title="Pin">📌</button></div>').join('')).join('');
    body.querySelectorAll('[data-src]').forEach((b) => b.addEventListener('click', () => push({ type: 'catalog', source: b.dataset.src, mode: 'popular' })));
    body.querySelectorAll('[data-latest]').forEach((b) => b.addEventListener('click', () => push({ type: 'catalog', source: b.dataset.latest, mode: 'latest' })));
    body.querySelectorAll('[data-pin]').forEach((b) => b.addEventListener('click', () => {
      const id = b.dataset.pin; const p = ls.get('pinned', []);
      ls.set('pinned', p.indexOf(id) >= 0 ? p.filter((x) => x !== id) : p.concat([id])); render();
    }));
  }

  // ---------------------------------------------------------------- Catalog (one source, or all sources)
  async function catalogScreen(root, scr) {
    const title = scr.global ? 'Search all sources' : sourceName(scr.source);
    const modeTabs = scr.global ? '' : '<div class="mTabs">' + [['popular', 'Popular'], ['latest', 'Latest'], ['search', 'Search']].map(([k, l]) =>
      '<button data-mode="' + k + '" class="' + (scr.mode === k ? 'on' : '') + '">' + l + '</button>').join('') + '</div>';
    const searchBox = scr.mode === 'search' ? '<form id="catSearch" class="mSearchRow"><input class="mSearch" id="catQuery" placeholder="Search ' + esc(title) + '" value="' + esc(scr.query || '') + '" /><button class="solid" type="submit">Search</button></form>' : '';
    shell(root, esc(title), '', modeTabs + searchBox + '<div id="catBody"></div>', false);
    root.querySelectorAll('[data-mode]').forEach((b) => b.addEventListener('click', () => { scr.mode = b.dataset.mode; scr.items = null; scr.page = 0; scr.done = false; render(); }));
    const form = root.querySelector('#catSearch');
    if (form) {
      form.addEventListener('submit', (e) => { e.preventDefault(); scr.query = root.querySelector('#catQuery').value.trim(); scr.items = null; scr.page = 0; scr.done = false; render(); });
      if (!scr.query) root.querySelector('#catQuery').focus();
    }
    const body = root.querySelector('#catBody');
    await loadSources(); if (!S.library) await loadLibrary();
    if (scr.global) return globalSearch(body, scr);
    if (scr.mode === 'search' && !scr.query) { body.innerHTML = empty('(｀・ω・´)', 'Type a title to search ' + title + '.'); return; }
    if (!scr.items) { scr.items = []; scr.page = 0; scr.done = false; }
    const draw = () => {
      body.innerHTML = (scr.items.length ? '<div class="mGrid">' + scr.items.map((m) => coverCard(m)).join('') + '</div>' : '') +
        (scr.error ? empty('(╬ಠ益ಠ)', scr.error) : scr.done ? (scr.items.length ? '' : empty('(・_・)', 'Nothing found')) : '<p class="mLoading" id="moreMarker">Loading…</p>');
      body.querySelectorAll('.mCover').forEach((b) => b.addEventListener('click', () => {
        const m = scr.items.find((x) => key(x) === b.dataset.key); if (m) push({ type: 'manga', manga: m });
      }));
      const marker = body.querySelector('#moreMarker');
      if (marker) new IntersectionObserver((entries, obs) => { if (entries.some((e) => e.isIntersecting)) { obs.disconnect(); loadMore(); } }, { rootMargin: '600px' }).observe(marker);
    };
    const loadMore = async () => {
      if (scr.loading || scr.done || S.stack[S.stack.length - 1] !== scr) return;
      scr.loading = true;
      try {
        const page = scr.mode === 'search'
          ? await api('/search?source=' + encodeURIComponent(scr.source) + '&q=' + encodeURIComponent(scr.query) + '&page=' + scr.page)
          : await api('/catalog?source=' + encodeURIComponent(scr.source) + '&mode=' + scr.mode + '&page=' + scr.page);
        const known = new Set(scr.items.map(key));
        const fresh = page.filter((m) => !known.has(key(m)));
        scr.items = scr.items.concat(fresh);
        scr.page++;
        if (!fresh.length) scr.done = true;
        scr.error = '';
      } catch (e) { scr.error = e.message; scr.done = true; }
      scr.loading = false;
      if (S.stack[S.stack.length - 1] === scr) draw();
    };
    draw();
    if (!scr.items.length) loadMore();
  }
  async function globalSearch(body, scr) {
    if (!scr.query) { body.innerHTML = empty('(｀・ω・´)', 'Search every source at once.'); return; }
    body.innerHTML = S.sources.map((s) => '<div class="mGroup">' + esc(s.name) + '</div><div class="mRowScroll" id="gs-' + esc(s.id) + '"><p class="mLoading">Searching…</p></div>').join('');
    S.sources.forEach(async (s) => {
      const box = body.querySelector('#gs-' + CSS.escape(s.id));
      try {
        const items = await api('/search?source=' + encodeURIComponent(s.id) + '&q=' + encodeURIComponent(scr.query) + '&page=0');
        if (!box) return;
        box.innerHTML = items.length ? items.slice(0, 20).map((m) => coverCard(m)).join('') : '<p class="mLoading">No results</p>';
        box.querySelectorAll('.mCover').forEach((b) => b.addEventListener('click', () => { const m = items.find((x) => key(x) === b.dataset.key); if (m) push({ type: 'manga', manga: m }); }));
      } catch (e) { if (box) box.innerHTML = '<p class="mLoading">' + esc(e.message) + '</p>'; }
    });
  }

  // Sources list chapters in either order; the APK shows newest first.
  function newestFirst(list) {
    const out = list.slice();
    if (out.length < 2) return out;
    const n = (c) => { const v = parseFloat(c.chapterNumber); return isNaN(v) ? null : v; };
    const a = n(out[0]), b = n(out[out.length - 1]);
    const ascending = a != null && b != null ? a < b : (out[0].dateUpload || 0) < (out[out.length - 1].dateUpload || 0);
    return ascending ? out.reverse() : out;
  }

  // ---------------------------------------------------------------- Title page
  async function mangaScreen(root, scr) {
    shell(root, '', '<button class="mIcon" data-act="mrefresh" title="Refresh">' + icon('refresh') + '</button>', '<p class="mLoading">Loading…</p>', false);
    root.querySelector('[data-act="mrefresh"]').addEventListener('click', () => { scr.details = null; scr.chapters = null; render(); });
    await loadSources(); if (!S.library) await loadLibrary();
    const m0 = scr.manga;
    try {
      if (!scr.details) scr.details = await api('/details', m0).catch(() => m0);
      if (!scr.chapters) scr.chapters = newestFirst(await api('/chapters', scr.details));
    } catch (e) { scr.chapterError = e.message; scr.chapters = scr.chapters || []; }
    if (S.stack[S.stack.length - 1] !== scr) return;
    const m = scr.details || m0;
    const langs = Array.from(new Set(scr.chapters.map((c) => (c.language || '').toLowerCase()).filter(Boolean)));
    if (scr.lang === undefined) scr.lang = langs.indexOf('en') >= 0 ? 'en' : 'all';
    if (scr.asc === undefined) scr.asc = false;
    let chapters = scr.chapters.filter((c) => scr.lang === 'all' || (c.language || '').toLowerCase() === scr.lang);
    if (scr.asc) chapters = chapters.slice().reverse();
    const lib = inLibrary(m);
    const read = (c) => S.library.readChapterIds.indexOf(c.sourceId + ':' + c.remoteId) >= 0;
    const meta = [m.authors && m.authors.join(', '), m.artists && m.artists.length && m.artists.join(', ') !== (m.authors || []).join(', ') ? m.artists.join(', ') : '', m.status, sourceName(m.sourceId)].filter(Boolean);
    const next = scr.chapters.slice().reverse().find((c) => !read(c) && (scr.lang === 'all' || (c.language || '').toLowerCase() === scr.lang));
    root.querySelector('h2').textContent = m.title;
    root.querySelector('.mBody').innerHTML =
      '<div class="mDetail" style="--cover:url(\'' + esc(img(m.coverUrl, sourceBase(m.sourceId))) + '\')">' +
      '<div class="cover" style="background-image:var(--cover)"></div><div class="info"><h1>' + esc(m.title) + '</h1>' +
      meta.map((x) => '<div class="sub">' + esc(x) + '</div>').join('') +
      '<div class="row"><button class="mLib' + (lib ? ' on' : '') + '" id="libBtn">' + icon(lib ? 'heart' : 'add') + '<span>' + (lib ? 'In library' : 'Add to library') + '</span></button>' +
      (m.canonicalUrl ? '<a class="mLib" href="' + esc(m.canonicalUrl) + '" target="_blank" rel="noopener">' + icon('globe') + '<span>WebView</span></a>' : '') + '</div>' +
      (lib ? '<div class="cats" id="catPick"></div>' : '') + '</div></div>' +
      (m.score || m.rating ? '<div class="sub" style="margin:10px 0">' + (m.score ? '★ Score: ' + esc(m.score) : '') + (m.rating ? '  ·  Content rating: ' + esc(m.rating) : '') + '</div>' : '') +
      (m.tags && m.tags.length ? '<div class="mTags">' + m.tags.slice(0, 30).map((t) => '<span>' + esc(t) + '</span>').join('') + '</div>' : '') +
      (m.description ? '<p class="mDesc' + (scr.more ? ' open' : '') + '" id="desc">' + esc(m.description) + '</p>' : '') +
      (next ? '<button class="solid mResume" id="resume">' + (scr.chapters.some(read) ? 'Continue · ' : 'Start · ') + esc(chapterLabel(next)) + '</button>' : '') +
      '<div class="mChHead"><strong>' + chapters.length + ' chapters</strong><div class="row">' +
      (langs.length > 1 ? '<select id="langPick"><option value="all">All languages</option>' + langs.map((l) => '<option value="' + esc(l) + '"' + (scr.lang === l ? ' selected' : '') + '>' + esc(langLabel(l)) + '</option>').join('') + '</select>' : '') +
      '<button class="mIcon" id="sortBtn" title="Sort">' + icon('sort') + '</button></div></div>' +
      (scr.chapterError ? empty('(╬ಠ益ಠ)', scr.chapterError) : '') +
      '<div class="mList">' + chapters.map((c, i) => '<button class="mChapter' + (read(c) ? ' read' : '') + '" data-i="' + i + '"><span class="txt"><strong>' + esc(chapterLabel(c)) + '</strong><span class="sub">' +
        esc([c.dateUpload ? new Date(c.dateUpload).toLocaleDateString() : '', c.scanlator, scr.lang === 'all' ? langLabel(c.language) : ''].filter(Boolean).join(' · ')) + '</span></span></button>').join('') + '</div>';
    const desc = root.querySelector('#desc');
    if (desc) desc.addEventListener('click', () => { scr.more = !scr.more; desc.classList.toggle('open', scr.more); });
    root.querySelector('#libBtn').addEventListener('click', async () => {
      S.library = await api('/library', { manga: m, categoryIds: lib ? [] : (S.category !== 'all' ? [S.category] : []), inLibrary: !lib });
      render();
    });
    const picker = root.querySelector('#catPick');
    if (picker) {
      const entry = S.library.entries.find((e) => key(e.manga) === key(m));
      picker.innerHTML = S.library.categories.map((c) => '<label><input type="checkbox" data-c="' + esc(c.id) + '"' + (entry.categoryIds.indexOf(c.id) >= 0 ? ' checked' : '') + ' /> ' + esc(c.name) + '</label>').join('');
      picker.querySelectorAll('input').forEach((box) => box.addEventListener('change', async () => {
        const ids = Array.from(picker.querySelectorAll('input:checked')).map((x) => x.dataset.c);
        S.library = await api('/library', { manga: m, categoryIds: ids, inLibrary: true });
      }));
    }
    const lp = root.querySelector('#langPick');
    if (lp) lp.addEventListener('change', () => { scr.lang = lp.value; render(); });
    root.querySelector('#sortBtn').addEventListener('click', () => { scr.asc = !scr.asc; render(); });
    root.querySelectorAll('.mChapter').forEach((b) => b.addEventListener('click', () => openReader(m, chapters[Number(b.dataset.i)], scr.asc ? chapters : chapters.slice().reverse())));
    const resume = root.querySelector('#resume');
    if (resume) resume.addEventListener('click', () => openReader(m, next, scr.chapters.filter((c) => scr.lang === 'all' || (c.language || '').toLowerCase() === scr.lang).slice().reverse()));
  }

  // ---------------------------------------------------------------- Reader
  async function openReader(manga, chapter, ordered, startPage) {
    closeReader();
    const box = document.createElement('div');
    box.className = 'mReader';
    document.body.appendChild(box);
    document.body.classList.add('mReading');
    const mode = ls.get('readerMode', 'webtoon');
    S.reader = { manga, chapter, ordered: ordered || [chapter], box, mode, pages: [], page: startPage || 0 };
    box.innerHTML = '<div class="rTop"><button class="mIcon" data-r="close">' + icon('close') + '</button><div class="rTitle"><strong>' + esc(manga.title) + '</strong><span>' + esc(chapterLabel(chapter)) + '</span></div>' +
      '<select data-r="mode"><option value="webtoon">Long strip</option><option value="ltr">Left to right</option><option value="rtl">Right to left</option></select></div>' +
      '<div class="rPages"><p class="mLoading">Loading pages…</p></div>' +
      '<div class="rBottom"><button class="mIcon" data-r="prevch" title="Previous chapter">' + icon('prev') + '</button><span class="rCount"></span><button class="mIcon" data-r="nextch" title="Next chapter">' + icon('next') + '</button></div>';
    box.querySelector('[data-r="mode"]').value = mode;
    box.querySelector('[data-r="close"]').addEventListener('click', () => { closeReader(); render(); });
    box.querySelector('[data-r="mode"]').addEventListener('change', (e) => { ls.set('readerMode', e.target.value); S.reader.mode = e.target.value; drawPages(); });
    const idx = S.reader.ordered.findIndex((c) => key(c) === key(chapter));
    const step = (d) => { const c = S.reader.ordered[idx + d]; if (c) openReader(manga, c, S.reader.ordered); };
    box.querySelector('[data-r="prevch"]').addEventListener('click', () => step(-1));
    box.querySelector('[data-r="nextch"]').addEventListener('click', () => step(1));
    box.querySelector('[data-r="prevch"]').disabled = idx <= 0;
    box.querySelector('[data-r="nextch"]').disabled = idx < 0 || idx >= S.reader.ordered.length - 1;
    try {
      S.reader.pages = await api('/pages', chapter);
    } catch (e) {
      box.querySelector('.rPages').innerHTML = empty('(╬ಠ益ಠ)', e.message);
      return;
    }
    if (!S.reader || S.reader.box !== box) return;
    if (!S.reader.pages.length) { box.querySelector('.rPages').innerHTML = empty('(・_・)', 'This chapter has no readable pages (it may be hosted on an official site).'); return; }
    drawPages();
    record();
    document.addEventListener('keydown', readerKeys);
  }
  function drawPages() {
    const r = S.reader; if (!r) return;
    const holder = r.box.querySelector('.rPages');
    const referer = r.chapter.canonicalUrl || sourceBase(r.chapter.sourceId);
    const url = (p) => img(p.remoteUrl, referer);
    holder.className = 'rPages ' + r.mode;
    if (r.mode === 'webtoon') {
      holder.innerHTML = r.pages.map((p, i) => '<img loading="lazy" data-i="' + i + '" src="' + esc(url(p)) + '" alt="Page ' + (i + 1) + '" onload="this.classList.add(\'ok\')" />').join('');
      const imgs = holder.querySelectorAll('img');
      const obs = new IntersectionObserver((entries) => {
        entries.forEach((e) => { if (e.isIntersecting) { r.page = Number(e.target.dataset.i); count(); } });
      }, { threshold: 0.5 });
      imgs.forEach((n) => obs.observe(n));
      if (r.page > 0 && imgs[r.page]) setTimeout(() => imgs[r.page].scrollIntoView(), 50);
      holder.onclick = null;
    } else {
      const show = () => {
        r.page = Math.max(0, Math.min(r.pages.length - 1, r.page));
        holder.innerHTML = '<img src="' + esc(url(r.pages[r.page])) + '" alt="Page ' + (r.page + 1) + '" />';
        const nxt = r.pages[r.page + 1]; if (nxt) { const pre = new Image(); pre.src = url(nxt); }
        count(); record();
      };
      holder.onclick = (e) => {
        const left = e.clientX < window.innerWidth / 2;
        const forward = r.mode === 'rtl' ? left : !left;
        r.page += forward ? 1 : -1; show();
      };
      r.show = show;
      show();
    }
    count();
  }
  function count() { const r = S.reader; if (r) r.box.querySelector('.rCount').textContent = (r.page + 1) + ' / ' + r.pages.length; }
  let recordTimer = 0;
  function record() {
    const r = S.reader; if (!r || ls.get('incognito', false)) return;
    clearTimeout(recordTimer);
    recordTimer = setTimeout(async () => {
      try { S.library = await api('/history', { manga: r.manga, chapter: r.chapter, page: r.page, pageCount: r.pages.length, readAt: Date.now() }); } catch (e) {}
    }, 800);
  }
  function readerKeys(e) {
    const r = S.reader; if (!r) return;
    if (e.key === 'Escape') { closeReader(); render(); return; }
    if (r.mode === 'webtoon' || !r.show) return;
    if (e.key === 'ArrowRight') { r.page += r.mode === 'rtl' ? -1 : 1; r.show(); }
    if (e.key === 'ArrowLeft') { r.page += r.mode === 'rtl' ? 1 : -1; r.show(); }
  }
  function closeReader() {
    if (!S.reader) return;
    record();
    S.reader.box.remove();
    S.reader = null;
    document.body.classList.remove('mReading');
    document.removeEventListener('keydown', readerKeys);
  }

  // ---------------------------------------------------------------- More
  async function moreScreen(root) {
    try { await loadSources(); await loadLibrary(); } catch (e) {}
    const page = S.morePage || '';
    if (page === 'categories') {
      shell(root, 'Categories', '<button class="mIcon" data-act="mback" title="Back">' + icon('back') + '</button>', '<div class="mList">' +
        S.library.categories.map((c) => '<div class="mRow"><span class="txt"><strong>' + esc(c.name) + '</strong><span class="sub">' + S.library.entries.filter((e) => e.categoryIds.indexOf(c.id) >= 0).length + ' titles</span></span></div>').join('') +
        '</div><form id="newCat" class="mSearchRow"><input class="mSearch" id="catName" placeholder="Category name" /><button class="solid" type="submit">Add</button></form>', true);
      root.querySelector('#newCat').addEventListener('submit', async (e) => { e.preventDefault(); const n = root.querySelector('#catName').value.trim(); if (n) { S.library = await api('/categories', { name: n }); render(); } });
    } else if (page === 'stats') {
      const lib = S.library;
      const rows = [['Titles in library', lib.entries.length], ['Chapters seen', lib.entries.reduce((a, e) => a + e.chapters.length, 0)], ['Chapters read', lib.readChapterIds.length], ['Categories', lib.categories.length], ['Sources', S.sources.length]];
      shell(root, 'Statistics', '<button class="mIcon" data-act="mback" title="Back">' + icon('back') + '</button>', '<div class="mList">' + rows.map(([k, v]) => '<div class="mRow"><span class="txt"><strong>' + esc(k) + '</strong></span><span class="stat">' + v + '</span></div>').join('') + '</div>', true);
    } else if (page === 'about') {
      shell(root, 'About', '<button class="mIcon" data-act="mback" title="Back">' + icon('back') + '</button>', '<div class="mEmpty"><div class="face">み</div><p>Weaverse Manga on the web — the APK\'s Manga Studio sources (' + esc(S.sources.map((s) => s.name).join(', ')) + '), run by Weaverse Desktop on this PC.</p></div>', true);
    } else {
      const incognito = ls.get('incognito', false);
      shell(root, 'More', '', '<div class="mMoreHead"><div class="face">み</div><strong>Weaverse Manga</strong></div><div class="mList">' +
        '<label class="mRow"><span class="txt"><strong>Incognito mode</strong><span class="sub">Pauses reading history</span></span><input type="checkbox" id="incog"' + (incognito ? ' checked' : '') + ' /></label>' +
        [['categories', 'Categories', 'Organise your library'], ['stats', 'Statistics', 'Your reading at a glance'], ['about', 'About', 'Sources and version']].map(([k, t, s]) =>
          '<button class="mRow" data-more="' + k + '"><span class="txt"><strong>' + t + '</strong><span class="sub">' + s + '</span></span></button>').join('') + '</div>', true);
      root.querySelector('#incog').addEventListener('change', (e) => ls.set('incognito', e.target.checked));
      root.querySelectorAll('[data-more]').forEach((b) => b.addEventListener('click', () => { S.morePage = b.dataset.more; render(); }));
    }
    const mb = root.querySelector('[data-act="mback"]');
    if (mb) mb.addEventListener('click', () => { S.morePage = ''; render(); });
  }

  window.WeaverseManga = {
    mount(stage) {
      R.stage = stage;
      if (!stage.querySelector('#mangaRoot')) stage.insertAdjacentHTML('beforeend', '<div id="mangaRoot"></div>');
      render();
    },
    leave() { closeReader(); }
  };
})();
