GitHub Pages enablement and verification

There are two supported ways to publish the Angular frontend of this repo to GitHub Pages.

A) Manual Deployment (No GitHub Actions)
- Use this when Actions are blocked by billing lock.
- Prerequisite (recommended): Ensure you are on main and push the latest local changes. This prevents losing Junie’s updates when switching to gh-pages.
  git checkout main
  git add .
  git commit -m "Add manual deployment script for GitHub Pages"  # or your message
  git push origin main
- Steps:
  1) Build the Angular app locally from frontend folder (Chambuavismart/Chambuavismart/frontend):
     ng build --configuration production --base-href "https://chambuavismart.github.io/Chambuavismart/"
     - This creates dist/app with static files.
  2) Create and switch to an orphan gh-pages branch:
     git checkout --orphan gh-pages
  3) Delete everything in this branch:
     git rm -rf .
  4) Copy built files to the repo root (not into a subfolder):
     - Copy the contents of Chambuavismart/Chambuavismart/frontend/dist/app/ into the repository root.
     - Optionally copy index.html to 404.html and create an empty .nojekyll file to improve SPA routing.
  5) Commit and push forcefully:
     git add .
     git commit -m "Deploy Angular app manually"
     git push origin gh-pages --force
  6) Configure GitHub Pages:
     - Repository → Settings → Pages → Branch: gh-pages, Folder: /
     - Save
  7) Your app will be live at: https://chambuavismart.github.io/Chambuavismart/
- Shortcut: You can run scripts/deploy-gh-pages.ps1 which automates the above. Example:
     powershell -ExecutionPolicy Bypass -File scripts/deploy-gh-pages.ps1

B) Recommended (when available): GitHub Actions (Pages environment)
- Already configured via .github/workflows/deploy-frontend.yml.
- Steps (one-time):
  1) In GitHub → Settings → Pages → Build and deployment → Source: GitHub Actions.
  2) Push to main (or manually run the workflow). The Action builds the app and deploys it to the Pages environment.
  3) Open the URL shown by the deployment (or visit https://<your-username>.github.io/<repo-name>/).
- Notes: Base href is set automatically and SPA routing is enabled (404.html, .nojekyll).

C) Alternative: Deploy from branch (gh-pages via Actions)
- Configured via .github/workflows/publish-gh-pages.yml (when Actions are enabled).
- Steps:
  1) Trigger a push to main (or run the workflow manually) to build and publish static files to the gh-pages branch (root).
  2) In GitHub → Settings → Pages → Build and deployment:
     - Source: Deploy from branch
     - Branch: gh-pages
     - Folder: /
     - Save
  3) Wait for the green confirmation banner and open the live link.
- Troubleshooting: If Pages only shows “Verify domains”, wait for the gh-pages branch to be populated by the workflow (it must contain index.html at the root).

Live URL for this repository
- https://chambuavismart.github.io/Chambuavismart/
