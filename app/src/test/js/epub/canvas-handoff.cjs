'use strict';
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync('app/src/main/assets/epub/canvas.js', 'utf8');
const lifecycle = source.slice(source.indexOf('        function discardFrame('), source.indexOf('        function bindLinks('));
const configure = source.slice(source.indexOf('        function layoutKey(config)'), source.indexOf('        async function move(value)'));
function frame(name, visible, events) {
    return { api: { cancelConfiguration() { events.push(name + ':cancel'); }, pauseMedia() { events.push(name + ':pause'); } },
        iframe: { style: { opacity: visible ? '1' : '0' }, remove() { events.push(name + ':iframe-remove'); } },
        backdrop: { remove() { events.push(name + ':background-remove'); } } };
}
{
    const events = [], old = frame('old', true, events);
    const c = { ...old, retainedFrame: null, cancelLoad: () => events.push('cancel-load'),
        window: { __ngEpubCancelBackground() {} } };
    vm.runInNewContext(lifecycle + '\nthis.clear = clear; this.release = releaseRetainedFrame;', c);
    c.clear(true);
    assert.equal(c.retainedFrame.iframe, old.iframe);
    assert.ok(!events.some(e => e.endsWith('remove')), 'Starting a replacement must keep the visible page');
    assert.ok(events.includes('old:pause'), 'Retained pixels cannot keep old media playing');
    Object.assign(c, frame('cancelled-hidden', false, events));
    c.clear(true);
    assert.equal(c.retainedFrame.iframe, old.iframe);
    assert.ok(events.includes('cancelled-hidden:iframe-remove'));
    assert.ok(!events.includes('old:iframe-remove'), 'Superseding a hidden load must keep the last visible page');
    c.release();
    assert.ok(events.includes('old:iframe-remove'));
    assert.equal(c.retainedFrame, null);
}
{
    const events = [], old = frame('old', true, events), next = frame('hidden', false, events);
    const c = { ...next, retainedFrame: old, cancelLoad: null, window: { __ngEpubCancelBackground() {} } };
    vm.runInNewContext(lifecycle + '\nthis.clear = clear;', c);
    c.clear();
    assert.ok(events.includes('old:iframe-remove') && events.includes('hidden:iframe-remove'));
    assert.equal(c.retainedFrame, null);
}
async function readyHandoff() {
    const events = [], old = frame('old', true, events);
    let resolveLayout;
    const layout = new Promise(resolve => { resolveLayout = resolve; });
    const document = { identity: 'new', path: 'new.xhtml', url: 'https://epub.test/new', contentUrl: 'https://epub.test/new.json' };
    const c = { retainedFrame: old, cancelLoad: null, revision: 0, selectionMove: null, configurationKey: null, options: {},
        documents: [document], current: 0, backdrop: { style: {}, remove() {} }, host: { style: {} },
        rect: { width: 400, height: 800 }, highlights: [], selectionTransparent: false,
        check() {}, resize() {}, repaint: async () => {}, selectionProjection: () => null,
        window: { __ngEpubCancelBackground() {} },
        iframe: { style: { set opacity(value) { events.push('new:opacity-' + value); } } },
        api: { configure: () => layout, state: () => ({ status: 'ready' }), setHighlights() {}, setSelectionTransparent() {} } };
    const api = vm.runInNewContext(lifecycle + configure + '\n({ configure });', c);
    const pending = api.configure({ token: 'new', index: 0, documents: [document], width: 400, height: 800 });
    assert.ok(!events.includes('old:iframe-remove'));
    resolveLayout(); await pending;
    assert.equal(c.status.status, 'ready');
    assert.ok(events.indexOf('new:opacity-1') < events.indexOf('old:iframe-remove'), 'Publish replacement before retiring the visible iframe');
}
readyHandoff().then(() => console.log('Canvas handoff: old page survives loading/cancellation, media pauses, ready replacement publishes before release, close frees both.'))
    .catch(error => { console.error(error); process.exitCode = 1; });
