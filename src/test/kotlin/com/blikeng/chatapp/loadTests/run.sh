#!/bin/bash
# Requires the server running with --spring.profiles.active=load
# (disables rate limiting and uses the test DB/Redis).
set -e

USERS=${USERS:-250}
RUN_ID=$(date +%s%3N)
USERS_FILE="./results/users.json"
COOKIES_FILE="./results/cookies.json"

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

SCRIPTS=(roomCreation wsSync roomList multiRoom wsMultiRoom roomHistory wsRoom)

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
