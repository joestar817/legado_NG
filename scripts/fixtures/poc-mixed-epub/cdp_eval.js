#!/usr/bin/env node
// Minimal CDP WebSocket client: node cdp_eval.js <webSocketDebuggerUrl> <expression>
// No dependencies; client frames masked per RFC 6455.
const net = require('net');
const crypto = require('crypto');
const url = new URL(process.argv[2]);
const expression = process.argv[3] || '1+1';
const key = crypto.randomBytes(16).toString('base64');

function encodeFrame(payload) {
    const data = Buffer.from(payload, 'utf8');
    const mask = crypto.randomBytes(4);
    const header = [];
    header.push(0x81); // FIN + text
    if (data.length < 126) header.push(0x80 | data.length);
    else if (data.length < 65536) { header.push(0x80 | 126, data.length >> 8, data.length & 0xff); }
    else { header.push(0x80 | 127, 0, 0, 0, 0, (data.length >>> 24) & 0xff, (data.length >> 16) & 0xff, (data.length >> 8) & 0xff, data.length & 0xff); }
    const masked = Buffer.from(data);
    for (let i = 0; i < masked.length; i++) masked[i] ^= mask[i % 4];
    return Buffer.concat([Buffer.from(header), mask, masked]);
}

function* parseFrames(buffer) {
    let offset = 0;
    while (buffer.length - offset >= 2) {
        const fin = (buffer[offset] & 0x80) !== 0;
        const opcode = buffer[offset] & 0x0f;
        let len = buffer[offset + 1] & 0x7f;
        let pos = offset + 2;
        if (len === 126) { if (buffer.length - pos < 2) return; len = buffer.readUInt16BE(pos); pos += 2; }
        else if (len === 127) { if (buffer.length - pos < 8) return; len = Number(buffer.readBigUInt64BE(pos)); pos += 8; }
        if (buffer.length - pos < len) return;
        const payload = buffer.slice(pos, pos + len);
        yield { fin, opcode, payload };
        offset = pos + len;
    }
}

const sock = net.connect(Number(url.port), url.hostname, () => {
    sock.write(
        `GET ${url.pathname} HTTP/1.1\r\nHost: ${url.host}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n` +
        `Sec-WebSocket-Key: ${key}\r\nSec-WebSocket-Version: 13\r\n\r\n`
    );
});

let handshaken = false;
let buffer = Buffer.alloc(0);
let fragments = [];
const pending = new Map();
let nextId = 1;

function send(method, params) {
    const id = nextId++;
    sock.write(encodeFrame(JSON.stringify({ id, method, params })));
    return new Promise((resolve, reject) => pending.set(id, { resolve, reject }));
}

function onMessage(obj) {
    if (obj.id && pending.has(obj.id)) {
        const p = pending.get(obj.id);
        pending.delete(obj.id);
        if (obj.error) p.reject(new Error(JSON.stringify(obj.error)));
        else p.resolve(obj.result);
    }
}

sock.on('data', chunk => {
    buffer = Buffer.concat([buffer, chunk]);
    if (!handshaken) {
        const idx = buffer.indexOf('\r\n\r\n');
        if (idx === -1) return;
        handshaken = true;
        buffer = buffer.slice(idx + 4);
        (async () => {
            await send('Runtime.enable', {});
            const result = await send('Runtime.evaluate', {
                expression, returnByValue: true, awaitPromise: true,
            });
            console.log(JSON.stringify(result, null, 1));
            sock.end();
            process.exit(0);
        })().catch(e => { console.error('CDP_ERROR', e.message); process.exit(1); });
    }
    const gen = parseFrames(buffer);
    let consumed = 0;
    for (const frame of gen) {
        consumed = frame ? buffer.indexOf(frame.payload) + frame.payload.length : 0;
        if (frame.opcode === 0x9) { sock.write(encodeFrame('')); continue; } // ping→pong (approximate)
        fragments.push(frame.payload);
        if (frame.fin) {
            const msg = Buffer.concat(fragments).toString('utf8');
            fragments = [];
            try { onMessage(JSON.parse(msg)); } catch { /* ignore */ }
        }
    }
});
sock.on('error', e => { console.error('SOCK_ERROR', e.message); process.exit(1); });
setTimeout(() => { console.error('TIMEOUT'); process.exit(2); }, 20000);
