import http from 'k6/http';
import {check, sleep} from 'k6';

export const options = {
    stages: [
        { duration: '15s', target: 10 },
        { duration: '30s', target: 20 },
        { duration: '30s', target: 40 },
        { duration: '15s', target: 0 },
    ],
};

const BASE = 'http://localhost:5050';

export default function () {
    const username = `user_${__VU}_${__ITER}`;
    const password = 'testpassword123';

    const payload = JSON.stringify({
        username: username,
        password: password,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const registerRes = http.post(`${BASE}/api/register`, payload, params);

    check(registerRes, {
        'register is 201': (r) => r.status === 201,
    });

    const loginRes = http.post(`${BASE}/api/login`, payload, params);

    check(loginRes, {
        'login is 200': (r) => r.status === 200,
    });

    const authRes = http.get(`${BASE}/api/auth`);

    check(authRes, {
        'auth is 200': (r) => r.status === 200,
    });

    sleep(1);
}