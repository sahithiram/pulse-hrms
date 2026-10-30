/** pm-task-filter.js  Gap 14: filter bar + saved filters + bulk toolbar. Needs pm-core.js. */
(function () {
  'use strict';
  var S = { projectId: null, f: {}, saved: [], sel: {}, rows: [], render: null, meta: {} };

  function el(id) { return document.getElementById(id); }
  function debounce(fn, ms) { var t; return function () { var a = arguments, c = this; clearTimeout(t); t = setTimeout(function () { fn.apply(c, a); }, ms); }; }

  /* ---------- URL hash state ---------- */
  function writeHash() {
    var p = [];
    Object.keys(S.f).forEach(function (k) {
      var v = S.f[k]; if (v === '' || v == null || (Array.isArray(v) && !v.length)) return;
      p.push(encodeURIComponent(k) + '=' + encodeURIComponent(Array.isArray(v) ? v.join(',') : v));
    });
    history.replaceState(null, '', '#' + p.join('&'));
  }
  function readHash() {
    var o = {}; (location.hash || '').replace(/^#/, '').split('&').forEach(function (kv) {
      if (!kv) return; var i = kv.indexOf('='); if (i < 0) return;
      var k = decodeURIComponent(kv.slice(0, i)), v = decodeURIComponent(kv.slice(i + 1));
      o[k] = (k === 'statuses' || k === 'priorities') ? v.split(',') : v;
    });
    return o;
  }

  /* ---------- request ---------- */
  function body() {
    var f = S.f, b = { projectId: S.projectId, page: 0, size: 200 };
    if (f.search) b.search = f.search;
    if (f.statuses && f.statuses.length) b.statuses = f.statuses;
    if (f.priorities && f.priorities.length) b.priorities = f.priorities;
    if (f.assigneeId === 'none') b.unassignedOnly = true; else if (f.assigneeId) b.assigneeId = +f.assigneeId;
    if (f.sprintId) b.sprintId = +f.sprintId;
    if (f.milestoneId) b.milestoneId = +f.milestoneId;
    if (f.dueFrom) b.dueFrom = f.dueFrom;
    if (f.dueTo) b.dueTo = f.dueTo;
    return b;
  }

  function run() {
    writeHash();
    return PM.api('POST', '/api/pm/tasks/filter', body()).then(function (page) {
      S.rows = (page && page.content) || [];
      S.sel = {};
      if (S.render) S.render(S.rows);
      syncBar();
    }).catch(function (e) { PM.toast(e.message || 'Filter failed', 'error'); });
  }
  var runDebounced = debounce(run, 300);

  /* ---------- bar ---------- */
  function opts(list, val, label) {
    return '<option value="">' + label + '</option>' + (list || []).map(function (x) {
      return '<option value="' + x.id + '"' + (String(val) === String(x.id) ? ' selected' : '') + '>' + PM.esc(x.name || x.title || x.fullName) + '</option>';
    }).join('');
  }
  function multiSel(id, vals, current) {
    return '<select class="pm-select" id="' + id + '" multiple size="1" style="min-width:110px" title="Hold Ctrl to pick several">' +
      vals.map(function (v) { return '<option' + ((current || []).indexOf(v) > -1 ? ' selected' : '') + '>' + v + '</option>'; }).join('') + '</select>';
  }
  function bar() {
    var f = S.f, m = S.meta;
    el('pmFilterBar').innerHTML =
      '<div class="pm-card" style="display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:8px;align-items:end">' +
      '<input class="pm-input" id="pfSearch" placeholder="Search tasks…" value="' + PM.esc(f.search || '') + '">' +
      multiSel('pfStatus', ['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'BLOCKED', 'DONE'], f.statuses) +
      multiSel('pfPrio', ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'], f.priorities) +
      '<select class="pm-select" id="pfAssignee"><option value="">Any assignee</option><option value="none"' + (f.assigneeId === 'none' ? ' selected' : '') + '>Unassigned</option>' +
        (m.members || []).map(function (x) { return '<option value="' + x.employeeId + '"' + (String(f.assigneeId) === String(x.employeeId) ? ' selected' : '') + '>' + PM.esc(x.name || x.employeeName) + '</option>'; }).join('') + '</select>' +
      '<select class="pm-select" id="pfSprint">' + opts(m.sprints, f.sprintId, 'Any sprint') + '</select>' +
      '<select class="pm-select" id="pfMile">' + opts(m.milestones, f.milestoneId, 'Any milestone') + '</select>' +
      '<input class="pm-input" type="date" id="pfFrom" value="' + (f.dueFrom || '') + '" title="Due from">' +
      '<input class="pm-input" type="date" id="pfTo" value="' + (f.dueTo || '') + '" title="Due to">' +
      '<button class="pm-btn" id="pfClear">Clear</button>' +
      '</div>' +
      '<div class="pm-card" style="display:flex;gap:8px;flex-wrap:wrap;align-items:center">' +
        '<select class="pm-select" id="pfSaved" style="min-width:180px"></select>' +
        '<button class="pm-btn pm-btn-sm" id="pfSave">Save current</button>' +
        '<button class="pm-btn pm-btn-sm" id="pfRename">Rename</button>' +
        '<button class="pm-btn pm-btn-sm" id="pfDefault">Set default</button>' +
        '<button class="pm-btn pm-btn-sm" id="pfDelete">Delete</button>' +
      '</div>';
    wire(); renderSaved();
  }
  function selVals(id) { return Array.prototype.map.call(el(id).selectedOptions, function (o) { return o.value || o.text; }); }
  function wire() {
    el('pfSearch').oninput = function () { S.f.search = this.value; runDebounced(); };
    el('pfStatus').onchange = function () { S.f.statuses = selVals('pfStatus'); run(); };
    el('pfPrio').onchange = function () { S.f.priorities = selVals('pfPrio'); run(); };
    el('pfAssignee').onchange = function () { S.f.assigneeId = this.value; run(); };
    el('pfSprint').onchange = function () { S.f.sprintId = this.value; run(); };
    el('pfMile').onchange = function () { S.f.milestoneId = this.value; run(); };
    el('pfFrom').onchange = function () { S.f.dueFrom = this.value; run(); };
    el('pfTo').onchange = function () { S.f.dueTo = this.value; run(); };
    el('pfClear').onclick = function () { S.f = {}; bar(); run(); };
    el('pfSaved').onchange = function () { applySaved(+this.value); };
    el('pfSave').onclick = saveCurrent;
    el('pfRename').onclick = renameSaved;
    el('pfDefault').onclick = setDefault;
    el('pfDelete').onclick = deleteSaved;
  }

  /* ---------- saved filters ---------- */
  function loadSaved() {
    return PM.api('GET', '/api/pm/saved-filters?projectId=' + S.projectId).then(function (l) { S.saved = l || []; });
  }
  function renderSaved() {
    var s = el('pfSaved'); if (!s) return;
    s.innerHTML = '<option value="">Saved filters…</option>' + S.saved.map(function (x) {
      return '<option value="' + x.id + '">' + PM.esc(x.name) + (x.isDefault ? ' ★' : '') + '</option>';
    }).join('');
  }
  function cur() { var id = +el('pfSaved').value; return S.saved.filter(function (x) { return x.id === id; })[0]; }
  function applySaved(id) {
    var x = S.saved.filter(function (y) { return y.id === id; })[0]; if (!x) return;
    S.f = JSON.parse(JSON.stringify(x.criteria || {})); bar(); el('pfSaved').value = id; run();
  }
  function saveCurrent() {
    var name = window.prompt('Name for this filter:'); if (!name) return;
    PM.api('POST', '/api/pm/saved-filters', { name: name, projectId: S.projectId, criteria: S.f })
      .then(function () { PM.toast('Filter saved', 'success'); return loadSaved(); }).then(renderSaved)
      .catch(function (e) { PM.toast(e.message, 'error'); });
  }
  function renameSaved() {
    var x = cur(); if (!x) { PM.toast('Pick a saved filter first', 'warning'); return; }
    var name = window.prompt('New name:', x.name); if (!name) return;
    PM.api('PUT', '/api/pm/saved-filters/' + x.id, { name: name })
      .then(function () { PM.toast('Renamed', 'success'); return loadSaved(); }).then(renderSaved)
      .catch(function (e) { PM.toast(e.message, 'error'); });
  }
  function setDefault() {
    var x = cur(); if (!x) { PM.toast('Pick a saved filter first', 'warning'); return; }
    PM.api('POST', '/api/pm/saved-filters/' + x.id + '/default')
      .then(function () { PM.toast('Default set', 'success'); return loadSaved(); }).then(renderSaved)
      .catch(function (e) { PM.toast(e.message, 'error'); });
  }
  function deleteSaved() {
    var x = cur(); if (!x) { PM.toast('Pick a saved filter first', 'warning'); return; }
    if (!window.confirm('Delete filter "' + x.name + '"?')) return;
    PM.api('DELETE', '/api/pm/saved-filters/' + x.id)
      .then(function () { PM.toast('Deleted', 'success'); return loadSaved(); }).then(renderSaved)
      .catch(function (e) { PM.toast(e.message, 'error'); });
  }

  /* ---------- selection + bulk toolbar ---------- */
  function ensureToolbar() {
    if (el('pmBulkBar')) return;
    var d = document.createElement('div');
    d.id = 'pmBulkBar';
    d.style.cssText = 'position:sticky;bottom:0;z-index:50;display:none;gap:8px;flex-wrap:wrap;align-items:center;padding:10px 12px;background:var(--bg2,#1b2538);border-top:1px solid var(--bd,#2a3950)';
    d.innerHTML =
      '<b id="pbCount">0 selected</b>' +
      '<select class="pm-select" id="pbStatus"><option value="">Status…</option><option>TODO</option><option>IN_PROGRESS</option><option>IN_REVIEW</option><option>BLOCKED</option><option>DONE</option></select>' +
      '<select class="pm-select" id="pbPrio"><option value="">Priority…</option><option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></select>' +
      '<select class="pm-select" id="pbAssignee"></select>' +
      '<select class="pm-select" id="pbSprint"></select>' +
      '<button class="pm-btn pm-btn-danger" id="pbDelete">Delete</button>' +
      '<button class="pm-btn" id="pbNone">Clear</button>';
    (el('pmBulkHost') || document.body).appendChild(d);
    el('pbStatus').onchange = function () { if (this.value) bulk({ action: 'STATUS', status: this.value }); this.value = ''; };
    el('pbPrio').onchange = function () { if (this.value) bulk({ action: 'PRIORITY', priority: this.value }); this.value = ''; };
    el('pbAssignee').onchange = function () { if (this.value) bulk({ action: 'ADD_ASSIGNEE', employeeIds: [+this.value] }); this.value = ''; };
    el('pbSprint').onchange = function () {
      if (this.value === '') return;
      bulk({ action: 'SPRINT', sprintId: this.value === 'backlog' ? null : +this.value }); this.value = '';
    };
    el('pbDelete').onclick = function () { if (window.confirm('Delete ' + ids().length + ' task(s)?')) bulk({ action: 'DELETE' }); };
    el('pbNone').onclick = function () { S.sel = {}; paintChecks(); syncBar(); };
    fillToolbarLists();
  }
  function fillToolbarLists() {
    el('pbAssignee').innerHTML = '<option value="">Add assignee…</option>' + (S.meta.members || []).map(function (x) {
      return '<option value="' + x.employeeId + '">' + PM.esc(x.name || x.employeeName) + '</option>'; }).join('');
    el('pbSprint').innerHTML = '<option value="">Move to sprint…</option><option value="backlog">Backlog</option>' +
      (S.meta.sprints || []).map(function (x) { return '<option value="' + x.id + '">' + PM.esc(x.name) + '</option>'; }).join('');
  }
  function ids() { return Object.keys(S.sel).filter(function (k) { return S.sel[k]; }).map(Number); }
  function syncBar() {
    ensureToolbar();
    var n = ids().length;
    el('pmBulkBar').style.display = n ? 'flex' : 'none';
    el('pbCount').textContent = n + ' selected';
    var all = el('pmSelAll'); if (all) all.checked = S.rows.length > 0 && n === S.rows.length;
  }
  function paintChecks() {
    Array.prototype.forEach.call(document.querySelectorAll('.pm-sel'), function (c) { c.checked = !!S.sel[c.getAttribute('data-id')]; });
  }
  function toggle(id, on) { S.sel[id] = !!on; syncBar(); }
  function toggleAll(on) { S.rows.forEach(function (r) { S.sel[r.id] = !!on; }); paintChecks(); syncBar(); }

  function bulk(extra) {
    var b = Object.assign({ taskIds: ids() }, extra);
    if (!b.taskIds.length) return;
    PM.api('POST', '/api/pm/tasks/bulk', b).then(function (r) {
      var failed = (r && r.failed) || [];
      if (!failed.length) PM.toast((r.ok || 0) + ' task(s) updated', 'success');
      else {
        PM.toast((r.ok || 0) + ' updated, ' + failed.length + ' failed', 'warning');
        PM.openDrawer('<div class="pm-card"><b>' + (r.ok || 0) + ' updated, ' + failed.length + ' failed</b><ul style="margin:8px 0 0 16px">' +
          failed.map(function (m) { return '<li style="font-size:var(--fs-xs)">' + PM.esc(m) + '</li>'; }).join('') + '</ul></div>', 'Bulk result');
      }
      return run();
    }).catch(function (e) { PM.toast(e.message, 'error'); });
  }

  /* ---------- public ---------- */
  /** opts: { projectId, render(rows), members:[{employeeId,name}], sprints:[{id,name}], milestones:[{id,name}] } */
  function init(o) {
    S.projectId = o.projectId; S.render = o.render;
    S.meta = { members: o.members || [], sprints: o.sprints || [], milestones: o.milestones || [] };
    // Gap 14 (Step 8): refresh the bulk toolbar's assignee + sprint lists after every init,
    // otherwise switching projects leaves them stale.
    if (el('pmBulkBar')) fillToolbarLists();
    S.f = readHash();
    return loadSaved().then(function () {
      var hasHash = Object.keys(S.f).length > 0;
      if (!hasHash) { var d = S.saved.filter(function (x) { return x.isDefault; })[0]; if (d) S.f = JSON.parse(JSON.stringify(d.criteria || {})); }
      bar(); return run();
    });
  }
  window.PMTaskFilter = { init: init, run: run, toggle: toggle, toggleAll: toggleAll, selected: ids };
})();