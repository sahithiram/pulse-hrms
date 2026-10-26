/**
 * pm-task-drawer.js — shared task drawer for PM pages.
 * Exposes: PMTask.open(id), PMTask.upload(taskId, file)
 * Requires pm-core.js loaded first.
 *
 * Phase 5: sprint + milestone pickers in Details tab.
 * Phase 6: hydratePickers (async, non-blocking).
 * Phase 7: Request Approval button.
 */
(function () {
  'use strict';

  const statusLabel = {
    todo:['primary','To Do'], 'in-progress':['warning','In Progress'],
    blocked:['danger','Blocked'], 'in-review':['info','In Review'], done:['success','Done']
  };
  const priorityBadge = {
    Critical:'danger', High:'danger', Medium:'warning', Low:'success'
  };
  const statusCol = {
    todo:'var(--tx3)', 'in-progress':'var(--wrn)', 'in-review':'var(--pur)',
    blocked:'var(--dng)', done:'var(--suc)'
  };

  function mapStatus(s){ return (s||'TODO').toLowerCase().replace(/_/g,'-'); }
   // ── Phase 4: allowed status moves come from the server (single source of truth) ──
   let WORKFLOW = null;
   async function loadWorkflow() {
     if (WORKFLOW) return WORKFLOW;
     try { WORKFLOW = await PM.api('GET', '/api/pm/workflow/task-transitions'); }
     catch (e) { WORKFLOW = {}; }
     return WORKFLOW;
   }
   function canMove(cur, k) {
     const up = s => String(s || '').toUpperCase().replace(/-/g, '_');
     const c = up(cur), t = up(k);
     if (c === t) return true;
     const allowed = (WORKFLOW || {})[c];
     return !allowed || allowed.indexOf(t) > -1;
   }
    function pLabel(p){ return (p||'MEDIUM').charAt(0)+(p||'MEDIUM').slice(1).toLowerCase(); }

  async function upload(taskId, file) {
    const fd = new FormData();
    fd.append('file', file);
    const res = await fetch('/api/pm/tasks/' + taskId + '/attachments', {
      method:'POST', credentials:'same-origin', body: fd,
      headers: { 'X-Requested-With':'XMLHttpRequest' }
    });
    const data = await res.json().catch(()=>({}));
    if (!res.ok || data.success === false)
      throw new Error(data.message || ('HTTP ' + res.status));
    return data.data;
  }

  async function open(id) {
    PM.openDrawer(
      '<div style="text-align:center;padding:40px;color:var(--tx3)">' +
        '<i class="fas fa-spinner fa-spin" style="font-size:24px"></i>' +
        '<div style="margin-top:8px">Loading…</div></div>', 'Task');

    try {
      const [task, subtasks, checklist, deps, watchers, comments, timeLogs, files] = await Promise.all([
        PM.api('GET','/api/pm/tasks/' + id),
        PM.api('GET','/api/pm/tasks/' + id + '/subtasks').catch(()=>[]),
        PM.api('GET','/api/pm/tasks/' + id + '/checklist').catch(()=>[]),
        PM.api('GET','/api/pm/tasks/' + id + '/dependencies/graph')
          .catch(()=>({blocks:[],blockedBy:[],openBlockerCount:0})),
        PM.api('GET','/api/pm/tasks/' + id + '/watchers').catch(()=>[]),
        PM.api('GET','/api/pm/tasks/' + id + '/comments').catch(()=>[]),
        PM.api('GET','/api/pm/tasks/' + id + '/time').catch(()=>[]),
        PM.api('GET','/api/pm/tasks/' + id + '/attachments').catch(()=>[])
      ]);
   await loadWorkflow();
         render(task, subtasks, checklist, deps, watchers, comments, timeLogs, files);
    } catch (e) {
      PM.toast(e.message || 'Failed to load task', 'error');
      PM.closeDrawer();
    }
  }

  function render(t, subtasks, checklist, deps, watchers, comments, timeLogs, files) {
    document.getElementById('pmDrawerTitle').textContent = t.title || 'Task';
    const sb = statusLabel[mapStatus(t.status)] || ['primary', t.status];
    const pri = pLabel(t.priority);

    let h = '';

    if (deps.openBlockerCount > 0) {
      h += '<div class="blocked-banner" style="background:var(--dng-soft);border:1px solid rgba(239,68,68,.3);' +
           'border-radius:8px;padding:10px 12px;margin-bottom:10px;display:flex;gap:10px;align-items:center">' +
           '<div style="width:32px;height:32px;border-radius:50%;background:var(--dng);color:#fff;' +
           'display:grid;place-content:center"><i class="fas fa-ban"></i></div>' +
           '<div><div style="font-weight:600;color:var(--dng)">Blocked by ' +
           deps.openBlockerCount + ' open task(s)</div>' +
           '<div style="font-size:11.5px;color:var(--tx2)">Resolve before marking DONE.</div></div></div>';
    }

    // Tabs
    h += '<div id="pmTaskTabs" style="display:flex;gap:0;border-bottom:1px solid var(--bd);margin-bottom:10px;overflow-x:auto">';
    ['details','subtasks','checklist','blockers','comments','time','files','history'].forEach((tab, i) => {
      h += '<button class="pm-task-tab' + (i === 0 ? ' active' : '') + '" data-tab="' + tab + '" ' +
           'style="background:none;border:none;color:var(--tx2);padding:8px 12px;font-size:12.5px;' +
           'cursor:pointer;border-bottom:2px solid transparent;white-space:nowrap;' +
           (i === 0 ? 'color:var(--pri);border-bottom-color:var(--pri);font-weight:600' : '') + '">' +
           tab.charAt(0).toUpperCase() + tab.slice(1) + '</button>';
    });
    h += '</div>';

    // Details pane
    h += '<div class="pm-task-pane" data-pane="details">';
    h += '<div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:10px">' +
         '<span class="pm-badge ' + sb[0] + '">' + sb[1] + '</span>' +
         '<span class="pm-badge ' + (priorityBadge[pri] || 'primary') + '">' + pri + '</span>' +
         (t.teamName ? '<span class="pm-badge purple"><i class="fas fa-users"></i> ' + PM.esc(t.teamName) + '</span>' : '') +
         (t.sprintName ? '<span class="pm-badge info"><i class="fas fa-bolt"></i> ' + PM.esc(t.sprintName) + '</span>' : '') +
         (t.milestoneName ? '<span class="pm-badge success"><i class="fas fa-flag-checkered"></i> ' + PM.esc(t.milestoneName) + '</span>' : '') +
         '</div>';
    h += '<div class="pm-card"><div style="font-size:10.5px;color:var(--tx3);margin-bottom:4px">DESCRIPTION</div>' +
         '<div style="font-size:12.5px;color:var(--tx2);line-height:1.5">' + PM.esc(t.description || 'No description') + '</div></div>';
    h += '<div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:10px">' +
         '<div class="pm-card" style="margin:0"><div style="font-size:10.5px;color:var(--tx3)">Due</div>' +
         '<div style="font-weight:600">' + (t.dueDate ? PM.fmtDate(t.dueDate) : '—') + '</div></div>' +
         '<div class="pm-card" style="margin:0"><div style="font-size:10.5px;color:var(--tx3)">Progress</div>' +
         '<div style="font-weight:600">' + (t.progressPct || 0) + '%</div></div>' +
         '</div>';
    h += '<div class="pm-card"><div style="font-size:10.5px;color:var(--tx3);margin-bottom:6px">ASSIGNEES</div>';
    if ((t.assigneeNames || []).length) {
      h += '<div style="display:flex;gap:6px;flex-wrap:wrap">' +
           t.assigneeNames.map(n => '<span class="pm-badge primary">' + PM.esc(n) + '</span>').join('') + '</div>';
    } else h += '<span style="color:var(--tx3);font-size:11.5px">Unassigned</span>';
    h += '</div>';

    // Sprint picker
    h += '<div class="pm-card">' +
         '<div style="font-size:10.5px;color:var(--tx3);margin-bottom:6px">SPRINT</div>' +
         '<select id="pmTaskSprintPicker" class="pm-input" ' +
         'style="background:var(--bg);border:1px solid var(--bd);color:var(--tx);padding:6px 10px;border-radius:6px;width:100%;font-family:inherit;font-size:12.5px" ' +
         'onchange="PMTask.changeSprint(' + t.id + ', this.value)">' +
         '<option value="">— Backlog —</option></select>' +
         '</div>';

    // Milestone picker
    h += '<div class="pm-card">' +
         '<div style="font-size:10.5px;color:var(--tx3);margin-bottom:6px">MILESTONE</div>' +
         '<select id="pmTaskMilestonePicker" class="pm-input" ' +
         'style="background:var(--bg);border:1px solid var(--bd);color:var(--tx);padding:6px 10px;border-radius:6px;width:100%;font-family:inherit;font-size:12.5px" ' +
         'onchange="PMTask.changeMilestone(' + t.id + ', this.value)">' +
         '<option value="">— None —</option></select>' +
         '</div>';

    // Move status
    h += '<div class="pm-card"><div style="font-size:10.5px;color:var(--tx3);margin-bottom:6px">MOVE STATUS</div>';
    Object.keys(statusLabel).forEach(k => {
      h += '<button class="pm-btn' + (mapStatus(t.status) === k ? ' pm-btn-primary' : '') +
           '" style="width:100%;margin-bottom:4px;justify-content:flex-start' +
           (canMove(t.status, k) ? '' : ';opacity:.4;cursor:not-allowed') + '" ' +
           (canMove(t.status, k) ? '' : 'disabled title="Not allowed from ' + statusLabel[mapStatus(t.status)][1] + '" ') +
           'onclick="PMTask.changeStatus(' + t.id + ',\'' + k + '\')">' + statusLabel[k][1] + '</button>';
    });
    h += '</div>';

    // Request approval
    h += '<div class="pm-card">' +
         '<div style="font-size:10.5px;color:var(--tx3);margin-bottom:6px">APPROVAL</div>' +
         '<button class="pm-btn pm-btn-primary" style="width:100%;justify-content:center" ' +
         'onclick="PMTask.requestApproval(' + t.id + ')">' +
         '<i class="fas fa-square-check"></i> Request approval</button>' +
         '<button class="pm-btn" style="width:100%;justify-content:center;margin-top:6px" ' +
         'onclick="PMTask.requestExtension(' + t.id + ')">' +
         '<i class="fas fa-calendar-plus"></i> Request extension</button>' +
         '</div>';

    h += '</div>'; // end details pane

    // Subtasks pane
    h += '<div class="pm-task-pane" data-pane="subtasks" style="display:none">';
    h += '<button class="pm-btn pm-btn-primary" onclick="PMTask.addSubtask(' + t.id + ')">' +
         '<i class="fas fa-plus"></i> Add subtask</button>';
    h += '<div style="margin-top:8px">' +
         (subtasks.length
           ? subtasks.map(st => '<div class="pm-card" style="cursor:pointer;margin-bottom:6px" ' +
               'onclick="PMTask.open(' + st.id + ')">' + PM.esc(st.title) + '</div>').join('')
           : '<div style="text-align:center;padding:20px;color:var(--tx3)">No subtasks</div>') +
         '</div></div>';

    // Checklist pane
    h += '<div class="pm-task-pane" data-pane="checklist" style="display:none">';
    h += '<button class="pm-btn pm-btn-primary" onclick="PMTask.addChecklist(' + t.id + ')">' +
         '<i class="fas fa-plus"></i> Add item</button>';
    h += '<div style="margin-top:8px">' + renderChecklist(checklist, t.id) + '</div></div>';

    // Blockers pane
    h += '<div class="pm-task-pane" data-pane="blockers" style="display:none">';
    h += '<button class="pm-btn pm-btn-primary" onclick="PMTask.addDependency(' + t.id + ')">' +
         '<i class="fas fa-plus"></i> Add blocker</button>';
    h += '<div style="margin-top:8px">';
    if ((deps.blockedBy || []).length) {
      h += '<div style="font-size:10.5px;color:var(--tx3);margin:6px 0 4px;font-weight:600">BLOCKED BY</div>';
      h += deps.blockedBy.map(d => depRow(d, t.id)).join('');
    }
    if ((deps.blocks || []).length) {
      h += '<div style="font-size:10.5px;color:var(--tx3);margin:6px 0 4px;font-weight:600">BLOCKS</div>';
      h += deps.blocks.map(d => depRow(d, t.id)).join('');
    }
    if (!(deps.blockedBy || []).length && !(deps.blocks || []).length)
      h += '<div style="text-align:center;padding:20px;color:var(--tx3)">No dependencies</div>';
    h += '</div></div>';

    // Comments pane
    h += '<div class="pm-task-pane" data-pane="comments" style="display:none">';
    h += '<textarea id="pmCommentBox" class="pm-textarea" placeholder="Write a comment… use @name to mention"></textarea>';
    h += '<button class="pm-btn pm-btn-primary" style="margin-top:6px" onclick="PMTask.postComment(' + t.id + ')">' +
         '<i class="fas fa-paper-plane"></i> Post</button>';
    h += '<div id="pmCommentList" style="margin-top:10px">' +
         (comments.length
           ? comments.map(c =>
               '<div class="pm-card" style="margin-bottom:6px">' +
                 '<div style="font-size:11.5px;color:var(--tx2);margin-bottom:4px">' +
                 '<strong>' + PM.esc(c.authorName || 'User') + '</strong> · ' +
                 PM.fmtDateTime(c.createdAt) + '</div>' +
                 '<div style="font-size:12.5px;line-height:1.5">' + PM.esc(c.body) + '</div>' +
                 (c.mentions ? '<div style="font-size:10.5px;color:var(--pri);margin-top:4px">' +
                   '<i class="fas fa-at"></i> ' + PM.esc(c.mentions) + '</div>' : '') +
               '</div>').join('')
           : '<div style="text-align:center;padding:14px;color:var(--tx3)">No comments yet</div>') +
         '</div></div>';

    // Time pane
    h += '<div class="pm-task-pane" data-pane="time" style="display:none">';
    h += '<div class="pm-card" style="display:flex;justify-content:space-between;align-items:center">' +
         '<div><div style="font-size:10.5px;color:var(--tx3)">LOGGED (total)</div>' +
         '<div style="font-size:18px;font-weight:700">' + (t.loggedHours || 0) + 'h</div></div>' +
         '<div style="font-size:11.5px;color:var(--tx2)">' + timeLogs.length + ' entries</div>' +
         '</div>';
    h += '<div style="display:grid;grid-template-columns:1fr 1fr;gap:6px;margin-bottom:6px">' +
         '<input type="date" id="pmLogDate" class="pm-input">' +
         '<input type="number" id="pmLogMin" class="pm-input" placeholder="Minutes" min="1">' +
         '</div>' +
         '<input type="text" id="pmLogNote" class="pm-input" placeholder="Note (optional)">' +
         '<button class="pm-btn pm-btn-primary" style="margin-top:6px" onclick="PMTask.logTime(' + t.id + ')">' +
         '<i class="fas fa-clock"></i> Log time</button>';
    h += '<div style="margin-top:10px">' +
         (timeLogs.length
           ? timeLogs.map(l =>
               '<div class="pm-card" style="display:flex;justify-content:space-between;margin-bottom:6px">' +
                 '<div><div style="font-size:12px;font-weight:500">' + l.minutes + ' min</div>' +
                 '<div style="font-size:10.5px;color:var(--tx3)">' + PM.fmtDate(l.logDate) +
                 (l.note ? ' · ' + PM.esc(l.note) : '') + '</div></div>' +
                 '<button class="pm-btn pm-btn-icon pm-btn-danger" onclick="PMTask.deleteTime(' + l.id + ',' + t.id + ')">' +
                 '<i class="fas fa-trash"></i></button>' +
               '</div>').join('')
           : '<div style="text-align:center;padding:14px;color:var(--tx3)">No time logged</div>') +
         '</div></div>';

    // Files pane
    h += '<div class="pm-task-pane" data-pane="files" style="display:none">';
    h += '<input type="file" id="pmFileInput" class="pm-input" style="padding:4px">' +
         '<button class="pm-btn pm-btn-primary" style="margin-top:6px" onclick="PMTask.uploadFile(' + t.id + ')">' +
         '<i class="fas fa-upload"></i> Upload</button>';
    h += '<div id="pmFileList" style="margin-top:10px">' +
         (files.length
           ? files.map(f =>
               '<div class="pm-card" style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">' +
                 '<div style="min-width:0"><div style="font-size:12px;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' +
                 PM.esc(f.fileName) + '</div>' +
                 '<div style="font-size:10.5px;color:var(--tx3)">' +
                 Math.round((f.fileSize || 0) / 1024) + ' KB · ' + PM.fmtDate(f.uploadedAt) + '</div></div>' +
                 '<div style="display:flex;gap:4px">' +
                 '<a class="pm-btn pm-btn-icon" href="/api/pm/tasks/attachments/' + f.id + '/download" title="Download">' +
                 '<i class="fas fa-download"></i></a>' +
                 '<button class="pm-btn pm-btn-icon pm-btn-danger" onclick="PMTask.deleteFile(' + f.id + ',' + t.id + ')">' +
                 '<i class="fas fa-trash"></i></button>' +
                 '</div></div>').join('')
           : '<div style="text-align:center;padding:14px;color:var(--tx3)">No files</div>') +
         '</div></div>';

    // History pane
    h += '<div class="pm-task-pane" data-pane="history" style="display:none">' +
         '<div id="pmHistoryList"><div style="text-align:center;padding:14px;color:var(--tx3)">Loading…</div></div>' +
         '</div>';

    document.getElementById('pmDrawerBody').innerHTML = h;

    // Wire tab switching
    document.querySelectorAll('#pmTaskTabs .pm-task-tab').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#pmTaskTabs .pm-task-tab').forEach(b => {
          b.classList.remove('active');
          b.style.color = 'var(--tx2)';
          b.style.borderBottomColor = 'transparent';
          b.style.fontWeight = '400';
        });
        btn.classList.add('active');
        btn.style.color = 'var(--pri)';
        btn.style.borderBottomColor = 'var(--pri)';
        btn.style.fontWeight = '600';

        document.querySelectorAll('.pm-task-pane').forEach(p => p.style.display = 'none');
        const pane = document.querySelector('.pm-task-pane[data-pane="' + btn.dataset.tab + '"]');
        if (pane) pane.style.display = 'block';

        if (btn.dataset.tab === 'history') loadHistory(t.id);
      });
    });

    hydratePickers(t);
  }

  async function hydratePickers(t) {
    const projectId = t.projectId;
    if (!projectId) return;

    try {
      const sprints = await PM.api('GET', '/api/pm/projects/' + projectId + '/sprints').catch(() => []);
      const sel = document.getElementById('pmTaskSprintPicker');
      if (sel) {
        const current = t.sprintId || '';
        let html = '<option value=""' + (!current ? ' selected' : '') + '>— Backlog —</option>';
        (sprints || []).forEach(s => {
          const selAttr = String(s.id) === String(current) ? ' selected' : '';
          html += '<option value="' + s.id + '"' + selAttr + '>' +
                  PM.esc(s.name) + ' (' + s.status + ')</option>';
        });
        sel.innerHTML = html;
      }
    } catch (e) {
      console.warn('sprint picker failed', e);
    }

    try {
      const milestones = await PM.api('GET', '/api/pm/projects/' + projectId + '/milestones').catch(() => []);
      const sel = document.getElementById('pmTaskMilestonePicker');
      if (sel) {
        const current = t.milestoneId || '';
        let html = '<option value=""' + (!current ? ' selected' : '') + '>— None —</option>';
        (milestones || []).forEach(m => {
          const selAttr = String(m.id) === String(current) ? ' selected' : '';
          html += '<option value="' + m.id + '"' + selAttr + '>' + PM.esc(m.name) + '</option>';
        });
        sel.innerHTML = html;
      }
    } catch (e) {
      console.warn('milestone picker failed', e);
    }
  }

  function renderChecklist(items, taskId) {
    if (!items || !items.length)
      return '<div style="text-align:center;padding:14px;color:var(--tx3)">No items</div>';
    let h = '';
    items.forEach(item => {
      h += '<div style="display:flex;align-items:center;gap:6px;padding:4px 0">' +
           '<input type="checkbox" ' + (item.done ? 'checked' : '') +
           ' onchange="PMTask.toggleCheck(' + item.id + ',' + taskId + ')" style="accent-color:var(--suc)">' +
           '<span style="' + (item.done ? 'text-decoration:line-through;color:var(--tx3)' : '') + '">' +
           PM.esc(item.title) + '</span></div>';
      if (item.children && item.children.length)
        h += '<div style="padding-left:20px">' + renderChecklist(item.children, taskId) + '</div>';
    });
    return h;
  }

  function depRow(d, taskId) {
    const s = mapStatus(d.status);
    return '<div class="pm-card" style="cursor:pointer;display:flex;align-items:center;gap:8px;margin-bottom:6px" ' +
           'onclick="PMTask.open(' + d.taskId + ')">' +
           '<span style="width:8px;height:8px;border-radius:50%;background:' + (statusCol[s] || 'var(--tx3)') + '"></span>' +
           '<div style="flex:1;min-width:0"><div style="font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' +
           PM.esc(d.title) + '</div>' +
           '<div style="font-size:10.5px;color:var(--tx3)">' + PM.esc(d.status) + ' · ' + PM.esc(d.priority) + '</div></div>' +
           '<i class="fas fa-chevron-right" style="color:var(--tx3);font-size:10px"></i>' +
           '</div>';
  }

  async function loadHistory(taskId) {
    const el = document.getElementById('pmHistoryList');
    try {
      const rows = await PM.api('GET', '/api/pm/tasks/' + taskId + '/history');
      if (!el) return;
      el.innerHTML = (rows || []).length
        ? rows.map(r =>
            '<div class="pm-card" style="margin-bottom:6px">' +
              '<div style="font-size:10.5px;color:var(--tx3)">' + PM.fmtDateTime(r.createdAt) + '</div>' +
              '<div style="font-size:12px;margin-top:2px"><strong>' + PM.esc(r.actorName || 'System') + '</strong> · ' +
              PM.esc(r.message || r.action) + '</div>' +
            '</div>').join('')
        : '<div style="text-align:center;padding:14px;color:var(--tx3)">No history yet</div>';
    } catch (e) {
      if (el) el.innerHTML = '<div style="text-align:center;padding:14px;color:var(--dng)">Failed to load history: ' +
        PM.esc(e.message || 'Unknown error') + '</div>';
    }
  }

  // ─── Step 3.2D — BLOCKED requires a reason ───
  async function changeStatus(taskId, newStatus) {
    try {
      const backend = newStatus.toUpperCase().replace('-', '_');
      const body = { status: backend };
      if (backend === 'BLOCKED') {
        var reason = prompt('Why is this task blocked?');
        if (!reason || !reason.trim()) { PM.toast('A reason is required to block a task', 'error'); return; }
        body.reason = reason.trim();
      }
      await PM.api('PATCH', '/api/pm/tasks/' + taskId + '/status', body);
      PM.toast('✓ Status updated', 'success');
      window.pmRefresh && window.pmRefresh();
      open(taskId);
    } catch (e) { PM.toast(e.message || 'Failed', 'error'); }
  }

  async function addSubtask(taskId) {
    const title = prompt('Subtask title:');
    if (!title) return;
    try {
      const parent = await PM.api('GET', '/api/pm/tasks/' + taskId);
      await PM.api('POST', '/api/pm/tasks/' + taskId + '/subtasks', {
        projectId: parent.projectId, teamId: parent.teamId,
        parentTaskId: taskId, title,
        priority: 'MEDIUM', type: 'TASK',
        storyPoints: 2, assigneeIds: []
      });
      PM.toast('✓ Subtask created', 'success');
      open(taskId);
    } catch (e) { PM.toast(e.message || 'Failed', 'error'); }
  }

  async function addChecklist(taskId) {
    const title = prompt('Checklist item:');
    if (!title) return;
    try {
      await PM.api('POST', '/api/pm/tasks/' + taskId + '/checklist', { title });
      open(taskId);
    } catch (e) { PM.toast(e.message || 'Failed', 'error'); }
  }

  async function toggleCheck(itemId, taskId) {
    try { await PM.api('PATCH', '/api/pm/tasks/checklist/' + itemId + '/toggle'); open(taskId); }
    catch (e) { PM.toast(e.message || 'Failed', 'error'); }
  }

  async function postComment(taskId) {
    const body = (document.getElementById('pmCommentBox').value || '').trim();
    if (!body) return;
    try {
      await PM.api('POST', '/api/pm/tasks/' + taskId + '/comments', { body });
      PM.toast('✓ Comment posted', 'success');
      open(taskId);
    } catch (e) { PM.toast(e.message || 'Failed', 'error'); }
  }

  async function logTime(taskId) {
    const logDate = document.getElementById('pmLogDate').value;
    const minutes = parseInt(document.getElementById('pmLogMin').value || '0', 10);
    const note = document.getElementById('pmLogNote').value || null;
    if (!logDate || !minutes) { PM.toast('Date and minutes required', 'warning'); return; }
    try {
      await PM.api('POST', '/api/pm/tasks/' + taskId + '/time', { logDate, minutes, note });
      PM.toast('✓ Time logged', 'success');
      open(taskId);
    } catch (e) { PM.toast(e.message || 'Failed', 'error'); }
  }

  async function deleteTime(id, taskId) {
    if (!confirm('Delete this time log?')) return;
    try {
      await PM.api('DELETE', '/api/pm/tasks/time/' + id);
      open(taskId);
    } catch (e) { PM.toast(e.message || 'Failed', 'error'); }
  }

  async function uploadFile(taskId) {
    const input = document.getElementById('pmFileInput');
    if (!input || !input.files[0]) { PM.toast('Pick a file first', 'warning'); return; }
    try {
      await upload(taskId, input.files[0]);
      PM.toast('✓ Uploaded', 'success');
      open(taskId);
    } catch (e) { PM.toast(e.message || 'Failed', 'error'); }
  }

  async function deleteFile(id, taskId) {
    if (!confirm('Delete this file?')) return;
    try {
      await PM.api('DELETE', '/api/pm/tasks/attachments/' + id);
      open(taskId);
    } catch (e) { PM.toast(e.message || 'Failed', 'error'); }
  }

  async function addDependency(taskId) {
    const taskIdStr = prompt('Task ID to link as blocker:');
    if (!taskIdStr) return;
    try {
      await PM.api('POST', '/api/pm/tasks/' + taskId + '/dependencies',
        { dependsOnTaskId: parseInt(taskIdStr, 10), type: 'BLOCKED_BY' });
      PM.toast('✓ Blocker added', 'success');
      open(taskId);
    } catch (e) { PM.toast(e.message || 'Failed', 'error'); }
  }

  async function changeSprint(taskId, sprintIdStr) {
    const sprintId = sprintIdStr ? parseInt(sprintIdStr, 10) : null;
    try {
      if (sprintId) {
        await PM.api('POST', '/api/pm/sprints/' + sprintId + '/tasks/' + taskId);
        PM.toast('✓ Sprint updated', 'success');
      } else {
        const t = await PM.api('GET', '/api/pm/tasks/' + taskId);
        if (t && t.sprintId) {
          await PM.api('DELETE', '/api/pm/sprints/' + t.sprintId + '/tasks/' + taskId);
          PM.toast('✓ Moved to backlog', 'success');
        } else {
          PM.toast('Task is already in the backlog', 'info');
        }
      }
      window.pmRefresh && window.pmRefresh();
    } catch (e) {
      PM.toast(e.message || 'Failed to update sprint', 'error');
    }
  }

  async function changeMilestone(taskId, milestoneIdStr) {
    const milestoneId = milestoneIdStr ? parseInt(milestoneIdStr, 10) : null;
    try {
      if (milestoneId) {
        await PM.api('POST', '/api/pm/milestones/' + milestoneId + '/tasks/' + taskId);
        PM.toast('✓ Milestone updated', 'success');
      } else {
        const t = await PM.api('GET', '/api/pm/tasks/' + taskId);
        if (t && t.milestoneId) {
          await PM.api('DELETE', '/api/pm/milestones/' + t.milestoneId + '/tasks/' + taskId);
          PM.toast('✓ Milestone cleared', 'success');
        } else {
          PM.toast('No milestone assigned', 'info');
        }
      }
      window.pmRefresh && window.pmRefresh();
    } catch (e) {
      PM.toast(e.message || 'Failed to update milestone', 'error');
    }
  }

  async function requestExtension(taskId) {
    const newDueDate = prompt('New due date (YYYY-MM-DD):');
    if (!newDueDate) return;
    const reason = prompt('Reason:') || '';
    const approverIdStr = prompt('Approver employee PK (numeric):');
    if (!approverIdStr) return;
    const approverId = parseInt(approverIdStr, 10);
    if (!approverId) { PM.toast('Approver PK must be a number', 'error'); return; }
    try {
      await PM.api('POST', '/api/pm/approvals', {
        entityType: 'EXTENSION',
        entityId: taskId,
        approverId: approverId,
        reason: reason,
        priority: 'NORMAL',
        payload: JSON.stringify({ newDueDate: newDueDate })
      });
      PM.toast('✓ Extension requested', 'success');
    } catch (e) {
      PM.toast(e.message || 'Failed to request extension', 'error');
    }
  }

  async function requestApproval(taskId) {
    const approverIdStr = prompt('Approver employee PK (numeric):');
    if (!approverIdStr) return;
    const approverId = parseInt(approverIdStr, 10);
    if (!approverId) { PM.toast('Approver PK must be a number', 'error'); return; }
    const reason = prompt('Reason (optional):', '') || null;
    try {
      await PM.api('POST', '/api/pm/approvals', {
        entityType: 'TASK',
        entityId: taskId,
        approverId: approverId,
        reason: reason,
        priority: 'HIGH',
        payload: null
      });
      PM.toast('✓ Approval requested', 'success');
    } catch (e) {
      PM.toast(e.message || 'Failed to request approval', 'error');
    }
  }

  window.PMTask = {
    open, upload,
    changeStatus, addSubtask, addChecklist, toggleCheck,
    postComment, logTime, deleteTime,
    uploadFile, deleteFile, addDependency,
    changeSprint, changeMilestone,
    requestApproval,
    requestExtension
  };
})();