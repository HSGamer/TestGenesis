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

    const ws = new WebSocket(`${location.protocol.replace('http','ws')}//${location.host}/telemetry/translation/${sid}`);
    ws.onmessage = (e) => {
        const d = JSON.parse(e.data);
        if (d.type === 'TELEMETRY') log(d);
        if (d.type === 'STATUS') {
            const b = el('status-badge');
            if (b) {
                b.textContent = d.state.replace('TRANSLATION_STATE_', '');
                b.className = `status-badge status-state-${d.state.toLowerCase().replace('translation_state_', '')}`;
            }
            if (el('status-message')) el('status-message').textContent = d.message || 'Processing...';
        }
        if (d.type === 'RESULT') {
            if (el('results-section')) el('results-section').classList.remove('d-none');
            const list = el('payloads-list');
            if (list) {
                d.payloads.forEach(p => {
                    if (document.querySelector(`[data-payload-id="${p.id}"]` )) return;
                    const div = document.createElement('div');
                    div.className = 'list-item flex-between';
                    div.setAttribute('data-payload-id', p.id);
                    div.innerHTML = p.removed 
                        ? `<span class="text-muted small">Payload #${p.id} (removed)</span>`
                        : `<span class="small">Payload #${p.id}</span> <a href="/payloads/${p.id}/edit" class="btn">Edit</a>`;
                    list.appendChild(div);
                });
            }
        }
    };
    ws.onopen = () => log({ level: 'INFO', message: 'Connected.', timestamp: Date.now() });
});
