import http from 'k6/http';
import ws from 'k6/ws';
import {check, sleep} from 'k6';

const BASE = 'http://localhost:5050';
const WS_URL = 'ws://localhost:5050/ws';

const PASSWORD = 'testpassword123';
const USER_COUNT = 25;
const ROOM_NAME = `ws-load-room-${Date.now()}`;

export const options = {
    stages: [
        { duration: '20s', target: 5 },
        { duration: '30s', target: 10 },
        { duration: '40s', target: 25 },
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
            username: `ws_user_${i}`,
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

    const createRoomRes = http.post(
        `${BASE}/api/rooms/make`,
        JSON.stringify({
            roomName: ROOM_NAME,
            encrypted: false,
        }),
        jsonParams(ownerCookie)
    );

    check(createRoomRes, {
        'setup room create ok': (r) => r.status === 201,
    });

    const roomsRes = getRooms(ownerCookie);
    check(roomsRes, {
        'setup rooms list ok': (r) => r.status === 200,
    });

    const rooms = roomsRes.json();
    const room = rooms.find((r) => r.roomName === ROOM_NAME);

    if (!room) {
        throw new Error(`Setup failed: room "${ROOM_NAME}" not found`);
    }

    const roomId = room.roomId;

    for (let i = 1; i < users.length; i++) {
        const userLoginRes = loginUser(users[i]);
        const cookie = getAuthCookie(userLoginRes);

        if (!cookie) {
            throw new Error(`Setup failed: no AUTH cookie for ${users[i].username}`);
        }

        const joinRes = http.post(
            `${BASE}/api/rooms/join`,
            JSON.stringify({ roomId }),
            jsonParams(cookie)
        );

        check(joinRes, {
            [`setup join ok ${users[i].username}`]: (r) => r.status === 200,
        });
    }

    return {
        users,
        roomId,
    };
}

export default function (data) {
    const user = data.users[(__VU - 1) % data.users.length];

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
            let sentMessage = false;
            let gotMessage = false;

            socket.on('open', () => {
                socket.send(JSON.stringify({
                    type: 'JOIN',
                    roomId: data.roomId,
                }));
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

                        sentMessage = true;
                    }

                    if (msg.type === 'MESSAGE') {
                        gotMessage = true;
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