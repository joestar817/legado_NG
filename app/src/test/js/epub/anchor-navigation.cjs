// Run from the repository root with Node. Real navigation functions, synthetic DOM rectangles.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = 'app/src/main/assets/epub/reader.js';
const source = fs.readFileSync(path, 'utf8');
function navigation(text) {
    const restore = text.slice(text.indexOf('    function restoreAnchor('), text.indexOf('    function flowPosition('));
    const move = text.slice(text.indexOf('    async function move(value)'), text.indexOf('    window.__ngEpub = Object.freeze('));
    assert.ok(restore && move);
    return restore + '\n' + move + '\n({ restoreAnchor, move });';
}
function renderer(text, options = {}) {
    const frames = [], logs = [], effects = [];
    const node = { nodeType: 3, length: 1000, connected: true };
    const extent = 428, initialScroll = options.scroll ?? 14 * extent + 23.5;
    let point;
    const context = {
        state: { mode: options.mode || 'HORIZONTAL', pageIndex: options.page ?? 14, pageCount: 19,
            scrolled: !!options.scrolled, ...(options.scrolled ? { scrollOffset: initialScroll } : {}), status: 'ready' },
        generation: 0, galleries: [], Node: { TEXT_NODE: 3 },
        axis: options.axis || 'x', sign: options.sign || 1, extent,
        viewportWidth: extent, viewportHeight: extent, performance: { now: () => 0 },
        body: { contains: n => n.connected },
        document: { createRange: () => ({
            setStart(n, offset) { point = offset; }, setEnd() {},
            getClientRects() {
                if (point === 647 || options.noRect) return [];
                if (options.zeroRect) return [{ width: 0, height: 21 }];
                const offset = options.scrolled ? context.state.scrollOffset : context.state.pageIndex * extent;
                const coordinate = (options.targetPage ?? 15) * extent + 20 - offset;
                return [{ left: context.sign > 0 ? coordinate : extent - coordinate - 6,
                    right: context.sign > 0 ? coordinate + 6 : extent - coordinate,
                    top: context.sign > 0 ? coordinate : extent - coordinate - 21,
                    bottom: context.sign > 0 ? coordinate + 21 : extent - coordinate,
                    width: 6, height: 21 }];
            }
        }) },
        window: { console: { debug: value => logs.push(JSON.parse(value.replace('EpubAloudTrace ', ''))) } },
        locationAnchor: value => options.unmapped ? null : {
            node: options.detached ? { ...node, connected: false } : node, offset: value.textOffset
        },
        textPoint: offset => ({ node, offset }),
        cancelConfiguration: () => context.generation++,
        clearSelection: () => effects.push('selection'), pauseMedia: () => effects.push('media'),
        scrollDocument(offset) {
            context.state.scrollOffset = offset;
            context.state.pageIndex = Math.floor(offset / extent);
        },
        place(page) {
            context.state.pageIndex = Math.max(0, Math.min(18, page));
            if (options.scrolled) context.scrollDocument(context.state.pageIndex * extent);
        },
        locate(fragment) { if (fragment) context.place(8); },
        frame: async () => frames.push({ page: context.state.pageIndex, scroll: context.state.scrollOffset }),
        captureAnchor: () => ({}), sourceLocation: () => ({ textOffset: context.state.pageIndex * 100 }),
        layoutTimings: () => ({}), paintHighlights() {}
    };
    return { api: vm.runInNewContext(navigation(text), context), context, frames, logs, effects, node };
}
async function run(text, options, value, diagnostic = false) {
    const fixture = renderer(text, options);
    await fixture.api.move({ token: '4', page: 0, ...value,
        ...(diagnostic ? { debugAloudTrace: true, debugTraceReason: 'fixture' } : {}) });
    return fixture;
}
function viewport(fixture) {
    return { page: fixture.context.state.pageIndex, scroll: fixture.context.state.scrollOffset };
}
(async () => {
    // The captured failure: native boundary 647 has no rect; real speech offset 649 maps to page 1.
    const regression = await run(source, { page: 1, targetPage: 1 }, { location: { textOffset: 647 } }, true);
    assert.deepEqual(regression.frames.map(f => f.page), [1, 1], 'failed boundary must never publish the first page');
    assert.equal(regression.logs[0].location.result, 'no-rect');
    await regression.api.move({ token: '5', page: 0, location: { textOffset: 649 } });
    assert.deepEqual(regression.frames.map(f => f.page), [1, 1, 1, 1]);

    const failures = [
        [{ noRect: true }, { location: { textOffset: 649 } }],
        [{ noRect: true }, { textOffset: 649 }],
        [{ zeroRect: true }, { location: { textOffset: 649 } }],
        [{ unmapped: true }, { location: { textOffset: 649 } }],
        [{ detached: true }, { location: { textOffset: 649 } }],
        [{ scrolled: true, axis: 'y', noRect: true }, { location: { textOffset: 649 } }],
        [{ scrolled: true, noRect: true }, { preserveScroll: true, textOffset: 649 }]
    ];
    for (const [options, value] of failures) for (const diagnostic of [false, true]) {
        const fixture = await run(source, options, value, diagnostic);
        assert.equal(fixture.context.state.pageIndex, 14);
        if (options.scrolled) assert.equal(fixture.context.state.scrollOffset, 14 * 428 + 23.5,
            'failed anchor must retain the exact scroll offset');
        assert.ok(fixture.frames.every(f => f.page === 14));
        assert.equal(fixture.context.state.status, 'ready');
        assert.equal(fixture.logs.length, diagnostic ? 2 : 0);
    }
    const direct = renderer(source, { scrolled: true, noRect: true });
    assert.equal(direct.api.restoreAnchor({ node: direct.node, offset: 649 }), false);
    assert.deepEqual(viewport(direct), { page: 14, scroll: 14 * 428 + 23.5 });

    const navigationCases = [
        [{}, { location: { textOffset: 649 } }, 15],
        [{ sign: -1 }, { location: { textOffset: 649 } }, 15],
        [{ mode: 'VERTICAL_RL', axis: 'y' }, { textOffset: 649 }, 15],
        [{ mode: 'VERTICAL_LR', axis: 'y', sign: -1 }, { textOffset: 649 }, 15],
        [{ scrolled: true, axis: 'y' }, { location: { textOffset: 649 } }, 15, 15 * 428],
        [{ unmapped: true }, { location: { textOffset: 647 }, textOffset: 649 }, 15],
        [{}, { page: 0 }, 0], [{}, { page: 15 }, 15], [{}, { last: true }, 18],
        [{}, { fragment: 'chapter' }, 8], [{ mode: 'FIXED' }, { page: 6, location: { textOffset: 649 } }, 6],
        [{ scrolled: true }, { preserveScroll: true }, 14, 14 * 428 + 23.5],
        [{}, { preserveScroll: true, textOffset: 649 }, 15]
    ];
    for (const [options, value, page, scroll] of navigationCases) {
        const fixture = await run(source, options, value);
        assert.deepEqual(viewport(fixture), { page, scroll });
        assert.deepEqual(fixture.frames, [{ page, scroll }, { page, scroll }]);
        assert.deepEqual(fixture.effects, value.preserveScroll ? ['selection'] : ['selection', 'media']);
    }
    console.log('Anchor navigation: 647/649 no-flash contract, 7 failure cases with logs on/off, exact scroll rollback and 13 navigation contracts passed.');
})().catch(error => { console.error(error); process.exitCode = 1; });
