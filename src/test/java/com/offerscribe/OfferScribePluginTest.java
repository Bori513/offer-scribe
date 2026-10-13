package com.offerscribe;

import java.util.Arrays;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Development entry point. It is not part of the Plugin Hub production JAR. */
public final class OfferScribePluginTest {
    private OfferScribePluginTest() {}

    public static void main(String[] args) throws Exception {
        // Bolt launches custom jars without `java -ea`. Enable assertions before
        // ExternalPluginManager is initialized so RuneLite's development safety
        // check remains active rather than bypassed.
        ClassLoader loader = OfferScribePluginTest.class.getClassLoader();
        loader.setDefaultAssertionStatus(true);
        loader.setPackageAssertionStatus("net.runelite.client", true);
        ExternalPluginManager.loadBuiltin(OfferScribePlugin.class);

        // Bolt normally launches RuneLite's launcher JAR, so it adds launcher
        // JVM passthrough arguments (for example -J-Duser.home=...). This fat
        // development JAR starts the client directly and must not pass those
        // launcher-only arguments to RuneLite's option parser.
        String[] clientArgs = Arrays.stream(args)
            .filter(argument -> !argument.startsWith("-J"))
            .toArray(String[]::new);
        RuneLite.main(clientArgs);
    }
}
