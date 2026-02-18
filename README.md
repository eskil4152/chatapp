# ChatApp

### SonarQube Ratings:

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=eskil4152_chatapp&metric=alert_status&token=058aad476a7cb87615dec0b47edb4ba3920b8684)](https://sonarcloud.io/summary/new_code?id=eskil4152_chatapp)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=eskil4152_chatapp&metric=coverage&token=058aad476a7cb87615dec0b47edb4ba3920b8684)](https://sonarcloud.io/summary/new_code?id=eskil4152_chatapp)

[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=eskil4152_chatapp&metric=reliability_rating&token=058aad476a7cb87615dec0b47edb4ba3920b8684)](https://sonarcloud.io/summary/new_code?id=eskil4152_chatapp)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=eskil4152_chatapp&metric=security_rating&token=058aad476a7cb87615dec0b47edb4ba3920b8684)](https://sonarcloud.io/summary/new_code?id=eskil4152_chatapp)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=eskil4152_chatapp&metric=sqale_rating&token=058aad476a7cb87615dec0b47edb4ba3920b8684)](https://sonarcloud.io/summary/new_code?id=eskil4152_chatapp)

### Features:
* Database
  * All tables are version-controlled and created via Flyway
  * Relational modeling with foreign keys
* JWT-based Authentication
  * Register and login issues a signed JWT-token stored as a Cookie with 24h expiration
  * Token contains userID as subject and username as claim, for easy fetching after validation
  * Every protected operation validates the token and extracts identification data
  * After token validation, user data is validated in database, to prevent valid token, but invalid user. I.e. if token is valid, but user has been deleted
* Password Handling
  * Passwords are hashed using BCrypt
  * Password changes work by checking current password hash with 'old password' from user hash
* DTO
  * Data is transferred via DTOs to hide entity structure, and limit info sent to client to minimum
  * Separation between API entities and DB entities
* User Management
  * Duplicate usernames are rejected with 409 Conflict
  * Login with non-existing username or wrong password results in 401
  * User can get their own data
  * User can change their own profile fields (bio, avatar etc)
  * User can change password
* Room Roles
  * Room creators are given OWNER role
  * Room joiners are given MEMBER role
  * User rooms are stored in user_rooms
  * Fetching all rooms gets all rooms for user from user_rooms (no need exhaust database)
* Chat Storage
  * All chats are stored in database, with room, user and message
  * Chat history is fetched from database upon join
  * Only MESSAGE type is saved, JOIN and LEAVE are only broadcasted
* WebSocket
  * HandshakeAuthenticator validates JWT from cookie before connection gets established
  * Injects userID and username to websocket session
  * Unauthorized handshakes are rejected
* Room sessions management
  * Thread-safe storage with ConcurrentHashMap and CopyOnWriteArraySet
  * Active sessions per room are stored
  * Broadcasting only broadcats to active sockets in a room
* CI
  * GitHub Actions runs on pull requests
  * Tests automatically executed
  * SonarQube analysis automatically updated

### TODO:
- [ ] allow for friends
- [ ] allow for dm
- [ ] Add login options
  - [ ] Google
  - [ ] Microsoft
- [ ] Fix security vulnerabilities
  - [ ] Allowed origins
  - [ ] CSRF
  - [ ] Chat Encryption
