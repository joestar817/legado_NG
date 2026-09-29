// Node contract check for the actual native-injected document readiness probe.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync('app/src/main/java/io/legado/app/ui/book/read/epub/EpubLayoutSurface.kt', 'utf8');
const start = source.indexOf('webView.evaluateJavascript("location.href ===');
const end = source.indexOf(') { ready ->', start);
assert.ok(start >= 0 && end > start);
const expected = 'https://epub-example.invalid/chapter.xhtml?ng-load=4';
const parts = [...source.slice(start, end).matchAll(/"([^"\n]*)"/g)].map(match => match[1]);
const probe = parts.join('').replace('${JSONObject.quote(url)}', JSON.stringify(expected));
function ready(href, state, sheets = [], mediaMatches = true) {
    return vm.runInNewContext(probe, {
        location: { href },
        document: { readyState: state, querySelectorAll: () => sheets },
        matchMedia: () => ({ matches: mediaMatches }),
    });
}
assert.equal(ready(expected.replace('ng-load=4', 'ng-load=3'), 'complete'), false, 'old document cannot configure a new navigation');
assert.equal(ready('about:blank', 'complete'), false);
assert.equal(ready(expected, 'loading'), false, 'must wait for the full DOM');
assert.equal(ready(expected, 'interactive'), false, 'interactive may still have pending CSS imports');
assert.equal(ready(expected, 'complete'), true, 'loaded document need not wait for a paint callback');
assert.equal(ready(expected, 'interactive', [{ sheet: null }]), false, 'applicable CSS must be ready');
assert.equal(ready(expected, 'complete', [{ sheet: null }]), false, 'load completion alone cannot satisfy this probe');
assert.equal(ready(expected, 'interactive', [{ sheet: {} }]), false);
assert.equal(ready(expected, 'complete', [{ disabled: true, sheet: null }]), true);
assert.equal(ready(expected, 'complete', [{ media: 'print', sheet: null }], false), true);
assert.equal(ready(expected, 'complete', [{ media: 'screen', sheet: null }], true), false);
console.log('Document readiness: 11 URL/DOM/CSS contracts passed; no compositor or device timing claim.');
