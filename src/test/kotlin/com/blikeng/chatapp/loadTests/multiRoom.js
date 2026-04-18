import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE, jsonParams, makeOptions } from './lib.js';

const COOKIES_FILE = __ENV.COOKIES_FILE;
if (!COOKIES_FILE) throw new Error('COOKIES_FILE is required — run auth.js first');
const _cookies = JSON.parse(open(COOKIES_FILE));
const TARGET_VUS = __ENV.USERS ? parseInt(__ENV.USERS) : 100;
const ROOM_COUNT = 10;

export const options = { ...makeOptions(TARGET_VUS) };

export function setup() {
    return _cookies.slice(0, TARGET_VUS);
}

export default function (cookies) {
    const cookie = cookies[(__VU - 1) % cookies.length];
    if (!cookie) return;

    // Multiple VUs create rooms with the same names — room names are not unique,
    // so all should succeed with 201.
    const createRes = http.post(
        `${BASE}/api/rooms/make`,
        JSON.stringify({ roomName: `multi_room_${__VU % ROOM_COUNT}`, encrypted: false }),
        jsonParams(cookie)
    );
    check(createRes, { 'room created': (r) => r.status === 201 });

    const roomsRes = http.get(`${BASE}/api/rooms`, jsonParams(cookie));
    check(roomsRes, { 'rooms fetched': (r) => r.status === 200 });

    sleep(Math.random() * 2);
}