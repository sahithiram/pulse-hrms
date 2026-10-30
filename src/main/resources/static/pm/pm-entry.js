/* /pm/pm-entry.js: adds Project Management entry points to HRMS pages.
   Shows only what /api/pm/me says this person may use. Safe to include on any page. */
(function () {
  if (window.__pmEntryLoaded) return;
  window.__pmEntryLoaded = true;

  function ready(fn) {
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', fn);
    else fn();
  }

  ready(async function () {
    var me;
    try {
      var r = await fetch('/api/pm/me', { credentials: 'same-origin', headers: { 'Accept': 'application/json' } });
      if (!r.ok) return;
      var j = await r.json();
      me = (j && j.data) ? j.data : j;
    } catch (e) { return; }
    if (!me) return;

    var items = [];
    if (me.admin || me.pm) items.push({ icon: 'fa-diagram-project', label: 'Project Management', href: '/pm/01-home' });
    items.push({ icon: 'fa-list-check', label: 'My Projects & Tasks', href: '/pm/03-my-work' });
    if (Array.isArray(me.leadOfTeamIds) && me.leadOfTeamIds.length) {
      items.push({ icon: 'fa-user-tie', label: 'Team Lead Dashboard', href: '/pm/12-team-lead' });
    }

    if (document.getElementById('pm-entry-section')) return;

    // 1) Inject into the HRMS sidebar if there is one
    var nav = document.querySelector('.sidebar-content nav') || document.querySelector('.sidebar nav');
    if (nav) {
      var wrap = document.createElement('div');
      wrap.id = 'pm-entry-section';
      wrap.innerHTML =
        '<div class="nav-section"><i class="fas fa-diagram-project"></i> PROJECTS</div>' +
        '<ul class="nav flex-column">' +
        items.map(function (i) {
          return '<li class="nav-item"><a class="nav-link" href="' + i.href + '"><i class="fas ' + i.icon + '"></i> <span>' + i.label + '</span></a></li>';
        }).join('') +
        '</ul>';
      nav.appendChild(wrap);
      return;
    }

    // 2) Fallback: floating pill buttons (page has no recognisable sidebar)
    var box = document.createElement('div');
    box.id = 'pm-entry-section';
    box.style.cssText = 'position:fixed;right:16px;bottom:16px;z-index:2000;display:flex;flex-direction:column;gap:8px;align-items:flex-end';
    box.innerHTML = items.map(function (i) {
      return '<a href="' + i.href + '" style="background:#6366f1;color:#fff;padding:8px 14px;border-radius:20px;font:600 13px Inter,sans-serif;text-decoration:none;box-shadow:0 4px 14px rgba(0,0,0,.35)"><i class="fas ' + i.icon + '"></i> ' + i.label + '</a>';
    }).join('');
    document.body.appendChild(box);
  });
})();