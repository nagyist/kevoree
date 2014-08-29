package org.kevoree.bootstrap.telemetry;

import org.kevoree.api.telemetry.TelemetryEvent;
import org.kevoree.bootstrap.Bootstrap;
import org.kevoree.core.impl.TelemetryEventImpl;
import org.kevoree.log.Log;
import org.kevoree.microkernel.KevoreeKernel;

import java.io.*;
import java.net.URISyntaxException;

/**
 * Created by duke on 8/14/14.
 */
public class BootstrapTelemetry {

    private static PrintStream systemOut, systemErr, myOut, myErr;

    public static void main(String[] args) throws URISyntaxException {
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();




        String nodeName = System.getProperty("node.name");
        if (nodeName == null) {
            nodeName = Bootstrap.defaultNodeName;
        }
        final String finalNodeName = nodeName;

        String telemetryURL = "tcp://localhost:9966";
        if (System.getProperty("telemetry.url") != null) {
            telemetryURL = System.getProperty("telemetry.url");
        }
        Log.info("Telemetry Server : " + telemetryURL);
        final MQTTDispatcher dispatcher = new MQTTDispatcher(telemetryURL, nodeName);

        Log.setLogger(new Log.Logger(){
            @Override
            public void log(int level, String message, Throwable ex) {
                switch (level) {
                    case Log.LEVEL_ERROR:dispatcher.notify(TelemetryEventImpl.build(finalNodeName, "error", message.replace("\n","\\n"), (ex!=null?ex.toString().replace("\n","\\n").replace("\t","\\t"):"")));break;
                    case Log.LEVEL_WARN:dispatcher.notify(TelemetryEventImpl.build(finalNodeName, "warn", message.replace("\n","\\n"), (ex!=null?ex.toString().replace("\n","\\n").replace("\t","\\t"):"")));break;
                    case Log.LEVEL_INFO:dispatcher.notify(TelemetryEventImpl.build(finalNodeName, "info", message.replace("\n","\\n"), (ex!=null?ex.toString().replace("\n","\\n").replace("\t","\\t"):"")));break;
                    case Log.LEVEL_DEBUG:dispatcher.notify(TelemetryEventImpl.build(finalNodeName, "debug", message.replace("\n","\\n"), (ex!=null?ex.toString().replace("\n","\\n").replace("\t","\\t"):"")));break;
                    case Log.LEVEL_TRACE:dispatcher.notify(TelemetryEventImpl.build(finalNodeName, "trace", message.replace("\n","\\n"), (ex!=null?ex.toString().replace("\n","\\n").replace("\t","\\t"):"")));break;
                    default: dispatcher.notify(TelemetryEventImpl.build(finalNodeName, "raw_out", message.replace("\n","\\n"), (ex!=null?ex.toString().replace("\n","\\n").replace("\t","\\t"):"")));break;
                }
            }
        });

        dispatcher.notify(TelemetryEventImpl.build(nodeName, "info", "Initiate Telemetry monitoring", ""));

        systemOut = System.out;
        systemErr = System.err;

        myOut = new PrintStream(new OutputStream() {
            StringBuffer buffer = new StringBuffer();

            @Override
            public void write(int b) throws IOException {
                systemOut.write(b);
                if (b == '\n') {
                    dispatcher.notify(TelemetryEventImpl.build(finalNodeName, "raw_out", buffer.toString(), ""));
                    buffer = new StringBuffer();
                } else {
                    buffer.append((char)b);
                }
            }
        });
        myErr = new PrintStream(new OutputStream() {
            StringBuffer buffer = new StringBuffer();

            @Override
            public void write(int b) throws IOException {
                systemErr.write(b);
                if (b == '\n') {
                    dispatcher.notify( TelemetryEventImpl.build(finalNodeName, "raw_err", buffer.toString(), ""));
                    buffer = new StringBuffer();
                } else {
                    buffer.append((char)b);
                }
            }
        });

        hackSystemStreams();

        final Bootstrap boot = new Bootstrap(KevoreeKernel.self.get(), nodeName);
        boot.getCore().addTelemetryListener(dispatcher);
        Runtime.getRuntime().addShutdownHook(new Thread("Shutdown Hook") {
            public void run() {
                try {
                    Thread.currentThread().setContextClassLoader(loader);
                    boot.stop();
                    dispatcher.notify(TelemetryEventImpl.build(finalNodeName, "stop", "Platform stopped", ""));
                } catch (Throwable ex) {
                    dispatcher.notify(TelemetryEventImpl.build(finalNodeName, "error", "Error stopping kevoree platform", ex.toString()));
                    System.out.println("Error stopping kevoree platform: " + ex.getMessage());
                } finally {
                    dispatcher.closeConnection();
                }
            }
        });
        String bootstrapModel = System.getProperty("node.bootstrap");
        try {
            if (bootstrapModel != null) {
                dispatcher.notify(TelemetryEventImpl.build(finalNodeName, "start", "Platform boot from file:" + bootstrapModel, ""));
                boot.bootstrapFromFile(new File(bootstrapModel));
            } else {
                dispatcher.notify(TelemetryEventImpl.build(finalNodeName, "start", "Platform boot from script", ""));
                if (System.getProperty("node.script") != null) {
                    boot.bootstrapFromKevScript(new ByteArrayInputStream(System.getProperty("node.script").getBytes()));
                } else {
                    String version;
                    if (boot.getCore().getFactory().getVersion().toLowerCase().contains("snapshot")) {
                        version = "latest";
                    } else {
                        version = "release";
                    }
                    Log.info("Create minimal system with library in version {}", version);
                    boot.bootstrapFromKevScript(new ByteArrayInputStream(Bootstrap.createBootstrapScript(nodeName, version).getBytes()));
                }
            }
        } catch (Exception e) {
            ByteArrayOutputStream boo = new ByteArrayOutputStream();
            PrintStream pr = new PrintStream(boo);
            e.printStackTrace(pr);
            pr.flush();
            pr.close();
            dispatcher.notify(TelemetryEventImpl.build(nodeName, "error", "Error during bootstrap", new String(boo.toByteArray())));
            //e.printStackTrace();
        }
    }

    protected static void hackSystemStreams() {
        System.setOut(myOut);
        System.setErr(myErr);
    }

    protected static void restoreSystemStreams() {
        System.setOut(systemOut);
        System.setErr(systemErr);
    }

}
