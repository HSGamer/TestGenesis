document.addEventListener('DOMContentLoaded', () => {
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
        
        agent.textContent = '';
        const defaultOpt = document.createElement('option');
        defaultOpt.value = '';
        defaultOpt.textContent = '-- No Agent --';
        agent.appendChild(defaultOpt);

        filtered.forEach(x => {
            const opt = document.createElement('option');
            opt.value = x.id;
            opt.textContent = x.displayName;
            if (x.id === val) opt.selected = true;
            agent.appendChild(opt);
        });
    };

    const init = () => {
        if (!window.agents) return;
        const types = new Set();
        window.agents.forEach(a => a.supportedTypes.forEach(st => types.add(st.testType)));

        if (list) {
            list.textContent = '';
            [...types].forEach(t => {
                const opt = document.createElement('option');
                opt.value = t;
                list.appendChild(opt);
            });
        }
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
