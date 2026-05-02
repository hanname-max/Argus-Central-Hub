package com.argus.centralhub.common;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ResultData<T> implements Serializable {
    
    private int code;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    
    private ResultData() {
        this.timestamp = LocalDateTime.now();
    }
    
    public static <T> ResultData<T> success() {
        return success(null);
    }
    
    public static <T> ResultData<T> success(T data) {
        ResultData<T> result = new ResultData<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }
    
    public static <T> ResultData<T> success(String message, T data) {
        ResultData<T> result = new ResultData<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(message);
        result.setData(data);
        return result;
    }
    
    public static <T> ResultData<T> failed() {
        return failed(ResultCode.FAILED.getMessage());
    }
    
    public static <T> ResultData<T> failed(String message) {
        return failed(ResultCode.FAILED.getCode(), message);
    }
    
    public static <T> ResultData<T> failed(int code, String message) {
        ResultData<T> result = new ResultData<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
    
    public static <T> ResultData<T> failed(ResultCode resultCode) {
        ResultData<T> result = new ResultData<>();
        result.setCode(resultCode.getCode());
        result.setMessage(resultCode.getMessage());
        return result;
    }
    
    public static <T> ResultData<T> failed(ResultCode resultCode, String message) {
        ResultData<T> result = new ResultData<>();
        result.setCode(resultCode.getCode());
        result.setMessage(message);
        return result;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
