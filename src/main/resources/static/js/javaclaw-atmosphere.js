/**
 * Atmosphere chat client for JavaClaw.
 *
 * Uses atmosphere.js v5 (global build) for WebSocket + SSE + long-polling
 * fallback and built-in reconnection with exponential backoff.
 *
 * Processes htmx OOB swap fragments so the existing chat UI works unchanged.
 */
(async function () {
    'use strict';

    var atm = AtmosphereJS.atmosphere;
    var statusTag = document.getElementById('status-tag');
    var textarea  = document.getElementById('message-input');
    var sendBtn   = document.getElementById('send-btn');
    var chatBody  = document.querySelector('.chat-body');

    var request = {
        url: document.location.origin + '/atmosphere/chat',
        contentType: 'application/json',
        transport: 'websocket',
        fallbackTransport: 'long-polling',
        reconnectInterval: 2000,
        maxReconnectOnClose: 60,
        trackMessageLength: false,
        enableProtocol: true
    };

    var handlers = {
        open: function () {
            setStatus('Connected', 'is-success');
            textarea.disabled = false;
            sendBtn.disabled = false;
        },

        reconnect: function () {
            setStatus('Reconnecting\u2026', 'is-warning');
            textarea.disabled = true;
            sendBtn.disabled = true;
        },

        close: function () {
            setStatus('Disconnected', 'is-warning');
            textarea.disabled = true;
            sendBtn.disabled = true;
        },

        error: function () {
            setStatus('Connection error', 'is-danger');
        },

        message: function (response) {
            var data = response.responseBody;
            if (!data || data.trim().length === 0) return;

            // Try JSON first (streaming tokens)
            if (data.charAt(0) === '{') {
                try {
                    var msg = JSON.parse(data);
                    if (msg.token !== undefined) {
                        appendToken(msg.token);
                        scrollToBottom();
                        return;
                    }
                    if (msg.done) {
                        finalizeStreaming();
                        scrollToBottom();
                        return;
                    }
                } catch (e) {
                    // Not JSON — fall through to OOB processing
                }
            }

            // OOB swap HTML (user bubble, typing indicator, background messages)
            processOobSwaps(data);
            scrollToBottom();
        }
    };

    var subscription;
    try {
        subscription = await atm.subscribe(request, handlers);
    } catch (err) {
        console.error('[JavaClaw] Atmosphere connection failed:', err);
        setStatus('Connection error', 'is-danger');
    }

    // ---- Send message ----

    function sendMessage() {
        var msg = textarea.value.trim();
        if (!msg || !subscription) return;

        subscription.push(JSON.stringify({ message: msg }));
        textarea.value = '';
        textarea.style.height = '';
    }

    var form = document.getElementById('chat-form');
    if (form) {
        form.addEventListener('submit', function (e) {
            e.preventDefault();
            sendMessage();
        });
    }

    if (textarea) {
        textarea.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });
    }

    // ---- Streaming token handling ----

    function appendToken(token) {
        var bubble = document.getElementById('streaming-bubble');
        if (!bubble) {
            // First token — create the agent bubble and hide typing dots
            var messages = document.getElementById('chat-messages');
            if (messages) {
                messages.insertAdjacentHTML('beforeend',
                    '<article class="ar-msg ar-msg--agent">' +
                    '<div class="ar-msg__avatar">JC</div>' +
                    '<div class="ar-msg__bubble" id="streaming-bubble"></div>' +
                    '</article>');
            }
            var typing = document.getElementById('typing-indicator');
            if (typing) typing.innerHTML = '';
            bubble = document.getElementById('streaming-bubble');
        }
        if (bubble) {
            bubble.insertAdjacentText('beforeend', token);
        }
    }

    function finalizeStreaming() {
        var bubble = document.getElementById('streaming-bubble');
        if (bubble) {
            bubble.removeAttribute('id');
        }
    }

    // ---- OOB swap processing ----

    function processOobSwaps(html) {
        var tmp = document.createElement('div');
        tmp.innerHTML = html;

        var oobElements = tmp.querySelectorAll('[hx-swap-oob]');
        for (var i = 0; i < oobElements.length; i++) {
            var el = oobElements[i];
            var swapStyle = el.getAttribute('hx-swap-oob');
            var targetId = el.id;
            if (!targetId) continue;

            var target = document.getElementById(targetId);
            if (!target) continue;

            if (swapStyle === 'true' || swapStyle === 'outerHTML') {
                target.innerHTML = el.innerHTML;
            } else if (swapStyle.indexOf('beforeend') === 0) {
                target.insertAdjacentHTML('beforeend', el.innerHTML);
            } else if (swapStyle.indexOf('afterbegin') === 0) {
                target.insertAdjacentHTML('afterbegin', el.innerHTML);
            } else {
                target.innerHTML = el.innerHTML;
            }
        }
    }

    // ---- Helpers ----

    function setStatus(text, bulmaClass) {
        if (!statusTag) return;
        statusTag.className = 'tag ' + bulmaClass + ' is-light is-rounded';
        statusTag.textContent = text;
    }

    function scrollToBottom() {
        if (chatBody) {
            chatBody.scrollTop = chatBody.scrollHeight;
        }
    }
}());
