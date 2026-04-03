// @ts-check
import { defineConfig } from "@playwright/test";

const baseURL = process.env.BASE_URL || "http://localhost:8082";

export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: false,
  retries: 1,
  timeout: 240_000,
  expect: {
    timeout: 10_000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    {
      name: "desktop-browser",
      use: {
        browserName: "chromium",
        channel: process.env.PW_CHANNEL || "chrome",
      },
    },
  ],
});
