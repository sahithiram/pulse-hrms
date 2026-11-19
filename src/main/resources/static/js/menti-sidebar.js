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

    if (window.__mentiSidebarControllerLoaded) {
        return;
    }
    window.__mentiSidebarControllerLoaded = true;
    window.__mentiSidebarV3 = true;

    var STORAGE_KEY = 'menti.sidebar.rail';

    function normalizePath(path) {
        if (!path) return '';
        return path.length > 1 ? path.replace(/\/+$/, '') : path;
    }

    function parseHref(href) {
        if (!href) return { isJs: false, path: '', hash: '' };
        if (href.indexOf('javascript') === 0) {
            return { isJs: true, path: '', hash: '' };
        }
        var hashIdx = href.indexOf('#');
        var rawPath = hashIdx === -1 ? href : href.slice(0, hashIdx);
        var rawHash = hashIdx === -1 ? '' : href.slice(hashIdx);
        var qIdx = rawPath.indexOf('?');
        if (qIdx !== -1) rawPath = rawPath.slice(0, qIdx);
        return {
            isJs: false,
            path: normalizePath(rawPath),
            hash: rawHash
        };
    }

    function initSidebarNav() {
        var nav = document.getElementById('sideNav');
        var sidebar = document.getElementById('sidebar');
        if (!nav || !sidebar) return;
        if (nav.dataset.mentiSidebarInit === '1') return;
        nav.dataset.mentiSidebarInit = '1';

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
            var h = submenu.scrollHeight;
            submenu.style.maxHeight = (h > 0 ? h : 600) + 'px';
            var toggle = group.querySelector('.side-group-toggle');
            if (toggle) toggle.setAttribute('aria-expanded', 'true');
            window.setTimeout(function () {
                if (group.classList.contains('is-open')) {
                    var h2 = submenu.scrollHeight;
                    submenu.style.maxHeight = (h2 > 0 ? h2 : 600) + 'px';
                }
            }, 300);
        }

        function closeGroup(group) {
            var submenu = submenuOf(group);
            if (!submenu) return;
            var toggle = group.querySelector('.side-group-toggle');
            if (!group.classList.contains('is-open')) {
                submenu.style.maxHeight = '0px';
                if (toggle) toggle.setAttribute('aria-expanded', 'false');
                return;
            }
            submenu.style.maxHeight = submenu.scrollHeight + 'px';
            void submenu.offsetHeight; // force reflow so collapse animates
            group.classList.remove('is-open');
            submenu.style.maxHeight = '0px';
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

        function expandActiveGroup() {
            var active = nav.querySelector('.nav-link.active');
            var group = active ? active.closest('.side-group') : null;
            if (!group) group = groups[0];
            if (group) {
                closeAll(group);
                openGroup(group);
            }
        }

        function setActiveLink(targetLink) {
            if (!targetLink) return;
            nav.querySelectorAll('.nav-link.active').forEach(function (el) {
                el.classList.remove('active');
            });
            targetLink.classList.add('active');
            markActive();
            var group = targetLink.closest('.side-group');
            if (group && !group.classList.contains('is-open')) {
                closeAll(group);
                openGroup(group);
            }
        }

        function syncActiveByLocation() {
            var currentPath = normalizePath(window.location.pathname);
            var currentHash = window.location.hash || '';
            var search = window.location.search || '';
            if (!currentHash && /[?&]showAttendance=true(?:&|$)/i.test(search)) {
                currentHash = '#attendance';
            }
            var links = Array.prototype.slice.call(nav.querySelectorAll('.nav-link'));
            var matched = null;

            // 1. If a URL hash is present (e.g. /dashboard/hr#attendance or ?showAttendance=true), match hash
            if (currentHash) {
                for (var i = 0; i < links.length; i++) {
                    var parsed = parseHref(links[i].getAttribute('href') || '');
                    if (!parsed.isJs && parsed.hash === currentHash &&
                        (parsed.path === '' || parsed.path === currentPath || /[?&]showAttendance=true/i.test(search))) {
                        matched = links[i];
                        break;
                    }
                }
            }

            // 2. Otherwise match exact pathname without hash (e.g. /dashboard/hr -> Overview)
            if (!matched) {
                for (var j = 0; j < links.length; j++) {
                    var p = parseHref(links[j].getAttribute('href') || '');
                    if (!p.isJs && !p.hash && p.path && p.path === currentPath) {
                        matched = links[j];
                        break;
                    }
                }
            }

            if (matched) {
                setActiveLink(matched);
            } else {
                expandActiveGroup();
                markActive();
            }
        }

        // Capture-phase delegation guarantees ONE click always toggles cleanly,
        // even if an older cached script attached duplicate listeners to .side-group-toggle.
        if (!window.__mentiSidebarToggleDelegated) {
            window.__mentiSidebarToggleDelegated = true;
            document.addEventListener('click', function (e) {
                var toggle = e.target && e.target.closest ? e.target.closest('#sideNav .side-group-toggle') : null;
                if (!toggle) return;
                var group = toggle.closest('.side-group');
                if (!group) return;
                e.preventDefault();
                e.stopPropagation();
                if (typeof e.stopImmediatePropagation === 'function') {
                    e.stopImmediatePropagation();
                }
                if (document.body.classList.contains('sidebar-rail')) {
                    document.body.classList.remove('sidebar-rail');
                    persistRail(false);
                    closeAll(group);
                    openGroup(group);
                    return;
                }
                if (group.classList.contains('is-open')) {
                    closeGroup(group);
                } else {
                    closeAll(group);
                    openGroup(group);
                }
            }, true);
        }

        groups.forEach(function (group) {
            group.querySelectorAll('.nav-link').forEach(function (link) {
                link.addEventListener('click', function (e) {
                    var wasPrevented = !!e.defaultPrevented;
                    // Let the page's own handlers run first (they may preventDefault).
                    window.setTimeout(function () {
                        var href = link.getAttribute('href') || '';
                        var parsed = parseHref(href);
                        var currentPath = normalizePath(window.location.pathname);
                        var isSamePageHash = !parsed.isJs && !!parsed.hash &&
                            (parsed.path === '' || parsed.path === currentPath);

                        if (parsed.isJs || isSamePageHash || wasPrevented || e.defaultPrevented) {
                            setActiveLink(link);
                        }
                        // On mobile, close the sidebar after a click.
                        if (window.matchMedia('(max-width: 768px)').matches) {
                            sidebar.classList.remove('active');
                        }
                    }, 0);
                });
            });
        });

        // Sync active item & group with pathname + hash on load and on hash changes.
        syncActiveByLocation();
        window.addEventListener('hashchange', syncActiveByLocation);

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
