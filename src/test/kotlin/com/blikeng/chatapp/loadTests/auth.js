import http from 'k6/http';
import { check } from 'k6';
import { BASE, jsonParams, getAuthCookie } from './lib.js';

// Logs in all seeded users once and saves their auth cookies to results/cookies.json.
// Run after seed.js. Requires server started with --spring.profiles.active=load
// (otherwise the login rate limit kicks in after 5 requests from localhost).

const USERS_FILE = __ENV.USERS_FILE;
if (!USERS_FILE) throw new Error('USERS_FILE is required — run seed.js first');
const users = JSON.parse(open(USERS_FILE));

// ~200ms per bcrypt login sequentially; add 60s headroom
const setupSecs = Math.ceil(users.length * 0.2) + 60;
export const options = { vus: 1, iterations: 1, setupTimeout: `${setupSecs}s` };

export function setup() {
    return users.map((user) => {
        const res = http.post(`${BASE}/api/login`, JSON.stringify(user), jsonParams());
        const ok = check(res, { 'logged in': (r) => r.status === 200 });
        if (!ok) console.error(`auth: login failed for ${user.username}: HTTP ${res.status}`);
        return getAuthCookie(res);
    });
}

export default function () {}

export function handleSummary(data) {
    return {
        'results/cookies.json': JSON.stringify(data.setup_data, null, 2),
    };
}