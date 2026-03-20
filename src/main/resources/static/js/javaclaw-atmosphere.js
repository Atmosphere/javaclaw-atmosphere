/**
 * Atmosphere chat client for JavaClaw.
 *
 * Replaces htmx's WebSocket extension (hx-ext="ws") with atmosphere.js,
 * gaining automatic transport fallback (WebSocket -> SSE -> long-polling)
 * and built-in reconnection with exponential backoff.
 *
 * Processes htmx OOB swap fragments so the existing chat UI works unchanged.
 */
(function () {
    'use strict';

    var statusTag = document.getElementById('status-tag');
    var textarea  = document.getElementById('message-input');
    var sendBtn   = document.getElementById('send-btn');
    var chatBody  = document.querySelector('.chat-body');
    var subSocket;

    // ---- Atmosphere connection ----

    var request = new atmosphere.AtmosphereRequest();
    request.url = document.location.origin + '/ws/chat';
    request.contentType = 'application/json';
    request.transport = 'websocket';
    request.fallbackTransport = 'long-polling';
    request.reconnectInterval = 2000;
    request.maxReconnectOnClose = 60;
    request.trackMessageLength = false;

    request.onOpen = function () {
        setStatus('Connected', 'is-success');
        textarea.disabled = false;
        sendBtn.disabled = false;
    };

    request.onReconnect = function () {
        setStatus('Reconnecting\u2026', 'is-warning');
        textarea.disabled = true;
        sendBtn.disabled = true;
    };

    request.onClose = function () {
        setStatus('Disconnected', 'is-warning');
        textarea.disabled = true;
        sendBtn.disabled = true;
    };

    request.onError = function () {
        setStatus('Connection error', 'is-danger');
    };

    request.onMessage = function (response) {
        var html = response.responseBody;
        if (!html || html.trim().length === 0) return;

        processOobSwaps(html);
        scrollToBottom();
    };

    subSocket = atmosphere.subscribe(request);

    // ---- Send message ----

    function sendMessage() {
        var msg = textarea.value.trim();
        if (!msg) return;

        subSocket.push(JSON.stringify({ message: msg }));
        textarea.value = '';
        textarea.style.height = '';
    }

    // Form submit
    var form = document.getElementById('chat-form');
    if (form) {
        form.addEventListener('submit', function (e) {
            e.preventDefault();
            sendMessage();
        });
    }

    // Enter key (without Shift)
    if (textarea) {
        textarea.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });
    }

    // ---- OOB swap processing ----

    /**
     * Parses HTML containing hx-swap-oob elements and applies them to the DOM,
     * replicating what htmx's WebSocket extension does internally.
     */
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
            } else if (swapStyle === 'beforeend' || swapStyle.indexOf('beforeend') === 0) {
                target.insertAdjacentHTML('beforeend', el.innerHTML);
            } else if (swapStyle === 'afterbegin' || swapStyle.indexOf('afterbegin') === 0) {
                target.insertAdjacentHTML('afterbegin', el.innerHTML);
            } else {
                // Default: replace inner content
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
