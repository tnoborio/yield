# Yield

A web application built with [Duct Framework](https://duct-framework.org/) + ClojureScript / [Reagent](https://reagent-project.github.io/).

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

Both CLJ and CLJS REPLs are available. You can evaluate `.clj` and `.cljs` files directly from the editor.

### REPL Commands

```clojure
(dev/go)      ; start the system
(dev/halt)    ; stop the system
(dev/reset)   ; reload namespaces and restart
```

### Terminal

```sh
# Start Duct server with nREPL
clj -M:dev
```

## Production Build

```sh
npm run build
clj -M:duct --main
```

## Tech Stack

- **Backend**: Clojure / [Duct Framework](https://duct-framework.org/) / Ring / Hiccup
- **Frontend**: ClojureScript / [Reagent](https://reagent-project.github.io/) / [React Flow](https://reactflow.dev/)
- **Styling**: [Tailwind CSS](https://tailwindcss.com/)
- **Database**: PostgreSQL
