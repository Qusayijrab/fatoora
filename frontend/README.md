# Fatoora frontend

Next.js 16 (App Router, Cache Components) + TypeScript + Tailwind + shadcn/ui.

The browser never calls the backend directly. Server Actions and Server Components
call the Spring Boot API with the user's JWT, which is kept in an httpOnly cookie.

```bash
cp .env.example .env.local   # API_URL=http://localhost:8080
npm install
npm run dev                  # http://localhost:3000
```

| Script | What it does |
|---|---|
| `npm run lint` | ESLint |
| `npm run typecheck` | Generates route types, then `tsc --noEmit` |
| `npm run build` | Production build |
| `npm run e2e` | Playwright tests (backend must be running on :8080) |
| `npm run seed:demo` | Creates a demo business with six months of history through the API, and prints its login |
