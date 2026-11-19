# KING AI Agent and Telegram Automation — Implementation Contract

This document is the acceptance checklist for the unified agent upgrade. It is intentionally explicit so that no requested capability silently disappears between implementation stages.

## Product goal

KING AI should understand a natural-language task, plan bounded steps, select only registered tools, execute them, inspect tool results, recover from safe errors, and report exactly what happened. Telegram is the first connected service, not a separate chatbot.

## Telegram account connection

- Use the existing TDLib account session and the account explicitly selected by the owner.
- Never ask for Telegram passwords, login codes, API hashes, or API tokens in the AI conversation. Authentication remains in the dedicated account UI and TDLib flow.
- Show connection state, active account, and last sync status.
- Read only the chats and message content needed for the approved task.
- Do not access other people's accounts or bypass Telegram restrictions.

## Agent engine

- Add a bounded agent loop: understand request → produce typed plan → validate plan → request approvals → execute allowlisted tools → feed results back to the model → verify outcome → final response.
- Typed tool registry with JSON/schema validation; unknown tools and malformed arguments are rejected.
- Enforce iteration, timeout, output-size, and API-token/cost budgets; cancellation must stop pending work.
- Preserve ordinary chat fallback when the selected model does not support tool calling. Never pretend a tool ran when it did not.
- Keep a local, privacy-conscious activity trace; never log credentials, login codes, API keys, or full private message bodies by default.

## Approval and permission UX

- Add a dedicated Permissions & Automations area in app settings.
- Every tool declares its risk class and exact required capability.
- Read-only operations require an explicit per-feature enablement and visible scope.
- Sending/editing/deleting messages, joining/leaving chats, publishing, forwarding, changing account settings, and sharing data require a fresh, one-action confirmation.
- Confirmation card must show the exact account, chat/recipient, content or change, and consequence, with clear green **Approve and run** and neutral **Cancel** actions.
- Approval is bound to the exact action payload and expires; it cannot be reused for a different action.
- Android runtime permissions (camera, microphone, notifications, etc.) are requested only when the feature is invoked, through Android's permission flow. App-level approval does not bypass Android permission dialogs.
- Owner can revoke feature permissions, cancel queued actions, pause all automations, or disconnect Telegram at any time.
- No hidden camera/microphone use, background UI interaction, or broad accessibility scraping.

## Daily and recurring tasks

- Natural-language schedules such as "every day at 9" are parsed into a preview showing timezone, target account, chat scope, exact steps, and whether any step needs approval.
- Persist schedules locally and use Android WorkManager for deferrable periodic work; explain Android battery/background limits and allow the user to pause/delete schedules.
- Recurring jobs may perform only pre-approved, bounded read-only actions. Any outbound or destructive action must pause and request fresh confirmation; unattended recurring sends are off by default.
- Show run history, next run, last result, and errors. Never claim a task ran unless the worker reports success.

## Telegram tool set (initial scope)

- Read recent messages from explicitly selected chats.
- Summarize unread/recent messages and draft replies.
- Send a message only after showing recipient and exact text for approval.
- Search within the connected account's accessible chats, subject to Telegram API support.
- Later tools (delete/edit/forward/join/leave/pin) must be separately implemented, separately permissioned, and individually confirmed.

## AI quality upgrade

- Improve conversational intent handling, spelling tolerance, Persian colloquial/Finglish and multilingual response matching.
- Use conversation context and memory only within user-selected privacy settings.
- Add structured planning, tool selection, result verification, concise clarification when ambiguity changes the action, and explicit uncertainty.
- Model capability detection: tool calling and vision are enabled only when the configured provider/model actually supports them.
- Vision analysis requires the user to select/share an image and an eligible vision model; no silent upload.

## Delivery and acceptance

- Preserve existing green/black UI, existing chat/history, camera, web preview, and file export.
- Add unit tests for policy checks, approval expiry/payload binding, malformed tool arguments, cancellation, schedule parsing, duplicate runs, and model fallback.
- Verify Telegram login, read-only tasks, approval card, approved send, denied send, schedule pause/cancel, and app restart recovery on a real Android device.
- Run Android build and tests; publish an APK only after a successful build. A merged PR alone is not proof of a working APK.

## Current safety fix in this branch

Telegram auto-reply is now **off by default**. It must not silently send messages merely because an account is connected. The owner must explicitly enable any auto-reply feature in its settings.
