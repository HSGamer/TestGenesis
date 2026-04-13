document.addEventListener('DOMContentLoaded', () => {
    const sid = window.sessionId;
    if (!sid) return;

    const el = id => document.getElementById(id);
    const logDiv = el('telemetry-console');
    
    const log = (m) => {
        if (!logDiv) return;
        const div = document.createElement('div');
        div.innerHTML = `<span class="text-muted small">${new Date(m.timestamp).toLocaleTimeString([], {hour12:false})} [${m.level||'INFO'}]</span> ${m.message}`;
        logDiv.appendChild(div);
        logDiv.scrollTop = logDiv.scrollHeight;
    };

    const render = (s) => {
        const st = s.status.toLowerCase().replace('step_status_', '');
        const div = document.createElement('div');
        div.className = 'card';
        div.innerHTML = `
            <div class="flex-between" style="cursor:pointer" onclick="this.nextElementSibling.classList.toggle('d-none')">
                <div class="flex"><span class="status-badge status-${st}">${st}</span><strong>${s.name}</strong></div>
                <span class="text-muted small">${s.summary?.totalDuration?.seconds||0}s</span>
            </div>
            <div class="d-none" style="margin-top:0.5rem; border-top:1px solid var(--border-color); padding-top:0.5rem">
                <pre>${JSON.stringify(s.summary?.metadata||{}, null, 2)}</pre>
                ${(s.attachments||[]).map(a => `<img src="data:${a.mimeType};base64,${a.data}" style="max-width:150px; margin-top:0.5rem; cursor:zoom-in" onclick="window.open(this.src)">`).join('')}
            </div>`;
        return div;
    };

    const ws = new WebSocket(`${location.protocol.replace('http','ws')}//${location.host}/telemetry/test/${sid}`);
    ws.onmessage = (e) => {
        const d = JSON.parse(e.data);
        if (d.type === 'TELEMETRY') log(d);
        if (d.type === 'STATUS') {
            const b = el('status-badge');
            if (b) {
                b.textContent = d.state.replace('TEST_STATE_', '');
                b.className = `status-badge status-state-${d.state.toLowerCase().replace('test_state_', '')}`;
            }
            if (el('status-message')) el('status-message').textContent = d.message;
        }
        if (d.type === 'RESULT' && el('steps-container')) {
            el('steps-container').innerHTML = '';
            (d.result.reports || []).forEach(r => el('steps-container').appendChild(render(r)));
        }
    };
    ws.onopen = () => log({ level: 'INFO', message: 'Connected.', timestamp: Date.now() });
});
