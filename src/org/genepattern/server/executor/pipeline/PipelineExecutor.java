/*******************************************************************************
 * Copyright (c) 2003, 2015 Broad Institute, Inc. and Massachusetts Institute of Technology.  All rights reserved.
 *******************************************************************************/
package org.genepattern.server.executor.pipeline;

import java.io.File;
import java.util.Map;

import org.genepattern.server.database.HibernateUtil;
import org.genepattern.server.domain.JobStatus;
import org.genepattern.server.executor.CommandExecutor;
import org.genepattern.server.executor.CommandExecutorException;
import org.genepattern.server.executor.CommandProperties;
import org.genepattern.webservice.JobInfo;

/**
 * Run all pipelines with this CommandExecutor.
 * 
 * This (initial) implementation uses a single thread for each pipeline using code ported from when each pipeline 
 * was run in a new JVM processes.
 * 
 * Manage submission of pipeline jobs, so that they can be terminated by jobId 
 * in response to user request or server shutdown.
 * 
 * @author pcarr
 */
public class PipelineExecutor implements CommandExecutor {

    //'pipeline.num.threads' from genepattern.properties
    //private int numPipelines = 20;   
    
    public void setConfigurationFilename(String filename) {
    }

    public void setConfigurationProperties(CommandProperties properties) {
    }

    public void start() {
    }

    public void stop() {
    }

    public void runCommand(final String[] commandLine,
            final Map<String, String> environmentVariables, 
            final File runDir,
            final File stdoutFile, 
            final File stderrFile, 
            final JobInfo jobInfo, 
            final File stdinFile) 
    throws CommandExecutorException {
        //no longer using commandLine for processing pipelines
        PipelineHandler.startPipeline(HibernateUtil.instance(), jobInfo, Integer.MAX_VALUE);
    }
    
    public void terminateJob(JobInfo jobInfo) throws Exception {
        PipelineHandler.terminatePipeline(HibernateUtil.instance(), jobInfo);
    }
    
    /**
     * Cleanup running pipelines which no longer have any active steps:
     * a) all of the child jobs are 'FINISHED' but the pipeline's status is still 'PROCESSING'
     * b) one of the child jobs is 'ERROR', but the pipeline's status is still 'PROCESSING'
     */
    public int handleRunningJob(JobInfo jobInfo) throws Exception {
        String origStatus = jobInfo.getStatus();
        int origStatusId = JobStatus.STATUS_MAP.get(origStatus);
        PipelineHandler.handleRunningPipeline(HibernateUtil.instance(), jobInfo);
        return origStatusId;
    }
}
