document.addEventListener('DOMContentLoaded', () => {
    const agentSelect = document.getElementById('agent-select');
    const typeSelect = document.getElementById('type-select');
    const startBtn = document.getElementById('start-btn');
    const typeInfo = document.getElementById('type-info');
    const payloadItems = document.querySelectorAll('#payload-list .list-item');

    const update = () => {
        if (!window.agents) return;
        const agent = window.agents.find(a => a.id === agentSelect.value);
        const config = agent?.supportedTranslations.find(st => st.type === typeSelect.value);
        const allowed = config ? config.sources : [];
        
        if (typeInfo) {
            typeInfo.textContent = config ? `${config.sources.join(', ')} \u2192 ${config.targets.join(', ')}` : '';
            typeInfo.classList.toggle('d-none', !config);
        }
        if (startBtn) startBtn.disabled = !typeSelect.value;

        payloadItems.forEach(item => {
            const isVisible = !typeSelect.value || allowed.includes(item.dataset.type);
            item.style.display = isVisible ? 'flex' : 'none';
            if (!isVisible) {
                const input = item.querySelector('input');
                if (input) input.checked = false;
            }
        });
    };

    if (agentSelect) {
        agentSelect.addEventListener('change', () => {
            if (!window.agents) return;
            const agent = window.agents.find(a => a.id === agentSelect.value);
            typeSelect.innerHTML = '<option value="">-- Select Type --</option>' + 
                (agent ? agent.supportedTranslations.map(st => `<option value="${st.type}">${st.type}</option>`).join('') : '');
            typeSelect.disabled = !agent;
            update();
        });
    }

    if (typeSelect) typeSelect.addEventListener('change', update);
});
