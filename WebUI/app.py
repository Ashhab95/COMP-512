"""
COMP-512 Travel Reservation System - Web UI
Flask backend that communicates with Java TCP Middleware
"""

from flask import Flask, render_template, request, jsonify
import socket
import json

app = Flask(__name__)

# Configuration - Update these to match your middleware
MIDDLEWARE_HOST = 'localhost'
MIDDLEWARE_PORT = 5010  # Your middleware port

def send_command_to_middleware(method, *args):
    """
    Send a JSON command to the Java TCP middleware and return the response.
    Replicates the exact behavior of TCPClient.java
    
    Args:
        method: Method name (e.g., "addFlight")
        *args: Arguments for the method
    
    Returns:
        Parsed response from middleware
    """
    try:
        # Build JSON request exactly like Java TCPClient
        request_data = {
            "method": method,
            "args": list(args)
        }
        json_request = json.dumps(request_data, separators=(',', ':'))
        
        print(f"\n[DEBUG] Sending: {json_request}")
        
        # Create TCP socket
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(10)
        
        # Connect
        sock.connect((MIDDLEWARE_HOST, MIDDLEWARE_PORT))
        
        # Send with newline (like out.println() in Java)
        sock.sendall((json_request + '\n').encode('utf-8'))
        
        # Read response line (like in.readLine() in Java)
        response_file = sock.makefile('r', encoding='utf-8')
        response_str = response_file.readline().strip()
        
        print(f"[DEBUG] Received: {response_str}")
        
        sock.close()
        
        if not response_str:
            return {"success": False, "message": "Empty response from server"}
        
        # Parse JSON response
        response_json = json.loads(response_str)
        
        # Check status like Java client does
        status = response_json.get("status")
        
        if status == "ok":
            # Extract response value
            result = response_json.get("response")
            return {"success": True, "message": str(result), "value": result}
        elif status == "failed":
            error_msg = response_json.get("message", "Operation failed")
            return {"success": False, "message": f"Error: {error_msg}"}
        else:
            return {"success": False, "message": f"Unknown status: {status}"}
    
    except socket.timeout:
        return {"success": False, "message": "Connection timeout"}
    except ConnectionRefusedError:
        return {"success": False, "message": "Could not connect to middleware. Is it running?"}
    except json.JSONDecodeError as e:
        print(f"[ERROR] JSON decode error: {e}")
        return {"success": False, "message": f"Invalid JSON response: {response_str}"}
    except Exception as e:
        print(f"[ERROR] {type(e).__name__}: {str(e)}")
        import traceback
        traceback.print_exc()
        return {"success": False, "message": str(e)}


@app.route('/')
def index():
    """Serve the main UI page"""
    return render_template('index.html')


@app.route('/api/flight/add', methods=['POST'])
def add_flight():
    """Add a new flight"""
    data = request.json
    result = send_command_to_middleware("addFlight", 
                                        int(data['flightNum']), 
                                        int(data['flightSeats']), 
                                        int(data['flightPrice']))
    return jsonify(result)


@app.route('/api/flight/delete', methods=['POST'])
def delete_flight():
    """Delete a flight"""
    data = request.json
    result = send_command_to_middleware("deleteFlight", int(data['flightNum']))
    return jsonify(result)


@app.route('/api/flight/query', methods=['POST'])
def query_flight():
    """Query flight information"""
    data = request.json
    result = send_command_to_middleware("queryFlight", int(data['flightNum']))
    return jsonify(result)


@app.route('/api/flight/query-price', methods=['POST'])
def query_flight_price():
    """Query flight price"""
    data = request.json
    result = send_command_to_middleware("queryFlightPrice", int(data['flightNum']))
    return jsonify(result)


@app.route('/api/car/add', methods=['POST'])
def add_car():
    """Add cars to a location"""
    data = request.json
    result = send_command_to_middleware("addCars", 
                                        data['location'], 
                                        int(data['numCars']), 
                                        int(data['price']))
    return jsonify(result)


@app.route('/api/car/delete', methods=['POST'])
def delete_car():
    """Delete cars from a location"""
    data = request.json
    result = send_command_to_middleware("deleteCars", data['location'])
    return jsonify(result)


@app.route('/api/car/query', methods=['POST'])
def query_car():
    """Query available cars"""
    data = request.json
    result = send_command_to_middleware("queryCars", data['location'])
    return jsonify(result)


@app.route('/api/car/query-price', methods=['POST'])
def query_car_price():
    """Query car price"""
    data = request.json
    result = send_command_to_middleware("queryCarsPrice", data['location'])
    return jsonify(result)


@app.route('/api/room/add', methods=['POST'])
def add_room():
    """Add rooms to a location"""
    data = request.json
    result = send_command_to_middleware("addRooms", 
                                        data['location'], 
                                        int(data['numRooms']), 
                                        int(data['price']))
    return jsonify(result)


@app.route('/api/room/delete', methods=['POST'])
def delete_room():
    """Delete rooms from a location"""
    data = request.json
    result = send_command_to_middleware("deleteRooms", data['location'])
    return jsonify(result)


@app.route('/api/room/query', methods=['POST'])
def query_room():
    """Query available rooms"""
    data = request.json
    result = send_command_to_middleware("queryRooms", data['location'])
    return jsonify(result)


@app.route('/api/room/query-price', methods=['POST'])
def query_room_price():
    """Query room price"""
    data = request.json
    result = send_command_to_middleware("queryRoomsPrice", data['location'])
    return jsonify(result)


@app.route('/api/customer/new', methods=['POST'])
def new_customer():
    """Create a new customer"""
    data = request.json
    if 'customerId' in data and data['customerId']:
        result = send_command_to_middleware("newCustomerID", int(data['customerId']))
    else:
        result = send_command_to_middleware("newCustomer")
    return jsonify(result)


@app.route('/api/customer/delete', methods=['POST'])
def delete_customer():
    """Delete a customer"""
    data = request.json
    result = send_command_to_middleware("deleteCustomer", int(data['customerId']))
    return jsonify(result)


@app.route('/api/customer/query', methods=['POST'])
def query_customer():
    """Query customer information"""
    data = request.json
    result = send_command_to_middleware("queryCustomer", int(data['customerId']))
    return jsonify(result)


@app.route('/api/reservation/flight', methods=['POST'])
def reserve_flight():
    """Reserve a flight for a customer"""
    data = request.json
    result = send_command_to_middleware("reserveFlight", 
                                        int(data['customerId']), 
                                        int(data['flightNum']))
    return jsonify(result)


@app.route('/api/reservation/car', methods=['POST'])
def reserve_car():
    """Reserve a car for a customer"""
    data = request.json
    result = send_command_to_middleware("reserveCar", 
                                        int(data['customerId']), 
                                        data['location'])
    return jsonify(result)


@app.route('/api/reservation/room', methods=['POST'])
def reserve_room():
    """Reserve a room for a customer"""
    data = request.json
    result = send_command_to_middleware("reserveRoom", 
                                        int(data['customerId']), 
                                        data['location'])
    return jsonify(result)


@app.route('/api/reservation/bundle', methods=['POST'])
def reserve_bundle():
    """Reserve a bundle (flights, car, room) for a customer"""
    data = request.json
    
    # Build flights list - convert all to strings like Java does
    flights = [str(f) for f in data['flights']]
    
    # Send to middleware with same format as Java client
    result = send_command_to_middleware("bundle", 
                                        int(data['customerId']), 
                                        flights,  # Send as list
                                        data['location'],
                                        data['car'] == 'true',
                                        data['room'] == 'true')
    return jsonify(result)

@app.post('/api/stats')
def api_stats():
    """Return persisted counts for flights, car locations, room locations, and customers.
    Attempts to infer the in-memory stores by common variable names so no refactor is required.
    """
    def guess_len(*names):
        g = globals()
        for n in names:
            if n in g:
                obj = g[n]
                # direct dict / set
                if isinstance(obj, (dict, set)):
                    return len(obj)
                # object with an inner dict/set attribute
                for attr in ('data', 'store', 'items', 'map', 'cache'):
                    if hasattr(obj, attr):
                        inner = getattr(obj, attr)
                        if isinstance(inner, (dict, set)):
                            return len(inner)
        return 0

    flights_cnt   = guess_len('flights', 'FLIGHTS', 'flight_map', 'flight_db', 'Flights', 'flightStore')
    car_loc_cnt   = guess_len('cars', 'CARS', 'car_map', 'car_db', 'carLocations', 'car_store', 'carStore')
    room_loc_cnt  = guess_len('rooms', 'ROOMS', 'room_map', 'room_db', 'roomLocations', 'room_store', 'roomStore')
    customers_cnt = guess_len('customers', 'CUSTOMERS', 'customer_map', 'customer_db', 'customerStore')

    return jsonify({
        'success': True,
        'flights': int(flights_cnt or 0),
        'cars': int(car_loc_cnt or 0),
        'rooms': int(room_loc_cnt or 0),
        'customers': int(customers_cnt or 0),
    })

if __name__ == '__main__':
    print("=" * 60)
    print("COMP-512 Travel Reservation System - Web UI")
    print("=" * 60)
    print(f"Middleware: {MIDDLEWARE_HOST}:{MIDDLEWARE_PORT}")
    print("Starting Flask server on http://localhost:8080")
    print("=" * 60)
    print("\nDEBUG MODE: You'll see detailed logs")
    print("=" * 60)
    app.run(debug=True, host='0.0.0.0', port=8080)
