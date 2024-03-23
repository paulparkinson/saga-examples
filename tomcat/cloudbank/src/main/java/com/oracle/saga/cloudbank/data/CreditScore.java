package com.oracle.saga.cloudbank.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

public class CreditScore {

    private String credit_operation_type;

    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    private String ossn;
    private String ucid;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CreditScore)) return false;
        CreditScore that = (CreditScore) o;
        return Objects.equals(getCredit_operation_type(), that.getCredit_operation_type()) && Objects.equals(getOssn(), that.getOssn()) && Objects.equals(getUcid(), that.getUcid());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCredit_operation_type(), getOssn(), getUcid());
    }



    public String getCredit_operation_type() {
        return credit_operation_type;
    }

    public void setCredit_operation_type(String credit_operation_type) {
        this.credit_operation_type = credit_operation_type;
    }

    public String getOssn() {
        return ossn;
    }

    public void setOssn(String ossn) {
        this.ossn = ossn;
    }

    public String getUcid() {
        return ucid;
    }

    public void setUcid(String ucid) {
        this.ucid = ucid;
    }



}
