package com.oracle.saga.cloudbank.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AccountTransferDTO {


    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    private String ucid;
    private String to_account_number;
    private String from_account_number;
    private String amount;
    private String password;

    public String getUcid() {
        return ucid;
    }

    public void setUcid(String ucid) {
        this.ucid = ucid;
    }

    public String getTo_account_number() {
        return to_account_number;
    }

    public void setTo_account_number(String to_account_number) {
        this.to_account_number = to_account_number;
    }

    public String getFrom_account_number() {
        return from_account_number;
    }

    public void setFrom_account_number(String from_account_number) {
        this.from_account_number = from_account_number;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }




}
