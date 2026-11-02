'use strict';
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync('app/src/main/assets/epub/reader.js', 'utf8');
const code = source.slice(source.indexOf('    function prepareScrollLayer()'), source.indexOf('    function place(page)'));
function run(scrolled, hint, priority, expected) {
    const writes = [];
    const c = { state: { scrolled }, getComputedStyle() { if (!scrolled) throw new Error('Paged layouts need no layer hint'); return { willChange: hint }; },
        body: { style: { getPropertyPriority: () => priority, setProperty: (...args) => writes.push(args) } } };
    const prepare = vm.runInNewContext(code + '\nprepareScrollLayer;', c);
    prepare();
    assert.deepEqual(writes, expected);
}
run(false, 'auto', '', []);
run(true, 'auto', '', [['will-change', 'transform', '']]);
run(true, 'opacity', 'important', [['will-change', 'opacity, transform', 'important']]);
run(true, 'scroll-position, opacity', '', [['will-change', 'scroll-position, opacity, transform', '']]);
run(true, 'transform', '', []);
run(true, 'opacity, transform', 'important', []);
const layout = source.slice(source.indexOf('    async function configureLayout('), source.indexOf('    async function move(value)'));
assert.ok(layout.indexOf('prepareScrollLayer();') > layout.indexOf('var restored = restoreAnchor(anchor);'), 'Promote after the original layout and position have settled');
assert.ok(layout.indexOf('prepareScrollLayer();') < layout.indexOf('await frame(frameTiming); await frame(frameTiming);', layout.indexOf('var restored = restoreAnchor(anchor);')), 'Prepare before exposing the first ready frame');
assert.ok(layout.indexOf('restore(root, originalRoot)') < layout.indexOf('prepareScrollLayer();'), 'Leaving scroll restores the authored inline style through the existing lifecycle');
console.log('Scroll layer: paginated path unchanged, author hints/priority preserved, duplicate hints avoided, promotion after original layout/position and before ready.');
