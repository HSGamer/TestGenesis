document.addEventListener('DOMContentLoaded', () => {
    const el = id => document.getElementById(id);
    const container = el("json-editor");
    const input = el("metadata-input");
    const type = document.querySelector('input[name="type"]');
    const file = el("payload-file");
    const error = el("mime-error");

    if (container && input) {
        const editor = new JSONEditor(container, {
            mode: 'code',
            onChange: () => { try { input.value = JSON.stringify(editor.get()); } catch (e) {} }
        });
        try {
            const initial = JSON.parse(input.value || '{}');
            editor.set(initial);
            input.value = JSON.stringify(initial);
        } catch (e) {}
    }

    const validate = () => {
        if (!type || !file || !window.mimeTypeMapping) return;
        const mimes = window.mimeTypeMapping[type.value];
        const f = file.files[0];
        const invalid = mimes && f && !mimes.includes(f.type);
        error.textContent = invalid ? `Warning: ${f.type} might not be supported.` : '';
        error.classList.toggle('d-none', !invalid);
    };

    if (type) type.addEventListener('input', validate);
    if (file) file.addEventListener('change', validate);
});
