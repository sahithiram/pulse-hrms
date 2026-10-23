/**
 * pm-finance.js — PHASE 5
 * "Health & budget" drawer for one project: automatic health with reasons, budget vs actual cost,
 * baseline variance, cost per person, and the weekly status update.
 * Loaded on demand by PM.openProjectFinance(id) (pm-core.js). Managers only (the API enforces it).
 */
(function () {
  'use strict';

  var P = { id: null, data: null };
  var HC = {
    ON_TRACK: ['success', 'On track'], AT_RISK: ['warning', 'At risk'],
    DELAYED: ['danger', 'Delayed'], CRITICAL: ['danger', 'Critical']
  };

  function inr(n) { return '₹' + Number(n || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 }); }
  function hrs(m) {
    m = m || 0; var h = Math.floor(m / 60), r = m % 60;
    return h ? (r ? h + 'h ' + r + 'm' : h + 'h') : r + 'm';
  }
  function sign(n) { return (n > 0 ? '+' : '') + n; }
  function body() { return document.getElementById('pmDrawerBody'); }
  function spinner() {
    return '<div style="padding:24px;text-align:center;color:var(--tx3)"><i class="fas fa-spinner fa-spin"></i> Loading…</div>';
  }
  function fail(e) { PM.toast((e && e.message) || 'Failed', 'error'); }
  function bar(pct, cls) {
    pct = Math.max(0, Math.min(100, pct || 0));
    return '<div class="pm-progress"><div class="pm-progress-bar ' + (cls || '') + '" style="width:' + pct + '%"></div></div>';
  }
  function label(k, v) {
    return '<div><div style="font-size:var(--fs-xs);color:var(--tx3)">' + k + '</div><div style="font-weight:600">' + v + '</div></div>';
  }
  function section(icon, title) {
    return '<div style="font-size:var(--fs-xs);color:var(--tx3);text-transform:uppercase;font-weight:700;margin-bottom:8px"><i class="fas ' + icon + '"></i> ' + title + '</div>';
  }

  function open(projectId) {
    P.id = projectId;
    PM.openDrawer(spinner(), 'Health & budget');
    load();
  }

  function load() {
    return PM.api('GET', '/api/pm/projects/' + P.id + '/finance')
      .then(function (d) { P.data = d; render(); })
      .catch(function (e) {
        body().innerHTML = '<div class="pm-card" style="color:var(--dng)">' + PM.esc(e.message || 'Failed to load') + '</div>';
      });
  }

  function render() {
    var d = P.data, h = d.health || {}, b = d.baseline;
    var hv = h.value || h.computed;
    var hc = HC[hv] || ['info', 'Not evaluated'];
    var h1 = '';

    /* ── health ── */
    h1 += '<div class="pm-card"><div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">' +
      '<b>' + PM.esc(d.projectName) + '</b><span class="pm-badge ' + hc[0] + '">' + hc[1] + '</span>' +
      '<span class="pm-badge ' + (h.mode === 'MANUAL' ? 'purple' : 'info') + '">' + (h.mode === 'MANUAL' ? 'Set manually' : 'Automatic') + '</span>' +
      '<span style="margin-left:auto;display:flex;gap:6px">' +
      (h.mode === 'MANUAL' ? '<button class="pm-btn pm-btn-sm" onclick="PMFinance.auto()">Back to automatic</button>' : '') +
      '<button class="pm-btn pm-btn-sm pm-btn-primary" onclick="PMFinance.recompute()"><i class="fas fa-rotate"></i> Recompute now</button></span></div>' +
      (h.tracked ? '' : '<div style="margin-top:6px;font-size:var(--fs-sm);color:var(--tx3)">Health is only tracked while a project is Active, At Risk or Delayed.</div>') +
      '<ul style="margin:8px 0 0 18px;padding:0;font-size:var(--fs)">' +
      (h.reasons || []).map(function (r) { return '<li>' + PM.esc(r) + '</li>'; }).join('') + '</ul>' +
      (h.mode === 'MANUAL' && h.computed && h.computed !== h.value
        ? '<div style="margin-top:6px;font-size:var(--fs-sm);color:var(--wrn)">The rules currently say <b>' + PM.esc((HC[h.computed] || [0, h.computed])[1]) + '</b>.</div>' : '') +
      (h.updatedAt ? '<div style="margin-top:6px;font-size:var(--fs-xs);color:var(--tx3)">Last calculated ' + PM.esc(String(h.updatedAt).replace('T', ' ').slice(0, 16)) + '</div>' : '') +
      '</div>';

    /* ── key numbers ── */
    h1 += '<div class="pm-card"><div style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px">' +
      label('Progress', d.progressPct + '%') + label('Time used', d.elapsedPct + '%') +
      label('Overdue tasks', d.overdueTasks + ' / ' + d.openTasks) +
      label('Open critical issues', d.criticalIssues) + '</div>' +
      '<div style="margin-top:10px;font-size:var(--fs-xs);color:var(--tx3)">Progress</div>' + bar(d.progressPct, d.progressPct >= d.elapsedPct ? 'success' : 'warning') +
      '<div style="margin-top:6px;font-size:var(--fs-xs);color:var(--tx3)">Time used</div>' + bar(d.elapsedPct, '') + '</div>';

    /* ── budget ── */
    h1 += '<div class="pm-card">' + section('fa-indian-rupee-sign', 'Budget') +
      '<div style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px">' +
      label('Budget', d.budgetTotal != null ? inr(d.budgetTotal) : '—') +
      label('Actual (approved)', inr(d.actualCost)) +
      label('Pending approval', inr(d.pendingCost)) +
      label('Remaining', d.remaining != null ? inr(d.remaining) : '—') + '</div>' +
      '<div style="margin-top:10px;font-size:var(--fs-xs);color:var(--tx3)">Budget used ' + d.burnPct + '%</div>' +
      bar(d.burnPct, d.burnPct > 100 ? 'danger' : d.burnPct >= 85 ? 'warning' : 'success') +
      (d.forecastAtCompletion != null
        ? '<div style="margin-top:8px;font-size:var(--fs)">Forecast at completion: <b>' + inr(d.forecastAtCompletion) + '</b>' +
          (d.budgetTotal != null && d.forecastAtCompletion > d.budgetTotal
            ? ' <span class="pm-badge danger">over budget by ' + inr(d.forecastAtCompletion - d.budgetTotal) + '</span>' : '') + '</div>' : '') +
      '</div>';

    /* ── baseline ── */
    h1 += '<div class="pm-card"><div style="display:flex;align-items:center;margin-bottom:8px">' + section('fa-flag', 'Baseline vs now') +
      '<button class="pm-btn pm-btn-sm" style="margin-left:auto" onclick="PMFinance.saveBaseline()"><i class="fas fa-camera"></i> ' + (b ? 'Re-baseline' : 'Save baseline') + '</button></div>';
    if (!b) {
      h1 += '<div style="color:var(--tx3)">No baseline yet. It is saved automatically the first time the project goes from Planning to Active, or you can save it now.</div>';
    } else {
      h1 += '<table class="pm-table"><thead><tr><th></th><th>Baseline</th><th>Now</th><th>Variance</th></tr></thead><tbody>' +
        '<tr><td>Start</td><td>' + PM.esc(b.start || '—') + '</td><td>' + PM.esc(b.currentStart || '—') + '</td><td>' + (b.startVarianceDays != null ? sign(b.startVarianceDays) + ' d' : '—') + '</td></tr>' +
        '<tr><td>End</td><td>' + PM.esc(b.end || '—') + '</td><td>' + PM.esc(b.currentEnd || '—') + '</td><td>' +
        (b.endVarianceDays != null ? '<b style="color:' + (b.endVarianceDays > 0 ? 'var(--dng)' : 'var(--suc)') + '">' + sign(b.endVarianceDays) + ' d</b>' : '—') + '</td></tr>' +
        '<tr><td>Budget</td><td>' + (b.budget != null ? inr(b.budget) : '—') + '</td><td>' + (b.currentBudget != null ? inr(b.currentBudget) : '—') + '</td><td>' +
        (b.budgetVariance != null ? '<b style="color:' + (b.budgetVariance > 0 ? 'var(--dng)' : 'var(--suc)') + '">' + (b.budgetVariance > 0 ? '+' : '') + inr(b.budgetVariance) + '</b>' : '—') + '</td></tr>' +
        '</tbody></table><div style="margin-top:6px;font-size:var(--fs-xs);color:var(--tx3)">Saved ' + PM.esc(String(b.at).replace('T', ' ').slice(0, 16)) +
        (b.byName ? ' by ' + PM.esc(b.byName) : '') + '</div>';
    }
    h1 += '</div>';

    /* ── cost per person ── */
    h1 += '<div class="pm-card" style="padding:0;overflow:hidden"><div style="padding:10px 12px 0">' + section('fa-users', 'Cost by person') + '</div>';
    if (!(d.lines || []).length) {
      h1 += '<div style="padding:0 12px 12px;color:var(--tx3)">No time has been logged on this project yet.</div>';
    } else {
      h1 += '<div style="overflow-x:auto"><table class="pm-table"><thead><tr><th>Person</th><th>Rate / h</th><th>Approved</th><th>Cost</th><th>Pending</th></tr></thead><tbody>' +
        d.lines.map(function (l) {
          return '<tr><td>' + PM.esc(l.name) + (l.projectMember ? '' : ' <span class="pm-badge warning">not a member</span>') + '</td>' +
            '<td>' + (l.rateMissing ? '<span class="pm-badge danger">no rate</span>' : inr(l.rate)) + '</td>' +
            '<td>' + hrs(l.approvedMinutes) + '</td><td><b>' + inr(l.approvedCost) + '</b></td>' +
            '<td>' + hrs(l.pendingMinutes) + (l.pendingMinutes ? ' · ' + inr(l.pendingCost) : '') + '</td></tr>';
        }).join('') + '</tbody></table></div>';
    }
    h1 += '</div>';

    /* ── warnings + actions ── */
    if ((d.warnings || []).length) {
      h1 += '<div class="pm-card" style="border-left:3px solid var(--wrn)"><ul style="margin:0 0 0 18px;padding:0">' +
        d.warnings.map(function (w) { return '<li>' + PM.esc(w) + '</li>'; }).join('') + '</ul></div>';
    }
    h1 += '<div style="display:flex;gap:6px;justify-content:flex-end">' +
      '<button class="pm-btn" onclick="PM.openProjectMembers(' + d.projectId + ')"><i class="fas fa-user-group"></i> Set rates</button>' +
      '<button class="pm-btn pm-btn-primary" onclick="PMFinance.sendReport()"><i class="fas fa-paper-plane"></i> Send status update</button></div>';

    body().innerHTML = h1;
  }

  /* ───────── actions ───────── */
  function recompute() {
    PM.api('POST', '/api/pm/projects/' + P.id + '/health/recompute')
      .then(function (d) { P.data = d; render(); PM.toast('Recomputed', 'success'); })
      .catch(fail);
  }
  function auto() {
    PM.api('POST', '/api/pm/projects/' + P.id + '/health/auto')
      .then(function (d) { P.data = d; render(); PM.toast('Health is automatic again', 'success'); })
      .catch(fail);
  }
  function saveBaseline() {
    if (!window.confirm('Save the current dates and budget as the baseline? This replaces any earlier baseline.')) return;
    PM.api('POST', '/api/pm/projects/' + P.id + '/baseline')
      .then(function () { PM.toast('Baseline saved', 'success'); return load(); })
      .catch(fail);
  }
  function sendReport() {
    PM.api('POST', '/api/pm/projects/' + P.id + '/status-report')
      .then(function (r) { PM.toast('Status update sent to ' + (r && r.notified != null ? r.notified : 0) + ' person(s)', 'success'); })
      .catch(fail);
  }

  window.PMFinance = { open: open, recompute: recompute, auto: auto, saveBaseline: saveBaseline, sendReport: sendReport };
})();