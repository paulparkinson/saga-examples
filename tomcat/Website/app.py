from flask import Flask, request, session, redirect, url_for, render_template
from flask_socketio import SocketIO, emit, join_room, leave_room
import requests
import json
from datetime import datetime
import threading
import time
from collections import defaultdict



app = Flask(__name__)
app.secret_key = 'oracle' 
socketio = SocketIO(app)


URL_CLOUDBANK_LOGIN = "http://localhost:8082/cloudbank/login"
URL_CLOUDBANK_NEW_CUSTOMER = "http://localhost:8082/cloudbank/newCustomer"
URL_CLOUDBANK_NEW_BANK_ACCOUNT = "http://localhost:8082/cloudbank/newBankAccount"
URL_CLOUDBANK_TRANSFER = "http://localhost:8082/cloudbank/transfer"
URL_CLOUDBANK_NEW_CREDIT_CARD = "http://localhost:8082/cloudbank/newCreditCard"
URL_CLOUDBANK_REFRESH = "http://localhost:8082/cloudbank/refresh"
URL_CLOUDBANK_NOTIFICATION = "http://localhost:8082/cloudbank/notification"


# Endpoint to serve the index.html page
@app.route('/')
def index():
    return render_template('index.html')

@app.route('/login', methods=['GET','POST'])
def login():
    if request.method == 'POST':
        username = request.form['username']
        password = request.form['password']
    
        
        # Making the API call
        response = requests.post(URL_CLOUDBANK_LOGIN, json={'id': username, 'pwd': password})
        
        if response.status_code == requests.codes.accepted:
            # Parse the JSON returned by the API
            data_main = response.json()
            data = json.loads(data_main['data'])
            
            for key, value in data.items():
                if isinstance(value, list):
                    session[key] = json.dumps(value)
                else:
                    session[key] = value
                    
            return redirect(url_for('dashboard'))
        else:
            # Handle login failure
            error = "Login failed. Please check your credentials."
            return render_template('login.html', error=error)
    else:
        login_id = session.pop('login_id', None)
        return render_template('login.html', login_id=login_id)
    
@app.route('/dashboard', methods=['GET'])
def dashboard():
    if 'full_name' not in session:  
        return redirect(url_for('login'))
    
    saga_id = session.pop("new_bank_saga_id", None)
    reason = session.pop("new_bank_reason", None)
    
    
    user_data = {}
    for key, value in session.items():
        # Check if the value is a JSON string that needs to be parsed
        if key in ['CHECKING', 'SAVING', 'CREDIT_CARD']:
            try:
                user_data[key] = json.loads(value)
            except json.JSONDecodeError:
                user_data[key] = []
        else:
            user_data[key] = value

    return render_template('dashboard.html', user_data=user_data, datetime=datetime, saga_id=saga_id, reason=reason)

@app.route('/refresh-dashboard', methods=['POST'])
def refresh_dashboard():
    
    response = requests.post(URL_CLOUDBANK_REFRESH, json={'ucid': session.get('ucid'), 'ossn': session.get('ossn')})
            
    data_main = response.json()
    data = json.loads(data_main['data'])
            
    for key, value in data.items():
        if isinstance(value, list):
            session[key] = json.dumps(value)
        else:
            session[key] = value
            

    return redirect(url_for('dashboard'))
    
    
@app.route('/createNewAccount', methods=['GET', 'POST'])
def create_new_account():
    if request.method == 'POST':
        # Extract data from form
        fullname = request.form.get('fullname')
        address = request.form.get('address') 
        phone = request.form.get('phone')
        email = request.form.get('email')
        ossn = request.form.get('ossn')
        bank = request.form.get('bank')
        password = request.form.get('password')

        # Make the API call
        
        response = requests.post(URL_CLOUDBANK_NEW_CUSTOMER, json={
            'full_name': fullname,
            'address': address,
            'phone': phone,
            'email': email,
            'ossn': ossn,
            'password': password,
            "bank": bank
        })

        if response.status_code == requests.codes.accepted: 
            response_data = response.json()
            login_id = response_data.get('login_id')
            session['login_id'] = login_id
            return redirect(url_for('login'))
        else:
            error = response.json().get('reason')
            return render_template('createNewAccount.html', error=error)
    else:
        return render_template('createNewAccount.html')
    
@app.route('/create_bank_account', methods=['GET', 'POST'])
def create_bank_account():
    if 'full_name' not in session:  
        return redirect(url_for('login'))
    
    if request.method == 'POST':
        account_type = request.form.get('account_type')
        if account_type == 'BANK_ACCOUNT':
            sub_type = request.form.get('sub_type')  
            response = requests.post(URL_CLOUDBANK_NEW_BANK_ACCOUNT, json={'operation_type': 'NEW_BANK_ACCOUNT', 'ucid': session.get("ucid"), 'account_type': sub_type})
        elif account_type == 'CREDIT_CARD':
            response = requests.post(URL_CLOUDBANK_NEW_CREDIT_CARD, json={'operation_type': 'NEW_CREDIT_CARD', 'ucid': session.get("ucid"), 'account_type': account_type})
            
        if response.status_code == requests.codes.accepted: 
            response_data = response.json()
            reason = response_data.get('reason')
            saga_id = response_data.get('id')
            session['new_bank_saga_id'] = saga_id
            session['new_bank_reason'] = reason
            return redirect(url_for('dashboard')) 
        else:
            error = 'Unable to create new account'
            return render_template('newBankAccount.html', error=error)
    else:
        return render_template('newBankAccount.html')
    
@app.route('/transfer', methods=['GET','POST'])
def transfer():
    if 'full_name' not in session:  
        return redirect(url_for('login'))
    
    user_data = {}
    for key, value in session.items():
        # Check if the value is a JSON string that needs to be parsed
        if key in ['CHECKING', 'SAVING', 'CREDIT_CARD']:
            try:
                user_data[key] = json.loads(value)
            except json.JSONDecodeError:
                user_data[key] = []
        else:
            user_data[key] = value
    
    if request.method == 'POST':
    
        from_account = request.form.get('from_account')
        to_account = request.form.get('to_account')
        amount = request.form.get('amount')
        password = request.form.get('password')
    
        response = requests.post(URL_CLOUDBANK_TRANSFER, json={'ucid': session.get("ucid"), 'to_account_number': to_account, 'from_account_number': from_account, 'amount': amount, 'password': password})

        if response.status_code == requests.codes.accepted: 
            response_data = response.json()
            reason = response_data.get('reason')
            saga_id = response_data.get('id')
            session['new_bank_saga_id'] = saga_id
            session['new_bank_reason'] = reason
            return redirect(url_for('dashboard')) 
        else:
            error = 'Unable to initiate transfer. Please check all the details and try again'
            return render_template('transfer.html', error=error, user_data=user_data)
    else:
        return render_template('transfer.html', user_data=user_data)
    
    
@app.route('/account-details')
def account_details():
    if 'full_name' not in session:  
        return redirect(url_for('login'))
    
    user_data = {
        'full_name': session.get('full_name'),
        'email': session.get('email'),
        'phone': session.get('phone'),
        'address': session.get('address'),
        'ossn': session.get('ossn'),  
        'ucid': session.get('ucid')
    }
    
    # Render the account details page with the user details
    return render_template('account_details.html', user_data=user_data)
    
    
@app.route('/logout')
def logout():
    session.clear()  
    return redirect(url_for('login'))



# Notification Logic
user_sessions = {}

@socketio.on('connect')
def handle_connect():
    ucid = get_user_id_from_request()
    if ucid:
        session_id = request.sid
        user_sessions[ucid] = session_id
        print(user_sessions)
        join_room(ucid)
        print(f'User {ucid} connected with SID {session_id}')

@socketio.on('disconnect')
def handle_disconnect():
    ucid = get_user_id_from_request()
    if ucid in user_sessions:
        leave_room(ucid)
        print(f'User {ucid} disconnected.')

        
def get_user_id_from_request():
    return session.get('ucid')
                
def fetch_new_entries():
    try:
        response = requests.get(URL_CLOUDBANK_NOTIFICATION)
        if response.status_code == requests.codes.accepted:
            response_json = response.json()
            
            data_str = response_json.get('data')
            if data_str:
                data_list = json.loads(data_str)
                new_entries = [json.loads(entry) for entry in data_list]
                
                return new_entries
        else:
            print(f"Failed to fetch new entries. Status code: {response.status_code}")
            return []
    except requests.RequestException as e:
        print(f"An error occurred: {e}")
        return []
    except json.JSONDecodeError as e:
        print(f"JSON decode error: {e}")
        return []

notification_cycles = defaultdict(int)

def notify_new_entries():
    while True:
        fetched_entries = fetch_new_entries()
        for entry in fetched_entries:
            key = (entry['saga_id'], entry['ucid'], entry['operation_type'], entry['operation_status'])
            if key not in notification_cycles:
                notification_cycles[key] = 0
        keys_to_remove = [] 
        for key, count in notification_cycles.items():
            saga_id, ucid, operation_type, operation_status = key
            user_id = ucid  
            data = 'Request ID: ' + saga_id + '. The ' + operation_type + ' operation\'s status is: ' + operation_status
            print(data)
            print(user_id)
            socketio.emit('new_notification', {'message': data}, room=user_id)
            notification_cycles[key] += 1
            if notification_cycles[key] >= 1:
                keys_to_remove.append(key)
        
        for key in keys_to_remove:
            del notification_cycles[key]
        time.sleep(15)
            
threading.Thread(target=notify_new_entries, daemon=True).start()    

if __name__ == '__main__':
   socketio.run(app, host='0.0.0.0', port=5000, debug=False)
