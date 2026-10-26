#!/bin/sh
set -eu

if [ -n "${NEW_RELIC_LICENSE_KEY:-}" ]; then
  exec java -javaagent:/opt/newrelic/newrelic.jar "$@"
fi

if [ "${NEW_RELIC_REQUIRED:-false}" = "true" ]; then
  printf '%s\n' 'New Relic instrumentation is required but NEW_RELIC_LICENSE_KEY is unavailable.' >&2
  exit 1
fi

# Local/test runs stay independent of New Relic credentials and never contact ingestion.
exec java "$@"
