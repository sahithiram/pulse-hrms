/**
 * pm-intake.js — PHASE 6.3
 * Drawer with two tabs: "Requests" (submit / approve / reject) and "Templates" (use / admin: add).
 * Loaded on demand by PM.openIntake(tab). Reloads live on every "pm:event".
 */
(function () {
  'use strict';

  var S = { tab: 'requests', reqs: [], tpls: [] };
  var INP = 'width:100%;padding:7px 9px;border-radius:6px;border:1px solid var(--bd);background:var(--card2);color:var(--tx);margin-top:3px';
  var ST = { SUBMITTED: ['warning', 'Waiting'], APPROVED: ['success', 'Approved'], REJECTED: ['danger', 'Rejected'] };

  function body() { return document.getElementById('pmDrawerBody'); }
  function fail(e) { PM.toast((e && e.message) || 'Failed', 'error', 5000); }
  function isAdmin() { return !!(PM.user && PM.user.admin); }
  function val(id) { var el = document.getElementById(id); return el ? el.value.trim() : ''; }

  function open(tab) {
    S.tab = tab === 'templates' ? 'templates' : 'requests';
    PM.openDrawer('<div style="padding:24px;text-align:center;color:var(--tx3)"><i class="fas fa-spinner fa-spin"></i> Loading…</div>', 'Project intake');
    load();
  }

  function load() {
    return Promise.all([PM.apiList('GET', '/api/pm/project-requests'), PM.apiList('GET', '/api/pm/project-templates')])
      .then(function (r) { S.reqs = r[0]; S.tpls = r[1]; render(); }).catch(fail);
  }

  function setTab(t) { S.tab = t; render(); }

  function render() {
    var h = '<div id="pmIntakeRoot" style="display:flex;gap:6px;margin-bottom:10px">' +
      ['requests', 'templates'].map(function (t) {
        return '<button class="pm-btn ' + (S.tab === t ? 'pm-btn-primary' : '') + '" onclick="PMIntake.setTab(\'' + t + '\')">' +
          (t === 'requests' ? 'Requests' : 'Templates') + '</button>';
      }).join('') + '</div>';
    h += S.tab === 'requests' ? requestsHtml() : templatesHtml();
    body().innerHTML = h;
  }

  /* ───────── requests ───────── */
  function sel(id) {
    return '<select id="' + id + '" style="' + INP + '">' +
      [1, 2, 3, 4, 5].map(function (n) { return '<option value="' + n + '"' + (n === 3 ? ' selected' : '') + '>' + n + '</option>'; }).join('') + '</select>';
  }

  function requestsHtml() {
    var h = '<div class="pm-card"><b>New project request</b>' +
      '<label style="display:block;margin-top:8px">Title<input id="rqTitle" maxlength="200" style="' + INP + '"></label>' +
      '<label style="display:block;margin-top:8px">Problem to solve<textarea id="rqProblem" rows="3" style="' + INP + '"></textarea></label>' +
      '<label style="display:block;margin-top:8px">Expected outcome<textarea id="rqOutcome" rows="3" style="' + INP + '"></textarea></label>' +
      '<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:8px">' +
      '<label>Impact (5 = high)' + sel('rqImpact') + '</label><label>Urgency (5 = high)' + sel('rqUrgency') + '</label>' +
      '<label>Effort (5 = big)' + sel('rqEffort') + '</label></div>' +
      '<button class="pm-btn pm-btn-primary" style="margin-top:10px" onclick="PMIntake.submit()">Submit request</button></div>';

    if (!S.reqs.length) return h + '<div style="color:var(--tx3);padding:10px">No requests yet.</div>';
    return h + S.reqs.map(function (r) {
      var st = ST[r.status] || ['info', r.status];
      return '<div class="pm-card"><div style="display:flex;gap:8px;align-items:center"><b style="flex:1">' + PM.esc(r.title) + '</b>' +
        '<span class="pm-badge ' + st[0] + '">' + st[1] + '</span><span class="pm-badge info" title="Priority score">' + r.priorityScore + ' · ' + r.suggestedPriority + '</span></div>' +
        '<div style="font-size:var(--fs-xs);color:var(--tx3);margin:4px 0">' + PM.esc(r.createdByName || '') + ' · ' + PM.esc(String(r.createdAt || '').slice(0, 10)) + '</div>' +
        '<div>' + PM.esc(r.problem || '') + '</div>' +
        (r.note ? '<div style="margin-top:4px;color:var(--tx3)">Note: ' + PM.esc(r.note) + '</div>' : '') +
        (r.status === 'APPROVED' && r.projectId ? '<div style="margin-top:4px"><a href="/pm/04-projects">Project #' + r.projectId + ' created</a></div>' : '') +
        ((isAdmin() && r.status === 'SUBMITTED')
          ? '<div style="display:flex;gap:6px;margin-top:8px"><button class="pm-btn pm-btn-primary" onclick="PMIntake.approveForm(' + r.id + ')">Approve</button>' +
            '<button class="pm-btn" onclick="PMIntake.reject(' + r.id + ')">Reject</button></div>' : '') + '</div>';
    }).join('');
  }

  function submit() {
    PM.api('POST', '/api/pm/project-requests', {
      title: val('rqTitle'), problem: val('rqProblem'), expectedOutcome: val('rqOutcome'),
      impact: Number(val('rqImpact')), urgency: Number(val('rqUrgency')), effort: Number(val('rqEffort'))
    }).then(function () { PM.toast('Request submitted', 'success'); return load(); }).catch(fail);
  }

  function approveForm(id) {
    var h = '<div id="pmIntakeRoot2" class="pm-card"><label>Create it from a template (optional)<select id="apTpl" style="' + INP + '">' +
      '<option value="">No template (empty project)</option>' +
      S.tpls.map(function (t) { return '<option value="' + t.id + '">' + PM.esc(t.name) + ' (' + t.type + ')</option>'; }).join('') + '</select></label>' +
      '<label style="display:block;margin-top:8px">Note (optional)<input id="apNote" maxlength="500" style="' + INP + '"></label>' +
      '<button class="pm-btn pm-btn-primary" style="margin-top:10px" onclick="PMIntake.approve(' + id + ')">Approve and create project</button></div>';
    PM.openDrawer(h, 'Approve request');
  }

  function approve(id) {
    var tpl = val('apTpl');
    PM.api('POST', '/api/pm/project-requests/' + id + '/approve',
      { templateId: tpl ? Number(tpl) : null, note: val('apNote') || null })
      .then(function () { PM.toast('Project created', 'success'); open('requests'); }).catch(fail);
  }

  function reject(id) {
    var note = window.prompt('Why is this request rejected?');
    if (note === null) return;
    PM.api('POST', '/api/pm/project-requests/' + id + '/reject', { note: note })
      .then(function () { PM.toast('Request rejected', 'success'); return load(); }).catch(fail);
  }

  /* ───────── templates ───────── */
  function templatesHtml() {
    var h = '';
    if (isAdmin()) {
      h += '<div class="pm-card"><b>New template (admin)</b>' +
        '<label style="display:block;margin-top:8px">Name<input id="tpName" maxlength="120" style="' + INP + '"></label>' +
        '<label style="display:block;margin-top:8px">Type<select id="tpType" style="' + INP + '"><option>SCRUM</option><option>KANBAN</option><option>WATERFALL</option><option>HYBRID</option></select></label>' +
        '<label style="display:block;margin-top:8px">Payload (JSON)<textarea id="tpPayload" rows="6" style="' + INP + ';font-family:monospace" placeholder=\'{"milestones":[{"name":"Kick-off","dueOffsetDays":7}],"sprints":[],"tasks":[]}\'></textarea></label>' +
        '<button class="pm-btn pm-btn-primary" style="margin-top:8px" onclick="PMIntake.saveTemplate()">Save template</button></div>';
    }
    if (!S.tpls.length) return h + '<div style="color:var(--tx3);padding:10px">No templates yet.</div>';
    return h + S.tpls.map(function (t) {
      return '<div class="pm-card"><div style="display:flex;gap:8px;align-items:center"><b style="flex:1">' + PM.esc(t.name) + '</b><span class="pm-badge info">' + t.type + '</span></div>' +
        '<div style="color:var(--tx3);margin:4px 0">' + PM.esc(t.description || '') + '</div>' +
        '<div style="font-size:var(--fs-xs)">' + t.milestoneCount + ' milestones · ' + t.sprintCount + ' sprints · ' + t.taskCount + ' tasks</div>' +
        '<button class="pm-btn pm-btn-primary" style="margin-top:8px" onclick="PMIntake.useForm(' + t.id + ')">Create project</button></div>';
    }).join('');
  }

  function saveTemplate() {
    var payload;
    try { payload = JSON.parse(val('tpPayload')); } catch (e) { PM.toast('The payload is not valid JSON', 'error'); return; }
    PM.api('POST', '/api/pm/project-templates', { name: val('tpName'), type: val('tpType'), payload: payload })
      .then(function () { PM.toast('Template saved', 'success'); return load(); }).catch(fail);
  }

  function useForm(id) {
    var h = '<div id="pmIntakeRoot3" class="pm-card"><label>Project name<input id="ufName" maxlength="200" style="' + INP + '"></label>' +
      '<label style="display:block;margin-top:8px">Start date<input id="ufStart" type="date" style="' + INP + '" value="' + new Date().toISOString().slice(0, 10) + '"></label>' +
      '<label style="display:block;margin-top:8px">Priority<select id="ufPri" style="' + INP + '"><option value="">Template default</option><option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></select></label>' +
      '<button class="pm-btn pm-btn-primary" style="margin-top:10px" onclick="PMIntake.use(' + id + ')">Create project</button></div>';
    PM.openDrawer(h, 'Create from template');
  }

  function use(id) {
    var b = { name: val('ufName'), startDate: val('ufStart') || null };
    if (val('ufPri')) b.priority = val('ufPri');
    PM.api('POST', '/api/pm/projects/from-template/' + id, b)
      .then(function (p) { PM.toast('Created "' + (p && p.name) + '"', 'success'); PM.closeDrawer(); if (window.pmRefresh) window.pmRefresh(); })
      .catch(fail);
  }

  /* realtime: reload when anything changes, unless the user is typing */
  window.addEventListener('pm:event', function () {
    if (!document.getElementById('pmIntakeRoot')) return;
    var a = document.activeElement;
    if (a && (a.tagName === 'INPUT' || a.tagName === 'TEXTAREA' || a.tagName === 'SELECT')) return;
    load();
  });

  window.PMIntake = { open: open, setTab: setTab, submit: submit, approveForm: approveForm, approve: approve,
                      reject: reject, saveTemplate: saveTemplate, useForm: useForm, use: use };
})();