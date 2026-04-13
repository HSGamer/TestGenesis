document.addEventListener('DOMContentLoaded', () => {
    const sessionId = window.sessionId;
    if (!sessionId) return;

    const consoleDiv = document.getElementById('telemetry-console');
    const statusBadge = document.getElementById('status-badge');
    const statusMessage = document.getElementById('status-message');
    const resultsSection = document.getElementById('results-section');
    const payloadsList = document.getElementById('payloads-list');

    const appendLog = (log) => {
        if (!consoleDiv) return;
        const line = document.createElement('div');
        line.className = 'log-line';
        const ts = new Date(log.timestamp).toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
        line.innerHTML = `<span class="log-timestamp">${ts}</span> <span class="log-severity-${(log.level || 'INFO').toLowerCase()}">[${log.level}]</span> ${log.message}`;
        consoleDiv.appendChild(line);
        consoleDiv.scrollTop = consoleDiv.scrollHeight;
    };

    const socket = new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/telemetry/translation/${sessionId}`);
    socket.onmessage = (e) => {
        const data = JSON.parse(e.data);
        if (data.type === 'TELEMETRY') {
            appendLog(data);
        } else if (data.type === 'STATUS') {
            if (statusBadge) {
                statusBadge.textContent = data.state.replace('TRANSLATION_STATE_', '');
                statusBadge.className = `status-badge status-state-${data.state.toLowerCase().replace('translation_state_', '')}`;
            }
            if (statusMessage) statusMessage.textContent = data.message || 'Processing...';
        } else if (data.type === 'RESULT') {
            if (resultsSection) resultsSection.classList.remove('d-none');
            if (payloadsList) {
                data.payloads.forEach(p => {
                    if (document.querySelector(`a[href="/payloads/${p.id}/edit"]`)) return;
                    const item = document.createElement('div');
                    item.className = 'list-item flex-between';
                    item.innerHTML = `<span>Payload #${p.id}</span> <a href="/payloads/${p.id}/edit" class="btn btn-sm btn-secondary">Edit</a>`;
                    payloadsList.appendChild(item);
                });
            }
        }
    };
    socket.onopen = () => appendLog({ level: 'INFO', message: 'Connected to telemetry stream.', timestamp: Date.now() });
});
