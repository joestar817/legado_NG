'use strict';
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync(process.argv[2] || 'app/src/main/assets/epub/canvas.js', 'utf8');
const code = source.slice(source.indexOf('        function layoutKey(config)'), source.indexOf('        async function move(value)'));
const chapter = { identity: 'one', path: 'one.xhtml', url: 'https://epub.test/one', contentUrl: 'https://epub.test/one.json', occurrence: 0 };
async function check(change, expectedLayouts, full = false, localStatus = 'ready', initialChange = {}) {
    let layouts = 0, moves = [], sizes = [];
    const local = { status: 'ready', cover: full, pageIndex: 0, pageCount: 10 };
    const c = { revision: 0, selectionMove: null, configurationKey: null, options: {}, documents: [chapter], current: 0,
        backdrop: null, host: { style: {} }, rect: null, highlights: [], selectionTransparent: false,
        iframe: { style: {} }, check() {}, releaseRetainedFrame() {}, repaint: async () => {}, selectionProjection: () => null,
        resize(full) { c.rect = { width: c.options.width, height: full ? 800 : 760 }; sizes.push(c.rect.height); },
        api: { async configure() { layouts++; local.status = 'ready'; }, async move(value) { moves.push(value); local.status = 'ready'; },
            state: () => local, setHighlights() {}, setSelectionTransparent() {} },
        async load(index) { c.current = index; } };
    const api = vm.runInNewContext(code + '\n({ configure });', c);
    const initial = { token: 'first', index: 0, width: 400, height: 800, documents: [chapter],
        readerStyle: { fontSize: 18 }, sourceChapters: [{ path: chapter.path, chapter: 3, offset: 0 }],
        titleSegmentsByDocument: { 0: [{ text: 'Title' }] }, features: { decoration: true }, ...initialChange };
    await api.configure(initial);
    assert.equal(c.status.status, 'ready');
    local.status = localStatus;
    const update = { ...initial, token: 'second', index: 1, retainWindowPosition: true, textOffset: 125,
        documents: [{ path: 'previous.xhtml' }, { ...chapter, occurrence: 1 }],
        titleSegmentsByDocument: { 0: [], 1: [{ text: 'Title' }] }, ...change };
    await api.configure(update);
    assert.equal(c.status.status, 'ready');
    assert.equal(layouts, expectedLayouts);
    assert.equal(moves.length, expectedLayouts === 1 ? 1 : 0);
    if (moves.length) {
        assert.equal(moves[0].token, 'second'); assert.equal(moves[0].textOffset, 125);
        assert.equal(moves[0].preserveScroll, true, 'A window refresh cannot reset the visible transform');
        assert.equal(sizes.at(-1), full ? 800 : 760, 'Retain the full author canvas when required');
    }
}
(async () => {
    await check({}, 1);
    await check({}, 1, true);
    await check({ readerStyle: { fontSize: 20 } }, 2);
    await check({ width: 420 }, 2);
    await check({ readerInsets: { top: 40 } }, 2);
    await check({ features: { decoration: false } }, 2);
    await check({ noteMarkers: [{ id: 'note', occurrence: 1, end: 200 }] }, 2);
    await check({ titleSegmentsByDocument: { 1: [{ text: 'Changed' }] } }, 2);
    await check({ textTransforms: { 1: { replace: true } } }, 2);
    await check({ unknownLayoutOption: true }, 2);
    await check({ retainWindowPosition: false }, 2);
    await check({ fragment: 'heading' }, 2);
    await check({ fixed: true }, 2);
    await check({ last: true }, 2);
    await check({}, 2, false, 'loading');
    // Captured from the installed reader: only this empty native marker differed
    // when an unchanged ready document was retained after a neighbour arrived.
    const emptyAloud = { action: 'aloud', range: null, ranges: [] };
    await check({ aloud: undefined }, 1, false, 'ready', { aloud: emptyAloud });
    await check({ aloud: emptyAloud }, 1);
    await check({ aloud: null }, 1, false, 'ready', { aloud: emptyAloud });
    await check({ aloud: emptyAloud }, 1, false, 'ready', { aloud: null });
    await check({ aloud: { range: { start: 1, end: 3 } } }, 2, false, 'ready', { aloud: emptyAloud });
    await check({ aloud: undefined }, 2, false, 'ready', { aloud: { ranges: [{ start: 1, end: 3 }] } });
    await check({ aloud: undefined }, 2, false, 'ready', { aloud: [] });
    console.log('Canvas window layout: 22 retained-layout, empty native aloud and real-layout/paint invalidation contracts passed.');
})().catch(error => { console.error(error); process.exitCode = 1; });
