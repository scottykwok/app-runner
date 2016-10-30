package com.danielflower.apprunner.runners;

import com.danielflower.apprunner.problems.ProjectCannotStartException;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.shared.invoker.InvocationOutputHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GoRunner implements AppRunner {
    public static final Logger log = LoggerFactory.getLogger(GoRunner.class);

    public static final String[] startCommands = new String[]{"go get", "go build", "go test", "./{app_dir_name}"};
    private final File projectRoot;
    private final String goPath;
    private final CommandLineProvider goCmd;
    private ExecuteWatchdog watchDog;

    public GoRunner(File projectRoot, String goPath, CommandLineProvider goCmd) {
        this.projectRoot = projectRoot;
        this.goPath = goPath;
        this.goCmd = goCmd;
    }

    @Override
    public File getInstanceDir() {
        return projectRoot;
    }

    public void start(InvocationOutputHandler buildLogHandler, InvocationOutputHandler consoleLogHandler, Map<String, String> envVarsForApp, Waiter startupWaiter) throws ProjectCannotStartException {
        envVarsForApp.put("GOPATH", goPath);
        try {
            rungo(buildLogHandler, envVarsForApp, "get");
        } catch (Exception e) {
            /*
            This is a hacky way to build go project:
            "go get" cmd can be run outside of GOPATH to only get the src of dependencies but throw exception on trying to build a package.
            Then "go build" cmd can be run outside of GOPATH to produce executable binary of the app on current dir.
             */
            log.debug("Ignoring the exception", e.getMessage());
        }
        rungo(buildLogHandler, envVarsForApp, "build");
        rungo(buildLogHandler, envVarsForApp, "test");

        CommandLine command = new CommandLine("." + File.separator + projectRoot.getName());

        watchDog = ProcessStarter.startDaemon(buildLogHandler, consoleLogHandler, envVarsForApp, command, projectRoot, startupWaiter);
    }

    private void rungo(InvocationOutputHandler buildLogHandler, Map<String, String> envVarsForApp, String... arguments) {
        CommandLine command = goCmd.commandLine(envVarsForApp);
        for (String argument : arguments)
            command.addArgument(argument);

        buildLogHandler.consumeLine("Running go " + StringUtils.join(arguments, " ") + " with " + command);
        ProcessStarter.run(buildLogHandler, envVarsForApp, command, projectRoot, TimeUnit.MINUTES.toMillis(20));
    }

    public void shutdown() {
        if (watchDog != null) {
            watchDog.destroyProcess();
            watchDog.stop();
        }
    }
}
