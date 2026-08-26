import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    vus: 100,
    duration: '10s',
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

    const seatId = __VU; // 1 to 100
    const params = { headers: { 'Content-Type': 'application/json' } };

    // ── Lock seat and abandon transaction without booking ────────────────────
    let lockRes = http.post(`${BASE_URL}/api/events/${EVENT_ID}/seats/${seatId}/lock`, null, params);
    check(lockRes, {
        'lock request executed': (r) => r.status === 200 || r.status === 409,
    });

    // Disconnect without booking — leaves seat in LOCKED state until Reaper TTL
    sleep(1);
}
