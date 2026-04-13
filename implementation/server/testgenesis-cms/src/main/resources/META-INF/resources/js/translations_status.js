document.addEventListener('DOMContentLoaded', () => {
    const sid = window.sessionId;
    if (!sid) return;

    const el = id => document.getElementById(id);
    const logDiv = el('telemetry-console');
    
    const log = (m) => {
        if (!logDiv) return;
        const div = document.createElement('div');
        const time = document.createElement('span');
        time.className = 'text-muted small';
        time.textContent = `${new Date(m.timestamp).toLocaleTimeString([], {hour12:false})} [${m.level||'INFO'}] `;
        div.appendChild(time);
        div.append(m.message);
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
                    if (document.querySelector(`[data-payload-id="${p.id}"]`)) return;
                    const t = el('payload-template').content.cloneNode(true);
                    const item = t.querySelector('.list-item');
                    item.setAttribute('data-payload-id', p.id);
                    
                    if (p.removed) {
                        t.querySelector('.payload-name').remove();
                        t.querySelector('.edit-link').remove();
                        const removed = t.querySelector('.removed-info');
                        removed.textContent = `Payload #${p.id} (removed)`;
                        removed.classList.remove('d-none');
                    } else {
                        t.querySelector('.payload-name').textContent = `Payload #${p.id}`;
                        t.querySelector('.edit-link').href = `/payloads/${p.id}/edit`;
                        t.querySelector('.removed-info').remove();
                    }
                    list.appendChild(t);
                });
            }
        }
    };
    ws.onopen = () => log({ level: 'INFO', message: 'Connected.', timestamp: Date.now() });
});
