import { test, expect } from '@playwright/test';

/**
 * Verifies the React consumer integration: forwardRef + useImperativeHandle
 * wrapper around an async-created KsonEditor, mounted under <StrictMode>.
 * The test drives the demo's existing buttons and asserts via the visible
 * log so we catch double-creates, dispose leaks, and ref-handle regressions.
 */
test.describe('React demo', () => {
    test('ref handle exposes a working editor; StrictMode does not double-create', async ({ page }) => {
        await page.goto('http://localhost:5176');

        const log = page.getByTestId('log');
        const editor = page.locator('.monaco-editor').first();

        await expect(editor).toBeVisible({ timeout: 15000 });
        await expect(log).toContainText('editor ready', { timeout: 15000 });

        // StrictMode double-invokes effects; the wrapper's cleanup must dispose
        // the throwaway editor so only a single live instance ends up rendered.
        await expect(page.locator('.monaco-editor')).toHaveCount(1);
        const readyCount = await log.locator('text=editor ready').count();
        expect(readyCount).toBe(1);

        await page.getByTestId('btn-get').click();
        await expect(log).toContainText(/\d+ chars/);

        await page.getByTestId('btn-set').click();
        await expect(page.locator('.view-lines')).toContainText('updated');
        await expect(page.locator('.view-lines')).toContainText('2.0.0');
    });

    test('onChange fires for user edits via the ref-driven listener', async ({ page }) => {
        await page.goto('http://localhost:5176');

        const log = page.getByTestId('log');
        await expect(log).toContainText('editor ready', { timeout: 15000 });

        await page.locator('.monaco-editor .view-lines').click();
        await page.keyboard.press('End');
        await page.keyboard.type(' ');

        await expect(log).toContainText('onChange', { timeout: 5000 });
    });

    test('explicit dispose() via the ref tears down cleanly', async ({ page }) => {
        await page.goto('http://localhost:5176');

        const log = page.getByTestId('log');
        await expect(log).toContainText('editor ready', { timeout: 15000 });

        await page.getByTestId('btn-dispose').click();
        await expect(log).toContainText('disposed');

        // After dispose the buttons go disabled (ready=false in the demo) and
        // re-clicking must not throw or log additional disposed entries.
        await expect(page.getByTestId('btn-get')).toBeDisabled();
        await expect(page.getByTestId('btn-set')).toBeDisabled();
        await expect(page.getByTestId('btn-dispose')).toBeDisabled();
    });
});
