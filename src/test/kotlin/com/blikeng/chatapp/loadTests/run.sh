#!/bin/bash

k6 run --summary-export=results/registerAndLogin.json registerAndLogin.js
k6 run --summary-export=results/roomList.json roomList.js
k6 run --summary-export=results/roomCreation.json roomCreation.js
k6 run --summary-export=results/multiRoom.json multiRoom.js
k6 run --summary-export=results/wsRoom.json wsRoom.js
k6 run --summary-export=results/wsMultiRoom.json wsMultiRoom.js
