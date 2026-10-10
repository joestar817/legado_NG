// Run from the repository root. Real source mapping/navigation functions, synthetic DOM rectangles.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const reader = fs.readFileSync('app/src/main/assets/epub/reader.js', 'utf8');
const content = fs.readFileSync('app/src/main/assets/epub/content.js', 'utf8');
function between(source, from, to) {
    const start = source.indexOf(from), end = source.indexOf(to, start);
    assert.ok(start >= 0 && end > start, from);
    return source.slice(start, end);
}
const code = between(content, '    var pointEntries = null', '    function selection(') + '\n' +
    between(reader, '    function sourcePositionProbe(', '    function interact(') + '\n' +
    between(reader, '    function restoreAnchor(', '    function style(') + '\n' +
    between(reader, '    async function move(value)', '    window.__ngEpub = Object.freeze(') + '\n' +
    'function contains(value) {' + between(reader, "            if (value.action === 'containsPosition') {", "            if (value.action === 'pageBounds')") + '}\n' +
    '({ sourcePositionProbe, restoreAnchor, flowPosition, move, contains });';

function fixture({ text, nodes, boundaries = [], page = 0, scrolled = false, axis = 'x', sign = 1 } = {}) {
    const extent = 428, frames = [];
    nodes.forEach((node, index) => Object.assign(node, { nodeType: 3, length: node.data.length, index, connected: true }));
    const context = {
        sourceText: text, chapterBoundaries: boundaries, entries: nodes.map(node => ({ node, runs: node.runs })), media: [],
        Node: { TEXT_NODE: 3 }, galleries: [], generation: 0, axis, sign, extent, viewportWidth: extent, viewportHeight: extent,
        state: { mode: 'HORIZONTAL', pageIndex: page, pageCount: 10, scrolled, scrollOffset: page * extent + 23.5, scrollLength: 4280, status: 'ready', token: 'old' },
        options: { readerInsets: { left: 20, right: 20, top: 20 } },
        body: { contains: node => node.connected }, document: {}, performance: { now: () => 0 },
        window: { console: { debug() {} } },
        visible: rect => rect.left >= 0 && rect.left < extent && rect.top >= 0 && rect.top < extent,
        cancelConfiguration() { context.generation++; }, clearSelection() {}, pauseMedia() {}, locate() {}, paintHighlights() {},
        place(at) { context.state.pageIndex = Math.max(0, Math.min(9, at)); if (scrolled) context.scrollDocument(context.state.pageIndex * extent); },
        scrollDocument(at) { context.state.scrollOffset = at; context.state.pageIndex = Math.floor(at / extent); },
        frame: async () => frames.push(context.state.pageIndex),
        captureAnchor: () => ({}), sourceLocation: () => ({ textOffset: context.state.pageIndex * 100 }), layoutTimings: () => ({})
    };
    function rectangles(from, to) {
        if (!from || !to) return [];
        const rects = [];
        for (let index = from.node.index; index <= to.node.index; index++) {
            const node = nodes[index], first = index === from.node.index ? from.offset : 0, last = index === to.node.index ? to.offset : node.length;
            for (let at = first; at < last; at++) {
                if (node.hidden || node.hiddenAt === at || !node.paintedSpaces && /\s/.test(node.data.charAt(at))) continue;
                const shift = scrolled ? context.state.scrollOffset : context.state.pageIndex * extent;
                const coordinate = node.page * extent + 30 - shift;
                const leading = sign > 0 ? coordinate : extent - coordinate - 8;
                rects.push({ left: axis === 'x' ? leading : 30, right: axis === 'x' ? leading + 8 : 38,
                    top: axis === 'y' ? leading : 30, bottom: axis === 'y' ? leading + 8 : 38, width: 8, height: axis === 'y' ? 8 : 20 });
            }
        }
        return rects;
    }
    context.document.createRange = () => ({
        setStart(node, offset) { this.startContainer = node; this.startOffset = offset; },
        setEnd(node, offset) { this.endContainer = node; this.endOffset = offset; },
        get collapsed() { return this.startContainer === this.endContainer && this.startOffset === this.endOffset; },
        getClientRects() { return rectangles({ node: this.startContainer, offset: this.startOffset }, { node: this.endContainer, offset: this.endOffset }); },
        getBoundingClientRect() { return this.getClientRects()[0] || { left: 0, right: 0, top: 0, bottom: 0, width: 0, height: 0 }; }
    });
    const api = vm.runInNewContext(code, context);
    context.textPoint = vm.runInNewContext('point', context);
    context.locationAnchor = value => context.textPoint(value.textOffset);
    return { api, context, frames, extent };
}
function captured(options = {}) {
    // Runtime: native boundary 254 had no rect; paragraph start 255 collapsed at DOM 0.
    // Two synthetic native indents have no glyph; the real following text begins at 257.
    return fixture({ text: 'x'.repeat(249) + '上一段。 \n  下一段。', nodes: [
        { data: '上一段。', runs: [[0, 4, 249, 253, 1]], page: 0 },
        { data: '下一段。', runs: [[0, 4, 257, 261, 1]], page: 1 }
    ], ...options });
}
function business(value) { const { probe, ...result } = value; return JSON.parse(JSON.stringify(result)); }

(async () => {
    const current = captured();
    const start = current.context.textPoint(255), end = current.context.textPoint(256, true);
    assert.equal(start.node, end.node);
    assert.equal(start.offset, 0);
    assert.equal(end.offset, 0, 'real content.point must reproduce the collapsed native indent');
    const found = current.api.contains({ action: 'containsPosition', offset: 255, debugProbe: true });
    assert.deepEqual(business(found), { token: 'old', mapped: true, visible: false });
    assert.equal(found.probe.resolvedOffset, 257);
    assert.equal(found.probe.whitespace, true);
    assert.equal(found.probe.collapsed, false);
    for (const offset of [254, 255, 257]) {
        const navigation = captured();
        await navigation.api.move({ token: 'new', page: 0, location: { textOffset: offset } });
        assert.deepEqual(navigation.frames, [1, 1], 'source ' + offset + ' must resolve to the next paragraph page');
        assert.equal(navigation.context.state.status, 'ready');
        assert.equal(navigation.api.contains({ action: 'containsPosition', offset: 255 }).visible, true);
    }
    const direct = captured();
    await direct.api.move({ token: 'new', page: 0, textOffset: 254 });
    assert.deepEqual(direct.frames, [1, 1]);
    for (const options of [{ sign: -1 }, { axis: 'y' }, { axis: 'y', sign: -1 }, { scrolled: true, axis: 'y' }]) {
        const navigation = captured(options);
        await navigation.api.move({ token: 'new', page: 0, location: { textOffset: 254 } });
        assert.deepEqual(navigation.frames, [1, 1]);
    }
    const continuous = captured({ scrolled: true, axis: 'y', page: 3 });
    const oldScroll = continuous.context.state.scrollOffset;
    assert.equal(continuous.api.flowPosition({ location: { textOffset: 254 } }), continuous.extent + 10);
    assert.equal(continuous.context.state.scrollOffset, oldScroll, 'flow measurement must retain exact viewport');
    assert.equal(continuous.api.flowPosition({ textOffset: 254 }), continuous.extent + 10);

    const stopCases = [
        ['empty paragraph', { text: '  \n字', nodes: [{ data: '字', runs: [[0, 1, 3, 4, 1]], page: 1 }] }, 0, 1],
        ['trailing whitespace', { text: '字  ', nodes: [{ data: '字', runs: [[0, 1, 0, 1, 1]], page: 1 }] }, 1],
        ['chapter boundary', { boundaries: [{ offset: 257 }] }, 255, 1],
        ['hidden first glyph', { hidden: true }, 255],
        ['hidden nonwhite character', { hiddenAt: 0 }, 257]
    ];
    for (const [name, options, offset, existingAnchorPage = 0] of stopCases) {
        let target;
        if (options.text) target = fixture(options);
        else {
            target = captured({ boundaries: options.boundaries || [] });
            if (options.hidden) target.context.entries[1].node.hidden = true;
            if (options.hiddenAt != null) target.context.entries[1].node.hiddenAt = options.hiddenAt;
        }
        assert.equal(target.api.contains({ action: 'containsPosition', offset }).mapped, false, name);
        await target.api.move({ token: 'new', page: 0, textOffset: offset });
        assert.deepEqual(target.frames, [existingAnchorPage, existingAnchorPage], name + ' must preserve existing direct-anchor behavior');
    }
    const keptSpace = fixture({ text: '  字', nodes: [{ data: '  字', runs: [[0, 3, 0, 3, 1]], page: 0, paintedSpaces: true }] });
    assert.equal(keptSpace.api.sourcePositionProbe(0).offset, 0, 'painted spaces preserve their original anchor');
    for (const separator of ['\n', '\r\n', '\r']) {
        const text = separator + '\u3000\u3000😀。', at = separator.length + 2;
        const unicode = fixture({ text, nodes: [{ data: '😀。', runs: [[0, 3, at, at + 3, 1]], page: 1 }] });
        assert.equal(unicode.api.sourcePositionProbe(0).offset, at, 'separator and fullwidth-indent support');
        assert.equal(unicode.api.contains({ action: 'containsPosition', offset: 0 }).mapped, true);
    }
    const unmapped = fixture({ text: '  字', nodes: [] });
    assert.deepEqual(business(unmapped.api.contains({ action: 'containsPosition', offset: 0 })), { token: 'old', mapped: false });
    assert.equal(current.api.contains({ action: 'containsPosition', offset: 257 }).probe, undefined, 'diagnostics remain opt-in');
    console.log('EPUB source-position follow: real content.point collapsed indent, native 254/255/257 navigation, RTL/vertical/continuous, paragraph/chapter/hidden/end boundaries and painted-space/Unicode cases passed. Synthetic DOM only.');
})().catch(error => { console.error(error); process.exitCode = 1; });
