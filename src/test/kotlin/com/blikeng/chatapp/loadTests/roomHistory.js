import http from 'k6/http';
import ws from 'k6/ws';
import {check, sleep} from 'k6';
import {BASE, jsonParams, makeOptions, WS_URL} from './lib.js';

// Tests GET /api/chats/{roomId} under concurrent read load.
// Setup creates a shared room, invites all users, seeds messages via WS so history is non-empty.
// Load phase is pure HTTP reads — isolates history pagination performance.

const COOKIES_FILE = __ENV.COOKIES_FILE;
if (!COOKIES_FILE) throw new Error('COOKIES_FILE is required — run auth.js first');
const _cookies = JSON.parse(open(COOKIES_FILE));
const TARGET_VUS = __ENV.USERS ? parseInt(__ENV.USERS) : 100;
const RUN_ID = Date.now();
const ROOM_NAME = `history-${RUN_ID}`;

// 2 setup reqs per user: one invite accept (DB write) — same cost as wsRoom setup.
export const options = { ...makeOptions(TARGET_VUS, 2) };

export function setup() {
    const cookies = _cookies.slice(0, TARGET_VUS);
    const ownerCookie = cookies[0];
    if (!ownerCookie) throw new Error('Setup: no cookie for owner');

    const createRes = http.post(
        `${BASE}/api/rooms/make`,
        JSON.stringify({ roomName: ROOM_NAME, encrypted: false }),
        jsonParams(ownerCookie)
    );
    check(createRes, { 'setup room created': (r) => r.status === 201 });

    const roomsRes = http.get(`${BASE}/api/rooms`, jsonParams(ownerCookie));
    const room = roomsRes.json().find((r) => r.roomName === ROOM_NAME);
    if (!room) throw new Error(`Setup: room "${ROOM_NAME}" not found`);
    const roomId = room.roomId;

    const openInviteRes = http.post(
        `${BASE}/api/invites/open`,
        JSON.stringify({ type: 'OPEN_ROOM_INVITE', roomId, maxUsages: cookies.length }),
        jsonParams(ownerCookie)
    );
    check(openInviteRes, { 'setup open invite created': (r) => r.status === 200 });
    const inviteId = openInviteRes.body.trim().replace(/^"|"$/g, '');

    for (let i = 1; i < cookies.length; i++) {
        if (!cookies[i]) continue;
        http.post(
            `${BASE}/api/invites/respond`,
            JSON.stringify({ inviteId, response: 'ACCEPTED' }),
            jsonParams(cookies[i])
        );
    }

    // Seed messages so history is non-empty before the load phase begins.
    ws.connect(WS_URL, { headers: { Cookie: `AUTH=${ownerCookie}` } }, function (socket) {
        socket.on('open', () => {
            socket.send(JSON.stringify({ type: 'JOIN', roomId }));
            // Always close after 5s — unconditional so the socket never hangs in setup
            socket.setTimeout(() => socket.close(), 5000);
        });

        socket.on('message', (raw) => {
            try {
                const msg = JSON.parse(raw);
                if (msg.type === 'ROOM_JOINED') {
                    for (let i = 0; i < 10; i++) {
                        socket.send(JSON.stringify({ type: 'MESSAGE', roomId, message: `seed ${i}` }));
                    }
                }
            } catch (_) {}
        });
    });

    return { cookies, roomId };
}

export default function (data) {
    const idx = (__VU - 1) % data.cookies.length;
    const cookie = data.cookies[idx];
    if (!cookie) return;

    const res = http.get(`${BASE}/api/chats/${data.roomId}`, jsonParams(cookie));
    check(res, {
        'room history 200': (r) => r.status === 200,
        'room history non-empty': (r) => r.json().length > 0,
    });
    sleep(1);
}
