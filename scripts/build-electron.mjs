import { spawn } from "node:child_process";

const args = process.argv.slice(2);
const cli = new URL("../node_modules/electron-builder/cli.js", import.meta.url);
const maxAttempts = 3;
const transientFailure = /Response code 5\\d\\d|ETIMEDOUT|ECONNRESET|EAI_AGAIN|socket hang up/i;

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function runBuild(attempt) {
  console.log(`\\nElectron Builder: попытка ${attempt}/${maxAttempts}\\n`);
  let output = "";
  const child = spawn(process.execPath, [cli.pathname, ...args], {
    stdio: ["inherit", "pipe", "pipe"],
    env: process.env,
  });

  child.stdout.on("data", (chunk) => {
    const value = chunk.toString();
    output += value;
    process.stdout.write(value);
  });
  child.stderr.on("data", (chunk) => {
    const value = chunk.toString();
    output += value;
    process.stderr.write(value);
  });

  const code = await new Promise((resolve, reject) => {
    child.once("error", reject);
    child.once("close", resolve);
  });

  if (code === 0) return true;
  if (attempt < maxAttempts && transientFailure.test(output)) {
    const delay = attempt * 15000;
    console.warn(`\\nВременная сетевая ошибка. Повтор сборки через ${delay / 1000} секунд...\\n`);
    await wait(delay);
    return runBuild(attempt + 1);
  }

  process.exitCode = typeof code === "number" ? code : 1;
  return false;
}

await runBuild(1);
