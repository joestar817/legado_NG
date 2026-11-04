'use strict';
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync('app/src/main/assets/epub/container.js', 'utf8');
const configure = source.slice(source.indexOf('    async function configure(value)'), source.indexOf('    async function move(value)'));
const chapter = { identity: 'one', path: 'one.xhtml', url: 'https://epub.test/one', contentUrl: 'https://epub.test/one.json' };
async function check(documents, mode, expectedClears) {
    const calls = [], old = { setHighlights() {}, setSelectionTransparent() {}, async configure(value) { calls.push(value); } };
    const c = { flow: old, flowMode: 'canvas', documents: [chapter], key: JSON.stringify([chapter]), revision: 0,
        scrollTask: null, options: {}, highlights: [], selectionTransparent: false, host: { style: {} }, linkHandler: null,
        state: () => ({}), check() {}, clears: 0,
        clear() { c.clears++; c.flow = null; c.flowMode = null; },
        window: { __ngEpubCreateCanvas() { throw new Error('An existing canvas cannot be recreated'); },
            __ngEpubCreateContinuous() { return { ...old, setLinkHandler() {} }; } } };
    const api = vm.runInNewContext(configure + '\n({ configure });', c);
    await api.configure({ token: 'new', documents, containerMode: mode, width: 400, height: 800, retainWindowPosition: true });
    assert.notEqual(c.status.status, 'error', c.status.error);
    assert.equal(c.clears, expectedClears);
    assert.equal(calls.length, 1);
    assert.equal(c.documents, documents);
    assert.equal(c.key, JSON.stringify(documents));
    if (!expectedClears) assert.equal(c.flow, old, 'Window reconciliation must reach the live canvas owner');
}
(async () => {
    await check([chapter], 'canvas', 0);
    await check([{ path: 'previous.xhtml' }, { ...chapter, occurrence: 1 }], 'canvas', 0);
    await check([{ ...chapter, identity: 'revision-two' }], 'canvas', 0);
    await check([{ ...chapter, url: 'https://epub.test/replaced' }], 'canvas', 0);
    await check([chapter], 'continuous', 1);
    console.log('Canvas parent: 5 stable-owner, shifted-window, changed-document/resource and renderer-switch contracts passed.');
})().catch(error => { console.error(error); process.exitCode = 1; });
