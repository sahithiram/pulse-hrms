/**
 * pm-closure.js — PHASE 6.2
 * "Closure" drawer: checklist, lessons learned, client sign-off, Complete button.
 * Loaded on demand by PM.openProjectClosure(id) (pm-core.js). Reloads live on every "pm:event".
 */
(function () {
  'use strict';

  var C = { id: null, data: null, lessons: '' };
  var SO = {
    NOT_REQUIRED: ['info', 'Not needed'], NOT_REQUESTED: ['warning', 'Not requested'],
    PENDING: ['warning', 'Waiting for approval'], APPROVED: ['success', 'Signed off'], REJECTED: ['danger', 'Rejected']
  };

  function body() { return document.getElementById('pmDrawerBody'); }
  function fail(e) { PM.toast((e && e.message) || 'Failed', 'error', 5000); }
  function base() { return '/api/pm/projects/' + C.id; }
  var INP = 'width:100%;padding:7px 9px;border-radius:6px;border:1px solid var(--bd);background:var(--card2);color:var(--tx)';

  function open(projectId) {
    C.id = projectId;
    PM.openDrawer('<div style="padding:24px;text-align:center;color:var(--tx3)"><i class="fas fa-spinner fa-spin"></i> Loading…</div>', 'Project closure');
    load();
  }

  function load() {
    return Promise.all([PM.api('GET', base() + '/closure-status'), PM.api('GET', base())])
      .then(function (r) { C.data = r[0]; C.lessons = (r[1] && r[1].lessonsLearned) || ''; render(); })
      .catch(function (e) {
        var b = body();
        if (b) b.innerHTML = '<div class="pm-card" style="color:var(--dng)">' + PM.esc(e.message || 'Failed to load') + '</div>';
      });
  }

  function render() {
    var d = C.data, h = '';
    var locked = d.projectStatus === 'COMPLETED' || d.projectStatus === 'ARCHIVED';

    /* readiness */
    h += '<div id="pmClosureRoot" class="pm-card" style="border-left:3px solid var(--' +     (locked ? '<b>This project is ' + d.projectStatus.toLowerCase() + '.</b> The checklist is locked.' : d.ready ? 'suc' : 'wrn') + ')">' +
      (d.ready
        ? '<b>Ready to complete.</b> Every closure rule passes.'
        : '<b>Not ready to complete:</b><ul style="margin:6px 0 0 18px;padding:0">' +
          d.blockers.map(function (b) { return '<li>' + PM.esc(b) + '</li>'; }).join('') + '</ul>') +
      '</div>';
  /* STEP 8: completion summary when completed, reopen note when running again */
  if (d.projectStatus === 'COMPLETED') {
    var late = d.delayDays == null ? '—'
      : (d.delayDays > 0 ? d.delayDays + ' day(s) late'
        : (d.delayDays < 0 ? Math.abs(d.delayDays) + ' day(s) early' : 'on the planned end date'));
    h += '<div class="pm-card" style="border-left:3px solid var(--suc)">' +
      '<div style="font-weight:700;margin-bottom:6px"><i class="fas fa-flag-checkered"></i> Completion summary</div>' +
      '<div style="font-size:var(--fs-sm);line-height:1.7">' +
      'Completed: <b>' + PM.esc(PM.fmtDateTime(d.completedAt)) + '</b> by ' + PM.esc(d.completedByName || '—') + '<br>' +
      'Planned end: <b>' + PM.esc(PM.fmtDate(d.plannedEnd)) + '</b> (' + PM.esc(late) + ')<br>' +
      'Tasks: <b>' + d.tasksDone + '</b> of ' + d.tasksTotal + ' done</div>' +
      ((PM.can && PM.can('super'))
        ? '<button class="pm-btn pm-btn-sm" style="margin-top:8px" onclick="PMClosure.reopen()"><i class="fas fa-rotate-left"></i> Reopen project</button>'
        : '<div style="margin-top:6px;font-size:var(--fs-xs);color:var(--tx3)">Only a Super Admin can reopen a completed project.</div>') +
      '</div>';
  } else if (d.reopenedAt) {
    h += '<div class="pm-card" style="border-left:3px solid var(--wrn)">' +
      '<b><i class="fas fa-rotate-left"></i> Reopened</b> on ' + PM.esc(PM.fmtDateTime(d.reopenedAt)) +
      ' by ' + PM.esc(d.reopenedByName || '—') + '<br>' +
      '<span style="font-size:var(--fs-sm)">Reason: ' + PM.esc(d.reopenReason || '—') + '</span><br>' +
      '<span style="font-size:var(--fs-xs);color:var(--tx3)">Client sign-off must be requested again before the project can be completed.</span></div>';
  }
    /* checklist */
    h += '<div class="pm-card"><div style="font-weight:700;margin-bottom:8px"><i class="fas fa-list-check"></i> Checklist (' + d.doneItems + '/' + d.totalItems + ')</div>';
    if (!d.items.length) h += '<div style="color:var(--tx3);margin-bottom:8px">No closure items yet.</div>';
    h += d.items.map(function (i) {
      return '<div style="display:flex;gap:8px;align-items:center;padding:5px 0;border-bottom:1px solid var(--bd)">' +
        '<input type="checkbox" ' + (i.done ? 'checked ' : '') + (locked ? 'disabled ' : '') + 'onchange="PMClosure.toggle(' + i.id + ',this.checked)">' +
        '<div style="flex:1;min-width:0"><div style="' + (i.done ? 'text-decoration:line-through;opacity:.7' : '') + '">' + PM.esc(i.title) + '</div>' +
        (i.done ? '<div style="font-size:var(--fs-xs);color:var(--tx3)">' + PM.esc(i.doneByName || '') + ' · ' + PM.esc(String(i.doneAt || '').replace('T', ' ').slice(0, 16)) + '</div>' : '') + '</div>' +
        (locked ? '' : '<button class="pm-iconbtn" title="Remove" onclick="PMClosure.remove(' + i.id + ')"><i class="fas fa-times"></i></button>') + '</div>';
    }).join('');
    if (!locked) {
      h += '<div style="display:flex;gap:6px;margin-top:10px"><input id="pmClosureNew" maxlength="300" placeholder="Add a checklist item" style="' + INP + '">' +
        '<button class="pm-btn pm-btn-primary" onclick="PMClosure.add()">Add</button></div>' +
        (d.items.length ? '' : '<button class="pm-btn pm-btn-sm" style="margin-top:8px" onclick="PMClosure.defaults()">Add standard checklist</button>');
    }
    h += '</div>';

    /* client sign-off */
    if (d.clientRequired) {
      var so = SO[d.signOffState] || ['info', d.signOffState];
      h += '<div class="pm-card"><div style="display:flex;align-items:center;gap:8px"><b><i class="fas fa-signature"></i> Client sign-off</b>' +
        '<span class="pm-badge ' + so[0] + '">' + so[1] + '</span>' +
        ((!locked && (d.signOffState === 'NOT_REQUESTED' || d.signOffState === 'REJECTED'))
          ? '<button class="pm-btn pm-btn-sm" style="margin-left:auto" onclick="PMClosure.signOff()">Request sign-off</button>' : '') + '</div>' +
        '<div style="margin-top:6px;font-size:var(--fs-xs);color:var(--tx3)">Needs every task finished first. A project manager approves it in the inbox after the client accepted the work.</div></div>';
    }

    /* lessons learned */
    h += '<div class="pm-card"><div style="font-weight:700;margin-bottom:6px"><i class="fas fa-lightbulb"></i> Lessons learned ' +
      '<span class="pm-badge ' + (d.lessonsLearnedWritten ? 'success' : 'warning') + '">' + (d.lessonsLearnedWritten ? 'Written' : 'Missing') + '</span></div>' +
      '<textarea id="pmLessons" rows="4" ' + (locked ? 'disabled ' : '') + 'style="' + INP + '" placeholder="What went well, what did not, what to do differently…">' + PM.esc(C.lessons) + '</textarea>' +
      (locked ? '' : '<button class="pm-btn pm-btn-sm" style="margin-top:6px" onclick="PMClosure.saveLessons()">Save lessons</button>') + '</div>';

    /* complete */
    if (!locked) {
      h += '<div style="display:flex;justify-content:flex-end"><button class="pm-btn pm-btn-primary" ' + (d.ready ? '' : 'disabled ') +
        'onclick="PMClosure.complete()"><i class="fas fa-flag-checkered"></i> Complete project</button></div>';
    }
    body().innerHTML = h;
  }

  /* ───── actions ───── */
  function toggle(id, done) { PM.api('PUT', base() + '/closure-items/' + id, { done: done }).then(load).catch(function (e) { fail(e); load(); }); }
  function add() {
    var el = document.getElementById('pmClosureNew');
    var t = el ? el.value.trim() : '';
    if (!t) return;
    PM.api('POST', base() + '/closure-items', { title: t }).then(load).catch(fail);
  }
  function remove(id) { PM.api('DELETE', base() + '/closure-items/' + id).then(load).catch(fail); }
  function defaults() { PM.api('POST', base() + '/closure-items/defaults').then(load).catch(fail); }
  function saveLessons() {
    var el = document.getElementById('pmLessons');
    PM.api('PUT', base(), { lessonsLearned: el ? el.value : '' })
      .then(function () { PM.toast('Lessons saved', 'success'); return load(); }).catch(fail);
  }
  // FIX: pass projectId so project managers show up as candidate approvers.
  function signOff() {
    PMApprovals.openRequestForm(C.id, 'CLIENT_SIGN_OFF', { projectId: C.id, onDone: function () { open(C.id); } });
  }
  function complete() {
    if (!window.confirm('Mark this project as completed?')) return;
    PM.api('PUT', base(), { status: 'COMPLETED' })
      .then(function () { PM.toast('Project completed', 'success'); return load(); })
      .catch(function (e) { fail(e); load(); });   // the message lists exactly what is still missing
  }
  // STEP 8: Super Admin only (the server checks too)
  function reopen() {
    var why = window.prompt('Why are you reopening this project? (at least 10 characters)');
    if (why === null) return;
    why = why.trim();
    if (why.length < 10) { PM.toast('Please give a reason of at least 10 characters', 'warning'); return; }
    PM.api('POST', base() + '/reopen', { reason: why })
      .then(function () { PM.toast('Project reopened', 'success'); return load(); })
      .catch(fail);
  }
  /* realtime: any write anywhere re-renders the open drawer, unless the user is typing */
  window.addEventListener('pm:event', function () {
    if (!C.id || !document.getElementById('pmClosureRoot')) return;
    var a = document.activeElement;
    if (a && (a.tagName === 'INPUT' || a.tagName === 'TEXTAREA')) return;
    load();
  });

  window.PMClosure = { open: open, toggle: toggle, add: add, remove: remove, defaults: defaults,
                       saveLessons: saveLessons, signOff: signOff, complete: complete };
})();