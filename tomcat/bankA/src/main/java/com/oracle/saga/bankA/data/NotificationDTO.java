package com.oracle.saga.bankA.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NotificationDTO {

    private String saga_id;

    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    private String ucid;
    private String operation_type;
    private String operation_status;

    public String getSaga_id() {
        return saga_id;
    }

    public void setSaga_id(String saga_id) {
        this.saga_id = saga_id;
    }

    public String getUcid() {
        return ucid;
    }

    public void setUcid(String ucid) {
        this.ucid = ucid;
    }

    public String getOperation_type() {
        return operation_type;
    }

    public void setOperation_type(String operation_type) {
        this.operation_type = operation_type;
    }

    public String getOperation_status() {
        return operation_status;
    }

    public void setOperation_status(String operation_status) {
        this.operation_status = operation_status;
    }
}
