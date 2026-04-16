document.addEventListener('DOMContentLoaded', () => {
    const id = window.batchId, 
          table = el('sessions-table'), 
          progress = el('batch-progress'), 
          count = el('completed-count'), 
          label = el('batch-status-label');
          
    if (!id) return;

    const ws = new WebSocket(window.getWsUrl('/telemetry/test/batch/' + id));
    
    ws.onmessage = (e) => {
        const d = JSON.parse(e.data);
        if (d.type === 'BATCH_UPDATE') {
            if (label) label.textContent = d.status;
            if (count) count.textContent = d.completed;
            if (progress) progress.value = d.completed;
            
            if (table && d.sessions) {
                const empty = el('empty-row');
                if (empty) empty.remove();
                
                d.sessions.forEach((s, i) => {
                    let row = table.querySelector('[data-session-id="' + s.sessionId + '"]');
                    if (!row) {
                        row = document.createElement('tr');
                        row.dataset.sessionId = s.sessionId;
                        
                        const tdIdx = document.createElement('td');
                        tdIdx.textContent = i + 1;
                        row.appendChild(tdIdx);

                        const tdSid = document.createElement('td');
                        const codeSid = document.createElement('code');
                        codeSid.textContent = s.sessionId;
                        tdSid.appendChild(codeSid);
                        row.appendChild(tdSid);

                        const tdStatus = document.createElement('td');
                        const badge = document.createElement('span');
                        badge.className = 'status-badge';
                        tdStatus.appendChild(badge);
                        row.appendChild(tdStatus);

                        const tdAction = document.createElement('td');
                        const viewLink = document.createElement('a');
                        viewLink.href = `/tests/${s.sessionId}/status`;
                        viewLink.className = 'btn small';
                        viewLink.textContent = 'View';
                        tdAction.appendChild(viewLink);
                        row.appendChild(tdAction);

                        table.appendChild(row);
                    }
                    
                    const badge = row.querySelector('.status-badge');
                    const cleanStatus = s.state.replace('TEST_STATE_', '');
                    badge.textContent = cleanStatus;
                    badge.className = 'status-badge status-state-' + cleanStatus.toLowerCase();
                    
                    const msgEl = row.querySelector('.status-message');
                    if (msgEl) msgEl.textContent = s.message;
                });
            }
        }
    };
    
    ws.onopen = () => console.log('Connected to batch telemetry');
    ws.onclose = () => console.log('Disconnected from batch telemetry');
});
