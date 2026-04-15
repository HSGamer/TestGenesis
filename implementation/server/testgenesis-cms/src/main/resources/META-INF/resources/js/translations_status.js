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
                d.result.forEach(p => {
                    if (document.querySelector('[data-payload-index="' + p.index + '"]')) return;
                    const t = el('payload-template').content.cloneNode(true);
                    const item = t.querySelector('li');
                    item.setAttribute('data-payload-index', p.index);
                    
                    t.querySelector('.payload-name').textContent = p.name || 'unnamed';
                    t.querySelector('.payload-type').textContent = p.type;
                    
                    t.querySelector('.download-link').href = `/translations/${sid}/payloads/${p.index}/download`;
                    t.querySelector('.save-link').href = `/translations/${sid}/payloads/${p.index}/save-form`;
                    
                    list.appendChild(t);
                });
            }
        }
    };
    ws.onopen = () => log({ level: 'INFO', message: 'Connected.', timestamp: Date.now() });
});
