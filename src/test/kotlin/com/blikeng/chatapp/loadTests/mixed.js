import http from 'k6/http';
import ws from 'k6/ws';
import {check, sleep} from 'k6';
import {BASE, jsonParams, WS_URL} from './lib.js';

// Mixed traffic simulation — splits 1000 VUs across realistic traffic patterns:
//   400 → WS connections (connect + SYNC + JOIN + MESSAGE)  — spread across 20 rooms (~20 users/room)
//   200 → WS sync only (connect + SYNC + snapshots)         — reconnecting clients
//   200 → room list reads (GET /api/rooms)                  — read-heavy
//   100 → room creation (POST /api/rooms/make)              — write traffic
//   100 → room history reads (GET /api/chats/{roomId})      — read-heavy
//
// Run after isolated tests at 1000 VUs to see how endpoints degrade under combined load.

const COOKIES_FILE = __ENV.COOKIES_FILE;
if (!COOKIES_FILE) throw new Error('COOKIES_FILE is required — run auth.js first');
const _cookies = JSON.parse(open(COOKIES_FILE));
const TARGET_VUS = __ENV.USERS ? parseInt(__ENV.USERS) : 1000;
const RUN_ID = Date.now();
const WS_ROOM_COUNT = 20; // ~20 users per room at 400 WS VUs — realistic group chat size

const WS_ROOM_VUS    = Math.round(TARGET_VUS * 0.40);
const WS_SYNC_VUS    = Math.round(TARGET_VUS * 0.20);
const ROOM_LIST_VUS  = Math.round(TARGET_VUS * 0.20);
const ROOM_CREATE_VUS = Math.round(TARGET_VUS * 0.10);
const ROOM_HISTORY_VUS = TARGET_VUS - WS_ROOM_VUS - WS_SYNC_VUS - ROOM_LIST_VUS - ROOM_CREATE_VUS;

const DURATION = '4m';
const RAMP     = '30s';

export const options = {
    setupTimeout: '20m',
    scenarios: {
        wsRoom: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: RAMP, target: WS_ROOM_VUS },
                { duration: DURATION, target: WS_ROOM_VUS },
                { duration: RAMP, target: 0 },
            ],
            gracefulRampDown: '30s',
            exec: 'wsRoomScenario',
        },
        wsSync: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: RAMP, target: WS_SYNC_VUS },
                { duration: DURATION, target: WS_SYNC_VUS },
                { duration: RAMP, target: 0 },
            ],
            gracefulRampDown: '30s',
            exec: 'wsSyncScenario',
        },
        roomList: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: RAMP, target: ROOM_LIST_VUS },
                { duration: DURATION, target: ROOM_LIST_VUS },
                { duration: RAMP, target: 0 },
            ],
            gracefulRampDown: '30s',
            exec: 'roomListScenario',
        },
        roomCreate: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: RAMP, target: ROOM_CREATE_VUS },
                { duration: DURATION, target: ROOM_CREATE_VUS },
                { duration: RAMP, target: 0 },
            ],
            gracefulRampDown: '30s',
            exec: 'roomCreateScenario',
        },
        roomHistory: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: RAMP, target: ROOM_HISTORY_VUS },
                { duration: DURATION, target: ROOM_HISTORY_VUS },
                { duration: RAMP, target: 0 },
            ],
            gracefulRampDown: '30s',
            exec: 'roomHistoryScenario',
        },
    },
};

export function setup() {
    const cookies = _cookies.slice(0, TARGET_VUS);
    const ownerCookie = cookies[0];
    if (!ownerCookie) throw new Error('Setup: no cookie for owner');

    const wsVUs = cookies.slice(0, WS_ROOM_VUS);
    const usersPerRoom = Math.ceil(wsVUs.length / WS_ROOM_COUNT);

    // Create WS rooms and distribute users across them.
    const wsRoomIds = [];
    for (let r = 0; r < WS_ROOM_COUNT; r++) {
        const roomName = `mixed-ws-${RUN_ID}-${r}`;
        http.post(`${BASE}/api/rooms/make`, JSON.stringify({ roomName, encrypted: false }), jsonParams(ownerCookie));

        const roomsRes = http.get(`${BASE}/api/rooms`, jsonParams(ownerCookie));
        const room = roomsRes.json().find((rm) => rm.roomName === roomName);
        if (!room) throw new Error(`Setup: room "${roomName}" not found`);

        const inviteRes = http.post(
            `${BASE}/api/invites/open`,
            JSON.stringify({ type: 'OPEN_ROOM_INVITE', roomId: room.roomId, maxUsages: usersPerRoom + 1 }),
            jsonParams(ownerCookie)
        );
        const inviteId = inviteRes.body.trim().replace(/^"|"$/g, '');

        const start = r * usersPerRoom;
        const end = Math.min(start + usersPerRoom, wsVUs.length);
        for (let i = start; i < end; i++) {
            if (!wsVUs[i]) continue;
            http.post(
                `${BASE}/api/invites/respond`,
                JSON.stringify({ inviteId, response: 'ACCEPTED' }),
                jsonParams(wsVUs[i])
            );
        }

        wsRoomIds.push(room.roomId);
    }

    // Create dedicated history room and seed messages.
    const historyRoomName = `mixed-history-${RUN_ID}`;
    http.post(`${BASE}/api/rooms/make`, JSON.stringify({ roomName: historyRoomName, encrypted: false }), jsonParams(ownerCookie));
    const historyRoomsRes = http.get(`${BASE}/api/rooms`, jsonParams(ownerCookie));
    const historyRoom = historyRoomsRes.json().find((r) => r.roomName === historyRoomName);
    if (!historyRoom) throw new Error(`Setup: history room not found`);
    const historyRoomId = historyRoom.roomId;

    const historyInviteRes = http.post(
        `${BASE}/api/invites/open`,
        JSON.stringify({ type: 'OPEN_ROOM_INVITE', roomId: historyRoomId, maxUsages: cookies.length }),
        jsonParams(ownerCookie)
    );
    check(historyInviteRes, { 'setup history invite created': (r) => r.status === 200 });
    const historyInviteId = historyInviteRes.body.trim().replace(/^"|"$/g, '');

    for (let i = 1; i < cookies.length; i++) {
        if (!cookies[i]) continue;
        http.post(
            `${BASE}/api/invites/respond`,
            JSON.stringify({ inviteId: historyInviteId, response: 'ACCEPTED' }),
            jsonParams(cookies[i])
        );
    }

    ws.connect(WS_URL, { headers: { Cookie: `AUTH=${ownerCookie}` } }, function (socket) {
        socket.on('open', () => {
            socket.send(JSON.stringify({ type: 'JOIN', roomId: historyRoomId }));
            socket.setTimeout(() => socket.close(), 5000);
        });
        socket.on('message', (raw) => {
            try {
                const msg = JSON.parse(raw);
                if (msg.type === 'ROOM_JOINED') {
                    for (let i = 0; i < 10; i++) {
                        socket.send(JSON.stringify({ type: 'MESSAGE', roomId: historyRoomId, message: `seed ${i}` }));
                    }
                }
            } catch (_) {}
        });
    });

    return { cookies, wsRoomIds, historyRoomId };
}

// ==========================
// Scenario functions
// ==========================
export function wsRoomScenario(data) {
    const idx = (__VU - 1) % data.cookies.length;
    const cookie = data.cookies[idx];
    if (!cookie) return;

    // Each VU connects to its assigned room — distributes load across WS_ROOM_COUNT rooms.
    const roomId = data.wsRoomIds[(__VU - 1) % data.wsRoomIds.length];

    const response = ws.connect(WS_URL, { headers: { Cookie: `AUTH=${cookie}` } }, function (socket) {
        let joined = false;

        socket.on('open', () => {
            socket.send(JSON.stringify({ type: 'SYNC' }));
            socket.send(JSON.stringify({ type: 'JOIN', roomId }));
        });

        socket.on('message', (raw) => {
            try {
                const msg = JSON.parse(raw);
                if (msg.type === 'ROOM_JOINED' && !joined) {
                    joined = true;
                    socket.send(JSON.stringify({ type: 'MESSAGE', roomId, message: `hello from VU ${__VU}` }));
                }
            } catch (_) {}
        });

        socket.on('error', (e) => console.log(`wsRoom error: ${JSON.stringify(e)}`));
        socket.setTimeout(() => socket.close(), 5000);
    });

    check(response, { 'wsRoom upgrade 101': (r) => r && r.status === 101 });
    sleep(1);
}

export function wsSyncScenario(data) {
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

        socket.on('error', (e) => console.log(`wsSync error: ${JSON.stringify(e)}`));
        socket.setTimeout(() => socket.close(), 5000);
    });

    check(response, { 'wsSync upgrade 101': (r) => r && r.status === 101 });
    check(null, {
        'wsSync FRIEND_SNAPSHOT': () => gotFriendSnapshot,
        'wsSync PENDING_INVITES': () => gotInviteSnapshot,
    });
    sleep(1);
}

export function roomListScenario(data) {
    const idx = (__VU - 1) % data.cookies.length;
    const cookie = data.cookies[idx];
    if (!cookie) return;

    const res = http.get(`${BASE}/api/rooms`, jsonParams(cookie));
    check(res, { 'roomList 200': (r) => r.status === 200 });
    sleep(1);
}

export function roomCreateScenario(data) {
    const idx = (__VU - 1) % data.cookies.length;
    const cookie = data.cookies[idx];
    if (!cookie) return;

    const res = http.post(
        `${BASE}/api/rooms/make`,
        JSON.stringify({ roomName: `mixed_${__VU}_${__ITER}`, encrypted: false }),
        jsonParams(cookie)
    );
    check(res, { 'roomCreate 201': (r) => r.status === 201 });
    sleep(1);
}

export function roomHistoryScenario(data) {
    const idx = (__VU - 1) % data.cookies.length;
    const cookie = data.cookies[idx];
    if (!cookie) return;

    const res = http.get(`${BASE}/api/chats/${data.historyRoomId}`, jsonParams(cookie));
    check(res, {
        'roomHistory 200': (r) => r.status === 200,
        'roomHistory non-empty': (r) => r.json().length > 0,
    });
    sleep(1);
}