# How to Create a Branch from Forked Project & Push to GitLab

## Your Current Setup
- **Original Project**: FossifyOrg/Messages (GitHub)
- **Your Current Remote**: origin → https://github.com/FossifyOrg/Messages
- **Your Goal**: Create a branch and push to gitlab.must.edu

---

## Step-by-Step Guide

### Step 1: Fork the Repository (If Not Already Done)
If you haven't forked the project yet:
1. Go to https://github.com/FossifyOrg/Messages
2. Click **Fork** button (top-right)
3. This creates your own copy at: `https://github.com/YOUR_USERNAME/Messages`

---

### Step 2: Update Your Local Remote to Your Fork (Important!)

Currently, your `origin` points to the original repository. You need to change it to your fork:

```bash
# First, add your fork as origin
git remote remove origin
git remote add origin https://github.com/YOUR_USERNAME/Messages.git

# Add the original repository as upstream (to stay updated)
git remote add upstream https://github.com/FossifyOrg/Messages.git

# Verify the remotes
git remote -v
```

**Expected output:**
```
origin    https://github.com/YOUR_USERNAME/Messages.git (fetch)
origin    https://github.com/YOUR_USERNAME/Messages.git (push)
upstream  https://github.com/FossifyOrg/Messages.git (fetch)
upstream  https://github.com/FossifyOrg/Messages.git (push)
```

---

### Step 3: Create Your Feature Branch

Create a new branch for your changes:

```bash
# Create and switch to a new branch
git checkout -b feature/your-branch-name

# Example: git checkout -b feature/messages-improvements
```

**Naming Convention:**
- `feature/feature-name` - for new features
- `bugfix/bug-name` - for bug fixes
- `docs/description` - for documentation
- `refactor/description` - for code improvements

---

### Step 4: Make Your Changes

Make your code changes, then:

```bash
# Check what changed
git status

# Stage your changes
git add .
# Or selectively: git add path/to/file

# Commit your changes
git commit -m "Your clear commit message"

# Example: git commit -m "Add feature: message scheduling improvements"
```

---

### Step 5: Push to GitHub (Your Fork)

```bash
# Push your branch to your fork on GitHub
git push origin feature/your-branch-name

# Example: git push origin feature/messages-improvements
```

---

### Step 6: Add GitLab as Additional Remote

To also push to GitLab:

```bash
# Add GitLab as a new remote
git remote add gitlab https://gitlab.must.edu/YOUR_USERNAME/Messages.git

# Verify all remotes
git remote -v
```

---

### Step 7: Push to Both GitHub and GitLab

```bash
# Push to GitHub (your fork)
git push origin feature/your-branch-name

# Push to GitLab 
git push gitlab feature/your-branch-name

# Or push to both at once (if you set it up)
git push origin feature/your-branch-name && git push gitlab feature/your-branch-name
```

---

### Step 8: Create Pull/Merge Requests

#### On GitHub (To contribute back to FossifyOrg)
1. Go to your fork: https://github.com/YOUR_USERNAME/Messages
2. Click **Pull Requests** → **New Pull Request**
3. Set base to `FossifyOrg/Messages` and compare to your branch
4. Add description and submit

#### On GitLab (To push to gitlab.must.edu)
1. Go to your GitLab project
2. Click **Merge Requests** → **New Merge Request**
3. Select your branch and target branch
4. Add description and submit

---

## Complete Workflow Summary

```bash
# 1. Setup (do once)
git remote remove origin
git remote add origin https://github.com/YOUR_USERNAME/Messages.git
git remote add upstream https://github.com/FossifyOrg/Messages.git
git remote add gitlab https://gitlab.must.edu/YOUR_USERNAME/Messages.git

# 2. Create branch
git checkout -b feature/my-changes

# 3. Make changes and commit
git add .
git commit -m "Description of changes"

# 4. Push to multiple remotes
git push origin feature/my-changes
git push gitlab feature/my-changes

# 5. Create PR/MR on web interfaces
```

---

## Useful Commands

```bash
# List all branches
git branch -a

# Switch to different branch
git checkout branch-name

# Update your branch from upstream
git fetch upstream
git rebase upstream/main

# Delete a local branch
git branch -d branch-name

# Delete a remote branch
git push origin --delete branch-name

# See commit history
git log --oneline

# Undo last commit (keep changes)
git reset --soft HEAD~1
```

---

## Important Notes

✅ **Best Practices:**
- Always create a new branch for each feature/fix
- Keep your main/master branch clean
- Sync with upstream before creating PR/MR
- Write clear, descriptive commit messages
- Create meaningful branch names

⚠️ **Important:**
- Use `upstream` to sync with the original project
- Use `origin` for your fork
- Use `gitlab` for GitLab mirror
- Always pull from upstream before pushing to avoid conflicts

---

## Troubleshooting

### If you accidentally committed to main:
```bash
# Switch to new branch (saves commits)
git branch feature/my-changes
git reset --hard upstream/main
git checkout feature/my-changes
```

### If you have merge conflicts:
```bash
# Abort merge
git merge --abort

# Or resolve conflicts manually and commit
git add .
git commit -m "Resolve merge conflicts"
```

### If you need to update your branch from main:
```bash
git fetch upstream
git rebase upstream/main
git push -f origin feature/my-changes  # Force push (careful!)
```

---

## Your Project Status

**Current:** You're on the main branch with local changes.

**Recommended:**
1. Create a new branch for your changes
2. Commit your current changes to that branch
3. Set up the remotes as described above
4. Push to both GitHub and GitLab

```bash
# Example for your current changes:
git stash  # Or commit to a branch first
git fetch upstream
git rebase upstream/main
git checkout -b feature/setup-documentation
# Then restore your changes and commit
```

