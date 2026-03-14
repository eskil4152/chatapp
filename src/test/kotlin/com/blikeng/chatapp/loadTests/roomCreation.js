import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = 'http://localhost:5050';
const PASSWORD = 'testpassword123';
const USER_COUNT = 100;

export const options = {
    stages: [
        { duration: '20s', target: 25 },
        { duration: '40s', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '20s', target: 0 },
    ],
};

export function setup() {
    const users = [];

    for (let i = 1; i <= USER_COUNT; i++) {
        const username = `list_user_${i}`;
        const payload = JSON.stringify({
            username,
            password: PASSWORD
        });
        const params = {
            headers: { 'Content-Type': 'application/json' }
        };

        http.post(`${BASE}/api/register`, payload, params);
        users.push({ username, password: PASSWORD });
    }

    return users;
}

export default function (users) {
    const user = users[(__VU - 1) % users.length];

    const loginRes = http.post(
        `${BASE}/api/login`,
        JSON.stringify(user),
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(loginRes, {
        'login ok': (r) => r.status === 200,
    });

    const roomsRes = http.get(`${BASE}/api/rooms`);

    check(roomsRes, {
        'rooms fetched': (r) => r.status === 200,
    });

    sleep(1);
}