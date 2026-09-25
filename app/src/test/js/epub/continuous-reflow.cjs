// Run from D:/code/legado with Node. No browser/device access or file writes.
// Executes the real parent flow; the child renderer simulates a layout reflow.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
let child;
let scale = 1;
const api = {
    configure: async () => {},
    state: () => ({
        status: 'ready', scrolled: true, scrollAxis: 'y', scrollSign: 1,
        scrollLength: 1000 * scale, scrollOffset: child.scroll,
        location: { offset: child.scroll / scale }, mode: 'HORIZONTAL'
    }),
    setLinkHandler() {}, setHighlights() {}, setSelectionTransparent() {},
    pauseMedia() {}, cancelConfiguration() {},
    scrollBy(delta) { child.scroll += delta; },
    captureLocation() { return { offset: child.scroll / scale }; },
    flowPosition(value) { return value.location.offset * scale; },
    updateStyles: async () => {
        // reader.updateStyles restores the old text anchor at its new pixel offset.
        scale = 2;
        child.scroll *= 2;
        return { reflow: true };
    }
};
function element(type) {
    const value = {
        style: {}, setAttribute() {}, appendChild() {}, remove() {}, replaceChildren() {}
    };
    if (type === 'iframe') {
        child = value;
        value.scroll = 0;
        value.contentDocument = { contentType: 'text/html', addEventListener() {} };
        value.contentWindow = { getSelection: () => ({ rangeCount: 0 }) };
        Object.defineProperty(value, 'src', {
            set() { queueMicrotask(() => value.onload?.()); }
        });
    }
    return value;
}
const context = {
    window: { __ngEpubInstall: () => api },
    document: { baseURI: 'https://local/book', createElement: element },
    URL, setTimeout, clearTimeout,
    requestAnimationFrame: callback => setImmediate(callback)
};
vm.createContext(context);
vm.runInContext(fs.readFileSync('app/src/main/assets/epub/continuous.js', 'utf8'), context);
(async () => {
    const flow = context.window.__ngEpubCreateContinuous(element('host'), [
        { url: '/a', path: 'a', occurrence: 0 }
    ]);
    await flow.configure({ width: 100, height: 100, token: '1', offset: 100 });
    console.log('before', JSON.stringify({ parent: flow.state().offset, child: child.scroll }));
    await flow.updateStyles({ token: '2' });
    console.log('after', JSON.stringify({ parent: flow.state().offset, child: child.scroll }));
    assert.equal(child.scroll, flow.state().offset,
        'Parent and child scroll offsets diverged after style reflow');
})().catch(error => { console.error(error); process.exitCode = 1; });
