'use strict';
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync('app/src/main/assets/epub/canvas.js', 'utf8');
const configure = source.slice(source.indexOf('        function layoutKey(config)'), source.indexOf('        async function move(value)'));
const chapter = { identity: 'one', path: 'one.xhtml', url: 'https://epub.test/one.xhtml', contentUrl: 'https://epub.test/one.json', occurrence: 0 };
async function check(overrides, retain, expectedLoads, index = 1) {
    let loads = 0, layouts = 0;
    const c = { revision: 0, selectionMove: null, configurationKey: null, options: {}, documents: [chapter], current: 0, backdrop: null,
        host: { style: {} }, rect: { width: 400, height: 800 }, highlights: [], selectionTransparent: false,
        iframe: { style: {} }, check() {}, resize() {}, releaseRetainedFrame() {}, repaint: async () => {}, selectionProjection: () => null,
        api: { configure: async () => { layouts++; }, state: () => ({ status: 'ready' }), setHighlights() {}, setSelectionTransparent() {} },
        async load(index) { loads++; c.current = index; } };
    const api = vm.runInNewContext(configure + '\n({ configure });', c);
    const next = { ...chapter, occurrence: index, ...overrides };
    await api.configure({ token: 'new-window', index, retainWindowPosition: retain, width: 400, height: 800,
        documents: index === 0 ? [next] : [{ path: 'previous.xhtml', url: 'https://epub.test/previous.xhtml' }, next] });
    assert.equal(c.status.status, 'ready'); assert.equal(c.current, index);
    assert.equal(loads, expectedLoads); assert.equal(layouts, 1, 'Reusing the DOM must still apply the new mapping/configuration');
}
(async () => {
    await check({}, true, 0);
    await check({}, false, 1);
    for (const [key, value] of Object.entries({ identity: 'two', path: 'two.xhtml', url: 'https://epub.test/two.xhtml',
        contentUrl: 'https://epub.test/one.json?revision=2', startFragment: 'part2', endFragment: 'part3' })) {
        await check({ [key]: value }, true, 1);
    }
    await check({ identity: 'different' }, true, 1, 0);
    await check({ contentUrl: 'https://epub.test/one.json?revision=2' }, true, 1, 0);
    console.log('Canvas window: identity/index shifts, same-slot replacements and resource/bounds changes (10 cases).');
})().catch(error => { console.error(error); process.exitCode = 1; });
