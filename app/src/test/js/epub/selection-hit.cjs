'use strict';
// Real reader/canvas hit functions with controlled caret geometry. These tests
// cover bridge contracts; browser layout, device gestures and saved ranges need
// their own acceptance evidence.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync('app/src/main/assets/epub/reader.js', 'utf8');
const canvas = fs.readFileSync('app/src/main/assets/epub/canvas.js', 'utf8');
const copy = value => JSON.parse(JSON.stringify(value));
function reader(mode = 'HORIZONTAL') {
    const anchor = Object.freeze({ sourceOffset: 91 }), selection = Object.freeze({ from: 25, to: 91 });
    const context = {
        state: { token: 'child', status: 'ready', mode }, viewportWidth: 428, viewportHeight: 800,
        selectedRange: selection, dragAnchor: anchor, picture: null,
        point: { node: {}, offset: 7, hitOffset: 5 },
        document: { baseURI: 'https://epub.test/chapter.xhtml', elementFromPoint: () => context.picture }, URL,
        caret: () => context.point, nearestVisibleCaret: () => context.point,
        rangeCaret: node => context.point, textOffset: (node, offset) => offset,
        clearSelection() { throw new Error('hit cleared the host projection'); },
        showSelection() { throw new Error('hit installed a DOM-owned selection'); }
    };
    const edge = source.slice(source.indexOf('    function selectionEdge('), source.indexOf('    var visibleCaretNodes'));
    const interact = source.slice(source.indexOf('    function interact(value)'), source.indexOf('    function captureAnchor('));
    assert.ok(edge && interact);
    const api = vm.runInNewContext(`${edge}\n${interact}\n({ interact });`, context);
    return { api, context, anchor, selection };
}

let checks = 0;
for (const [mode, points] of [
    ['HORIZONTAL', [[200, 590, 1], [200, 35, -1], [200, 300, 0]]],
    ['VERTICAL_LR', [[385, 300, 1], [15, 300, -1], [200, 300, 0]]],
    ['VERTICAL_RL', [[15, 300, 1], [385, 300, -1], [200, 300, 0]]],
    ['FIXED', [[385, 590, 0], [15, 35, 0]]]
]) {
    const f = reader(mode);
    // A projection can have no visible DOM range after navigation; Kotlin still
    // owns the fixed endpoint, so edge probing cannot depend on selectedRange.
    f.context.selectedRange = null;
    for (const [x, y, edge] of points) {
        const value = { token: 'child', action: 'hit', nearest: true, x, y,
            edgeBounds: { left: 10, top: 30, right: 390, bottom: 600 } };
        const result = f.api.interact(value);
        assert.equal(result.offset, 7); assert.equal(result.edge, edge);
        assert.equal(f.context.dragAnchor, f.anchor); assert.equal(f.context.selectedRange, null);
        const strict = f.api.interact({ ...value, nearest: false });
        assert.equal(strict.offset, 5); assert.equal(strict.edge, 0); checks++;
    }
}
{
    const f = reader();
    const value = { token: 'child', action: 'hit', nearest: true, x: 100, y: 400,
        edgePoint: { x: 100, y: 900 }, edgeBounds: { left: 0, top: 0, right: 428, bottom: 800 } };
    assert.equal(f.api.interact(value).edge, 1, 'Clamping a caret must retain the original edge direction');
    f.context.point = null;
    const missing = f.api.interact(value); assert.equal(missing.offset, -1); assert.equal(missing.edge, 1,
        'A selected drag can advance past a page without text without inventing a text endpoint');
    const strict = f.api.interact({ ...value, nearest: false });
    assert.equal(strict.offset, -1); assert.equal(strict.edge, 0);
    assert.equal(f.context.selectedRange, f.selection); assert.equal(f.context.dragAnchor, f.anchor);
    assert.equal(f.api.interact({ ...value, token: 'stale' }), null); checks++;
}
{
    const f = reader();
    f.context.picture = { closest: () => ({ localName: 'img', getAttribute: name => name === 'src' ? 'image.jpg' : null }) };
    assert.equal(f.api.interact({ token: 'child', action: 'hit', x: 1, y: 1, nearest: false }).image,
        'https://epub.test/image.jpg');
    assert.equal(f.api.interact({ token: 'child', action: 'hit', x: 1, y: 1, nearest: true }).offset, 7);
    f.context.point = null;
    const imagePage = f.api.interact({ token: 'child', action: 'hit', x: 200, y: 900, nearest: true });
    assert.equal(imagePage.offset, -1); assert.equal(imagePage.edge, 1);
    assert.equal(imagePage.image, undefined, 'Nearest drag must not turn an image into a text endpoint');
    assert.equal(f.context.selectedRange, f.selection); assert.equal(f.context.dragAnchor, f.anchor); checks++;
}
{
    const f = reader(), calls = [], rect = { left: 10, top: 20, width: 300, height: 500 };
    const contentRect = { left: 10, top: 35, width: 300, height: 460 };
    const context = { api: { state: () => f.context.state, interact(value) { calls.push(copy(value)); return f.api.interact(value); } },
        status: { token: 'outer', status: 'ready' }, selectionMove: null, current: 0,
        documents: [{ path: 'chapter.xhtml' }], rect, contentRect };
    const interact = canvas.slice(canvas.indexOf('        function interact(value)'), canvas.indexOf('        return Object.freeze({ configure:'));
    const api = vm.runInNewContext(`${interact}\n({ interact });`, context);
    const result = api.interact({ token: 'outer', action: 'hit', nearest: true, x: 100, y: 490 });
    assert.equal(result.edge, 1, 'Canvas chrome clipping owns the edge, rather than the full child viewport');
    assert.deepEqual(copy(result.selectionBounds), { left: 10, top: 35, right: 310, bottom: 495 });
    assert.equal(calls.at(-1).y, 470);
    assert.deepEqual(calls.at(-1).edgeBounds, { left: 0, top: 15, right: 300, bottom: 475 });
    const before = calls.length;
    api.interact({ token: 'outer', action: 'hit', nearest: false, x: 100, y: 495 });
    assert.equal(calls.length, before, 'Strict hit cannot enter the clipped footer');
    const outside = api.interact({ token: 'outer', action: 'hit', nearest: true, x: 100, y: 700 });
    assert.equal(outside.edge, 1); assert.equal(calls.at(-1).y, 680);
    assert.equal(f.context.selectedRange, f.selection); assert.equal(f.context.dragAnchor, f.anchor); checks++;
}
function projectionReader() {
    const events = [], nativeAnchor = Object.freeze({ chapter: 4, offset: 20 });
    const context = {
        state: { token: 'child', status: 'ready', mode: 'HORIZONTAL', pageIndex: 0, pageCount: 3 },
        sourceText: 'x'.repeat(120), viewportWidth: 428, viewportHeight: 800,
        selectedRange: { from: 20, to: 30 }, dragAnchor: nativeAnchor,
        document: { baseURI: 'https://epub.test/chapter.xhtml', elementFromPoint: () => null }, URL,
        point: { node: {}, offset: 91 }, caret: () => context.point,
        nearestVisibleCaret: () => context.point, rangeCaret: () => context.point,
        textOffset: (node, offset) => offset,
        highlightRange(value) { return { from: value.from, to: value.to }; },
        selectionResult() { return context.selectedRange && { ...context.selectedRange,
            start: { left: 10, right: 20, top: 20, bottom: 40 },
            end: { left: 200, right: 210, top: 700, bottom: 720 } }; },
        window: { getSelection: () => ({ removeAllRanges() {}, addRange() {} }) },
        paintSelection() { events.push(['paint-selection', copy(context.selectedRange)]); },
        paintHighlights() { events.push(['paint-marks', context.state.pageIndex]); context.paintSelection(); }
    };
    const show = source.slice(source.indexOf('    function showSelection('), source.indexOf('    function selectionEdge('));
    const hit = source.slice(source.indexOf('    function selectionEdge('), source.indexOf('    var visibleCaretNodes'));
    const interact = source.slice(source.indexOf('    function interact(value)'), source.indexOf('    function captureAnchor('));
    const api = vm.runInNewContext(`${show}\n${hit}\n${interact}\n({ projectSelection, interact });`, context);
    return { context, api, events, nativeAnchor, code: `${show}\n${hit}` };
}
const projection = { anchor: 20, from: 0, to: 120, x: 200, y: 790, pointerRevision: 5 };
{
    const f = projectionReader();
    const result = f.api.interact({ ...projection, token: 'child', action: 'projectSelection' });
    assert.deepEqual(copy(f.context.selectedRange), { from: 20, to: 91 });
    assert.equal(result.offset, 91); assert.equal(result.edge, 1); assert.equal(result.pointerRevision, 5);
    assert.equal(result.selection.from, 20); assert.equal(f.context.dragAnchor, f.nativeAnchor);
    f.context.point.offset = 7;
    const reverse = f.api.projectSelection(projection);
    assert.deepEqual(copy(f.context.selectedRange), { from: 7, to: 20 });
    assert.equal(reverse.offset, 7); assert.equal(f.context.dragAnchor, f.nativeAnchor); checks++;
}
{
    const f = projectionReader(), before = f.context.selectedRange;
    assert.equal(f.api.interact({ ...projection, token: 'stale', action: 'projectSelection' }), null);
    assert.equal(f.api.projectSelection({ ...projection, anchor: 121 }), null);
    assert.equal(f.context.selectedRange, before);
    f.context.point = null;
    const blank = f.api.projectSelection(projection);
    assert.equal(blank.offset, -1); assert.equal(blank.edge, 1); assert.equal(blank.selection, undefined);
    assert.equal(f.context.selectedRange, before, 'No text cannot invent a source endpoint');
    f.context.state.mode = 'FIXED'; assert.equal(f.api.projectSelection(projection), null);
    f.context.state.mode = 'HORIZONTAL'; f.context.state.scrolled = true;
    assert.equal(f.api.projectSelection(projection), null); checks++;
}
{
    const local = { token: 'child', offset: 91, pointerRevision: 5,
        selection: { start: { left: 1, right: 2, top: 3, bottom: 4 }, end: { left: 5, right: 6, top: 7, bottom: 8 } } };
    const context = { current: 2, rect: { left: 10, top: 20, width: 300, height: 500 },
        contentRect: { left: 10, top: 35, width: 300, height: 460 },
        documents: [{}, {}, { path: 'chapter.xhtml', url: 'doc2' }],
        api: { state: () => ({ status: 'ready', selectionHit: local }) },
        status: { status: 'ready', token: 'outer' }, backdrop: null,
        window: { __ngEpubBackgroundWarnings: () => [] } };
    const code = canvas.slice(canvas.indexOf('        function translate('), canvas.indexOf('        function interact(value)'));
    const api = vm.runInNewContext(`${code}\n({ state, selectionProjection });`, context);
    const packet = { selectionProjection: { x: 110, y: 490, pointerRevision: 5,
        anchors: [{ index: 0, anchor: 9, from: 0, to: 10 }, { index: 2, anchor: 20, from: 0, to: 120 }] } };
    const translated = api.selectionProjection(packet);
    assert.equal(translated.anchor, 20); assert.equal(translated.x, 100); assert.equal(translated.y, 470);
    assert.deepEqual(copy(translated.edgeBounds), { left: 0, top: 15, right: 300, bottom: 475 });
    const receipt = api.state().selectionHit;
    assert.equal(receipt.index, 2); assert.equal(receipt.token, 'outer');
    assert.equal(receipt.selection.start.left, 11); assert.equal(receipt.selection.end.bottom, 28);
    assert.equal(local.selection.start.left, 1, 'Outer coordinates cannot mutate the child receipt');
    assert.equal(api.selectionProjection({}), null); checks++;
}

async function firstFrameProjection() {
    for (const preserve of [true, false]) {
        const f = projectionReader(), frames = [];
        Object.assign(f.context, {
            generation: 0, performance: { now: () => 1 },
            cancelConfiguration() { f.context.generation++; },
            clearSelection() { f.events.push(['clear']); f.context.selectedRange = null; },
            pauseMedia() {}, locate() {},
            place(page) { f.context.state.pageIndex = page; f.events.push(['place', page]); },
            frame() { return new Promise(resolve => frames.push(resolve)); },
            captureAnchor: () => ({}), layoutTimings: () => ({})
        });
        f.context.state.selectionHit = { offset: 1, pointerRevision: -1 };
        const move = source.slice(source.indexOf('    async function move(value)'), source.indexOf('    window.__ngEpub ='));
        const api = vm.runInNewContext(`${f.code}\n${move}\n({ move });`, f.context);
        const pending = api.move({ token: 'next', page: 1, preserveSelection: preserve,
            selectionProjection: preserve ? projection : null });
        assert.equal(frames.length, 1);
        if (preserve) {
            assert.equal(f.context.state.selectionHit.offset, 91);
            assert.deepEqual(copy(f.context.selectedRange), { from: 20, to: 91 });
            assert.equal(f.events[0][0], 'place');
            assert.ok(f.events.some(event => event[0] === 'paint-selection' && event[1].to === 91),
                'The new source endpoint must be painted before the first animation-frame yield');
        } else {
            assert.equal(f.context.state.selectionHit, undefined, 'Ordinary navigation must retire the previous receipt');
            assert.equal(f.context.selectedRange, null); assert.equal(f.events[0][0], 'clear');
        }
        frames.shift()(); await new Promise(setImmediate);
        assert.equal(frames.length, 1);
        frames.shift()(); await pending;
        assert.equal(f.context.state.status, 'ready'); assert.equal(f.context.state.token, 'next'); checks++;
    }
    for (const cancel of [false, true]) {
        const events = [], context = { revision: 0, backdrop: null, current: 0, documents: [], options: {}, status: {}, selectionMove: null,
            rect: { left: 10, top: 20, width: 300, height: 500 }, contentRect: null,
            host: { style: {} }, highlights: [], selectionTransparent: false,
            resize() {}, check() {}, repaint: async () => events.push('repaint'),
            api: { configure: async () => events.push('layout'), state: () => ({ token: 'child', status: 'ready' }),
                interact(value) { if (value.action === 'clear') { events.push('clear'); return { token: 'child' }; }
                    assert.equal(value.action, 'projectSelection'); assert.equal(value.anchor, 20);
                    events.push('projection'); return { offset: 91, pointerRevision: 5 }; },
                setHighlights() {}, setSelectionTransparent() {} },
            iframe: { style: { set opacity(value) { if (value === '1') events.push('visible'); } } }
        };
        const helper = canvas.slice(canvas.indexOf('        function selectionProjection('), canvas.indexOf('        function state()'));
        const configure = canvas.slice(canvas.indexOf('        async function configure(value)'), canvas.indexOf('        async function move(value)'));
        const interact = canvas.slice(canvas.indexOf('        function interact(value)'), canvas.indexOf('        return Object.freeze({ configure:'));
        const api = vm.runInNewContext(`${helper}\n${configure}\n${interact}\n({ configure, interact });`, context);
        const pending = api.configure({ token: 'outer', index: 0, width: 320, height: 600,
            documents: [{ path: 'chapter.xhtml', contentUrl: 'content' }], selectionProjection: {
                x: 110, y: 490, pointerRevision: 5, anchors: [{ index: 0, anchor: 20, from: 0, to: 120 }] } });
        if (cancel) api.interact({ token: 'outer', action: 'clear' });
        await pending;
        assert.equal(context.status.status, 'ready');
        if (cancel) {
            assert.equal(events.includes('projection'), false, 'Cancellation during layout must prevent a later projection from reviving');
            assert.equal(context.status.selectionHit, null);
        } else {
            assert.ok(events.indexOf('projection') < events.indexOf('visible'), 'A new chapter must project before exposing its iframe');
            assert.equal(context.status.selectionHit.offset, 91);
        }
        assert.equal(context.options.selectionProjection, undefined, 'The next ordinary navigation cannot inherit a held pointer');
        checks++;
    }
    {
        const calls = [], context = { revision: 0, current: 0, selectionMove: null, options: {}, status: {},
            rect: { left: 10, top: 20, width: 300, height: 500 },
            contentRect: { left: 10, top: 20, width: 300, height: 500 },
            check() {}, async repaint() { context.contentRect = { left: 10, top: 50, width: 300, height: 440 }; },
            api: { async move(value) { calls.push(copy(value.selectionProjection)); },
                state: () => ({ token: 'child', status: 'ready' }),
                interact(value) { calls.push(copy(value)); return { offset: 91, pointerRevision: 5 }; } } };
        const helper = canvas.slice(canvas.indexOf('        function selectionProjection('), canvas.indexOf('        function state()'));
        const move = canvas.slice(canvas.indexOf('        async function move(value)'), canvas.indexOf('        function translate('));
        const api = vm.runInNewContext(`${helper}\n${move}\n({ move });`, context);
        await api.move({ token: 'outer', page: 1, preserveSelection: true, selectionProjection: {
            x: 110, y: 490, pointerRevision: 5, anchors: [{ index: 0, anchor: 20, from: 0, to: 120 }] } });
        assert.equal(calls[0].edgeBounds.top, 0); assert.equal(calls[1].edgeBounds.top, 30);
        assert.equal(calls[1].action, 'projectSelection'); assert.equal(context.status.selectionHit.offset, 91);
        checks++;
    }
}
firstFrameProjection().then(() => console.log(`Selection hit: ${checks} endpoint, first-frame projection and canvas handoff contracts passed.`))
    .catch(error => { console.error(error); process.exitCode = 1; });
