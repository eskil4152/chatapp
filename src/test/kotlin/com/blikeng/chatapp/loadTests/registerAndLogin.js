import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE, PASSWORD, jsonParams, getAuthCookie, makeOptions } from './lib.js';

const TARGET_VUS = __ENV.USERS ? parseInt(__ENV.USERS) : 100;
const RUN_ID = Date.now();

export const options = { ...makeOptions(TARGET_VUS) };

export function setup() {
    const users = [];
    for (let i = 1; i <= TARGET_VUS; i++) {
        const user = { username: `ral_${RUN_ID}_${i}`, password: PASSWORD };
        const res = http.post(`${BASE}/api/register`, JSON.stringify(user), jsonParams());
        check(res, { 'register ok': (r) => r.status === 201 });
        users.push(user);
    }
    return users;
}

export default function (users) {
    const user = users[(__VU - 1) % users.length];

    const loginRes = http.post(`${BASE}/api/login`, JSON.stringify(user), jsonParams());
    check(loginRes, { 'login is 200': (r) => r.status === 200 });

    const cookie = getAuthCookie(loginRes);
    if (!cookie) return;

    const authRes = http.get(`${BASE}/api/auth`, jsonParams(cookie));
    check(authRes, { 'auth is 200': (r) => r.status === 200 });

    sleep(1);
}
