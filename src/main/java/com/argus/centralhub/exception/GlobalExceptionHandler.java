package com.argus.centralhub.exception;

import com.argus.centralhub.common.ResultCode;
import com.argus.centralhub.common.ResultData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(BusinessException.class)
    public ResultData<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResultData.failed(e.getCode(), e.getMessage());
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultData<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return ResultData.failed(ResultCode.VALIDATE_FAILED.getCode(), message);
    }
    
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultData<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败: {}", message);
        return ResultData.failed(ResultCode.VALIDATE_FAILED.getCode(), message);
    }
    
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultData<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        String message = "参数缺失: " + e.getParameterName();
        log.warn(message);
        return ResultData.failed(ResultCode.VALIDATE_FAILED.getCode(), message);
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultData<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String message = "参数类型不匹配: " + e.getName();
        log.warn(message);
        return ResultData.failed(ResultCode.VALIDATE_FAILED.getCode(), message);
    }
    
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResultData<Void> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("请求路径不存在: {}", e.getRequestURL());
        return ResultData.failed(ResultCode.NOT_FOUND);
    }
    
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ResultData<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        String message = "不支持的请求方法: " + e.getMethod();
        log.warn(message);
        return ResultData.failed(HttpStatus.METHOD_NOT_ALLOWED.value(), message);
    }
    
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ResultData<Void> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        log.warn("不支持的媒体类型: {}", e.getContentType());
        return ResultData.failed(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "不支持的媒体类型");
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResultData<Void> handleException(Exception e) {
        log.error("系统异常: ", e);
        return ResultData.failed(ResultCode.SYSTEM_ERROR);
    }
}
