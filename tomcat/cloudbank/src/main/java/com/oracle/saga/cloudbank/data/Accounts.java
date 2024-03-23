package com.oracle.saga.cloudbank.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

public class Accounts {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Accounts)) return false;
        Accounts accounts = (Accounts) o;
        return Objects.equals(getOperation_type(), accounts.getOperation_type()) && Objects.equals(ucid, accounts.ucid) && Objects.equals(transaction_type, accounts.transaction_type) && Objects.equals(transaction_amount, accounts.transaction_amount) && Objects.equals(account_type, accounts.account_type) && Objects.equals(balance_amount, accounts.balance_amount) && Objects.equals(account_number, accounts.account_number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getOperation_type(), ucid, transaction_type, transaction_amount, account_type, balance_amount, account_number);
    }

    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    public String getOperation_type() {
        return operation_type;
    }

    public void setOperation_type(String operation_type) {
        this.operation_type = operation_type;
    }

    public String getUcid() {
        return ucid;
    }

    public void setUcid(String ucid) {
        this.ucid = ucid;
    }

    public String getTransaction_type() {
        return transaction_type;
    }

    public void setTransaction_type(String transaction_type) {
        this.transaction_type = transaction_type;
    }

    public String getTransaction_amount() {
        return transaction_amount;
    }

    public void setTransaction_amount(String transaction_amount) {
        this.transaction_amount = transaction_amount;
    }

    public String getAccount_type() {
        return account_type;
    }

    public void setAccount_type(String account_type) {
        this.account_type = account_type;
    }

    public String getBalance_amount() {
        return balance_amount;
    }

    public void setBalance_amount(String balance_amount) {
        this.balance_amount = balance_amount;
    }

    public String getAccount_number() {
        return account_number;
    }

    public void setAccount_number(String account_number) {
        this.account_number = account_number;
    }

    private String operation_type;
    private String ucid;
    private String transaction_type;
    private String transaction_amount;
    private String account_type;
    private String balance_amount;
    private String account_number;

}
