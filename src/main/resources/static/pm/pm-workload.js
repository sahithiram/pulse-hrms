/**
 * pm-workload.js — Workload + Timesheet UI for 07-team.html
 */
(function () {
  'use strict';

  function fmtH(h) { return (h == null ? 0 : Math.round(h)) + 'h'; }
  function statusBadge(s) {
    if (s === 'OVER')  return ['danger','Over'];
    if (s === 'WARN')  return ['warning','Busy'];
    if (s === 'UNDER') return ['info','Free'];
    return ['success','OK'];
  }
  function row(w) {
    var sb = statusBadge(w.status);
    var util = Math.min(100, w.utilizationPct);
    return '' +
      '<tr style="cursor:pointer" onclick="PMWorkload.open(' + w.employeePk + ')">' +
        '<td><div style="display:flex;align-items:center;gap:8px">' +
          '<div class="av-md">' + PM.getInitials(w.employeeName) + '</div>' +
          '<div><div style="font-weight:600">' + PM.esc(w.employeeName) + '</div>' +
          '<div style="font-size:var(--fs-xs);color:var(--tx3)">' + PM.esc(w.employeeId || '') + ' · ' + PM.esc(w.department || '') + '</div></div>' +
        '</div></td>' +
        '<td>' + PM.esc(w.teamName || '—') + '</td>' +
        '<td><strong>' + fmtH(w.capacityHours) + '</strong></td>' +
        '<td style="color:' + (w.leaveDays > 0 ? 'var(--wrn)' : 'var(--tx2)') + '">' + fmtH(w.leaveHours) + (w.leaveDays > 0 ? ' (' + w.leaveDays + 'd)' : '') + '</td>' +
        '<td>' + fmtH(w.allocatedHours) + '</td>' +
        '<td>' + fmtH(w.loggedHours) + '</td>' +
        '<td><div class="pm-progress" style="width:120px"><div class="pm-progress-bar ' +
          (w.utilizationPct > 100 ? 'danger' : w.utilizationPct > 85 ? 'warning' : 'success') +
          '" style="width:' + util + '%"></div></div>' +
          '<div style="font-size:var(--fs-xs);color:var(--tx3);margin-top:2px">' + w.utilizationPct + '%</div></td>' +
        '<td><span class="pm-badge ' + sb[0] + '">' + sb[1] + '</span></td>' +
      '</tr>';
  }

  async function loadResources() {
    var tb = document.getElementById('resTableBody');
    if (!tb) return;
    tb.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:20px;color:var(--tx3)"><i class="fas fa-spinner fa-spin"></i> Loading…</td></tr>';

    var teamId = (document.getElementById('resTeam') || {}).value;
    var from   = (document.getElementById('resFrom') || {}).value;
    var to     = (document.getElementById('resTo') || {}).value;

    try {
      var rows = [];
      if (teamId) {
        var qs = [];
        if (from) qs.push('from=' + from);
        if (to)   qs.push('to=' + to);
        rows = await PM.api('GET', '/api/pm/workload/team/' + teamId + (qs.length ? '?' + qs.join('&') : ''));
      } else {
        var teams = await PM.api('GET','/api/pm/teams/dropdown').catch(function(){ return []; });
        var seen = {};
               var failed = 0;
               for (var i = 0; i < (teams || []).length; i++) {
                 var t = teams[i];
                 try {
                   var list = await PM.api('GET','/api/pm/workload/team/' + t.id + (from && to ? '?from=' + from + '&to=' + to : ''));
                   (list || []).forEach(function (w) { if (!seen[w.employeePk]) { seen[w.employeePk] = true; rows.push(w); } });
                 } catch (e) { failed++; }
               }
               if (!rows.length && failed) throw new Error('You do not have access to workload for these teams.');
        }
      }
      rows.sort(function (a, b) { return b.utilizationPct - a.utilizationPct; });

      var over  = rows.filter(function (w) { return w.utilizationPct > 100; }).length;
      var under = rows.filter(function (w) { return w.utilizationPct < 40 && w.capacityHours > 0; }).length;
      var avg   = rows.length ? Math.round(rows.reduce(function (a, w) { return a + w.utilizationPct; }, 0) / rows.length) : 0;

      var overEl = document.getElementById('resOver');   if (overEl)  overEl.textContent  = over;
      var underEl = document.getElementById('resUnder');  if (underEl) underEl.textContent = under;
      var utilEl = document.getElementById('resUtil');    if (utilEl)  utilEl.textContent  = avg + '%';
      var totEl = document.getElementById('resTotal');    if (totEl)   totEl.textContent   = rows.length;

      tb.innerHTML = rows.length
        ? rows.map(row).join('')
        : '<tr><td colspan="8" style="text-align:center;padding:20px;color:var(--tx3)">No resources</td></tr>';
    } catch (e) {
      tb.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:20px;color:var(--dng)">Failed: ' + PM.esc(e.message) + '</td></tr>';
    }
  }

  async function openWorkload(employeePk) {
    PM.openDrawer('<div style="text-align:center;padding:40px;color:var(--tx3)"><i class="fas fa-spinner fa-spin"></i></div>', 'Workload');
    try {
      var w = await PM.api('GET','/api/pm/workload/employee/' + employeePk);
      if (!w) { PM.toast('No data','warning'); PM.closeDrawer(); return; }
      var sb = statusBadge(w.status);
      var h = '';
      h += '<div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px">' +
             '<span class="pm-badge ' + sb[0] + '">' + sb[1] + '</span>' +
             '<span class="pm-badge primary">' + w.utilizationPct + '% util</span>' +
           '</div>';
      h += '<div class="pm-card">' +
             '<div style="display:grid;grid-template-columns:1fr 1fr;gap:8px">' +
               '<div><div style="font-size:var(--fs-xs);color:var(--tx3)">Capacity</div><div style="font-size:18px;font-weight:700">' + fmtH(w.capacityHours) + '</div></div>' +
               '<div><div style="font-size:var(--fs-xs);color:var(--tx3)">Allocated</div><div style="font-size:18px;font-weight:700">' + fmtH(w.allocatedHours) + '</div></div>' +
               '<div><div style="font-size:var(--fs-xs);color:var(--tx3)">Logged</div><div style="font-size:18px;font-weight:700">' + fmtH(w.loggedHours) + '</div></div>' +
               '<div><div style="font-size:var(--fs-xs);color:var(--tx3)">Leave hours</div><div style="font-size:18px;font-weight:700">' + fmtH(w.leaveHours) + '</div></div>' +
             '</div></div>';

      if ((w.leaves || []).length) {
        h += '<div class="pm-card"><div style="font-size:var(--fs-xs);color:var(--tx3);text-transform:uppercase;font-weight:700;margin-bottom:6px">Approved Leaves</div>';
        w.leaves.forEach(function (l) {
          h += '<div style="display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid rgba(42,57,80,.4)">' +
               '<span>' + PM.esc(l.leaveType || 'Leave') + '</span>' +
               '<span style="font-size:var(--fs-xs);color:var(--tx3)">' + PM.fmtDate(l.startDate) + ' → ' + PM.fmtDate(l.endDate) + ' (' + l.days + 'd)</span>' +
               '</div>';
        });
        h += '</div>';
      }
      if ((w.tasks || []).length) {
        h += '<div class="pm-card"><div style="font-size:var(--fs-xs);color:var(--tx3);text-transform:uppercase;font-weight:700;margin-bottom:6px">Assigned Tasks (' + w.tasks.length + ')</div>';
        w.tasks.forEach(function (t) {
          h += '<div style="padding:6px 0;border-bottom:1px solid rgba(42,57,80,.4);cursor:pointer" onclick="PMTask.open(' + t.taskId + ')">' +
               '<div style="font-weight:500">' + PM.esc(t.title) + '</div>' +
               '<div style="font-size:var(--fs-xs);color:var(--tx3)">' + PM.esc(t.status) + ' · ' + PM.esc(t.priority) + ' · ' + (t.estimateHours || '—') + 'h</div>' +
               '</div>';
        });
        h += '</div>';
      }
      PM.openDrawer(h, w.employeeName || 'Workload');
    } catch (e) { PM.toast(e.message, 'error'); PM.closeDrawer(); }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // GAP 19c — "Suggest balance"
  //   button -> POST /api/pm/workload/balance/preview (writes nothing)
  //   -> table, every row UNTICKED by default -> user ticks -> Apply
  //   -> POST /api/pm/workload/balance/apply with ONLY the ticked rows.
  // ═══════════════════════════════════════════════════════════════════════════

  var balState = null;   // { from, to, moves: [...] } of the preview currently on screen

  function balPctColor(p) {
    return p > 100 ? 'var(--dng)' : p > 85 ? 'var(--wrn)' : 'var(--tx2)';
  }
  function balPct(before, after) {
    return '<span style="white-space:nowrap">' +
      '<span style="color:' + balPctColor(before) + '">' + before + '%</span> → ' +
      '<strong style="color:' + balPctColor(after) + '">' + after + '%</strong></span>';
  }

  // The button is created here if 07-team.html does not already have one with id="resBalanceBtn".
  // It copies the class of a neighbouring button so it matches the page styling.
  function ensureBalanceButton() {
    if (document.getElementById('resBalanceBtn')) return;
    var anchor = document.getElementById('resTeam');
    if (!anchor || !anchor.parentNode) return;
    var sib = anchor.parentNode.querySelector('button');
    var b = document.createElement('button');
    b.id = 'resBalanceBtn';
    b.type = 'button';
    b.className = sib ? sib.className : 'pm-btn';
    b.innerHTML = '<i class="fas fa-scale-balanced"></i> Suggest balance';
    b.onclick = function () { suggestBalance(); };
    anchor.parentNode.appendChild(b);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ensureBalanceButton);
  else ensureBalanceButton();

  async function suggestBalance() {
    var teamId = (document.getElementById('resTeam') || {}).value;
    if (!teamId) { PM.toast('Pick a team first, then press Suggest balance', 'warning'); return; }
    var from = (document.getElementById('resFrom') || {}).value;
    var to = (document.getElementById('resTo') || {}).value;

    PM.openDrawer('<div style="text-align:center;padding:40px;color:var(--tx3)"><i class="fas fa-spinner fa-spin"></i></div>', 'Suggest balance');
    try {
      var body = { teamId: Number(teamId) };
      if (from) body.from = from;
      if (to) body.to = to;
      var r = await PM.api('POST', '/api/pm/workload/balance/preview', body);
      balState = { from: r.from, to: r.to, moves: r.moves || [] };
      renderBalance(r);
    } catch (e) {
      PM.toast(e.message, 'error');
      PM.closeDrawer();
    }
  }

  function renderBalance(r) {
    var moves = r.moves || [];
    var h = '<div style="font-size:var(--fs-xs);color:var(--tx3);margin-bottom:8px">' +
      'Suggestions only. Nothing changes until you tick moves and press Apply. ' +
      'Period ' + PM.esc(r.from) + ' to ' + PM.esc(r.to) + '.</div>';

    if (!moves.length) {
      h += '<div class="pm-card" style="padding:16px;text-align:center">No moves to suggest.</div>';
    } else {
      h += '<div style="display:flex;gap:8px;align-items:center;margin-bottom:6px">' +
        '<label style="cursor:pointer"><input type="checkbox" id="balAll" onchange="PMWorkload.balToggleAll(this.checked)"> Select all</label>' +
        '<span style="font-size:var(--fs-xs);color:var(--tx3)">' + moves.length + ' suggested' +
        (r.capped ? ' (capped at ' + r.maxMoves + ')' : '') + '</span></div>';

      h += '<div style="overflow-x:auto"><table class="pm-table" style="width:100%;min-width:560px"><thead><tr>' +
        '<th></th><th>Task</th><th>From</th><th>To</th><th>Why</th></tr></thead><tbody>';
      moves.forEach(function (m, i) {
        h += '<tr>' +
          '<td><input type="checkbox" id="balChk' + i + '" onchange="PMWorkload.balUpdate()"></td>' +
          '<td><div style="font-weight:600">' + PM.esc(m.title) + '</div>' +
              '<div style="font-size:var(--fs-xs);color:var(--tx3)">' + PM.esc(m.projectName || '') + ' · ' + m.hours + 'h</div></td>' +
          '<td>' + PM.esc(m.fromName) + '<div style="font-size:var(--fs-xs)">' + balPct(m.fromBeforePct, m.fromAfterPct) + '</div></td>' +
          '<td>' + PM.esc(m.toName) + '<div style="font-size:var(--fs-xs)">' + balPct(m.toBeforePct, m.toAfterPct) + '</div></td>' +
          '<td style="font-size:var(--fs-xs);color:var(--tx2);max-width:240px">' + PM.esc(m.reason) + '</td>' +
          '</tr>';
      });
      h += '</tbody></table></div>';

      h += '<div style="display:flex;justify-content:flex-end;gap:8px;margin-top:12px">' +
        '<button type="button" class="pm-btn" onclick="PM.closeDrawer()">Cancel</button>' +
        '<button type="button" class="pm-btn primary" id="balApplyBtn" disabled onclick="PMWorkload.balApply()">Apply selected (0)</button>' +
        '</div>';
    }

    (r.notes || []).forEach(function (n) {
      h += '<div style="font-size:var(--fs-xs);color:var(--tx3);margin-top:8px">• ' + PM.esc(n) + '</div>';
    });
    PM.openDrawer(h, 'Suggest balance');
  }

  function balPicked() {
    var out = [];
    if (!balState) return out;
    balState.moves.forEach(function (m, i) {
      var c = document.getElementById('balChk' + i);
      if (c && c.checked) out.push(m);
    });
    return out;
  }

  function balToggleAll(on) {
    if (!balState) return;
    balState.moves.forEach(function (m, i) {
      var c = document.getElementById('balChk' + i);
      if (c) c.checked = !!on;
    });
    balUpdate();
  }

  function balUpdate() {
    var n = balPicked().length;
    var b = document.getElementById('balApplyBtn');
    if (b) { b.disabled = n === 0; b.textContent = 'Apply selected (' + n + ')'; }
    var all = document.getElementById('balAll');
    if (all && balState) all.checked = n > 0 && n === balState.moves.length;
  }

  async function balApply() {
    var picked = balPicked();
    if (!picked.length) { PM.toast('Tick at least one move', 'warning'); return; }
    var btn = document.getElementById('balApplyBtn');
    if (btn) { btn.disabled = true; btn.textContent = 'Applying…'; }
    try {
      var res = await PM.api('POST', '/api/pm/workload/balance/apply', {
        from: balState.from,
        to: balState.to,
        moves: picked.map(function (m) {
          return { taskId: m.taskId, toEmployeeId: m.toEmployeeId, fromEmployeeId: m.fromEmployeeId };
        })
      });
      var skipped = res.skipped || [];
      PM.toast(res.applied + ' task(s) reassigned' + (skipped.length ? ', ' + skipped.length + ' skipped' : ''),
        skipped.length ? 'warning' : 'success');
      if (skipped.length) {
        // keep the drawer open so the user can read why something was not applied
        var h = '<div class="pm-card" style="padding:12px"><strong>' + res.applied + ' applied, ' + skipped.length + ' skipped</strong><ul style="margin:8px 0 0 16px">';
        skipped.forEach(function (s) { h += '<li style="font-size:var(--fs-xs)">' + PM.esc(s) + '</li>'; });
        h += '</ul></div>';
        PM.openDrawer(h, 'Balance result');
      } else {
        PM.closeDrawer();
      }
      balState = null;
      loadResources();   // refresh the utilisation table
    } catch (e) {
      PM.toast(e.message, 'error');
      if (btn) { btn.disabled = false; balUpdate(); }
    }
  }

  // ── REPLACE the existing export line with this one ──
  window.PMWorkload = {
    loadResources: loadResources,
    open: openWorkload,
    suggestBalance: suggestBalance,
    balToggleAll: balToggleAll,
    balUpdate: balUpdate,
    balApply: balApply
  };
})();