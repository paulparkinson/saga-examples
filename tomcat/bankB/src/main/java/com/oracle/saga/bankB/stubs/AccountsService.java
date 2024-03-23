/**
 * Copyright (c) 2023 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.bankB.stubs;

import java.io.Reader;
import java.io.StringReader;
import java.sql.*;
import java.util.ArrayList;

import com.oracle.saga.bankB.data.CompensationData;
import com.oracle.saga.bankB.data.CreditResponse;
import com.oracle.saga.bankB.data.NotificationDTO;
import com.oracle.saga.bankB.data.ViewBADTO;
import com.oracle.saga.bankB.exception.AccountsException;
import oracle.jdbc.OraclePreparedStatement;
import oracle.jdbc.OracleResultSet;
import oracle.jdbc.OracleTypes;
import oracle.jdbc.driver.json.tree.OracleJsonObjectImpl;
import oracle.sql.NUMBER;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oracle.sql.json.OracleJsonException;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonObject;
import oracle.sql.json.OracleJsonParser;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

public class AccountsService {

    private static final String REG_EXP_REMOVE_QUOTES = "(^\")|(\"$)";
    private static final String PENDING = "PENDING";
    private static final String ONGOING = "ONGOING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    private Connection connection = null;
    private static final Logger logger = LoggerFactory.getLogger(AccountsService.class);
    Cache<String, ArrayList> accountsCompensationCache;

    public AccountsService(Connection conn, CacheManager cacheManager) throws AccountsException {
        accountsCompensationCache = cacheManager.getCache("bankBCompensationData", String.class,
               ArrayList.class);
        if (conn == null) {
            throw new AccountsException("Database connection is invalid.");
        }
        this.connection = conn;
    }


    public String newBankAccount(String accountPayload, String sagaId) {

        Reader inputReader = new StringReader(accountPayload);
        OracleJsonFactory jsonFactory = new OracleJsonFactory();
        String account_number =null;

        try (OracleJsonParser parser = jsonFactory.createJsonTextParser(inputReader)) {
            parser.next();
            OracleJsonObject accountsJsonObj = parser.getObject();
            int statusInsert = insertInBook(accountsJsonObj, sagaId);

            if (statusInsert != -1 ) {
                account_number = newInAccountsTable(accountsJsonObj);

                final CompensationData accountsCompensationInfo = new CompensationData();
                accountsCompensationInfo.setSagaId(sagaId);
                accountsCompensationInfo.setUcid(accountsJsonObj.get("ucid").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                accountsCompensationInfo.setOperation_type(accountsJsonObj.get("operation_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                if(account_number!=null){
                    accountsCompensationInfo.setAccount_number(account_number);
                }
                accountsCompensationCache.put(sagaId, new ArrayList<CompensationData>(){{add(accountsCompensationInfo);}});
                updateOperationStatus(sagaId,ONGOING);
                updateAccountNumberInLogs(sagaId, account_number);
            } else {
                updateOperationStatus(sagaId,FAILED);
            }

        } catch (OracleJsonException ex) {
            logger.error("Unable to parse payload", ex);
        }
        return account_number;
    }

    private String newInAccountsTable(OracleJsonObject account) {
        String newEntryBankAccount = "INSERT INTO bankB(ucid,account_number,account_type,balance_amount) VALUES (?,SEQ_ACCOUNT_NUMBER_BANK_B.NEXTVAL,?,0) RETURNING account_number INTO ?";
        int insertRslt = 0;
        long insertedId=-1;

                try (OraclePreparedStatement stmt1 = (OraclePreparedStatement) connection.prepareStatement(newEntryBankAccount)) {
                    stmt1.setString(1, account.get("ucid").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                    stmt1.setString(2, account.get("account_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));

                    stmt1.registerReturnParameter(3, OracleTypes.NUMBER);
                    insertRslt = stmt1.executeUpdate();
                    
                    if (insertRslt != 1) {
                        insertRslt = -1;
                    }
                    try (OracleResultSet rs = (OracleResultSet) stmt1.getReturnResultSet()) {
                        if (rs.next()) {
                            insertedId = rs.getLong(1); // Use getLong for NUMBER
                        }
                    }
                } catch (SQLException ex) {
                    logger.error("INSERT IN ACCOUNTS bank B ERROR", ex);
                }


                if(insertedId==-1){
                    return null;
                }else{
                    return String.valueOf(insertedId);
                }
    }

    public CreditResponse newCCAccount(String accountPayload, String sagaId) {

        Reader inputReader = new StringReader(accountPayload);
        OracleJsonFactory jsonFactory = new OracleJsonFactory();
        CreditResponse resp= null;

        try (OracleJsonParser parser = jsonFactory.createJsonTextParser(inputReader)) {
            parser.next();
            OracleJsonObject accountsJsonObj = parser.getObject();
            int statusInsert = insertInBook(accountsJsonObj, sagaId);

            if (statusInsert != -1 ) {
                resp = newInCCTable(accountsJsonObj);

                final CompensationData restaurantCompensationInfo = new CompensationData();
                restaurantCompensationInfo.setSagaId(sagaId);
                restaurantCompensationInfo.setUcid(accountsJsonObj.get("ucid").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                restaurantCompensationInfo.setOperation_type(accountsJsonObj.get("operation_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                if(resp!=null){
                    restaurantCompensationInfo.setAccount_number(resp.getAccountNumber());
                }
                accountsCompensationCache.put(sagaId, new ArrayList<CompensationData>(){{add(restaurantCompensationInfo);}});
                updateOperationStatus(sagaId,ONGOING);
                updateAccountNumberInLogs(sagaId, resp.getAccountNumber());
            } else {
                updateOperationStatus(sagaId,FAILED);
            }

        } catch (OracleJsonException ex) {
            logger.error("Unable to parse payload", ex);
        }
        return resp;
    }

    private CreditResponse newInCCTable(OracleJsonObject account) {
        String newEntryBankAccount = "INSERT INTO bankB(ucid,account_number,account_type,balance_amount) VALUES (?,SEQ_CREDIT_CARD_NUMBER_BANK_B.NEXTVAL,?,0) RETURNING account_number INTO ?";
        int insertRslt = 0;
        CreditResponse resp =null;

        try (OraclePreparedStatement stmt1 = (OraclePreparedStatement) connection.prepareStatement(newEntryBankAccount)) {
            stmt1.setString(1, account.get("ucid").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
            stmt1.setString(2, account.get("account_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));

            stmt1.registerReturnParameter(3, OracleTypes.NUMBER);
            insertRslt = stmt1.executeUpdate();

            try (OracleResultSet rs = (OracleResultSet) stmt1.getReturnResultSet()) {
                if (rs.next()) {
                    resp = new CreditResponse();
                    resp.setAccountNumber(String.valueOf(rs.getLong(1)));
                    resp.setCreditLimit("0");
                }
            }
        } catch (SQLException ex) {
            logger.error("INSERT IN ACCOUNTS bank B ERROR", ex);
        }

        return resp;
    }

    public boolean updateCreditLimit(String accountPayload, String sagaId) {

        Reader inputReader = new StringReader(accountPayload);
        OracleJsonFactory jsonFactory = new OracleJsonFactory();
        Boolean resp=Boolean.FALSE;

        try (OracleJsonParser parser = jsonFactory.createJsonTextParser(inputReader)) {
            parser.next();
            OracleJsonObject accountsJsonObj = parser.getObject();
            int statusInsert = insertInBook(accountsJsonObj, sagaId);

            if (statusInsert != -1 ) {
                resp = updateInCCTable(accountsJsonObj);

                if(resp!=Boolean.FALSE){
                    final CompensationData restaurantCompensationInfo = new CompensationData();
                    restaurantCompensationInfo.setSagaId(sagaId);
                    restaurantCompensationInfo.setUcid(accountsJsonObj.get("ucid").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                    restaurantCompensationInfo.setOperation_type(accountsJsonObj.get("operation_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                    accountsCompensationCache.put(sagaId, new ArrayList<CompensationData>(){{add(restaurantCompensationInfo);}});
                }
                updateOperationStatus(sagaId,ONGOING);
            } else {
                updateOperationStatus(sagaId,FAILED);
            }

        } catch (OracleJsonException ex) {
            logger.error("Unable to parse payload", ex);
        }
        return resp;
    }

    private boolean updateInCCTable(OracleJsonObject account) {
        String newEntryBankAccount = "UPDATE bankB SET balance_amount = balance_amount + ? where account_number = ?";
        int insertRslt = 0;
        CreditResponse resp =new CreditResponse();

        try (PreparedStatement stmt1 = connection.prepareStatement(newEntryBankAccount)) {

            stmt1.setDouble(1, Double.valueOf(account.get("balance_amount").toString().replaceAll(REG_EXP_REMOVE_QUOTES, "")));
            stmt1.setString(2, account.get("account_number").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));

            insertRslt = stmt1.executeUpdate();

            if(insertRslt!=1){
                insertRslt=-1;
            }
        } catch (SQLException ex) {
            logger.error("INSERT IN ACCOUNTS ERROR", ex);
        }

        return insertRslt>0;
    }

    public String depositMoney(String accountPayload, String sagaId) {

        Reader inputReader = new StringReader(accountPayload);
        OracleJsonFactory jsonFactory = new OracleJsonFactory();
        double balance_amount=-1.0;

        try (OracleJsonParser parser = jsonFactory.createJsonTextParser(inputReader)) {
            parser.next();
            OracleJsonObject accountsJsonObj = parser.getObject();
            int statusInsert = insertInBookTransfer(accountsJsonObj, sagaId);

            if (statusInsert != -1 ) {
                balance_amount = transactMoney(sagaId,accountsJsonObj);

                if(balance_amount!=-1.0){
                    final CompensationData accountsCompensationInfo = new CompensationData();
                    accountsCompensationInfo.setSagaId(sagaId);
                    accountsCompensationInfo.setUcid(accountsJsonObj.get("ucid").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                    accountsCompensationInfo.setOperation_type(accountsJsonObj.get("operation_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                    accountsCompensationCache.put(sagaId, new ArrayList<CompensationData>(){{add(accountsCompensationInfo);}});
                }else{
                    updateOperationStatus(sagaId,FAILED,accountsJsonObj.get("transaction_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                }
                updateOperationStatus(sagaId,ONGOING,accountsJsonObj.get("transaction_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
            }

        } catch (OracleJsonException ex) {
            logger.error("Unable to parse payload", ex);
        }
        return String.valueOf(balance_amount);
    }

    public String withdrawMoney(String accountPayload, String sagaId) {

        Reader inputReader = new StringReader(accountPayload);
        OracleJsonFactory jsonFactory = new OracleJsonFactory();
        double balance_amount=-1.0;

        try (OracleJsonParser parser = jsonFactory.createJsonTextParser(inputReader)) {
            parser.next();
            OracleJsonObject accountsJsonObj = parser.getObject();
            int statusInsert = insertInBookTransfer(accountsJsonObj, sagaId);

            if (statusInsert != -1 ) {
                if(validateUserWithdrawing(accountsJsonObj)){

                    balance_amount = transactMoney(sagaId,accountsJsonObj);

                    if(balance_amount!=-1.0){
                        final CompensationData accountsCompensationInfo = new CompensationData();
                        accountsCompensationInfo.setSagaId(sagaId);
                        accountsCompensationInfo.setUcid(accountsJsonObj.get("ucid").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                        accountsCompensationInfo.setOperation_type(accountsJsonObj.get("operation_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                        accountsCompensationCache.put(sagaId, new ArrayList<CompensationData>(){{add(accountsCompensationInfo);}});
                    }else{
                        updateOperationStatus(sagaId,FAILED,accountsJsonObj.get("transaction_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                    }

                }else{
                    updateOperationStatus(sagaId,FAILED,accountsJsonObj.get("transaction_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                }
                updateOperationStatus(sagaId,ONGOING,accountsJsonObj.get("transaction_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
            }

        } catch (OracleJsonException ex) {
            logger.error("Unable to parse payload", ex);
        }
        return String.valueOf(balance_amount);
    }

    public String transactIntraMoney(String accountPayload, String sagaId) {

        Reader inputReader = new StringReader(accountPayload);
        OracleJsonFactory jsonFactory = new OracleJsonFactory();
        double balance_amount_withdraw=-1.0;
        double balance_amount_deposit=-1.0;

        try (OracleJsonParser parser = jsonFactory.createJsonTextParser(inputReader)) {
            parser.next();
            OracleJsonObject accountsJsonObjOG = parser.getObject();

            OracleJsonObject accountsJsonObjWithdraw = new OracleJsonObjectImpl(accountsJsonObjOG);
            accountsJsonObjWithdraw.put("operation_type","WITHDRAW");
            accountsJsonObjWithdraw.put("transaction_type","DEBIT");
            OracleJsonObject accountsJsonObjDeposit = new OracleJsonObjectImpl(accountsJsonObjOG);
            accountsJsonObjDeposit.put("operation_type","DEPOSIT");
            accountsJsonObjDeposit.put("transaction_type","CREDIT");

            int statusInsert1 = insertInBookTransfer(accountsJsonObjWithdraw, sagaId);
            int statusInsert2 = insertInBookTransfer(accountsJsonObjDeposit, sagaId);

            if (statusInsert1 != -1  && statusInsert2 != -1) {
                balance_amount_deposit = transactMoney(sagaId,accountsJsonObjDeposit);

                if(balance_amount_deposit!=-1.0){
                    updateOperationStatus(sagaId,ONGOING,accountsJsonObjDeposit.get("transaction_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                }else{
                    updateOperationStatus(sagaId,FAILED,accountsJsonObjDeposit.get("transaction_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                }

                if(validateUserWithdrawing(accountsJsonObjWithdraw)){
                    balance_amount_withdraw = transactMoney(sagaId,accountsJsonObjWithdraw);
                    if(balance_amount_withdraw!=-1.0){
                        updateOperationStatus(sagaId,ONGOING,accountsJsonObjWithdraw.get("transaction_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                    }else{
                        updateOperationStatus(sagaId,FAILED,accountsJsonObjWithdraw.get("transaction_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                    }
                }else{
                    updateOperationStatus(sagaId,FAILED,accountsJsonObjWithdraw.get("transaction_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                }

                if(balance_amount_deposit == -1.0 || balance_amount_withdraw == -1.0){
                    return String.valueOf(balance_amount_deposit<=balance_amount_withdraw?balance_amount_deposit:balance_amount_withdraw);
                }

                final CompensationData accountsCompensationInfo = new CompensationData();
                accountsCompensationInfo.setSagaId(sagaId);
                accountsCompensationInfo.setUcid(accountsJsonObjOG.get("ucid").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                accountsCompensationInfo.setOperation_type(accountsJsonObjOG.get("operation_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                accountsCompensationCache.put(sagaId, new ArrayList<CompensationData>(){{add(accountsCompensationInfo);}});

                return String.valueOf(balance_amount_withdraw);
            }else{
                if(statusInsert1==-1 && statusInsert2 !=-1){
                    updateOperationStatus(sagaId,FAILED,accountsJsonObjDeposit.get("transaction_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                }
                if(statusInsert2==-1 && statusInsert1!=-1){
                    updateOperationStatus(sagaId,FAILED,accountsJsonObjWithdraw.get("transaction_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                }
            }


        } catch (OracleJsonException ex) {
            logger.error("Unable to parse payload", ex);
        }

        return String.valueOf(balance_amount_withdraw);
    }

    private boolean validateUserWithdrawing(OracleJsonObject accountsJsonObj) {

        String select_CC = "SELECT COUNT(*) from bankB where ucid = ? and account_number = ?";
        int rslt = 0;
        int count=-1;
        JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
        try (OraclePreparedStatement stmt = (OraclePreparedStatement) connection.prepareStatement(select_CC)) {
            stmt.setString(1, accountsJsonObj.get("ucid").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
            stmt.setString(2, accountsJsonObj.get("from_account_number").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));

            rslt = stmt.executeUpdate();

            try (ResultSet rs = stmt.getReturnResultSet()) {
                if (rs.next()) {
                    count= rs.getInt(1);
                }
            }

        } catch (OracleJsonException | SQLException ex) {
            logger.error("Validate Account bank B error", ex);
        }

        return count==1;
    }

    public String fetchCC(String accountPayload, String sagaId) {

        Reader inputReader = new StringReader(accountPayload);
        OracleJsonFactory jsonFactory = new OracleJsonFactory();
        String outJSON=null;

        try (OracleJsonParser parser = jsonFactory.createJsonTextParser(inputReader)) {
            parser.next();
            OracleJsonObject accountsJsonObj = parser.getObject();
            int statusInsert = insertInBook(accountsJsonObj, sagaId);

            if (statusInsert != -1 ) {
                outJSON = selectAllAccounts(accountsJsonObj);

                if(outJSON!=null){
                    final CompensationData accountsCompensationInfo = new CompensationData();
                    accountsCompensationInfo.setSagaId(sagaId);
                    accountsCompensationInfo.setUcid(accountsJsonObj.get("ucid").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                    accountsCompensationInfo.setOperation_type(accountsJsonObj.get("operation_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                    accountsCompensationCache.put(sagaId, new ArrayList<CompensationData>(){{add(accountsCompensationInfo);}});
                }
                updateOperationStatus(sagaId,ONGOING);
            } else {
                updateOperationStatus(sagaId,FAILED);
            }

        } catch (OracleJsonException ex) {
            logger.error("Unable to parse payload", ex);
        }
        return outJSON;
    }

    public String fetchBA(String accountPayload, String sagaId) {

        Reader inputReader = new StringReader(accountPayload);
        OracleJsonFactory jsonFactory = new OracleJsonFactory();
        String outJSON=null;

        try (OracleJsonParser parser = jsonFactory.createJsonTextParser(inputReader)) {
            parser.next();
            OracleJsonObject accountsJsonObj = parser.getObject();
            int statusInsert = insertInBook(accountsJsonObj, sagaId);

            if (statusInsert != -1 ) {
                outJSON = selectAllAccounts(accountsJsonObj);

                if(outJSON!=null){
                    final CompensationData accountsCompensationInfo = new CompensationData();
                    accountsCompensationInfo.setSagaId(sagaId);
                    accountsCompensationInfo.setUcid(accountsJsonObj.get("ucid").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                    accountsCompensationInfo.setOperation_type(accountsJsonObj.get("operation_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
                    accountsCompensationCache.put(sagaId, new ArrayList<CompensationData>(){{add(accountsCompensationInfo);}});
                }
                updateOperationStatus(sagaId,ONGOING);
            } else {
                updateOperationStatus(sagaId,FAILED);
            }

        } catch (OracleJsonException ex) {
            logger.error("Unable to parse payload", ex);
        }
        return outJSON;
    }

    public String selectAllAccounts(OracleJsonObject accountsJsonObj){
        String select_CC = "SELECT account_number, account_type, balance_amount, created_at from bankB where ucid = ? and account_type = ?";
        int rslt = 0;
        JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
        try (OraclePreparedStatement stmt = (OraclePreparedStatement) connection.prepareStatement(select_CC)) {
            stmt.setString(1, accountsJsonObj.get("ucid").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
            stmt.setString(2, accountsJsonObj.get("account_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));

            rslt = stmt.executeUpdate();

                try (ResultSet rs = stmt.getReturnResultSet()) {
                    if (rs.next()) {
                        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
                        jsonObjectBuilder.add("account_number", rs.getString("account_number"));
                        jsonObjectBuilder.add("account_type", rs.getString("account_type"));
                        jsonObjectBuilder.add("balance_amount", rs.getDouble("balance_amount"));
                        jsonObjectBuilder.add("created_at", rs.getTimestamp("created_at").toString());
                        jsonArrayBuilder.add(jsonObjectBuilder.build());
                    }
                }

        } catch (OracleJsonException | SQLException ex) {
            logger.error("FETCH CC error", ex);
        }

        if(rslt>0){
            return jsonArrayBuilder.build().toString();
        }else{
            JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
            jsonObjectBuilder.add("TNA", "0");
            return jsonObjectBuilder.build().toString();
        }
    }

    public static String viewAllAccounts(Connection connection, ViewBADTO payload, String type){
        String select_BAC = "SELECT account_number, account_type, balance_amount, created_at from bankB where ucid = ? and account_type = 'CHECKING'";
        String select_BAS = "SELECT account_number, account_type, balance_amount, created_at from bankB where ucid = ? and account_type = 'SAVING'";
        String select_CC = "SELECT account_number, account_type, balance_amount, created_at from bankB where ucid = ? and account_type = 'CREDIT_CARD'";
        int rslt = 0;
        JsonObjectBuilder jsonObjectBuilderP = Json.createObjectBuilder();


            switch(type){
                case "ALL":
                case "CHECKING":
                    try (PreparedStatement stmt1 = connection.prepareStatement(select_BAC)) {
                        JsonArrayBuilder jsonArrayBuilder1 = Json.createArrayBuilder();
                        stmt1.setString(1, payload.getUcid());

                        rslt = stmt1.executeUpdate();
                        try (ResultSet rs = stmt1.getResultSet()) {
                            while (rs.next()) {
                                JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
                                jsonObjectBuilder.add("account_number", rs.getString("account_number"));
                                jsonObjectBuilder.add("account_type", rs.getString("account_type"));
                                jsonObjectBuilder.add("balance_amount", rs.getDouble("balance_amount"));
                                jsonObjectBuilder.add("created_at", rs.getTimestamp("created_at").toString());
                                jsonArrayBuilder1.add(jsonObjectBuilder.build());
                            }
                        }
                        jsonObjectBuilderP.add("CHECKING",jsonArrayBuilder1.build());
                    } catch (OracleJsonException | SQLException ex) {
                        logger.error("FETCH Checking error", ex);
                    }

                    if(!type.equals("ALL")) break;

                case "SAVING":
                    try (PreparedStatement stmt2 = connection.prepareStatement(select_BAS)) {
                        JsonArrayBuilder jsonArrayBuilder2 = Json.createArrayBuilder();
                        stmt2.setString(1, payload.getUcid());

                        rslt = stmt2.executeUpdate();
                        try (ResultSet rs = stmt2.getResultSet()) {
                            while (rs.next()) {
                                JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
                                jsonObjectBuilder.add("account_number", rs.getString("account_number"));
                                jsonObjectBuilder.add("account_type", rs.getString("account_type"));
                                jsonObjectBuilder.add("balance_amount", rs.getDouble("balance_amount"));
                                jsonObjectBuilder.add("created_at", rs.getTimestamp("created_at").toString());
                                jsonArrayBuilder2.add(jsonObjectBuilder.build());
                            }
                        }
                        jsonObjectBuilderP.add("SAVING", jsonArrayBuilder2.build());
                    } catch (OracleJsonException | SQLException ex) {
                        logger.error("FETCH SAVING error", ex);
                    }
                    if(!type.equals("ALL")) break;

                case "CREDIT_CARD":
                    try (PreparedStatement stmt3 = connection.prepareStatement(select_CC)){
                    JsonArrayBuilder jsonArrayBuilder3 = Json.createArrayBuilder();

                    stmt3.setString(1, payload.getUcid());

                    rslt = stmt3.executeUpdate();
                    try (ResultSet rs =  stmt3.getResultSet()) {
                        while (rs.next()) {
                            JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
                            jsonObjectBuilder.add("account_number", rs.getString("account_number"));
                            jsonObjectBuilder.add("account_type", rs.getString("account_type"));
                            jsonObjectBuilder.add("balance_amount", rs.getDouble("balance_amount"));
                            jsonObjectBuilder.add("created_at", rs.getTimestamp("created_at").toString());
                            jsonArrayBuilder3.add(jsonObjectBuilder.build());
                        }
                    }

                    jsonObjectBuilderP.add("CREDIT_CARD",jsonArrayBuilder3.build());
                    } catch (OracleJsonException | SQLException ex) {
                        logger.error("FETCH CC error", ex);
                    }
                    if(!type.equals("ALL")) break;
                default:
                    break;

            }

        return jsonObjectBuilderP.build().toString();
    }


    public double transactMoney(String sagaId,OracleJsonObject accountsJsonObj){
        String withdraw = "UPDATE bankB set balance_amount = balance_amount - ? where account_number = ?";
        String deposit = "UPDATE bankB set balance_amount = balance_amount + ? where account_number = ?";
        int rslt = 0;
        Double final_balance = -1.0;
        String finalQuery="";
        Boolean isCredit = Boolean.FALSE;
        if(accountsJsonObj.get("transaction_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, "").equals("CREDIT")){
            finalQuery = deposit;
            isCredit = Boolean.TRUE;
        }else{
            finalQuery = withdraw;
        }

        try (OraclePreparedStatement stmt = (OraclePreparedStatement) connection.prepareStatement(finalQuery)) {
            if(isCredit){
                stmt.setDouble(1, Double.parseDouble(accountsJsonObj.get("amount").toString().replaceAll(REG_EXP_REMOVE_QUOTES, "")));
                stmt.setNUMBER(2, NUMBER.textToPrecisionNumber(accountsJsonObj.get("to_account_number").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""),true,3,false,3,null));
            }else{
                stmt.setDouble(1, Double.parseDouble(accountsJsonObj.get("amount").toString().replaceAll(REG_EXP_REMOVE_QUOTES, "")));
                stmt.setNUMBER(2, NUMBER.textToPrecisionNumber(accountsJsonObj.get("from_account_number").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""),true,3,false,3,null));
            }

            rslt = stmt.executeUpdate();

            if(rslt==1){

                String selectBalance = "SELECT balance_amount from bankB where account_number = ?";
                int rslt1 = 0;
                try (OraclePreparedStatement stmt1 = (OraclePreparedStatement) connection.prepareStatement(selectBalance)) {
                    if(isCredit){
                        stmt1.setNUMBER(1, NUMBER.textToPrecisionNumber(accountsJsonObj.get("to_account_number").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""),true,3,false,3,null));
                    }else{
                        stmt1.setNUMBER(1, NUMBER.textToPrecisionNumber(accountsJsonObj.get("from_account_number").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""),true,3,false,3,null));
                    }

                    rslt1 = stmt1.executeUpdate();

                    try (ResultSet rs = stmt1.getReturnResultSet()) {
                        if (rs.next()) {
                            final_balance = rs.getDouble("balance_amount");
                        }
                    }

                } catch (OracleJsonException | SQLException ex) {
                    logger.error("FETCH Balance Amount error", ex);
                }

            }


        } catch (OracleJsonException | SQLException ex) {
            logger.error("UPDATE STATUS from ACCOUNTS_BOOK error", ex);
            updateOperationStatus(sagaId, AccountsService.FAILED);
        }
        return final_balance;
    }

    public boolean updateOperationStatus(String sagaId, String state){
        String updateOperationStateBooking = "UPDATE bankB_book set operation_status = ? where SAGA_ID = ?";
        int rslt = 0;
        try (PreparedStatement stmt = connection.prepareStatement(updateOperationStateBooking)) {
            stmt.setString(1, state);
            stmt.setString(2, sagaId);

            rslt = stmt.executeUpdate();
        } catch (OracleJsonException | SQLException ex) {
            logger.error("UPDATE STATUS from ACCOUNTS_BOOK bank B error", ex);
        }
        return rslt > 0;
    }

    public boolean updateAccountNumberInLogs(String sagaId, String account_number){
        String updateOperationStateBooking = "UPDATE bankB_book set account_number = ? where SAGA_ID = ?";
        int rslt = 0;
        try (PreparedStatement stmt = connection.prepareStatement(updateOperationStateBooking)) {
            stmt.setString(1, account_number);
            stmt.setString(2, sagaId);

            rslt = stmt.executeUpdate();
        } catch (OracleJsonException | SQLException ex) {
            logger.error("UPDATE Account number in ACCOUNTS_BOOK bank B error", ex);
        }
        return rslt > 0;
    }

    public boolean updateOperationStatus(String sagaId, String state, String txnType){
        String updateOperationStateBooking = "UPDATE bankB_book set operation_status = ? where SAGA_ID = ? and transaction_type = ?";
        int rslt = 0;
        try (PreparedStatement stmt = connection.prepareStatement(updateOperationStateBooking)) {
            stmt.setString(1, state);
            stmt.setString(2, sagaId);
            stmt.setString(3, txnType);

            rslt = stmt.executeUpdate();
        } catch (OracleJsonException | SQLException ex) {
            logger.error("UPDATE STATUS from ACCOUNTS_BOOK bank B error", ex);
        }
        return rslt > 0;
    }

    private int insertInBook(OracleJsonObject account, String sagaId) {

        String insertCustomerInfo = "INSERT INTO bankB_book (saga_id, ucid, operation_type, transaction_type, transaction_amount, account_number,operation_status) VALUES (?,?,?,?,?,?,?)";
        int insertRslt = 0;
        try (PreparedStatement stmt = connection.prepareStatement(insertCustomerInfo)) {
            stmt.setString(1,sagaId);
            stmt.setString(2,account.get("ucid").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
            stmt.setString(3,account.get("operation_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
            stmt.setString(4,account.get("transaction_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
            if(!account.get("transaction_amount").toString().replaceAll(REG_EXP_REMOVE_QUOTES, "").equals("null")){
                stmt.setDouble(5,Double.parseDouble(account.get("transaction_amount").toString().replaceAll(REG_EXP_REMOVE_QUOTES, "")));
            }else{
                stmt.setDouble(5,Double.parseDouble("0.00"));
            }

            stmt.setString(6,account.get("account_number").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
            stmt.setString(7, PENDING);

            insertRslt = stmt.executeUpdate();

            if (insertRslt != 1) {
                insertRslt = -1;
            }

        } catch (SQLException ex) {
            logger.error("Insert accounts error", ex);
        }

        return insertRslt;
    }

    private int insertInBookTransfer(OracleJsonObject account, String sagaId) {

        String insertCustomerInfo = "INSERT INTO bankB_book (saga_id, ucid, operation_type, transaction_type, transaction_amount, account_number,operation_status) VALUES (?,?,?,?,?,?,?)";
        int insertRslt = 0;
        try (PreparedStatement stmt = connection.prepareStatement(insertCustomerInfo)) {
            stmt.setString(1,sagaId);
            stmt.setString(2,account.get("ucid").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
            stmt.setString(3,account.get("operation_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
            stmt.setString(4,account.get("transaction_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
            if(!account.get("amount").toString().replaceAll(REG_EXP_REMOVE_QUOTES, "").equals("null")){
                stmt.setDouble(5,Double.parseDouble(account.get("amount").toString().replaceAll(REG_EXP_REMOVE_QUOTES, "")));
            }else{
                stmt.setDouble(5,Double.parseDouble("0.00"));
            }
            if(account.get("operation_type").toString().replaceAll(REG_EXP_REMOVE_QUOTES, "").equalsIgnoreCase("deposit")){
                stmt.setString(6,account.get("to_account_number").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
            }else{
                stmt.setString(6,account.get("from_account_number").toString().replaceAll(REG_EXP_REMOVE_QUOTES, ""));
            }
            stmt.setString(7, PENDING);

            insertRslt = stmt.executeUpdate();

            if (insertRslt != 1) {
                insertRslt = -1;
            }

        } catch (SQLException ex) {
            logger.error("Insert accounts error", ex);
        }

        return insertRslt;
    }

    public boolean deleteAccount(CompensationData account) {
        String deleteAccount = "DELETE FROM bankB where ACCOUNT_NUMBER = ?";
        int rslt = 0;
        try (PreparedStatement stmt = connection.prepareStatement(deleteAccount)) {
            stmt.setString(1, account.getAccount_number());

            rslt = stmt.executeUpdate();
        } catch (OracleJsonException | SQLException ex) {
            logger.error("UNABLE TO DELETE ACCOUNT FROM ACCOUNTS TABLE bank B", ex);
        }
        return rslt > 0;
    }

    public static String getNotifications(Connection connection){


        String selectOngoingNotificationQuery = "SELECT saga_id,ucid,operation_type,operation_status from bankB_book where operation_status = 'ONGOING'";
        String getNotificationQuery = "UPDATE bankB_book SET read = 'TRUE' WHERE read = 'FALSE' and operation_status != 'PENDING' and operation_status != 'ONGOING' RETURNING saga_id,ucid,operation_type,operation_status into ?,?,?,?";
        int rowsAffected = 0;
        NotificationDTO resp ;
        JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();

        try (PreparedStatement stmt = connection.prepareStatement(selectOngoingNotificationQuery)) {

            stmt.executeUpdate();

            ResultSet rs = stmt.getResultSet();
            while (rs.next()) {
                resp = new NotificationDTO();
                resp.setSaga_id(rs.getString(1));
                resp.setUcid(rs.getString(2));
                resp.setOperation_type(rs.getString(3));
                resp.setOperation_status(rs.getString(4));

                jsonArrayBuilder.add(resp.toString());
            }
        } catch (Exception ex) {
            logger.error("Select Ongoing error", ex);
        }

        try (OraclePreparedStatement stmt1 = (OraclePreparedStatement) connection.prepareStatement(getNotificationQuery)) {

            stmt1.registerReturnParameter(1, OracleTypes.VARCHAR);
            stmt1.registerReturnParameter(2, OracleTypes.VARCHAR);
            stmt1.registerReturnParameter(3, OracleTypes.VARCHAR);
            stmt1.registerReturnParameter(4, OracleTypes.VARCHAR);

            rowsAffected = stmt1.executeUpdate();

            try (OracleResultSet rs = (OracleResultSet) stmt1.getReturnResultSet()) {
                while (rs.next()) {
                    resp = new NotificationDTO();
                    resp.setSaga_id(rs.getString(1));
                    resp.setUcid(rs.getString(2));
                    resp.setOperation_type(rs.getString(3));
                    resp.setOperation_status(rs.getString(4));

                    jsonArrayBuilder.add(resp.toString());
                }
            }
        } catch (SQLException ex) {
            logger.error("NOTIFICATION ERROR", ex);
        }

        return jsonArrayBuilder.build().toString();

    }

}
