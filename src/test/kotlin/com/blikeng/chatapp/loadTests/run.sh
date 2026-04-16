#!/bin/bash
set -e

USERS=${USERS:-100}
RUN_ID=$(date +%s%3N)
USERS_FILE="./results/users.json"
SCRIPTS=(login roomList roomCreation multiRoom wsRoom wsMultiRoom)

echo "Seeding ${USERS} users (RUN_ID=${RUN_ID})..."
k6 run -e USERS=$USERS -e RUN_ID=$RUN_ID seed.js
echo "Seed complete: ${USERS_FILE}"
echo "--------------------------------"

echo "Running all load tests with ${USERS} VUs..."
echo "================================"

for SCRIPT in "${SCRIPTS[@]}"; do
    echo "Running ${SCRIPT}.js..."
    k6 run -e USERS=$USERS -e USERS_FILE=$USERS_FILE \
        --summary-export="results/${SCRIPT}.json" \
        "${SCRIPT}.js"
    echo "Done: ${SCRIPT}"
    echo "--------------------------------"
done

echo "All tests complete."
