/**
 * Copyright (c) 2023 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.bankA.controller;

import java.io.Reader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Properties;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oracle.saga.bankA.data.BankCheckDTO;
import com.oracle.saga.bankA.data.ViewBADTO;
import com.oracle.saga.bankA.stubs.AccountsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.saga.bankA.data.CompensationData;
import com.oracle.saga.bankA.data.CreditResponse;
import com.oracle.saga.bankA.util.ConnectionPools;
import com.oracle.saga.bankA.util.PropertiesHelper;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import oracle.saga.annotation.*;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oracle.saga.SagaException;
import oracle.saga.SagaMessageContext;
import oracle.saga.SagaParticipant;
import oracle.sql.json.OracleJsonException;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonObject;
import oracle.sql.json.OracleJsonParser;

@Path("/")
@Singleton
@Participant(name = "BankA")
public class AccountsController extends SagaParticipant {

    private static final String REG_EXP_REMOVE_QUOTES = "(^\")|(\"$)";
    private static final String FAILURE = "{\"result\":\"failure\"}";
    private static final Logger logger = LoggerFactory.getLogger(AccountsController.class);
    private static final String STATUS = "status";

    CacheManager cacheManager;
    Cache<String, ArrayList> cache;



    public AccountsController() throws SagaException, SQLException {
        Properties p = PropertiesHelper.loadProperties();
        int cacheSize = Integer.parseInt(p.getProperty("cacheSize", "100000"));
        cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);
        cache = cacheManager.createCache("bankACompensationData",
                CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class,
                        ArrayList.class, ResourcePoolsBuilder.heap(cacheSize)));
    }


    @Override
    @PreDestroy
    public void close() {
        try {
            logger.debug("Shutting down Bank A Controller");
            super.close();
        } catch (SagaException e) {
            logger.error("Unable to shutdown Bank A initiator", e);
        }
    }

    @GET
    @Path("version")
    public Response getVersion() {
        return Response.status(Response.Status.OK.getStatusCode()).entity("1.0").build();
    }

    @Compensate
    public void onPostRollback(SagaMessageContext info) {
        Connection connection = null;

        try {
            connection = info.getConnection();
        } catch (SagaException e) {
            logger.error("Unable to get database connection for accounts service");
        }

        try {

            CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);
            Cache<String, ArrayList> cachedCompensationInfo = cacheManager
                    .getCache("bankACompensationData", String.class, ArrayList.class);

            if(cachedCompensationInfo.containsKey(info.getSagaId())){
                ArrayList<CompensationData> accountCompensationInfo = cachedCompensationInfo.get(info.getSagaId());
                AccountsService as =new AccountsService(connection,cacheManager);
                for(CompensationData account:accountCompensationInfo){
                    if(account.getOperation_type().equals("NEW_CREDIT_CARD") || account.getOperation_type().equals("NEW_BANK_ACCOUNT")){
                        Boolean check = as.deleteAccount(account);
                        if (!check) {
                            logger.error("Unable to remove account {} from accounts.", account.getAccount_number());
                        } else {
                            logger.debug("Account {} was successfully removed from accounts.",
                                    account.getAccount_number());
                        }
                    }
                    as.updateOperationStatus(info.getSagaId(), AccountsService.FAILED);
                }
            }

        } catch (Exception e) {
            logger.error("Bank  A Response", e);
        }
    }

    @BeforeComplete
    public void onPreCommit(SagaMessageContext info) {
        logger.debug("Before Commit(SMC) from {} for {}", info.getSender(), info.getSagaId());
        Connection connection = null;

        long start = System.currentTimeMillis();
        long end = 0;

        try {
            connection = info.getConnection();
        } catch (SagaException e) {
            logger.error("Unable to get database connection for accounts service");
        }

        try {
            AccountsService as =new AccountsService(connection, this.cacheManager);
            as.updateOperationStatus(info.getSagaId(),AccountsService.COMPLETED);

        } catch (Exception e) {
            logger.error("Bank A Response", e);
        }

        end = System.currentTimeMillis();
        logger.debug("Status of compensation, rt: {}", end - start);
    }

    @InviteToJoin
    public boolean onInviteToJoin(SagaMessageContext info) {
        logger.info("Joining saga: {}", info.getSagaId());
        return true;
    }

    @Complete
    public void onPostCommit(SagaMessageContext info) {
        logger.debug("After Commit from {} for {}", info.getSender(), info.getSagaId());
    }

    @BeforeCompensate
    public void onPreRollback(SagaMessageContext info) {
        logger.debug("Before Rollback from {} for {}", info.getSender(), info.getSagaId());
    }

    @POST
    @Path("viewAccounts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response viewAll(@QueryParam("invokeError") String invokeError, ViewBADTO payload) {
        
        Response response;
        ObjectMapper obj = new ObjectMapper();
        String details = null;

        try (Connection conn = ConnectionPools.getAccountsConnection();) {
            details = AccountsService.viewAllAccounts(conn, payload, "ALL");
        } catch (SQLException ex) {
            logger.error("Error viewing accounts!!!", ex);
        }

        response = Response.status(Response.Status.ACCEPTED).entity(details).build();

        logger.debug("The response: {}", response);
        return response;
    }

    @POST
    @Path("isInBank")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response viewAll(@QueryParam("invokeError") String invokeError, BankCheckDTO payload) {

        Response response;
        ObjectMapper obj = new ObjectMapper();
        Boolean isPresent = Boolean.FALSE;

        try (Connection conn = ConnectionPools.getAccountsConnection();) {
            isPresent = AccountsService.bankCheck(conn, payload);
        } catch (SQLException ex) {
            logger.error("Error viewing accounts!!!", ex);
        }
        response = Response.status(Response.Status.ACCEPTED).build();

        logger.debug("The response: {}", response);

        if(isPresent){
            response = Response.status(Response.Status.ACCEPTED).build();
        }else{
            response = Response.status(Response.Status.BAD_REQUEST).build();
        }
        return response;
    }

    @POST
    @Path("viewBAC")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response viewBAC(@QueryParam("invokeError") String invokeError, ViewBADTO payload) {

        Response response;
        ObjectMapper obj = new ObjectMapper();
        String details = null;

        try (Connection conn = ConnectionPools.getAccountsConnection();) {
            details = AccountsService.viewAllAccounts(conn, payload,"CHECKING");
        } catch (SQLException ex) {
            logger.error("Error viewing accounts!!!", ex);
        }

        response = Response.status(Response.Status.ACCEPTED).entity(details).build();

        logger.debug("The response: {}", response);
        return response;
    }

    @POST
    @Path("viewBAS")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response viewBAS(@QueryParam("invokeError") String invokeError, ViewBADTO payload) {

        Response response;
        ObjectMapper obj = new ObjectMapper();
        String details = null;

        try (Connection conn = ConnectionPools.getAccountsConnection();) {
            details = AccountsService.viewAllAccounts(conn, payload,"SAVING");
        } catch (SQLException ex) {
            logger.error("Error viewing accounts!!!", ex);
        }

        response = Response.status(Response.Status.ACCEPTED).entity(details).build();

        logger.debug("The response: {}", response);
        return response;
    }

    @POST
    @Path("viewCC")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response viewCC(@QueryParam("invokeError") String invokeError, ViewBADTO payload) {

        Response response;
        ObjectMapper obj = new ObjectMapper();
        String details = null;

        try (Connection conn = ConnectionPools.getAccountsConnection();) {
            details = AccountsService.viewAllAccounts(conn, payload,"CREDIT_CARD");
        } catch (SQLException ex) {
            logger.error("Error viewing accounts!!!", ex);
        }

        response = Response.status(Response.Status.ACCEPTED).entity(details).build();

        logger.debug("The response: {}", response);
        return response;
    }

    @GET
    @Path("notification")
    @Produces(MediaType.APPLICATION_JSON)
    public Response notification(@QueryParam("invokeError") String invokeError) {

        Response response;
        ObjectMapper obj = new ObjectMapper();
        String reply = null;

        try (Connection conn = ConnectionPools.getAccountsConnection();) {
            reply = AccountsService.getNotifications(conn);
        } catch (SQLException e) {
            logger.error("FETCH NOTIFICATIONS ERROR {}",e);
        }

        if(reply!=null) {
            ObjectNode rpayload = obj.createObjectNode();
            rpayload.put(STATUS, "Accepted");
            rpayload.put("data", reply);
            rpayload.put("participant", "bankA");
            response = Response.status(Response.Status.ACCEPTED).entity(rpayload.toString()).build();
            logger.debug("The response: {}", response);
        }else{
            response = Response.status(Response.Status.BAD_REQUEST).build();
        }

        return response;
    }


    @Request(sender = "CloudBank")
    public String onRequest(SagaMessageContext info) {

        Connection connection = null;

        String status = FAILURE;
        try {
            connection = info.getConnection();
        } catch (SagaException e) {
            logger.error("Unable to get database connection for Bank A", e);
        }

        AccountsService account;
        try {

            account = new AccountsService(connection, this.cacheManager);

            String accountsAction = parseAccountsAction(info.getPayload());

            switch (accountsAction) {
            case "new_bank_account":
                String newAccount = account.newBankAccount(info.getPayload(), info.getSagaId());

                if(newAccount!=null){
                    status = "{\"result\":\"success\",\"account_number\":\""+newAccount+"\"}";
                }
                break;
            case "new_credit_card":
                CreditResponse resp = account.newCCAccount(info.getPayload(), info.getSagaId());

                if(resp!=null){
                    status = "{\"result\":\"success\",\"cc_number\":\""+resp.getAccountNumber()+"\",\"credit_limit\":\""+resp.getCreditLimit()+"\"}";
                }
                break;
            case "new_credit_card_set_balance":
                Boolean state = account.updateCreditLimit(info.getPayload(), info.getSagaId());

                if(state){
                    status = "{\"result\":\"success\"}";
                }
                break;
            case "deposit":
                String depositStatus = account.depositMoney(info.getPayload(), info.getSagaId());
                status = "{\"result\":\"failure\",\"operation_type\":\"DEPOSIT\"}";
                if(Double.valueOf(depositStatus)!=-1){
                    status = "{\"result\":\"success\",\"balance_amount\":\""+depositStatus+"\",\"operation_type\":\"DEPOSIT\"}";
                }
                break;
            case "withdraw":
                String withdrawStatus = account.withdrawMoney(info.getPayload(), info.getSagaId());
                status = "{\"result\":\"failure\",\"operation_type\":\"WITHDRAW\"}";
                if(Double.valueOf(withdrawStatus)!=-1){
                    status = "{\"result\":\"success\",\"balance_amount\":\""+withdrawStatus+"\",\"operation_type\":\"WITHDRAW\"}";
                }
                break;
            case "transact":
                String transactionStatus = account.transactIntraMoney(info.getPayload(), info.getSagaId());
                status = "{\"result\":\"failure\",\"operation_type\":\"TRANSACT\"}";
                if(Double.valueOf(transactionStatus)!=-1){
                    status = "{\"result\":\"success\",\"balance_amount\":\""+transactionStatus+"\",\"operation_type\":\"TRANSACT\"}";
                }
                break;
            case "view_balance_cc":
                String jsonCC = account.fetchCC(info.getPayload(), info.getSagaId());
                if(jsonCC!=null){
                    JsonObject jsonObject = Json.createReader(new java.io.StringReader(jsonCC)).readObject();
                    JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder(jsonObject).add("result", "success");
                    JsonObject updatedJsonObject = jsonObjectBuilder.build();
                    status=updatedJsonObject.toString();
                }
                break;
            case "view_balance_ba":
                String jsonBA = account.fetchBA(info.getPayload(), info.getSagaId());
                if(jsonBA!=null){
                    JsonObject jsonObject = Json.createReader(new java.io.StringReader(jsonBA)).readObject();
                    JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder(jsonObject).add("result", "success");
                    JsonObject updatedJsonObject = jsonObjectBuilder.build();
                    status=updatedJsonObject.toString();
                }
                break;
            default:
                logger.error("Invalid Bank A action specified: {}", accountsAction);
            }
        } catch (Exception e) {
            logger.error("Unable to create new account in Bank A", e);
        }

        JsonObject jsonObject = Json.createReader(new java.io.StringReader(status)).readObject();
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder(jsonObject).add("participant", "BankA");
        JsonObject updatedJsonObject = jsonObjectBuilder.build();
        status=updatedJsonObject.toString();

        logger.info("RESPONSE {}", status);
        return status;
    }

    private String parseAccountsAction(String payload) {
        Reader inputReader = new StringReader(payload);
        OracleJsonFactory jsonFactory = new OracleJsonFactory();
        String accountsAction = "";

        try (OracleJsonParser parser = jsonFactory.createJsonTextParser(inputReader)) {
            parser.next();
            OracleJsonObject currentJsonObj = parser.getObject();
            accountsAction = currentJsonObj.get("operation_type").toString()
                    .replaceAll(REG_EXP_REMOVE_QUOTES, "").toLowerCase();
        } catch (OracleJsonException ex) {
            logger.error("Unable to parse payload", ex);
        }
        return accountsAction;
    }
}
