'use strict';
// Executes the actual container request protocol with manually completed render work.
// No browser/device performance claim.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync('app/src/main/assets/epub/container.js', 'utf8');
const deferred = () => { let resolve, reject; const promise = new Promise((a,b)=>{resolve=a;reject=b;}); return {promise,resolve,reject}; };
function fixture(mode='continuous') {
    const jobs=[], counts={scroll:0,capture:0};
    let token='1', offset=0, ended=false;
    const report = extra => ({status:'ready',token,scrolled:true,busy:false,pageIndex:0,pageCount:10,
        location:{textOffset:offset},offset,overshoot:3,cover:false,bleedHeader:true,...extra});
    function start(delta) {
        counts.scroll++;const job=deferred();jobs.push({...job,delta});return job.promise;
    }
    const flow = {
        configure: async v => {token=v.token;},
        updateStyles: async v => {token=v.token;},
        move: async v => {token=v.token;},
        state: () => report(),
        setHighlights(){},setSelectionTransparent(){},setLinkHandler(){},cancelConfiguration(){},close(){ended=true;},
        scrollBy(delta){offset+=delta;counts.scroll++;},
        settle(){counts.capture++;const job=deferred();jobs.push({...job,delta:0});return job.promise;}
    };
    if(mode==='continuous')flow.scrollAndSettle=start;
    const host={style:{},replaceChildren(){}};
    const context={window:{__ngEpubCreateContinuous:()=>flow,__ngEpubCreateCanvas:()=>flow},
        document:{getElementById:()=>host},Promise,Number,Set,Map,Object,Array,Error};
    vm.createContext(context);vm.runInContext(source,context);
    const api=context.window.__ngEpubContainer;
    const options=t=>({token:t,containerMode:mode,documents:[{url:'https://epub.test/a'}],width:400,height:700});
    return {api,jobs,counts,report,options,flow,
        async ready(){await api.configure(options('1'));},
        complete(i, extra={}){offset+=jobs[i].delta;counts.capture++;jobs[i].resolve(report(extra));},ended:()=>ended};
}
const copy=x=>JSON.parse(JSON.stringify(x));
(async()=>{
    let checks=0;
    // A non-busy live state does not acknowledge a pending geometry result.
    {
        const f=fixture();await f.ready();
        const done=f.api.requestScroll({token:'1',request:1,delta:24});
        assert.equal(f.api.state().busy,false);
        assert.equal(f.api.scrollResult('1',1),null);
        f.complete(0);const returned=await done;
        const r=f.api.scrollResult('1',1);
        assert.equal(r.location.textOffset,24);assert.equal(r.scrollOvershoot,3);
        assert.equal(r.canvas,true);assert.equal(r.fullViewport,true);assert.equal(r.hideHeader,true);
        assert.equal(f.counts.scroll,1);assert.equal(f.counts.capture,1);
        assert.equal(returned,r);
        for(let i=0;i<3;i++){assert.equal(await done,r);assert.equal(f.api.scrollResult('1',1),r);}
        assert.equal(f.api.scrollResult('wrong',1),null);assert.equal(f.api.scrollResult('1',0),null);
        checks++;
    }
    // A late old completion must never replace the slot for a later request.
    {
        const f=fixture();await f.ready();
        const old=f.api.requestScroll({token:'1',request:1,delta:1});
        const latest=f.api.requestScroll({token:'1',request:2,delta:2});
        f.complete(1);await latest;const saved=copy(f.api.scrollResult('1',2));
        f.complete(0);await old;
        assert.deepEqual(copy(f.api.scrollResult('1',2)),saved);
        assert.equal(f.api.scrollResult('1',1),null);checks++;
    }
    // Each navigation/lifecycle transition invalidates the previous task.
    for(const operation of ['configure','updateStyles','move','cancelConfiguration','close']){
        const f=fixture();await f.ready();
        const done=f.api.requestScroll({token:'1',request:1,delta:10});
        if(operation==='configure')await f.api.configure(f.options('2'));
        else if(operation==='move'||operation==='updateStyles')await f.api[operation]({token:'2'});
        else f.api[operation]();
        f.complete(0);await done;
        assert.equal(f.api.scrollResult('1',1),null,operation+' retained obsolete report');
        checks++;
    }
    // Rejection and resolved renderer-error results both surface as errors.
    {
        const f=fixture();await f.ready();
        const done=f.api.requestScroll({token:'1',request:1,delta:1});
        f.jobs[0].reject(new Error('resource failed'));await done;
        const r=f.api.scrollResult('1',1);assert.equal(r.status,'error');assert.match(r.error,/resource failed/);
        const next=f.api.requestScroll({token:'1',request:2,delta:1});
        f.complete(1,{status:'error',error:'layout failed'});await next;
        assert.equal(f.api.scrollResult('1',2).status,'error');
        checks++;
    }
    // Even a failure before movement must be returned through the polling protocol.
    {
        const f=fixture();await f.ready();
        f.flow.state=()=>{throw new Error('state read failed');};
        await f.api.requestScroll({token:'1',request:1,delta:1});
        const r=f.api.scrollResult('1',1);
        assert.equal(r.status,'error');assert.match(r.error,/state read failed/);
        assert.equal(f.counts.scroll,0);checks++;
    }
    // A stale layout token must never move the new document.
    {
        const f=fixture();await f.ready();
        await f.api.requestScroll({token:'obsolete',request:1,delta:100});
        assert.equal(f.counts.scroll,0);assert.equal(f.api.scrollResult('obsolete',1),null);
        checks++;
    }
    // Unsupported selection preparation is a no-op; supported navigation drops old work.
    for(const supported of [false,true]){
        const f=fixture();await f.ready();
        if(supported)f.flow.prepareSelection=async()=>{};
        const done=f.api.requestScroll({token:'1',request:1,delta:5});
        await f.api.prepareSelection({token:'1'});
        f.complete(0);await done;
        assert.equal(f.api.scrollResult('1',1)===null,supported);
        checks++;
    }
    // Canvas keeps its own synchronous movement + final capture/settle path.
    {
        const f=fixture('canvas');await f.ready();
        const done=f.api.requestScroll({token:'1',request:1,delta:19});
        assert.equal(f.counts.scroll,1);assert.equal(f.counts.capture,1);
        f.jobs[0].resolve(f.report({canvas:true,hideFooter:true}));await done;
        const r=f.api.scrollResult('1',1);assert.equal(r.location.textOffset,19);
        assert.equal(r.hideFooter,true);checks++;
    }
    console.log('Scroll handshake: '+checks+' pending/identity/navigation/error/backend contracts passed.');
})().catch(error=>{console.error(error);process.exitCode=1;});
