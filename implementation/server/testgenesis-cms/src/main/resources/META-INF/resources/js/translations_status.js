document.addEventListener('DOMContentLoaded', () => {
    const sid = window.sessionId;
    if (!sid) return;

    const log = window.initLogger('telemetry-console');
    let isCompleted = false;

    const ws = new WebSocket(window.getWsUrl('/telemetry/translation/' + sid));
    ws.onmessage = (e) => {
        const d = JSON.parse(e.data);
        if (d.type === 'TELEMETRY') {
            log(d);
        } else if (d.type === 'STATUS') {
            const b = el('status-badge');
            if (b) {
                const s = window.formatStatus(d.state, 'TRANSLATION_STATE_');
                b.textContent = s.text;
                b.className = 'status-badge status-state-' + s.class;
            }
            if (el('status-message')) el('status-message').textContent = d.message || 'Processing...';
        } else if (d.type === 'RESULT') {
            if (!isCompleted) {
                isCompleted = true;
                // Only reload if the page was rendered as "pending"
                // If it's already rendered as "result", we don't need to reload
                if (!document.getElementById('results-section')) {
                    console.log('Task completed, refreshing to show final results...');
                    setTimeout(() => window.location.reload(), 1000);
                }
            }
        }
    };
    ws.onopen = () => {
        log({ level: 'INFO', message: 'Connected.', timestamp: Date.now() });
    };
    ws.onerror = (err) => console.error('WS Error:', err);
});
