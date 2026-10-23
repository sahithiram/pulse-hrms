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
        for (var i = 0; i < (teams || []).length; i++) {
          var t = teams[i];
          try {
            var list = await PM.api('GET','/api/pm/workload/team/' + t.id + (from && to ? '?from=' + from + '&to=' + to : ''));
            (list || []).forEach(function (w) { if (!seen[w.employeePk]) { seen[w.employeePk] = true; rows.push(w); } });
          } catch (e) {}
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

  window.PMWorkload = { loadResources: loadResources, open: openWorkload };
})();