/**
 * Copyright (c) 2023 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.cloudbank.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class CloudBankSagaInfo implements Serializable {

    public String getSagaId() {
        return sagaId;
    }

    public void setSagaId(String sagaId) {
        this.sagaId = sagaId;
    }

    public void setReplies(Set<String> replies) {
        this.replies = replies;
    }

    public boolean isAccounts() {
        return accounts;
    }

    public void setAccounts(boolean accounts) {
        this.accounts = accounts;
    }

    public boolean isNewBA() {
        return newBA;
    }

    public void setNewBA(boolean newBA) {
        this.newBA = newBA;
    }

    public boolean isNewCC() {
        return newCC;
    }

    public void setNewCC(boolean newCC) {
        this.newCC = newCC;
    }

    public boolean isViewAll() {
        return viewAll;
    }

    public void setViewAll(boolean viewAll) {
        this.viewAll = viewAll;
    }

    public boolean isViewBA() {
        return viewBA;
    }

    public void setViewBA(boolean viewBA) {
        this.viewBA = viewBA;
    }

    public boolean isViewCC() {
        return viewCC;
    }

    public void setViewCC(boolean viewCC) {
        this.viewCC = viewCC;
    }

    public boolean isViewCS() {
        return viewCS;
    }

    public void setViewCS(boolean viewCS) {
        this.viewCS = viewCS;
    }

    public boolean isAccTransfer() {
        return accTransfer;
    }

    public void setAccTransfer(boolean accTransfer) {
        this.accTransfer = accTransfer;
    }

    public boolean isNewCustomer() {
        return newCustomer;
    }

    public void setNewCustomer(boolean newCustomer) {
        this.newCustomer = newCustomer;
    }

    public boolean isInvokeError() {
        return invokeError;
    }

    public void setInvokeError(boolean invokeError) {
        this.invokeError = invokeError;
    }

    public boolean isRollbackPerformed() {
        return rollbackPerformed;
    }

    public void setRollbackPerformed(boolean rollbackPerformed) {
        this.rollbackPerformed = rollbackPerformed;
    }

    public boolean isAccountsResponse() {
        return accountsResponse;
    }

    public void setAccountsResponse(boolean accountsResponse) {
        this.accountsResponse = accountsResponse;
    }

    public boolean isAccountsSecondResponse() {
        return accountsSecondResponse;
    }

    public void setAccountsSecondResponse(boolean accountsSecondResponse) {
        this.accountsSecondResponse = accountsSecondResponse;
    }

    public boolean isCreditScoreResponse() {
        return creditScoreResponse;
    }

    public void setCreditScoreResponse(boolean creditScoreResponse) {
        this.creditScoreResponse = creditScoreResponse;
    }

    public LoginDTO getLoginPayload() {
        return loginPayload;
    }

    public void setLoginPayload(LoginDTO loginPayload) {
        this.loginPayload = loginPayload;
    }

    public AccountTransferDTO getAccountTransferPayload() {
        return accountTransferPayload;
    }

    public void setAccountTransferPayload(AccountTransferDTO accountTransferPayload) {
        this.accountTransferPayload = accountTransferPayload;
    }

    public Accounts getRequestAccounts() {
        return requestAccounts;
    }

    public void setRequestAccounts(Accounts requestAccounts) {
        this.requestAccounts = requestAccounts;
    }

    public CreditScore getRequestCreditScore() {
        return requestCreditScore;
    }

    public void setRequestCreditScore(CreditScore requestCreditScore) {
        this.requestCreditScore = requestCreditScore;
    }

    private String sagaId;
    private Set<String> replies;
    private boolean accounts;
    private boolean newBA;
    private String accountResponse;

    public String getAccountResponse() {
        return accountResponse;
    }

    public void setAccountResponse(String accountResponse) {
        this.accountResponse = accountResponse;
    }

    public String getCreditScoreResponse() {
        return CreditScoreResponse;
    }

    public void setCreditScoreResponse(String creditScoreResponse) {
        CreditScoreResponse = creditScoreResponse;
    }

    private boolean newCC;

    private String CreditScoreResponse;

    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    private boolean viewAll;
    private boolean viewBA;
    private boolean viewCC;
    private boolean viewCS;
    private boolean accTransfer;
    private boolean newCustomer;
    private boolean invokeError;
    private boolean rollbackPerformed;
    private boolean accountsResponse;
    private boolean accountsSecondResponse;
    private boolean creditScoreResponse;
    private LoginDTO loginPayload;
    private AccountTransferDTO accountTransferPayload;
    private Accounts requestAccounts;
    private CreditScore requestCreditScore;

    public Boolean getDepositResponse() {
        return isDepositResponse;
    }

    public void setDepositResponse(Boolean depositResponse) {
        isDepositResponse = depositResponse;
    }

    public Boolean getWithdrawResponse() {
        return isWithdrawResponse;
    }

    public void setWithdrawResponse(Boolean withdrawResponse) {
        isWithdrawResponse = withdrawResponse;
    }

    private Boolean isDepositResponse;
    private Boolean isWithdrawResponse;

    public CloudBankSagaInfo() {
        replies = new HashSet<>();
    }

    public Set<String> getReplies() {
        return this.replies;
    }

    public void addReply(String participant) {
        replies.add(participant);
    }

    private String from_bank;
    private String to_bank;

    public String getFrom_bank() {
        return from_bank;
    }

    public void setFrom_bank(String from_bank) {
        this.from_bank = from_bank;
    }

    public String getTo_bank() {
        return to_bank;
    }

    public void setTo_bank(String to_bank) {
        this.to_bank = to_bank;
    }
}
