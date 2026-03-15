import http from 'k6/http';
import {check, sleep} from 'k6';

const BASE = 'http://localhost:5050';
const PASSWORD = 'testpassword123';

export const options = {
    vus: 50,
    duration: '60s',
};

export function setup() {
    const users = [];

    for (let i = 1; i <= options.vus; i++) {
        const username = `multi_user_${i}`;
        const payload = JSON.stringify({
            username: username,
            password: PASSWORD,
        });

        const params = {
            headers: { 'Content-Type': 'application/json' }
        };

        http.post(`${BASE}/api/register`, payload, params);

        users.push({
            username: username,
            password: PASSWORD,
        });
    }

    return users;
}

export default function (users) {
    const user = users[__VU - 1];

    const loginRes = http.post(`${BASE}/api/login`, JSON.stringify(user), {
        headers: { 'Content-Type': 'application/json' }
    });

    check(loginRes, {
        'login success': (r) => r.status === 200,
    });

    const createRoomRes = http.post(`${BASE}/api/rooms/make`, JSON.stringify({
        roomName: `room-${Math.floor(__VU % 10)}`,
        encrypted: false
    }), {
        headers: { 'Content-Type': 'application/json' }
    });

    check(createRoomRes, {
        'room create accepted': (r) => r.status === 201 || r.status === 400 || r.status === 409,
    });

    const roomsRes = http.get(`${BASE}/api/rooms`);

    check(roomsRes, {
        'rooms fetched': (r) => r.status === 200,
    });

    sleep(Math.random() * 2);
}