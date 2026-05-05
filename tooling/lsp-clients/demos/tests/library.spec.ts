import { test, expect } from '@playwright/test';

/**
 * Verifies the @kson/monaco-editor ES module consumer integration:
 * the package's export map resolves, types from dist/ load, the editor
 * mounts, and the bundled JSON Schema produces diagnostics.
 */
test.describe('Library demo', () => {
    test('createKsonEditor mounts editor and exposes get/setValue', async ({ page }) => {
        await page.goto('http://localhost:5174');

        const editor = page.locator('.monaco-editor').first();
        await expect(editor).toBeVisible({ timeout: 15000});

        const log = page.locator('#log');
        await expect(log).toContainText('editor ready', { timeout: 15000 });

        await page.locator('#btn-get').click();
        await expect(log).toContainText('name: "my-project"');
        await expect(log).toContainText('version: "1.0.0"');

        await page.locator('#btn-set').click();
        await expect(page.locator('#editor .view-lines')).toContainText('updated');
        await expect(page.locator('#editor .view-lines')).toContainText('2.0.0');

        await page.locator('#btn-get').click();
        await expect(log).toContainText('name: "updated"');
    });

    test('bundled schema produces diagnostic for unknown key', async ({ page }) => {
        await page.goto('http://localhost:5174');

        await expect(page.locator('.monaco-editor').first()).toBeVisible({ timeout: 15000});
        await expect(page.locator('#log')).toContainText('editor ready', { timeout: 15000 });

        // Insert an additionalProperties violation; the schema disallows any
        // key beyond name/version/tags, so the LSP must surface a diagnostic.
        await page.locator('.monaco-editor .view-lines').click();
        await page.keyboard.press('Control+Home');
        await page.keyboard.press('ArrowDown');
        await page.keyboard.press('Home');
        await page.keyboard.type('unknownKey: "oops"\n');

        // Squiggly overlays are the user-visible LSP-marker signal; the
        // additionalProperties violation surfaces as a warning-level squiggle.
        await expect(page.locator('#editor [class*="squiggly"]').first())
            .toBeVisible({ timeout: 15000 });
    });
});
