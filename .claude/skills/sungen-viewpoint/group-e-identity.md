# GROUP E: IDENTITY & AUTHENTICATION

> The user creates an account, authenticates their identity, and manages credentials.

Patterns: 15. Login / Logout · 16. Register · 17. Password Management
See `SKILL.md` for the 4 Viewpoints, Shared Checks, and Security Tag Rules.

---

## 15. Login / Logout

**Apply when**: the screen has a login form and/or a logout button.

**Inherits**: Form & Inputs — field-level validation (required, format, maxlength, whitespace, real-time error clear). Generate those once here; this pattern adds the auth-specific rules below.

**Shared checks applied**: XSS/Injection

---

### Tier 1 — @high

**[VP-LOGIC] Auth behavior**

- Enter the correct email + password → login succeeds, redirect to the dashboard/home, a session is created with a valid token
- Click Logout → the session is destroyed server-side, redirect to the Login page, the old token no longer works
- After logout, use the browser Back button to a protected page → redirect to Login (no cached content shown)
- "Remember me" checked → the session persists after closing/reopening the browser (cookie expiry longer than the default session)

**[VP-VAL]**

- Correct email, wrong password → a generic error message "Email or password is incorrect" (does not state which field is wrong, to avoid enumeration)
- An email not in the system → the same generic error as a wrong password (does not reveal "email not registered")
- Wrong password N times in a row (N per config) → the account is temporarily locked, the error states the wait time

**[VP-SEC]**

- Access a protected page while not logged in → redirect to Login, the intended URL is preserved in a query param so post-login redirects correctly
- Login succeeds → a new session token is issued (session fixation prevention), the old pre-login token no longer works
- Password field: the value is not visible in the page source, not logged in the browser console or the network payload

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] Password field: the show/hide toggle works, the eye/eye-off icon changes to the correct state
- [@normal] Submit button: disabled or showing a loading spinner while the API is being called (prevents double submit)
- [@normal] Login failure: the form keeps the entered email, only the password field is cleared
- [@low] Caps Lock warning: when Caps Lock is on → a warning text shows near the password field

**[VP-VAL] Edge cases**

- [@normal] Email with uppercase characters → login succeeds even when entering "User@Email.com" instead of "user@email.com" (case-insensitive)
- [@low] Session timeout while in use → redirect to Login with a toast "Your session has expired"

---

### ⚡ Cross-pattern interactions

- **+ Navigation**: After a successful login → the active menu item reflects the first page the user is redirected to
- **+ Notification**: The API returns 401 while in use → a toast "Your session has expired" shows before redirecting to Login

---

## 16. Register

**Apply when**: the screen has a form to create a new account (email/password) or sign up via SSO (Google, Microsoft, Facebook…).

**Inherits**: Form & Inputs — field-level validation (required, format, maxlength, whitespace, real-time error clear). Generate those once here; this pattern adds the registration-specific rules below.

**Shared checks applied**: XSS/Injection

---

### Tier 1 — @high

**[VP-LOGIC] Registration flow**

- Fill in all valid information, submit → the account is created, the user receives a verification email, redirect to a confirmation page
- Sign up via SSO: click a provider button → redirect to the provider to authorize → redirect back to the app, the account is created/linked automatically, the user enters the app immediately without email verification
- Verification email link → click → the account is activated, the user can log in immediately
- An unverified account tries to access a protected page → blocked, a message asks for verification with a "Resend email" button

**[VP-VAL]**

- An email already in the system → submit is blocked, inline error "This email is already registered"
- A password that is not strong enough (below min length, missing a character type per the rule) → error at the field, no submit
- Confirm password differs from password → inline error "Passwords do not match", no submit
- Wrong email format → inline error at the email field, no submit

**[VP-SEC]**

- Submit without a CSRF token → the server rejects the request, no account is created
- The POST body contains an extra field `role=admin` → the server ignores that field, the account is created with the default role (user)

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] Password strength indicator: updates in real time as the user types, the color changes correctly by level (weak/medium/strong)
- [@normal] Terms & Conditions checkbox: Submit disabled while unchecked, enabled after checking
- [@normal] SSO buttons: the provider logos are correct, the button switches to loading/disabled while redirecting
- [@low] Resend verification email: the button only activates after the cooldown ends, disabled + a countdown timer shows during the wait

**[VP-VAL] Edge cases**

- [@normal] Register with a sub-address email (user+tag@sun-asterisk.com) → treated as a unique email, creation succeeds
- [@low] Browser autofill fills the fields → the form receives the correct values, no conflict with a custom input component

---

### ⚡ Cross-pattern interactions

- **+ Login**: After register + email verification succeeds → the user can log in immediately with the just-created credentials
- **+ Notification**: Register success → a toast "Check your email to complete registration"; resend email → a cooldown toast with a countdown

---

## 17. Password Management

**Apply when**: the screen has a Forgot Password function, a Reset Password via email function, or a Change Password while logged in function.

**Inherits**: Form & Inputs — field-level validation (required, format, maxlength, whitespace, real-time error clear). Generate those once here; this pattern adds the password-specific rules below.

**Shared checks applied**: (no default shared check)

---

### Tier 1 — @high

**[VP-LOGIC] Password flow**

- *Forgot Password:* Enter a registered email → a reset-link email is sent to the inbox, an "email sent" message shows in the UI
- *Reset Password:* Click the link in the email → a set-new-password form opens; submit a valid password → the password is changed, the user can log in immediately with the new password
- *Single-use reset link:* Reuse an already-used link → 400/410, a message "Link already used", no second reset
- *Expired reset link:* Use a link past its expiry → 400/410, a message "Link expired" with a link back to the Forgot Password page
- *Change Password:* Enter the correct current password + a valid new password → the password is changed, all other sessions are invalidated immediately

**[VP-VAL]**

- *Forgot Password:* Enter an email not in the system → the same "email sent" message (does not reveal "email not registered")
- *Reset Password:* A new password that is not strong enough → error at the field, no submit
- *Reset Password:* Confirm password differs → inline error "Passwords do not match"
- *Change Password:* Enter the wrong current password → error "Current password is incorrect", the new password is not saved
- *Change Password:* The new password equals the current password → error "New password must differ from the old one"

**[VP-SEC]**

- A tampered/forged reset token in the URL → the server rejects it, 400 error, the password is unchanged
- The token is invalidated immediately after use: after a successful reset, reusing the same link → rejected
- The Change Password endpoint without authentication → 401 Forbidden, the password is unchanged

---

### Tier 2 — @normal + @low

**[VP-UI] Interface states**

- [@normal] Forgot Password form: only 1 email field + a Submit button + a back-to-Login link, no extra fields
- [@normal] Reset Password form: both password fields are type=password with a show/hide toggle, a strength indicator updates in real time
- [@normal] Change Password form: 3 separate fields (current, new, confirm), fully separated from the profile form
- [@low] Rate limiting: send many reset requests in a row in a short time → the Submit button is on cooldown or a Captcha appears

**[VP-VAL] Edge cases**

- [@normal] Forgot Password sent multiple times for the same email → only the latest link is valid, all older links are invalidated
- [@low] Click a reset link from browser history after it was used → rejected immediately with a "Link already used" message

---

### ⚡ Cross-pattern interactions

- **+ Login**: Reset succeeds → redirect to Login with a toast "Password changed successfully, please log in again"
- **+ Notification**: Change Password succeeds → a success toast in the UI + an email notification "Your account password was just changed" sent to the inbox
