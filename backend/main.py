from fastapi import FastAPI, Depends, BackgroundTasks, Header, HTTPException
from sqlalchemy.orm import Session
from database import SessionLocal, engine
import models
from pydantic import BaseModel
from typing import List, Optional
import asyncio
import httpx
import os
import time

# Auto-create tables on startup
models.Base.metadata.create_all(bind=engine)

def run_migrations():
    from sqlalchemy import text
    try:
        with engine.begin() as conn:
            if "postgres" in str(engine.url):
                conn.execute(text("ALTER TABLE configs ADD COLUMN IF NOT EXISTS user_email VARCHAR;"))
                conn.execute(text("ALTER TABLE expenses ADD COLUMN IF NOT EXISTS user_email VARCHAR;"))
                conn.execute(text("ALTER TABLE contact_rules ADD COLUMN IF NOT EXISTS user_email VARCHAR;"))
                # Drop old unique indexes to allow multi-user overlapping names
                try: conn.execute(text("ALTER TABLE configs DROP CONSTRAINT IF EXISTS configs_key_key CASCADE;"))
                except: pass
                try: conn.execute(text("ALTER TABLE contact_rules DROP CONSTRAINT IF EXISTS contact_rules_name_key CASCADE;"))
                except: pass
                try: conn.execute(text("DROP INDEX IF EXISTS ix_configs_key;"))
                except: pass
                try: conn.execute(text("DROP INDEX IF EXISTS ix_contact_rules_name;"))
                except: pass
            else:
                try: conn.execute(text("ALTER TABLE configs ADD COLUMN user_email VARCHAR;"))
                except: pass
                try: conn.execute(text("ALTER TABLE expenses ADD COLUMN user_email VARCHAR;"))
                except: pass
                try: conn.execute(text("ALTER TABLE contact_rules ADD COLUMN user_email VARCHAR;"))
                except: pass
                # SQLite doesn't support dropping constraints easily, so we handle basic exceptions
    except Exception as e:
        print(f"[DEBUG] Migration: {e}")

run_migrations()

app = FastAPI(title="Multi-User Expense Tracker API")

# DB Dependency
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

# Identity Dependency (Simple header for now, upgradable to JWT)
def get_user_email(x_user_email: str = Header(...)):
    if not x_user_email:
        raise HTTPException(status_code=400, detail="X-User-Email header missing")
    return x_user_email

# Models for Request/Response
class ExpenseCreate(BaseModel):
    title: str
    date: str
    amount: str
    isExpense: bool
    originalSms: str
    paymentId: Optional[str] = None
    assignedContact: str = "User"
    note: str = ""
    type: str = "Debited"
    bank: str = "Unknown Bank"
    status: str = "Unpaid"
    remainingAmount: float = 0.0

class RuleSync(BaseModel):
    name: str
    email: str
    frequencyType: str
    frequencyValue: str
    currentCount: Optional[int] = 0
    isEnabled: bool

class AuthPayload(BaseModel):
    authCode: str

class InvoiceRequest(BaseModel):
    contactName: str
    email: str

# HELPERS
def safe_int(val, default=1):
    try: return int(val)
    except: return default

# --- ENDPOINTS ---

@app.get("/")
def read_root():
    return {"status": "online", "message": "Multi-User Backend is active"}

@app.post("/api/auth/google")
async def authenticate_google(payload: AuthPayload, db: Session = Depends(get_db)):
    # Exchange code for tokens
    GOOGLE_CLIENT_ID = os.getenv("GOOGLE_CLIENT_ID")
    GOOGLE_CLIENT_SECRET = os.getenv("GOOGLE_CLIENT_SECRET")
    
    async with httpx.AsyncClient() as client:
        resp = await client.post(
            "https://oauth2.googleapis.com/token",
            data={
                "code": payload.authCode,
                "client_id": GOOGLE_CLIENT_ID,
                "client_secret": GOOGLE_CLIENT_SECRET,
                "redirect_uri": "",
                "grant_type": "authorization_code"
            }
        )
        token_data = resp.json()
        
    if "error" in token_data:
        return {"status": "error", "message": token_data.get("error_description")}

    # Get User Info from ID Token (SECURE IDENTITY)
    from google.oauth2 import id_token
    from google.auth.transport import requests as google_requests
    
    id_info = id_token.verify_oauth2_token(token_data["id_token"], google_requests.Request(), GOOGLE_CLIENT_ID)
    email = id_info['email']
    
    refresh_token = token_data.get("refresh_token")
    if refresh_token:
        # Save private config for this user
        config = db.query(models.ConfigDB).filter(models.ConfigDB.user_email == email, models.ConfigDB.key == "google_refresh_token").first()
        if config:
            config.value = refresh_token
        else:
            config = models.ConfigDB(user_email=email, key="google_refresh_token", value=refresh_token)
            db.add(config)
        db.commit()
    
    return {"status": "success", "email": email, "message": f"Welcome {email}!"}

@app.post("/api/sync")
async def sync_expenses(expenses: List[ExpenseCreate], background_tasks: BackgroundTasks, db: Session = Depends(get_db), email: str = Depends(get_user_email)):
    pending_emails = {}
    for e in expenses:
        # Check if exists FOR THIS USER
        db_expense = db.query(models.ExpenseDB).filter(models.ExpenseDB.user_email == email, models.ExpenseDB.originalSms == e.originalSms).first()
        clean_contact = e.assignedContact.strip()
        
        if not db_expense:
            db_expense = models.ExpenseDB(
                user_email=email,
                title=e.title,
                date=e.date,
                amount=e.amount,
                isExpense=e.isExpense,
                originalSms=e.originalSms,
                paymentId=e.paymentId,
                assignedContact=clean_contact,
                note=e.note.strip(),
                type=e.type,
                bank=e.bank,
                status=e.status,
                remainingAmount=e.remainingAmount
            )
            db.add(db_expense)
        else:
            # UPDATE EXISTING (Fix: Bug - Persistence after categorization)
            db_expense.assignedContact = clean_contact
            db_expense.note = e.note.strip()
            db_expense.status = e.status
            db_expense.remainingAmount = e.remainingAmount
            # Also update bank/paymentId if they were missing before
            if not db_expense.bank or db_expense.bank == "Unknown Bank":
                db_expense.bank = e.bank
            if not db_expense.paymentId:
                db_expense.paymentId = e.paymentId

        db.flush()

            # Debt Resolver (Filtered by User)
            if db_expense.type == "Credited" and clean_contact != "User":
                try:
                    val = float(db_expense.amount.replace(',', ''))
                    unpaids = db.query(models.ExpenseDB).filter(
                        models.ExpenseDB.user_email == email,
                        models.ExpenseDB.assignedContact.ilike(clean_contact),
                        models.ExpenseDB.status == "Unpaid",
                        models.ExpenseDB.note.ilike(db_expense.note)
                    ).order_by(models.ExpenseDB.id.asc()).all()

                    for u in unpaids:
                        if val <= 0: break
                        if u.remainingAmount <= val:
                            val -= u.remainingAmount
                            u.remainingAmount = 0.0
                            u.status = "Paid"
                        else:
                            u.remainingAmount -= val
                            val = 0.0
                    
                    if val <= 0:
                        db_expense.status = "Paid"
                        db_expense.remainingAmount = 0.0
                    else:
                        db_expense.status = "Paid"
                        db_expense.remainingAmount = val
                except: pass

            # Trigger Logic
            rule = db.query(models.ContactRuleDB).filter(models.ContactRuleDB.user_email == email, models.ContactRuleDB.name == clean_contact).first()
            if rule and rule.isEnabled:
                rule.currentCount += 1
                if rule.currentCount >= rule.frequencyValue:
                    pending_emails[rule.name] = rule.email
                    rule.currentCount = 0
                    
    db.commit()
    for contact_name, target_email in pending_emails.items():
        background_tasks.add_task(send_dual_emails, email, contact_name, target_email)
    
    return {"message": "Sync successful"}

@app.get("/api/expenses/all")
def get_expenses(db: Session = Depends(get_db), email: str = Depends(get_user_email)):
    return db.query(models.ExpenseDB).filter(models.ExpenseDB.user_email == email).all()

@app.get("/api/rules/all")
def get_rules(db: Session = Depends(get_db), email: str = Depends(get_user_email)):
    return db.query(models.ContactRuleDB).filter(models.ContactRuleDB.user_email == email).all()

@app.post("/api/rules/sync")
def sync_rules(rules: List[RuleSync], db: Session = Depends(get_db), email: str = Depends(get_user_email)):
    seen = {}
    for r in rules: 
        seen[r.name.strip()] = r
        
    for name, r in seen.items():
        db_rule = db.query(models.ContactRuleDB).filter(models.ContactRuleDB.user_email == email, models.ContactRuleDB.name == name).first()
        f_val = safe_int(r.frequencyValue)
        c_val = r.currentCount if r.currentCount is not None else 0
        if db_rule:
            db_rule.email = r.email
            db_rule.frequencyType = r.frequencyType
            db_rule.frequencyValue = f_val
            db_rule.isEnabled = r.isEnabled
            db_rule.currentCount = c_val
        else:
            db.add(models.ContactRuleDB(user_email=email, name=name, email=r.email, frequencyType=r.frequencyType, frequencyValue=f_val, currentCount=c_val, isEnabled=r.isEnabled))
    db.commit()
    return {"message": "Rules synced"}

@app.delete("/api/rules/{name}")
def delete_rule(name: str, db: Session = Depends(get_db), email: str = Depends(get_user_email)):
    clean_name = name.strip()
    rule = db.query(models.ContactRuleDB).filter(models.ContactRuleDB.user_email == email, models.ContactRuleDB.name == clean_name).first()
    if rule:
        # 1. UNASSIGN ALL EXPENSES of this name back to "User" (Bug #2)
        expenses = db.query(models.ExpenseDB).filter(models.ExpenseDB.user_email == email, models.ExpenseDB.assignedContact.ilike(clean_name)).all()
        for e in expenses:
            e.assignedContact = "User"
            e.note = "User"
        
        db.delete(rule)
        db.commit()
    return {"message": "Rule deleted and history reset to User"}

@app.post("/api/admin/clear_database")
def clear_database_all(db: Session = Depends(get_db), email: str = Depends(get_user_email)):
    # Wipe all user data (keeping tokens/configs)
    from sqlalchemy import text
    try:
        db.execute(text("DELETE FROM expenses"))
        db.execute(text("DELETE FROM contact_rules"))
        db.commit()
        return {"status": "success", "message": "All expenses and rules wiped."}
    except Exception as e:
        return {"status": "error", "message": str(e)}

@app.post("/api/invoice/send")
def trigger_manual(payload: InvoiceRequest, background_tasks: BackgroundTasks, db: Session = Depends(get_db), email: str = Depends(get_user_email)):
    # Verify data exists for this user
    # Verify data exists for this user (Case-Insensitive - Bug Fix)
    clean_name = payload.contactName.strip()
    p_count = db.query(models.ExpenseDB).filter(models.ExpenseDB.user_email == email, models.ExpenseDB.assignedContact.ilike(clean_name), models.ExpenseDB.status == "Paid").count()
    u_count = db.query(models.ExpenseDB).filter(models.ExpenseDB.user_email == email, models.ExpenseDB.assignedContact.ilike(clean_name), models.ExpenseDB.status == "Unpaid").count()
    
    if p_count == 0 and u_count == 0:
        return {"status": "error", "message": "No data found"}
        
    background_tasks.add_task(send_dual_emails, email, clean_name, payload.email.strip())
    return {"status": "success", "message": "Queued"}

# --- ASYNC HIGH-SCALE MAILER ---

async def send_gmail_raw(db: Session, user_email: str, to: str, subject: str, html_body: str):
    # Get PRIVATE token for THIS user
    config = db.query(models.ConfigDB).filter(models.ConfigDB.user_email == user_email, models.ConfigDB.key == "google_refresh_token").first()
    if not config: return False
    
    from google.oauth2.credentials import Credentials
    from google.auth.transport import requests as google_requests
    
    creds = Credentials(token=None, refresh_token=config.value, token_uri="https://oauth2.googleapis.com/token", client_id=os.getenv("GOOGLE_CLIENT_ID"), client_secret=os.getenv("GOOGLE_CLIENT_SECRET"))
    creds.refresh(google_requests.Request())
    
    import base64
    from email.mime.text import MIMEText
    message = MIMEText(html_body, "html")
    message["to"] = to
    message["subject"] = subject
    raw = base64.urlsafe_b64encode(message.as_bytes()).decode()
    
    async with httpx.AsyncClient() as client:
        resp = await client.post(
            "https://gmail.googleapis.com/gmail/v1/users/me/messages/send",
            headers={"Authorization": f"Bearer {creds.token}"},
            json={"raw": raw}
        )
    return resp.status_code == 200

async def send_dual_emails(user_email: str, contact_name: str, target_email: str):
    db = SessionLocal()
    try:
        # Fetch fresh data for THIS user (Case-Insensitive)
        clean_name = contact_name.strip()
        paid = db.query(models.ExpenseDB).filter(models.ExpenseDB.user_email == user_email, models.ExpenseDB.assignedContact.ilike(clean_name), models.ExpenseDB.status == "Paid", models.ExpenseDB.type == "Credited").all()
        unpaid = db.query(models.ExpenseDB).filter(models.ExpenseDB.user_email == user_email, models.ExpenseDB.assignedContact.ilike(clean_name), models.ExpenseDB.status == "Unpaid").all()
        
        if paid:
            total_p = sum([float(e.amount.replace(',', '')) for e in paid if e.amount])
            rows = "".join([f"<tr><td>{e.date}</td><td>{e.note}</td><td>₹{e.amount}</td></tr>" for e in paid])
            html = f"""<html><body style='font-family: Arial;'>
                <h2 style='color: #38A169;'>Payment Receipt - Confirmed</h2>
                <p>We have received your payment(s):</p>
                <table border='1' cellpadding='8' style='border-collapse: collapse; width: 100%;'>
                    <tr style='background-color: #F0FFF4;'><th>Date</th><th>Description</th><th>Amount</th></tr>
                    {rows}
                </table>
                <p>Total Confirmed: <b>₹{total_p}</b></p>
                <p>Thank you for your business!</p>
            </body></html>"""
            
            if await send_gmail_raw(db, user_email, target_email, f"Receipt: Payment Confirmed (₹{total_p})", html):
                # Archive
                all_res = db.query(models.ExpenseDB).filter(models.ExpenseDB.user_email == user_email, models.ExpenseDB.assignedContact.ilike(clean_name), models.ExpenseDB.status == "Paid").all()
                for e in all_res: e.status = "Sent"
                db.commit()
            await asyncio.sleep(2)

        if unpaid:
            total_u = sum([float(str(e.remainingAmount)) for e in unpaid])
            rows = "".join([f"<tr><td>{e.date}</td><td>{e.note}</td><td>₹{e.remainingAmount}</td></tr>" for e in unpaid])
            html = f"""<html><body style='font-family: Arial;'>
                <h2 style='color: #E53E3E;'>Outstanding Invoice - Action Required</h2>
                <p>The following balances are currently pending:</p>
                <table border='1' cellpadding='8' style='border-collapse: collapse; width: 100%;'>
                    <tr style='background-color: #FFF5F5;'><th>Date</th><th>Description</th><th>Balance</th></tr>
                    {rows}
                </table>
                <p>Total Outstanding: <b>₹{total_u}</b></p>
                <p>Please settle the amount at your earliest convenience.</p>
            </body></html>"""
            
            await send_gmail_raw(db, user_email, target_email, f"Invoice: Payment Pending (₹{total_u})", html)
            
    finally:
        db.close()
