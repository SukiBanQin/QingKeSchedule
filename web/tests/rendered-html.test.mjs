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

test("server-renders the archived QingKe concept", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<title>青课 · iPhone UI Concept<\/title>/i);
  assert.match(html, /QINGKE \/ DAILY LOG/);
  assert.match(html, /交互设计基础/);
  assert.match(html, /今日/);
  assert.match(html, /课表/);
  assert.match(html, /设置/);
  assert.doesNotMatch(html, /codex-preview|react-loading-skeleton/i);
});

test("keeps the P3R direction isolated as a replaceable archive", async () => {
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

  assert.match(page, /design-archive\/p3r-concept\/page/);
  assert.match(css, /design-archive\/p3r-concept\/globals\.css/);
  assert.match(archivePage, /data-screen="today"/);
  assert.match(archivePage, /data-screen="schedule"/);
  assert.match(archivePage, /data-screen="settings"/);
  assert.match(archivePage, /data-screen="editor"/);
  assert.match(archiveCss, /width:\s*min\(430px, 100%\)/);
  assert.match(archiveCss, /prefers-reduced-motion:\s*reduce/);
  assert.doesNotMatch(packageJson, /react-loading-skeleton/);

  await assert.rejects(
    access(new URL("../app/_sites-preview/SkeletonPreview.tsx", import.meta.url)),
  );
});
