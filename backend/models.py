from sqlalchemy import Column, Integer, String, Boolean, Float
from database import Base

class ExpenseDB(Base):
    __tablename__ = "expenses"

    id = Column(Integer, primary_key=True, index=True)
    user_email = Column(String, index=True) # Multi-user isolation
    title = Column(String, index=True)
    date = Column(String)
    amount = Column(String)
    isExpense = Column(Boolean)
    originalSms = Column(String)
    paymentId = Column(String, nullable=True)
    assignedContact = Column(String, default="User")
    note = Column(String)
    type = Column(String) # "Credited" or "Debited"
    bank = Column(String, default="Unknown Bank")
    status = Column(String, default="Unpaid")
    remainingAmount = Column(Float, default=0.0)

class ConfigDB(Base):
    __tablename__ = "configs"
    
    id = Column(Integer, primary_key=True, index=True)
    user_email = Column(String, index=True)
    key = Column(String, index=True) # e.g. "google_refresh_token"
    value = Column(String)

class ContactRuleDB(Base):
    __tablename__ = "contact_rules"

    id = Column(Integer, primary_key=True, index=True)
    user_email = Column(String, index=True)
    name = Column(String, index=True)
    email = Column(String)
    frequencyType = Column(String, default="Transaction Count")
    frequencyValue = Column(Integer, default=1)
    currentCount = Column(Integer, default=0)
    isEnabled = Column(Boolean, default=True)
