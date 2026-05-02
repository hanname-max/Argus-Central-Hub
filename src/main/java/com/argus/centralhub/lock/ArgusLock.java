package com.argus.centralhub.lock;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ArgusLock {

    String value() default "";

    String prefix() default "argus:lock:";

    long waitTime() default 3;

    long leaseTime() default 30;

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    LockType lockType() default LockType.REENTRANT;

    enum LockType {
        REENTRANT,
        FAIR,
        READ,
        WRITE
    }
}
