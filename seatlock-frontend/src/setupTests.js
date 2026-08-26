import '@testing-library/jest-dom';
import crypto from 'crypto';

// Polyfill Web Crypto API (crypto.randomUUID) in jsdom test environment
if (!global.crypto) {
  global.crypto = {};
}
if (!global.crypto.randomUUID) {
  global.crypto.randomUUID = () => crypto.randomUUID();
}
