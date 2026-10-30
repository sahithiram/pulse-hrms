/** pm-notify-settings.js  Gap 1. Needs pm-core.js. */
(function () {
  'use strict';
  function load() {
    return PM.api('GET', '/api/pm/settings/notify').then(function (s) {
      document.getElementById('pnOn').checked = !!s.emailHandover;
      document.getElementById('pnHigh').checked = !!s.emailHighOnly;
      document.getElementById('pnGlobalOff').style.display = s.globalEnabled ? 'none' : 'block';
    }).catch(function (e) { PM.toast(e.message, 'error'); });
  }
  function save() {
    PM.api('PUT', '/api/pm/settings/notify', {
      emailHandover: document.getElementById('pnOn').checked,
      emailHighOnly: document.getElementById('pnHigh').checked
    }).then(function () { PM.toast('Saved', 'success'); }).catch(function (e) { PM.toast(e.message, 'error'); });
  }
  window.PMNotifySettings = { load: load, save: save };
})();