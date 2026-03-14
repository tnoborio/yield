#!/bin/bash
set -e

if [ -n "$DATABASE_URL" ]; then
  exec clj -M:duct --main --jdbc-url "$DATABASE_URL"
else
  exec clj -M:duct --main
fi
