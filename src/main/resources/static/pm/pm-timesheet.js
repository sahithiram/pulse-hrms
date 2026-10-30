/**
 * pm-timesheet.js — PHASE 3
 * "My Timesheet" tab on /pm/03-my-work: log time, review the week, submit / withdraw.
 * Gap 13: billable checkbox, billable / overtime badges, billable toggle, week totals.
 * Needs pm-core.js. Exposes window.PMTimesheetMe.
 */
(function () {
  'use strict';

  var S = { week: null, data: null, tasks: null };

  /* ───────── date helpers (local time, never toISOString) ───────── */
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function iso(d) { return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }
  function parse(s) { var p = s.split('-'); return new Date(+p[0], +p[1] - 1, +p[2]); }
  function mondayOf(d) {
    var day = d.getDay(), diff = day === 0 ? -6 : 1 - day;
    return new Date(d.getFullYear(), d.getMonth(), d.getDate() + diff);
  }
  function fmtMin(m) {
    m = m || 0;
    var h = Math.floor(m / 60), r = m % 60;
    return h ? (r ? h + 'h ' + r + 'm' : h + 'h') : r + 'm';
  }
  function fmtDay(s) {
    return parse(s).toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });
  }
  function root() { return document.getElementById('pmtsRoot'); }
  function val(id) { var el = document.getElementById(id); return el ? el.value : ''; }

  var STATUS = {
    DRAFT: ['info', 'Draft'],
    SUBMITTED: ['warning', 'Submitted · awaiting approval'],
    APPROVED: ['success', 'Approved · locked'],
    REJECTED: ['danger', 'Rejected · please fix and resubmit'],
    LOCKED: ['info', 'Locked · cannot edit']
  };

  /* ───────── billable / overtime helpers ───────── */
  // a missing flag counts as billable (DB default is 1)
  function isBillable(e) { return e.billable !== false; }
  function isOvertime(e) { return e.overtime === true; }

  // use the server totals when present, otherwise add the entries up here
  function weekTotals(d) {
    var bill = 0, ot = 0;
    (d.days || []).forEach(function (day) {
      (day.entries || []).forEach(function (e) {
        if (isBillable(e)) bill += e.minutes || 0;
        if (isOvertime(e)) ot += e.minutes || 0;
      });
    });
    return {
      billable: typeof d.billableMinutes === 'number' ? d.billableMinutes : bill,
      overtime: typeof d.overtimeMinutes === 'number' ? d.overtimeMinutes : ot
    };
  }

  /* ───────── load ───────── */
  function load() {
    if (!root()) return Promise.resolve();
    if (!S.week) S.week = iso(mondayOf(new Date()));
    root().innerHTML = '<div style="text-align:center;padding:30px;color:var(--tx3)"><i class="fas fa-spinner fa-spin"></i> Loading…</div>';

    var tasksP = S.tasks ? Promise.resolve(S.tasks)
      : PM.api('GET', '/api/pm/me/tasks').then(function (t) { S.tasks = t || []; return S.tasks; })
          .catch(function () { S.tasks = []; return S.tasks; });

    return Promise.all([PM.api('GET', '/api/pm/timesheets/me?week=' + S.week), tasksP])
      .then(function (r) { S.data = r[0]; render(); })
      .catch(function (e) {
        root().innerHTML = '<div class="pm-card" style="color:var(--dng)">' + PM.esc(e.message || 'Failed to load timesheet') + '</div>';
      });
  }

  /* ───────── render ───────── */
  function render() {
    var d = S.data;
    if (!d) return;
    var st = STATUS[d.status] || ['info', d.status];
    var editable = !!d.editable && d.status !== 'LOCKED';
    var weekEnd = d.weekEnd;
    var tot = weekTotals(d);
    var nonBill = Math.max(0, (d.totalMinutes || 0) - tot.billable);
    var h = '';

    // toolbar
    h += '<div class="pm-card" style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">' +
      '<button class="pm-btn pm-btn-sm" onclick="PMTimesheetMe.shift(-1)"><i class="fas fa-chevron-left"></i></button>' +
      '<div style="font-weight:600;min-width:190px;text-align:center">' + fmtDay(d.weekStart) + ' – ' + fmtDay(weekEnd) + ' ' + parse(weekEnd).getFullYear() + '</div>' +
      '<button class="pm-btn pm-btn-sm" onclick="PMTimesheetMe.shift(1)"><i class="fas fa-chevron-right"></i></button>' +
      '<button class="pm-btn pm-btn-sm" onclick="PMTimesheetMe.thisWeek()">This week</button>' +
      '<span class="pm-badge ' + st[0] + '" style="margin-left:6px">' + PM.esc(st[1]) + '</span>' +
      '<div style="margin-left:auto;display:flex;gap:6px;align-items:center;flex-wrap:wrap">' +
      '<span style="font-size:var(--fs-sm);color:var(--tx2)">Total <b style="color:var(--tx)">' + fmtMin(d.totalMinutes) + '</b></span>' +
      (editable
        ? '<button class="pm-btn pm-btn-primary" onclick="PMTimesheetMe.submit()"' + (d.totalMinutes > 0 ? '' : ' disabled style="opacity:.5;cursor:not-allowed"') + '><i class="fas fa-paper-plane"></i> Submit week</button>'
        : '') +
      (d.status === 'SUBMITTED' && d.approvalId
        ? '<button class="pm-btn" onclick="PMTimesheetMe.withdraw()"><i class="fas fa-rotate-left"></i> Withdraw</button>'
        : '') +
      '</div></div>';

    // billable / overtime summary
    if (d.totalMinutes > 0) {
      h += '<div class="pm-card" style="display:flex;gap:16px;flex-wrap:wrap;font-size:var(--fs-sm);color:var(--tx2)">' +
        '<span><i class="fas fa-dollar-sign"></i> Billable <b style="color:var(--tx)">' + fmtMin(tot.billable) + '</b></span>' +
        '<span>Non-billable <b style="color:var(--tx)">' + fmtMin(nonBill) + '</b></span>' +
        '<span><i class="fas fa-moon"></i> Overtime <b style="color:' + (tot.overtime ? 'var(--wrn)' : 'var(--tx)') + '">' + fmtMin(tot.overtime) + '</b></span>' +
        '</div>';
    }

    // status banners
    if (d.status === 'REJECTED') {
      h += '<div class="pm-card" style="border-left:3px solid var(--dng)"><div style="font-weight:600;color:var(--dng)">Rejected' +
        (d.decidedByName ? ' by ' + PM.esc(d.decidedByName) : '') + '</div>' +
        '<div style="margin-top:4px">' + PM.esc(d.statusNote || '(no note)') + '</div></div>';
    } else if (d.status === 'SUBMITTED') {
      h += '<div class="pm-card" style="border-left:3px solid var(--wrn)">Waiting for <b>' + PM.esc(d.approverName || 'the approver') +
        '</b> to approve. This week is locked — withdraw it if you need to change something.</div>';
    } else if (d.status === 'APPROVED') {
      h += '<div class="pm-card" style="border-left:3px solid var(--suc)">Approved' +
        (d.decidedByName ? ' by <b>' + PM.esc(d.decidedByName) + '</b>' : '') + '. This week is locked.' +
        (d.statusNote ? ' Note: ' + PM.esc(d.statusNote) : '') + '</div>';
    } else if (d.statusNote) {
      h += '<div class="pm-card" style="border-left:3px solid var(--inf)">' + PM.esc(d.statusNote) + '</div>';
    }

    // log-time form (only while editable)
    if (editable) h += logForm(d);

    // days
    h += '<div class="pm-card" style="padding:0;overflow:hidden">' +
      (d.days || []).map(function (day) { return dayRow(day, editable); }).join('') + '</div>';

    root().innerHTML = h;
  }

  function myOpenTasks() {
    var me = PM.user && PM.user.id;
    return (S.tasks || []).filter(function (t) { return (t.assigneeIds || []).indexOf(me) > -1; });
  }

  function logForm(d) {
    var today = iso(new Date());
    var min = d.weekStart;
    var max = d.weekEnd < today ? d.weekEnd : today; // can't log in the future
    var dflt = (today >= min && today <= max) ? today : (max >= min ? max : min);
    var tasks = myOpenTasks();
    var opts = tasks.map(function (t) {
      return '<option value="' + t.id + '">' + PM.esc(t.title) + (t.projectName ? ' · ' + PM.esc(t.projectName) : '') + '</option>';
    }).join('');
    if (max < min) {
      return '<div class="pm-card" style="color:var(--tx3)">This week hasn\'t started yet — nothing to log.</div>';
    }
    return '<div class="pm-card"><div style="font-size:var(--fs-xs);color:var(--tx3);text-transform:uppercase;font-weight:700;margin-bottom:8px"><i class="fas fa-plus"></i> Log time</div>' +
      // auto-fit grid: 3 columns on desktop, stacks on phones
      '<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:8px">' +
      '<select class="pm-select" id="pmtsTask" style="grid-column:1/-1"><option value="">Select a task…</option>' + opts + '</select>' +
      '<input class="pm-input" type="date" id="pmtsDate" min="' + min + '" max="' + max + '" value="' + dflt + '">' +
      '<input class="pm-input" type="number" id="pmtsMin" min="1" max="960" value="60" placeholder="Minutes">' +
      '</div><div style="display:flex;gap:6px;margin-top:8px;align-items:center;flex-wrap:wrap">' +
      '<input class="pm-input" id="pmtsNote" placeholder="Note (optional)" style="flex:1;min-width:180px" maxlength="500">' +
      [15, 30, 60, 120, 240].map(function (m) {
        return '<button class="pm-btn pm-btn-sm" onclick="document.getElementById(\'pmtsMin\').value=' + m + '">' + fmtMin(m) + '</button>';
      }).join('') +
      '</div><div style="display:flex;gap:10px;margin-top:8px;align-items:center;flex-wrap:wrap">' +
      '<label style="display:flex;gap:6px;align-items:center;cursor:pointer;font-size:var(--fs-sm)">' +
      '<input type="checkbox" id="pmtsBill" checked> Billable</label>' +
      '<button class="pm-btn pm-btn-primary" style="margin-left:auto" onclick="PMTimesheetMe.logTime()"><i class="fas fa-check"></i> Add</button></div>' +
      (tasks.length ? '' : '<div style="margin-top:8px;font-size:var(--fs-sm);color:var(--tx3)">You have no tasks assigned to you, so there is nothing to log time on yet.</div>') +
      '</div>';
  }

  function badges(e) {
    var b = '';
    if (!isBillable(e)) b += ' <span class="pm-badge info" title="Not billable to the client">Non-billable</span>';
    if (isOvertime(e)) b += ' <span class="pm-badge warning" title="Over 8h that day or 40h that week">OT</span>';
    return b;
  }

  function dayRow(day, editable) {
    var entries = (day.entries || []).map(function (e) {
      var bill = isBillable(e);
      return '<div style="display:flex;gap:8px;align-items:center;padding:3px 0;flex-wrap:wrap">' +
        '<span style="flex:1;min-width:0"><b>' + PM.esc(e.taskTitle || 'Task') + '</b>' +
        (e.projectName ? ' <span style="color:var(--tx3)">· ' + PM.esc(e.projectName) + '</span>' : '') +
        (e.note ? ' <span style="color:var(--tx2)">— ' + PM.esc(e.note) + '</span>' : '') +
        badges(e) + '</span>' +
        '<span style="font-weight:600">' + fmtMin(e.minutes) + '</span>' +
        (editable
          ? '<button class="pm-btn pm-btn-sm pm-btn-icon" title="' + (bill ? 'Mark as non-billable' : 'Mark as billable') + '" ' +
            'style="' + (bill ? '' : 'opacity:.5') + '" onclick="PMTimesheetMe.toggleBillable(' + e.timeLogId + ',' + (bill ? 'false' : 'true') + ')">' +
            '<i class="fas fa-dollar-sign"></i></button>' +
            '<button class="pm-btn pm-btn-sm pm-btn-icon" title="Delete" onclick="PMTimesheetMe.del(' + e.timeLogId + ')"><i class="fas fa-trash"></i></button>'
          : '') +
        '</div>';
    }).join('') || '<span style="color:var(--tx3)">—</span>';
    return '<div style="display:grid;grid-template-columns:minmax(64px,96px) 1fr auto;gap:8px;padding:8px 12px;border-bottom:1px solid var(--bd)">' +
      '<div><b>' + PM.esc(day.weekday) + '</b><div style="font-size:var(--fs-xs);color:var(--tx3)">' + fmtDay(day.date) + '</div></div>' +
      '<div>' + entries + '</div>' +
      '<div style="text-align:right;font-weight:700">' + (day.minutes ? fmtMin(day.minutes) : '') +
      (day.overtimeMinutes ? '<div style="font-size:var(--fs-xs);color:var(--wrn);font-weight:600">OT ' + fmtMin(day.overtimeMinutes) + '</div>' : '') +
      '</div></div>';
  }

  /* ───────── actions ───────── */
  function fail(e) { PM.toast((e && e.message) || 'Failed', 'error'); }

  function logTime() {
    var tid = parseInt(val('pmtsTask'), 10), date = val('pmtsDate'), mins = parseInt(val('pmtsMin'), 10);
    var billEl = document.getElementById('pmtsBill');
    if (!tid) { PM.toast('Pick a task first', 'warning'); return; }
    if (!date) { PM.toast('Pick a date', 'warning'); return; }
    if (!mins || mins < 1 || mins > 960) { PM.toast('Minutes must be between 1 and 960', 'warning'); return; }
    PM.api('POST', '/api/pm/tasks/' + tid + '/time', {
      logDate: date,
      minutes: mins,
      note: val('pmtsNote') || null,
      billable: billEl ? billEl.checked : true
    })
      .then(function () { PM.toast('Time logged', 'success'); return load(); })
      .catch(fail);
  }

  function toggleBillable(id, billable) {
    PM.api('PUT', '/api/pm/tasks/time/' + id, { billable: billable })
      .then(function () { PM.toast(billable ? 'Marked as billable' : 'Marked as non-billable', 'success'); return load(); })
      .catch(fail);
  }

  function del(id) {
    if (!window.confirm('Delete this time entry?')) return;
    PM.api('DELETE', '/api/pm/tasks/time/' + id)
      .then(function () { PM.toast('Entry deleted', 'success'); return load(); })
      .catch(fail);
  }

  function submit() {
    if (!window.confirm('Submit this week for approval? It will be locked until the approver decides.')) return;
    PM.api('POST', '/api/pm/timesheets/submit?week=' + S.week)
      .then(function () { PM.toast('Submitted for approval', 'success'); return load(); })
      .catch(fail);
  }

  function withdraw() {
    if (!S.data || !S.data.approvalId) return;
    if (!window.confirm('Withdraw this submission so you can edit the week?')) return;
    PM.api('POST', '/api/pm/approvals/' + S.data.approvalId + '/cancel')
      .then(function () { PM.toast('Submission withdrawn', 'success'); return load(); })
      .catch(fail);
  }

  function shift(delta) {
    var d = parse(S.week); d.setDate(d.getDate() + 7 * delta);
    S.week = iso(d);
    load();
  }
  function thisWeek() { S.week = iso(mondayOf(new Date())); load(); }

  window.PMTimesheetMe = {
    load: load, logTime: logTime, del: del, toggleBillable: toggleBillable,
    submit: submit, withdraw: withdraw, shift: shift, thisWeek: thisWeek
  };
  // keep the tab fresh when another user's action fires a realtime event
  window.addEventListener('pm:event', function () {
    var pane = document.getElementById('pane-timesheet');
    if (pane && pane.classList.contains('active')) load();
  });
})();