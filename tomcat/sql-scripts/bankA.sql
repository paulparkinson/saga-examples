set serveroutput on

exec dbms_saga_adm.add_participant(participant_name=> 'BankA' ,dblink_to_broker => 'pdb1_link',mailbox_schema=> 'admin',broker_name=> 'TEST', dblink_to_participant=> 'pdb2_link');

CREATE SEQUENCE SEQ_BANK_A_LOGS
  START WITH 1
  INCREMENT BY 1
  NOMAXVALUE;

CREATE SEQUENCE SEQ_ACCOUNT_NUMBER_BANK_A
  START WITH 1234560001
  INCREMENT BY 1
  NOMAXVALUE;

CREATE SEQUENCE SEQ_CREDIT_CARD_NUMBER_BANK_A
  START WITH 123456789012000
  INCREMENT BY 1
  NOMAXVALUE;

CREATE TABLE bankA (
  ucid VARCHAR2(50),
  account_number NUMBER(20) PRIMARY KEY,
  account_type VARCHAR2(15) CHECK (account_type IN ('CHECKING', 'SAVING', 'CREDIT_CARD')),
  balance_amount decimal(10,2) reservable constraint balance_con check(balance_amount >= 0),
  created_at TIMESTAMP DEFAULT SYSTIMESTAMP
);

CREATE TABLE bankA_book (
  log_id NUMBER DEFAULT SEQ_BANK_A_LOGS.NEXTVAL PRIMARY KEY,
  saga_id VARCHAR2(100),
  ucid VARCHAR2(50),
  operation_type VARCHAR2(30) CHECK (operation_type IN ('VIEW_BALANCE_BA', 'VIEW_BALANCE_CC', 'WITHDRAW', 'DEPOSIT', 'NEW_BANK_ACCOUNT','NEW_CREDIT_CARD','NEW_CREDIT_CARD_SET_BALANCE','TRANSACT')),
  transaction_type VARCHAR2(10) CHECK (transaction_type IN ('CREDIT', 'DEBIT','null')),
  transaction_amount decimal(10,2),
  account_number VARCHAR2(100),
  operation_status VARCHAR2(10) CHECK (operation_status IN ('PENDING', 'ONGOING', 'COMPLETED', 'FAILED')),
  read VARCHAR2(10)  DEFAULT 'FALSE' CHECK (read IN ('TRUE', 'FALSE')),
  created_at TIMESTAMP DEFAULT SYSTIMESTAMP
);

INSERT INTO bankA (ucid, account_number, account_type, balance_amount)
VALUES ('ORACLE001', SEQ_ACCOUNT_NUMBER_BANK_A.NEXTVAL, 'CHECKING', 2000.00);

INSERT INTO bankA (ucid, account_number, account_type, balance_amount)
VALUES ('ORACLE004', SEQ_ACCOUNT_NUMBER_BANK_A.NEXTVAL, 'SAVING', 2000.00);
