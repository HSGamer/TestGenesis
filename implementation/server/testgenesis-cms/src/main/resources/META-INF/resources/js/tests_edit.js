document.addEventListener('DOMContentLoaded', () => {
    const typeInput = document.getElementById('test-type-input');
    const agentSelect = document.getElementById('agent-selector');
    const typeDatalist = document.getElementById('test-type-suggestions');
    const payloadItems = document.querySelectorAll('#payload-selector .list-item');

    const refresh = () => {
        if (!window.agents) return;
        const selectedType = typeInput.value;
        const agent = window.agents.find(a => a.id === agentSelect.value);
        const capability = agent?.supportedTypes.find(st => st.testType === selectedType);
        
        const suggested = new Set(capability ? [...capability.required, ...capability.optional] : (selectedType ? [selectedType] : []));

        payloadItems.forEach(item => {
            const isSuggested = suggested.has(item.dataset.type);
            item.style.backgroundColor = isSuggested ? 'rgba(88, 166, 255, 0.05)' : '';
            const label = item.querySelector('.suggestion-label');
            if (label) label.classList.toggle('d-none', !isSuggested);
        });

        // Update agent options if type changed
        const currentAgent = agentSelect.value;
        const filteredAgents = window.agents.filter(a => !selectedType || a.supportedTypes.some(st => st.testType === selectedType));
        
        agentSelect.innerHTML = '<option value="">-- No Agent Selected --</option>' + 
            filteredAgents.map(a => `<option value="${a.id}" ${a.id === currentAgent ? 'selected' : ''}>${a.displayName}</option>`).join('');
    };

    const init = () => {
        if (!window.agents) return;
        const types = new Set();
        window.agents.forEach(a => a.supportedTypes.forEach(st => types.add(st.testType)));
        if (typeDatalist) {
            typeDatalist.innerHTML = Array.from(types).map(t => `<option value="${t}"></option>`).join('');
        }
        refresh();
    };

    if (typeInput) typeInput.addEventListener('input', refresh);
    if (agentSelect) {
        agentSelect.addEventListener('change', () => {
            if (!window.agents) return;
            const agent = window.agents.find(a => a.id === agentSelect.value);
            if (agent && typeInput.value && !agent.supportedTypes.some(st => st.testType === typeInput.value)) {
                typeInput.value = agent.supportedTypes[0].testType;
            }
            refresh();
        });
    }

    init();
});
