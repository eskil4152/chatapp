import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { BASE, WS_URL, PASSWORD, jsonParams, getAuthCookie, makeOptions } from './lib.js';

const USERS_FILE = __ENV.USERS_FILE;
const _preloaded = USERS_FILE ? JSON.parse(open(USERS_FILE)) : null;
const TARGET_VUS = __ENV.USERS ? parseInt(__ENV.USERS) : 100;
const RUN_ID = Date.now();
const ROOM_NAME = `ws-room-${RUN_ID}`;

export const options = { ...makeOptions(TARGET_VUS, _preloaded ? 2 : 3) };

export function setup() {
    let users;
    if (_preloaded) {
        users = _preloaded.slice(0, TARGET_VUS);
    } else {
        users = [];
        for (let i = 1; i <= TARGET_VUS; i++) {
            const user = { username: `ws_${RUN_ID}_${i}`, password: PASSWORD };
            http.post(`${BASE}/api/register`, JSON.stringify(user), jsonParams());
            users.push(user);
        }
    }

    const owner = users[0];
    const ownerLoginRes = http.post(`${BASE}/api/login`, JSON.stringify(owner), jsonParams());
    const ownerCookie = getAuthCookie(ownerLoginRes);
    if (!ownerCookie) throw new Error('Setup: owner login failed');

    const createRes = http.post(
        `${BASE}/api/rooms/make`,
        JSON.stringify({ roomName: ROOM_NAME, encrypted: false }),
        jsonParams(ownerCookie)
    );
    check(createRes, { 'setup room created': (r) => r.status === 201 });

    const roomsRes = http.get(`${BASE}/api/rooms`, jsonParams(ownerCookie));
    const rooms = roomsRes.json();
    const room = rooms.find((r) => r.roomName === ROOM_NAME);
    if (!room) throw new Error(`Setup: room "${ROOM_NAME}" not found`);

    for (let i = 1; i < users.length; i++) {
        const loginRes = http.post(`${BASE}/api/login`, JSON.stringify(users[i]), jsonParams());
        const cookie = getAuthCookie(loginRes);
        if (!cookie) continue;

        http.post(`${BASE}/api/rooms/join`, JSON.stringify({ roomId: room.roomId }), jsonParams(cookie));
    }

    return { users, roomId: room.roomId };
}

export default function (data) {
    const user = data.users[(__VU - 1) % data.users.length];

    const loginRes = http.post(`${BASE}/api/login`, JSON.stringify(user), jsonParams());
    check(loginRes, { 'login ok': (r) => r.status === 200 });

    const cookie = getAuthCookie(loginRes);
    if (!cookie) return;

    const response = ws.connect(WS_URL, { headers: { Cookie: `AUTH=${cookie}` } }, function (socket) {
        let joined = false;

        socket.on('open', () => {
            socket.send(JSON.stringify({ type: 'JOIN', roomId: data.roomId }));
        });

        socket.on('message', (raw) => {
            try {
                const msg = JSON.parse(raw);
                if (msg.type === 'JOINED' && !joined) {
                    joined = true;
                    socket.send(JSON.stringify({
                        type: 'MESSAGE',
                        roomId: data.roomId,
                        message: `hello from ${user.username}`,
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
