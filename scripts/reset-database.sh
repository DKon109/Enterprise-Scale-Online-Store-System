#!/bin/bash

# Database Reset Script for COMP5348 Project
# Usage: ./scripts/reset-database.sh [option]
# Options: 
#   1 - Restart PostgreSQL
#   2 - Drop and recreate database
#   3 - Clear all data (keep schema)
#   4 - Full reset (drop + recreate + restart app)
#   5 - Show database status

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Database configuration
DB_USER="postgres"
DB_NAME="onlinestore"
DB_HOST="localhost"
DB_PORT="5432"

# Functions
print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
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

# Check if PostgreSQL is running
check_postgres() {
    if psql -U $DB_USER -h $DB_HOST -p $DB_PORT -c "SELECT 1" > /dev/null 2>&1; then
        print_success "PostgreSQL is running"
        return 0
    else
        print_error "PostgreSQL is not running"
        return 1
    fi
}

# Option 1: Restart PostgreSQL
restart_postgres() {
    print_header "Option 1: Restarting PostgreSQL"
    
    echo "Stopping PostgreSQL..."
    brew services stop postgresql || true
    sleep 2
    
    echo "Starting PostgreSQL..."
    brew services start postgresql
    sleep 2
    
    if check_postgres; then
        print_success "PostgreSQL restarted successfully"
    else
        print_error "Failed to restart PostgreSQL"
        exit 1
    fi
}

# Option 2: Drop and recreate database
drop_recreate_db() {
    print_header "Option 2: Drop and Recreate Database"
    
    if ! check_postgres; then
        print_error "PostgreSQL is not running"
        exit 1
    fi
    
    echo "Dropping database '$DB_NAME'..."
    psql -U $DB_USER -h $DB_HOST -p $DB_PORT -c "DROP DATABASE IF EXISTS $DB_NAME;" || true
    sleep 1
    
    echo "Creating database '$DB_NAME'..."
    psql -U $DB_USER -h $DB_HOST -p $DB_PORT -c "CREATE DATABASE $DB_NAME;"
    sleep 1
    
    print_success "Database dropped and recreated"
    echo ""
    echo "Next steps:"
    echo "1. Run: ./gradlew bootRun"
    echo "2. Application will create tables automatically"
}

# Option 3: Clear all data
clear_data() {
    print_header "Option 3: Clear All Data (Keep Schema)"
    
    if ! check_postgres; then
        print_error "PostgreSQL is not running"
        exit 1
    fi
    
    echo "Clearing all data from tables..."
    psql -U $DB_USER -h $DB_HOST -p $DB_PORT -d $DB_NAME << EOF
-- Disable foreign key constraints
ALTER TABLE orders DISABLE TRIGGER ALL;
ALTER TABLE fulfillments DISABLE TRIGGER ALL;
ALTER TABLE deliveries DISABLE TRIGGER ALL;
ALTER TABLE customers DISABLE TRIGGER ALL;
ALTER TABLE products DISABLE TRIGGER ALL;
ALTER TABLE warehouses DISABLE TRIGGER ALL;
ALTER TABLE inventory DISABLE TRIGGER ALL;
ALTER TABLE outbox DISABLE TRIGGER ALL;

-- Truncate tables
TRUNCATE TABLE orders CASCADE;
TRUNCATE TABLE fulfillments CASCADE;
TRUNCATE TABLE deliveries CASCADE;
TRUNCATE TABLE customers CASCADE;
TRUNCATE TABLE products CASCADE;
TRUNCATE TABLE warehouses CASCADE;
TRUNCATE TABLE inventory CASCADE;
TRUNCATE TABLE outbox CASCADE;

-- Re-enable foreign key constraints
ALTER TABLE orders ENABLE TRIGGER ALL;
ALTER TABLE fulfillments ENABLE TRIGGER ALL;
ALTER TABLE deliveries ENABLE TRIGGER ALL;
ALTER TABLE customers ENABLE TRIGGER ALL;
ALTER TABLE products ENABLE TRIGGER ALL;
ALTER TABLE warehouses ENABLE TRIGGER ALL;
ALTER TABLE inventory ENABLE TRIGGER ALL;
ALTER TABLE outbox ENABLE TRIGGER ALL;
EOF
    
    print_success "All data cleared"
    echo ""
    echo "Next steps:"
    echo "1. Run: ./gradlew bootRun"
    echo "2. Demo account will be recreated automatically"
}

# Option 4: Full reset
full_reset() {
    print_header "Option 4: Full Reset"
    
    print_warning "This will drop the database and restart the application"
    read -p "Continue? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_warning "Cancelled"
        exit 0
    fi
    
    drop_recreate_db
    echo ""
    echo "Starting application..."
    cd /Users/shevaan/Mercara/Tutorial-09-Group-01
    ./gradlew bootRun
}

# Option 5: Show status
show_status() {
    print_header "Database Status"
    
    if check_postgres; then
        echo ""
        echo "Databases:"
        psql -U $DB_USER -h $DB_HOST -p $DB_PORT -l | grep $DB_NAME || echo "Database '$DB_NAME' not found"
        
        if psql -U $DB_USER -h $DB_HOST -p $DB_PORT -d $DB_NAME -c "SELECT 1" > /dev/null 2>&1; then
            echo ""
            echo "Tables:"
            psql -U $DB_USER -h $DB_HOST -p $DB_PORT -d $DB_NAME -c "\dt"
            
            echo ""
            echo "Row counts:"
            psql -U $DB_USER -h $DB_HOST -p $DB_PORT -d $DB_NAME << EOF
SELECT 'customers' as table_name, COUNT(*) as count FROM customers
UNION ALL
SELECT 'products', COUNT(*) FROM products
UNION ALL
SELECT 'warehouses', COUNT(*) FROM warehouses
UNION ALL
SELECT 'orders', COUNT(*) FROM orders
UNION ALL
SELECT 'fulfillments', COUNT(*) FROM fulfillments
UNION ALL
SELECT 'deliveries', COUNT(*) FROM deliveries
UNION ALL
SELECT 'outbox', COUNT(*) FROM outbox;
EOF
        else
            print_error "Cannot connect to database '$DB_NAME'"
        fi
    else
        print_error "PostgreSQL is not running"
    fi
}

# Main menu
show_menu() {
    echo ""
    echo "Database Reset Options:"
    echo "1) Restart PostgreSQL"
    echo "2) Drop and recreate database"
    echo "3) Clear all data (keep schema)"
    echo "4) Full reset (drop + recreate + restart app)"
    echo "5) Show database status"
    echo "0) Exit"
    echo ""
}

# Main script
main() {
    if [ $# -eq 0 ]; then
        # Interactive mode
        while true; do
            show_menu
            read -p "Select option (0-5): " option
            
            case $option in
                1) restart_postgres ;;
                2) drop_recreate_db ;;
                3) clear_data ;;
                4) full_reset ;;
                5) show_status ;;
                0) print_success "Exiting"; exit 0 ;;
                *) print_error "Invalid option" ;;
            esac
            
            echo ""
            read -p "Press Enter to continue..."
        done
    else
        # Command line mode
        case $1 in
            1) restart_postgres ;;
            2) drop_recreate_db ;;
            3) clear_data ;;
            4) full_reset ;;
            5) show_status ;;
            *) print_error "Invalid option: $1"; exit 1 ;;
        esac
    fi
}

# Run main
main "$@"

