import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    vus: 100,
    duration: '10s',
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    const seatId = __VU; // 1 to 100
    const payload = JSON.stringify({ userId: __VU, seatId: seatId });
    const params = { headers: { 'Content-Type': 'application/json' } };
    
    // Lock seat
    let lockRes = http.post(`${BASE_URL}/api/v1/seats/lock`, payload, params);
    check(lockRes, { 'locked': (r) => r.status === 200 });
    
    // Disconnect without booking
    sleep(1);
}
