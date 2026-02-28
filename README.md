# Yield

A web application built with Duct Framework + ClojureScript (shadow-cljs / Reagent).

## Prerequisites

- Java 21+
- [Clojure CLI](https://clojure.org/guides/install_clojure)
- Node.js 20+
- Docker (for PostgreSQL)

## Setup

```sh
npm install
docker compose up -d
```

## Development

### Calva (VSCode)

1. **Start Jack-in**
   - `Ctrl+Alt+C` → `Ctrl+Alt+J`
   - Project type: **deps.edn + shadow-cljs**
   - deps.edn aliases: none
   - shadow-cljs build: **:app**

2. **Start Tailwind CSS watch in a separate terminal**
   ```sh
   npm run css:watch
   ```

3. **Start the Duct server from the CLJ REPL**
   ```clojure
   (require 'dev)
   (dev/go)
   ```

4. **Open browser**
   http://localhost:3000

### REPL Commands

```clojure
(dev/go)      ; start the system
(dev/halt)    ; stop the system
(dev/reset)   ; reload namespaces and restart
```

### Terminal

```sh
# ClojureScript watch + Tailwind CSS watch
npm run dev

# Start Duct server in a separate terminal
clj -M:dev
```

## Build

```sh
npm run build
```

## Tech Stack

- **Backend**: Clojure / [Duct Framework](https://github.com/duct-framework) / Ring / Hiccup
- **Frontend**: ClojureScript / [shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html) / [Reagent](https://reagent-project.github.io/) / [React Flow](https://reactflow.dev/)
- **Styling**: [Tailwind CSS](https://tailwindcss.com/)
- **Database**: PostgreSQL
