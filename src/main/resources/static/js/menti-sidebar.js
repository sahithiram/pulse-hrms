/* =====================================================================
   MENTI HRMS — SHARED SIDEBAR CONTROLLER
   Accordion groups + live search + desktop icon rail + mobile collapse.
   Shared by every page (HR fragment, HR dashboard inline, PM inline).
   Expects the shared markup contract:
     #sidebar, #sideNav, .side-group > .side-group-toggle + .side-submenu,
     #sideNavSearch, #sidebarRailToggle, #mobileMenuToggle, #sideNavEmpty
   ===================================================================== */
(function () {
    'use strict';

    var STORAGE_KEY = 'menti.sidebar.rail';

    function initSidebarNav() {
        var nav = document.getElementById('sideNav');
        var sidebar = document.getElementById('sidebar');
        if (!nav || !sidebar) return;

        var groups = Array.prototype.slice.call(nav.querySelectorAll('.side-group'));
        var searchInput = document.getElementById('sideNavSearch');
        var emptyState = document.getElementById('sideNavEmpty');
        var railToggle = document.getElementById('sidebarRailToggle');
        var railIcon = document.getElementById('sidebarRailIcon');
        var mobileToggle = document.getElementById('mobileMenuToggle');

        /* ---------- accordion ---------- */
        function submenuOf(group) { return group.querySelector('.side-submenu'); }

        function openGroup(group) {
            var submenu = submenuOf(group);
            if (!submenu) return;
            group.classList.add('is-open');
            submenu.style.maxHeight = submenu.scrollHeight + 'px';
            var toggle = group.querySelector('.side-group-toggle');
            if (toggle) toggle.setAttribute('aria-expanded', 'true');
            window.setTimeout(function () {
                if (group.classList.contains('is-open')) {
                    submenu.style.maxHeight = submenu.scrollHeight + 'px';
                }
            }, 300);
        }

        function closeGroup(group) {
            var submenu = submenuOf(group);
            if (!submenu) return;
            submenu.style.maxHeight = submenu.scrollHeight + 'px';
            void submenu.offsetHeight; // force reflow so collapse animates
            group.classList.remove('is-open');
            submenu.style.maxHeight = '0px';
            var toggle = group.querySelector('.side-group-toggle');
            if (toggle) toggle.setAttribute('aria-expanded', 'false');
        }

        function closeAll(except) {
            groups.forEach(function (group) {
                if (group !== except) closeGroup(group);
            });
        }

        function markActive() {
            groups.forEach(function (group) {
                group.classList.toggle('has-active', !!group.querySelector('.nav-link.active'));
            });
        }

        groups.forEach(function (group) {
            var toggle = group.querySelector('.side-group-toggle');
            if (!toggle) return;

            toggle.addEventListener('click', function () {
                if (document.body.classList.contains('sidebar-rail')) {
                    document.body.classList.remove('sidebar-rail');
                    persistRail(false);
                    openGroup(group);
                    return;
                }
                if (group.classList.contains('is-open')) {
                    closeGroup(group);
                } else {
                    closeAll(group);
                    openGroup(group);
                }
            });

            group.querySelectorAll('.nav-link').forEach(function (link) {
                link.addEventListener('click', function () {
                    // Let the page's own handlers run first (they may preventDefault).
                    window.setTimeout(function () {
                        var href = link.getAttribute('href') || '';
                        // Only force active styling for in-page (javascript:) links.
                        if (href.indexOf('javascript') === 0) {
                            nav.querySelectorAll('.nav-link.active').forEach(function (el) {
                                el.classList.remove('active');
                            });
                            link.classList.add('active');
                            markActive();
                        }
                        // On mobile, close the sidebar after a click.
                        if (window.matchMedia('(max-width: 768px)').matches) {
                            sidebar.classList.remove('active');
                        }
                    }, 0);
                });
            });
        });

        // If a page uses pathname-based active (rare), mark it too.
        nav.querySelectorAll('.nav-link').forEach(function (link) {
            var href = link.getAttribute('href');
            if (href && href !== 'javascript:void(0)' && href === window.location.pathname) {
                nav.querySelectorAll('.nav-link.active').forEach(function (el) {
                    el.classList.remove('active');
                });
                link.classList.add('active');
            }
        });

        // Open the group that owns the active link on load.
        function expandActiveGroup() {
            var active = nav.querySelector('.nav-link.active');
            var group = active ? active.closest('.side-group') : null;
            if (!group) group = groups[0];
            if (group) {
                closeAll(group);
                openGroup(group);
            }
        }

        expandActiveGroup();
        markActive();

        /* ---------- search ---------- */
        if (searchInput) {
            searchInput.addEventListener('input', function () {
                var term = this.value.trim().toLowerCase();

                groups.forEach(function (group) {
                    var items = Array.prototype.slice.call(group.querySelectorAll('.side-submenu > li'));
                    var groupLabel = (group.querySelector('.side-group-label') || {}).textContent || '';
                    var groupMatches = groupLabel.toLowerCase().indexOf(term) !== -1;
                    var visibleCount = 0;

                    items.forEach(function (item) {
                        var text = item.textContent.toLowerCase();
                        var match = !term || groupMatches || text.indexOf(term) !== -1;
                        item.hidden = !match;
                        if (match) visibleCount += 1;
                    });

                    group.hidden = !!term && visibleCount === 0;

                    if (term && visibleCount > 0) {
                        closeAll(group);
                        openGroup(group);
                    } else if (!term) {
                        closeAll(null);
                        expandActiveGroup();
                    }
                });

                if (emptyState) {
                    var any = groups.some(function (group) { return !group.hidden; });
                    emptyState.hidden = any;
                }

                if (!term) markActive();
            });
        }

        /* ---------- icon rail (desktop) ---------- */
        function persistRail(isRail) {
            try { window.localStorage.setItem(STORAGE_KEY, isRail ? '1' : '0'); } catch (err) { /* noop */ }
        }

        function setRail(isRail) {
            document.body.classList.toggle('sidebar-rail', isRail);
            if (railToggle) {
                railToggle.setAttribute('aria-label', isRail ? 'Expand sidebar' : 'Collapse sidebar to icon rail');
                railToggle.setAttribute('title', isRail ? 'Expand sidebar' : 'Collapse sidebar');
            }
            if (railIcon) {
                railIcon.className = isRail ? 'fas fa-angles-right' : 'fas fa-angles-left';
            }
        }

        if (railToggle) {
            try { setRail(window.localStorage.getItem(STORAGE_KEY) === '1'); }
            catch (err) { setRail(false); }

            railToggle.addEventListener('click', function () {
                var next = !document.body.classList.contains('sidebar-rail');
                setRail(next);
                persistRail(next);
                if (!next) expandActiveGroup();
            });
        }

        /* ---------- mobile ---------- */
        if (mobileToggle) {
            mobileToggle.addEventListener('click', function () {
                sidebar.classList.toggle('active');
                window.setTimeout(function () {
                    var open = nav.querySelector('.side-group.is-open');
                    if (sidebar.classList.contains('active') && !open) expandActiveGroup();
                }, 0);
            });
            document.addEventListener('click', function (e) {
                if (sidebar.classList.contains('active') &&
                    !sidebar.contains(e.target) && e.target !== mobileToggle) {
                    sidebar.classList.remove('active');
                }
            });
        }

        // Keep open panels sized correctly on resize.
        window.addEventListener('resize', function () {
            nav.querySelectorAll('.side-group.is-open .side-submenu').forEach(function (submenu) {
                submenu.style.maxHeight = submenu.scrollHeight + 'px';
            });
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initSidebarNav);
    } else {
        initSidebarNav();
    }
})();