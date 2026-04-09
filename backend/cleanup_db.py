import os
import sqlite3
import psycopg2
from urllib.parse import urlparse

# This script clears specifically the 'expenses' table while keeping rules and configs safe.

def clear_expenses():
    db_url = os.getenv("DATABASE_URL", "sqlite:///C:/Users/The_last/AndroidStudioProjects/expensetracker/backend/expenses.db")
    
    print(f"Connecting to: {db_url}")
    
    if db_url.startswith("sqlite"):
        # Local SQLite
        path = db_url.replace("sqlite:///", "")
        conn = sqlite3.connect(path)
        cursor = conn.cursor()
        cursor.execute("DELETE FROM expenses")
        conn.commit()
        conn.close()
        print("Success: Local SQLite expenses cleared.")
        
    else:
        # Cloud PostgreSQL
        result = urlparse(db_url)
        username = result.username
        password = result.password
        database = result.path[1:]
        hostname = result.hostname
        port = result.port
        
        conn = psycopg2.connect(
            database=database,
            user=username,
            password=password,
            host=hostname,
            port=port
        )
        cursor = conn.cursor()
        cursor.execute("DELETE FROM expenses")
        conn.commit()
        conn.close()
        print("Success: Cloud PostgreSQL expenses cleared.")

if __name__ == "__main__":
    confirm = input("Are you sure you want to WIPE all expenses? (yes/no): ")
    if confirm.lower() == "yes":
        clear_expenses()
    else:
        print("Aborted.")
