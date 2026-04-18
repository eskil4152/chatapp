import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { BASE, WS_URL, jsonParams, makeOptions } from './lib.js';

const COOKIES_FILE = __ENV.COOKIES_FILE;
if (!COOKIES_FILE) throw new Error('COOKIES_FILE is required — run auth.js first');
const _cookies = JSON.parse(open(COOKIES_FILE));
const TARGET_VUS = __ENV.USERS ? parseInt(__ENV.USERS) : 100;
const RUN_ID = Date.now();
const ROOM_NAME = `ws-room-${RUN_ID}`;

// Setup: invite accept per user involves a DB write — assume ~7 req/s, not 14.
export const options = { ...makeOptions(TARGET_VUS, 0) };

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

    const openInviteRes = http.post(
        `${BASE}/api/invites/open`,
        JSON.stringify({ type: 'OPEN_ROOM_INVITE', roomId: room.roomId, maxUsages: cookies.length }),
        jsonParams(ownerCookie)
    );
    check(openInviteRes, { 'setup open invite created': (r) => r.status === 200 });
    const inviteId = openInviteRes.body.trim().replace(/^"|"$/g, '');

    const batchRequests = [];
    for (let i = 1; i < cookies.length; i++) {
        if (!cookies[i]) continue;
        batchRequests.push({
            method: 'POST',
            url: `${BASE}/api/invites/respond`,
            body: JSON.stringify({ inviteId, response: 'ACCEPTED' }),
            params: jsonParams(cookies[i]),
        });
    }
    http.batch(batchRequests);

    return { cookies, roomId: room.roomId };
}

export default function (data) {
    const idx = (__VU - 1) % data.cookies.length;
    const cookie = data.cookies[idx];
    if (!cookie) return;

    const response = ws.connect(WS_URL, { headers: { Cookie: `AUTH=${cookie}` } }, function (socket) {
        let joined = false;

        socket.on('open', () => {
            socket.send(JSON.stringify({ type: 'SYNC' }));
            socket.send(JSON.stringify({ type: 'JOIN', roomId: data.roomId }));
        });

        socket.on('message', (raw) => {
            try {
                const msg = JSON.parse(raw);
                if (msg.type === 'ROOM_JOINED' && !joined) {
                    joined = true;
                    socket.send(JSON.stringify({
                        type: 'MESSAGE',
                        roomId: data.roomId,
                        message: `hello from VU ${__VU}`,
                    }));
                }
            } catch (_) {}
        });

        socket.on('error', (e) => console.log(`ws error: ${JSON.stringify(e)}`));
        socket.setTimeout(() => socket.close(), 8000);
    });

    check(response, { 'ws upgrade 101': (r) => r && r.status === 101 });
    sleep(1);
}
