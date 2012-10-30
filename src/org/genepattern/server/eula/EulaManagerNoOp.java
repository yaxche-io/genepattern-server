package org.genepattern.server.eula;

import java.util.Collections;
import java.util.List;

import org.genepattern.server.config.ServerConfiguration.Context;
import org.genepattern.webservice.TaskInfo;

/**
 * When the server is configured to ignore all EULA info.
 * @author pcarr
 *
 */
public class EulaManagerNoOp implements IEulaManager {

    public List<EulaInfo> getEulas(TaskInfo taskInfo) {
        return Collections.emptyList();
    }

    public void setEula(EulaInfo eula, TaskInfo taskInfo) {
        //ignore
    }

    public void setEulas(List<EulaInfo> eulas, TaskInfo taskInfo) {
        //ignore
    }

    public boolean requiresEula(Context taskContext) {
        return false;
    }

    public List<EulaInfo> getAllEulaForModule(Context taskContext) {
        return Collections.emptyList();
    }

    public List<EulaInfo> getPendingEulaForModule(Context taskContext) {
        return Collections.emptyList();
    }

    public void recordEula(Context taskContext) throws IllegalArgumentException {
        //ignore
    }

}