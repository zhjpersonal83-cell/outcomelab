import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  use: { baseURL: process.env.BASE_URL || "http://127.0.0.1:5173" },
  webServer: process.env.BASE_URL
    ? undefined
    : [
        {
          command: "python -m uvicorn app:app --host 127.0.0.1 --port 8000",
          cwd: "../backend",
          url: "http://127.0.0.1:8000/api/ready",
          reuseExistingServer: !process.env.CI,
        },
        {
          command: "pnpm dev",
          url: "http://127.0.0.1:5173",
          reuseExistingServer: !process.env.CI,
        },
      ],
});
