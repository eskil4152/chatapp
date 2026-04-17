import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { WS_URL, makeOptions } from './lib.js';

// Tests the client-pull SYNC flow: connect, send SYNC, assert both snapshots arrive.
// No room setup needed — SYNC returns friend presence and pending invites, independent of rooms.

const COOKIES_FILE = __ENV.COOKIES_FILE;
if (!COOKIES_FILE) throw new Error('COOKIES_FILE is required — run auth.js first');
const _cookies = JSON.parse(open(COOKIES_FILE));
const TARGET_VUS = __ENV.USERS ? parseInt(__ENV.USERS) : 100;

export const options = { ...makeOptions(TARGET_VUS, 0) };

export function setup() {
    return { cookies: _cookies.slice(0, TARGET_VUS) };
}

export default function (data) {
    const idx = (__VU - 1) % data.cookies.length;
    const cookie = data.cookies[idx];
    if (!cookie) return;

    let gotFriendSnapshot = false;
    let gotInviteSnapshot = false;

    const response = ws.connect(WS_URL, { headers: { Cookie: `AUTH=${cookie}` } }, function (socket) {
        socket.on('open', () => {
            socket.send(JSON.stringify({ type: 'SYNC' }));
        });

        socket.on('message', (raw) => {
            try {
                const msg = JSON.parse(raw);
                if (msg.type === 'FRIEND_SNAPSHOT') gotFriendSnapshot = true;
                if (msg.type === 'PENDING_INVITES') gotInviteSnapshot = true;
                if (gotFriendSnapshot && gotInviteSnapshot) socket.close();
            } catch (_) {}
        });

        socket.on('error', (e) => console.log(`ws error: ${JSON.stringify(e)}`));
        socket.setTimeout(() => socket.close(), 8000);
    });

    check(response, { 'ws upgrade 101': (r) => r && r.status === 101 });
    check(null, {
        'received FRIEND_SNAPSHOT': () => gotFriendSnapshot,
        'received PENDING_INVITES': () => gotInviteSnapshot,
    });
    sleep(1);
}
