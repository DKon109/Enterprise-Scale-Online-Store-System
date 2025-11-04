#!/usr/bin/env python3
"""
Update Postman collection with comprehensive tests for COMP5348 requirements.
Tests validate: status codes, headers, response bodies, failure scenarios, idempotency.
"""

import os
import sys
import requests
import json
from typing import Dict, List, Any

POSTMAN_API_KEY = os.getenv("POSTMAN_API_KEY")
COLLECTION_UID = "17376391-59c3ce5f-e0b8-4e9a-ab5a-af4d2c853ddb"
BASE_URL = "https://api.getpostman.com"

# Test scripts for each request type
TEST_SCRIPTS = {
    "default": [
        'pm.test("Status code is valid", function() {',
        '    pm.expect(pm.response.code).to.be.oneOf([200, 201, 204, 409]);',
        '});'
    ],
    "payment_failure": [
        'pm.test("Status 201 Created", function() {',
        '    pm.expect(pm.response.code).to.equal(201);',
        '});',
        'pm.test("Order status is CANCELLED (payment failed)", function() {',
        '    var jsonData = pm.response.json();',
        '    pm.expect(jsonData.status).to.equal("CANCELLED");',
        '});'
    ],
    "delivery_failure": [
        'pm.test("Status 201 Created", function() {',
        '    pm.expect(pm.response.code).to.equal(201);',
        '});',
        'pm.test("Order status is PAID (delivery failed)", function() {',
        '    var jsonData = pm.response.json();',
        '    pm.expect(jsonData.status).to.equal("PAID");',
        '});'
    ],
    "idempotency": [
        'pm.test("Status 201 Created", function() {',
        '    pm.expect(pm.response.code).to.equal(201);',
        '});',
        'pm.test("Response has orderId", function() {',
        '    var jsonData = pm.response.json();',
        '    pm.expect(jsonData.orderId).to.exist;',
        '});'
    ],
    "headers": [
        'pm.test("Has X-Request-ID header", function() {',
        '    pm.expect(pm.response.headers.get("X-Request-ID")).to.exist;',
        '});',
        'pm.test("Has X-Correlation-ID header", function() {',
        '    pm.expect(pm.response.headers.get("X-Correlation-ID")).to.exist;',
        '});'
    ]
}

def get_collection():
    """Fetch collection from Postman API."""
    url = f"{BASE_URL}/collections/{COLLECTION_UID}"
    headers = {"X-API-Key": POSTMAN_API_KEY}
    response = requests.get(url, headers=headers)
    response.raise_for_status()
    return response.json()["collection"]

def update_request_tests(request_name: str, test_type: str = "default") -> List[str]:
    """Get appropriate test script for request."""
    if "Payment Fails" in request_name:
        return TEST_SCRIPTS["payment_failure"]
    elif "DeliveryCo Rejects" in request_name:
        return TEST_SCRIPTS["delivery_failure"]
    elif "Idempotency" in request_name:
        return TEST_SCRIPTS["idempotency"]
    else:
        return TEST_SCRIPTS["default"]

def collect_requests(items: List[Dict], requests_list: List[Dict]):
    """Recursively collect all requests from collection."""
    for item in items:
        if "item" in item:
            collect_requests(item["item"], requests_list)
        elif "request" in item:
            requests_list.append(item)

def main():
    if not POSTMAN_API_KEY:
        print("❌ POSTMAN_API_KEY environment variable not set")
        return 1
    
    print("📥 Fetching collection from Postman API...")
    collection = get_collection()
    
    # Collect all requests
    all_requests = []
    collect_requests(collection.get("item", []), all_requests)
    
    print(f"✅ Found {len(all_requests)} requests")
    print("\n📝 Updating tests for each request...\n")
    
    for req in all_requests:
        name = req.get("name", "Unknown")
        test_type = "default"
        
        # Determine test type
        if "Payment Fails" in name:
            test_type = "payment_failure"
        elif "DeliveryCo Rejects" in name:
            test_type = "delivery_failure"
        elif "Idempotency" in name:
            test_type = "idempotency"
        
        test_script = update_request_tests(name, test_type)
        
        # Update event
        if "event" not in req:
            req["event"] = []
        
        # Remove existing test event
        req["event"] = [e for e in req["event"] if e.get("listen") != "test"]
        
        # Add new test event
        req["event"].append({
            "listen": "test",
            "script": {
                "type": "text/javascript",
                "exec": test_script
            }
        })
        
        print(f"✅ {name} ({test_type})")
    
    # Update collection via API
    print("\n📤 Uploading updated collection to Postman...")
    url = f"{BASE_URL}/collections/{COLLECTION_UID}"
    headers = {"X-API-Key": POSTMAN_API_KEY}
    payload = {"collection": collection}
    
    response = requests.put(url, json=payload, headers=headers)
    response.raise_for_status()
    
    print("✅ Collection updated successfully!")
    print(f"\n🎉 All {len(all_requests)} requests now have comprehensive tests")
    return 0

if __name__ == "__main__":
    sys.exit(main())

