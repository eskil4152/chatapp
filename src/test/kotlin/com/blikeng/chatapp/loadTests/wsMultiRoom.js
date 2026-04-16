import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { BASE, WS_URL, jsonParams, makeOptions } from './lib.js';

const COOKIES_FILE = __ENV.COOKIES_FILE;
if (!COOKIES_FILE) throw new Error('COOKIES_FILE is required — run auth.js first');
const _cookies = JSON.parse(open(COOKIES_FILE));
const TARGET_VUS = __ENV.USERS ? parseInt(__ENV.USERS) : 100;
const RUN_ID = Date.now();
const ROOM_COUNT = 5;
const ROOM_PREFIX = `ws-multi-${RUN_ID}`;

// Setup creates ROOM_COUNT rooms + 1 invite accept per user.
export const options = { ...makeOptions(TARGET_VUS, 1) };

export function setup() {
    const cookies = _cookies.slice(0, TARGET_VUS);
    const ownerCookie = cookies[0];
    if (!ownerCookie) throw new Error('Setup: no cookie for owner');

    const roomNames = Array.from({ length: ROOM_COUNT }, (_, i) => `${ROOM_PREFIX}-${i}`);
    for (const roomName of roomNames) {
        const res = http.post(
            `${BASE}/api/rooms/make`,
            JSON.stringify({ roomName, encrypted: false }),
            jsonParams(ownerCookie)
        );
        check(res, { [`setup room created: ${roomName}`]: (r) => r.status === 201 });
    }

    const roomsRes = http.get(`${BASE}/api/rooms`, jsonParams(ownerCookie));
    const listedRooms = roomsRes.json();
    const roomIds = roomNames.map((name) => {
        const room = listedRooms.find((r) => r.roomName === name);
        if (!room) throw new Error(`Setup: room "${name}" not found`);
        return room.roomId;
    });

    const usersPerRoom = Math.ceil((cookies.length - 1) / ROOM_COUNT);
    const inviteIds = roomIds.map((roomId) => {
        const res = http.post(
            `${BASE}/api/invites/open`,
            JSON.stringify({ type: 'OPEN_ROOM_INVITE', roomId, maxUsages: usersPerRoom }),
            jsonParams(ownerCookie)
        );
        check(res, { [`setup open invite created for ${roomId}`]: (r) => r.status === 200 });
        return res.body.trim().replace(/^"|"$/g, '');
    });

    // Each user accepts the invite for their assigned room.
    const assignedRoomIds = [roomIds[0]]; // owner is in room 0
    for (let i = 1; i < cookies.length; i++) {
        if (!cookies[i]) { assignedRoomIds.push(null); continue; }
        const assignedIdx = (i - 1) % ROOM_COUNT;
        http.post(
            `${BASE}/api/invites/respond`,
            JSON.stringify({ inviteId: inviteIds[assignedIdx], response: 'ACCEPTED' }),
            jsonParams(cookies[i])
        );
        assignedRoomIds.push(roomIds[assignedIdx]);
    }

    return { cookies, assignedRoomIds };
}

export default function (data) {
    const idx = (__VU - 1) % data.cookies.length;
    const cookie = data.cookies[idx];
    const roomId = data.assignedRoomIds[idx];
    if (!cookie || !roomId) return;

    const response = ws.connect(WS_URL, { headers: { Cookie: `AUTH=${cookie}` } }, function (socket) {
        let joined = false;

        socket.on('open', () => {
            socket.send(JSON.stringify({ type: 'JOIN', roomId }));
        });

        socket.on('message', (raw) => {
            try {
                const msg = JSON.parse(raw);
                if (msg.type === 'JOINED' && !joined) {
                    joined = true;
                    socket.send(JSON.stringify({
                        type: 'MESSAGE',
                        roomId,
                        message: `hello from VU ${__VU}`,
                    }));
                }
            } catch (_) {}
        });

        socket.on('error', (e) => console.log(`ws error: ${JSON.stringify(e)}`));
        socket.setTimeout(() => socket.close(), 5000);
    });

    check(response, { 'ws upgrade 101': (r) => r && r.status === 101 });
    sleep(1);
}
