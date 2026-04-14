/**
 * Common utilities for TestGenesis CMS
 */

window.el = (id) => document.getElementById(id);

window.initLogger = (containerId) => {
    const logDiv = window.el(containerId);
    if (!logDiv) return () => {};
    
    return (m) => {
        const div = document.createElement('div');
        const time = document.createElement('span');
        time.className = 'text-muted small';
        time.textContent = new Date(m.timestamp).toLocaleTimeString([], {hour12:false}) + ' [' + (m.level||'INFO') + '] ';
        div.appendChild(time);
        div.append(m.message);
        logDiv.appendChild(div);
        logDiv.scrollTop = logDiv.scrollHeight;
    };
};

window.getWsUrl = (path) => {
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    return protocol + '//' + location.host + path;
};

window.formatStatus = (status, prefix) => {
    if (!status) return '';
    const clean = status.replace(prefix, '');
    return {
        text: clean,
        class: clean.toLowerCase()
    };
};
