package support;

import authentication.AuthenticationRuntimeFactory;
import framework.authentication.AuthenticationRuntime;
import framework.authentication.EnvironmentCredentialSource;
import framework.config.ConfigLoader;
import framework.config.FrameworkConfig;
import org.testng.ISuite;
import org.testng.ISuiteListener;

/**
 * Loads immutable configuration and authentication runtime state once for the
 * complete TestNG suite. Test classes retrieve both objects from suite
 * attributes instead of parsing configuration or constructing token caches.
 */
public final class SuiteConfigListener implements ISuiteListener {
    static final String CONFIG_ATTRIBUTE = FrameworkConfig.class.getName();
    static final String AUTHENTICATION_ATTRIBUTE = AuthenticationRuntime.class.getName();
    private static final String CONFIG_RESOURCE = "config/config.yaml";

    /** Resolves configuration and publishes fully built suite dependencies before tests start. */
    @Override
    public void onStart(ISuite suite) {
        // CI's -Denvironment value wins over the local testng.xml parameter.
        String selected = System.getProperty(
                "environment", suite.getXmlSuite().getParameter("environment"));
        FrameworkConfig config = new ConfigLoader()
                .loadFromClasspath(CONFIG_RESOURCE, selected);
        // Publish only after loading and validation succeed, so consumers never
        // observe a partially initialized configuration.
        suite.setAttribute(CONFIG_ATTRIBUTE, config);
        // Providers and their caches are constructed once, then shared safely by this suite.
        suite.setAttribute(AUTHENTICATION_ATTRIBUTE,
                AuthenticationRuntimeFactory.create(config, new EnvironmentCredentialSource()));
    }
}
