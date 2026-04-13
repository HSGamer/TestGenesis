document.addEventListener('DOMContentLoaded', () => {
    const container = document.getElementById("json-editor");
    const metadataInput = document.getElementById("metadata-input");
    const typeInput = document.querySelector('input[name="type"]');
    const attachmentInput = document.getElementById("payload-file");
    const mimeError = document.getElementById("mime-error");

    if (container && metadataInput) {
        // JSONEditor Initialization
        const editor = new JSONEditor(container, {
            mode: 'code',
            modes: ['code', 'text'],
            onChange: () => {
                try {
                    metadataInput.value = JSON.stringify(editor.get());
                } catch (e) {}
            }
        });

        try {
            const initial = JSON.parse(metadataInput.value || '{}');
            editor.set(initial);
            metadataInput.value = JSON.stringify(initial);
        } catch (e) {}
    }

    // MIME Validation
    const validate = () => {
        if (!typeInput || !attachmentInput || !window.mimeTypeMapping) return;
        const mimes = window.mimeTypeMapping[typeInput.value];
        const file = attachmentInput.files[0];
        if (mimes && file && !mimes.includes(file.type)) {
            mimeError.textContent = `Warning: ${file.type} might not be supported.`;
            mimeError.classList.remove('d-none');
        } else {
            mimeError.classList.add('d-none');
        }
    };

    if (typeInput) typeInput.addEventListener('input', validate);
    if (attachmentInput) attachmentInput.addEventListener('change', validate);
});
