// Run from repository root with Node. Real runtime functions, synthetic mappings/resources.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const content = fs.readFileSync('app/src/main/assets/epub/content.js', 'utf8');
const reader = fs.readFileSync('app/src/main/assets/epub/reader.js', 'utf8');
function section(source, from, to) {
    const start = source.indexOf(from), end = source.indexOf(to, start);
    assert.ok(start >= 0 && end > start);
    return source.slice(start, end);
}
// Reference: b5049906a point(), preserving enumeration order and affinity at shared boundaries.
function reference(entries, media, position, after) {
    let closest = null, distance = Infinity;
    entries.forEach(entry => entry.runs.forEach(r => {
        const delta = after ? (position <= r[2] ? r[2] - position + 1 : position > r[3] ? position - r[3] : 0)
            : (position < r[2] ? r[2] - position : position >= r[3] ? position - r[3] + 1 : 0);
        if (delta < distance) {
            distance = delta;
            closest = { node: entry.node, offset: r[4] ? r[0] + Math.max(0, Math.min(r[1] - r[0], position - r[2])) : r[after ? 1 : 0] };
        }
    }));
    media.forEach(item => {
        const delta = position < item.start ? item.start - position : position >= item.end ? position - item.end + 1 : 0;
        if (delta < distance) { distance = delta; closest = { node: item.node, offset: 0 }; }
    });
    return closest;
}
const pointSource = section(content, '    var pointEntries', '    function selection(range)');
const context = vm.createContext({ entries: [], media: [], visits: 0 });
vm.runInContext(pointSource.replace('var item = node.item, r = item.run;', 'visits++; var item = node.item, r = item.run;'), context);
let seed = 37, checks = 0;
function random(n) { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed % n; }
for (let fixture = 0; fixture < 150; fixture++) {
    context.entries = Array.from({ length: random(60) }, (_, id) => ({ node: id, runs: Array.from({ length: random(4) }, () => {
        const from = random(100), size = random(20);
        return [random(3), 5 + random(20), from, from + size, random(2)];
    }) }));
    context.media = Array.from({ length: random(8) }, (_, id) => ({ node: id + 100, start: random(100), end: 100 + random(30) }));
    for (let p = -1; p <= 140; p++) for (const after of [false, true]) {
        assert.equal(JSON.stringify(context.point(p, after)), JSON.stringify(reference(context.entries, context.media, p, after)));
        checks++;
    }
}
context.entries = Array.from({ length: 10000 }, (_, i) => ({ node: i, runs: [[0, 10, i * 10, i * 10 + 10, true]] }));
context.media = [];
context.point(99995, false); context.visits = 0;
assert.equal(context.point(99995, false).node, 9999);
assert.ok(context.visits < 50, `Long-chapter tail query visited ${context.visits} runs`);
console.log(`point: ${checks} reference comparisons passed; 10000-run tail query visits ${context.visits}`);

async function resourceCancellation() {
    const listeners = new Map();
    const img = { complete: false, naturalWidth: 1, getAttribute() { return ''; },
        addEventListener(k, v) { listeners.set(k, v); }, removeEventListener(k) { listeners.delete(k); } };
    const c = vm.createContext({ Promise, resourceWaits: new Set(), resourceWarnings: new Set(),
        document: { images: [img], fonts: { ready: Promise.resolve() } } });
    vm.runInContext(section(reader, '    function resources()', '    function place(page)'), c);
    const waiting = c.resources();
    assert.equal(listeners.size, 2);
    for (const cancel of c.resourceWaits) cancel();
    await assert.rejects(waiting, /取消/);
    assert.equal(listeners.size, 0); assert.equal(c.resourceWaits.size, 0);
    img.complete = true;
    await c.resources();
    assert.equal(c.resourceWaits.size, 0);
    console.log('resources: cancelled wait detaches listeners; subsequent configuration completes');
}
async function contentReuse() {
    let requests = 0, applied = 0, layouts = 0, pendingSignal;
    const c = vm.createContext({ URL, AbortController, Promise,
        generation: 0, textRequest: null, contentCache: null, resourceWaits: new Set(), state: {},
        resourceWarnings: new Set(), sourceNodes: [],
        document: { baseURI: 'https://epub.invalid/chapter' },
        window: { location: { origin: 'https://epub.invalid' }, __ngEpubContent: {
            apply() { applied++; }, loadFonts: () => Promise.resolve([])
        } }, indexContentNodes() {}, configureLayout() { layouts++; },
        fetch: async (_, options) => { requests++; return { ok: true, json: async () => ({ text: 'abc' }) }; }
    });
    vm.runInContext(section(reader, '    function cancelConfiguration()', '    function indexContentNodes()') +
        section(reader, '    async function configure(value)', '    function applyChapterBounds(value)'), c);
    const input = { token: '1', contentUrl: 'https://epub.invalid/content?revision=1' };
    await c.configure(input); await c.configure({ ...input, token: '2' });
    assert.equal(requests, 1); assert.equal(layouts, 2);
    await c.configure({ ...input, contentUrl: 'https://epub.invalid/content?revision=2' });
    assert.equal(requests, 2);
    c.fetch = (_, options) => new Promise((resolve, reject) => {
        pendingSignal = options.signal;
        pendingSignal.addEventListener('abort', () => reject(new Error('aborted')));
    });
    const old = c.configure({ ...input, contentUrl: 'https://epub.invalid/content?revision=3' });
    const count = applied;
    c.cancelConfiguration();
    await old;
    assert.equal(pendingSignal.aborted, true); assert.equal(applied, count);
    console.log('configure: same revision fetches once; new revision reloads; cancelled fetch cannot apply');
}
(async () => { await resourceCancellation(); await contentReuse(); })().catch(error => { console.error(error); process.exitCode = 1; });
