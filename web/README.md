# QingKe iPhone UI Demo

This local Web prototype is the visual sandbox for the QingKe iOS redesign.
It does not replace or modify the production iOS data model or business logic.

The first blue-and-white P3R-inspired concept has been frozen under
`design-archive/p3r-concept/`. The live entry points temporarily re-export that
snapshot while the next visual direction is being planned.

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
