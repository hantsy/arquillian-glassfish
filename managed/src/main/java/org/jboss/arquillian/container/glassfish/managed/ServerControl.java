/*
 * Copyright 2011, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.container.glassfish.managed;

import org.jboss.arquillian.container.spi.client.container.LifecycleException;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Runtime.getRuntime;

/**
 * A class for issuing asadmin commands using the admin-cli.jar of the GlassFish distribution.
 *
 * @author <a href="http://community.jboss.org/people/dan.j.allen">Dan Allen</a>
 */
class ServerControl {
    private static final Logger logger = Logger.getLogger(ServerControl.class.getName());

    private static final String DERBY_MISCONFIGURED_HINT = """
        It seems that the Glassfish version you are running might have a problem starting embedded \
        Derby database. Please take a look at the server logs. You can also switch off 'enableDerby' \
        property in your 'arquillian.xml' if you don't need it. For more information please refer to \
        relevant issues for existing workarounds: https://java.net/jira/browse/GLASSFISH-21004 \
        https://issues.apache.org/jira/browse/DERBY-6438""";

    private final ManagedContainerConfiguration config;

    private Thread shutdownHook;

    ServerControl(ManagedContainerConfiguration config) {
        this.config = config;
    }

    void start() throws LifecycleException {
        registerShutdownHook();

        if (config.isEnableDerby()) {
            startDerbyDatabase();
        }

        var cmd = asadmin("Starting container")
            .globalOption("--terse")
            .subcommand("start-domain");
        if (config.isDebug()) {
            cmd.option("--debug");
        }
        cmd.operand(config.getDomain()).execute(createProcessOutputConsumer());
    }

    void stop() throws LifecycleException {
        removeShutdownHook();
        try {
            stopContainer();
        } catch (LifecycleException failedStoppingContainer) {
            logger.log(Level.SEVERE, "Failed stopping container.", failedStoppingContainer);
        } finally {
            stopDerbyDatabase();
        }
    }

    private void stopContainer() throws LifecycleException {
        asadmin("Stopping container").globalOption("--terse").subcommand("stop-domain")
            .option("--kill")
            .operand(config.getDomain())
            .execute(createProcessOutputConsumer());
    }

    private void startDerbyDatabase() throws LifecycleException {
        if (!config.isEnableDerby()) {
            return;
        }
        try {
            asadmin("Starting database").globalOption("--terse").subcommand("start-database").execute(createProcessOutputConsumer());
        } catch (LifecycleException e) {
            logger.warning(DERBY_MISCONFIGURED_HINT);
            throw e;
        }
    }

    private void stopDerbyDatabase() throws LifecycleException {
        if (config.isEnableDerby()) {
            asadmin("Stopping database").globalOption("--terse").subcommand("stop-database").execute(createProcessOutputConsumer());
        }
    }

    private void removeShutdownHook() {
        if (shutdownHook != null) {
            getRuntime().removeShutdownHook(shutdownHook);
            shutdownHook = null;
        }
    }

    private void registerShutdownHook() {
        shutdownHook = new Thread(() -> {
            logger.warning("Forcing container shutdown");
            try {
                stopContainer();
                stopDerbyDatabase();
            } catch (LifecycleException e) {
                logger.log(Level.SEVERE, "Failed stopping services through shutdown hook.", e);
            }
        });
        getRuntime().addShutdownHook(shutdownHook);
    }

    private AsadminCommand.Builder asadmin(String description) {
        return AsadminCommand.builder(description,
            config.getAdminCli().toAbsolutePath().toString(),
            config.isOutputToConsole());
    }

    /**
     * An asadmin CLI command: {@code asadmin [global-options] subcommand [options] [operands]}.
     * Built via the phased builder returned by {@link #builder}.
     */
    private static final class AsadminCommand {
        private final String description;
        private final List<String> command;
        private final boolean outputToConsole;

        private AsadminCommand(String description, List<String> command, boolean outputToConsole) {
            this.description = description;
            this.command = command;
            this.outputToConsole = outputToConsole;
        }

        static Builder builder(String description, String asadminPath, boolean outputToConsole) {
            return new Builder(description, asadminPath, outputToConsole);
        }

        void execute(ProcessOutputConsumer consumer) throws LifecycleException {
            if (outputToConsole) {
                System.out.println(description + " using command: " + command);
            }
            Process process;
            try {
                process = new ProcessBuilder(command).redirectErrorStream(true).start();
            } catch (IOException e) {
                throw new LifecycleException("Unable to execute " + command, e);
            }
            try (var reader = new ConsoleReader(process, consumer)) {
                Thread.startVirtualThread(reader);
                int result = process.waitFor();
                if (result != 0) {
                    throw new LifecycleException("Command returned " + result + ": " + command);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LifecycleException("Interrupted: " + description, e);
            } finally {
                process.destroy();
            }
        }

        /** Builds commands following {@code asadmin [global-options] subcommand [options] [operands]}. */
        static final class Builder {
            private final String description;
            private final String asadminPath;
            private final boolean outputToConsole;
            private final List<String> globalOptions = new ArrayList<>();
            private String subcommand;
            private final List<String> options = new ArrayList<>();
            private final List<String> operands = new ArrayList<>();

            Builder(String description, String asadminPath, boolean outputToConsole) {
                this.description = description;
                this.asadminPath = asadminPath;
                this.outputToConsole = outputToConsole;
            }

            Builder globalOption(String option) {
                globalOptions.add(option);
                return this;
            }

            Builder subcommand(String name) {
                this.subcommand = name;
                return this;
            }

            Builder option(String option) {
                options.add(option);
                return this;
            }

            Builder operand(String value) {
                if (value != null) {
                    operands.add(value);
                }
                return this;
            }

            AsadminCommand build() {
                var cmd = new ArrayList<String>();
                cmd.add(asadminPath);
                cmd.addAll(globalOptions);
                cmd.add(subcommand);
                cmd.addAll(options);
                cmd.addAll(operands);
                return new AsadminCommand(description, cmd, outputToConsole);
            }

            void execute(ProcessOutputConsumer consumer) throws LifecycleException {
                build().execute(consumer);
            }
        }
    }

    private static class ConsoleReader implements Runnable, Closeable {

        private final ProcessOutputConsumer consumer;

        private final BufferedReader reader;

        private ConsoleReader(final Process process, ProcessOutputConsumer consumer) {
            this.reader = process.inputReader();
            this.consumer = consumer;
        }

        public void run() {
            try {
                reader.lines().forEach(consumer::consume);
            } catch (UncheckedIOException failOnReading) {
                logger.log(Level.SEVERE, failOnReading.getCause().getMessage(), failOnReading.getCause());
            }
        }

        public void close() {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException failOnClose) {
                    logger.log(Level.SEVERE, failOnClose.getMessage(), failOnClose);
                }
            }
        }
    }

    private interface ProcessOutputConsumer {

        void consume(String line);
    }

    private ProcessOutputConsumer createProcessOutputConsumer() {
        return config.isOutputToConsole()
            ? System.out::println
            : line -> { };
    }
}
