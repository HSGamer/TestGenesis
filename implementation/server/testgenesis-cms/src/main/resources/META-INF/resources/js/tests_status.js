document.addEventListener('DOMContentLoaded', () => {
    const sid = window.sessionId;
    if (!sid) return;

    const log = window.initLogger('telemetry-console');

    const render = (s) => {
        const t = el('step-template').content.cloneNode(true);
        const card = t.querySelector('article');
        const st = s.status.toLowerCase().replace('step_status_', '');
        
        const badge = t.querySelector('.status-badge');
        badge.textContent = st;
        badge.className = 'status-badge status-' + st;
        
        t.querySelector('.step-name').textContent = s.name;
        t.querySelector('.duration').textContent = (s.summary?.totalDuration||0) + 'ms';
        t.querySelector('.metadata').textContent = JSON.stringify(s.summary?.metadata||{}, null, 2);
        
        t.querySelector('.step-header').onclick = () => card.querySelector('.details').classList.toggle('d-none');
        return t;
    };

    const ws = new WebSocket(window.getWsUrl('/telemetry/test/' + sid));
    ws.onmessage = (e) => {
        const d = JSON.parse(e.data);
        if (d.type === 'TELEMETRY') log(d);
        if (d.type === 'STATUS') {
            const b = el('status-badge');
            if (b) {
                const s = window.formatStatus(d.state, 'TEST_STATE_');
                b.textContent = s.text;
                b.className = 'status-badge status-state-' + s.class;
            }
            if (el('status-message')) el('status-message').textContent = d.message;
        }
        if (d.type === 'RESULT' && el('steps-container')) {
            el('steps-container').textContent = '';
            (d.result.reports || []).forEach(r => el('steps-container').appendChild(render(r)));
            
            const sumSection = el('summary-section');
            if (sumSection && d.result.summary) {
                sumSection.classList.remove('d-none');
                el('summary-duration').textContent = d.result.summary.totalDuration + 'ms';
                el('summary-json').textContent = JSON.stringify(d.result.summary.metadata || {}, null, 2);
            }

            const attSection = el('attachments-section');
            const attContainer = el('attachments-container');
            if (attSection && attContainer && d.result.attachments?.length) {
                attSection.classList.remove('d-none');
                attContainer.textContent = '';
                d.result.attachments.forEach(a => {
                    const t = el('attachment-template').content.cloneNode(true);
                    const nameEl = t.querySelector('.attachment-name');
                    const imgEl = t.querySelector('.attachment-img');
                    const dlLink = t.querySelector('.download-link');
                    
                    nameEl.textContent = a.name || 'unnamed-file';
                    const url = a.url;
                    dlLink.href = url;
                    dlLink.download = a.name || 'file';

                    if (a.mimeType.startsWith('image/')) {
                        imgEl.src = url;
                        imgEl.classList.remove('d-none');
                        imgEl.onclick = () => window.open(url);
                    }
                    
                    attContainer.appendChild(t);
                });
            }
        }
    };
    ws.onopen = () => log({ level: 'INFO', message: 'Connected.', timestamp: Date.now() });
});
