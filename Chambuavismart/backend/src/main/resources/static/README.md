This directory can be used to serve the compiled frontend (Angular) via Spring Boot static resources.

How to (optionally) serve the SPA from the backend jar:

1) Build the Angular app in production mode from the `frontend` directory:

   npm ci
   npm run build:prod

   The build output will be in `frontend/dist/app/` (see `frontend/angular.json`).

2) Copy the contents of `frontend/dist/app/` into this folder before building the backend jar, e.g.:

   xcopy /E /I /Y ..\..\..\..\frontend\dist\app\* .

   (Run the above from this `static` folder in a Windows shell.)

3) Rebuild backend and redeploy the jar:

   mvn -q -DskipTests package

4) After deployment, Spring Boot will serve:
   - `index.html` at `/` (or at your reverse proxy path)
   - JS/CSS assets from this directory

Notes:
- If you deploy the frontend separately (e.g., Nginx or a frontend container), you do NOT need to copy files here. Keep this directory empty.
- Ensure the dist is regenerated whenever frontend code changes to reflect UI updates (e.g., H2H rendering changes).