# JARVIS — Phase 0: Arc Reactor Spec Sheet

## 1. System Overview
**Definition:**  
Cuddy is a local authority layer that processes user input, routes it to a single agent (Cameron), and returns a response.

**Purpose:**  
Establish control, privacy, and routing foundation.

**Scope:**  
- Input handling  
- Routing (hardcoded)  
- API dispatch  
- Response handling  
- Logging  

**Exclusions:**  
- Memory  
- Voice  
- Multi-agent orchestration  

---

## 2. Architecture Overview
**Tech Stack:**  
- Kotlin (Android)  
- OkHttp  
- Gemini API (Cameron)  

**Core Components:**  
- InputProcessor  
- Router  
- Dispatcher  
- Logger  
- UI (MainActivity)  

**Flow:**  
User → Cuddy → Cameron → Response → Log → UI  

---

## 3. Data Model
**Exchange Object:**  
- id: UUID  
- timestamp: String  
- raw_input: String  
- cleaned_input: String  
- character_count: Int  

**Invariant:**  
Every exchange must be logged.

---

## 4. Interface Design
**Input:**  
- Text string  

**Output:**  
- Response string  

**Validation Rules:**  
- Must not be empty  
- Max length: 4000 characters  

**Failure Cases:**  
- Network failure  
- API failure  

**Failure Handling:**  
- Return safe fallback message  
- Log error  

---

## 5. Data Flow
1. Receive input  
2. Clean and validate  
3. Route (hardcoded → Cameron)  
4. Dispatch API call  
5. Receive response  
6. Format output  
7. Log exchange  
8. Display to user  

---

## 6. Core Components

### CuddyService
- Orchestrates full pipeline  
- Manages lifecycle of exchange  

### InputProcessor
- Cleans and validates input  
- Creates Exchange object  

### Router
- Returns constant routing decision  
- Always selects Cameron  

### Dispatcher
- Handles API call  
- Returns response  

### Logger
- Writes JSONL logs  
- Ensures persistence  

---

## 7. Task Breakdown

### Feature: Query Processing Loop

**Objective:**  
Complete one full request-response cycle reliably.

**Scope:**  
Input → Routing → API → Response → Logging  

**Dependencies:**  
- Gemini API access  
- Network connectivity  

**Preconditions:**  
- App installed and running  
- API key configured  

---

### Task 1: Process Input
**Purpose:** Convert raw input into structured Exchange  
**Output:** Valid Exchange object  
**Validation:** Non-empty, cleaned input  

---

### Task 2: Route Query
**Purpose:** Decide target agent  
**Output:** `{ agent: "CAMERON", confidence: 1.0 }`  
**Constraint:** No dynamic logic  

---

### Task 3: Dispatch API Call
**Purpose:** Send request to Gemini API  
**Output:** Response string  
**Failure Handling:** Catch errors, return fallback  

---

### Task 4: Log Exchange
**Purpose:** Persist interaction  
**Output:** JSONL entry  
**Validation:** Entry exists in log file  

---

### Task 5: Return Response
**Purpose:** Display output to user  
**Output:** Visible response in UI  

---

## 8. Completion Criteria
- App runs without crash  
- User receives valid responses  
- All exchanges logged locally  
- Handles network/API failures gracefully  
- Works for 10 consecutive queries without failure  

---