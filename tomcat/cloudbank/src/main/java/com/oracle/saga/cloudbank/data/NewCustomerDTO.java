package com.oracle.saga.cloudbank.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

public class NewCustomerDTO {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NewCustomerDTO)) return false;
        NewCustomerDTO that = (NewCustomerDTO) o;
        return Objects.equals(getFull_name(), that.getFull_name()) && Objects.equals(getAddress(), that.getAddress()) && Objects.equals(getPhone(), that.getPhone()) && Objects.equals(getEmail(), that.getEmail()) && Objects.equals(getOssn(), that.getOssn()) && Objects.equals(getPassword(), that.getPassword());

    }

    public String getFull_name() {
        return full_name;
    }

    public void setFull_name(String full_name) {
        this.full_name = full_name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOssn() {
        return ossn;
    }

    public void setOssn(String ossn) {
        this.ossn = ossn;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    private String full_name;
    private String address;
    private String phone;
    private String email;
    private String ossn;
    private String password;

    public String getBank() {
        return bank;
    }

    public void setBank(String bank) {
        this.bank = bank;
    }

    private String bank;


}
