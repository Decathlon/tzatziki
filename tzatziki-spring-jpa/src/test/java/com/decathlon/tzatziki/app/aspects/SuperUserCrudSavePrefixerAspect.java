package com.decathlon.tzatziki.app.aspects;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import com.decathlon.tzatziki.app.model.SuperUser;

import java.util.Arrays;

@Aspect
@Configuration
@EnableAspectJAutoProxy
public class SuperUserCrudSavePrefixerAspect {
    @Around(value = "execution(* com.decathlon.tzatziki.app.dao.SuperUserDataSpringRepository.saveAll(..))")
    public Object addPrefixToRoles(ProceedingJoinPoint joinPoint) throws Throwable {
        ((Iterable<SuperUser>) Arrays.stream(joinPoint.getArgs()).findFirst().get()).forEach(superUser -> superUser.setRole("superUser_"+superUser.getRole()));
        return joinPoint.proceed();
    }
}
