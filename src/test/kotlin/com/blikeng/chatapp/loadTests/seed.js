import http from 'k6/http';
import {check} from 'k6';
import {scenario} from 'k6/execution';
import {BASE, jsonParams, PASSWORD} from './lib.js';

const USERS = __ENV.USERS ? parseInt(__ENV.USERS) : 100;
const RUN_ID = __ENV.RUN_ID || Date.now();

const userList = Array.from({ length: USERS }, (_, i) => ({
    username: `shared_${RUN_ID}_${i + 1}`,
    password: PASSWORD,
}));

export const options = { vus: 10, iterations: USERS };

export default function () {
    const user = userList[scenario.iterationInTest];
    const res = http.post(`${BASE}/api/register`, JSON.stringify(user), jsonParams());
    check(res, { 'registered': (r) => r.status === 201 });
}

export function handleSummary() {
    return {
        'results/users.json': JSON.stringify(userList, null, 2),
    };
}