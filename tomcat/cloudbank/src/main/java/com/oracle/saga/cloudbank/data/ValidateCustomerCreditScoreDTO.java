package com.oracle.saga.cloudbank.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ValidateCustomerCreditScoreDTO {


    public String getOssn() {
        return ossn;
    }

    public void setOssn(String ossn) {
        this.ossn = ossn;
    }

    public String getFull_name() {
        return full_name;
    }

    public void setFull_name(String full_name) {
        this.full_name = full_name;
    }

    private String ossn;
    private String full_name;

    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "";
        }
    }
}
