# ChatApp

It supports chat rooms, private DMs, role-based access control, invite-based room membership, friend requests, optional AES-256-GCM message encryption, 
and horizontally scalable WebSocket messaging using Redis Pub/Sub and RabbitMQ.
## [Access](https://chatapp.blikeng.com)

---

### SonarCloud Analysis

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=eskil4152_chatapp&metric=alert_status&token=058aad476a7cb87615dec0b47edb4ba3920b8684)](https://sonarcloud.io/summary/new_code?id=eskil4152_chatapp)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=eskil4152_chatapp&metric=reliability_rating&token=058aad476a7cb87615dec0b47edb4ba3920b8684)](https://sonarcloud.io/summary/new_code?id=eskil4152_chatapp)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=eskil4152_chatapp&metric=security_rating&token=058aad476a7cb87615dec0b47edb4ba3920b8684)](https://sonarcloud.io/summary/new_code?id=eskil4152_chatapp)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=eskil4152_chatapp&metric=sqale_rating&token=058aad476a7cb87615dec0b47edb4ba3920b8684)](https://sonarcloud.io/summary/new_code?id=eskil4152_chatapp)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=eskil4152_chatapp&metric=coverage&token=058aad476a7cb87615dec0b47edb4ba3920b8684)](https://sonarcloud.io/summary/new_code?id=eskil4152_chatapp)

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Features](#features)
  - [Authentication & Security](#authentication--security)
  - [Rate Limiting](#rate-limiting)
  - [WebSocket & Real-Time Messaging](#websocket--real-time-messaging)
  - [Distributed Messaging Pipeline](#distributed-messaging-pipeline)
  - [Message Encryption](#message-encryption)
  - [Friends & Direct Messages](#friends--direct-messages)
  - [Database & Persistence](#database--persistence)
  - [Room & User Management](#room--user-management)
  - [Invitations & Notifications](#invitations--notifications)
  - [Presence Tracking](#presence-tracking)
  - [Buffered Message Persistence](#buffered-message-persistence)
- [API Overview](#api-overview)
- [Deployment](#deployment)
- [CI/CD Pipeline](#cicd-pipeline)
- [Testing](#testing)
- [Load Testing & Multi-Instance Testing](#load-testing-and-multi-instance-testing)
- [Privacy](#privacy)
- [License](#license)

---

## Tech Stack

| Layer             | Technology                                |
|-------------------|-------------------------------------------|
| Language          | Kotlin                                    |
| Framework         | Spring Boot                               |
| Real-time         | WebSockets (Spring WebSocket)             |
| Auth              | JWT (HTTP-only cookie, `SameSite=Strict`) |
| Encryption        | AES-256-GCM                               |
| Password Hashing  | BCrypt                                    |
| Database          | PostgreSQL (schema managed via Flyway)    |
| Messaging         | RabbitMQ                                  |
| Distributed State | Redis                                     |
| Containerization  | Docker                                    |
| Cloud             | Azure Web App + Azure Container Registry  |
| CI/CD             | GitHub Actions                            |
| Rate Limiting     | Bucket4j                                  |
| Code Quality      | SonarCloud                                |

---

## Architecture

![Architecture](docs/ChatApp-high.svg)

The system separates real-time delivery (Redis Pub/Sub + WebSockets) from durable persistence (RabbitMQ + PostgreSQL), 
allowing the chat service to scale horizontally without coupling WebSocket throughput to database writes.

A more detailed architecture diagram is available [here](docs/ChatApp-low.svg).

---

## Features

### Authentication & Security

**JWT Authentication**
- Registration and login issue a signed JWT stored as an `HttpOnly`, `SameSite=Strict` cookie with a 24-hour expiration.
- The token encodes `userID` as the subject and `username` as a claim for efficient identity extraction.
- A dedicated `JwtAuthFilter` is responsible for all authentication checks.
- After token validation, user existence is re-verified in the database. A valid token for a deleted user results in a `400 Bad Request`.

**Password Handling**
- Passwords are hashed with **BCrypt**, plaintext passwords are never persisted.
- Password changes require the current password to be verified against the stored hash before a new hash is saved.
- A minimum length is enforced for both usernames and passwords during registration and password changes.

**WebSocket Handshake Authentication**
- An `AuthHandshakeInterceptor` intercepts every WebSocket request and validates the JWT from the cookie before any connection is established.
- `userID` and `username` are injected into the WebSocket session after validation.
- Room membership is verified before a user can join a WebSocket room session. Unauthorized or non-member connections are rejected.

---

### Rate Limiting

Rate limiting is applied at both the HTTP and WebSocket layers using **Bucket4j** token buckets.

**HTTP** — limits are per IP address:
| Endpoint                  | Limit          |
|---------------------------|----------------|
| `POST /api/login`         | 5 / minute     |
| `POST /api/register`      | 10 / minute    |
| `PUT /api/user/edit`      | 2 / minute     |
| `PATCH /api/user/edit/password` | 2 / minute |
| All other `/api/**` paths | 60 / minute    |

Exceeded requests return `429 Too Many Requests`.

**WebSocket**
- Chat messages are limited to **10 messages per minute** per user.
- Excess messages receive a structured WebSocket error response instead of being broadcast.

---

### WebSocket & Real-Time Messaging

- Messages are broadcast in real-time to all active sessions in a room.
- Room session state is managed with thread-safe data structures: `ConcurrentHashMap` for room-to-sessions mapping and `CopyOnWriteArraySet` for per-room session sets.
- Only `MESSAGE` events are persisted to the database. `JOIN` and `LEAVE` are broadcast-only.
- On room join, history is assembled from both the database and the current Redis message buffer to ensure no messages are missed between persistence batches.

**Session Liveness (PING/TTL)**
- Clients send a `PING` event at regular intervals. The server resets a per-session last-seen timestamp and responds with `pong`.
- A background sweep runs every **30 seconds** and closes any session that has not sent a `PING` in the last **60 seconds**.
- Closing a stale session triggers the normal disconnect flow — presence counters are decremented and friends/room members are notified — keeping online status accurate even when clients crash without a clean disconnect.

**WebSocket Events**

| Direction       | Type              | Description                                              |
|-----------------|-------------------|----------------------------------------------------------|
| Client → Server | `MESSAGE`         | Send a chat message to a room                            |
| Client → Server | `JOIN`            | Join a room WebSocket session                            |
| Client → Server | `LEAVE`           | Leave a room WebSocket session                           |
| Client → Server | `PING`            | Keep-alive heartbeat; resets session TTL                 |
| Server → Client | `MESSAGE`         | Chat message broadcast                                   |
| Server → Client | `JOIN` / `LEAVE`  | User join/leave announcement broadcast to room           |
| Server → Client | `ROOM_MEMBERS`    | Full member snapshot sent once to the joining session    |
| Server → Client | `ROOM_PRESENCE`   | Lightweight online/offline update for a room member      |
| Server → Client | `FRIEND_PRESENCE` | Friend online/offline status update                      |
| Server → Client | `ROOM_ACTION`     | Kick or ban notification sent to the target user         |
| Server → Client | `ROOM_DELETED`    | Room deletion notification sent to all members           |
| Server → Client | `pong`            | Response to client `PING`                                |
| Server → Client | `ERROR`           | Structured error with HTTP status code and message       |

---

### Distributed Messaging Pipeline

To support horizontal scaling and decouple real-time messaging from persistence, the application uses a Redis + RabbitMQ pipeline.

**Redis Pub/Sub**
- All WebSocket broadcasts are published to Redis channels (`room:{roomId}`).
- Targeted user notifications (kick, ban, room deletion, friend presence) are published to `user:{userId}` channels.
- Each instance subscribes to both channel patterns and routes messages to the appropriate local WebSocket sessions.
- This ensures messages reach users connected to different application instances.

**RabbitMQ Message Queue**
- Chat messages are asynchronously persisted through RabbitMQ.
- When a message is sent:
  1. The message is published to RabbitMQ.
  2. A background consumer batches messages and writes them to the database.
- Manual acknowledgements ensure messages are retried if persistence fails.

This architecture separates:
- **real-time delivery**
- **durable persistence**

---

### Message Encryption

Encryption is **optional** and configured per room.

- Rooms can enable **AES-256-GCM** encryption for all messages.
- Each encrypted message stores three fields: `ciphertext`, `nonce`, and `keyVersion`.
- **Additional Authenticated Data (AAD)** binds each message to its specific room, user, and message identifier.
- `keyVersion` allows for future key rotation without losing the ability to decrypt historical messages.

---

### Friends & Direct Messages

- Users can send and receive **friend requests**, which must be accepted before a friendship is established.
- Friendships are stored using deterministic identifiers to ensure consistency and prevent duplicates.
- Friends can open private DM conversations backed by the same WebSocket and persistence pipeline as rooms.
- Friend status is intentionally never exposed to non-friends. Looking up a non-friend returns the same response as a non-existent user, preventing user enumeration.
- `FRIEND_PRESENCE` notifications are published to each friend’s `user:{friendId}` Redis channel, ensuring delivery across all instances.
- On connect, a presence snapshot is sent to the session with the current online status of all friends.

---

### Database & Persistence

**Schema Management with Flyway**
- All tables are version-controlled through Flyway migration scripts, applied automatically on startup.

**Relational Modeling**
- Foreign keys link `users` and `rooms` to `user_rooms` and `chats`.
- The `user_rooms` join table is indexed for efficient lookups when fetching a user's rooms.
- Duplicate room memberships are prevented via database constraints and repository checks.

**DTOs**
- All API responses use Data Transfer Objects to decouple the API surface from the internal entity structure and minimize data exposure to clients.

---

### Room & User Management

**Rooms**
- Room membership is managed through an **invite-based system**. Users cannot join rooms directly without an invitation.
- Invites can be:
  - direct (sent to a specific user)
  - open (shared links with limited usage)
- Rooms support **role-based access control**:
  - `OWNER` — full control (delete room, manage roles, invites)
  - `ADMIN` — manage users and invites
  - `MEMBER` — participate in messaging
- Users can be promoted and demoted within a room based on role permissions.
- Room owners and admins can kick or ban members. Banned users cannot rejoin.
- Kick and ban actions notify the target user via real-time WebSocket events (`ROOM_ACTION`).
- When a room is deleted, all members receive a `ROOM_DELETED` WebSocket notification.
- Notifications are delivered via Redis pub/sub after transaction commit using `@TransactionalEventListener`.

**Users**
- Duplicate usernames are rejected with `409 Conflict`.
- Login with an unknown username or incorrect password returns `401 Unauthorized`.
- Users can retrieve and update their own profile fields (bio, avatar, etc.) and change their password.
- Account deletion preserves chat history — messages are reassigned to a sentinel `[deleted]` user rather than removed.

---

### Invitations & Notifications

- The system uses a unified **invitation model** for both room access and friend relationships.
- Invitations have expiration timestamps and can be accepted or rejected.
- Open room invites support configurable usage limits.
- All invitation-related actions trigger real-time notifications:
  - `INVITE_RECEIVED`
  - `INVITE_ACCEPTED`
- Notifications are delivered through Redis Pub/Sub and routed to active WebSocket sessions across instances.

---
### Presence Tracking

User presence is tracked using Redis.

- Each active user session increments a Redis counter.
- When sessions close, the counter is decremented.
- A user is considered online when the counter is greater than zero.
- Stale presence keys from previous server runs are cleared on startup.

Room presence events are broadcast to all members of a room when a user connects or disconnects.
On room join, a full `ROOM_MEMBERS` snapshot is sent to the joining session. Subsequent presence updates use a lightweight `ROOM_PRESENCE` event containing only the user ID and online status.

On WebSocket connect, a `FRIEND_PRESENCE` snapshot is sent to the session with the current online status of all friends, so clients have accurate presence state immediately without waiting for a change event.

---

### Buffered Message Persistence

To reduce database write pressure and support high message throughput, message persistence is handled asynchronously.

**Redis Message Buffer**
- Messages are temporarily stored in Redis lists (`chat.peek.{roomId}`).
- This buffer allows newly joined users to retrieve messages that have not yet been persisted.

**RabbitMQ Batch Persistence**
- Messages are also published to a RabbitMQ queue.
- A background consumer batches messages and writes them to PostgreSQL.

**Flush Strategy**
Messages are persisted when either:

- A batch reaches a configured size threshold
- A periodic flush interval is reached

This design ensures:

- consistent message history
- reduced database write amplification
- resilience to transient database failures

---

## API Overview
| Method | Endpoint                        | Description                                                                  |
|--------|---------------------------------|------------------------------------------------------------------------------|
| POST   | /api/register                   | Register                                                                     |
| POST   | /api/login                      | Login                                                                        |
| POST   | /api/logout                     | Log out                                                                      |
| GET    | /api/auth                       | Check auth status                                                            |
| GET    | /api/rooms                      | List all rooms                                                               |
| POST   | /api/rooms/make                 | Create room                                                                  |
| PUT    | /api/rooms/edit                 | Edit room                                                                    |
| DELETE | /api/rooms/leave                | Leave room                                                                   |
| DELETE | /api/rooms/delete               | Delete room                                                                  |
| POST   | /api/rooms/action               | Kick or ban a user                                                           |
| DELETE | /api/rooms/unban                | Unban a user                                                                 |
| GET    | /api/rooms/{roomId}/bans        | Get banned users for a room                                                  |
| GET    | /api/rooms/{roomId}/members     | Get members and their roles for a room                                       |
| POST   | /api/rooms/changeRole           | Promote or demote a room member                                              |
| POST   | /api/rooms/dm                   | Make or get private room                                                     |
| GET    | /api/user                       | Get user info                                                                |
| PUT    | /api/user/edit                  | Edit user profile                                                            |
| PATCH  | /api/user/edit/password         | Edit password                                                                |
| DELETE | /api/user/delete                | Delete account                                                               |
| GET    | /api/chats/{roomId}             | Get message history (paginated: `page`, `size` — allowed sizes: 25, 50, 100) |
| GET    | /api/friends                    | Get all friends                                                              |
| DELETE | /api/friends/remove             | Remove friend                                                                |
| GET    | /api/friends/{userId}           | Get friend info                                                              |
| GET    | /api/invites/pending            | Get pending invites received by the current user                             |
| GET    | /api/invites/outgoing           | Get outgoing pending invites sent by the current user                        |
| POST   | /api/invites/friend             | Send a friend request                                                        |
| POST   | /api/invites/room               | Send a room invite to a specific user                                        |
| POST   | /api/invites/open               | Create an open room invite (returns invite ID)                               |
| POST   | /api/invites/respond            | Accept or reject an invite                                                   |
| WS     | /ws                             | WebSocket endpoint                                                           |

---

## Deployment

The application runs as a Docker container on Azure Web App, with Docker images built and tagged using the
Git commit SHA and stored in Azure Container Registry.

The CI/CD pipeline handles building, pushing, and redeploying the container automatically on every merge to `main`.

The server uses **graceful shutdown** with a 30-second drain window, allowing in-flight requests and active WebSocket sessions to close cleanly before the process exits.

---

## CI/CD Pipeline

Two GitHub Actions workflows run on the repository:

**`testing.yml`** — triggers on every pull request and merge to main:
- Executes the full Maven test suite
- Updates SonarCloud analysis

**`azure.yml`** — triggers on every merge to `main`:
- Builds the Docker image
- Pushes to Azure Container Registry
- Azure Web App picks up the new image and redeploys

```
Pull Request
    │
    ▼
Run Tests + SonarCloud
    │
    │ (merge to main only if all tests pass)
    ▼
Build Docker Image
    │
    ▼
Push to Azure Container Registry with SHA tag
    │
    ▼
Azure Web App redeploys with SHA tag
```

---

## Testing

The project has comprehensive test coverage including unit tests and full **end-to-end tests**, covering every component and the full
user flow, from registering to adding friends and chatting.

---

## Load testing and multi-instance testing

| Test                      | Scenario                                                                  | Result                                                                         |
|---------------------------|---------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| Multi-instance validation | 2 local application instances with shared PostgreSQL, Redis, and RabbitMQ | Passed                                                                         |
| HTTP load test            | Register + login + authenticated access                                   | 0 failed checks, avg 51 ms, p95 90 ms                                          |
| HTTP load test            | Room listing                                                              | 0 failed checks, ~88–92 req/s, p95 177–186 ms                                  |
| WebSocket load test       | 25 users, 1 shared room                                                   | 211 successful WebSocket sessions, 100% successful login/cookie/upgrade checks |
| WebSocket load test       | 30 users, 5 rooms                                                         | 267 successful WebSocket sessions, 100% successful login/cookie/upgrade checks |

These tests were performed locally on a single development machine. The results are therefore most useful as validation of concurrency 
behavior, distributed setup correctness, and baseline performance rather than production-capacity benchmarks.

---

## Privacy

This application is a personal hobby project.

- Data stored: username, hashed passwords, messages, friendships, and optional user profile fields.
- Data is not shared with third parties, but is stored on third-party infrastructure (e.g., cloud hosting, databases) for operation.
- Users may delete their account at any time.
- This service is provided as-is, with no guarantees of long-term storage, availability, or production-grade data protection.

---

## License

[MIT](LICENSE)