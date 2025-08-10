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
        
        if (jParams != null) {
            for (ParameterValue pv : jParams) {
                this.jobParams.add(pv.getName());
            }
        }
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
        addEnvVars(run, env);
    }

    @Override
    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
        addEnvVars(build, env);
    }

    private void addEnvVars(Run<?, ?> run, EnvVars env) {

        if (env == null || messageParams == null) {
            return;
        }

        // Only include variables in environment that are not defined as job parameters. And
        // do not overwrite any existing environment variables (like PATH).
        for (Map.Entry<String, String> e : messageParams.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            
            if (!jobParams.contains(key) && !env.containsKey(key)) {
                
                // Special handling for CI_MESSAGE - save to file instead of environment variable
                if ("CI_MESSAGE".equals(key)) {
                    saveCIMessageToFile(run, value, env);
                } else {
                    env.put(key, value);
                }
            }
        }
    }

    private void saveCIMessageToFile(Run<?, ?> run, String ciMessage, EnvVars env) {
        try {
            // Get workspace
            FilePath workspace = null;
            
            if (run instanceof AbstractBuild) {
                workspace = ((AbstractBuild<?, ?>) run).getWorkspace();
            } else {
                // For pipeline jobs, try to get workspace from run
                try {
                    workspace = new FilePath(run.getRootDir()).child("workspace");
                } catch (Exception e) {
                    workspace = new FilePath(run.getRootDir());
                }
            }

            if (workspace != null) {
                FilePath ciMessageFile = workspace.child(CI_MESSAGE_FILE);
                ciMessageFile.write(ciMessage, StandardCharsets.UTF_8.name());
                env.put("CI_MESSAGE_FILE", ciMessageFile.getRemote());
            } else {
                env.put("CI_MESSAGE", ciMessage);
            }
        } catch (IOException | InterruptedException e) {
            env.put("CI_MESSAGE", ciMessage);
        }
    }
}
