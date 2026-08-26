import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    scenarios: {
        burst: {
            executor: 'per-vu-iterations',
            vus: 500,
            iterations: 1,
            maxDuration: '30s',
        },
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    // 1. Burst hitting waiting room queue
    let queueRes = http.post(`${BASE_URL}/api/v1/queue/join`, JSON.stringify({ userId: __VU }), { headers: { 'Content-Type': 'application/json' } });
    check(queueRes, { 'joined queue': (r) => r.status === 200 || r.status === 202 });

    let admitted = false;
    for(let i=0; i<10; i++) {
        let pollRes = http.get(`${BASE_URL}/api/v1/queue/status?userId=${__VU}`);
        if(pollRes.status === 200 && pollRes.json('status') === 'ADMITTED') {
            admitted = true;
            break;
        }
        sleep(1);
    }

    if(admitted) {
        const seatId = Math.floor(Math.random() * 100) + 1;
        const payload = JSON.stringify({ userId: __VU, seatId: seatId });
        const params = { headers: { 'Content-Type': 'application/json' } };
        let lockRes = http.post(`${BASE_URL}/api/v1/seats/lock`, payload, params);
        if (lockRes.status === 200) {
            http.post(`${BASE_URL}/api/v1/seats/book`, payload, params);
        }
    }
}
