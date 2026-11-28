/* =====================================================================
   MENTI HRMS — SHARED SIDEBAR CONTROLLER  (hardened v2)
   Accordion groups + live search + desktop icon rail (the « / » double
   arrow) + mobile collapse.
   Shared by every page (HR fragment, HR dashboard inline, PM pages).

   Expects the shared markup contract:
     #sidebar, #sideNav, .side-group > .side-group-toggle + .side-submenu,
     #sideNavSearch, #sidebarRailToggle, #sidebarRailIcon,
     #mobileMenuToggle, #sideNavEmpty

   What changed vs v1 (why clicks sometimes did nothing):
   - Safe to load more than once (12-team-lead used to load it twice, so
     every click ran twice and cancelled itself: open -> close, « -> »).
   - Uses event delegation, so it keeps working if another script
     re-renders / clones sidebar nodes (PM team-lead mode injects links).
   - Never depends on scrollHeight being measurable at load time
     (submenus no longer stay collapsed with max-height:0).
   - The double arrow works even before / without the other page scripts.
   ===================================================================== */
(function () {
    'use strict';

    if (window.__mentiSidebarLoaded) return;   // idempotent
    window.__mentiSidebarLoaded = true;

    var RAIL_KEY = 'menti.sidebar.rail';
    var OPEN_KEY = 'menti.sidebar.group';

    function lsGet(k) { try { return window.localStorage.getItem(k); } catch (e) { return null; } }
    function lsSet(k, v) { try { window.localStorage.setItem(k, v); } catch (e) { /* noop */ } }

    function isDesktop() { return !window.matchMedia('(max-width: 768px)').matches; }

    function init() {
        var nav = document.getElementById('sideNav');
        var sidebar = document.getElementById('sidebar');
        if (!nav || !sidebar) return;
        if (nav.__mentiSidebarBound) return;
        nav.__mentiSidebarBound = true;

        var searchInput = document.getElementById('sideNavSearch');
        var emptyState = document.getElementById('sideNavEmpty');
        var railToggle = document.getElementById('sidebarRailToggle');
        var railIcon = document.getElementById('sidebarRailIcon');
        var mobileToggle = document.getElementById('mobileMenuToggle');

        /* groups are re-queried every time: other scripts may add links/groups */
        function groups() { return Array.prototype.slice.call(nav.querySelectorAll('.side-group')); }
        function submenuOf(g) { return g.querySelector('.side-submenu'); }
        function toggleOf(g) { return g.querySelector('.side-group-toggle'); }

        /* ---------- accordion ---------- */
        function openGroup(g) {
            var sm = submenuOf(g);
            if (!sm) return;
            g.classList.add('is-open');
            var h = sm.scrollHeight;
            sm.style.maxHeight = (h > 0 ? h : 1000) + 'px';
            var t = toggleOf(g);
            if (t) t.setAttribute('aria-expanded', 'true');
            // after the animation let the panel grow freely (search / injected links)
            window.setTimeout(function () {
                if (g.classList.contains('is-open')) sm.style.maxHeight = 'none';
            }, 320);
        }

        function closeGroup(g) {
            var sm = submenuOf(g);
            if (!sm) return;
            // from 'none' we must go back to a pixel value so the collapse animates
            sm.style.maxHeight = sm.scrollHeight + 'px';
            void sm.offsetHeight;
            g.classList.remove('is-open');
            sm.style.maxHeight = '0px';
            var t = toggleOf(g);
            if (t) t.setAttribute('aria-expanded', 'false');
        }

        function closeAll(except) {
            groups().forEach(function (g) { if (g !== except) closeGroup(g); });
        }

        function markActive() {
            groups().forEach(function (g) {
                g.classList.toggle('has-active', !!g.querySelector('.nav-link.active'));
            });
        }

        function groupKey(g) { return (g && g.getAttribute('data-group')) || ''; }

        function expandActiveGroup() {
            var active = nav.querySelector('.nav-link.active');
            var g = active ? active.closest('.side-group') : null;
            if (!g) {
                var saved = lsGet(OPEN_KEY);
                if (saved) g = nav.querySelector('.side-group[data-group="' + saved + '"]');
            }
            if (!g) g = groups()[0];
            if (g) { closeAll(g); openGroup(g); }
        }

        /* mark the link that matches the current URL when the page did not */
        (function markByUrl() {
            if (nav.querySelector('.nav-link.active')) return;
            var path = window.location.pathname.replace(/\/+$/, '');
            nav.querySelectorAll('.nav-link').forEach(function (a) {
                var href = (a.getAttribute('href') || '').split('?')[0].split('#')[0].replace(/\/+$/, '');
                if (href && href.indexOf('javascript') !== 0 && href === path) a.classList.add('active');
            });
        })();

        /* ---------- delegated clicks inside the nav ---------- */
        nav.addEventListener('click', function (e) {
            var toggle = e.target.closest('.side-group-toggle');
            if (toggle && nav.contains(toggle)) {
                var g = toggle.closest('.side-group');
                if (!g) return;
                e.preventDefault();

                if (document.body.classList.contains('sidebar-rail') && isDesktop()) {
                    setRail(false);            // clicking a group icon expands the rail first
                    closeAll(g);
                    openGroup(g);
                    lsSet(OPEN_KEY, groupKey(g));
                    return;
                }
                if (g.classList.contains('is-open')) {
                    closeGroup(g);
                } else {
                    closeAll(g);
                    openGroup(g);
                    lsSet(OPEN_KEY, groupKey(g));
                }
                return;
            }

            var link = e.target.closest('.nav-link');
            if (link && nav.contains(link)) {
                var href = link.getAttribute('href') || '';
                if (href.indexOf('javascript') === 0 || href === '#' || href === '') {
                    nav.querySelectorAll('.nav-link.active').forEach(function (el) { el.classList.remove('active'); });
                    link.classList.add('active');
                    markActive();
                }
                var owner = link.closest('.side-group');
                if (owner) lsSet(OPEN_KEY, groupKey(owner));
                if (!isDesktop()) sidebar.classList.remove('active');   // mobile: close after click
            }
        });

        expandActiveGroup();
        markActive();

        /* ---------- search ---------- */
        if (searchInput) {
            searchInput.addEventListener('input', function () {
                var term = this.value.trim().toLowerCase();
                var list = groups();

                list.forEach(function (g) {
                    var items = Array.prototype.slice.call(g.querySelectorAll('.side-submenu > li'));
                    var label = ((g.querySelector('.side-group-label') || {}).textContent || '').toLowerCase();
                    var groupMatches = label.indexOf(term) !== -1;
                    var visible = 0;
                    items.forEach(function (li) {
                        var ok = !term || groupMatches || li.textContent.toLowerCase().indexOf(term) !== -1;
                        li.hidden = !ok;
                        if (ok) visible += 1;
                    });
                    g.hidden = !!term && visible === 0;
                    if (term && visible > 0) openGroup(g);
                });

                if (!term) { closeAll(null); expandActiveGroup(); markActive(); }
                if (emptyState) emptyState.hidden = list.some(function (g) { return !g.hidden; });
            });
        }

        /* ---------- icon rail  («  /  ») ---------- */
        function setRail(isRail) {
            document.body.classList.toggle('sidebar-rail', !!isRail);
            lsSet(RAIL_KEY, isRail ? '1' : '0');
            if (railToggle) {
                railToggle.setAttribute('aria-label', isRail ? 'Expand sidebar' : 'Collapse sidebar to icon rail');
                railToggle.setAttribute('title', isRail ? 'Expand sidebar' : 'Collapse sidebar');
                railToggle.setAttribute('aria-pressed', isRail ? 'true' : 'false');
            }
            if (railIcon) railIcon.className = isRail ? 'fas fa-angles-right' : 'fas fa-angles-left';
            if (!isRail) {
                // submenus were display:none while in rail mode — re-measure
                window.setTimeout(function () {
                    var open = nav.querySelector('.side-group.is-open');
                    if (open) { var sm = submenuOf(open); if (sm) sm.style.maxHeight = 'none'; }
                    else expandActiveGroup();
                }, 30);
            }
        }

        setRail(lsGet(RAIL_KEY) === '1' && isDesktop());

        if (railToggle) {
            railToggle.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                setRail(!document.body.classList.contains('sidebar-rail'));
            });
        }

        /* ---------- mobile ---------- */
        if (mobileToggle) {
            mobileToggle.addEventListener('click', function (e) {
                e.stopPropagation();
                sidebar.classList.toggle('active');
                if (sidebar.classList.contains('active') && !nav.querySelector('.side-group.is-open')) expandActiveGroup();
            });
        }
        document.addEventListener('click', function (e) {
            if (!sidebar.classList.contains('active')) return;
            if (sidebar.contains(e.target)) return;
            if (mobileToggle && mobileToggle.contains(e.target)) return;
            sidebar.classList.remove('active');
        });

        /* ---------- resize ---------- */
        window.addEventListener('resize', function () {
            if (!isDesktop()) document.body.classList.remove('sidebar-rail');
            else if (lsGet(RAIL_KEY) === '1') document.body.classList.add('sidebar-rail');
        });

        /* ---------- keep the open group right after late DOM changes ---------- */
        if (window.MutationObserver) {
            var t = null;
            new MutationObserver(function () {
                window.clearTimeout(t);
                t = window.setTimeout(function () {
                    markActive();
                    if (!nav.querySelector('.side-group.is-open') && !document.body.classList.contains('sidebar-rail')) {
                        expandActiveGroup();
                    }
                }, 60);
            }).observe(nav, { childList: true, subtree: true });
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
