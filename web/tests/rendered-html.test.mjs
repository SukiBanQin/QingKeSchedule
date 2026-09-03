import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import test from "node:test";

async function render() {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);

  return worker.fetch(
    new Request("http://localhost/", {
      headers: { accept: "text/html" },
    }),
    {
      ASSETS: {
        fetch: async () => new Response("Not found", { status: 404 }),
      },
    },
    {
      waitUntil() {},
      passThroughOnException() {},
    },
  );
}

test("server-renders the terminal-style Today concept", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<title>青课 · 今日终端 UI Concept<\/title>/i);
  assert.match(html, /QINGKE/);
  assert.match(html, /ACADEMIC TERMINAL/);
  assert.match(html, /SCHEDULE :\/\/ TODAY/);
  assert.match(html, /ACTIVE MISSION/);
  assert.match(html, /交互设计基础/);
  assert.match(html, /课程序列/);
  assert.match(html, /aria-current="page"/);
  assert.doesNotMatch(html, /codex-preview|react-loading-skeleton/i);
});

test("implements the iPhone layout, glass navigation, and reduced-motion fallback", async () => {
  const [page, css, archivePage, archiveCss, packageJson] = await Promise.all([
    readFile(new URL("../app/page.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/globals.css", import.meta.url), "utf8"),
    readFile(
      new URL("../design-archive/p3r-concept/page.tsx", import.meta.url),
      "utf8",
    ),
    readFile(
      new URL("../design-archive/p3r-concept/globals.css", import.meta.url),
      "utf8",
    ),
    readFile(new URL("../package.json", import.meta.url), "utf8"),
  ]);

  assert.match(page, /data-screen="today"/);
  assert.match(page, /className="liquid-nav"/);
  assert.match(page, /className="floating-add"/);
  assert.match(page, /aria-live="polite"/);
  assert.doesNotMatch(page, /design-archive\/p3r-concept/);
  assert.match(css, /width:\s*min\(430px, 100%\)/);
  assert.match(css, /backdrop-filter:\s*blur\(28px\) saturate\(160%\)/);
  assert.match(css, /@media \(max-width: 520px\)/);
  assert.match(css, /prefers-contrast:\s*more/);
  assert.match(css, /prefers-reduced-motion:\s*reduce/);

  assert.match(archivePage, /QINGKE \/ DAILY LOG/);
  assert.match(archiveCss, /--blue-bright:/);
  assert.doesNotMatch(packageJson, /react-loading-skeleton/);

  await assert.rejects(
    access(new URL("../app/_sites-preview/SkeletonPreview.tsx", import.meta.url)),
  );
});
