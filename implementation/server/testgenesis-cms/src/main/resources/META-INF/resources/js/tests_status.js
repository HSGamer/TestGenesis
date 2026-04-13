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

    const render = (s) => {
        const t = el('step-template').content.cloneNode(true);
        const card = t.querySelector('.card');
        const st = s.status.toLowerCase().replace('step_status_', '');
        
        const badge = t.querySelector('.status-badge');
        badge.textContent = st;
        badge.className = `status-badge status-${st}`;
        
        t.querySelector('.step-name').textContent = s.name;
        t.querySelector('.duration').textContent = `${s.summary?.totalDuration||0}ms`;
        t.querySelector('.metadata').textContent = JSON.stringify(s.summary?.metadata||{}, null, 2);
        
        t.querySelector('.header').onclick = () => card.querySelector('.details').classList.toggle('d-none');
        return t;
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
            
            const sumSection = el('summary-section');
            if (sumSection && d.result.summary) {
                sumSection.classList.remove('d-none');
                el('summary-duration').textContent = `${d.result.summary.totalDuration}ms`;
                el('summary-json').textContent = JSON.stringify(d.result.summary.metadata || {}, null, 2);
            }

            const attSection = el('attachments-section');
            const attContainer = el('attachments-container');
            if (attSection && attContainer && d.result.attachments?.length) {
                attSection.classList.remove('d-none');
                attContainer.innerHTML = '';
                d.result.attachments.forEach(a => {
                    const t = el('attachment-template').content.cloneNode(true);
                    const img = t.querySelector('img');
                    img.src = `data:${a.mimeType};base64,${a.data}`;
                    attContainer.appendChild(t);
                });
            }
        }
    };
    ws.onopen = () => log({ level: 'INFO', message: 'Connected.', timestamp: Date.now() });
});
