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
                        const template = el('session-row-template');
                        const clone = template.content.cloneNode(true);
                        row = clone.querySelector('tr');
                        row.dataset.sessionId = s.sessionId;
                        
                        row.querySelector('.col-index').textContent = i + 1;
                        row.querySelector('.agent-name').textContent = s.agentName;
                        row.querySelector('.agent-id').textContent = s.agentId;
                        row.querySelector('.session-id').textContent = s.sessionId;
                        row.querySelector('.view-btn').href = `/tests/${s.sessionId}/status`;
                        
                        table.appendChild(clone);
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
