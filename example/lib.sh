# Shared helpers for the example steps. Source, don't execute.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ "${EXAMPLE_REST:-0}" = "1" ]; then
    export COMPOSE_FILE=docker-compose.yml:docker-compose.rest.yml
fi

PSQL="docker compose exec -T postgres psql -U postgres -d postgres -v ON_ERROR_STOP=1 -X -q"

say() { echo "== $* =="; }

fail() {
    echo "EXAMPLE FAIL: $*" >&2
    exit 1
}

assert_eq() {
    if [ "$2" != "$3" ]; then
        echo "EXAMPLE FAIL: $1" >&2
        echo "--- expected:" >&2; echo "$2" >&2
        echo "--- got:" >&2; echo "$3" >&2
        exit 1
    fi
}

wait_for() {
    local label=$1 sql=$2 expected=$3
    for _ in $(seq 1 60); do
        if [ "$($PSQL -tA -c "$sql")" = "$expected" ]; then
            echo "   ... $label"
            return 0
        fi
        sleep 2
    done
    fail "timeout waiting for: $label"
}
