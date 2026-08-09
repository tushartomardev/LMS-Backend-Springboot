# Google OAuth2 Login - Complete Setup Guide

This guide provides step-by-step instructions to set up and test Google OAuth2 login for your Library Management System.

---

## Table of Contents

1. [Overview](#overview)
2. [Backend Implementation Summary](#backend-implementation-summary)
3. [Frontend Implementation Summary](#frontend-implementation-summary)
4. [Google Cloud Console Setup](#google-cloud-console-setup)
5. [Configuration](#configuration)
6. [Testing the Integration](#testing-the-integration)
7. [Troubleshooting](#troubleshooting)

---

## Overview

This implementation adds Google OAuth2 as an **additional authentication method** alongside your existing email/password login. Users can choose either method to sign in.

### Key Features:
- Existing JWT-based authentication remains intact
- Google users are automatically registered on first login
- User accounts can be linked (Google can be added to existing email/password accounts)
- JWT token is generated after successful Google login
- Redirects to appropriate dashboard based on user role

---

## Backend Implementation Summary

### Files Created/Modified:

1. **`pom.xml`** - Added OAuth2 client dependency
2. **`AuthProvider.java`** - Enum to track authentication provider (LOCAL/GOOGLE)
3. **`User.java`** - Added OAuth fields: `authProvider`, `googleId`, `profileImage`
4. **`CustomOAuth2UserService.java`** - Handles Google user info and registration
5. **`OAuth2UserPrincipal.java`** - Wrapper for OAuth2 user details
6. **`OAuth2LoginSuccessHandler.java`** - Generates JWT and redirects to frontend
7. **`SecurityConfig.java`** - Updated with OAuth2 login configuration
8. **`application.properties`** - OAuth2 Google credentials configuration

### How It Works:

1. User clicks "Continue with Google" button
2. Backend redirects to Google's OAuth consent screen
3. User grants permissions
4. Google redirects back to backend with authorization code
5. Backend exchanges code for user info (email, name, picture)
6. `CustomOAuth2UserService` creates/updates user in database
7. `OAuth2LoginSuccessHandler` generates JWT token
8. User is redirected to frontend with token as query parameter
9. Frontend stores token and fetches complete user profile

---

## Frontend Implementation Summary

### Files Created/Modified:

1. **`Login.jsx`** - Added "Continue with Google" button
2. **`OAuth2Callback.jsx`** - Handles callback from Google, stores token
3. **`App.jsx`** - Added `/oauth2/callback` route
4. **`authThunk.js`** - Already compatible (no changes needed)
5. **`authSlice.js`** - Already has `setCredentials` action (no changes needed)

### Flow:

1. User clicks Google button → Redirects to `http://localhost:5000/oauth2/authorization/google`
2. After Google login → Redirects to `http://localhost:5173/oauth2/callback?token=...&email=...`
3. Callback page extracts token, stores in localStorage
4. Updates Redux store with user credentials
5. Redirects to dashboard based on role

---

## Google Cloud Console Setup

Follow these steps to create OAuth2 credentials in Google Cloud Console:

### Step 1: Create a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click **"Select a project"** → **"New Project"**
3. Enter project name: `Library Management System`
4. Click **"Create"**

### Step 2: Enable Google+ API

1. In the left sidebar, go to **"APIs & Services"** → **"Library"**
2. Search for **"Google+ API"**
3. Click on it and press **"Enable"**

### Step 3: Configure OAuth Consent Screen

1. Go to **"APIs & Services"** → **"OAuth consent screen"**
2. Choose **"External"** → Click **"Create"**
3. Fill in the required fields:
   - **App name**: Library Management System
   - **User support email**: Your email
   - **Developer contact email**: Your email
4. Click **"Save and Continue"**
5. **Scopes**: Click **"Add or Remove Scopes"**
   - Select: `userinfo.email`, `userinfo.profile`, `openid`
   - Click **"Update"** → **"Save and Continue"**
6. **Test Users**: Add your Google email for testing
7. Click **"Save and Continue"**

### Step 4: Create OAuth2 Credentials

1. Go to **"APIs & Services"** → **"Credentials"**
2. Click **"Create Credentials"** → **"OAuth client ID"**
3. Choose **"Web application"**
4. Fill in:
   - **Name**: Library Management OAuth Client
   - **Authorized JavaScript origins**:
     ```
     http://localhost:5173
     http://localhost:5000
     ```
   - **Authorized redirect URIs**:
     ```
     http://localhost:5000/login/oauth2/code/google
     ```
5. Click **"Create"**
6. **Copy the Client ID and Client Secret** (you'll need these next)

---

## Configuration

### Backend Configuration

1. **Option 1: Using Environment Variables (Recommended for Production)**

   Create a `.env` file or set environment variables:
   ```bash
   export GOOGLE_CLIENT_ID=your-client-id-here
   export GOOGLE_CLIENT_SECRET=your-client-secret-here
   ```

2. **Option 2: Direct Configuration (For Development/Testing)**

   Open `src/main/resources/application.properties` and update:
   ```properties
   # Replace YOUR_GOOGLE_CLIENT_ID and YOUR_GOOGLE_CLIENT_SECRET
   spring.security.oauth2.client.registration.google.client-id=YOUR_GOOGLE_CLIENT_ID
   spring.security.oauth2.client.registration.google.client-secret=YOUR_GOOGLE_CLIENT_SECRET
   ```

   **Example:**
   ```properties
   spring.security.oauth2.client.registration.google.client-id=123456789-abc123def456.apps.googleusercontent.com
   spring.security.oauth2.client.registration.google.client-secret=GOCSPX-aBcDeFgHiJkLmNoPqRsTuVwXyZ
   ```

### Frontend Configuration

No additional configuration needed! The OAuth2 callback URL is already set to:
```
http://localhost:5173/oauth2/callback
```

---

## Testing the Integration

### Step 1: Start Backend

```bash
cd Library-Management-System
./mvnw spring-boot:run
```

Backend should start on: `http://localhost:5000`

### Step 2: Start Frontend

```bash
cd library-managment-frontend
npm install  # If not already installed
npm run dev
```

Frontend should start on: `http://localhost:5173`

### Step 3: Test Google Login

1. Open browser and navigate to: `http://localhost:5173/login`
2. You should see the login page with:
   - Email/Password fields (existing login)
   - **"Continue with Google"** button (new)
3. Click **"Continue with Google"**
4. You'll be redirected to Google's consent screen
5. Sign in with your Google account
6. Grant permissions
7. You'll be redirected back to the app
8. You should see a loading screen saying "Completing Google Sign In..."
9. After 1 second, you'll be redirected to:
   - `/admin` dashboard (if you're an admin)
   - `/` dashboard (if you're a regular user)

### Step 4: Verify User in Database

Check your MySQL database:

```sql
SELECT id, full_name, email, auth_provider, google_id, profile_image, role, verified
FROM users
WHERE auth_provider = 'GOOGLE';
```

You should see a new user with:
- `auth_provider` = `GOOGLE`
- `google_id` = Your Google user ID
- `profile_image` = URL to your Google profile picture
- `verified` = `true`

### Step 5: Test JWT Token

After logging in, check browser's localStorage:

```javascript
// Open browser console
console.log(localStorage.getItem('jwt'));
console.log(localStorage.getItem('token'));
```

You should see a JWT token stored.

---

## Troubleshooting

### Issue 1: "Redirect URI Mismatch" Error

**Problem**: Google shows error about redirect URI not matching.

**Solution**:
1. Go to Google Cloud Console → Credentials
2. Edit your OAuth client
3. Ensure **Authorized redirect URIs** includes:
   ```
   http://localhost:5000/login/oauth2/code/google
   ```
4. Save and wait 5 minutes for changes to propagate

### Issue 2: Backend Throws "OAuth2 Client Not Found"

**Problem**: Application fails to start or shows OAuth2 configuration error.

**Solution**:
1. Verify `spring-boot-starter-oauth2-client` is in `pom.xml`
2. Run `./mvnw clean install` to download dependencies
3. Verify `application.properties` has correct Google client ID and secret
4. Restart backend

### Issue 3: User Not Created in Database

**Problem**: Google login succeeds but no user in database.

**Solution**:
1. Check backend logs for errors
2. Verify `CustomOAuth2UserService.java` is being called
3. Check database connection
4. Ensure `spring.jpa.hibernate.ddl-auto=update` in `application.properties`

### Issue 4: Frontend Shows "Loading..." Forever

**Problem**: Callback page doesn't redirect after Google login.

**Solution**:
1. Open browser console and check for errors
2. Verify callback URL has `?token=...` query parameter
3. Check Redux DevTools to see if `setCredentials` action was dispatched
4. Ensure `/oauth2/callback` route is added in `App.jsx`

### Issue 5: CORS Error

**Problem**: Browser console shows CORS error.

**Solution**:
1. Verify `SecurityConfig.java` has frontend URL in CORS configuration:
   ```java
   cfg.setAllowedOrigins(Arrays.asList(
       "http://localhost:5173",
       // ... other origins
   ));
   ```
2. Restart backend after changes

### Issue 6: Token Not Stored

**Problem**: User logged in but can't access protected routes.

**Solution**:
1. Open browser console → Application → Local Storage
2. Check if `jwt` and `token` keys exist
3. Verify `OAuth2Callback.jsx` is storing token:
   ```javascript
   localStorage.setItem('jwt', token);
   localStorage.setItem('token', token);
   ```

---

## Production Deployment Checklist

When deploying to production, update these configurations:

### Backend (`application.properties`)

```properties
# Update redirect URI to production domain
spring.security.oauth2.client.registration.google.redirect-uri=https://yourdomain.com/login/oauth2/code/google
```

### Frontend (`OAuth2LoginSuccessHandler.java`)

```java
// Update frontend URL in OAuth2LoginSuccessHandler.java
String frontendUrl = "https://yourdomain.com/oauth2/callback";
```

### Frontend (`Login.jsx`)

```javascript
// Update backend URL
window.location.href = 'https://api.yourdomain.com/oauth2/authorization/google';
```

### Google Cloud Console

1. Add production URLs to **Authorized JavaScript origins**:
   ```
   https://yourdomain.com
   https://api.yourdomain.com
   ```
2. Add production redirect URI:
   ```
   https://api.yourdomain.com/login/oauth2/code/google
   ```

---

## Security Best Practices

1. **Never commit secrets**: Use environment variables for `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`
2. **Use HTTPS in production**: Google OAuth requires HTTPS for production apps
3. **Rotate secrets periodically**: Change OAuth credentials every 90 days
4. **Limit OAuth scopes**: Only request necessary permissions (email, profile)
5. **Implement CSRF protection**: Already included via Spring Security
6. **Validate tokens**: Backend validates JWT before granting access

---

## API Endpoints Reference

### Google OAuth Endpoints (Handled by Spring Security)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/oauth2/authorization/google` | GET | Initiates Google login (redirects to Google) |
| `/login/oauth2/code/google` | GET | Callback endpoint (handled by Spring Security) |

### Your Application Endpoints (Unchanged)

| Endpoint | Method | Auth Required | Description |
|----------|--------|---------------|-------------|
| `/auth/login` | POST | No | Email/password login |
| `/auth/signup` | POST | No | User registration |
| `/api/users/profile` | GET | Yes (JWT) | Get current user profile |

---

## Additional Resources

- [Spring Security OAuth2 Documentation](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html)
- [Google OAuth2 Documentation](https://developers.google.com/identity/protocols/oauth2)
- [JWT Best Practices](https://datatracker.ietf.org/doc/html/rfc8725)

---

## Support

If you encounter issues:

1. Check backend logs: Look for errors in Spring Boot console
2. Check frontend console: Open browser DevTools → Console
3. Verify database: Check if user table has OAuth fields
4. Check Google Cloud Console: Verify credentials and redirect URIs

---

## Summary

You now have a fully functional Google OAuth2 login system that:

- ✅ Works alongside existing email/password authentication
- ✅ Automatically registers new users via Google
- ✅ Links Google accounts to existing users
- ✅ Generates JWT tokens for API access
- ✅ Handles role-based redirects (admin vs. user)
- ✅ Stores user profile pictures from Google
- ✅ Marks Google users as verified by default

**Next Steps:**
1. Set up Google Cloud credentials
2. Configure `application.properties`
3. Test the login flow
4. Deploy to production with HTTPS

Happy coding! 🚀
