# QR Attendance System

A complete Spring Boot web application for marking employee attendance by scanning QR codes via mobile or laptop camera. All records are automatically saved to an Excel file using Apache POI — **no database required**.

---

## ✅ Features

| Feature | Details |
|---|---|
| QR Scanning | Real-time camera scan via ZXing JS |
| Manual Entry | Fallback text input for Employee ID |
| Auto-save to Excel | Apache POI — creates file if missing, appends records |
| Duplicate Prevention | Same employee cannot mark twice on the same day |
| Rate Limiting | 10-second cooldown between scans |
| Admin Dashboard | KPI cards, filterable attendance table, live refresh |
| Admin Access Gate | Dashboard hidden behind an Admin ID prompt; nav link only shows once unlocked |
| Edit/Delete Attendance | Admins can correct a candidate's status/time/date or remove a record before sending reports |
| Excel Download | One-click download of the attendance report |
| QR Code Generation | Server-side ZXing generates PNG QR codes per employee |
| Employee Directory | Card and table view with individual QR download |
| Responsive UI | Bootstrap 5 — works on mobile and desktop |

---

## 📁 Project Structure

```
qr-attendance/
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── java/com/attendance/
        │   ├── QrAttendanceApplication.java       # Entry point
        │   ├── controller/
        │   │   ├── AttendanceController.java       # /api/attendance/*
        │   │   ├── EmployeeController.java         # /api/employees/*
        │   │   └── AdminController.java            # /api/admin/verify
        │   ├── model/
        │   │   ├── Employee.java
        │   │   ├── AttendanceRecord.java
        │   │   └── ApiResponse.java
        │   └── service/
        │       ├── EmployeeService.java            # In-memory employee list
        │       ├── ExcelService.java               # Apache POI read/write/update/delete
        │       └── QrCodeService.java              # ZXing QR generation
        └── resources/
            ├── application.properties
            └── static/
                ├── index.html                      # Home / landing page
                ├── css/
                │   └── style.css
                ├── js/
                │   └── admin-gate.js               # Shared dashboard-nav visibility helper
                └── pages/
                    ├── scanner.html                # QR camera scanner
                    ├── dashboard.html              # Admin dashboard (gated + editable)
                    └── employees.html              # Employee directory + QR codes
```

---

## 🛠️ Prerequisites

| Requirement | Version |
|---|---|
| Java | 17 or higher |
| Maven | 3.6+ |
| Modern browser | Chrome / Firefox / Edge / Safari |

> **No database installation needed.** The app stores everything in `attendance_records.xlsx`.

---

## 🚀 Setup & Run

### 1. Clone / unzip the project

```bash
cd qr-attendance
```

### 2. Build the project

```bash
mvn clean package -DskipTests
```

### 3. Run the application

```bash
mvn spring-boot:run
```

Or run the JAR directly:

```bash
java -jar target/qr-attendance-1.0.0.jar
```

### 4. Open in browser

```
http://localhost:8080
```

---

## 🌐 API Endpoints

### Attendance

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/attendance/mark` | Mark attendance (body: `{"employeeId":"EMP001"}`) |
| GET | `/api/attendance/today` | Get today's attendance |
| GET | `/api/attendance/all` | Get all attendance records |
| PUT | `/api/attendance/update` | Update a record's status/time/date/department (admin) — body: `{"employeeId","originalDate","date","time","status","department"}` |
| DELETE | `/api/attendance/delete?employeeId=&date=` | Delete a record (admin) |
| GET | `/api/attendance/download` | Download Excel report |

### Admin

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/admin/verify` | Verify the Admin ID entered on the dashboard gate — body: `{"id":"..."}` |

### Employees

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/employees` | List all employees |
| GET | `/api/employees/{id}` | Get single employee |
| GET | `/api/employees/{id}/qrcode` | Get QR code PNG |

---

## 👥 Default Employee List

| ID | Name | Department |
|---|---|---|
| EMP001 | Arjun Sharma | Engineering |
| EMP002 | Priya Nair | Human Resources |
| EMP003 | Rahul Mehta | Finance |
| EMP004 | Sneha Krishnan | Marketing |
| EMP005 | Vikram Iyer | Engineering |
| EMP006 | Anjali Desai | Sales |
| EMP007 | Karthik Reddy | Operations |
| EMP008 | Meera Pillai | Engineering |
| EMP009 | Suresh Babu | Finance |
| EMP010 | Divya Chandran | Human Resources |

To add more employees, edit `EmployeeService.java` and add entries to the `EMPLOYEES` map.

---

## 📱 How to Mark Attendance

1. Open `http://localhost:8080/pages/employees.html`
2. Find your name → click **View QR Code** → download your personal QR PNG
3. Open `http://localhost:8080/pages/scanner.html` on a second device or the same browser
4. Click **Start Camera** and point it at your QR code
5. Attendance is marked automatically with a success beep and on-screen confirmation

---

## 📊 Excel File Format

The generated `attendance_records.xlsx` has these columns:

| Employee ID | Employee Name | Department | Date | Time | Status |
|---|---|---|---|---|---|
| EMP001 | Arjun Sharma | Engineering | 06-06-2025 | 09:15:32 | Present |

The file is created automatically in the project root on first attendance marking.

---

## 🔐 Admin Access (Dashboard Gate)

The **Dashboard** link in the navbar is hidden by default, and the dashboard page itself shows a lock screen until the correct **Admin ID** is entered.

- Default Admin ID: **`ADMIN123`** (change it — see Configuration below)
- On success, the dashboard unlocks and the Dashboard nav link appears on every page for the rest of the browser tab's session (stored in `sessionStorage`, cleared on tab close or by clicking **Lock**)
- This is a lightweight UI gate for keeping the dashboard out of casual users' way — it is **not** a substitute for real authentication (no hashing, no rate limiting, no per-user accounts). For production use with sensitive data, replace it with Spring Security and a real user store.

### Editing attendance before sending a report

Every row in the dashboard table now has an **Edit** button. Clicking it opens a modal where an admin can:

- Correct the **date**, **time**, or **status** (Present / Late / Half Day / On Leave / Absent)
- Update the **department**
- **Delete** the record entirely

Changes are written directly to `attendance_records.xlsx`, so they're reflected immediately in both the **Download Report** button and the **Send Daily/Weekly Report** email actions — letting an admin fix mistakes before either goes out.

---

## ⚙️ Configuration

Edit `src/main/resources/application.properties`:

```properties
server.port=8080                              # Change port if needed
attendance.excel.path=attendance_records.xlsx # Excel file location
admin.access.id=ADMIN123                      # Admin ID required to unlock the dashboard
```

On Railway/Render, set `ADMIN_ACCESS_ID` as an environment variable instead of editing the file directly.

---

## 🔒 Business Rules

- **Duplicate prevention** — one entry per employee per day (checked against Excel)
- **Scan cooldown** — 10-second server-side cooldown per employee ID to prevent accidental double-scans
- **Unknown IDs** — QR codes not matching a known employee ID are rejected with an error

---

## 🛟 Troubleshooting

| Issue | Fix |
|---|---|
| Camera not working | Use HTTPS or `localhost`; ensure browser camera permission is granted |
| Port already in use | Change `server.port` in `application.properties` |
| Excel file locked | Close the file in Excel before running the app |
| Build fails | Ensure Java 17+ is active (`java -version`) |
