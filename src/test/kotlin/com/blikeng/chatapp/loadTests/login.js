import http from 'k6/http';
import {check, sleep} from 'k6';
import {BASE, getAuthCookie, jsonParams, makeOptions} from './lib.js';

const USERS_FILE = __ENV.USERS_FILE;
const _preloaded = USERS_FILE ? JSON.parse(open(USERS_FILE)) : null;
const TARGET_VUS = __ENV.USERS ? parseInt(__ENV.USERS) : 100;

export const options = { ...makeOptions(TARGET_VUS) };

export function setup() {
    if (!_preloaded) throw new Error('USERS_FILE is required for login.js — run seed.js first');
    return _preloaded.slice(0, TARGET_VUS);
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