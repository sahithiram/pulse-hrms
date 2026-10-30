/** pm-documents.js  Gap 15: project Documents tab. Needs pm-core.js. Use: PMDocs.load(projectId, 'pmDocsRoot') */
(function () {
  'use strict';
  var S = { pid: null, root: null, cfg: null, rows: [] };

  function fmtSize(b) {
    if (b == null) return '';
    if (b < 1024) return b + ' B';
    if (b < 1048576) return (b / 1024).toFixed(1) + ' KB';
    return (b / 1048576).toFixed(1) + ' MB';
  }
  function ext(n) { var i = (n || '').lastIndexOf('.'); return i < 0 ? '' : n.slice(i + 1).toLowerCase(); }
  function base() { return '/api/pm/projects/' + S.pid + '/documents'; }

  function load(pid, rootId) {
    S.pid = pid; S.root = document.getElementById(rootId);
    if (!S.root) return Promise.resolve();
    S.root.innerHTML = '<div style="padding:20px;text-align:center;color:var(--tx3)"><i class="fas fa-spinner fa-spin"></i></div>';
    return Promise.all([PM.api('GET', base() + '/config'), PM.api('GET', base())])
      .then(function (r) { S.cfg = r[0]; S.rows = r[1] || []; render(); })
      .catch(function (e) { S.root.innerHTML = '<div class="pm-card" style="color:var(--dng)">' + PM.esc(e.message) + '</div>'; });
  }

  function render() {
    var c = S.cfg, h = '';
    if (c.canWrite) {
      h += '<div class="pm-card" id="pdDrop" style="border:2px dashed var(--bd);text-align:center;padding:18px">' +
        '<div style="margin-bottom:8px">Drag files here or</div>' +
        '<div style="display:flex;gap:8px;justify-content:center;flex-wrap:wrap;align-items:center">' +
        '<select class="pm-select" id="pdCat">' + c.categories.map(function (x) { return '<option>' + PM.esc(x) + '</option>'; }).join('') + '</select>' +
        '<input class="pm-input" id="pdTitle" placeholder="Title (optional)" style="min-width:160px">' +
        '<button class="pm-btn pm-btn-primary" id="pdPick">Choose file</button>' +
        '<input type="file" id="pdFile" multiple style="display:none">' +
        '</div>' +
        '<div style="margin-top:10px;display:flex;gap:8px;justify-content:center;flex-wrap:wrap">' +
        '<input class="pm-input" id="pdUrl" placeholder="https://… (add a link instead)" style="min-width:220px">' +
        '<button class="pm-btn" id="pdAddLink">Add link</button></div>' +
        '<div style="font-size:var(--fs-xs);color:var(--tx3);margin-top:8px">Max ' + fmtSize(c.maxBytes) +
        '. Not allowed: ' + c.blockedExtensions.map(function (x) { return '.' + x; }).join(' ') + '</div></div>';
    } else {
      h += '<div class="pm-card" style="font-size:var(--fs-sm);color:var(--tx3)">You can view and download documents. Uploading is not available for viewers or for completed/archived projects.</div>';
    }
    h += '<div class="pm-card" style="padding:0;overflow-x:auto"><table class="pm-table" style="width:100%;min-width:560px"><thead><tr>' +
      '<th>Name</th><th>Type</th><th>Size</th><th>Uploaded by</th><th>Date</th><th></th></tr></thead><tbody>' +
      (S.rows.length ? S.rows.map(row).join('') : '<tr><td colspan="6" style="text-align:center;padding:20px;color:var(--tx3)">No documents yet</td></tr>') +
      '</tbody></table></div>';
    S.root.innerHTML = h;
    if (c.canWrite) wire();
  }

  function row(d) {
    var isLink = d.kind === 'LINK';
    var open = isLink
      ? '<a class="pm-btn pm-btn-sm" href="' + PM.esc(d.linkUrl) + '" target="_blank" rel="noopener noreferrer">Open</a>'
      : '<button class="pm-btn pm-btn-sm" onclick="PMDocs.download(' + d.id + ')">Download</button>';
    var del = (S.cfg.canWrite && (S.cfg.canAdminister || d.uploadedByName === S.cfg.meName))
      ? ' <button class="pm-btn pm-btn-sm pm-btn-icon" title="Delete" onclick="PMDocs.del(' + d.id + ')"><i class="fas fa-trash"></i></button>' : '';
    return '<tr><td><b>' + PM.esc(d.title) + '</b>' + (d.fileName && d.fileName !== d.title ? '<div style="font-size:var(--fs-xs);color:var(--tx3)">' + PM.esc(d.fileName) + '</div>' : '') + '</td>' +
      '<td><span class="pm-badge info">' + PM.esc(d.category) + '</span> ' + (isLink ? 'Link' : 'File') + '</td>' +
      '<td>' + (isLink ? '—' : fmtSize(d.fileSize)) + '</td>' +
      '<td>' + PM.esc(d.uploadedByName || '') + '</td>' +
      '<td>' + (d.uploadedAt ? PM.fmtDate(d.uploadedAt) : '') + '</td>' +
      '<td style="white-space:nowrap">' + open + del + '</td></tr>';
  }

  function wire() {
    var drop = document.getElementById('pdDrop'), fi = document.getElementById('pdFile');
    document.getElementById('pdPick').onclick = function () { fi.click(); };
    fi.onchange = function () { upload(Array.prototype.slice.call(fi.files)); fi.value = ''; };
    ['dragenter', 'dragover'].forEach(function (ev) { drop.addEventListener(ev, function (e) { e.preventDefault(); drop.style.background = 'rgba(80,120,200,.08)'; }); });
    ['dragleave', 'drop'].forEach(function (ev) { drop.addEventListener(ev, function (e) { e.preventDefault(); drop.style.background = ''; }); });
    drop.addEventListener('drop', function (e) { upload(Array.prototype.slice.call(e.dataTransfer.files)); });
    document.getElementById('pdAddLink').onclick = addLink;
  }

  function check(f) {
    if (f.size > S.cfg.maxBytes) return f.name + ': larger than ' + fmtSize(S.cfg.maxBytes);
    if (S.cfg.blockedExtensions.indexOf(ext(f.name)) > -1) return f.name + ': .' + ext(f.name) + ' files are not allowed';
    if (f.size === 0) return f.name + ': file is empty';
    return null;
  }

  async function upload(files) {
    if (!files.length) return;
    var cat = document.getElementById('pdCat').value, title = document.getElementById('pdTitle').value, bad = [], ok = 0;
    for (var i = 0; i < files.length; i++) {
      var f = files[i], err = check(f);
      if (err) { bad.push(err); continue; }
      var fd = new FormData(); fd.append('file', f); fd.append('category', cat);
      if (title && files.length === 1) fd.append('title', title);
      try { await rawPost(base(), fd); ok++; } catch (e) { bad.push(f.name + ': ' + e.message); }
    }
    if (ok) PM.toast(ok + ' file(s) uploaded', 'success');
    if (bad.length) PM.openDrawer('<div class="pm-card"><b>Some files were not uploaded</b><ul style="margin:8px 0 0 16px">' +
      bad.map(function (m) { return '<li style="font-size:var(--fs-xs)">' + PM.esc(m) + '</li>'; }).join('') + '</ul></div>', 'Upload result');
    load(S.pid, S.root.id);
  }

  // multipart: PM.api sets JSON headers, so use fetch here. ADAPT the auth header line to what PM.api uses.
  function rawPost(url, fd) {
    var headers = {};
    if (window.PM && PM.authHeaders) headers = PM.authHeaders();            // ADAPT: remove if you use cookie sessions
    return fetch(url, { method: 'POST', body: fd, headers: headers, credentials: 'same-origin' })
      .then(function (r) { return r.json().catch(function () { return {}; }).then(function (j) {
        if (!r.ok) throw new Error(j.message || ('Upload failed (' + r.status + ')'));
        return j; }); });
  }

  function addLink() {
    var u = document.getElementById('pdUrl').value.trim();
    if (!/^https?:\/\//i.test(u)) { PM.toast('Enter a valid http:// or https:// link', 'warning'); return; }
    PM.api('POST', base() + '/link', { category: 'LINK', title: document.getElementById('pdTitle').value || null, url: u })
      .then(function () { PM.toast('Link added', 'success'); return load(S.pid, S.root.id); })
      .catch(function (e) { PM.toast(e.message, 'error'); });
  }

  function download(id) {
    var headers = (window.PM && PM.authHeaders) ? PM.authHeaders() : {};    // ADAPT as above
    fetch(base() + '/' + id + '/download', { headers: headers, credentials: 'same-origin' })
      .then(function (r) { if (!r.ok) throw new Error('Download failed (' + r.status + ')'); 
        var cd = r.headers.get('Content-Disposition') || '', m = /filename\*=UTF-8''([^;]+)/i.exec(cd);
        return r.blob().then(function (b) { return { b: b, name: m ? decodeURIComponent(m[1]) : 'document' }; }); })
      .then(function (x) { var a = document.createElement('a'); a.href = URL.createObjectURL(x.b); a.download = x.name;
        document.body.appendChild(a); a.click(); setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 500); })
      .catch(function (e) { PM.toast(e.message, 'error'); });
  }

  function del(id) {
    if (!window.confirm('Delete this document?')) return;
    PM.api('DELETE', base() + '/' + id)
      .then(function () { PM.toast('Deleted', 'success'); return load(S.pid, S.root.id); })
      .catch(function (e) { PM.toast(e.message, 'error'); });
  }

  window.PMDocs = { load: load, download: download, del: del };
})();