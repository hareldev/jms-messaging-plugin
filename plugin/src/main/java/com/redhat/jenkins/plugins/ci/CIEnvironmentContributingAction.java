/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.redhat.jenkins.plugins.ci;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;
import hudson.model.ParameterValue;
import hudson.model.Run;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CIEnvironmentContributingAction implements EnvironmentContributingAction {

    private static final Logger log = Logger.getLogger(CIEnvironmentContributingAction.class.getName());
    private static final String CI_MESSAGE_FILE = ".ci_message.txt";

    private final transient Map<String, String> messageParams;
    private final transient Set<String> jobParams = new HashSet<>();

    public CIEnvironmentContributingAction(Map<String, String> messageParams) {
        this(messageParams, null);
    }

    public CIEnvironmentContributingAction(Map<String, String> mParams, List<ParameterValue> jParams) {
        this.messageParams = mParams;
        
        log.info("=== CIEnvironmentContributingAction CREATED ===");
        log.info("Message parameters received: " + (mParams != null ? mParams.size() : 0) + " variables");
        if (mParams != null) {
            for (Map.Entry<String, String> entry : mParams.entrySet()) {
                log.info("Variable: " + entry.getKey() + " (size: " + 
                        (entry.getValue() != null ? entry.getValue().length() : 0) + " characters)");
            }
        }
        log.info("Job parameters: " + (jParams != null ? jParams.size() : 0) + " parameters");
        
        if (jParams != null) {
            for (ParameterValue pv : jParams) {
                this.jobParams.add(pv.getName());
                log.info("Job parameter: " + pv.getName());
            }
        }
        
        log.info("=== CIEnvironmentContributingAction initialization completed ===");
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }

    @Override
    public void buildEnvironment(@Nonnull Run<?, ?> run, @Nonnull EnvVars env) {
        log.info("=== buildEnvironment() called ===");
        log.info("Run type: " + (run != null ? run.getClass().getSimpleName() : "null"));
        log.info("Build number: " + (run != null ? run.getNumber() : "unknown"));
        addEnvVars(run, env);
    }

    @Override
    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
        log.info("=== buildEnvVars() called ===");
        log.info("Build type: " + (build != null ? build.getClass().getSimpleName() : "null"));
        log.info("Build number: " + (build != null ? build.getNumber() : "unknown"));
        addEnvVars(build, env);
    }

    private void addEnvVars(Run<?, ?> run, EnvVars env) {

        if (env == null || messageParams == null) {
            log.info("Skipping environment variable processing: env=" + (env == null ? "null" : "available") + 
                    ", messageParams=" + (messageParams == null ? "null" : messageParams.size() + " parameters"));
            return;
        }

        log.info("Processing " + messageParams.size() + " message parameters for environment variables");

        // Only include variables in environment that are not defined as job parameters. And
        // do not overwrite any existing environment variables (like PATH).
        for (Map.Entry<String, String> e : messageParams.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            
            if (!jobParams.contains(key) && !env.containsKey(key)) {
                
                // Special handling for CI_MESSAGE - save to file instead of environment variable
                if ("CI_MESSAGE".equals(key)) {
                    log.info("Processing CI_MESSAGE parameter - routing to file instead of environment variable (size: " + 
                            (value != null ? value.length() : 0) + " characters)");
                    saveCIMessageToFile(run, value, env);
                } else {
                    log.fine("Setting environment variable: " + key + " (size: " + 
                            (value != null ? value.length() : 0) + " characters)");
                    env.put(key, value);
                }
            } else {
                log.fine("Skipping parameter '" + key + "' - " + 
                        (jobParams.contains(key) ? "defined as job parameter" : "already exists in environment"));
            }
        }
        
        log.info("Environment variable processing completed");
    }

    private void saveCIMessageToFile(Run<?, ?> run, String ciMessage, EnvVars env) {
        log.info("=== Starting CI_MESSAGE file save process ===");
        log.info("CI_MESSAGE content size: " + (ciMessage != null ? ciMessage.length() : 0) + " characters");
        log.info("Run type: " + (run != null ? run.getClass().getSimpleName() : "null"));
        
        try {
            // Get workspace
            log.info("Step 1: Determining workspace location...");
            FilePath workspace = null;
            
            if (run instanceof AbstractBuild) {
                log.info("Run is AbstractBuild - attempting to get workspace directly");
                workspace = ((AbstractBuild<?, ?>) run).getWorkspace();
                if (workspace != null) {
                    log.info("Successfully obtained workspace from AbstractBuild: " + workspace.getRemote());
                } else {
                    log.warning("AbstractBuild workspace is null");
                }
            } else {
                // For pipeline jobs, try to get workspace from run
                log.info("Run is not AbstractBuild (likely Pipeline) - attempting alternative workspace discovery");
                try {
                    workspace = new FilePath(run.getRootDir()).child("workspace");
                    log.info("Created workspace path from build root: " + workspace.getRemote());
                } catch (Exception e) {
                    log.log(Level.WARNING, "Could not determine workspace from build root, using build directory as fallback", e);
                    workspace = new FilePath(run.getRootDir());
                    log.info("Using build directory as workspace: " + workspace.getRemote());
                }
            }

            if (workspace != null) {
                log.info("Step 2: Creating file path for CI_MESSAGE...");
                FilePath ciMessageFile = workspace.child(CI_MESSAGE_FILE);
                log.info("Target file path: " + ciMessageFile.getRemote());
                
                log.info("Step 3: Writing CI_MESSAGE content to file...");
                log.info("File encoding: " + StandardCharsets.UTF_8.name());
                ciMessageFile.write(ciMessage, StandardCharsets.UTF_8.name());
                log.info("Successfully wrote " + (ciMessage != null ? ciMessage.length() : 0) + " characters to file");
                
                log.info("Step 4: Setting CI_MESSAGE_FILE environment variable...");
                env.put("CI_MESSAGE_FILE", ciMessageFile.getRemote());
                log.info("Environment variable CI_MESSAGE_FILE set to: " + ciMessageFile.getRemote());
                
                log.info("=== CI_MESSAGE file save process completed successfully ===");
                
                // Verify file exists and log its properties
                try {
                    if (ciMessageFile.exists()) {
                        long fileSize = ciMessageFile.length();
                        log.info("File verification: File exists with size " + fileSize + " bytes");
                    } else {
                        log.warning("File verification: File does not exist after write operation");
                    }
                } catch (Exception verifyEx) {
                    log.log(Level.WARNING, "File verification failed", verifyEx);
                }
                
            } else {
                log.warning("Step 1 FAILED: Could not determine workspace location");
                log.info("FALLBACK: Using CI_MESSAGE environment variable instead of file");
                env.put("CI_MESSAGE", ciMessage);
                log.info("Set CI_MESSAGE environment variable (size: " + (ciMessage != null ? ciMessage.length() : 0) + " characters)");
                log.warning("=== CI_MESSAGE file save process failed - used environment variable fallback ===");
            }
        } catch (IOException | InterruptedException e) {
            log.severe("EXCEPTION during CI_MESSAGE file save process: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            log.info("FALLBACK: Using CI_MESSAGE environment variable due to exception");
            env.put("CI_MESSAGE", ciMessage);
            log.info("Set CI_MESSAGE environment variable (size: " + (ciMessage != null ? ciMessage.length() : 0) + " characters)");
            log.log(Level.WARNING, "=== CI_MESSAGE file save process failed with exception - used environment variable fallback ===", e);
        }
    }
}
