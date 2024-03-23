/**
 * Copyright (c) 2023 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.cloudbank.controller;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;

import com.oracle.saga.cloudbank.data.*;
import com.oracle.saga.cloudbank.stubs.Stubs;
import oracle.saga.annotation.*;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonObject;
import oracle.sql.json.OracleJsonParser;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oracle.saga.cloudbank.util.ConnectionPools;
import com.oracle.saga.cloudbank.util.PropertiesHelper;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import oracle.saga.Saga;
import oracle.saga.SagaException;
import oracle.saga.SagaInitiator;
import oracle.saga.SagaMessageContext;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Path("/")
@Singleton
@Participant(name = "CloudBank")
public class CloudBankController extends SagaInitiator {
    private static final Logger logger = LoggerFactory.getLogger(CloudBankController.class);
    private static final String STATUS = "status";
    private CacheManager cacheManager;

    private Cache<String, CloudBankSagaInfo> cloudBankSagaCache;
    private int maxStatusWait;
    private boolean finalize = true;
    public CloudBankController() throws SagaException, SQLException {
        Properties p = PropertiesHelper.loadProperties();
        this.finalize = Boolean.parseBoolean(p.getProperty("finalize", "true"));
        int cacheSize = Integer.parseInt(p.getProperty("cacheSize", "100000"));
        cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);
        cloudBankSagaCache = cacheManager.createCache("cloudBankSaga",
                CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class,
                        CloudBankSagaInfo.class, ResourcePoolsBuilder.heap(cacheSize)));
        this.maxStatusWait = Integer.parseInt(p.getProperty("maxStatusWait", "60000"));
    }

    @Override
    @PreDestroy
    public void close() {
        try {
            logger.debug("Shutting down CloudBank Controller");
            super.close();
        } catch (SagaException e) {
            logger.error("Unable to shutdown initiator", e);
        }
    }

    @GET
    @Path("version")
    public Response getVersion() {
        return Response.status(Response.Status.OK.getStatusCode()).entity("1.0").build();
    }


    @POST
    @Path("newCustomer")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response newCustomer(@QueryParam("invokeError") String invokeError, NewCustomerDTO payload) {
        Response response;
        ObjectMapper obj = new ObjectMapper();
        String login_id = null;
        Boolean validCustomer=Boolean.FALSE;

        ValidateCustomerCreditScoreDTO val =new ValidateCustomerCreditScoreDTO();
        val.setFull_name(payload.getFull_name());
        val.setOssn(payload.getOssn());

        try{
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(Stubs.URL_validate_customer_in_CreditScoreDB))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(val.toString()))
                .build();
            HttpResponse<String> apiResp = client.send(request, HttpResponse.BodyHandlers.ofString());

            if(Response.Status.ACCEPTED.getStatusCode() == apiResp.statusCode()){
                validCustomer=Boolean.TRUE;
            }
        } catch (Exception e) {
            System.err.println("Error during HTTP call: " + e.getMessage());
        }

        if(validCustomer){
            try (Connection conn = ConnectionPools.getCloudBankConnection();) {
                login_id = Stubs.createNewCustomer(conn, payload);
            } catch (SQLException ex) {
                logger.error("Error creating new customer!!!", ex);
            }
        }

        ObjectNode rpayload = obj.createObjectNode();
        if(login_id!=null){
            rpayload.put(STATUS, "Accepted");
            rpayload.put("login_id", login_id);
            response = Response.status(Response.Status.ACCEPTED).entity(rpayload.toString()).build();
        }else{
            rpayload.put(STATUS, "Rejected");
            if(!validCustomer){
                rpayload.put("reason", "NOT IN CREDIT SCORE DB.");
            }
            response = Response.status(Response.Status.BAD_REQUEST).entity(rpayload.toString()).build();
        }
        logger.debug("The response: {}", response);

        return response;
    }


    @POST
    @Path("login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(@QueryParam("invokeError") String invokeError, LoginDTO payload) {

        Response response;
        ObjectMapper obj = new ObjectMapper();
        LoginReplyDTO rplyQuery = null;
        Boolean loginStatus = Boolean.FALSE;
        String bank=null;

        try (Connection conn = ConnectionPools.getCloudBankConnection();) {
            rplyQuery = Stubs.login(conn, payload);
        } catch (SQLException ex) {
            logger.error("Error logging in!!!", ex);
        }
        if(rplyQuery!=null){

            JsonObject jsonObject1 = Json.createReader(new java.io.StringReader(rplyQuery.toString())).readObject();
            JsonObjectBuilder jsonObjectBuilderMain = Json.createObjectBuilder(jsonObject1);

            try (Connection conn = ConnectionPools.getCloudBankConnection();) {
                bank = Stubs.getBankBasedOnUCID(conn, payload.getId());
            } catch (SQLException e) {
                logger.error("FETCH OSSN ERROR {}",e);
            }

            if(bank!=null){
                try {
                    HttpClient client1 = HttpClient.newHttpClient();
                    ViewAllAccountsDTO pack1 = new ViewAllAccountsDTO();
                    pack1.setUcid(rplyQuery.getUcid());
                    HttpRequest request1 =null;
                    if(bank.equalsIgnoreCase("BankA")){
                        request1 = HttpRequest.newBuilder()
                                .uri(new URI(Stubs.URL_viewAllAccounts_Bank_A))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(pack1.toString()))
                                .build();
                    }else{
                        request1 = HttpRequest.newBuilder()
                                .uri(new URI(Stubs.URL_viewAllAccounts_Bank_B))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(pack1.toString()))
                                .build();
                    }
                    HttpResponse<String> apiResp1 = client1.send(request1, HttpResponse.BodyHandlers.ofString());

                    if(Response.Status.ACCEPTED.getStatusCode() == apiResp1.statusCode()){
                        JsonObject jsonObject2 = Json.createReader(new java.io.StringReader(apiResp1.body())).readObject();
                        JsonObjectBuilder jsonObjectBuildertemp = Json.createObjectBuilder(jsonObject2);
                        jsonObjectBuilderMain.addAll(jsonObjectBuildertemp);
                    }

                    HttpClient client2 = HttpClient.newHttpClient();
                    ViewCreditScoreDTO pack2 =new ViewCreditScoreDTO();
                    pack2.setOssn(rplyQuery.getOssn());
                    HttpRequest request2 = HttpRequest.newBuilder()
                            .uri(new URI(Stubs.URL_view_creditScore_in_CreditScoreDB))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(pack2.toString()))
                            .build();
                    HttpResponse<String> apiResp2 = client2.send(request2, HttpResponse.BodyHandlers.ofString());

                    if(Response.Status.ACCEPTED.getStatusCode() == apiResp2.statusCode()){
                        JsonObject jsonObject3 = Json.createReader(new java.io.StringReader(apiResp2.body())).readObject();
                        JsonObjectBuilder jsonObjectBuildertemp = Json.createObjectBuilder(jsonObject3);
                        jsonObjectBuilderMain.addAll(jsonObjectBuildertemp);
                    }

                    JsonObject finalJSON = jsonObjectBuilderMain.build();

                    ObjectNode rpayload = obj.createObjectNode();
                    rpayload.put(STATUS, "Accepted");
                    rpayload.put("data", finalJSON.toString());
                    response = Response.status(Response.Status.ACCEPTED).entity(rpayload.toString()).build();
                    logger.debug("The response: {}", response);
                } catch (Exception e1) {
                    logger.error("Login Viewing Error", e1);
                    response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
                    logger.debug("Status of {} returned.", Response.Status.INTERNAL_SERVER_ERROR);
                }
            }else{
                response = Response.status(Response.Status.FORBIDDEN).build();
            }

        }else{
            response = Response.status(Response.Status.FORBIDDEN).build();
        }

        return response;
    }

    @POST
    @Path("refresh")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response refresh(@QueryParam("invokeError") String invokeError, RefreshDTO payload) {

        Response response;
        ObjectMapper obj = new ObjectMapper();
        String bank =null;


        if(payload!=null){
            JsonObjectBuilder jsonObjectBuilderMain = Json.createObjectBuilder();

            try (Connection conn = ConnectionPools.getCloudBankConnection();) {
                bank = Stubs.getBankBasedOnUCID(conn, payload.getUcid());
            } catch (SQLException e) {
                logger.error("FETCH OSSN ERROR {}",e);
            }

            if(bank!=null){
                try {
                    HttpClient client1 = HttpClient.newHttpClient();
                    ViewAllAccountsDTO pack1 = new ViewAllAccountsDTO();
                    pack1.setUcid(payload.getUcid());
                    HttpRequest request1 =null;
                    if(bank.equalsIgnoreCase("BankA")){
                        request1 = HttpRequest.newBuilder()
                                .uri(new URI(Stubs.URL_viewAllAccounts_Bank_A))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(pack1.toString()))
                                .build();
                    }else{
                        request1 = HttpRequest.newBuilder()
                                .uri(new URI(Stubs.URL_viewAllAccounts_Bank_B))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(pack1.toString()))
                                .build();
                    }
                    HttpResponse<String> apiResp1 = client1.send(request1, HttpResponse.BodyHandlers.ofString());

                    if(Response.Status.ACCEPTED.getStatusCode() == apiResp1.statusCode()){
                        JsonObject jsonObject2 = Json.createReader(new java.io.StringReader(apiResp1.body())).readObject();
                        JsonObjectBuilder jsonObjectBuildertemp = Json.createObjectBuilder(jsonObject2);
                        jsonObjectBuilderMain.addAll(jsonObjectBuildertemp);
                    }

                    HttpClient client2 = HttpClient.newHttpClient();
                    ViewCreditScoreDTO pack2 =new ViewCreditScoreDTO();
                    pack2.setOssn(payload.getOssn());
                    HttpRequest request2 = HttpRequest.newBuilder()
                            .uri(new URI(Stubs.URL_view_creditScore_in_CreditScoreDB))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(pack2.toString()))
                            .build();
                    HttpResponse<String> apiResp2 = client2.send(request2, HttpResponse.BodyHandlers.ofString());

                    if(Response.Status.ACCEPTED.getStatusCode() == apiResp2.statusCode()){
                        JsonObject jsonObject3 = Json.createReader(new java.io.StringReader(apiResp2.body())).readObject();
                        JsonObjectBuilder jsonObjectBuildertemp = Json.createObjectBuilder(jsonObject3);
                        jsonObjectBuilderMain.addAll(jsonObjectBuildertemp);
                    }

                    JsonObject finalJSON = jsonObjectBuilderMain.build();

                    ObjectNode rpayload = obj.createObjectNode();
                    rpayload.put(STATUS, "Accepted");
                    rpayload.put("data", finalJSON.toString());
                    response = Response.status(Response.Status.ACCEPTED).entity(rpayload.toString()).build();
                    logger.debug("The response: {}", response);
                } catch (Exception e1) {
                    logger.error("Login Viewing Error", e1);
                    response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
                    logger.debug("Status of {} returned.", Response.Status.INTERNAL_SERVER_ERROR);
                }
            }else{
                response = Response.status(Response.Status.FORBIDDEN).build();
            }

        }else{
            response = Response.status(Response.Status.FORBIDDEN).build();
        }

        return response;
    }

    @GET
    @Path("notification")
    @Produces(MediaType.APPLICATION_JSON)
    public Response notification(@QueryParam("invokeError") String invokeError) {

        Response response;
        ObjectMapper obj = new ObjectMapper();
        String reply = null;

        try (Connection conn = ConnectionPools.getCloudBankConnection();) {
            reply = Stubs.getNotifications(conn);
        } catch (SQLException e) {
            logger.error("FETCH NOTIFICATIONS ERROR {}",e);
        }

            if(reply!=null) {
                ObjectNode rpayload = obj.createObjectNode();
                rpayload.put(STATUS, "Accepted");
                rpayload.put("data", reply);
                rpayload.put("participant", "cloudbank");
                response = Response.status(Response.Status.ACCEPTED).entity(rpayload.toString()).build();
                logger.debug("The response: {}", response);
            }else{
                response = Response.status(Response.Status.BAD_REQUEST).build();
            }

        return response;
    }

    @LRA(end = false)
    @POST
    @Path("newBankAccount")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response newBankAccount(@QueryParam("invokeError") String invokeError,
                                   @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId, Accounts payload) {
        logger.info("Creating new account with saga id: {}", lraId);
        Response response;
        ObjectMapper obj = new ObjectMapper();
        CloudBankSagaInfo sagaInfo = new CloudBankSagaInfo();
        String checkInvokeErrorParam = invokeError;
        sagaInfo.setInvokeError(checkInvokeErrorParam != null && checkInvokeErrorParam.contentEquals("true"));
        String sagaId = lraId.toString();
        String bank =null;

        try (Connection conn = ConnectionPools.getCloudBankConnection();) {
            bank = Stubs.getBankBasedOnUCID(conn, payload.getUcid());
        } catch (SQLException e) {
            logger.error("FETCH Bank ERROR {}",e);
        }

        try {
            Saga saga = this.getSaga(sagaId);
            logger.debug("New Bank Account saga id: {}", sagaId);
            sagaInfo.setSagaId(sagaId);
            sagaInfo.setNewBA(Boolean.TRUE);
            sagaInfo.setAccounts(Boolean.TRUE);
            sagaInfo.setRequestAccounts(payload);
            sagaInfo.setFrom_bank(bank);
            cloudBankSagaCache.put(sagaId, sagaInfo);
            logBookUpdateCLoudBank(sagaInfo, "PENDING", "NEW_ACCOUNT");

            if(bank!=null){
                if(bank.equalsIgnoreCase("BankA")){
                    saga.sendRequest("BankA", payload.toString());
                }else{
                    saga.sendRequest("BankB", payload.toString());
                }

            }

            ObjectNode finalPayload = obj.createObjectNode();
            finalPayload.put(STATUS, "Accepted");
            finalPayload.put("reason", "Your new account is being created. You will receive a notification once its created.");
            finalPayload.put("id", sagaId);
            response = Response.status(Response.Status.ACCEPTED).entity(finalPayload.toString()).build();
            logger.debug("The response: {}", response);
            logBookUpdateCLoudBank(sagaInfo, "ONGOING", "NEW_ACCOUNT");
        } catch (SagaException e1) {
            logger.error("NEW ACCOUNT CREATION ERROR", e1);
            response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            logger.debug("Status of {} returned.", Response.Status.INTERNAL_SERVER_ERROR);
            logBookUpdateCLoudBank(sagaInfo, "FAILED", "NEW_ACCOUNT");
        }
        return response;
    }

    @LRA(end = false)
    @POST
    @Path("newCreditCard")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response newCreditCard(@QueryParam("invokeError") String invokeError,
                                   @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId, Accounts payload) {
        logger.info("Creating new credit card with saga id: {}", lraId);
        Response response;
        ObjectMapper obj = new ObjectMapper();
        CloudBankSagaInfo sagaInfo = new CloudBankSagaInfo();
        String checkInvokeErrorParam = invokeError;
        sagaInfo.setInvokeError(checkInvokeErrorParam != null && checkInvokeErrorParam.contentEquals("true"));
        String sagaId = lraId.toString();
        try {
            Saga saga = this.getSaga(sagaId);
            logger.debug("New CreditCard saga id: {}", sagaId);
            sagaInfo.setSagaId(sagaId);
            sagaInfo.setNewCC(Boolean.TRUE);
            sagaInfo.setAccounts(Boolean.TRUE);
            sagaInfo.setRequestAccounts(payload);
            sagaInfo.setCreditScoreResponse(Boolean.FALSE);
            sagaInfo.setAccountsResponse(Boolean.FALSE);
            sagaInfo.setAccountsSecondResponse(Boolean.FALSE);

            logBookUpdateCLoudBank(sagaInfo, "PENDING", "NEW_CREDIT_CARD");
            String ossn = null, bank = null;

            try (Connection conn = ConnectionPools.getCloudBankConnection();) {
                ossn = Stubs.fetchOssnByUCID(conn, payload);
                bank = Stubs.getBankBasedOnUCID(conn, payload.getUcid());
            } catch (SQLException e) {
               logger.error("FETCH OSSN ERROR {}",e);
            }

            sagaInfo.setFrom_bank(bank);
            cloudBankSagaCache.put(sagaId, sagaInfo);

            if(ossn != null ){
                if(bank!=null){
                    if(bank.equalsIgnoreCase("BankA")){
                        saga.sendRequest("BankA", payload.toString());
                    }else{
                        saga.sendRequest("BankB", payload.toString());
                    }

                }
                ObjectNode creditscore_request = obj.createObjectNode();
                creditscore_request.put("credit_operation_type","credit_check");
                creditscore_request.put("ossn",ossn);
                creditscore_request.put("ucid",payload.getUcid());
                saga.sendRequest("CreditScore", creditscore_request.toString());

                ObjectNode finalPayload = obj.createObjectNode();
                finalPayload.put(STATUS, "Accepted");
                finalPayload.put("reason", "Your new credit card request is being processed. You will receive an update shortly.");
                finalPayload.put("id", sagaId);
                response = Response.status(Response.Status.ACCEPTED).entity(finalPayload.toString()).build();
                logger.debug("The response: {}", response);
                logBookUpdateCLoudBank(sagaInfo, "ONGOING", "NEW_CREDIT_CARD");

            }else{
                ObjectNode finalPayload = obj.createObjectNode();
                finalPayload.put(STATUS, "Rejected");
                finalPayload.put("reason", "Your new credit card request is denied. Ossn not available.");
                response = Response.status(Response.Status.FORBIDDEN).entity(finalPayload.toString()).build();
            }

        } catch (SagaException e1) {
            logger.error("NEW CREDIT CARD ERROR", e1);
            response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            logger.debug("Status of {} returned.", Response.Status.INTERNAL_SERVER_ERROR);
            logBookUpdateCLoudBank(sagaInfo, "FAILED", "NEW_CREDIT_CARD");
        }
        return response;
    }


    @LRA(end = false)
    @POST
    @Path("transfer")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response transfer(@QueryParam("invokeError") String invokeError,
                                   @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId, AccountTransferDTO payload) {
        logger.info("Starting new transfer with saga id: {}", lraId);
        Response response;
        ObjectMapper obj = new ObjectMapper();
        CloudBankSagaInfo sagaInfo = new CloudBankSagaInfo();
        String checkInvokeErrorParam = invokeError;
        sagaInfo.setInvokeError(checkInvokeErrorParam != null && checkInvokeErrorParam.contentEquals("true"));
        String sagaId = lraId.toString();
        try {
            Saga saga = this.getSaga(sagaId);
            logger.debug("New transfer saga id: {}", sagaId);
            sagaInfo.setSagaId(sagaId);
            sagaInfo.setAccTransfer(Boolean.TRUE);
            sagaInfo.setAccounts(Boolean.TRUE);
            sagaInfo.setAccountTransferPayload(payload);
            sagaInfo.setDepositResponse(Boolean.FALSE);
            sagaInfo.setWithdrawResponse(Boolean.FALSE);

            String bank = null;
            Boolean same = Boolean.FALSE;

            try (Connection conn = ConnectionPools.getCloudBankConnection();) {
                bank = Stubs.getBankBasedOnUCID(conn, payload.getUcid());
                same = Stubs.bankCompare(conn, bank,payload.getTo_account_number());
            } catch (SQLException e) {
                logger.error("FETCH BANK ERROR {}",e);
            }

            sagaInfo.setFrom_bank(bank);
            if(bank!=null){
                if(same){
                    sagaInfo.setTo_bank(bank);
                }else{
                    if(bank.equalsIgnoreCase("BankA")){
                        sagaInfo.setTo_bank("BankB");
                    }else{
                        sagaInfo.setTo_bank("BankA");
                    }
                }
            }
            cloudBankSagaCache.put(sagaId, sagaInfo);
            logBookUpdateCLoudBank(sagaInfo, "PENDING", "TRANSFER");

            try (Connection conn = ConnectionPools.getCloudBankConnection();) {
                if(Stubs.verifyUserForTransaction(conn,payload)){
                    JsonObject jsonObject = Json.createReader(new java.io.StringReader(payload.toString())).readObject();

                    if(same){
                        JsonObjectBuilder jsonObjectBuildertemp = Json.createObjectBuilder(jsonObject).add("operation_type","TRANSACT");
                        if(bank!=null){
                            if(bank.equalsIgnoreCase("BankA")){
                                saga.sendRequest("BankA", jsonObjectBuildertemp.build().toString());
                            }else{
                                saga.sendRequest("BankB", jsonObjectBuildertemp.build().toString());
                            }

                        }
                    }else{

                        if(bank!=null){
                            if(bank.equalsIgnoreCase("BankA")){
                                JsonObjectBuilder jsonObjectBuildertemp = Json.createObjectBuilder(jsonObject).add("operation_type","DEPOSIT").add("transaction_type","CREDIT");
                                saga.sendRequest("BankB", jsonObjectBuildertemp.build().toString());
                                JsonObjectBuilder jsonObjectBuildertemp1 = Json.createObjectBuilder(jsonObject).add("operation_type","WITHDRAW").add("transaction_type","DEBIT");
                                saga.sendRequest("BankA", jsonObjectBuildertemp1.build().toString());
                            }else{
                                JsonObjectBuilder jsonObjectBuildertemp = Json.createObjectBuilder(jsonObject).add("operation_type","DEPOSIT").add("transaction_type","CREDIT");
                                saga.sendRequest("BankA", jsonObjectBuildertemp.build().toString());
                                JsonObjectBuilder jsonObjectBuildertemp1 = Json.createObjectBuilder(jsonObject).add("operation_type","WITHDRAW").add("transaction_type","DEBIT");
                                saga.sendRequest("BankB", jsonObjectBuildertemp1.build().toString());
                            }
                        }
                    }
                }
            } catch (SQLException ex) {
                logger.error("Error starting new transfer!!!", ex);
            }

            ObjectNode finalPayload = obj.createObjectNode();
            finalPayload.put(STATUS, "Accepted");
            finalPayload.put("reason", "Transfer process started. You will be updated shortly.");
            finalPayload.put("id", sagaId);
            response = Response.status(Response.Status.ACCEPTED).entity(finalPayload.toString()).build();
            logger.debug("The response: {}", response);
            logBookUpdateCLoudBank(sagaInfo, "ONGOING", "TRANSFER");
        } catch (SagaException e1) {
            logger.error("TRANSFER ERROR", e1);
            response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            logger.debug("Status of {} returned.", Response.Status.INTERNAL_SERVER_ERROR);
            logBookUpdateCLoudBank(sagaInfo, "FAILED", "TRANSFER");
        }
        return response;
    }


    @Compensate
    public void onPostRollback(SagaMessageContext info) {
        logger.debug("After Rollback from {} for {}", info.getSender(), info.getSagaId());
        Saga saga = null;
        CloudBankSagaInfo sagaInfo = null;
        Cache<String, CloudBankSagaInfo> cachedSagaInfo = cacheManager
                .getCache("cloudBankSaga", String.class, CloudBankSagaInfo.class);
        try {
            sagaInfo = cachedSagaInfo.get(info.getSagaId());
            saga = info.getSaga();
        } catch (Exception e) {
            logger.error("Error in handling response", e);
        }

        if(sagaInfo!=null){
            if(sagaInfo.isNewBA()){
                logBookUpdateCLoudBank(sagaInfo, "FAILED", "NEW_ACCOUNT");
            }
            if(sagaInfo.isAccTransfer()){
                logBookUpdateCLoudBank(sagaInfo, "FAILED", "TRANSFER");
            }
            if(sagaInfo.isNewCC()){
                logBookUpdateCLoudBank(sagaInfo, "FAILED", "NEW_CREDIT_CARD");
            }
        }
    }

    @BeforeComplete
    public void onPreCommit(SagaMessageContext info) {
        logger.debug("Before Commit from {} for {}", info.getSender(), info.getSagaId());
    }

    @InviteToJoin
    public boolean onInviteToJoin(SagaMessageContext info) {
        logger.debug("CloudBank received invite to join saga {}", info.getSagaId());
        return true;
    }

    @Request(sender = ".*")
    public String onRequest(SagaMessageContext info) {
        return null;
    }

    @Complete
    public void onPostCommit(SagaMessageContext info) {
        logger.debug("After Commit from {} for {}", info.getSender(), info.getSagaId());
    }

    @BeforeCompensate
    public void onPreRollback(SagaMessageContext info) {
        logger.debug("Before Rollback from {} for {}", info.getSender(), info.getSagaId());
    }

    @oracle.saga.annotation.Response(sender = "BankA.*")
    public void onResponseBankA(SagaMessageContext info) {
        logger.info("Response(BankA) from {} for saga {}: {}", info.getSender(), info.getSagaId(),
                info.getPayload());
        handleResponse(info);
    }

    @oracle.saga.annotation.Response(sender = "BankB.*")
    public void onResponseBankB(SagaMessageContext info) {
        logger.info("Response(BankB) from {} for saga {}: {}", info.getSender(), info.getSagaId(),
                info.getPayload());
        handleResponse(info);
    }

    @oracle.saga.annotation.Response(sender = "CreditScore.*")
    public void onResponseCreditScore(SagaMessageContext info) {
        logger.info("Response(CreditScore) from {} for saga {}: {}", info.getSender(), info.getSagaId(),
                info.getPayload());
        handleResponse(info);
    }

    public void handleResponse(SagaMessageContext info) {
        Saga saga = null;
        CloudBankSagaInfo sagaInfo = null;
        Cache<String, CloudBankSagaInfo> cachedSagaInfo = cacheManager
                .getCache("cloudBankSaga", String.class, CloudBankSagaInfo.class);
        try {
            sagaInfo = cachedSagaInfo.get(info.getSagaId());
            if(!sagaInfo.isRollbackPerformed()){
                saga = info.getSaga();
            }else{
                logger.error("Saga Already Rolled Back", info.getSagaId());
            }
        } catch (Exception e) {
            logger.error("Error in handling response", e);
        }

        if (saga != null && sagaInfo != null) {
            sagaInfo.addReply(info.getSender());
            Reader inputReader = new StringReader(info.getPayload());
            OracleJsonFactory factory = new OracleJsonFactory();
            try (OracleJsonParser parser = factory.createJsonTextParser(inputReader)) {
                parser.next();
                OracleJsonObject currentJsonObj = parser.getObject();
                String result = currentJsonObj.get("result").toString().replaceAll("(^\")|(\"$)","");

                if (!result.equals("success") ) {
                    if(!sagaInfo.isRollbackPerformed()){
                        try {
                            logger.info("Rollingback Saga [{}]", info.getSagaId());
                            saga.rollbackSaga();
                            sagaInfo.setRollbackPerformed(true);
                        } catch (SagaException e) {
                            logger.error("Unable to rollback after encountering error in response", e);
                        }
                    }else{
                        logger.info("Saga {} Already Rolled Back", info.getSagaId());
                    }
                }
                if(!sagaInfo.isRollbackPerformed()){
                    if(info.getSender().equalsIgnoreCase("BankA") || info.getSender().equalsIgnoreCase("BankB")){
                        if(sagaInfo.isAccountsResponse()){
                            sagaInfo.setAccountsSecondResponse(true);
                        }
                        sagaInfo.setAccountsResponse(true);
                    } else if (info.getSender().equalsIgnoreCase("creditscore")) {
                        sagaInfo.setCreditScoreResponse(true);
                    }
                }

                if(sagaInfo.isAccountsResponse() && sagaInfo.isNewBA() && !sagaInfo.isRollbackPerformed()){

                    try {
                        if (!sagaInfo.isInvokeError()) {
                            logger.info("Committing Saga [{}]", info.getSagaId());
                            saga.commitSaga();
                            logBookUpdateCLoudBank(sagaInfo, "COMPLETED", "NEW_ACCOUNT");
                        } else {
                            logger.info("Intentionally Causing a Rollback for Saga [{}]",
                                    info.getSagaId());
                            saga.rollbackSaga();
                            sagaInfo.setRollbackPerformed(true);
                            logBookUpdateCLoudBank(sagaInfo, "FAILED", "NEW_ACCOUNT");
                        }
                    } catch (SagaException e) {
                        logger.error("Unable to finalize", e);
                    }

                }else{
                    logger.debug("{}: replies:{}, getInvokeError[{}] getRollbackPerformed[{}] ",
                            info.getSagaId(), sagaInfo.getReplies(), sagaInfo.isInvokeError(),
                            sagaInfo.isRollbackPerformed());
                }

                if(sagaInfo.isAccountsResponse() && sagaInfo.isAccTransfer() && !sagaInfo.isRollbackPerformed()){
                    String operation_type = currentJsonObj.get("operation_type").toString().replaceAll("(^\")|(\"$)","");
                    if(operation_type.equals("DEPOSIT")){
                        sagaInfo.setDepositResponse(Boolean.TRUE);
                    }else if (operation_type.equals("WITHDRAW")){
                        sagaInfo.setWithdrawResponse(Boolean.TRUE);
                    }else if( operation_type.equalsIgnoreCase("TRANSACT")){
                        sagaInfo.setDepositResponse(Boolean.TRUE);
                        sagaInfo.setWithdrawResponse(Boolean.TRUE);
                    }

                    if(sagaInfo.getDepositResponse() && sagaInfo.getWithdrawResponse()){
                        try {
                            if (!sagaInfo.isInvokeError()) {
                                logger.info("Committing Saga [{}]", info.getSagaId());
                                saga.commitSaga();
                                logBookUpdateCLoudBank(sagaInfo, "COMPLETED", "TRANSFER");
                            } else {
                                logger.info("Intentionally Causing a Rollback for Saga [{}]",
                                        info.getSagaId());
                                saga.rollbackSaga();
                                sagaInfo.setRollbackPerformed(true);
                                logBookUpdateCLoudBank(sagaInfo, "FAILED", "TRANSFER");
                            }
                        } catch (SagaException e) {
                            logger.error("Unable to finalize", e);
                        }
                    }
                }else{
                    logger.debug("{}: replies:{}, getInvokeError[{}] getRollbackPerformed[{}] ",
                            info.getSagaId(), sagaInfo.getReplies(), sagaInfo.isInvokeError(),
                            sagaInfo.isRollbackPerformed());
                }

                if(sagaInfo.isCreditScoreResponse() && sagaInfo.isNewCC() && !sagaInfo.isAccountsResponse() && !sagaInfo.isRollbackPerformed() && !sagaInfo.isAccountsSecondResponse()){
                    logger.info("Credit Score Fetched. Waiting for Credit Card Creation.");
                    sagaInfo.setCreditScoreResponse(info.getPayload());
                }

                if(sagaInfo.isAccountsResponse() && sagaInfo.isNewCC() && !sagaInfo.isRollbackPerformed() && !sagaInfo.isCreditScoreResponse() && !sagaInfo.isAccountsSecondResponse()){
                    logger.info("Credit Card Created. Waiting for Credit Score Validation.");
                    sagaInfo.setAccountResponse(info.getPayload());
                }

                if(sagaInfo.isCreditScoreResponse() && sagaInfo.isNewCC() && sagaInfo.isAccountsResponse() && !sagaInfo.isRollbackPerformed() && sagaInfo.isAccountsSecondResponse()){
                    try {
                        if (!sagaInfo.isInvokeError()) {
                            logger.info("Committing Saga [{}]", info.getSagaId());
                            saga.commitSaga();
                            logBookUpdateCLoudBank(sagaInfo, "COMPLETED", "NEW_CREDIT_CARD");
                        } else {
                            logger.info("Intentionally Causing a Rollback for Saga [{}]",
                                    info.getSagaId());
                            saga.rollbackSaga();
                            sagaInfo.setRollbackPerformed(true);
                            logBookUpdateCLoudBank(sagaInfo, "FAILED", "NEW_CREDIT_CARD");
                        }
                    } catch (SagaException e) {
                        logger.error("Unable to finalize", e);
                    }
                }

                if(sagaInfo.isCreditScoreResponse() && sagaInfo.isNewCC() && sagaInfo.isAccountsResponse() && !sagaInfo.isRollbackPerformed() && !sagaInfo.isAccountsSecondResponse()){

                    Accounts payload = sagaInfo.getRequestAccounts();

                    if(sagaInfo.getCreditScoreResponse()==null){
                        sagaInfo.setCreditScoreResponse(info.getPayload());
                    }
                    String balance = Stubs.setBalanceNewCC(sagaInfo.getCreditScoreResponse());
                    if(balance==null){
                        saga.rollbackSaga();
                        sagaInfo.setRollbackPerformed(true);
                        logBookUpdateCLoudBank(sagaInfo, "FAILED", "NEW_CREDIT_CARD");
                    }else{
                        if(sagaInfo.getAccountResponse()==null){
                            sagaInfo.setAccountResponse(info.getPayload());
                        }
                        Reader accountsResponse = new StringReader(sagaInfo.getAccountResponse());
                        OracleJsonFactory factoryAccountsResponse = new OracleJsonFactory();
                        try (OracleJsonParser parserAccountsResponse = factoryAccountsResponse.createJsonTextParser(accountsResponse)) {
                            parserAccountsResponse.next();
                            OracleJsonObject savedAccountsResponse = parserAccountsResponse.getObject();
                            payload.setAccount_number(savedAccountsResponse.get("cc_number").toString().replaceAll("(^\")|(\"$)",""));
                        }catch (Exception e){
                            logger.error("Unknown error", e);
                        }
                        payload.setOperation_type("NEW_CREDIT_CARD_SET_BALANCE");
                        payload.setBalance_amount(balance);

                        if(sagaInfo.getFrom_bank().equalsIgnoreCase("BankA")){
                            saga.sendRequest("BankA", payload.toString());
                        }else{
                            saga.sendRequest("BankB", payload.toString());
                        }
                    }
                }


            } catch (Exception e1) {
                logger.error("Unknown error", e1);
                try {
                    saga.rollbackSaga();
                } catch (SagaException e) {
                    logger.error("Unable to rollback after encountering error", e);
                }
                cachedSagaInfo.remove(info.getSagaId());
            }
        } else {
            if (saga == null) {
                logger.error("Saga is null for: {} ", info.getSagaId());
            }
            if (sagaInfo == null) {
                logger.error("SagaInfo is null for: {} ", info.getSagaId());
            }
        }
    }

    private void logBookUpdateCLoudBank(CloudBankSagaInfo sagaInfo, String state, String operation_type) {

        String queryInsert = "INSERT INTO cloudbank_book (saga_id, ucid, operation_type, operation_status, created_at, transfer_type) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP,?)";
        String queryUpdate = "UPDATE cloudbank_book set operation_status = ? where saga_id = ? ";

        try (Connection conn = ConnectionPools.getCloudBankConnection()){

            if(state.equals("PENDING")){
                try (PreparedStatement insertStmt = conn.prepareStatement(queryInsert)) {
                    insertStmt.setString(1, sagaInfo.getSagaId());
                    if(operation_type.equals("NEW_ACCOUNT")){
                        insertStmt.setString(2, sagaInfo.getRequestAccounts().getUcid());
                    }else if(operation_type.equals("TRANSFER")){
                        insertStmt.setString(2, sagaInfo.getAccountTransferPayload().getUcid());
                    }else if(operation_type.equals("NEW_CREDIT_CARD")){
                        insertStmt.setString(2, sagaInfo.getRequestAccounts().getUcid());
                    }

                    insertStmt.setString(3, operation_type);
                    insertStmt.setString(4, state);
                    if(operation_type.equalsIgnoreCase("TRANSFER")){
                        if(sagaInfo.getFrom_bank().equalsIgnoreCase(sagaInfo.getTo_bank())){
                            insertStmt.setString(5, "INTRA-BANK");
                        }else{
                            insertStmt.setString(5, "INTER-BANK");
                        }
                    }else{
                        insertStmt.setString(5, "null");
                    }

                    int rowsAffected = insertStmt.executeUpdate();

                    if(rowsAffected!=1){
                        logger.error("Unable to create new entry in cloudbank_book");
                    }
                }
            }else {
                try (PreparedStatement updateStmt = conn.prepareStatement(queryUpdate)) {
                    updateStmt.setString(1, state);
                    updateStmt.setString(2, sagaInfo.getSagaId());

                    int rowsAffected = updateStmt.executeUpdate();

                    if(rowsAffected!=1){
                        logger.error("Unable to update sags status in cloudbank_book");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Connection error logbookUpdateCloudBank", e);
        }
    }
}
