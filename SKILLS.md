# TestGenesis Design & Frontend Standards

This project follows the "Genesis-Dark" aesthetic—a premium, high-density, and developer-centric dark theme. All UI contributions must adhere to these structural and stylistic guidelines.

## Project Architecture & Modules
TestGenesis is structured as a multi-language monorepo:

### 1. Specification (`/specification`)
Contains the source `.proto` files that define the Universal Agent Protocol (UAP). This is the "Source of Truth" for all communications.

### 2. CMS Hub (`/implementation/server/testgenesis-cms`)
A **Quarkus** application that serves as the central brain.
- **Hub**: Manages gRPC connections from agents.
- **CMS**: Provides the web dashboard for test/payload management.
- **Persistence**: Uses Hibernate Panache with PostgreSQL for storing test data.

### 3. Agent Implementations (`/implementation/client`)
Specialized workers that connect to the Hub to execute tasks:
- **`testgenesis-client-node`**: Core TypeScript SDK for building Node.js agents.
- **`side-agent`**: Executes Selenium-IDE (`.side`) projects.
- **`puppeteer-replay-agent`**: Runs Chrome DevTools Recorder JSON exports and provides Puppeteer-to-Selenium translation services.

---

## Frontend Aesthetic: "Genesis-Dark"

## 1. Core Aesthetic & Variables
- **Background**: `#010409` (Primary), `#0d1117` (Surface/Cards)
- **Accents**: `#58a6ff` (Primary), `#238636` (Success/Action)
- **Typography**: `-apple-system, BlinkMacSystemFont, "Segoe UI", ...`
- **Transitions**: `0.2s` all-around for interactive elements.

## 2. Layout Patterns

### Clearfix & Float Areas
The primary method for headers and list items is a float-based layout using `.title-area` and `.action-area` wrapped in a `.clearfix`.
```html
<section class="clearfix">
    <div class="title-area">
        <h2>Dashboard</h2>
    </div>
    <div class="action-area">
        <a href="/new" class="btn btn-primary">New Item</a>
    </div>
</section>
```

### Statistical Boxes
Used for high-level system metrics. Should be wrapped in a `<section>` for proper vertical rhythm.
```html
<section>
    <h3>System Overview</h3>
    <div class="stats-container">
        <div class="stat-box">
            <span class="label">Active Sessions</span>
            <span class="value">5</span>
        </div>
    </div>
</section>
```

## 3. Form Standards
Forms must be semantic and data-driven. **Never use `<p>` tags for wrapping fields.**

### Fieldset & Legend
Group related inputs using fieldsets for better accessibility and hierarchy.
```html
<fieldset>
    <legend>Metadata</legend>
    <div class="field">
        <label>Name</label>
        <input type="text" name="name">
    </div>
</fieldset>
```

### Display Modes
- **Default**: Inline-block (good for table actions).
- **Primary Forms**: Use `main > form` or `.full-width-form` to expand fieldsets to 100% width.

## 4. Component Standards

### Status Badges
Consistent, color-coded badges for real-time tracking:
- `.status-completed`: Success/Green (`#3fb950`)
- `.status-running`: Active/Blue (`#58a6ff`)
- `.status-pending` / `.status-waiting`: Warning/Yellow (`#d29922`)
- `.status-failed`: Error/Red (`#f85149`)

### Buttons
- `.btn`: Standard gray secondary button.
- `.btn-primary`: Vibrant green call-to-action.
- `.btn-error`: Red destructive action.
- `.btn.small`: Compact version for dense tables.

### Telemetry Console
Used for live agent logs.
```html
<pre class="console" id="telemetry-console"></pre>
```
*Utilities: `.console` provides a fixed-height scrollable window with monospace font.*

## 5. Table Management
- **Action Columns**: Use `text-align: right` and `white-space: nowrap` for the last column.
- **Shrink-to-Fit**: The last column is set to `width: 1%` to ensure it only takes the space required by buttons.
- **Actions Utility**: Wrap buttons in `.actions` to manage spacing (`margin-left: 0.5rem`).

## 6. CSS Utilities
- `.d-none`: Forceful `display: none`.
- `.truncate`: Ellipsis for long text in cards.
- `.muted`: Small, grayed-out text (`#8b949e`).
- `.grid`: Responsive grid for cards (`auto-fill, minmax(220px, 1fr)`).
