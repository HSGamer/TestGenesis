document.addEventListener('DOMContentLoaded', () => {
    const sid = window.sessionId;
    if (!sid) return;

    const log = window.initLogger('telemetry-console');

    const ws = new WebSocket(window.getWsUrl('/telemetry/translation/' + sid));
    ws.onmessage = (e) => {
        const d = JSON.parse(e.data);
        if (d.type === 'TELEMETRY') log(d);
        if (d.type === 'STATUS') {
            const b = el('status-badge');
            if (b) {
                const s = window.formatStatus(d.state, 'TRANSLATION_STATE_');
                b.textContent = s.text;
                b.className = 'status-badge status-state-' + s.class;
            }
            if (el('status-message')) el('status-message').textContent = d.message || 'Processing...';
        }
        if (d.type === 'RESULT') {
            if (el('results-section')) el('results-section').classList.remove('d-none');
            const list = el('payloads-list');
            if (list) {
                d.payloads.forEach(p => {
                    if (document.querySelector('[data-payload-id="' + p.id + '"]')) return;
                    const t = el('payload-template').content.cloneNode(true);
                    const item = t.querySelector('.list-item');
                    item.setAttribute('data-payload-id', p.id);
                    
                    if (p.removed) {
                        t.querySelector('.payload-name').remove();
                        t.querySelector('.edit-link').remove();
                        const removed = t.querySelector('.removed-info');
                        removed.textContent = 'Payload #' + p.id + ' (removed)';
                        removed.classList.remove('d-none');
                    } else {
                        t.querySelector('.payload-name').textContent = 'Payload #' + p.id;
                        t.querySelector('.edit-link').href = '/payloads/' + p.id + '/edit';
                        t.querySelector('.removed-info').remove();
                    }
                    list.appendChild(t);
                });
            }
        }
    };
    ws.onopen = () => log({ level: 'INFO', message: 'Connected.', timestamp: Date.now() });
});
