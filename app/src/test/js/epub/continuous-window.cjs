'use strict';
// Run with Node from the repository root. Real continuous/container scripts, controlled
// child documents: these are lifecycle/identity contracts, not browser timing tests.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const sources = ['continuous', 'container'].map(name =>
    fs.readFileSync(`app/src/main/assets/epub/${name}.js`, 'utf8'));
const tick = async () => { for (let i = 0; i < 5; i++) await new Promise(setImmediate); };
const copy = value => JSON.parse(JSON.stringify(value));
function documentList(names) {
    return names.map((name, occurrence) => ({
        url: `https://epub.test/${name}.xhtml`, contentUrl: `https://epub.test/content/${name}?key=1`,
        path: `${name}.xhtml`, occurrence, title: name
    }));
}
function fixture() {
    const frames = [], flows = [], holds = new Set(), styleHolds = new Set(), heldFrames = [];
    let sequence = 0, holdNextFrame = false;
    function element(type) {
        const node = { type, style: {}, children: [], parentNode: null, attributes: {},
            setAttribute(name, value) { this.attributes[name] = value; },
            appendChild(child) { child.remove(); child.parentNode = this; this.children.push(child); },
            insertBefore(child, before) {
                child.remove(); child.parentNode = this;
                const index = this.children.indexOf(before);
                this.children.splice(index < 0 ? this.children.length : index, 0, child);
            },
            remove() {
                if (!this.parentNode) return;
                const siblings = this.parentNode.children;
                siblings.splice(siblings.indexOf(this), 1); this.parentNode = null;
            },
            replaceChildren() { for (const child of [...this.children]) child.remove(); }
        };
        Object.defineProperty(node, 'firstChild', { get: () => node.children[0] || null });
        if (type !== 'iframe') return node;
        node.scroll = 0; node.anchor = 0; node.selection = 0; node.configurations = [];
        node.cancelled = 0; node.handlers = {}; node.styleUpdates = [];
        node.contentDocument = { contentType: 'application/xhtml+xml',
            addEventListener(event, callback) { (node.handlers[event] ||= []).push(callback); } };
        node.contentWindow = { frame: node, location: { href: 'about:blank' },
            getSelection: () => ({ rangeCount: node.selection }) };
        node.api = {
            async configure(value) {
                node.configurations.push(copy(value)); node.token = value.token;
                const name = new URL(node.contentWindow.location.href).pathname;
                if (holds.delete(name)) await new Promise(resolve => { node.release = resolve; });
            },
            async updateStyles(value) {
                node.styleUpdates.push(copy(value)); node.token = value.token;
                const name = new URL(node.contentWindow.location.href).pathname;
                if (styleHolds.delete(name)) await new Promise(resolve => { node.releaseStyle = resolve; });
                return { reflow: false };
            },
            state: () => ({ status: 'ready', token: node.token, scrolled: true,
                mode: 'HORIZONTAL', scrollAxis: 'y', scrollSign: 1,
                scrollLength: 1000, scrollOffset: node.scroll, location: { textOffset: node.anchor },
                textLength: 1000, hasSelectableText: true, chapterBoundaries: [] }),
            scrollBy(delta) { node.scroll += delta; },
            captureLocation() { node.anchor = node.scroll; return { textOffset: node.anchor }; },
            flowPosition(value) { return value.location?.textOffset ?? value.textOffset ?? 0; },
            setLinkHandler(handler) { node.linkHandler = handler; },
            setHighlights(values) { node.highlights = copy(values); },
            setSelectionTransparent(value) { node.transparent = value; },
            pauseMedia() {}, cancelConfiguration() { node.cancelled++; },
            interact(value) { if (value.action === 'clear') node.selection = 0; return {}; }
        };
        Object.defineProperty(node, 'src', { set(value) {
            node.contentWindow.location.href = value; queueMicrotask(() => node.onload?.());
        } });
        frames.push(node); return node;
    }
    const host = element('host');
    const context = { window: {
        __ngEpubInstall: child => child.frame.api,
        __ngEpubPaintBackground: async () => {}, __ngEpubCancelBackground() {},
        __ngEpubBackgroundWarnings: () => []
    }, document: { baseURI: 'https://epub.test/book', createElement: element,
        getElementById: () => host }, URL, setTimeout, clearTimeout,
        requestAnimationFrame(callback) {
            if (holdNextFrame) { holdNextFrame = false; heldFrames.push(() => setImmediate(callback)); }
            else setImmediate(callback);
        } };
    vm.createContext(context); vm.runInContext(sources[0], context);
    const create = context.window.__ngEpubCreateContinuous;
    context.window.__ngEpubCreateContinuous = (...args) => {
        const flow = create(...args); flows.push(flow); return flow;
    };
    vm.runInContext(sources[1], context);
    const api = context.window.__ngEpubContainer;
    const options = (names, extra = {}) => ({ containerMode: 'continuous', width: 100, height: 100,
        token: String(++sequence), documents: documentList(names), index: 0,
        readerInsets: { left: 0, right: 0, top: 0, bottom: 0 }, ...extra });
    function liveFrames() { return frames.filter(frame => frame.parentNode?.parentNode === host); }
    function current(name) {
        return liveFrames().find(frame => new URL(frame.contentWindow.location.href).pathname === `/${name}.xhtml`);
    }
    return { api, flows, frames, host, options, current, liveFrames, heldFrames,
        hold(name) { holds.add(`/${name}.xhtml`); },
        holdStyle(name) { styleHolds.add(`/${name}.xhtml`); },
        holdFrame() { holdNextFrame = true; },
        async configure(value) { await api.configure(value); await tick();
            assert.equal(api.state().status, 'ready', JSON.stringify(api.state())); },
        close() { api.close(); }
    };
}

(async () => {
    let checks = 0;
    // Window expansion in both directions keeps the actual iframe and its laid-out child.
    {
        const f = fixture();
        await f.configure(f.options(['b', 'c'], { offset: 240 }));
        const b = f.current('b'), c = f.current('c');
        await f.configure(f.options(['a', 'b', 'c', 'd'], { index: 1, retainWindowPosition: true }));
        assert.equal(f.flows.length, 1); assert.equal(f.current('b'), b); assert.equal(f.current('c'), c);
        assert.equal(b.configurations.length, 1); assert.equal(c.configurations.length, 1);
        assert.equal(f.api.state().index, 1); assert.equal(f.api.state().offset, 240);
        await f.configure(f.options(['b', 'c', 'd'], { index: 0, retainWindowPosition: true }));
        assert.equal(f.current('b'), b); assert.equal(f.api.state().index, 0);
        assert.equal(f.api.state().offset, 240); f.close(); checks++;
    }
    // A completed native neighbour can arrive after the current document is already at its end.
    // Publishing it must preserve the viewport, then let ordinary scrolling enter that child.
    {
        const f = fixture(); await f.configure(f.options(['a'], { offset: 900 }));
        const a = f.current('a');
        assert.equal(f.api.state().atEnd, true);
        f.hold('b');
        const expanded = f.options(['a', 'b'], { retainWindowPosition: true, textOffset: 0 });
        await f.configure(expanded);
        const b = f.current('b'); assert.equal(typeof b.release, 'function');
        assert.equal(f.flows.length, 1); assert.equal(f.current('a'), a);
        assert.equal(a.configurations.length, 1); assert.equal(a.cancelled, 0);
        assert.equal(a.scroll, 900); assert.equal(f.api.state().offset, 900);
        assert.equal(f.api.state().readingLocation.textOffset, 900);
        assert.equal(f.api.state().atEnd, false, 'Late neighbour must remove the old window-end boundary');
        const crossing = f.api.requestScroll({ token: expanded.token, request: 1, delta: 150 });
        await tick();
        assert.equal(f.api.scrollResult(expanded.token, 1), null,
            'Crossing must not report a completed location before the new child is ready');
        b.release(); await crossing; await tick();
        const result = f.api.scrollResult(expanded.token, 1);
        assert.equal(result.status, 'ready'); assert.equal(result.document.path, 'b.xhtml');
        assert.equal(result.index, 1); assert.equal(result.offset, 50);
        assert.equal(result.readingLocation.textOffset, 50);
        assert.equal(f.current('a'), a); assert.equal(f.current('b'), b);
        assert.equal(a.configurations.length, 1); assert.equal(b.configurations.length, 1);
        await f.api.requestScroll({ token: expanded.token, request: 2, delta: -150 });
        const returned = f.api.scrollResult(expanded.token, 2);
        assert.equal(returned.document.path, 'a.xhtml'); assert.equal(returned.offset, 900);
        assert.equal(f.current('a'), a); assert.equal(a.configurations.length, 1);
        f.close(); checks++;
    }
    // An incoming native position can be stale: drain already queued motion before remapping.
    {
        const f = fixture();
        await f.configure(f.options(['a', 'b'], { offset: 100 }));
        const a = f.current('a');
        f.api.scrollBy(135);
        await f.configure(f.options(['x', 'a', 'b'], { index: 1, retainWindowPosition: true,
            location: { textOffset: 100 }, offset: 0 }));
        assert.equal(f.current('a'), a); assert.equal(f.api.state().offset, 235);
        assert.equal(f.api.state().readingLocation.textOffset, 235); f.close(); checks++;
    }
    // Explicit chapter navigation must still position the destination, including last-page entry.
    for (const target of [{ offset: 123 }, { textOffset: 315 }, { last: true }]) {
        const f = fixture(); await f.configure(f.options(['a', 'b']));
        const b = f.current('b');
        const value = f.options(['a', 'b', 'c'], { index: 1, ...target });
        await f.configure(value);
        assert.equal(f.current('b'), b); assert.equal(b.configurations.length, 1);
        assert.equal(f.api.state().index, 1);
        assert.equal(f.api.state().offset, target.last ? 900 : target.textOffset ?? target.offset);
        assert.equal(f.api.state().token, value.token); f.close(); checks++;
    }
    // Two native window publications may overlap while updateWindow awaits settle.
    // Only the newest publication may remap the live flow or become its final token.
    {
        const f = fixture(); await f.configure(f.options(['a', 'b'], { offset: 235 }));
        const a = f.current('a'), b = f.current('b');
        const obsolete = f.options(['x', 'a', 'b'], { index: 1, retainWindowPosition: true });
        const latest = f.options(['a', 'b', 'c'], { index: 0, retainWindowPosition: true });
        await Promise.all([f.api.configure(obsolete), f.api.configure(latest)]);
        await tick();
        const state = f.api.state();
        assert.equal(state.status, 'ready', JSON.stringify(state));
        assert.equal(state.token, latest.token); assert.equal(state.document.path, 'a.xhtml');
        assert.equal(state.index, 0); assert.equal(state.offset, 235);
        assert.equal(state.readingLocation.textOffset, 235);
        assert.equal(f.current('a'), a); assert.equal(f.current('b'), b);
        assert.equal(a.configurations.length, 1); assert.equal(b.configurations.length, 1);
        assert.equal(f.flows.length, 1); assert.equal(f.current('x'), undefined);
        f.close(); checks++;
    }
    // Navigation, style changes and close also supersede a pending window publication.
    for (const operation of ['move', 'updateStyles', 'close']) {
        const f = fixture(); await f.configure(f.options(['a', 'b'], { offset: 235 }));
        const a = f.current('a'), b = f.current('b');
        const obsolete = f.options(['x', 'a', 'b'], { index: 1, retainWindowPosition: true });
        const pending = f.api.configure(obsolete);
        if (operation === 'move') await f.api.move({ token: 'new-navigation', index: 1, textOffset: 315 });
        if (operation === 'updateStyles') await f.api.updateStyles({ token: 'new-style', fontSize: 24 });
        if (operation === 'close') f.api.close();
        await pending; await tick();
        assert.equal(f.flows.length, 1); assert.equal(f.current('x'), undefined);
        if (operation === 'close') {
            assert.ok(a.cancelled > 0); assert.ok(b.cancelled > 0);
            assert.equal(f.host.children.length, 0); assert.equal(f.liveFrames().length, 0);
        } else {
            const state = f.api.state(); assert.equal(state.status, 'ready', JSON.stringify(state));
            assert.equal(f.current('a'), a); assert.equal(f.current('b'), b);
            assert.equal(a.configurations.length, 1); assert.equal(b.configurations.length, 1);
            if (operation === 'move') {
                assert.equal(state.token, 'new-navigation'); assert.equal(state.document.path, 'b.xhtml');
                assert.equal(state.index, 1); assert.equal(state.offset, 315);
            } else {
                assert.equal(state.token, 'new-style'); assert.equal(state.document.path, 'a.xhtml');
                assert.equal(state.index, 0); assert.equal(state.offset, 235);
                assert.equal(a.styleUpdates.length, 1); assert.equal(b.styleUpdates.length, 1);
            }
            f.close();
        }
        checks++;
    }
    // Supersession also happens AFTER remapping, while render waits for its paint frame.
    // A slower new style pass owns loading/ready; releasing the old frame cannot finish it.
    {
        const f = fixture(); await f.configure(f.options(['a', 'b'], { offset: 235 }));
        const a = f.current('a'), b = f.current('b');
        f.holdFrame();
        const windowUpdate = f.api.configure(f.options(['x', 'a', 'b'], {
            index: 1, retainWindowPosition: true
        }));
        await tick();
        assert.equal(f.heldFrames.length, 1, 'Window update did not reach its render frame');
        assert.equal(f.api.state().index, 1, 'Window must already be remapped before supersession');
        assert.equal(f.api.state().status, 'loading');
        f.holdStyle('a');
        const styles = f.api.updateStyles({ token: 'slow-new-style', fontSize: 24 });
        await tick();
        assert.equal(typeof a.releaseStyle, 'function', 'New style pass must be awaiting its child');
        assert.equal(f.api.state().token, 'slow-new-style');
        f.heldFrames.shift()(); await windowUpdate; await tick();
        assert.equal(f.api.state().token, 'slow-new-style');
        assert.equal(f.api.state().status, 'loading', 'Obsolete render frame completed the newer style pass');
        a.releaseStyle(); await styles; await tick();
        assert.equal(f.api.state().status, 'ready'); assert.equal(f.api.state().token, 'slow-new-style');
        assert.equal(f.api.state().index, 1); assert.equal(f.api.state().offset, 235);
        assert.equal(f.current('a'), a); assert.equal(f.current('b'), b);
        assert.equal(a.configurations.length, 1); assert.equal(b.configurations.length, 1);
        f.close(); checks++;
    }
    // Focus and links from a retained child must route through its NEW window index.
    {
        const f = fixture(); const links = [];
        f.api.setLinkHandler(value => links.push(value));
        await f.configure(f.options(['a', 'b'], { offset: 950 }));
        const b = f.current('b');
        await f.configure(f.options(['x', 'a', 'b', 'c'], { index: 1, retainWindowPosition: true }));
        assert.deepEqual(copy(f.api.state().visible), [1, 2]);
        b.handlers.focusin.forEach(callback => callback());
        assert.equal(f.api.state().activeIndex, 2); assert.equal(f.api.state().active.path, 'b.xhtml');
        b.linkHandler({ href: '#target' });
        assert.equal(links.length, 1); assert.equal(links[0].index, 2);
        assert.equal(links[0].document.path, 'b.xhtml');
        f.close(); checks++;
    }
    // Reuse rejection is non-destructive; container then takes the original configure path.
    for (const reason of ['selection', 'viewport', 'style', 'content', 'preserve', 'transform', 'duplicate']) {
        const f = fixture(); const initial = f.options(['a', 'b']); await f.configure(initial);
        const a = f.current('a'); const value = f.options(['a', 'b', 'c'], { retainWindowPosition: true });
        if (reason === 'selection') a.selection = 1;
        if (reason === 'viewport') value.height = 120;
        if (reason === 'style') value.fontSize = 24;
        if (reason === 'content') value.documents[0].contentUrl += '&changed=1';
        if (reason === 'preserve') value.preservePosition = true;
        if (reason === 'transform') value.textTransforms = { 0: { replacement: 'new text' } };
        if (reason === 'duplicate') value.documents[2] = { ...value.documents[0], occurrence: 2 };
        assert.equal(await f.flows[0].updateWindow(value, value.documents), false, reason);
        assert.equal(f.current('a'), a); assert.equal(a.cancelled, 0, reason);
        await f.configure(value);
        assert.notEqual(f.current('a'), a, reason); assert.ok(a.cancelled > 0, reason);
        f.close(); checks++;
    }
    // The outer XHTML URL can stay stable while its staged content key changes.
    // Rejecting reuse must pass the NEW document metadata into the fallback renderer.
    {
        const f = fixture(); await f.configure(f.options(['a', 'b']));
        const a = f.current('a'); const value = f.options(['a', 'b']);
        value.documents[0].contentUrl = 'https://epub.test/content/a?key=2';
        await f.configure(value);
        assert.notEqual(f.current('a'), a);
        assert.equal(f.current('a').configurations[0].contentUrl, value.documents[0].contentUrl);
        f.close(); checks++;
    }
    // A pending old-index preload is cancelled; its late configure completion cannot overwrite
    // the newly mapped source or resurrect an extra iframe in the host.
    {
        const f = fixture(); f.hold('b'); await f.configure(f.options(['a', 'b']));
        const obsolete = f.current('b'); assert.equal(typeof obsolete.release, 'function');
        await f.configure(f.options(['x', 'a', 'b', 'c'], { index: 1, retainWindowPosition: true }));
        const replacement = f.current('b'); assert.notEqual(replacement, obsolete);
        obsolete.release(); await tick();
        assert.equal(obsolete.parentNode.parentNode, null);
        assert.equal(f.current('b'), replacement);
        await f.configure(f.options(['x', 'a', 'b', 'c'], { index: 2, offset: 80 }));
        assert.equal(f.current('b'), replacement); assert.equal(f.api.state().document.path, 'b.xhtml');
        assert.equal(f.api.state().index, 2); assert.equal(f.api.state().offset, 80);
        f.close(); checks++;
    }
    // Occurrence numbers are local to the window; preserve the same document's highlight data.
    {
        const f = fixture(); await f.configure(f.options(['a', 'b'], {
            highlights: [{ occurrence: 0, from: 10, to: 20 }]
        }));
        const a = f.current('a');
        await f.configure(f.options(['x', 'a', 'b'], { index: 1, retainWindowPosition: true }));
        assert.deepEqual(a.highlights, [{ occurrence: 1, from: 10, to: 20 }]);
        f.close(); checks++;
    }
    // Many consecutive forward/backward windows stay bounded and retain the reused destination.
    {
        const f = fixture();
        await f.configure(f.options(['ch0', 'ch1', 'ch2'], { index: 1 }));
        for (const chapter of [...Array.from({ length: 20 }, (_, i) => i + 2),
            ...Array.from({ length: 20 }, (_, i) => 20 - i)]) {
            const expected = f.current(`ch${chapter}`);
            await f.configure(f.options([`ch${chapter - 1}`, `ch${chapter}`, `ch${chapter + 1}`], { index: 1 }));
            if (expected) assert.equal(f.current(`ch${chapter}`), expected);
            assert.ok(f.liveFrames().length <= 3, 'Live frames grew beyond bounded window');
            assert.equal(f.host.children.length, f.liveFrames().length * 2);
            assert.ok(f.api.state().contexts <= 3);
            assert.equal(f.api.state().document.path, `ch${chapter}.xhtml`);
        }
        assert.equal(f.flows.length, 1); f.close();
        assert.equal(f.host.children.length, 0); checks++;
    }
    // Use the native gateway's URL shape, including per-document content revisions.
    {
        const f = fixture();
        const gatewayDocuments = (names, versions) => documentList(names).map((document, i) => ({ ...document,
            url: `${document.url}?document=${names[i]}-key`,
            contentUrl: `https://epub.test/__ng_reader_test/content.json?document=${names[i]}-key&revision=${versions[i]}`
        }));
        await f.configure(f.options(['a', 'b'], { documents: gatewayDocuments(['a', 'b'], [11, 12]), offset: 120 }));
        const a = f.current('a');
        await f.configure(f.options(['a', 'b', 'c'], { documents: gatewayDocuments(['a', 'b', 'c'], [11, 12, 13]),
            retainWindowPosition: true, textOffset: 0 }));
        assert.equal(f.current('a'), a, 'Adding a neighbour must retain a document with unchanged content revision');
        assert.equal(f.api.state().offset, 120);
        await f.configure(f.options(['a', 'b', 'c'], { documents: gatewayDocuments(['a', 'b', 'c'], [14, 12, 13]) }));
        assert.notEqual(f.current('a'), a, 'Changed content revision must still invalidate the visible document');
        f.close(); checks++;
    }
    console.log(`Continuous window: ${checks} reuse, navigation, fallback, stale-load and bounded-DOM contracts passed.`);
})().catch(error => { console.error(error); process.exitCode = 1; });
