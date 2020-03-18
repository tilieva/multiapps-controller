package com.sap.cloud.lm.sl.cf.process.listeners;

import javax.inject.Named;

import org.cloudfoundry.client.lib.domain.CloudServiceBroker;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.impl.context.Context;
import org.flowable.engine.runtime.Execution;

import com.sap.cloud.lm.sl.cf.client.lib.domain.CloudApplicationExtended;
import com.sap.cloud.lm.sl.cf.process.Constants;
import com.sap.cloud.lm.sl.cf.process.Messages;
import com.sap.cloud.lm.sl.cf.process.steps.StepsUtil;
import com.sap.cloud.lm.sl.cf.process.variables.Variables;
import com.sap.cloud.lm.sl.cf.process.variables.VariablesHandler;
import com.sap.cloud.lm.sl.common.util.JsonUtil;

@Named("deployAppSubProcessEndListener")
public class DeployAppSubProcessEndListener implements ExecutionListener {

    private static final long serialVersionUID = 1L;

    @Override
    public void notify(DelegateExecution context) {
        CloudServiceBroker cloudServiceBrokerExtended = StepsUtil.getCreatedOrUpdatedServiceBroker(context);

        if (cloudServiceBrokerExtended != null) {
            setVariableInParentProcess(context, Constants.VAR_APP_SERVICE_BROKER_VAR_PREFIX, cloudServiceBrokerExtended);
        }
    }

    private void setVariableInParentProcess(DelegateExecution context, String variablePrefix, Object variableValue) {
        VariablesHandler variablesHandler = new VariablesHandler(context);
        CloudApplicationExtended cloudApplication = variablesHandler.get(Variables.APP_TO_PROCESS);
        if (cloudApplication == null) {
            throw new IllegalStateException(Messages.CANNOT_DETERMINE_CURRENT_APPLICATION);
        }

        String moduleName = cloudApplication.getModuleName();
        if (moduleName == null) {
            throw new IllegalStateException(Messages.CANNOT_DETERMINE_MODULE_NAME);
        }
        String exportedVariableName = variablePrefix + moduleName;

        RuntimeService runtimeService = Context.getProcessEngineConfiguration()
                                               .getRuntimeService();

        String superExecutionId = context.getParentId();
        Execution superExecutionResult = runtimeService.createExecutionQuery()
                                                       .executionId(superExecutionId)
                                                       .singleResult();
        superExecutionId = superExecutionResult.getSuperExecutionId();

        byte[] binaryJson = variableValue == null ? null : JsonUtil.toJsonBinary(variableValue);
        runtimeService.setVariable(superExecutionId, exportedVariableName, binaryJson);
    }

}
