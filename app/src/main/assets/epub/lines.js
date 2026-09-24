(function install(window) {
    'use strict';
    if (window.__ngEpubLines) return;
    var document = window.document, saved = null;
    window.__ngEpubLinesInstall = function (target) {
        if (target.location.origin !== window.location.origin) throw new Error('EPUB 行排版来源不一致');
        install(target);
    };
    function clear() {
        if (!saved) return null;
        var old = saved; saved = null;
        old.children.forEach(function (children, parent) { parent.replaceChildren.apply(parent, children); });
        old.text.forEach(function (text, node) { node.data = text; });
        old.styles.forEach(function (style, node) { if (style == null) node.removeAttribute('style'); else node.setAttribute('style',style); });
        old.imageIds.forEach(function (id,node) { if(id==null)node.removeAttribute('data-ng-line-image');else node.setAttribute('data-ng-line-image',id); });
        old.lineDeltas.forEach(function (delta,node) { if(delta==null)node.removeAttribute('data-ng-line-delta');else node.setAttribute('data-ng-line-delta',delta); });
        return old.entries;
    }
    function sliceRuns(runs, from, to) {
        return runs.filter(function (r) { return r[0]<to && r[1]>from; }).map(function (r) {
            var a=Math.max(from,r[0]),b=Math.min(to,r[1]);
            return [a-from,b-from,r[4]?r[2]+a-r[0]:r[2],r[4]?r[2]+b-r[0]:r[3],r[4]];
        });
    }
    function apply(value, entries) {
        clear();
        var body=document.body, vertical=value.vertical, reverse=vertical && value.reverse;
        var range=document.createRange(), blocks=new Map(), protectedPages=new Set(), anchors=new Map(), atoms=new Map(), atomicSeen=new Set();
        var allRows=[], children=new Map(), text=new Map(), styles=new Map(), imageIds=new Map(), lineDeltas=new Map(), originalElements=new Set();
        function rect(r) {
            var inline=vertical?(r.top+r.bottom)/2:(r.left+r.right)/2;
            var page=Math.floor((value.sign>0?inline:value.extent-inline)/value.extent);
            return {page:page,top:vertical?(reverse?value.cross-r.right:r.left):r.top,
                bottom:vertical?(reverse?value.cross-r.left:r.right):r.bottom,left:r.left,right:r.right,y:r.top,y2:r.bottom};
        }
        function same(a,b) {
            return a.page===b.page && Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top)>Math.min(a.bottom-a.top,b.bottom-b.top)/2;
        }
        function protect(element) {
            Array.from(element.getClientRects()).forEach(function (r) { if(r.width>0 && r.height>0){var box=rect(r);protectedPages.add(box.page);anchors.set(box.page,Math.max(anchors.get(box.page)||-Infinity,box.bottom));} });
        }
        function atomic(element) {
            if(element===body || atomicSeen.has(element))return;
            atomicSeen.add(element);
            var css=window.getComputedStyle(element),boxes=Array.from(element.getClientRects()).filter(function(r){return r.width>0&&r.height>0;});
            var positioned=Array.from(element.querySelectorAll('*')).some(function(el){var s=window.getComputedStyle(el);return s.cssFloat!=='none'||s.position==='absolute'||s.position==='fixed';});
            if(boxes.length!==1||css.cssFloat!=='none'||css.position==='absolute'||css.position==='fixed'||css.transform!=='none'||positioned){protect(element);return;}
            var box=rect(boxes[0]);
            var ink=document.createRange();ink.selectNodeContents(element);
            var inkBoxes=Array.from(ink.getClientRects()).filter(function(r){return r.width>0&&r.height>0;}).map(rect);
            if(inkBoxes.some(function(r){return r.page!==box.page;})){protect(element);protect(ink);return;}
            inkBoxes.forEach(function(r){box.top=Math.min(box.top,r.top);box.bottom=Math.max(box.bottom,r.bottom);});
            // A block spanning a page edge keeps its original fragmentation. Text after its
            // final fragment can still share the remaining bottom space.
            if(box.top<value.start-.5||box.bottom>value.end+.5){protect(element);protect(ink);return;}
            atoms.set(element,Object.assign({atom:element,delta:0,image:!element.textContent.trim()&&(element.matches('img,svg,video')||!!element.querySelector('img,svg,video'))},box));
        }
        function owner(node) {
            var element=node.parentElement;
            while(element && element!==body && /^(inline|contents)$/.test(window.getComputedStyle(element).display))element=element.parentElement;
            return element;
        }
        function supported(block) {
            var css=window.getComputedStyle(block);
            var cap=window.getComputedStyle(block,'::first-letter'),firstLine=window.getComputedStyle(block,'::first-line');
            if(cap.cssFloat!=='none'||cap.fontSize!==css.fontSize||['font-size','font-weight','font-family','color'].some(function(p){return firstLine.getPropertyValue(p)!==css.getPropertyValue(p);}))return false;
            if(block.closest('table,pre,svg,math') || block.querySelector('table,pre,svg,math,ruby,[data-ng-trigger-control]') ||
                block===body&&block.querySelector('img,video') || css.cssFloat!=='none' || block!==body&&(css.position==='absolute' || css.position==='fixed' || css.transform!=='none') ||
                css.backgroundImage!=='none' || !/^(transparent|rgba\(0, 0, 0, 0\))$/.test(css.backgroundColor) ||
                [css.borderTopWidth,css.borderBottomWidth,css.borderLeftWidth,css.borderRightWidth].some(function(x){return parseFloat(x)>0;}))return false;
            return !Array.from(block.children).some(function(el){return !/^(inline|contents)/.test(window.getComputedStyle(el).display);});
        }
        entries.forEach(function(entry,index){
            var node=entry.node,block=owner(node);
            if(!block || !node.data.trim() || window.getComputedStyle(node.parentElement).visibility!=='visible')return;
            range.selectNodeContents(node);
            if(!Array.from(range.getClientRects()).some(function(r){return r.width>0&&r.height>0;}))return;
            if(!supported(block)){atomic(block.closest('table,pre,svg,math')||block);return;}
            var info=blocks.get(block);if(!info){info={block:block,parts:[],rows:[],images:Array.from(block.querySelectorAll('ng-nine-slice')).map(function(el){return {node:el,rects:Array.from(el.getClientRects()).map(function(r){return {left:r.left,right:r.right,top:r.top,bottom:r.bottom};})};})};blocks.set(block,info);}
            var start=0;
            while(start<node.length){
                var count=node.data.codePointAt(start)>65535?2:1;
                range.setStart(node,start);range.setEnd(node,Math.min(node.length,start+count));
                var first=Array.from(range.getClientRects()).find(function(r){return r.width>0&&r.height>0;});
                if(!first){start+=count;continue;}
                var target=rect(first),low=start+count,high=Math.min(node.length,start+32);
                function onLine(end) {
                    range.setEnd(node,end);
                    return Array.from(range.getClientRects()).filter(function(r){return r.width>0&&r.height>0;}).every(function(r){return same(rect(r),target);});
                }
                // Bound the search to this visual row before bisecting. Probing the entire
                // remaining paragraph on every row would repeatedly allocate all later rects.
                while(high<node.length && onLine(high)){low=high;high=Math.min(node.length,start+(high-start)*2);}
                while(low<high){
                    var mid=Math.ceil((low+high)/2);
                    if(onLine(mid))low=mid;
                    else high=mid-1;
                }
                var end=low;if(end<node.length&&/[\uDC00-\uDFFF]/.test(node.data[end]))end--;
                if(end<=start)end=start+count;
                range.setEnd(node,end);
                var boxes=Array.from(range.getClientRects()).filter(function(r){return r.width>0&&r.height>0;}).map(rect);
                var row=info.rows.find(function(r){return same(r,target);});
                if(!row){row=Object.assign({start:{node:node,offset:start},startTop:target.top,startInline:vertical?first.top:first.left,block:block,parts:[],delta:0},target);info.rows.push(row);allRows.push(row);}
                boxes.forEach(function(r){row.top=Math.min(row.top,r.top);row.bottom=Math.max(row.bottom,r.bottom);});
                var part={entry:entry,index:index,from:start,to:end,row:row};row.parts.push(part);info.parts.push(part);start=end;
            }
        });
        // Rich blocks move as a whole; their internal CSS, events and nodes stay untouched.
        Array.from(body.querySelectorAll('img,svg,video,table,pre,math')).forEach(function(el){var parent=owner(el);atomic(el.closest('table,pre,svg,math')||(parent===body?el:parent)||el);});
        var atomicRoots=Array.from(atoms.keys()).filter(function(el){return !Array.from(atoms.keys()).some(function(other){return other!==el&&other.contains(el);});});
        Array.from(blocks.entries()).forEach(function(pair){if(atomicRoots.some(function(el){return el===pair[0]||el.contains(pair[0]);})){
            allRows=allRows.filter(function(row){return row.block!==pair[0];});blocks.delete(pair[0]);
        }});
        atomicRoots.forEach(function(el){allRows.push(atoms.get(el));});
        Array.from(body.querySelectorAll('*')).forEach(function(el){
            if(atomicRoots.some(function(root){return root===el||root.contains(el);}))return;
            var css=window.getComputedStyle(el);if(css.cssFloat!=='none'||css.position==='absolute'||css.position==='fixed')protect(el);
        });
        // Border-image fragments and note boxes belong to the text line, including their outer edges.
        Array.from(body.querySelectorAll('ng-nine-slice,ng-note-pin')).forEach(function(el){
            Array.from(el.getClientRects()).forEach(function(r){var box=rect(r),row=allRows.find(function(row){return same(row,box);});
                if(row){row.top=Math.min(row.top,box.top);row.bottom=Math.max(row.bottom,box.bottom);}
            });
        });
        var pages=new Map();allRows.forEach(function(row){if(!pages.has(row.page))pages.set(row.page,[]);pages.get(row.page).push(row);});
        var shifted=0;
        pages.forEach(function(rows,page){
            rows.sort(function(a,b){return a.top-b.top;});
            var lines=[];
            rows.forEach(function(row){var line=lines.find(function(line){return same(line,row);});
                if(line){line.members.push(row);line.top=Math.min(line.top,row.top);line.bottom=Math.max(line.bottom,row.bottom);}
                else lines.push({page:page,top:row.top,bottom:row.bottom,members:[row]});
            });
            var anchored=anchors.has(page);
            if(anchored)lines=lines.filter(function(line){return line.top>=anchors.get(page)-.5;});
            if(lines.length<(anchored?1:2))return;
            var last=lines[lines.length-1],surplus=value.end-last.bottom;
            if(last.members.some(function(row){return row.image;}))return;
            // Same short-page guard as native TextPage.upLinesPosition, with browser line boxes.
            if(surplus<=.25 || surplus-value.advance>=last.bottom-last.top)return;
            var step=surplus/(lines.length-(anchored?0:1));
            lines.forEach(function(line,index){line.members.forEach(function(row){row.delta=(index+(anchored?1:0))*step;if(row.delta)shifted++;});});
        });
        if(!shifted)return {entries:entries,shifted:0,protectedPages:Array.from(protectedPages)};
        function snapshot(parent){
            if(children.has(parent))return;
            children.set(parent,Array.from(parent.childNodes));originalElements.add(parent);
            Array.from(parent.childNodes).forEach(function(node){if(node.nodeType===3)text.set(node,node.data);else if(node.nodeType===1)snapshot(node);});
        }
        var affected=Array.from(blocks.values()).filter(function(info){return info.rows.some(function(row){return row.delta;});});
        affected.forEach(function(info){snapshot(info.block);});
        saved={entries:entries,children:children,text:text,styles:styles,imageIds:imageIds,lineDeltas:lineDeltas};
        try{
            atomicRoots.forEach(function(el){
                var row=atoms.get(el);if(!row.delta)return;
                var css=window.getComputedStyle(el),property=vertical?'left':'top',initial=parseFloat(css.getPropertyValue(property))||0;
                styles.set(el,el.getAttribute('style'));lineDeltas.set(el,el.getAttribute('data-ng-line-delta'));
                el.style.setProperty('position','relative','important');el.style.setProperty(property,(initial+(reverse?-1:1)*row.delta)+'px','important');
                el.setAttribute('data-ng-line-delta',row.delta);
            });
            affected.forEach(function(info){
                var block=info.block;
                info.images.forEach(function(image,index){imageIds.set(image.node,image.node.getAttribute('data-ng-line-image'));image.node.setAttribute('data-ng-line-image',index);});
                // Freeze static inline presentation whose parent selector changes after fragmentation.
                Array.from(block.querySelectorAll('*')).filter(function(el){return !el.localName.startsWith('ng-');}).forEach(function(el){
                    var css=window.getComputedStyle(el),properties={};
                    ['font-family','font-size','font-style','font-weight','font-variant','font-stretch','line-height','letter-spacing','word-spacing',
                     'color','text-decoration','text-shadow','text-transform','white-space','vertical-align','direction','unicode-bidi',
                     'background','border','border-radius','padding','margin','box-shadow','opacity','filter','text-emphasis','text-orientation',
                     'text-combine-upright','-webkit-text-stroke','box-decoration-break','-webkit-box-decoration-break'].forEach(function(p){properties[p]=css.getPropertyValue(p);});
                    styles.set(el,el.getAttribute('style'));Object.keys(properties).forEach(function(p){el.style.setProperty(p,properties[p],'important');});
                });
                entries.forEach(function(entry,index){
                    if(!block.contains(entry.node))return;
                    var wrapper=document.createElementNS(block.namespaceURI,'ng-line-source');wrapper.setAttribute('data-ng-line-source',index);
                    wrapper.style.cssText='display:inline!important;margin:0!important;padding:0!important;border:0!important;background:transparent!important;';
                    entry.node.replaceWith(wrapper);wrapper.appendChild(entry.node);
                });
                var fragments=[];
                for(var i=info.rows.length-1;i>0;i--){
                    var at=info.rows[i].start,node=at.node;
                    if(at.offset===0){while(node.parentNode!==block && !node.previousSibling)node=node.parentNode;range.setStartBefore(node);}
                    else range.setStart(node,at.offset);
                    range.setEnd(block,block.childNodes.length);fragments.unshift(range.extractContents());
                }
                var first=document.createDocumentFragment();while(block.firstChild)first.appendChild(block.firstChild);fragments.unshift(first);
                fragments.forEach(function(fragment,index){
                    Array.from(fragment.querySelectorAll('[id],[data-ng-epub-source]')).forEach(function(el){if(!originalElements.has(el)){el.removeAttribute('id');el.removeAttribute('data-ng-epub-source');}});
                    var line=document.createElementNS(block.namespaceURI,'ng-justified-line');
                    line.style.cssText='display:inline!important;position:relative!important;margin:0!important;padding:0!important;border:0!important;background:transparent!important;';
                    line.style.setProperty(vertical?'left':'top',((reverse?-1:1)*info.rows[index].delta)+'px','important');
                    line.setAttribute('data-ng-page',info.rows[index].page);line.setAttribute('data-ng-line-delta',info.rows[index].delta);
                    line.appendChild(fragment);block.appendChild(line);
                    Array.from(line.querySelectorAll('ng-nine-slice[data-ng-line-image]')).forEach(function(el){
                        if(!el.textContent && !el.querySelector('ng-note-pin')){el.remove();return;}
                        var image=info.images[+el.getAttribute('data-ng-line-image')],row=info.rows[index];
                        var source=image.rects.find(function(r){return same(rect(r),row);});
                        if(!source)return;
                        // Chromium paints cloned end borders outside the fragmentainer when a
                        // match starts mid-line. Preserve that measured advance after splitting.
                        var end=value.sign>0?(row.page+1)*value.extent:-row.page*value.extent;
                        var overflow=value.sign>0?(vertical?source.bottom:source.right)-end:end-(vertical?source.top:source.left);
                        if(overflow>.1){
                            if(originalElements.has(el)&&!styles.has(el))styles.set(el,el.getAttribute('style'));
                            el.style.setProperty(vertical?(value.sign>0?'margin-bottom':'margin-top'):(value.sign>0?'margin-right':'margin-left'),-overflow+'px','important');
                        }
                    });
                });
            });
            var offsets=new Map(),replacements=new Map();
            Array.from(body.querySelectorAll('[data-ng-line-source]')).forEach(function(el){
                var index=+el.getAttribute('data-ng-line-source'),old=entries[index],cursor=offsets.get(index)||0;
                var walker=document.createTreeWalker(el,window.NodeFilter.SHOW_TEXT),node;
                while((node=walker.nextNode())){
                    if(node.data!==text.get(old.node).slice(cursor,cursor+node.length))throw new Error('EPUB 行拆分改变了正文');
                    if(!replacements.has(old))replacements.set(old,[]);
                    replacements.get(old).push({node:node,runs:sliceRuns(old.runs,cursor,cursor+node.length),sourceFrom:cursor,sourceTo:cursor+node.length});cursor+=node.length;
                }
                offsets.set(index,cursor);
            });
            offsets.forEach(function(count,index){if(count!==text.get(entries[index].node).length)throw new Error('EPUB 行拆分遗漏了正文');});
            affected.forEach(function(info){info.rows.forEach(function(row){
                var old=entries[row.parts[0].index],at=row.start.offset;
                var part=(replacements.get(old)||[]).find(function(part){return part.sourceFrom<=at&&at<part.sourceTo;});
                if(!part)throw new Error('EPUB 行位置无法对应原文');
                var from=at-part.sourceFrom;range.setStart(part.node,from);range.setEnd(part.node,Math.min(part.node.length,from+(part.node.data.codePointAt(from)>65535?2:1)));
                var r=Array.from(range.getClientRects()).find(function(r){return r.width>0&&r.height>0;}),now=r&&rect(r);
                if(!now||now.page!==row.page||Math.abs(now.top-row.delta-row.startTop)>1||Math.abs((vertical?r.top:r.left)-row.startInline)>1)
                    throw new Error('EPUB 页底对齐改变了原有断行');
            });});
            var result=[];entries.forEach(function(entry){result.push.apply(result,replacements.get(entry)||[entry]);});
            return {entries:result,shifted:shifted,protectedPages:Array.from(protectedPages)};
        }catch(error){clear();throw error;}
    }
    window.__ngEpubLines=Object.freeze({apply:apply,clear:clear});
})(window);
