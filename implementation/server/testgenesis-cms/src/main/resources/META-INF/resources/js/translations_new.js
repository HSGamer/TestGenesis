document.addEventListener('DOMContentLoaded', () => {
    const el = id => document.getElementById(id);
    const agent = el('agent-select'), type = el('type-select'), btn = el('start-btn'), info = el('type-info');
    const items = document.querySelectorAll('#payload-list .list-item');

    const update = () => {
        const a = window.agents?.find(x => x.id === agent.value);
        const c = a?.supportedTranslations.find(x => x.type === type.value);
        const allowed = c ? c.sources : [];
        
        if (info) {
            info.textContent = c ? `${c.sources.join(', ')} \u2192 ${c.targets.join(', ')}` : '';
            info.classList.toggle('d-none', !c);
        }
        if (btn) btn.disabled = !type.value;

        items.forEach(item => {
            const ok = !type.value || allowed.includes(item.dataset.type);
            item.style.display = ok ? 'flex' : 'none';
            if (!ok) { const i = item.querySelector('input'); if (i) i.checked = false; }
        });
    };

    if (agent) agent.addEventListener('change', () => {
        const a = window.agents?.find(x => x.id === agent.value);
        type.innerHTML = '';
        
        const defaultOpt = el('option-template').content.cloneNode(true);
        const optEl = defaultOpt.querySelector('option');
        optEl.value = '';
        optEl.textContent = '-- Select Type --';
        type.appendChild(defaultOpt);

        if (a) {
            a.supportedTranslations.forEach(x => {
                const opt = el('option-template').content.cloneNode(true);
                const o = opt.querySelector('option');
                o.value = x.type;
                o.textContent = x.type;
                type.appendChild(opt);
            });
        }
        
        type.disabled = !a;
        update();
    });
    if (type) type.addEventListener('change', update);
});
