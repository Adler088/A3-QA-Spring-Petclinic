package org.springframework.samples.petclinic.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PQT-6: the monitoring aspect is a singleton shared by all request threads; its counters must not lose updates.
 */
class CallMonitoringAspectTests {

    private static ProceedingJoinPoint joinPoint(Object result) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.toShortString()).thenReturn("execution(Repository.call())");
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    @Test
    @DisplayName("PQT-6 | Chamadas concorrentes sao todas contabilizadas")
    void countsConcurrentCallsWithoutLosingUpdates() throws Throwable {
        CallMonitoringAspect aspect = new CallMonitoringAspect();
        ProceedingJoinPoint joinPoint = joinPoint("ok");
        int threads = 8;
        int callsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < callsPerThread; i++) {
                        try {
                            aspect.invoke(joinPoint);
                        } catch (Throwable e) {
                            throw new IllegalStateException(e);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(aspect.getCallCount()).isEqualTo(threads * callsPerThread);
        assertThat(aspect.getCallTime()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("PQT-6 | Monitoramento desabilitado apenas delega a chamada")
    void disabledMonitoringOnlyProceeds() throws Throwable {
        CallMonitoringAspect aspect = new CallMonitoringAspect();
        aspect.setEnabled(false);

        assertThat(aspect.invoke(joinPoint("result"))).isEqualTo("result");
        assertThat(aspect.isEnabled()).isFalse();
        assertThat(aspect.getCallCount()).isZero();
        assertThat(aspect.getCallTime()).isZero();
    }

    @Test
    @DisplayName("PQT-6 | Excecao do metodo monitorado tambem e contabilizada e propagada")
    void failedCallsAreCountedAndRethrown() throws Throwable {
        CallMonitoringAspect aspect = new CallMonitoringAspect();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.toShortString()).thenReturn("execution(Repository.fail())");
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.invoke(joinPoint)).isInstanceOf(IllegalStateException.class);
        assertThat(aspect.getCallCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("PQT-6 | reset zera contadores")
    void resetClearsCounters() throws Throwable {
        CallMonitoringAspect aspect = new CallMonitoringAspect();
        aspect.invoke(joinPoint("ok"));
        aspect.invoke(joinPoint("ok"));

        aspect.reset();

        assertThat(aspect.getCallCount()).isZero();
        assertThat(aspect.getCallTime()).isZero();
        assertThat(aspect.isEnabled()).isTrue();
    }
}
