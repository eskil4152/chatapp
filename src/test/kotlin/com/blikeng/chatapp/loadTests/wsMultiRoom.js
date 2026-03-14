import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';

const BASE = 'http://localhost:5050';
const WS_URL = 'ws://localhost:5050/ws';

const PASSWORD = 'testpassword123';
const USER_COUNT = 30;
const ROOM_COUNT = 5;
const ROOM_PREFIX = `ws-multi-room-${Date.now()}`;

export const options = {
    stages: [
        { duration: '20s', target: 5 },
        { duration: '30s', target: 15 },
        { duration: '40s', target: 30 },
        { duration: '20s', target: 0 },
    ],
};

function jsonParams(cookie = null) {
    const headers = {
        'Content-Type': 'application/json',
    };

    if (cookie) {
        headers.Cookie = `AUTH=${cookie}`;
    }

    return { headers };
}

function registerUser(user) {
    return http.post(
        `${BASE}/api/register`,
        JSON.stringify(user),
        jsonParams()
    );
}

function loginUser(user) {
    return http.post(
        `${BASE}/api/login`,
        JSON.stringify(user),
        jsonParams()
    );
}

function getAuthCookie(loginRes) {
    const authCookie = loginRes.cookies.AUTH;
    return authCookie && authCookie.length > 0 ? authCookie[0].value : null;
}

function getRooms(cookie) {
    return http.get(`${BASE}/api/rooms`, jsonParams(cookie));
}

export function setup() {
    const users = [];

    for (let i = 1; i <= USER_COUNT; i++) {
        const user = {
            username: `ws_multi_user_${i}`,
            password: PASSWORD,
        };

        registerUser(user);
        users.push(user);
    }

    const owner = users[0];
    const ownerLoginRes = loginUser(owner);
    check(ownerLoginRes, {
        'setup owner login ok': (r) => r.status === 200,
    });

    const ownerCookie = getAuthCookie(ownerLoginRes);
    if (!ownerCookie) {
        throw new Error('Setup failed: no AUTH cookie returned for owner');
    }

    const roomNames = [];
    for (let i = 0; i < ROOM_COUNT; i++) {
        const roomName = `${ROOM_PREFIX}-${i}`;
        roomNames.push(roomName);

        const createRoomRes = http.post(
            `${BASE}/api/rooms/make`,
            JSON.stringify({
                roomName,
                encrypted: false,
            }),
            jsonParams(ownerCookie)
        );

        check(createRoomRes, {
            [`setup create room ok ${roomName}`]: (r) => r.status === 201,
        });
    }

    const roomsRes = getRooms(ownerCookie);
    check(roomsRes, {
        'setup rooms list ok': (r) => r.status === 200,
    });

    const listedRooms = roomsRes.json();
    const roomIds = roomNames.map((roomName) => {
        const room = listedRooms.find((r) => r.roomName === roomName);
        if (!room) {
            throw new Error(`Setup failed: room "${roomName}" not found`);
        }
        return room.roomId;
    });

    for (let i = 1; i < users.length; i++) {
        const user = users[i];
        const userLoginRes = loginUser(user);
        const cookie = getAuthCookie(userLoginRes);

        if (!cookie) {
            throw new Error(`Setup failed: no AUTH cookie for ${user.username}`);
        }

        const assignedRoomId = roomIds[(i - 1) % roomIds.length];

        const joinRes = http.post(
            `${BASE}/api/rooms/join`,
            JSON.stringify({ roomId: assignedRoomId }),
            jsonParams(cookie)
        );

        check(joinRes, {
            [`setup join ok ${user.username}`]: (r) => r.status === 200,
        });
    }

    return {
        users,
        roomIds,
    };
}

export default function (data) {
    const userIndex = (__VU - 1) % data.users.length;
    const user = data.users[userIndex];
    const roomId = data.roomIds[userIndex % data.roomIds.length];

    const loginRes = loginUser(user);
    check(loginRes, {
        'login ok': (r) => r.status === 200,
    });

    const authCookie = getAuthCookie(loginRes);
    check(authCookie, {
        'auth cookie exists': (c) => c !== null,
    });

    if (!authCookie) {
        return;
    }

    const response = ws.connect(
        WS_URL,
        {
            headers: {
                Cookie: `AUTH=${authCookie}`,
            },
        },
        function (socket) {
            let joined = false;

            socket.on('open', () => {
                socket.send(JSON.stringify({
                    type: 'JOIN',
                    roomId,
                }));
            });

            socket.on('message', (raw) => {
                try {
                    const msg = JSON.parse(raw);

                    if (msg.type === 'JOINED' && !joined) {
                        joined = true;

                        socket.send(JSON.stringify({
                            type: 'MESSAGE',
                            roomId,
                            message: `hello from ${user.username} in ${roomId}`,
                        }));
                    }
                } catch (_) {
                }
            });

            socket.on('error', (e) => {
                console.log(`ws error for ${user.username}: ${JSON.stringify(e)}`);
            });

            socket.setTimeout(() => {
                socket.close();
            }, 5000);
        }
    );

    check(response, {
        'ws upgrade 101': (r) => r && r.status === 101,
    });

    sleep(1);
}