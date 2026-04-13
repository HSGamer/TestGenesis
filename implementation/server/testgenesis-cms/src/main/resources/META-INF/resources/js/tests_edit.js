document.addEventListener('DOMContentLoaded', () => {
    const el = id => document.getElementById(id);
    const type = el('test-type-input');
    const agent = el('agent-selector');
    const list = el('test-type-suggestions');
    const items = document.querySelectorAll('#payload-selector .list-item');

    const refresh = () => {
        if (!window.agents) return;
        const t = type.value;
        const a = window.agents.find(x => x.id === agent.value);
        const c = a?.supportedTypes.find(st => st.testType === t);
        const sug = new Set(c ? [...c.required, ...c.optional] : (t ? [t] : []));

        items.forEach(item => {
            const is = sug.has(item.dataset.type);
            item.style.background = is ? 'rgba(88, 166, 255, 0.05)' : '';
            const lbl = item.querySelector('.suggestion-label');
            if (lbl) lbl.classList.toggle('d-none', !is);
        });

        const val = agent.value;
        const filtered = window.agents.filter(x => !t || x.supportedTypes.some(st => st.testType === t));
        agent.innerHTML = '<option value="">-- No Agent --</option>' + 
            filtered.map(x => `<option value="${x.id}" ${x.id === val ? 'selected' : ''}>${x.displayName}</option>`).join('');
    };

    const init = () => {
        if (!window.agents) return;
        const types = new Set();
        window.agents.forEach(a => a.supportedTypes.forEach(st => types.add(st.testType)));
        if (list) list.innerHTML = [...types].map(t => `<option value="${t}"></option>`).join('');
        refresh();
    };

    if (type) type.addEventListener('input', refresh);
    if (agent) {
        agent.addEventListener('change', () => {
            const a = window.agents?.find(x => x.id === agent.value);
            if (a && type.value && !a.supportedTypes.some(st => st.testType === type.value)) type.value = a.supportedTypes[0].testType;
            refresh();
        });
    }
    init();
});
