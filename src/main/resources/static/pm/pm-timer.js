/**
 * pm-timer.js — Gap 20 UI. Sticky timer bar on every PM page + PMTimer.start(taskId).
 * Loaded by pm-core.js. Elapsed time = server clock - started_at (never a counter).
 */
(function () {
  'use strict';
  if (window.PMTimer || !window.PM || !PM.api) return;

  var S = { t: null, skew: 0, tick: null, poll: null };

  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function clock(ms) {
    var s = Math.max(0, Math.floor(ms / 1000));
    return pad(Math.floor(s / 3600)) + ':' + pad(Math.floor((s % 3600) / 60)) + ':' + pad(s % 60);
  }
  function fmtMin(m) {
    m = m || 0; var h = Math.floor(m / 60), r = m % 60;
    return h ? (r ? h + 'h ' + r + 'm' : h + 'h') : r + 'm';
  }
  function nowMs() { return Date.now() + S.skew; }

  function css() {
    if (document.getElementById('pmTimerCss')) return;
    var st = document.createElement('style');
    st.id = 'pmTimerCss';
    st.textContent =
      '#pmTimerBar{position:fixed;left:0;right:0;bottom:0;z-index:1500;display:none;align-items:center;gap:8px;box-sizing:border-box;max-width:100vw;' +
      'padding:8px 12px calc(8px + env(safe-area-inset-bottom,0px));background:var(--bg2,var(--dark-card,#1b2538));' +
      'border-top:2px solid var(--pri,var(--primary,#6366f1));color:var(--tx,var(--dark-text,#e5e7eb))}' +
      '#pmTimerBar.on{display:flex}' +
      '.pmtb-dot{width:10px;height:10px;border-radius:50%;background:#ef4444;flex:none;animation:pmtbPulse 1.4s infinite}' +
      '@keyframes pmtbPulse{50%{opacity:.25}}' +
      '.pmtb-main{flex:1;min-width:0}' +
      '.pmtb-title{white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-weight:600;cursor:pointer}' +
      '.pmtb-sub{font-size:12px;opacity:.7;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}' +
      '.pmtb-time{font-variant-numeric:tabular-nums;font-weight:700;font-size:18px;flex:none}' +
      '.pmtb-btn{min-height:44px;min-width:44px;border-radius:8px;border:1px solid var(--bd,var(--dark-border,#334155));' +
      'background:transparent;color:inherit;cursor:pointer;font:inherit;padding:0 12px;flex:none}' +
      '.pmtb-stop{background:#ef4444;border-color:transparent;color:#fff;font-weight:700}' +
      'body.pm-has-timer{padding-bottom:calc(68px + env(safe-area-inset-bottom,0px))}' +
      '.pm-timer-start{min-height:44px;min-width:44px}' +
      '@media (max-width:420px){.pmtb-lbl{display:none}.pmtb-time{font-size:16px}}';
    document.head.appendChild(st);
  }

  function bar() {
    var b = document.getElementById('pmTimerBar');
    if (b) return b;
    b = document.createElement('div');
    b.id = 'pmTimerBar';
    b.setAttribute('role', 'status');
    b.innerHTML =
      '<span class="pmtb-dot"></span>' +
      '<div class="pmtb-main"><div class="pmtb-title" id="pmtbTitle"></div><div class="pmtb-sub" id="pmtbSub"></div></div>' +
      '<span class="pmtb-time" id="pmtbTime">00:00:00</span>' +
      '<button class="pmtb-btn pmtb-stop" id="pmtbStop" aria-label="Stop timer"><i class="fas fa-stop"></i><span class="pmtb-lbl"> Stop</span></button>' +
      '<button class="pmtb-btn" id="pmtbDiscard" aria-label="Discard timer"><i class="fas fa-trash"></i><span class="pmtb-lbl"> Discard</span></button>';
    document.body.appendChild(b);
    document.getElementById('pmtbStop').onclick = function () { stop(false); };
    document.getElementById('pmtbDiscard').onclick = discard;
    document.getElementById('pmtbTitle').onclick = function () {
      if (S.t && window.PMTask && PMTask.open) PMTask.open(S.t.taskId);
    };
    return b;
  }

  function paint() {
    var el = document.getElementById('pmtbTime');
    if (el && S.t) el.textContent = clock(nowMs() - S.t.startedAtMs);
  }
  function startTick() { if (!S.tick) S.tick = setInterval(paint, 1000); }
  function stopTick() { if (S.tick) { clearInterval(S.tick); S.tick = null; } }

  function render() {
    var b = bar(), t = S.t;
    if (!t) {
      b.classList.remove('on'); document.body.classList.remove('pm-has-timer'); stopTick(); return;
    }
    b.classList.add('on'); document.body.classList.add('pm-has-timer');
    document.getElementById('pmtbTitle').textContent = t.taskTitle || ('Task #' + t.taskId);
    document.getElementById('pmtbSub').textContent = (t.projectName || '') + (t.billable ? '' : ' · non-billable');
    paint(); startTick();
  }

  function setFrom(t) {
    if (t && t.serverNowMs) S.skew = t.serverNowMs - Date.now();
    S.t = t && t.running ? t : null;
    render();
  }

  function sync() {
    return PM.api('GET', '/api/pm/timer/current').then(function (t) { setFrom(t); return S.t; })
      .catch(function () { /* logged out or offline: keep what is shown */ });
  }

  function refreshPages() { window.dispatchEvent(new CustomEvent('pm:event')); } // pm-timesheet reloads on this

  function start(taskId, opts) {
    opts = opts || {};
    return PM.api('POST', '/api/pm/timer/start', { taskId: +taskId, note: opts.note || null, billable: opts.billable !== false })
      .then(function (t) { setFrom(t); PM.toast('Timer started', 'success'); })
      .catch(function (e) {
        var m = (e && e.message) || '';
        if (/already running/i.test(m)) {
          if (window.confirm(m + '\n\nStop it (its time is logged) and start this one?')) {
            return stop(false).then(function (ok) { if (ok) return start(taskId, opts); });
          }
          return;
        }
        PM.toast(m || 'Could not start the timer', 'error');
      });
  }

  function stop(confirmed) {
    return PM.api('POST', '/api/pm/timer/stop', { confirm: !!confirmed }).then(function (r) {
      if (r && r.needsConfirm) {
        var ok = window.confirm('This timer has run for ' + fmtMin(r.ranMinutes) + ', more than the 16h limit for one entry.\n\n' +
          'OK = log 16h (the maximum).\nCancel = keep it running; you can Discard it and log the time by hand.');
        return ok ? stop(true) : false;
      }
      PM.toast('Logged ' + fmtMin(r.loggedMinutes) + (r.entries > 1 ? ' across ' + r.entries + ' days' : ''), 'success');
      S.t = null; render(); refreshPages();
      return true;
    }).catch(function (e) {
      // e.g. locked week: the server rolled everything back, the timer is still running
      PM.toast((e && e.message) || 'Could not stop the timer', 'error');
      sync();
      return false;
    });
  }

  function discard() {
    if (!window.confirm('Discard this timer? The time will NOT be logged.')) return;
    PM.api('POST', '/api/pm/timer/discard').then(function () {
      S.t = null; render(); PM.toast('Timer discarded', 'info');
    }).catch(function (e) { PM.toast((e && e.message) || 'Failed', 'error'); });
  }

  /** HTML for a Start button (use in My Work cards and the task drawer). */
  function startButton(taskId, big) {
    var running = S.t && S.t.taskId === +taskId;
    return '<button class="pm-btn pm-btn-primary pm-timer-start" style="' + (big ? 'width:100%;font-size:16px;' : '') + '"' +
      (running ? ' disabled' : '') + ' onclick="event.stopPropagation();PMTimer.start(' + (+taskId) + ')">' +
      '<i class="fas fa-play"></i> ' + (running ? 'Running…' : 'Start timer') + '</button>';
  }

  css(); bar(); sync();
  document.addEventListener('visibilitychange', function () { if (!document.hidden) sync(); });
  window.addEventListener('focus', sync);
  window.addEventListener('pageshow', sync);
  S.poll = setInterval(function () { if (!document.hidden) sync(); }, 60000);

  window.PMTimer = { start: start, stop: function () { return stop(false); }, discard: discard, sync: sync,
    startButton: startButton, current: function () { return S.t; } };
})();