(function () {
    'use strict';
    if (window.__ngEpubCreateCanvas) return;
    // CSS remains the only interpreter of author backgrounds (including gradients,
    // layered images and blend modes). These layers never participate in text layout.
    var paintedBackgrounds = new WeakMap();
    window.__ngEpubCancelBackground = function (element) {
        var record = element && paintedBackgrounds.get(element);
        if (record && record.cancel) record.cancel();
        if (element) paintedBackgrounds.delete(element);
    };
    window.__ngEpubBackgroundWarnings = function (element) {
        var record = element && paintedBackgrounds.get(element); return record ? record.warnings : [];
    };
    window.__ngEpubPaintBackground = function (element, value) {
        var previous = paintedBackgrounds.get(element);
        if (previous && previous.value === value) return previous.promise;
        window.__ngEpubCancelBackground(element);
        var owner = element.ownerDocument, context = owner.defaultView;
        var record = { value: value, warnings: [], cancel: null, promise: null };
        paintedBackgrounds.set(element, record);
        element.style.background = 'transparent';
        element.style.backgroundBlendMode = 'normal';
        Object.entries(value && value.properties || {}).forEach(function (pair) {
            if (/^background(?:-|$)/.test(pair[0])) element.style.setProperty(pair[0], pair[1]);
        });
        // Decode in the very document that paints the backdrop. A decoded child-frame
        // image is not proof that the parent's no-store image request has completed.
        record.promise = new Promise(function (resolve, reject) {
            var cleanups = [], timeout, frameId = null, finished = false;
            function finish(error) {
                if (finished) return; finished = true;
                context.clearTimeout(timeout);
                if (frameId != null) context.cancelAnimationFrame(frameId);
                cleanups.forEach(function (cleanup) { cleanup(); }); record.cancel = null;
                if (error) { element.style.background = 'transparent';
                    if (paintedBackgrounds.get(element) === record) paintedBackgrounds.delete(element); reject(error); }
                else resolve();
            }
            record.cancel = function () { finish(new Error('EPUB 页面背景等待已取消')); };
            timeout = context.setTimeout(function () { finish(new Error('EPUB 页面背景加载超时')); }, 25000);
            var imageCss = context.getComputedStyle(element).backgroundImage, urls = [], match;
            var pattern = /url\(\s*(?:"((?:\\.|[^"\\])*)"|'((?:\\.|[^'\\])*)'|([^)]*))\s*\)/gi;
            try {
                while ((match = pattern.exec(imageCss))) {
                    var raw = (match[1] == null ? match[2] == null ? match[3].trim() : match[2] : match[1])
                        .replace(/\\([0-9a-f]{1,6}\s?|.)/gi, function (_, escape) {
                            if (!/^[0-9a-f]/i.test(escape)) return escape;
                            var code = parseInt(escape, 16);
                            return String.fromCodePoint(code > 0 && code <= 0x10ffff ? code : 0xfffd);
                        });
                    var url = new context.URL(raw, owner.baseURI);
                    if ((url.origin === context.location.origin && !url.username && !url.password || /^data:image\//i.test(url.href)) && !urls.includes(url.href))
                        urls.push(url.href);
                }
                Promise.all(urls.map(function (url) {
                    return new Promise(function (loaded) {
                        var image = new context.Image(), decoding = false, complete = false;
                        function cleanup() { image.onload = null; image.onerror = null; image.src = ''; loaded(); }
                        cleanups.push(cleanup);
                        function done(failed) {
                            if (complete || finished) return; complete = true;
                            image.onload = null; image.onerror = null;
                            if (failed && !record.warnings.length) record.warnings.push('页面背景加载失败');
                            loaded();
                        }
                        image.onload = function () {
                            if (decoding || complete || finished) return; decoding = true;
                            if (typeof image.decode === 'function') image.decode().then(function () { done(false); }, function () { done(true); });
                            else done(false);
                        };
                        image.onerror = function () { done(true); }; image.src = url;
                        if (image.complete && image.naturalWidth > 0) image.onload();
                    });
                })).then(function () {
                    if (!finished) frameId = context.requestAnimationFrame(function () { finish(); });
                }, finish);
            } catch (error) { finish(error); }
        });
        return record.promise;
    };
    window.__ngEpubCreateCanvas = function (host, documents) {
        var iframe = null, backdrop = null, api = null, options = {}, current = -1, revision = 0;
        var status = { status: 'idle' }, rect = null, contentRect = null, cancelLoad = null, closed = false;
        var highlights = [], selectionTransparent = false, linkHandler = null;
        function check(mine) { if (closed || mine !== revision) throw new Error('EPUB layout cancelled'); }
        function clear() {
            if (cancelLoad) cancelLoad(); cancelLoad = null;
            if (api) { api.cancelConfiguration(); api.pauseMedia(); } api = null;
            if (iframe) iframe.remove(); iframe = null;
            if (backdrop) { window.__ngEpubCancelBackground(backdrop); backdrop.remove(); } backdrop = null;
        }
        function bindLinks() {
            if (api) api.setLinkHandler(linkHandler ? function (value) {
                if (status.status === 'ready') linkHandler(Object.assign({}, value, {
                    token: options.token, index: current, document: documents[current] }));
            } : null);
        }
        function documentRect(full) {
            var inset = full ? {} : options.readerInsets || {};
            var left = Math.max(0, inset.left || 0), top = Math.max(0, inset.top || 0);
            return { left: left, top: top, width: Math.max(1, options.width - left - Math.max(0, inset.right || 0)),
                height: Math.max(1, options.height - top - Math.max(0, inset.bottom || 0)) };
        }
        function resize(full) {
            rect = documentRect(full);
            iframe.style.left = rect.left + 'px'; iframe.style.top = rect.top + 'px';
            iframe.style.width = rect.width + 'px'; iframe.style.height = rect.height + 'px';
        }
        function repaint() {
            if (!api || !backdrop) return;
            var local = api.state();
            var ready = window.__ngEpubPaintBackground(backdrop, local.pageBackground);
            var chrome = options.chromeInsets || {}, top = 0, bottom = 0;
            if (local.bleed && !local.cover) {
                top = local.hideHeader ? 0 : Math.max(0, chrome.top || 0);
                bottom = local.hideFooter ? 0 : Math.max(0, chrome.bottom || 0);
            }
            iframe.style.clipPath = top || bottom ? 'inset(' + top + 'px 0 ' + bottom + 'px 0)' : 'none';
            contentRect = { left: rect.left, top: rect.top + top, width: rect.width, height: Math.max(0, rect.height - top - bottom) };
            return ready;
        }
        async function load(index, mine) {
            clear(); current = index;
            var value = documents[index]; if (!value) throw new Error('EPUB document is outside reading order');
            var url = new URL(value.url, document.baseURI);
            if (url.origin !== location.origin || url.username || url.password || !['http:', 'https:'].includes(url.protocol))
                throw new Error('EPUB document origin mismatch');
            backdrop = document.createElement('div');
            backdrop.setAttribute('data-ng-epub-background', 'canvas');
            backdrop.style.cssText = 'position:absolute;inset:0;pointer-events:none'; host.appendChild(backdrop);
            iframe = document.createElement('iframe');
            iframe.setAttribute('sandbox', 'allow-same-origin allow-scripts');
            iframe.setAttribute('title', value.title || value.path || 'EPUB');
            iframe.style.cssText = 'position:absolute;border:0;opacity:0;background:transparent';
            resize(false); host.appendChild(iframe);
            await new Promise(function (resolve, reject) {
                var target = iframe, timeout;
                function finish(error) {
                    clearTimeout(timeout); target.onload = null; target.onerror = null;
                    if (cancelLoad === cancel) cancelLoad = null;
                    if (error) reject(error); else resolve();
                }
                function cancel() { finish(new Error('EPUB layout cancelled')); }
                cancelLoad = cancel;
                timeout = setTimeout(function () { finish(new Error('EPUB chapter load timed out')); }, 25000);
                target.onload = function () {
                    if (target.contentWindow.location.href === 'about:blank') return;
                    finish();
                };
                target.onerror = function () { finish(new Error('EPUB chapter load failed')); };
                target.src = url.href;
            });
            check(mine);
            if (!['application/xhtml+xml', 'text/html', 'image/svg+xml'].includes(iframe.contentDocument.contentType))
                throw new Error('EPUB chapter resource did not return a document');
            api = window.__ngEpubInstall(iframe.contentWindow); bindLinks();
        }
        async function configure(value) {
            var mine = ++revision, saved = api && value.preservePosition ? api.captureLocation() : null;
            if (backdrop) window.__ngEpubCancelBackground(backdrop);
            options = value; documents = value.documents || documents; status = { status: 'loading', token: value.token };
            try {
                var index = Number.isInteger(value.index) ? value.index : 0;
                if (!api || index !== current) await load(index, mine);
                check(mine);
                var item = documents[current], occurrence = item.occurrence == null ? current : item.occurrence;
                host.style.cssText = 'position:relative;overflow:hidden;width:' + value.width + 'px;height:' + value.height + 'px';
                resize(false);
                var config = Object.assign({}, value, { width: rect.width, height: rect.height,
                    viewportFull: false, externalBackground: true, contentUrl: item.contentUrl,
                    startFragment: item.startFragment, endFragment: item.endFragment,
                    sourceChapters: (value.sourceChapters || []).filter(function (chapter) { return chapter.path === item.path; }),
                    noteMarkers: (value.noteMarkers || []).filter(function (mark) { return mark.occurrence === occurrence; }),
                    titleSegments: (value.titleSegmentsByDocument || {})[occurrence] || value.titleSegments || [],
                    textTransform: (value.textTransforms || {})[occurrence] || value.textTransform || null,
                    location: saved || value.location });
                await api.configure(config); check(mine);
                var local = api.state();
                if (local.status === 'viewport') {
                    resize(local.fullViewport);
                    await api.configure(Object.assign({}, config, { width: rect.width, height: rect.height,
                        viewportFull: local.fullViewport })); check(mine); local = api.state();
                }
                if (local.status !== 'ready') throw new Error(local.error || 'EPUB chapter layout failed');
                await repaint(); check(mine); iframe.style.opacity = '1';
                api.setHighlights(highlights.filter(function (mark) { return mark.occurrence == null || mark.occurrence === occurrence; }));
                api.setSelectionTransparent(selectionTransparent);
                status = { status: 'ready', token: value.token };
            } catch (error) { if (!closed && mine === revision) status = { status: 'error', token: value.token, error: String(error.message || error) }; }
        }
        async function move(value) {
            if (!api) return;
            var target = value.location && Number.isInteger(value.location.index) ? value.location.index : value.index;
            if (Number.isInteger(target) && target !== current)
                return configure(Object.assign({}, options, value, { index: target, preservePosition: false }));
            var mine = ++revision;
            options = Object.assign({}, options, { token: value.token }); status = { status: 'loading', token: value.token };
            try {
                await api.move(value); check(mine);
                var local = api.state(); if (local.status !== 'ready') throw new Error(local.error || 'EPUB navigation failed');
                await repaint(); check(mine); status = { status: 'ready', token: value.token };
            } catch (error) { if (!closed && mine === revision) status = { status: 'error', token: value.token, error: String(error.message || error) }; }
        }
        function translate(r) { return Object.assign({}, r, { left: r.left + rect.left, right: r.right + rect.left,
            top: r.top + rect.top, bottom: r.bottom + rect.top }); }
        function state() {
            var local = api ? api.state() : {};
            var clip = contentRect || rect;
            return Object.assign({}, local, status, { canvas: true, fullViewport: true, index: current,
                document: documents[current], documentRect: contentRect || rect, canvasDocumentKey: documents[current] && documents[current].url,
                warnings: (local.warnings || []).concat(window.__ngEpubBackgroundWarnings(backdrop)),
                media: (local.media || []).map(function (item) { var r = translate(item);
                    return Object.assign({}, r, { left: Math.max(r.left, clip.left), right: Math.min(r.right, clip.left + clip.width),
                        top: Math.max(r.top, clip.top), bottom: Math.min(r.bottom, clip.top + clip.height),
                        visible: item.visible && r.right > clip.left && r.left < clip.left + clip.width &&
                            r.bottom > clip.top && r.top < clip.top + clip.height });
                }), bleedRects: (local.bleedRects || []).map(translate) });
        }
        function interact(value) {
            if (!api || value.token !== status.token || status.status !== 'ready') return null;
            var pointer = ['tap', 'select', 'gallerySwipe', 'activate'].includes(value.action) || value.action === 'hit' && !value.nearest;
            var clip = contentRect || rect;
            if (pointer && (value.x < clip.left || value.x >= clip.left + clip.width || value.y < clip.top || value.y >= clip.top + clip.height))
                return { token: value.token };
            if (value.action === 'setRanges') value = Object.assign({}, value, {
                ranges: (value.ranges || []).filter(function (range) { return range.index === current; }) });
            if (value.action === 'aloud') value = Object.assign({}, value, {
                range: (value.ranges || []).find(function (range) { return range.index === current; }) || value.range || null });
            var result = api.interact(Object.assign({}, value, { token: api.state().token, index: current,
                x: (value.x || 0) - rect.left, y: (value.y || 0) - rect.top }));
            if (!result) return result;
            if (result.selection) result.selection = Object.assign({}, result.selection, {
                start: translate(result.selection.start), end: translate(result.selection.end) });
            if (result.highlightRect) result.highlightRect = translate(result.highlightRect);
            return Object.assign({}, result, { token: value.token, index: current, document: documents[current] });
        }
        return Object.freeze({ configure: configure, move: move, state: state, interact: interact,
            updateStyles: async function (value) {
                if (!api) return;
                var mine = ++revision; options.token = value.token; status = { status: 'loading', token: value.token };
                if (backdrop) window.__ngEpubCancelBackground(backdrop);
                try {
                    var result = await api.updateStyles(value); check(mine);
                    await repaint(); check(mine); var local = api.state();
                    status = { status: local.status, token: value.token, error: local.error }; return result;
                } catch (error) {
                    if (!closed && mine === revision) status = { status: 'error', token: value.token, error: String(error.message || error) };
                    return { error: String(error.message || error) };
                }
            },
            setLinkHandler: function (handler) { linkHandler = typeof handler === 'function' ? handler : null; bindLinks(); },
            cancelConfiguration: function () { revision++; if (cancelLoad) cancelLoad(); if (api) api.cancelConfiguration();
                if (backdrop) window.__ngEpubCancelBackground(backdrop); },
            beginScroll: function () { if (api) api.beginScroll(); },
            scrollBy: function (delta) { if (api) { api.scrollBy(delta); repaint(); } return state(); },
            settle: async function () { if (api) api.captureLocation(); return state(); },
            captureLocation: function () { return api && api.captureLocation(); },
            flowPosition: function (value) { return api && api.flowPosition(value); },
            revealText: function (value) { if (!api) return null; var result = api.revealText(value); repaint(); return result; },
            mediaOverlay: function (value) { if (api) api.mediaOverlay(value); },
            pauseMedia: function () { if (api) api.pauseMedia(); },
            setHighlights: function (values) { highlights = values; if (api) {
                var occurrence = documents[current].occurrence == null ? current : documents[current].occurrence;
                api.setHighlights(values.filter(function (mark) { return mark.occurrence == null || mark.occurrence === occurrence; }));
            } },
            setSelectionTransparent: function (value) { selectionTransparent = !!value; if (api) api.setSelectionTransparent(selectionTransparent); },
            close: function () { closed = true; revision++; clear(); }
        });
    };
})();
