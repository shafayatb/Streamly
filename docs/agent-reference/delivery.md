# Streamly Delivery Reference

## Milestones, tasks, and handoffs

The dates below are milestones, not single tasks or single commits. Break each milestone into
small, reviewable tasks with one primary outcome and its own acceptance criteria. Keep the app
buildable after each task. These targets do not permit omitting a brief requirement:

1. **October 1 — foundation:** module/dependency setup; persisted session and onboarding; feed
   cards; navigation to a player destination. Verify each as a separate task on a device.
2. **October 2 — playback:** normal HLS player and controls; lifecycle/navigation behavior; Shorts
   autoplay and player limit. Test each behavior on a device as it arrives.
3. **October 3 — offline and account:** real Media3 download/progress; offline playback/removal;
   profile and sign-out. Test offline playback with connectivity disabled.
4. **October 4 — hardening and submission:** adaptive layout regression, state/error polish, regression tests,
   README, debug APK, and a 2–4 minute demo of all seven reference views.

For each task: implement its single outcome, run the build and relevant automated tests, exercise
the changed journey on a device, then stage a focused commit or a few independently meaningful
commits and stop for the user's diff review. Do not batch onboarding, feed, player, and downloads
into one acceptance gate or commit. Fix failures before marking the task complete. If a device or
required stream is unavailable, record the exact unverified behavior and leave the feature branch
unmerged. After a verified task and the user's approval, commit, merge into `develop`, summarize
evidence, write the next self-contained task in `FRESH_PROMPT.md`,
and stop. Invite the user to start a fresh chat with that file; the chat boundary is for context
management, while the acceptance gate is verification of the focused task.

## Git and AI workflow

- Use **Gitflow**. Keep `main` for verified releases and `develop` for integration. If `develop`
  does not yet exist, create it from `main` before the first implementation task. Develop each
  focused task on `feature/<task-name>` branched from `develop`. After its build, relevant tests,
  and device gate pass and the user approves the staged diff, make focused commits and merge it into
  `develop`; check the integrated build.
  Leave a failing or unverified feature branch unmerged and report why.
- For delivery, create `release/<version>` from `develop`. Perform the full end-to-end device pass,
  regression checks, README/APK/demo review, and any release fixes there. Merge the verified
  release into `main` and back into `develop`; tag the release if requested. If a post-release
  fix is needed, branch `hotfix/<name>` from `main` and merge it into both `main` and `develop`
  after verification. Never force a merge or discard someone else's changes.
- AI-assisted development is a graded requirement. The commit history must show the agent in the
  loop.
- Use a `Co-Authored-By:` trailer for each agent-authored commit as this repo's chosen evidence of
  AI-assisted work; the brief also accepts an agent changelog or prompt logs.
- Write small, focused commits with descriptive messages. Never commit secrets, `local.properties`,
  or the brief PDF.
- The project owner has authorized creating local Gitflow branches. **Commits and merges are not
  pre-authorized:** stage the changes, show `git status` and a `git diff --cached` summary with the
  proposed commit message(s), and stop until the user explicitly approves. Approval covers only
  the commits or merge it names. Push or create a remote pull request only when the user asks.
- Keep the README up to date. It must cover setup, architecture decisions, the AI-assisted workflow
  (setup and prompting approach), and any shortcuts taken and why.
- Keep `FRESH_PROMPT.md` as a single, current handoff prompt. Update it at task boundaries
  with the next goal, acceptance criteria, relevant files/decisions, verification evidence,
  blockers, and the first action for a new chat. Never let it assert tests passed without evidence.

## Deliverables checklist

- [ ] Public or invite-only GitHub repo with a clean history and `AGENTS.md` symlinked per tool
- [ ] README: setup, architecture, AI workflow, shortcuts
- [ ] 2–4 minute demo covering all seven screens, including a real download playing offline
- [ ] Debug APK, or a note to build from source
