import { test, expect } from "@playwright/test";
test("complete an honest experiment", async ({ page }) => {
  await page.goto("/");
  await expect(
    page.getByText("Fresh sandbox. Baseline restored."),
  ).toBeVisible();
  await page.getByRole("button", { name: "Run prediction" }).click();
  await expect(page.getByText("Routes to")).toBeVisible();
  await page.getByRole("button", { name: "Apply correction" }).click();
  await expect(page.getByText("MODEL v1", { exact: false })).toBeVisible();
  await page.getByLabel("Inject distracting vocabulary").check();
  await expect(page.getByText("Shifted set:", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: "Roll back one update" }).click();
  await expect(page.getByText("MODEL v0", { exact: false })).toBeVisible();
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Export JSON" }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe("outcomelab-experiment.json");
  await page.getByRole("button", { name: "Reset experiment" }).click();
  await expect(
    page.getByText("Fresh sandbox. Baseline restored."),
  ).toBeVisible();
  await page.getByRole("button", { name: "Toggle colour theme" }).click();
  await expect(page.locator("[data-theme=light]")).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  const layout = await page.evaluate(() => ({
    width: innerWidth,
    scrollWidth: document.documentElement.scrollWidth,
    overflowing: Array.from(document.querySelectorAll("body *"))
      .filter((el) => el.getBoundingClientRect().right > innerWidth + 1)
      .map((el) => ({
        tag: el.tagName,
        className: el.className,
        right: el.getBoundingClientRect().right,
      })),
  }));
  expect(layout.scrollWidth, JSON.stringify(layout)).toBeLessThanOrEqual(
    layout.width,
  );
});
