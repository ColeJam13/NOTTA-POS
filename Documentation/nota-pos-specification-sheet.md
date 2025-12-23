# Nota-POS: Software Requirements Specification (SRS)
## MVP - Version 1.0

**Project Name:** Nota-POS (Point of Sale System)  
**Version:** 1.0 - MVP  
**Target Completion Date:** January 26, 2025  
**Document Author:** Cole Jamison (CJ)  
**Organization:** Zip Code Wilmington - Capstone Project  
**Last Updated:** December 23, 2024

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Project Overview](#2-project-overview)
3. [Scope Definition](#3-scope-definition)
4. [System Architecture](#4-system-architecture)
5. [Data Model](#5-data-model)
6. [Functional Requirements](#6-functional-requirements)
7. [Non-Functional Requirements](#7-non-functional-requirements)
8. [User Stories & Acceptance Criteria](#8-user-stories--acceptance-criteria)
9. [Technical Stack](#9-technical-stack)
10. [Development Timeline](#10-development-timeline)
11. [Testing Strategy](#11-testing-strategy)
12. [Deployment Plan](#12-deployment-plan)
13. [Success Metrics](#13-success-metrics)
14. [Risks & Mitigation](#14-risks--mitigation)
15. [Future Enhancements](#15-future-enhancements)
16. [Glossary](#16-glossary)

---

## 1. Executive Summary

### 1.1 Purpose
Nota-POS is a restaurant point of sale system built from the ground up by someone with 20 years of industry experience. The MVP focuses on core POS functionality with one signature differentiating feature: a customizable order delay buffer that prevents kitchen ticket errors.

### 1.2 Problem Statement
Current POS systems (Toast, Square, Clover) are built by software developers who have never worked a Friday night dinner rush. They're designed to extract maximum revenue from restaurant owners while ignoring the actual pain points of servers, cooks, and managers on the ground.

### 1.3 Solution
A clean, uncluttered POS system that includes only features servers actually use during service, with thoughtful workflow design based on real restaurant operations experience.

### 1.4 Target Users
- **Primary:** Servers and bartenders at small, independent restaurants
- **Secondary:** Kitchen staff, floor managers
- **Restaurant Type:** Sit-down restaurants, bars, independent establishments

### 1.5 MVP Goals
By January 26, 2025, deliver a working POS system that:
- Manages tables and their status
- Takes orders with items and modifiers
- Implements 10-second delay buffer before sending orders to kitchen
- Tracks order flow from creation → kitchen → ready → payment
- Provides clean, intuitive UI with retro gaming aesthetic

---

## 2. Project Overview

### 2.1 Background
This project evolved from a Zip Code Wilmington capstone assignment (Ones to Manys database project) into a passion project leveraging the developer's extensive restaurant industry experience.

### 2.2 Vision
**Long-term:** Build a portfolio-quality POS system that could be presented to established companies to drive real change in how these tools are built.

**Short-term MVP:** Prove that someone with industry knowledge can build better tools by delivering core functionality that works reliably and feels natural to restaurant staff.

### 2.3 Key Differentiators
1. **Order Delay Buffer** - Prevents multiple ticket revisions from reaching kitchen
2. **Built by Industry Veteran** - Every design decision based on 20 years of real experience
3. **Zero Bloat** - Only essential features, no unnecessary complexity
4. **Clean UX** - 8-bit retro aesthetic with neon purple accents, optimized for touch tablets

---

## 3. Scope Definition

### 3.1 In Scope (MVP - Must Have)

**Core Functionality:**
- Table management (view status, assign servers, seat guests)
- Order creation and management
- Menu browsing and item selection
- Modifier application (protein choices, sides, temperatures, add-ons)
- Order delay buffer (10-second default, customizable)
- Item status tracking (pending → fired → preparing → ready → delivered)
- Payment processing (simple tracking, no integrated payment gateway)
- Order history and financial summaries

**User Interface:**
- Floor map view
- Active tables view
- Active orders view
- Order entry/menu selection
- Payment processing screen
- Financials summary

**Technical Requirements:**
- RESTful API backend
- Relational database with proper relationships
- Responsive touch-optimized frontend
- Real-time status updates (simulated for MVP)

### 3.2 Out of Scope (Phase 2+)

**Future Features:**
- User authentication/login system
- Kitchen Display System (separate screen for cooks)
- Server challenges and gamification
- Real-time sales analytics and reporting
- Inventory tracking and 86'd items management
- Multi-location support
- Cloud sync and offline operation
- Integrated payment processing (Stripe, Square)
- Receipt printing
- Email/SMS notifications
- Manager dashboard
- Employee management and scheduling

### 3.3 Assumptions
- Single-location operation
- No user authentication required for MVP
- All staff members can access any function (role-based permissions deferred to Phase 2)
- Internet connectivity available (offline mode is Phase 2)
- Tablet device available for testing (iPad or similar)
- Local development environment only (cloud deployment is Phase 2)

### 3.4 Constraints
- **Time:** 5 weeks (December 23, 2024 - January 26, 2025)
- **Team Size:** Solo developer (CJ)
- **Budget:** $0 (using free/open-source tools only)
- **Technical:** Must demonstrate in classroom presentation on January 26th
- **Authentication:** Explicitly prohibited by instructors for MVP

---

## 4. System Architecture

### 4.1 Architecture Pattern
**3-Tier Architecture:**

```
┌─────────────────────────────────────┐
│   TIER 1: Presentation Layer        │
│   - React Frontend (tablet-optimized)│
│   - HTTP/JSON communication          │
└─────────────────────────────────────┘
                 ↕ REST API
┌─────────────────────────────────────┐
│   TIER 2: Business Logic Layer      │
│   - Spring Boot Application          │
│   - Controllers (HTTP endpoints)     │
│   - Services (business logic)        │
│   - Repositories (data access)       │
└─────────────────────────────────────┘
                 ↕ JPA/Hibernate
┌─────────────────────────────────────┐
│   TIER 3: Data Layer                │
│   - SQLite Database                  │
│   - 12 interconnected tables         │
└─────────────────────────────────────┘
```

### 4.2 Technology Justification
- **Spring Boot:** Industry-standard Java framework, aligns with bootcamp curriculum
- **SQLite:** Lightweight, file-based, perfect for MVP/local development
- **React:** Component-based, large community, suitable for interactive UI
- **RESTful API:** Standard communication pattern, easy to test and extend

---

## 5. Data Model

### 5.1 Entity Relationship Overview

**12 Core Entities:**
1. `tables` - Physical tables in restaurant
2. `table_status_log` - Audit trail of table status changes
3. `orders` - Customer checks/tickets
4. `order_items` - Line items on orders
5. `order_item_modifiers` - Applied modifiers to order items
6. `payments` - Payment transactions
7. `menu_items` - Dishes and drinks
8. `menu_categories` - Menu organization
9. `prep_stations` - Kitchen stations (Sauté, Fryer, Cold Prep, Bar)
10. `modifier_groups` - Categories of modifiers
11. `modifiers` - Individual modifier options
12. `menu_item_modifier_groups` - Junction table linking items to modifier groups

### 5.2 Key Relationships
- `tables` → `orders` (1:many) - One table can have multiple orders over time
- `orders` → `order_items` (1:many) - One order has many items
- `orders` → `payments` (1:many) - Support for split checks
- `menu_items` → `order_items` (1:many) - Menu items referenced in orders
- `menu_items` → `menu_categories` (many:1) - Items belong to categories
- `menu_items` → `prep_stations` (many:1) - Items routed to stations
- `menu_items` ↔ `modifier_groups` (many:many via junction table)
- `order_items` ↔ `modifiers` (many:many via junction table)

### 5.3 Critical Fields

**Orders Table - Signature Feature:**
```sql
delay_seconds INTEGER DEFAULT 10          -- Customizable delay
delay_expires_at TIMESTAMP                -- Calculated expiration
is_locked BOOLEAN DEFAULT FALSE           -- Prevents edits after delay
status VARCHAR(20) DEFAULT 'open'         -- Order lifecycle status
notes TEXT                                -- Table-level instructions
```

**Order Items Table - Status Tracking:**
```sql
item_status VARCHAR(20) DEFAULT 'pending' -- Item lifecycle
item_ordered_at TIMESTAMP                 -- When added to order
item_fired_at TIMESTAMP                   -- When sent to kitchen
item_completed_at TIMESTAMP               -- When kitchen finished
special_instructions TEXT                 -- Item-specific notes
```

*(Full database schema available in separate Data Model document)*

---

## 6. Functional Requirements

### 6.1 Table Management

**FR-TM-001: View Floor Map**
- **Description:** Display visual representation of all tables in restaurant
- **Priority:** HIGH
- **Inputs:** None (loads all tables from database)
- **Outputs:** Grid of table cards showing number, status, guest count, bill total
- **Business Rules:**
  - Table status must be one of: available, occupied, needs_cleaning
  - Current server's tables highlighted in purple
  - Section filtering available (Front, Back, Bar, Patio)
  - Real-time updates when table status changes

**FR-TM-002: Update Table Status**
- **Description:** Change table status throughout service flow
- **Priority:** HIGH
- **Inputs:** Table ID, new status, server name
- **Outputs:** Updated table with new status, logged in table_status_log
- **Business Rules:**
  - Status transitions: available → occupied → needs_cleaning → available
  - Cannot seat table that is occupied or needs cleaning
  - Status change logs who made change and when

**FR-TM-003: View Active Tables**
- **Description:** List all tables currently assigned to logged-in server
- **Priority:** HIGH
- **Inputs:** Server name (from session/context)
- **Outputs:** Card-based list of tables with order summaries
- **Business Rules:**
  - Only show tables with status = 'occupied' for this server
  - Display guest count, bill total, time seated
  - Show individual item statuses for each order
  - Sort options: table number, time seated, bill total, status

### 6.2 Menu Management

**FR-MM-001: Browse Menu by Category**
- **Description:** Display menu items organized by category
- **Priority:** HIGH
- **Inputs:** Category selection
- **Outputs:** Grid of menu items with name, price, prep station
- **Business Rules:**
  - Only show items where is_active = TRUE and is_86d = FALSE
  - Categories displayed in display_order
  - Items show base price (modifiers adjust final price)

**FR-MM-002: View Item Details**
- **Description:** Show full details for selected menu item
- **Priority:** MEDIUM
- **Inputs:** Menu item ID
- **Outputs:** Item name, description, base price, available modifier groups
- **Business Rules:**
  - Display all modifier groups assigned to this item
  - Show which modifier groups are required vs optional
  - Display price adjustments for each modifier

### 6.3 Order Creation & Management

**FR-OM-001: Create New Order**
- **Description:** Start new order for a table
- **Priority:** HIGH
- **Inputs:** Table ID, server name, guest count
- **Outputs:** New order record with unique order_id
- **Business Rules:**
  - Table must be in 'available' status
  - Sets order_created_at = CURRENT_TIMESTAMP
  - Calculates delay_expires_at = order_created_at + delay_seconds
  - Sets is_locked = FALSE, status = 'open'
  - Updates table status to 'occupied'

**FR-OM-002: Add Item to Order**
- **Description:** Add menu item to existing order
- **Priority:** HIGH
- **Inputs:** Order ID, menu item ID, quantity, selected modifiers, special instructions
- **Outputs:** New order_item record
- **Business Rules:**
  - Can only add items if is_locked = FALSE
  - Cache item_name and base_price from menu_items (preserve if menu changes)
  - Calculate modifiers_total from selected modifiers
  - Calculate item_total = (base_price + modifiers_total) × quantity
  - Update order subtotal, tax, total
  - Reset delay timer (delay_expires_at recalculates)

**FR-OM-003: Apply Modifiers to Item**
- **Description:** Select modifiers for an order item
- **Priority:** HIGH
- **Inputs:** Order item ID, selected modifier IDs
- **Outputs:** Records in order_item_modifiers junction table
- **Business Rules:**
  - Validate required modifier groups are satisfied
  - Enforce allow_multiple rules for each group
  - Cache modifier_name and price_adjustment (preserve history)
  - Sum all price_adjustments for modifiers_total
  - Recalculate item_total

**FR-OM-004: Edit Order Items (During Delay Window)**
- **Description:** Modify order items before kitchen receives them
- **Priority:** HIGH (SIGNATURE FEATURE)
- **Inputs:** Order ID, item changes
- **Outputs:** Updated order_items, reset delay timer
- **Business Rules:**
  - Can only edit if is_locked = FALSE
  - Can add items, remove items, change quantities, change modifiers
  - Each edit resets delay_expires_at
  - Cannot edit after is_locked = TRUE

**FR-OM-005: Order Delay Timer**
- **Description:** Background process that locks orders after delay expires
- **Priority:** CRITICAL (SIGNATURE FEATURE)
- **Inputs:** None (runs automatically)
- **Outputs:** Locked orders sent to kitchen
- **Business Rules:**
  - Runs every 1 second checking all orders
  - IF order.is_locked = FALSE AND CURRENT_TIMESTAMP >= order.delay_expires_at THEN:
    - Set is_locked = TRUE
    - Set status = 'sent'
    - Set order_sent_at = CURRENT_TIMESTAMP
    - Set all order_items.item_status = 'fired'
    - Set all order_items.item_fired_at = CURRENT_TIMESTAMP
  - Once locked, order cannot be edited (must void and recreate)

**FR-OM-006: View Order Details**
- **Description:** Display full itemized order with modifiers
- **Priority:** HIGH
- **Inputs:** Order ID
- **Outputs:** Complete order breakdown
- **Business Rules:**
  - Group items by category (Appetizers, Entrees, Beverages, etc.)
  - Show item name, quantity, base price, modifiers, item_total
  - Display order-level notes prominently
  - Show delay timer countdown if is_locked = FALSE
  - Show locked status if is_locked = TRUE
  - Display subtotal, tax, tip (if any), total

**FR-OM-007: Void Order**
- **Description:** Cancel an order before payment
- **Priority:** MEDIUM
- **Inputs:** Order ID, void reason
- **Outputs:** Order status changed to 'voided'
- **Business Rules:**
  - Can void at any time before payment
  - Cannot delete orders, only mark as voided
  - Preserve all data for audit trail
  - Update table status appropriately

### 6.4 Item Status Tracking

**FR-IS-001: Update Item Status**
- **Description:** Track item progress through kitchen
- **Priority:** HIGH
- **Inputs:** Order item ID, new status
- **Outputs:** Updated item_status and corresponding timestamp
- **Business Rules:**
  - Status flow: pending → fired → preparing → ready → delivered
  - Set timestamps: item_fired_at, item_completed_at based on status
  - Cannot move backwards in status (e.g., ready → preparing)

**FR-IS-002: View Items by Status**
- **Description:** Filter orders/items by current status
- **Priority:** MEDIUM
- **Inputs:** Status filter selection
- **Outputs:** Filtered list of items or orders
- **Business Rules:**
  - Filter options: all, pending, fired, preparing, ready, delivered
  - Useful for kitchen staff to see what needs prep

### 6.5 Payment Processing

**FR-PP-001: Process Payment**
- **Description:** Record payment for an order
- **Priority:** HIGH
- **Inputs:** Order ID, payment method, amount, tip amount
- **Outputs:** Payment record, order status updated
- **Business Rules:**
  - Payment methods: cash, credit_card, debit_card, gift_card
  - Can have multiple payments (split checks)
  - Sum of all payments should equal order.total (validated but not enforced)
  - When fully paid: set order.status = 'closed', order_closed_at = CURRENT_TIMESTAMP
  - Update table status based on business rules (ready for cleaning or next guest)

**FR-PP-002: Split Payment**
- **Description:** Allow multiple payments for one order
- **Priority:** MEDIUM
- **Inputs:** Order ID, multiple payment method + amount pairs
- **Outputs:** Multiple payment records
- **Business Rules:**
  - Each payment is separate record
  - Track individual tip amounts
  - Total of all payments = order.total

### 6.6 Financial Reporting

**FR-FR-001: View Server Financials**
- **Description:** Display sales and tip summaries for server
- **Priority:** MEDIUM
- **Inputs:** Server name, date range
- **Outputs:** Financial summary dashboard
- **Business Rules:**
  - Show total sales, total tips, orders closed
  - Calculate average check size, average tip percentage
  - Count tables served
  - Payment method breakdown (cash vs card percentages)
  - Recent transaction log (last 5-10 orders)

---

## 7. Non-Functional Requirements

### 7.1 Performance

**NFR-P-001: Response Time**
- API endpoints respond within 500ms for 95% of requests
- Database queries optimized with proper indexes
- Frontend renders under 2 seconds on target device

**NFR-P-002: Concurrent Users**
- System supports 5 concurrent servers without performance degradation
- Database handles 20 concurrent connections
- Frontend state management prevents race conditions

### 7.2 Usability

**NFR-U-001: Touch Optimization**
- All interactive elements minimum 44x44 pixels
- Buttons have generous padding and spacing
- No hover-dependent functionality

**NFR-U-002: Visual Design**
- 8-bit retro aesthetic with neon purple accents (#B026FF, #8B00FF)
- Dark background (#1A1A2E, #16213E) to reduce eye strain
- High contrast text (white/off-white) for readability
- Consistent iconography throughout

**NFR-U-003: Learning Curve**
- New user can complete basic order flow in under 5 minutes
- Interface follows restaurant POS conventions
- Clear visual indicators for system state

### 7.3 Reliability

**NFR-R-001: Data Integrity**
- All database constraints enforced at schema level
- Foreign key relationships prevent orphaned records
- Soft deletes preserve historical data

**NFR-R-002: Error Handling**
- Graceful degradation if API unavailable
- User-friendly error messages (no stack traces)
- Failed requests retry automatically (up to 3 attempts)

### 7.4 Maintainability

**NFR-M-001: Code Quality**
- Modular architecture with clear separation of concerns
- Comprehensive inline comments
- Consistent naming conventions
- RESTful API follows standard conventions

**NFR-M-002: Documentation**
- API endpoints documented with examples
- Database schema fully documented
- Setup instructions for local development

### 7.5 Scalability (Future)

**NFR-S-001: Architecture**
- Design allows migration to PostgreSQL in future
- API stateless to support horizontal scaling
- Frontend component-based for reusability

---

## 8. User Stories & Acceptance Criteria

### Epic 1: Table Management

**US-001: View Floor Layout**
- **As a** server
- **I want to** see a visual map of all tables in my section
- **So that** I can quickly identify which tables need attention

**Acceptance Criteria:**
- [ ] Floor map displays all tables in grid layout
- [ ] My assigned tables highlighted in purple
- [ ] Table status visible at a glance (available, occupied, needs cleaning)
- [ ] Guest count displayed for occupied tables
- [ ] Current bill total displayed for occupied tables
- [ ] Can filter by section (All, Front, Back, Bar, Patio)
- [ ] Tapping a table navigates to order details or creates new order
- [ ] Status updates in real-time (within 5 seconds)

---

**US-002: Seat a Table**
- **As a** server
- **I want to** mark a table as occupied when I seat guests
- **So that** the system knows I'm responsible for that table

**Acceptance Criteria:**
- [ ] Can tap available table to create new order
- [ ] Prompted to enter guest count
- [ ] Table status changes to 'occupied' immediately
- [ ] Table appears in "My Active Tables" view
- [ ] Change is logged in table_status_log with my name and timestamp
- [ ] Cannot seat a table already marked occupied or needs cleaning

---

**US-003: Clear a Table**
- **As a** server
- **I want to** mark a table as needing cleaning after guests leave
- **So that** bussers know to clear it

**Acceptance Criteria:**
- [ ] Can mark occupied table as "needs cleaning" after payment processed
- [ ] Table status changes to 'needs_cleaning'
- [ ] Table removed from my active tables list
- [ ] Change logged with timestamp
- [ ] Manager or busser can mark table as 'available' when clean

---

### Epic 2: Order Creation

**US-004: Start a New Order**
- **As a** server
- **I want to** create a new order when I greet a table
- **So that** I can begin taking their requests

**Acceptance Criteria:**
- [ ] Can tap occupied table or "New Order" button
- [ ] System creates order with unique ID
- [ ] Order linked to correct table
- [ ] My name recorded as server
- [ ] Guest count captured
- [ ] Order timestamp recorded
- [ ] Delay timer starts immediately (10-second default)
- [ ] Order marked as 'open' and 'unlocked'

---

**US-005: Browse Menu by Category**
- **As a** server
- **I want to** browse menu items organized by category
- **So that** I can find items quickly during service

**Acceptance Criteria:**
- [ ] Category tabs visible (Appetizers, Entrees, Steaks, Burgers, Drinks, etc.)
- [ ] Tapping category shows only items in that category
- [ ] Items display name, base price, and icon/emoji
- [ ] Out of stock items not shown (is_86d = TRUE)
- [ ] Inactive items not shown (is_active = FALSE)
- [ ] Search bar can filter items by name

---

**US-006: Add Item to Order**
- **As a** server
- **I want to** add a menu item to an order
- **So that** I can build the customer's order

**Acceptance Criteria:**
- [ ] Tapping menu item adds it to current order
- [ ] Item appears in order summary immediately
- [ ] Quantity defaults to 1 (can be adjusted)
- [ ] Base price shown
- [ ] If item has required modifiers, prompted to select them before adding
- [ ] Delay timer resets to 10 seconds
- [ ] Order subtotal and total recalculated
- [ ] Can add multiple items in sequence

---

**US-007: Apply Modifiers to Item**
- **As a** server
- **I want to** select modifiers for menu items
- **So that** I can customize orders to guest preferences

**Acceptance Criteria:**
- [ ] Modifier selection screen shows all applicable groups
- [ ] Required groups marked clearly (e.g., "Choose Protein *")
- [ ] Optional groups allow skipping
- [ ] Single-choice groups use radio buttons
- [ ] Multi-choice groups use checkboxes
- [ ] Price adjustments displayed next to each modifier
- [ ] Cannot proceed without selecting required modifiers
- [ ] Final item price shown (base + modifier adjustments)
- [ ] Modifiers saved with order item
- [ ] Order total updates immediately

---

**US-008: Add Special Instructions**
- **As a** server
- **I want to** add free-form notes to an item
- **So that** I can communicate special requests to the kitchen

**Acceptance Criteria:**
- [ ] Text input field available for each item
- [ ] Common phrases available as quick-tap buttons ("No onions", "Allergy: nuts", "Extra crispy")
- [ ] Notes display with item in order summary
- [ ] Notes visible to kitchen staff when order fires
- [ ] Character limit: 200 characters

---

### Epic 3: Order Delay Feature (SIGNATURE)

**US-009: Edit Order Before Sending**
- **As a** server
- **I want to** make changes to an order for 10 seconds before it goes to the kitchen
- **So that** I can catch mistakes without multiple ticket revisions

**Acceptance Criteria:**
- [ ] Visual countdown timer displayed on order screen
- [ ] Timer shows seconds remaining (e.g., "8 seconds to edit")
- [ ] Progress bar visualizes time remaining
- [ ] Can add items during delay window
- [ ] Can remove items during delay window
- [ ] Can change quantities during delay window
- [ ] Can modify modifiers during delay window
- [ ] Each change resets timer to 10 seconds
- [ ] Timer color changes as it runs low (green → yellow → red)
- [ ] Cannot edit after timer expires

---

**US-010: Automatically Lock Order**
- **As a** server
- **I want** the system to automatically send my order to the kitchen after the delay
- **So that** I don't have to manually submit every order

**Acceptance Criteria:**
- [ ] When delay timer reaches 0, order locks automatically
- [ ] Order status changes to 'sent'
- [ ] All items status changes to 'fired'
- [ ] Timestamp recorded for when order sent
- [ ] Visual indicator shows order is locked (purple lock icon)
- [ ] "Edit Order" button becomes disabled
- [ ] Can still view order details but not modify
- [ ] Toast notification: "Order sent to kitchen"

---

**US-011: Override Delay Timer**
- **As a** server
- **I want to** manually send an order before the delay expires
- **So that** urgent orders reach the kitchen immediately

**Acceptance Criteria:**
- [ ] "Send Now" button available during delay window
- [ ] Tapping button immediately locks order
- [ ] Order sent to kitchen without waiting for timer
- [ ] Same locking behavior as automatic timer expiration
- [ ] Confirmation prompt: "Send to kitchen now?" with Yes/No

---

### Epic 4: Order Tracking

**US-012: View My Active Tables**
- **As a** server
- **I want to** see a list of all my current tables with order statuses
- **So that** I know what needs my attention

**Acceptance Criteria:**
- [ ] Card-based layout showing each table
- [ ] Table number prominently displayed
- [ ] Guest count shown
- [ ] Time seated displayed ("12m ago", "45m ago")
- [ ] Order number and bill total shown
- [ ] Overall order status: Editable (green), Locked (purple), Ready (yellow)
- [ ] Item-level statuses: Cooking (fire icon), Ready (clock icon), Delivered (checkmark)
- [ ] Can sort by: table number, time seated, bill total, status
- [ ] Quick action buttons: View Order, Edit Order, Add Items, Process Payment

---

**US-013: View Order Details**
- **As a** server
- **I want to** see the complete itemized breakdown of an order
- **So that** I can verify accuracy and answer guest questions

**Acceptance Criteria:**
- [ ] Header shows order number, table, guest count, time created
- [ ] Delay timer shown if order not yet locked
- [ ] Lock status shown if order locked
- [ ] Items grouped by category (Appetizers, Entrees, Beverages, etc.)
- [ ] Each item shows: name, quantity, modifiers, special instructions, price, status
- [ ] Order-level notes displayed prominently
- [ ] Financial summary: subtotal, tax, tip (if added), total
- [ ] Prep station info for each item (for kitchen coordination)
- [ ] Timestamps: ordered, fired, completed for each item

---

**US-014: Track Item Status**
- **As a** server
- **I want to** know when items are ready in the kitchen
- **So that** I can deliver food promptly

**Acceptance Criteria:**
- [ ] Item status updates automatically (simulated for MVP)
- [ ] Status icons: ⏳ Pending, 🔥 Cooking, ⏱️ Ready, ✅ Delivered
- [ ] Visual distinction between statuses (color-coded)
- [ ] "Ready" items highlighted prominently
- [ ] Can mark items as delivered manually
- [ ] Notification when all items for a course ready (future: sound/vibration)

---

### Epic 5: Payment Processing

**US-015: Process Full Payment**
- **As a** server
- **I want to** record payment when a guest is ready to leave
- **So that** I can close their check

**Acceptance Criteria:**
- [ ] Payment screen shows order total clearly
- [ ] Can select payment method: Cash, Credit Card, Debit Card, Gift Card
- [ ] Can enter tip amount (or percentage)
- [ ] Total with tip calculated automatically
- [ ] Confirmation prompt before processing
- [ ] Payment recorded with timestamp
- [ ] Order status changes to 'closed'
- [ ] Table status updates to 'needs_cleaning'
- [ ] Receipt data generated (for future printing feature)

---

**US-016: Process Split Payment**
- **As a** server
- **I want to** accept multiple payments for one check
- **So that** guests can split the bill

**Acceptance Criteria:**
- [ ] "Split Payment" option available
- [ ] Can add multiple payment entries
- [ ] Running total shows amount paid and remaining
- [ ] Each payment has: amount, tip, payment method
- [ ] Visual indicator when fully paid
- [ ] Cannot close order until full amount paid
- [ ] All payments recorded with timestamps
- [ ] Can remove incorrect payment entry before finalizing

---

### Epic 6: Financial Tracking

**US-017: View My Daily Sales**
- **As a** server
- **I want to** see my sales and tips for the current shift
- **So that** I can track my performance

**Acceptance Criteria:**
- [ ] Financials screen shows shift summary
- [ ] Shift start time and duration displayed
- [ ] Total sales (sum of all closed orders)
- [ ] Total tips (sum of all tip amounts)
- [ ] Number of orders closed
- [ ] Average check size
- [ ] Average tip percentage
- [ ] Number of tables served
- [ ] Payment breakdown: percentage cash vs card
- [ ] Recent transactions list (last 5-10 orders)
- [ ] Can filter by date range: Today, This Week, This Month

---

## 9. Technical Stack

### 9.1 Backend

**Framework:** Spring Boot 3.5.8
- **Rationale:** Industry-standard Java framework, aligns with Zip Code curriculum
- **Configuration:** JAR packaging, Properties file format

**Database:** SQLite 3.44.1
- **Rationale:** Lightweight, file-based, zero configuration
- **JDBC Driver:** org.xerial:sqlite-jdbc:3.44.1.0
- **Dialect:** org.hibernate.community.dialect.SQLiteDialect

**ORM:** Hibernate / JPA
- **Rationale:** Object-relational mapping simplifies database operations
- **Configuration:** hibernate.ddl-auto=update, hibernate.show-sql=true

**API Style:** RESTful
- **Endpoints follow:** `/api/{resource}` convention
- **HTTP Methods:** GET (read), POST (create), PUT (update), DELETE (void)
- **Response Format:** JSON

**Build Tool:** Maven
- **Rationale:** Standard Java dependency management

**Java Version:** 17 (LTS)
- **Rationale:** Current stable long-term support version

### 9.2 Frontend

**Framework:** React 18+
- **Rationale:** Component-based architecture, large community, interactive UI
- **Alternative Option:** Vue.js (if time permits, demonstrate same functionality in multiple frameworks)

**State Management:** React Context API or useState/useReducer
- **Rationale:** Built-in, suitable for MVP complexity
- **Future:** Consider Redux if state grows complex

**HTTP Client:** Fetch API (native)
- **Rationale:** No external dependencies needed
- **Alternative:** Axios for advanced features (future)

**Styling:** CSS3 with custom 8-bit retro theme
- **Colors:**
  - Primary: `#B026FF` (neon purple)
  - Secondary: `#8B00FF` (deep purple)
  - Background: `#1A1A2E` (dark navy)
  - Success: `#39FF14` (neon green)
  - Warning: `#FF9500` (neon orange)
  - Danger: `#FF3333` (red)
- **Typography:** Retro gaming font for headers, clean sans-serif for body
- **Icons:** Emoji + simple geometric shapes

**Build Tool:** Create React App or Vite
- **Rationale:** Quick setup, development server, hot reload

### 9.3 Development Tools

**IDE:** Visual Studio Code
- **Extensions:** Java Extension Pack, ES7+ React snippets, Prettier

**API Testing:** Postman / Insomnia / curl
- **Rationale:** Test endpoints before frontend integration

**Version Control:** Git + GitHub
- **Repository:** https://github.com/ColeJam13/NotaPOS

**Database Tool:** SQLite Browser / DBeaver
- **Rationale:** Visual inspection of database during development

### 9.4 API Endpoint Specification

**Base URL:** `http://localhost:8080/api`

#### Tables Endpoints
```
GET    /api/tables                  - List all tables
GET    /api/tables/{id}             - Get table by ID
POST   /api/tables                  - Create new table
PUT    /api/tables/{id}             - Update table status
GET    /api/tables/section/{name}   - Get tables by section
```

#### Orders Endpoints
```
GET    /api/orders                  - List all orders
GET    /api/orders/{id}             - Get order by ID with items
POST   /api/orders                  - Create new order
PUT    /api/orders/{id}             - Update order
DELETE /api/orders/{id}             - Void order
GET    /api/orders/table/{tableId}  - Get orders for table
GET    /api/orders/server/{name}    - Get orders for server
GET    /api/orders/status/{status}  - Get orders by status
```

#### Order Items Endpoints
```
GET    /api/items                   - List all order items
GET    /api/items/{id}              - Get order item by ID
POST   /api/items                   - Add item to order
PUT    /api/items/{id}              - Update item (modifiers, quantity)
DELETE /api/items/{id}              - Remove item from order
PUT    /api/items/{id}/status       - Update item status
```

#### Menu Endpoints
```
GET    /api/menu/items              - List all menu items
GET    /api/menu/items/{id}         - Get menu item details
GET    /api/menu/categories         - List all categories
GET    /api/menu/category/{id}      - Get items in category
GET    /api/menu/modifiers/{itemId} - Get modifier groups for item
```

#### Payments Endpoints
```
GET    /api/payments                - List all payments
GET    /api/payments/{id}           - Get payment by ID
POST   /api/payments                - Process payment
GET    /api/payments/order/{orderId}- Get payments for order
```

#### Financials Endpoints
```
GET    /api/financials/server/{name}           - Server summary
GET    /api/financials/server/{name}/range     - Server summary by date range
GET    /api/financials/daily                   - Daily summary (all servers)
```

**Standard Response Format:**
```json
{
  "success": true,
  "data": { /* response payload */ },
  "message": "Operation completed successfully",
  "timestamp": "2025-01-15T14:30:00Z"
}
```

**Error Response Format:**
```json
{
  "success": false,
  "error": "Resource not found",
  "message": "Order with ID 999 does not exist",
  "timestamp": "2025-01-15T14:30:00Z"
}
```

---

## 10. Development Timeline

### 10.1 Project Phases

**Total Duration:** 5 weeks (December 23, 2024 - January 26, 2025)

---

### **Week 1: Foundation & Backend (Dec 23 - Dec 29)**

**Goals:** Complete backend API and database

**Tasks:**
- [ ] Set up Spring Boot project with dependencies
- [ ] Create database schema (12 tables with relationships)
- [ ] Write seed data script (sample menu, tables)
- [ ] Create JPA entities for all tables
- [ ] Build repository layer (extend JpaRepository)
- [ ] Build service layer (business logic)
- [ ] Build controller layer (REST endpoints)
- [ ] Test all CRUD operations with Postman
- [ ] Implement order delay timer background job

**Deliverables:**
- Fully functional REST API
- Database with sample data
- All endpoints tested and documented

**Risk Mitigation:**
- If JPA relationships complex, simplify to basic CRUD first
- Focus on core tables (tables, orders, order_items, menu_items) before modifiers

---

### **Week 2: Frontend Foundation (Dec 30 - Jan 5)**

**Goals:** Set up React app and build core UI components

**Tasks:**
- [ ] Initialize React project with routing
- [ ] Set up project structure (components, services, utils)
- [ ] Create API service layer (fetch wrapper)
- [ ] Build reusable UI components (Button, Card, Input, Modal)
- [ ] Apply 8-bit retro theme styles
- [ ] Create navigation/routing (Floor Map, My Tables, etc.)
- [ ] Implement global state management (Context)

**Deliverables:**
- React app running locally
- Navigation between views working
- API integration working for basic GET requests

**Risk Mitigation:**
- Use Create React App template to save setup time
- Copy/adapt styled components from Vue.js capstone for consistency

---

### **Week 3: Core Features (Jan 6 - Jan 12)**

**Goals:** Implement table management and order creation

**Tasks:**
- [ ] Floor Map view (table grid, status indicators)
- [ ] My Active Tables view (card layout)
- [ ] Create new order flow
- [ ] Menu browsing by category
- [ ] Add items to order
- [ ] Modifier selection UI
- [ ] Order summary display
- [ ] Update table status

**Deliverables:**
- Can create order for table
- Can add items with modifiers
- Table status updates properly

**Risk Mitigation:**
- Prioritize "happy path" flows over edge cases
- Use placeholder UI for complex modifier selection if time tight

---

### **Week 4: Delay Timer & Order Management (Jan 13 - Jan 19)**

**Goals:** Implement signature delay feature and order tracking

**Tasks:**
- [ ] Visual countdown timer component
- [ ] Progress bar animation
- [ ] Edit order during delay window
- [ ] Automatic order locking logic
- [ ] "Send Now" manual override
- [ ] Item status display and updates
- [ ] My Active Orders detailed view
- [ ] Filter orders by status

**Deliverables:**
- Delay timer fully functional
- Orders lock automatically after 10 seconds
- Can track item status through lifecycle

**Risk Mitigation:**
- Timer is MVP's signature feature - allocate extra time
- If WebSockets for real-time difficult, use polling (setInterval every 2 seconds)

---

### **Week 5: Payments & Polish (Jan 20 - Jan 26)**

**Goals:** Complete payment flow, financials, and prepare demo

**Tasks:**
- [ ] Payment processing screen
- [ ] Split payment support
- [ ] Close order flow
- [ ] Financials summary view
- [ ] Server sales dashboard
- [ ] Bug fixes and edge cases
- [ ] UI polish (animations, loading states, error handling)
- [ ] Comprehensive testing
- [ ] Prepare demo script
- [ ] Create demo video (2-3 minutes)
- [ ] Finalize presentation slides

**Deliverables:**
- Complete end-to-end flow working
- Payment processing functional
- Demo-ready application
- Presentation materials

**Risk Mitigation:**
- Payments can be simplified (just record, no validation)
- Focus on making demo flow smooth over 100% feature complete
- Have backup recorded demo video in case of live demo issues

---

### 10.2 Milestone Dates

| Date | Milestone | Status |
|------|-----------|--------|
| Dec 29 | Backend API Complete | Pending |
| Jan 5 | Frontend UI Components Complete | Pending |
| Jan 12 | Core Order Flow Working | Pending |
| Jan 19 | Delay Timer Fully Functional | Pending |
| Jan 26 | **Final Presentation & Demo** | Pending |

---

## 11. Testing Strategy

### 11.1 Backend Testing

**Unit Tests:**
- Service layer methods (business logic)
- Repository queries
- Entity validations

**Integration Tests:**
- API endpoints (controller → service → repository)
- Database transactions
- Foreign key constraints

**Test Framework:** JUnit 5 + Mockito

**Coverage Goal:** 60% minimum for MVP (focus on critical paths)

**Manual Testing:**
- Postman collection for all endpoints
- Test data creation scripts
- Edge case scenarios documented

### 11.2 Frontend Testing

**Manual Testing Priority:**
- User flows from start to finish
- UI responsiveness on tablet
- Touch interaction testing
- Error handling and edge cases

**Automated Testing (if time permits):**
- Component unit tests (Jest + React Testing Library)
- Integration tests for API calls

**Browser Testing:**
- Chrome (primary)
- Safari (secondary, if on iOS tablet)

**Device Testing:**
- iPad or similar tablet (10-12 inch screen)
- Desktop browser (development)

### 11.3 End-to-End Testing

**Test Scenarios:**

1. **Happy Path - Full Service Flow:**
   - Server views floor map
   - Seats table (creates order)
   - Browses menu and adds items with modifiers
   - Delay timer counts down
   - Order locks and sends to kitchen
   - Items marked as ready
   - Server processes payment
   - Table marked for cleaning

2. **Order Editing:**
   - Create order with items
   - Add more items during delay window
   - Remove items during delay window
   - Change modifier selections
   - Verify timer resets with each edit

3. **Split Payment:**
   - Order with $100 total
   - Process payment 1: $40 cash
   - Process payment 2: $60 card
   - Verify order closes when fully paid

4. **Multiple Tables:**
   - Server manages 3 tables simultaneously
   - Orders at different stages
   - Verify no data mixing between tables

**Acceptance Testing:**
- Demo to instructors on January 26th
- Gather feedback from peers (simulate server role)

---

## 12. Deployment Plan

### 12.1 MVP Deployment (Local)

**Environment:** Local development machine only

**Steps:**
1. Run Spring Boot backend: `mvn spring-boot:run`
2. Run React frontend: `npm start`
3. Access app: `http://localhost:3000`

**Database:**
- SQLite file stored in project root: `restaurant.db`
- Seed data loaded on first run

**Demo Setup:**
- Laptop connected to projector/screen
- Backup: Pre-recorded demo video
- Backup: Screenshots of key features

### 12.2 Future Deployment (Post-MVP)

**Phase 2 Goals:**
- Deploy backend to cloud (Heroku, Railway, Render)
- Host frontend on Vercel/Netlify
- Migrate to PostgreSQL
- Set up CI/CD pipeline
- Domain name acquisition

---

## 13. Success Metrics

### 13.1 MVP Completion Criteria

**Feature Completeness:**
- [ ] Can create and manage orders
- [ ] Delay timer works reliably
- [ ] Items tracked through lifecycle
- [ ] Payments can be processed
- [ ] UI is clean and navigable

**Technical Quality:**
- [ ] No critical bugs in demo flow
- [ ] API responds without errors
- [ ] Database maintains referential integrity
- [ ] Frontend handles edge cases gracefully

**Presentation Quality:**
- [ ] Clear 10-minute demo of key features
- [ ] Explains problem and solution effectively
- [ ] Demonstrates industry knowledge
- [ ] Shows technical competence

### 13.2 Post-MVP Goals

**Portfolio Quality:**
- Professional README with screenshots
- Code well-commented and organized
- GitHub repository polished

**User Feedback:**
- Share with restaurant industry contacts
- Gather feedback on UX and feature priorities
- Identify must-have features for Phase 2

**Personal Growth:**
- Demonstrate full-stack capability
- Prove domain knowledge translates to better software
- Build confidence for job interviews

---

## 14. Risks & Mitigation

### 14.1 Technical Risks

**Risk: Timer implementation complexity**
- **Impact:** HIGH - This is signature feature
- **Probability:** MEDIUM
- **Mitigation:**
  - Start with simple setInterval polling
  - Defer WebSocket real-time updates to Phase 2
  - Build timer UI first, then integrate backend logic

**Risk: React learning curve**
- **Impact:** MEDIUM
- **Probability:** LOW (already completed Vue.js version)
- **Mitigation:**
  - Leverage existing Vue.js capstone as reference
  - Use familiar patterns from Vanilla JS implementation
  - Focus on functional components + hooks (simpler than class components)

**Risk: JPA relationship mappings**
- **Impact:** MEDIUM
- **Probability:** MEDIUM
- **Mitigation:**
  - Start with simple relationships (tables → orders → items)
  - Add complex many-to-many (modifiers) later
  - Use SQL schema as source of truth, JPA annotations follow

**Risk: Database performance**
- **Impact:** LOW
- **Probability:** LOW (SQLite handles small datasets well)
- **Mitigation:**
  - Add indexes on foreign keys
  - Limit demo data to 20 tables, 50 menu items, 100 orders
  - Profile queries if slowness detected

### 14.2 Scope Risks

**Risk: Feature creep**
- **Impact:** HIGH
- **Probability:** HIGH (passion project syndrome)
- **Mitigation:**
  - Refer to spec sheet frequently
  - Mark Phase 2 features explicitly in backlog
  - Remember: MVP = Minimum Viable Product

**Risk: Authentication temptation**
- **Impact:** MEDIUM
- **Probability:** LOW (instructors explicitly prohibited it)
- **Mitigation:**
  - Do not implement auth for MVP
  - Use server name as string field (no validation)
  - Document Phase 2 auth plan separately

### 14.3 Time Risks

**Risk: React implementation takes longer than expected**
- **Impact:** HIGH
- **Probability:** MEDIUM
- **Mitigation:**
  - Tonight (Dec 23): Complete React basics from capstone
  - Have Vue.js implementation as working fallback
  - Focus on functionality over visual polish

**Risk: Delay feature debugging takes multiple days**
- **Impact:** HIGH
- **Probability:** MEDIUM
- **Mitigation:**
  - Allocate Week 4 entirely to this feature
  - Build in 2-day buffer before presentation
  - Have simplified version ready (manual "Send" button) as fallback

**Risk: Lost development time due to holidays**
- **Impact:** MEDIUM
- **Probability:** MEDIUM (Dec 24-25, Jan 1)
- **Mitigation:**
  - Front-load critical backend work (Week 1)
  - Plan lighter tasks around holidays
  - No slack days - every day counts

---

## 15. Future Enhancements (Phase 2+)

### 15.1 Phase 2: Kitchen Display System

**Features:**
- Separate view for kitchen staff
- Orders appear on screen when locked
- Items grouped by prep station
- Cooks can mark items as complete
- Timer shows how long each order pending

**Estimated Effort:** 2 weeks

---

### 15.2 Phase 3: Gamification & Analytics

**Server Challenges:**
- Daily/weekly sales goals with rewards
- Upsell tracking (appetizers, drinks, desserts)
- Leaderboard with rankings
- Achievement badges

**Analytics Dashboard:**
- Popular items by time of day
- Server performance comparison
- Table turnover rates
- Revenue trends

**Estimated Effort:** 3 weeks

---

### 15.3 Phase 4: Advanced Features

- User authentication with role-based access
- 86'd item management (out of stock)
- Inventory tracking
- Manager approval workflows
- Receipt printing
- Multi-location support
- Cloud sync with local server (Nota Box)

**Estimated Effort:** 8-12 weeks

---

## 16. Glossary

**Term** | **Definition**
---------|---------------
**86'd** | Restaurant slang for "out of stock" or unavailable menu item
**BoH** | Back of House (kitchen staff)
**Check** | The bill/receipt for a table's order
**Delay Buffer** | Time window during which server can edit order before kitchen receives it
**FoH** | Front of House (servers, hosts, bartenders)
**Modifier** | Customization option for menu item (protein choice, temperature, toppings)
**MVP** | Minimum Viable Product - initial version with core features only
**Order Item** | Individual line item on an order (one dish or drink)
**POS** | Point of Sale - system for processing transactions
**Prep Station** | Area in kitchen where specific items are prepared (Sauté, Fryer, Cold Prep, Bar)
**Seat** | Action of marking a table as occupied when guests arrive
**Server** | Wait staff member who takes orders and serves guests
**Split Check** | Dividing one check into multiple payments
**Ticket** | Kitchen's copy of the order showing what to prepare
**Turn Table** | Complete the service cycle for a table (seat → order → serve → pay → clear)
**Void** | Cancel an order or item before completion

---

## Appendix A: Related Documents

- **One-Pager:** `nota-pos-one-pager.md` - Project pitch and vision
- **Data Model:** `nota-pos-data-model.md` - Complete database schema with relationships
- **UX Mockups:** `nota-pos-ux-mockups.md` - ASCII wireframes of all screens
- **Visual Data Model:** `Screenshot_2025-12-22_at_12_56_44_PM.png` - UML diagram

---

## Appendix B: Contact & Resources

**Developer:** Cole Jamison (CJ)  
**Email:** [Your Email]  
**GitHub:** https://github.com/ColeJam13  
**Project Repo:** https://github.com/ColeJam13/NotaPOS  
**Presentation Date:** January 26, 2025  
**Institution:** Zip Code Wilmington  

**Instructor Contact:** [If applicable]  
**Office Hours:** [If applicable]

---

## Document Approval

This specification has been reviewed and approved for implementation:

**Author:** Cole Jamison  
**Date:** December 23, 2024  
**Version:** 1.0 - MVP Specification  

**Approved By:** [Instructor Name if required]  
**Date:** _____________  

---

**END OF SPECIFICATION**

*This is a living document. Updates and changes will be tracked in version history.*
