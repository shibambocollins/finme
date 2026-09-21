# Git workflow

## Branches

`main` is the deployed branch. Vercel builds from it on push, and CI runs on every push and on
PRs into it.

Feature branches are named for the work:

```text
ui-redesign-transaction-mgmt
email-verification
iteration-3-dashboard-v1
```

## Remote operations

**Pushing, opening PRs and merging are done by the repository owner.** Local branches and local
commits are fine; anything that touches the remote is manual.

## Commit messages

Explain the reasoning, not just the change. The message is the only place a future reader finds
out why.

**Format:** a `type: summary` line, then a blank line, then the reasoning.

```text
fix: Make receipt extraction asynchronous, matching statements

Receipt upload ran the vision AI call inline and returned 201 only once the provider
had answered, so the browser held an open request for the whole round trip. Statements
never behaved that way; receipts were the odd one out.

Upload now returns 202 with a PROCESSING row. Receipt gains a failureReason column:
once the work happens on a thread nobody is waiting on, a thrown exception has nowhere
to go, so the outcome is recorded on the row instead.

File-type validation deliberately stays synchronous - it needs no AI call, and a wrong
file is worth rejecting outright rather than as a failed receipt to go looking for.
```

Types in use: `feat`, `fix`, `refactor`, `test`, `chore`, `docs`.

**What makes a body worth writing:**

- What was wrong, concretely — not "improved handling"
- Why this fix rather than an alternative
- Anything deliberately *not* changed, and why
- A trap the next reader would otherwise fall into

A body is unnecessary when the summary genuinely says everything. That is rarer than it seems.

## Before committing

```bash
cd backend && ./mvnw test
cd frontend && npm run test:e2e && npm run lint
git status              # check what is actually staged
```

Review a broad `git add`. If a filename looks unfamiliar, open it before committing — this is
how `.env` files and keys escape.

## Rewriting history

Occasionally necessary; always worth care.

**Take a backup branch first:**

```bash
git branch backup-before-rewrite
```

**Force-push with a lease, never bare force:**

```bash
git push --force-with-lease origin main
```

`--force-with-lease` aborts if the remote has commits you have not seen.
`--force` overwrites them.

**Verify before you push:**

```bash
git log --oneline origin/main..main   # what you are about to add
git log --oneline main..origin/main   # what you are about to lose
```

A non-empty second list means stop.

**Afterwards, clean up locally:**

```bash
git branch -D backup-before-rewrite
rm -rf .git/refs/original
git reflog expire --expire=now --all
git gc --prune=now
```

::: warning A force-push does not immediately erase commits from GitHub
Rewritten commits stay retrievable by SHA until GitHub garbage-collects, and derived views like
the contributor graph can lag around 24 hours. Verify with the API rather than the UI:

```bash
gh api repos/OWNER/REPO/contributors --jq '.[] | "\(.login): \(.contributions)"'
```
:::

## Merging

Merge in one place. Merging locally *and* through a GitHub PR produces two commits with
identical content and different SHAs, leaving the branches diverged and refusing to
fast-forward — a confusing state with no real conflict behind it.

If it happens and the content is identical:

```bash
git diff origin/main HEAD    # empty output means it is safe
git reset --hard origin/main
```

## Gitignored

`.env` files, build output, `test-results/`, `playwright-report/`, IDE directories, JVM crash
artefacts (`hs_err_pid*.log` — patterns, not filenames, since the PID changes).
