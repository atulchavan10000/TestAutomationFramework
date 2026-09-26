package support;

import framework.config.ConfigLoader;
import framework.config.FrameworkConfig;
import org.testng.ISuite;
import org.testng.ISuiteListener;

/** Loads the immutable consumer configuration once for the complete TestNG suite. */
public final class SuiteConfigListener implements ISuiteListener {
    static final String CONFIG_ATTRIBUTE = FrameworkConfig.class.getName();
    private static final String CONFIG_RESOURCE = "config/config.yaml";

    @Override
    public void onStart(ISuite suite) {
        // CI's -Denvironment value wins over the local testng.xml parameter.
        String selected = System.getProperty(
                "environment", suite.getXmlSuite().getParameter("environment"));
        FrameworkConfig config = new ConfigLoader()
                .loadFromClasspath(CONFIG_RESOURCE, selected);
        suite.setAttribute(CONFIG_ATTRIBUTE, config);
    }
}
