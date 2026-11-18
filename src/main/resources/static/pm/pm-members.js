/**
 * pm-members.js — PHASE 2
 * Member-management drawers for projects and teams.
 * Loaded on demand by PM.openProjectMembers(id, name) / PM.openTeamMembers(id, name)
 * (both defined in pm-core.js). Uses only PM.api / PM.openDrawer / PM.toast / PM.esc.
 */
(function () {
  'use strict';

  var ROLES = ['MANAGER', 'LEAD', 'MEMBER', 'VIEWER'];
  var ROLE_CLS = { MANAGER: 'primary', LEAD: 'purple', MEMBER: 'info', VIEWER: 'warning' };
  var people = null;

  /* ───────── helpers ───────── */
  function loadPeople() {
    if (people) return Promise.resolve(people);
    return PM.api('GET', '/api/pm/employees/dropdown')
      .then(function (list) {
        people = (list || []).map(function (e) { return { id: e.id, name: e.name }; });
        return people;
      })
      .catch(function () { people = []; return people; });
  }
  function bodyEl() { return document.getElementById('pmDrawerBody'); }
  function spinner() {
    return '<div style="padding:24px;text-align:center;color:var(--tx3)"><i class="fas fa-spinner fa-spin"></i> Loading…</div>';
  }
  function fail(e) {
    var b = bodyEl();
    if (b) b.innerHTML = '<div class="pm-card" style="color:var(--dng)">' + PM.esc((e && e.message) || 'Failed to load') + '</div>';
  }
  function avatar(name) {
    return '<div style="width:26px;height:26px;border-radius:50%;background:var(--pri);color:#fff;font-size:10px;font-weight:700;display:grid;place-content:center;flex-shrink:0">' +
      PM.esc(PM.getInitials(name)) + '</div>';
  }
  function personOptions(excludeIds) {
    return (people || [])
      .filter(function (p) { return excludeIds.indexOf(p.id) === -1; })
      .map(function (p) { return '<option value="' + p.id + '">' + PM.esc(p.name) + '</option>'; })
      .join('');
  }
  function toInt(v, dflt) { var n = parseInt(v, 10); return isNaN(n) ? dflt : n; }
  function sectionTitle(icon, text) {
    return '<div style="font-size:var(--fs-xs);color:var(--tx3);text-transform:uppercase;font-weight:700;margin-bottom:8px"><i class="fas ' + icon + '"></i> ' + text + '</div>';
  }

  /* ───────── multi-select people picker (used by both "Add member" boxes) ───────── */
  function pickerHtml(pfx) {
    return '<input class="pm-input" id="' + pfx + 'Search" placeholder="Search people…" autocomplete="off" style="width:100%;margin-bottom:6px">' +
      '<div style="display:flex;justify-content:space-between;align-items:center;font-size:var(--fs-xs);color:var(--tx3);margin-bottom:4px">' +
      '<span id="' + pfx + 'Count">0 selected</span>' +
      '<span><a href="javascript:void(0)" id="' + pfx + 'All">Select all shown</a> · <a href="javascript:void(0)" id="' + pfx + 'None">Clear</a></span></div>' +
      '<div id="' + pfx + 'List" style="max-height:220px;overflow:auto;border:1px solid var(--bd,rgba(128,128,128,.25));border-radius:8px"></div>';
  }
  /** sel is a Set of employee ids that survives searching; onChange(n) is called whenever the count changes. */
  function pickerInit(pfx, usedIds, sel, onChange) {
    var search = document.getElementById(pfx + 'Search');
    var list = document.getElementById(pfx + 'List');
    var count = document.getElementById(pfx + 'Count');
    function visible() {
      var q = (search.value || '').trim().toLowerCase();
      return (people || []).filter(function (p) {
        return usedIds.indexOf(p.id) === -1 && (!q || String(p.name).toLowerCase().indexOf(q) > -1);
      });
    }
    function tick() { count.textContent = sel.size + ' selected'; onChange(sel.size); }
    function paint() {
      var v = visible();
      list.innerHTML = v.length ? v.map(function (p) {
        return '<label style="display:flex;align-items:center;gap:8px;padding:6px 10px;cursor:pointer">' +
          '<input type="checkbox" data-pid="' + p.id + '"' + (sel.has(p.id) ? ' checked' : '') + '>' +
          avatar(p.name) + '<span>' + PM.esc(p.name) + '</span></label>';
      }).join('') : '<div style="padding:12px;text-align:center;color:var(--tx3)">' +
        ((people || []).length ? 'No matches' : 'No people available') + '</div>';
      tick();
    }
    list.onchange = function (ev) {
      var cb = ev.target;
      if (!cb || !cb.dataset || !cb.dataset.pid) return;
      var id = Number(cb.dataset.pid);
      if (cb.checked) sel.add(id); else sel.delete(id);
      tick();
    };
    search.oninput = paint;
    document.getElementById(pfx + 'All').onclick = function () { visible().forEach(function (p) { sel.add(p.id); }); paint(); };
    document.getElementById(pfx + 'None').onclick = function () { sel.clear(); paint(); };
    paint();
  }
  function addLabel(n) { return '<i class="fas fa-plus"></i> ' + (n ? 'Add ' + n + (n === 1 ? ' person' : ' people') : 'Add'); }
  function reportBulk(r) {
    r = r || {};
    var added = (r.addedIds || []).length, skipped = r.skipped || [];
    if (added) PM.toast(added + (added === 1 ? ' person' : ' people') + ' added', 'success');
    if (skipped.length) {
      var lines = skipped.slice(0, 3).map(function (s) {
        var p = (people || []).filter(function (x) { return x.id === s.employeeId; })[0];
        return (p ? p.name : '#' + s.employeeId) + ' — ' + s.reason;
      });
      PM.toast('Not added: ' + lines.join('; ') + (skipped.length > 3 ? ' … and ' + (skipped.length - 3) + ' more' : ''), 'warning', 8000);
    }
    if (!added && !skipped.length) PM.toast('Nothing to add', 'info');
  }

  /* ═════════════════ PROJECT MEMBERS ═════════════════ */
  var P = { id: null, name: '', access: {}, members: [], sel: new Set() };

  function openProject(projectId, name) {
    P.id = projectId;
    P.sel = new Set();
    P.name = name || ('Project #' + projectId);
    PM.openDrawer(spinner(), 'Members · ' + P.name);
    loadProject().catch(fail);
  }

  function loadProject() {
    var base = '/api/pm/projects/' + P.id + '/members';
    return Promise.all([PM.api('GET', base + '/me'), PM.api('GET', base), loadPeople()])
      .then(function (r) { P.access = r[0] || {}; P.members = r[1] || []; renderProject(); });
  }

  function renderProject() {
    var can = !!P.access.canAdminister;
    var h = '<div class="pm-card" style="display:flex;gap:8px;align-items:center;justify-content:space-between">' +
      '<div><div style="font-weight:600">' + PM.esc(P.name) + '</div>' +
      '<div style="font-size:var(--fs-xs);color:var(--tx3)">Your access: ' + PM.esc(P.access.label || '—') + '</div></div>' +
      '<span class="pm-badge ' + (can ? 'primary' : 'info') + '">' + (can ? 'Can manage' : 'View only') + '</span></div>';

    if (can) {
      var used = P.members.map(function (m) { return m.employeeId; });
      h += '<div class="pm-card">' + sectionTitle('fa-user-plus', 'Add members') +
        pickerHtml('pmmPick') +
        '<div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:10px">' +
        '<select class="pm-select" id="pmmRole" title="Role for everyone selected">' +
        ROLES.map(function (r) { return '<option value="' + r + '"' + (r === 'MEMBER' ? ' selected' : '') + '>' + r + '</option>'; }).join('') +
        '</select>' +
        '<input class="pm-input" id="pmmAlloc" type="number" min="0" max="100" value="100" placeholder="Allocation %" title="Allocation for everyone selected">' +
        '<input class="pm-input" id="pmmRate" type="number" min="0" step="0.01" placeholder="Hourly rate (optional)">' +
        '</div><div style="margin-top:8px;text-align:right">' +
        '<button class="pm-btn pm-btn-primary" id="pmmAdd" disabled><i class="fas fa-plus"></i> Add</button></div></div>';
    }

    if (!P.members.length) {
      h += '<div class="pm-card" style="text-align:center;color:var(--tx3)">No explicit members yet.</div>';
    } else {
      h += '<div class="pm-card" style="padding:0;overflow:hidden"><div style="overflow-x:auto"><table class="pm-table"><thead><tr>' +
        '<th>Person</th><th>Role</th><th>Alloc %</th><th>Util %</th>' + (can ? '<th>Rate / h</th><th></th>' : '') +
        '</tr></thead><tbody>' + P.members.map(function (m) { return projectRow(m, can); }).join('') + '</tbody></table></div></div>';
    }
    bodyEl().innerHTML = h;
    bindProject(can);
  }

  function projectRow(m, can) {
    var alloc = m.allocationPct == null ? 100 : m.allocationPct;
    var roleCell = can
      ? '<select class="pm-select pmm-role" style="min-width:110px">' +
        ROLES.map(function (r) { return '<option value="' + r + '"' + (m.role === r ? ' selected' : '') + '>' + r + '</option>'; }).join('') + '</select>'
      : '<span class="pm-badge ' + (ROLE_CLS[m.role] || 'info') + '">' + PM.esc(m.role) + '</span>';
    var allocCell = can
      ? '<input class="pm-input pmm-alloc" type="number" min="0" max="100" value="' + alloc + '" style="width:72px">'
      : alloc + '%';
    return '<tr data-id="' + m.id + '" data-name="' + PM.esc(m.employeeName) + '">' +
      '<td><div style="display:flex;align-items:center;gap:8px">' + avatar(m.employeeName) +
      '<div><div style="font-weight:600">' + PM.esc(m.employeeName) + (m.owner ? ' <span class="pm-badge purple">Owner</span>' : '') + '</div>' +
      '<div style="font-size:var(--fs-xs);color:var(--tx3)">' + PM.esc(m.employeeCode || '') + '</div></div></div></td>' +
      '<td>' + roleCell + '</td><td>' + allocCell + '</td>' +
      '<td>' + (function () {
        var util = m.utilizationPct == null ? 0 : m.utilizationPct;
        var color = util > 100 ? 'var(--dng)' : util > 85 ? 'var(--wrn)' : 'var(--suc)';
        var width = Math.max(0, Math.min(util, 100));
        return '<div style="display:flex;align-items:center;gap:6px"><div class="pm-progress" style="width:72px"><div class="pm-progress-bar" style="width:' + width + '%;background:' + color + '"></div></div><span style="font-size:var(--fs-xs);font-weight:600">' + util + '%</span></div>';
      })() + '</td>' +
      (can
        ? '<td><input class="pm-input pmm-rate" type="number" min="0" step="0.01" value="' + (m.hourlyRate == null ? '' : m.hourlyRate) + '" style="width:90px"></td>' +
          '<td><button class="pm-btn pm-btn-sm pm-btn-icon pm-btn-danger pmm-del" title="Remove"><i class="fas fa-trash"></i></button></td>'
        : '') +
      '</tr>';
  }

  function bindProject(can) {
    if (!can) return;
    var base = '/api/pm/projects/' + P.id + '/members';
    function done(msg) { PM.toast(msg, 'success'); return loadProject(); }
    function oops(e) { PM.toast(e.message || 'Failed', 'error'); loadProject().catch(fail); }

    var add = document.getElementById('pmmAdd');
    if (add) {
      var used = P.members.map(function (m) { return m.employeeId; });
      pickerInit('pmmPick', used, P.sel, function (n) { add.disabled = n === 0; add.innerHTML = addLabel(n); });
      add.onclick = function () {
        var ids = Array.from(P.sel);
        if (!ids.length) { PM.toast('Tick at least one person', 'warning'); return; }
        var alloc = toInt(document.getElementById('pmmAlloc').value, 100);
        if (alloc < 0 || alloc > 100) { PM.toast('Allocation must be 0–100', 'warning'); return; }
        var rate = document.getElementById('pmmRate').value;
        add.disabled = true;
        PM.api('POST', base + '/bulk', {
          employeeIds: ids,
          role: document.getElementById('pmmRole').value,
          allocationPct: alloc,
          hourlyRate: rate === '' ? null : Number(rate)
        }).then(function (r) { P.sel = new Set(); reportBulk(r); return loadProject(); }).catch(oops);
      };
    }

    bodyEl().querySelectorAll('tr[data-id]').forEach(function (tr) {
      var id = tr.dataset.id;
      function save(patch) {
        PM.api('PUT', base + '/' + id, patch).then(function () { return done('Saved'); }).catch(oops);
      }
      var role = tr.querySelector('.pmm-role');
      if (role) role.onchange = function () { save({ role: role.value }); };
      var alloc = tr.querySelector('.pmm-alloc');
      if (alloc) alloc.onchange = function () {
        var v = toInt(alloc.value, -1);
        if (v < 0 || v > 100) { PM.toast('Allocation must be 0–100', 'warning'); loadProject().catch(fail); return; }
        save({ allocationPct: v });
      };
      var rate = tr.querySelector('.pmm-rate');
      if (rate) rate.onchange = function () { if (rate.value !== '') save({ hourlyRate: Number(rate.value) }); };
      var del = tr.querySelector('.pmm-del');
      if (del) del.onclick = function () {
        if (!window.confirm('Remove ' + tr.dataset.name + ' from this project?')) return;
        PM.api('DELETE', base + '/' + id).then(function () { return done('Member removed'); }).catch(oops);
      };
    });
  }

  /* ═════════════════ TEAM MEMBERS ═════════════════ */
  var T = { id: null, name: '', can: false, members: [], sel: new Set() };

  function openTeam(teamId, name) {
    T.id = teamId;
    T.sel = new Set();
    T.name = name || ('Team #' + teamId);
    PM.openDrawer(spinner(), 'Team members · ' + T.name);
    loadTeam().catch(fail);
  }

  function loadTeam() {
    var base = '/api/pm/teams/' + T.id + '/members';
    return Promise.all([PM.api('GET', base + '/me'), PM.api('GET', base + '/details'), loadPeople()])
      .then(function (r) { T.can = !!(r[0] && r[0].canManage); T.members = r[1] || []; renderTeam(); });
  }

  function renderTeam() {
    var can = T.can;
    var h = '<div class="pm-card" style="display:flex;justify-content:space-between;align-items:center">' +
      '<div style="font-weight:600">' + PM.esc(T.name) + '</div>' +
      '<span class="pm-badge ' + (can ? 'primary' : 'info') + '">' + (can ? 'Can manage' : 'View only') + '</span></div>';

    if (can) {
      h += '<div class="pm-card">' + sectionTitle('fa-user-plus', 'Add members') +
        pickerHtml('pmtPick') +
        '<div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:10px">' +
        '<input class="pm-input" id="pmtRole" value="Developer" maxlength="80" placeholder="Role in team" title="Role for everyone selected">' +
        '<input class="pm-input" id="pmtAlloc" type="number" min="0" max="100" value="100" placeholder="Allocation %" title="Allocation for everyone selected">' +
        '</div><div style="margin-top:8px;text-align:right">' +
        '<button class="pm-btn pm-btn-primary" id="pmtAdd" disabled><i class="fas fa-plus"></i> Add</button></div></div>';
    }

    if (!T.members.length) {
      h += '<div class="pm-card" style="text-align:center;color:var(--tx3)">No members yet.</div>';
    } else {
      h += '<div class="pm-card" style="padding:0;overflow:hidden"><div style="overflow-x:auto"><table class="pm-table"><thead><tr>' +
        '<th>Person</th><th>Role in team</th><th>Alloc %</th>' + (can ? '<th></th>' : '') +
        '</tr></thead><tbody>' + T.members.map(function (m) { return teamRow(m, can); }).join('') + '</tbody></table></div></div>';
    }
    bodyEl().innerHTML = h;
    bindTeam(can);
  }

  function teamRow(m, can) {
    var alloc = m.allocationPct == null ? 100 : m.allocationPct;
    return '<tr data-emp="' + m.employeeId + '" data-name="' + PM.esc(m.employeeName) + '">' +
      '<td><div style="display:flex;align-items:center;gap:8px">' + avatar(m.employeeName) +
      '<div><div style="font-weight:600">' + PM.esc(m.employeeName) + (m.lead ? ' <span class="pm-badge purple">Lead</span>' : '') + '</div>' +
      '<div style="font-size:var(--fs-xs);color:var(--tx3)">' + PM.esc(m.employeeCode || '') + '</div></div></div></td>' +
      '<td>' + (can ? '<input class="pm-input pmt-role" value="' + PM.esc(m.roleInTeam || '') + '" maxlength="80" style="min-width:120px">' : PM.esc(m.roleInTeam || '—')) + '</td>' +
      '<td>' + (can ? '<input class="pm-input pmt-alloc" type="number" min="0" max="100" value="' + alloc + '" style="width:72px">' : alloc + '%') + '</td>' +
      (can ? '<td><button class="pm-btn pm-btn-sm pm-btn-icon pm-btn-danger pmt-del" title="Remove"><i class="fas fa-trash"></i></button></td>' : '') +
      '</tr>';
  }

  function bindTeam(can) {
    if (!can) return;
    var base = '/api/pm/teams/' + T.id + '/members';
    function done(msg) { PM.toast(msg, 'success'); return loadTeam(); }
    function oops(e) { PM.toast(e.message || 'Failed', 'error'); loadTeam().catch(fail); }

    var add = document.getElementById('pmtAdd');
    if (add) {
      var used = T.members.map(function (m) { return m.employeeId; });
      pickerInit('pmtPick', used, T.sel, function (n) { add.disabled = n === 0; add.innerHTML = addLabel(n); });
      add.onclick = function () {
        var ids = Array.from(T.sel);
        if (!ids.length) { PM.toast('Tick at least one person', 'warning'); return; }
        var alloc = toInt(document.getElementById('pmtAlloc').value, 100);
        if (alloc < 0 || alloc > 100) { PM.toast('Allocation must be 0–100', 'warning'); return; }
        add.disabled = true;
        PM.api('POST', base + '/bulk', {
          employeeIds: ids,
          roleInTeam: document.getElementById('pmtRole').value,
          allocationPct: alloc
        }).then(function (r) { T.sel = new Set(); reportBulk(r); return loadTeam(); }).catch(oops);
      };
    }

    bodyEl().querySelectorAll('tr[data-emp]').forEach(function (tr) {
      var emp = tr.dataset.emp;
      function save(patch) {
        PM.api('PUT', base + '/' + emp, patch).then(function () { return done('Saved'); }).catch(oops);
      }
      var role = tr.querySelector('.pmt-role');
      if (role) role.onchange = function () { if (role.value.trim()) save({ roleInTeam: role.value.trim() }); };
      var alloc = tr.querySelector('.pmt-alloc');
      if (alloc) alloc.onchange = function () {
        var v = toInt(alloc.value, -1);
        if (v < 0 || v > 100) { PM.toast('Allocation must be 0–100', 'warning'); loadTeam().catch(fail); return; }
        save({ allocationPct: v });
      };
      var del = tr.querySelector('.pmt-del');
      if (del) del.onclick = function () {
        if (!window.confirm('Remove ' + tr.dataset.name + ' from this team?')) return;
        PM.api('DELETE', base + '/' + emp).then(function () { return done('Member removed'); }).catch(oops);
      };
    });
  }

  window.PMMembers = { openProject: openProject, openTeam: openTeam };
})();