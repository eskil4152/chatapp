# ChatApp

### SonarQube Ratings:

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=eskil4152_chatapp&metric=alert_status&token=058aad476a7cb87615dec0b47edb4ba3920b8684)](https://sonarcloud.io/summary/new_code?id=eskil4152_chatapp)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=eskil4152_chatapp&metric=coverage&token=058aad476a7cb87615dec0b47edb4ba3920b8684)](https://sonarcloud.io/summary/new_code?id=eskil4152_chatapp)

[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=eskil4152_chatapp&metric=reliability_rating&token=058aad476a7cb87615dec0b47edb4ba3920b8684)](https://sonarcloud.io/summary/new_code?id=eskil4152_chatapp)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=eskil4152_chatapp&metric=security_rating&token=058aad476a7cb87615dec0b47edb4ba3920b8684)](https://sonarcloud.io/summary/new_code?id=eskil4152_chatapp)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=eskil4152_chatapp&metric=sqale_rating&token=058aad476a7cb87615dec0b47edb4ba3920b8684)](https://sonarcloud.io/summary/new_code?id=eskil4152_chatapp)

### Description:
A real-time chat application built with Java and Spring Boot. Features a REST API, websockets, optional encryption, persistent storage and user authentication.
Hosted on Azure and connected to GitHub Pages frontend.

### Access:
[Frontend (production)](https://chatapp.blikeng.com)

[GitHub Pages (temporary, will be removed due to SameSite cookie limitations)](https://eskil4152.github.io/chatapp-client)

### Features:
* Database
  * All tables are version-controlled and created via Flyway
  * Relational modeling with foreign keys
  * Saves in batches to reduce the database load
    * Batches flush depending on the time since the last flush, or number of messages in the batch
* Encryption
  * Optional per-room message encryption using AES-256-GCM
  * Stores `ciphertext`, `nonce`, and `keyVersion` per message
  * Uses AAD to bind messages to room/user/message identifiers
* JWT-based Authentication
  * Register and login issue a signed JWT-token stored as a Cookie with 24h expiration
  * Token contains userID as the subject, and username as a claim, for easy fetching after validation
  * Every protected operation validates the token and extracts identification data
  * After token validation, user existence is validated in database, to prevent valid token, but invalid user. I.e. if token is valid, but user has been deleted
* Password Handling
  * Passwords are hashed using BCrypt
  * Password changes work by checking the current password hash with 'old password' from the user hash
* DTO
  * Data is transferred via DTOs to hide entity structure and limit info sent to the client to a minimum
  * Separation between API entities and DB entities
* User Management
  * Duplicate usernames are rejected with 409 Conflict
  * Login with a non-existing username or wrong password results in 401
  * Users can get their own data
  * Users can change their own profile fields (bio, avatar, etc.)
  * User can change password
* Room Roles
  * Room creators are given the OWNER role
  * Room joiners are given a MEMBER role
  * User rooms are stored in user_rooms
  * Rooms are fetched via user_rooms join table (indexed lookup)
* Chat Storage
  * All chats are stored in a database, with room, user and message
  * Chat history is fetched from the database and the unflushed buffer upon joining a room
  * Only `MESSAGE` type is saved, `JOIN` and `LEAVE` are only broadcasted
* WebSocket
  * HandshakeAuthenticator validates JWT from the cookie before any connection gets established
  * Injects userID and username to websocket session
  * Unauthorized handshakes are rejected
* Room sessions management
  * Thread-safe storage with ConcurrentHashMap and CopyOnWriteArraySet
  * Active sessions per room are stored
  * Broadcasting only broadcasts to active sockets in a room
* Testing
  * GitHub Actions runs on pull requests
  * Tests are automatically executed
  * SonarQube analysis automatically updated
* Hosting
  * GitHub Actions builds Docker image
  * Deploys to Azure Container Registry
  * Updates Azure Web App, which looks for ACR image:latest

### Future additions:
- [ ] Allow for friends
  - [ ] Allow for dm
- [ ] Compress long messages before saving
