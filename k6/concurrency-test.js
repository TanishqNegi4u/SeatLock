import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

export let errorRate = new Rate('errors');

export let options = {
    stages: [
        { duration: '30s', target: 500 },
    ],
    thresholds: {
        errors: ['rate<0.1'], // <10% errors
        http_req_duration: ['p(95)<500'] // 95% of requests must complete below 500ms
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    // 1. GET seat map
    let res = http.get(`${BASE_URL}/api/v1/seats`);
    check(res, { 'status is 200': (r) => r.status === 200 });

    const seatId = Math.floor(Math.random() * 500) + 1;
    
    // 2. POST lock
    const payload = JSON.stringify({ userId: __VU, seatId: seatId });
    const params = { headers: { 'Content-Type': 'application/json' } };
    let lockRes = http.post(`${BASE_URL}/api/v1/seats/lock`, payload, params);
    
    check(lockRes, {
        'locked or conflict': (r) => r.status === 200 || r.status === 409,
    });

    if (lockRes.status === 200) {
        // 3. POST book
        let bookRes = http.post(`${BASE_URL}/api/v1/seats/book`, payload, params);
        check(bookRes, { 'booked 200': (r) => r.status === 200 });
        if (bookRes.status !== 200) errorRate.add(1);
    }
    
    sleep(Math.random() * 2);
}
