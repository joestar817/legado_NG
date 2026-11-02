'use strict';
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync('app/src/main/assets/epub/canvas.js', 'utf8');
const geometry = source.slice(source.indexOf('        function documentRect('), source.indexOf('        function repaint('));
const layout = source.slice(source.indexOf('        function layoutKey(config)'), source.indexOf('        async function configure(value)'));
const configure = source.slice(source.indexOf('        async function configure(value)'), source.indexOf('        async function move(value)'));
const move = source.slice(source.indexOf('        async function move(value)'), source.indexOf('        function translate('));
const updateStart = source.indexOf('            updateStyles: async function');
const updateStyles = source.slice(updateStart, source.indexOf('            setLinkHandler:', updateStart));
const functions = geometry + layout + source.slice(source.indexOf('        async function load(index, mine)'), source.indexOf('        var selectionMove = null;'));
function fixture() {
    const frames = [], children = [], moves = [], configurations = [], control = {};
    let installed = 0, configured = 0;
    const documentFor = (n, path = `${n}.xhtml`) => ({ identity: `identity-${n}`, path,
        url: `https://epub.test/${path}`, contentUrl: `https://epub.test/${n}.json` });
    const original = { cancelConfiguration() {}, pauseMedia() {}, setLinkHandler() {},
        state: () => ({ status: 'ready' }),
        async updateStyles() { control.styleRevision = (control.styleRevision || 1) + 1; return { updated: true }; } };
    const c = { URL, Promise, setTimeout, clearTimeout, closed: false, revision: 0,
        preloadedFrame: null, documentPreloaded: false, preloadedCaptureFence: false, preloadedLayoutReused: false,
        status: { token: 'accepted', status: 'ready' },
        current: 0, documents: [documentFor(0), documentFor(1), documentFor(2)], options: { width: 400, height: 800 },
        api: original, iframe: null, backdrop: null, cancelLoad: null, location: { origin: 'https://epub.test' },
        rect: { left: 0, top: 0, width: 400, height: 800 }, selectionMove: null, configurationKey: null,
        highlights: [], selectionTransparent: false, repaint: async () => {}, selectionProjection: () => null, releaseRetainedFrame() {},
        document: { baseURI: 'https://epub.test/', createElement(tag) {
            const style = {}; let cssText = '';
            const body = { textContent: control.readingText === false ? 'body { color: black }' : 'Chapter text' };
            Object.defineProperty(style, 'cssText', { get: () => cssText, set(value) {
                cssText = value; for (const key of Object.keys(style)) delete style[key];
            } });
            const frame = { style, attrs: {}, setAttribute(k, v) { this.attrs[k] = v; },
                removeAttribute(k) { delete this.attrs[k]; },
                contentWindow: { location: { href: 'about:blank' },
                    getComputedStyle: () => ({ writingMode: control.writingMode || 'horizontal-tb' }), NodeFilter: { SHOW_TEXT: 4 } },
                contentDocument: { contentType: 'application/xhtml+xml',
                    body: control.bodyMissing ? null : body, documentElement: body,
                    querySelector: selector => control.mediaElement || (control.inlineImage && selector.split(',').includes('img') ? { localName: 'img' } : null),
                    createTreeWalker() {
                        const nodes = [{ data: 'body { color: black }', parentElement: { closest: () => ({ localName: 'style' }) } }];
                        if (control.readingText !== false) nodes.push({ data: 'Chapter text', parentElement: { closest: () => null } });
                        let index = 0; return { nextNode: () => nodes[index++] || null };
                    } },
                remove() { const i = children.indexOf(frame); if (i >= 0) children.splice(i, 1); frame.removed = true; } };
            frame.contentWindow.ownerFrame = frame;
            Object.defineProperty(frame, 'src', { set(url) {
                frame.url = url; frame.contentWindow.location.href = url;
                if (!url.includes('stall')) queueMicrotask(() => frame.onload?.());
            } });
            if (tag === 'iframe') frames.push(frame);
            return frame;
        } },
        host: { style: {},
            appendChild(frame) {
                const i = children.indexOf(frame);
                assert.ok(i < 0 || !frame.url, 'Moving a loaded iframe destroys its browsing context');
                if (i >= 0) children.splice(i, 1);
                children.push(frame);
            },
            insertBefore(frame, before) {
                assert.ok(!children.includes(frame), 'Activation inserts a new background without moving the iframe');
                const i = children.indexOf(before); assert.ok(i >= 0); children.splice(i, 0, frame);
            }
        },
        window: { __ngEpubInstall(frameWindow) {
            installed++;
            let generation = 0, state = { status: 'idle' }, contentRevision = null, size = null;
            return { ...original, cancelConfiguration() { generation++; },
                async configure(value) {
                    const mine = ++generation;
                    configured++; configurations.push(JSON.parse(JSON.stringify(value)));
                    state = { status: 'loading', token: value.token };
                    if (contentRevision == null) contentRevision = control.styleRevision || 1;
                    const gate = control.gate; delete control.gate;
                    if (gate) await gate;
                    if (mine === generation && control.fullViewport && !value.viewportFull) {
                        state = { status: 'viewport', token: value.token, fullViewport: true }; return;
                    }
                    size = { width: value.width, height: value.height };
                    if (mine === generation) state = { status: 'ready', mode: 'HORIZONTAL', media: [], galleryIndexes: [],
                        token: value.token, pageIndex: value.page || 0, bleed: !!control.fullViewport,
                        styleRevision: contentRevision, ...control.state };
                },
                async move(value) { generation++; moves.push(JSON.parse(JSON.stringify(value)));
                    state = { ...state, token: value.token, pageIndex: value.last ? 9 : value.page || 0 }; },
                state() {
                    if (control.assertPhysicalSize && state.status === 'ready') {
                        assert.equal(frameWindow.ownerFrame.style.width, size.width + 'px', 'A settled iframe must keep its physical viewport');
                        assert.equal(frameWindow.ownerFrame.style.height, size.height + 'px', 'Adoption must not shrink and re-expand the ready document');
                    }
                    return state;
                }, setHighlights() {}, setSelectionTransparent() {} };
        }, __ngEpubCancelBackground() {} },
        check(mine) { assert.equal(mine, c.revision); }, bindLinks() {},
        clear() { c.api = null; c.iframe = null; c.backdrop = null; },
        discardFrame(frame) { frame.api?.cancelConfiguration(); frame.api?.pauseMedia(); frame.iframe?.remove(); } };
    vm.runInNewContext(functions + configure + move + '\nthis.preload = preloadDocument; this.load = load; this.discard = discardPreloadedFrame; this.configure = configure; this.move = move; this.updateStyles = ({' + updateStyles + '}).updateStyles;', c);
    c.preloadRuntime = c.preload;
    c.preload = value => c.preloadRuntime({ ...value, layoutRevision: value.layoutRevision || 'private-1',
        nativeCaptureFence: true, layout: { ...c.options, index: value.index, ...value.layout } });
    return { c, frames, children, original, documentFor, control, moves, configurations,
        installed: () => installed, configured: () => configured };
}
(async () => {
    {
        const f = fixture(), c = f.c;
        assert.equal(await c.preloadRuntime({ token: 'accepted', index: 1 }), true);
        assert.equal(c.current, 0); assert.equal(c.api, f.original);
        assert.equal(f.configured(), 0, 'Foreground preload installs only the original runtime');
        assert.equal(c.status.token, 'accepted', 'Speculation cannot publish another token');
        assert.ok(f.frames[0].style.cssText.includes('opacity:0'));
        assert.equal(f.frames[0].attrs.sandbox, 'allow-same-origin allow-scripts');
        await c.preloadRuntime({ token: 'accepted', index: 1 });
        assert.equal(f.frames.length, 1, 'Repeated idle warm requests share one pending document');
        await c.load(1, 0);
        assert.equal(c.current, 1); assert.equal(c.iframe, f.frames[0]);
        assert.equal(c.documentPreloaded, true); assert.equal(c.preloadedFrame, null);
        assert.equal(c.iframe.attrs['aria-hidden'], undefined);
        assert.equal(f.children[f.children.indexOf(c.iframe) - 1], c.backdrop);
        assert.equal(f.frames.length, 1); assert.equal(f.installed(), 1, 'Actual activation reuses the loaded runtime');
    }
    const base = f => ({ token: 'accepted', index: 0, width: 400, height: 800,
        readerInsets: { left: 10, top: 20, right: 30, bottom: 40 }, documents: f.c.documents,
        reader: { fontSize: 18 }, features: { title: false }, titleSegmentsByDocument: { 1: ['next title'] },
        textTransforms: { 1: { from: 'one', to: 'two' } },
        sourceChapters: [{ path: '0.xhtml', index: 0 }, { path: '1.xhtml', index: 1 }],
        noteMarkers: [{ occurrence: 0, offset: 5 }, { occurrence: 1, offset: 9 }] });
    {
        const f = fixture(), c = f.c, extended = c.documents.slice();
        c.documents = extended.slice(0, 2); c.current = 1; c.options = { ...base(f), index: 1 };
        const documents = c.documents, options = c.options, status = c.status, rect = c.rect;
        const target = { ...c.options, index: 2, documents: extended,
            sourceChapters: [{ path: '2.xhtml', index: 62 }], titleSegmentsByDocument: { 2: ['third chapter'] } };
        assert.equal(await c.preloadRuntime({ token: 'accepted', index: 2, documents: extended }), false,
            'Foreground runtime preloading keeps its installed window');
        assert.equal(await c.preload({ token: 'accepted', index: 2, layout: target }), true,
            'The next private chapter can settle before the retained window installs its appended descriptor');
        assert.equal(c.documents, documents); assert.equal(c.options, options); assert.equal(c.status, status);
        assert.equal(c.rect, rect); assert.equal(c.current, 1); assert.equal(c.api, f.original);
        assert.equal(f.configurations[0].sourceChapters[0].index, 62);
        assert.deepEqual(f.configurations[0].titleSegments, ['third chapter']);
        await c.preload({ token: 'accepted', index: 2, layout: target });
        assert.equal(f.frames.length, 1); assert.equal(f.configured(), 1, 'Repeated staging keeps one hidden layout');
        await c.configure({ ...target, token: 'third-chapter-open', page: 3, aloud: null });
        assert.equal(c.documents, extended); assert.equal(c.current, 2);
        assert.equal(f.configured(), 1, 'The normal new-window configure adopts the staged target without reflow');
        assert.equal(f.moves.length, 1); assert.equal(f.moves[0].nativeCaptureFence, true);
        assert.equal(c.preloadedLayoutReused, true); assert.equal(c.status.status, 'ready');
    }
    for (const change of [v => v.reverse(), v => v.pop(), v => v[0].contentUrl += '?changed',
        v => v[1].endFragment = 'changed', v => v[0].occurrence = 4]) {
        const f = fixture(), c = f.c, next = JSON.parse(JSON.stringify(c.documents));
        change(next);
        assert.equal(await c.preload({ token: 'accepted', index: 1, layout: { documents: next } }), false,
            'A private staged window cannot replace, reorder or truncate installed descriptors');
        assert.equal(f.frames.length, 0); assert.equal(c.api, f.original);
    }
    async function warmed(overrides = {}) {
        const f = fixture(); f.control.state = overrides;
        f.c.options = base(f);
        const beforeRect = f.c.rect;
        await f.c.preload({ token: 'accepted', index: 1 });
        assert.equal(f.c.rect, beforeRect, 'Hidden geometry cannot replace the active body rectangle');
        assert.equal(f.c.current, 0); assert.equal(f.c.api, f.original);
        assert.equal(f.c.status.token, 'accepted'); assert.equal(f.c.status.status, 'ready');
        assert.equal(f.configurations[0].width, 360); assert.equal(f.configurations[0].height, 740);
        assert.deepEqual(f.configurations[0].titleSegments, ['next title']);
        assert.deepEqual(f.configurations[0].textTransform, { from: 'one', to: 'two' });
        assert.deepEqual(f.configurations[0].sourceChapters, [{ path: '1.xhtml', index: 1 }]);
        assert.deepEqual(f.configurations[0].noteMarkers, [{ occurrence: 1, offset: 9 }]);
        return f;
    }
    {
        const f = await warmed();
        // Controller's real cross-chapter open always includes the empty aloud marker.
        await f.c.configure({ ...base(f), token: 'activated', index: 1, page: 3, aloud: null });
        assert.equal(f.configured(), 1, 'Exact settled layout is adopted without reflow');
        assert.equal(f.moves.length, 1); assert.equal(f.moves[0].page, 3);
        assert.equal(f.c.api.state().token, 'activated');
        assert.equal(f.c.status.status, 'ready'); assert.equal(f.c.iframe.style.opacity, '1');
    }
    for (const changedWidth of [false, true]) {
        const f = fixture(), c = f.c, originalRect = c.rect;
        f.control.fullViewport = true; f.control.assertPhysicalSize = true;
        c.options = base(f);
        await c.preload({ token: 'accepted', index: 1 }); await Promise.resolve();
        assert.equal(f.configured(), 2, 'Hidden layout completes the same viewport negotiation as active layout');
        assert.equal(f.configurations[0].width, 360); assert.equal(f.configurations[0].height, 740);
        assert.equal(f.configurations[1].width, 400); assert.equal(f.configurations[1].height, 800);
        assert.equal(f.configurations[1].viewportFull, true);
        assert.equal(c.rect, originalRect, 'Hidden full viewport cannot replace the active rectangle');
        await c.configure({ ...base(f), token: 'bleed-active', index: 1, page: 3, aloud: null,
            width: changedWidth ? 420 : 400 });
        assert.equal(f.configured(), changedWidth ? 4 : 2);
        assert.equal(f.moves.length, changedWidth ? 0 : 1);
        assert.equal(c.rect.width, changedWidth ? 420 : 400); assert.equal(c.rect.height, 800);
        assert.equal(c.iframe.style.left, '0px'); assert.equal(c.iframe.style.top, '0px');
        assert.equal(c.api.state().bleed, true); assert.equal(c.status.status, 'ready');
    }
    {
        const f = await warmed();
        assert.equal(f.c.preloadedFrame.api.state().styleRevision, 1);
        await f.c.updateStyles({ token: 'updated-style', documents: [{ key: 'identity-1', charStyles: ['new style'] }] });
        assert.equal(f.c.api, f.original); assert.equal(f.c.current, 0);
        await f.c.configure({ ...base(f), token: 'fresh-style', index: 1 });
        assert.equal(f.c.api.state().styleRevision, 2, 'A changed style payload at the same URL cannot reuse old inactive content');
        assert.equal(f.moves.length, 0); assert.equal(f.frames[0].removed, true);
    }
    {
        const f = await warmed();
        await f.c.move({ token: 'cross-move', page: 3, preserveSelection: false, selectionProjection: null,
            debugAloudTrace: true, debugTraceReason: 'page', location: { index: 1 } });
        assert.equal(f.configured(), 1, 'Actual native cross-document move adopts the settled layout');
        assert.equal(f.moves.length, 1); assert.equal(f.moves[0].page, 3);
        assert.equal(f.moves[0].debugAloudTrace, true); assert.equal(f.moves[0].preserveSelection, false);
        assert.equal(f.c.status.status, 'ready'); assert.equal(f.c.api.state().token, 'cross-move');
    }
    {
        const f = await warmed();
        await f.c.move({ token: 'selection-move', page: 3, preserveSelection: true, location: { index: 1 } });
        assert.equal(f.configured(), 2, 'A retained selection still uses original configure');
        assert.equal(f.moves.length, 0);
    }
    {
        const f = fixture();
        f.c.documents[1] = f.documentFor(1, 'stall-busy.xhtml');
        const pending = f.c.preload({ token: 'accepted', index: 1 });
        f.c.revision++; f.c.status = { token: 'moving', status: 'loading' };
        f.frames[0].onload(); await pending;
        assert.equal(f.configured(), 1, 'An accepted private request can finish while its parent moves');
        await f.c.configure({ token: 'ready', index: 1, width: 400, height: 800 });
        assert.equal(f.configured(), 1, 'Settled private layout remains available to activation');
        assert.equal(f.moves.length, 1); assert.equal(f.c.status.status, 'ready');
    }
    {
        const f = fixture(), c = f.c;
        c.options = { ...base(f), sourceChapters: [{ path: '0.xhtml', index: 0 }] };
        const target = { ...base(f), sourceChapters: [{ path: '1.xhtml', index: 61 }],
            titleSegmentsByDocument: { 1: ['real target title'] } };
        await c.preload({ token: 'accepted', index: 1, layout: target });
        assert.equal(f.configurations[0].sourceChapters[0].index, 61, 'Use target chapter mapping, not the source options');
        await c.configure({ ...target, token: 'target-native-open', index: 1, aloud: null });
        assert.equal(f.configured(), 1); assert.equal(f.moves.length, 1);
        assert.equal(f.moves[0].nativeCaptureFence, true, 'Private adoption retains its native visual-state fence');
        assert.equal(c.preloadedLayoutReused, true);
        await c.move({ token: 'next-page', page: 2 });
        assert.equal(c.preloadedLayoutReused, false, 'A regular page move cannot be reported as layout adoption');
    }
    {
        const f = fixture(), c = f.c; c.options = base(f);
        await c.preload({ token: 'accepted', index: 1 });
        const previous = f.frames[0];
        await c.preload({ token: 'accepted', index: 1, layoutRevision: 'private-2',
            layout: { ...base(f), reader: { fontUrl: 'https://epub.test/new-font.ttf' } } });
        assert.equal(previous.removed, true, 'A new private request revision replaces the same-URL inactive runtime');
        assert.equal(f.frames.length, 2); assert.equal(f.configured(), 2);
    }
    {
        const f = fixture(), c = f.c;
        await c.preloadRuntime({ token: 'accepted', index: 1 });
        await c.updateStyles({ token: 'style-update' });
        assert.equal(f.configured(), 0); assert.equal(f.frames[0].removed, undefined,
            'A foreground runtime-only preload keeps the original style-update behavior');
    }
    for (const navigation of [{ page: 4 }, { last: true }, { fragment: 'part-two' },
        { location: { textOffset: 123 } }, { textOffset: 456 }]) {
        const f = await warmed();
        await f.c.configure({ ...base(f), token: 'navigation', index: 1, ...navigation });
        assert.equal(f.configured(), 1); assert.equal(f.moves.length, 1);
        for (const [key, value] of Object.entries(navigation)) assert.deepEqual(f.moves[0][key], value);
    }
    for (const change of [v => v.width++, v => v.height++, v => v.readerInsets.left++,
        v => v.reader.fontUrl = 'https://epub.test/font-2.ttf', v => v.features.title = true,
        v => v.titleSegmentsByDocument[1] = ['changed title'], v => v.textTransforms[1].to = 'three',
        v => v.noteMarkers[1].offset++, v => v.sourceChapters[1].index++,
        v => v.documents[1].contentUrl += '?changed', v => v.documents[1].startFragment = 'part-two']) {
        const f = await warmed(), value = JSON.parse(JSON.stringify(base(f)));
        change(value); await f.c.configure({ ...value, token: 'changed', index: 1 });
        assert.equal(f.configured(), 2, 'Changed content/style/geometry uses original configure');
        assert.equal(f.moves.length, 0); assert.equal(f.c.status.status, 'ready');
    }
    for (const state of [{ busy: true }, { mode: 'FIXED' }, { mode: 'VERTICAL' }, { scrolled: true }, { cover: true },
        { media: [{}] }, { galleryIndexes: [0] }, { media: undefined }, { galleryIndexes: undefined }]) {
        const f = await warmed(state);
        await f.c.configure({ ...base(f), token: 'complex', index: 1 });
        assert.equal(f.configured(), 2); assert.equal(f.moves.length, 0, 'Complex or unconfirmed layouts reconfigure');
    }
    for (const fullViewport of [false, true]) {
        const f = fixture(); f.c.options = base(f); f.control.fullViewport = fullViewport;
        f.control.assertPhysicalSize = true;
        let resolve; f.control.gate = new Promise(done => { resolve = done; });
        await f.c.preload({ token: 'accepted', index: 1 });
        let finished = false;
        const activation = f.c.configure({ ...base(f), token: 'foreground', index: 1, page: 5 }).then(() => { finished = true; });
        await new Promise(setImmediate);
        assert.equal(f.configured(), 1, 'An exact pending private layout is shared, not cancelled and restarted');
        assert.equal(finished, false); assert.equal(f.c.api.state().status, 'loading');
        resolve(); await activation;
        assert.equal(f.configured(), fullViewport ? 2 : 1, 'Only the original full viewport negotiation may configure twice');
        assert.equal(f.c.preloadedLayoutReused, true); assert.equal(f.moves.length, 1);
        assert.equal(f.moves[0].page, 5); assert.equal(f.moves[0].nativeCaptureFence, true);
        assert.equal(f.c.api.state().token, 'foreground');
    }
    for (const fullViewport of [false, true]) {
        const f = fixture(); f.c.options = base(f); f.control.fullViewport = fullViewport;
        f.control.assertPhysicalSize = true; f.control.inlineImage = true;
        let resolve; f.control.gate = new Promise(done => { resolve = done; });
        await f.c.preloadRuntime({ token: 'accepted', index: 1, layoutRevision: 'foreground-1',
            foregroundPreload: true, nativeCaptureFence: false, layout: { ...base(f), index: 1 } });
        let finished = false;
        const activation = f.c.configure({ ...base(f), token: 'visible-cross-chapter', index: 1, page: 4 })
            .then(() => { finished = true; });
        await new Promise(setImmediate);
        assert.equal(f.configured(), 1, 'Foreground activation shares the original pending resource/layout wait');
        assert.equal(finished, false, 'Foreground speculation cannot publish before resources finish');
        resolve(); await activation;
        assert.equal(f.configured(), fullViewport ? 2 : 1);
        assert.equal(f.c.preloadedLayoutReused, true); assert.equal(f.moves.length, 1);
        assert.equal(f.moves[0].nativeCaptureFence, false, 'Foreground adoption retains its original JS paint frames');
        assert.equal(f.moves[0].page, 4); assert.equal(f.c.api.state().token, 'visible-cross-chapter');
        assert.deepEqual(f.configurations[0].sourceChapters, [{ path: '1.xhtml', index: 1 }]);
        assert.deepEqual(f.configurations[0].titleSegments, ['next title']);
        assert.deepEqual(f.configurations[0].textTransform, { from: 'one', to: 'two' });
    }
    for (const bodyMissing of [false, true]) {
        const f = fixture(); f.c.options = base(f); f.control.inlineImage = true; f.control.bodyMissing = bodyMissing;
        let resolve; f.control.gate = new Promise(done => { resolve = done; });
        await f.c.preload({ token: 'accepted', index: 1 });
        const activation = f.c.configure({ ...base(f), token: 'inline-image', index: 1 });
        await new Promise(setImmediate);
        assert.equal(f.configured(), 1, 'Text with a static inline image shares the original resource wait');
        resolve(); await activation;
        assert.equal(f.configured(), 1); assert.equal(f.c.preloadedLayoutReused, true);
    }
    for (const foreground of [false, true]) for (const mismatch of ['width', 'selection', 'media', 'vertical', 'image-only']) {
        const f = fixture(); f.c.options = base(f);
        if (mismatch === 'media') f.control.mediaElement = {};
        if (mismatch === 'vertical') f.control.writingMode = 'vertical-rl';
        if (mismatch === 'image-only') { f.control.inlineImage = true; f.control.readingText = false; }
        let resolve; f.control.gate = new Promise(done => { resolve = done; });
        if (foreground) await f.c.preloadRuntime({ token: 'accepted', index: 1, layoutRevision: 'foreground-fallback',
            foregroundPreload: true, nativeCaptureFence: false, layout: { ...base(f), index: 1 } });
        else await f.c.preload({ token: 'accepted', index: 1 });
        let finished = false;
        const fallback = f.c.configure({ ...base(f), token: 'fallback', index: 1, page: 5,
            ...(mismatch === 'width' ? { width: 420 } : mismatch === 'selection' ? { preserveSelection: true } : {}) })
            .then(() => { finished = true; });
        await new Promise(setImmediate);
        assert.equal(finished, true, 'An immediate fallback cannot wait for the speculative resource gate');
        await fallback;
        assert.equal(f.configured(), 2, 'Changed geometry, retained selection and unknown media keep the original immediate fallback');
        assert.equal(f.c.preloadedLayoutReused, false);
        resolve(); await new Promise(setImmediate);
        assert.equal(f.c.api.state().token, 'fallback', 'Late speculative completion cannot overwrite the fallback');
    }
    {
        const f = fixture(); f.c.options = base(f);
        let resolve; f.control.gate = new Promise(done => { resolve = done; });
        await f.c.preload({ token: 'accepted', index: 1 });
        const obsolete = f.c.configure({ ...base(f), token: 'obsolete', index: 1 });
        await new Promise(setImmediate);
        await f.c.configure({ ...base(f), token: 'replacement', index: 2 });
        resolve(); await obsolete;
        assert.equal(f.c.api.state().token, 'replacement'); assert.equal(f.c.current, 2);
        assert.equal(f.configured(), 2, 'An interrupted pending activation cannot reconfigure the replacement');
        assert.equal(f.moves.length, 0); assert.equal(f.c.preloadedLayoutReused, false);
    }
    {
        const f = fixture(); f.c.options = base(f);
        let resolve; f.control.gate = new Promise(done => { resolve = done; });
        await f.c.preload({ token: 'accepted', index: 1 });
        const obsolete = f.c.configure({ ...base(f), token: 'obsolete-style', index: 1 });
        await new Promise(setImmediate);
        await f.c.updateStyles({ token: 'replacement-style' });
        f.c.api.cancelConfiguration();
        f.c.api.state = () => ({ status: 'viewport', token: 'replacement-style', fullViewport: true });
        const dimensions = [f.frames[0].style.width, f.frames[0].style.height];
        resolve(); await obsolete;
        assert.equal(f.configured(), 1, 'A same-API style update cancels the old activation before viewport negotiation');
        assert.deepEqual([f.frames[0].style.width, f.frames[0].style.height], dimensions);
        assert.equal(f.c.status.token, 'replacement-style'); assert.equal(f.moves.length, 0);
    }
    for (const empty of [{ action: 'aloud', range: null, ranges: [] }, { range: null }]) {
        const f = fixture(); f.c.options = base(f);
        await f.c.preload({ token: 'accepted', index: 1, layout: { ...base(f), aloud: empty } });
        await f.c.configure({ ...base(f), token: 'empty-paint', index: 1, aloud: null });
        assert.equal(f.configured(), 1, 'Confirmed empty speech paint has the same layout as an absent marker');
        assert.equal(f.c.preloadedLayoutReused, true);
        const inverse = await warmed();
        await inverse.c.configure({ ...base(inverse), token: 'empty-marker', index: 1, aloud: empty });
        assert.equal(inverse.configured(), 1); assert.equal(inverse.c.preloadedLayoutReused, true);
    }
    for (const paint of [{ range: { from: 0, to: 2 } }, { range: null, ranges: [{ from: 0, to: 2 }] }]) {
        const f = await warmed();
        await f.c.configure({ ...base(f), token: 'painted', index: 1, aloud: paint });
        assert.equal(f.configured(), 2, 'Nonempty speech paint still invalidates the prepared layout');
    }
    {
        const f = fixture(), c = f.c;
        c.documents[1] = f.documentFor(1, 'stall-one.xhtml');
        c.documents[2] = f.documentFor(2, 'stall-two.xhtml');
        const first = c.preload({ token: 'accepted', index: 1 });
        const second = c.preload({ token: 'accepted', index: 2 });
        assert.equal(await first, false); assert.equal(f.frames[0].removed, true);
        f.frames[1].onload(); assert.equal(await second, true);
        c.discard(); assert.equal(c.preloadedFrame, null); assert.equal(f.frames[1].removed, true);
        assert.equal(c.current, 0); assert.equal(c.api, f.original);
    }
    {
        const f = fixture(), c = f.c;
        await c.preload({ token: 'accepted', index: 1 });
        c.documents[1] = { ...c.documents[1], contentUrl: 'https://epub.test/changed.json' };
        await c.load(1, 0);
        assert.equal(c.documentPreloaded, false); assert.equal(f.frames[0].removed, true);
        assert.equal(f.frames.length, 2); assert.equal(f.installed(), 2);
    }
    {
        const f = fixture(), c = f.c;
        assert.equal(await c.preload({ token: 'old', index: 1 }), false);
        c.documents[1].url = 'https://foreign.test/chapter.xhtml';
        assert.equal(await c.preload({ token: 'accepted', index: 1 }), false);
        assert.equal(f.frames.length, 0, 'Invalid token and cross-origin requests never create a frame');
        c.documents[1].url = 'https://epub.test/stall-close.xhtml';
        const pending = c.preload({ token: 'accepted', index: 1 });
        c.closed = true; c.discard();
        assert.equal(await pending, false); assert.equal(c.preloadedFrame, null);
    }
    console.log('Canvas preload: exact foreground/private target adoption, original paint/resource waits, full viewport, empty paint, epoch cancellation and immediate complex fallbacks.');
})().catch(error => { console.error(error); process.exitCode = 1; });
