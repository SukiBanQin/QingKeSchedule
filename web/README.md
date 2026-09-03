# QingKe iPhone UI Demo

This local Web prototype is the visual sandbox for the QingKe iOS redesign.
It does not replace or modify the production iOS data model or business logic.

The active concept is a complete, session-only interaction demo covering Today,
the week matrix, course creation and editing, semester and period settings,
reminders, data-transfer entry points, and the first-run setup flow. Its visual
system uses a flat academic-terminal layout, condensed typography, restrained
signal colors, acrylic content panels, and a Liquid Glass-inspired bottom
navigation bar. Course editing includes five quick color presets plus a native
custom color picker, and the header uses the project-provided transparent
QingKe logo without background blending.
Reminder settings keep the common presets and also support a custom lead time
from 1 to 180 minutes.

The earlier blue-and-white P3R-inspired concept remains frozen under
`design-archive/p3r-concept/` for later review.

## Local preview

Requires Node.js 22.13 or newer.

```bash
npm install --cache .npm-cache
npm run dev
```

Open `http://localhost:3000/` and view it at an iPhone-sized viewport.

## Validation

```bash
npm run lint
npm test
```
