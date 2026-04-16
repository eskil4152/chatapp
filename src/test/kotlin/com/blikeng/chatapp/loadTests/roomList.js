import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE, PASSWORD, jsonParams, getAuthCookie, makeOptions } from './lib.js';

const USERS_FILE = __ENV.USERS_FILE;
const _preloaded = USERS_FILE ? JSON.parse(open(USERS_FILE)) : null;
const TARGET_VUS = __ENV.USERS ? parseInt(__ENV.USERS) : 100;
const RUN_ID = Date.now();

export const options = { ...makeOptions(TARGET_VUS) };

export function setup() {
    if (_preloaded) return _preloaded.slice(0, TARGET_VUS);
    const users = [];
    for (let i = 1; i <= TARGET_VUS; i++) {
        const user = { username: `list_${RUN_ID}_${i}`, password: PASSWORD };
        http.post(`${BASE}/api/register`, JSON.stringify(user), jsonParams());
        users.push(user);
    }
    return users;
}

export default function (users) {
    const user = users[(__VU - 1) % users.length];

    const loginRes = http.post(`${BASE}/api/login`, JSON.stringify(user), jsonParams());
    check(loginRes, { 'login ok': (r) => r.status === 200 });

    const cookie = getAuthCookie(loginRes);
    if (!cookie) return;

    const roomsRes = http.get(`${BASE}/api/rooms`, jsonParams(cookie));
    check(roomsRes, { 'rooms fetched': (r) => r.status === 200 });

    sleep(1);
}
