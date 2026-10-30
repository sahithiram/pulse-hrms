/* =====================================================================
   MENTI AI ASSISTANT — SHARED CONTROLLER (HR / Super Admin / PM)
   One implementation, role-driven config. Expects shared markup:
     #mentiAiPanel, #mentiAiOverlay, #mentiAiFab, #mentiAiNavBtn,
     #mentiAiMessages, #mentiAiInput, #mentiAiSendBtn,
     #mentiAiSessionsCol/List/Count, #mentiAiBackdrop, #mentiAiHdrChevron
   ===================================================================== */
(function () {
    'use strict';

    var cfg = window.MENTI_AI_CONFIG || {};
    var ROLE = cfg.role || 'ASSISTANT';
    var SEND_URL = cfg.sendUrl || '/api/chat/hr/send';
    var SESSIONS_URL = cfg.sessionsUrl || '/api/chat/hr/sessions';
    var HISTORY_URL = cfg.historyUrl || '/api/chat/hr/session';
    var DELETE_URL = cfg.deleteUrl || '/api/chat/hr/session';

    var panelOpen = false;
    var isTyping = false;
    var currentSessionId = null;
    var sidebarOpen = false;
    var temporaryHistory = [];
    var sessions = [];

    // ---------- helpers ----------
    function $(id) { return document.getElementById(id); }

    function getEmployeeId() {
        return ($('currentEmployeeId') || {}).value || '';
    }

    function esc(str) {
        var d = document.createElement('div');
        d.textContent = String(str || '');
        return d.innerHTML;
    }

    function fmtTime() {
        return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }

    // ---------- panel ----------
    window.toggleMentiAiPanel = function () {
        panelOpen ? closeMentiAiPanel() : openMentiAiPanel();
    };

    function openMentiAiPanel() {
        var panel = $('mentiAiPanel'), overlay = $('mentiAiOverlay'), fab = $('mentiAiFab');
        if (panel) panel.classList.add('open');
        if (overlay) overlay.classList.add('open');
        if (fab) fab.style.display = 'flex';
        panelOpen = true;
        setTimeout(function () {
            var input = $('mentiAiInput');
            if (input) input.focus();
        }, 350);
        if (!currentSessionId) startNewSession();
    }
    window.openMentiAiPanel = openMentiAiPanel;

    function closeMentiAiPanel() {
        var panel = $('mentiAiPanel'), overlay = $('mentiAiOverlay'), fab = $('mentiAiFab');
        if (panel) panel.classList.remove('open');
        if (overlay) overlay.classList.remove('open');
        if (fab) fab.style.display = 'none';
        panelOpen = false;
    }
    window.closeMentiAiPanel = closeMentiAiPanel;

    // ---------- sessions sidebar ----------
    window.toggleMentiAiSessionsSidebar = function () {
        sidebarOpen = !sidebarOpen;
        var col = $('mentiAiSessionsCol');
        var backdrop = $('mentiAiBackdrop');
        var chevron = $('mentiAiHdrChevron');
        if (col) col.classList.toggle('open', sidebarOpen);
        if (backdrop) {
            backdrop.style.background = sidebarOpen ? 'rgba(0,0,0,0.45)' : 'rgba(0,0,0,0)';
            backdrop.style.pointerEvents = sidebarOpen ? 'auto' : 'none';
        }
        if (chevron) chevron.classList.toggle('open', sidebarOpen);
        if (sidebarOpen) loadSessionsList();
    };

    // ---------- messages ----------
    function appendMsg(text, role, isError) {
        var messages = $('mentiAiMessages');
        if (!messages) return;
        var isUser = role === 'user';
        var row = document.createElement('div');
        row.className = 'menti-ai-msg ' + role;

        var av = document.createElement('div');
        av.className = 'menti-ai-msg-av ' + (isUser ? 'user' : 'bot');
        av.textContent = isUser ? ((cfg.userInitial || 'U')) : 'M';

        var body = document.createElement('div');
        body.className = 'menti-ai-msg-body';

        var bubble = document.createElement('div');
        bubble.className = 'menti-ai-bubble ' + (isUser ? 'user' : 'bot') + (isError ? ' error' : '');
        bubble.innerHTML = esc(text).replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>').replace(/\n/g, '<br>');

        var time = document.createElement('div');
        time.className = 'menti-ai-msg-time';
        time.textContent = fmtTime();

        body.appendChild(bubble);
        body.appendChild(time);
        if (isUser) { row.appendChild(body); row.appendChild(av); }
        else { row.appendChild(av); row.appendChild(body); }

        messages.appendChild(row);
        messages.scrollTop = messages.scrollHeight;
    }

    function showTyping() {
        var messages = $('mentiAiMessages');
        if (!messages) return null;
        var row = document.createElement('div');
        row.className = 'menti-ai-msg bot';
        row.id = 'mentiAiTypingRow';
        row.innerHTML =
            '<div class="menti-ai-msg-av bot">M</div>' +
            '<div class="menti-ai-msg-body">' +
            '<div class="menti-ai-bubble bot"><div class="menti-ai-typing"><span></span><span></span><span></span></div></div>' +
            '</div>';
        messages.appendChild(row);
        messages.scrollTop = messages.scrollHeight;
        return row.id;
    }

    function removeTyping() {
        var t = $('mentiAiTypingRow');
        if (t) t.remove();
    }

    function clearMessages() {
        var m = $('mentiAiMessages');
        if (m) m.innerHTML = '';
    }

    function showWelcome() {
        var messages = $('mentiAiMessages');
        if (!messages) return;
        var name = cfg.assistantName || 'Menti';
        var tag = cfg.roleTag || ROLE;
        messages.innerHTML =
            '<div class="menti-ai-welcome-msg">' +
            '<div class="menti-ai-welcome-av">M</div>' +
            '<div class="menti-ai-welcome-bubble">' +
            'Hi! I\'m <strong>' + esc(name) + '</strong> <span class="menti-ai-role-tag">' + esc(tag) + '</span>, your AI assistant.<br><br>' +
            'I can help you with <strong>all</strong> HRMS tasks including:<br><br>' +
            '&bull; 👥 Employee &amp; User Management<br>' +
            '&bull; 📊 Reports &amp; Analytics<br>' +
            '&bull; 📋 Leave &amp; Attendance Approvals<br>' +
            '&bull; 🏢 Company Settings &amp; Configuration<br>' +
            '&bull; 📢 Announcements &amp; Communications<br>' +
            '&bull; 🔐 Roles &amp; Permissions<br><br>' +
            '<span style="color:#10b981;font-size:11px;">💾 All chats are saved automatically</span>' +
            '</div></div>';
    }

    function updateSessionHeader() {
        var el = $('mentiAiModeLabel');
        if (!el) return;
        if (currentSessionId) {
            el.textContent = '💾 Saved chat';
            el.style.color = '#10b981';
        } else {
            el.textContent = '💾 New saved chat';
            el.style.color = '#0ea5e9';
        }
    }

    // ---------- sessions ----------
    function startNewSession() {
        currentSessionId = null;
        clearMessages();
        showWelcome();
        updateSessionHeader();
        if (sidebarOpen) toggleMentiAiSessionsSidebar();
        var chips = $('mentiAiSuggestions');
        if (chips) chips.style.removeProperty('display');
        document.querySelectorAll('.menti-ai-session-item').forEach(function (el) {
            el.classList.remove('active');
        });
    }
    window.startNewMentiAiSession = startNewSession;

    function loadSessionsList() {
        var employeeId = getEmployeeId();
        if (!employeeId) return;
        fetch(SESSIONS_URL + '?employeeId=' + encodeURIComponent(employeeId))
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (!data.success) return;
                sessions = data.sessions || [];
                var countEl = $('mentiAiSessionCount');
                if (countEl) countEl.textContent = sessions.length;
                renderSessionsList();
            })
            .catch(function (e) { console.error('Error loading sessions:', e); });
    }
    window.loadMentiAiSessionsList = loadSessionsList;

    function renderSessionsList() {
        var container = $('mentiAiSessionsList');
        if (!container) return;
        if (!sessions.length) {
            container.innerHTML = '<div style="padding:20px 10px;text-align:center;font-size:10px;color:#334155;">No saved chats yet</div>';
            return;
        }
        container.innerHTML = sessions.map(function (s) {
            return '<div class="menti-ai-session-item ' + (currentSessionId === s.id ? 'active' : '') + '"' +
                ' onclick="loadMentiAiSession(' + s.id + ')" data-session-id="' + s.id + '">' +
                '<div class="menti-ai-session-item-name">' + esc(s.sessionName) +
                '<span class="menti-ai-role-badge">' + esc(cfg.roleTag || 'Admin') + '</span></div>' +
                '<div class="menti-ai-session-item-meta">' + esc(s.updatedAt) + ' &middot; ' + (s.messageCount || 0) + ' msgs</div>' +
                '<button class="menti-ai-session-del" onclick="event.stopPropagation();deleteMentiAiSession(' + s.id + ')" title="Delete">&times;</button>' +
                '</div>';
        }).join('');
    }

    function loadSession(sessionId) {
        var employeeId = getEmployeeId();
        if (!employeeId) return;
        fetch(HISTORY_URL + '/' + sessionId + '/history?employeeId=' + encodeURIComponent(employeeId))
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (!data.success) return;
                currentSessionId = sessionId;
                clearMessages();
                updateSessionHeader();
                if (sidebarOpen) toggleMentiAiSessionsSidebar();
                if (data.messages && data.messages.length) {
                    data.messages.forEach(function (m) { appendMsg(m.content, m.role); });
                } else {
                    showWelcome();
                }
                document.querySelectorAll('.menti-ai-session-item').forEach(function (el) {
                    el.classList.remove('active');
                });
                var active = document.querySelector('[data-session-id="' + sessionId + '"]');
                if (active) active.classList.add('active');
                var chips = $('mentiAiSuggestions');
                if (chips) chips.style.display = 'none';
            })
            .catch(function (e) { console.error('Error loading session:', e); });
    }
    window.loadMentiAiSession = loadSession;

    function deleteSession(sessionId) {
        if (!confirm('Delete this chat history?')) return;
        var employeeId = getEmployeeId();
        if (!employeeId) return;
        fetch(DELETE_URL + '/' + sessionId + '?employeeId=' + encodeURIComponent(employeeId), { method: 'DELETE' })
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (data.success) {
                    if (currentSessionId === sessionId) startNewSession();
                    loadSessionsList();
                }
            })
            .catch(function (e) { console.error('Error deleting session:', e); });
    }
    window.deleteMentiAiSession = deleteSession;

    // ---------- send ----------
    function sendMessage() {
        var input = $('mentiAiInput');
        var btn = $('mentiAiSendBtn');
        if (!input || isTyping) return;
        var text = input.value.trim();
        if (!text) return;

        input.value = '';
        input.style.height = 'auto';

        var chips = $('mentiAiSuggestions');
        if (chips) chips.style.display = 'none';

        appendMsg(text, 'user');
        isTyping = true;
        if (btn) btn.disabled = true;
        showTyping();

        // Optional local intent hooks (e.g. HR report downloads)
        if (typeof window.mentiAiLocalIntent === 'function') {
            var handled = window.mentiAiLocalIntent(text, appendMsg);
            if (handled) {
                removeTyping();
                isTyping = false;
                if (btn) btn.disabled = false;
                return;
            }
        }

        fetch(SEND_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                employeeId: getEmployeeId(),
                message: text,
                sessionId: currentSessionId,
                role: ROLE
            })
        })
            .then(function (r) { return r.json(); })
            .then(function (data) {
                removeTyping();
                if (data.success) {
                    appendMsg(data.reply, 'assistant');
                    if (data.sessionId && data.sessionId > 0) {
                        currentSessionId = data.sessionId;
                        updateSessionHeader();
                        loadSessionsList();
                    }
                } else {
                    appendMsg(data.error || 'Sorry, something went wrong. Please try again.', 'assistant', true);
                }
            })
            .catch(function () {
                removeTyping();
                appendMsg('Connection error. Please check your network.', 'assistant', true);
            })
            .finally(function () {
                isTyping = false;
                if (btn) btn.disabled = false;
                if (input) input.focus();
            });
    }
    window.sendMentiAiMessage = sendMessage;

    window.sendMentiAiSuggestion = function (text) {
        var input = $('mentiAiInput');
        if (input) input.value = text;
        sendMessage();
    };

    window.handleMentiAiKeydown = function (e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    };

    window.autoResizeMentiAiInput = function (el) {
        el.style.height = 'auto';
        el.style.height = Math.min(el.scrollHeight, 100) + 'px';
    };

    // keyboard shortcuts
    document.addEventListener('keydown', function (e) {
        if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'M') {
            e.preventDefault();
            toggleMentiAiPanel();
        }
        if (e.key === 'Escape' && panelOpen) closeMentiAiPanel();
    });

    // auto-init
    document.addEventListener('DOMContentLoaded', function () {
        setTimeout(loadSessionsList, 1000);
    });
})();