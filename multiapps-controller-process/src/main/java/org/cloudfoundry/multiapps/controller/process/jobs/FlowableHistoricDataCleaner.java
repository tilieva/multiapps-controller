package org.cloudfoundry.multiapps.controller.process.jobs;

import static java.text.MessageFormat.format;

import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.cloudfoundry.multiapps.controller.process.Messages;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

@Named
@Order(20)
public class FlowableHistoricDataCleaner implements Cleaner {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowableHistoricDataCleaner.class);
    private static final int PAGE_SIZE = 100;

    private final HistoryService historyService;
    private final int pageSize;

    @Inject
    public FlowableHistoricDataCleaner(HistoryService historyService) {
        this(historyService, PAGE_SIZE);
    }

    public FlowableHistoricDataCleaner(HistoryService historyService, int pageSize) {
        this.historyService = historyService;
        this.pageSize = pageSize;
    }

    @Override
    public void execute(Date expirationTime) {
        LOGGER.info(CleanUpJob.LOG_MARKER, format(Messages.WILL_DELETE_HISTORIC_PROCESSES_BEFORE_0, expirationTime));
        long deletedProcessesCount = 0;
        long expiredProcessesPages = getExpiredProcessesPageCount(expirationTime);
        for (int i = 0; i < expiredProcessesPages; i++) {
            deletedProcessesCount += deleteExpiredProcessesPage(expirationTime);
        }
        LOGGER.info(CleanUpJob.LOG_MARKER, format(Messages.DELETED_HISTORIC_PROCESSES_0, deletedProcessesCount));
    }

    private long getExpiredProcessesPageCount(Date expirationTime) {
        return (long) Math.ceil((double) getExpiredProcessesCount(expirationTime) / pageSize);
    }

    private long getExpiredProcessesCount(Date expirationTime) {
        return createExpiredHistoricProcessInstancesQuery(expirationTime).count();
    }

    private HistoricProcessInstanceQuery createExpiredHistoricProcessInstancesQuery(Date expirationTime) {
        return historyService.createHistoricProcessInstanceQuery()
                             .finished()
                             .excludeSubprocesses(true)
                             .startedBefore(expirationTime);
    }

    private long deleteExpiredProcessesPage(Date expirationTime) {
        List<HistoricProcessInstance> processesToDelete = getExpiredProcessesPage(expirationTime);
        return processesToDelete.stream()
                                .filter(this::deleteProcessSafely)
                                .count();
    }

    private List<HistoricProcessInstance> getExpiredProcessesPage(Date expirationTime) {
        return createExpiredHistoricProcessInstancesQuery(expirationTime).listPage(0, pageSize);
    }

    private boolean deleteProcessSafely(HistoricProcessInstance process) {
        String processId = process.getId();
        try {
            LOGGER.debug(CleanUpJob.LOG_MARKER, format(Messages.DELETING_HISTORIC_PROCESS_0, processId));
            historyService.deleteHistoricProcessInstance(processId);
            return true;
        } catch (Exception e) {
            LOGGER.warn(CleanUpJob.LOG_MARKER, format(Messages.COULD_NOT_DELETE_HISTORIC_PROCESS_0, processId), e);
            return false;
        }
    }

}
