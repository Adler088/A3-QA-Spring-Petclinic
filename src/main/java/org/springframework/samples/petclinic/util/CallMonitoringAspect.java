/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.util;

import java.util.concurrent.atomic.LongAdder;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.util.StopWatch;

/**
 * Simple aspect that monitors call count and call invocation time. It uses JMX
 * annotations and therefore can be monitored using any JMX console such as the
 * jConsole
 * <p/> This is only useful if you use JPA or JDBC.  Spring-data-jpa doesn't
 * have any correctly annotated classes to join on
 * <p/> The aspect is a singleton shared by every request thread: the counters
 * are {@link LongAdder}s (lock-free and contention friendly) and the
 * {@code enabled} flag is {@code volatile}, so JMX reads and writes are always
 * visible to the request threads (SpotBugs {@code IS2_INCONSISTENT_SYNC},
 * {@code AT_NONATOMIC_64BIT_PRIMITIVE} and
 * {@code AT_STALE_THREAD_WRITE_OF_PRIMITIVE}). {@link #reset()} is not atomic
 * with calls that finish at the same time, which is acceptable for monitoring
 * statistics.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Michael Isvy
 * @since 2.5
 */
@ManagedResource("petclinic:type=CallMonitor")
@Aspect
public class CallMonitoringAspect {

    private volatile boolean enabled = true;

    private final LongAdder callCount = new LongAdder();

    private final LongAdder accumulatedCallTime = new LongAdder();

    @ManagedAttribute
    public boolean isEnabled() {
        return enabled;
    }

    @ManagedAttribute
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @ManagedOperation
    public void reset() {
        this.callCount.reset();
        this.accumulatedCallTime.reset();
    }

    @ManagedAttribute
    public int getCallCount() {
        return this.callCount.intValue();
    }

    @ManagedAttribute
    public long getCallTime() {
        long count = this.callCount.sum();
        if (count > 0) {
            return this.accumulatedCallTime.sum() / count;
        }
        return 0;
    }


    @Around("within(@org.springframework.stereotype.Repository *)")
    public Object invoke(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!this.enabled) {
            return joinPoint.proceed();
        }
        StopWatch sw = new StopWatch(joinPoint.toShortString());
        sw.start("invoke");
        try {
            return joinPoint.proceed();
        } finally {
            sw.stop();
            this.accumulatedCallTime.add(sw.getTotalTimeMillis());
            this.callCount.increment();
        }
    }

}
