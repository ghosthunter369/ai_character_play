# Project Review (Quick Index)

If you couldn't find the previous Chinese report (`项目审查报告.md`), this file is an English index.

## What was reviewed
- Backend security: auth checks, IDOR risk, CORS, secret management.
- Config hygiene: hardcoded credentials and environment portability.

## What was fixed in code
1. `GET /chatHistory/exportChatHistoryTxt`
   - now enforces login user context;
   - non-admin users cannot export other users' history.
2. `GET /user/get/vo`
   - no longer indirectly depends on admin-only endpoint.
3. CORS
   - replaced wildcard origin-with-credentials behavior with configurable allowlist (`app.cors.allowed-origin-patterns`).
4. Secrets in config
   - replaced hardcoded DB/API/voice credentials with environment variable placeholders.

## Files changed in that review fix
- `src/main/java/com/character/controller/ChatHistoryController.java`
- `src/main/java/com/character/controller/UserController.java`
- `src/main/java/com/character/config/CorsConfig.java`
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `项目审查报告.md`

## Note
Changes are committed on the current branch. If your remote repo UI still shows no change, confirm:
- you're viewing the same branch;
- you've fetched/pulled latest commits;
- the PR branch is correct.
