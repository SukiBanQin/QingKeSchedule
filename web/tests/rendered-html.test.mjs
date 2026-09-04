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
  assert.match(html, /<title>青课 · 终端课表 UI Demo<\/title>/i);
  assert.match(html, /QINGKE/);
  assert.match(html, /ACADEMIC TERMINAL/);
  assert.match(html, /SCHEDULE :\/\/ TODAY/);
  assert.match(html, /CURRENT CLASS/);
  assert.match(html, /进度更新于 15:02/);
  assert.match(html, /交互设计基础/);
  assert.match(html, /课程序列/);
  assert.match(html, /aria-current="page"/);
  assert.doesNotMatch(html, /codex-preview|react-loading-skeleton/i);
});

test("implements the complete interactive terminal-style demo", async () => {
  const [page, css, packageJson] = await Promise.all([
    readFile(new URL("../app/page.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/globals.css", import.meta.url), "utf8"),
    readFile(new URL("../package.json", import.meta.url), "utf8"),
  ]);

  assert.match(page, /data-screen=\{activeView\}/);
  assert.match(page, /className="liquid-nav"/);
  assert.match(page, /className="floating-add"/);
  assert.equal(page.match(/className="floating-add"/g)?.length, 1);
  assert.match(page, /activeView !== "settings"/);
  assert.match(page, /aria-live="polite"/);
  assert.match(page, /data-view="schedule"/);
  assert.match(page, /data-view="settings"/);
  assert.match(page, /data-overlay="course-editor"/);
  assert.match(page, /data-overlay="onboarding"/);
  assert.match(page, /role="switch"/);
  assert.match(page, /repeatLabels/);
  assert.match(page, /IMPORT \/\//);
  assert.match(page, /type="color"/);
  assert.match(page, /className="brand-logo"/);
  assert.match(page, /customLeadActive/);
  assert.match(page, /自定义提前提醒分钟数/);
  assert.match(page, /type="number"/);
  assert.match(page, /min="1"/);
  assert.match(page, /max="180"/);
  assert.equal(
    page.match(/className="delete-course-button"/g)?.length,
    1,
    "course editor must expose one delete-course action",
  );
  assert.match(css, /width:\s*min\(430px, 100%\)/);
  assert.match(css, /backdrop-filter:\s*blur\(28px\) saturate\(160%\)/);
  assert.match(css, /\.week-matrix\s*\{/);
  assert.match(css, /\.overlay-screen\s*\{/);
  assert.match(css, /\.onboarding-actions\s*\{/);
  assert.match(css, /\.custom-lead-control\s*\{/);
  assert.match(css, /@media \(max-width: 520px\)/);
  assert.match(css, /prefers-contrast:\s*more/);
  assert.match(css, /prefers-reduced-motion:\s*reduce/);

  const palette = page.match(/const coursePalette = \[([^\]]+)\]/)?.[1] ?? "";
  assert.equal(palette.match(/#[0-9a-f]{6}/gi)?.length, 5);

  const floatingAddRule = css.match(/\.floating-add\s*\{([^}]+)\}/s)?.[1] ?? "";
  assert.match(floatingAddRule, /position:\s*absolute/);
  assert.match(floatingAddRule, /right:\s*22px/);
  assert.match(floatingAddRule, /bottom:\s*112px/);
  assert.match(css, /url\("\/qingke-logo-lockup\.png"\)/);
  assert.doesNotMatch(css, /mix-blend-mode:\s*multiply/);

  assert.doesNotMatch(packageJson, /react-loading-skeleton/);

  const logo = await readFile(
    new URL("../public/qingke-logo-lockup.png", import.meta.url),
  );
  assert.deepEqual([...logo.subarray(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);
  assert.equal(logo[25], 6, "logo asset should preserve RGBA transparency");

  await assert.rejects(
    access(new URL("../app/_sites-preview/SkeletonPreview.tsx", import.meta.url)),
  );
});

test("shares the schemaVersion 1 custom course color contract with iOS", async () => {
  const [schemaText, fixtureText] = await Promise.all([
    readFile(new URL("../../ios/Shared/schedule-data.schema.json", import.meta.url), "utf8"),
    readFile(new URL("../../ios/Shared/fixtures/valid/complete-schedule.json", import.meta.url), "utf8"),
  ]);
  const schema = JSON.parse(schemaText);
  const fixture = JSON.parse(fixtureText);
  const colorRule = schema.$defs.course.properties.color;
  const pattern = new RegExp(colorRule.pattern);

  assert.equal(schema.properties.schemaVersion.const, 1);
  assert.equal(colorRule.enum, undefined);
  assert.equal(colorRule.pattern, "^#[0-9A-Fa-f]{6}$");
  assert.ok(fixture.courses.some((course) => course.color === "#12ABEF"));
  assert.ok(fixture.courses.every((course) => pattern.test(course.color)));
  assert.equal(pattern.test("blue"), false);
});
