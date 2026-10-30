/** pm-handover.js  Gap 4UI. Needs pm-core.js. Use: PMHandover.init(fromIdOrNull) */
(function () {
  'use strict';

  /* ADAPT these three to the real HandoverController */
  var EP = {
    tasks:   function () { return '/api/pm/handover/tasks'; },                                  // body {fromEmployeeId, toEmployeeId, taskIds}
    project: function (pid) { return '/api/pm/handover/projects/' + pid + '/owner'; },          // body {employeeId}
    team:    function (tid) { return '/api/pm/handover/team/' + tid + '/lead'; }               // body {employeeId}
  };

  var S = { from: null, pending: [], detail: null, people: [], sel: {} };

  function listEl() { return document.getElementById('hvList'); }
  function detEl() { return document.getElementById('hvDetail'); }

  function peopleOptions(exceptId, placeholder) {
    return '<option value="">' + placeholder + '</option>' + S.people
      .filter(function (p) { return String(p.id) !== String(exceptId); })
      .map(function (p) { return '<option value="' + p.id + '">' + PM.esc(p.name) + '</option>'; }).join('');
  }

  function init(from) {
    S.from = from ? +from : null;
    listEl().innerHTML = spinner();
    return Promise.all([PM.api('GET', '/api/pm/handover/pending'), PM.api('GET', '/api/pm/handover/candidates').catch(function () { return []; })])
      .then(function (r) {
        S.pending = r[0] || []; S.people = r[1] || [];
        renderList();
        if (S.from) return openDetail(S.from);
      })
      .catch(function (e) { listEl().innerHTML = '<div class="pm-card" style="color:var(--dng)">' + PM.esc(e.message) + '</div>'; });
  }

  function spinner() { return '<div style="padding:20px;text-align:center;color:var(--tx3)"><i class="fas fa-spinner fa-spin"></i></div>'; }

  function renderList() {
    if (!S.pending.length) {
      listEl().innerHTML = '<div class="pm-card" style="text-align:center;color:var(--tx3)">Nobody needs a handover right now.</div>';
      return;
    }
    listEl().innerHTML = '<div class="pm-card" style="padding:0;overflow-x:auto"><table class="pm-table" style="width:100%;min-width:520px"><thead><tr>' +
      '<th>Employee</th><th>Last day</th><th>Tasks</th><th>Projects</th><th>Teams</th><th></th></tr></thead><tbody>' +
      S.pending.map(function (p) {
        return '<tr><td><b>' + PM.esc(p.name) + '</b></td><td>' + (p.lastWorkingDay ? PM.fmtDate(p.lastWorkingDay) : '—') + '</td>' +
          '<td>' + p.openTasks + '</td><td>' + p.ownedProjects + '</td><td>' + p.ledTeams + '</td>' +
          '<td><button class="pm-btn pm-btn-sm pm-btn-primary" onclick="PMHandover.open(' + p.employeeId + ')">Hand over</button></td></tr>';
      }).join('') + '</tbody></table></div>';
  }

  function openDetail(id) {
    S.from = id; S.sel = {};
    detEl().innerHTML = spinner();
    return PM.api('GET', '/api/pm/handover/pending/' + id)
      .then(function (d) { S.detail = d; d.tasks.forEach(function (t) { S.sel[t.id] = true; }); renderDetail(); })
      .catch(function (e) { detEl().innerHTML = '<div class="pm-card" style="color:var(--dng)">' + PM.esc(e.message) + '</div>'; });
  }

  function renderDetail() {
    var d = S.detail, h = '';
    h += '<div class="pm-card"><b>' + PM.esc(d.name) + '</b>' + (d.lastWorkingDay ? ' · last working day ' + PM.fmtDate(d.lastWorkingDay) : '') + '</div>';

    h += '<div class="pm-card"><div style="font-size:var(--fs-xs);text-transform:uppercase;font-weight:700;margin-bottom:6px">Open tasks (' + d.tasks.length + ')</div>';
    if (d.tasks.length) {
      h += '<div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin-bottom:8px">' +
        '<label><input type="checkbox" checked onchange="PMHandover.selAll(this.checked)"> All</label>' +
        '<select class="pm-select" id="hvTaskTo">' + peopleOptions(d.employeeId, 'Hand selected tasks to…') + '</select>' +
        '<button class="pm-btn pm-btn-primary pm-btn-sm" onclick="PMHandover.applyTasks()">Hand over tasks</button></div>' +
        '<div style="overflow-x:auto"><table class="pm-table" style="width:100%;min-width:480px"><tbody>' +
        d.tasks.map(function (t) {
          return '<tr><td style="width:32px"><input type="checkbox" class="hvSel" data-id="' + t.id + '" checked onchange="PMHandover.toggle(' + t.id + ', this.checked)"></td>' +
            '<td><b>' + PM.esc(t.title) + '</b><div style="font-size:var(--fs-xs);color:var(--tx3)">' + PM.esc(t.projectName) + '</div></td>' +
            '<td><span class="pm-badge">' + PM.esc(t.priority) + '</span></td><td>' + (t.dueDate ? PM.fmtDate(t.dueDate) : '—') + '</td></tr>';
        }).join('') + '</tbody></table></div>';
    } else h += '<div style="color:var(--tx3);font-size:var(--fs-sm)">None</div>';
    h += '</div>';

    h += '<div class="pm-card"><div style="font-size:var(--fs-xs);text-transform:uppercase;font-weight:700;margin-bottom:6px">Projects owned (' + d.ownedProjects.length + ')</div>' +
      (d.ownedProjects.length ? d.ownedProjects.map(function (p) {
        return '<div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;padding:4px 0"><span style="flex:1;min-width:140px">' + PM.esc(p.name) + '</span>' +
          '<select class="pm-select" id="hvProj' + p.id + '">' + peopleOptions(d.employeeId, 'New owner…') + '</select>' +
          '<button class="pm-btn pm-btn-sm" onclick="PMHandover.applyProject(' + p.id + ')">Change</button></div>';
      }).join('') : '<div style="color:var(--tx3);font-size:var(--fs-sm)">None</div>') + '</div>';

    h += '<div class="pm-card"><div style="font-size:var(--fs-xs);text-transform:uppercase;font-weight:700;margin-bottom:6px">Teams led (' + d.ledTeams.length + ')</div>' +
      (d.ledTeams.length ? d.ledTeams.map(function (t) {
        return '<div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;padding:4px 0"><span style="flex:1;min-width:140px">' + PM.esc(t.name) + '</span>' +
          '<select class="pm-select" id="hvTeam' + t.id + '">' + peopleOptions(d.employeeId, 'New team lead…') + '</select>' +
          '<button class="pm-btn pm-btn-sm" onclick="PMHandover.applyTeam(' + t.id + ')">Change</button></div>';
      }).join('') : '<div style="color:var(--tx3);font-size:var(--fs-sm)">None</div>') + '</div>';

    h += '<div id="hvResult"></div>';
    detEl().innerHTML = h;
  }

  function toggle(id, on) { S.sel[id] = !!on; }
  function selAll(on) {
    S.detail.tasks.forEach(function (t) { S.sel[t.id] = !!on; });
    Array.prototype.forEach.call(document.querySelectorAll('.hvSel'), function (c) { c.checked = !!on; });
  }

  function result(ok, failed) {
    var el = document.getElementById('hvResult');
    if (el) el.innerHTML = '<div class="pm-card"><b>' + ok + ' done' + (failed.length ? ', ' + failed.length + ' failed' : '') + '</b>' +
      (failed.length ? '<ul style="margin:8px 0 0 16px">' + failed.map(function (m) { return '<li style="font-size:var(--fs-xs)">' + PM.esc(m) + '</li>'; }).join('') + '</ul>' : '') + '</div>';
    PM.toast(ok + ' done' + (failed.length ? ', ' + failed.length + ' failed' : ''), failed.length ? 'warning' : 'success');
  }

  function reload() {
    return Promise.all([PM.api('GET', '/api/pm/handover/pending'), openDetail(S.from).catch(function () {})])
      .then(function (r) { S.pending = r[0] || []; renderList(); });
  }

  function applyTasks() {
    var to = document.getElementById('hvTaskTo').value;
    var ids = Object.keys(S.sel).filter(function (k) { return S.sel[k]; }).map(Number);
    if (!to) { PM.toast('Pick who receives the tasks', 'warning'); return; }
    if (!ids.length) { PM.toast('Select at least one task', 'warning'); return; }
    if (!window.confirm('Hand ' + ids.length + ' task(s) to the selected person?')) return;
    PM.api('POST', EP.tasks(), { fromEmployeeId: S.from, toEmployeeId: +to, taskIds: ids })
      .then(function (r) {
        var failed = (r && r.failed) || [];                                     // ADAPT: use the response shape your endpoint returns
        return reload().then(function () { result(ids.length - failed.length, failed); });
      }).catch(function (e) { PM.toast(e.message, 'error'); });
  }

  function applyProject(pid) {
    var to = document.getElementById('hvProj' + pid).value;
    if (!to) { PM.toast('Pick the new owner', 'warning'); return; }
    if (!window.confirm('Change the project owner?')) return;
    PM.api('POST', EP.project(pid), { employeeId: +to })
      .then(function () { return reload().then(function () { result(1, []); }); })
      .catch(function (e) { PM.toast(e.message, 'error'); });
  }

  function applyTeam(tid) {
    var to = document.getElementById('hvTeam' + tid).value;
    if (!to) { PM.toast('Pick the new team lead', 'warning'); return; }
    if (!window.confirm('Change the team lead?')) return;
    PM.api('POST', EP.team(tid), { employeeId: +to })
      .then(function () { return reload().then(function () { result(1, []); }); })
      .catch(function (e) { PM.toast(e.message, 'error'); });
  }

  window.PMHandover = { init: init, open: openDetail, toggle: toggle, selAll: selAll,
                        applyTasks: applyTasks, applyProject: applyProject, applyTeam: applyTeam };
})();