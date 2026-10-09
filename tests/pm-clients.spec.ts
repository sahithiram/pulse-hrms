import { test, expect } from '@playwright/test';

test.describe('PM - Clients', () => {

  // =========================================================
  // P0 - CRITICAL
  // =========================================================

  test('PM-CLI-P0-001 - Clients page should load successfully', async ({ page }) => {
    const response = await page.goto('/pm/08-clients', {
      waitUntil: 'domcontentloaded'
    });

    expect(response?.status()).toBeLessThan(400);

    await expect(page).toHaveURL(
      'http://localhost:8080/pm/08-clients'
    );

    await expect(page.locator('body')).toBeVisible();
  });


  test('PM-CLI-P0-002 - New Client workflow should open', async ({ page }) => {
    await page.goto('/pm/08-clients');

    await page.getByRole('button', {
      name: '+ New Client'
    }).click();

    await expect(
      page.getByRole('button', {
        name: /Template Industry presets/
      })
    ).toBeVisible();

    await expect(
      page.getByRole('button', {
        name: /Manual Full form/
      })
    ).toBeVisible();
  });


  // =========================================================
  // P1 - HIGH
  // =========================================================

  test('PM-CLI-P1-001 - Template Industry presets should open', async ({ page }) => {
    await page.goto('/pm/08-clients');

    await page.getByRole('button', {
      name: '+ New Client'
    }).click();

    await page.getByRole('button', {
      name: /Template Industry presets/
    }).click();

    await expect(
      page.getByText('FintechFintech StartupFast-')
    ).toBeVisible();
  });


  test('PM-CLI-P1-002 - Clone Copy and Tweak option should be available', async ({ page }) => {
    await page.goto('/pm/08-clients');

    await page.getByRole('button', {
      name: '+ New Client'
    }).click();

    await expect(
      page.getByRole('button', {
        name: /Clone Copy \+ tweak/
      })
    ).toBeVisible();
  });


  test('PM-CLI-P1-003 - Import CSV Bulk upload option should be available', async ({ page }) => {
    await page.goto('/pm/08-clients');

    await page.getByRole('button', {
      name: '+ New Client'
    }).click();

    await expect(
      page.getByRole('button', {
        name: /Import CSV Bulk upload/
      })
    ).toBeVisible();
  });


  test('PM-CLI-P1-004 - Manual Full Form should open', async ({ page }) => {
    await page.goto('/pm/08-clients');

    await page.getByRole('button', {
      name: '+ New Client'
    }).click();

    await page.getByRole('button', {
      name: /Manual Full form/
    }).click();

    await expect(page.locator('#fldIndustry')).toBeVisible();
    await expect(page.locator('#fldStatus')).toBeVisible();
    await expect(page.locator('#fldBilling')).toBeVisible();
  });


  test('PM-CLI-P1-005 - Industry can be selected', async ({ page }) => {
    await page.goto('/pm/08-clients');

    await page.getByRole('button', {
      name: '+ New Client'
    }).click();

    await page.getByRole('button', {
      name: /Manual Full form/
    }).click();

    await page.locator('#fldIndustry').selectOption('Healthcare');

    await expect(
      page.locator('#fldIndustry')
    ).toHaveValue('Healthcare');
  });


  test('PM-CLI-P1-006 - Client status can be selected', async ({ page }) => {
    await page.goto('/pm/08-clients');

    await page.getByRole('button', {
      name: '+ New Client'
    }).click();

    await page.getByRole('button', {
      name: /Manual Full form/
    }).click();

    await page.locator('#fldStatus').selectOption('active');

    await expect(
      page.locator('#fldStatus')
    ).toHaveValue('active');
  });


  test('PM-CLI-P1-007 - Client location details can be entered', async ({ page }) => {
    await page.goto('/pm/08-clients');

    await page.getByRole('button', {
      name: '+ New Client'
    }).click();

    await page.getByRole('button', {
      name: /Manual Full form/
    }).click();

    await page.getByRole('textbox', {
      name: 'Mumbai'
    }).fill('Hyderabad');

    await page.getByRole('textbox', {
      name: 'India'
    }).fill('India');

    await page.getByRole('textbox', {
      name: 'Street, area...'
    }).fill('Grand View Tower, Hafeezpet');

    await expect(
      page.getByRole('textbox', { name: 'Mumbai' })
    ).toHaveValue('Hyderabad');
  });


  test('PM-CLI-P1-008 - Geocode option should be available', async ({ page }) => {
    await page.goto('/pm/08-clients');

    await page.getByRole('button', {
      name: '+ New Client'
    }).click();

    await page.getByRole('button', {
      name: /Manual Full form/
    }).click();

    await expect(
      page.getByRole('button', {
        name: /Geocode/
      })
    ).toBeVisible();
  });


  test('PM-CLI-P1-009 - Contact details can be entered', async ({ page }) => {
    await page.goto('/pm/08-clients');

    await page.getByRole('button', {
      name: '+ New Client'
    }).click();

    await page.getByRole('button', {
      name: /Manual Full form/
    }).click();

    await page.getByRole('textbox', {
      name: 'Meera Kulkarni'
    }).fill('Rohini Anand');

    await page.getByRole('textbox', {
      name: 'VP Operations'
    }).fill('CTO');

    await expect(
      page.getByRole('textbox', { name: 'Meera Kulkarni' })
    ).toHaveValue('Rohini Anand');

    await expect(
      page.getByRole('textbox', { name: 'VP Operations' })
    ).toHaveValue('CTO');
  });


  test('PM-CLI-P1-010 - Email and phone details can be entered', async ({ page }) => {
    await page.goto('/pm/08-clients');

    await page.getByRole('button', {
      name: '+ New Client'
    }).click();

    await page.getByRole('button', {
      name: /Manual Full form/
    }).click();

    await page.getByRole('textbox', {
      name: 'meera@zenith.example'
    }).fill('qa@example.com');

    await page.getByRole('textbox', {
      name: '+91 98200'
    }).fill('9866753772');

    await expect(
      page.getByRole('textbox', { name: '+91 98200' })
    ).toHaveValue('9866753772');
  });


  test('PM-CLI-P1-011 - Billing type and amount can be entered', async ({ page }) => {
    await page.goto('/pm/08-clients');

    await page.getByRole('button', {
      name: '+ New Client'
    }).click();

    await page.getByRole('button', {
      name: /Manual Full form/
    }).click();

    await page.locator('#fldBilling').selectOption('Fixed Price');

    await page.getByRole('spinbutton', {
      name: '58.5'
    }).fill('5000000');

    await expect(
      page.locator('#fldBilling')
    ).toHaveValue('Fixed Price');
  });


  test('PM-CLI-P1-012 - Tags input should be available', async ({ page }) => {
    await page.goto('/pm/08-clients');

    await page.getByRole('button', {
      name: '+ New Client'
    }).click();

    await page.getByRole('button', {
      name: /Manual Full form/
    }).click();

    await expect(
      page.getByRole('textbox', {
        name: 'Type + Enter'
      })
    ).toBeVisible();
}
);
});
