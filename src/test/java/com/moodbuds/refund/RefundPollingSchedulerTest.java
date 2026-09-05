package com.moodbuds.refund;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

class RefundPollingSchedulerTest {
    @Test
    void reconcilesEachDueRefund() {
        RefundStore store = mock(RefundStore.class);
        RefundService service = mock(RefundService.class);
        when(store.dueRefundIds(25)).thenReturn(List.of(3L, 7L));
        var scheduler = new RefundPollingScheduler(store, service, new RefundProperties(25));

        scheduler.reconcileRefunds();

        verify(service).process(3L);
        verify(service).process(7L);
    }
}
