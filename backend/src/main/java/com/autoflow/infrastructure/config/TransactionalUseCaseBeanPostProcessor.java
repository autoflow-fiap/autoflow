package com.autoflow.infrastructure.config;

import com.autoflow.application.exception.ApplicationException;
import com.autoflow.application.transaction.TransactionalUseCase;
import com.autoflow.infrastructure.observability.NewRelicBusinessTelemetry;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;

@Component
public class TransactionalUseCaseBeanPostProcessor implements BeanPostProcessor {
    private final PlatformTransactionManager transactionManager;
    private final NewRelicBusinessTelemetry telemetry;

    public TransactionalUseCaseBeanPostProcessor(PlatformTransactionManager transactionManager,
                                                 NewRelicBusinessTelemetry telemetry) {
        this.transactionManager = transactionManager;
        this.telemetry = telemetry;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();
        if (!hasTransactionalMethod(targetClass)) {
            return bean;
        }

        ProxyFactory proxyFactory = new ProxyFactory(bean);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice((MethodInterceptor) invocation -> {
            Method method = targetClass.getMethod(
                    invocation.getMethod().getName(), invocation.getMethod().getParameterTypes());
            if (AnnotationUtils.findAnnotation(method, TransactionalUseCase.class) == null) {
                return invocation.proceed();
            }
            try {
                return new TransactionTemplate(transactionManager).execute(status -> {
                    try {
                        Object result = invocation.proceed();
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                telemetry.recordSuccessfulUseCase(targetClass.getSimpleName(), result);
                            }
                        });
                        return result;
                    } catch (RuntimeException | Error exception) {
                        status.setRollbackOnly();
                        throw exception;
                    } catch (Throwable exception) {
                        status.setRollbackOnly();
                        throw new IllegalStateException(exception);
                    }
                });
            } catch (RuntimeException exception) {
                if (exception instanceof DataAccessException) {
                    telemetry.recordIntegrationFailure("database", "DATABASE_OPERATION", exception);
                }
                if (isUnexpectedOrderFailure(targetClass, exception)) {
                    telemetry.recordOrderProcessingFailure(targetClass.getSimpleName(), exception);
                }
                throw exception;
            }
        });
        return proxyFactory.getProxy();
    }

    private boolean isUnexpectedOrderFailure(Class<?> targetClass, RuntimeException exception) {
        return targetClass.getPackageName().contains("usecases.ordemservico")
                && !(exception instanceof DataAccessException)
                && !(exception instanceof ApplicationException)
                && !(exception instanceof IllegalArgumentException)
                && !(exception instanceof IllegalStateException);
    }

    private boolean hasTransactionalMethod(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (AnnotationUtils.findAnnotation(method, TransactionalUseCase.class) != null) {
                return true;
            }
        }
        return false;
    }
}
