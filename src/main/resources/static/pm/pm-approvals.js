/**
 * pm-approvals.js — Approval inbox UI for /pm/02-inbox?tab=approvals
 * Depends on pm-core.js.
 *
 * Step 3b:
 *   - entity icons (incl. CLIENT_SIGN_OFF)
 *   - human-readable payload summaries (incl. CLIENT_SIGN_OFF)
 *   - openRequestForm(entityId, entityType, opts) — used by pm-closure.js
 *   - CSV export of the current tab
 */
(function () {
  'use strict';

  function whenLabel(iso) {
    if (!iso) return '';
    try {
      var d = new Date(iso);
      var diff = (Date.now() - d.getTime()) / 1000;
      if (diff < 60) return 'just now';
      if (diff < 3600) return Math.floor(diff/60) + 'm ago';
      if (diff < 86400) return Math.floor(diff/3600) + 'h ago';
      return Math.floor(diff/86400) + 'd ago';
    } catch (e) { return ''; }
  }

  function statusBadge(status) {
    if (status === 'PENDING')   return ['warning', 'Pending'];
    if (status === 'APPROVED')  return ['success', 'Approved'];
    if (status === 'REJECTED')  return ['danger',  'Rejected'];
    if (status === 'CANCELLED') return ['info',    'Cancelled'];
    return ['primary', status];
  }

  function entityIcon(t) {
    if (t === 'TASK')            return 'fa-list-check';
    if (t === 'PROJECT')         return 'fa-folder';
    if (t === 'BUDGET')          return 'fa-indian-rupee-sign';
    if (t === 'TIMESHEET')       return 'fa-clock';
    if (t === 'CLIENT_SIGN_OFF') return 'fa-signature';
    if (t === 'EXTENSION') return 'fa-calendar-plus';
    return 'fa-square-check';
  }

  // ─── Step 2.7 — human-readable summary of the payload ───
  function payloadSummary(a) {
    if (!a.payload) return '';
    try {
      var p = JSON.parse(a.payload);
      if (a.entityType === 'PROJECT' && p.toStatus)
        return 'Change status to ' + p.toStatus;
      if (a.entityType === 'BUDGET' && p.newBudget != null)
        return 'Set budget to ₹' + Number(p.newBudget).toLocaleString('en-IN');
      if (a.entityType === 'TIMESHEET' && p.totalMinutes != null) {
        var fm = function (m) { var h = Math.floor(m / 60), r = m % 60; return h ? (r ? h + 'h ' + r + 'm' : h + 'h') : r + 'm'; };
        return 'Week of ' + p.weekStart + ' · ' + fm(p.totalMinutes) + ' — ' +
          (p.byProject || []).map(function (x) { return (x.projectName || 'Project') + ' ' + fm(x.minutes); }).join(', ');
      }
      if (a.entityType === 'CLIENT_SIGN_OFF') {
        return p.note ? 'Client accepted the work · ' + p.note : 'Client accepted the work';
      }
      if (a.entityType === 'EXTENSION' && p.newDueDate)
        return 'New due date: ' + p.newDueDate;
    } catch (e) {}
    return '';
  }

  function row(a) {
    var sb = statusBadge(a.status);
    var isPending = a.status === 'PENDING';
    return `
      <div class="row" style="cursor:pointer;border-left:3px solid var(--${sb[0] === 'warning' ? 'wrn' : sb[0] === 'success' ? 'suc' : sb[0] === 'danger' ? 'dng' : 'pri'})" onclick="PMApprovals.open(${a.id})">
        <div class="av"><i class="fas ${entityIcon(a.entityType)}"></i></div>
        <div style="flex:1;min-width:0">
          <div class="msg">${PM.esc(a.entityTitle || a.entityType + ' #' + a.entityId)}
            <span class="badge">${PM.esc(a.entityType)}</span>
            <span class="badge" style="background:var(--${sb[0]}-soft,var(--pri-soft));color:var(--${sb[0]},var(--pri))">${sb[1]}</span>
          </div>
          <div class="meta">Requested by <strong>${PM.esc(a.requestedByName || '—')}</strong> → <strong>${PM.esc(a.approverName || '—')}</strong></div>
          <div class="meta" style="color:var(--pri)">${PM.esc(payloadSummary(a))}</div>
          <div class="meta" style="opacity:.7">${whenLabel(a.createdAt)}${a.reason ? ' · ' + PM.esc(a.reason.substring(0, 60)) : ''}</div>
        </div>
        ${isPending ? `
          <div style="display:flex;gap:4px;align-items:center" onclick="event.stopPropagation()">
            <button class="pm-btn pm-btn-sm pm-btn-primary" onclick="PMApprovals.decide(${a.id},'APPROVED')"><i class="fas fa-check"></i></button>
            <button class="pm-btn pm-btn-sm pm-btn-danger" onclick="PMApprovals.decide(${a.id},'REJECTED')"><i class="fas fa-times"></i></button>
          </div>
        ` : `
          <div style="font-size:11px;color:var(--tx3);align-self:center">${a.decidedByName ? PM.esc(a.decidedByName) + ' · ' + whenLabel(a.decidedAt) : ''}</div>
        `}
      </div>`;
  }

  async function load() {
    var box = document.getElementById('list');
    if (!box) return;
    box.innerHTML = '<div class="empty"><i class="fas fa-spinner fa-spin"></i></div>';

    var chips = `
      <div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:10px;">
        <button class="pm-btn pm-btn-primary" onclick="PMApprovals.setTab('mine')">For me</button>
        <button class="pm-btn" onclick="PMApprovals.setTab('requested')">My requests</button>
                 ${(PM.user && PM.user.admin) ? '<button class="pm-btn" onclick="PMApprovals.setTab(\'all\')">All</button>' : ''}
        <button class="pm-btn pm-btn-sm" style="margin-left:auto;" onclick="PMApprovals.exportCsv()" title="Download the current tab as CSV"><i class="fas fa-file-csv"></i> Export</button>
        <button class="pm-btn pm-btn-sm" onclick="PMApprovals.load()"><i class="fas fa-rotate"></i> Refresh</button>
      </div>`;

    try {
           var url = '/api/pm/approvals' + (state.tab === 'mine' ? '/mine'
             : state.tab === 'requested' ? '/requested' : '');
      var list = await PM.api('GET', url) || [];
      box.innerHTML = chips + (list.length
        ? list.map(row).join('')
        : '<div class="empty"><i class="fas fa-square-check" style="font-size:22px"></i><div style="margin-top:8px">Nothing pending. You\'re all caught up.</div></div>');
    } catch (e) {
      box.innerHTML = chips + '<div class="empty">Failed to load: ' + PM.esc(e.message) + '</div>';
    }
  }

  var state = { tab: 'mine' };

  async function decide(id, decision) {
    var note = prompt((decision === 'APPROVED' ? 'Approval' : 'Rejection') + ' note (optional):', '');
    try {
      await PM.api('POST', '/api/pm/approvals/' + id + '/decide', { decision: decision, note: note || null });
      PM.toast('✓ ' + decision, decision === 'APPROVED' ? 'success' : 'info');
      await load();
      window.pmRefresh && window.pmRefresh();
    } catch (e) { PM.toast(e.message, 'error'); }
  }

  function openApproval(id) {
    PM.api('GET', '/api/pm/approvals/' + id).then(function (a) {
      var sb = statusBadge(a.status);
      var h = '';
      h += '<div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:10px">' +
             '<span class="pm-badge primary">' + PM.esc(a.entityType) + '</span>' +
             '<span class="pm-badge ' + sb[0] + '">' + sb[1] + '</span>' +
             (a.priority === 'HIGH' ? '<span class="pm-badge danger">High priority</span>' : '') +
           '</div>';
      h += '<div class="pm-card"><div style="font-size:var(--fs-xs);color:var(--tx3);margin-bottom:4px">' +
             PM.esc(a.entityType) + '</div>' +
             '<div style="font-size:15px;font-weight:600">' + PM.esc(a.entityTitle || '—') + '</div>' +
           '</div>';
      // ─── Step 2.7 — Requested change block ───
      if (payloadSummary(a)) {
        h += '<div class="pm-card"><div style="font-size:10.5px;color:var(--tx3);margin-bottom:4px">Requested change</div>' +
             '<div style="font-weight:600">' + PM.esc(payloadSummary(a)) + '</div></div>';
      }
      h += '<div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:10px">' +
             '<div class="pm-card" style="margin:0"><div style="font-size:10.5px;color:var(--tx3)">Requested by</div>' +
             '<div style="font-weight:600">' + PM.esc(a.requestedByName || '—') + '</div></div>' +
             '<div class="pm-card" style="margin:0"><div style="font-size:10.5px;color:var(--tx3)">Approver</div>' +
             '<div style="font-weight:600">' + PM.esc(a.approverName || '—') + '</div></div>' +
           '</div>';
      if (a.reason) {
        h += '<div class="pm-card"><div style="font-size:10.5px;color:var(--tx3);margin-bottom:4px">Reason</div>' +
             '<div style="font-size:12.5px;line-height:1.5">' + PM.esc(a.reason) + '</div></div>';
      }
      if (a.status !== 'PENDING') {
        h += '<div class="pm-card"><div style="font-size:10.5px;color:var(--tx3);margin-bottom:4px">Decision</div>' +
             '<div style="font-size:12.5px">' + PM.esc(a.decisionNote || '—') + '</div>' +
             '<div style="font-size:10.5px;color:var(--tx3);margin-top:6px">' +
             PM.esc(a.decidedByName || '—') + ' · ' + PM.fmtDateTime(a.decidedAt) + '</div></div>';
      }
      if (a.status === 'PENDING') {
        h += '<div style="display:flex;gap:6px;margin-top:8px">' +
               '<button class="pm-btn pm-btn-primary" style="flex:1;justify-content:center" onclick="PMApprovals.decide(' + a.id + ',\'APPROVED\')"><i class="fas fa-check"></i> Approve</button>' +
               '<button class="pm-btn pm-btn-danger" style="flex:1;justify-content:center" onclick="PMApprovals.decide(' + a.id + ',\'REJECTED\')"><i class="fas fa-times"></i> Reject</button>' +
             '</div>';
      }
      PM.openDrawer(h, 'Approval #' + a.id);
    }).catch(function (e) { PM.toast(e.message, 'error'); });
  }

  // ────────────────────────────────────────────────────────────
  // Step 3b — openRequestForm(entityId, entityType, opts?)
  //   opts.projectId — project whose managers are the candidate approvers
  //   opts.onDone    — called after a successful POST
  //   opts.title     — drawer title override
  //   opts.reason    — pre-filled reason
  //   opts.payload   — object stored as JSON on the approval
  // ────────────────────────────────────────────────────────────
  async function openRequestForm(entityId, entityType, opts) {
    opts = opts || {};
    entityType = entityType || 'TASK';

    // Pull project members so MANAGERs can be listed as approvers.
    var projectId = opts.projectId || null;
    var members = [];
    if (projectId) {
      try { members = await PM.apiList('GET', '/api/pm/projects/' + projectId + '/members'); }
      catch (e) { members = []; }
    }

    var meCode = PM.user && PM.user.employeeId;   // employee code
    var mePk = PM.user && PM.user.id;             // employee PK
    var people = [];
    try { people = await PM.apiList('GET', '/api/pm/employees/dropdown'); } catch (e) { /* admins just won't be listed */ }
    var adminRoles = { SUPER_ADMIN: 1, HR_MANAGER: 1, HR: 1 };
    var seen = {}, approvers = [];
    members.filter(function (m) { return m.role === 'MANAGER' && m.employeeCode !== meCode; })
      .forEach(function (m) { seen[m.employeeId] = 1; approvers.push({ id: m.employeeId, name: m.employeeName || m.employeeCode }); });
    people.filter(function (p) { return adminRoles[p.role] && p.id !== mePk && !seen[p.id]; })
      .forEach(function (p) { approvers.push({ id: p.id, name: p.name + ' (admin)' }); });
    if (!approvers.length) {
      PM.toast('No approver available. Add another project manager under Members first.', 'warning', 5000);
      return;
    }

    var INP = 'width:100%;padding:7px 9px;border-radius:6px;border:1px solid var(--bd);background:var(--card2);color:var(--tx)';
    var h = '';
    h += '<div class="pm-card">' +
           '<div style="font-size:var(--fs-xs);color:var(--tx3);text-transform:uppercase;font-weight:700;margin-bottom:6px">' +
             '<i class="fas ' + entityIcon(entityType) + '"></i> ' + PM.esc(entityType) + ' #' + entityId +
           '</div>' +
           '<div style="font-size:var(--fs-sm);color:var(--tx3)">Pick who should approve this request.</div>' +
         '</div>';

    h += '<div class="pm-card">' +
           '<div style="font-size:var(--fs-xs);color:var(--tx3);text-transform:uppercase;font-weight:700;margin-bottom:6px">Approver</div>' +
           '<select class="pm-select" id="pmReqApprover">' +
             approvers.map(function (a) { return '<option value="' + a.id + '">' + PM.esc(a.name) + '</option>'; }).join('') +
           '</select>' +
         '</div>';

    h += '<div class="pm-card">' +
           '<div style="font-size:var(--fs-xs);color:var(--tx3);text-transform:uppercase;font-weight:700;margin-bottom:6px">Priority</div>' +
           '<select class="pm-select" id="pmReqPriority">' +
             '<option value="NORMAL">Normal</option>' +
             '<option value="HIGH">High</option>' +
           '</select>' +
         '</div>';

    h += '<div class="pm-card">' +
           '<div style="font-size:var(--fs-xs);color:var(--tx3);text-transform:uppercase;font-weight:700;margin-bottom:6px">Reason (optional)</div>' +
           '<textarea id="pmReqReason" maxlength="1000" style="' + INP + ';height:90px;resize:vertical" placeholder="Why is this approval needed?">' +
             PM.esc(opts.reason || '') +
           '</textarea>' +
         '</div>';

    h += '<div style="display:flex;gap:6px;justify-content:flex-end">' +
           '<button class="pm-btn" onclick="PM.closeDrawer()">Cancel</button>' +
           '<button class="pm-btn pm-btn-primary" id="pmReqSubmit"><i class="fas fa-paper-plane"></i> Send request</button>' +
         '</div>';

    PM.openDrawer(h, opts.title || ('Request approval · ' + entityType + ' #' + entityId));

    var btn = document.getElementById('pmReqSubmit');
    if (!btn) return;
    btn.onclick = async function () {
      var approverId = parseInt(document.getElementById('pmReqApprover').value, 10);
      var priority = document.getElementById('pmReqPriority').value;
      var reason = (document.getElementById('pmReqReason').value || '').trim() || null;
      if (!approverId) { PM.toast('Pick an approver', 'warning'); return; }
      btn.disabled = true;
      try {
        await PM.api('POST', '/api/pm/approvals', {
          entityType: entityType,
          entityId: entityId,
          approverId: approverId,
          reason: reason,
          priority: priority,
          payload: opts.payload ? JSON.stringify(opts.payload) : null
        });
        PM.toast('✓ Approval requested', 'success');
        PM.closeDrawer();
        window.pmRefresh && window.pmRefresh();
        if (typeof opts.onDone === 'function') opts.onDone();
      } catch (e) {
        PM.toast(e.message || 'Failed to request approval', 'error');
        btn.disabled = false;
      }
    };
  }

  // ────────────────────────────────────────────────────────────
  // Step 3b — CSV export of the current tab
  // ────────────────────────────────────────────────────────────
  function exportCsv() {
    var url = '/api/pm/approvals/export.csv' + (state.tab !== 'mine' ? '?tab=' + state.tab : '');
    try {
      window.location.href = url;
    } catch (e) {
      PM.toast('Export failed: ' + (e && e.message || 'unknown'), 'error');
    }
  }

  window.PMApprovals = {
    load: load,
    open: openApproval,
    decide: decide,
    setTab: function (t) { state.tab = t; load(); },
    openRequestForm: openRequestForm,
    exportCsv: exportCsv
  };
})();