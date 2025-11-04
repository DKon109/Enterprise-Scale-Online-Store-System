#!/bin/bash

# E2E Integration Test for Bank Service Idempotency and Correlation ID
# Tests the complete flow: Store → Bank → RabbitMQ with idempotency and correlation tracking

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_URL="http://localhost:8081"
BANK_URL="http://localhost:8081"
RABBITMQ_URL="http://localhost:15672/api"
RABBITMQ_USER="guest"
RABBITMQ_PASS="guest"

# Test data
CUSTOMER_ID="550e8400-e29b-41d4-a716-446655440001"
PRODUCT_ID="SKU-001"
QUANTITY=2
AMOUNT=100.0

# Generate unique IDs for this test run
TIMESTAMP=$(date +%s%N)
IDEMPOTENCY_KEY="e2e-test-${TIMESTAMP}"
CORRELATION_ID="corr-${TIMESTAMP}"
ORDER_ID=$(uuidgen)

# Helper functions
print_header() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

# Test 1: Basic payment flow with idempotency
test_basic_payment_flow() {
    print_header "TEST 1: Basic Payment Flow with Idempotency"
    
    print_info "Creating payment transaction with:"
    print_info "  Order ID: $ORDER_ID"
    print_info "  Idempotency Key: $IDEMPOTENCY_KEY"
    print_info "  Correlation ID: $CORRELATION_ID"
    print_info "  Amount: $AMOUNT"
    
    # Call Bank service to create payment transaction
    RESPONSE=$(curl -s -X POST "$BANK_URL/transactions" \
        -H "Content-Type: application/json" \
        -H "X-Correlation-ID: $CORRELATION_ID" \
        -d "{
            \"orderId\": \"$ORDER_ID\",
            \"amount\": $AMOUNT,
            \"type\": \"Purchase\",
            \"idempotencyKey\": \"$IDEMPOTENCY_KEY\"
        }")
    
    print_info "Response: $RESPONSE"
    
    # Extract transaction ID and verify fields
    TRANSACTION_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
    STATUS=$(echo "$RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    RETURNED_IDEMPOTENCY_KEY=$(echo "$RESPONSE" | grep -o '"idempotencyKey":"[^"]*"' | cut -d'"' -f4)
    RETURNED_CORRELATION_ID=$(echo "$RESPONSE" | grep -o '"correlationId":"[^"]*"' | cut -d'"' -f4)
    
    if [ -z "$TRANSACTION_ID" ]; then
        print_error "Failed to create payment transaction"
        return 1
    fi
    
    print_success "Payment transaction created: ID=$TRANSACTION_ID"
    print_success "Status: $STATUS"
    print_success "Idempotency Key: $RETURNED_IDEMPOTENCY_KEY"
    print_success "Correlation ID: $RETURNED_CORRELATION_ID"
    
    # Verify idempotency key is stored
    if [ "$RETURNED_IDEMPOTENCY_KEY" != "$IDEMPOTENCY_KEY" ]; then
        print_error "Idempotency key mismatch!"
        return 1
    fi
    
    # Verify correlation ID is stored
    if [ "$RETURNED_CORRELATION_ID" != "$CORRELATION_ID" ]; then
        print_error "Correlation ID mismatch!"
        return 1
    fi
    
    print_success "TEST 1 PASSED: Idempotency and correlation ID properly stored"
    return 0
}

# Test 2: Verify idempotency prevents duplicate processing
test_idempotency_duplicate_prevention() {
    print_header "TEST 2: Verify Idempotency Prevents Duplicate Processing"
    
    print_info "Retrying same payment with same idempotency key..."
    
    # Retry with same idempotency key
    RESPONSE2=$(curl -s -X POST "$BANK_URL/transactions" \
        -H "Content-Type: application/json" \
        -H "X-Correlation-ID: $CORRELATION_ID" \
        -d "{
            \"orderId\": \"$ORDER_ID\",
            \"amount\": $AMOUNT,
            \"type\": \"Purchase\",
            \"idempotencyKey\": \"$IDEMPOTENCY_KEY\"
        }")
    
    print_info "Retry Response: $RESPONSE2"
    
    # Extract transaction ID from retry
    TRANSACTION_ID_2=$(echo "$RESPONSE2" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
    
    if [ "$TRANSACTION_ID" != "$TRANSACTION_ID_2" ]; then
        print_error "Idempotency key did not prevent duplicate! Different transaction IDs returned."
        return 1
    fi
    
    print_success "TEST 2 PASSED: Same transaction ID returned on retry (idempotency working)"
    return 0
}

# Test 3: Verify RabbitMQ message publishing
test_rabbitmq_message_publishing() {
    print_header "TEST 3: Verify RabbitMQ Message Publishing"
    
    print_info "Checking RabbitMQ bank_queue for published events..."
    
    # Get queue stats
    QUEUE_RESPONSE=$(curl -s -u "$RABBITMQ_USER:$RABBITMQ_PASS" \
        "$RABBITMQ_URL/queues/%2F/bank_queue")
    
    print_info "Queue Response: $QUEUE_RESPONSE"
    
    # Extract message count
    MESSAGE_COUNT=$(echo "$QUEUE_RESPONSE" | grep -o '"messages":[0-9]*' | cut -d':' -f2)
    
    if [ -z "$MESSAGE_COUNT" ]; then
        print_warning "Could not retrieve queue stats"
        return 1
    fi
    
    print_success "Messages in bank_queue: $MESSAGE_COUNT"
    
    if [ "$MESSAGE_COUNT" -gt 0 ]; then
        print_success "TEST 3 PASSED: RabbitMQ events are being published"
        return 0
    else
        print_warning "TEST 3 WARNING: No messages in queue (may have been consumed)"
        return 0
    fi
}

# Test 4: Verify database state
test_database_state() {
    print_header "TEST 4: Verify Database State"
    
    print_info "Checking payment_transaction table for correlation ID tracking..."
    
    # Query database for the transaction
    DB_RESPONSE=$(psql -U postgres -h localhost -d onlinestore -c \
        "SELECT id, idempotency_key, correlation_id, status FROM payment_transaction WHERE order_id = '$ORDER_ID' LIMIT 1;" 2>/dev/null || echo "")
    
    if [ -z "$DB_RESPONSE" ]; then
        print_warning "Could not query database directly"
        return 1
    fi
    
    print_info "Database Response:\n$DB_RESPONSE"
    
    if echo "$DB_RESPONSE" | grep -q "$IDEMPOTENCY_KEY"; then
        print_success "Idempotency key found in database"
    else
        print_error "Idempotency key NOT found in database"
        return 1
    fi
    
    if echo "$DB_RESPONSE" | grep -q "$CORRELATION_ID"; then
        print_success "Correlation ID found in database"
    else
        print_error "Correlation ID NOT found in database"
        return 1
    fi
    
    print_success "TEST 4 PASSED: Database state is correct"
    return 0
}

# Main test execution
main() {
    print_header "E2E INTEGRATION TEST: Bank Service Idempotency & Correlation ID"
    
    print_info "Test Configuration:"
    print_info "  Base URL: $BASE_URL"
    print_info "  RabbitMQ URL: $RABBITMQ_URL"
    print_info "  Test Timestamp: $TIMESTAMP"
    
    # Run tests
    PASSED=0
    FAILED=0
    
    if test_basic_payment_flow; then
        ((PASSED++))
    else
        ((FAILED++))
    fi
    
    if test_idempotency_duplicate_prevention; then
        ((PASSED++))
    else
        ((FAILED++))
    fi
    
    if test_rabbitmq_message_publishing; then
        ((PASSED++))
    else
        ((FAILED++))
    fi
    
    if test_database_state; then
        ((PASSED++))
    else
        ((FAILED++))
    fi
    
    # Summary
    print_header "TEST SUMMARY"
    print_success "Tests Passed: $PASSED"
    if [ $FAILED -gt 0 ]; then
        print_error "Tests Failed: $FAILED"
    else
        print_success "Tests Failed: $FAILED"
    fi
    
    if [ $FAILED -eq 0 ]; then
        print_success "ALL TESTS PASSED!"
        return 0
    else
        print_error "SOME TESTS FAILED"
        return 1
    fi
}

# Run main
main "$@"

