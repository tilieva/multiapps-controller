package com.sap.cloud.lm.sl.cf.core.cf.util;

import java.util.List;

import org.cloudfoundry.multiapps.mta.model.Module;

public interface ModulesContentValidator {

    void validate(List<Module> modules);

}
