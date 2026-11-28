/* =====================================================================
   PM TAB GUARD  —  makes every PM tab / inner tab / view-switcher clickable
   from the very first paint.

   WHY THIS EXISTS
   Every /pm page declares its tab functions (switchTab, switchHome,
   switchInboxTab, switchView, setView, setCreateMode …) INSIDE
   PM.bootstrap().then(...). Until /api/pm/me answers (or if it is slow /
   fails / the route guard stops the page) those names do not exist, so
   the inline onclick="switchTab('x',this)" on the tab buttons throws
   "switchTab is not defined" and the tab looks dead.

   HOW IT WORKS
   Load this file BEFORE the page scripts (right after menti-sidebar.js).
   It installs a tiny accessor for each name:
     - before the page assigns window.switchTab = ... a click gets a stub
       that already highlights the tab / shows its pane, and remembers the
       request;
     - when the page assigns the real function, it is used from then on and
       the remembered click is replayed (so the user's click wins over the
       initial ?tab= deep link);
     - every real call is wrapped in try/catch: if the page's own handler
       throws half-way (e.g. a missing title element) the tab still switches.
   It does not change any page logic.
   ===================================================================== */
(function () {
    'use strict';
    if (window.__pmTabsGuard) return;
    window.__pmTabsGuard = true;

    var TAB_FUNCS = ['switchTab', 'switchHome', 'switchInboxTab'];          // (tab, btn)
    var OTHER_FUNCS = ['switchView', 'setView', 'setCreateMode'];           // (mode, btn)
    var ALL = TAB_FUNCS.concat(OTHER_FUNCS);

    var BAR = '#pmTabs, #tabs, .pm-tabs';

    function visualTab(tab, el) {
        try {
            var bars = document.querySelectorAll(BAR);
            Array.prototype.forEach.call(bars, function (bar) {
                var btns = bar.querySelectorAll('[data-tab]');
                if (!btns.length) return;
                var hit = false;
                Array.prototype.forEach.call(btns, function (b) { if (b.getAttribute('data-tab') === tab) hit = true; });
                if (!hit) return;
                Array.prototype.forEach.call(btns, function (b) {
                    b.classList.toggle('active', b.getAttribute('data-tab') === tab);
                });
            });
            var pane = document.getElementById('pane-' + tab);
            if (pane) {
                Array.prototype.forEach.call(document.querySelectorAll('.pm-pane'), function (p) {
                    p.classList.toggle('active', p === pane);
                });
            }
        } catch (e) { /* cosmetic only */ }
    }

    function visualMode(el) {
        // view / mode buttons: highlight the clicked one inside its own group
        try {
            if (!el || !el.parentNode) return;
            Array.prototype.forEach.call(el.parentNode.children, function (s) { s.classList.remove('active'); });
            el.classList.add('active');
        } catch (e) { /* cosmetic only */ }
    }

    function fallback(name, args) {
        if (TAB_FUNCS.indexOf(name) >= 0) {
            if (typeof args[0] === 'string') visualTab(args[0], args[1]);
        } else {
            visualMode(args[1]);
        }
    }

    ALL.forEach(function (name) {
        var real = null;
        var wrapped = null;
        var pending = null;
        var lock = null;      // the user's own click made before the page was ready

        function stub() {
            pending = arguments;
            if (TAB_FUNCS.indexOf(name) >= 0 && typeof arguments[0] === 'string') window.__pmUserTab = arguments[0];
            fallback(name, arguments);
        }

        function fromUserClick() {
            var ev = window.event;
            return !!(ev && (ev.type === 'click' || ev.type === 'keydown' || ev.type === 'keyup'));
        }

        function wrap(fn) {
            return function () {
                var args = arguments;
                // Page start-up code (default tab / ?tab= deep link) runs AFTER a
                // click the user already made while the page was loading. Let the
                // user's choice win for a few seconds.
                if (lock && Date.now() < lock.until && !fromUserClick() && TAB_FUNCS.indexOf(name) >= 0) {
                    if (args[0] !== lock.args[0]) args = lock.args;
                } else if (lock && fromUserClick()) {
                    lock = null;
                }
                try {
                    return fn.apply(this, args);
                } catch (err) {
                    if (window.console) console.error('[pm-tabs] ' + name + ' failed:', err);
                    fallback(name, args);
                }
            };
        }

        try {
            Object.defineProperty(window, name, {
                configurable: true,
                enumerable: true,
                get: function () { return wrapped || stub; },
                set: function (fn) {
                    if (typeof fn !== 'function') { real = wrapped = null; return; }
                    real = fn;
                    wrapped = wrap(fn);
                    if (pending) {
                        var a = pending;
                        pending = null;
                        lock = { args: a, until: Date.now() + 6000 };
                        window.setTimeout(function () { wrapped.apply(window, a); }, 0);
                    }
                }
            });
        } catch (e) { /* if the browser refuses, pages keep their old behaviour */ }
    });

})();
