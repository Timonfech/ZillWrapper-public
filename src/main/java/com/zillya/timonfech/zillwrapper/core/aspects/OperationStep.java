package com.zillya.timonfech.zillwrapper.core.aspects;

import com.zillya.timonfech.zillwrapper.core.entities.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationStep {
    OperationType type();

    Props [] stepProps() default Props.CRUCIAL;
    enum Props {NONE, START, CRUCIAL, FINAL, INTERACTIVE, RESUME, ASYNC}
}
