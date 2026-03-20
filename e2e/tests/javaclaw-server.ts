import { type ChildProcess, spawn } from 'child_process';
import { resolve } from 'path';
import net from 'net';

const JAVACLAW_DIR = process.env.JAVACLAW_DIR || resolve(__dirname, '..', '..', '..', 'javaclaw-upstream');

export interface JavaClawServer {
  baseUrl: string;
  stop(): Promise<void>;
  getOutput(): string;
}

async function waitForHttp(url: string, timeoutMs = 90_000): Promise<void> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const res = await fetch(url);
      if (res.status < 500) return;
    } catch {
      // keep retrying
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error(`HTTP endpoint ${url} not ready after ${timeoutMs}ms`);
}

async function isPortInUse(port: number): Promise<boolean> {
  return new Promise((resolve) => {
    const sock = net.createConnection(port, '127.0.0.1');
    sock.once('connect', () => { sock.destroy(); resolve(true); });
    sock.once('error', () => resolve(false));
  });
}

export async function startJavaClaw(port = 8080): Promise<JavaClawServer> {
  // If JavaClaw is already running (e.g., started externally), reuse it
  if (await isPortInUse(port)) {
    console.log(`JavaClaw already running on port ${port}`);
    return {
      baseUrl: `http://localhost:${port}`,
      stop: async () => {},
      getOutput: () => '(externally managed)',
    };
  }

  console.log(`Starting JavaClaw from ${JAVACLAW_DIR}...`);
  const gradlew = resolve(JAVACLAW_DIR, 'gradlew');
  const proc = spawn(gradlew, [':app:bootRun', `--args=--server.port=${port}`], {
    cwd: JAVACLAW_DIR,
    env: { ...process.env },
    stdio: ['ignore', 'pipe', 'pipe'],
  });

  let output = '';
  proc.stdout?.on('data', (d) => { output += d.toString(); });
  proc.stderr?.on('data', (d) => { output += d.toString(); });

  try {
    await waitForHttp(`http://localhost:${port}/chat`, 90_000);
  } catch (e) {
    proc.kill('SIGTERM');
    console.error(`=== JavaClaw output ===\n${output.slice(-3000)}`);
    throw new Error(`Failed to start JavaClaw: ${e}`);
  }

  console.log(`JavaClaw started on port ${port}`);

  return {
    baseUrl: `http://localhost:${port}`,
    stop: async () => {
      if (proc.killed) return;
      proc.kill('SIGTERM');
      await new Promise<void>((resolve) => {
        const timeout = setTimeout(() => { proc.kill('SIGKILL'); resolve(); }, 10_000);
        proc.once('exit', () => { clearTimeout(timeout); resolve(); });
      });
    },
    getOutput: () => output,
  };
}
