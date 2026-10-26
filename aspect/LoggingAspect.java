package com.ecommerce.productordermanagement.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {

    @Before("execution(* com.ecommerce.productordermanagement.service..*(..))")
    public void logBefore(JoinPoint joinPoint) {
        System.out.println("AOP LOG - Method started: "
                + joinPoint.getSignature().getName());
    }

    @AfterReturning(
            pointcut = "execution(* com.ecommerce.productordermanagement.service..*(..))",
            returning = "result")
    public void logAfter(JoinPoint joinPoint, Object result) {
        System.out.println("AOP LOG - Method completed: "
                + joinPoint.getSignature().getName());
    }
}