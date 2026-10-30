/**
 * pm-sprints.js — PHASE 4 + GAP 19b
 * Adds to /pm/05-planning: sprint picker, Start / Complete buttons, a Complete dialog with
 * carry-over, and retrospective integration (opens pm-retro.js on demand).
 * Needs pm-core.js and the small hooks added to 05-planning.html (window.__pmPlanState).
 * Also needs pm/js/pm-retro.js (loaded lazily by withRetro).
 */
(function () {
  'use strict';

  function S() { return window.__pmPlanState || {}; }
  function api(m, u, b) { return PM.api(m, u, b); }
  function fail(e) { PM.toast((e && e.message) || 'Failed', 'error'); }

  /* ───────── Gap 19b: lazy-load pm-retro.js ───────── */
  function withRetro(fn) {
    if (window.PMRetro) return fn();
    var s = document.createElement('script');
    s.src = '/pm/js/pm-retro.js';
    s.onload = fn;
    s.onerror = function () { PM.toast('Could not load the retrospective module', 'error'); };
    document.body.appendChild(s);
  }
  function openRetro(id) { withRetro(function () { PMRetro.open(id); }); }
  function retroHistory() { withRetro(function () { PMRetro.history(S().projectId); }); }

  function cur() {
    var st = S();
    return (st.sprints || []).find(function (s) { return s.id === st.activeSprintId; }) || null;
  }
  var CLS = { PLANNED: 'primary', ACTIVE: 'success', COMPLETED: 'info' };

  /* ───────── data helpers ───────── */
  async function ensureSprints() {
    var st = S();
    if (!st.projectId) return [];
    if (!st.sprints || !st.sprints.length || st._sprintsProject !== st.projectId) {
      try {
        st.sprints = await api('GET', '/api/pm/projects/' + st.projectId + '/sprints') || [];
        st._sprintsProject = st.projectId;
      } catch (e) { st.sprints = []; }
    }
    return st.sprints;
  }

  /** The sprint a dropped backlog item should go into: the active one, else the next planned one. */
  async function targetSprintId() {
    var list = await ensureSprints();
    var active = list.find(function (s) { return s.status === 'ACTIVE'; });
    if (active) return active.id;
    var planned = list.filter(function (s) { return s.status === 'PLANNED'; })
      .sort(function (a, b) { return String(a.startDate).localeCompare(String(b.startDate)); });
    return planned.length ? planned[0].id : null;
  }

  /* ───────── backlog side panel ───────── */
  async function refreshDropzone() {
    var h = document.querySelector('.bl-dropzone h4');
    if (!h) return;
    var list = await ensureSprints();
    var id = await targetSprintId();
    var s = list.find(function (x) { return x.id === id; });
    h.innerHTML = '<i class="fas fa-bolt"></i> ' + (s
      ? PM.esc(s.name) + ' <span class="pm-badge ' + (CLS[s.status] || 'primary') + '">' + s.status + '</span>'
      : 'No sprint yet');
    var btn = document.querySelector('.bl-dropzone .pm-btn-primary');
    if (btn) {
      var running = !!(s && s.status === 'ACTIVE');
      btn.innerHTML = '<i class="fas fa-play"></i> ' + (running ? 'Sprint running' : 'Start Sprint');
      btn.disabled = running;
      btn.style.opacity = running ? '.5' : '1';
    }
  }

  async function startNext() {
    var list = await ensureSprints();
    if (list.some(function (s) { return s.status === 'ACTIVE'; })) { PM.toast('A sprint is already running', 'info'); return; }
    var id = await targetSprintId();
    if (!id) { PM.toast('Create a sprint first (Sprints tab → New Sprint)', 'warning'); return; }
    return start(id);
  }

  /* ───────── actions ───────── */
  function pin(id) {
    var st = S();
    st.pinnedSprintId = parseInt(id, 10) || null;
    st.activeSprintId = st.pinnedSprintId;
    st._sprintsProject = null;                 // force a reload of the sprint list
    if (window.pmRefresh) window.pmRefresh();
  }

  function start(id) {
    var s = (S().sprints || []).find(function (x) { return x.id === id; });
    if (!window.confirm('Start ' + (s ? s.name : 'this sprint') + '? Everyone assigned to its tasks will be notified.')) return;
    api('POST', '/api/pm/sprints/' + id + '/start')
      .then(function () { PM.toast('🚀 Sprint started', 'success'); pin(id); })
      .catch(fail);
  }

  function openComplete(id) {
    var st = S();
    var s = (st.sprints || []).find(function (x) { return x.id === id; });
    if (!s) return;
    PM.openDrawer('<div style="padding:24px;text-align:center;color:var(--tx3)"><i class="fas fa-spinner fa-spin"></i> Loading…</div>',
      'Complete · ' + s.name);

    api('GET', '/api/pm/sprints/' + id + '/tasks').then(function (tasks) {
      tasks = tasks || [];
      var done = tasks.filter(function (t) { return t.status === 'DONE'; });
      var open = tasks.filter(function (t) { return t.status !== 'DONE'; });
      var pts = function (l) { return l.reduce(function (a, t) { return a + (t.storyPoints || 0); }, 0); };
      var planned = (st.sprints || []).filter(function (x) { return x.id !== id && x.status === 'PLANNED'; });

      var h = '<div class="pm-card"><div style="font-weight:600">' + done.length + ' of ' + tasks.length +
        ' tasks done · ' + pts(done) + ' of ' + pts(tasks) + ' story points</div>' +
        '<div style="font-size:var(--fs-sm);color:var(--tx2);margin-top:4px">Velocity recorded for this sprint: <b>' +
        pts(done) + ' pts</b></div></div>';

      if (open.length) {
        h += '<div class="pm-card"><div style="font-size:var(--fs-xs);color:var(--tx3);text-transform:uppercase;font-weight:700;margin-bottom:6px">' +
          open.length + ' unfinished task(s)</div>' +
          open.map(function (t) {
            return '<div style="display:flex;gap:8px;padding:3px 0"><span style="flex:1">' + PM.esc(t.title) +
              '</span><span class="pm-badge warning">' + PM.esc(String(t.status).replace('_', ' ')) + '</span>' +
              '<span style="color:var(--tx3)">' + (t.storyPoints || 0) + ' pts</span></div>';
          }).join('') +
          '<div style="margin-top:10px;font-size:var(--fs-xs);color:var(--tx3);text-transform:uppercase;font-weight:700;margin-bottom:6px">Move them to</div>' +
          '<select class="pm-select" id="pmCarrySel"><option value="">Backlog</option>' +
          planned.map(function (x) { return '<option value="' + x.id + '">' + PM.esc(x.name) + ' (planned)</option>'; }).join('') +
          '</select>' +
          (planned.length ? '' : '<div style="font-size:var(--fs-xs);color:var(--tx3);margin-top:4px">No planned sprint exists. Create one first if you want to carry the work over.</div>') +
          '</div>';
      } else {
        h += '<div class="pm-card" style="color:var(--suc)"><i class="fas fa-circle-check"></i> Everything in this sprint is done.</div>';
      }

      // Gap 19b Edit 2: no more retro textarea in the Complete dialog.
      h += '<div class="pm-card" style="font-size:var(--fs-sm);color:var(--tx2)"><i class="fas fa-comments"></i> After completing, you will be asked to start the retrospective.</div>' +
        '<div style="display:flex;gap:6px;justify-content:flex-end">' +
        '<button class="pm-btn" onclick="PM.closeDrawer()">Cancel</button>' +
        '<button class="pm-btn pm-btn-primary" id="pmCompleteBtn"><i class="fas fa-flag-checkered"></i> Complete sprint</button></div>';

      document.getElementById('pmDrawerBody').innerHTML = h;
      document.getElementById('pmCompleteBtn').onclick = function () {
        var sel = document.getElementById('pmCarrySel');
        var carry = sel && sel.value ? parseInt(sel.value, 10) : null;
        this.disabled = true;
        // Gap 19b Edit 3: no retroNotes in the body; prompt the retro after completion instead.
        api('POST', '/api/pm/sprints/' + id + '/complete', { carryOverSprintId: carry })
          .then(function (r) {
            PM.toast('Sprint completed · velocity ' + (r && r.velocity != null ? r.velocity : 0) + ' pts', 'success');
            PM.closeDrawer();
            pin(id);
            withRetro(function () { PMRetro.promptAfterComplete(id, s.name); });
          })
          .catch(function (e) { fail(e); var b = document.getElementById('pmCompleteBtn'); if (b) b.disabled = false; });
      };
    }).catch(function (e) {
      document.getElementById('pmDrawerBody').innerHTML =
        '<div class="pm-card" style="color:var(--dng)">' + PM.esc(e.message || 'Failed to load') + '</div>';
    });
  }

  // Kept for backwards compatibility. The real retro editing lives in pm-retro.js.
  function saveRetro() {
    PM.toast('Use the Retrospective panel to edit notes', 'info');
  }

  /* ───────── rendering (called by the page after each loadSprints) ───────── */
  async function render() {
    var st = S();
    var grid = document.querySelector('#pane-sprints .sprint-grid');
    if (!grid) return;

    var host = document.getElementById('pmSprintToolbar');
    if (!host) {
      host = document.createElement('div');
      host.id = 'pmSprintToolbar';
      grid.parentNode.insertBefore(host, grid);
    }
    var retroHost = document.getElementById('sprintRetroHost');
    if (!retroHost) {
      retroHost = document.createElement('div');
      retroHost.id = 'sprintRetroHost';
      document.getElementById('pane-sprints').appendChild(retroHost);
    }

    var list = st.sprints || [];
    var s = cur();
    if (!list.length || !s) { host.innerHTML = ''; retroHost.innerHTML = ''; return; }

    var canManage = true;
    try { var a = await api('GET', '/api/pm/projects/' + st.projectId + '/members/me'); canManage = !!(a && a.canManage); } catch (e) { }
    var vel = null;
    try { vel = await api('GET', '/api/pm/projects/' + st.projectId + '/velocity'); } catch (e) { }

    var opts = list.map(function (x) {
      return '<option value="' + x.id + '"' + (x.id === s.id ? ' selected' : '') + '>' + PM.esc(x.name) + ' · ' + x.status + '</option>';
    }).join('');

    var btns = '';
    if (canManage && s.status === 'PLANNED')
      btns = '<button class="pm-btn pm-btn-primary" onclick="PMSprints.start(' + s.id + ')"><i class="fas fa-play"></i> Start sprint</button>';
    else if (canManage && s.status === 'ACTIVE')
      btns = '<button class="pm-btn pm-btn-primary" onclick="PMSprints.openComplete(' + s.id + ')"><i class="fas fa-flag-checkered"></i> Complete sprint</button>';
    else if (s.status === 'COMPLETED')
      btns = '<span class="pm-badge info">Closed · velocity ' + (s.velocity != null ? s.velocity : 0) + ' pts</span>';

    host.innerHTML = '<div class="pm-card" style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">' +
      '<select class="pm-select" style="max-width:280px" onchange="PMSprints.pin(this.value)">' + opts + '</select>' +
      '<span class="pm-badge ' + (CLS[s.status] || 'primary') + '">' + s.status + '</span>' + btns +
      '<span style="margin-left:auto;font-size:var(--fs-sm);color:var(--tx2)">' +
      (vel && vel.sprintsCounted
        ? 'Avg velocity (last ' + vel.sprintsCounted + '): <b style="color:var(--tx)">' + vel.averageVelocity + ' pts</b> · suggested commitment <b style="color:var(--tx)">' + vel.suggestedCommitment + '</b>'
        : 'Velocity appears after your first completed sprint') +
      '</span></div>';

    // Gap 19b Edit 4: Retrospective card with Open + History buttons (edit rights enforced in pm-retro.js).
    retroHost.innerHTML = '<div class="pm-card" style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">' +
      '<div style="font-weight:600"><i class="fas fa-comments" style="color:var(--pri)"></i> Retrospective · ' + PM.esc(s.name) + '</div>' +
      '<span style="margin-left:auto;display:flex;gap:6px;flex-wrap:wrap">' +
      '<button class="pm-btn pm-btn-primary" onclick="PMSprints.openRetro(' + s.id + ')"><i class="fas fa-comments"></i> Open retrospective</button>' +
      '<button class="pm-btn" onclick="PMSprints.retroHistory()"><i class="fas fa-clock-rotate-left"></i> History</button></span></div>';
  }

  window.PMSprints = {
    render: render, pin: pin, start: start, startNext: startNext,
    openComplete: openComplete, saveRetro: saveRetro,
    openRetro: openRetro, retroHistory: retroHistory,
    targetSprintId: targetSprintId, refreshDropzone: refreshDropzone
  };
})();