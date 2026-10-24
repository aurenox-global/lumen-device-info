#!/usr/bin/env node
// Uso: node scripts/preview.mjs <width> <height> <out.png>
// Mide overflow horizontal, captura consola y hace screenshot de página completa.

import { spawn } from "node:child_process";
import { writeFile } from "node:fs/promises";

const [widthArg, heightArg, out] = process.argv.slice(2);
const width = Number(widthArg || 390);
const height = Number(heightArg || 844);
const target = out || `/tmp/lumen-${width}.png`;
const port = 9334;
const url = "http://127.0.0.1:8899/";

const brave = spawn("/Applications/Brave Browser.app/Contents/MacOS/Brave Browser", [
  "--headless=new",
  `--remote-debugging-port=${port}`,
  "--no-first-run",
  "--no-default-browser-check",
  "--disable-gpu",
  "--hide-scrollbars",
  "--user-data-dir=/tmp/lumen-brave-profile",
  "about:blank",
], { stdio: "ignore" });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function waitEndpoint() {
  for (let i = 0; i < 60; i++) {
    try {
      const r = await fetch(`http://127.0.0.1:${port}/json/version`);
      if (r.ok) return await r.json();
    } catch {}
    await sleep(250);
  }
  throw new Error("Brave no abrió el puerto de depuración");
}

let ws;
try {
  await waitEndpoint();
  const res = await fetch(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(url)}`, { method: "PUT" });
  const page = await res.json();

  ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => {
    ws.onopen = resolve;
    ws.onerror = reject;
  });

  let id = 0;
  const pending = new Map();
  const consoleErrors = [];

  ws.onmessage = (event) => {
    const msg = JSON.parse(event.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      msg.error ? reject(new Error(msg.error.message)) : resolve(msg.result);
    }
    if (msg.method === "Runtime.exceptionThrown") {
      consoleErrors.push(msg.params.exceptionDetails?.text || "exception");
    }
    if (msg.method === "Log.entryAdded" && msg.params.entry.level === "error") {
      consoleErrors.push(msg.params.entry.text);
    }
  };

  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const mid = ++id;
    pending.set(mid, { resolve, reject });
    ws.send(JSON.stringify({ id: mid, method, params }));
  });

  await send("Page.enable");
  await send("Runtime.enable");
  await send("Log.enable");
  await send("Emulation.setDeviceMetricsOverride", {
    width, height, deviceScaleFactor: 2, mobile: false,
  });
  await send("Page.navigate", { url });
  await sleep(1800);

  const expression = `(() => {
    const vw = window.innerWidth;
    const de = document.documentElement;
    const offenders = [...document.querySelectorAll('body *')]
      .map(el => { const r = el.getBoundingClientRect(); return { tag: el.tagName.toLowerCase(), cls: (el.className && el.className.baseVal !== undefined ? el.className.baseVal : el.className) || '', right: Math.round(r.right), width: Math.round(r.width) }; })
      .filter(o => o.right > vw + 1 && o.width > 0)
      .slice(0, 12);
    return JSON.stringify({
      innerWidth: vw,
      docScrollWidth: de.scrollWidth,
      bodyScrollWidth: document.body.scrollWidth,
      docHeight: Math.max(de.scrollHeight, document.body.scrollHeight),
      offenders,
      title: document.title,
      h1: document.querySelector('h1')?.innerText,
      cards: document.querySelectorAll('.feature').length,
      images: [...document.images].map(i => ({ src: i.getAttribute('src'), ok: i.complete && i.naturalWidth > 0 })),
      theme: de.getAttribute('data-theme')
    });
  })()`;

  const measured = await send("Runtime.evaluate", { expression, returnByValue: true });
  const data = JSON.parse(measured.result.value);
  console.log(JSON.stringify(data, null, 2));
  console.log("console errors:", consoleErrors.length ? consoleErrors : "none");

  const fullHeight = Math.min(data.docHeight + 40, 16000);
  await send("Emulation.setDeviceMetricsOverride", {
    width, height: fullHeight, deviceScaleFactor: 2, mobile: false,
  });
  await sleep(500);
  const shot = await send("Page.captureScreenshot", { format: "png", captureBeyondViewport: true });
  await writeFile(target, Buffer.from(shot.data, "base64"));
  console.log("saved:", target, `${width}x${fullHeight}`);
} finally {
  try { ws?.close(); } catch {}
  brave.kill("SIGTERM");
}
