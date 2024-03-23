package com.oracle.saga.cloudbank.stubs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oracle.saga.cloudbank.controller.CloudBankController;
import com.oracle.saga.cloudbank.data.*;
import jakarta.ws.rs.core.Response;
import oracle.jdbc.OraclePreparedStatement;
import oracle.jdbc.OracleResultSet;
import oracle.jdbc.OracleTypes;
import oracle.sql.json.OracleJsonException;
import oracle.sql.json.OracleJsonFactory;
import oracle.sql.json.OracleJsonObject;
import oracle.sql.json.OracleJsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Stubs {

    private static final Logger logger = LoggerFactory.getLogger(CloudBankController.class);

    public static final String URL_validate_customer_in_CreditScoreDB = "http://0.0.0.0:8082/creditscore/validateCreditScore";
    public static final String URL_view_creditScore_in_CreditScoreDB = "http://0.0.0.0:8082/creditscore/viewCreditScore";
    public static final String URL_viewAllAccounts_Bank_A = "http://0.0.0.0:8082/bankA/viewAccounts";
    public static final String URL_verifyAccount_Bank_A = "http://0.0.0.0:8082/bankA/isInBank";
    public static final String URL_viewAllAccounts_Bank_B = "http://0.0.0.0:8082/bankB/viewAccounts";




    public static String createNewCustomer(Connection connection, NewCustomerDTO payload) {

        String insertCustomerInfo = "INSERT INTO cloudbank_customer (customer_id, password, full_name, address, phone, email, ossn, bank) VALUES (SEQ_CLOUDBANK_CUSTOMER_ID.NEXTVAL,?,?,?,?,?,?,?) RETURNING customer_id into ?";
        int insertRslt = 0;
        String customer_id=null;
        try (OraclePreparedStatement stmt = (OraclePreparedStatement) connection.prepareStatement(insertCustomerInfo)) {
            stmt.setString(1, payload.getPassword());
            stmt.setString(2, payload.getFull_name());
            stmt.setString(3, payload.getAddress());
            stmt.setString(4, payload.getPhone());
            stmt.setString(5, payload.getEmail());
            stmt.setString(6, payload.getOssn());
            stmt.setString(7, payload.getBank());

            stmt.registerReturnParameter(8, OracleTypes.VARCHAR);

            insertRslt = stmt.executeUpdate();

            try (OracleResultSet rs = (OracleResultSet) stmt.getReturnResultSet()) {
                if (rs.next()) {
                    customer_id = rs.getString(1);
                }
            }


            if (insertRslt != 1) {
                insertRslt = -1;
            }

        } catch (SQLException ex) {
            logger.error("Insert customer error", ex);
        }

        return customer_id;

    }

    public static LoginReplyDTO login(Connection connection, LoginDTO payload) {

        String query = "SELECT customer_id, full_name, address, phone, email, ossn, bank from cloudbank_customer where customer_id = ? and password = ?";
        LoginReplyDTO reply = null;

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, payload.getId());
            stmt.setString(2, payload.getPwd());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getResultSet()) {
                if (rs.next()) {
                    reply=new LoginReplyDTO();

                    reply.setUcid(rs.getString("customer_id"));
                    reply.setFull_name(rs.getString("full_name"));
                    reply.setAddress(rs.getString("address"));
                    reply.setPhone(rs.getString("phone"));
                    reply.setEmail(rs.getString("email"));
                    reply.setOssn(rs.getString("ossn"));
                    reply.setBank(rs.getString("bank"));
                }
            }
        } catch (Exception ex) {
            logger.error("Login error", ex);
        }

        return reply;
    }

    public static boolean verifyUserForTransaction(Connection connection,AccountTransferDTO payload) {

        String query = "SELECT count(*) from cloudbank_customer where customer_id = ? and password = ?";
        LoginReplyDTO reply = null;
        Boolean validateStatus =Boolean.FALSE;

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, payload.getUcid());
            stmt.setString(2, payload.getPassword());

            stmt.executeUpdate();

            ResultSet rs = stmt.getResultSet();
            if (rs.next()) {
                int count = rs.getInt(1);
                if(count==1){
                    validateStatus=Boolean.TRUE;
                }
            }
        } catch (Exception ex) {
            logger.error("Validation Error", ex);
        }

        return validateStatus;

    }

    public static String getNotifications(Connection connection){


        String selectOngoingNotificationQuery = "SELECT saga_id,ucid,operation_type,operation_status from cloudbank_book where operation_status = 'ONGOING'";
        String getNotificationQuery = "UPDATE cloudbank_book SET read = 'TRUE' WHERE read = 'FALSE' and operation_status != 'PENDING' and operation_status != 'ONGOING' RETURNING saga_id,ucid,operation_type,operation_status into ?,?,?,?";
        int rowsAffected = 0;
        NotificationDTO resp ;
        JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();

//        try (PreparedStatement stmt = connection.prepareStatement(selectOngoingNotificationQuery)) {
//
//            stmt.executeUpdate();
//
//            ResultSet rs = stmt.getResultSet();
//            while (rs.next()) {
//                resp = new NotificationDTO();
//                resp.setSaga_id(rs.getString(1));
//                resp.setUcid(rs.getString(2));
//                resp.setOperation_type(rs.getString(3));
//                resp.setOperation_status(rs.getString(4));
//
//                jsonArrayBuilder.add(resp.toString());
//            }
//        } catch (Exception ex) {
//            logger.error("Select Ongoing error", ex);
//        }

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

    public static String fetchOssnByUCID(Connection connection,Accounts payload) {

        String select_CC = "SELECT ossn from cloudbank_customer where customer_id = ?";
        int rslt = 0;
        String ossn =null;
        try (OraclePreparedStatement stmt = (OraclePreparedStatement) connection.prepareStatement(select_CC)) {
            stmt.setString(1, payload.getUcid());

            rslt = stmt.executeUpdate();

            try (ResultSet rs = stmt.getReturnResultSet()) {
                if (rs.next()) {
                    ossn = rs.getString("ossn");
                }
            }

        } catch (OracleJsonException | SQLException ex) {
            logger.error("FETCH OSSN error", ex);
        }
        return ossn;
    }


    public static String setBalanceNewCC(String creditScoreResponse) {

        Reader inputReader = new StringReader(creditScoreResponse);
        OracleJsonFactory factory = new OracleJsonFactory();
        try (OracleJsonParser parser = factory.createJsonTextParser(inputReader)) {
            parser.next();
            OracleJsonObject currentJsonObj = parser.getObject();
            int creditscore = Integer.parseInt(currentJsonObj.get("credit_score").toString().replaceAll("(^\")|(\"$)",""));

            if(creditscore<=650){
                return null;
            }else if(creditscore>650 && creditscore<=720){
                return "2000.00";
            }else if(creditscore>720 && creditscore<=780){
                return "5000.00";
            }else if(creditscore>780 && creditscore<=820){
                return "10000.00";
            }else{
                return "20000.00";
            }

        }catch (Exception e){
            logger.error("Unknown error", e);
        }
        return null;
    }

    public static String getBankBasedOnUCID(Connection connection, String ucid){
        String bankQ = "SELECT bank from cloudbank_customer where customer_id = ?";
        int rslt = 0;
        String bank =null;
        try (OraclePreparedStatement stmt = (OraclePreparedStatement) connection.prepareStatement(bankQ)) {
            stmt.setString(1, ucid);

            rslt = stmt.executeUpdate();

            try (ResultSet rs = stmt.getReturnResultSet()) {
                if (rs.next()) {
                    bank = rs.getString("bank");
                }
            }

        } catch (OracleJsonException | SQLException ex) {
            logger.error("FETCH OSSN error", ex);
        }
        return bank;
    }

    public static Boolean bankCompare(Connection connection, String bank, String toAccountNumber) {

        HttpClient client1 = HttpClient.newHttpClient();
        HttpRequest request1 =null;

        ObjectMapper obj = new ObjectMapper();
        ObjectNode rpayload = obj.createObjectNode();
        rpayload.put("account_number", toAccountNumber);
        HttpResponse<String> apiResp1 =null;

        try {
            request1 = HttpRequest.newBuilder()
                    .uri(new URI(Stubs.URL_verifyAccount_Bank_A))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(rpayload.toString()))
                    .build();
            apiResp1 = client1.send(request1, HttpResponse.BodyHandlers.ofString());
        }catch (Exception e){
            logger.error("bank compare error");
        }
        String bankTo = null;
        if(apiResp1.statusCode() == Response.Status.ACCEPTED.getStatusCode()){
            bankTo = "BankA";
        }else{
            bankTo = "BankB";
        }

        return bank.equalsIgnoreCase(bankTo);

    }

}
