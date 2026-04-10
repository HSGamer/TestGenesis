import { parseArgs } from "node:util";

/**
 * Encapsulates the application configuration from CLI arguments
 * and environment variables.
 */
const { values } = parseArgs({
  args: process.argv.slice(2),
  options: {
    name: { type: "string", short: "n" },
    url: { type: "string", short: "u" },
    selenium: { type: "string", short: "s" },
  },
});

export const CONFIG = {
  HUB_URL: values.url || process.env.HUB_URL || "http://localhost:9000",
  CLIENT_NAME: values.name || process.env.CLIENT_NAME || "SideAgent-" + Math.random().toString(36).substring(7),
  SELENIUM_REMOTE_URL: values.selenium || process.env.SELENIUM_REMOTE_URL,
};

console.log(`[Config] Hub: ${CONFIG.HUB_URL}`);
console.log(`[Config] Name: ${CONFIG.CLIENT_NAME}`);
if (CONFIG.SELENIUM_REMOTE_URL) {
  console.log(`[Config] Selenium: ${CONFIG.SELENIUM_REMOTE_URL}`);
}
