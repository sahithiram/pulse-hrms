/** pm-recurring.js — Gap 19a UI. Public: PMRecurring.open(projectId), PMRecurring.fromTask(task). Needs pm-core.js */
(function () {
  'use strict';
  var S = { projectId: null, rows: [], canManage: false, editing: null, employees: null };
  var DOW = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function today() { var d = new Date(); return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }
  function val(id) { var e = document.getElementById(id); return e ? e.value : ''; }
  function fail(e) { PM.toast((e && e.message) || 'Failed', 'error'); }
  function spin() { return '<div style="text-align:center;padding:40px;color:var(--tx3)"><i class="fas fa-spinner fa-spin"></i></div>'; }

  function describe(r) {
    if (r.frequency === 'WEEKLY') return 'Every ' + DOW[r.dayOfWeek];
    if (r.frequency === 'MONTHLY') return 'Monthly on day ' + r.dayOfMonth;
    return 'Every day';
  }

  function open(projectId) {
    S.projectId = projectId;
    PM.openDrawer(spin(), 'Recurring tasks');
    return refresh();
  }

  function refresh() {
    return Promise.all([
      PM.api('GET', '/api/pm/projects/' + S.projectId + '/recurring'),
      PM.api('GET', '/api/pm/projects/' + S.projectId + '/members/me').catch(function () { return {}; })
    ]).then(function (r) {
      S.rows = r[0] || [];
      S.canManage = !!(r[1] && r[1].canAdminister);
      renderList();
    }).catch(function (e) { fail(e); PM.closeDrawer(); });
  }

  function renderList() {
    var h = '<div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:10px">' +
      (S.canManage ? '<button class="pm-btn pm-btn-primary" style="min-height:44px" onclick="PMRecurring.form()"><i class="fas fa-plus"></i> New recurring task</button>' +
        '<button class="pm-btn" style="min-height:44px" onclick="PMRecurring.runNow()"><i class="fas fa-bolt"></i> Run now</button>' : '') + '</div>';
    if (!S.rows.length) h += '<div class="pm-card" style="color:var(--tx3)">No recurring tasks yet.</div>';
    S.rows.forEach(function (r) {
      h += '<div class="pm-card"><div style="display:flex;gap:8px;align-items:flex-start;flex-wrap:wrap">' +
        '<div style="flex:1;min-width:0"><b>' + PM.esc(r.title) + '</b>' +
        '<div style="font-size:var(--fs-xs);color:var(--tx3)">' + PM.esc(describe(r)) + ' · due +' + r.dueAfterDays + 'd · ' + PM.esc(r.priority) + '</div>' +
        '<div style="font-size:var(--fs-xs);color:var(--tx3)">Next: ' + PM.fmtDate(r.nextRunDate) + (r.endDate ? ' · ends ' + PM.fmtDate(r.endDate) : '') + '</div></div>' +
        '<span class="pm-badge ' + (r.paused ? 'warning' : 'success') + '">' + (r.paused ? 'Paused' : 'Active') + '</span></div>' +
        (r.canManage ? '<div style="display:flex;gap:6px;margin-top:8px;flex-wrap:wrap">' +
          '<button class="pm-btn pm-btn-sm" onclick="PMRecurring.form(' + r.id + ')">Edit</button>' +
          '<button class="pm-btn pm-btn-sm" onclick="PMRecurring.toggle(' + r.id + ',' + (r.paused ? 'false' : 'true') + ')">' + (r.paused ? 'Resume' : 'Pause') + '</button>' +
          '<button class="pm-btn pm-btn-sm" onclick="PMRecurring.del(' + r.id + ')">Delete</button></div>' : '') + '</div>';
    });
    h += '<div style="font-size:var(--fs-xs);color:var(--tx3)">Deleting a rule never deletes tasks it already created.</div>';
    PM.openDrawer(h, 'Recurring tasks');
  }

  function employees() {
    if (S.employees) return Promise.resolve(S.employees);
    return PM.api('GET', '/api/pm/employees/dropdown').then(function (l) { S.employees = l || []; return S.employees; })
      .catch(function () { S.employees = []; return S.employees; });
  }

  /** form(ruleId) edits; form() creates; form(null, prefill) creates with prefilled values. */
  function form(ruleId, prefill) {
    var r = ruleId ? S.rows.filter(function (x) { return x.id === ruleId; })[0] : null;
    S.editing = r ? r.id : null;
    var p = r || prefill || {};
    employees().then(function (emps) {
      var f = p.frequency || 'WEEKLY';
      var h = '<div class="pm-card">' +
        '<input class="pm-input" id="rcTitle" placeholder="Title" maxlength="300" value="' + PM.esc(p.title || '') + '" style="width:100%;margin-bottom:8px">' +
        '<textarea class="pm-input" id="rcDesc" placeholder="Description (optional)" style="width:100%;height:70px;padding:8px;margin-bottom:8px">' + PM.esc(p.description || '') + '</textarea>' +
        '<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:8px">' +
        '<select class="pm-select" id="rcPrio">' + ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(function (x) {
          return '<option' + ((p.priority || 'MEDIUM') === x ? ' selected' : '') + '>' + x + '</option>'; }).join('') + '</select>' +
        '<select class="pm-select" id="rcAssignee"><option value="">Unassigned</option>' + emps.map(function (e) {
          return '<option value="' + e.id + '"' + (String(p.assigneeId) === String(e.id) ? ' selected' : '') + '>' + PM.esc(e.name || ('Employee #' + e.id)) + '</option>'; }).join('') + '</select>' +
        '<select class="pm-select" id="rcFreq" onchange="PMRecurring.freq()">' + ['DAILY', 'WEEKLY', 'MONTHLY'].map(function (x) {
          return '<option' + (f === x ? ' selected' : '') + '>' + x + '</option>'; }).join('') + '</select>' +
        '<select class="pm-select" id="rcDow">' + [1, 2, 3, 4, 5, 6, 7].map(function (n) {
          return '<option value="' + n + '"' + ((p.dayOfWeek || 1) === n ? ' selected' : '') + '>' + DOW[n] + '</option>'; }).join('') + '</select>' +
        '<input class="pm-input" type="number" id="rcDom" min="1" max="28" value="' + (p.dayOfMonth || 1) + '" title="Day of month (1-28)">' +
        '<input class="pm-input" type="number" id="rcDue" min="0" max="365" value="' + (p.dueAfterDays || 0) + '" title="Due after (days)">' +
        '<input class="pm-input" type="date" id="rcStart" value="' + (p.startDate || today()) + '" title="Start date">' +
        '<input class="pm-input" type="date" id="rcEnd" value="' + (p.endDate || '') + '" title="End date (optional)"></div>' +
        '<div style="font-size:var(--fs-xs);color:var(--tx3);margin-top:6px">Due = run day + "due after" days. Month days are limited to 1–28.</div></div>' +
        '<div style="display:flex;gap:6px;justify-content:flex-end"><button class="pm-btn" style="min-height:44px" onclick="PMRecurring.back()">Cancel</button>' +
        '<button class="pm-btn pm-btn-primary" style="min-height:44px" onclick="PMRecurring.save()">Save</button></div>';
      PM.openDrawer(h, r ? 'Edit recurring task' : 'New recurring task');
      freq();
    });
  }
  function freq() {
    var f = val('rcFreq');
    var dow = document.getElementById('rcDow'), dom = document.getElementById('rcDom');
    if (dow) dow.style.display = f === 'WEEKLY' ? '' : 'none';
    if (dom) dom.style.display = f === 'MONTHLY' ? '' : 'none';
  }
  function back() { refresh(); }

  function save() {
    var title = val('rcTitle').trim();
    if (!title) { PM.toast('Title is required', 'warning'); return; }
    var b = {
      title: title, description: val('rcDesc') || null, priority: val('rcPrio'),
      assigneeId: val('rcAssignee') ? parseInt(val('rcAssignee'), 10) : null,
      frequency: val('rcFreq'), dayOfWeek: parseInt(val('rcDow'), 10), dayOfMonth: parseInt(val('rcDom'), 10),
      dueAfterDays: parseInt(val('rcDue'), 10) || 0, startDate: val('rcStart'), endDate: val('rcEnd') || null
    };
    var req = S.editing ? PM.api('PUT', '/api/pm/recurring/' + S.editing, b)
                        : PM.api('POST', '/api/pm/projects/' + S.projectId + '/recurring', b);
    req.then(function () { PM.toast('Saved', 'success'); return refresh(); }).catch(fail);
  }
  function toggle(id, pause) {
    PM.api('POST', '/api/pm/recurring/' + id + (pause ? '/pause' : '/resume'))
      .then(function () { return refresh(); }).catch(fail);
  }
  function del(id) {
    if (!window.confirm('Delete this recurring rule? Tasks it already created will stay.')) return;
    PM.api('DELETE', '/api/pm/recurring/' + id).then(function () { return refresh(); }).catch(fail);
  }
  function runNow() {
    PM.api('POST', '/api/pm/projects/' + S.projectId + '/recurring/run-now')
      .then(function (r) { PM.toast((r && r.created || 0) + ' task(s) created', 'success'); return refresh(); }).catch(fail);
  }

  /** "Repeat" button in the task drawer: task = {projectId,title,description,priority,assigneeIds} */
  function fromTask(task) {
    S.projectId = task.projectId; S.rows = [];
    form(null, { title: task.title, description: task.description, priority: String(task.priority || 'MEDIUM').toUpperCase(),
      assigneeId: (task.assigneeIds || [])[0] || null, frequency: 'WEEKLY' });
  }

  window.PMRecurring = { open: open, form: form, freq: freq, back: back, save: save, toggle: toggle, del: del,
    runNow: runNow, fromTask: fromTask };
})();