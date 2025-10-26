# COMP-512 Travel Reservation System

A distributed **Travel Reservation System** built for COMP-512 (Distributed Systems) using **Java TCP middleware** and a **Flask web interface**.  
It allows managing **Flights**, **Cars**, **Rooms**, and **Customers** through multiple distributed components that communicate via TCP sockets.

---

## System Overview

The system consists of:

- **Resource Managers (RMs)** — handle resources independently (`Flights`, `Cars`, `Rooms`)
- **Middleware** — coordinates between RMs and clients, handles forwarding and transactions
- **Client / Web UI** — sends commands to the middleware using TCP sockets or REST API

### Architecture

```
[ Web UI / Java Client ]
           │
           ▼
      [ Middleware ]
       /     |     \
 [Flights] [Cars] [Rooms]
```

---

## Setup Instructions

### 1 Compile the Java Files

In both `TCP/Server` and `TCP/Client` directories:

```bash
javac *.java
```

---

### 2 Start the Resource Managers

Run each on a separate terminal or machine:

```bash
# Machine 1
./run_server.sh Flights 5001

# Machine 2
./run_server.sh Cars 5002

# Machine 3
./run_server.sh Rooms 5003
```

---

### 3 Start the Middleware

On another terminal or machine:

```bash
./run_middleware.sh 5010 localhost:5001 localhost:5002 localhost:5003
```

If running remotely, replace `localhost` with the appropriate hostnames of the RMs.

---

### 4 Start the Java Client

From the `TCP/Client` directory:

```bash
./run_client.sh localhost 5010
```

For distributed setups, use the middleware’s hostname instead of `localhost`.

---

##  Optional: Flask Web Interface

A modern web dashboard is available in the `WebUI/` directory.

### Run the Flask App

```bash
cd WebUI
python3 app.py
```

Then open your browser to:

```
http://localhost:8080
```

---

## 👩‍💻 Contributors

- **Kazi Ashhab Rahman**  
- **Akash Lanard**  

---

## 🧳 License

Developed as part of **McGill University’s COMP-512: Distributed Systems** course.  
Use freely for academic or educational purposes.

---
