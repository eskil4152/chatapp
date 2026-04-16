export const BASE = 'http://localhost:5050';
export const WS_URL = 'ws://localhost:5050/ws';
export const PASSWORD = 'testpassword123';

export function jsonParams(cookie = null) {
    const headers = { 'Content-Type': 'application/json' };
    if (cookie) headers.Cookie = `AUTH=${cookie}`;
    return { headers };
}

export function getAuthCookie(res) {
    const c = res.cookies.AUTH;
    return c && c.length > 0 ? c[0].value : null;
}

// Ramp to 25% → full → hold 2m → ramp down
// setupTimeout scales with VU count: ~14 reqs/s in setup, +60s headroom
// setupReqsPerUser: HTTP calls per user in setup (1 for HTTP tests, 3 for WS: register+login+join)
export function makeOptions(target, setupReqsPerUser = 1) {
    const setupSecs = Math.ceil(target * setupReqsPerUser / 14) + 60;
    return {
        setupTimeout: `${setupSecs}s`,
        stages: [
            { duration: '30s', target: Math.ceil(target * 0.25) },
            { duration: '1m',  target: target },
            { duration: '2m',  target: target },
            { duration: '30s', target: 0 },
        ],
    };
}
