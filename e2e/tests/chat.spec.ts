import { test, expect } from '@playwright/test';
import { startJavaClaw, type JavaClawServer } from './javaclaw-server';

let server: JavaClawServer;

test.beforeAll(async () => {
  server = await startJavaClaw();
});

test.afterAll(async () => {
  await server?.stop();
});

test.describe('Atmosphere Chat Transport', () => {

  test('connects via Atmosphere and shows Connected status', async ({ page }) => {
    await page.goto(`${server.baseUrl}/chat`);

    // Should show "Connected" via atmosphere.js (not htmx-ws)
    await expect(page.locator('#status-tag')).toHaveText('Connected', { timeout: 15_000 });
    await expect(page.locator('#status-tag')).toHaveClass(/is-success/);
  });

  test('sends a message and receives streaming response', async ({ page }) => {
    await page.goto(`${server.baseUrl}/chat`);
    await expect(page.locator('#status-tag')).toHaveText('Connected', { timeout: 15_000 });

    // Type and send a message
    await page.locator('#message-input').fill('Say hello');
    await page.locator('#send-btn').click();

    // User message should appear
    await expect(page.locator('.ar-msg--user')).toBeVisible();
    await expect(page.locator('.ar-msg--user .ar-msg__bubble')).toContainText('Say hello');

    // Input should be cleared
    await expect(page.locator('#message-input')).toHaveValue('');

    // Agent response should stream in (wait for content to appear)
    await expect(page.locator('.ar-msg--agent .ar-msg__bubble').last())
      .not.toBeEmpty({ timeout: 60_000 });
  });

  test('renders markdown in streaming response', async ({ page }) => {
    await page.goto(`${server.baseUrl}/chat`);
    await expect(page.locator('#status-tag')).toHaveText('Connected', { timeout: 15_000 });

    // Ask something that should produce markdown
    await page.locator('#message-input').fill('List 3 Java features using bullet points');
    await page.locator('#send-btn').click();

    // Wait for response
    await expect(page.locator('.ar-msg--agent .ar-msg__bubble').last())
      .not.toBeEmpty({ timeout: 60_000 });

    // Markdown should be rendered (not raw text) — look for HTML elements
    // produced by marked.js (p, ul/ol, li, strong, code, etc.)
    const bubble = page.locator('.ar-msg--agent .ar-msg__bubble--markdown').last();
    await expect(bubble).toBeVisible({ timeout: 60_000 });
  });

  test('multiple tabs connect simultaneously', async ({ browser }) => {
    const context1 = await browser.newContext();
    const context2 = await browser.newContext();
    const page1 = await context1.newPage();
    const page2 = await context2.newPage();

    await page1.goto(`${server.baseUrl}/chat`);
    await page2.goto(`${server.baseUrl}/chat`);

    // Both should connect via Atmosphere
    await expect(page1.locator('#status-tag')).toHaveText('Connected', { timeout: 15_000 });
    await expect(page2.locator('#status-tag')).toHaveText('Connected', { timeout: 15_000 });

    // Send from tab 1 and verify it gets a response
    await page1.locator('#message-input').fill('Hello from tab 1');
    await page1.locator('#send-btn').click();
    await expect(page1.locator('.ar-msg--agent .ar-msg__bubble').last())
      .not.toBeEmpty({ timeout: 60_000 });

    // Tab 2 should still be connected after tab 1's exchange
    await expect(page2.locator('#status-tag')).toHaveText('Connected');

    // Send from tab 2 and verify it also gets a response
    await page2.locator('#message-input').fill('Hello from tab 2');
    await page2.locator('#send-btn').click();
    await expect(page2.locator('.ar-msg--agent .ar-msg__bubble').last())
      .not.toBeEmpty({ timeout: 60_000 });

    await context1.close();
    await context2.close();
  });

  test('typing indicator disappears after response streams', async ({ page }) => {
    await page.goto(`${server.baseUrl}/chat`);
    await expect(page.locator('#status-tag')).toHaveText('Connected', { timeout: 15_000 });

    await page.locator('#message-input').fill('Tell me a joke');
    await page.locator('#send-btn').click();

    // Wait for the agent response to stream in
    await expect(page.locator('.ar-msg--agent .ar-msg__bubble').last())
      .not.toBeEmpty({ timeout: 60_000 });

    // After streaming completes, typing indicator should be gone
    await expect(page.locator('.ar-typing__dots')).not.toBeVisible({ timeout: 10_000 });
  });

  test('Enter key sends message', async ({ page }) => {
    await page.goto(`${server.baseUrl}/chat`);
    await expect(page.locator('#status-tag')).toHaveText('Connected', { timeout: 15_000 });

    await page.locator('#message-input').fill('Quick test');
    await page.locator('#message-input').press('Enter');

    // Message should be sent
    await expect(page.locator('.ar-msg--user .ar-msg__bubble')).toContainText('Quick test');
    await expect(page.locator('#message-input')).toHaveValue('');
  });

  test('welcome message is visible on load', async ({ page }) => {
    await page.goto(`${server.baseUrl}/chat`);

    await expect(page.locator('.ar-msg--agent .ar-msg__bubble').first())
      .toContainText('JavaClaw assistant');
  });
});
