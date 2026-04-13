document.addEventListener('DOMContentLoaded', () => {
    const sessionId = window.sessionId;
    if (!sessionId) return;

    const consoleDiv = document.getElementById('telemetry-console');
    const statusBadge = document.getElementById('status-badge');
    const statusMessage = document.getElementById('status-message');
    const stepsContainer = document.getElementById('steps-container');
    const summaryStats = document.getElementById('summary-stats');

    const appendLog = (log) => {
        if (!consoleDiv) return;
        const line = document.createElement('div');
        line.className = 'log-line';
        const ts = new Date(log.timestamp).toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
        line.innerHTML = `<span class="log-timestamp">${ts}</span> <span class="log-severity-${(log.level || 'INFO').toLowerCase()}">[${log.level}]</span> ${log.message}`;
        consoleDiv.appendChild(line);
        consoleDiv.scrollTop = consoleDiv.scrollHeight;
    };

    const renderStep = (step) => {
        const status = step.status.toLowerCase().replace('step_status_', '');
        const card = document.createElement('div');
        card.className = 'card mb-1';
        card.innerHTML = `
            <div class="flex-between" style="cursor: pointer; padding: 0.5rem 0;" onclick="this.nextElementSibling.classList.toggle('d-none')">
                <div class="d-flex gap-1 align-items-center">
                    <span class="status-badge status-${status}">${status.toUpperCase()}</span>
                    <strong>${step.name}</strong>
                </div>
                <span class="text-muted small-text">Duration: ${step.summary?.totalDuration?.seconds || 'N/A'}s</span>
            </div>
            <div class="mt-1 d-none" style="border-top: 1px solid var(--border-color); padding-top: 0.5rem;">
                <div class="small-text text-muted mb-1">Metadata:</div>
                <pre style="background: var(--card-bg); padding: 0.5rem; border-radius: 4px; border: 1px solid var(--border-color); font-size: 0.75rem; overflow-x: auto; color: var(--text-color);">${JSON.stringify(step.summary?.metadata || {}, null, 2)}</pre>
                ${(step.attachments || []).map(a => `<img src="data:${a.mimeType};base64,${a.data}" style="max-width: 150px; border-radius: 4px; border: 1px solid var(--border-color); cursor: zoom-in; margin-top: 0.5rem;" onclick="window.open(this.src)">`).join('')}
            </div>
        `;
        return card;
    };

    const socket = new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/telemetry/test/${sessionId}`);
    socket.onmessage = (e) => {
        const data = JSON.parse(e.data);
        if (data.type === 'TELEMETRY') {
            appendLog(data);
        } else if (data.type === 'STATUS') {
            if (statusBadge) {
                statusBadge.textContent = data.state.replace('TEST_STATE_', '');
                statusBadge.className = `status-badge status-state-${data.state.toLowerCase().replace('test_state_', '')}`;
            }
            if (data.message && statusMessage) statusMessage.textContent = data.message;
        } else if (data.type === 'RESULT') {
            if (stepsContainer) {
                stepsContainer.innerHTML = '';
                const reports = data.result.reports || [];
                if (summaryStats) {
                    summaryStats.innerHTML = `<span class="status-badge status-passed">${reports.filter(r => r.status === 'STEP_STATUS_PASSED').length} PASSED</span>
                                               <span class="status-badge status-failed">${reports.filter(r => r.status === 'STEP_STATUS_FAILED').length} FAILED</span>`;
                }
                if (reports.length) {
                    reports.forEach(r => stepsContainer.appendChild(renderStep(r)));
                } else {
                    stepsContainer.innerHTML = '<p class="text-muted text-center py-2">No steps reported.</p>';
                }
            }
        }
    };
    socket.onopen = () => appendLog({ level: 'INFO', message: 'Connected to control plane.', timestamp: Date.now() });
});
