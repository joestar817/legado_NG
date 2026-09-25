(function () {
    'use strict';
    if (window.__ngEpubCreateContinuous) return;
    // One native WebView, isolated original documents, viewport-sized child frames.
    // Source identity is occurrence + path. Pixel offsets are ephemeral layout data only.
    window.__ngEpubCreateContinuous = function (host, documents) {
        var entries = new Map(), lengths = new Map(), loads = new Map(), pending = new Set(), wanted = new Set(), emptySources = new Map();
        var index = 0, offset = 0, active = 0, width = 0, height = 0, extent = 0;
        var axis = 'y', sign = 1, token = '', revision = 0, serial = 0, closed = false;
        var options = {}, visible = [], status = 'idle', error = null, queued = 0, draining = false;
        var overshoot = 0, boundaryBefore = null, boundaryAfter = null, renderEnd = 0;
        var highlightData = [];
        var selectionTransparent = false;
        var linkHandler = null;
        var failures = new Map(), failedBefore = null, failedAfter = null, selectionError = null;
        var chaptersByPath = new Map();
        var base = new URL(document.baseURI);
        documents = documents.map(function (value) {
            var url = new URL(value.url, base);
            if (url.origin !== base.origin || url.username || url.password || !['http:', 'https:'].includes(url.protocol)) throw new Error('EPUB document origin mismatch');
            return Object.assign({}, value, { url: url.href });
        });
        function check(mine) { if (closed || mine !== revision) throw new Error('continuous-cancelled'); }
        function frame() { return new Promise(function (resolve) { requestAnimationFrame(resolve); }); }
        function bindLinks(entry) {
            entry.api.setLinkHandler(linkHandler ? function (value) {
                var i = entry.index;
                if (!linkHandler || closed || status !== 'ready' || !visible.includes(i) || entries.get(i) !== entry) return;
                active = i;
                linkHandler(Object.assign({}, value, { token: token, index: i, document: documents[i] }));
            } : null);
        }
        function dispose(i) {
            var entry = entries.get(i); if (!entry) return;
            entry.api.cancelConfiguration(); entry.api.pauseMedia(); entry.wrapper.remove(); entries.delete(i);
        }
        async function ensure(i, mine) {
            check(mine);
            if (failures.has(i)) throw new Error(failures.get(i));
            if (entries.has(i)) return entries.get(i);
            if (lengths.get(i) === 0) return { length: 0 };
            if (loads.has(i)) return loads.get(i);
            var loading = (async function () {
                var item = documents[i]; if (!item) throw new Error('EPUB document is outside reading order');
                var wrapper = document.createElement('div'), iframe = document.createElement('iframe');
                wrapper.style.cssText = 'position:absolute;overflow:hidden;opacity:0;left:0;top:0;pointer-events:none;width:' + width + 'px;height:' + height + 'px';
                iframe.style.cssText = 'position:absolute;border:0;left:0;top:0;width:' + width + 'px;height:' + height + 'px';
                iframe.setAttribute('sandbox', 'allow-same-origin allow-scripts');
                iframe.setAttribute('title', item.title || item.path || 'EPUB');
                wrapper.appendChild(iframe); host.appendChild(wrapper);
                var loadTimeout, cancel, cancellation = new Promise(function (_, reject) { cancel = function () {
                    clearTimeout(loadTimeout); iframe.onload = null; iframe.onerror = null;
                    if (api) api.cancelConfiguration();
                    wrapper.remove(); reject(new Error('continuous-cancelled'));
                }; });
                pending.add(cancel);
                try {
                    await Promise.race([cancellation, new Promise(function (resolve, reject) {
                        loadTimeout = setTimeout(function () { reject(new Error('EPUB chapter load timed out')); }, 25000);
                        iframe.onload = function () { clearTimeout(loadTimeout); iframe.onload = null; iframe.onerror = null; resolve(); };
                        iframe.onerror = function () { clearTimeout(loadTimeout); iframe.onload = null; iframe.onerror = null; reject(new Error('EPUB chapter load failed')); };
                        iframe.src = item.url;
                    })]);
                    check(mine);
                    if (!['application/xhtml+xml', 'text/html', 'image/svg+xml'].includes(iframe.contentDocument.contentType))
                        throw new Error('EPUB chapter resource did not return a document');
                    var api = window.__ngEpubInstall(iframe.contentWindow);

                    await Promise.race([cancellation, api.configure(Object.assign({}, options, { token: token + ':doc:' + i + ':' + (++serial),
                        width: width, height: height, flow: 'scrolled-doc', fixed: false, preservePosition: false,
                        continuousInsets: true,
                        contentUrl: item.contentUrl || null,
                        noteMarkers: (options.noteMarkers || []).filter(function (mark) { return mark.occurrence === item.occurrence; }),
                        titleSegments: (options.titleSegmentsByDocument || {})[item.occurrence] || [],
                        startFragment: item.startFragment || null, endFragment: item.endFragment || null,
                        sourceChapters: chaptersByPath.get(item.path) || [],
                        textTransform: (options.textTransforms || {})[item.occurrence] || null,

                        location: null, textOffset: null, fragment: null, page: 0, last: false }))]);
                    check(mine);
                    var state = api.state(); if (state.status !== 'ready') throw new Error(state.error || 'EPUB chapter layout failed');
                    var documentAxis = state.scrollAxis || (state.mode === 'HORIZONTAL' ? 'y' : 'x');
                    var documentSign = state.scrollSign || (state.mode === 'VERTICAL_RL' ? -1 : 1);
                    var length = state.scrolled ? state.scrollLength : (documentAxis === 'x' ? width : height);
                    if (!Number.isFinite(length) || length < 0) throw new Error('Invalid EPUB flow length');
                    var entry = { index: i, api: api, iframe: iframe, wrapper: wrapper, length: length,
                        axis: documentAxis, sign: documentSign, scroll: 0, clip: null, cover: !!state.cover, bleedTop: false };
                    bindLinks(entry);
                    ['mousedown', 'touchstart', 'focusin'].forEach(function (event) {
                        iframe.contentDocument.addEventListener(event, function () {
                            if (!closed && visible.includes(i)) active = i;
                        }, { capture: true, passive: true });
                    });
                    entries.set(i, entry); lengths.set(i, length);
                    api.setHighlights(highlightData.filter(function (mark) { return mark.occurrence === item.occurrence; }));
                    api.setSelectionTransparent(selectionTransparent);
                    if (!length) { emptySources.set(i, state); dispose(i); return { length: 0 }; }
                    return entry;
                } catch (failure) {
                    wrapper.remove();
                    if (!closed && mine === revision) failures.set(i, String(failure.message || failure));
                    throw failure;
                }
                finally { pending.delete(cancel); }
            })();
            loads.set(i, loading);
            try { return await loading; } finally { if (loads.get(i) === loading) loads.delete(i); }
        }
        function compatible(entry) { return !entry.length || (entry.axis === axis && entry.sign === sign); }
        async function neighbor(i, mine, direction) {
            try { return await ensure(i, mine); }
            catch (failure) {
                check(mine);
                if (!failures.has(i)) throw failure;
                if (direction < 0) failedBefore = i; else failedAfter = i;
                return null;
            }
        }
        function failureMessage(i) {
            return i == null || !failures.has(i) ? null : '章节加载失败：' + documents[i].path + '\n' + failures.get(i);
        }
        async function shift(delta, mine) {
            offset += delta;
            while (offset < 0 && index > 0) {
                var previous = await neighbor(index - 1, mine, -1); check(mine);
                if (!previous) break;
                if (!compatible(previous)) { boundaryBefore = index - 1; break; }
                index--; offset += previous.length;
            }
            if (offset < 0) overshoot += offset;
            offset = Math.max(0, offset);
            while (index + 1 < documents.length) {
                var current = await ensure(index, mine); check(mine);
                if (offset < current.length && current.length > 0) break;
                var next = await neighbor(index + 1, mine, 1); check(mine);
                if (!next) break;
                if (!compatible(next)) { boundaryAfter = index + 1; break; }
                offset -= current.length; index++;
            }
            var last = await ensure(index, mine); check(mine);
            if (offset > last.length) overshoot += offset - last.length;
            offset = Math.min(offset, last.length);
        }
        async function render(mine, clampEnd) {
            var windows = [], covered = -offset, i = index;
            while (i < documents.length && covered < extent) {
                var entry = i === index ? await ensure(i, mine) : await neighbor(i, mine, 1); check(mine);
                if (!entry) break;
                if (!compatible(entry)) { boundaryAfter = i; break; }
                if (entry.length) windows.push({ entry: entry, start: covered });
                covered += entry.length; i++;
            }
            if (clampEnd && covered < extent && (index > 0 || offset > 0)) {
                // A direction change ends this flow segment. Keep its final viewport complete,
                // and expose a navigation boundary instead of failing a valid mixed-flow book.
                overshoot += Math.max(0, extent - covered);
                await shift(covered - extent, mine); return render(mine, false);
            }
            check(mine);
            renderEnd = i;
            var nextVisible = [];
            windows.forEach(function (window) {
                var entry = window.entry;
                entry.flowStart = window.start; entry.flowEnd = window.start + entry.length;
                var consumed = Math.max(0, -window.start), start = Math.max(0, window.start);
                var size = Math.min(extent - start, entry.length - consumed);
                if (!(size > 0)) return;
                var childOffset = Math.min(consumed, Math.max(0, entry.length - extent));
                entry.api.scrollBy(childOffset - entry.scroll); entry.scroll = childOffset;
                var remainder = consumed - childOffset;
                var position = sign > 0 ? start : extent - start - size;
                entry.wrapper.style.cssText = 'position:absolute;overflow:hidden;visibility:visible;' +
                    (axis === 'y' ? 'left:0;top:' + position + 'px;width:' + width + 'px;height:' + size + 'px'
                        : 'top:0;left:' + position + 'px;height:' + height + 'px;width:' + size + 'px');
                var translation = sign > 0 ? -remainder : size - extent + remainder;
                entry.iframe.style.left = (axis === 'x' ? translation : 0) + 'px';
                entry.iframe.style.top = (axis === 'y' ? translation : 0) + 'px';
                entry.clip = { left: axis === 'x' ? position : 0, top: axis === 'y' ? position : 0,
                    width: axis === 'x' ? size : width, height: axis === 'y' ? size : height,
                    translateX: axis === 'x' ? translation : 0, translateY: axis === 'y' ? translation : 0 };
                // The native canvas is full size. Each source controls its own bleed while
                // normal content stays inside the reader's stable safe rectangle.
                var inset = options.readerInsets || {}, c = entry.clip;
                var left = Math.max(0, inset.left || 0), top = Math.max(0, inset.top || 0);
                var right = width - Math.max(0, inset.right || 0), bottom = height - Math.max(0, inset.bottom || 0);
                entry.bleedTop = false;
                if (entry.cover) { left = 0; top = 0; right = width; bottom = height; }
                else (entry.api.state().bleedRects || []).forEach(function (r) {
                    var x = c.left + c.translateX, y = c.top + c.translateY;
                    if (r.bottom + y <= 0 || r.top + y >= height || r.right + x <= 0 || r.left + x >= width) return;
                    if (r.sides.includes('left')) left = 0;
                    if (r.sides.includes('right')) right = width;
                    if (r.sides.includes('top') && r.top + y < top && r.bottom + y > 0) { top = 0; entry.bleedTop = true; }
                });
                var clippedLeft = Math.max(c.left, left), clippedTop = Math.max(c.top, top);
                var clippedRight = Math.min(c.left + c.width, right), clippedBottom = Math.min(c.top + c.height, bottom);
                if (clippedRight <= clippedLeft || clippedBottom <= clippedTop) return;
                c.translateX -= clippedLeft - c.left; c.translateY -= clippedTop - c.top;
                c.left = clippedLeft; c.top = clippedTop; c.width = clippedRight - clippedLeft; c.height = clippedBottom - clippedTop;
                entry.wrapper.style.left = c.left + 'px'; entry.wrapper.style.top = c.top + 'px';
                entry.wrapper.style.width = c.width + 'px'; entry.wrapper.style.height = c.height + 'px';
                entry.iframe.style.left = c.translateX + 'px'; entry.iframe.style.top = c.translateY + 'px';
                nextVisible.push(entry.index);
            });
            entries.forEach(function (entry, key) {
                if (!nextVisible.includes(key)) { entry.api.pauseMedia(); entry.wrapper.style.opacity = '0'; entry.wrapper.style.pointerEvents = 'none'; }
            });
            var activeEntry = entries.get(active);
            var selecting = activeEntry && activeEntry.iframe.contentWindow.getSelection().rangeCount > 0;
            visible = nextVisible; if (!selecting) active = visible.length ? visible[0] : index;
            // Only visible documents and one neighbor on each side retain a live document context.
            // The logical origin may be behind the reader's top/side inset while
            // the next source is already visible. Keep it for offset arithmetic.
            wanted = new Set(visible.concat([index - 1, index, i]));
            if (selecting) wanted.add(active);
            Array.from(entries.keys()).forEach(function (key) { if (!wanted.has(key)) dispose(key); });
            await frame(); check(mine);
            // Preload one neighboring document at a time after the visible window is complete.
            var next = i < documents.length ? i : index - 1;
            if (next >= 0) ensure(next, mine).then(function () {
                if (mine === revision && !wanted.has(next)) dispose(next);
            }).catch(function () { /* Foreground navigation reports a failed neighbor if it is opened. */ });
        }
        var styleRevision = 0;
        async function updateStyles(value) {
            captureLocation();
            var saved = state(), mine = revision, styleMine = ++styleRevision;
            saved.index = saved.readingIndex; saved.location = saved.readingLocation;
            token = value.token; options.token = token; status = 'loading';
            try {
                await Promise.allSettled(Array.from(loads.values())); check(mine);
                if (styleMine !== styleRevision) return { cancelled: true };
                var reflow = false;
                for (var entry of entries.values()) {
                    var result = await entry.api.updateStyles(Object.assign({}, value, { token: token + ':style:' + entry.index }));
                    check(mine); if (styleMine !== styleRevision) return { cancelled: true };
                    if (result.error) throw new Error(result.error);
                    reflow = reflow || result.reflow;
                    if (result.reflow) {
                        var local = entry.api.state();
                        entry.length = local.scrolled ? local.scrollLength : extent;
                        entry.scroll = local.scrolled ? local.scrollOffset : 0;
                        lengths.set(entry.index, entry.length);
                    }
                }
                if (reflow) {
                    Array.from(lengths.keys()).forEach(function (i) { if (!entries.has(i)) lengths.delete(i); });
                    emptySources.clear();
                    var current = entries.get(saved.index);
                    if (current) { index = saved.index; offset = current.api.flowPosition({ location: saved.location }) || 0; }
                    await shift(0, mine); await render(mine, true); captureLocation();
                }
                check(mine); status = 'ready';
                return { reflow: reflow };
            } catch (failure) {
                if (mine === revision && !closed) { status = 'error'; error = String(failure.message || failure); }
                return { error: String(failure.message || failure) };
            }
        }
        async function configure(value) {
            if (closed) throw new Error('continuous-closed');
            if (value.preservePosition) captureLocation();
            var saved = value.preservePosition ? state() : null;
            if (saved) { saved.index = saved.readingIndex; saved.location = saved.readingLocation; }
            var mine = ++revision;
            pending.forEach(function (cancel) { cancel(); }); pending.clear();
            Array.from(entries.keys()).forEach(dispose); loads.clear(); lengths.clear(); visible = [];
            emptySources.clear(); failures.clear(); failedBefore = null; failedAfter = null; selectionError = null;
            queued = 0; draining = false; options = value; token = value.token; status = 'loading'; error = null;
            chaptersByPath.clear();
            (value.sourceChapters || []).forEach(function (chapter) {
                if (!chaptersByPath.has(chapter.path)) chaptersByPath.set(chapter.path, []);
                chaptersByPath.get(chapter.path).push(chapter);
            });
            overshoot = 0; boundaryBefore = null; boundaryAfter = null;
            width = value.width; height = value.height;
            host.style.cssText = 'position:relative;overflow:hidden;width:' + width + 'px;height:' + height + 'px';
            try {
                if (!(width > 0 && height > 0 && documents.length)) throw new Error('Invalid continuous viewport');
                index = Math.max(0, Math.min(documents.length - 1, saved ? saved.index : value.index || 0)); offset = 0;
                var entry = await ensure(index, mine);
                check(mine);
                while (!entry.length && index + 1 < documents.length) { entry = await ensure(++index, mine); check(mine); }
                axis = entry.axis || 'y'; sign = entry.sign || 1; extent = axis === 'x' ? width : height;
                var target = saved ? { location: saved.location } : value;
                if (entry.api && (target.location || target.fragment || target.textOffset != null)) {
                    var sourcePosition = entry.api.flowPosition(target);
                    if (sourcePosition == null && saved && !entry.cover) throw new Error('EPUB source position is not available');
                    offset = sourcePosition || 0;
                }
                else if (saved && saved.offset > 0 && !entry.cover) throw new Error('EPUB source position is not available');
                else if (value.last) offset = Math.max(0, entry.length - extent);
                await shift(value.offset || 0, mine); await render(mine, true);
                captureLocation();
                check(mine); status = 'ready';
                if (queued) drain();
            } catch (failure) { if (mine === revision && !closed) { status = 'error'; error = String(failure.message || failure); } }
        }
        async function drain() {
            if (draining || status !== 'ready') return;
            var mine = revision; draining = true;
            try {
                while (queued && !closed && mine === revision) {
                    var delta = queued; queued = 0;
                    await shift(delta, mine); await render(mine, true);
                }
            } catch (failure) { if (mine === revision && !closed) { status = 'error'; error = String(failure.message || failure); } }
            finally { if (mine === revision) draining = false; }
        }
        async function seek(value) {
            if (closed) throw new Error('continuous-closed');
            if (!value.preserveSelection) interact({ token: token, action: 'clear' });
            var mine = ++revision;
            pending.forEach(function (cancel) { cancel(); }); pending.clear(); loads.clear();
            queued = 0; draining = false; status = 'loading'; error = null; token = value.token || token;
            failedBefore = null; failedAfter = null; selectionError = null;
            overshoot = 0; boundaryBefore = null; boundaryAfter = null;
            try {
                index = Math.max(0, Math.min(documents.length - 1, Number.isInteger(value.index) ? value.index : active)); offset = 0;
                var targetIndex = index;
                // Explicit navigation retries this source; ordinary scrolling does not
                // continually reload a known failed neighbor.
                failures.delete(index);
                var entry = await ensure(index, mine);
                check(mine);
                axis = entry.axis || axis; sign = entry.sign || sign; extent = axis === 'x' ? width : height;
                if (entry.api && (value.location || value.fragment || value.textOffset != null)) {
                    var position = entry.api.flowPosition(value);
                    if (position == null && !entry.cover) throw new Error('EPUB source position is not available');
                    offset = position || 0;
                } else offset = value.last ? Math.max(0, entry.length - extent) : value.offset || 0;
                await shift(0, mine); await render(mine, true); check(mine); status = 'ready';
                active = targetIndex;
                captureLocation();
            } catch (failure) { if (mine === revision && !closed) { status = 'error'; error = String(failure.message || failure); } }
        }
        async function prepareSelection(value) {
            var mine = revision;
            var savedIndex = index, savedOffset = offset, savedActive = active;
            selectionError = null;
            try {
                await settle(); check(mine);
                savedIndex = index; savedOffset = offset; savedActive = active;
                var target = value.index;
                if (!Number.isInteger(target) || target < 0 || target >= documents.length) throw new Error('Invalid selection source');
                token = value.token; status = 'loading'; error = null;
                var entry = await ensure(target, mine); check(mine);
                if (!compatible(entry)) return seek(Object.assign({}, value, { last: target < active }));
                // A neighboring source that is already visible keeps exactly the same viewport.
                // If it lies just beyond the safe area, reveal it by normal edge scrolling.
                if (entry.length && !visible.includes(target)) {
                    var start = -offset;
                    for (var i = Math.min(index, target); i < Math.max(index, target); i++) {
                        var length = lengths.has(i) ? lengths.get(i) : (await ensure(i, mine)).length;
                        check(mine); start += target > index ? length : -length;
                    }
                    var inset = options.readerInsets || {}, padding = axis === 'y'
                        ? Math.max(inset.top || 0, inset.bottom || 0) : Math.max(inset.left || 0, inset.right || 0);
                    var reveal = Math.min(entry.length, padding + extent / 4);
                    var delta = target < index ? start + entry.length - reveal : start - extent + reveal;
                    await shift(delta, mine); await render(mine, true); check(mine);
                }
                active = target; captureLocation(); status = 'ready';
            } catch (failure) {
                if (mine === revision && !closed) {
                    index = savedIndex; offset = savedOffset; active = savedActive;
                    if (entries.has(index)) {
                        selectionError = failureMessage(value.index) || String(failure.message || failure);
                        error = null; status = 'ready';
                    } else { status = 'error'; error = String(failure.message || failure); }
                }
            }
        }
        function captureLocation() {
            Array.from(new Set([index, active].concat(visible))).forEach(function (i) {
                var entry = entries.get(i);
                if (entry && entry.clip && visible.includes(i)) entry.api.captureLocation({ left: -entry.clip.translateX, top: -entry.clip.translateY,
                    width: entry.clip.width, height: entry.clip.height });
            });
        }
        async function settle() {
            var mine = revision;
            await drain(); while (draining) { await frame(); check(mine); }
            check(mine);
            captureLocation();
            return state();
        }
        async function move(value) {
            if (closed) return;
            selectionError = null;
            if (!value.preserveSelection) interact({ token: token, action: 'clear' });
            if (!value.preserveScroll && value.delta == null) {
                return seek(Object.assign({}, value, { offset: (value.page || 0) * extent }));
            }
            var mine = revision;
            if (Number.isFinite(value.delta)) {
                var inset = options.readerInsets || {}, current = entries.get(visible.length ? visible[0] : index);
                var obscured = current && current.cover ? 0 : axis === 'y'
                    ? (inset.top || 0) + (inset.bottom || 0) : (inset.left || 0) + (inset.right || 0);
                queued += value.delta * Math.max(1, extent - obscured);
            }
            var work = drain();
            token = value.token || token; status = 'loading';
            try {
                await work; while (draining) { await frame(); check(mine); }
                check(mine);
                if (!error) { captureLocation(); status = 'ready'; }
            } catch (failure) { if (mine === revision && !closed) { status = 'error'; error = String(failure.message || failure); } }
        }
        function state() {
            var entry = entries.get(index), local = entry ? entry.api.state() : emptySources.get(index);
            var activeEntry = entries.get(active), activeState = activeEntry ? activeEntry.api.state() : emptySources.get(active);
            var pageCount = Math.max(1, Math.ceil((entry && entry.length || 0) / Math.max(1, extent)));
            // A source's bottom margin can remain in the viewport after all of its
            // content has left. Report the next visible source anchor in that case.
            var anchored = visible.find(function (i) { var e = entries.get(i); return e && (e.cover || e.api.state().location); });
            var readingIndex = anchored != null ? anchored : visible.length ? visible[0] : index, readingEntry = entries.get(readingIndex);
            var readingState = readingEntry ? readingEntry.api.state() : emptySources.get(readingIndex);
            var readingOffset = readingEntry ? Math.max(0, -readingEntry.flowStart) : 0;
            var readingCount = Math.max(1, Math.ceil((readingEntry && readingEntry.length || 0) / Math.max(1, extent)));
            var media = [];
            visible.forEach(function (i) {
                var e = entries.get(i), c = e && e.clip; if (!c) return;
                (e.api.state().media || []).forEach(function (item) {
                    var left = item.left + c.left + c.translateX, right = item.right + c.left + c.translateX;
                    var top = item.top + c.top + c.translateY, bottom = item.bottom + c.top + c.translateY;
                    media.push(Object.assign({}, item, { left: Math.max(left, c.left), right: Math.min(right, c.left + c.width),
                        top: Math.max(top, c.top), bottom: Math.min(bottom, c.top + c.height),
                        visible: item.visible && right > c.left && left < c.left + c.width && bottom > c.top && top < c.top + c.height }));
                });
            });
            var previous = index - 1; while (previous >= 0 && lengths.get(previous) === 0) previous--;
            var first = previous < 0 || boundaryBefore === previous || failedBefore === previous;
            var last = visible.length && (renderEnd === documents.length || boundaryAfter === renderEnd || failedAfter === renderEnd);
            var remaining = -offset;
            for (var i = index; i < renderEnd; i++) remaining += lengths.get(i) || 0;
            return { token: token, status: status, error: error, index: index, document: documents[index], offset: offset, media: media,
                readingIndex: readingIndex, readingLocation: readingState && readingState.location,
                displayPageCount: readingCount, displayPageIndex: readingOffset >= Math.max(0, (readingEntry && readingEntry.length || 0) - extent) - 1
                    ? readingCount - 1 : Math.floor(readingOffset / Math.max(1, extent)),
                fontWarnings: Array.from(new Set(visible.flatMap(function (i) { return entries.get(i).api.state().fontWarnings || []; }))),
                bleed: true, cover: !!(entry && entry.cover), bleedHeader: !!(entry && entry.bleedTop),
                textLength: local && local.textLength || 0, warnings: (local && local.warnings || []).concat(
                    [failureMessage(failedBefore), failureMessage(failedAfter)].filter(Boolean)),
                failedBefore: failureMessage(failedBefore), failedAfter: failureMessage(failedAfter), selectionError: selectionError,
                chapterBoundaries: local && local.chapterBoundaries || [], activeChapterBoundaries: activeState && activeState.chapterBoundaries || [],
                atStart: first && offset <= 1, atEnd: !!last && remaining <= extent + 1,
                overshoot: overshoot, boundaryBefore: boundaryBefore, boundaryAfter: boundaryAfter,
                axis: axis, sign: sign, busy: draining, visible: visible.slice(), contexts: entries.size,
                mode: local && local.mode || 'HORIZONTAL', scrolled: true, scrollAxis: axis, scrollSign: sign,
                scrollLength: entry && entry.length || 0, scrollExtent: extent, scrollOffset: offset,
                pageIndex: offset >= Math.max(0, (entry && entry.length || 0) - extent) - 1 ? pageCount - 1 : Math.floor(offset / Math.max(1, extent)), pageCount: pageCount,
                location: local && local.location, active: documents[active], activeIndex: active,
                activeTextLength: activeState && activeState.textLength || 0,
                hasSelectableText: !!(local && local.hasSelectableText), activeHasSelectableText: !!(activeState && activeState.hasSelectableText),
                activeLocation: activeState && activeState.location };
        }
        function interact(value) {
            if (value.token != null && value.token !== token) return null;
            if (status !== 'ready' && value.action !== 'clear') return null;
            var selected = Array.from(entries.values()).some(function (e) { return e.iframe.contentWindow.getSelection().rangeCount > 0; });
            if (value.action === 'clear' || (value.action === 'tap' && selected)) {
                entries.forEach(function (e) { e.api.interact({ token: e.api.state().token, action: 'clear' }); });
                return { token: token, dismissed: value.action === 'tap', selection: null };
            }
            if (value.action === 'pageBounds') return { token: token, pages: Array.from(entries.values()).flatMap(function (e) {
                var result = e.api.interact({ token: e.api.state().token, action: 'pageBounds', index: e.index });
                return result && result.pages || [];
            }) };
            if (value.action === 'aloud') {
                entries.forEach(function (e) { e.api.interact({ token: e.api.state().token, action: 'aloud',
                    range: (value.ranges || []).find(function (r) { return r.index === e.index; }) || null }); });
                return { token: token };
            }
            if (value.action === 'setRanges') {
                var selected = [];
                Array.from(entries.values()).sort(function (a, b) { return a.index - b.index; }).forEach(function (e) {
                    var ranges = value.ranges.filter(function (r) { return r.index === e.index; });
                    var result = e.api.interact({ token: e.api.state().token, action: 'setRanges', ranges: ranges });
                    if (result && result.selection && e.clip) {
                        ['start', 'end'].forEach(function (key) {
                            var r = result.selection[key]; r.left += e.clip.left + e.clip.translateX; r.right += e.clip.left + e.clip.translateX;
                            r.top += e.clip.top + e.clip.translateY; r.bottom += e.clip.top + e.clip.translateY;
                        }); selected.push(result.selection);
                    }
                });
                return { token: token, selection: selected.length ? { start: selected[0].start, end: selected[selected.length - 1].end } : null };
            }
            var entry;
            if (['tap', 'select', 'gallerySwipe', 'activate', 'hit'].includes(value.action)) {
                entry = visible.map(function (i) { return entries.get(i); }).find(function (e) {
                    var c = e.clip; return value.x >= c.left && value.x < c.left + c.width && value.y >= c.top && value.y < c.top + c.height;
                });
                if (entry) {
                    if (value.action === 'select') entries.forEach(function (e) {
                        if (e !== entry) e.api.interact({ token: e.api.state().token, action: 'clear' });
                    });
                    active = entry.index;
                }
            } else entry = entries.get(Number.isInteger(value.index) ? value.index : active);
            if (!entry && value.action === 'rangePart') {
                var requestedIndex = Number.isInteger(value.index) ? value.index : active;
                var empty = emptySources.get(requestedIndex);
                if (empty && Number.isInteger(value.from) && Number.isInteger(value.to) && value.from >= 0 && value.to >= value.from && value.to <= empty.textLength)
                    return { token: token, index: requestedIndex, document: documents[requestedIndex],
                        part: { from: value.from, to: value.to, length: empty.textLength, text: '' } };
            }
            if (entry && ['rangePart'].includes(value.action)) return Object.assign({}, entry.api.interact(Object.assign({}, value,
                { token: entry.api.state().token })), { token: token, index: entry.index, document: documents[entry.index] });
            if (!entry || !entry.clip) return null;
            var c = entry.clip, result;
            if (value.action === 'activate' || value.action === 'activeSource') result = {};
            if (value.action === 'extend') {
                var position = axis === 'x' ? value.x : value.y;
                var progress = sign > 0 ? position : extent - position;
                var direction = progress < entry.flowStart ? -1 : progress >= entry.flowEnd ? 1 : 0;
                if (!visible.includes(active)) direction = active < index ? 1 : -1;
                if (direction && active + direction >= 0 && active + direction < documents.length) {
                    var boundary = entry.api.interact({ token: entry.api.state().token, action: 'selection' });
                    result = Object.assign({}, boundary, { token: token, index: active, document: documents[active],
                        edge: direction, documentBoundary: true });
                }
            }
            if (!result) result = entry.api.interact(Object.assign({}, value, { token: entry.api.state().token,
                x: (value.x || 0) - c.left - c.translateX, y: (value.y || 0) - c.top - c.translateY }));
            if (!result) return null;
            if (result.selection) ['start', 'end'].forEach(function (key) {
                var r = result.selection[key]; r.left += c.left + c.translateX; r.right += c.left + c.translateX;
                r.top += c.top + c.translateY; r.bottom += c.top + c.translateY;
            });
            if (result.highlightRect) {
                var r = result.highlightRect; r.left += c.left + c.translateX; r.right += c.left + c.translateX;
                r.top += c.top + c.translateY; r.bottom += c.top + c.translateY;
            }
            var sourceLocation = entry.api.captureLocation({ left: -c.translateX, top: -c.translateY, width: c.width, height: c.height });
            return Object.assign({}, result, { token: token, index: entry.index, document: documents[entry.index], location: sourceLocation,
                chapterBoundaries: entry.api.state().chapterBoundaries, textLength: entry.api.state().textLength });
        }
        return Object.freeze({ configure: configure, updateStyles: updateStyles, seek: seek, move: move, prepareSelection: prepareSelection,
            setLinkHandler: function (handler) { linkHandler = typeof handler === 'function' ? handler : null; entries.forEach(bindLinks); },
            beginScroll: function () { overshoot = 0; },
            scrollBy: function (delta) { if (Number.isFinite(delta)) { queued += delta; drain(); } return state(); },
            settle: settle, state: state, interact: interact,
            revealText: function (textOffset) {
                var entry = entries.get(active); if (!entry) return null;
                var position = entry.api.flowPosition(textOffset && typeof textOffset === 'object' ? { location: textOffset } : { textOffset: textOffset });
                if (position == null) return null;
                var c = entry.clip, start = c && (axis === 'x' ? (sign > 0 ? c.left : extent - c.left - c.width) : c.top);
                var consumed = active === index ? offset : 0;
                return { moved: !c || position < consumed || position >= consumed + (axis === 'x' ? c.width : c.height),
                    index: active, document: documents[active], start: start };
            },
            mediaOverlay: function (value) {
                entries.forEach(function (entry) { entry.api.mediaOverlay(entry.index === active ? value : {}); });
            },
            setHighlights: function (values) {
                highlightData = values.map(function (value) { return Object.assign({}, value, {
                    occurrence: value.occurrence == null ? documents[active].occurrence : value.occurrence }); });
                entries.forEach(function (entry) { entry.api.setHighlights(highlightData.filter(function (mark) {
                    return mark.occurrence === documents[entry.index].occurrence;
                })); });
            },
            setSelectionTransparent: function (value) {
                selectionTransparent = !!value;
                entries.forEach(function (entry) { entry.api.setSelectionTransparent(selectionTransparent); });
            },
            pauseMedia: function () { entries.forEach(function (entry) { entry.api.pauseMedia(); }); },
            close: function () { closed = true; revision++; status = 'closed'; draining = false; queued = 0; linkHandler = null;
                pending.forEach(function (cancel) { cancel(); }); pending.clear();
                Array.from(entries.keys()).forEach(dispose); host.replaceChildren(); } });
    };
})();
