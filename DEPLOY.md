# 🚀 Deployment Guide — QR Attendance System

> **Critical:** Camera/QR scanning requires HTTPS in browsers.
> All options below provide HTTPS automatically.

---

## Option A — Render.com (Recommended, Free)

Your `render.yaml` is pre-configured. Just:

1. Push this project to a GitHub repo
2. Go to https://render.com → **New → Blueprint**
3. Connect your GitHub repo — Render reads `render.yaml` automatically
4. Set these **Environment Variables** in Render dashboard:

| Variable | Value |
|---|---|
| `RESEND_API_KEY` | Your key from resend.com |
| `RESEND_FROM_EMAIL` | your@verifieddomain.com |
| `ATTENDANCE_EMAIL_TO` | where reports go |
| `ADMIN_ACCESS_ID` | your secure admin password |

5. Deploy → get a free `*.onrender.com` HTTPS URL ✅

**Persistent disk** is already configured in `render.yaml` — attendance data survives redeploys.

---

## Option B — Railway.app (Free tier)

1. Push to GitHub
2. https://railway.app → New Project → Deploy from GitHub
3. Add the same 4 environment variables above
4. Also add: `ATTENDANCE_EXCEL_PATH` = `/data/attendance_records.xlsx`
5. Add a **Volume** mounted at `/data` in Railway's storage settings
6. Railway auto-detects Maven and deploys ✅

---

## Option C — Docker on any VPS (DigitalOcean / AWS EC2 / etc.)

### Prerequisites
```bash
# Install Docker & Docker Compose on your VPS
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
```

### Deploy
```bash
# 1. Copy project to your server
scp -r . user@your-server-ip:/opt/qr-attendance

# 2. SSH into server
ssh user@your-server-ip
cd /opt/qr-attendance

# 3. Create your .env file
cp .env.example .env
nano .env   # fill in your values

# 4. Build and start
docker compose up -d --build

# App is running on port 8080
```

### Add HTTPS with Nginx + Let's Encrypt
```bash
# Install Nginx & Certbot
sudo apt install nginx certbot python3-certbot-nginx -y

# Create Nginx config
sudo tee /etc/nginx/sites-available/qr-attendance << 'EOF'
server {
    listen 80;
    server_name yourdomain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF

sudo ln -s /etc/nginx/sites-available/qr-attendance /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# Get free SSL certificate
sudo certbot --nginx -d yourdomain.com
```

Done! Your app is live at `https://yourdomain.com` ✅

---

## Option D — Run JAR directly on VPS (No Docker)

```bash
# Install Java 17
sudo apt update && sudo apt install openjdk-17-jdk -y

# Build
./mvnw clean package -DskipTests

# Run with env vars
export RESEND_API_KEY=your_key
export ADMIN_ACCESS_ID=your_admin_id
export ATTENDANCE_EXCEL_PATH=/opt/data/attendance_records.xlsx
mkdir -p /opt/data

java -jar target/qr-attendance-1.0.0.jar

# To keep running after logout:
nohup java -jar target/qr-attendance-1.0.0.jar > app.log 2>&1 &
```

Then set up Nginx + SSL as shown in Option C.

---

## Environment Variables Reference

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | Server port |
| `ATTENDANCE_EXCEL_PATH` | `attendance_records.xlsx` | Path to Excel file (use `/data/...` on server) |
| `RESEND_API_KEY` | *(required for email)* | API key from resend.com |
| `RESEND_FROM_EMAIL` | `onboarding@resend.dev` | Sender email (must be verified in Resend) |
| `ATTENDANCE_EMAIL_TO` | `sr9941101@gmail.com` | Where daily/weekly reports are sent |
| `ADMIN_ACCESS_ID` | `ADMIN123` | **Change this!** Password for admin dashboard |
| `GEO_LAT` | `13.0827` | Office latitude for geo-fencing |
| `GEO_LON` | `80.2707` | Office longitude for geo-fencing |
| `GEO_RADIUS` | `1000` | Allowed radius in meters |
| `GEO_ENFORCE` | `true` | Block attendance if outside radius |

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Camera not working | You MUST use HTTPS — camera is blocked on plain HTTP |
| Attendance data lost on redeploy | Set `ATTENDANCE_EXCEL_PATH` to a persistent volume path |
| Build fails | Ensure Java 17+ (`java -version`) |
| Port conflict | Change `PORT` env var |
| Email not sending | Check `RESEND_API_KEY` and verify sender domain in Resend |
