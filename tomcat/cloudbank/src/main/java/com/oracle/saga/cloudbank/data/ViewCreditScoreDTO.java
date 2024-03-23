package com.oracle.saga.cloudbank.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ViewCreditScoreDTO {

    private String ossn;

    public String getOssn() {
        return ossn;
    }

    public void setOssn(String ossn) {
        this.ossn = ossn;
    }

    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "";
        }
    }


}
