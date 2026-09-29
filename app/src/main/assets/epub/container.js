(function () {
    'use strict';
    if (window.__ngEpubContainer) return;
    var host = document.getElementById('ng-epub-continuous'), flow = null, documents = [], key = '';
    var status = { status: 'idle' }, entries = [], revision = 0, options = {}, active = 0;
    var highlights = [], pending = new Set();
    var selectionTransparent = false, linkHandler = null, flowMode = '';
    function clear() {
        pending.forEach(function (cancel) { cancel(); }); pending.clear();
        if (flow) flow.close(); flow = null; flowMode = '';
        entries.forEach(function (entry) { window.__ngEpubCancelBackground(entry.backdrop);
            entry.api.cancelConfiguration(); entry.api.pauseMedia(); entry.frame.remove(); }); entries = [];
        host.replaceChildren();
    }
    function check(mine) { if (mine !== revision) throw new Error('EPUB layout cancelled'); }
    async function fixedEntry(index, mine) {
        var value = documents[index], iframe = document.createElement('iframe'), backdrop = document.createElement('div');
        var url = new URL(value.url, location.href);
        if (url.origin !== location.origin || url.username || url.password || !['http:', 'https:'].includes(url.protocol)) throw new Error('EPUB document origin mismatch');
        iframe.setAttribute('sandbox', 'allow-same-origin allow-scripts');
        iframe.style.cssText = 'position:absolute;border:0;opacity:0;left:0;top:0;width:' + options.width + 'px;height:' + options.height + 'px';
        backdrop.setAttribute('data-ng-epub-background', 'fixed');
        backdrop.style.cssText = 'position:absolute;pointer-events:none';
        host.insertBefore(backdrop, host.firstChild); host.appendChild(iframe);
        await new Promise(function (resolve, reject) {
            var timeout, cancel = function () { clearTimeout(timeout); iframe.onload = null; iframe.remove(); reject(new Error('EPUB layout cancelled')); };
            pending.add(cancel);
            timeout = setTimeout(function () { pending.delete(cancel); cancel(); }, 25000);
            iframe.onload = function () { if (iframe.contentWindow.location.href === 'about:blank') return;
                clearTimeout(timeout); pending.delete(cancel); resolve(); };
            iframe.onerror = function () { pending.delete(cancel); cancel(); };
            iframe.src = url.href;
        });
        check(mine);
        var api = window.__ngEpubInstall(iframe.contentWindow);
        // Register before resource/layout waits so close/configure can cancel this
        // document even while its fixed dimensions are still being measured.
        var entry = { index: index, api: api, frame: iframe, backdrop: backdrop,
            width: options.width, height: options.height, left: 0, top: 0, scale: 1 };
        entries.push(entry);
        var config = Object.assign({}, options, { token: options.token + ':' + index, contentUrl: value.contentUrl,
            noteMarkers: (options.noteMarkers || []).filter(function (mark) { return mark.occurrence === index; }),
            titleSegments: (options.titleSegmentsByDocument || {})[index] || [],
            fixedWidth: value.fixedWidth, fixedHeight: value.fixedHeight, fixedSvg: value.fixedSvg,
            width: options.width, height: options.height, fixed: true, externalBackground: true, flow: 'paginated', preservePosition: false,
            location: null, textOffset: null, fragment: null, startFragment: value.startFragment, endFragment: value.endFragment });
        await api.configure(config); check(mine);
        var state = api.state(); if (state.status !== 'ready') throw new Error(state.error || 'EPUB fixed page failed');
        var width = state.fixedWidth, height = state.fixedHeight;
        iframe.style.width = width + 'px'; iframe.style.height = height + 'px';
        await api.configure(Object.assign({}, config, { width: width, height: height })); check(mine);
        entry.width = width; entry.height = height;
        api.setLinkHandler(function (value) { if (linkHandler && status.status === 'ready') {
            active = index; linkHandler(Object.assign({}, value, { token: options.token, index: index, document: documents[index] }));
        } });
        api.setHighlights(highlights.filter(function (mark) { return mark.occurrence === index; }));
        api.setSelectionTransparent(selectionTransparent); return entry;
    }
    function fixedState() {
        var entry = entries.find(function (e) { return e.index === active; }) || entries[0];
        var local = entry && entry.api.state();
        // Spread indices belong to the transient navigation window. The native footer
        // displays the active document's pagination, not its preloaded neighbours.
        return Object.assign({}, status, { index: active, location: local && local.location,
            fontWarnings: Array.from(new Set(entries.flatMap(function (e) { return e.api.state().fontWarnings || []; }))),
            warnings: Array.from(new Set(entries.flatMap(function (e) { return (e.api.state().warnings || [])
                .concat(window.__ngEpubBackgroundWarnings(e.backdrop)); }))),
            displayPageIndex: local && local.pageIndex || 0, displayPageCount: local && local.pageCount || 1,
            textLength: local && local.textLength || 0, mode: 'FIXED', cover: false, fullViewport: true, scrolled: false,
            hideHeader: true, hideFooter: true,
            media: entries.flatMap(function (e) { return (e.api.state().media || []).map(function (m) {
                return Object.assign({}, m, { left: e.left + m.left * e.scale, right: e.left + m.right * e.scale,
                    top: e.top + m.top * e.scale, bottom: e.top + m.bottom * e.scale });
            }); }) });
    }
    async function showSpread(page, mine) {
        clear();
        var spreads = options.spreads || [];
        page = Math.max(0, Math.min(spreads.length - 1, page));
        var spread = spreads[page]; if (!spread) throw new Error('EPUB spread is missing');
        var slots = spread.center != null ? ['center'] : ['left', 'right'];
        for (var slot of slots) if (spread[slot] != null) await fixedEntry(spread[slot], mine);
        check(mine);
        var left = entries.find(function (e) { return e.index === spread.left; });
        var right = entries.find(function (e) { return e.index === spread.right; });
        var centered = spread.center != null;
        var pageLeft = left || right || entries[0], pageRight = right || left || entries[0];
        var naturalWidth = centered ? entries[0].width : pageLeft.width + pageRight.width;
        var naturalHeight = Math.max.apply(null, entries.map(function (e) { return e.height; }));
        var scale = Math.min(options.width / naturalWidth, options.height / naturalHeight);
        var start = (options.width - naturalWidth * scale) / 2;
        var split = start + pageLeft.width * scale;
        entries.forEach(function (entry) {
            entry.left = start + (!centered && entry === right ? pageLeft.width * scale : 0);
            entry.top = (options.height - entry.height * scale) / 2; entry.scale = scale;
            entry.frame.style.left = entry.left + 'px'; entry.frame.style.top = entry.top + 'px';
            entry.frame.style.transformOrigin = '0 0'; entry.frame.style.transform = 'scale(' + scale + ')'; entry.frame.style.opacity = '1';
            entry.backdrop.style.left = (!centered && entry === right ? split : 0) + 'px';
            entry.backdrop.style.top = '0'; entry.backdrop.style.width = (centered ? options.width : entry === right ? options.width - split : split) + 'px';
            entry.backdrop.style.height = options.height + 'px';
        });
        await Promise.all(entries.map(function (entry) { return window.__ngEpubPaintBackground(entry.backdrop, entry.api.state().pageBackground); }));
        check(mine);
        // A TOC/restore target may be the second page of a spread. Displaying its partner
        // must not silently move the native chapter/progress back to the first page.
        var requested = options.index;
        active = Object.values(spread).includes(requested) ? requested
            : centered ? spread.center : (options.rtl ? spread.right ?? spread.left : spread.left ?? spread.right);
        status = { status: 'ready', token: options.token, pageIndex: page, pageCount: spreads.length };
    }
    async function updateStyles(value) {
        var mine = ++revision;
        options.token = value.token; status.token = value.token;
        if (flow) return flow.updateStyles(value);
        status.status = 'loading';
        entries.forEach(function (entry) { window.__ngEpubCancelBackground(entry.backdrop); });
        try {
            for (var entry of entries) {
                var result = await entry.api.updateStyles(Object.assign({}, value, { token: value.token + ':style:' + entry.index }));
                check(mine); if (result.error) throw new Error(result.error);
                await window.__ngEpubPaintBackground(entry.backdrop, entry.api.state().pageBackground); check(mine);
            }
            status.status = 'ready';
        } catch (error) { if (mine === revision) status = { status: 'error', token: value.token, error: String(error.message || error) }; }
    }
    async function configure(value) {
        var mine = ++revision, previous = state();
        options = value; status = { status: 'loading', token: value.token };
        if (Array.isArray(value.highlights)) highlights = value.highlights;
        if (typeof value.selectionTransparent === 'boolean') selectionTransparent = value.selectionTransparent;
        try {
            var nextKey = JSON.stringify(value.documents.map(function (d) { return d.url; }));
            if (key !== nextKey || flowMode !== value.containerMode || !flow) {
                clear(); documents = value.documents; key = nextKey;
                if (value.containerMode === 'continuous') flow = window.__ngEpubCreateContinuous(host, documents);
                else if (value.containerMode === 'canvas') flow = window.__ngEpubCreateCanvas(host, documents);
                if (flow) { flowMode = value.containerMode; flow.setLinkHandler(linkHandler); }
            }
            host.style.cssText = 'position:relative;overflow:hidden;width:' + value.width + 'px;height:' + value.height + 'px';
            if (flow) {
                flow.setHighlights(highlights); flow.setSelectionTransparent(selectionTransparent);
                await flow.configure(value); check(mine);
            } else {
                var index = value.preservePosition && Number.isInteger(previous.index) ? previous.index : value.index || 0;
                var page = value.spreads.findIndex(function (spread) { return Object.values(spread).includes(index); });
                await showSpread(!value.preservePosition && Number.isInteger(value.spreadPage) ? value.spreadPage : page, mine);
            }
        } catch (error) { if (mine === revision) status = { status: 'error', token: value.token, error: String(error.message || error) }; }
    }
    async function move(value) {
        if (flow) {
            var index = value.location && value.location.index;
            if (Number.isInteger(index)) value = Object.assign({}, value, { index: index });
            return flow.move(value);
        }
        var mine = ++revision;
        options = Object.assign({}, options, { token: value.token });
        status = { status: 'loading', token: value.token };
        try {
            var page = value.location && Number.isInteger(value.location.index)
                ? options.spreads.findIndex(function (spread) { return Object.values(spread).includes(value.location.index); }) : value.page || 0;
            await showSpread(page, mine);
        }
        catch (error) { if (mine === revision) status = { status: 'error', token: value.token, error: String(error.message || error) }; }
    }
    function state() {
        if (status.status === 'error') return status;
        if (!flow) return fixedState();
        var current = flow.state();
        if (flowMode === 'canvas') return current;
        return Object.assign({}, current, { canvas: true, fullViewport: true, scrollOvershoot: current.overshoot,
            hideHeader: current.cover || current.bleedHeader, hideFooter: current.cover });
    }
    function interact(value) {
        if (flow) return flow.interact(value);
        if (value.token !== status.token || status.status !== 'ready') return null;
        if (value.action === 'pageBounds') return { token: value.token, pages: entries.map(function (e) {
            var result = e.api.interact({ token: e.api.state().token, action: 'pageBounds', index: e.index });
            return Object.assign({}, result.pages[0], { group: 'fixed:' + status.pageIndex });
        }) };
        if (value.action === 'aloud') {
            entries.forEach(function (e) { e.api.interact({ token: e.api.state().token, action: 'aloud',
                range: (value.ranges || []).find(function (r) { return r.index === e.index; }) || null }); });
            return { token: value.token };
        }
        if (value.action === 'clear') {
            entries.forEach(function (e) { e.api.interact({ token: e.api.state().token, action: 'clear' }); });
            return { selection: null, token: value.token };
        }
        if (value.action === 'setRanges') {
            var selected = [];
            entries.slice().sort(function (a, b) { return a.index - b.index; }).forEach(function (e) {
                var ranges = value.ranges.filter(function (r) { return r.index === e.index; });
                var result = e.api.interact({ token: e.api.state().token, action: 'setRanges', ranges: ranges });
                if (result && result.selection) {
                    ['start', 'end'].forEach(function (key) {
                        var r = result.selection[key]; r.left = e.left + r.left * e.scale; r.right = e.left + r.right * e.scale;
                        r.top = e.top + r.top * e.scale; r.bottom = e.top + r.bottom * e.scale;
                    }); selected.push(result.selection);
                }
            });
            return { token: value.token, selection: selected.length ? { start: selected[0].start, end: selected[selected.length - 1].end } : null };
        }
        var entry = ['tap','select','gallerySwipe','hit'].includes(value.action) ? entries.find(function (e) {
            return value.x >= e.left && value.x < e.left + e.width * e.scale && value.y >= e.top && value.y < e.top + e.height * e.scale;
        }) : entries.find(function (e) { return e.index === (Number.isInteger(value.index) ? value.index : active); });
        if (!entry) return { token: value.token };
        active = entry.index;
        var result = entry.api.interact(Object.assign({}, value, { token: entry.api.state().token,
            x: ((value.x || 0) - entry.left) / entry.scale, y: ((value.y || 0) - entry.top) / entry.scale }));
        if (result && result.selection) ['start','end'].forEach(function (key) {
            var rect = result.selection[key]; rect.left = entry.left + rect.left * entry.scale; rect.right = entry.left + rect.right * entry.scale;
            rect.top = entry.top + rect.top * entry.scale; rect.bottom = entry.top + rect.bottom * entry.scale;
        });
        if (result && result.highlightRect) {
            var r = result.highlightRect;
            r.left = entry.left + r.left * entry.scale; r.right = entry.left + r.right * entry.scale;
            r.top = entry.top + r.top * entry.scale; r.bottom = entry.top + r.bottom * entry.scale;
        }
        return result && Object.assign({}, result, { token: value.token, index: entry.index });
    }
    window.__ngEpubContainer = Object.freeze({ configure: configure, updateStyles: updateStyles, move: move, state: state, interact: interact,
        setLinkHandler: function (handler) { linkHandler = typeof handler === 'function' ? handler : null;
            if (flow) flow.setLinkHandler(linkHandler); },
        cancelConfiguration: function () { revision++; pending.forEach(function (cancel) { cancel(); }); pending.clear();
            if (flow && flow.cancelConfiguration) flow.cancelConfiguration();
            else entries.forEach(function (e) { e.api.cancelConfiguration(); window.__ngEpubCancelBackground(e.backdrop); }); },
        pauseMedia: function () { if (flow) flow.pauseMedia(); else entries.forEach(function (e) { e.api.pauseMedia(); }); },
        mediaOverlay: function (value) { if (flow) flow.mediaOverlay(value); else entries.forEach(function (e) {
            e.api.mediaOverlay(e.index === active ? value : {}); }); },
        revealText: function (value) { if (flow) return flow.revealText(value);
            var entry = entries.find(function (e) { return e.index === active; }); return entry && entry.api.revealText(value); },
        prepareSelection: function (value) { if (flow && flow.prepareSelection) return flow.prepareSelection(value); },
        flowPosition: function (value) { return flow && flow.flowPosition ? flow.flowPosition(value) : null; },
        beginScroll: function () { if (flow) flow.beginScroll(); },
        scrollBy: function (delta) { if (flow) flow.scrollBy(delta); },
        settle: async function () { if (flow) await flow.settle(); return state(); },
        captureLocation: function () { if (flow && flow.captureLocation) flow.captureLocation(); return state().location; },
        setHighlights: function (values) { highlights = values; if (flow) flow.setHighlights(values); else entries.forEach(function (e) {
            e.api.setHighlights(values.filter(function (mark) { return mark.occurrence === e.index; }));
        }); },
        setSelectionTransparent: function (value) {
            selectionTransparent = !!value;
            if (flow) flow.setSelectionTransparent(selectionTransparent);
            else entries.forEach(function (e) { e.api.setSelectionTransparent(selectionTransparent); });
        },
        close: function () { revision++; clear(); },
    });
})();
