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
        var retainedFrame = null, configurationKey = null;
        var preloadedFrame = null, documentPreloaded = false, preloadedCaptureFence = false, preloadedLayoutReused = false;
        function check(mine) { if (closed || mine !== revision) throw new Error('EPUB layout cancelled'); }
        function discardFrame(frame) {
            if (!frame) return;
            if (frame.api) { frame.api.cancelConfiguration(); frame.api.pauseMedia(); }
            if (frame.iframe) frame.iframe.remove();
            if (frame.backdrop) { window.__ngEpubCancelBackground(frame.backdrop); frame.backdrop.remove(); }
        }
        function releaseRetainedFrame() {
            discardFrame(retainedFrame); retainedFrame = null;
        }
        function clear(retain) {
            if (cancelLoad) cancelLoad(); cancelLoad = null;
            var frame = { api: api, iframe: iframe, backdrop: backdrop };
            if (retain && iframe && iframe.style.opacity === '1') {
                releaseRetainedFrame(); retainedFrame = frame;
                if (api) { api.cancelConfiguration(); api.pauseMedia(); }
            } else discardFrame(frame);
            api = null; iframe = null; backdrop = null; configurationKey = null;
            if (!retain) releaseRetainedFrame();
        }
        function bindLinks() {
            if (api) api.setLinkHandler(linkHandler ? function (value) {
                if (status.status === 'ready') linkHandler(Object.assign({}, value, {
                    token: options.token, index: current, document: documents[current] }));
            } : null);
        }
        function documentRect(full, value) {
            value = value || options;
            var inset = full ? {} : value.readerInsets || {};
            var left = Math.max(0, inset.left || 0), top = Math.max(0, inset.top || 0);
            return { left: left, top: top, width: Math.max(1, value.width - left - Math.max(0, inset.right || 0)),
                height: Math.max(1, value.height - top - Math.max(0, inset.bottom || 0)) };
        }
        function resize(full) {
            rect = documentRect(full);
            setFrameRect(iframe, rect);
        }
        function setFrameRect(frame, box) {
            frame.style.left = box.left + 'px'; frame.style.top = box.top + 'px';
            frame.style.width = box.width + 'px'; frame.style.height = box.height + 'px';
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
            var value = documents[index]; if (!value) throw new Error('EPUB document is outside reading order');
            var url = new URL(value.url, document.baseURI);
            if (url.origin !== location.origin || url.username || url.password || !['http:', 'https:'].includes(url.protocol))
                throw new Error('EPUB document origin mismatch');
            var pending = preloadedFrame, reused = null;
            if (pending && samePreloadedDocument(pending.document, value)) {
                await pending.promise; check(mine);
                if (pending === preloadedFrame && pending.api) {
                    reused = pending; reused.activationRevision = mine; preloadedFrame = null;
                }
            }
            if (preloadedFrame) discardPreloadedFrame();
            clear(true); current = index; documentPreloaded = !!reused;
            preloadedCaptureFence = !!(reused && reused.nativeCaptureFence);
            backdrop = document.createElement('div');
            backdrop.setAttribute('data-ng-epub-background', 'canvas');
            backdrop.style.cssText = 'position:absolute;inset:0;pointer-events:none;opacity:0';
            if (reused) host.insertBefore(backdrop, reused.iframe);
            else host.appendChild(backdrop);
            iframe = reused ? reused.iframe : document.createElement('iframe');
            iframe.setAttribute('sandbox', 'allow-same-origin allow-scripts');
            iframe.setAttribute('title', value.title || value.path || 'EPUB');
            iframe.style.cssText = 'position:absolute;border:0;opacity:0;background:transparent';
            if (reused && reused.layoutRect) {
                rect = reused.layoutRect; setFrameRect(iframe, rect);
            } else resize(false);
            // Moving an already loaded iframe resets its browsing context. Keep the
            // warm frame mounted; only insert its background underneath it.
            if (!reused) host.appendChild(iframe);
            if (reused) {
                iframe.removeAttribute('aria-hidden'); api = reused.api; bindLinks();
                return reused;
            }
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
        function samePreloadedDocument(a, b) {
            return !!(a && b && a.identity && a.identity === b.identity && a.path === b.path &&
                a.url === b.url && a.contentUrl === b.contentUrl &&
                a.startFragment === b.startFragment && a.endFragment === b.endFragment);
        }
        function discardPreloadedFrame() {
            var value = preloadedFrame; preloadedFrame = null;
            if (!value) return;
            value.cancelled = true;
            if (value.cancel) value.cancel();
            discardFrame(value);
        }
        function ownsPreloadedLayout(record) {
            return !closed && !record.cancelled && (preloadedFrame === record ||
                api === record.api && record.activationRevision === revision);
        }
        // One hidden neighbour may settle its original layout while idle. Activation
        // still owns navigation, marks, projection, background paint and publication.
        function preloadDocument(value) {
            var index = value.index;
            if (closed || value.token !== status.token || status.status !== 'ready' ||
                !Number.isInteger(index) || index === current) return Promise.resolve(false);
            var available = documents, staged = value.layout && value.layout.documents;
            if (staged != null) {
                if (!Array.isArray(staged) || staged.length < documents.length || documents.some(function (item, i) {
                    var next = staged[i];
                    return !samePreloadedDocument(item, next) ||
                        (item.occurrence == null ? i : item.occurrence) !== (next.occurrence == null ? i : next.occurrence) ||
                        item.fixedSvg !== next.fixedSvg || item.fixedWidth !== next.fixedWidth || item.fixedHeight !== next.fixedHeight;
                })) return Promise.resolve(false);
                // The private producer may stage an appended neighbour without changing
                // the installed window or the active layout. Configure still commits it.
                available = staged;
            }
            var item = available[index]; if (!item) return Promise.resolve(false);
            if (preloadedFrame && samePreloadedDocument(preloadedFrame.document, item) &&
                    preloadedFrame.layoutRevision === value.layoutRevision) return preloadedFrame.promise;
            discardPreloadedFrame();
            var url = new URL(item.url, document.baseURI);
            if (url.origin !== location.origin || url.username || url.password || !['http:', 'https:'].includes(url.protocol))
                return Promise.resolve(false);
            var frame = document.createElement('iframe');
            frame.setAttribute('sandbox', 'allow-same-origin allow-scripts');
            frame.setAttribute('aria-hidden', 'true');
            frame.setAttribute('title', item.title || item.path || 'EPUB');
            frame.style.cssText = 'position:absolute;border:0;opacity:0;background:transparent';
            frame.style.width = Math.max(1, options.width || 1) + 'px';
            frame.style.height = Math.max(1, options.height || 1) + 'px';
            var record = { iframe: frame, document: item, api: null, cancel: null, promise: null,
                requestKey: null, layoutKey: null, layoutRect: null, layoutPromise: null,
                canSharePending: false, cancelled: false, activationRevision: null, layoutRevision: value.layoutRevision,
                nativeCaptureFence: !!value.nativeCaptureFence };
            preloadedFrame = record;
            record.promise = new Promise(function (resolve, reject) {
                var timeout, finished = false;
                function finish(error) {
                    if (finished) return; finished = true;
                    clearTimeout(timeout); frame.onload = null; frame.onerror = null; record.cancel = null;
                    if (error) reject(error); else resolve();
                }
                record.cancel = function () { finish(new Error('EPUB preload cancelled')); };
                timeout = setTimeout(function () { finish(new Error('EPUB preload timed out')); }, 25000);
                frame.onload = function () {
                    if (frame.contentWindow.location.href !== 'about:blank') finish();
                };
                frame.onerror = function () { finish(new Error('EPUB preload failed')); };
                host.appendChild(frame); frame.src = url.href;
            }).then(function () {
                if (closed || preloadedFrame !== record) return false;
                if (!['application/xhtml+xml', 'text/html', 'image/svg+xml'].includes(frame.contentDocument.contentType))
                    throw new Error('EPUB preload did not return a document');
                record.api = window.__ngEpubInstall(frame.contentWindow);
                if (value.layout && !value.layout.fixed && !/^scrolled-/.test(value.layout.flow || '')) {
                    // The native owner supplies the actual target's complete options;
                    // the original configure path still owns resource/viewport readiness.
                    var layoutOptions = Object.assign({}, value.layout), box = documentRect(false, layoutOptions);
                    var config = documentConfiguration(layoutOptions, item, index, box);
                    ['page', 'last', 'location', 'fragment', 'textOffset', 'preservePosition',
                        'preserveScroll', 'retainWindowPosition', 'selectionProjection'].forEach(function (key) { delete config[key]; });
                    record.layoutRect = box; setFrameRect(frame, box);
                    var key = preloadedLayoutKey(config);
                    record.requestKey = key;
                    // Use the reader's text/image-only distinction. Inline images keep
                    // its original resource wait and complete viewport negotiation.
                    var body = frame.contentDocument.body || frame.contentDocument.documentElement, hasText = false;
                    var writing = body ? frame.contentWindow.getComputedStyle(body).writingMode || '' : '';
                    if (body) {
                        var walker = frame.contentDocument.createTreeWalker(body, frame.contentWindow.NodeFilter.SHOW_TEXT), node;
                        while ((node = walker.nextNode())) {
                            if (node.data.trim() && (!node.parentElement || !node.parentElement.closest('style,script,svg'))) {
                                hasText = true; break;
                            }
                        }
                    }
                    record.canSharePending = (record.nativeCaptureFence || value.foregroundPreload === true) && hasText &&
                        !/^(vertical|sideways)-/.test(writing) &&
                        !frame.contentDocument.querySelector('svg,math,video,audio,iframe,object,embed,.duokan-image-gallery');
                    record.layoutPromise = record.api.configure(config).then(async function () {
                        if (!ownsPreloadedLayout(record)) return false;
                        var local = record.api.state();
                        if (local.status === 'viewport') {
                            // Use the active path's original viewport negotiation. Bleed
                            // text keeps its full author canvas, including under chrome.
                            box = documentRect(local.fullViewport, layoutOptions);
                            record.layoutRect = box; setFrameRect(frame, box);
                            await record.api.configure(Object.assign({}, config, { width: box.width, height: box.height,
                                viewportFull: local.fullViewport }));
                            if (!ownsPreloadedLayout(record)) return false;
                            local = record.api.state();
                        }
                        if (reusableLayout(local)) { record.layoutKey = key; return true; }
                        return false;
                    }).catch(function () { return false; });
                }
                return true;
            }).catch(function () {
                if (preloadedFrame === record) discardPreloadedFrame();
                return false;
            });
            return record.promise;
        }
        var selectionMove = null;
        function layoutKey(config) {
            var navigation = ['token', 'index', 'documents', 'titleSegmentsByDocument', 'textTransforms',
                'page', 'last', 'location', 'fragment', 'textOffset', 'preservePosition', 'preserveScroll',
                'retainWindowPosition', 'selectionProjection'];
            function ordered(value) {
                if (Array.isArray(value)) return value.map(ordered);
                if (!value || typeof value !== 'object') return value;
                var result = {};
                Object.keys(value).sort().forEach(function (key) { result[key] = ordered(value[key]); });
                return result;
            }
            var layout = {};
            Object.keys(config).forEach(function (key) { if (!navigation.includes(key)) layout[key] = config[key]; });
            // An empty native aloud marker paints no ranges, just like an omitted one.
            // Keep active paint in the key so it still invalidates the settled layout.
            var paint = layout.aloud;
            if (paint == null || typeof paint === 'object' && !Array.isArray(paint) && paint.range == null &&
                    (paint.ranges == null || Array.isArray(paint.ranges) && paint.ranges.length === 0)) delete layout.aloud;
            return JSON.stringify(ordered(layout));
        }
        function documentConfiguration(value, item, index, box) {
            var occurrence = item.occurrence == null ? index : item.occurrence;
            return Object.assign({}, value, { width: box.width, height: box.height,
                viewportFull: false, externalBackground: true, contentUrl: item.contentUrl,
                startFragment: item.startFragment, endFragment: item.endFragment,
                sourceChapters: (value.sourceChapters || []).filter(function (chapter) { return chapter.path === item.path; }),
                noteMarkers: (value.noteMarkers || []).filter(function (mark) { return mark.occurrence === occurrence; }),
                titleSegments: (value.titleSegmentsByDocument || {})[occurrence] || value.titleSegments || [],
                textTransform: (value.textTransforms || {})[occurrence] || value.textTransform || null });
        }
        function preloadedLayoutKey(config) {
            var value = Object.assign({}, config);
            // Cross-document moves carry navigation/fence/trace flags that do not
            // change settled geometry. Empty paint is normalized by layoutKey.
            ['preserveSelection', 'nativeCaptureFence', 'debugAloudTrace', 'debugTraceReason'].forEach(function (key) { delete value[key]; });
            return layoutKey(value);
        }
        function reusableLayout(local) {
            return local.status === 'ready' && !local.busy && !local.scrolled && local.mode === 'HORIZONTAL' &&
                !local.cover && Array.isArray(local.media) && local.media.length === 0 &&
                Array.isArray(local.galleryIndexes) && local.galleryIndexes.length === 0;
        }
        async function configure(value) {
            preloadedLayoutReused = false;
            selectionMove = value;
            var mine = ++revision, saved = api && value.preservePosition ? api.captureLocation() : null;
            if (backdrop) window.__ngEpubCancelBackground(backdrop);
            options = Object.assign({}, value); delete options.selectionProjection;
            var previousDocument = documents[current];
            documents = value.documents || documents; status = { status: 'loading', token: value.token };
            try {
                var index = Number.isInteger(value.index) ? value.index : 0;
                var nextDocument = documents[index];
                var sameDocument = previousDocument && nextDocument && previousDocument.identity &&
                    previousDocument.identity === nextDocument.identity &&
                    previousDocument.path === nextDocument.path && previousDocument.url === nextDocument.url &&
                    previousDocument.contentUrl === nextDocument.contentUrl &&
                    previousDocument.startFragment === nextDocument.startFragment && previousDocument.endFragment === nextDocument.endFragment;
                if (value.retainWindowPosition && api && sameDocument) current = index;
                var prepared = null;
                if (!api || index !== current || !sameDocument) prepared = await load(index, mine);
                check(mine);
                var item = documents[current], occurrence = item.occurrence == null ? current : item.occurrence;
                host.style.cssText = 'position:relative;overflow:hidden;width:' + value.width + 'px;height:' + value.height + 'px';
                if (!prepared || !prepared.requestKey) resize(false);
                var config = documentConfiguration(value, item, current, prepared && prepared.requestKey ? documentRect(false) : rect);
                config.location = saved || value.location;
                var key = layoutKey(config), candidateKey = prepared && prepared.requestKey ? preloadedLayoutKey(config) : null;
                var matching = prepared && prepared.requestKey && prepared.requestKey === candidateKey && !config.fixed &&
                    !config.preserveScroll && !config.preserveSelection;
                if (matching && prepared.layoutKey == null && prepared.canSharePending && prepared.layoutPromise) {
                    await prepared.layoutPromise; check(mine);
                }
                var local = api.state();
                if (matching && prepared.layoutKey === candidateKey && reusableLayout(local)) {
                    resize(local.cover || local.bleed);
                    await api.move(Object.assign({}, config, { nativeCaptureFence: preloadedCaptureFence }));
                    preloadedLayoutReused = true;
                } else if (value.retainWindowPosition && sameDocument && !config.fixed && !config.fragment && !config.last &&
                        key === configurationKey && local.status === 'ready' && !local.busy) {
                    // Only the neighbouring window changed. Reflow restores the original body
                    // before an asynchronous resource wait, briefly discarding its page transform.
                    // Keep the exact settled layout and use the existing navigation/token path.
                    resize(local.cover || local.bleed);
                    await api.move({ token: value.token, preserveScroll: true,
                        location: config.location, textOffset: config.textOffset });
                } else {
                    if (prepared) { prepared.cancelled = true; if (prepared.requestKey) resize(false); }
                    await api.configure(config);
                }
                check(mine); local = api.state();
                if (local.status === 'viewport') {
                    resize(local.fullViewport);
                    await api.configure(Object.assign({}, config, { width: rect.width, height: rect.height,
                        viewportFull: local.fullViewport })); check(mine); local = api.state();
                }
                if (local.status !== 'ready') throw new Error(local.error || 'EPUB chapter layout failed');
                await repaint(); check(mine);
                var projection = selectionProjection(value);
                var projected = projection && api.interact(Object.assign({}, projection,
                    { token: api.state().token, action: 'projectSelection' }));
                api.setHighlights(highlights.filter(function (mark) { return mark.occurrence == null || mark.occurrence === occurrence; }));
                api.setSelectionTransparent(selectionTransparent);
                status = { status: 'ready', token: value.token, selectionHit: projected || null };
                if (backdrop) backdrop.style.opacity = '1';
                iframe.style.opacity = '1';
                releaseRetainedFrame();
                configurationKey = key;
            } catch (error) { if (!closed && mine === revision) status = { status: 'error', token: value.token, error: String(error.message || error) }; }
        }
        async function move(value) {
            if (!api) return;
            selectionMove = value;
            var target = value.location && Number.isInteger(value.location.index) ? value.location.index : value.index;
            if (Number.isInteger(target) && target !== current)
                return configure(Object.assign({}, options, value, { index: target, preservePosition: false }));
            preloadedLayoutReused = false;
            var mine = ++revision;
            options = Object.assign({}, options, { token: value.token }); status = { status: 'loading', token: value.token };
            try {
                var projection = selectionProjection(value);
                await api.move(Object.assign({}, value, { selectionProjection: projection })); check(mine);
                var local = api.state(); if (local.status !== 'ready') throw new Error(local.error || 'EPUB navigation failed');
                await repaint(); check(mine);
                var finalProjection = selectionProjection(value), projected = null;
                if (projection && finalProjection && JSON.stringify(projection.edgeBounds) !== JSON.stringify(finalProjection.edgeBounds))
                    projected = api.interact(Object.assign({}, finalProjection, { token: local.token, action: 'projectSelection' }));
                status = { status: 'ready', token: value.token, selectionHit: projected };
            } catch (error) { if (!closed && mine === revision) status = { status: 'error', token: value.token, error: String(error.message || error) }; }
        }
        function translate(r) { return Object.assign({}, r, { left: r.left + rect.left, right: r.right + rect.left,
            top: r.top + rect.top, bottom: r.bottom + rect.top }); }
        function selectionProjection(value) {
            var projection = value.selectionProjection;
            var anchor = projection && (projection.anchors || []).find(function (part) { return part.index === current; });
            if (!anchor) return null;
            var clip = contentRect || rect;
            return Object.assign({}, anchor, { x: projection.x - rect.left, y: projection.y - rect.top,
                pointerRevision: projection.pointerRevision,
                edgeBounds: { left: clip.left - rect.left, top: clip.top - rect.top,
                    right: clip.left + clip.width - rect.left, bottom: clip.top + clip.height - rect.top } });
        }
        function state() {
            var local = api ? api.state() : {};
            var clip = contentRect || rect;
            var hit = status.selectionHit || local.selectionHit;
            if (hit) hit = Object.assign({}, hit, { token: status.token, index: current,
                selection: hit.selection && Object.assign({}, hit.selection,
                    { start: translate(hit.selection.start), end: translate(hit.selection.end) }) });
            return Object.assign({}, local, status, { canvas: true, fullViewport: true, index: current,
                selectionHit: hit || null,
                document: documents[current], documentRect: contentRect || rect, canvasDocumentKey: documents[current] && documents[current].url,
                documentPreloaded: documentPreloaded,
                preloadedLayoutReused: preloadedLayoutReused,
                warnings: (local.warnings || []).concat(window.__ngEpubBackgroundWarnings(backdrop)),
                media: (local.media || []).map(function (item) { var r = translate(item);
                    return Object.assign({}, r, { left: Math.max(r.left, clip.left), right: Math.min(r.right, clip.left + clip.width),
                        top: Math.max(r.top, clip.top), bottom: Math.min(r.bottom, clip.top + clip.height),
                        visible: item.visible && r.right > clip.left && r.left < clip.left + clip.width &&
                            r.bottom > clip.top && r.top < clip.top + clip.height });
                }), bleedRects: (local.bleedRects || []).map(translate) });
        }
        function interact(value) {
            if (value.token !== status.token) return null;
            if (value.action === 'clear' || value.action === 'clearRange') {
                if (selectionMove) delete selectionMove.selectionProjection;
                delete status.selectionHit;
                if (api) api.interact({ token: api.state().token, action: 'clear' });
                return { token: value.token };
            }
            if (!api || status.status !== 'ready') return null;
            if (['clear', 'clearRange', 'setRange', 'setRanges', 'select', 'extend', 'tap'].includes(value.action))
                delete status.selectionHit;
            var pointer = ['tap', 'select', 'gallerySwipe', 'activate'].includes(value.action) || value.action === 'hit' && !value.nearest;
            var clip = contentRect || rect;
            if (pointer && (value.x < clip.left || value.x >= clip.left + clip.width || value.y < clip.top || value.y >= clip.top + clip.height))
                return { token: value.token };
            if (value.action === 'setRanges') value = Object.assign({}, value, {
                ranges: (value.ranges || []).filter(function (range) { return range.index === current; }) });
            if (value.action === 'aloud') value = Object.assign({}, value, {
                range: (value.ranges || []).find(function (range) { return range.index === current; }) || value.range || null });
            var bounds = { left: clip.left, top: clip.top, right: clip.left + clip.width, bottom: clip.top + clip.height };
            var result = api.interact(Object.assign({}, value, { token: api.state().token, index: current,
                x: (value.x || 0) - rect.left, y: (value.y || 0) - rect.top,
                edgeBounds: { left: bounds.left - rect.left, top: bounds.top - rect.top,
                    right: bounds.right - rect.left, bottom: bounds.bottom - rect.top } }));
            if (!result) return result;
            if (result.selection) result.selection = Object.assign({}, result.selection, {
                start: translate(result.selection.start), end: translate(result.selection.end) });
            if (result.highlightRect) result.highlightRect = translate(result.highlightRect);
            if (value.action === 'hit') result.selectionBounds = bounds;
            return Object.assign({}, result, { token: value.token, index: current, document: documents[current] });
        }
        return Object.freeze({ configure: configure, move: move, state: state, interact: interact, preloadDocument: preloadDocument,
            updateStyles: async function (value) {
                // Style payloads can change at the same content URL. The active
                // child receives the update; an inactive content cache must reload.
                if (preloadedFrame && preloadedFrame.layoutRevision != null) discardPreloadedFrame();
                preloadedLayoutReused = false;
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
                if (preloadedFrame && preloadedFrame.layoutRevision != null) discardPreloadedFrame();
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
            close: function () { closed = true; revision++; discardPreloadedFrame(); clear(); }
        });
    };
})();
