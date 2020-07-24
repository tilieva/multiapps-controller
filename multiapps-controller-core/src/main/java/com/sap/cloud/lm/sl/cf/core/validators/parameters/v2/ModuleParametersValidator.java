package com.sap.cloud.lm.sl.cf.core.validators.parameters.v2;

import java.util.Map;

import org.cloudfoundry.multiapps.mta.model.Module;

import com.sap.cloud.lm.sl.cf.core.validators.parameters.ParametersValidator;
import com.sap.cloud.lm.sl.cf.core.validators.parameters.ParametersValidatorHelper;

public class ModuleParametersValidator extends ParametersValidator<Module> {

    protected final Module module;

    protected ModuleParametersValidator(Module module, ParametersValidatorHelper helper) {
        super("", module.getName(), helper, Module.class);
        this.module = module;
    }

    @Override
    public Module validate() {
        Map<String, Object> parameters = validateParameters(module.getParameters());
        module.setParameters(parameters);
        return module;
    }

}