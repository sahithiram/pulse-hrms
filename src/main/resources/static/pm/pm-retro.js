/** pm-retro.js  Gap 19b. Needs pm-core.js. Public: PMRetro.open(sprintId), PMRetro.history(projectId), PMRetro.promptAfterComplete(sprintId, name) */
(function () {
  'use strict';
  var S = { sprintId: null, data: null };

  var COLS = [
    ['WENT_WELL', 'Went well', 'success'],
    ['TO_IMPROVE', 'To improve', 'warning'],
    ['ACTION', 'Action items', 'primary']
  ];

  function open(sprintId) {
    S.sprintId = sprintId;
    PM.openDrawer('<div style="text-align:center;padding:40px;color:var(--tx3)"><i class="fas fa-spinner fa-spin"></i></div>', 'Retrospective');
    return refresh();
  }

  function refresh() {
    return PM.api('GET', '/api/pm/sprints/' + S.sprintId + '/retro')
      .then(function (d) { S.data = d; render(); })
      .catch(function (e) { PM.toast(e.message, 'error'); PM.closeDrawer(); });
  }

  function render() {
    var d = S.data, h = '';
    h += '<div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:10px">' +
      '<b>' + PM.esc(d.sprintName) + '</b><span class="pm-badge info">' + PM.esc(d.sprintStatus) + '</span>' +
      (d.closed ? '<span class="pm-badge success">Closed · read-only</span>' : '') +
      '<span style="margin-left:auto;display:flex;gap:6px">' +
      (d.canManage && !d.closed ? '<button class="pm-btn pm-btn-sm" onclick="PMRetro.close()">Close retro</button>' : '') +
      (d.canManage && d.closed ? '<button class="pm-btn pm-btn-sm" onclick="PMRetro.reopen()">Reopen</button>' : '') +
      '</span></div>';
    if (!d.canEdit && !d.closed) {
      h += '<div class="pm-card" style="font-size:var(--fs-sm);color:var(--tx3)">You can read this retrospective. Editing is for sprint members and managers once the sprint has started.</div>';
    }
    COLS.forEach(function (c) {
      var items = (d.items || []).filter(function (i) { return i.category === c[0]; });
      h += '<div class="pm-card"><div style="font-size:var(--fs-xs);text-transform:uppercase;font-weight:700;margin-bottom:6px">' +
        c[1] + ' (' + items.length + ')</div>' +
        (items.length ? items.map(function (i) { return itemHtml(i); }).join('') : '<div style="color:var(--tx3);font-size:var(--fs-sm)">Nothing yet</div>');
      if (d.canEdit) {
        h += '<div style="display:flex;gap:6px;margin-top:8px;flex-wrap:wrap">' +
          '<input class="pm-input" id="rt_' + c[0] + '" placeholder="Add…" maxlength="1000" style="flex:1;min-width:160px" onkeydown="if(event.key===\'Enter\')PMRetro.add(\'' + c[0] + '\')">' +
          (c[0] === 'ACTION' ? '<input class="pm-input" type="date" id="rtDue" title="Due date" style="width:150px">' : '') +
          '<button class="pm-btn pm-btn-sm pm-btn-primary" onclick="PMRetro.add(\'' + c[0] + '\')">Add</button></div>';
      }
      h += '</div>';
    });
    if (d.legacyNotes) {
      h += '<div class="pm-card"><div style="font-size:var(--fs-xs);color:var(--tx3);text-transform:uppercase;font-weight:700">Earlier notes (read-only)</div>' +
        '<div style="white-space:pre-wrap;margin-top:4px">' + PM.esc(d.legacyNotes) + '</div></div>';
    }
    PM.openDrawer(h, 'Retrospective');
  }

  function itemHtml(i) {
    return '<div style="display:flex;gap:8px;align-items:flex-start;padding:6px 0;border-bottom:1px solid rgba(42,57,80,.4)">' +
      '<div style="flex:1;min-width:0"><div>' + PM.esc(i.text) + '</div>' +
      '<div style="font-size:var(--fs-xs);color:var(--tx3)">' + PM.esc(i.createdByName || '') +
      (i.assigneeName ? ' · → ' + PM.esc(i.assigneeName) : '') + (i.dueDate ? ' · due ' + PM.fmtDate(i.dueDate) : '') + '</div>' +
      (i.convertedTaskId ? '<a href="#" onclick="PMTask.open(' + i.convertedTaskId + ');return false" class="pm-badge success">Task #' + i.convertedTaskId + '</a>' : '') +
      '</div>' +
      (i.canConvert ? '<button class="pm-btn pm-btn-sm" onclick="PMRetro.convert(' + i.id + ')">Make task</button>' : '') +
      (i.canDelete ? '<button class="pm-btn pm-btn-sm pm-btn-icon" title="Delete" onclick="PMRetro.del(' + i.id + ')"><i class="fas fa-trash"></i></button>' : '') +
      '</div>';
  }

  function add(cat) {
    var inp = document.getElementById('rt_' + cat), text = inp && inp.value.trim();
    if (!text) { PM.toast('Write something first', 'warning'); return; }
    var b = { category: cat, text: text };
    if (cat === 'ACTION') { var due = document.getElementById('rtDue'); if (due && due.value) b.dueDate = due.value; }
    PM.api('POST', '/api/pm/sprints/' + S.sprintId + '/retro/items', b)
      .then(refresh).catch(function (e) { PM.toast(e.message, 'error'); });
  }
  function del(id) {
    if (!window.confirm('Delete this item?')) return;
    PM.api('DELETE', '/api/pm/sprints/' + S.sprintId + '/retro/items/' + id)
      .then(refresh).catch(function (e) { PM.toast(e.message, 'error'); });
  }
  function convert(id) {
    PM.api('POST', '/api/pm/sprints/' + S.sprintId + '/retro/items/' + id + '/convert', {})
      .then(function (t) { PM.toast('Task #' + t.id + ' created (backlog)', 'success'); return refresh(); })
      .catch(function (e) { PM.toast(e.message, 'error'); });
  }
  function close() {
    if (!window.confirm('Close this retrospective? It becomes read-only.')) return;
    PM.api('POST', '/api/pm/sprints/' + S.sprintId + '/retro/close').then(refresh).catch(function (e) { PM.toast(e.message, 'error'); });
  }
  function reopen() {
    PM.api('POST', '/api/pm/sprints/' + S.sprintId + '/retro/reopen').then(refresh).catch(function (e) { PM.toast(e.message, 'error'); });
  }

  /* read-only history for a project */
  function history(projectId) {
    PM.openDrawer('<div style="text-align:center;padding:40px"><i class="fas fa-spinner fa-spin"></i></div>', 'Retrospective history');
    PM.api('GET', '/api/pm/projects/' + projectId + '/retros').then(function (rows) {
      PM.openDrawer('<div style="overflow-x:auto"><table class="pm-table" style="width:100%;min-width:480px"><thead><tr>' +
        '<th>Sprint</th><th>Went well</th><th>To improve</th><th>Actions</th><th>Converted</th><th></th></tr></thead><tbody>' +
        ((rows || []).map(function (r) {
          return '<tr><td><b>' + PM.esc(r.sprintName) + '</b><div style="font-size:var(--fs-xs);color:var(--tx3)">' + PM.esc(r.sprintStatus) +
            (r.closed ? ' · closed' : '') + '</div></td><td>' + r.wentWell + '</td><td>' + r.toImprove + '</td><td>' + r.actions + '</td><td>' + r.converted +
            '</td><td><button class="pm-btn pm-btn-sm" onclick="PMRetro.open(' + r.sprintId + ')">' + (r.hasRetro ? 'View' : 'Start') + '</button></td></tr>';
        }).join('') || '<tr><td colspan="6" style="text-align:center;padding:20px;color:var(--tx3)">No sprints</td></tr>') +
        '</tbody></table></div>', 'Retrospective history');
    }).catch(function (e) { PM.toast(e.message, 'error'); PM.closeDrawer(); });
  }

  /* call this right after a sprint is completed successfully */
  function promptAfterComplete(sprintId, name) {
    if (window.confirm('Sprint "' + name + '" is complete. Start the retrospective now?')) open(sprintId);
  }

  window.PMRetro = { open: open, add: add, del: del, convert: convert, close: close, reopen: reopen,
                     history: history, promptAfterComplete: promptAfterComplete };
})();