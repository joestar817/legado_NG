// Run with Node. This exercises the bundled reader without a browser or third-party packages.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function reader() {
    const rectangle = { left: 0, top: 0, right: 400, bottom: 800, width: 400, height: 800 };
    function element(name) {
        const attributes = new Map();
        const properties = new Map();
        return {
            localName: name, nodeType: 1, children: [], childNodes: [], parentElement: null,
            scrollWidth: 400, scrollHeight: 800, clientWidth: 400, clientHeight: 800,
            style: {
                setProperty(key, value) { properties.set(key, value); },
                getPropertyValue(key) { return properties.get(key) || ''; },
                getPropertyPriority() { return ''; },
            },
            getAttribute(key) { return attributes.get(key) || null; },
            setAttribute(key, value) { attributes.set(key, value); },
            removeAttribute(key) { attributes.delete(key); },
            hasAttribute(key) { return attributes.has(key); },
            querySelectorAll() { return []; }, querySelector() { return null; },
            appendChild(child) { this.children.push(child); child.parentElement = this; return child; },
            insertBefore(child) { return this.appendChild(child); },
            replaceChildren() { this.children = []; },
            getBoundingClientRect() { return rectangle; },
            closest() { return null; }, matches() { return false; },
            remove() {}, addEventListener() {},
        };
    }
    const root = element('html'), body = element('body'), head = element('head');
    root.appendChild(head); root.appendChild(body);
    const fonts = [];
    fonts.ready = Promise.resolve(); fonts.add = () => {}; fonts.delete = () => {};
    const document = {
        documentElement: root, body, head, fonts, images: [], baseURI: 'https://epub.invalid/chapter.xhtml',
        addEventListener() {}, querySelectorAll() { return []; }, querySelector() { return null; },
        getElementById() { return null; }, getElementsByTagName() { return []; },
        getElementsByTagNameNS() { return []; }, getElementsByName() { return []; },
        createElement: element, createElementNS(_namespace, name) { return element(name); },
        createTreeWalker() { return { nextNode() { return null; } }; },
        createRange() { return { selectNodeContents() {}, getBoundingClientRect() { return rectangle; },
            getClientRects() { return []; } }; },
    };
    const css = {
        direction: 'ltr', writingMode: 'horizontal-tb', visibility: 'visible', display: 'block',
        backgroundImage: 'none', backgroundColor: 'transparent', transform: 'none',
        paddingLeft: '0px', paddingRight: '0px', paddingTop: '0px', paddingBottom: '0px',
        borderLeftWidth: '0px', borderRightWidth: '0px', borderTopWidth: '0px', borderBottomWidth: '0px',
        marginTop: '0px', marginBottom: '0px', fontSize: '16px', lineHeight: '20px',
        getPropertyValue() { return ''; },
    };
    const window = {
        document, location: { origin: 'https://epub.invalid' }, URL, Intl, performance,
        Node: { ELEMENT_NODE: 1, TEXT_NODE: 3 }, NodeFilter: { SHOW_ELEMENT: 1, SHOW_TEXT: 4 },
        Range: {}, HTMLMediaElement: class {}, MouseEvent: class {},
        getComputedStyle() { return css; }, matchMedia() { return { matches: false }; },
        requestAnimationFrame(callback) { return setTimeout(callback, 0); },
        getSelection() { return { removeAllRanges() {} }; }, scrollTo() {},
        setTimeout, clearTimeout, console,
    };
    window.window = window;
    const source = process.argv[2] || path.join(__dirname, '../app/src/main/assets/epub/reader.js');
    const code = fs.readFileSync(source, 'utf8');
    vm.runInNewContext(code, window, { filename: 'reader.js' });
    return window;
}

async function main() {
    const early = reader();
    assert.doesNotThrow(() => early.__ngEpub.setHighlights([]));

    const w = reader();
    const pendingNotes = [];
    let paints = 0;
    w.__ngEpubContent = {
        clearLineBoxes: () => false, clearUnderlines() {}, clearStyleImages() {},
        setNotes: () => false, setTitleSegments: () => false,
        loadNotes: () => new Promise((resolve) => pendingNotes.push(resolve)),
        prepareStyleImages: async () => [], prepareUnderlines() {},
        paintUnderlines() { paints++; }, noteRects: () => [],
    };
    const initial = w.__ngEpub.configure({ token: '1', width: 400, height: 800, measurementOnly: true });
    assert.equal(w.__ngEpub.state().status, 'loading');
    assert.doesNotThrow(() => w.__ngEpub.setHighlights([{ from: 0, to: 1 }]));
    assert.equal(paints, 0);
    const latest = w.__ngEpub.configure({ token: '2', width: 400, height: 800, measurementOnly: true });
    w.__ngEpub.setHighlights([]);
    pendingNotes[1]([]);
    await latest;
    const ready = w.__ngEpub.state();
    assert.equal(ready.status, 'ready', ready.error);
    assert.equal(ready.token, '2');
    assert.equal(ready.pageCount, 1);
    assert.equal(ready.pageIndex, 0);
    assert.equal(paints, 1);
    pendingNotes[0]([]);
    await initial;
    assert.equal(w.__ngEpub.state().token, '2');
    assert.equal(w.__ngEpub.state().status, 'ready');

    const rapid = reader();
    const waiters = [];
    let rapidPaints = 0;
    rapid.__ngEpubContent = {
        clearLineBoxes: () => false, clearUnderlines() {}, clearStyleImages() {},
        setNotes: () => false, setTitleSegments: () => false,
        loadNotes: () => new Promise((resolve) => waiters.push(resolve)),
        prepareStyleImages: async () => [], prepareUnderlines() {},
        paintUnderlines() { rapidPaints++; }, noteRects: () => [],
    };
    const requests = Array.from({ length: 20 }, (_, index) => {
        const work = rapid.__ngEpub.configure({ token: String(index), width: 400, height: 800, measurementOnly: true });
        rapid.__ngEpub.setHighlights(index % 2 ? [] : [{ from: 0, to: 1 }]);
        return work;
    });
    waiters[19]([]);
    await requests[19];
    for (let index = 0; index < 19; index++) waiters[index]([]);
    await Promise.all(requests);
    assert.equal(rapid.__ngEpub.state().status, 'ready');
    assert.equal(rapid.__ngEpub.state().token, '19');
    assert.equal(rapid.__ngEpub.state().pageCount, 1);
    assert.equal(rapid.__ngEpub.state().pageIndex, 0);
    assert.equal(rapidPaints, 1);
    console.log('EPUB reader lifecycle checks passed');
}

main().catch((error) => { console.error(error); process.exitCode = 1; });
