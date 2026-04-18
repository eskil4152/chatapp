#!/bin/bash
# Cold-start load test runner.
# Kills any running server, starts a fresh instance with the load profile,
# waits for it to be ready, runs the full test suite, then shuts it down.
# Usage: USERS=500 ./run-cold.sh
set -e

USERS=${USERS:-100}
RUN_ID=$(date +%s%3N)
USERS_FILE="./results/users.json"
COOKIES_FILE="./results/cookies.json"
PROJECT_ROOT="$(cd ../../../../../../../.. && pwd)"
HEALTH_URL="http://localhost:5050/actuator/health"

# ==========================
# Reset DB
# ==========================
echo "Killing any existing server..."
pkill -f 'spring-boot:run' || true
sleep 2

echo "Resetting database..."
docker compose -f "$PROJECT_ROOT/docker-compose.test.yml" down -v
docker compose -f "$PROJECT_ROOT/docker-compose.test.yml" up -d

echo "Waiting for postgres to be ready..."
until docker exec chatapp-postgres-test pg_isready -U test > /dev/null 2>&1; do
    sleep 1
done
echo "Database ready."
echo "--------------------------------"

# ==========================
# Start server
# ==========================
echo "Starting server with load profile..."
cd "$PROJECT_ROOT"
mvn spring-boot:run -Dspring-boot.run.profiles=load > /tmp/chatapp-server.log 2>&1 &
SERVER_PID=$!
echo "Server PID: $SERVER_PID"

echo "Waiting for server to be ready..."
until curl -sf "$HEALTH_URL" > /dev/null 2>&1; do
    if ! kill -0 $SERVER_PID 2>/dev/null; then
        echo "Server process died. Check /tmp/chatapp-server.log"
        exit 1
    fi
    sleep 2
done
echo "Server ready."
echo "--------------------------------"

cd "$OLDPWD"

# ==========================
# Run tests
# ==========================
trap "echo 'Stopping server...'; kill $SERVER_PID 2>/dev/null || true" EXIT

echo "Seeding ${USERS} users (RUN_ID=${RUN_ID})..."
k6 run -e USERS=$USERS -e RUN_ID=$RUN_ID seed.js
echo "Seed complete: ${USERS_FILE}"
echo "--------------------------------"

echo "Running login load test..."
k6 run -e USERS=$USERS -e USERS_FILE=$USERS_FILE \
    --summary-export="results/login.json" \
    login.js
echo "Done: login"
echo "--------------------------------"

echo "Logging in all users and caching cookies..."
k6 run -e USERS_FILE=$USERS_FILE auth.js
echo "Auth complete: ${COOKIES_FILE}"
echo "--------------------------------"

SCRIPTS=(roomCreation multiRoom roomList wsRoom wsMultiRoom wsSync roomHistory)

echo "Running remaining load tests with ${USERS} VUs..."
echo "================================"

for SCRIPT in "${SCRIPTS[@]}"; do
    echo "Running ${SCRIPT}.js..."
    k6 run -e USERS=$USERS -e COOKIES_FILE=$COOKIES_FILE \
        --summary-export="results/${SCRIPT}.json" \
        "${SCRIPT}.js"
    echo "Done: ${SCRIPT}"
    echo "--------------------------------"
done

echo "All tests complete."
