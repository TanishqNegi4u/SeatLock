const { test, expect } = require('@playwright/test');

test.describe('SeatLock Distributed Booking Flow', () => {
  test('user enters queue, selects available seat, locks, and confirms booking', async ({ page }) => {
    // 1. Navigate to event booking page
    await page.goto('/event/1');

    // 2. Wait for waiting room admission (or immediate access if admitted)
    await expect(page.locator('.stage')).toBeVisible({ timeout: 15000 });

    // 3. Find and select first available seat
    const availableSeat = page.locator('.seat.available').first();
    await expect(availableSeat).toBeVisible();
    await availableSeat.click();

    // 4. Verify selected seat details in booking panel
    await expect(page.locator('.seat-label-big')).toBeVisible();
    const payBtn = page.getByRole('button', { name: /Confirm & Pay/i });
    await expect(payBtn).toBeVisible();

    // 5. Submit payment
    await payBtn.click();

    // 6. Verify booking confirmation and booking ID
    await expect(page.getByText('Booking Confirmed!')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.booking-detail').first()).toContainText('Seat:');
  });

  test('admin dashboard renders real-time telemetry and audit stream', async ({ page }) => {
    await page.goto('/event/1/admin');

    await expect(page.getByText(/Distributed Engine Telemetry/i)).toBeVisible();
    await expect(page.locator('.stat-cards-grid')).toBeVisible();
    await expect(page.locator('.audit-table')).toBeVisible();
  });
});
