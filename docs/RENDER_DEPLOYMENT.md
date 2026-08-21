# Render Deployment

## CORS

Set the production frontend origin in Render so browser requests from Netlify are allowed without allowing every origin.

Required Render environment variable:

```properties
CORS_ALLOWED_ORIGINS=https://your-netlify-site.netlify.app
```

If you prefer to keep one frontend URL variable, `FRONTEND_URL` is also used as the CORS origin when `CORS_ALLOWED_ORIGINS` is not set. For multiple frontend origins, use a comma-separated value:

```properties
CORS_ALLOWED_ORIGINS=https://your-netlify-site.netlify.app,https://your-custom-domain.com
```

Local development origins `http://localhost:5173` and `http://localhost:3000` are always allowed by the backend security configuration.
