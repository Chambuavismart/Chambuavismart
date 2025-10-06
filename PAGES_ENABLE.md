GitHub Pages enablement and verification

There are two supported ways to publish the Angular frontend of this repo to GitHub Pages.

A) Recommended: GitHub Actions (Pages environment)
- Already configured via .github/workflows/deploy-frontend.yml.
- Steps (one-time):
  1) In GitHub → Settings → Pages → Build and deployment → Source: GitHub Actions.
  2) Push to main (or manually run the workflow). The Action builds the app and deploys it to the Pages environment.
  3) Open the URL shown by the deployment (or visit https://<your-username>.github.io/<repo-name>/).
- Notes: Base href is set automatically and SPA routing is enabled (404.html, .nojekyll).

B) Alternative: Deploy from branch (gh-pages)
- Also configured via .github/workflows/publish-gh-pages.yml.
- Steps:
  1) Trigger a push to main (or run the workflow manually) to build and publish static files to the gh-pages branch (root).
  2) In GitHub → Settings → Pages → Build and deployment:
     - Source: Deploy from branch
     - Branch: gh-pages
     - Folder: /
     - Save
  3) Wait for the green confirmation banner and open the live link.
- Troubleshooting: If Pages only shows “Verify domains”, wait for the gh-pages branch to be populated by the workflow (it must contain index.html at the root).

Manual local build (if you need to publish artifacts yourself)
- From the frontend folder (Chambuavismart/Chambuavismart/frontend):
  ng build --configuration production --base-href "/<repo-name>/"
- The build output is under frontend/dist/app. It must include index.html.
- You can serve it locally or publish its contents using any static hosting.

Live URL for this repository
- https://chambuavismart.github.io/Chambuavismart/
