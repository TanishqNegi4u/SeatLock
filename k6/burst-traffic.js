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
const EVENT_ID = __ENV.EVENT_ID || 1;

function uuidv4() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        var r = (Math.random() * 16) | 0,
            v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
}

export default function () {
    const jar = http.cookieJar();
    const userId = uuidv4();
    jar.set(BASE_URL, 'seatlock_user_id', userId);

    const params = { headers: { 'Content-Type': 'application/json' } };

    // ── 1. Burst join waiting room queue ─────────────────────────────────────
    let queueRes = http.post(`${BASE_URL}/api/events/${EVENT_ID}/queue`, null, params);
    check(queueRes, {
        'joined waiting room': (r) => r.status === 200,
    });

    // ── 2. Poll waiting room status until admitted ───────────────────────────
    let admitted = false;
    for (let i = 0; i < 15; i++) {
        let pollRes = http.get(`${BASE_URL}/api/events/${EVENT_ID}/queue/status`);
        if (pollRes.status === 200) {
            let jsonBody = pollRes.json();
            if (jsonBody && jsonBody.status === 'ADMITTED') {
                admitted = true;
                break;
            }
        }
        sleep(1);
    }

    // ── 3. If admitted, pick and lock seat ───────────────────────────────────
    if (admitted) {
        const seatId = Math.floor(Math.random() * 500) + 1;
        let lockRes = http.post(`${BASE_URL}/api/events/${EVENT_ID}/seats/${seatId}/lock`, null, params);
        if (lockRes.status === 200) {
            const idempotencyKey = uuidv4();
            const bookPayload = JSON.stringify({ seatId: seatId, idempotencyKey: idempotencyKey });
            http.post(`${BASE_URL}/api/events/${EVENT_ID}/book`, bookPayload, params);
        }
    }
}
