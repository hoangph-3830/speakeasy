import { test as base, expect, type Page } from '@playwright/test';
import { applyLocaleInjection } from './locale-fixture';

type CleanupConfig = {
  overlay?: boolean;
  forms?: boolean;
  scroll?: boolean;
  storage?: boolean;
};

async function cleanupPage(page: Page, config: CleanupConfig): Promise<void> {
  if (config.overlay) {
    await page.keyboard.press('Escape').catch(() => {});
    await page.locator('body').click({ position: { x: 1, y: 1 }, force: true }).catch(() => {});
    const hasOverlay = await page.evaluate(`(() => {
      const el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
      if (!el) return false;
      let current = el;
      while (current && current !== document.body) {
        if (getComputedStyle(current).position === 'fixed') return true;
        current = current.parentElement;
      }
      return false;
    })()`).catch(() => false);
    if (hasOverlay) {
      await page.keyboard.press('Escape').catch(() => {});
    }
  }
  if (config.forms) {
    await page.evaluate(`(() => {
      const inputSetter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
      const textareaSetter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')?.set;
      document.querySelectorAll('input:not([type=hidden]):not([type=submit]):not([type=checkbox]):not([type=radio])').forEach(el => {
        inputSetter?.call(el, '');
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
      });
      document.querySelectorAll('textarea').forEach(el => {
        textareaSetter?.call(el, '');
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
      });
      document.querySelectorAll('select').forEach(el => {
        el.selectedIndex = 0;
        el.dispatchEvent(new Event('change', { bubbles: true }));
      });
      document.querySelectorAll('input[type=checkbox]').forEach(el => {
        if (el.checked !== el.defaultChecked) {
          el.checked = el.defaultChecked;
          el.dispatchEvent(new Event('change', { bubbles: true }));
        }
      });
      document.querySelectorAll('form').forEach(f => f.reset());
    })()`).catch(() => {});
  }
  if (config.scroll) {
    await page.evaluate('window.scrollTo(0, 0)').catch(() => {});
  }
  if (config.storage) {
    await page.evaluate('sessionStorage.clear()').catch(() => {});
  }
}

const test = base.extend<{
  screenshotOnFailure: boolean;
  _autoScreenshot: void;
}>({
  screenshotOnFailure: [false, { option: true }],

  // Wrap the default `context` fixture so locale state (sessionStorage,
  // localStorage, cookies) is injected before the first page navigation.
  // No-op when SUNGEN_ENV is unset or specs/locale-config.json is missing.
  context: async ({ context }, use) => {
    await applyLocaleInjection(context);
    await use(context);
  },

  page: async ({ context }, use) => {
    const page = await context.newPage();
    await use(page);
    await page.close();
  },

  _autoScreenshot: [async ({ page, screenshotOnFailure }, use, testInfo) => {
    await use();

    if (screenshotOnFailure && testInfo.status !== testInfo.expectedStatus) {
      await testInfo.attach('screenshot', {
        body: await page.screenshot(),
        contentType: 'image/png',
      });
    }
  }, { auto: true }],
});

export { test, expect, cleanupPage };
