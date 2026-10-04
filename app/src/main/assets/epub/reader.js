(function install(window) {
    'use strict';
    if (window.__ngEpub) return;
    var document = window.document, Node = window.Node, NodeFilter = window.NodeFilter, Range = window.Range;
    var HTMLMediaElement = window.HTMLMediaElement, MouseEvent = window.MouseEvent, URL = window.URL;
    var getComputedStyle = window.getComputedStyle.bind(window), requestAnimationFrame = window.requestAnimationFrame.bind(window);
    var matchMedia = window.matchMedia.bind(window), performance = window.performance, Intl = window.Intl;
    var linkHandler = null;
    document.addEventListener('click', function (event) {
        if (!linkHandler || event.defaultPrevented) return;
        var link = event.target;
        while (link && link.localName !== 'a') link = link.parentElement;
        var href = link && (link.getAttribute('href') || link.getAttributeNS('http://www.w3.org/1999/xlink', 'href'));
        if (!href) return;
        var observer = event.target;
        while (observer && !triggerObservers.has(observer)) observer = observer.parentElement;
        // All navigation stays native, including same-document hashes. Only browser
        // user activation may dispatch it; synthetic book/trigger clicks cannot.
        event.preventDefault();
        if (!event.isTrusted || state.status !== 'ready' || observer) return;
        try { linkHandler({ token: state.token, href: new URL(href, document.baseURI).href,
            location: sourceLocation() }); } catch (_) { /* Native navigation is unavailable. */ }
    });
    Object.defineProperty(window, '__ngEpubInstall', { value: function (target) {
        if (target.location.origin !== window.location.origin) throw new Error('EPUB document origin mismatch');
        if (window.__ngEpubContentInstall) window.__ngEpubContentInstall(target);
        install(target); return target.__ngEpub;
    } });
    // External sheets are adapted by the resource gateway; inline CSS needs the same
    // preservation before the browser drops the legacy declaration from CSSOM.
    function legacyCss(css) {
        var out = '', i = 0, start = true, parentheses = 0;
        while (i < css.length) {
            var c = css[i];
            if (c === '/' && css[i + 1] === '*') {
                var end = css.indexOf('*/', i + 2); end = end < 0 ? css.length : end + 2;
                out += css.slice(i, end); i = end; continue;
            }
            if (c === '"' || c === "'") {
                var quote = c; out += c; i++;
                while (i < css.length) {
                    var next = css[i++]; out += next;
                    if (next === '\\' && i < css.length) out += css[i++];
                    else if (next === quote) break;
                }
                start = false; continue;
            }
            if (start && !parentheses && /^duokan-bleed\s*:/i.test(css.slice(i))) {
                out += '--ng-duokan-bleed'; i += 12; start = false; continue;
            }
            out += c; i++;
            if (c === '(') parentheses++;
            if (c === ')') parentheses = Math.max(0, parentheses - 1);
            if (!parentheses && (c === '{' || c === ';')) start = true;
            else if (!/\s/.test(c)) start = false;
        }
        return out;
    }
    document.querySelectorAll('style').forEach(function (el) { var css = legacyCss(el.textContent); if (css !== el.textContent) el.textContent = css; });
    document.querySelectorAll('[style]').forEach(function (el) { var css = legacyCss(el.getAttribute('style')); if (css !== el.getAttribute('style')) el.setAttribute('style', css); });
    var root = document.documentElement, body = document.body || root;
    var originalRoot = root.getAttribute('style'), originalBody = body.getAttribute('style');
    var originalViewport = Array.from(document.getElementsByTagName('meta')).find(function (meta) {
        return (meta.getAttribute('name') || '').toLowerCase() === 'viewport';
    });
    var fixedViewport = originalViewport ? originalViewport.content : '';
    var adjusted = new Map();
    var resourceWarnings = new Set();
    var state = { status: 'idle' }, options, generation = 0, axis = 'x', sign = 1, extent = 1;
    // Document-local source position; page numbers are only derived layout data.
    // The host maps these coordinates to its existing content positions.
    var readingAnchor = null, viewportWidth = 0, viewportHeight = 0;
    var selectedRange = null, dragAnchor = null;
    var galleries = [], galleryIndexes = new WeakMap();
    var sourceNodes = [], sourceOffsets = new WeakMap(), sourceLengths = new WeakMap(), sourceText = '', sourceAnchors = new Map(), chapterBoundaries = [];
    var displayOffsets = new WeakMap(), displayText = '', textTransform = null;
    var textRequest = null, contentCoordinates = false;
    var pageStartsCache = null;
    var readerFonts = {
        reader: { family: 'NGReaderFont', url: null, face: null, failed: false },
        title: { family: 'NGTitleFont', url: null, face: null, failed: false }
    };
    function readerFamily(reader) {
        return (reader.hasFont ? 'NGReaderFont,' : '') + (reader.fontFamily || 'sans-serif');
    }
    async function loadReaderFont(kind, requested, mine) {
        var cached = readerFonts[kind], face = null, failed = false;
        requested = requested || null;
        if (requested === cached.url) return cached.failed;
        if (requested) {
            var url = new URL(requested, document.baseURI);
            if (url.origin !== window.location.origin || url.username || url.password) throw new Error('阅读字体来源无效');
            face = new FontFace(cached.family, 'url(' + JSON.stringify(url.href) + ')');
            try { await face.load(); }
            catch (_) { failed = true; face = null; }
            if (mine !== generation) return false;
        }
        // A rejected user font is a resource failure, not a failed chapter. Do not keep
        // the previous font after a failed switch, or retry the same revision every turn.
        if (cached.face) document.fonts.delete(cached.face);
        cached.url = requested; cached.face = face; cached.failed = failed;
        if (face) document.fonts.add(face);
        return failed;
    }

    (function indexSource() {
        var walker = document.createTreeWalker(body, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT), node;
        while ((node = walker.nextNode())) {
            var element = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
            if (element.closest('script,style,rt,rp,svg')) continue;
            if (node.nodeType === Node.ELEMENT_NODE) {
                [node.id, node.localName === 'a' && node.getAttribute('name')].forEach(function (id) {
                    if (id && !sourceAnchors.has(id)) sourceAnchors.set(id, sourceText.length);
                });
            } else { sourceOffsets.set(node, sourceText.length); sourceLengths.set(node, node.length); sourceNodes.push(node); sourceText += node.data; }
        }
        if (body.id) sourceAnchors.set(body.id, 0);
    })();
    // Canvas scripts are disabled; expose the author's accessible fallback rather than a blank bitmap.
    body.querySelectorAll('canvas').forEach(function (canvas) {
        if (!canvas.textContent.trim()) return;
        var fallback = document.createElementNS('http://www.w3.org/1999/xhtml', 'div');
        if (canvas.hasAttribute('data-ng-epub-source')) {
            fallback.setAttribute('data-ng-epub-source', canvas.getAttribute('data-ng-epub-source'));
            canvas.removeAttribute('data-ng-epub-source');
        }
        while (canvas.firstChild) fallback.appendChild(canvas.firstChild);
        canvas.after(fallback); canvas.hidden = true;
    });
    function applyTextTransform() {
        displayOffsets = new WeakMap();
        sourceNodes.forEach(function (node) { displayOffsets.set(node, sourceOffsets.get(node)); });
        displayText = sourceText;
    }
    function displayOffset(node, offset) { return (displayOffsets.get(node) || 0) + (offset || 0); }
    function textOffset(node, offset, after) {
        if (contentCoordinates) return window.__ngEpubContent.offset(node, offset || 0, !!after);
        var value = displayOffset(node, offset); return textTransform ? textTransform.toSource(value, !!after) : value;
    }
    function displayPoint(offset) {
        offset = Math.max(0, Math.min(displayText.length, offset));
        for (var i = 0; i < sourceNodes.length; i++) {
            var node = sourceNodes[i], start = displayOffsets.get(node);
            if (offset < start + node.length || i === sourceNodes.length - 1) return { node: node, offset: offset - start };
        }
        return null;
    }
    function textPoint(offset, after) {
        if (contentCoordinates) return window.__ngEpubContent.point(offset, !!after);
        offset = Math.max(0, Math.min(sourceText.length, offset));
        return displayPoint(textTransform ? textTransform.toDisplay(offset, !!after) : offset);
    }
    applyTextTransform(null);
    function sourceLocation() {
        var anchor = readingAnchor;
        if (!anchor) return null;
        var sourceOffset = textOffset(anchor.node, anchor.offset), localOffset = anchor.offset || 0;
        var node = anchor.node, path = [];
        if (textTransform && sourceOffsets.has(anchor.node)) for (var i = 0; i < sourceNodes.length; i++) {
            var original = sourceNodes[i], start = sourceOffsets.get(original);
            if (sourceOffset < start + sourceLengths.get(original) || i === sourceNodes.length - 1) {
                node = original; localOffset = sourceOffset - start; break;
            }
        }
        while (node && node !== body) {
            path.unshift(Array.prototype.indexOf.call(node.parentNode.childNodes, node)); node = node.parentNode;
        }
        if (node !== body) return null;
        var result = { nodePath: path, offset: localOffset, textOffset: sourceOffset, version: 1 };
        if (textTransform && sourceOffsets.has(anchor.node)) result.display = {
            revision: textTransform.revision, offset: displayOffset(anchor.node, anchor.offset) };
        if (result.display) result.ownerOffset = textTransform.ownerOffset(result.display.offset, false);
        return result;
    }
    function locationAnchor(value) {
        if (contentCoordinates && value && Number.isInteger(value.textOffset)) return textPoint(value.textOffset);
        if (!value || value.version !== 1 || !Array.isArray(value.nodePath)) return null;
        if (value.display && textTransform && value.display.revision === textTransform.revision &&
            Number.isInteger(value.display.offset) && value.display.offset >= 0 && value.display.offset <= displayText.length &&
            textTransform.toSource(value.display.offset, false) === value.textOffset) return displayPoint(value.display.offset);
        var node = body;
        for (var i of value.nodePath) { node = node.childNodes[i]; if (!node) return null; }
        if ((value.display || (textTransform && node.nodeType === Node.TEXT_NODE)) && Number.isInteger(value.textOffset)) {
            var offset = value.textOffset;
            if (Number.isInteger(value.ownerOffset) && value.ownerOffset >= 0 && value.ownerOffset <= sourceText.length)
                offset = Math.max(offset, value.ownerOffset);
            return textPoint(offset);
        }
        return { node: node, offset: node.nodeType === Node.TEXT_NODE ? Math.min(value.offset || 0, node.length) : undefined };
    }
    var highlights = [], highlightRects = [], noteHitRects = [], highlightLayer;
    var triggerObservers = new Set();
    function prepareTriggers() {
        Array.from(document.getElementsByTagNameNS('http://www.idpf.org/2007/ops', 'trigger')).forEach(function (trigger) {
            var observer = document.getElementById(trigger.getAttributeNS('http://www.w3.org/2001/xml-events', 'observer'));
            var event = trigger.getAttributeNS('http://www.w3.org/2001/xml-events', 'event');
            var target = document.getElementById(trigger.getAttribute('ref')), action = trigger.getAttribute('action');
            if (!observer || !target || !['click','dblclick','ended','play','pause'].includes(event)) return;
            if (!['play','pause','resume','mute','unmute','show','hide'].includes(action)) return;
            if (event === 'click' || event === 'dblclick') {
                triggerObservers.add(observer);
                if (!observer.hasAttribute('data-ng-trigger-control')) {
                    observer.setAttribute('data-ng-trigger-control', '');
                    if (!observer.hasAttribute('tabindex')) observer.setAttribute('tabindex', '0');
                    if (!observer.hasAttribute('role')) observer.setAttribute('role', 'button');
                    observer.addEventListener('keydown', function (key) {
                        if (key.key === 'Enter' || key.key === ' ') { key.preventDefault(); observer.dispatchEvent(new MouseEvent('click', { bubbles: true })); }
                    });
                }
            }
            observer.addEventListener(event, function () {
                if (action === 'show' || action === 'hide') target.style.visibility = action === 'show' ? 'visible' : 'hidden';
                else if (target instanceof HTMLMediaElement) {
                    if (action === 'mute' || action === 'unmute') target.muted = action === 'mute';
                    else if (action === 'pause') target.pause();
                    else { if (action === 'play') target.currentTime = 0; target.play().catch(function (error) { resourceWarnings.add('媒体播放失败：' + error.message); }); }
                }
            });
        });
    }
    prepareTriggers();
    document.querySelectorAll('audio,video').forEach(function (media) {
        // With book scripts disabled, every media element must retain a usable control surface.
        media.controls = true; media.autoplay = false; media.preload = 'none';
        media.removeAttribute('autoplay');
    });
    function highlightRange(value) {
        var display = value.display, a, b;
        if (display && textTransform && display.revision === textTransform.revision) {
            if (Number.isInteger(display.from) && display.from === display.to && display.from >= 0 && display.to <= displayText.length &&
                value.from === value.to && [textTransform.toSource(display.from, false), textTransform.toSource(display.from, true)].includes(value.from)) return null;
            if (!Number.isInteger(display.from) || !Number.isInteger(display.to) || display.from < 0 ||
                display.to > displayText.length || display.to <= display.from ||
                textTransform.toSource(display.from, false) !== value.from || textTransform.toSource(display.to, true) !== value.to)
                throw new Error('显示选区与原文映射不一致');
            a = displayPoint(display.from); b = displayPoint(display.to);
        } else {
            if (value.to <= value.from) return null;
            var from = value.from, to = value.to;
            if (Number.isInteger(value.ownerFrom) && value.ownerFrom >= 0 && value.ownerFrom <= sourceText.length) from = Math.max(from, value.ownerFrom);
            if (Number.isInteger(value.ownerTo) && chapterBoundaries.some(function (chapter) { return chapter.offset === value.ownerTo; })) {
                var ownerEnd = sourceText.length;
                chapterBoundaries.forEach(function (chapter) { if (chapter.offset > value.ownerTo) ownerEnd = Math.min(ownerEnd, chapter.offset); });
                to = Math.min(to, ownerEnd);
            }
            if (to <= from) return null;
            if (textTransform) {
                var first = textTransform.toDisplay(from, false), last = textTransform.toDisplay(to, true);
                var firstOwner = Number.isInteger(value.ownerFrom) && textTransform.ownerBounds(value.ownerFrom);
                var lastOwner = Number.isInteger(value.ownerTo) && textTransform.ownerBounds(value.ownerTo);
                if (firstOwner) first = Math.max(first, firstOwner.from);
                if (lastOwner) last = Math.min(last, lastOwner.to);
                if (last <= first) return null;
                a = displayPoint(first); b = displayPoint(last);
            } else { a = textPoint(from); b = textPoint(to, true); }
        }
        if (!a || !b) return null;
        var range = document.createRange(); range.setStart(a.node, a.offset); range.setEnd(b.node, b.offset); return range;
    }
    var fullLineUnderlines = [], fullLineLayer;
    function bodyTranslation() {
        var matrix = /^matrix\(([^)]+)\)$/.exec(getComputedStyle(body).transform);
        var values = matrix && matrix[1].split(',').map(Number);
        return values ? { x:values[4], y:values[5], scale:Math.abs(values[3]) || 1 } : { x:0, y:0, scale:1 };
    }
    function prepareFullLineUnderlines() {
        fullLineUnderlines = [];
        var spec = options.fullLineUnderline;
        if (!spec || body === root) return;
        var translation = bodyTranslation(), groups = new Map(), range = document.createRange();
        sourceNodes.forEach(function (node) {
            var parent = node.parentElement;
            // Native rich HTML blocks are drawn by their own renderer, without paper lines.
            if (!node.data.trim() || !parent || parent.closest('svg,math,table,pre,rt,rp') || getComputedStyle(parent).visibility !== 'visible') return;
            var block = parent;
            while (block.parentElement && block !== body && /^(inline|contents)$/.test(getComputedStyle(block).display)) block = block.parentElement;
            var lines = groups.get(block); if (!lines) { lines = []; groups.set(block,lines); }
            var vertical = /^(vertical|sideways)/.test(getComputedStyle(parent).writingMode);
            range.selectNodeContents(node);
            Array.from(range.getClientRects()).forEach(function (r) {
                if (!(r.width > 0 && r.height > 0)) return;
                var a = vertical ? r.top-translation.y : r.left-translation.x, b = a+(vertical ? r.height : r.width);
                var top = vertical ? r.left-translation.x : r.top-translation.y, bottom = top+(vertical ? r.width : r.height);
                var size = vertical ? viewportHeight : viewportWidth, page = Math.floor((a+.5)/size);
                var line = lines.find(function (line) { return line.page===page && line.vertical===vertical &&
                    Math.min(line.bottom,bottom)-Math.max(line.top,top)>Math.min(line.bottom-line.top,bottom-top)/2; });
                if (line) { line.a=Math.min(line.a,a);line.b=Math.max(line.b,b);line.top=Math.min(line.top,top);line.bottom=Math.max(line.bottom,bottom); }
                else lines.push({ a:a,b:b,top:top,bottom:bottom,page:page,extent:size,vertical:vertical,blocked:[] });
            });
        });
        groups.forEach(function (lines) { fullLineUnderlines.push.apply(fullLineUnderlines,lines); });
        var blocked = window.__ngEpubContent ? window.__ngEpubContent.ruleUnderlineRects() : [];
        blocked.forEach(function (r) { fullLineUnderlines.forEach(function (line) {
            var a=line.vertical?r.top-translation.y:r.left-translation.x, b=a+(line.vertical?r.height:r.width);
            var top=line.vertical?r.left-translation.x:r.top-translation.y, bottom=top+(line.vertical?r.width:r.height);
            if(a<line.b && b>line.a && Math.min(line.bottom,bottom)>Math.max(line.top,top))line.blocked.push([a,b]);
        }); });
    }
    function paintFullLineUnderlines() {
        if (fullLineLayer) fullLineLayer.replaceChildren();
        var spec = options.fullLineUnderline;
        if (!spec || !fullLineUnderlines.length) return;
        if (!fullLineLayer) {
            fullLineLayer=document.createElementNS('http://www.w3.org/2000/svg','svg');
            fullLineLayer.setAttribute('aria-hidden','true');fullLineLayer.setAttribute('data-ng-full-underline','');
            style(fullLineLayer,{position:'fixed',inset:'0',width:'100%',height:'100%',overflow:'hidden','pointer-events':'none','user-select':'none','z-index':'2147483638'});
            root.appendChild(fullLineLayer);
        }
        var translation=bodyTranslation();
        fullLineUnderlines.forEach(function (line) {
            var rect=line.vertical?{left:line.top+translation.x,right:line.bottom+translation.x,top:line.a+translation.y,bottom:line.b+translation.y}
                :{left:line.a+translation.x,right:line.b+translation.x,top:line.top+translation.y,bottom:line.bottom+translation.y};
            rect.width=rect.right-rect.left;rect.height=rect.bottom-rect.top;
            if(!visible(rect))return;
            var start=spec.extend?line.page*line.extent:line.a,end=spec.extend?start+line.extent:line.b,segments=[],cursor=start;
            line.blocked.slice().sort(function(a,b){return a[0]-b[0];}).forEach(function(blocked){
                var a=Math.max(start,Math.min(end,blocked[0])),b=Math.max(start,Math.min(end,blocked[1]));
                if(b<=cursor)return;if(a>cursor)segments.push([cursor,a]);cursor=Math.max(cursor,b);
            });
            if(cursor<end)segments.push([cursor,end]);
            var cross=line.vertical?rect.left-spec.offset*translation.scale:rect.bottom+spec.offset*translation.scale;
            segments.forEach(function(segment){
                var shape=document.createElementNS(fullLineLayer.namespaceURI,'line');
                shape.setAttribute('x1',line.vertical?cross:segment[0]+translation.x);shape.setAttribute('x2',line.vertical?cross:segment[1]+translation.x);
                shape.setAttribute('y1',line.vertical?segment[0]+translation.y:cross);shape.setAttribute('y2',line.vertical?segment[1]+translation.y:cross);
                style(shape,{stroke:spec.color,'stroke-width':spec.width*translation.scale,'stroke-dasharray':spec.dash?spec.dash.map(function(n){return n*translation.scale;}).join(' '):'none'});
                fullLineLayer.appendChild(shape);
            });
        });
    }
    function paintHighlights() {
        if (body === root) return;
        paintSelection();
        paintFullLineUnderlines();
        if (window.__ngEpubContent) window.__ngEpubContent.paintUnderlines(visible);
        if (!highlightLayer) {
            highlightLayer = document.createElement('div');
            highlightLayer.setAttribute('aria-hidden', 'true');
            style(highlightLayer, { position: 'fixed', inset: '0', 'pointer-events': 'none', 'z-index': '2147483640', overflow: 'hidden' });
            root.appendChild(highlightLayer);
        }
        highlightLayer.replaceChildren(); highlightRects = []; noteHitRects = [];
        highlights.forEach(function (value) {
            var range;
            try { range = highlightRange(value); }
            catch (_) { resourceWarnings.add('部分高亮与当前显示文本不一致'); return; }
            if (!range) return;
            Array.from(range.getClientRects()).filter(visible).forEach(function (r) {
                var mark = document.createElement('span'), color = value.color || '#ffff7b';
                style(mark, { position: 'absolute', left: r.left + 'px', top: r.top + 'px', width: r.width + 'px', height: r.height + 'px',
                    'box-sizing': 'border-box', background: value.style ? 'transparent' : color, opacity: value.style ? '1' : String(72 / 255),
                    'border-bottom': value.style === 1 ? '2px solid ' + color : 'none' });
                if (value.style === 2) {
                    var wave = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
                    wave.setAttribute('width', r.width); wave.setAttribute('height', '5');
                    style(wave, { position: 'absolute', left: '0', bottom: '0' });
                    var line = document.createElementNS(wave.namespaceURI, 'path'), d = 'M0 2.5';
                    for (var x = 0; x < r.width; x += 8) d += ' q2 -3 4 0 q2 3 4 0';
                    line.setAttribute('d', d); line.setAttribute('fill', 'none'); line.setAttribute('stroke', color);
                    wave.appendChild(line); mark.appendChild(wave);
                }
                highlightLayer.appendChild(mark); highlightRects.push({ rect: r, value: value });
            });
        });
        if (window.__ngEpubContent) window.__ngEpubContent.noteRects().filter(visible).forEach(function (r) {
            var icon = document.createElement('img'); icon.src = r.image; icon.setAttribute('aria-hidden', 'true');
            style(icon, { position: 'absolute', left: r.left + 'px', top: r.top + 'px', width: r.width + 'px', height: r.height + 'px',
                border: '0', padding: '0', margin: '0', 'max-width': 'none', 'max-height': 'none', 'pointer-events': 'none', 'user-select': 'none' });
            highlightLayer.appendChild(icon); noteHitRects.push(r);
        });
    }
    function justifyPageLines() {
        var reader = options.readerStyle || options.readerDefaults || {};
        if (!reader.bottomJustify || !window.__ngEpubContent || state.scrolled || state.mode==='FIXED' || state.cover ||
            !options.readerStyle && (options.features || {}).paragraph!==false) return;
        var page=state.pageIndex; place(0);
        try {
            var css=getComputedStyle(body),box=body.getBoundingClientRect(),vertical=state.mode!=='HORIZONTAL',reverse=state.mode==='VERTICAL_RL';
            var end=vertical ? reverse ? viewportWidth-box.left-parseFloat(css.paddingLeft)-parseFloat(css.borderLeftWidth)
                :box.right-parseFloat(css.paddingRight)-parseFloat(css.borderRightWidth)
                :box.bottom-parseFloat(css.paddingBottom)-parseFloat(css.borderBottomWidth);
            var start=vertical ? reverse ? viewportWidth-box.right+parseFloat(css.paddingRight)+parseFloat(css.borderRightWidth)
                :box.left+parseFloat(css.paddingLeft)+parseFloat(css.borderLeftWidth)
                :box.top+parseFloat(css.paddingTop)+parseFloat(css.borderTopWidth);
            var result=window.__ngEpubContent.justifyLines({vertical:vertical,reverse:reverse,cross:viewportWidth,
                extent:extent,sign:sign,start:start,end:end,advance:reader.lineHeight || parseFloat(css.lineHeight)});
            if(result.shifted)indexContentNodes();
        } finally { place(page); }
    }
    function setHighlights(values) { highlights = values || []; paintHighlights(); }
    var selectionLayer, selectionHighlightTransparent = false;
    function setSelectionTransparent(value) {
        selectionHighlightTransparent = !!value;
        var sheet = document.getElementById('ng-epub-selection-style');
        if (!sheet) {
            sheet = document.createElementNS('http://www.w3.org/1999/xhtml', 'style');
            sheet.id = 'ng-epub-selection-style'; (document.head || root).appendChild(sheet);
        }
        // Browser selection fills inline line-height/ancestor boxes. The native reader
        // fills only selected text cells, so retain the DOM range but paint its text rects.
        sheet.textContent = '::selection { background-color: transparent !important; color: inherit !important; }';
        paintSelection();
    }
    function selectedTextRects(range) {
        var rects = [], walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT);
        walker.currentNode = range.startContainer;
        var node = range.startContainer.nodeType === Node.TEXT_NODE ? range.startContainer : walker.nextNode();
        var part = document.createRange();
        while (node) {
            if (sourceOffsets.has(node) && range.intersectsNode(node) && !node.parentElement.closest('script,style,rt,rp') &&
                getComputedStyle(node.parentElement).visibility === 'visible') {
                var from = node === range.startContainer ? range.startOffset : 0;
                var to = node === range.endContainer ? range.endOffset : node.length;
                if (to > from) {
                    part.setStart(node, from); part.setEnd(node, to);
                    rects.push.apply(rects, Array.from(part.getClientRects()).filter(visible));
                }
            }
            if (node === range.endContainer) break;
            node = walker.nextNode();
        }
        return rects;
    }
    function paintSelection() {
        if (selectionLayer) selectionLayer.replaceChildren();
        if (!selectedRange || selectionHighlightTransparent || body === root || !options || !options.selectionColor) return;
        var rects = selectedTextRects(selectedRange);
        if (!rects.length) return;
        if (!selectionLayer) {
            selectionLayer = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
            selectionLayer.setAttribute('data-ng-selection', ''); selectionLayer.setAttribute('aria-hidden', 'true');
            style(selectionLayer, { position: 'fixed', inset: '0', width: '100%', height: '100%', overflow: 'hidden',
                'pointer-events': 'none', 'user-select': 'none', 'z-index': '2147483641' });
            root.appendChild(selectionLayer);
        }
        var shape = document.createElementNS(selectionLayer.namespaceURI, 'path');
        // One path fills overlapping text fragments once, without darker stacked alpha.
        shape.setAttribute('d', rects.map(function (r) {
            return 'M' + r.left + ' ' + r.top + 'H' + r.right + 'V' + r.bottom + 'H' + r.left + 'Z';
        }).join(''));
        style(shape, { fill: options.selectionColor, stroke: 'none', 'fill-rule': 'nonzero' });
        selectionLayer.appendChild(shape);
    }
    function revealText(offset) {
        var point = offset && typeof offset === 'object' ? locationAnchor(offset) : textPoint(offset); if (!point) return null;
        var range = document.createRange(); range.setStart(point.node, point.offset);
        range.setEnd(point.node, Math.min(point.node.length, point.offset + 1));
        var shown = Array.from(range.getClientRects()).some(visible);
        if (!shown) { restoreAnchor(point); readingAnchor = point; paintHighlights(); }
        return { moved: !shown, page: state.pageIndex };
    }
    var overlayElement, overlayClass;
    function mediaOverlay(value) {
        if (overlayElement && overlayClass) overlayElement.classList.remove(overlayClass);
        overlayElement = value.fragment ? document.getElementById(value.fragment) : null;
        overlayClass = value.activeClass || 'ng-epub-overlay-active';
        if (!document.getElementById('ng-epub-overlay-style')) {
            var sheet = document.createElementNS('http://www.w3.org/1999/xhtml', 'style');
            sheet.id = 'ng-epub-overlay-style'; sheet.textContent = '.ng-epub-overlay-active { background-color:rgba(255,210,60,.3) !important; }';
            (document.head || root).appendChild(sheet);
        }
        if (overlayElement && !/\s/.test(overlayClass)) overlayElement.classList.add(overlayClass);
    }
    function galleryAt(x, y) {
        var hit = document.elementFromPoint(x, y), element = hit && hit.closest('.duokan-image-gallery');
        return galleries.find(function (g) {
            return g.element === element;
        });
    }
    function changeGallery(g, delta) {
        if (!g) return false;

        var index = Math.max(0, Math.min(g.cells.length - 1, galleryIndexes.get(g.element) + delta));
        galleryIndexes.set(g.element, index);
        g.cells.forEach(function (cell, i) { adjust(cell, { display: i === index ? 'flow-root' : 'none' }); });
        g.controls.setAttribute('aria-label', '第' + (index + 1) + '张，共' + g.cells.length + '张');
        g.dots.forEach(function (dot, i) { dot.style.opacity = i === index ? '0.55' : '0.16'; });
        if (state.status === 'ready') readingAnchor = captureAnchor();
        return true; // A swipe at either end stays in the gallery, never unexpectedly turns the book.
    }
    function clearSelection() {
        selectedRange = null; dragAnchor = null;
        window.getSelection().removeAllRanges();
        if (selectionLayer) selectionLayer.replaceChildren();
    }
    function pointInText(rect, x, y) {
        return visible(rect) && x >= rect.left - 1 && x <= rect.right + 1 && y >= rect.top - 1 && y <= rect.bottom + 1;
    }
    function rangeCaret(node, x, y) {
        if (!sourceOffsets.has(node) || !node.data.trim()) return null;
        var range = document.createRange(), lo = 0, hi = node.length;
        function contains(from, to) {
            range.setStart(node, from); range.setEnd(node, to);
            return Array.from(range.getClientRects()).some(function (r) { return pointInText(r, x, y); });
        }
        if (!contains(lo, hi)) return null;
        // Native caret hit testing can report the wrong offset in reverse multicolumn text.
        // Substring rectangles remain accurate; narrow the original node without rewriting it.
        while (hi - lo > 1) {
            var mid = Math.floor((lo + hi) / 2);
            if (contains(lo, mid)) hi = mid;
            else if (contains(mid, hi)) lo = mid;
            else return null;
        }
        if (lo > 0 && /[\uDC00-\uDFFF]/.test(node.data[lo])) lo--;
        hi = Math.min(node.length, lo + (node.data.codePointAt(lo) > 65535 ? 2 : 1));
        function distance(offset) {
            range.setStart(node, offset); range.collapse(true);
            var r = range.getBoundingClientRect();
            return Math.abs(x - (r.left + r.right) / 2) + Math.abs(y - (r.top + r.bottom) / 2);
        }
        return { node: node, offset: distance(lo) <= distance(hi) ? lo : hi, hitOffset: lo };
    }
    function caret(x, y) {
        var point = document.caretPositionFromPoint ? document.caretPositionFromPoint(x, y) : null;
        var range = !point && document.caretRangeFromPoint ? document.caretRangeFromPoint(x, y) : null;
        var node = point ? point.offsetNode : range && range.startContainer;
        var offset = point ? point.offset : range && range.startOffset;
        if (!node || node.nodeType !== Node.TEXT_NODE || !body.contains(node) || !sourceOffsets.has(node) ||
            node.parentElement.closest('script,style,rt,rp') || !node.data.trim()) return null;
        var probe = document.createRange();
        probe.setStart(node, Math.max(0, offset - 1)); probe.setEnd(node, Math.min(node.length, offset + 1));
        if (!Array.from(probe.getClientRects()).some(function (r) { return pointInText(r, x, y); })) return rangeCaret(node, x, y);
        return { node: node, offset: Math.min(offset, node.length) };
    }
    function rangeText(selectedRange) {
        // Keep ruby annotations and hidden/source-only text out of copied text.
        var walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT);
        var node = selectedRange.startContainer, text = '', lastBlock = null;
        walker.currentNode = node;
        // Both endpoints come from text carets: only visit selected nodes, not the whole chapter on every drag.
        do {
            if (!sourceOffsets.has(node) || !selectedRange.intersectsNode(node) || node.parentElement.closest('script,style,rt,rp') ||
                getComputedStyle(node.parentElement).visibility !== 'visible') continue;
            var part = document.createRange(); part.selectNodeContents(node);
            if (!part.getClientRects().length) continue;
            var chunk = node.data.slice(node === selectedRange.startContainer ? selectedRange.startOffset : 0,
                node === selectedRange.endContainer ? selectedRange.endOffset : node.length);
            if (chunk) {
                var block = node.parentElement.closest('p,div,li,h1,h2,h3,h4,h5,h6,blockquote,td,pre');
                if (text && block !== lastBlock) text += '\n';
                text += chunk; lastBlock = block;
            }
            if (text.length > 32768) throw new Error('选择的文字过长，请缩小范围');
        } while (node !== selectedRange.endContainer && (node = walker.nextNode()));
        return text;
    }
    function selectionResult() {
        if (!selectedRange) return null;
        var rects = selectedTextRects(selectedRange);
        if (!rects.length) return null;
        var text = rangeText(selectedRange);
        if (!text.trim()) return null;
        function box(r) { return { left: r.left, top: r.top, right: r.right, bottom: r.bottom }; }
        var result = { text: text, start: box(rects[0]), end: box(rects[rects.length - 1]),
            from: textOffset(selectedRange.startContainer, selectedRange.startOffset),
            to: textOffset(selectedRange.endContainer, selectedRange.endOffset, true), length: sourceText.length,
            anchor: dragAnchor ? textOffset(dragAnchor.startContainer, dragAnchor.startOffset) : null };
        if (textTransform) result.display = { revision: textTransform.revision,
            from: displayOffset(selectedRange.startContainer, selectedRange.startOffset),
            to: displayOffset(selectedRange.endContainer, selectedRange.endOffset), length: displayText.length,
            anchor: dragAnchor ? displayOffset(dragAnchor.startContainer, dragAnchor.startOffset) : null };
        if (result.display) {
            result.ownerFrom = textTransform.ownerOffset(result.display.from, false);
            result.ownerTo = textTransform.ownerOffset(result.display.to, true);
        }
        return result;
    }
    function showSelection(range) {
        selectedRange = range;
        var result = selectionResult();
        var selection = window.getSelection(); selection.removeAllRanges();
        if (result) selection.addRange(range); else selectedRange = null;
        paintSelection();
        return result;
    }
    function selectionEdge(x, y) {
        if (!selectedRange || state.mode === 'FIXED') return 0;
        var edge = 24;
        if (state.mode === 'VERTICAL_RL') return x < edge ? 1 : x > viewportWidth - edge ? -1 : 0;
        if (state.mode === 'VERTICAL_LR') return x > viewportWidth - edge ? 1 : x < edge ? -1 : 0;
        return y > viewportHeight - edge ? 1 : y < edge ? -1 : 0;
    }
    var visibleCaretNodes = null, visibleCaretKey = '';
    function nearestVisibleCaret(x, y) {
        var closest = null, distance = Infinity, range = document.createRange();
        var key = generation + '/' + state.pageIndex + '/' + (state.scrollOffset || 0);
        if (key !== visibleCaretKey) {
            visibleCaretKey = key;
            visibleCaretNodes = sourceNodes.filter(function (node) {
                range.selectNodeContents(node); return Array.from(range.getClientRects()).some(visible);
            });
        }
        (visibleCaretNodes || []).forEach(function (node) {
            if (!node.data.trim() || getComputedStyle(node.parentElement).visibility !== 'visible') return;
            range.selectNodeContents(node);
            Array.from(range.getClientRects()).filter(visible).forEach(function (rect) {
                var px = Math.max(Math.max(1, rect.left + 1), Math.min(x, Math.min(viewportWidth - 1, rect.right - 1)));
                var py = Math.max(Math.max(1, rect.top + 1), Math.min(y, Math.min(viewportHeight - 1, rect.bottom - 1)));
                var d = Math.abs(px - x) + Math.abs(py - y);
                if (d < distance) { var point = rangeCaret(node, px, py); if (point) { closest = point; distance = d; } }
            });
        });
        return closest;
    }
    function interact(value) {
        if (value.token !== state.token) return null;
        if (value.action === 'clear' || value.action === 'clearRange') { clearSelection(); return { token: state.token }; }
        if (state.status !== 'ready') return null;
        try {
            if (value.action === 'containsPosition') {
                var a = textPoint(value.offset), b = textPoint(Math.min(sourceText.length, value.offset + 1), true);
                if (!a || !b) return { token: state.token, mapped: false };
                var probe = document.createRange(); probe.setStart(a.node, a.offset); probe.setEnd(b.node, b.offset);
                var rects = Array.from(probe.getClientRects()).filter(function (r) { return r.width > 0 && r.height > 0; });
                return { token: state.token, mapped: !!rects.length, visible: rects.some(visible) };
            }
            if (value.action === 'pageBounds') return { token: state.token, pages: [{ index: value.index || 0, starts: pageStarts() }] };
            if (value.action === 'aloud') {
                var selected = selectionResult(), location = sourceLocation();
                if (window.__ngEpubContent && window.__ngEpubContent.setAloud(value.range || null)) {
                    indexContentNodes(); justifyPageLines(); window.__ngEpubContent.prepareUnderlines(); prepareFullLineUnderlines(); paintHighlights();
                    readingAnchor = locationAnchor(location);
                    if (selected) { var range = highlightRange(selected); if (range) showSelection(range); }
                }
                return { token: state.token };
            }
            if (value.action === 'setRanges') {
                var part = (value.ranges || [])[0];
                return part ? interact(Object.assign({}, part, { token: value.token, action: 'setRange', anchor: part.from }))
                    : (clearSelection(), { token: value.token, selection: null });
            }
            if (value.action === 'hit') {
                var hit = document.elementFromPoint(value.x, value.y);
                var picture = hit && hit.closest('img,svg');
                if (picture && !value.nearest) {
                    var image = picture.localName === 'img' ? picture : picture.querySelector('image');
                    var url = image && (image.getAttribute('src') || image.getAttribute('href') || image.getAttributeNS('http://www.w3.org/1999/xlink', 'href'));
                    if (url) return { token: state.token, image: new URL(url, document.baseURI).href };
                }
                var point = caret(value.x, value.y) || (value.nearest ? nearestVisibleCaret(value.x, value.y) : null);
                if (point && !value.nearest) point = rangeCaret(point.node, value.x, value.y) || point;
                return { token: state.token, offset: point ? textOffset(point.node,
                    !value.nearest && Number.isInteger(point.hitOffset) ? point.hitOffset : point.offset, false) : -1 };
            }
            if (value.action === 'tap') {
                if (selectedRange) { clearSelection(); return { token: state.token, dismissed: true }; }
                var note = noteHitRects.map(function (r) {
                    var dx = value.x - (r.left + r.right) / 2, dy = value.y - (r.top + r.bottom) / 2;
                    return { rect: r, distance: dx * dx + dy * dy, hit: Math.abs(dx) <= r.touch / 2 && Math.abs(dy) <= r.touch / 2 };
                }).filter(function (r) { return r.hit; }).sort(function (a,b) {
                    return a.distance - b.distance || b.rect.order - a.rect.order;
                })[0];
                if (note) return { token: state.token, highlight: note.rect.id, highlightRect: note.rect };
                var mark = highlightRects.slice().reverse().find(function (h) { var r = h.rect; return value.x >= r.left && value.x <= r.right && value.y >= r.top && value.y <= r.bottom; });
                if (mark) { var r = mark.rect; return { token: state.token, highlight: mark.value.id,
                    highlightRect: { left: r.left, top: r.top, right: r.right, bottom: r.bottom } }; }
                var hit = document.elementFromPoint(value.x, value.y);
                var observer = hit;
                while (observer && !triggerObservers.has(observer)) observer = observer.parentElement;
                if (observer) { observer.dispatchEvent(new MouseEvent('click', { bubbles: true })); return { token: state.token, handled: true }; }
                var link = hit;
                while (link && link.localName !== 'a') link = link.parentElement;
                var href = link && (link.getAttribute('href') || link.getAttributeNS('http://www.w3.org/1999/xlink', 'href'));
                return { token: state.token, href: href ? new URL(href, document.baseURI).href : null };
            }
            if (value.action === 'gallerySwipe') return { token: state.token,
                handled: changeGallery(galleryAt(value.x, value.y), value.start ? -1 : 1) };
            if (value.action === 'beginDrag') {
                if (!selectedRange) return null;
                dragAnchor = document.createRange();
                dragAnchor.setStart(value.start ? selectedRange.endContainer : selectedRange.startContainer,
                    value.start ? selectedRange.endOffset : selectedRange.startOffset);
                dragAnchor.collapse(true);
                return { token: state.token, selection: selectionResult() };
            }
            if (value.action === 'selection') return { token: state.token, selection: selectionResult() };
            if (value.action === 'boundaryPart') {
                if (!dragAnchor || ![1, -1].includes(value.direction)) throw new Error('选择拖动已结束');
                var anchor = textOffset(dragAnchor.startContainer, dragAnchor.startOffset);
                var start = value.direction > 0 ? anchor : 0, end = value.direction > 0 ? sourceText.length : anchor;
                var display = null;
                if (textTransform) {
                    var a = displayOffset(dragAnchor.startContainer, dragAnchor.startOffset);
                    display = { revision: textTransform.revision, from: value.direction > 0 ? a : 0,
                        to: value.direction > 0 ? displayText.length : a, length: displayText.length, anchor: a };
                    start = textTransform.toSource(display.from, false); end = textTransform.toSource(display.to, true);
                    anchor = value.direction > 0 ? start : end;
                }
                var whole = highlightRange({ from: start, to: end, display: display });
                return { token: state.token, part: { from: start, to: end, anchor: anchor,
                    text: whole ? rangeText(whole) : '', length: sourceText.length, display: display,
                    ownerFrom: display ? textTransform.ownerOffset(display.from, false) : null,
                    ownerTo: display ? textTransform.ownerOffset(display.to, true) : null } };
            }
            if (value.action === 'rangePart' || value.action === 'setRange') {
                var from = value.from, to = value.to;
                if (!Number.isInteger(from) || !Number.isInteger(to) || from < 0 || to < from || to > sourceText.length)
                    throw new Error('选择范围与原文不一致');
                function boundary(offset) {
                    return offset === 0 || offset === sourceText.length ||
                        !(/[\uD800-\uDBFF]/.test(sourceText[offset - 1]) && /[\uDC00-\uDFFF]/.test(sourceText[offset]));
                }
                if (!boundary(from) || !boundary(to)) throw new Error('选择范围切断了完整字符');
                if (!sourceNodes.length) return { token: state.token, selection: null,
                    part: { from: 0, to: 0, text: '', length: 0 } };
                var sourceRange = highlightRange(value);
                if (value.action === 'rangePart') {
                    var part = { from: from, to: to, text: sourceRange ? rangeText(sourceRange) : '', length: sourceText.length };
                    if (sourceRange) {
                        part.from = textOffset(sourceRange.startContainer, sourceRange.startOffset);
                        part.to = textOffset(sourceRange.endContainer, sourceRange.endOffset, true);
                    }
                    if (textTransform && sourceRange) {
                        part.display = { revision: textTransform.revision, from: displayOffset(sourceRange.startContainer, sourceRange.startOffset),
                            to: displayOffset(sourceRange.endContainer, sourceRange.endOffset), length: displayText.length };
                        part.ownerFrom = textTransform.ownerOffset(part.display.from, false);
                        part.ownerTo = textTransform.ownerOffset(part.display.to, true);
                    }
                    return { token: state.token, part: part };
                }
                var anchorOffset = value.anchor;
                if (!Number.isInteger(anchorOffset) || (anchorOffset !== from && anchorOffset !== to))
                    throw new Error('选择锚点与原文不一致');
                var anchorPoint = textPoint(anchorOffset, value.anchorAffinity === 'after');
                if (value.display && textTransform && value.display.revision === textTransform.revision) {
                    var displayAnchor = value.display.anchor;
                    if (displayAnchor !== value.display.from && displayAnchor !== value.display.to) throw new Error('显示锚点与选区不一致');
                    anchorPoint = displayPoint(displayAnchor);
                }
                dragAnchor = document.createRange(); dragAnchor.setStart(anchorPoint.node, anchorPoint.offset); dragAnchor.collapse(true);
                return { token: state.token, selection: showSelection(sourceRange), length: sourceText.length };
            }
            if (value.action === 'key') return { token: state.token,
                editing: !!(document.activeElement && document.activeElement.closest('input,textarea,select,button,audio,video,[contenteditable="true"]')) };
            if (value.action === 'select') clearSelection();
            var point = caret(value.x, value.y);
            if (!point && value.action === 'extend' && dragAnchor) point = nearestVisibleCaret(value.x, value.y);
            if (!point) return { token: state.token, selection: selectionResult(), edge: value.action === 'extend' ? selectionEdge(value.x, value.y) : 0 };
            var range = document.createRange();
            if (value.action === 'select') {
                var offset = Math.min(point.offset, point.node.length - 1), start = offset, end = offset + 1;
                if (start > 0 && /[\uDC00-\uDFFF]/.test(point.node.data[start])) start--;
                end = start + (point.node.data.codePointAt(start) > 65535 ? 2 : 1);
                range.setStart(point.node, start); range.setEnd(point.node, end);
                // A caret API can snap a blank/image hit to neighbouring text: reject that snap.
                if (!Array.from(range.getClientRects()).some(function (r) {
                    return value.x >= r.left - 2 && value.x <= r.right + 2 && value.y >= r.top - 2 && value.y <= r.bottom + 2;
                })) return { token: state.token, selection: null };
                dragAnchor = range.cloneRange(); dragAnchor.collapse(true);
            } else if (value.action === 'extend' && dragAnchor) {
                var moving = document.createRange(); moving.setStart(point.node, point.offset); moving.collapse(true);
                var before = moving.compareBoundaryPoints(Range.START_TO_START, dragAnchor) < 0;
                var first = before ? moving : dragAnchor, last = before ? dragAnchor : moving;
                range.setStart(first.startContainer, first.startOffset); range.setEnd(last.startContainer, last.startOffset);
            } else return { token: state.token, selection: selectionResult() };
            return { token: state.token, selection: showSelection(range), edge: value.action === 'extend' ? selectionEdge(value.x, value.y) : 0 };
        } catch (error) {
            clearSelection(); return { token: state.token, error: String(error.message || error) };
        }
    }
    function visible(rect) {
        return rect.width > 0 && rect.height > 0 && rect.right > 0.5 &&
            rect.left < viewportWidth - 0.5 && rect.bottom > 0.5 && rect.top < viewportHeight - 0.5;
    }
    function captureAnchor(clip) {
        if (state.mode === 'FIXED') return null;
        var isVisible = !clip ? visible : function (r) { return visible(r) && r.right > clip.left && r.left < clip.left + clip.width &&
            r.bottom > clip.top && r.top < clip.top + clip.height; };
        var walker = document.createTreeWalker(body, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
        var node, range = document.createRange();
        while ((node = walker.nextNode())) {
            if (node.nodeType === Node.ELEMENT_NODE) {
                if (['img', 'svg', 'video'].includes(node.localName) && isVisible(node.getBoundingClientRect()))
                    return { node: node };
                continue;
            }
            if (!node.textContent.trim() || node.parentElement.closest('script,style,rt,rp,svg') ||
                getComputedStyle(node.parentElement).visibility !== 'visible') continue;
            range.selectNodeContents(node);
            if (!Array.from(range.getClientRects()).some(isVisible)) continue;
            // Find the first visible UTF-16 character without measuring every character.
            var low = 1, high = node.length;
            while (low < high) {
                var middle = Math.floor((low + high) / 2);
                range.setEnd(node, middle);
                if (Array.from(range.getClientRects()).some(isVisible)) high = middle;
                else low = middle + 1;
            }
            var offset = low - 1;
            if (offset > 0 && /[\uDC00-\uDFFF]/.test(node.data[offset])) offset--;
            return { node: node, offset: offset };
        }
        return null;
    }
    function pageStarts() {
        if (pageStartsCache) return pageStartsCache;
        var page = state.pageIndex, scroll = state.scrollOffset || 0, starts = [], probe = document.createRange();
        function pageOf(rect) {
            if (state.mode === 'FIXED') return 0;
            var position = axis === 'x' ? (sign > 0 ? rect.left : viewportWidth - rect.right)
                : (sign > 0 ? rect.top : viewportHeight - rect.bottom);
            return Math.max(0, Math.min(state.pageCount - 1, Math.floor(Math.max(0, position) / extent)));
        }
        function remember(page, offset) {
            if (Number.isInteger(offset) && offset >= 0 && offset <= sourceText.length)
                starts[page] = starts[page] == null ? offset : Math.min(starts[page], offset);
        }
        try {
            place(0);
            sourceNodes.forEach(function (node) {
                if (!node.length || !node.data.trim() || getComputedStyle(node.parentElement).visibility !== 'visible') return;
                probe.selectNodeContents(node);
                var pages = new Set(Array.from(probe.getClientRects()).filter(function (r) { return r.width > 0 && r.height > 0; }).map(pageOf));
                pages.forEach(function (target) {
                    var low = 1, high = node.length;
                    while (low < high) {
                        var mid = (low + high) >>> 1; probe.setStart(node, 0); probe.setEnd(node, mid);
                        if (Array.from(probe.getClientRects()).some(function (r) { return r.width > 0 && r.height > 0 && pageOf(r) >= target; })) high = mid;
                        else low = mid + 1;
                    }
                    var at = low - 1;
                    if (at > 0 && /[\uDC00-\uDFFF]/.test(node.data[at])) at--;
                    remember(target, textOffset(node, at, false));
                });
            });
            if (contentCoordinates) Array.from(body.querySelectorAll('img,svg,video')).forEach(function (node) {
                var rect = node.getBoundingClientRect();
                if (rect.width > 0 && rect.height > 0 && getComputedStyle(node).visibility === 'visible')
                    remember(pageOf(rect), window.__ngEpubContent.offset(node, 0, false));
            });
            pageStartsCache = starts.filter(Number.isInteger);
            return pageStartsCache;
        } finally { if (state.scrolled) scrollDocument(scroll); else place(page); }
    }
    function restoreAnchor(anchor) {
        if (!anchor || !body.contains(anchor.node) || state.mode === 'FIXED') return false;
        galleries.forEach(function (gallery) {
            var index = gallery.cells.findIndex(function (cell) { return cell === anchor.node || cell.contains(anchor.node); });
            if (index >= 0 && index !== galleryIndexes.get(gallery.element)) changeGallery(gallery, index - galleryIndexes.get(gallery.element));
        });
        place(0);
        var rect;
        if (anchor.node.nodeType === Node.TEXT_NODE) {
            var range = document.createRange();
            range.setStart(anchor.node, anchor.offset);
            range.setEnd(anchor.node, Math.min(anchor.node.length, anchor.offset + 1));
            rect = Array.from(range.getClientRects()).find(function (r) { return r.width > 0 && r.height > 0; });
        } else rect = anchor.node.getBoundingClientRect();
        if (!rect) return false;
        var position = axis === 'x' ? (sign > 0 ? rect.left : viewportWidth - rect.right)
            : (sign > 0 ? rect.top : viewportHeight - rect.bottom);
        place(Math.floor(Math.max(0, position) / extent));
        return true;
    }
    function flowPosition(value) {
        if (!state.scrolled) return null;
        var anchor = value.location && locationAnchor(value.location);
        if (!anchor && value.textOffset != null) anchor = textPoint(value.textOffset);
        if (!anchor && value.fragment) {
            var element = document.getElementById(value.fragment) || document.getElementsByName(value.fragment)[0];
            if (element) anchor = { node: element };
        }
        if (!anchor || !body.contains(anchor.node)) return null;
        var old = state.scrollOffset || 0;
        scrollDocument(0);
        var rect;
        if (anchor.node.nodeType === Node.TEXT_NODE) {
            var range = document.createRange(); range.setStart(anchor.node, anchor.offset);
            range.setEnd(anchor.node, Math.min(anchor.node.length, anchor.offset + 1)); rect = range.getBoundingClientRect();
        } else rect = anchor.node.getBoundingClientRect();
        var position = axis === 'x' ? (sign > 0 ? rect.left : viewportWidth - rect.right) : rect.top;
        scrollDocument(old);
        // The continuous host clips normal content to these same reader insets.
        // Place the source at that readable edge, not behind the clipped margin.
        var inset = options.readerInsets || {};
        var leading = state.cover ? 0 : axis === 'x' ? (sign > 0 ? inset.left : inset.right) : inset.top;
        return Math.max(0, Math.min(state.scrollLength, position - (leading || 0)));
    }
    function style(el, values) {
        Object.keys(values).forEach(function (key) { el.style.setProperty(key, values[key], 'important'); });
    }
    function restore(el, value) { if (value === null) el.removeAttribute('style'); else el.setAttribute('style', value); }
    function adjust(el, values) {
        if (!adjusted.has(el)) adjusted.set(el, el.getAttribute('style'));
        style(el, values);
    }
    function fitFlowSpacing(w, h) {
        var vertical = state.mode === 'VERTICAL_RL' || state.mode === 'VERTICAL_LR', inline = vertical ? h : w;
        var sides = vertical ? ['top', 'bottom'] : ['left', 'right'];
        Array.from(body.querySelectorAll('section,article,nav,div,ol,ul,li,blockquote,p,h1,h2,h3,h4,h5,h6,span')).forEach(function (el) {
            var css = getComputedStyle(el);
            if (!['block', 'list-item'].includes(css.display) || !['static', 'relative'].includes(css.position)) return;
            var size = parseFloat(vertical ? css.height : css.width), font = parseFloat(css.fontSize);
            var pc = getComputedStyle(el.parentElement), parentSize = parseFloat(vertical ? pc.height : pc.width);
            if (pc.boxSizing === 'border-box') parentSize -= sides.reduce(function (sum, side) {
                return sum + (parseFloat(pc.getPropertyValue('padding-' + side)) || 0) +
                    (parseFloat(pc.getPropertyValue('border-' + side + '-width')) || 0);
            }, 0);
            if (parentSize > 0 && size > Math.min(inline, parentSize)) {
                var cap = {}; cap[vertical ? 'max-height' : 'max-width'] = Math.min(inline, parentSize) + 'px';
                adjust(el, cap); css = getComputedStyle(el); size = parseFloat(vertical ? css.height : css.width);
            }
            var padding = sides.reduce(function (sum, side) { return sum + (parseFloat(css.getPropertyValue('padding-' + side)) || 0); }, 0);
            if (css.boxSizing === 'border-box') size -= padding;
            // Only excessive nested spacing on narrow columns is capped; normal/wide layouts are untouched.
            if (!(size > 0 && size < Math.min(font * 12, inline * 0.6))) return;
            sides.forEach(function (side) {
                ['margin-', 'padding-'].forEach(function (prefix) {
                    var key = prefix + side, amount = parseFloat(css.getPropertyValue(key));
                    if (amount > font) { var values = {}; values[key] = font + 'px'; adjust(el, values); }
                });
            });
        });
    }
    function fitMedia(w, h) {
        Array.from(document.querySelectorAll('img,svg,video,audio,iframe,object,math')).forEach(function (el) {
            var parent = el.parentElement;
            // Image-only paragraphs are atomic. Inline symbols in text remain inline.
            if (parent && parent.localName === 'p' && !parent.textContent.trim() && parent.children.length === 1) {
                adjust(parent, { 'break-inside': 'avoid', 'text-indent': '0' });
                adjust(el, { display: 'block', 'margin-left': 'auto', 'margin-right': 'auto' });
            }
            var css = getComputedStyle(el), ew = parseFloat(css.width), eh = parseFloat(css.height);
            // An over-wide block's used auto margin may be negative; it is not extra page space.
            var horizontalMargin = Math.max(0, parseFloat(css.marginLeft) || 0) + Math.max(0, parseFloat(css.marginRight) || 0);
            var verticalMargin = Math.max(0, parseFloat(css.marginTop) || 0) + Math.max(0, parseFloat(css.marginBottom) || 0);
            var parentW = w, nestedVertical = 0;
            for (var ancestor = el.parentElement; ancestor && ancestor !== body; ancestor = ancestor.parentElement) {
                var ac = getComputedStyle(ancestor), aw = parseFloat(ac.width);
                if (ac.display === 'inline') continue;
                if (ac.boxSizing === 'border-box') aw -= (parseFloat(ac.paddingLeft) || 0) + (parseFloat(ac.paddingRight) || 0) +
                    (parseFloat(ac.borderLeftWidth) || 0) + (parseFloat(ac.borderRightWidth) || 0);
                if (aw > 0) parentW = Math.min(parentW, aw);
                nestedVertical += (parseFloat(ac.paddingTop) || 0) + (parseFloat(ac.paddingBottom) || 0) +
                    (parseFloat(ac.borderTopWidth) || 0) + (parseFloat(ac.borderBottomWidth) || 0);
            }
            var availableW = Math.max(1, parentW - horizontalMargin), availableH = Math.max(1, h - nestedVertical - verticalMargin);
            var rendered = el.getBoundingClientRect();
            // CSS zoom/transform can make the painted box larger than its computed width.
            var scale = Math.min(1, availableW / Math.max(ew, rendered.width), availableH / Math.max(eh, rendered.height));
            if (scale < 1 && ew > 0 && eh > 0) {
                adjust(el, { width: (ew * scale) + 'px', height: (eh * scale) + 'px',
                    'min-width': '0', 'min-height': '0' });
            }
            adjust(el, { 'max-width': availableW + 'px', 'max-height': availableH + 'px', 'break-inside': 'avoid' });
        });
    }
    function prepareGalleries(w, h) {
        Array.from(body.querySelectorAll('.duokan-image-gallery')).forEach(function (el) {
            var cells = Array.from(el.children).filter(function (cell) { return cell.classList.contains('duokan-image-gallery-cell'); });
            if (cells.length < 2) return;
            var ns = 'http://www.w3.org/1999/xhtml', controls = document.createElementNS(ns, 'div');
            controls.setAttribute('role', 'img');
            var dots = cells.map(function () {
                var dot = document.createElementNS(ns, 'span');
                dot.setAttribute('data-ng-gallery-dot', ''); dot.setAttribute('aria-hidden', 'true');
                style(dot, { display: 'block', width: '3px', height: '3px', 'flex-shrink': '0',
                    margin: '0', padding: '0', border: '0', 'border-radius': '50%', background: 'currentColor' });
                controls.appendChild(dot); return dot;
            });
            style(controls, { display: 'flex', 'align-items': 'center', 'justify-content': 'center',
                gap: '3px', height: '32px', 'text-indent': '0', 'user-select': 'none' });
            // A monolithic inline block prevents measurement of a cell spanning two columns
            // from returning the union of both pages (and reserving a whole page of blank space).
            var groupWidth = Math.min(w, parseFloat(getComputedStyle(el).width) || w);
            adjust(el, { display: 'inline-block', width: groupWidth + 'px', 'vertical-align': 'top',
                'break-inside': 'avoid', 'max-width': w + 'px' });
            var height = 0;
            cells.forEach(function (cell) {
                adjust(cell, { display: 'flow-root', margin: '0', 'text-align': 'center' });
                // Reserve title space separately; tall covers must fit together with their controls.
                var img = cell.querySelector('img');
                if (img) {
                    var rect = img.getBoundingClientRect(), overhead = Math.max(0, cell.getBoundingClientRect().height - rect.height);
                    var limit = Math.max(1, h - 60 - overhead);
                    if (rect.height > limit) adjust(img, { height: limit + 'px', width: (rect.width * limit / rect.height) + 'px' });
                }
                height = Math.max(height, cell.getBoundingClientRect().height);
            });
            cells.forEach(function (cell) { adjust(cell, { height: height + 'px', 'box-sizing': 'border-box', overflow: 'hidden' }); });
            el.appendChild(controls);
            var g = { element: el, cells: cells, controls: controls, dots: dots };
            galleries.push(g);
            if (!galleryIndexes.has(el)) galleryIndexes.set(el, 0);
            changeGallery(g, 0);
        });
    }
    function isImagePage() {
        if (body === root || body.querySelector('audio,video,.duokan-image-gallery')) return false;
        var walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT), node;
        while ((node = walker.nextNode())) {
            if (node.data.trim() && !node.parentElement.closest('style,script,svg')) return false;
        }
        return !!body.querySelector('img,svg');
    }
    var activeBleeds = [];
    function bleedBlocks() {
        return Array.from(body.querySelectorAll('*')).map(function (el) {
            var value = getComputedStyle(el).getPropertyValue('--ng-duokan-bleed').trim().toLowerCase();
            var directions = value.match(/left|right|top|bottom/g) || [];
            if (!directions.length || directions.join('') !== value.replace(/\s/g, '')) return null;
            if (el.parentElement !== body && getComputedStyle(el.parentElement).getPropertyValue('--ng-duokan-bleed').trim().toLowerCase() === value) return null;
            var leading = el;
            while (leading.parentElement !== body && !leading.previousElementSibling) leading = leading.parentElement;
            return { element: el, sides: directions, leading: leading === body.firstElementChild };
        }).filter(Boolean);
    }
    function preserveBackground(computed, cover) {
        var hasBackground = computed.backgroundImage !== 'none' || computed.backgroundColor !== 'rgba(0, 0, 0, 0)' && computed.backgroundColor !== 'transparent';
        if (hasBackground) {
            // A viewport layer keeps decorations stationary when the paginated body translates.
            style(root, { background: computed.background });
            style(body, { background: 'transparent', color: computed.color });
        } else if (cover) style(root, { 'background-color': '#000' });
    }
    function fitImagePage(w, h) {
        style(body, { width: w + 'px', height: h + 'px', margin: '0', padding: '0',
            position: 'absolute', left: '0', top: '0', 'box-sizing': 'border-box' });
        var images = Array.from(body.querySelectorAll('img,svg')).filter(function (el) { return !el.parentElement.closest('svg'); });
        if (images.length === 1) {
            var image = images[0], r = image.getBoundingClientRect();
            var zoom = r.width / parseFloat(getComputedStyle(image).width) || 1;
            var ratio = image.localName === 'svg' && image.viewBox.baseVal.width > 0
                ? image.viewBox.baseVal.width / image.viewBox.baseVal.height : image.naturalWidth / image.naturalHeight || r.width / r.height;
            var iw = Math.min(w, h * ratio), ih = iw / ratio;
            // Use a flex page rather than stretching the image to a phone aspect ratio.
            var parent = image.parentElement;
            while (parent && parent !== body) { adjust(parent, { margin: '0', padding: '0', width: '100%', height: '100%' }); parent = parent.parentElement; }
            adjust(image.parentElement, { display: 'flex', 'align-items': 'center', 'justify-content': 'center' });
            adjust(image, { width: (iw / zoom) + 'px', height: (ih / zoom) + 'px', 'max-width': '100%', 'max-height': '100%' });
        } else {
            style(body, { 'padding-top': Math.round(w * 0.06) + 'px' });
            Array.from(body.children).forEach(function (el) {
                if (parseFloat(getComputedStyle(el).marginTop) < 0) adjust(el, { 'margin-top': '0' });
            });
            fitMedia(w, h);
            var bottom = Math.max.apply(null, images.map(function (el) { return el.getBoundingClientRect().bottom; }));
            var padding = parseFloat(body.style.paddingTop) || 0;
            if (bottom > h) {
                var scale = Math.max(0.01, (h - padding) / (bottom - padding));
                Array.from(body.children).forEach(function (el) {
                    var css = getComputedStyle(el);
                    adjust(el, { 'margin-top': (parseFloat(css.marginTop) * scale) + 'px',
                        'margin-bottom': (parseFloat(css.marginBottom) * scale) + 'px' });
                });
                images.forEach(function (el) {
                    var r = el.getBoundingClientRect();
                    adjust(el, { width: (r.width * scale) + 'px', height: (r.height * scale) + 'px' });
                });
            }
        }
    }
    var observedMedia = new WeakSet();
    function mediaState() {
        return Array.from(document.querySelectorAll('video,audio,input,textarea,select,button,iframe,[data-ng-trigger-control]')).map(function (el) {
            if (!(el instanceof HTMLMediaElement)) {
                var rect = el.getBoundingClientRect();
                return { controls: true, visible: visible(rect), left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom, error: null };
            }
            if (!observedMedia.has(el)) {
                observedMedia.add(el);
                el.addEventListener('error', function () {
                    console.error('EPUB media: ' + (el.currentSrc || el.src) + ' ' +
                        (el.error ? el.error.code + ': ' + el.error.message : 'unknown error'));
                });
            }
            var r = el.getBoundingClientRect();
            return { controls: el.controls, visible: visible(r), left: r.left, top: r.top, right: r.right, bottom: r.bottom,
                error: el.error ? el.error.code + ': ' + el.error.message : null, path: el.currentSrc || el.src,
                readyState: el.readyState, networkState: el.networkState };
        });
    }
    function pauseMedia() { document.querySelectorAll('video,audio').forEach(function (el) { el.pause(); }); }
    function frame() { return new Promise(function (resolve) {
        // A service-owned measurement document has no visible frame. Its caller needs
        // computed DOM geometry after resources settle, not a claim of painted pixels.
        if (options && options.measurementOnly) window.setTimeout(resolve, 0);
        else requestAnimationFrame(resolve);
    }); }
    function resources() {
        if (!document.fonts || !document.fonts.ready) return Promise.reject(new Error('WebView 不支持字体就绪检测'));
        return Promise.all([document.fonts.ready].concat(Array.from(document.images || []).map(function (img) {
            img.loading = 'eager';
            return new Promise(function (resolve, reject) {
                function done() {
                    img.removeEventListener('load', done); img.removeEventListener('error', done);
                    if (img.naturalWidth <= 0) {
                        resourceWarnings.add('图片加载失败：' + (img.getAttribute('src') || '').slice(0, 160));
                        if (!img.getAttribute('alt')) img.setAttribute('alt', '图片加载失败');
                    }
                    resolve();
                }
                if (img.complete) done(); else { img.addEventListener('load', done); img.addEventListener('error', done); }
            });
        })));
    }
    function place(page) {
        // There is one page offset only, never native scroll plus a CSS transform.
        window.scrollTo(0, 0); root.scrollLeft = 0; root.scrollTop = 0;
        state.pageIndex = Math.max(0, Math.min(state.pageCount - 1, page));
        if (state.scrolled) { scrollDocument(state.pageIndex * extent); return; }
        if (state.mode !== 'FIXED') {
            var offset = -sign * state.pageIndex * extent;
            style(body, { transform: axis === 'x' ? 'translateX(' + offset + 'px)' : 'translateY(' + offset + 'px)' });
        }
    }
    function scrollDocument(offset) {
        var max = Math.max(0, state.scrollLength - extent);
        state.scrollOffset = Math.max(0, Math.min(max, offset));
        state.pageIndex = state.scrollOffset >= max - 1 ? state.pageCount - 1 : Math.floor(state.scrollOffset / extent);
        style(body, { transform: (axis === 'x' ? 'translateX(' : 'translateY(') + (-sign * state.scrollOffset) + 'px)' });
    }
    function scrollBy(delta) {
        if (!state.scrolled) return;
        var target = (state.scrollOffset || 0) + delta;
        scrollDocument(target); paintHighlights();
        if (delta) {
            var outside = target - state.scrollOffset;
            state.scrollOvershoot = outside ? (Math.sign(outside) === Math.sign(state.scrollOvershoot || 0) ? state.scrollOvershoot : 0) + outside : 0;
        }
        return { atEnd: state.scrollOffset >= Math.max(0, state.scrollLength - extent) - 1,
            overshoot: state.scrollOvershoot || 0 };
    }
    function locate(fragment) {
        if (!fragment || state.mode === 'FIXED') return;
        var target = document.getElementById(fragment) || document.getElementsByName(fragment)[0];
        if (!target) throw new Error('目录锚点不存在');
        place(0);
        var rect = target.getBoundingClientRect();
        var position = axis === 'x' ? (sign > 0 ? rect.left : viewportWidth - rect.right)
            : (sign > 0 ? rect.top : viewportHeight - rect.bottom);
        place(Math.floor(Math.max(0, position) / extent));
    }
    function cancelConfiguration() {
        ++generation;
        if (textRequest) { textRequest.abort(); textRequest = null; }
    }
    function indexContentNodes() {
        sourceNodes = window.__ngEpubContent.nodes();
        sourceOffsets = new WeakMap(); sourceLengths = new WeakMap(); displayOffsets = new WeakMap();
        var contentOffset = 0;
        sourceNodes.forEach(function (node) {
            sourceOffsets.set(node, contentOffset); displayOffsets.set(node, contentOffset);
            sourceLengths.set(node, node.length); contentOffset += node.length;
        });
    }
    var styleUpdatePending = false, styleReflowPending = false, styleSelection = null;
    async function updateStyles(value) {
        cancelConfiguration();
        var mine = generation, location = sourceLocation();
        if (!styleUpdatePending) styleSelection = selectedRange && window.__ngEpubContent.selection(selectedRange);
        state.token = value.token; options.token = value.token;
        try {
            var data = value.documents.find(function (item) { return item.key === window.__ngEpubContent.key(); });
            if (!data) throw new Error('字样式正文已变更');
            var result = window.__ngEpubContent.updateStyles(data);
            styleUpdatePending = styleUpdatePending || result.changed;
            styleReflowPending = styleReflowPending || result.reflow;
            if (!styleUpdatePending) { state.status = 'ready'; return result; }
            result.reflow = styleReflowPending;
            state.status = 'loading';
            indexContentNodes();
            readingAnchor = locationAnchor(location);
            var warnings = await window.__ngEpubContent.loadFonts();
            if (mine !== generation) return { cancelled: true };
            warnings.forEach(function (warning) { resourceWarnings.add(warning); });
            if (result.reflow) await configureLayout(Object.assign({}, options, { token: value.token,
                preservePosition: true, preserveScroll: false, location: location, textOffset: null, fragment: null, last: false }), mine);
            else {
                await window.__ngEpubContent.prepareStyleImages();
                if (mine !== generation) return { cancelled: true };
                justifyPageLines(); readingAnchor = locationAnchor(location);
                window.__ngEpubContent.prepareUnderlines(); prepareFullLineUnderlines(); paintHighlights(); state.status = 'ready';
            }
            if (mine !== generation) return { cancelled: true };
            styleUpdatePending = false; styleReflowPending = false;
            if (styleSelection) { var range = highlightRange(styleSelection); if (range) showSelection(range); }
            styleSelection = null;
            return result;
        } catch (error) {
            if (mine === generation) state = { status: 'error', token: value.token, error: String(error.message || error) };
            return { error: String(error.message || error) };
        }
    }
    async function configure(value) {
        styleUpdatePending = false; styleReflowPending = false; styleSelection = null;
        cancelConfiguration();
        var mine = generation;
        if (value.contentUrl) {
            state = { status: 'loading', token: value.token };
            try {
                var contentUrl = new URL(value.contentUrl, document.baseURI);
                if (contentUrl.origin !== window.location.origin || contentUrl.username || contentUrl.password) throw new Error('正文位置数据来源无效');
                var contentResponse = await fetch(contentUrl.href, { credentials: 'omit', redirect: 'error' });
                if (!contentResponse.ok) throw new Error('正文位置数据加载失败');
                var contentValue = await contentResponse.json();
                if (mine !== generation) return;
                window.__ngEpubContent.apply(contentValue);
                var styleWarnings = await window.__ngEpubContent.loadFonts();
                if (mine !== generation) return;
                styleWarnings.forEach(function (warning) { resourceWarnings.add(warning); });
                contentCoordinates = true;
                indexContentNodes();
                sourceText = contentValue.text; displayText = sourceNodes.map(function (node) { return node.data; }).join('');
                return configureLayout(value, mine);
            } catch (error) {
                if (mine === generation) state = { status: 'error', token: value.token, error: String(error.message || error) };
                return;
            }
        }
        return configureLayout(value, mine);
    }
    function applyChapterBounds(value) {
        var start = value.startFragment ? document.getElementById(value.startFragment) : null;
        var end = value.endFragment ? document.getElementById(value.endFragment) : null;
        if (value.startFragment && !start) throw new Error('章节起点不存在：' + value.startFragment);
        if (value.endFragment && !end) throw new Error('章节终点不存在：' + value.endFragment);
        if (!start && !end) return;
        function visit(parent) {
            Array.from(parent.children).forEach(function (element) {
                var before = start && !element.contains(start) &&
                    (element.compareDocumentPosition(start) & Node.DOCUMENT_POSITION_FOLLOWING);
                var after = end && !element.contains(end) &&
                    (end.compareDocumentPosition(element) & Node.DOCUMENT_POSITION_FOLLOWING);
                if (element === end || before || after) adjust(element, { display: 'none' });
                else visit(element);
            });
        }
        visit(body);
    }
    var retainedInitials = [];
    function readerShadow(reader) {
        var shadow = reader.shadow;
        return shadow ? shadow.x + 'px ' + shadow.y + 'px ' + shadow.radius + 'px ' + shadow.color : 'none';
    }
    function applyFeaturePolicy(value) {
        retainedInitials = [];
        var old = document.getElementById('ng-epub-feature-policy'); if (old) old.remove();
        if (value.fixed) return; // Fixed pages retain authored coordinates and dimensions.
        var features = value.features || {}, reader = value.readerStyle || value.readerDefaults || {};
        var family = readerFamily(reader);
        var headingSelector = 'h1,h2,h3,h4,h5,h6,[role="heading"]';
        var initialCandidates = Array.from(body.querySelectorAll('p>span,p>b,p>strong,p>em,p>i,div>span')).filter(function (el) {
            var text = el.textContent.trim(), css = getComputedStyle(el), parent = getComputedStyle(el.parentElement);
            return text && Array.from(text).length <= 4 && el.parentElement.textContent.trim().startsWith(text) &&
                parseFloat(css.fontSize) >= parseFloat(parent.fontSize) * 1.4 &&
                (css.cssFloat !== 'none' || parseFloat(css.getPropertyValue('initial-letter')) > 1);
        });
        var elements = [root, body].concat(Array.from(body.querySelectorAll('*')).filter(function (el) {
            return !el.closest('svg,math,script,style') && !el.localName.startsWith('ng-') &&
                !['img','video','audio','source','br','hr','canvas'].includes(el.localName);
        }));
        elements.forEach(function (el) {
            var properties = {};
            if (features.font === false) properties['font-family'] = family;
            if (features.color === false) properties.color = reader.color || '#222222';
            if (features.size === false) {
                properties['font-size'] = el === root || el === body ? (reader.fontSize || 18) + 'px' : 'inherit';
                if (el.matches(headingSelector)) properties['font-size'] = el.localName === 'h1' ? '1.5em' : '1.3em';
                if (features.initial !== false && initialCandidates.includes(el)) properties['font-size'] = '2em';
            }
            if (features.decoration === false) {
                Object.assign(properties, { background: 'transparent', border: 'none', 'box-shadow': 'none', 'text-shadow': 'none' });
            }
            if (Object.keys(properties).length) adjust(el, properties);
        });
        if (features.paragraph === false) {
            Array.from(body.querySelectorAll('p')).forEach(function (el) {
                adjust(el, { 'text-indent': reader.indentPx != null ? reader.indentPx + 'px' : (reader.indent || 0) + 'em',
                    'text-align': reader.align || 'justify', 'margin-block-start': '0', 'margin-block-end': reader.paragraphSpacingPx != null
                        ? reader.paragraphSpacingPx + 'px' : (reader.paragraphSpacing || 0) + 'em',
                    'margin-inline-start': '0', 'margin-inline-end': '0',
                    'line-height': reader.lineHeight > 0 ? reader.lineHeight + 'px' : String(1.2 * (1 + (reader.lineSpacing || 0))) });
            });
        }
        if (features.title === false) {
            var title = reader.title || {}, titleFamily = (title.hasFont ? 'NGTitleFont,' : '') + family;
            var segmentedTitles = window.__ngEpubContent ? new Set(window.__ngEpubContent.titleHeadings()) : new Set();
            Array.from(body.querySelectorAll(headingSelector)).forEach(function (heading) {
                adjust(heading, { 'text-align': title.align || 'start',
                    'margin-block-start': (title.top || 0) + 'px', 'margin-block-end': (title.bottom || 0) + 'px' });
                [heading].concat(Array.from(heading.querySelectorAll('*'))).forEach(function (el) {
                    if (el.localName.startsWith('ng-') || ['img','svg','br'].includes(el.localName) || el.closest('svg')) return;
                    adjust(el, { 'font-family': titleFamily, 'font-size': el === heading ? (title.fontSize || reader.fontSize || 18) + 'px' : 'inherit',
                        color: title.color || reader.color || '#222222', 'font-weight': title.weight || 400, 'font-style': 'normal',
                        'line-height': segmentedTitles.has(heading) ? '0px' : title.lineHeight > 0 ? title.lineHeight + 'px' : 'normal', 'text-indent': '0', background: 'transparent', border: 'none',
                        'text-shadow': 'none', 'box-shadow': 'none', 'letter-spacing': 'normal' });
                });
            });
        }
        initialCandidates.forEach(function (el) {
            if (features.initial === false) {
                adjust(el, { 'font-size': 'inherit', 'font-weight': 'inherit', 'font-family': 'inherit',
                    'line-height': 'inherit', 'font-style': 'inherit', color: 'inherit', float: 'none',
                    'initial-letter': 'normal', display: 'inline', margin: '0', padding: '0',
                    position: 'static', 'vertical-align': 'baseline', 'text-shadow': 'none', border: 'none', background: 'transparent' });
            } else retainedInitials.push(el.parentElement);
        });
        if (features.initial === false) {
            var sheet = document.createElementNS('http://www.w3.org/1999/xhtml', 'style'); sheet.id = 'ng-epub-feature-policy';
            sheet.textContent = 'html body p::first-letter,html body div::first-letter{font-size:inherit!important;font-family:inherit!important;' +
                'font-weight:inherit!important;font-style:inherit!important;color:inherit!important;float:none!important;' +
                'initial-letter:normal!important;line-height:inherit!important;margin:0!important;padding:0!important;background:transparent!important;}';
            (document.head || root).appendChild(sheet);
        }
    }
    async function configureLayout(value, mine, retainedLocation) {
        pageStartsCache = null;

        var began = performance.now(), resourcesAt = began;
        var anchor = value.preservePosition && !value.fragment && !value.last ? readingAnchor : null;
        var anchorLocation = retainedLocation || (anchor ? sourceLocation() : null);
        clearSelection();
        if (window.__ngEpubContent && window.__ngEpubContent.clearLineBoxes()) indexContentNodes();
        if (window.__ngEpubContent) window.__ngEpubContent.clearUnderlines();
        if (window.__ngEpubContent) window.__ngEpubContent.clearStyleImages();
        fullLineUnderlines = []; if (fullLineLayer) fullLineLayer.replaceChildren();
        if (window.__ngEpubContent && window.__ngEpubContent.setNotes(value.noteMarkers || [], value.noteMarkerStyle || null)) {
            indexContentNodes();
        }
        if (window.__ngEpubContent) {
            var titleEnabled = !value.fixed && (value.features || {}).title === false;
            var title = (value.readerStyle || value.readerDefaults || {}).title;
            if (window.__ngEpubContent.setTitleSegments(titleEnabled ? value.titleSegments || [] : [], titleEnabled ? title || null : null)) indexContentNodes();
        }
        options = value;
        setSelectionTransparent(value.selectionTransparent);
        state = { status: 'loading', token: value.token };
        try {
            if (window.__ngEpubContent) {
                var noteWarnings = await window.__ngEpubContent.loadNotes();
                if (mine !== generation) return;
                noteWarnings.forEach(function (warning) { resourceWarnings.add(warning); });
            }
            if (document.getElementsByTagName('parsererror').length || document.getElementsByTagNameNS('http://www.mozilla.org/newlayout/xml/parsererror.xml', 'parsererror').length) {
                throw new Error('原书章节格式错误，无法完整显示');
            }
            if (!contentCoordinates) applyTextTransform();
            if (anchorLocation) anchor = locationAnchor(anchorLocation);
            galleries.forEach(function (g) { g.controls.remove(); }); galleries = [];
            restore(root, originalRoot); if (body !== root) restore(body, originalBody);
            // Reader defaults fill gaps in the book CSS. Zero specificity and insertion before
            // author sheets preserve explicitly authored fonts, sizes, colours and line heights.
            var defaultsSheet = document.getElementById('ng-reader-defaults');
            if (defaultsSheet) defaultsSheet.remove();
            if (value.readerDefaults && !value.fixed) {
                var defaults = value.readerDefaults;
                defaultsSheet = document.createElementNS('http://www.w3.org/1999/xhtml', 'style');
                defaultsSheet.id = 'ng-reader-defaults';
                defaultsSheet.textContent = ':where(html){font-size:' + defaults.fontSize + 'px;color:' + defaults.color +
                    ';font-family:' + readerFamily(defaults) +
                    ';font-weight:' + (defaults.weight || 400) + ';font-style:' + (defaults.italic ? 'oblique' : 'normal') +
                    ';text-shadow:' + readerShadow(defaults) +
                    ';line-height:' + (defaults.lineHeight > 0 && defaults.fontSize > 0 ? defaults.lineHeight / defaults.fontSize : 'normal') + '}';
                document.head.insertBefore(defaultsSheet, document.head.firstChild);
            }
            adjusted.forEach(function (value, el) { restore(el, value); }); adjusted.clear();
            applyChapterBounds(value);
            applyFeaturePolicy(value);
            // Determine the native viewport before doing font/resource waits and pagination.
            // A bleed/cover page otherwise gets fully laid out twice at two different sizes.
            var layoutCover = !value.fixed && isImagePage();
            var layoutComputed = getComputedStyle(body);
            var layoutWriting = layoutComputed.writingMode || layoutComputed.webkitWritingMode;
            var layoutBleeds = !value.fixed && !layoutCover && !/^vertical-/.test(layoutWriting) ? bleedBlocks() : [];
            var fullViewport = layoutCover || layoutBleeds.length > 0;
            if (!value.fixed && typeof value.viewportFull === 'boolean' && value.viewportFull !== fullViewport) {
                state = { status: 'viewport', token: value.token, fullViewport: fullViewport };
                return;
            }
            var reader = value.readerStyle || value.readerDefaults || {}, fontWarnings = [];
            if (await loadReaderFont('reader', !value.fixed && reader.fontUrl, mine)) fontWarnings.push('reader');
            if (mine !== generation) return;
            if (await loadReaderFont('title', !value.fixed && (value.features || {}).title === false && (reader.title || {}).fontUrl, mine)) fontWarnings.push('title');
            if (mine !== generation) return;

            if (body !== root) {
                var viewport = originalViewport || document.querySelector('meta[name="viewport"]');
                if (!viewport) {
                    viewport = document.createElementNS('http://www.w3.org/1999/xhtml', 'meta');
                    viewport.setAttribute('name', 'viewport'); document.head.appendChild(viewport);
                }
                viewport.setAttribute('content', 'width=device-width,initial-scale=1');
            }
            await frame();
            if (mine !== generation) return;
            await resources();
            resourcesAt = performance.now();
            if (mine !== generation) return;
            var warnings = Array.from(resourceWarnings);
            if (document.querySelector('script')) warnings.push('书籍脚本未启用，显示静态内容和标准媒体控件');
            if (Array.from(document.querySelectorAll('[src],[href]')).some(function (el) {
                return el.localName !== 'a' && /^https?:\/\//i.test(el.getAttribute('src') || el.getAttribute('href') || '');
            })) warnings.push('本书含远程资源，离线阅读未加载这些资源');
            // Legacy books may request device-only fonts (e.g. Sony res://). Once settled,
            // normal CSS fallback remains usable; do not fetch fonts or reject the whole document.
            if (Array.from(document.fonts).some(function (font) { return font.status === 'error'; })) warnings.push('原书部分字体不可用，已使用 CSS 后备字体');
            if (Array.from(document.querySelectorAll('link[rel="stylesheet"]')).some(function (link) {
                return !link.disabled && (!link.media || matchMedia(link.media).matches) && !link.sheet;
            })) throw new Error('原书样式表加载失败');
            // innerWidth can include oversized image overflow on mobile WebView.
            // The host's measured viewport is authoritative, not the document's scroll bounds.
            var w = value.width || root.clientWidth, h = value.height || root.clientHeight;
            if (!(w > 0 && h > 0)) throw new Error('正文区域尺寸无效');
            viewportWidth = w; viewportHeight = h;
            var computed = getComputedStyle(body), writing = computed.writingMode || computed.webkitWritingMode;
            var authorBackground = { background: computed.background, backgroundImage: computed.backgroundImage,
                backgroundColor: computed.backgroundColor, color: computed.color };
            var mode = value.fixed ? 'FIXED' : writing === 'vertical-rl' ? 'VERTICAL_RL' : writing === 'vertical-lr' ? 'VERTICAL_LR' : 'HORIZONTAL';
            style(root, { width: w + 'px', height: h + 'px', 'min-width': '0', 'min-height': '0',
                margin: '0', padding: '0', border: '0', position: 'fixed', left: '0', top: '0',
                overflow: 'hidden', 'touch-action': 'none', 'overscroll-behavior': 'none', '-webkit-text-size-adjust': 'none' });
            var cover = layoutCover;
            var bleeds = layoutBleeds;
            var bleed = bleeds.length > 0;
            activeBleeds = bleeds;
            chapterBoundaries = (value.sourceChapters || []).map(function (chapter) {
                return { chapter: chapter.chapter, offset: chapter.fragment ? sourceAnchors.get(chapter.fragment) : 0 };
            }).filter(function (chapter) { return Number.isInteger(chapter.chapter) && Number.isInteger(chapter.offset); });
            state = { status: 'loading', token: value.token, mode: mode, cover: cover, bleed: bleed,
                bleedHeader: bleeds.some(function (b) { return b.leading && b.sides.includes('top'); }),
                pageCount: 1, pageIndex: 0, warnings: warnings, fontWarnings: fontWarnings };
            state.scrolled = !value.fixed && !cover && /^scrolled-/.test(value.flow || '');
            if (value.fixed) {
                if (root.localName === 'svg') {
                    var vb = root.viewBox.baseVal;
                    var sw = vb.width || root.width.baseVal.value, sh = vb.height || root.height.baseVal.value;
                    if (!(sw > 0 && sh > 0)) throw new Error('固定 SVG 缺少有效尺寸');
                    state.fixedWidth = sw; state.fixedHeight = sh;
                    if (!vb.width) root.setAttribute('viewBox', '0 0 ' + sw + ' ' + sh);
                    var ss = Math.min(w / sw, h / sh);
                    style(root, { width: (sw * ss) + 'px', height: (sh * ss) + 'px',
                        left: ((w - sw * ss) / 2) + 'px', top: ((h - sh * ss) / 2) + 'px', overflow: 'hidden' });
                } else {
                    var content = fixedViewport;
                    var wm = content.match(/(?:^|[,;\s])width\s*=\s*([^,;\s]*)/i);
                    var hm = content.match(/(?:^|[,;\s])height\s*=\s*([^,;\s]*)/i);
                    var fw = wm && wm[1].toLowerCase() === 'device-width' ? value.deviceWidth || w
                        : wm && /^[0-9]/.test(wm[1]) ? parseFloat(wm[1]) : 0;
                    var fh = hm && hm[1].toLowerCase() === 'device-height' ? value.deviceHeight || h
                        : hm && /^[0-9]/.test(hm[1]) ? parseFloat(hm[1]) : 0;
                    if (value.fixedWidth > 0 && value.fixedHeight > 0) { fw = value.fixedWidth; fh = value.fixedHeight; }
                    if (!(fw > 0 && fh > 0)) throw new Error('固定版式缺少有效 viewport');
                    var scale = Math.min(w / fw, h / fh);
                    state.fixedWidth = fw; state.fixedHeight = fh;
                    if (!value.virtualFixed) style(body, { width: fw + 'px', height: fh + 'px', margin: '0', position: 'absolute',
                        overflow: 'hidden',
                        left: ((w - fw * scale) / 2) + 'px', top: ((h - fh * scale) / 2) + 'px',
                        'transform-origin': '0 0', transform: 'scale(' + scale + ')' });
                    if (value.fixedSvg) {
                        var svg = body.querySelector('svg');
                        if (svg) adjust(svg, { width: '100%', height: '100%', display: 'block' });
                    }
                }
            } else {
                if (body === root) throw new Error('流式正文必须是 XHTML');
                if (value.readerStyle) {
                    var family = readerFamily(value.readerStyle);
                    style(root, { color: value.readerStyle.color,
                        'font-size': value.readerStyle.fontSize + 'px', 'font-family': family,
                        'font-weight': value.readerStyle.weight || 400, 'font-style': value.readerStyle.italic ? 'oblique' : 'normal',
                        'text-shadow': readerShadow(value.readerStyle) });
                    style(body, { color: value.readerStyle.color,
                        'font-size': value.readerStyle.fontSize + 'px', 'letter-spacing': value.readerStyle.letterSpacing + 'em',
                        'font-family': family, 'font-weight': value.readerStyle.weight || 400,
                        'font-style': value.readerStyle.italic ? 'oblique' : 'normal', 'text-shadow': readerShadow(value.readerStyle),
                        'line-height': value.readerStyle.lineHeight > 0 ? value.readerStyle.lineHeight + 'px'
                            : String(1.2 * (1 + (value.readerStyle.lineSpacing || 0))) });
                    Array.from(body.querySelectorAll('p')).forEach(function (element) {
                        adjust(element, { 'margin-block-end': value.readerStyle.paragraphSpacingPx != null ? value.readerStyle.paragraphSpacingPx + 'px'
                            : (value.readerStyle.paragraphSpacing || 0) + 'em',
                            'font-size': 'inherit', 'font-family': 'inherit', 'font-weight': 'inherit', 'font-style': 'inherit',
                            'text-shadow': 'inherit', 'text-align': value.readerStyle.align || 'justify', color: 'inherit', 'line-height': 'inherit',
                            'text-indent': value.readerStyle.indentPx != null ? value.readerStyle.indentPx + 'px' : (value.readerStyle.indent || 0) + 'em' });
                    });
                }
                // A retained drop cap and its zero-indent paragraph form one authored layout.
                retainedInitials.forEach(function (paragraph) { adjust(paragraph, { 'text-indent': '0' }); });
                axis = mode === 'HORIZONTAL' && !state.scrolled ? 'x' : 'y';
                sign = computed.direction === 'rtl' ? -1 : 1;
                if (state.scrolled) {
                    axis = mode === 'HORIZONTAL' ? 'y' : 'x';
                    sign = mode === 'VERTICAL_RL' ? -1 : 1;
                    state.scrollAxis = axis; state.scrollSign = sign;
                }
                extent = axis === 'x' ? w : h;
                if (value.readerStyle && !cover) {
                    // The preset owns the page canvas in reader-style mode. Nested decorations
                    // and images remain authored content; the native page supplies its background.
                    style(root, { background: 'transparent' });
                    style(body, { background: 'transparent' });
                } else preserveBackground(authorBackground, cover);
                if (cover) {
                    fitImagePage(w, h);
                } else {
                var readerInsets = (bleed || value.continuousInsets) && value.readerInsets || { left: 0, top: 0, right: 0 };
                var topInset = Math.max(0, readerInsets.top || 0);
                var bottomInset = value.continuousInsets ? 0 : Math.max(0, readerInsets.bottom || 0);
                if (bleed) style(body, { 'padding-left': (parseFloat(computed.paddingLeft) + Math.max(0, readerInsets.left || 0)) + 'px',
                    'padding-right': (parseFloat(computed.paddingRight) + Math.max(0, readerInsets.right || 0)) + 'px' });
                else if (value.continuousInsets) style(body, {
                    'padding-left': (parseFloat(computed.paddingLeft) + Math.max(0, readerInsets.left || 0)) + 'px',
                    'padding-right': (parseFloat(computed.paddingRight) + Math.max(0, readerInsets.right || 0)) + 'px' });
                if (value.continuousInsets) style(body, {
                    'padding-bottom': (parseFloat(computed.paddingBottom) + Math.max(0, readerInsets.bottom || 0)) + 'px' });
                var inset = axis === 'x'
                    ? parseFloat(computed.paddingLeft) + parseFloat(computed.paddingRight) + parseFloat(computed.borderLeftWidth) + parseFloat(computed.borderRightWidth)
                    : parseFloat(computed.paddingTop) + parseFloat(computed.paddingBottom) + parseFloat(computed.borderTopWidth) + parseFloat(computed.borderBottomWidth);
                if (!(extent > inset)) throw new Error('原书内边距大于阅读区域');
                // Browser fragmentation, not screenshot slicing: each column is one viewport.
                style(body, { 'box-sizing': 'border-box', width: w + 'px', height: (h - topInset - bottomInset) + 'px', margin: '0',
                    'min-width': '0', 'max-width': 'none', 'min-height': '0', 'max-height': 'none',
                    'column-width': (extent - inset) + 'px', 'column-gap': inset + 'px', 'column-count': 'auto',
                    'column-fill': 'auto', overflow: 'visible', position: 'absolute', left: '0', top: topInset + 'px',
                    'transform-origin': '0 0', transform: 'none' });
                style(body, { 'overflow-wrap': 'anywhere' });
                if (state.scrolled) {
                    style(body, { 'column-width': 'auto', 'column-count': 'auto', 'column-gap': 'normal' });
                    if (axis === 'y') style(body, { height: 'auto' });
                    else style(body, { width: 'max-content', left: sign < 0 ? 'auto' : '0', right: sign < 0 ? '0' : 'auto' });
                }
                fitFlowSpacing(w, h);
                var box = getComputedStyle(body);
                var contentW = w - parseFloat(box.paddingLeft) - parseFloat(box.paddingRight) - parseFloat(box.borderLeftWidth) - parseFloat(box.borderRightWidth);
                var contentH = h - topInset - bottomInset - parseFloat(box.paddingTop) - parseFloat(box.paddingBottom) - parseFloat(box.borderTopWidth) - parseFloat(box.borderBottomWidth);
                fitMedia(contentW, contentH);
                bleeds.forEach(function (b) {
                    var el = b.element, css = getComputedStyle(el);
                    var rect = el.getBoundingClientRect(), column = Math.floor((rect.left + rect.right) / 2 / w);
                    var left = b.sides.includes('left') ? Math.max(0, rect.left - column * w) : 0;
                    var right = b.sides.includes('right') ? Math.max(0, (column + 1) * w - rect.right) : 0;
                    var width = rect.width + left + right;
                    adjust(el, { width: width + 'px', 'max-width': 'none', 'box-sizing': 'border-box',
                        'margin-left': ((parseFloat(css.marginLeft) || 0) - left) + 'px',
                        'margin-right': ((parseFloat(css.marginRight) || 0) - right) + 'px',
                        'margin-top': ((parseFloat(css.marginTop) || 0) - (b.leading && b.sides.includes('top') ? Math.max(0, rect.top) : 0)) + 'px',
                        'break-inside': 'avoid' });
                    Array.from(el.querySelectorAll('img')).forEach(function (img) {
                        if (adjusted.has(img)) restore(img, adjusted.get(img));
                        adjust(img, { 'max-width': width + 'px', 'break-inside': 'avoid' });
                    });
                });
                prepareGalleries(contentW, contentH);
                if (Array.isArray(value.galleryIndexes)) galleries.forEach(function (gallery, index) {
                    var target = value.galleryIndexes[index];
                    if (Number.isInteger(target)) changeGallery(gallery, target - (galleryIndexes.get(gallery.element) || 0));
                });
                await frame();
                if (mine !== generation) return;
                if (window.__ngEpubContent) {
                    var nineWarnings = await window.__ngEpubContent.prepareStyleImages();
                    if (mine !== generation) return;
                    warnings.push.apply(warnings, nineWarnings);
                }
                var length = axis === 'x' ? body.scrollWidth : body.scrollHeight;
                // In legacy HTML the body's scroll size may be the viewport's size, even
                // while columns extend beyond it. Range geometry also covers negative RTL overflow.
                var contents = document.createRange(); contents.selectNodeContents(body);
                var contentRect = contents.getBoundingClientRect();
                length = Math.max(length, extent, axis === 'x'
                    ? (sign < 0 ? w - contentRect.left : contentRect.right)
                    : (sign < 0 ? h - contentRect.top : contentRect.bottom));
                if (state.scrolled) {
                    var flowContents = document.createRange(); flowContents.selectNodeContents(body);
                    var flowRect = flowContents.getBoundingClientRect();
                    length = axis === 'y' ? Math.max(length + topInset, flowRect.bottom)
                        : Math.max(body.getBoundingClientRect().width, (flowRect.width || flowRect.height)
                            ? (sign < 0 ? w - flowRect.left : flowRect.right) : 0);
                    state.scrollLength = length; state.scrollExtent = extent;
                    state.scrollHeight = axis === 'y' ? length : h;
                }
                state.pageCount = Math.max(1, Math.ceil((length - 1) / extent));
                if (state.pageCount > 100000) throw new Error('分页数量超出限制');
                justifyPageLines();
                if (anchorLocation) anchor = locationAnchor(anchorLocation);
                }
            }
            if (value.fixed && window.__ngEpubContent) {
                var fixedNineWarnings = await window.__ngEpubContent.prepareStyleImages();
                if (mine !== generation) return;
                warnings.push.apply(warnings, fixedNineWarnings);
            }
            if (!value.preserveScroll) place(value.last ? state.pageCount - 1 : (value.page || 0));
            locate(value.fragment);
            if (value.location) restoreAnchor(locationAnchor(value.location));
            if (value.textOffset != null) restoreAnchor(textPoint(value.textOffset));
            var restored = restoreAnchor(anchor);
            await frame(); await frame();
            if (mine !== generation) return;
            readingAnchor = restored ? anchor : captureAnchor();
            state.timings = { resourcesMs: Math.round(resourcesAt - began), layoutMs: Math.round(performance.now() - resourcesAt) };
            state.hasSelectableText = sourceNodes.some(function (node) {
                if (!node.data.trim() || getComputedStyle(node.parentElement).visibility !== 'visible') return false;
                var range = document.createRange(); range.selectNodeContents(node);
                return Array.from(range.getClientRects()).some(function (r) { return r.width > 0 && r.height > 0; });
            });
              if (window.__ngEpubContent) window.__ngEpubContent.prepareUnderlines();
              prepareFullLineUnderlines();
              state.status = 'ready';
              paintHighlights();
        } catch (error) {
            if (mine === generation) state = { status: 'error', token: value.token, error: String(error.message || error) };
        }
    }
    async function move(value) {
        cancelConfiguration();
        var mine = generation;
        if (!value.preserveSelection) clearSelection();
        if (!value.preserveScroll) pauseMedia();
        state.token = value.token;
        state.status = 'loading';
        try {
            if (!value.preserveScroll) place(value.last ? state.pageCount - 1 : (value.page || 0));
            locate(value.fragment);
            delete state.timings;
            if (value.location) restoreAnchor(locationAnchor(value.location));
            if (value.textOffset != null) restoreAnchor(textPoint(value.textOffset));
            await frame(); await frame();
            if (mine === generation) {
                readingAnchor = captureAnchor();
                  state.status = 'ready';
                  paintHighlights();
            }
        } catch (error) { if (mine === generation) state = { status: 'error', token: value.token, error: String(error) }; }
    }
    window.__ngEpub = Object.freeze({ configure: configure, updateStyles: updateStyles, move: move, interact: interact, pauseMedia: pauseMedia,
        setLinkHandler: function (handler) { linkHandler = typeof handler === 'function' ? handler : null; },
        cancelConfiguration: cancelConfiguration,
        scrollBy: scrollBy,
        beginScroll: function () { state.scrollOvershoot = 0; },
        setHighlights: setHighlights,
        setSelectionTransparent: setSelectionTransparent,
        mediaOverlay: mediaOverlay,
        revealText: revealText,
        captureLocation: function (clip) { readingAnchor = captureAnchor(clip); return sourceLocation(); },
        flowPosition: flowPosition,
        state: function () {
            var bleedRects = activeBleeds.map(function (value) { var r = value.element.getBoundingClientRect();
                return { left: r.left, right: r.right, top: r.top, bottom: r.bottom, sides: value.sides }; });
            var inset = options && options.readerInsets || {};
            function visible(r) { return r.right > 0 && r.left < viewportWidth && r.bottom > 0 && r.top < viewportHeight; }
            return Object.assign({}, state, { status: state.status,
            hideHeader: !!state.cover || bleedRects.some(function (r) { return visible(r) && r.sides.includes('top') && r.top < (inset.top || 0); }),
            hideFooter: !!state.cover || bleedRects.some(function (r) { return visible(r) && r.sides.includes('bottom') && r.bottom > viewportHeight - (inset.bottom || 0); }),
            media: mediaState(), location: sourceLocation(), textLength: sourceText.length, chapterBoundaries: chapterBoundaries,
            galleryIndexes: galleries.map(function (gallery) { return galleryIndexes.get(gallery.element) || 0; }),
            bleedRects: bleedRects }); } });
})(window);
